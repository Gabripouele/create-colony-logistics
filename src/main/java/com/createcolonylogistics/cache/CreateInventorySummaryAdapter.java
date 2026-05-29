package com.createcolonylogistics.cache;

import com.createcolonylogistics.CreateColonyLogistics;
import com.createcolonylogistics.config.ColonyLogisticsConfig;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import net.minecraft.world.item.ItemStack;

public final class CreateInventorySummaryAdapter {
    private static final long COPY_FAILURE_LOG_INTERVAL_MILLIS = 60_000L;
    private static long lastCopyFailureLogMillis;

    private CreateInventorySummaryAdapter() {
    }

    public static InventorySummary safeCopy(InventorySummary source) {
        InventorySummary copy = new InventorySummary();
        if (source == null) {
            return copy;
        }

        try {
            copy.contributingLinks = source.contributingLinks;
            for (BigItemStack stack : source.getStacks()) {
                addCopiedStack(copy, stack);
            }
        } catch (RuntimeException exception) {
            debugCopyFailure(exception);
        }
        return copy;
    }

    private static void addCopiedStack(InventorySummary target, BigItemStack stack) {
        if (stack == null || stack.stack == null || stack.stack.isEmpty() || stack.count == 0) {
            return;
        }

        ItemStack ownedStack = stack.stack.copy();
        target.add(ownedStack, stack.count);
    }

    private static void debugCopyFailure(RuntimeException exception) {
        if (!ColonyLogisticsConfig.DEBUG_LOGGING.get()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastCopyFailureLogMillis < COPY_FAILURE_LOG_INTERVAL_MILLIS) {
            return;
        }

        lastCopyFailureLogMillis = now;
        CreateColonyLogistics.LOGGER.debug("Failed to defensively copy Create InventorySummary; using safe partial summary", exception);
    }
}
