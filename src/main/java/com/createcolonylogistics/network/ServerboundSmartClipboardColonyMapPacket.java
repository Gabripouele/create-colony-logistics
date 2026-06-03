package com.createcolonylogistics.network;

import com.createcolonylogistics.CreateColonyLogistics;
import com.createcolonylogistics.clipboard.ColonyContextResolver;
import com.createcolonylogistics.clipboard.RequestAnalysisService;
import com.createcolonylogistics.clipboard.SmartClipboardColonyMapStorage;
import com.createcolonylogistics.clipboard.SmartClipboardFilterState;
import com.createcolonylogistics.clipboard.SmartClipboardReport;
import com.createcolonylogistics.clipboard.SmartClipboardScrollStorage;
import com.minecolonies.api.colony.IColony;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.Optional;

public record ServerboundSmartClipboardColonyMapPacket(int action) implements CustomPacketPayload {
    public static final int INSERT = 0;
    public static final int REMOVE = 1;
    public static final int OPEN = 2;

    public static final Type<ServerboundSmartClipboardColonyMapPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateColonyLogistics.MOD_ID, "smart_clipboard_colony_map")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundSmartClipboardColonyMapPacket> STREAM_CODEC =
            StreamCodec.ofMember(ServerboundSmartClipboardColonyMapPacket::encode, ServerboundSmartClipboardColonyMapPacket::decode);

    private static ServerboundSmartClipboardColonyMapPacket decode(RegistryFriendlyByteBuf buffer) {
        return new ServerboundSmartClipboardColonyMapPacket(buffer.readVarInt());
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(action);
    }

    public static void handle(ServerboundSmartClipboardColonyMapPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            Optional<ItemStack> clipboard = SmartClipboardScrollStorage.findSmartClipboard(player);
            if (clipboard.isEmpty()) {
                return;
            }

            if (packet.action() == OPEN) {
                SmartClipboardColonyMapStorage.MutationResult result = SmartClipboardColonyMapStorage.validateOpen(clipboard.get());
                if (result.changed()) {
                    PacketDistributor.sendToPlayer(player, new ClientboundSmartColonyMapOpenPacket(result.colonyMap()));
                } else {
                    player.sendSystemMessage(Component.literal(result.message()));
                }
                return;
            }

            SmartClipboardColonyMapStorage.MutationResult result;
            if (packet.action() == INSERT) {
                result = SmartClipboardColonyMapStorage.insertFirstColonyMap(player, clipboard.get());
            } else if (packet.action() == REMOVE) {
                result = SmartClipboardColonyMapStorage.removeColonyMap(player, clipboard.get());
            } else {
                result = SmartClipboardColonyMapStorage.MutationResult.rejected("Invalid Smart Colony Map action.");
            }

            if (!result.message().isBlank()) {
                player.sendSystemMessage(Component.literal(result.message()));
            }
            PacketDistributor.sendToPlayer(player, new ClientboundSmartClipboardReportPacket(buildReport(player, clipboard.get())));
        });
    }

    private static SmartClipboardReport buildReport(ServerPlayer player, ItemStack clipboard) {
        Optional<IColony> colony = ColonyContextResolver.resolveLinkedClipboard(clipboard);
        boolean importantOnly = SmartClipboardFilterState.isImportantOnly(clipboard);
        List<ItemStack> resourceScrolls = SmartClipboardScrollStorage.read(clipboard);
        ItemStack colonyMap = SmartClipboardColonyMapStorage.read(clipboard);
        if (colony.isPresent()) {
            RequestAnalysisService.AnalysisResult analysis = RequestAnalysisService.analyze(player.serverLevel(), colony.get(), 250, resourceScrolls);
            return SmartClipboardReport.fromAnalysis(analysis, resourceScrolls, colonyMap, importantOnly);
        }
        return new SmartClipboardReport("", 0, 0, 0, false, importantOnly, resourceScrolls, colonyMap, List.of());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
