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

public record ClientboundSmartClipboardReportPacket(SmartClipboardReport report) implements CustomPacketPayload {
    public static final Type<ClientboundSmartClipboardReportPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateColonyLogistics.MOD_ID, "smart_clipboard_report")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundSmartClipboardReportPacket> STREAM_CODEC =
            StreamCodec.ofMember(ClientboundSmartClipboardReportPacket::encode, ClientboundSmartClipboardReportPacket::decode);

    private static ClientboundSmartClipboardReportPacket decode(RegistryFriendlyByteBuf buffer) {
        return new ClientboundSmartClipboardReportPacket(SmartClipboardReport.decode(buffer));
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        report.encode(buffer);
    }

    public static void handle(ClientboundSmartClipboardReportPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                SmartClipboardClientBridge.open(packet.report());
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
