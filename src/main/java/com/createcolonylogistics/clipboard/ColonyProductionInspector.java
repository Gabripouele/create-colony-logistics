package com.createcolonylogistics.clipboard;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.ICraftingBuildingModule;
import com.minecolonies.api.crafting.IGenericRecipe;
import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.api.crafting.ModCraftingTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

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

    private static boolean safeIsRecipeCompatible(ICraftingBuildingModule module, IGenericRecipe recipe) {
        try {
            return module.isRecipeCompatible(recipe);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String buildingLabel(IBuilding building, ICraftingBuildingModule module) {
        String buildingName = building.getCustomName() == null || building.getCustomName().isBlank()
                ? building.getBuildingDisplayName()
                : building.getCustomName();
        return buildingName + " / " + module.getId();
    }

    public record ProductionKnowledge(List<String> knownBy, List<String> canLearn) {
    }
}
