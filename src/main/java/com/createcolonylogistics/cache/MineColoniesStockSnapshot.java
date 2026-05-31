package com.createcolonylogistics.cache;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class MineColoniesStockSnapshot {
    private final Map<Item, List<Entry>> entriesByItem;

    private MineColoniesStockSnapshot(Map<Item, List<Entry>> entriesByItem) {
        this.entriesByItem = entriesByItem;
    }

    public static MineColoniesStockSnapshot from(InventorySummary summary) {
        Map<Item, List<Entry>> grouped = new IdentityHashMap<>();
        if (summary == null) {
            return new MineColoniesStockSnapshot(Map.of());
        }

        for (BigItemStack stack : summary.getStacks()) {
            if (stack == null || stack.stack == null || stack.stack.isEmpty() || stack.count == 0) {
                continue;
            }

            ItemStack ownedStack = stack.stack.copy();
            grouped.computeIfAbsent(ownedStack.getItem(), ignored -> new ArrayList<>())
                    .add(new Entry(ownedStack, stack.count));
        }

        Map<Item, List<Entry>> immutable = new IdentityHashMap<>();
        for (Map.Entry<Item, List<Entry>> entry : grouped.entrySet()) {
            immutable.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return new MineColoniesStockSnapshot(Collections.unmodifiableMap(immutable));
    }

    public int getCountOf(ItemStack filter) {
        if (filter == null || filter.isEmpty()) {
            return 0;
        }

        List<Entry> entries = entriesByItem.get(filter.getItem());
        if (entries == null) {
            return 0;
        }

        for (Entry entry : entries) {
            if (ItemStack.isSameItemSameComponents(entry.stack(), filter)) {
                return entry.count();
            }
        }
        return 0;
    }

    private record Entry(ItemStack stack, int count) {
    }
}
