package com.yision.fluidlogistics.util;

import java.util.List;

import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.yision.fluidlogistics.block.CopperBasin.CopperBasinBlockEntity;
import com.yision.fluidlogistics.config.FeatureToggle;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public final class BasinFluidPackageUnpacking {

	private BasinFluidPackageUnpacking() {
	}

	public static boolean isSupportedTarget(BlockState state, BlockEntity target) {
		if (!(target instanceof BasinBlockEntity)) {
			return false;
		}

		if (com.simibubi.create.AllBlocks.BASIN.has(state)) {
			return true;
		}

		if (com.yision.fluidlogistics.registry.AllBlocks.COPPER_BASIN.has(state)) {
			return FeatureToggle.isEnabled(FeatureToggle.COPPER_BASIN)
				&& target instanceof CopperBasinBlockEntity;
		}

		return false;
	}

	public static boolean unpackFluids(BasinBlockEntity basin, List<FluidStack> packageFluids, boolean simulate) {
		if (basin.inputTank == null) {
			return false;
		}

		IFluidHandler inputTank = basin.inputTank.getCapability();
		if (inputTank == null) {
			return false;
		}

		return simulate
			? FluidInsertionHelper.canAcceptAllBySnapshot(basin, inputTank, packageFluids)
			: FluidInsertionHelper.insertAllOrNothingBySnapshot(basin, inputTank, packageFluids);
	}
}
