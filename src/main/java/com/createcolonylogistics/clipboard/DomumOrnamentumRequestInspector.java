package com.createcolonylogistics.clipboard;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DomumOrnamentumRequestInspector {
    private static final String DOMUM_ORNAMENTUM = "domum_ornamentum";
    private static final String ARCHITECTS_CUTTER = "architect";

    private DomumOrnamentumRequestInspector() {
    }

    public static boolean isDomumOrnamentumStack(ItemStack stack) {
        return !stack.isEmpty() && DOMUM_ORNAMENTUM.equals(itemId(stack).getNamespace());
    }

    public static ResourceLocation itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    public static String exactComboFingerprint(ItemStack stack) {
        return itemId(stack) + "|" + stack.getComponentsPatch();
    }

    public static Optional<ResourceLocation> findCutterRecipe(RecipeManager recipeManager, HolderLookup.Provider provider, ItemStack requestedStack) {
        return findCutterRecipeHolder(recipeManager, provider, requestedStack).map(RecipeHolder::id);
    }

    public static List<IngredientRequirement> findCutterRequirements(RecipeManager recipeManager, HolderLookup.Provider provider, ItemStack requestedStack) {
        Optional<RecipeHolder<?>> recipe = findCutterRecipeHolder(recipeManager, provider, requestedStack);
        if (recipe.isEmpty()) {
            return List.of();
        }

        ItemStack result = recipe.get().value().getResultItem(provider);
        int outputCount = Math.max(1, result.getCount());
        int crafts = Math.max(1, (requestedStack.getCount() + outputCount - 1) / outputCount);
        Map<ResourceLocation, ItemStack> stacks = new LinkedHashMap<>();
        Map<ResourceLocation, Integer> counts = new LinkedHashMap<>();

        try {
            for (Ingredient ingredient : recipe.get().value().getIngredients()) {
                if (ingredient == null || ingredient.isEmpty()) {
                    continue;
                }
                ItemStack[] options = ingredient.getItems();
                if (options.length == 0 || options[0].isEmpty()) {
                    continue;
                }
                ItemStack option = options[0].copy();
                int required = Math.max(1, option.getCount()) * crafts;
                ResourceLocation itemId = itemId(option);
                stacks.putIfAbsent(itemId, option.copyWithCount(required));
                counts.merge(itemId, required, Integer::sum);
            }
        } catch (RuntimeException ignored) {
            return List.of();
        }

        List<IngredientRequirement> requirements = new ArrayList<>();
        for (Map.Entry<ResourceLocation, ItemStack> entry : stacks.entrySet()) {
            int count = counts.getOrDefault(entry.getKey(), entry.getValue().getCount());
            requirements.add(new IngredientRequirement(entry.getValue().copyWithCount(count), count));
        }
        return requirements;
    }

    private static Optional<RecipeHolder<?>> findCutterRecipeHolder(RecipeManager recipeManager, HolderLookup.Provider provider, ItemStack requestedStack) {
        Optional<RecipeHolder<?>> compatibleMatch = Optional.empty();
        try {
            for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
                ResourceLocation typeId = BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType());
                boolean cutterType = typeId != null && typeId.getNamespace().equals(DOMUM_ORNAMENTUM)
                        && typeId.getPath().contains(ARCHITECTS_CUTTER);
                boolean cutterPath = holder.id().getNamespace().equals(DOMUM_ORNAMENTUM)
                        && holder.id().getPath().contains(ARCHITECTS_CUTTER);

                if (!cutterType && !cutterPath) {
                    continue;
                }

                ItemStack result = holder.value().getResultItem(provider);
                if (ItemStack.isSameItemSameComponents(result, requestedStack)) {
                    return Optional.of(holder);
                }
                if (compatibleMatch.isEmpty() && !result.isEmpty() && itemId(result).equals(itemId(requestedStack))) {
                    compatibleMatch = Optional.of(holder);
                }
            }
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
        return compatibleMatch;
    }

    public record IngredientRequirement(ItemStack stack, int count) {
    }
}
