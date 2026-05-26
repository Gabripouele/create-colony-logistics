package com.createcolonylogistics.cache;

import com.simibubi.create.content.logistics.packager.InventorySummary;

public final class CreateInventorySummaryAdapter {
    private CreateInventorySummaryAdapter() {
    }

    public static InventorySummary safeCopy(InventorySummary source) {
        if (source == null) {
            return new InventorySummary();
        }

        try {
            return source.copy();
        } catch (RuntimeException ignored) {
            return source;
        }
    }
}
