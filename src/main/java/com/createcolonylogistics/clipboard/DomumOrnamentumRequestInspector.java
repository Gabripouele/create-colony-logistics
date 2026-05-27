package com.createcolonylogistics.clipboard;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

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
        try {
            for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
                ResourceLocation typeId = BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType());
                boolean cutterType = typeId != null && typeId.getNamespace().equals(DOMUM_ORNAMENTUM)
                        && typeId.getPath().contains(ARCHITECTS_CUTTER);
                boolean cutterPath = holder.id().getNamespace().equals(DOMUM_ORNAMENTUM)
                        && holder.id().getPath().contains(ARCHITECTS_CUTTER);

                if ((cutterType || cutterPath) && ItemStack.isSameItemSameComponents(holder.value().getResultItem(provider), requestedStack)) {
                    return Optional.of(holder.id());
                }
            }
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }
}
