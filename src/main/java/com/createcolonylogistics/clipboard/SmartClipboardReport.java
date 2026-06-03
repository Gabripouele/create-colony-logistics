package com.createcolonylogistics.clipboard;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public record SmartClipboardReport(
        String colonyName,
        int colonyId,
        int buildingCount,
        int activeRequestCount,
        boolean capped,
        boolean importantOnly,
        List<ItemStack> resourceScrolls,
        ItemStack colonyMap,
        List<ProductionInfo> productionIndex,
        List<SmartInfoIndexEntry> smartInfoIndex,
        List<Entry> entries
) {
    public static final int SMART_INFO_PRIORITY_REQUEST_LINK = 0;
    public static final int SMART_INFO_PRIORITY_EXACT = 10;
    public static final int SMART_INFO_PRIORITY_RESOURCE = 15;
    public static final int SMART_INFO_PRIORITY_OUTPUT = 20;
    public static final int SMART_INFO_PRIORITY_MATERIAL = 25;
    public static final int SMART_INFO_PRIORITY_FINGERPRINT = 30;
    public static final int SMART_INFO_PRIORITY_DISPLAY = 35;
    public static final int SMART_INFO_PRIORITY_TREE_PARENT = 40;
    public static final int SMART_INFO_PRIORITY_REQUEST_TOKEN = SMART_INFO_PRIORITY_REQUEST_LINK;
    public static final int SMART_INFO_PRIORITY_ALTERNATIVE = 70;
    public static final int SMART_INFO_PRIORITY_SEMANTIC = 80;
    public static final int SMART_INFO_PRIORITY_ITEM_ID = 100;

    public SmartClipboardReport(String colonyName, int colonyId, int buildingCount, int activeRequestCount, boolean capped, boolean importantOnly,
                                List<ItemStack> resourceScrolls, List<Entry> entries) {
        this(colonyName, colonyId, buildingCount, activeRequestCount, capped, importantOnly, resourceScrolls, ItemStack.EMPTY, entries);
    }

    public SmartClipboardReport(String colonyName, int colonyId, int buildingCount, int activeRequestCount, boolean capped, boolean importantOnly,
                                List<ItemStack> resourceScrolls, ItemStack colonyMap, List<Entry> entries) {
        this(colonyName, colonyId, buildingCount, activeRequestCount, capped, importantOnly, resourceScrolls, colonyMap, List.of(), buildSmartInfoIndex(entries), entries);
    }

    public static SmartClipboardReport fromAnalysis(RequestAnalysisService.AnalysisResult result) {
        return fromAnalysis(result, List.of(), false);
    }

    public static SmartClipboardReport fromAnalysis(RequestAnalysisService.AnalysisResult result, List<ItemStack> resourceScrolls) {
        return fromAnalysis(result, resourceScrolls, false);
    }

    public static SmartClipboardReport fromAnalysis(RequestAnalysisService.AnalysisResult result, List<ItemStack> resourceScrolls, boolean importantOnly) {
        return fromAnalysis(result, resourceScrolls, ItemStack.EMPTY, importantOnly);
    }

    public static SmartClipboardReport fromAnalysis(RequestAnalysisService.AnalysisResult result, List<ItemStack> resourceScrolls, ItemStack colonyMap, boolean importantOnly) {
        List<Entry> entries = new ArrayList<>();
        result.groupedEntries().values().forEach(group -> group.forEach(entry -> entries.add(fromAnalysisEntry(entry))));

        return new SmartClipboardReport(
                result.colonyName(),
                result.colonyId(),
                result.buildingCount(),
                result.activeRequestCount(),
                result.capped(),
                importantOnly,
                resourceScrolls.stream().map(ItemStack::copy).toList(),
                colonyMap.copy(),
                result.productionIndex().stream()
                        .map(SmartClipboardReport::fromProductionInfo)
                        .toList(),
                buildSmartInfoIndex(entries),
                entries
        );
    }

    private static ProductionInfo fromProductionInfo(RequestAnalysisService.ProductionInfo info) {
        return new ProductionInfo(
                info.stack().copy(),
                List.copyOf(info.knownBy()),
                List.copyOf(info.canLearn()),
                info.recipeId().map(Object::toString),
                info.doBlockId().map(Object::toString).orElse(""),
                info.keys().stream()
                        .map(key -> new SmartInfoKey(key.key(), key.priority()))
                        .toList()
        );
    }

    private static Entry fromAnalysisEntry(RequestAnalysisService.RequestReportEntry entry) {
        return new Entry(
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
                        .map(node -> new RequestTreeNode(
                                node.depth(),
                                node.stack().copy(),
                                node.count(),
                                node.quantityDisplay(),
                                node.label(),
                                node.requestToken(),
                                node.parentToken(),
                                node.requesterName(),
                                node.requesterLocation(),
                                node.requesterDimension(),
                                node.resolverName(),
                                node.requestType(),
                                node.synthetic()
                        ))
                        .toList(),
                entry.smartInfoKeys().stream()
                        .map(key -> new SmartInfoKey(key.key(), key.priority()))
                        .toList()
        );
    }

    public static SmartClipboardReport decode(RegistryFriendlyByteBuf buffer) {
        String colonyName = buffer.readUtf();
        int colonyId = buffer.readVarInt();
        int buildingCount = buffer.readVarInt();
        int activeRequestCount = buffer.readVarInt();
        boolean capped = buffer.readBoolean();
        boolean importantOnly = buffer.readBoolean();
        int scrollCount = buffer.readVarInt();
        List<ItemStack> resourceScrolls = new ArrayList<>(scrollCount);
        for (int i = 0; i < scrollCount; i++) {
            resourceScrolls.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
        }
        ItemStack colonyMap = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
        int productionCount = buffer.readVarInt();
        List<ProductionInfo> productionIndex = new ArrayList<>(productionCount);
        for (int i = 0; i < productionCount; i++) {
            productionIndex.add(ProductionInfo.decode(buffer));
        }
        int entryCount = buffer.readVarInt();
        List<Entry> entries = new ArrayList<>(entryCount);
        for (int i = 0; i < entryCount; i++) {
            entries.add(Entry.decode(buffer));
        }
        int indexCount = buffer.readVarInt();
        List<SmartInfoIndexEntry> smartInfoIndex = new ArrayList<>(indexCount);
        for (int i = 0; i < indexCount; i++) {
            smartInfoIndex.add(SmartInfoIndexEntry.decode(buffer));
        }
        return new SmartClipboardReport(colonyName, colonyId, buildingCount, activeRequestCount, capped, importantOnly, resourceScrolls, colonyMap, productionIndex, smartInfoIndex, entries);
    }

    public void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(colonyName);
        buffer.writeVarInt(colonyId);
        buffer.writeVarInt(buildingCount);
        buffer.writeVarInt(activeRequestCount);
        buffer.writeBoolean(capped);
        buffer.writeBoolean(importantOnly);
        buffer.writeVarInt(resourceScrolls.size());
        for (ItemStack scroll : resourceScrolls) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, scroll);
        }
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, colonyMap);
        buffer.writeVarInt(productionIndex.size());
        for (ProductionInfo productionInfo : productionIndex) {
            productionInfo.encode(buffer);
        }
        buffer.writeVarInt(entries.size());
        for (Entry entry : entries) {
            entry.encode(buffer);
        }
        buffer.writeVarInt(smartInfoIndex.size());
        for (SmartInfoIndexEntry indexEntry : smartInfoIndex) {
            indexEntry.encode(buffer);
        }
    }

    public static String exactStackKey(ItemStack stack) {
        return stack.isEmpty() ? "" : "exact:" + itemId(stack) + "|" + stack.getComponentsPatch();
    }

    public static String resourceStackKey(ItemStack stack) {
        return stack.isEmpty() ? "" : "resource:" + stack.getDescriptionId() + "-" + stack.getComponentsPatch().hashCode();
    }

    public static String resourceStackKey(String resourceKey) {
        if (resourceKey == null || resourceKey.isBlank()) {
            return "";
        }
        return resourceKey.startsWith("resource:") ? resourceKey : "resource:" + resourceKey;
    }

    public static String domumFingerprintKey(ItemStack stack) {
        return stack.isEmpty() || !DomumOrnamentumRequestInspector.isDomumOrnamentumStack(stack)
                ? ""
                : domumFingerprintKey(DomumOrnamentumRequestInspector.exactComboFingerprint(stack));
    }

    public static String domumFingerprintKey(String fingerprint) {
        return fingerprint == null || fingerprint.isBlank() ? "" : "fingerprint:" + fingerprint;
    }

    public static String domumMaterialKey(ItemStack stack) {
        return stack.isEmpty() ? "" : DomumOrnamentumRequestInspector.canonicalMaterialKey(stack)
                .map(SmartClipboardReport::domumMaterialKey)
                .orElse("");
    }

    public static String domumMaterialKey(String materialKey) {
        return materialKey == null || materialKey.isBlank() ? "" : "material:" + materialKey;
    }

    public static String recipeOutputKey(String recipeId, ItemStack output) {
        return recipeId == null || recipeId.isBlank() || output.isEmpty()
                ? ""
                : "recipe-output:" + recipeId + "|" + exactStackKey(output);
    }

    public static String recipeKey(String recipeId) {
        return recipeId == null || recipeId.isBlank() ? "" : "recipe:" + recipeId;
    }

    public static String requestTokenKey(String token) {
        return token == null || token.isBlank() ? "" : "request:" + token;
    }

    public static String treeParentStackKey(ItemStack stack) {
        String exact = exactStackKey(stack);
        return exact.isBlank() ? "" : "tree:" + exact;
    }

    public static String alternativeStackKey(ItemStack stack) {
        String exact = exactStackKey(stack);
        return exact.isBlank() ? "" : "alternative:" + exact;
    }

    public static String itemIdKey(ItemStack stack) {
        return stack.isEmpty() ? "" : "item:" + itemId(stack);
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static List<SmartInfoIndexEntry> buildSmartInfoIndex(List<Entry> entries) {
        List<SmartInfoIndexEntry> index = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < entries.size(); i++) {
            int entryIndex = i;
            Entry entry = entries.get(i);
            if (!isSmartInfoEntry(entry)) {
                continue;
            }
            addIndex(index, seen, entryIndex, domumFingerprintKey(entry.comboFingerprintFull()), SMART_INFO_PRIORITY_FINGERPRINT);
            addStackIndex(index, seen, entryIndex, entry.requestedStack(), SMART_INFO_PRIORITY_EXACT);
            for (ItemStack stack : entry.displayStacks()) {
                addStackIndex(index, seen, entryIndex, stack, SMART_INFO_PRIORITY_DISPLAY);
            }
            for (RequestTreeNode node : entry.requestTree()) {
                addIndex(index, seen, entryIndex, treeParentStackKey(node.stack()), SMART_INFO_PRIORITY_TREE_PARENT);
            }
            entry.requestToken().ifPresent(token -> addIndex(index, seen, entryIndex, requestTokenKey(token), SMART_INFO_PRIORITY_REQUEST_TOKEN));
            for (SmartInfoKey key : entry.smartInfoKeys()) {
                addIndex(index, seen, entryIndex, key.key(), key.priority());
            }
            addIndex(index, seen, entryIndex, itemIdKey(entry.requestedStack()), SMART_INFO_PRIORITY_ITEM_ID);
        }
        return List.copyOf(index);
    }

    private static void addStackIndex(List<SmartInfoIndexEntry> index, Set<String> seen, int entryIndex, ItemStack stack, int priority) {
        if (stack.isEmpty()) {
            return;
        }
        if (DomumOrnamentumRequestInspector.isDomumOrnamentumStack(stack)) {
            addIndex(index, seen, entryIndex, domumMaterialKey(stack), Math.max(priority, SMART_INFO_PRIORITY_MATERIAL));
            addIndex(index, seen, entryIndex, domumFingerprintKey(stack), Math.max(priority, SMART_INFO_PRIORITY_FINGERPRINT));
        }
        addIndex(index, seen, entryIndex, exactStackKey(stack), priority);
        addIndex(index, seen, entryIndex, resourceStackKey(stack), Math.max(priority, SMART_INFO_PRIORITY_RESOURCE));
    }

    private static void addIndex(List<SmartInfoIndexEntry> index, Set<String> seen, int entryIndex, String key, int priority) {
        if (key == null || key.isBlank() || !seen.add(entryIndex + "|" + priority + "|" + key)) {
            return;
        }
        index.add(new SmartInfoIndexEntry(key, entryIndex, priority));
    }

    public static boolean isSmartInfoEntry(Entry entry) {
        return entry.doBlockId().startsWith("domum_ornamentum:")
                && (entry.cutterRecipeId().isPresent()
                || DomumOrnamentumRequestInspector.isMaterializedArchitectsCutterOutput(entry.requestedStack())
                || DomumOrnamentumRequestInspector.hasArchitectsCutterMetadata(entry.requestedStack()));
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
            List<RequestTreeNode> requestTree,
            List<SmartInfoKey> smartInfoKeys
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
            int keyCount = buffer.readVarInt();
            List<SmartInfoKey> smartInfoKeys = new ArrayList<>(keyCount);
            for (int i = 0; i < keyCount; i++) {
                smartInfoKeys.add(SmartInfoKey.decode(buffer));
            }
            return new Entry(requestedStack, displayStacks, requestedCount, quantityDisplay, requestingBuildingName, requestingBuildingPos, requestingWorkerName,
                    dimensionName, resolverName, important, minimumStockRequest,
                    warehouseStock, doBlockId, cutterRecipeId, comboFingerprintShort, comboFingerprintFull,
                    exactComboAlreadyTaught, recipeKnownBy, canLearnCombo, requestToken, requestTree, smartInfoKeys);
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
            buffer.writeVarInt(smartInfoKeys.size());
            for (SmartInfoKey key : smartInfoKeys) {
                key.encode(buffer);
            }
        }
    }

    public record SmartInfoKey(String key, int priority) {
        private static SmartInfoKey decode(RegistryFriendlyByteBuf buffer) {
            return new SmartInfoKey(buffer.readUtf(), buffer.readVarInt());
        }

        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(key);
            buffer.writeVarInt(priority);
        }
    }

    public record SmartInfoIndexEntry(String key, int entryIndex, int priority) {
        private static SmartInfoIndexEntry decode(RegistryFriendlyByteBuf buffer) {
            return new SmartInfoIndexEntry(buffer.readUtf(), buffer.readVarInt(), buffer.readVarInt());
        }

        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(key);
            buffer.writeVarInt(entryIndex);
            buffer.writeVarInt(priority);
        }
    }

    public record ProductionInfo(
            ItemStack stack,
            List<String> knownBy,
            List<String> canLearn,
            Optional<String> recipeId,
            String doBlockId,
            List<SmartInfoKey> keys
    ) {
        private static ProductionInfo decode(RegistryFriendlyByteBuf buffer) {
            ItemStack stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
            List<String> knownBy = buffer.readList(FriendlyByteBuf::readUtf);
            List<String> canLearn = buffer.readList(FriendlyByteBuf::readUtf);
            Optional<String> recipeId = readOptionalString(buffer);
            String doBlockId = buffer.readUtf();
            int keyCount = buffer.readVarInt();
            List<SmartInfoKey> keys = new ArrayList<>(keyCount);
            for (int i = 0; i < keyCount; i++) {
                keys.add(SmartInfoKey.decode(buffer));
            }
            return new ProductionInfo(stack, knownBy, canLearn, recipeId, doBlockId, keys);
        }

        private void encode(RegistryFriendlyByteBuf buffer) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, stack);
            buffer.writeCollection(knownBy, FriendlyByteBuf::writeUtf);
            buffer.writeCollection(canLearn, FriendlyByteBuf::writeUtf);
            writeOptionalString(buffer, recipeId);
            buffer.writeUtf(doBlockId == null ? "" : doBlockId);
            buffer.writeVarInt(keys.size());
            for (SmartInfoKey key : keys) {
                key.encode(buffer);
            }
        }
    }

    public record RequestTreeNode(
            int depth,
            ItemStack stack,
            int count,
            String quantityDisplay,
            String label,
            Optional<String> requestToken,
            Optional<String> parentToken,
            Optional<String> requesterName,
            Optional<BlockPos> requesterLocation,
            Optional<String> requesterDimension,
            Optional<String> resolverName,
            String requestType,
            boolean synthetic
    ) {
        private static RequestTreeNode decode(RegistryFriendlyByteBuf buffer) {
            return new RequestTreeNode(
                    buffer.readVarInt(),
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                    buffer.readVarInt(),
                    buffer.readUtf(),
                    buffer.readUtf(),
                    readOptionalString(buffer),
                    readOptionalString(buffer),
                    readOptionalString(buffer),
                    readOptionalBlockPos(buffer),
                    readOptionalString(buffer),
                    readOptionalString(buffer),
                    buffer.readUtf(),
                    buffer.readBoolean()
            );
        }

        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(depth);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, stack);
            buffer.writeVarInt(count);
            buffer.writeUtf(quantityDisplay);
            buffer.writeUtf(label);
            writeOptionalString(buffer, requestToken);
            writeOptionalString(buffer, parentToken);
            writeOptionalString(buffer, requesterName);
            writeOptionalBlockPos(buffer, requesterLocation);
            writeOptionalString(buffer, requesterDimension);
            writeOptionalString(buffer, resolverName);
            buffer.writeUtf(requestType == null ? "" : requestType);
            buffer.writeBoolean(synthetic);
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
