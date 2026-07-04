package com.createcolonylogistics.cache;

import com.createcolonylogistics.CreateColonyLogistics;
import com.createcolonylogistics.config.ColonyLogisticsConfig;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.tileentities.AbstractTileEntityRack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class WarehouseStockpileSwitchCache {
    private static final int STALE_GRACE_TICKS = 20;
    private static final long WARNING_INTERVAL_MILLIS = 60_000L;

    public static final WarehouseStockpileSwitchCache INSTANCE = new WarehouseStockpileSwitchCache();

    private final Map<WarehouseKey, WarehouseThresholdSnapshot> cache = new HashMap<>();
    private long lastStatsLogGameTime;
    private long lastWarningMillis;
    private long snapshotsBuilt;
    private long cacheHits;
    private long cacheMisses;
    private long fallbackOff;
    private long targetsHandled;

    private WarehouseStockpileSwitchCache() {
    }

    public Optional<WarehouseThresholdSnapshot> getSnapshot(ServerLevel level, BlockEntity warehouse) {
        if (!ColonyLogisticsConfig.ENABLE_WAREHOUSE_STOCKPILE_SWITCH_ADAPTER.get()) {
            return Optional.empty();
        }

        WarehouseKey key = new WarehouseKey(level.dimension(), warehouse.getBlockPos());
        long gameTime = level.getGameTime();
        int ttlTicks = ColonyLogisticsConfig.WAREHOUSE_STOCKPILE_CACHE_TTL_TICKS.get();
        WarehouseThresholdSnapshot cached = cache.get(key);

        try {
            WarehouseInventoryView view = collectInventories(level, warehouse);
            if (cached != null && cached.isValid(gameTime, view.containerCount(), view.slotCount(), ttlTicks)) {
                cacheHits++;
                return Optional.of(cached);
            }

            cacheMisses++;
            WarehouseThresholdSnapshot snapshot = buildSnapshot(level, warehouse, view);
            snapshotsBuilt++;
            cache.put(key, snapshot);
            return Optional.of(snapshot);
        } catch (RuntimeException exception) {
            if (cached != null && cached.isRecentEnough(gameTime, ttlTicks, STALE_GRACE_TICKS)) {
                cacheHits++;
                debug("Using recent warehouse stockpile snapshot after refresh failure", exception);
                return Optional.of(cached);
            }

            recordFallbackOff(exception);
            return Optional.empty();
        }
    }

    public void recordTargetHandled() {
        targetsHandled++;
    }

    public void recordFallbackOff(RuntimeException exception) {
        fallbackOff++;
        debug("Warehouse stockpile adapter falling back to safe off state", exception);
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
        CreateColonyLogistics.LOGGER.info(
                "Warehouse stockpile adapter: {} live snapshots, {} targets handled, {} snapshots built, {} cache hits, {} cache misses, {} safe-off fallbacks",
                cache.size(),
                targetsHandled,
                snapshotsBuilt,
                cacheHits,
                cacheMisses,
                fallbackOff);
    }

    private WarehouseThresholdSnapshot buildSnapshot(ServerLevel level, BlockEntity warehouse,
                                                     WarehouseInventoryView view) {
        WarehouseThresholdSnapshot.Builder builder = WarehouseThresholdSnapshot.builder(level.dimension(),
                warehouse.getBlockPos(), level.getGameTime(), view.containerCount(), view.slotCount());

        for (IItemHandlerModifiable inventory : view.inventories()) {
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                int effectiveCapacity = effectiveCapacity(stack, inventory.getSlotLimit(slot));
                builder.addSlot(stack, effectiveCapacity);
            }
        }

        return builder.build();
    }

    private WarehouseInventoryView collectInventories(ServerLevel level, BlockEntity warehouse) {
        IBuilding building = buildingFrom(warehouse);
        if (building == null) {
            throw new IllegalStateException("Warehouse building is unavailable");
        }

        List<BlockPos> containers = building.getContainers();
        List<IItemHandlerModifiable> inventories = new ArrayList<>();
        BlockPos warehousePos = warehouse.getBlockPos();
        int slotCount = 0;

        for (BlockPos containerPos : containers) {
            if (containerPos == null || containerPos.equals(warehousePos) || !level.isLoaded(containerPos)) {
                continue;
            }

            BlockEntity blockEntity = level.getBlockEntity(containerPos);
            if (!(blockEntity instanceof AbstractTileEntityRack rack)) {
                continue;
            }

            IItemHandlerModifiable inventory = rack.getInventory();
            inventories.add(inventory);
            slotCount = WarehouseThresholdSnapshot.saturatedAdd(slotCount, inventory.getSlots());
        }

        if (!(warehouse instanceof AbstractTileEntityRack warehouseRack)) {
            throw new IllegalStateException("Warehouse tile does not expose rack inventory");
        }

        IItemHandlerModifiable warehouseInventory = warehouseRack.getInventory();
        inventories.add(warehouseInventory);
        slotCount = WarehouseThresholdSnapshot.saturatedAdd(slotCount, warehouseInventory.getSlots());

        return new WarehouseInventoryView(containers.size(), slotCount, List.copyOf(inventories));
    }

    private IBuilding buildingFrom(BlockEntity warehouse) {
        try {
            Object building = warehouse.getClass().getMethod("getBuilding").invoke(warehouse);
            return building instanceof IBuilding mineColoniesBuilding ? mineColoniesBuilding : null;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to resolve MineColonies warehouse building", exception);
        }
    }

    private int effectiveCapacity(ItemStack stack, int slotLimit) {
        if (slotLimit <= 0) {
            return 0;
        }

        int stackLimit = stack == null || stack.isEmpty()
                ? 64
                : stack.getOrDefault(DataComponents.MAX_STACK_SIZE, 64);
        return Math.min(stackLimit, slotLimit);
    }

    private void debug(String message, RuntimeException exception) {
        if (!ColonyLogisticsConfig.DEBUG_LOGGING.get()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastWarningMillis < WARNING_INTERVAL_MILLIS) {
            return;
        }

        lastWarningMillis = now;
        CreateColonyLogistics.LOGGER.debug(message, exception);
    }

    private record WarehouseKey(ResourceKey<Level> dimension, BlockPos warehousePos) {
        private WarehouseKey {
            warehousePos = warehousePos.immutable();
        }
    }

    private record WarehouseInventoryView(int containerCount, int slotCount,
                                          List<IItemHandlerModifiable> inventories) {
    }
}
