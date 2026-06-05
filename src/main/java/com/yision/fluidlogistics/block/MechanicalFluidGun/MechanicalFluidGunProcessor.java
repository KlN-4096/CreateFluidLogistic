package com.yision.fluidlogistics.block.MechanicalFluidGun;

import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.logistics.depot.DepotBehaviour;
import com.yision.fluidlogistics.block.Faucet.FaucetFilling;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

class MechanicalFluidGunProcessor {

	static final int TRANSFER_INTERVAL = 10;
	private static final int IDLE_RECHECK_INTERVAL = 20;

	private final MechanicalFluidGunBlockEntity be;

	MechanicalFluidGunProcessor(MechanicalFluidGunBlockEntity be) {
		this.be = be;
	}

	void tickServer() {
		MechanicalFluidGunTargets targets = be.getTargetsHelper();
		MechanicalFluidGunCycle cycle = be.getCycleHelper();
		MechanicalFluidGunVisuals visuals = be.getVisualsHelper();
		MechanicalFluidGunItemFilling itemFilling = be.getItemFillingHelper();
		MechanicalFluidGunBeltHandler beltHandler = be.getBeltHandlerHelper();

		if (be.getSpeed() == 0) {
			be.endWorkCycle();
			return;
		}

		if (itemFilling.isFilling()) {
			if (itemFilling.isFillingBelt()) return;
			if (itemFilling.getProcessingTicks() <= 0) {
				finishDepotItemFilling();
				return;
			}
			itemFilling.decrementTicks();
			return;
		}

		boolean sprayCompleted = visuals.tickTransientSpray(itemFilling.isFilling(), () -> {
			if (visuals.shouldAdvanceAfterSpray()) {
				advanceToProcessableTargetOrIdle(targets.getActiveTargetIndex());
			}
		});
		if (sprayCompleted) {
			be.notifyGunUpdate();
		}

		if (beltHandler.shouldWaitForBeltCallback()) {
			beltHandler.tickKeepAlive();
			cycle.tickCooldown();
			return;
		}

		if (cycle.tickCooldown()) {
			return;
		}

		if (targets.isEmpty()) {
			return;
		}

		tryInject();
	}

	private void tryInject() {
		MechanicalFluidGunTargets targets = be.getTargetsHelper();
		MechanicalFluidGunCycle cycle = be.getCycleHelper();

		if (!targets.hasValidTarget(be.getLevel(), be.gunPos())) {
			cycle.setTransferCooldown(MechanicalFluidGunCycle.getSpeedAdjustedInterval(IDLE_RECHECK_INTERVAL, Math.abs(be.getSpeed())));
			be.endWorkCycle();
			return;
		}

		IFluidHandler sourceHandler = be.sourceHandler();
		if (sourceHandler == null) {
			cycle.setTransferCooldown(MechanicalFluidGunCycle.getSpeedAdjustedInterval(IDLE_RECHECK_INTERVAL, Math.abs(be.getSpeed())));
			return;
		}

		int targetCount = targets.size();
		int startIndex = getFirstCandidateIndex();
		for (int step = 0; step < targetCount; step++) {
			int index = Math.floorMod(startIndex + step, targetCount);
			MechanicalFluidGunTargetConfig target = targets.get(index);
			BlockPos absTarget = target.absoluteFrom(be.gunPos());
			if (!targets.isTargetValid(be.getLevel(), be.gunPos(), absTarget)) continue;
			if (be.getBeltHandlerHelper().isBeltTarget(absTarget)) continue;

			BlockState targetState = be.getLevel().getBlockState(absTarget);
			if (!canProcess(sourceHandler, target, targetState, absTarget)) continue;

			if (!be.aimAtTarget(index)) {
				cycle.setTransferCooldown(1);
				return;
			}
			if (tryProcess(sourceHandler, target, absTarget)) {
				cycle.setTransferCooldown(MechanicalFluidGunCycle.getSpeedAdjustedInterval(TRANSFER_INTERVAL, Math.abs(be.getSpeed())));
				return;
			}
		}

		cycle.setTransferCooldown(MechanicalFluidGunCycle.getSpeedAdjustedInterval(IDLE_RECHECK_INTERVAL, Math.abs(be.getSpeed())));
		be.endWorkCycle();
	}

	private int getFirstCandidateIndex() {
		MechanicalFluidGunTargets targets = be.getTargetsHelper();
		MechanicalFluidGunCycle cycle = be.getCycleHelper();
		if (cycle.isActive() && targets.getActiveTargetIndex() >= 0 && targets.getActiveTargetIndex() < targets.size()) {
			return targets.getActiveTargetIndex();
		}
		return 0;
	}

	private boolean canProcess(IFluidHandler sourceHandler, MechanicalFluidGunTargetConfig target,
							   BlockState targetState, BlockPos absTarget) {
		if (!targetState.is(MechanicalFluidGunBlock.TARGETS)) return false;

		BlockEntity targetEntity = be.getLevel().getBlockEntity(absTarget);

		if (targetEntity != null && isDepot(targetEntity)) {
			ItemStack itemOnDepot = getItemOnDepot(targetEntity);
			return !itemOnDepot.isEmpty()
				&& FaucetFilling.canItemBeFilled(be.getLevel(), itemOnDepot)
				&& !MechanicalFluidGunFillOperations.findFillableFluidForItem(be, sourceHandler, itemOnDepot).isEmpty();
		}

		if (targetEntity != null && isBelt(targetEntity)) {
			return false;
		}

		if (targetState.is(Blocks.CAULDRON) || targetState.is(Blocks.WATER_CAULDRON)) {
			return !MechanicalFluidGunFillOperations.findFillableFluidForCauldron(be, sourceHandler, targetState).isEmpty();
		}

		if (targetEntity == null) return false;

		IFluidHandler targetHandler = MechanicalFluidGunFillOperations.getTargetFluidHandler(
			be.getLevel(), targetEntity.getBlockPos(), target.face());
		if (targetHandler != null) {
			return !MechanicalFluidGunFillOperations.findFillableFluidForContainer(be, sourceHandler, targetHandler, absTarget).isEmpty();
		}

		return MechanicalFluidGunFillOperations.canFuel(be, sourceHandler, targetState, absTarget);
	}

	private boolean tryProcess(IFluidHandler sourceHandler, MechanicalFluidGunTargetConfig target,
							   BlockPos absTarget) {
		Level level = be.getLevel();
		BlockEntity targetEntity = level.getBlockEntity(absTarget);
		BlockState targetState = level.getBlockState(absTarget);

		if (!targetState.is(MechanicalFluidGunBlock.TARGETS)) return false;

		if (targetEntity != null && isDepot(targetEntity)) {
			ItemStack itemOnDepot = getItemOnDepot(targetEntity);
			if (!itemOnDepot.isEmpty() && FaucetFilling.canItemBeFilled(level, itemOnDepot)) {
				FluidStack fillableFluid = MechanicalFluidGunFillOperations.findFillableFluidForItem(be, sourceHandler, itemOnDepot);
				if (!fillableFluid.isEmpty()) {
					return startDepotItemFilling(sourceHandler, itemOnDepot, fillableFluid);
				}
			}
			return false;
		}

		if (targetState.is(Blocks.CAULDRON) || targetState.is(Blocks.WATER_CAULDRON)) {
			FluidStack fillableFluid = MechanicalFluidGunFillOperations.findFillableFluidForCauldron(be, sourceHandler, targetState);
			if (!fillableFluid.isEmpty()) {
				MechanicalFluidGunVisuals visuals = be.getVisualsHelper();
				return MechanicalFluidGunFillOperations.tryFillCauldron(be, visuals, absTarget, targetState, fillableFluid);
			}
			return false;
		}

		if (targetEntity != null) {
			MechanicalFluidGunVisuals visuals = be.getVisualsHelper();
			if (MechanicalFluidGunFillOperations.tryFillContainerWithActiveTarget(be, visuals, target, absTarget)) {
				return true;
			}

			return MechanicalFluidGunFillOperations.tryFuel(be, visuals, sourceHandler, targetState, absTarget);
		}

		return false;
	}

	private boolean startDepotItemFilling(IFluidHandler sourceHandler, ItemStack item, FluidStack availableFluid) {
		return MechanicalFluidGunItemFilling.startFilling(
			be, sourceHandler, item, availableFluid,
			MechanicalFluidGunItemFilling.ProcessingTarget.DEPOT, null);
	}

	private void finishDepotItemFilling() {
		MechanicalFluidGunTargets targets = be.getTargetsHelper();
		MechanicalFluidGunItemFilling itemFilling = be.getItemFillingHelper();
		MechanicalFluidGunVisuals visuals = be.getVisualsHelper();
		MechanicalFluidGunCycle cycle = be.getCycleHelper();

		if (!itemFilling.isFillingDepot() || itemFilling.getProcessingItem().isEmpty() || itemFilling.getPendingFluid().isEmpty()) {
			itemFilling.clear();
			be.endWorkCycle();
			return;
		}

		BlockPos absTarget = targets.getAbsoluteTarget(be.gunPos());
		if (absTarget == null) {
			itemFilling.clear();
			be.endWorkCycle();
			return;
		}

		BlockEntity targetEntity = be.getLevel().getBlockEntity(absTarget);
		if (!isDepot(targetEntity)) {
			itemFilling.clear();
			be.endWorkCycle();
			return;
		}

		ItemStack currentItem = getItemOnDepot(targetEntity);
		if (!ItemStack.isSameItemSameComponents(currentItem, itemFilling.getProcessingItem()) || currentItem.getCount() < 1) {
			itemFilling.clear();
			be.endWorkCycle();
			return;
		}

		DepotBehaviour behaviour = DepotBehaviour.get(targetEntity, DepotBehaviour.TYPE);
		if (behaviour == null) {
			itemFilling.clear();
			be.endWorkCycle();
			return;
		}

		ItemStack result = FaucetFilling.fillItem(be.getLevel(), itemFilling.getPendingFluid().getAmount(),
			currentItem.copyWithCount(1), itemFilling.getPendingFluid().copy());
		if (result.isEmpty()) {
			itemFilling.clear();
			be.endWorkCycle();
			return;
		}

		IFluidHandler sourceHandler = be.sourceHandler();
		if (sourceHandler == null) {
			itemFilling.clear();
			be.endWorkCycle();
			return;
		}

		FluidStack drained = sourceHandler.drain(itemFilling.getPendingFluid().copy(), IFluidHandler.FluidAction.EXECUTE);
		if (drained.isEmpty() || drained.getAmount() < itemFilling.getPendingFluid().getAmount()) {
			itemFilling.clear();
			be.endWorkCycle();
			return;
		}

		ItemStack remaining = currentItem.copy();
		remaining.shrink(1);
		if (remaining.isEmpty()) {
			behaviour.setHeldItem(new TransportedItemStack(result));
		} else {
			behaviour.setHeldItem(new TransportedItemStack(remaining));
			net.minecraft.world.Containers.dropItemStack(be.getLevel(),
				absTarget.getX() + 0.5, absTarget.getY() + 0.75, absTarget.getZ() + 0.5, result);
		}

		targetEntity.setChanged();
		be.getLevel().sendBlockUpdated(absTarget, targetEntity.getBlockState(), targetEntity.getBlockState(), 3);
		be.getLevel().playSound(null, absTarget, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.5f, 1.0f + be.getLevel().random.nextFloat() * 0.2f);

		MechanicalFluidGunTargetConfig activeTarget = targets.getActiveTarget();
		Vec3 aimPoint = be.getTargetAimPoint(activeTarget);
		visuals.spawnServerSprayParticles(be.getLevel(), be.gunPos(), aimPoint);

		itemFilling.clear();
		cycle.setTransferCooldown(MechanicalFluidGunCycle.getSpeedAdjustedInterval(TRANSFER_INTERVAL, Math.abs(be.getSpeed())));
		be.notifyGunUpdate();
	}

	boolean advanceToProcessableTargetOrIdle(int startIndex) {
		int processableTarget = findNextProcessableTarget(startIndex);
		MechanicalFluidGunTargets targets = be.getTargetsHelper();
		MechanicalFluidGunCycle cycle = be.getCycleHelper();

		if (processableTarget == -1) {
			targets.resetActive();
			cycle.setActive(false);
			cycle.setTargetProgress(1);
		} else {
			if (targets.getActiveTargetIndex() != processableTarget || !cycle.isActive()) {
				cycle.setTargetProgress(0);
			}
			targets.setActiveTargetIndex(processableTarget);
			cycle.setActive(true);
			cycle.setTransferCooldown(0);
		}

		be.updateVisuals();
		return processableTarget != -1;
	}

	private int findNextProcessableTarget(int startIndex) {
		MechanicalFluidGunTargets targets = be.getTargetsHelper();
		IFluidHandler sourceHandler = be.sourceHandler();
		if (sourceHandler == null) return -1;

		int size = targets.size();
		if (size == 0) return -1;
		int start = Math.max(0, Math.min(startIndex, size - 1));

		for (int step = 0; step < size; step++) {
			int index = Math.floorMod(start + step, size);
			MechanicalFluidGunTargetConfig target = targets.get(index);
			BlockPos absTarget = target.absoluteFrom(be.gunPos());
			if (!targets.isTargetValid(be.getLevel(), be.gunPos(), absTarget)) continue;

			BlockState targetState = be.getLevel().getBlockState(absTarget);
			if (canProcess(sourceHandler, target, targetState, absTarget)) {
				return index;
			}
		}
		return -1;
	}

	boolean isDepot(BlockEntity entity) {
		return entity != null && DepotBehaviour.get(entity, DepotBehaviour.TYPE) != null;
	}

	ItemStack getItemOnDepot(BlockEntity depot) {
		DepotBehaviour behaviour = DepotBehaviour.get(depot, DepotBehaviour.TYPE);
		return behaviour == null ? ItemStack.EMPTY : behaviour.getHeldItemStack();
	}

	private boolean isBelt(BlockEntity entity) {
		return entity instanceof com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
	}
}
