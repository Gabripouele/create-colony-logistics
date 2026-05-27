package com.createcolonylogistics;

import com.mojang.logging.LogUtils;
import com.createcolonylogistics.config.ColonyLogisticsConfig;
import com.createcolonylogistics.cache.ColonyStockCache;
import com.createcolonylogistics.network.CCLNetworking;
import com.createcolonylogistics.registry.CCLItems;
import com.createcolonylogistics.registry.CCLMenus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

@Mod(CreateColonyLogistics.MOD_ID)
public final class CreateColonyLogistics {
    public static final String MOD_ID = "create_colony_logistics";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CreateColonyLogistics(IEventBus modEventBus, ModContainer modContainer) {
        CCLItems.register(modEventBus);
        CCLMenus.register(modEventBus);
        modEventBus.addListener(CCLItems::addCreativeTabItems);
        modEventBus.addListener(CCLNetworking::registerPayloads);
        modContainer.registerConfig(ModConfig.Type.SERVER, ColonyLogisticsConfig.SPEC);
        NeoForge.EVENT_BUS.addListener(this::onServerPostTick);
    }

    private void onServerPostTick(ServerTickEvent.Post event) {
        ColonyStockCache.INSTANCE.logStatsIfNeeded(event.getServer().overworld().getGameTime());
    }
}
