package com.createcolonylogistics.clipboard;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.ICraftingBuildingModule;
import com.minecolonies.api.crafting.IGenericRecipe;
import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.api.crafting.ModCraftingTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ColonyProductionInspector {
    private ColonyProductionInspector() {
    }

    public static ProductionKnowledge inspect(IColony colony, Level level, ItemStack requestedStack) {
        List<String> knownBy = new ArrayList<>();
        List<String> canLearn = new ArrayList<>();
        List<IGenericRecipe> exactRecipes = exactArchitectsCutterRecipes(level, requestedStack);

        for (IBuilding building : colony.getBuildingManager().getBuildings().values()) {
            for (ICraftingBuildingModule module : building.getModulesByType(ICraftingBuildingModule.class)) {
                if (!supportsArchitectsCutter(module)) {
                    continue;
                }

                IRecipeStorage knownRecipe = safeGetFirstRecipe(module, requestedStack);
                if (knownRecipe != null && ItemStack.isSameItemSameComponents(knownRecipe.getPrimaryOutput(), requestedStack)) {
                    knownBy.add(buildingLabel(building, module));
                } else if (!exactRecipes.isEmpty() && safeCanLearn(module) && exactRecipes.stream().anyMatch(recipe -> safeIsRecipeCompatible(module, recipe))) {
                    canLearn.add(buildingLabel(building, module));
                }
            }
        }

        return new ProductionKnowledge(knownBy, canLearn);
    }

    private static List<IGenericRecipe> exactArchitectsCutterRecipes(Level level, ItemStack requestedStack) {
        Optional<ResourceLocation> cutterRecipeId = DomumOrnamentumRequestInspector.findExactCutterRecipe(
                level.getRecipeManager(),
                level.registryAccess(),
                requestedStack
        );
        if (cutterRecipeId.isEmpty()) {
            return List.of();
        }

        try {
            return ModCraftingTypes.ARCHITECTS_CUTTER.get().findRecipes(level.getRecipeManager(), level).stream()
                    .filter(recipe -> cutterRecipeId.get().equals(recipe.getRecipeId()) || recipeOutputMatches(recipe, requestedStack))
                    .toList();
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private static boolean recipeOutputMatches(IGenericRecipe recipe, ItemStack requestedStack) {
        try {
            if (ItemStack.isSameItemSameComponents(recipe.getPrimaryOutput(), requestedStack)) {
                return true;
            }
            for (ItemStack output : recipe.getAllMultiOutputs()) {
                if (ItemStack.isSameItemSameComponents(output, requestedStack)) {
                    return true;
                }
            }
        } catch (RuntimeException ignored) {
            return false;
        }
        return false;
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
