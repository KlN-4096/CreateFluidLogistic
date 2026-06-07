package com.yision.fluidlogistics.config;

import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.common.conditions.ICondition;
import org.jetbrains.annotations.NotNull;

public record FluidHatchAdvertisedCondition() implements ICondition {
    public static final MapCodec<FluidHatchAdvertisedCondition> CODEC =
            MapCodec.unit(new FluidHatchAdvertisedCondition());

    @Override
    public boolean test(@NotNull IContext context) {
        return FeatureToggle.isFluidHatchAdvertised();
    }

    @Override
    public @NotNull MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
