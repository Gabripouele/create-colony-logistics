package com.createcolonylogistics.network;

import com.createcolonylogistics.CreateColonyLogistics;
import com.createcolonylogistics.clipboard.ColonyContextResolver;
import com.createcolonylogistics.clipboard.RequestAnalysisService;
import com.createcolonylogistics.clipboard.SmartClipboardFilterState;
import com.createcolonylogistics.clipboard.SmartClipboardReport;
import com.createcolonylogistics.clipboard.SmartClipboardScrollStorage;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.colony.requestsystem.token.StandardToken;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record ServerboundSmartClipboardCancelPacket(String requestToken) implements CustomPacketPayload {
    public static final Type<ServerboundSmartClipboardCancelPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateColonyLogistics.MOD_ID, "smart_clipboard_cancel")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundSmartClipboardCancelPacket> STREAM_CODEC =
            StreamCodec.ofMember(ServerboundSmartClipboardCancelPacket::encode, ServerboundSmartClipboardCancelPacket::decode);

    private static final Pattern STANDARD_TOKEN_PATTERN = Pattern.compile("^StandardToken\\{id=([^}]+)}$");
    private static final int MAX_REPORT_REQUESTS = 250;

    private static ServerboundSmartClipboardCancelPacket decode(RegistryFriendlyByteBuf buffer) {
        return new ServerboundSmartClipboardCancelPacket(buffer.readUtf());
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(requestToken == null ? "" : requestToken);
    }

    public static void handle(ServerboundSmartClipboardCancelPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            Optional<ItemStack> clipboard = SmartClipboardScrollStorage.findSmartClipboard(player);
            if (clipboard.isEmpty()) {
                return;
            }

            Optional<IColony> colony = ColonyContextResolver.resolveLinkedClipboard(clipboard.get());
            if (colony.isEmpty()) {
                sendReport(player, clipboard.get(), Optional.empty());
                return;
            }

            tokenFromReportValue(packet.requestToken())
                    .ifPresent(token -> cancelIfValidRootRequest(colony.get(), token));
            sendReport(player, clipboard.get(), colony);
        });
    }

    private static void cancelIfValidRootRequest(IColony colony, IToken<?> token) {
        try {
            IRequest<?> request = colony.getRequestManager().getRequestForToken(token);
            if (request != null && !request.hasParent() && isActiveRequestState(request.getState())) {
                colony.getRequestManager().updateRequestState(request.getId(), RequestState.CANCELLED);
            }
        } catch (RuntimeException ignored) {
            // The request may have resolved, disappeared, or been cancelled between render and click.
        }
    }

    private static void sendReport(ServerPlayer player, ItemStack clipboard, Optional<IColony> colony) {
        boolean importantOnly = SmartClipboardFilterState.isImportantOnly(clipboard);
        SmartClipboardReport report;
        if (colony.isPresent()) {
            RequestAnalysisService.AnalysisResult analysis = RequestAnalysisService.analyze(player.serverLevel(), colony.get(), MAX_REPORT_REQUESTS);
            report = SmartClipboardReport.fromAnalysis(analysis, SmartClipboardScrollStorage.read(clipboard), importantOnly);
        } else {
            report = new SmartClipboardReport("", 0, 0, 0, false, importantOnly, SmartClipboardScrollStorage.read(clipboard), java.util.List.of());
        }
        PacketDistributor.sendToPlayer(player, new ClientboundSmartClipboardReportPacket(report));
    }

    private static Optional<IToken<?>> tokenFromReportValue(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String trimmed = value.strip();
        Matcher matcher = STANDARD_TOKEN_PATTERN.matcher(trimmed);
        if (matcher.matches()) {
            trimmed = matcher.group(1);
        }
        try {
            return Optional.of(new StandardToken(UUID.fromString(trimmed)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static boolean isActiveRequestState(RequestState state) {
        return state != RequestState.RESOLVED
                && state != RequestState.COMPLETED
                && state != RequestState.OVERRULED
                && state != RequestState.CANCELLED
                && state != RequestState.RECEIVED
                && state != RequestState.FAILED;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
