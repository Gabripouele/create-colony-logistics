package com.createcolonylogistics.network;

import com.createcolonylogistics.CreateColonyLogistics;
import com.createcolonylogistics.clipboard.SmartClipboardFilterState;
import com.createcolonylogistics.clipboard.SmartClipboardScrollStorage;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ServerboundSmartClipboardFilterPacket(boolean importantOnly) implements CustomPacketPayload {
    public static final Type<ServerboundSmartClipboardFilterPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateColonyLogistics.MOD_ID, "smart_clipboard_filter")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundSmartClipboardFilterPacket> STREAM_CODEC =
            StreamCodec.ofMember(ServerboundSmartClipboardFilterPacket::encode, ServerboundSmartClipboardFilterPacket::decode);

    private static ServerboundSmartClipboardFilterPacket decode(RegistryFriendlyByteBuf buffer) {
        return new ServerboundSmartClipboardFilterPacket(buffer.readBoolean());
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(importantOnly);
    }

    public static void handle(ServerboundSmartClipboardFilterPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            SmartClipboardScrollStorage.findSmartClipboard(player).ifPresent(clipboard -> {
                SmartClipboardFilterState.setImportantOnly(clipboard, packet.importantOnly());
                player.getInventory().setChanged();
            });
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
