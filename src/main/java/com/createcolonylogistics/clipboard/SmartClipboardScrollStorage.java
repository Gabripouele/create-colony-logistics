package com.createcolonylogistics.clipboard;

import com.createcolonylogistics.item.SmartColonyClipboardItem;
import com.minecolonies.api.items.component.BuildingId;
import com.minecolonies.api.items.component.ColonyId;
import com.minecolonies.api.items.component.WarehouseSnapshot;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SmartClipboardScrollStorage {
    public static final int SLOT_COUNT = 18;

    private static final ResourceLocation RESOURCE_SCROLL_ID = ResourceLocation.fromNamespaceAndPath("minecolonies", "resourcescroll");

    private SmartClipboardScrollStorage() {
    }

    public static List<ItemStack> read(ItemStack clipboard) {
        NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        clipboard.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
        List<ItemStack> result = new ArrayList<>(SLOT_COUNT);
        for (int i = 0; i < SLOT_COUNT; i++) {
            ItemStack stack = items.get(i);
            result.add(isResourceScroll(stack) ? sanitizedScrollStack(stack, ScrollLinkSnapshot.from(stack)) : ItemStack.EMPTY);
        }
        return result;
    }

    public static Optional<ItemStack> findSmartClipboard(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof SmartColonyClipboardItem) {
            return Optional.of(main);
        }
        ItemStack offhand = player.getOffhandItem();
        if (offhand.getItem() instanceof SmartColonyClipboardItem) {
            return Optional.of(offhand);
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof SmartColonyClipboardItem) {
                return Optional.of(stack);
            }
        }
        return Optional.empty();
    }

    public static MutationResult insertFirstResourceScroll(ServerPlayer player, ItemStack clipboard) {
        List<ItemStack> scrolls = read(clipboard);
        for (int i = 0; i < scrolls.size(); i++) {
            if (scrolls.get(i).isEmpty()) {
                return insertResourceScroll(player, clipboard, i, ScrollLinkSnapshot.EMPTY);
            }
        }
        return MutationResult.rejected(-1, "storage full");
    }

    public static MutationResult insertResourceScroll(ServerPlayer player, ItemStack clipboard, int slot, ScrollLinkSnapshot scrollSnapshot) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            return MutationResult.rejected(slot, "invalid slot");
        }
        List<ItemStack> scrolls = read(clipboard);
        if (!scrolls.get(slot).isEmpty()) {
            return MutationResult.rejected(slot, "requested slot occupied");
        }

        ItemStack matchedStack = ItemStack.EMPTY;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (matchesSnapshot(stack, scrollSnapshot)) {
                matchedStack = stack;
                break;
            }
        }

        if (matchedStack.isEmpty()) {
            return MutationResult.rejected(slot, "matching Resource Scroll not found");
        }

        // Store only the MineColonies link components needed by the scroll UI. Copying arbitrary client
        // ItemStack components into player data can introduce components with no network codec.
        ItemStack stored = sanitizedScrollStack(matchedStack, scrollSnapshot);
        matchedStack.shrink(1);
        scrolls.set(slot, stored);
        write(clipboard, scrolls);
        player.getInventory().setChanged();
        return MutationResult.inserted(slot, ScrollLinkSnapshot.from(stored));
    }

    private static boolean matchesSnapshot(ItemStack stack, ScrollLinkSnapshot snapshot) {
        if (!isResourceScroll(stack)) {
            return false;
        }
        ScrollLinkSnapshot safe = snapshot == null ? ScrollLinkSnapshot.EMPTY : snapshot;
        ScrollLinkSnapshot actual = ScrollLinkSnapshot.from(stack);
        if (safe.colonyId().hasColonyId() && !safe.colonyId().equals(actual.colonyId())) {
            return false;
        }
        if (safe.buildingId().hasId() && !safe.buildingId().equals(actual.buildingId())) {
            return false;
        }
        boolean hasWarehouse = !safe.warehouseSnapshot().hash().isEmpty() || !safe.warehouseSnapshot().snapshot().isEmpty();
        return !hasWarehouse || safe.warehouseSnapshot().equals(actual.warehouseSnapshot());
    }

    private static ItemStack sanitizedScrollStack(ItemStack serverStack, ScrollLinkSnapshot snapshot) {
        ItemStack stored = new ItemStack(serverStack.getItem());
        ScrollLinkSnapshot safe = snapshot == null ? ScrollLinkSnapshot.EMPTY : snapshot;
        ScrollLinkSnapshot effective = safe.hasLink() ? safe : ScrollLinkSnapshot.from(serverStack);
        if (effective.colonyId().hasColonyId()) {
            effective.colonyId().writeToItemStack(stored);
        }
        if (effective.buildingId().hasId()) {
            effective.buildingId().writeToItemStack(stored);
        }
        if (!effective.warehouseSnapshot().hash().isEmpty() || !effective.warehouseSnapshot().snapshot().isEmpty()) {
            effective.warehouseSnapshot().writeToItemStack(stored);
        }
        return stored;
    }

    public static MutationResult removeResourceScroll(ServerPlayer player, ItemStack clipboard, int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            return MutationResult.rejected(slot, "invalid slot");
        }
        List<ItemStack> scrolls = read(clipboard);
        ItemStack stored = scrolls.get(slot);
        if (!isResourceScroll(stored)) {
            return MutationResult.rejected(slot, "slot empty");
        }
        ItemStack returning = stored.copyWithCount(1);
        if (!player.getInventory().add(returning)) {
            return MutationResult.rejected(slot, "inventory full");
        }
        scrolls.set(slot, ItemStack.EMPTY);
        write(clipboard, scrolls);
        player.getInventory().setChanged();
        return MutationResult.removed(slot);
    }

    public static boolean isResourceScroll(ItemStack stack) {
        return !stack.isEmpty() && RESOURCE_SCROLL_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    private static void write(ItemStack clipboard, List<ItemStack> scrolls) {
        NonNullList<ItemStack> contents = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        for (int i = 0; i < SLOT_COUNT && i < scrolls.size(); i++) {
            ItemStack stack = scrolls.get(i);
            contents.set(i, isResourceScroll(stack) ? sanitizedScrollStack(stack, ScrollLinkSnapshot.from(stack)) : ItemStack.EMPTY);
        }
        clipboard.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
    }

    public record ScrollLinkSnapshot(ColonyId colonyId, BuildingId buildingId, WarehouseSnapshot warehouseSnapshot) {
        public static final ScrollLinkSnapshot EMPTY = new ScrollLinkSnapshot(ColonyId.EMPTY, BuildingId.EMPTY, WarehouseSnapshot.EMPTY);

        public static ScrollLinkSnapshot from(ItemStack stack) {
            return new ScrollLinkSnapshot(
                    ColonyId.readFromItemStack(stack),
                    BuildingId.readFromItemStack(stack),
                    WarehouseSnapshot.readFromItemStack(stack)
            );
        }

        public boolean hasLink() {
            return colonyId.hasColonyId() || buildingId.hasId() || !warehouseSnapshot.hash().isEmpty() || !warehouseSnapshot.snapshot().isEmpty();
        }
    }

    public record MutationResult(boolean changed, int requestedSlot, int actualSlot,
                                 String rejectedReason, ScrollLinkSnapshot matchedSnapshot) {
        public static MutationResult inserted(int slot, ScrollLinkSnapshot matchedSnapshot) {
            return new MutationResult(true, slot, slot, "", matchedSnapshot);
        }

        public static MutationResult removed(int slot) {
            return new MutationResult(true, slot, slot, "", ScrollLinkSnapshot.EMPTY);
        }

        public static MutationResult rejected(int requestedSlot, String reason) {
            return new MutationResult(false, requestedSlot, -1, reason, ScrollLinkSnapshot.EMPTY);
        }
    }
}
