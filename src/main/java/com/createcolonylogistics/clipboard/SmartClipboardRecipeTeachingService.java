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
import java.util.ArrayList;

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
        int disabled = 0;
        List<Component> detailMessages = new ArrayList<>();

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
                TeachCheck check = canTeach(module, recipeManager, match.get());
                if (check.status() == TeachStatus.DISABLED) {
                    disabled++;
                    detailMessages.add(Component.literal(recipeName(match.get()) + " recipe is disabled.")
                            .withStyle(ChatFormatting.YELLOW));
                    continue;
                }
                if (check.status() != TeachStatus.TEACHABLE) {
                    continue;
                }

                IToken<?> token = recipeManager.checkOrAddRecipe(match.get().recipeStorage());
                if (token == null || !module.addRecipe(token)) {
                    continue;
                }
                targetBuilding.markDirty();
                taught++;
                detailMessages.add(Component.literal(recipeName(match.get()) + " was taught.")
                        .withStyle(ChatFormatting.DARK_GREEN));
            }
        }

        for (Component message : detailMessages) {
            player.sendSystemMessage(message);
        }
        if (taught > 0) {
            SoundUtils.playSuccessSound(player, player.blockPosition());
            player.displayClientMessage(Component.literal(summary(taught, disabled)).withStyle(ChatFormatting.DARK_GREEN), true);
        } else if (disabled > 0) {
            player.displayClientMessage(Component.literal(summary(taught, disabled)).withStyle(ChatFormatting.YELLOW), true);
        } else {
            noAvailable(player, true);
        }
        return true;
    }

    private static TeachCheck canTeach(ICraftingBuildingModule module, IRecipeManager recipeManager, DomumOrnamentumRequestInspector.CutterRecipeMatch match) {
        try {
            if (!module.isRecipeCompatible(match.genericRecipe())) {
                return TeachCheck.rejected();
            }
            IRecipeStorage knownRecipe = module.getFirstRecipe(match.assembledOutput());
            if (knownRecipe != null && ItemStack.isSameItemSameComponents(knownRecipe.getPrimaryOutput(), match.assembledOutput())) {
                return TeachCheck.known();
            }
            IToken<?> existingToken = recipeManager.getRecipeId(match.recipeStorage());
            if (existingToken != null) {
                if (module.isDisabled(existingToken)) {
                    return TeachCheck.disabled();
                }
                if (module.holdsRecipe(existingToken)) {
                    return TeachCheck.known();
                }
            }
            Optional<IToken<?>> disabledToken = disabledMatchingRecipe(module, recipeManager, match);
            if (disabledToken.isPresent()) {
                return TeachCheck.disabled();
            }
            return TeachCheck.teachable();
        } catch (RuntimeException ignored) {
            return TeachCheck.rejected();
        }
    }

    private static Optional<IToken<?>> disabledMatchingRecipe(ICraftingBuildingModule module, IRecipeManager recipeManager,
                                                              DomumOrnamentumRequestInspector.CutterRecipeMatch match) {
        for (IToken<?> token : module.getRecipes()) {
            try {
                if (!module.isDisabled(token)) {
                    continue;
                }
                IRecipeStorage stored = recipeManager.getRecipe(token);
                if (stored != null && sameRecipe(stored, match.recipeStorage())) {
                    return Optional.of(token);
                }
            } catch (RuntimeException ignored) {
                // Keep scanning other recipes.
            }
        }
        return Optional.empty();
    }

    private static boolean sameRecipe(IRecipeStorage left, IRecipeStorage right) {
        if (!ItemStack.isSameItemSameComponents(left.getPrimaryOutput(), right.getPrimaryOutput())) {
            return false;
        }
        return recipeFingerprint(left).equals(recipeFingerprint(right));
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
        noAvailable(player, false);
    }

    private static void noAvailable(ServerPlayer player, boolean playSound) {
        if (playSound) {
            SoundUtils.playErrorSound(player, player.blockPosition());
        }
        player.displayClientMessage(Component.literal("No available recipes can be taught at this Worker Hut.")
                .withStyle(ChatFormatting.GRAY), true);
    }

    private static String recipeName(DomumOrnamentumRequestInspector.CutterRecipeMatch match) {
        return match.assembledOutput().getHoverName().getString();
    }

    private static String summary(int taught, int disabled) {
        if (taught > 0 && disabled > 0) {
            return taught + " recipe" + plural(taught) + " taught; " + disabled + " disabled.";
        }
        if (taught > 0) {
            return taught + " recipe" + plural(taught) + " taught.";
        }
        return disabled + " recipe" + plural(disabled) + " disabled.";
    }

    private static String plural(int count) {
        return count == 1 ? "" : "s";
    }

    private enum TeachStatus {
        TEACHABLE,
        KNOWN,
        DISABLED,
        REJECTED
    }

    private record TeachCheck(TeachStatus status) {
        static TeachCheck teachable() {
            return new TeachCheck(TeachStatus.TEACHABLE);
        }

        static TeachCheck known() {
            return new TeachCheck(TeachStatus.KNOWN);
        }

        static TeachCheck disabled() {
            return new TeachCheck(TeachStatus.DISABLED);
        }

        static TeachCheck rejected() {
            return new TeachCheck(TeachStatus.REJECTED);
        }
    }
}
