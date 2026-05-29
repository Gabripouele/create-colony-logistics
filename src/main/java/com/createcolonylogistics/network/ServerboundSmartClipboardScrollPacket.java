package com.createcolonylogistics.network;

import com.createcolonylogistics.CreateColonyLogistics;
import com.createcolonylogistics.clipboard.ColonyContextResolver;
import com.createcolonylogistics.clipboard.RequestAnalysisService;
import com.createcolonylogistics.clipboard.SmartClipboardReport;
import com.createcolonylogistics.clipboard.SmartClipboardScrollStorage;
import com.createcolonylogistics.clipboard.SmartClipboardScrollStorage.MutationResult;
import com.createcolonylogistics.clipboard.SmartClipboardScrollStorage.ScrollLinkSnapshot;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.items.component.BuildingId;
import com.minecolonies.api.items.component.ColonyId;
import com.minecolonies.api.items.component.WarehouseSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;

public record ServerboundSmartClipboardScrollPacket(int action, int slot, ScrollLinkSnapshot scrollSnapshot) implements CustomPacketPayload {
    public static final int INSERT = 0;
    public static final int REMOVE = 1;

    public static final Type<ServerboundSmartClipboardScrollPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateColonyLogistics.MOD_ID, "smart_clipboard_scroll")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundSmartClipboardScrollPacket> STREAM_CODEC =
            StreamCodec.ofMember(ServerboundSmartClipboardScrollPacket::encode, ServerboundSmartClipboardScrollPacket::decode);

    private static ServerboundSmartClipboardScrollPacket decode(RegistryFriendlyByteBuf buffer) {
        return new ServerboundSmartClipboardScrollPacket(buffer.readVarInt(), buffer.readVarInt(), readSnapshot(buffer));
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(action);
        buffer.writeVarInt(slot);
        writeSnapshot(buffer, scrollSnapshot);
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

            MutationResult result = packet.action() == INSERT
                    ? SmartClipboardScrollStorage.insertResourceScroll(player, clipboard.get(), packet.slot(), packet.scrollSnapshot())
                    : SmartClipboardScrollStorage.removeResourceScroll(player, clipboard.get(), packet.slot());
            if (!result.changed()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(result.rejectedReason()));
                return;
            }

            SmartClipboardReport report;
            Optional<IColony> colony = ColonyContextResolver.resolve(player, Optional.empty());
            if (colony.isPresent()) {
                RequestAnalysisService.AnalysisResult analysis = RequestAnalysisService.analyze(player.serverLevel(), colony.get(), 250);
                report = SmartClipboardReport.fromAnalysis(analysis, SmartClipboardScrollStorage.read(clipboard.get()));
            } else {
                report = new SmartClipboardReport("", 0, 0, 0, false, SmartClipboardScrollStorage.read(clipboard.get()), java.util.List.of());
            }
            PacketDistributor.sendToPlayer(player, new ClientboundSmartClipboardReportPacket(report));
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static ScrollLinkSnapshot readSnapshot(RegistryFriendlyByteBuf buffer) {
        return new ScrollLinkSnapshot(
                ColonyId.STREAM_CODEC.decode(buffer),
                BuildingId.STREAM_CODEC.decode(buffer),
                WarehouseSnapshot.STREAM_CODEC.decode(buffer)
        );
    }

    private static void writeSnapshot(RegistryFriendlyByteBuf buffer, ScrollLinkSnapshot snapshot) {
        ScrollLinkSnapshot safe = snapshot == null ? ScrollLinkSnapshot.EMPTY : snapshot;
        ColonyId.STREAM_CODEC.encode(buffer, safe.colonyId());
        BuildingId.STREAM_CODEC.encode(buffer, safe.buildingId());
        WarehouseSnapshot.STREAM_CODEC.encode(buffer, safe.warehouseSnapshot());
    }
}
