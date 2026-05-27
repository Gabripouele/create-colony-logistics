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
                entry.displayStacks().stream().map(ItemStack::copy).toList(),
                entry.requestedCount(),
                entry.quantityDisplay(),
                entry.requesterName(),
                entry.requesterPosition(),
                entry.workerName(),
                entry.dimensionName(),
                entry.resolverName(),
                entry.important(),
                entry.minimumStockRequest(),
                entry.warehouseStock(),
                entry.domumBlockId().toString(),
                entry.cutterRecipe().map(Object::toString),
                shortenFingerprint(entry.exactComboFingerprint()),
                entry.exactComboFingerprint(),
                entry.exactComboTaught(),
                List.copyOf(entry.knownBy()),
                List.copyOf(entry.canLearn()),
                Optional.ofNullable(entry.requestToken()).filter(token -> !token.isBlank()),
                entry.requestTree().stream()
                        .map(node -> new RequestTreeNode(node.depth(), node.stack().copy(), node.count(), node.quantityDisplay(), node.label()))
                        .toList()
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
            List<ItemStack> displayStacks,
            int requestedCount,
            String quantityDisplay,
            String requestingBuildingName,
            Optional<BlockPos> requestingBuildingPos,
            Optional<String> requestingWorkerName,
            Optional<String> dimensionName,
            Optional<String> resolverName,
            boolean important,
            boolean minimumStockRequest,
            int warehouseStock,
            String doBlockId,
            Optional<String> cutterRecipeId,
            String comboFingerprintShort,
            String comboFingerprintFull,
            boolean exactComboAlreadyTaught,
            List<String> recipeKnownBy,
            List<String> canLearnCombo,
            Optional<String> requestToken,
            List<RequestTreeNode> requestTree
    ) {
        private static Entry decode(RegistryFriendlyByteBuf buffer) {
            ItemStack requestedStack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
            int displayStackCount = buffer.readVarInt();
            List<ItemStack> displayStacks = new ArrayList<>(displayStackCount);
            for (int i = 0; i < displayStackCount; i++) {
                displayStacks.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
            }
            int requestedCount = buffer.readVarInt();
            String quantityDisplay = buffer.readUtf();
            String requestingBuildingName = buffer.readUtf();
            Optional<BlockPos> requestingBuildingPos = readOptionalBlockPos(buffer);
            Optional<String> requestingWorkerName = readOptionalString(buffer);
            Optional<String> dimensionName = readOptionalString(buffer);
            Optional<String> resolverName = readOptionalString(buffer);
            boolean important = buffer.readBoolean();
            boolean minimumStockRequest = buffer.readBoolean();
            int warehouseStock = buffer.readVarInt();
            String doBlockId = buffer.readUtf();
            Optional<String> cutterRecipeId = readOptionalString(buffer);
            String comboFingerprintShort = buffer.readUtf();
            String comboFingerprintFull = buffer.readUtf();
            boolean exactComboAlreadyTaught = buffer.readBoolean();
            List<String> recipeKnownBy = buffer.readList(FriendlyByteBuf::readUtf);
            List<String> canLearnCombo = buffer.readList(FriendlyByteBuf::readUtf);
            Optional<String> requestToken = readOptionalString(buffer);
            int treeSize = buffer.readVarInt();
            List<RequestTreeNode> requestTree = new ArrayList<>(treeSize);
            for (int i = 0; i < treeSize; i++) {
                requestTree.add(RequestTreeNode.decode(buffer));
            }
            return new Entry(requestedStack, displayStacks, requestedCount, quantityDisplay, requestingBuildingName, requestingBuildingPos, requestingWorkerName,
                    dimensionName, resolverName, important, minimumStockRequest,
                    warehouseStock, doBlockId, cutterRecipeId, comboFingerprintShort, comboFingerprintFull,
                    exactComboAlreadyTaught, recipeKnownBy, canLearnCombo, requestToken, requestTree);
        }

        private void encode(RegistryFriendlyByteBuf buffer) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, requestedStack);
            buffer.writeVarInt(displayStacks.size());
            for (ItemStack displayStack : displayStacks) {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, displayStack);
            }
            buffer.writeVarInt(requestedCount);
            buffer.writeUtf(quantityDisplay);
            buffer.writeUtf(requestingBuildingName);
            writeOptionalBlockPos(buffer, requestingBuildingPos);
            writeOptionalString(buffer, requestingWorkerName);
            writeOptionalString(buffer, dimensionName);
            writeOptionalString(buffer, resolverName);
            buffer.writeBoolean(important);
            buffer.writeBoolean(minimumStockRequest);
            buffer.writeVarInt(warehouseStock);
            buffer.writeUtf(doBlockId);
            writeOptionalString(buffer, cutterRecipeId);
            buffer.writeUtf(comboFingerprintShort);
            buffer.writeUtf(comboFingerprintFull);
            buffer.writeBoolean(exactComboAlreadyTaught);
            buffer.writeCollection(recipeKnownBy, FriendlyByteBuf::writeUtf);
            buffer.writeCollection(canLearnCombo, FriendlyByteBuf::writeUtf);
            writeOptionalString(buffer, requestToken);
            buffer.writeVarInt(requestTree.size());
            for (RequestTreeNode node : requestTree) {
                node.encode(buffer);
            }
        }
    }

    public record RequestTreeNode(
            int depth,
            ItemStack stack,
            int count,
            String quantityDisplay,
            String label
    ) {
        private static RequestTreeNode decode(RegistryFriendlyByteBuf buffer) {
            return new RequestTreeNode(
                    buffer.readVarInt(),
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                    buffer.readVarInt(),
                    buffer.readUtf(),
                    buffer.readUtf()
            );
        }

        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(depth);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, stack);
            buffer.writeVarInt(count);
            buffer.writeUtf(quantityDisplay);
            buffer.writeUtf(label);
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
