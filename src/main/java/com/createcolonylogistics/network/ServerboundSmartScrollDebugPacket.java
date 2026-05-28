package com.createcolonylogistics.network;

import com.createcolonylogistics.CreateColonyLogistics;
import com.createcolonylogistics.clipboard.SmartClipboardScrollStorage;
import com.createcolonylogistics.clipboard.SmartClipboardScrollStorage.ScrollLinkSnapshot;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.items.component.BuildingId;
import com.minecolonies.api.items.component.ColonyId;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.Optional;

public record ServerboundSmartScrollDebugPacket(
        int selectedSlot,
        String selectedItemId,
        int selectedCount,
        boolean selectedHasComponents,
        ScrollLinkSnapshot selectedLink,
        boolean clientAdapterList,
        String clientError,
        int clientModuleResources,
        int clientAdaptedResources,
        int clientRenderedRows
) implements CustomPacketPayload {
    public static final Type<ServerboundSmartScrollDebugPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateColonyLogistics.MOD_ID, "smart_scroll_debug")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundSmartScrollDebugPacket> STREAM_CODEC =
            StreamCodec.ofMember(ServerboundSmartScrollDebugPacket::encode, ServerboundSmartScrollDebugPacket::decode);

    private static ServerboundSmartScrollDebugPacket decode(RegistryFriendlyByteBuf buffer) {
        return new ServerboundSmartScrollDebugPacket(
                buffer.readVarInt(),
                buffer.readUtf(),
                buffer.readVarInt(),
                buffer.readBoolean(),
                readSnapshot(buffer),
                buffer.readBoolean(),
                buffer.readUtf(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt()
        );
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(selectedSlot);
        buffer.writeUtf(selectedItemId);
        buffer.writeVarInt(selectedCount);
        buffer.writeBoolean(selectedHasComponents);
        writeSnapshot(buffer, selectedLink);
        buffer.writeBoolean(clientAdapterList);
        buffer.writeUtf(clientError);
        buffer.writeVarInt(clientModuleResources);
        buffer.writeVarInt(clientAdaptedResources);
        buffer.writeVarInt(clientRenderedRows);
    }

    public static void handle(ServerboundSmartScrollDebugPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            Optional<ItemStack> clipboard = SmartClipboardScrollStorage.findSmartClipboard(player);
            List<ItemStack> storedScrolls = clipboard.map(SmartClipboardScrollStorage::read).orElse(List.of());
            ItemStack storedStack = packet.selectedSlot() >= 0 && packet.selectedSlot() < storedScrolls.size()
                    ? storedScrolls.get(packet.selectedSlot())
                    : ItemStack.EMPTY;

            logClientSnapshot(packet);
            logStack("server-stored", packet.selectedSlot(), storedStack);
            CreateColonyLogistics.LOGGER.info("[SmartScrollDebug] ServerComparison: comparisonOnly=true renderAuthority=client serverPathAuthoritative=false player={} slot={} clipboardFound={} storedSlots={} clientAdapterList={} clientError='{}' clientModuleResources={} clientAdaptedResources={} clientRenderedRows={}",
                    player.getGameProfile().getName(),
                    packet.selectedSlot(),
                    clipboard.isPresent(),
                    storedScrolls.size(),
                    packet.clientAdapterList(),
                    packet.clientError(),
                    packet.clientModuleResources(),
                    packet.clientAdaptedResources(),
                    packet.clientRenderedRows());
            player.sendSystemMessage(Component.literal("Smart Scroll server debug logged for slot " + packet.selectedSlot() + "."));
        });
    }

    private static void logClientSnapshot(ServerboundSmartScrollDebugPacket packet) {
        ScrollLinkSnapshot snapshot = packet.selectedLink() == null ? ScrollLinkSnapshot.EMPTY : packet.selectedLink();
        CreateColonyLogistics.LOGGER.info("[SmartScrollDebug] Stack[client-selected]: slot={} item={} count={} hasComponents={} hasColonyId={} colonyId={} dimension={} hasBuildingId={} buildingPos={} viewResolved=n/a viewClass=client-snapshot isBuildingBuilderView=n/a",
                packet.selectedSlot(),
                packet.selectedItemId(),
                packet.selectedCount(),
                packet.selectedHasComponents(),
                snapshot.colonyId().hasColonyId(),
                snapshot.colonyId().id(),
                snapshot.colonyId().dimension().location(),
                snapshot.buildingId().hasId(),
                snapshot.buildingId().id());
    }

    private static void logStack(String label, int slot, ItemStack stack) {
        ColonyId colonyId = ColonyId.readFromItemStack(stack);
        BuildingId buildingId = BuildingId.readFromItemStack(stack);
        IBuildingView buildingView = stack.isEmpty() ? null : BuildingId.readBuildingViewFromItemStack(stack);
        CreateColonyLogistics.LOGGER.info("[SmartScrollDebug] Stack[{}]: slot={} item={} count={} hasComponents={} hasColonyId={} colonyId={} dimension={} hasBuildingId={} buildingPos={} viewResolved={} viewClass={} isBuildingBuilderView={}",
                label,
                slot,
                stack.isEmpty() ? "empty" : String.valueOf(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())),
                stack.getCount(),
                !stack.getComponentsPatch().isEmpty(),
                colonyId.hasColonyId(),
                colonyId.id(),
                colonyId.dimension().location(),
                buildingId.hasId(),
                buildingId.id(),
                buildingView != null,
                buildingView == null ? "none" : buildingView.getClass().getName(),
                buildingView != null && buildingView.getClass().getName().endsWith("BuildingBuilder$View"));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static ScrollLinkSnapshot readSnapshot(RegistryFriendlyByteBuf buffer) {
        return new ScrollLinkSnapshot(
                ColonyId.STREAM_CODEC.decode(buffer),
                BuildingId.STREAM_CODEC.decode(buffer),
                com.minecolonies.api.items.component.WarehouseSnapshot.STREAM_CODEC.decode(buffer)
        );
    }

    private static void writeSnapshot(RegistryFriendlyByteBuf buffer, ScrollLinkSnapshot snapshot) {
        ScrollLinkSnapshot safe = snapshot == null ? ScrollLinkSnapshot.EMPTY : snapshot;
        ColonyId.STREAM_CODEC.encode(buffer, safe.colonyId());
        BuildingId.STREAM_CODEC.encode(buffer, safe.buildingId());
        com.minecolonies.api.items.component.WarehouseSnapshot.STREAM_CODEC.encode(buffer, safe.warehouseSnapshot());
    }
}
