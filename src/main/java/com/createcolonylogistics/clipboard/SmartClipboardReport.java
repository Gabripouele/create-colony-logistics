package com.createcolonylogistics.clipboard;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record SmartClipboardReport(
        String colonyName,
        int colonyId,
        int buildingCount,
        int activeRequestCount,
        boolean capped,
        List<Entry> entries
) {
    public static SmartClipboardReport fromAnalysis(RequestAnalysisService.AnalysisResult result) {
        List<Entry> entries = new ArrayList<>();
        result.groupedEntries().values().forEach(group -> group.forEach(entry -> entries.add(new Entry(
                entry.requestedStack().copy(),
                entry.requestedCount(),
                entry.requesterName(),
                entry.requesterPosition(),
                entry.workerName(),
                entry.warehouseStock(),
                entry.domumBlockId().toString(),
                entry.cutterRecipe().map(Object::toString),
                shortenFingerprint(entry.exactComboFingerprint()),
                entry.exactComboFingerprint(),
                entry.exactComboTaught(),
                List.copyOf(entry.knownBy()),
                List.copyOf(entry.canLearn()),
                Optional.ofNullable(entry.requestToken()).filter(token -> !token.isBlank())
        ))));

        return new SmartClipboardReport(
                result.colonyName(),
                result.colonyId(),
                result.buildingCount(),
                result.activeRequestCount(),
                result.capped(),
                entries
        );
    }

    public static SmartClipboardReport decode(RegistryFriendlyByteBuf buffer) {
        String colonyName = buffer.readUtf();
        int colonyId = buffer.readVarInt();
        int buildingCount = buffer.readVarInt();
        int activeRequestCount = buffer.readVarInt();
        boolean capped = buffer.readBoolean();
        int entryCount = buffer.readVarInt();
        List<Entry> entries = new ArrayList<>(entryCount);
        for (int i = 0; i < entryCount; i++) {
            entries.add(Entry.decode(buffer));
        }
        return new SmartClipboardReport(colonyName, colonyId, buildingCount, activeRequestCount, capped, entries);
    }

    public void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(colonyName);
        buffer.writeVarInt(colonyId);
        buffer.writeVarInt(buildingCount);
        buffer.writeVarInt(activeRequestCount);
        buffer.writeBoolean(capped);
        buffer.writeVarInt(entries.size());
        for (Entry entry : entries) {
            entry.encode(buffer);
        }
    }

    private static String shortenFingerprint(String fingerprint) {
        if (fingerprint.length() <= 64) {
            return fingerprint;
        }
        return fingerprint.substring(0, 48) + "..." + fingerprint.substring(fingerprint.length() - 12);
    }

    public record Entry(
            ItemStack requestedStack,
            int requestedCount,
            String requestingBuildingName,
            Optional<BlockPos> requestingBuildingPos,
            Optional<String> requestingWorkerName,
            int warehouseStock,
            String doBlockId,
            Optional<String> cutterRecipeId,
            String comboFingerprintShort,
            String comboFingerprintFull,
            boolean exactComboAlreadyTaught,
            List<String> recipeKnownBy,
            List<String> canLearnCombo,
            Optional<String> requestToken
    ) {
        private static Entry decode(RegistryFriendlyByteBuf buffer) {
            ItemStack requestedStack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
            int requestedCount = buffer.readVarInt();
            String requestingBuildingName = buffer.readUtf();
            Optional<BlockPos> requestingBuildingPos = readOptionalBlockPos(buffer);
            Optional<String> requestingWorkerName = readOptionalString(buffer);
            int warehouseStock = buffer.readVarInt();
            String doBlockId = buffer.readUtf();
            Optional<String> cutterRecipeId = readOptionalString(buffer);
            String comboFingerprintShort = buffer.readUtf();
            String comboFingerprintFull = buffer.readUtf();
            boolean exactComboAlreadyTaught = buffer.readBoolean();
            List<String> recipeKnownBy = buffer.readList(FriendlyByteBuf::readUtf);
            List<String> canLearnCombo = buffer.readList(FriendlyByteBuf::readUtf);
            Optional<String> requestToken = readOptionalString(buffer);
            return new Entry(requestedStack, requestedCount, requestingBuildingName, requestingBuildingPos, requestingWorkerName,
                    warehouseStock, doBlockId, cutterRecipeId, comboFingerprintShort, comboFingerprintFull,
                    exactComboAlreadyTaught, recipeKnownBy, canLearnCombo, requestToken);
        }

        private void encode(RegistryFriendlyByteBuf buffer) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, requestedStack);
            buffer.writeVarInt(requestedCount);
            buffer.writeUtf(requestingBuildingName);
            writeOptionalBlockPos(buffer, requestingBuildingPos);
            writeOptionalString(buffer, requestingWorkerName);
            buffer.writeVarInt(warehouseStock);
            buffer.writeUtf(doBlockId);
            writeOptionalString(buffer, cutterRecipeId);
            buffer.writeUtf(comboFingerprintShort);
            buffer.writeUtf(comboFingerprintFull);
            buffer.writeBoolean(exactComboAlreadyTaught);
            buffer.writeCollection(recipeKnownBy, FriendlyByteBuf::writeUtf);
            buffer.writeCollection(canLearnCombo, FriendlyByteBuf::writeUtf);
            writeOptionalString(buffer, requestToken);
        }
    }

    private static Optional<String> readOptionalString(FriendlyByteBuf buffer) {
        return buffer.readBoolean() ? Optional.of(buffer.readUtf()) : Optional.empty();
    }

    private static void writeOptionalString(FriendlyByteBuf buffer, Optional<String> value) {
        buffer.writeBoolean(value.isPresent());
        value.ifPresent(buffer::writeUtf);
    }

    private static Optional<BlockPos> readOptionalBlockPos(FriendlyByteBuf buffer) {
        return buffer.readBoolean() ? Optional.of(buffer.readBlockPos()) : Optional.empty();
    }

    private static void writeOptionalBlockPos(FriendlyByteBuf buffer, Optional<BlockPos> value) {
        buffer.writeBoolean(value.isPresent());
        value.ifPresent(buffer::writeBlockPos);
    }
}
