package com.createcolonylogistics.clipboard;

import com.minecolonies.api.crafting.GenericRecipe;
import com.minecolonies.api.crafting.IGenericRecipe;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import com.minecolonies.api.colony.requestsystem.request.IRequest;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
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
    private static final Pattern BLOCK_TOKEN = Pattern.compile("Block\\{([a-z0-9_.-]+:[a-z0-9_./-]+)}");

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

    public static Optional<ResourceLocation> findExactCutterRecipe(RecipeManager recipeManager, HolderLookup.Provider provider, ItemStack requestedStack) {
        return findExactCutterRecipeHolder(recipeManager, provider, requestedStack).map(RecipeHolder::id);
    }

    public static Optional<CutterRecipeMatch> findArchitectsCutterMatch(Level level, ItemStack requestedStack) {
        if (level == null || requestedStack.isEmpty() || !isDomumOrnamentumStack(requestedStack)) {
            return Optional.empty();
        }

        try {
            Object texturedBlock = domumBlock(requestedStack);
            if (texturedBlock == null) {
                return Optional.empty();
            }

            Object textureData = materialTextureData(requestedStack);
            if (textureData == null || materialTextureDataIsEmpty(textureData)) {
                return Optional.empty();
            }

            Collection<?> components = texturedBlockComponents(texturedBlock);
            if (components.isEmpty()) {
                return Optional.empty();
            }

            Map<?, ?> texturedComponents = texturedComponents(textureData);
            SimpleContainer inputInventory = new SimpleContainer(maxTexturableComponentCount(components.size()));
            List<ItemStack> materialStacks = new ArrayList<>();
            int slot = 0;
            for (Object component : components) {
                Object componentId = componentId(component);
                Object material = texturedComponents.get(componentId);
                if (material == null) {
                    material = Blocks.AIR;
                }
                if (!(material instanceof Block materialBlock)) {
                    return Optional.empty();
                }
                ItemStack materialStack = new ItemStack(materialBlock);
                if (materialStack.isEmpty()) {
                    return Optional.empty();
                }
                inputInventory.setItem(slot++, materialStack.copyWithCount(1));
                materialStacks.add(materialStack.copyWithCount(1));
            }

            Object inputObject = architectsCutterRecipeInput(inputInventory);
            if (!(inputObject instanceof RecipeInput recipeInput)) {
                return Optional.empty();
            }

            RecipeType<?> recipeType = architectsCutterRecipeType();
            @SuppressWarnings({"rawtypes", "unchecked"})
            List<RecipeHolder<?>> recipes = (List) level.getRecipeManager().getRecipesFor((RecipeType) recipeType, recipeInput, level);
            List<AssembledCutterRecipe> matches = new ArrayList<>();
            for (RecipeHolder<?> recipe : recipes) {
                ItemStack assembled = assembleCutterRecipe(recipe.value(), inputObject, level.registryAccess()).copy();
                Object assembledBlock = domumBlock(assembled);
                if (assembledBlock == null || texturedBlockComponents(assembledBlock).size() != materialStacks.size()) {
                    continue;
                }
                if (ItemStack.isSameItemSameComponents(assembled, requestedStack)) {
                    matches.add(new AssembledCutterRecipe(recipe, assembled));
                }
            }
            if (matches.isEmpty()) {
                return Optional.empty();
            }

            AssembledCutterRecipe primary = matches.getFirst();
            List<ItemStack> alternateOutputs = matches.stream()
                    .skip(1)
                    .map(match -> match.output().copy())
                    .toList();
            List<List<ItemStack>> inputs = materialStacks.stream()
                    .map(stack -> List.of(stack.copyWithCount(1)))
                    .toList();
            IGenericRecipe genericRecipe = GenericRecipe.builder()
                    .withRecipeId(primary.recipe().id())
                    .withOutputs(primary.output().copy(), alternateOutputs)
                    .withInputs(inputs)
                    .withGridSize(3)
                    .build();
            return Optional.of(new CutterRecipeMatch(primary.recipe().id(), genericRecipe, primary.output().copy(), List.copyOf(materialStacks)));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        } catch (LinkageError | ReflectiveOperationException ignored) {
            return Optional.empty();
        }
    }

    public static List<IngredientRequirement> findCutterRequirements(RecipeManager recipeManager, HolderLookup.Provider provider, ItemStack requestedStack) {
        Optional<RecipeHolder<?>> recipe = findCutterRecipeHolder(recipeManager, provider, requestedStack);
        if (recipe.isEmpty()) {
            return List.of();
        }

        ItemStack result = recipe.get().value().getResultItem(provider);
        int outputCount = Math.max(1, result.getCount());
        int crafts = Math.max(1, (requestedStack.getCount() + outputCount - 1) / outputCount);
        List<ItemStack> materialStacks = materialCandidatesFromComponents(requestedStack).stream()
                .flatMap(candidate -> candidate.stack().stream())
                .toList();
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
            if (stacks.isEmpty() && !materialStacks.isEmpty()) {
                for (ItemStack materialStack : materialStacks) {
                    int required = Math.max(1, materialStack.getCount()) * crafts;
                    ResourceLocation itemId = itemId(materialStack);
                    stacks.putIfAbsent(itemId, materialStack.copyWithCount(required));
                    counts.merge(itemId, required, Integer::sum);
                }
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
        List<MaterialCandidate> materials = materialCandidatesFromComponents(requestedStack);
        String materialSummary = materials.stream()
                .map(MaterialCandidate::debugSummary)
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
        String generationSummary = materialGenerationSummary(recipe.get(), provider, requestedStack, materials, crafts);
        return "recipe=" + recipe.get().id()
                + " output=" + itemId(result)
                + " outputCount=" + outputCount
                + " requestedCount=" + requestedStack.getCount()
                + " crafts=" + crafts
                + " materials=" + materialSummary
                + " generated=" + generated
                + " materialGeneration=" + generationSummary
                + " components=" + compactComponentSummary(requestedStack);
    }

    private static Object domumBlock(ItemStack stack) throws ReflectiveOperationException {
        Class<?> util = Class.forName("com.minecolonies.core.util.DomumOrnamentumUtils");
        return util.getMethod("getBlock", ItemStack.class).invoke(null, stack);
    }

    private static Object materialTextureData(ItemStack stack) throws ReflectiveOperationException {
        Class<?> textureData = Class.forName("com.ldtteam.domumornamentum.client.model.data.MaterialTextureData");
        return textureData.getMethod("readFromItemStack", ItemStack.class).invoke(null, stack);
    }

    private static boolean materialTextureDataIsEmpty(Object textureData) throws ReflectiveOperationException {
        return (boolean) textureData.getClass().getMethod("isEmpty").invoke(textureData);
    }

    private static Map<?, ?> texturedComponents(Object textureData) throws ReflectiveOperationException {
        Object result = textureData.getClass().getMethod("getTexturedComponents").invoke(textureData);
        return result instanceof Map<?, ?> map ? map : Map.of();
    }

    private static Collection<?> texturedBlockComponents(Object texturedBlock) throws ReflectiveOperationException {
        Class<?> blockInterface = Class.forName("com.ldtteam.domumornamentum.block.IMateriallyTexturedBlock");
        Object result = blockInterface.getMethod("getComponents").invoke(texturedBlock);
        return result instanceof Collection<?> collection ? collection : List.of();
    }

    private static Object componentId(Object component) throws ReflectiveOperationException {
        Class<?> componentInterface = Class.forName("com.ldtteam.domumornamentum.block.IMateriallyTexturedBlockComponent");
        return componentInterface.getMethod("getId").invoke(component);
    }

    private static int maxTexturableComponentCount(int fallback) {
        try {
            Class<?> manager = Class.forName("com.ldtteam.domumornamentum.block.MateriallyTexturedBlockManager");
            Object instance = manager.getMethod("getInstance").invoke(null);
            return (int) manager.getMethod("getMaxTexturableComponentCount").invoke(instance);
        } catch (LinkageError | ReflectiveOperationException | RuntimeException ignored) {
            return Math.max(1, fallback);
        }
    }

    private static Object architectsCutterRecipeInput(SimpleContainer inputInventory) throws ReflectiveOperationException {
        Class<?> inputClass = Class.forName("com.ldtteam.domumornamentum.recipe.architectscutter.ArchitectsCutterRecipeInput");
        Constructor<?> constructor = inputClass.getConstructor(net.minecraft.world.Container.class);
        return constructor.newInstance(inputInventory);
    }

    private static RecipeType<?> architectsCutterRecipeType() throws ReflectiveOperationException {
        Class<?> recipeTypes = Class.forName("com.ldtteam.domumornamentum.recipe.ModRecipeTypes");
        Field field = recipeTypes.getField("ARCHITECTS_CUTTER");
        Object holder = field.get(null);
        return (RecipeType<?>) holder.getClass().getMethod("get").invoke(holder);
    }

    private static ItemStack assembleCutterRecipe(Object recipe, Object input, HolderLookup.Provider provider) throws ReflectiveOperationException {
        for (Method method : recipe.getClass().getMethods()) {
            if (!method.getName().equals("assemble") || method.getParameterCount() != 2) {
                continue;
            }
            Object result = method.invoke(recipe, input, provider);
            if (result instanceof ItemStack stack) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static Optional<RecipeHolder<?>> findCutterRecipeHolder(RecipeManager recipeManager, HolderLookup.Provider provider, ItemStack requestedStack) {
        Optional<RecipeHolder<?>> compatibleMatch = Optional.empty();
        try {
            for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
                if (!isArchitectsCutterRecipe(holder)) {
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

    private static Optional<RecipeHolder<?>> findExactCutterRecipeHolder(RecipeManager recipeManager, HolderLookup.Provider provider, ItemStack requestedStack) {
        try {
            for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
                if (!isArchitectsCutterRecipe(holder)) {
                    continue;
                }

                ItemStack result = holder.value().getResultItem(provider);
                if (ItemStack.isSameItemSameComponents(result, requestedStack)) {
                    return Optional.of(holder);
                }
            }
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private static boolean isArchitectsCutterRecipe(RecipeHolder<?> holder) {
        ResourceLocation typeId = BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType());
        boolean cutterType = typeId != null && typeId.getNamespace().equals(DOMUM_ORNAMENTUM)
                && typeId.getPath().contains(ARCHITECTS_CUTTER);
        boolean cutterPath = holder.id().getNamespace().equals(DOMUM_ORNAMENTUM)
                && holder.id().getPath().contains(ARCHITECTS_CUTTER);
        return cutterType || cutterPath;
    }

    private static List<MaterialCandidate> materialCandidatesFromComponents(ItemStack requestedStack) {
        Map<ResourceLocation, MaterialCandidate> candidates = new LinkedHashMap<>();
        collectMaterialIds(requestedStack.getComponents().toString(), requestedStack, candidates);
        collectMaterialIds(requestedStack.getComponentsPatch().toString(), requestedStack, candidates);
        return new ArrayList<>(candidates.values());
    }

    private static void collectMaterialIds(String data, ItemStack requestedStack, Map<ResourceLocation, MaterialCandidate> candidates) {
        Matcher blockMatcher = BLOCK_TOKEN.matcher(data);
        while (blockMatcher.find()) {
            collectMaterialId("Block{" + blockMatcher.group(1) + "}", blockMatcher.group(1), requestedStack, candidates);
        }

        Matcher matcher = RESOURCE_LOCATION.matcher(data);
        while (matcher.find()) {
            collectMaterialId(matcher.group(), matcher.group(), requestedStack, candidates);
        }
    }

    private static void collectMaterialId(String rawToken, String idText, ItemStack requestedStack, Map<ResourceLocation, MaterialCandidate> candidates) {
        ResourceLocation id = ResourceLocation.tryParse(idText);
        if (id == null) {
            return;
        }
        if (itemId(requestedStack).equals(id) || candidates.containsKey(id)) {
            return;
        }
        candidates.put(id, resolveMaterial(rawToken, id));
    }

    private static MaterialCandidate resolveMaterial(String rawToken, ResourceLocation id) {
        Optional<ItemStack> itemStack = BuiltInRegistries.ITEM.getOptional(id)
                .map(ItemStack::new)
                .filter(stack -> !stack.isEmpty());
        if (itemStack.isPresent()) {
            return new MaterialCandidate(rawToken, id, itemStack, "item", "");
        }

        Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(id);
        if (block.isEmpty()) {
            return new MaterialCandidate(rawToken, id, Optional.empty(), "unresolved", "registry lookup failed");
        }
        Item item = block.get().asItem();
        ItemStack stack = new ItemStack(item);
        if (stack.isEmpty()) {
            return new MaterialCandidate(rawToken, id, Optional.empty(), "block", "block has no item form");
        }
        return new MaterialCandidate(rawToken, id, Optional.of(stack), "block", "");
    }

    private static String materialGenerationSummary(RecipeHolder<?> recipe, HolderLookup.Provider provider, ItemStack requestedStack,
                                                    List<MaterialCandidate> materials, int crafts) {
        List<Integer> inputCounts = recipeInputCounts(recipe);
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < materials.size(); i++) {
            MaterialCandidate material = materials.get(i);
            int baseCount = i < inputCounts.size()
                    ? inputCounts.get(i)
                    : material.stack().map(ItemStack::getCount).orElse(1);
            int scaledCount = Math.max(1, baseCount) * crafts;
            lines.add(material.debugSummary()
                    + " baseCount=" + baseCount
                    + " scaledCount=" + scaledCount
                    + " generated=" + material.stack().isPresent()
                    + (material.skipReason().isBlank() ? "" : " skip=" + material.skipReason()));
        }
        if (lines.isEmpty()) {
            lines.add("no material components found");
        }
        if (recipe.value().getResultItem(provider).isEmpty()) {
            lines.add("recipe output count unavailable");
        }
        return lines.toString();
    }

    private static List<Integer> recipeInputCounts(RecipeHolder<?> recipe) {
        List<Integer> counts = new ArrayList<>();
        try {
            for (Ingredient ingredient : recipe.value().getIngredients()) {
                if (ingredient == null || ingredient.isEmpty()) {
                    continue;
                }
                ItemStack[] options = ingredient.getItems();
                if (options.length == 0 || options[0].isEmpty()) {
                    continue;
                }
                counts.add(Math.max(1, options[0].getCount()));
            }
        } catch (RuntimeException ignored) {
            // Debug-only detail; an empty list falls back to material stack counts.
        }
        return counts;
    }

    private static String compactComponentSummary(ItemStack stack) {
        String summary = (stack.getComponents() + " " + stack.getComponentsPatch()).replaceAll("\\s+", " ");
        return summary.length() <= 240 ? summary : summary.substring(0, 240) + "...";
    }

    public record IngredientRequirement(ItemStack stack, int count) {
    }

    public record CutterRecipeMatch(ResourceLocation recipeId, IGenericRecipe genericRecipe, ItemStack assembledOutput, List<ItemStack> materialStacks) {
    }

    private record AssembledCutterRecipe(RecipeHolder<?> recipe, ItemStack output) {
    }

    private record MaterialCandidate(String rawToken, ResourceLocation id, Optional<ItemStack> stack, String resolution, String skipReason) {
        String debugSummary() {
            return "{raw=" + rawToken
                    + ", id=" + id
                    + ", resolvedAs=" + resolution
                    + ", stack=" + stack.map(value -> value.getHoverName().getString()).orElse("none")
                    + (skipReason.isBlank() ? "" : ", skip=" + skipReason)
                    + "}";
        }
    }
}
