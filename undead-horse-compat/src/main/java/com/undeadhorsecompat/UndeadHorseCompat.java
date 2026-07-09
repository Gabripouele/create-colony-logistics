package com.undeadhorsecompat;

import com.undeadhorsecompat.config.UHCConfig;
import com.undeadhorsecompat.horse.UndeadHorseTamingHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

@Mod(UndeadHorseCompat.MOD_ID)
public final class UndeadHorseCompat {
    public static final String MOD_ID = "undead_horse_compat";

    public UndeadHorseCompat(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, UHCConfig.SPEC);
        NeoForge.EVENT_BUS.addListener(UndeadHorseTamingHandler::onEntityInteract);
    }
}
