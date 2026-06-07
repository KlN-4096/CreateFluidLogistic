package com.yision.fluidlogistics.client;

import com.simibubi.create.AllItems;
import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.block.MechanicalFluidGun.MechanicalFluidGunBlock;
import com.yision.fluidlogistics.block.MechanicalFluidGun.MechanicalFluidGunBlockEntity;
import com.yision.fluidlogistics.block.MechanicalFluidGun.MechanicalFluidGunTargetConfig;

import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@EventBusSubscriber(modid = FluidLogistics.MODID, value = Dist.CLIENT)
public final class MechanicalFluidGunWrenchTargetHandler {

	private static final int TARGET_COLOR = 0x7FCDE0;
	private static final float LINE_WIDTH = 1 / 16f;
	private static final String OUTLINE_PREFIX = "MFGWrenchTarget_";

	private static BlockPos hoveredGunPos;
	private static final Set<String> activeOutlines = new HashSet<>();

	private MechanicalFluidGunWrenchTargetHandler() {
	}

	@SubscribeEvent
	public static void tick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		Level level = mc.level;

		if (player == null || level == null || !AllItems.WRENCH.isIn(player.getMainHandItem())) {
			clear();
			return;
		}

		if (!(mc.hitResult instanceof BlockHitResult hit)) {
			clear();
			return;
		}

		BlockPos gunPos = hit.getBlockPos();
		if (!(level.getBlockEntity(gunPos) instanceof MechanicalFluidGunBlockEntity gun)) {
			clear();
			return;
		}

		renderTargets(level, gunPos, gun);
	}

	private static void renderTargets(Level level, BlockPos gunPos, MechanicalFluidGunBlockEntity gun) {
		if (!gunPos.equals(hoveredGunPos)) {
			clear();
			hoveredGunPos = gunPos.immutable();
		}

		List<MechanicalFluidGunTargetConfig> targets = gun.getTargets();
		Set<String> validKeys = new HashSet<>();

		for (MechanicalFluidGunTargetConfig target : targets) {
			BlockPos targetPos = target.absoluteFrom(gunPos);
			if (!MechanicalFluidGunBlock.isSelectableTarget(level, gunPos, targetPos)) {
				continue;
			}

			BlockState state = level.getBlockState(targetPos);
			VoxelShape shape = state.getShape(level, targetPos);
			if (shape.isEmpty()) {
				continue;
			}

			String key = outlineKey(gunPos, targetPos);
			validKeys.add(key);

			Outliner.getInstance()
				.showAABB(key, shape.bounds().move(targetPos))
				.colored(TARGET_COLOR)
				.lineWidth(LINE_WIDTH);
		}

		activeOutlines.removeIf(oldKey -> {
			if (!validKeys.contains(oldKey)) {
				Outliner.getInstance().remove(oldKey);
				return true;
			}
			return false;
		});

		activeOutlines.addAll(validKeys);
	}

	private static void clear() {
		for (String key : activeOutlines) {
			Outliner.getInstance().remove(key);
		}
		activeOutlines.clear();
		hoveredGunPos = null;
	}

	private static String outlineKey(BlockPos gunPos, BlockPos targetPos) {
		return OUTLINE_PREFIX + gunPos.asLong() + "_" + targetPos.asLong();
	}
}
