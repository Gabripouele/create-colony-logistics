package com.createcolonylogistics.mixin;

import com.createcolonylogistics.cache.WarehouseStockpileSwitchCache;
import com.createcolonylogistics.cache.WarehouseThresholdSnapshot;
import com.createcolonylogistics.config.ColonyLogisticsConfig;
import com.minecolonies.api.tileentities.AbstractTileEntityWareHouse;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlock;
import com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlock;
import com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlockEntity;
import com.simibubi.create.foundation.blockEntity.SyncedBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(ThresholdSwitchBlockEntity.class)
public abstract class ThresholdSwitchBlockEntityMixin {
    @Shadow(remap = false)
    public int onWhenAbove;

    @Shadow(remap = false)
    public int offWhenBelow;

    @Shadow(remap = false)
    public int currentMinLevel;

    @Shadow(remap = false)
    public int currentLevel;

    @Shadow(remap = false)
    public int currentMaxLevel;

    @Shadow(remap = false)
    private boolean redstoneState;

    @Shadow(remap = false)
    private FilteringBehaviour filtering;

    @Shadow(remap = false)
    protected abstract void scheduleBlockTick();

    @Inject(
            method = "updateCurrentLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/foundation/blockEntity/behaviour/inventory/TankManipulationBehaviour;findNewCapability()V",
                    shift = At.Shift.AFTER
            ),
            cancellable = true,
            remap = false
    )
    private void create_colony_logistics$useWarehouseStockpileAdapter(CallbackInfo ci) {
        if (!ColonyLogisticsConfig.ENABLE_WAREHOUSE_STOCKPILE_SWITCH_ADAPTER.get()) {
            return;
        }

        BlockEntity self = (BlockEntity) (Object) this;
        Level level = self.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        BlockPos targetPos = self.getBlockPos().relative(ThresholdSwitchBlock.getTargetDirection(self.getBlockState()));
        BlockEntity target = serverLevel.getBlockEntity(targetPos);
        if (!(target instanceof AbstractTileEntityWareHouse warehouse)) {
            return;
        }

        WarehouseStockpileSwitchCache.INSTANCE.recordTargetHandled();
        try {
            Optional<WarehouseThresholdSnapshot> snapshot = WarehouseStockpileSwitchCache.INSTANCE.getSnapshot(serverLevel, warehouse);
            if (snapshot.isPresent()) {
                create_colony_logistics$applyWarehouseSnapshot(serverLevel, self, snapshot.get());
            } else {
                create_colony_logistics$applySafeOffState(serverLevel, self);
            }
        } catch (RuntimeException exception) {
            WarehouseStockpileSwitchCache.INSTANCE.recordFallbackOff(exception);
            create_colony_logistics$applySafeOffState(serverLevel, self);
        }
        ci.cancel();
    }

    private void create_colony_logistics$applyWarehouseSnapshot(ServerLevel level, BlockEntity self,
                                                               WarehouseThresholdSnapshot snapshot) {
        int previousLevel = currentLevel;
        currentMinLevel = 0;
        currentMaxLevel = snapshot.totalCapacity();
        currentLevel = Mth.clamp(snapshot.countMatching(this::create_colony_logistics$passesFilter),
                currentMinLevel, currentMaxLevel);

        boolean levelChanged = currentLevel != previousLevel;
        boolean previousRedstoneState = redstoneState;
        if (redstoneState && currentLevel <= offWhenBelow) {
            redstoneState = false;
        } else if (!redstoneState && currentLevel >= onWhenAbove) {
            redstoneState = true;
        }

        boolean redstoneChanged = previousRedstoneState != redstoneState;
        int visualLevel = create_colony_logistics$visualLevel();
        BlockState newState = self.getBlockState().setValue(ThresholdSwitchBlock.LEVEL, visualLevel);
        level.setBlock(self.getBlockPos(), newState, redstoneChanged ? 3 : 2);

        if (redstoneChanged) {
            scheduleBlockTick();
        }

        if (levelChanged || redstoneChanged) {
            DisplayLinkBlock.notifyGatherers(level, self.getBlockPos());
            ((SyncedBlockEntity) (Object) this).notifyUpdate();
        }
    }

    private boolean create_colony_logistics$passesFilter(ItemStack stack) {
        return filtering == null || filtering.test(stack);
    }

    private int create_colony_logistics$visualLevel() {
        if (currentLevel <= 0 || currentMaxLevel <= currentMinLevel) {
            return 0;
        }

        float fill = (currentLevel - currentMinLevel) / (float) (currentMaxLevel - currentMinLevel);
        return Mth.clamp(1 + (int) (fill * 4.0f), 0, 5);
    }

    private void create_colony_logistics$applySafeOffState(ServerLevel level, BlockEntity self) {
        currentMinLevel = -1;
        currentMaxLevel = -1;
        if (currentLevel == -1 && !redstoneState) {
            return;
        }

        level.setBlock(self.getBlockPos(), self.getBlockState().setValue(ThresholdSwitchBlock.LEVEL, 0), 3);
        currentLevel = -1;
        redstoneState = false;
        ((SyncedBlockEntity) (Object) this).sendData();
        scheduleBlockTick();
    }
}
