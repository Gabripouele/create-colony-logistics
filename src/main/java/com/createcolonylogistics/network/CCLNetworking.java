package com.createcolonylogistics.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class CCLNetworking {
    private CCLNetworking() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
                ClientboundSmartClipboardReportPacket.TYPE,
                ClientboundSmartClipboardReportPacket.STREAM_CODEC,
                ClientboundSmartClipboardReportPacket::handle
        );
        // Release safety: debug-only payloads are intentionally not registered.
        registrar.playToServer(
                ServerboundSmartClipboardScrollPacket.TYPE,
                ServerboundSmartClipboardScrollPacket.STREAM_CODEC,
                ServerboundSmartClipboardScrollPacket::handle
        );
    }
}
