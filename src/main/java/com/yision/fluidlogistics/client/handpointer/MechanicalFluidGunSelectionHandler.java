package com.yision.fluidlogistics.client.handpointer;

import com.yision.fluidlogistics.block.MechanicalFluidGun.MechanicalFluidGunBlock;
import com.yision.fluidlogistics.block.MechanicalFluidGun.MechanicalFluidGunBlockEntity;
import com.yision.fluidlogistics.block.MechanicalFluidGun.MechanicalFluidGunTargetConfig;
import com.yision.fluidlogistics.network.MechanicalFluidGunPackets;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

public class MechanicalFluidGunSelectionHandler {

    private static final String GUN_HIGHLIGHT = "HandPointerMechanicalFluidGunHighlight";
    private static final int SELECTED_COLOR = 0xDDC166;
    private static final int VALID_COLOR = 0x7FCDE0;

    private static BlockPos selectedGunPos;
    private static final List<MechanicalFluidGunPackets.TargetPacket.TargetEntry> targets = new ArrayList<>();

    private MechanicalFluidGunSelectionHandler() {
    }

    public static void enterMode(Level level, BlockPos pos) {
        selectedGunPos = pos.immutable();
        targets.clear();

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MechanicalFluidGunBlockEntity gun && gun.hasTarget()) {
            for (MechanicalFluidGunTargetConfig target : gun.getTargets()) {
                targets.add(new MechanicalFluidGunPackets.TargetPacket.TargetEntry(target.absoluteFrom(pos).immutable(), target.face()));
            }
        }
    }

    public static boolean isSelectedGun(BlockPos pos) {
        return selectedGunPos != null && selectedGunPos.equals(pos);
    }

    public static boolean setTarget(Level level, BlockPos pos, @Nullable Direction face) {
        if (!isValidTarget(level, pos)) {
            return false;
        }

        Iterator<MechanicalFluidGunPackets.TargetPacket.TargetEntry> iterator = targets.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().pos().equals(pos)) {
                iterator.remove();
                clearTargetOutline(pos);
                return true;
            }
        }

        if (targets.size() >= MechanicalFluidGunPackets.TargetPacket.MAX_TARGETS) {
            return false;
        }

        targets.add(new MechanicalFluidGunPackets.TargetPacket.TargetEntry(pos.immutable(), face));
        return true;
    }

    public static boolean isTargetSelected(BlockPos pos) {
        for (MechanicalFluidGunPackets.TargetPacket.TargetEntry target : targets) {
            if (target.pos().equals(pos)) {
                return true;
            }
        }
        return false;
    }

    public static boolean submit() {
        if (selectedGunPos == null || targets.isEmpty()) {
            return false;
        }

        PacketDistributor.sendToServer(MechanicalFluidGunPackets.TargetPacket.setTargets(selectedGunPos, List.copyOf(targets)));
        return true;
    }

    public static boolean clearTarget() {
        if (selectedGunPos == null) {
            return false;
        }

        PacketDistributor.sendToServer(MechanicalFluidGunPackets.TargetPacket.clearTarget(selectedGunPos));
        clearTargetOutlines();
        targets.clear();
        return true;
    }

    public static int getTargetCount() {
        return targets.size();
    }

    public static void clearSelection() {
        selectedGunPos = null;
        clearTargetOutlines();
        targets.clear();
        clearHoverPreview();
        Outliner.getInstance().remove(GUN_HIGHLIGHT);
    }

    public static void clearHoverPreview() {
        clearTargetOutlines();
    }

    public static void renderSelection(Minecraft mc) {
        if (mc.level == null || selectedGunPos == null) {
            clearSelection();
            return;
        }

        renderOutline(mc.level, selectedGunPos, GUN_HIGHLIGHT, SELECTED_COLOR);

        if (targets.isEmpty()) {
            clearHoverPreview();
            return;
        }

        for (MechanicalFluidGunPackets.TargetPacket.TargetEntry target : targets) {
            if (isValidTarget(mc.level, target.pos())) {
                renderOutline(mc.level, target.pos(), targetSlot(target.pos()), VALID_COLOR);
            } else {
                clearTargetOutline(target.pos());
            }
        }
    }

    private static boolean isValidTarget(Level level, BlockPos pos) {
        if (selectedGunPos == null || pos == null) {
            return false;
        }
        return MechanicalFluidGunBlock.isSelectableTarget(level, selectedGunPos, pos);
    }

    private static void renderOutline(Level level, BlockPos pos, String slot, int color) {
        BlockState state = level.getBlockState(pos);
        VoxelShape shape = state.getShape(level, pos);
        if (shape.isEmpty()) {
            Outliner.getInstance().remove(slot);
            return;
        }
        Outliner.getInstance().showAABB(slot, shape.bounds().move(pos)).colored(color).lineWidth(0.0625F);
    }

    private static String targetSlot(BlockPos pos) {
        return "HandPointerMechanicalFluidGunTarget_" + pos.asLong();
    }

    private static void clearTargetOutline(BlockPos pos) {
        Outliner.getInstance().remove(targetSlot(pos));
    }

    private static void clearTargetOutlines() {
        for (MechanicalFluidGunPackets.TargetPacket.TargetEntry target : targets) {
            clearTargetOutline(target.pos());
        }
    }
}
