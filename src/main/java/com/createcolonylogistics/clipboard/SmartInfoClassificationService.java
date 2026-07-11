package com.createcolonylogistics.clipboard;

import com.createcolonylogistics.config.ColonyLogisticsConfig;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.ICraftingBuildingModule;
import com.minecolonies.api.crafting.IRecipeStorage;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class SmartInfoClassificationService {
    private static final Map<ClassificationCacheKey, CachedClassification> CLASSIFICATION_CACHE = new HashMap<>();

    private SmartInfoClassificationService() {
    }

    public static Classification classify(IColony colony, Level level, ItemStack stack) {
        return classify(colony, level, stack, List.of());
    }

    public static Classification classify(IColony colony, Level level, ItemStack stack, List<RequestAnalysisService.SmartInfoMatchKey> extraKeys) {
        if (colony == null || level == null || stack == null || stack.isEmpty()) {
            return Classification.empty(stack == null ? ItemStack.EMPTY : stack);
        }

        ClassificationCacheKey cacheKey = cacheKey(colony, level, stack);
        long gameTime = level.getGameTime();
        CachedClassification cached = CLASSIFICATION_CACHE.get(cacheKey);
        if (cached != null && cached.isValid(gameTime, ColonyLogisticsConfig.SMART_CLIPBOARD_PRODUCTION_CACHE_TTL_TICKS.get())) {
            return cached.classification().withExtraKeys(extraKeys);
        }

        Classification classification = buildClassification(colony, level, stack);
        CLASSIFICATION_CACHE.put(cacheKey, new CachedClassification(gameTime, classification.copy()));
        return classification.withExtraKeys(extraKeys);
    }

    private static Classification buildClassification(IColony colony, Level level, ItemStack stack) {
        ItemStack normalized = stack.copyWithCount(1);
        if (!DomumOrnamentumRequestInspector.isDomumOrnamentumStack(normalized)) {
            return new Classification(
                    normalized,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    List.of(),
                    List.of(),
                    ClassificationStatus.NON_DOMUM,
                    "non-domum",
                    baseStackKeys(normalized)
            );
        }

        Optional<DomumOrnamentumRequestInspector.CutterRecipeMatch> cutterMatch =
                DomumOrnamentumRequestInspector.findArchitectsCutterMatch(level, normalized);
        List<String> knownBy = new ArrayList<>();
        List<String> canLearn = new ArrayList<>();

        for (IBuilding building : colony.getBuildingManager().getBuildings().values()) {
            for (ICraftingBuildingModule module : building.getModulesByType(ICraftingBuildingModule.class)) {
                if (!ColonyProductionInspector.supportsArchitectsCutter(module)) {
                    continue;
                }

                IRecipeStorage knownRecipe = ColonyProductionInspector.safeGetFirstRecipe(module, normalized);
                if (knownRecipe != null && ColonyProductionInspector.knownRecipeMatches(knownRecipe, normalized, cutterMatch.orElse(null))) {
                    addUnique(knownBy, ColonyProductionInspector.buildingLabel(building, module));
                    continue;
                }

                if (cutterMatch.isPresent()
                        && ColonyProductionInspector.safeCanLearn(module)
                        && ColonyProductionInspector.safeIsRecipeCompatible(module, cutterMatch.get().genericRecipe())) {
                    addUnique(canLearn, ColonyProductionInspector.buildingLabel(building, module));
                }
            }
        }

        Optional<ResourceLocation> recipeId = cutterMatch.map(DomumOrnamentumRequestInspector.CutterRecipeMatch::recipeId);
        Optional<ResourceLocation> doBlockId = Optional.of(BuiltInRegistries.ITEM.getKey(normalized.getItem()));
        ClassificationStatus status;
        String reason;
        if (!knownBy.isEmpty() || !canLearn.isEmpty()) {
            status = ClassificationStatus.EXACT_PRODUCTION;
            reason = "exact-module-validation";
        } else if (cutterMatch.isPresent()) {
            status = ClassificationStatus.SHAPE_ONLY;
            reason = "exact-recipe-no-module";
        } else {
            status = ClassificationStatus.NO_EXACT_RECIPE;
            reason = "no-exact-cutter-match";
        }

        return new Classification(
                normalized,
                recipeId,
                doBlockId,
                cutterMatch,
                List.copyOf(knownBy),
                List.copyOf(canLearn),
                status,
                reason,
                exactClassificationKeys(normalized, cutterMatch)
        );
    }

    private static ClassificationCacheKey cacheKey(IColony colony, Level level, ItemStack stack) {
        String materialKey = DomumOrnamentumRequestInspector.canonicalMaterialKey(stack).orElse("");
        return new ClassificationCacheKey(
                level.dimension().location(),
                colony.getID(),
                SmartClipboardReport.exactStackKey(stack),
                materialKey
        );
    }

    private static List<RequestAnalysisService.SmartInfoMatchKey> exactClassificationKeys(
            ItemStack stack,
            Optional<DomumOrnamentumRequestInspector.CutterRecipeMatch> cutterMatch
    ) {
        List<RequestAnalysisService.SmartInfoMatchKey> keys = new ArrayList<>(baseStackKeys(stack));
        cutterMatch.ifPresent(match -> {
            addStackKeys(keys, match.assembledOutput(), SmartClipboardReport.SMART_INFO_PRIORITY_OUTPUT);
            addKey(keys, SmartClipboardReport.recipeOutputKey(match.recipeId().toString(), match.assembledOutput()),
                    SmartClipboardReport.SMART_INFO_PRIORITY_OUTPUT);
        });
        return keys.stream().distinct().toList();
    }

    private static List<RequestAnalysisService.SmartInfoMatchKey> baseStackKeys(ItemStack stack) {
        List<RequestAnalysisService.SmartInfoMatchKey> keys = new ArrayList<>();
        addStackKeys(keys, stack, SmartClipboardReport.SMART_INFO_PRIORITY_EXACT);
        addKey(keys, SmartClipboardReport.itemIdKey(stack), SmartClipboardReport.SMART_INFO_PRIORITY_ITEM_ID);
        return keys.stream().distinct().toList();
    }

    private static void addStackKeys(List<RequestAnalysisService.SmartInfoMatchKey> keys, ItemStack stack, int priority) {
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

    static void addKey(List<RequestAnalysisService.SmartInfoMatchKey> keys, String key, int priority) {
        if (key != null && !key.isBlank()) {
            keys.add(new RequestAnalysisService.SmartInfoMatchKey(key, priority));
        }
    }

    private static List<RequestAnalysisService.SmartInfoMatchKey> mergeKeys(
            List<RequestAnalysisService.SmartInfoMatchKey> first,
            List<RequestAnalysisService.SmartInfoMatchKey> second
    ) {
        Set<RequestAnalysisService.SmartInfoMatchKey> values = new LinkedHashSet<>();
        values.addAll(first);
        values.addAll(second);
        return List.copyOf(values);
    }

    private static void addUnique(List<String> values, String value) {
        if (value != null && !value.isBlank() && !values.contains(value)) {
            values.add(value);
        }
    }

    public enum ClassificationStatus {
        EXACT_PRODUCTION,
        SHAPE_ONLY,
        NO_EXACT_RECIPE,
        NON_DOMUM
    }

    public record Classification(
            ItemStack stack,
            Optional<ResourceLocation> recipeId,
            Optional<ResourceLocation> doBlockId,
            Optional<DomumOrnamentumRequestInspector.CutterRecipeMatch> cutterMatch,
            List<String> knownBy,
            List<String> canLearn,
            ClassificationStatus status,
            String reason,
            List<RequestAnalysisService.SmartInfoMatchKey> keys
    ) {
        public static Classification empty(ItemStack stack) {
            return new Classification(stack.copy(), Optional.empty(), Optional.empty(), Optional.empty(), List.of(), List.of(),
                    ClassificationStatus.NON_DOMUM, "empty", List.of());
        }

        public Classification withExtraKeys(List<RequestAnalysisService.SmartInfoMatchKey> extraKeys) {
            if (extraKeys == null || extraKeys.isEmpty()) {
                return copy();
            }
            return new Classification(stack.copy(), recipeId, doBlockId, cutterMatch, List.copyOf(knownBy), List.copyOf(canLearn),
                    status, reason, mergeKeys(keys, extraKeys));
        }

        private Classification copy() {
            return new Classification(stack.copy(), recipeId, doBlockId, cutterMatch, List.copyOf(knownBy), List.copyOf(canLearn),
                    status, reason, List.copyOf(keys));
        }

        public ColonyProductionInspector.ProductionKnowledge productionKnowledge() {
            return new ColonyProductionInspector.ProductionKnowledge(List.copyOf(knownBy), List.copyOf(canLearn));
        }

        public Optional<RequestAnalysisService.ProductionInfo> toProductionInfo() {
            boolean hasProduction = !knownBy.isEmpty() || !canLearn.isEmpty();
            boolean hasShape = recipeId.isPresent() && doBlockId.isPresent();
            if (!hasProduction && !hasShape) {
                return Optional.empty();
            }
            return Optional.of(new RequestAnalysisService.ProductionInfo(
                    stack.copy(),
                    List.copyOf(knownBy),
                    List.copyOf(canLearn),
                    recipeId,
                    doBlockId,
                    List.copyOf(keys)
            ));
        }
    }

    private record ClassificationCacheKey(ResourceLocation dimension, int colonyId, String exactStackKey, String materialKey) {
    }

    private record CachedClassification(long gameTime, Classification classification) {
        private boolean isValid(long now, int ttlTicks) {
            return now >= gameTime && now - gameTime <= ttlTicks;
        }
    }
}
