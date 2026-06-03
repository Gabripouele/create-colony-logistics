package com.createcolonylogistics.client;

import com.createcolonylogistics.clipboard.DomumOrnamentumRequestInspector;
import com.createcolonylogistics.clipboard.SmartClipboardReport;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class SmartInfoResolver {
    private final SmartClipboardReport report;

    SmartInfoResolver(SmartClipboardReport report) {
        this.report = report;
    }

    Optional<SmartClipboardReport.Entry> resolveEntry(SmartClipboardReport.Entry entry) {
        if (entry == null) {
            return Optional.empty();
        }
        ItemStack stack = entry.requestedStack();
        if (DomumOrnamentumRequestInspector.isDomumOrnamentumStack(stack)) {
            SmartClipboardReport.Entry production = productionFallback(stack, stackKeys(stack)).orElse(null);
            if (production != null && hasProductionContext(production)) {
                return Optional.of(production);
            }
        }
        return isSmartInfoEntry(entry) ? Optional.of(entry) : productionFallback(stack, stackKeys(stack));
    }

    Optional<SmartClipboardReport.Entry> resolveStack(ItemStack stack) {
        SmartClipboardReport.Entry exact = resolveKeys(stackKeys(stack)).orElse(null);
        if (exact != null) {
            return Optional.of(exact);
        }
        if (DomumOrnamentumRequestInspector.isDomumOrnamentumStack(stack)) {
            SmartClipboardReport.Entry production = productionFallback(stack, stackKeys(stack)).orElse(null);
            if (production != null) {
                return Optional.of(production);
            }
        }
        SmartClipboardReport.Entry item = uniqueItemMatch(stack).orElse(null);
        return item != null ? Optional.of(item) : productionFallback(stack, stackKeys(stack));
    }

    Optional<SmartClipboardReport.Entry> resolveStack(ItemStack stack, SmartClipboardReport.Entry parentEntry) {
        SmartClipboardReport.Entry exact = resolveKeys(stackKeys(stack)).orElse(null);
        if (exact != null) {
            return Optional.of(exact);
        }
        if (DomumOrnamentumRequestInspector.isDomumOrnamentumStack(stack)) {
            SmartClipboardReport.Entry production = productionFallback(stack, stackKeys(stack)).orElse(null);
            if (production != null) {
                return Optional.of(production);
            }
        }
        if (isSmartInfoEntry(parentEntry)) {
            return Optional.of(parentEntry);
        }
        SmartClipboardReport.Entry item = uniqueItemMatch(stack).orElse(null);
        return item != null ? Optional.of(item) : productionFallback(stack, stackKeys(stack));
    }

    Optional<SmartClipboardReport.Entry> resolveResource(ResourceLookup lookup) {
        SmartClipboardReport.Entry exact = resolveKeys(lookup.keys()).orElse(null);
        if (exact != null) {
            return Optional.of(exact);
        }
        if (DomumOrnamentumRequestInspector.isDomumOrnamentumStack(lookup.stack())) {
            SmartClipboardReport.Entry production = productionFallback(lookup.stack(), lookup.keys()).orElse(null);
            if (production != null) {
                return Optional.of(production);
            }
        }
        SmartClipboardReport.Entry semantic = uniqueSemanticDomumMatch(lookup.stack()).orElse(null);
        if (semantic != null) {
            return Optional.of(semantic);
        }
        SmartClipboardReport.Entry item = uniqueItemMatch(lookup.stack()).orElse(null);
        return item != null ? Optional.of(item) : productionFallback(lookup.stack(), lookup.keys());
    }

    static List<SmartClipboardReport.SmartInfoKey> resourceRowKeys(ItemStack stack, String resourceKey) {
        List<SmartClipboardReport.SmartInfoKey> keys = new ArrayList<>(stackKeys(stack));
        addKey(keys, SmartClipboardReport.resourceStackKey(resourceKey), SmartClipboardReport.SMART_INFO_PRIORITY_RESOURCE);
        Level level = Minecraft.getInstance().level;
        if (level != null && DomumOrnamentumRequestInspector.isDomumOrnamentumStack(stack)) {
            DomumOrnamentumRequestInspector.findArchitectsCutterMatch(level, stack).ifPresent(match -> {
                addStackKeys(keys, match.assembledOutput(), SmartClipboardReport.SMART_INFO_PRIORITY_OUTPUT);
                addKey(keys, SmartClipboardReport.recipeKey(match.recipeId().toString()), SmartClipboardReport.SMART_INFO_PRIORITY_OUTPUT);
                addKey(keys, SmartClipboardReport.recipeOutputKey(match.recipeId().toString(), match.assembledOutput()), SmartClipboardReport.SMART_INFO_PRIORITY_OUTPUT);
            });
        }
        return keys.stream().distinct().toList();
    }

    private Optional<SmartClipboardReport.Entry> productionFallback(ItemStack stack, List<SmartClipboardReport.SmartInfoKey> keys) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        ProductionMatch match = resolveProduction(stack, keys).orElse(null);
        if (match == null) {
            return Optional.empty();
        }
        SmartClipboardReport.ProductionInfo production = match.production();
        boolean domumFallback = DomumOrnamentumRequestInspector.isDomumOrnamentumStack(stack);
        boolean exactDomumProduction = isExactDomumProductionMatch(match, stack);
        List<String> knownBy = domumFallback && !exactDomumProduction ? List.of() : production.knownBy();
        List<String> canLearn = domumFallback && !exactDomumProduction ? List.of() : production.canLearn();
        boolean hasProductionContext = !knownBy.isEmpty() || !canLearn.isEmpty();
        boolean hasDomumShapeContext = domumFallback
                && (production.recipeId().isPresent() || (production.doBlockId() != null && !production.doBlockId().isBlank()));
        if (!hasProductionContext && !hasDomumShapeContext) {
            return Optional.empty();
        }
        return Optional.of(fallbackEntry(stack, production, knownBy, canLearn));
    }

    private Optional<ProductionMatch> resolveProduction(ItemStack stack, List<SmartClipboardReport.SmartInfoKey> keys) {
        ProductionMatch best = null;
        ProductionRank bestRank = null;
        for (SmartClipboardReport.SmartInfoKey key : keys) {
            if (key.key() == null || key.key().isBlank()) {
                continue;
            }
            for (SmartClipboardReport.ProductionInfo production : report.productionIndex()) {
                for (SmartClipboardReport.SmartInfoKey productionKey : production.keys()) {
                    if (!key.key().equals(productionKey.key())) {
                        continue;
                    }
                    int priority = Math.max(key.priority(), productionKey.priority());
                    ProductionMatch candidate = new ProductionMatch(production, key.key(), priority);
                    ProductionRank rank = productionRank(candidate, stack);
                    if (bestRank == null || rank.compareTo(bestRank) < 0) {
                        best = candidate;
                        bestRank = rank;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean isExactDomumProductionMatch(ProductionMatch match, ItemStack stack) {
        return isConcreteProductionKey(match.key());
    }

    private static int productionSourceScore(ProductionMatch match) {
        String key = match.key();
        if (isConcreteProductionKey(key)) {
            return 0;
        }
        if (key != null && key.startsWith("recipe:")) {
            return 10;
        }
        if (key != null && key.startsWith("item:")) {
            return 20;
        }
        return 30;
    }

    private static ProductionRank productionRank(ProductionMatch match, ItemStack stack) {
        boolean domum = DomumOrnamentumRequestInspector.isDomumOrnamentumStack(stack);
        boolean exactDomum = !domum || isExactDomumProductionMatch(match, stack);
        boolean context = !match.production().knownBy().isEmpty() || !match.production().canLearn().isEmpty();
        return new ProductionRank(
                exactDomum ? productionSourceScore(match) : productionSourceScore(match) + 100,
                context ? 0 : 1,
                match.priority()
        );
    }

    private static boolean isConcreteProductionKey(String key) {
        return key != null
                && (key.startsWith("exact:")
                || key.startsWith("resource:")
                || key.startsWith("material:")
                || key.startsWith("fingerprint:")
                || key.startsWith("recipe-output:"));
    }

    private static boolean hasProductionContext(SmartClipboardReport.Entry entry) {
        return entry != null && (!entry.recipeKnownBy().isEmpty() || !entry.canLearnCombo().isEmpty());
    }

    private SmartClipboardReport.Entry fallbackEntry(ItemStack stack, SmartClipboardReport.ProductionInfo production,
                                                    List<String> knownBy, List<String> canLearn) {
        ItemStack shown = stack.copyWithCount(Math.max(1, stack.getCount()));
        String doBlockId = production.doBlockId() == null ? "" : production.doBlockId();
        String fingerprint = DomumOrnamentumRequestInspector.isDomumOrnamentumStack(shown)
                ? DomumOrnamentumRequestInspector.exactComboFingerprint(shown)
                : "";
        return new SmartClipboardReport.Entry(
                shown.copy(),
                List.of(shown.copy()),
                Math.max(1, shown.getCount()),
                "x" + Math.max(1, shown.getCount()),
                "",
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                false,
                false,
                0,
                doBlockId,
                production.recipeId(),
                fingerprint,
                fingerprint,
                !knownBy.isEmpty(),
                knownBy,
                canLearn,
                Optional.empty(),
                List.of(),
                production.keys()
        );
    }

    private Optional<SmartClipboardReport.Entry> resolveKeys(List<SmartClipboardReport.SmartInfoKey> keys) {
        int bestEntry = -1;
        int bestPriority = Integer.MAX_VALUE;
        boolean ambiguous = false;
        for (SmartClipboardReport.SmartInfoKey key : keys) {
            if (key.key() == null || key.key().isBlank()) {
                continue;
            }
            for (SmartClipboardReport.SmartInfoIndexEntry indexEntry : report.smartInfoIndex()) {
                if (!key.key().equals(indexEntry.key()) || indexEntry.entryIndex() < 0 || indexEntry.entryIndex() >= report.entries().size()) {
                    continue;
                }
                SmartClipboardReport.Entry entry = report.entries().get(indexEntry.entryIndex());
                if (!isSmartInfoEntry(entry)) {
                    continue;
                }
                int priority = Math.max(key.priority(), indexEntry.priority());
                if (priority < bestPriority) {
                    bestEntry = indexEntry.entryIndex();
                    bestPriority = priority;
                    ambiguous = false;
                } else if (priority == bestPriority && bestEntry != indexEntry.entryIndex()) {
                    ambiguous = true;
                }
            }
        }
        return !ambiguous && bestEntry >= 0 ? Optional.of(report.entries().get(bestEntry)) : Optional.empty();
    }

    private Optional<SmartClipboardReport.Entry> uniqueSemanticDomumMatch(ItemStack stack) {
        if (stack.isEmpty() || !DomumOrnamentumRequestInspector.hasArchitectsCutterMetadata(stack)) {
            return Optional.empty();
        }
        SmartClipboardReport.Entry result = null;
        for (SmartClipboardReport.Entry entry : report.entries()) {
            if (!isSmartInfoEntry(entry) || !entryMatchesStack(entry, stack)) {
                continue;
            }
            if (result != null && result != entry) {
                return Optional.empty();
            }
            result = entry;
        }
        return Optional.ofNullable(result);
    }

    private Optional<SmartClipboardReport.Entry> uniqueItemMatch(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        SmartClipboardReport.Entry result = null;
        for (SmartClipboardReport.SmartInfoIndexEntry indexEntry : report.smartInfoIndex()) {
            if (!SmartClipboardReport.itemIdKey(stack).equals(indexEntry.key())
                    || indexEntry.entryIndex() < 0
                    || indexEntry.entryIndex() >= report.entries().size()) {
                continue;
            }
            SmartClipboardReport.Entry entry = report.entries().get(indexEntry.entryIndex());
            if (!isSmartInfoEntry(entry)) {
                continue;
            }
            if (result != null && result != entry) {
                return Optional.empty();
            }
            result = entry;
        }
        return Optional.ofNullable(result);
    }

    private boolean entryMatchesStack(SmartClipboardReport.Entry entry, ItemStack stack) {
        if (DomumOrnamentumRequestInspector.sameMaterializedDomumOutput(stack, entry.requestedStack())) {
            return true;
        }
        for (ItemStack displayStack : entry.displayStacks()) {
            if (DomumOrnamentumRequestInspector.sameMaterializedDomumOutput(stack, displayStack)) {
                return true;
            }
        }
        for (SmartClipboardReport.RequestTreeNode node : entry.requestTree()) {
            if (DomumOrnamentumRequestInspector.sameMaterializedDomumOutput(stack, node.stack())) {
                return true;
            }
        }
        return false;
    }

    private static List<SmartClipboardReport.SmartInfoKey> stackKeys(ItemStack stack) {
        List<SmartClipboardReport.SmartInfoKey> keys = new ArrayList<>();
        addStackKeys(keys, stack, SmartClipboardReport.SMART_INFO_PRIORITY_EXACT);
        return keys;
    }

    private static void addStackKeys(List<SmartClipboardReport.SmartInfoKey> keys, ItemStack stack, int priority) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        addKey(keys, SmartClipboardReport.exactStackKey(stack), priority);
        addKey(keys, SmartClipboardReport.resourceStackKey(stack), Math.max(priority, SmartClipboardReport.SMART_INFO_PRIORITY_RESOURCE));
        if (DomumOrnamentumRequestInspector.isDomumOrnamentumStack(stack)) {
            addKey(keys, SmartClipboardReport.domumMaterialKey(stack), Math.max(priority, SmartClipboardReport.SMART_INFO_PRIORITY_MATERIAL));
            addKey(keys, SmartClipboardReport.domumFingerprintKey(stack), Math.max(priority, SmartClipboardReport.SMART_INFO_PRIORITY_FINGERPRINT));
        }
    }

    private static void addKey(List<SmartClipboardReport.SmartInfoKey> keys, String key, int priority) {
        if (key != null && !key.isBlank()) {
            keys.add(new SmartClipboardReport.SmartInfoKey(key, priority));
        }
    }

    private static boolean isSmartInfoEntry(SmartClipboardReport.Entry entry) {
        return entry != null && SmartClipboardReport.isSmartInfoEntry(entry);
    }

    record ResourceLookup(ItemStack stack, List<SmartClipboardReport.SmartInfoKey> keys) {
    }

    private record ProductionMatch(SmartClipboardReport.ProductionInfo production, String key, int priority) {
    }

    private record ProductionRank(int sourceScore, int contextPenalty, int priority) implements Comparable<ProductionRank> {
        @Override
        public int compareTo(ProductionRank other) {
            int source = Integer.compare(sourceScore, other.sourceScore);
            if (source != 0) {
                return source;
            }
            int context = Integer.compare(contextPenalty, other.contextPenalty);
            if (context != 0) {
                return context;
            }
            return Integer.compare(priority, other.priority);
        }
    }
}
