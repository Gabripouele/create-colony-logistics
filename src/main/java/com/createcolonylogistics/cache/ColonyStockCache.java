package com.createcolonylogistics.cache;

import com.minecolonies.api.inventory.api.CombinedItemHandler;
import com.createcolonylogistics.CreateColonyLogistics;
import com.createcolonylogistics.config.ColonyLogisticsConfig;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.items.IItemHandler;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class ColonyStockCache {
    public static final ColonyStockCache INSTANCE = new ColonyStockCache();

    private final ReferenceQueue<IItemHandler> staleHandlers = new ReferenceQueue<>();
    private final Map<WeakIdentityHandlerKey, CachedSummary> cache = new HashMap<>();
    private long lastStatsLogGameTime;

    private ColonyStockCache() {
    }

    public Optional<InventorySummary> getIfFresh(IItemHandler handler, ServerLevel level) {
        if (!isEnabledFor(handler)) {
            return Optional.empty();
        }

        try {
            pruneStaleHandlers();
            int slotCount = handler.getSlots();
            CachedSummary cached = cache.get(WeakIdentityHandlerKey.lookup(handler));
            if (cached == null) {
                return Optional.empty();
            }

            if (!cached.isValid(level.getGameTime(), slotCount, ColonyLogisticsConfig.CACHE_TTL_TICKS.get())) {
                cached.recordMiss();
                return Optional.empty();
            }

            cached.recordHit();
            return Optional.of(CreateInventorySummaryAdapter.safeCopy(cached.summary()));
        } catch (RuntimeException exception) {
            debug("Cache lookup failed, falling back to Create summary scan", exception);
            return Optional.empty();
        }
    }

    public void store(IItemHandler handler, ServerLevel level, InventorySummary summary) {
        if (!isEnabledFor(handler)) {
            return;
        }

        try {
            pruneStaleHandlers();
            InventorySummary safeCopy = CreateInventorySummaryAdapter.safeCopy(summary);
            int slotCount = handler.getSlots();
            WeakIdentityHandlerKey key = WeakIdentityHandlerKey.lookup(handler);
            CachedSummary existing = cache.get(key);
            if (existing == null) {
                cache.put(WeakIdentityHandlerKey.stored(handler, staleHandlers), new CachedSummary(safeCopy, level.getGameTime(), slotCount));
            } else {
                existing.replace(safeCopy, level.getGameTime(), slotCount);
            }
        } catch (RuntimeException exception) {
            debug("Cache store failed; Create result will be used without caching", exception);
        }
    }

    public boolean isEnabledFor(IItemHandler handler) {
        return ColonyLogisticsConfig.ENABLE_MINECOLONIES_SUMMARY_CACHE.get()
                && handler instanceof CombinedItemHandler;
    }

    public void logStatsIfNeeded(long gameTime) {
        if (!ColonyLogisticsConfig.DEBUG_LOGGING.get()) {
            return;
        }

        int interval = ColonyLogisticsConfig.LOG_CACHE_STATS_INTERVAL_TICKS.get();
        if (interval <= 0 || gameTime - lastStatsLogGameTime < interval) {
            return;
        }

        lastStatsLogGameTime = gameTime;
        long hits = 0;
        long misses = 0;
        pruneStaleHandlers();
        for (CachedSummary summary : cache.values()) {
            hits += summary.hitCount();
            misses += summary.missCount();
        }
        CreateColonyLogistics.LOGGER.info("MineColonies stock summary cache: {} live handlers, {} hits, {} stale misses", cache.size(), hits, misses);
    }

    private void pruneStaleHandlers() {
        WeakIdentityHandlerKey key;
        while ((key = (WeakIdentityHandlerKey) staleHandlers.poll()) != null) {
            cache.remove(key);
        }
    }

    private static void debug(String message, Throwable throwable) {
        if (ColonyLogisticsConfig.DEBUG_LOGGING.get()) {
            CreateColonyLogistics.LOGGER.debug(message, throwable);
        }
    }

    private static final class WeakIdentityHandlerKey extends WeakReference<IItemHandler> {
        private final int identityHash;

        private WeakIdentityHandlerKey(IItemHandler handler, ReferenceQueue<IItemHandler> queue) {
            super(handler, queue);
            this.identityHash = System.identityHashCode(handler);
        }

        private static WeakIdentityHandlerKey stored(IItemHandler handler, ReferenceQueue<IItemHandler> queue) {
            return new WeakIdentityHandlerKey(handler, queue);
        }

        private static WeakIdentityHandlerKey lookup(IItemHandler handler) {
            return new WeakIdentityHandlerKey(handler, null);
        }

        @Override
        public int hashCode() {
            return identityHash;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof WeakIdentityHandlerKey key)) {
                return false;
            }
            IItemHandler handler = get();
            return handler != null && handler == key.get();
        }
    }
}
