package com.createcolonylogistics.client;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.jobs.IJobView;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.resolver.player.IPlayerRequestResolver;
import com.minecolonies.api.colony.requestsystem.resolver.retrying.IRetryingRequestResolver;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.items.component.ColonyId;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.IItemDecorator;

import java.util.HashSet;
import java.util.Set;

public class SmartColonyClipboardDecorator implements IItemDecorator {
    private static final int COUNT_RGB = 0xFF4500;
    private static final int ICON_SIZE = 16;
    private static final int MIN_ALPHA = 0x59;
    private static final int MAX_ALPHA = 0xFF;
    private static final int ALPHA_RANGE = MAX_ALPHA - MIN_ALPHA;
    private static final int PULSE_CYCLE_TICKS = 60;
    private static final int PULSE_HOLD_TICKS = 20;
    private static final int PULSE_FADE_OUT_TICKS = 20;
    private static final int PULSE_FADE_IN_TICKS = 20;

    @Override
    public boolean render(GuiGraphics graphics, Font font, ItemStack stack, int x, int y) {
        int count = pendingRequestCount(stack);
        if (count <= 0) {
            return false;
        }

        drawCenteredCount(graphics, font, count, x, y);
        return true;
    }

    private static int pendingRequestCount(ItemStack stack) {
        IColonyView colonyView = ColonyId.readColonyViewFromItemStack(stack);
        if (colonyView == null) {
            return 0;
        }

        try {
            Set<IToken<?>> asyncRequests = citizenAsyncRequests(colonyView);
            IRequestManager requestManager = colonyView.getRequestManager();
            if (requestManager == null) {
                return 0;
            }

            Set<IToken<?>> assignedRequests = new HashSet<>();
            IPlayerRequestResolver playerResolver = requestManager.getPlayerResolver();
            IRetryingRequestResolver retryingResolver = requestManager.getRetryingRequestResolver();
            assignedRequests.addAll(playerResolver.getAllAssignedRequests());
            assignedRequests.addAll(retryingResolver.getAllAssignedRequests());

            int count = 0;
            for (IToken<?> token : assignedRequests) {
                if (!asyncRequests.contains(token)) {
                    count++;
                }
            }
            return count;
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static Set<IToken<?>> citizenAsyncRequests(IColonyView colonyView) {
        Set<IToken<?>> asyncRequests = new HashSet<>();
        for (ICitizenDataView citizen : colonyView.getCitizens().values()) {
            IJobView job = citizen.getJobView();
            if (job != null) {
                asyncRequests.addAll(job.getAsyncRequests());
            }
        }
        return asyncRequests;
    }

    private static void drawCenteredCount(GuiGraphics graphics, Font font, int count, int x, int y) {
        String text = Integer.toString(count);
        int centerX = x + ICON_SIZE / 2;
        int drawY = y + (ICON_SIZE - font.lineHeight) / 2;

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 500.0F);
        graphics.drawCenteredString(font, Component.literal(text), centerX, drawY, pulsingCountColor());
        graphics.pose().popPose();
    }

    private static int pulsingCountColor() {
        Minecraft minecraft = Minecraft.getInstance();
        long tick = minecraft.level == null ? System.currentTimeMillis() / 50L : minecraft.level.getGameTime();
        long cycleTick = tick % PULSE_CYCLE_TICKS;
        int alpha;
        if (cycleTick < PULSE_HOLD_TICKS) {
            alpha = MAX_ALPHA;
        } else if (cycleTick < PULSE_HOLD_TICKS + PULSE_FADE_OUT_TICKS) {
            double progress = (cycleTick - PULSE_HOLD_TICKS) / (double) (PULSE_FADE_OUT_TICKS - 1);
            alpha = MAX_ALPHA - (int) Math.round(progress * ALPHA_RANGE);
        } else {
            double progress = (cycleTick - PULSE_HOLD_TICKS - PULSE_FADE_OUT_TICKS) / (double) (PULSE_FADE_IN_TICKS - 1);
            alpha = MIN_ALPHA + (int) Math.round(progress * ALPHA_RANGE);
        }
        return (alpha << 24) | COUNT_RGB;
    }
}
