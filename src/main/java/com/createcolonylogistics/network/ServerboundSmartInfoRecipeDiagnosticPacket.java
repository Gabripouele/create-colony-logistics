package com.createcolonylogistics.network;

import com.createcolonylogistics.CreateColonyLogistics;
import com.createcolonylogistics.clipboard.SmartInfoRecipeDiagnosticDumper;
import com.createcolonylogistics.clipboard.SmartInfoTooltipSourceDiagnosticDumper;
import com.createcolonylogistics.clipboard.ColonyContextResolver;
import com.createcolonylogistics.clipboard.SmartClipboardReport;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.io.IOException;
import java.util.ArrayList;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

// DIAGNOSTIC ONLY - remove after Cutter recipe source-truth audit
public record ServerboundSmartInfoRecipeDiagnosticPacket(
        ItemStack stack,
        SmartClipboardReport report,
        String context,
        String hoverPath,
        int directEntryIndex,
        int parentEntryIndex,
        String resourceKey,
        List<SmartClipboardReport.SmartInfoKey> hoverKeys
) implements CustomPacketPayload {
    public static final Type<ServerboundSmartInfoRecipeDiagnosticPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateColonyLogistics.MOD_ID, "smart_info_recipe_diagnostic")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundSmartInfoRecipeDiagnosticPacket> STREAM_CODEC =
            StreamCodec.ofMember(ServerboundSmartInfoRecipeDiagnosticPacket::encode, ServerboundSmartInfoRecipeDiagnosticPacket::decode);

    private static ServerboundSmartInfoRecipeDiagnosticPacket decode(RegistryFriendlyByteBuf buffer) {
        return new ServerboundSmartInfoRecipeDiagnosticPacket(
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                buffer.readBoolean() ? SmartClipboardReport.decode(buffer) : null,
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readUtf(),
                buffer.readCollection(ArrayList::new, valueBuffer -> new SmartClipboardReport.SmartInfoKey(
                        valueBuffer.readUtf(),
                        valueBuffer.readVarInt()
                ))
        );
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, stack == null ? ItemStack.EMPTY : stack);
        if (report == null) {
            buffer.writeBoolean(false);
        } else {
            buffer.writeBoolean(true);
            report.encode(buffer);
        }
        buffer.writeUtf(context == null ? "" : context);
        buffer.writeUtf(hoverPath == null ? "" : hoverPath);
        buffer.writeVarInt(directEntryIndex);
        buffer.writeVarInt(parentEntryIndex);
        buffer.writeUtf(resourceKey == null ? "" : resourceKey);
        List<SmartClipboardReport.SmartInfoKey> keys = hoverKeys == null ? List.of() : hoverKeys;
        buffer.writeCollection(keys, (valueBuffer, key) -> {
            valueBuffer.writeUtf(key == null || key.key() == null ? "" : key.key());
            valueBuffer.writeVarInt(key == null ? Integer.MAX_VALUE : key.priority());
        });
    }

    public static void handle(ServerboundSmartInfoRecipeDiagnosticPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            boolean recipeEnabled = SmartInfoRecipeDiagnosticDumper.ENABLE_SMART_INFO_RECIPE_DIAGNOSTICS;
            boolean sourceEnabled = SmartInfoTooltipSourceDiagnosticDumper.ENABLE_SMART_INFO_TOOLTIP_SOURCE_DIAGNOSTICS;
            if (!recipeEnabled && !sourceEnabled) {
                player.sendSystemMessage(Component.literal("Smart Info recipe diagnostics are disabled."));
                return;
            }
            Optional<com.minecolonies.api.colony.IColony> colony = ColonyContextResolver.resolve(player, Optional.empty());
            if (colony.isEmpty()) {
                player.sendSystemMessage(Component.literal("Smart Info recipe diagnostic skipped: no colony resolved."));
                return;
            }
            Optional<Path> sourcePath = Optional.empty();
            Optional<Path> path = recipeEnabled ? SmartInfoRecipeDiagnosticDumper.dump(player, colony.get(), packet.stack(), packet.context()) : Optional.empty();
            if (sourceEnabled && !packet.hoverPath().isBlank()) {
                try {
                    sourcePath = SmartInfoTooltipSourceDiagnosticDumper.dump(
                            player,
                            colony.get(),
                            packet.report() == null ? SmartInfoTooltipSourceDiagnosticDumper.emptyReport() : packet.report(),
                            packet.stack(),
                            packet.hoverPath(),
                            packet.context(),
                            packet.resourceKey(),
                            packet.hoverKeys() == null ? List.of() : packet.hoverKeys(),
                            packet.directEntryIndex(),
                            packet.parentEntryIndex());
                } catch (IOException exception) {
                    player.sendSystemMessage(Component.literal("Smart Info tooltip-source diagnostic failed to write."));
                    CreateColonyLogistics.LOGGER.warn("[SmartInfoTooltipSourceDiagnostic] failed to write diagnostics", exception);
                }
            }
            StringBuilder message = new StringBuilder();
            message.append("Smart Info recipe diagnostic ").append(path
                    .map(value -> "written: " + value)
                    .orElse("failed"));
            sourcePath.ifPresent(value -> message.append(" | tooltip-source trace written: ").append(value));
            player.sendSystemMessage(Component.literal(message.toString()));
            if (path.isPresent() || sourcePath.isPresent()) {
                return;
            }
            player.sendSystemMessage(Component.literal("Smart Info diagnostics failed."));
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
