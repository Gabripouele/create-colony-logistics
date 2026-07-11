package com.createcolonylogistics.cache;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class WarehouseThresholdSnapshot {
    private final ResourceKey<Level> dimension;
    private final BlockPos warehousePos;
    private final long gameTimeBuilt;
    private final int containerCount;
    private final int slotCount;
    private final int totalCapacity;
    private final int totalItemCount;
    private final List<Entry> entries;

    WarehouseThresholdSnapshot(ResourceKey<Level> dimension, BlockPos warehousePos, long gameTimeBuilt,
                               int containerCount, int slotCount, int totalCapacity, int totalItemCount,
                               List<Entry> entries) {
        this.dimension = dimension;
        this.warehousePos = warehousePos.immutable();
        this.gameTimeBuilt = gameTimeBuilt;
        this.containerCount = containerCount;
        this.slotCount = slotCount;
        this.totalCapacity = totalCapacity;
        this.totalItemCount = totalItemCount;
        this.entries = List.copyOf(entries);
    }

    static Builder builder(ResourceKey<Level> dimension, BlockPos warehousePos, long gameTimeBuilt,
                           int containerCount, int slotCount) {
        return new Builder(dimension, warehousePos, gameTimeBuilt, containerCount, slotCount);
    }

    public boolean isValid(long gameTime, int containerCount, int slotCount, int ttlTicks) {
        return this.containerCount == containerCount
                && this.slotCount == slotCount
                && gameTime - gameTimeBuilt <= ttlTicks;
    }

    public boolean isRecentEnough(long gameTime, int ttlTicks, int graceTicks) {
        return gameTime - gameTimeBuilt <= (long) ttlTicks + graceTicks;
    }

    public int countMatching(Predicate<ItemStack> predicate) {
        int total = 0;
        for (Entry entry : entries) {
            if (predicate.test(entry.stack())) {
                total = saturatedAdd(total, entry.count());
            }
        }
        return total;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public BlockPos warehousePos() {
        return warehousePos;
    }

    public long gameTimeBuilt() {
        return gameTimeBuilt;
    }

    public int containerCount() {
        return containerCount;
    }

    public int slotCount() {
        return slotCount;
    }

    public int totalCapacity() {
        return totalCapacity;
    }

    public int totalItemCount() {
        return totalItemCount;
    }

    public List<Entry> entries() {
        return entries;
    }

    static int saturatedAdd(int left, int right) {
        long sum = (long) left + right;
        return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
    }

    public record Entry(ItemStack stack, int count) {
        public Entry {
            stack = stack.copy();
        }
    }

    static final class Builder {
        private final ResourceKey<Level> dimension;
        private final BlockPos warehousePos;
        private final long gameTimeBuilt;
        private final int containerCount;
        private final int slotCount;
        private final List<Entry> entries = new ArrayList<>();
        private int totalCapacity;
        private int totalItemCount;

        private Builder(ResourceKey<Level> dimension, BlockPos warehousePos, long gameTimeBuilt,
                        int containerCount, int slotCount) {
            this.dimension = dimension;
            this.warehousePos = warehousePos.immutable();
            this.gameTimeBuilt = gameTimeBuilt;
            this.containerCount = containerCount;
            this.slotCount = slotCount;
        }

        void addSlot(ItemStack stack, int effectiveCapacity) {
            if (effectiveCapacity <= 0) {
                return;
            }

            totalCapacity = saturatedAdd(totalCapacity, effectiveCapacity);
            if (stack == null || stack.isEmpty()) {
                return;
            }

            int count = stack.getCount();
            totalItemCount = saturatedAdd(totalItemCount, count);
            for (int i = 0; i < entries.size(); i++) {
                Entry entry = entries.get(i);
                if (ItemStack.isSameItemSameComponents(entry.stack(), stack)) {
                    entries.set(i, new Entry(entry.stack(), saturatedAdd(entry.count(), count)));
                    return;
                }
            }

            entries.add(new Entry(stack, count));
        }

        WarehouseThresholdSnapshot build() {
            return new WarehouseThresholdSnapshot(dimension, warehousePos, gameTimeBuilt, containerCount,
                    slotCount, totalCapacity, totalItemCount, entries);
        }
    }
}
