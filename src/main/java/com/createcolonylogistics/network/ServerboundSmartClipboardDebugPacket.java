package com.createcolonylogistics.network;

import com.createcolonylogistics.CreateColonyLogistics;
import com.createcolonylogistics.clipboard.ColonyContextResolver;
import com.createcolonylogistics.clipboard.RequestAnalysisService;
import com.minecolonies.api.colony.IColony;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;

public record ServerboundSmartClipboardDebugPacket(
        String requestToken,
        String rowItemName,
        String quantityDisplay,
        String requesterDisplay,
        Optional<String> workerDisplay,
        boolean minimumStockRequest,
        boolean expandable,
        String expandableReason,
        int clientDependencyCount
) implements CustomPacketPayload {
    public static final Type<ServerboundSmartClipboardDebugPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateColonyLogistics.MOD_ID, "smart_clipboard_debug")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundSmartClipboardDebugPacket> STREAM_CODEC =
            StreamCodec.ofMember(ServerboundSmartClipboardDebugPacket::encode, ServerboundSmartClipboardDebugPacket::decode);

    private static ServerboundSmartClipboardDebugPacket decode(RegistryFriendlyByteBuf buffer) {
        return new ServerboundSmartClipboardDebugPacket(
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readUtf(),
                readOptionalString(buffer),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readUtf(),
                buffer.readVarInt()
        );
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(requestToken);
        buffer.writeUtf(rowItemName);
        buffer.writeUtf(quantityDisplay);
        buffer.writeUtf(requesterDisplay);
        writeOptionalString(buffer, workerDisplay);
        buffer.writeBoolean(minimumStockRequest);
        buffer.writeBoolean(expandable);
        buffer.writeUtf(expandableReason);
        buffer.writeVarInt(clientDependencyCount);
    }

    public static void handle(ServerboundSmartClipboardDebugPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            Optional<IColony> colony = ColonyContextResolver.resolve(player, Optional.empty());
            if (colony.isEmpty()) {
                player.sendSystemMessage(Component.literal("Smart Clipboard debug skipped: no colony resolved."));
                return;
            }
            RequestAnalysisService.debugDump(
                    player,
                    colony.get(),
                    packet.requestToken(),
                    packet.rowItemName(),
                    packet.quantityDisplay(),
                    packet.requesterDisplay(),
                    packet.workerDisplay(),
                    packet.minimumStockRequest(),
                    packet.expandable(),
                    packet.expandableReason(),
                    packet.clientDependencyCount()
            );
            player.sendSystemMessage(Component.literal("Smart Clipboard debug dumped for " + packet.rowItemName() + "."));
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static Optional<String> readOptionalString(FriendlyByteBuf buffer) {
        return buffer.readBoolean() ? Optional.of(buffer.readUtf()) : Optional.empty();
    }

    private static void writeOptionalString(FriendlyByteBuf buffer, Optional<String> value) {
        buffer.writeBoolean(value.isPresent());
        value.ifPresent(buffer::writeUtf);
    }
}
