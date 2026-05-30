package com.createcolonylogistics.mixin;

import com.createcolonylogistics.cache.ColonyStockCache;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(PackagerBlockEntity.class)
public abstract class PackagerBlockEntityMixin {
    @Shadow(remap = false)
    public InvManipulationBehaviour targetInventory;

    @Unique
    private boolean create_colony_logistics$returnedCachedSummary;

    /**
     * Create uses this method for stock monitoring summaries. MineColonies CombinedItemHandler can be
     * expensive to scan repeatedly, so this short-circuits only read-only summary calls and leaves all
     * actual insertion/extraction paths in Create and MineColonies untouched.
     */
    @Inject(method = "getAvailableItems", at = @At("HEAD"), cancellable = true, remap = false)
    private void create_colony_logistics$useCachedMineColoniesSummary(CallbackInfoReturnable<InventorySummary> cir) {
        ServerLevel level = create_colony_logistics$serverLevel();
        IItemHandler handler = create_colony_logistics$targetHandler();
        if (level == null || handler == null) {
            create_colony_logistics$returnedCachedSummary = false;
            return;
        }

        Optional<InventorySummary> cached = ColonyStockCache.INSTANCE.getIfFresh(handler, level);
        create_colony_logistics$returnedCachedSummary = cached.isPresent();
        cached.ifPresent(cir::setReturnValue);
    }

    @Inject(method = "getAvailableItems", at = @At("RETURN"), remap = false)
    private void create_colony_logistics$storeMineColoniesSummary(CallbackInfoReturnable<InventorySummary> cir) {
        if (create_colony_logistics$returnedCachedSummary) {
            create_colony_logistics$returnedCachedSummary = false;
            return;
        }

        ServerLevel level = create_colony_logistics$serverLevel();
        IItemHandler handler = create_colony_logistics$targetHandler();
        if (level != null && handler != null) {
            ColonyStockCache.INSTANCE.store(handler, level, cir.getReturnValue());
        }
    }

    @Unique
    private ServerLevel create_colony_logistics$serverLevel() {
        BlockEntity blockEntity = (BlockEntity) (Object) this;
        return blockEntity.getLevel() instanceof ServerLevel serverLevel ? serverLevel : null;
    }

    @Unique
    private IItemHandler create_colony_logistics$targetHandler() {
        Object inventory = targetInventory == null ? null : targetInventory.getInventory();
        return inventory instanceof IItemHandler handler ? handler : null;
    }
}
