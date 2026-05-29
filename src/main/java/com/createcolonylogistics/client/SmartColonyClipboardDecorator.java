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
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.IItemDecorator;

import java.util.HashSet;
import java.util.Set;

public class SmartColonyClipboardDecorator implements IItemDecorator {
    private static final int HIGHLIGHT_RGB = 0xD85A48;
    private static final int MIN_ALPHA = 0x59;
    private static final int ALPHA_RANGE = 0x73;
    private static final int PULSE_PERIOD_TICKS = 40;

    @Override
    public boolean render(GuiGraphics graphics, Font font, ItemStack stack, int x, int y) {
        if (pendingRequestCount(stack) <= 0) {
            return false;
        }

        drawInnerPulse(graphics, x, y);
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

    private static void drawInnerPulse(GuiGraphics graphics, int x, int y) {
        int color = (pulseAlpha() << 24) | HIGHLIGHT_RGB;
        int left = x + 1;
        int top = y + 1;
        int right = x + 15;
        int bottom = y + 15;

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 500.0F);
        graphics.fill(left, top, right + 1, top + 1, color);
        graphics.fill(left, bottom, right + 1, bottom + 1, color);
        graphics.fill(left, top, left + 1, bottom + 1, color);
        graphics.fill(right, top, right + 1, bottom + 1, color);
        graphics.pose().popPose();
    }

    private static int pulseAlpha() {
        Minecraft minecraft = Minecraft.getInstance();
        long tick = minecraft.level == null ? System.currentTimeMillis() / 50L : minecraft.level.getGameTime();
        double phase = (tick % PULSE_PERIOD_TICKS) / (double) PULSE_PERIOD_TICKS;
        double wave = (Math.sin(phase * Math.PI * 2.0D) + 1.0D) * 0.5D;
        return MIN_ALPHA + (int) Math.round(wave * ALPHA_RANGE);
    }
}
