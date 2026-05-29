package com.createcolonylogistics.client;

import com.createcolonylogistics.registry.CCLItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;

public final class CCLClientEvents {
    private CCLClientEvents() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(CCLClientEvents::registerItemDecorations);
    }

    private static void registerItemDecorations(RegisterItemDecorationsEvent event) {
        event.register(CCLItems.SMART_COLONY_CLIPBOARD.get(), new SmartColonyClipboardDecorator());
    }
}
