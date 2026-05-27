package com.createcolonylogistics.menu;

import com.createcolonylogistics.clipboard.SmartClipboardReport;
import com.createcolonylogistics.item.SmartColonyClipboardItem;
import com.createcolonylogistics.registry.CCLMenus;
import com.minecolonies.core.items.ItemResourceScroll;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

public class SmartClipboardMenu extends AbstractContainerMenu {
    public static final int SCROLL_SLOT_COUNT = 27;
    public static final int BUTTON_SAVE = 0;
    public static final int BUTTON_CANCEL = 1;

    private final SimpleContainer scrollContainer = new SimpleContainer(SCROLL_SLOT_COUNT);
    private final Inventory playerInventory;
    private final SmartClipboardReport report;
    private final int clipboardSlot;
    private boolean saved;

    public SmartClipboardMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, inventory, buffer.readVarInt(), SmartClipboardReport.decode(buffer));
    }

    public SmartClipboardMenu(int containerId, Inventory inventory, int clipboardSlot, SmartClipboardReport report) {
        super(CCLMenus.SMART_CLIPBOARD.get(), containerId);
        this.playerInventory = inventory;
        this.clipboardSlot = clipboardSlot;
        this.report = report;
        loadScrolls();
        addScrollSlots();
        addPlayerSlots();
    }

    public SmartClipboardReport report() {
        return report;
    }

    public ItemStack getScrollStack(int index) {
        return scrollContainer.getItem(index);
    }

    public int firstScrollSlotIndex() {
        return 0;
    }

    public int scrollSlotCount() {
        return SCROLL_SLOT_COUNT;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_SAVE) {
            saveScrolls();
            saved = true;
            player.closeContainer();
            return true;
        }
        if (id == BUTTON_CANCEL) {
            player.closeContainer();
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack source = slot.getItem();
        ItemStack copy = source.copy();
        if (index < SCROLL_SLOT_COUNT) {
            if (!moveItemStackTo(source, SCROLL_SLOT_COUNT, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (isResourceScroll(source)) {
            if (!moveItemStackTo(source, 0, SCROLL_SLOT_COUNT, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }

        if (source.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return clipboardStack().getItem() instanceof SmartColonyClipboardItem;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!saved && !player.level().isClientSide) {
            for (int i = 0; i < scrollContainer.getContainerSize(); i++) {
                ItemStack stack = scrollContainer.removeItemNoUpdate(i);
                if (!stack.isEmpty()) {
                    player.getInventory().placeItemBackInInventory(stack);
                }
            }
        }
    }

    private void addScrollSlots() {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(scrollContainer, row * 9 + col, 47 + col * 18, 228 + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return isResourceScroll(stack);
                    }

                    @Override
                    public int getMaxStackSize() {
                        return 1;
                    }
                });
            }
        }
    }

    private void addPlayerSlots() {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 47 + col * 18, 292 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 47 + col * 18, 350));
        }
    }

    private void loadScrolls() {
        NonNullList<ItemStack> stacks = NonNullList.withSize(SCROLL_SLOT_COUNT, ItemStack.EMPTY);
        clipboardStack().getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(stacks);
        for (int i = 0; i < SCROLL_SLOT_COUNT; i++) {
            ItemStack stack = stacks.get(i);
            if (isResourceScroll(stack)) {
                scrollContainer.setItem(i, stack.copyWithCount(1));
            }
        }
    }

    private void saveScrolls() {
        NonNullList<ItemStack> stacks = NonNullList.withSize(SCROLL_SLOT_COUNT, ItemStack.EMPTY);
        for (int i = 0; i < SCROLL_SLOT_COUNT; i++) {
            stacks.set(i, scrollContainer.getItem(i).copy());
        }
        clipboardStack().set(DataComponents.CONTAINER, ItemContainerContents.fromItems(stacks));
    }

    private ItemStack clipboardStack() {
        if (clipboardSlot >= 0 && clipboardSlot < playerInventory.getContainerSize()) {
            return playerInventory.getItem(clipboardSlot);
        }
        return playerInventory.player.getMainHandItem();
    }

    private static boolean isResourceScroll(ItemStack stack) {
        return stack.getItem() instanceof ItemResourceScroll;
    }
}
