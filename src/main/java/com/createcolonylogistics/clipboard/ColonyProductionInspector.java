package com.createcolonylogistics.clipboard;

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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ColonyProductionInspector {
    private ColonyProductionInspector() {
    }

    public static ProductionKnowledge inspect(IColony colony, Level level, ItemStack requestedStack) {
        List<String> knownBy = new ArrayList<>();
        List<String> canLearn = new ArrayList<>();
        DomumOrnamentumRequestInspector.CutterRecipeMatch cutterMatch = DomumOrnamentumRequestInspector.findArchitectsCutterMatch(level, requestedStack)
                .orElse(null);
        List<IGenericRecipe> exactRecipes = cutterMatch == null ? List.of() : List.of(cutterMatch.genericRecipe());

        for (IBuilding building : colony.getBuildingManager().getBuildings().values()) {
            for (ICraftingBuildingModule module : building.getModulesByType(ICraftingBuildingModule.class)) {
                if (!supportsArchitectsCutter(module)) {
                    continue;
                }

                IRecipeStorage knownRecipe = safeGetFirstRecipe(module, requestedStack);
                if (knownRecipe != null && knownRecipeMatches(knownRecipe, requestedStack, cutterMatch)) {
                    knownBy.add(buildingLabel(building, module));
                } else if (!exactRecipes.isEmpty() && safeCanLearn(module) && exactRecipes.stream().anyMatch(recipe -> safeIsRecipeCompatible(module, recipe))) {
                    canLearn.add(buildingLabel(building, module));
                }
            }
        }

        return new ProductionKnowledge(knownBy, canLearn);
    }

    public static List<RequestAnalysisService.ProductionInfo> inspectGlobal(IColony colony, Level level) {
        Map<String, ProductionAccumulator> outputs = new LinkedHashMap<>();
        IRecipeManager recipeManager = IColonyManager.getInstance().getRecipeManager();
        for (IBuilding building : colony.getBuildingManager().getBuildings().values()) {
            for (ICraftingBuildingModule module : building.getModulesByType(ICraftingBuildingModule.class)) {
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
                for (IGenericRecipe recipe : learnableRecipes(module, level)) {
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
        return outputs.values().stream()
                .filter(ProductionAccumulator::hasUsefulContext)
                .map(ProductionAccumulator::toInfo)
                .toList();
    }

    private static boolean supportsArchitectsCutter(ICraftingBuildingModule module) {
        try {
            return module.canLearn(ModCraftingTypes.ARCHITECTS_CUTTER.get())
                    || module.getSupportedCraftingTypes().contains(ModCraftingTypes.ARCHITECTS_CUTTER.get());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static IRecipeStorage safeGetFirstRecipe(ICraftingBuildingModule module, ItemStack requestedStack) {
        try {
            return module.getFirstRecipe(requestedStack);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean knownRecipeMatches(IRecipeStorage knownRecipe, ItemStack requestedStack, DomumOrnamentumRequestInspector.CutterRecipeMatch cutterMatch) {
        ItemStack primaryOutput = knownRecipe.getPrimaryOutput();
        if (ItemStack.isSameItemSameComponents(primaryOutput, requestedStack)
                || DomumOrnamentumRequestInspector.sameMaterializedDomumOutput(primaryOutput, requestedStack)) {
            return true;
        }
        return cutterMatch != null
                && (ItemStack.isSameItemSameComponents(primaryOutput, cutterMatch.assembledOutput())
                || DomumOrnamentumRequestInspector.sameMaterializedDomumOutput(primaryOutput, cutterMatch.assembledOutput()));
    }

    private static boolean safeCanLearn(ICraftingBuildingModule module) {
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

    private static boolean safeIsRecipeCompatible(ICraftingBuildingModule module, IGenericRecipe recipe) {
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

    private static List<IGenericRecipe> learnableRecipes(ICraftingBuildingModule module, Level level) {
        List<IGenericRecipe> recipes = new ArrayList<>();
        for (CraftingType type : safeSupportedCraftingTypes(module)) {
            if (!safeCanLearn(module, type)) {
                continue;
            }
            try {
                recipes.addAll(type.findRecipes(level.getRecipeManager(), level));
            } catch (RuntimeException ignored) {
                // Some crafting types are intentionally context-sensitive.
            }
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

    private static String buildingLabel(IBuilding building, ICraftingBuildingModule module) {
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

    private static void addKey(List<RequestAnalysisService.SmartInfoMatchKey> keys, String key, int priority) {
        if (key != null && !key.isBlank()) {
            keys.add(new RequestAnalysisService.SmartInfoMatchKey(key, priority));
        }
    }

    public record ProductionKnowledge(List<String> knownBy, List<String> canLearn) {
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
