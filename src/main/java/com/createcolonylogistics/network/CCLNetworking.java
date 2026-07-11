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
        registrar.playToClient(
                ClientboundSmartColonyMapOpenPacket.TYPE,
                ClientboundSmartColonyMapOpenPacket.STREAM_CODEC,
                ClientboundSmartColonyMapOpenPacket::handle
        );
        registrar.playToServer(
                ServerboundSmartClipboardScrollPacket.TYPE,
                ServerboundSmartClipboardScrollPacket.STREAM_CODEC,
                ServerboundSmartClipboardScrollPacket::handle
        );
        registrar.playToServer(
                ServerboundSmartClipboardColonyMapPacket.TYPE,
                ServerboundSmartClipboardColonyMapPacket.STREAM_CODEC,
                ServerboundSmartClipboardColonyMapPacket::handle
        );
        registrar.playToServer(
                ServerboundSmartClipboardFilterPacket.TYPE,
                ServerboundSmartClipboardFilterPacket.STREAM_CODEC,
                ServerboundSmartClipboardFilterPacket::handle
        );
        registrar.playToServer(
                ServerboundSmartClipboardCancelPacket.TYPE,
                ServerboundSmartClipboardCancelPacket.STREAM_CODEC,
                ServerboundSmartClipboardCancelPacket::handle
        );
    }
}
