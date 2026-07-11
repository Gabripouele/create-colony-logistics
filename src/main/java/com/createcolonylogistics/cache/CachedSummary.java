package com.createcolonylogistics.cache;

import com.simibubi.create.content.logistics.packager.InventorySummary;

public final class CachedSummary {
    private InventorySummary summary;
    private MineColoniesStockSnapshot snapshot;
    private long lastBuildGameTime;
    private int sourceSlotCount;
    private long hitCount;
    private long missCount;
    private long factoryPanelSnapshotHitCount;
    private long sameTickFactoryPanelReadCount;
    private long lastFactoryPanelReadGameTime = Long.MIN_VALUE;

    CachedSummary(InventorySummary summary, long lastBuildGameTime, int sourceSlotCount) {
        this.summary = summary;
        this.snapshot = MineColoniesStockSnapshot.from(summary);
        this.lastBuildGameTime = lastBuildGameTime;
        this.sourceSlotCount = sourceSlotCount;
    }

    boolean isValid(long gameTime, int slotCount, int ttlTicks) {
        return sourceSlotCount == slotCount && gameTime - lastBuildGameTime <= ttlTicks;
    }

    InventorySummary copySummary() {
        return CreateInventorySummaryAdapter.safeCopy(summary);
    }

    int countOf(net.minecraft.world.item.ItemStack filter, long gameTime) {
        recordFactoryPanelSnapshotHit(gameTime);
        return snapshot.getCountOf(filter);
    }

    void replace(InventorySummary newSummary, long gameTime, int slotCount) {
        this.summary = newSummary;
        this.snapshot = MineColoniesStockSnapshot.from(newSummary);
        this.lastBuildGameTime = gameTime;
        this.sourceSlotCount = slotCount;
    }

    void recordHit() {
        hitCount++;
    }

    void recordMiss() {
        missCount++;
    }

    private void recordFactoryPanelSnapshotHit(long gameTime) {
        factoryPanelSnapshotHitCount++;
        if (lastFactoryPanelReadGameTime == gameTime) {
            sameTickFactoryPanelReadCount++;
        }
        lastFactoryPanelReadGameTime = gameTime;
    }

    public long hitCount() {
        return hitCount;
    }

    public long missCount() {
        return missCount;
    }

    public long factoryPanelSnapshotHitCount() {
        return factoryPanelSnapshotHitCount;
    }

    public long sameTickFactoryPanelReadCount() {
        return sameTickFactoryPanelReadCount;
    }
}
