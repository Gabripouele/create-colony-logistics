package com.createcolonylogistics.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ColonyLogisticsConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_MINECOLONIES_SUMMARY_CACHE = BUILDER
            .comment("Caches read-only Create InventorySummary builds for MineColonies CombinedItemHandler targets.")
            .define("enableMineColoniesSummaryCache", true);

    public static final ModConfigSpec.IntValue CACHE_TTL_TICKS = BUILDER
            .comment("How long a cached MineColonies stock summary may be reused for display/monitoring reads.")
            .defineInRange("cacheTtlTicks", 40, 1, 20 * 60 * 10);

    public static final ModConfigSpec.BooleanValue ENABLE_WAREHOUSE_STOCKPILE_SWITCH_ADAPTER = BUILDER
            .comment("Uses a MineColonies-aware cached warehouse snapshot when Create Stockpile Switches target Warehouse hut blocks.")
            .define("enableWarehouseStockpileSwitchAdapter", true);

    public static final ModConfigSpec.IntValue WAREHOUSE_STOCKPILE_CACHE_TTL_TICKS = BUILDER
            .comment("How long a cached MineColonies Warehouse snapshot may be reused by Create Stockpile Switch adapters.")
            .defineInRange("warehouseStockpileCacheTtlTicks", 40, 1, 20 * 60 * 10);

    public static final ModConfigSpec.IntValue SMART_CLIPBOARD_PRODUCTION_CACHE_TTL_TICKS = BUILDER
            .comment("How long Smart Clipboard global production fallback data may be reused between report opens.")
            .defineInRange("smartClipboardProductionCacheTtlTicks", 100, 1, 20 * 60 * 10);

    public static final ModConfigSpec.BooleanValue DEBUG_LOGGING = BUILDER
            .comment("Enables extra diagnostics for cache decisions.")
            .define("debugLogging", false);

    public static final ModConfigSpec.IntValue LOG_CACHE_STATS_INTERVAL_TICKS = BUILDER
            .comment("Interval for cache hit/miss logging when debugLogging is true. Set to 0 to disable periodic stats.")
            .defineInRange("logCacheStatsIntervalTicks", 1200, 0, 20 * 60 * 60);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ColonyLogisticsConfig() {
    }
}
