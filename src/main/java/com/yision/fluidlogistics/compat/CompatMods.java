package com.yision.fluidlogistics.compat;

import net.neoforged.fml.ModList;

public final class CompatMods {
    public static final String CREATE_ENCHANTMENT_INDUSTRY = "create_enchantment_industry";

    private CompatMods() {
    }

    public static boolean createEnchantmentIndustryLoaded() {
        return ModList.get().isLoaded(CREATE_ENCHANTMENT_INDUSTRY);
    }
}
