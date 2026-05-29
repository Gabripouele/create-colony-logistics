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
    private static final int ALPHA_RANGE = 0x73;
    private static final int PULSE_PERIOD_TICKS = 40;
    private static final int HALF_PULSE_TICKS = PULSE_PERIOD_TICKS / 2;

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
        long cycleTick = tick % PULSE_PERIOD_TICKS;
        double progress = (cycleTick % HALF_PULSE_TICKS) / (double) (HALF_PULSE_TICKS - 1);
        double intensity = cycleTick < HALF_PULSE_TICKS ? 1.0D - progress : progress;
        int alpha = MIN_ALPHA + (int) Math.round(intensity * ALPHA_RANGE);
        return (alpha << 24) | COUNT_RGB;
    }
}
