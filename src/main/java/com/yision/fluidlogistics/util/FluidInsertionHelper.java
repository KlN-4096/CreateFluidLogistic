package com.yision.fluidlogistics.util;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

public final class FluidInsertionHelper {

	private FluidInsertionHelper() {
	}

	@Nullable
	private static FluidStack getSingleMergedFluid(List<FluidStack> fluids) {
		FluidStack merged = FluidStack.EMPTY;

		for (FluidStack fluid : fluids) {
			if (fluid.isEmpty()) {
				continue;
			}

			if (merged.isEmpty()) {
				merged = fluid.copy();
				continue;
			}

			if (!FluidStack.isSameFluidSameComponents(merged, fluid)) {
				return null;
			}

			merged.grow(fluid.getAmount());
		}

		return merged;
	}

	public static boolean canAcceptAll(@Nullable BlockEntity target, IFluidHandler handler, List<FluidStack> packageFluids) {
		FluidStack fluid = getSingleMergedFluid(packageFluids);
		if (fluid == null) {
			return false;
		}
		if (fluid.isEmpty()) {
			return true;
		}

		if (InfiniteFluidHandlerHelper.canAcceptInfinitely(handler, fluid)) {
			return true;
		}

		return handler.fill(fluid.copy(), FluidAction.SIMULATE) == fluid.getAmount();
	}

	public static boolean insertAllOrNothing(@Nullable BlockEntity target, IFluidHandler handler, List<FluidStack> packageFluids) {
		FluidStack fluid = getSingleMergedFluid(packageFluids);
		if (fluid == null) {
			return false;
		}
		if (fluid.isEmpty()) {
			return true;
		}

		if (!canAcceptAll(target, handler, packageFluids)) {
			return false;
		}

		int filled = handler.fill(fluid.copy(), FluidAction.EXECUTE);
		return filled == fluid.getAmount();
	}
}
