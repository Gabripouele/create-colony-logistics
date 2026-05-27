package com.createcolonylogistics.network;

import com.createcolonylogistics.CreateColonyLogistics;
import com.createcolonylogistics.clipboard.ColonyContextResolver;
import com.createcolonylogistics.clipboard.RequestAnalysisService;
import com.createcolonylogistics.clipboard.SmartClipboardReport;
import com.createcolonylogistics.clipboard.SmartClipboardScrollStorage;
import com.minecolonies.api.colony.IColony;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;

public record ServerboundSmartClipboardScrollPacket(int action, int slot) implements CustomPacketPayload {
    public static final int INSERT = 0;
    public static final int REMOVE = 1;

    public static final Type<ServerboundSmartClipboardScrollPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateColonyLogistics.MOD_ID, "smart_clipboard_scroll")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundSmartClipboardScrollPacket> STREAM_CODEC =
            StreamCodec.ofMember(ServerboundSmartClipboardScrollPacket::encode, ServerboundSmartClipboardScrollPacket::decode);

    private static ServerboundSmartClipboardScrollPacket decode(RegistryFriendlyByteBuf buffer) {
        return new ServerboundSmartClipboardScrollPacket(buffer.readVarInt(), buffer.readVarInt());
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(action);
        buffer.writeVarInt(slot);
    }

    public static void handle(ServerboundSmartClipboardScrollPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            Optional<ItemStack> clipboard = SmartClipboardScrollStorage.findSmartClipboard(player);
            if (clipboard.isEmpty()) {
                return;
            }

            boolean changed = packet.action() == INSERT
                    ? SmartClipboardScrollStorage.insertFirstResourceScroll(player, clipboard.get())
                    : SmartClipboardScrollStorage.removeResourceScroll(player, clipboard.get(), packet.slot());
            if (!changed) {
                return;
            }

            Optional<IColony> colony = ColonyContextResolver.resolve(player, Optional.empty());
            if (colony.isEmpty()) {
                return;
            }
            RequestAnalysisService.AnalysisResult result = RequestAnalysisService.analyze(player.serverLevel(), colony.get(), 250);
            PacketDistributor.sendToPlayer(player, new ClientboundSmartClipboardReportPacket(
                    SmartClipboardReport.fromAnalysis(result, SmartClipboardScrollStorage.read(clipboard.get()))
            ));
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
