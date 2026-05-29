package com.createcolonylogistics.network;

import com.createcolonylogistics.CreateColonyLogistics;
import com.createcolonylogistics.clipboard.SmartClipboardReport;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ClientboundSmartClipboardReportPacket(
        SmartClipboardReport report,
        String confirmedCancelledRequestToken,
        boolean cancelAccepted
) implements CustomPacketPayload {
    public static final Type<ClientboundSmartClipboardReportPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateColonyLogistics.MOD_ID, "smart_clipboard_report")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundSmartClipboardReportPacket> STREAM_CODEC =
            StreamCodec.ofMember(ClientboundSmartClipboardReportPacket::encode, ClientboundSmartClipboardReportPacket::decode);

    public ClientboundSmartClipboardReportPacket(SmartClipboardReport report) {
        this(report, "", false);
    }

    private static ClientboundSmartClipboardReportPacket decode(RegistryFriendlyByteBuf buffer) {
        return new ClientboundSmartClipboardReportPacket(SmartClipboardReport.decode(buffer), buffer.readUtf(), buffer.readBoolean());
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        report.encode(buffer);
        buffer.writeUtf(confirmedCancelledRequestToken == null ? "" : confirmedCancelledRequestToken);
        buffer.writeBoolean(cancelAccepted);
    }

    public static void handle(ClientboundSmartClipboardReportPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                if (packet.confirmedCancelledRequestToken() == null || packet.confirmedCancelledRequestToken().isBlank()) {
                    SmartClipboardClientBridge.open(packet.report());
                } else {
                    SmartClipboardClientBridge.applyCancelResult(packet.report(), packet.confirmedCancelledRequestToken(), packet.cancelAccepted());
                }
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
