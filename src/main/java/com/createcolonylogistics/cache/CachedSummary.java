package com.createcolonylogistics.cache;

import com.simibubi.create.content.logistics.packager.InventorySummary;

public final class CachedSummary {
    private InventorySummary summary;
    private long lastBuildGameTime;
    private int sourceSlotCount;
    private long hitCount;
    private long missCount;

    CachedSummary(InventorySummary summary, long lastBuildGameTime, int sourceSlotCount) {
        this.summary = summary;
        this.lastBuildGameTime = lastBuildGameTime;
        this.sourceSlotCount = sourceSlotCount;
    }

    boolean isValid(long gameTime, int slotCount, int ttlTicks) {
        return sourceSlotCount == slotCount && gameTime - lastBuildGameTime <= ttlTicks;
    }

    InventorySummary summary() {
        return summary;
    }

    void replace(InventorySummary newSummary, long gameTime, int slotCount) {
        this.summary = newSummary;
        this.lastBuildGameTime = gameTime;
        this.sourceSlotCount = slotCount;
    }

    void recordHit() {
        hitCount++;
    }

    void recordMiss() {
        missCount++;
    }

    public long hitCount() {
        return hitCount;
    }

    public long missCount() {
        return missCount;
    }
}
