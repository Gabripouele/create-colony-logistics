package com.createcolonylogistics.clipboard;

import com.createcolonylogistics.registry.CCLDataComponents;
import net.minecraft.world.item.ItemStack;

public final class SmartClipboardFilterState {
    private SmartClipboardFilterState() {
    }

    public static boolean isImportantOnly(ItemStack clipboard) {
        return clipboard.getOrDefault(CCLDataComponents.IMPORTANT_ONLY.get(), false);
    }

    public static void setImportantOnly(ItemStack clipboard, boolean importantOnly) {
        clipboard.set(CCLDataComponents.IMPORTANT_ONLY.get(), importantOnly);
    }
}
