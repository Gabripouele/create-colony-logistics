package com.createcolonylogistics.client;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.jobs.IJobView;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.resolver.player.IPlayerRequestResolver;
import com.minecolonies.api.colony.requestsystem.resolver.retrying.IRetryingRequestResolver;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.items.component.ColonyId;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.IItemDecorator;

import java.util.HashSet;
import java.util.Set;

public class SmartColonyClipboardDecorator implements IItemDecorator {
    static final int COUNT_X_OFFSET = 15;
    static final int COUNT_Y_OFFSET = -2;

    @Override
    public boolean render(GuiGraphics graphics, Font font, ItemStack stack, int x, int y) {
        int count = pendingRequestCount(stack);
        if (count <= 0) {
            return false;
        }

        drawCenteredCount(graphics, font, count, x, y);
        return true;
    }

    static int pendingRequestCount(ItemStack stack) {
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
        int centerX = x + COUNT_X_OFFSET;
        int drawY = y + COUNT_Y_OFFSET;

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 500.0F);
        graphics.drawCenteredString(font, Component.literal(text), centerX, drawY, SmartClipboardPulse.pulsingCountColor());
        graphics.pose().popPose();
    }
}
