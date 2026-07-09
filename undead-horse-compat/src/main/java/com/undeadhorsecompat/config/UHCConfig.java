package com.undeadhorsecompat.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class UHCConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_ZOMBIE_HORSE_TAMING = BUILDER
            .comment("Allows players to tame zombie horses by right-clicking them.")
            .define("enableZombieHorseTaming", true);

    public static final ModConfigSpec.BooleanValue ENABLE_SKELETON_HORSE_TAMING = BUILDER
            .comment("Allows players to tame skeleton horses by right-clicking them. Skeleton trap horses are ignored.")
            .define("enableSkeletonHorseTaming", true);

    public static final ModConfigSpec.BooleanValue ENABLE_ZOMBIE_HORSE_ARMOR = BUILDER
            .comment("Allows zombie horses to equip equestrian horse armor in their body armor slot.")
            .define("enableZombieHorseArmor", true);

    public static final ModConfigSpec.BooleanValue ENABLE_SKELETON_HORSE_ARMOR = BUILDER
            .comment("Allows skeleton horses to equip equestrian horse armor in their body armor slot.")
            .define("enableSkeletonHorseArmor", true);

    public static final ModConfigSpec.BooleanValue ENABLE_DONKEY_ARMOR = BUILDER
            .comment("Allows donkeys to equip equestrian horse armor. Disabled by default to preserve vanilla behavior.")
            .define("enableDonkeyArmor", false);

    public static final ModConfigSpec.BooleanValue ENABLE_MULE_ARMOR = BUILDER
            .comment("Allows mules to equip equestrian horse armor. Disabled by default to preserve vanilla behavior.")
            .define("enableMuleArmor", false);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private UHCConfig() {
    }
}
