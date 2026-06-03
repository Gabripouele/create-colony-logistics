package com.createcolonylogistics.clipboard;

import com.createcolonylogistics.registry.CCLDataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public final class SmartClipboardColonyMapStorage {
    private static final ResourceLocation COLONY_MAP_ID = ResourceLocation.fromNamespaceAndPath("minecolonies", "colonymap");

    private SmartClipboardColonyMapStorage() {
    }

    public static ItemStack read(ItemStack clipboard) {
        ItemStack stored = clipboard.getOrDefault(CCLDataComponents.COLONY_MAP.get(), ItemStack.EMPTY);
        return isColonyMap(stored) ? stored.copy() : ItemStack.EMPTY;
    }

    public static MutationResult insertFirstColonyMap(ServerPlayer player, ItemStack clipboard) {
        if (!read(clipboard).isEmpty()) {
            return MutationResult.rejected("Smart Colony Clipboard already contains a Smart Colony Map.");
        }

        Optional<Integer> slot = firstInventoryColonyMapSlot(player);
        if (slot.isEmpty()) {
            return MutationResult.rejected("No colony map can be added.");
        }

        ItemStack inventoryStack = player.getInventory().getItem(slot.get());
        ItemStack stored = inventoryStack.copyWithCount(1);
        inventoryStack.shrink(1);
        write(clipboard, stored);
        player.getInventory().setChanged();
        return MutationResult.changed("Smart Colony Map added to Smart Colony Clipboard.", stored);
    }

    public static MutationResult removeColonyMap(ServerPlayer player, ItemStack clipboard) {
        ItemStack stored = read(clipboard);
        if (stored.isEmpty()) {
            return MutationResult.rejected("No Smart Colony Map is stored.");
        }

        ItemStack returning = stored.copy();
        if (!player.getInventory().add(returning)) {
            return MutationResult.rejected("Inventory full.");
        }

        clear(clipboard);
        player.getInventory().setChanged();
        return MutationResult.changed("Smart Colony Map removed from Smart Colony Clipboard.", ItemStack.EMPTY);
    }

    public static MutationResult validateOpen(ItemStack clipboard) {
        ItemStack stored = read(clipboard);
        if (stored.isEmpty()) {
            return MutationResult.rejected(Component.translatable("com.minecolonies.core.item.colonymap.needcolony").getString());
        }
        return MutationResult.changed("", stored);
    }

    public static boolean isColonyMap(ItemStack stack) {
        return !stack.isEmpty() && COLONY_MAP_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    private static Optional<Integer> firstInventoryColonyMapSlot(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (isColonyMap(player.getInventory().getItem(i))) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    private static void write(ItemStack clipboard, ItemStack colonyMap) {
        if (isColonyMap(colonyMap)) {
            clipboard.set(CCLDataComponents.COLONY_MAP.get(), colonyMap.copy());
        } else {
            clear(clipboard);
        }
    }

    private static void clear(ItemStack clipboard) {
        clipboard.remove(CCLDataComponents.COLONY_MAP.get());
    }

    public record MutationResult(boolean changed, String message, ItemStack colonyMap) {
        public static MutationResult changed(String message, ItemStack colonyMap) {
            return new MutationResult(true, message, colonyMap == null ? ItemStack.EMPTY : colonyMap.copy());
        }

        public static MutationResult rejected(String message) {
            return new MutationResult(false, message == null ? "" : message, ItemStack.EMPTY);
        }
    }
}
