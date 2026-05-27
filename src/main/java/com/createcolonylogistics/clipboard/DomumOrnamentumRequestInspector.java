package com.createcolonylogistics.clipboard;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DomumOrnamentumRequestInspector {
    private static final String DOMUM_ORNAMENTUM = "domum_ornamentum";
    private static final String ARCHITECTS_CUTTER = "architect";
    private static final Pattern RESOURCE_LOCATION = Pattern.compile("\\b[a-z0-9_.-]+:[a-z0-9_./-]+\\b");

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

    public static Optional<ItemStack> materializedRequestedStack(IRequest<?> request) {
        try {
            Class<?> util = Class.forName("com.minecolonies.core.util.DomumOrnamentumUtils");
            Object result = util.getMethod("getRequestedStack", IRequest.class).invoke(null, request);
            if (result instanceof ItemStack stack && !stack.isEmpty() && isDomumOrnamentumStack(stack)) {
                return Optional.of(stack.copy());
            }
        } catch (LinkageError | ReflectiveOperationException | RuntimeException ignored) {
            // Optional MineColonies helper path; fall back to the already extracted request stack.
        }
        return Optional.empty();
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
        List<ItemStack> materialStacks = materialStacksFromComponents(requestedStack);
        Map<ResourceLocation, ItemStack> stacks = new LinkedHashMap<>();
        Map<ResourceLocation, Integer> counts = new LinkedHashMap<>();

        try {
            int materialIndex = 0;
            for (Ingredient ingredient : recipe.get().value().getIngredients()) {
                if (ingredient == null || ingredient.isEmpty()) {
                    continue;
                }
                ItemStack[] options = ingredient.getItems();
                if (options.length == 0 || options[0].isEmpty()) {
                    continue;
                }
                ItemStack option = materialIndex < materialStacks.size()
                        ? materialStacks.get(materialIndex).copy()
                        : options[0].copy();
                int required = Math.max(1, options[0].getCount()) * crafts;
                materialIndex++;
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

    public static String debugCutterSummary(RecipeManager recipeManager, HolderLookup.Provider provider, ItemStack requestedStack) {
        Optional<RecipeHolder<?>> recipe = findCutterRecipeHolder(recipeManager, provider, requestedStack);
        List<ItemStack> materials = materialStacksFromComponents(requestedStack);
        String materialSummary = materials.stream()
                .map(stack -> itemId(stack) + " x" + stack.getCount())
                .toList()
                .toString();
        if (recipe.isEmpty()) {
            return "recipe=none requested=" + itemId(requestedStack)
                    + " requestedCount=" + requestedStack.getCount()
                    + " materials=" + materialSummary
                    + " components=" + compactComponentSummary(requestedStack);
        }
        ItemStack result = recipe.get().value().getResultItem(provider);
        int outputCount = Math.max(1, result.getCount());
        int crafts = Math.max(1, (requestedStack.getCount() + outputCount - 1) / outputCount);
        int generated = findCutterRequirements(recipeManager, provider, requestedStack).size();
        return "recipe=" + recipe.get().id()
                + " output=" + itemId(result)
                + " outputCount=" + outputCount
                + " requestedCount=" + requestedStack.getCount()
                + " crafts=" + crafts
                + " materials=" + materialSummary
                + " generated=" + generated
                + " components=" + compactComponentSummary(requestedStack);
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

    private static List<ItemStack> materialStacksFromComponents(ItemStack requestedStack) {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        collectMaterialIds(requestedStack.getComponents().toString(), requestedStack, ids);
        collectMaterialIds(requestedStack.getComponentsPatch().toString(), requestedStack, ids);

        List<ItemStack> stacks = new ArrayList<>();
        for (ResourceLocation id : ids) {
            BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> stacks.add(new ItemStack(item)));
        }
        return stacks;
    }

    private static void collectMaterialIds(String data, ItemStack requestedStack, Set<ResourceLocation> ids) {
        Matcher matcher = RESOURCE_LOCATION.matcher(data);
        ResourceLocation requestedId = itemId(requestedStack);
        while (matcher.find()) {
            ResourceLocation id = ResourceLocation.tryParse(matcher.group());
            if (id == null || DOMUM_ORNAMENTUM.equals(id.getNamespace()) || requestedId.equals(id)) {
                continue;
            }
            if (BuiltInRegistries.ITEM.containsKey(id)) {
                ids.add(id);
            }
        }
    }

    private static String compactComponentSummary(ItemStack stack) {
        String summary = (stack.getComponents() + " " + stack.getComponentsPatch()).replaceAll("\\s+", " ");
        return summary.length() <= 240 ? summary : summary.substring(0, 240) + "...";
    }

    public record IngredientRequirement(ItemStack stack, int count) {
    }
}
