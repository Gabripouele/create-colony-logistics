package com.createcolonylogistics.clipboard;

import com.createcolonylogistics.CreateColonyLogistics;
import com.createcolonylogistics.config.ColonyLogisticsConfig;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.ICraftingBuildingModule;
import com.minecolonies.api.crafting.IGenericRecipe;
import com.minecolonies.api.crafting.IRecipeManager;
import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.api.crafting.ModCraftingTypes;
import com.minecolonies.api.crafting.registry.CraftingType;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.items.component.BuildingId;
import com.minecolonies.core.colony.buildings.modules.BuildingResourcesModule;
import com.minecolonies.core.colony.buildings.utils.BuildingBuilderResource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ColonyProductionInspector {
    private static final Map<ProductionCacheKey, CachedProductionIndex> PRODUCTION_CACHE = new HashMap<>();

    private ColonyProductionInspector() {
    }

    public static ProductionKnowledge inspect(IColony colony, Level level, ItemStack requestedStack) {
        return SmartInfoClassificationService.classify(colony, level, requestedStack).productionKnowledge();
    }

    public static List<RequestAnalysisService.ProductionInfo> inspectGlobal(IColony colony, Level level) {
        long gameTime = level.getGameTime();
        ProductionCacheKey cacheKey = new ProductionCacheKey(level.dimension().location(), colony.getID());
        CachedProductionIndex cached = PRODUCTION_CACHE.get(cacheKey);
        if (cached != null && cached.isValid(gameTime, ColonyLogisticsConfig.SMART_CLIPBOARD_PRODUCTION_CACHE_TTL_TICKS.get())) {
            if (ColonyLogisticsConfig.DEBUG_LOGGING.get()) {
                CreateColonyLogistics.LOGGER.info("[SmartClipboardPerf] inspectGlobal cache hit colony={} ageTicks={} entries={} buildings={} modules={} recipes={} architectsCutterRecipes={}",
                        colony.getID(), gameTime - cached.gameTime(), cached.productionIndex().size(), cached.stats().buildingCount(),
                        cached.stats().moduleCount(), cached.stats().recipeCandidateCount(), cached.stats().architectsCutterRecipeCount());
            }
            return copyProductionIndex(cached.productionIndex());
        }

        long startNanos = System.nanoTime();
        Map<String, ProductionAccumulator> outputs = new LinkedHashMap<>();
        IRecipeManager recipeManager = IColonyManager.getInstance().getRecipeManager();
        RecipeLookupCache recipeLookupCache = new RecipeLookupCache();
        int buildingCount = 0;
        int moduleCount = 0;
        int recipeCandidateCount = 0;
        int architectsCutterRecipeCount = 0;
        for (IBuilding building : colony.getBuildingManager().getBuildings().values()) {
            buildingCount++;
            for (ICraftingBuildingModule module : building.getModulesByType(ICraftingBuildingModule.class)) {
                moduleCount++;
                String label = buildingLabel(building, module);
                for (IToken<?> token : safeRecipeTokens(module)) {
                    IRecipeStorage storage = safeRecipe(recipeManager, token);
                    if (storage != null) {
                        addKnown(outputs, storage.getPrimaryOutput(), label, Optional.ofNullable(storage.getRecipeSource()));
                        for (ItemStack output : storage.getAlternateOutputs()) {
                            addKnown(outputs, output, label, Optional.ofNullable(storage.getRecipeSource()));
                        }
                        for (ItemStack output : storage.getSecondaryOutputs()) {
                            addKnown(outputs, output, label, Optional.ofNullable(storage.getRecipeSource()));
                        }
                    }
                }
                for (IGenericRecipe recipe : learnableRecipes(module, level, recipeLookupCache)) {
                    recipeCandidateCount++;
                    if (isArchitectsCutterRecipe(recipe)) {
                        architectsCutterRecipeCount++;
                    }
                    if (safeIsRecipeCompatible(module, recipe)) {
                        addLearnable(outputs, recipe.getPrimaryOutput(), label, Optional.ofNullable(recipe.getRecipeId()));
                        for (ItemStack output : recipe.getAllMultiOutputs()) {
                            addLearnable(outputs, output, label, Optional.ofNullable(recipe.getRecipeId()));
                        }
                        for (ItemStack output : recipe.getAdditionalOutputs()) {
                            addLearnable(outputs, output, label, Optional.ofNullable(recipe.getRecipeId()));
                        }
                    }
                }
            }
        }
        List<RequestAnalysisService.ProductionInfo> productionIndex = outputs.values().stream()
                .filter(ProductionAccumulator::hasUsefulContext)
                .map(ProductionAccumulator::toInfo)
                .toList();
        ProductionStats stats = new ProductionStats(buildingCount, moduleCount, recipeCandidateCount, architectsCutterRecipeCount);
        PRODUCTION_CACHE.put(cacheKey, new CachedProductionIndex(gameTime, copyProductionIndex(productionIndex), stats));
        if (ColonyLogisticsConfig.DEBUG_LOGGING.get()) {
            CreateColonyLogistics.LOGGER.info("[SmartClipboardPerf] inspectGlobal rebuilt colony={} durationMs={} entries={} buildings={} modules={} recipes={} architectsCutterRecipes={} recipeCacheHits={} recipeCacheMisses={}",
                    colony.getID(), elapsedMillis(startNanos), productionIndex.size(), buildingCount, moduleCount, recipeCandidateCount,
                    architectsCutterRecipeCount, recipeLookupCache.hits(), recipeLookupCache.misses());
        }
        return productionIndex;
    }

    public static List<RequestAnalysisService.ProductionInfo> withExactResourceScrollProduction(
            IColony colony,
            Level level,
            List<RequestAnalysisService.ProductionInfo> baseProductionIndex,
            List<ItemStack> resourceScrolls
    ) {
        if (resourceScrolls == null || resourceScrolls.isEmpty()) {
            return baseProductionIndex;
        }

        Map<String, RequestAnalysisService.ProductionInfo> exactOutputs = new LinkedHashMap<>();
        Map<String, SmartInfoClassificationService.Classification> classificationCache = new HashMap<>();
        for (ItemStack scroll : resourceScrolls) {
            if (scroll == null || scroll.isEmpty()) {
                continue;
            }
            IBuilding building = scrollBuilding(scroll);
            if (building == null || building.getColony() == null || building.getColony().getID() != colony.getID()) {
                continue;
            }
            BuildingResourcesModule resources = building.getFirstModuleOccurance(BuildingResourcesModule.class);
            if (resources == null) {
                continue;
            }
            for (Map.Entry<String, BuildingBuilderResource> entry : resources.getNeededResources().entrySet()) {
                BuildingBuilderResource resource = entry.getValue();
                if (resource == null || resource.getItemStack().isEmpty()) {
                    continue;
                }
                ItemStack stack = resource.getItemStack().copyWithCount(1);
                if (!DomumOrnamentumRequestInspector.isDomumOrnamentumStack(stack)) {
                    continue;
                }
                String exactKey = SmartClipboardReport.exactStackKey(stack);
                SmartInfoClassificationService.Classification classification = classificationCache.computeIfAbsent(exactKey,
                        ignored -> SmartInfoClassificationService.classify(colony, level, stack));
                List<RequestAnalysisService.SmartInfoMatchKey> resourceKeys = new ArrayList<>();
                SmartInfoClassificationService.addKey(resourceKeys, SmartClipboardReport.resourceStackKey(entry.getKey()),
                        SmartClipboardReport.SMART_INFO_PRIORITY_RESOURCE);
                classification.withExtraKeys(resourceKeys)
                        .toProductionInfo()
                        .ifPresent(info -> exactOutputs.merge(exactKey, info, ColonyProductionInspector::mergeProductionInfo));
            }
        }

        if (exactOutputs.isEmpty()) {
            return baseProductionIndex;
        }
        List<RequestAnalysisService.ProductionInfo> result = new ArrayList<>(baseProductionIndex);
        result.addAll(exactOutputs.values());
        return List.copyOf(result);
    }

    private static IBuilding scrollBuilding(ItemStack scroll) {
        try {
            return BuildingId.readBuildingFromItemStack(scroll);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    static boolean supportsArchitectsCutter(ICraftingBuildingModule module) {
        try {
            return module.canLearn(ModCraftingTypes.ARCHITECTS_CUTTER.get())
                    || module.getSupportedCraftingTypes().contains(ModCraftingTypes.ARCHITECTS_CUTTER.get());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static IRecipeStorage safeGetFirstRecipe(ICraftingBuildingModule module, ItemStack requestedStack) {
        try {
            return module.getFirstRecipe(requestedStack);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    static boolean knownRecipeMatches(IRecipeStorage knownRecipe, ItemStack requestedStack, DomumOrnamentumRequestInspector.CutterRecipeMatch cutterMatch) {
        ItemStack primaryOutput = knownRecipe.getPrimaryOutput();
        if (ItemStack.isSameItemSameComponents(primaryOutput, requestedStack)
                || DomumOrnamentumRequestInspector.sameMaterializedDomumOutput(primaryOutput, requestedStack)) {
            return true;
        }
        return cutterMatch != null
                && (ItemStack.isSameItemSameComponents(primaryOutput, cutterMatch.assembledOutput())
                || DomumOrnamentumRequestInspector.sameMaterializedDomumOutput(primaryOutput, cutterMatch.assembledOutput()));
    }

    static boolean safeCanLearn(ICraftingBuildingModule module) {
        try {
            return module.canLearn(ModCraftingTypes.ARCHITECTS_CUTTER.get());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean safeCanLearn(ICraftingBuildingModule module, CraftingType type) {
        try {
            return module.canLearn(type);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static boolean safeIsRecipeCompatible(ICraftingBuildingModule module, IGenericRecipe recipe) {
        try {
            return module.isRecipeCompatible(recipe);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static List<IToken<?>> safeRecipeTokens(ICraftingBuildingModule module) {
        try {
            return module.getRecipes();
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private static IRecipeStorage safeRecipe(IRecipeManager recipeManager, IToken<?> token) {
        try {
            return recipeManager.getRecipe(token);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static List<IGenericRecipe> learnableRecipes(ICraftingBuildingModule module, Level level, RecipeLookupCache recipeLookupCache) {
        List<IGenericRecipe> recipes = new ArrayList<>();
        for (CraftingType type : safeSupportedCraftingTypes(module)) {
            if (!safeCanLearn(module, type)) {
                continue;
            }
            recipes.addAll(recipeLookupCache.findRecipes(type, level));
        }
        try {
            recipes.addAll(module.getAdditionalRecipesForDisplayPurposesOnly(level));
        } catch (RuntimeException ignored) {
            // Optional MineColonies display-only recipe source.
        }
        return recipes;
    }

    private static List<CraftingType> safeSupportedCraftingTypes(ICraftingBuildingModule module) {
        try {
            return List.copyOf(module.getSupportedCraftingTypes());
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    static String buildingLabel(IBuilding building, ICraftingBuildingModule module) {
        String buildingName = building.getCustomName() == null || building.getCustomName().isBlank()
                ? building.getBuildingDisplayName()
                : building.getCustomName();
        return buildingName;
    }

    private static void addKnown(Map<String, ProductionAccumulator> outputs, ItemStack output, String label, Optional<ResourceLocation> recipeId) {
        accumulator(outputs, output, recipeId).ifPresent(accumulator -> accumulator.addKnown(label));
    }

    private static void addLearnable(Map<String, ProductionAccumulator> outputs, ItemStack output, String label, Optional<ResourceLocation> recipeId) {
        accumulator(outputs, output, recipeId).ifPresent(accumulator -> accumulator.addLearnable(label));
    }

    private static Optional<ProductionAccumulator> accumulator(Map<String, ProductionAccumulator> outputs, ItemStack output, Optional<ResourceLocation> recipeId) {
        if (output == null || output.isEmpty()) {
            return Optional.empty();
        }
        String key = SmartClipboardReport.exactStackKey(output);
        ProductionAccumulator accumulator = outputs.computeIfAbsent(key, ignored -> new ProductionAccumulator(output.copyWithCount(1)));
        recipeId.ifPresent(accumulator::addRecipeId);
        return Optional.of(accumulator);
    }

    private static List<RequestAnalysisService.SmartInfoMatchKey> productionKeys(ItemStack stack, Optional<ResourceLocation> recipeId) {
        List<RequestAnalysisService.SmartInfoMatchKey> keys = new ArrayList<>();
        addKey(keys, SmartClipboardReport.exactStackKey(stack), SmartClipboardReport.SMART_INFO_PRIORITY_EXACT);
        addKey(keys, SmartClipboardReport.resourceStackKey(stack), SmartClipboardReport.SMART_INFO_PRIORITY_RESOURCE);
        addKey(keys, SmartClipboardReport.itemIdKey(stack), SmartClipboardReport.SMART_INFO_PRIORITY_ITEM_ID);
        if (DomumOrnamentumRequestInspector.isDomumOrnamentumStack(stack)) {
            addKey(keys, SmartClipboardReport.domumMaterialKey(stack), SmartClipboardReport.SMART_INFO_PRIORITY_MATERIAL);
            addKey(keys, SmartClipboardReport.domumFingerprintKey(stack), SmartClipboardReport.SMART_INFO_PRIORITY_FINGERPRINT);
        }
        recipeId.ifPresent(id -> {
            addKey(keys, SmartClipboardReport.recipeKey(id.toString()), SmartClipboardReport.SMART_INFO_PRIORITY_OUTPUT);
            addKey(keys, SmartClipboardReport.recipeOutputKey(id.toString(), stack), SmartClipboardReport.SMART_INFO_PRIORITY_OUTPUT);
        });
        return keys.stream().distinct().toList();
    }

    private static List<RequestAnalysisService.SmartInfoMatchKey> exactResourceProductionKeys(
            ItemStack stack,
            Optional<ResourceLocation> recipeId,
            String resourceKey,
            Optional<DomumOrnamentumRequestInspector.CutterRecipeMatch> cutterMatch
    ) {
        List<RequestAnalysisService.SmartInfoMatchKey> keys = new ArrayList<>(productionKeys(stack, recipeId));
        addKey(keys, SmartClipboardReport.resourceStackKey(resourceKey), SmartClipboardReport.SMART_INFO_PRIORITY_RESOURCE);
        cutterMatch.ifPresent(match -> {
            addKey(keys, SmartClipboardReport.exactStackKey(match.assembledOutput()), SmartClipboardReport.SMART_INFO_PRIORITY_OUTPUT);
            addKey(keys, SmartClipboardReport.resourceStackKey(match.assembledOutput()), SmartClipboardReport.SMART_INFO_PRIORITY_OUTPUT);
            addKey(keys, SmartClipboardReport.domumMaterialKey(match.assembledOutput()), SmartClipboardReport.SMART_INFO_PRIORITY_MATERIAL);
            addKey(keys, SmartClipboardReport.domumFingerprintKey(match.assembledOutput()), SmartClipboardReport.SMART_INFO_PRIORITY_FINGERPRINT);
            addKey(keys, SmartClipboardReport.recipeOutputKey(match.recipeId().toString(), match.assembledOutput()), SmartClipboardReport.SMART_INFO_PRIORITY_OUTPUT);
        });
        return keys.stream().distinct().toList();
    }

    private static RequestAnalysisService.ProductionInfo mergeProductionInfo(RequestAnalysisService.ProductionInfo first, RequestAnalysisService.ProductionInfo second) {
        Optional<ResourceLocation> recipeId = first.recipeId().isPresent() ? first.recipeId() : second.recipeId();
        Optional<ResourceLocation> doBlockId = first.doBlockId().isPresent() ? first.doBlockId() : second.doBlockId();
        return new RequestAnalysisService.ProductionInfo(
                first.stack().copy(),
                mergeStrings(first.knownBy(), second.knownBy()),
                mergeStrings(first.canLearn(), second.canLearn()),
                recipeId,
                doBlockId,
                mergeKeys(first.keys(), second.keys())
        );
    }

    private static List<String> mergeStrings(List<String> first, List<String> second) {
        Set<String> values = new LinkedHashSet<>();
        values.addAll(first);
        values.addAll(second);
        return List.copyOf(values);
    }

    private static List<RequestAnalysisService.SmartInfoMatchKey> mergeKeys(List<RequestAnalysisService.SmartInfoMatchKey> first, List<RequestAnalysisService.SmartInfoMatchKey> second) {
        Set<RequestAnalysisService.SmartInfoMatchKey> values = new LinkedHashSet<>();
        values.addAll(first);
        values.addAll(second);
        return List.copyOf(values);
    }

    private static void addKey(List<RequestAnalysisService.SmartInfoMatchKey> keys, String key, int priority) {
        if (key != null && !key.isBlank()) {
            keys.add(new RequestAnalysisService.SmartInfoMatchKey(key, priority));
        }
    }

    private static boolean isArchitectsCutterRecipe(IGenericRecipe recipe) {
        ResourceLocation recipeId = recipe.getRecipeId();
        return recipeId != null
                && recipeId.getNamespace().equals("domum_ornamentum")
                && recipeId.getPath().contains("architect");
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private static List<RequestAnalysisService.ProductionInfo> copyProductionIndex(List<RequestAnalysisService.ProductionInfo> productionIndex) {
        return productionIndex.stream()
                .map(info -> new RequestAnalysisService.ProductionInfo(
                        info.stack().copy(),
                        List.copyOf(info.knownBy()),
                        List.copyOf(info.canLearn()),
                        info.recipeId(),
                        info.doBlockId(),
                        List.copyOf(info.keys())
                ))
                .toList();
    }

    public record ProductionKnowledge(List<String> knownBy, List<String> canLearn) {
    }

    private record ProductionCacheKey(ResourceLocation dimension, int colonyId) {
    }

    private record CachedProductionIndex(long gameTime, List<RequestAnalysisService.ProductionInfo> productionIndex, ProductionStats stats) {
        private boolean isValid(long now, int ttlTicks) {
            return now >= gameTime && now - gameTime <= ttlTicks;
        }
    }

    private record ProductionStats(int buildingCount, int moduleCount, int recipeCandidateCount, int architectsCutterRecipeCount) {
    }

    private static final class RecipeLookupCache {
        private final Map<CraftingType, List<IGenericRecipe>> recipesByType = new HashMap<>();
        private int hits;
        private int misses;

        private List<IGenericRecipe> findRecipes(CraftingType type, Level level) {
            List<IGenericRecipe> cached = recipesByType.get(type);
            if (cached != null) {
                hits++;
                return cached;
            }
            misses++;
            List<IGenericRecipe> recipes;
            try {
                recipes = List.copyOf(type.findRecipes(level.getRecipeManager(), level));
            } catch (RuntimeException ignored) {
                recipes = List.of();
            }
            recipesByType.put(type, recipes);
            return recipes;
        }

        private int hits() {
            return hits;
        }

        private int misses() {
            return misses;
        }
    }

    private static final class ProductionAccumulator {
        private final ItemStack stack;
        private final List<String> knownBy = new ArrayList<>();
        private final List<String> canLearn = new ArrayList<>();
        private ResourceLocation recipeId;

        private ProductionAccumulator(ItemStack stack) {
            this.stack = stack;
        }

        private void addKnown(String label) {
            addUnique(knownBy, label);
        }

        private void addLearnable(String label) {
            addUnique(canLearn, label);
        }

        private void addRecipeId(ResourceLocation recipeId) {
            if (this.recipeId == null) {
                this.recipeId = recipeId;
            }
        }

        private boolean hasUsefulContext() {
            return !knownBy.isEmpty() || !canLearn.isEmpty();
        }

        private RequestAnalysisService.ProductionInfo toInfo() {
            Optional<ResourceLocation> recipe = Optional.ofNullable(recipeId);
            Optional<ResourceLocation> domumId = DomumOrnamentumRequestInspector.isDomumOrnamentumStack(stack)
                    ? Optional.of(BuiltInRegistries.ITEM.getKey(stack.getItem()))
                    : Optional.empty();
            return new RequestAnalysisService.ProductionInfo(stack.copy(), List.copyOf(knownBy), List.copyOf(canLearn),
                    recipe, domumId, productionKeys(stack, recipe));
        }

        private static void addUnique(List<String> values, String value) {
            if (value != null && !value.isBlank() && !values.contains(value)) {
                values.add(value);
            }
        }
    }
}
