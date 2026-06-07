package com.yision.fluidlogistics.client;

import com.simibubi.create.foundation.utility.CreateLang;
import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.block.FluidHatch.FluidHatchFluidHandlerForwarder;
import com.yision.fluidlogistics.block.MechanicalFluidGun.MechanicalFluidGunBlock;
import com.yision.fluidlogistics.network.MechanicalFluidGunPackets;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.createmod.catnip.outliner.Outliner;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = FluidLogistics.MODID, value = Dist.CLIENT)
public class MechanicalFluidGunItemSelectionHandler {

    private static final String TARGET_OUTLINE = "MechanicalFluidGunItemTarget";
    private static final int TARGET_COLOR = 0x7FCDE0;

    private static ItemStack currentItem;
    private static final List<TargetSelection> selectedTargets = new ArrayList<>();

    private record TargetSelection(BlockPos pos, Direction face) {
    }

    private MechanicalFluidGunItemSelectionHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void rightClickingBlocksSelectsTarget(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (!level.isClientSide()) {
            return;
        }

        Player player = event.getEntity();
        if (player == null || player.isSpectator() || !isHoldingGun(player)) {
            return;
        }

        BlockPos pos = event.getPos();
        if (!MechanicalFluidGunBlock.isTargetTagged(level, pos)) {
            return;
        }

        Iterator<TargetSelection> iterator = selectedTargets.iterator();
        while (iterator.hasNext()) {
            TargetSelection selectedTarget = iterator.next();
            if (selectedTarget.pos().equals(pos)) {
                iterator.remove();
                Outliner.getInstance().remove(targetOutline(pos));
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                return;
            }
        }

        if (selectedTargets.size() >= MechanicalFluidGunPackets.TargetPacket.MAX_TARGETS) {
            CreateLang.builder()
                .translate("fluidlogistics.mechanical_fluid_gun.target_limit_reached", MechanicalFluidGunPackets.TargetPacket.MAX_TARGETS)
                .style(ChatFormatting.RED)
                .sendStatus(player);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        selectedTargets.add(new TargetSelection(pos.immutable(), getTargetFace(level, pos, event.getFace())));
        sendTargetStatus(player, level.getBlockState(pos));

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public static void leftClickingBlocksClearsTarget(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getLevel().isClientSide() || selectedTargets.isEmpty() || !isHoldingGun(event.getEntity())) {
            return;
        }
        Iterator<TargetSelection> iterator = selectedTargets.iterator();
        while (iterator.hasNext()) {
            TargetSelection target = iterator.next();
            if (target.pos().equals(event.getPos())) {
                iterator.remove();
                Outliner.getInstance().remove(targetOutline(event.getPos()));
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Player player = Minecraft.getInstance().player;
        if (player == null || !isHoldingGun(player)) {
            currentItem = null;
            clearTargets();
            return;
        }

        ItemStack held = player.getMainHandItem();
        if (held != currentItem) {
            currentItem = held;
            clearTargets();
            return;
        }

        drawTargetOutlines();
    }

    public static void flushTarget(BlockPos gunPos) {
        if (selectedTargets.isEmpty()) {
            return;
        }

        Level level = Minecraft.getInstance().level;
        List<MechanicalFluidGunPackets.TargetPacket.TargetEntry> packetTargets = new ArrayList<>(selectedTargets.size());
        int skippedOutOfRange = 0;
        for (TargetSelection selection : selectedTargets) {
            if (packetTargets.size() >= MechanicalFluidGunPackets.TargetPacket.MAX_TARGETS) {
                break;
            }
            BlockPos targetPos = selection.pos();
            if (level == null || !MechanicalFluidGunBlock.isTargetTagged(level, targetPos)) {
                continue;
            }
            if (!MechanicalFluidGunBlock.isTargetInRange(gunPos, targetPos)) {
                skippedOutOfRange++;
                continue;
            }
            packetTargets.add(new MechanicalFluidGunPackets.TargetPacket.TargetEntry(targetPos, selection.face()));
        }
        if (!packetTargets.isEmpty()) {
            PacketDistributor.sendToServer(
                MechanicalFluidGunPackets.TargetPacket.setTargets(gunPos, packetTargets));
        }
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            if (skippedOutOfRange > 0) {
                CreateLang.builder()
                    .translate("fluidlogistics.mechanical_fluid_gun.targets_out_of_range", skippedOutOfRange)
                    .style(ChatFormatting.RED)
                    .sendStatus(player);
            } else if (!packetTargets.isEmpty()) {
                CreateLang.builder()
                    .translate("fluidlogistics.mechanical_fluid_gun.target_summary", packetTargets.size())
                    .style(ChatFormatting.WHITE)
                    .sendStatus(player);
            }
        }
        clearTargets();
        currentItem = null;
    }

    private static boolean isHoldingGun(Player player) {
        return com.yision.fluidlogistics.registry.AllBlocks.MECHANICAL_FLUID_GUN.isIn(player.getMainHandItem());
    }

    private static Direction getTargetFace(Level level, BlockPos pos, Direction clickedFace) {
        Direction hatchSide = FluidHatchFluidHandlerForwarder.getExposedSide(level.getBlockState(pos));
        return hatchSide == null ? clickedFace : hatchSide;
    }

    private static void drawTargetOutlines() {
        if (selectedTargets.isEmpty()) {
            Outliner.getInstance().remove(TARGET_OUTLINE);
            return;
        }

        Iterator<TargetSelection> iterator = selectedTargets.iterator();
        while (iterator.hasNext()) {
            TargetSelection selectedTarget = iterator.next();
            Level level = Minecraft.getInstance().level;
            if (level == null || !MechanicalFluidGunBlock.isTargetTagged(level, selectedTarget.pos())) {
                Outliner.getInstance().remove(targetOutline(selectedTarget.pos()));
                iterator.remove();
                continue;
            }

            BlockPos pos = selectedTarget.pos();
            VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
            if (shape.isEmpty()) {
                Outliner.getInstance().remove(targetOutline(pos));
                continue;
            }

            Outliner.getInstance()
                .showAABB(targetOutline(pos), shape.bounds().move(pos))
                .colored(TARGET_COLOR)
                .lineWidth(1 / 16f);
        }
    }

    private static void clearTargets() {
        for (TargetSelection selectedTarget : selectedTargets) {
            Outliner.getInstance().remove(targetOutline(selectedTarget.pos()));
        }
        selectedTargets.clear();
        Outliner.getInstance().remove(TARGET_OUTLINE);
    }

    private static String targetOutline(BlockPos pos) {
        return TARGET_OUTLINE + "_" + pos.asLong();
    }

    public static void sendTargetStatus(Player player, BlockState state) {
        String key = MechanicalFluidGunBlock.targetsItemOn(state)
            ? "fluidlogistics.mechanical_fluid_gun.inject_item_target"
            : "fluidlogistics.mechanical_fluid_gun.inject_block_target";
        CreateLang.builder()
            .translate(key, CreateLang.blockName(state).style(ChatFormatting.WHITE))
            .color(TARGET_COLOR)
            .sendStatus(player);
    }
}
