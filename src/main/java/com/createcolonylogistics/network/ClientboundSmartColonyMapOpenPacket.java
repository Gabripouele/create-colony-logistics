package com.createcolonylogistics.network;

import com.createcolonylogistics.CreateColonyLogistics;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ClientboundSmartColonyMapOpenPacket(ItemStack colonyMap) implements CustomPacketPayload {
    public static final Type<ClientboundSmartColonyMapOpenPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateColonyLogistics.MOD_ID, "smart_colony_map_open")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundSmartColonyMapOpenPacket> STREAM_CODEC =
            StreamCodec.ofMember(ClientboundSmartColonyMapOpenPacket::encode, ClientboundSmartColonyMapOpenPacket::decode);

    private static ClientboundSmartColonyMapOpenPacket decode(RegistryFriendlyByteBuf buffer) {
        return new ClientboundSmartColonyMapOpenPacket(ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, colonyMap);
    }

    public static void handle(ClientboundSmartColonyMapOpenPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                SmartClipboardClientBridge.openColonyMap(packet.colonyMap());
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
