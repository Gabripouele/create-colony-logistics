package com.createcolonylogistics.clipboard;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import com.minecolonies.api.colony.IColony;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

// DIAGNOSTIC ONLY - remove after tooltip source-selection audit
public final class SmartInfoTooltipSourceDiagnosticDumper {
    public static final boolean ENABLE_SMART_INFO_TOOLTIP_SOURCE_DIAGNOSTICS = false;

    private static final Path OUTPUT_DIR = Path.of("run", "smart-info-tooltip-source-diagnostics");
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private SmartInfoTooltipSourceDiagnosticDumper() {
    }

    public static SmartClipboardReport emptyReport() {
        return new SmartClipboardReport("", 0, 0, 0, false, false, List.of(), ItemStack.EMPTY, List.of());
    }

    public static Optional<Path> dump(
            ServerPlayer player,
            IColony colony,
            SmartClipboardReport report,
            ItemStack hoveredStack,
            String hoverPath,
            String context,
            String resourceKey,
            List<SmartClipboardReport.SmartInfoKey> hoverKeys,
            int directEntryIndex,
            int parentEntryIndex
    ) throws IOException {
        if (!ENABLE_SMART_INFO_TOOLTIP_SOURCE_DIAGNOSTICS || player == null || colony == null || report == null
                || hoveredStack == null || hoveredStack.isEmpty()) {
            return Optional.empty();
        }

        Files.createDirectories(OUTPUT_DIR);
        String itemName = sanitize(BuiltInRegistries.ITEM.getKey(hoveredStack.getItem()).getPath());
        Path path = OUTPUT_DIR.resolve(LocalDateTime.now().format(FILE_TIME) + "-" + itemName + "-tooltip-source.md");
        Files.writeString(path, buildReport(player, colony, report, hoveredStack.copyWithCount(1), hoverPath, context,
                resourceKey, hoverKeys == null ? List.of() : hoverKeys, directEntryIndex, parentEntryIndex));
        return Optional.of(path);
    }

    private static String buildReport(
            ServerPlayer player,
            IColony colony,
            SmartClipboardReport report,
            ItemStack stack,
            String hoverPath,
            String context,
            String resourceKey,
            List<SmartClipboardReport.SmartInfoKey> hoverKeys,
            int directEntryIndex,
            int parentEntryIndex
    ) {
        SmartInfoClassificationService.Classification fresh =
                SmartInfoClassificationService.classify(colony, player.serverLevel(), stack);
        List<SmartClipboardReport.SmartInfoKey> resolverKeys = resolverKeysForHover(stack, hoverPath, hoverKeys);
        ResolverTrace trace = traceResolver(report, stack, hoverPath, resolverKeys, directEntryIndex, parentEntryIndex);
        List<EntryMatch> entryMatches = matchingEntries(report, stack, resolverKeys);
        List<ProductionCandidate> productionMatches = productionCandidates(report, stack, resolverKeys);

        StringBuilder out = new StringBuilder();
        out.append("# Smart Info Tooltip Source Diagnostic\n\n");
        out.append("Generated: ").append(LocalDateTime.now()).append("\n\n");
        out.append("## Hover Context\n\n");
        out.append("- hover path: ").append(blank(hoverPath)).append('\n');
        out.append("- context: ").append(blank(context)).append('\n');
        out.append("- resource key: ").append(blank(resourceKey)).append('\n');
        out.append("- hovered item id: ").append(itemId(stack)).append('\n');
        out.append("- display name: ").append(stack.getHoverName().getString()).append('\n');
        out.append("- exact stack key: `").append(SmartClipboardReport.exactStackKey(stack)).append("`\n");
        out.append("- resource stack key: `").append(SmartClipboardReport.resourceStackKey(stack)).append("`\n");
        out.append("- material key: `").append(SmartClipboardReport.domumMaterialKey(stack)).append("`\n");
        out.append("- fingerprint key: `").append(SmartClipboardReport.domumFingerprintKey(stack)).append("`\n");
        fresh.cutterMatch().ifPresent(match -> out.append("- recipe-output key: `")
                .append(SmartClipboardReport.recipeOutputKey(match.recipeId().toString(), match.assembledOutput()))
                .append("`\n"));
        out.append("\n## Fresh Classifier Result\n\n");
        out.append("- status: ").append(fresh.status()).append('\n');
        out.append("- reason: ").append(fresh.reason()).append('\n');
        out.append("- recipe id: ").append(fresh.recipeId().map(Object::toString).orElse("none")).append('\n');
        out.append("- do block id: ").append(fresh.doBlockId().map(Object::toString).orElse("none")).append('\n');
        out.append("- knownBy: ").append(fresh.knownBy()).append('\n');
        out.append("- canLearn: ").append(fresh.canLearn()).append('\n');
        out.append("- serialized exact equivalent found: ").append(serializedEquivalentFound(fresh, entryMatches, productionMatches)).append("\n\n");

        out.append("## Serialized Matching Entries\n\n");
        if (entryMatches.isEmpty()) {
            out.append("No serialized report entries matched the resolver keys or DO semantic comparison.\n\n");
        } else {
            for (EntryMatch match : entryMatches) {
                appendEntryCandidate(out, match.source(), match.matchedKey(), match.priority(), match.entry(), match.note());
            }
        }

        out.append("## Serialized Matching Production Entries\n\n");
        if (productionMatches.isEmpty()) {
            out.append("No serialized production entries matched the resolver keys.\n\n");
        } else {
            for (ProductionCandidate candidate : productionMatches) {
                appendProductionCandidate(out, candidate);
            }
        }

        out.append("## Resolver Candidate Trace\n\n");
        for (Candidate candidate : trace.candidates()) {
            out.append("### ").append(candidate.source()).append('\n');
            out.append("- matched key: `").append(blank(candidate.key())).append("`\n");
            out.append("- priority: ").append(candidate.priority()).append('\n');
            out.append("- decision: ").append(candidate.decision()).append('\n');
            out.append("- note: ").append(candidate.note()).append('\n');
            out.append("- ").append(candidate.decision()).append(" because: ").append(candidate.note()).append('\n');
            candidate.entry().ifPresent(entry -> appendEntryFields(out, entry));
            candidate.production().ifPresent(production -> appendProductionFields(out, production, stack, candidate.key(), candidate.priority()));
            out.append('\n');
        }

        out.append("## Selected Tooltip Entry\n\n");
        if (trace.selected().isEmpty()) {
            out.append("No Smart Info entry selected; vanilla tooltip would render.\n\n");
        } else {
            Candidate selected = trace.selected().get();
            out.append("- selected source: ").append(selected.source()).append('\n');
            out.append("- selected key: `").append(blank(selected.key())).append("`\n");
            out.append("- selected priority: ").append(selected.priority()).append('\n');
            out.append("- selected note: ").append(selected.note()).append('\n');
            selected.entry().ifPresent(entry -> {
                appendEntryFields(out, entry);
                out.append("- final teaching list: ").append(teachingFeedbackBuildings(entry)).append('\n');
                out.append("- final visible Smart Info lines: ").append(visibleSmartInfoLines(entry)).append('\n');
            });
            selected.production().ifPresent(production -> out.append("- production selected without fallback entry materialization: ")
                    .append(production.stack().getHoverName().getString()).append('\n'));
            out.append('\n');
        }

        out.append("## Divergence Conclusion\n\n");
        out.append(divergenceConclusion(fresh, trace.selected())).append('\n');
        return out.toString();
    }

    private static List<SmartClipboardReport.SmartInfoKey> resolverKeysForHover(
            ItemStack stack,
            String hoverPath,
            List<SmartClipboardReport.SmartInfoKey> hoverKeys
    ) {
        if ("resource-scroll".equals(hoverPath) && hoverKeys != null && !hoverKeys.isEmpty()) {
            return hoverKeys;
        }
        return stackKeys(stack);
    }

    private static ResolverTrace traceResolver(
            SmartClipboardReport report,
            ItemStack stack,
            String hoverPath,
            List<SmartClipboardReport.SmartInfoKey> keys,
            int directEntryIndex,
            int parentEntryIndex
    ) {
        List<Candidate> candidates = new ArrayList<>();
        Optional<Candidate> selected = Optional.empty();

        if ("main-row".equals(hoverPath)) {
            Optional<Candidate> production = DomumOrnamentumRequestInspector.isDomumOrnamentumStack(stack)
                    ? productionFallbackCandidate(report, stack, keys, candidates)
                    : Optional.empty();
            if (production.isPresent() && production.get().entry().map(SmartInfoTooltipSourceDiagnosticDumper::hasProductionContext).orElse(false)) {
                return new ResolverTrace(candidates, production);
            }
            Optional<SmartClipboardReport.Entry> direct = entryAt(report, directEntryIndex);
            if (direct.isPresent()) {
                boolean accepted = SmartClipboardReport.isSmartInfoEntry(direct.get());
                Candidate candidate = Candidate.entry("direct row entry", "", SmartClipboardReport.SMART_INFO_PRIORITY_EXACT,
                        accepted ? "accepted" : "rejected", accepted ? "direct Smart Info entry wins" : "row is not Smart Info entry", direct.get());
                candidates.add(candidate);
                if (accepted) {
                    return new ResolverTrace(candidates, Optional.of(candidate));
                }
            } else {
                candidates.add(Candidate.note("direct row entry", "none", Integer.MAX_VALUE, "rejected", "no direct row index supplied"));
            }
            return production.isPresent() ? new ResolverTrace(candidates, production) : productionFallbackTrace(report, stack, keys, candidates);
        }

        Optional<Candidate> exact = indexedTrace(report, keys, candidates);
        if (exact.isPresent()) {
            return new ResolverTrace(candidates, exact);
        }

        if (DomumOrnamentumRequestInspector.isDomumOrnamentumStack(stack)) {
            Optional<Candidate> production = productionFallbackCandidate(report, stack, keys, candidates);
            if (production.isPresent()) {
                return new ResolverTrace(candidates, production);
            }
        }

        if ("request-tree".equals(hoverPath)) {
            Optional<SmartClipboardReport.Entry> parent = entryAt(report, parentEntryIndex);
            if (parent.isPresent()) {
                boolean accepted = SmartClipboardReport.isSmartInfoEntry(parent.get());
                Candidate candidate = Candidate.entry("parent entry", "", SmartClipboardReport.SMART_INFO_PRIORITY_TREE_PARENT,
                        accepted ? "accepted" : "rejected", accepted ? "parent Smart Info entry wins after exact miss" : "parent is not Smart Info entry", parent.get());
                candidates.add(candidate);
                if (accepted) {
                    return new ResolverTrace(candidates, Optional.of(candidate));
                }
            }
        }

        if ("resource-scroll".equals(hoverPath)) {
            Optional<Candidate> semantic = semanticTrace(report, stack, candidates);
            if (semantic.isPresent()) {
                return new ResolverTrace(candidates, semantic);
            }
        }

        Optional<Candidate> item = itemTrace(report, stack, candidates);
        if (item.isPresent()) {
            selected = item;
            return new ResolverTrace(candidates, selected);
        }

        return productionFallbackTrace(report, stack, keys, candidates);
    }

    private static ResolverTrace productionFallbackTrace(
            SmartClipboardReport report,
            ItemStack stack,
            List<SmartClipboardReport.SmartInfoKey> keys,
            List<Candidate> candidates
    ) {
        ProductionResolveResult production = resolveProduction(report, stack, keys);
        if (production.selected().isEmpty()) {
            candidates.add(Candidate.note("production fallback", production.matchedKey(), production.priority(), "rejected", production.reason()));
            return new ResolverTrace(candidates, Optional.empty());
        }

        ProductionCandidate match = production.selected().get();
        boolean domumFallback = DomumOrnamentumRequestInspector.isDomumOrnamentumStack(stack);
        boolean exactDomumProduction = isExactDomumProductionMatch(match, stack);
        List<String> knownBy = domumFallback && !exactDomumProduction ? List.of() : match.production().knownBy();
        List<String> canLearn = domumFallback && !exactDomumProduction ? List.of() : match.production().canLearn();
        boolean hasProductionContext = !knownBy.isEmpty() || !canLearn.isEmpty();
        boolean hasDomumShapeContext = domumFallback
                && (match.production().recipeId().isPresent()
                || (match.production().doBlockId() != null && !match.production().doBlockId().isBlank()));
        if (!hasProductionContext && !hasDomumShapeContext) {
            candidates.add(Candidate.production("production fallback", match.matchedKey(), match.priority(), "rejected",
                    "matched production entry has no usable production or DO shape context after DO exactness filter", match.production()));
            return new ResolverTrace(candidates, Optional.empty());
        }

        SmartClipboardReport.Entry fallback = fallbackEntry(stack, match.production(), knownBy, canLearn);
        Candidate candidate = Candidate.entry("production fallback", match.matchedKey(), match.priority(), "accepted",
                exactDomumProduction ? "exact production fallback selected" : "shape-only or non-DO production fallback selected", fallback);
        candidates.add(candidate);
        return new ResolverTrace(candidates, Optional.of(candidate));
    }

    private static Optional<Candidate> productionFallbackCandidate(
            SmartClipboardReport report,
            ItemStack stack,
            List<SmartClipboardReport.SmartInfoKey> keys,
            List<Candidate> candidates
    ) {
        ResolverTrace trace = productionFallbackTrace(report, stack, keys, candidates);
        return trace.selected();
    }

    private static Optional<Candidate> indexedTrace(
            SmartClipboardReport report,
            List<SmartClipboardReport.SmartInfoKey> keys,
            List<Candidate> candidates
    ) {
        EntryResolveResult result = resolveIndexed(report, keys);
        result.candidates().forEach(candidates::add);
        return result.selected();
    }

    private static EntryResolveResult resolveIndexed(SmartClipboardReport report, List<SmartClipboardReport.SmartInfoKey> keys) {
        List<Candidate> candidates = new ArrayList<>();
        Candidate best = null;
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
                int priority = Math.max(key.priority(), indexEntry.priority());
                boolean smart = SmartClipboardReport.isSmartInfoEntry(entry);
                Candidate candidate = Candidate.entry("indexed key match", key.key(), priority,
                        smart ? "candidate" : "rejected", smart ? "matched smartInfoIndex entry" : "indexed entry is not Smart Info", entry);
                candidates.add(candidate);
                if (!smart) {
                    continue;
                }
                if (priority < bestPriority) {
                    best = candidate;
                    bestPriority = priority;
                    ambiguous = false;
                } else if (priority == bestPriority && best != null && best.entry().orElse(null) != entry) {
                    ambiguous = true;
                }
            }
        }
        if (ambiguous) {
            candidates.add(Candidate.note("indexed key match", "", bestPriority, "rejected", "ambiguous equal-priority indexed matches"));
            return new EntryResolveResult(candidates, Optional.empty());
        }
        if (best != null) {
            Candidate selected = best.withDecision("accepted", "best non-ambiguous indexed key match");
            return new EntryResolveResult(candidates, Optional.of(selected));
        }
        candidates.add(Candidate.note("indexed key match", "", Integer.MAX_VALUE, "rejected", "no matching indexed entry"));
        return new EntryResolveResult(candidates, Optional.empty());
    }

    private static Optional<Candidate> semanticTrace(SmartClipboardReport report, ItemStack stack, List<Candidate> candidates) {
        if (stack.isEmpty() || !DomumOrnamentumRequestInspector.hasArchitectsCutterMetadata(stack)) {
            candidates.add(Candidate.note("semantic DO match", "", Integer.MAX_VALUE, "rejected", "hovered stack lacks Architects Cutter metadata"));
            return Optional.empty();
        }
        SmartClipboardReport.Entry result = null;
        for (SmartClipboardReport.Entry entry : report.entries()) {
            if (!SmartClipboardReport.isSmartInfoEntry(entry) || !entryMatchesStack(entry, stack)) {
                continue;
            }
            Candidate candidate = Candidate.entry("semantic DO match", "sameMaterializedDomumOutput", SmartClipboardReport.SMART_INFO_PRIORITY_SEMANTIC,
                    "candidate", "entry semantically matches hovered DO stack", entry);
            candidates.add(candidate);
            if (result != null && result != entry) {
                candidates.add(Candidate.note("semantic DO match", "", SmartClipboardReport.SMART_INFO_PRIORITY_SEMANTIC,
                        "rejected", "ambiguous semantic DO matches"));
                return Optional.empty();
            }
            result = entry;
        }
        if (result == null) {
            candidates.add(Candidate.note("semantic DO match", "", Integer.MAX_VALUE, "rejected", "no semantic DO entry matched"));
            return Optional.empty();
        }
        Candidate selected = Candidate.entry("semantic DO match", "sameMaterializedDomumOutput", SmartClipboardReport.SMART_INFO_PRIORITY_SEMANTIC,
                "accepted", "unique semantic DO match", result);
        candidates.add(selected);
        return Optional.of(selected);
    }

    private static Optional<Candidate> itemTrace(SmartClipboardReport report, ItemStack stack, List<Candidate> candidates) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        String itemKey = SmartClipboardReport.itemIdKey(stack);
        SmartClipboardReport.Entry result = null;
        for (SmartClipboardReport.SmartInfoIndexEntry indexEntry : report.smartInfoIndex()) {
            if (!itemKey.equals(indexEntry.key()) || indexEntry.entryIndex() < 0 || indexEntry.entryIndex() >= report.entries().size()) {
                continue;
            }
            SmartClipboardReport.Entry entry = report.entries().get(indexEntry.entryIndex());
            if (!SmartClipboardReport.isSmartInfoEntry(entry)) {
                candidates.add(Candidate.entry("unique item match", itemKey, SmartClipboardReport.SMART_INFO_PRIORITY_ITEM_ID,
                        "rejected", "item-id entry is not Smart Info", entry));
                continue;
            }
            candidates.add(Candidate.entry("unique item match", itemKey, SmartClipboardReport.SMART_INFO_PRIORITY_ITEM_ID,
                    "candidate", "item-id Smart Info match", entry));
            if (result != null && result != entry) {
                candidates.add(Candidate.note("unique item match", itemKey, SmartClipboardReport.SMART_INFO_PRIORITY_ITEM_ID,
                        "rejected", "ambiguous item-id matches"));
                return Optional.empty();
            }
            result = entry;
        }
        if (result == null) {
            candidates.add(Candidate.note("unique item match", itemKey, SmartClipboardReport.SMART_INFO_PRIORITY_ITEM_ID, "rejected", "no unique item match"));
            return Optional.empty();
        }
        Candidate selected = Candidate.entry("unique item match", itemKey, SmartClipboardReport.SMART_INFO_PRIORITY_ITEM_ID,
                "accepted", "unique item-id match", result);
        candidates.add(selected);
        return Optional.of(selected);
    }

    private static ProductionResolveResult resolveProduction(SmartClipboardReport report, ItemStack stack, List<SmartClipboardReport.SmartInfoKey> keys) {
        ProductionCandidate best = null;
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
                    ProductionCandidate candidate = new ProductionCandidate(production, key.key(), priority, "matched production key");
                    ProductionRank rank = productionRank(candidate, stack);
                    if (bestRank == null || rank.compareTo(bestRank) < 0) {
                        best = candidate;
                        bestRank = rank;
                    }
                }
            }
        }
        if (best == null) {
            return new ProductionResolveResult(Optional.empty(), "", Integer.MAX_VALUE, "no matching production fallback entry");
        }
        return new ProductionResolveResult(Optional.of(best), best.matchedKey(), best.priority(), "best production fallback match");
    }

    private static List<EntryMatch> matchingEntries(SmartClipboardReport report, ItemStack stack, List<SmartClipboardReport.SmartInfoKey> keys) {
        List<EntryMatch> matches = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (SmartClipboardReport.SmartInfoKey key : keys) {
            for (SmartClipboardReport.SmartInfoIndexEntry indexEntry : report.smartInfoIndex()) {
                if (!key.key().equals(indexEntry.key()) || indexEntry.entryIndex() < 0 || indexEntry.entryIndex() >= report.entries().size()) {
                    continue;
                }
                String seenKey = indexEntry.entryIndex() + "|" + key.key() + "|" + indexEntry.priority();
                if (seen.add(seenKey)) {
                    matches.add(new EntryMatch(report.entries().get(indexEntry.entryIndex()), key.key(),
                            Math.max(key.priority(), indexEntry.priority()), "smartInfoIndex", "matched serialized smartInfoIndex key"));
                }
            }
        }
        for (int i = 0; i < report.entries().size(); i++) {
            SmartClipboardReport.Entry entry = report.entries().get(i);
            if (SmartClipboardReport.isSmartInfoEntry(entry) && entryMatchesStack(entry, stack) && seen.add(i + "|semantic")) {
                matches.add(new EntryMatch(entry, "sameMaterializedDomumOutput", SmartClipboardReport.SMART_INFO_PRIORITY_SEMANTIC,
                        "semantic", "matched by sameMaterializedDomumOutput"));
            }
        }
        return matches;
    }

    private static List<ProductionCandidate> productionCandidates(SmartClipboardReport report, ItemStack stack, List<SmartClipboardReport.SmartInfoKey> keys) {
        List<ProductionCandidate> matches = new ArrayList<>();
        for (SmartClipboardReport.SmartInfoKey key : keys) {
            for (SmartClipboardReport.ProductionInfo production : report.productionIndex()) {
                for (SmartClipboardReport.SmartInfoKey productionKey : production.keys()) {
                    if (key.key().equals(productionKey.key())) {
                        int priority = Math.max(key.priority(), productionKey.priority());
                        boolean exact = isExactDomumProductionMatch(new ProductionCandidate(production, key.key(), priority, ""), stack);
                        matches.add(new ProductionCandidate(production, key.key(), priority,
                                exact ? "exact production match" : "generic/weak production match"));
                    }
                }
            }
        }
        return matches;
    }

    private static boolean serializedEquivalentFound(
            SmartInfoClassificationService.Classification fresh,
            List<EntryMatch> entries,
            List<ProductionCandidate> productions
    ) {
        return entries.stream().anyMatch(match -> sameLists(match.entry().recipeKnownBy(), fresh.knownBy())
                && sameLists(match.entry().canLearnCombo(), fresh.canLearn()))
                || productions.stream().anyMatch(match -> sameLists(match.production().knownBy(), fresh.knownBy())
                && sameLists(match.production().canLearn(), fresh.canLearn()));
    }

    private static boolean sameLists(List<String> first, List<String> second) {
        return Set.copyOf(first).equals(Set.copyOf(second));
    }

    private static SmartClipboardReport.Entry fallbackEntry(
            ItemStack stack,
            SmartClipboardReport.ProductionInfo production,
            List<String> knownBy,
            List<String> canLearn
    ) {
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

    private static boolean isExactDomumProductionMatch(ProductionCandidate match, ItemStack stack) {
        return isConcreteProductionKey(match.matchedKey());
    }

    private static ProductionRank productionRank(ProductionCandidate candidate, ItemStack stack) {
        boolean domum = DomumOrnamentumRequestInspector.isDomumOrnamentumStack(stack);
        boolean exactDomum = !domum || isExactDomumProductionMatch(candidate, stack);
        boolean context = !candidate.production().knownBy().isEmpty() || !candidate.production().canLearn().isEmpty();
        return new ProductionRank(
                exactDomum ? productionSourceScore(candidate.matchedKey()) : productionSourceScore(candidate.matchedKey()) + 100,
                context ? 0 : 1,
                candidate.priority()
        );
    }

    private static int productionSourceScore(String key) {
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

    private static boolean isConcreteProductionKey(String key) {
        return key != null
                && (key.startsWith("exact:")
                || key.startsWith("resource:")
                || key.startsWith("material:")
                || key.startsWith("fingerprint:")
                || key.startsWith("recipe-output:"));
    }

    private static boolean entryMatchesStack(SmartClipboardReport.Entry entry, ItemStack stack) {
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

    private static Optional<SmartClipboardReport.Entry> entryAt(SmartClipboardReport report, int index) {
        if (index < 0 || index >= report.entries().size()) {
            return Optional.empty();
        }
        return Optional.of(report.entries().get(index));
    }

    private static void appendEntryCandidate(StringBuilder out, String source, String key, int priority, SmartClipboardReport.Entry entry, String note) {
        out.append("### ").append(source).append('\n');
        out.append("- matched key: `").append(blank(key)).append("`\n");
        out.append("- priority: ").append(priority).append('\n');
        out.append("- note: ").append(note).append('\n');
        appendEntryFields(out, entry);
        out.append('\n');
    }

    private static void appendEntryFields(StringBuilder out, SmartClipboardReport.Entry entry) {
        out.append("- item: ").append(itemId(entry.requestedStack())).append('\n');
        out.append("- doBlockId: ").append(entry.doBlockId()).append('\n');
        out.append("- recipe id: ").append(entry.cutterRecipeId().orElse("none")).append('\n');
        out.append("- recipeKnownBy: ").append(entry.recipeKnownBy()).append('\n');
        out.append("- canLearnCombo: ").append(entry.canLearnCombo()).append('\n');
        out.append("- exactComboAlreadyTaught: ").append(entry.exactComboAlreadyTaught()).append('\n');
        out.append("- teaching list: ").append(teachingFeedbackBuildings(entry)).append('\n');
        out.append("- shape/source class: ").append(entrySourceClass(entry)).append('\n');
    }

    private static void appendProductionCandidate(StringBuilder out, ProductionCandidate candidate) {
        out.append("### production entry\n");
        out.append("- matched key: `").append(blank(candidate.matchedKey())).append("`\n");
        out.append("- priority: ").append(candidate.priority()).append('\n');
        out.append("- note: ").append(candidate.note()).append('\n');
        appendProductionFields(out, candidate.production(), candidate.production().stack(), candidate.matchedKey(), candidate.priority());
        out.append('\n');
    }

    private static void appendProductionFields(StringBuilder out, SmartClipboardReport.ProductionInfo production, ItemStack hovered, String key, int priority) {
        ProductionCandidate candidate = new ProductionCandidate(production, key, priority, "");
        out.append("- production stack: ").append(itemId(production.stack())).append(" / ")
                .append(production.stack().getHoverName().getString()).append('\n');
        out.append("- doBlockId: ").append(production.doBlockId()).append('\n');
        out.append("- recipe id: ").append(production.recipeId().orElse("none")).append('\n');
        out.append("- knownBy: ").append(production.knownBy()).append('\n');
        out.append("- canLearn: ").append(production.canLearn()).append('\n');
        out.append("- exact DO production match: ").append(isExactDomumProductionMatch(candidate, hovered)).append('\n');
        out.append("- production class: ").append(productionSourceClass(candidate, hovered)).append('\n');
    }

    private static List<String> teachingFeedbackBuildings(SmartClipboardReport.Entry entry) {
        return entry.canLearnCombo().isEmpty() ? entry.recipeKnownBy() : entry.canLearnCombo();
    }

    private static List<String> visibleSmartInfoLines(SmartClipboardReport.Entry entry) {
        List<String> lines = new ArrayList<>();
        if (!hasSmartInfoTooltip(entry)) {
            return lines;
        }
        lines.add("Smart Info");
        String shape = humanizeDomumShape(entry);
        if (!shape.isBlank()) {
            lines.add("Shape: " + shape);
        }
        List<String> teaching = teachingFeedbackBuildings(entry);
        if (!teaching.isEmpty()) {
            lines.add("Can learn: " + String.join(", ", teaching));
        }
        return lines;
    }

    private static boolean hasSmartInfoTooltip(SmartClipboardReport.Entry entry) {
        return isArchitectsCutterEntry(entry) || !teachingFeedbackBuildings(entry).isEmpty();
    }

    private static boolean hasProductionContext(SmartClipboardReport.Entry entry) {
        return entry != null && (!entry.recipeKnownBy().isEmpty() || !entry.canLearnCombo().isEmpty());
    }

    private static boolean isArchitectsCutterEntry(SmartClipboardReport.Entry entry) {
        return entry.doBlockId().startsWith("domum_ornamentum:")
                && (entry.cutterRecipeId().isPresent()
                || DomumOrnamentumRequestInspector.isMaterializedArchitectsCutterOutput(entry.requestedStack())
                || DomumOrnamentumRequestInspector.hasArchitectsCutterMetadata(entry.requestedStack()));
    }

    private static String humanizeDomumShape(SmartClipboardReport.Entry entry) {
        if (!entry.doBlockId().startsWith("domum_ornamentum:")) {
            return "";
        }
        String value = entry.doBlockId().substring(entry.doBlockId().indexOf(':') + 1).replace('_', ' ');
        String[] parts = value.split(" ");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.toString();
    }

    private static String entrySourceClass(SmartClipboardReport.Entry entry) {
        if (!entry.recipeKnownBy().isEmpty() || !entry.canLearnCombo().isEmpty()) {
            return "production-context";
        }
        if (entry.cutterRecipeId().isPresent() || entry.doBlockId().startsWith("domum_ornamentum:")) {
            return "shape-only";
        }
        return "non-smart-info";
    }

    private static String productionSourceClass(ProductionCandidate candidate, ItemStack hovered) {
        if (DomumOrnamentumRequestInspector.isDomumOrnamentumStack(hovered) && !isExactDomumProductionMatch(candidate, hovered)) {
            return "generic-or-shape-only";
        }
        if (!candidate.production().knownBy().isEmpty() || !candidate.production().canLearn().isEmpty()) {
            return "exact-or-concrete-production";
        }
        return "shape-only";
    }

    private static String divergenceConclusion(SmartInfoClassificationService.Classification fresh, Optional<Candidate> selected) {
        if (selected.isEmpty() || selected.get().entry().isEmpty()) {
            return "- fresh classifier differs from selected tooltip entry: yes\n- divergence: resolver selected no Smart Info entry";
        }
        SmartClipboardReport.Entry entry = selected.get().entry().get();
        boolean knownSame = sameLists(fresh.knownBy(), entry.recipeKnownBy());
        boolean canSame = sameLists(fresh.canLearn(), entry.canLearnCombo());
        if (knownSame && canSame) {
            return "- fresh classifier differs from selected tooltip entry: no\n- divergence: none detected in knownBy/canLearn";
        }
        List<String> reasons = new ArrayList<>();
        if (!knownSame) {
            reasons.add("knownBy differs fresh=" + fresh.knownBy() + " selected=" + entry.recipeKnownBy());
        }
        if (!canSame) {
            reasons.add("canLearn differs fresh=" + fresh.canLearn() + " selected=" + entry.canLearnCombo());
        }
        return "- fresh classifier differs from selected tooltip entry: yes\n- divergence: "
                + String.join("; ", reasons)
                + "\n- likely stage: serialized report selection/source mismatch; inspect selected source above";
    }

    private static String itemId(ItemStack stack) {
        return stack == null || stack.isEmpty() ? "empty" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? "none" : value;
    }

    private static String sanitize(String value) {
        return value == null || value.isBlank() ? "unknown" : value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private record ResolverTrace(List<Candidate> candidates, Optional<Candidate> selected) {
    }

    private record EntryResolveResult(List<Candidate> candidates, Optional<Candidate> selected) {
    }

    private record ProductionResolveResult(Optional<ProductionCandidate> selected, String matchedKey, int priority, String reason) {
    }

    private record EntryMatch(SmartClipboardReport.Entry entry, String matchedKey, int priority, String source, String note) {
    }

    private record ProductionCandidate(SmartClipboardReport.ProductionInfo production, String matchedKey, int priority, String note) {
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

    private record Candidate(
            String source,
            String key,
            int priority,
            String decision,
            String note,
            Optional<SmartClipboardReport.Entry> entry,
            Optional<SmartClipboardReport.ProductionInfo> production
    ) {
        private static Candidate entry(String source, String key, int priority, String decision, String note, SmartClipboardReport.Entry entry) {
            return new Candidate(source, key, priority, decision, note, Optional.of(entry), Optional.empty());
        }

        private static Candidate production(String source, String key, int priority, String decision, String note, SmartClipboardReport.ProductionInfo production) {
            return new Candidate(source, key, priority, decision, note, Optional.empty(), Optional.of(production));
        }

        private static Candidate note(String source, String key, int priority, String decision, String note) {
            return new Candidate(source, key, priority, decision, note, Optional.empty(), Optional.empty());
        }

        private Candidate withDecision(String newDecision, String newNote) {
            return new Candidate(source, key, priority, newDecision, newNote, entry, production);
        }
    }
}
