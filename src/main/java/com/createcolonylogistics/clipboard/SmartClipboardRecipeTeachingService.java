package com.createcolonylogistics.clipboard;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.ICraftingBuildingModule;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.crafting.IRecipeManager;
import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.api.crafting.ModCraftingTypes;
import com.minecolonies.api.util.SoundUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class SmartClipboardRecipeTeachingService {
    private static final int MAX_TEACH_REQUEST_SCAN = 1000;

    private SmartClipboardRecipeTeachingService() {
    }

    public static boolean teachRequestedArchitectsCutterRecipes(ServerPlayer player, ServerLevel level, IColony colony, IBuilding targetBuilding) {
        List<ICraftingBuildingModule> modules = targetBuilding.getModulesByType(ICraftingBuildingModule.class).stream()
                .filter(SmartClipboardRecipeTeachingService::supportsArchitectsCutter)
                .toList();
        if (modules.isEmpty()) {
            fail(player);
            return true;
        }

        IRecipeManager recipeManager = IColonyManager.getInstance().getRecipeManager();
        RequestAnalysisService.AnalysisResult analysis = RequestAnalysisService.analyze(level, colony, MAX_TEACH_REQUEST_SCAN);
        Set<String> attempted = new LinkedHashSet<>();
        int taught = 0;

        for (RequestAnalysisService.RequestReportEntry entry : analysis.groupedEntries().values().stream().flatMap(List::stream).toList()) {
            Optional<DomumOrnamentumRequestInspector.CutterRecipeMatch> match =
                    DomumOrnamentumRequestInspector.findArchitectsCutterMatch(level, entry.requestedStack());
            if (match.isEmpty()) {
                continue;
            }

            for (ICraftingBuildingModule module : modules) {
                String key = module.getId() + "|" + recipeFingerprint(match.get().recipeStorage());
                if (!attempted.add(key)) {
                    continue;
                }
                if (!canTeach(module, recipeManager, match.get())) {
                    continue;
                }

                IToken<?> token = recipeManager.checkOrAddRecipe(match.get().recipeStorage());
                if (token == null || !module.addRecipe(token)) {
                    continue;
                }
                targetBuilding.markDirty();
                taught++;
                player.displayClientMessage(Component.literal(match.get().assembledOutput().getHoverName().getString() + " was taught.")
                        .withStyle(ChatFormatting.DARK_GREEN), true);
            }
        }

        if (taught > 0) {
            SoundUtils.playSuccessSound(player, player.blockPosition());
        } else {
            fail(player);
        }
        return true;
    }

    private static boolean canTeach(ICraftingBuildingModule module, IRecipeManager recipeManager, DomumOrnamentumRequestInspector.CutterRecipeMatch match) {
        try {
            if (!module.isRecipeCompatible(match.genericRecipe())) {
                return false;
            }
            IRecipeStorage knownRecipe = module.getFirstRecipe(match.assembledOutput());
            if (knownRecipe != null && ItemStack.isSameItemSameComponents(knownRecipe.getPrimaryOutput(), match.assembledOutput())) {
                return false;
            }
            IToken<?> existingToken = recipeManager.getRecipeId(match.recipeStorage());
            return existingToken == null || !module.holdsRecipe(existingToken);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean supportsArchitectsCutter(ICraftingBuildingModule module) {
        try {
            return module.canLearn(ModCraftingTypes.ARCHITECTS_CUTTER.get())
                    || module.getSupportedCraftingTypes().contains(ModCraftingTypes.ARCHITECTS_CUTTER.get());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String recipeFingerprint(IRecipeStorage storage) {
        StringBuilder builder = new StringBuilder();
        builder.append(DomumOrnamentumRequestInspector.exactComboFingerprint(storage.getPrimaryOutput()));
        storage.getInput().forEach(input -> builder
                .append('|')
                .append(DomumOrnamentumRequestInspector.exactComboFingerprint(input.getItemStack()))
                .append('x')
                .append(input.getAmount()));
        return builder.toString();
    }

    private static void fail(ServerPlayer player) {
        SoundUtils.playErrorSound(player, player.blockPosition());
        player.displayClientMessage(Component.literal("No available recipes can be taught at this Worker Hut.")
                .withStyle(ChatFormatting.DARK_RED), true);
    }
}
