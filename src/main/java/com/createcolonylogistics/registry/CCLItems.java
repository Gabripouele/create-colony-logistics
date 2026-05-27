package com.createcolonylogistics.registry;

import com.createcolonylogistics.CreateColonyLogistics;
import com.createcolonylogistics.item.SmartColonyClipboardItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CCLItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreateColonyLogistics.MOD_ID);

    public static final DeferredItem<SmartColonyClipboardItem> SMART_COLONY_CLIPBOARD = ITEMS.registerItem(
            "smart_colony_clipboard",
            SmartColonyClipboardItem::new,
            new Item.Properties().stacksTo(1)
    );

    private CCLItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }

    public static void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(SMART_COLONY_CLIPBOARD.get());
        }
    }
}
