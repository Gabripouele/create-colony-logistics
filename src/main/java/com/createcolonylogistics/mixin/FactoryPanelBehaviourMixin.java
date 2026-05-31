package com.createcolonylogistics.mixin;

import com.createcolonylogistics.cache.ColonyStockCache;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.OptionalInt;

@Mixin(FactoryPanelBehaviour.class)
public abstract class FactoryPanelBehaviourMixin {
    private static Field create_colony_logistics$restockerField;
    private static Method create_colony_logistics$restockedPackagerMethod;

    @Shadow(remap = false)
    public abstract FactoryPanelBlockEntity panelBE();

    @Inject(method = "getLevelInStorage", at = @At("HEAD"), cancellable = true, remap = false)
    private void create_colony_logistics$getMineColoniesSnapshotCount(CallbackInfoReturnable<Integer> cir) {
        ItemStack filter = ((FilteringBehaviour) (Object) this).getFilter();
        if (filter.isEmpty()) {
            return;
        }

        Object panel = panelBE();
        if (panel == null || !isRestocker(panel)) {
            return;
        }

        PackagerBlockEntity packager = restockedPackager(panel);
        if (packager == null || packager.targetInventory == null) {
            return;
        }

        Level level = ((BlockEntity) packager).getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        Object inventory = packager.targetInventory.getInventory();
        if (!(inventory instanceof IItemHandler handler)) {
            return;
        }

        OptionalInt count = ColonyStockCache.INSTANCE.getFactoryPanelCountIfFresh(handler, serverLevel, filter);
        if (count.isPresent()) {
            cir.setReturnValue(count.getAsInt());
        }
    }

    private static boolean isRestocker(Object panel) {
        try {
            Field restocker = create_colony_logistics$restockerField;
            if (restocker == null) {
                restocker = panel.getClass().getField("restocker");
                create_colony_logistics$restockerField = restocker;
            }
            return restocker.getBoolean(panel);
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    private static PackagerBlockEntity restockedPackager(Object panel) {
        try {
            Method method = create_colony_logistics$restockedPackagerMethod;
            if (method == null) {
                method = panel.getClass().getMethod("getRestockedPackager");
                create_colony_logistics$restockedPackagerMethod = method;
            }
            Object packager = method.invoke(panel);
            return packager instanceof PackagerBlockEntity blockEntity ? blockEntity : null;
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }
}
