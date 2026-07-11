package com.createcolonylogistics.clipboard;

import com.createcolonylogistics.CreateColonyLogistics;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.ICraftingBuildingModule;
import com.minecolonies.api.crafting.IRecipeManager;
import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.crafting.ModCraftingTypes;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.util.OptionalPredicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

// DIAGNOSTIC ONLY - remove after Cutter recipe source-truth audit
public final class SmartInfoRecipeDiagnosticDumper {
    public static final boolean ENABLE_SMART_INFO_RECIPE_DIAGNOSTICS = false;
    private static final Path OUTPUT_DIR = Path.of("run", "smart-info-recipe-diagnostics");
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private SmartInfoRecipeDiagnosticDumper() {
    }

    public static Optional<Path> dump(ServerPlayer player, IColony colony, ItemStack hoveredStack, String context) {
        if (!ENABLE_SMART_INFO_RECIPE_DIAGNOSTICS || hoveredStack == null || hoveredStack.isEmpty()) {
            return Optional.empty();
        }
        try {
            Files.createDirectories(OUTPUT_DIR);
            String itemName = sanitizeFilePart(hoveredStack.getHoverName().getString());
            Path path = OUTPUT_DIR.resolve(LocalDateTime.now().format(FILE_TIME) + "-" + itemName + ".md");
            Files.writeString(path, buildReport(player, colony, hoveredStack.copy(), context == null ? "" : context));
            CreateColonyLogistics.LOGGER.info("[SmartInfoRecipeDiagnostic] wrote {}", path.toAbsolutePath());
            return Optional.of(path);
        } catch (IOException | RuntimeException exception) {
            CreateColonyLogistics.LOGGER.warn("[SmartInfoRecipeDiagnostic] failed to write recipe diagnostic", exception);
            return Optional.empty();
        }
    }

    private static String buildReport(ServerPlayer player, IColony colony, ItemStack stack, String context) {
        StringBuilder out = new StringBuilder();
        out.append("# Smart Info Recipe Diagnostic\n\n");
        out.append("- Player: ").append(player.getGameProfile().getName()).append('\n');
        out.append("- Colony: ").append(colony.getName()).append(" (").append(colony.getID()).append(")\n");
        out.append("- Context: ").append(context.isBlank() ? "unspecified" : context).append("\n\n");

        appendStackIdentity(out, "Hovered Stack", stack);
        TextureDiagnostic texture = inspectTexture(stack);
        appendTexture(out, texture);

        CutterDiagnostic cutter = inspectCutter(player.serverLevel(), stack, texture);
        appendCutter(out, cutter);

        SmartInfoClassificationService.Classification classification = SmartInfoClassificationService.classify(colony, player.serverLevel(), stack);
        appendClassification(out, classification);
        appendModules(out, colony, stack, cutter.match().orElse(null));
        appendConclusion(out, stack, cutter, classification);
        return out.toString();
    }

    private static void appendStackIdentity(StringBuilder out, String title, ItemStack stack) {
        out.append("## ").append(title).append("\n\n");
        out.append("- item id: ").append(itemId(stack)).append('\n');
        out.append("- display name: ").append(stack.getHoverName().getString()).append('\n');
        out.append("- count: ").append(stack.getCount()).append('\n');
        out.append("- exact components: `").append(escapeInline(stack.getComponents().toString())).append("`\n");
        out.append("- components patch: `").append(escapeInline(stack.getComponentsPatch().toString())).append("`\n");
        out.append("- exact stack key: `").append(escapeInline(SmartClipboardReport.exactStackKey(stack))).append("`\n");
        out.append("- material key: `").append(escapeInline(SmartClipboardReport.domumMaterialKey(stack))).append("`\n");
        out.append("- fingerprint: `").append(escapeInline(DomumOrnamentumRequestInspector.exactComboFingerprint(stack))).append("`\n\n");
    }

    private static void appendTexture(StringBuilder out, TextureDiagnostic diagnostic) {
        out.append("## Texture Data\n\n");
        out.append("- is DO stack: ").append(diagnostic.domumStack()).append('\n');
        out.append("- textured block present: ").append(diagnostic.texturedBlockPresent()).append('\n');
        out.append("- MaterialTextureData present: ").append(diagnostic.textureDataPresent()).append('\n');
        out.append("- MaterialTextureData empty: ").append(diagnostic.textureDataEmpty()).append('\n');
        out.append("- textured block component ids: ").append(diagnostic.componentIds()).append('\n');
        out.append("- textured component map entries: ").append(diagnostic.componentEntries()).append('\n');
        out.append("- missing component ids: ").append(diagnostic.missingComponentIds()).append('\n');
        out.append("- AIR substitutions: ").append(diagnostic.airSubstitutions()).append('\n');
        out.append("- non-Block material rejections: ").append(diagnostic.nonBlockRejections()).append("\n\n");
        if (!diagnostic.failure().isBlank()) {
            out.append("- texture inspection failure: ").append(diagnostic.failure()).append("\n\n");
        }
    }

    private static void appendCutter(StringBuilder out, CutterDiagnostic diagnostic) {
        out.append("## findArchitectsCutterMatch\n\n");
        out.append("- called: true\n");
        out.append("- match present: ").append(diagnostic.match().isPresent()).append('\n');
        out.append("- rejection reason if absent: ").append(diagnostic.match().isPresent() ? "n/a" : diagnostic.rejectionReason()).append('\n');
        out.append("- recipe candidates returned by getRecipesFor: ").append(diagnostic.candidates().size()).append("\n\n");

        for (CandidateDiagnostic candidate : diagnostic.candidates()) {
            out.append("### Candidate ").append(candidate.recipeId()).append("\n\n");
            appendStackSummary(out, "- assembled output", candidate.assembledOutput());
            out.append("- accepted: ").append(candidate.accepted()).append('\n');
            out.append("- rejection reason: ").append(candidate.rejectionReason()).append("\n\n");
        }

        diagnostic.match().ifPresent(match -> {
            out.append("## Selected Generated Recipe\n\n");
            out.append("- selected recipe id: ").append(match.recipeId()).append('\n');
            appendStackSummary(out, "- assembled output", match.assembledOutput());
            appendStackSummary(out, "- GenericRecipe primary output", match.genericRecipe().getPrimaryOutput());
            appendStackSummary(out, "- RecipeStorage primary output", match.recipeStorage().getPrimaryOutput());
            out.append("- generated materialStacks:\n");
            for (int i = 0; i < match.materialStacks().size(); i++) {
                ItemStack material = match.materialStacks().get(i);
                out.append("  - input ").append(i + 1).append(": ").append(stackLine(material)).append('\n');
                out.append("    - tags: ").append(itemTags(material)).append('\n');
            }
            out.append("- RecipeStorage inputs:\n");
            for (ItemStorage input : match.recipeStorage().getInput()) {
                out.append("  - ").append(stackLine(input.getItemStack().copyWithCount(input.getAmount()))).append('\n');
            }
            out.append('\n');
        });
    }

    private static void appendClassification(StringBuilder out, SmartInfoClassificationService.Classification classification) {
        out.append("## Classification Result\n\n");
        out.append("- classifier status: ").append(classification.status()).append('\n');
        out.append("- reason: ").append(classification.reason()).append('\n');
        out.append("- recipe id: ").append(classification.recipeId().map(ResourceLocation::toString).orElse("none")).append('\n');
        out.append("- shape source: ").append(classification.recipeId().isPresent() ? "classifier recipe id" : "none").append('\n');
        out.append("- knownBy: ").append(classification.knownBy()).append('\n');
        out.append("- canLearn: ").append(classification.canLearn()).append("\n\n");
    }

    private static void appendModules(StringBuilder out, IColony colony, ItemStack stack, DomumOrnamentumRequestInspector.CutterRecipeMatch match) {
        out.append("## Module Validation\n\n");
        IRecipeManager recipeManager = IColonyManager.getInstance().getRecipeManager();
        for (IBuilding building : colony.getBuildingManager().getBuildings().values()) {
            for (ICraftingBuildingModule module : building.getModulesByType(ICraftingBuildingModule.class)) {
                if (!ColonyProductionInspector.supportsArchitectsCutter(module)) {
                    continue;
                }
                out.append("### ").append(ColonyProductionInspector.buildingLabel(building, module)).append("\n\n");
                out.append("- module id: ").append(safeModuleId(module)).append('\n');
                boolean canLearn = ColonyProductionInspector.safeCanLearn(module);
                out.append("- canLearn(ARCHITECTS_CUTTER): ").append(canLearn).append('\n');
                if (match == null) {
                    out.append("- isRecipeCompatible(generated recipe): false\n");
                    out.append("- reason: no generated CutterRecipeMatch was available.\n\n");
                    continue;
                }
                boolean compatible = ColonyProductionInspector.safeIsRecipeCompatible(module, match.genericRecipe());
                out.append("- isRecipeCompatible(generated recipe): ").append(compatible).append('\n');
                IRecipeStorage known = ColonyProductionInspector.safeGetFirstRecipe(module, stack);
                out.append("- known recipe result: ").append(known == null ? "none" : stackLine(known.getPrimaryOutput())).append('\n');
                IToken<?> existingToken = safeRecipeToken(recipeManager, match.recipeStorage());
                out.append("- existing recipe token: ").append(existingToken == null ? "none" : existingToken).append('\n');
                out.append("- disabled/held token state: ").append(tokenState(module, existingToken)).append('\n');
                List<String> matchingInputs = matchingInputs(module, match.materialStacks());
                out.append("- validator-matched generated inputs: ").append(matchingInputs).append('\n');
                out.append("- exact reason: ").append(moduleReason(canLearn, compatible, matchingInputs)).append("\n\n");
            }
        }
    }

    private static void appendConclusion(StringBuilder out, ItemStack stack, CutterDiagnostic cutter,
                                         SmartInfoClassificationService.Classification classification) {
        String path = DomumOrnamentumRequestInspector.itemId(stack).getPath();
        out.append("## Diagnostic Conclusion\n\n");
        if (path.contains("stair")) {
            out.append("### Wood Stair Questions\n\n");
            out.append("- exact material stacks generated: ")
                    .append(cutter.match().map(match -> match.materialStacks().stream().map(SmartInfoRecipeDiagnosticDumper::stackLine).toList().toString()).orElse("none"))
                    .append('\n');
            out.append("- Sawmill/Stonemason pass/fail: see Module Validation section.\n");
            out.append("- if Stonemason passes, the matching input listed above is the ingredient that caused it.\n");
            out.append("- generated recipe materialized: ").append(cutter.match().isPresent() ? "inspect materialStacks and one-option RecipeStorage inputs above" : "no match").append("\n\n");
        }
        if (path.contains("panel")) {
            out.append("### Panel Questions\n\n");
            out.append("- findArchitectsCutterMatch returned match: ").append(cutter.match().isPresent()).append('\n');
            out.append("- material stacks generated: ")
                    .append(cutter.match().map(match -> match.materialStacks().stream().map(SmartInfoRecipeDiagnosticDumper::stackLine).toList().toString()).orElse("none"))
                    .append('\n');
            out.append("- selected panel recipe variant: ").append(cutter.match().map(match -> match.recipeId().toString()).orElse("none")).append('\n');
            out.append("- modules accepting generated recipe: ").append(classification.canLearn()).append('\n');
            out.append("- if modules accept it but Can learn is missing in-game, investigate post-classification paths separately.\n\n");
        }
    }

    private static TextureDiagnostic inspectTexture(ItemStack stack) {
        if (!DomumOrnamentumRequestInspector.isDomumOrnamentumStack(stack)) {
            return new TextureDiagnostic(false, false, false, true, List.of(), List.of(), List.of(), List.of(), List.of(), "not a DO stack");
        }
        try {
            Object texturedBlock = domumBlock(stack);
            Object textureData = materialTextureData(stack);
            boolean textureEmpty = textureData == null || materialTextureDataIsEmpty(textureData);
            Collection<?> components = texturedBlock == null ? List.of() : texturedBlockComponents(texturedBlock);
            Map<?, ?> texturedComponents = textureData == null ? Map.of() : texturedComponents(textureData);
            List<String> componentIds = new ArrayList<>();
            List<String> entries = new ArrayList<>();
            List<String> missing = new ArrayList<>();
            List<String> air = new ArrayList<>();
            List<String> nonBlock = new ArrayList<>();
            for (Object component : components) {
                Object componentId = componentId(component);
                String id = String.valueOf(componentId);
                componentIds.add(id);
                Object material = texturedComponents.get(componentId);
                if (material == null) {
                    missing.add(id);
                    material = Blocks.AIR;
                    air.add(id);
                }
                entries.add(id + "=" + material);
                if (!(material instanceof Block)) {
                    nonBlock.add(id + "=" + material);
                }
            }
            return new TextureDiagnostic(true, texturedBlock != null, textureData != null, textureEmpty,
                    componentIds, entries, missing, air, nonBlock, "");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            return new TextureDiagnostic(true, false, false, true, List.of(), List.of(), List.of(), List.of(), List.of(),
                    exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    private static CutterDiagnostic inspectCutter(ServerLevel level, ItemStack stack, TextureDiagnostic texture) {
        if (level == null || stack.isEmpty() || !DomumOrnamentumRequestInspector.isDomumOrnamentumStack(stack)) {
            return new CutterDiagnostic(Optional.empty(), List.of(), "not a DO stack or no level");
        }
        if (!texture.textureDataPresent() || texture.textureDataEmpty()) {
            return new CutterDiagnostic(Optional.empty(), List.of(), "MaterialTextureData missing or empty");
        }
        try {
            List<ItemStack> materialStacks = materialStacks(stack);
            if (materialStacks.isEmpty()) {
                return new CutterDiagnostic(Optional.empty(), List.of(), "no material stacks generated");
            }
            Object inputObject = architectsCutterRecipeInput(materialStacks);
            if (!(inputObject instanceof RecipeInput recipeInput)) {
                return new CutterDiagnostic(Optional.empty(), List.of(), "ArchitectsCutterRecipeInput was not a RecipeInput");
            }
            RecipeType<?> recipeType = architectsCutterRecipeType();
            @SuppressWarnings({"rawtypes", "unchecked"})
            List<RecipeHolder<?>> recipes = (List) level.getRecipeManager().getRecipesFor((RecipeType) recipeType, recipeInput, level);
            List<CandidateDiagnostic> candidates = new ArrayList<>();
            for (RecipeHolder<?> recipe : recipes) {
                ItemStack assembled = assembleCutterRecipe(recipe.value(), inputObject, level.registryAccess()).copy();
                String rejection = materializedOutputRejection(assembled, stack, materialStacks.size());
                candidates.add(new CandidateDiagnostic(recipe.id(), assembled, rejection.isBlank(), rejection.isBlank() ? "accepted" : rejection));
            }
            Optional<DomumOrnamentumRequestInspector.CutterRecipeMatch> match = DomumOrnamentumRequestInspector.findArchitectsCutterMatch(level, stack);
            return new CutterDiagnostic(match, candidates, match.isPresent() ? "n/a" : "no candidate assembled output matched requested stack");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            return new CutterDiagnostic(Optional.empty(), List.of(), exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    private static List<ItemStack> materialStacks(ItemStack stack) throws ReflectiveOperationException {
        Object texturedBlock = domumBlock(stack);
        Object textureData = materialTextureData(stack);
        if (texturedBlock == null || textureData == null || materialTextureDataIsEmpty(textureData)) {
            return List.of();
        }
        Map<?, ?> texturedComponents = texturedComponents(textureData);
        List<ItemStack> stacks = new ArrayList<>();
        for (Object component : texturedBlockComponents(texturedBlock)) {
            Object material = texturedComponents.get(componentId(component));
            if (material == null) {
                material = Blocks.AIR;
            }
            if (!(material instanceof Block block)) {
                return List.of();
            }
            ItemStack materialStack = new ItemStack(block);
            if (materialStack.isEmpty()) {
                return List.of();
            }
            stacks.add(materialStack.copyWithCount(1));
        }
        return stacks;
    }

    private static Object architectsCutterRecipeInput(List<ItemStack> materialStacks) throws ReflectiveOperationException {
        SimpleContainer inputInventory = new SimpleContainer(maxTexturableComponentCount(materialStacks.size()));
        for (int i = 0; i < materialStacks.size(); i++) {
            inputInventory.setItem(i, materialStacks.get(i).copyWithCount(1));
        }
        Class<?> inputClass = Class.forName("com.ldtteam.domumornamentum.recipe.architectscutter.ArchitectsCutterRecipeInput");
        Constructor<?> constructor = inputClass.getConstructor(net.minecraft.world.Container.class);
        return constructor.newInstance(inputInventory);
    }

    private static String materializedOutputRejection(ItemStack assembled, ItemStack requested, int materialSlots) {
        if (assembled.isEmpty()) {
            return "assembled output empty";
        }
        if (!itemId(assembled).equals(itemId(requested))) {
            return "assembled item id " + itemId(assembled) + " != requested " + itemId(requested);
        }
        if (ItemStack.isSameItemSameComponents(assembled, requested)) {
            return "";
        }
        try {
            Object assembledBlock = domumBlock(assembled);
            Object requestedBlock = domumBlock(requested);
            if (assembledBlock == null || requestedBlock == null) {
                return "assembled/requested DO block missing";
            }
            if (texturedBlockComponents(assembledBlock).size() != materialSlots
                    || texturedBlockComponents(requestedBlock).size() != materialSlots) {
                return "component count does not equal material slot count " + materialSlots;
            }
            Object assembledTexture = materialTextureData(assembled);
            Object requestedTexture = materialTextureData(requested);
            if (assembledTexture == null || requestedTexture == null
                    || materialTextureDataIsEmpty(assembledTexture)
                    || materialTextureDataIsEmpty(requestedTexture)) {
                return "assembled/requested texture data missing or empty";
            }
            return texturedComponents(assembledTexture).equals(texturedComponents(requestedTexture))
                    ? ""
                    : "textured component maps differ";
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            return exception.getClass().getSimpleName() + ": " + exception.getMessage();
        }
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

    private static List<String> matchingInputs(ICraftingBuildingModule module, List<ItemStack> inputs) {
        OptionalPredicate<ItemStack> validator;
        try {
            validator = module.getIngredientValidator();
        } catch (RuntimeException ignored) {
            return List.of("validator unavailable");
        }
        if (validator == null) {
            return List.of("validator null");
        }
        List<String> matches = new ArrayList<>();
        for (ItemStack input : inputs) {
            try {
                Optional<Boolean> result = validator.test(input);
                if (result.orElse(false)) {
                    matches.add(stackLine(input));
                } else if (result.isEmpty()) {
                    matches.add(stackLine(input) + " (validator undecided)");
                }
            } catch (RuntimeException ignored) {
                matches.add(stackLine(input) + " (validator threw)");
            }
        }
        return matches;
    }

    private static IToken<?> safeRecipeToken(IRecipeManager recipeManager, IRecipeStorage storage) {
        try {
            return recipeManager.getRecipeId(storage);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String tokenState(ICraftingBuildingModule module, IToken<?> token) {
        if (token == null) {
            return "no existing token";
        }
        try {
            if (module.isDisabled(token)) {
                return "disabled";
            }
            if (module.holdsRecipe(token)) {
                return "held";
            }
            return "token exists but is not held/disabled";
        } catch (RuntimeException exception) {
            return "unavailable: " + exception.getClass().getSimpleName();
        }
    }

    private static String moduleReason(boolean canLearn, boolean compatible, List<String> matchingInputs) {
        if (!canLearn) {
            return "module cannot learn Architects Cutter recipes";
        }
        if (!compatible) {
            return matchingInputs.isEmpty()
                    ? "no generated ingredient matched the module ingredient validator"
                    : "validator matched inputs but module.isRecipeCompatible rejected the generated recipe";
        }
        return matchingInputs.isEmpty()
                ? "module accepted the recipe, but matching input could not be identified from getIngredientValidator"
                : "module accepted because generated input(s) matched its ingredient validator";
    }

    private static String safeModuleId(ICraftingBuildingModule module) {
        try {
            return String.valueOf(module.getId());
        } catch (RuntimeException ignored) {
            return "unavailable";
        }
    }

    private static void appendStackSummary(StringBuilder out, String label, ItemStack stack) {
        out.append(label).append(": ").append(stackLine(stack)).append('\n');
        out.append("  - exact key: `").append(escapeInline(SmartClipboardReport.exactStackKey(stack))).append("`\n");
        out.append("  - material key: `").append(escapeInline(SmartClipboardReport.domumMaterialKey(stack))).append("`\n");
        out.append("  - fingerprint: `").append(escapeInline(DomumOrnamentumRequestInspector.exactComboFingerprint(stack))).append("`\n");
    }

    private static String stackLine(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "empty";
        }
        return itemId(stack) + " x" + stack.getCount() + " (" + stack.getHoverName().getString() + ")";
    }

    private static String itemTags(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "[]";
        }
        return stack.getTags()
                .map(tag -> tag.location().toString())
                .sorted()
                .collect(Collectors.toList())
                .toString();
    }

    private static ResourceLocation itemId(ItemStack stack) {
        return stack == null || stack.isEmpty()
                ? ResourceLocation.fromNamespaceAndPath("minecraft", "air")
                : BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    private static String sanitizeFilePart(String value) {
        String sanitized = value == null ? "unknown" : value.toLowerCase().replaceAll("[^a-z0-9._-]+", "-");
        return sanitized.isBlank() ? "unknown" : sanitized;
    }

    private static String escapeInline(String value) {
        return value == null ? "" : value.replace("`", "'");
    }

    private record TextureDiagnostic(
            boolean domumStack,
            boolean texturedBlockPresent,
            boolean textureDataPresent,
            boolean textureDataEmpty,
            List<String> componentIds,
            List<String> componentEntries,
            List<String> missingComponentIds,
            List<String> airSubstitutions,
            List<String> nonBlockRejections,
            String failure
    ) {
    }

    private record CutterDiagnostic(
            Optional<DomumOrnamentumRequestInspector.CutterRecipeMatch> match,
            List<CandidateDiagnostic> candidates,
            String rejectionReason
    ) {
    }

    private record CandidateDiagnostic(ResourceLocation recipeId, ItemStack assembledOutput, boolean accepted, String rejectionReason) {
    }
}
