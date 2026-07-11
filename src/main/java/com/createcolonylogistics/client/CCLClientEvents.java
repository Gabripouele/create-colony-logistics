package com.createcolonylogistics.client;

import com.createcolonylogistics.registry.CCLItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;

public final class CCLClientEvents {
    private CCLClientEvents() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(CCLClientEvents::registerItemDecorations);
        NeoForge.EVENT_BUS.addListener(CCLClientEvents::onClientTick);
    }

    private static void registerItemDecorations(RegisterItemDecorationsEvent event) {
        event.register(CCLItems.SMART_COLONY_CLIPBOARD.get(), new SmartColonyClipboardDecorator());
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        SmartClipboardClient.onClientTick();
    }
}
