package com.createcolonylogistics.mixin;

import com.createcolonylogistics.diagnostics.MalformedExtractionDiagnostics;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour;
import com.simibubi.create.foundation.item.ItemHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Predicate;

@Mixin(InvManipulationBehaviour.class)
public abstract class InvManipulationBehaviourMixin {
    @Redirect(
            method = "extract(Lcom/simibubi/create/foundation/item/ItemHelper$ExtractionCountMode;ILjava/util/function/Predicate;)Lnet/minecraft/world/item/ItemStack;",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/foundation/item/ItemHelper;extract(Lnet/neoforged/neoforge/items/IItemHandler;Ljava/util/function/Predicate;Lcom/simibubi/create/foundation/item/ItemHelper$ExtractionCountMode;IZ)Lnet/minecraft/world/item/ItemStack;"
            ),
            remap = false
    )
    private ItemStack create_colony_logistics$guardMalformedExtraction(
            IItemHandler inventory,
            Predicate<ItemStack> filter,
            ItemHelper.ExtractionCountMode mode,
            int amount,
            boolean simulate) {
        try {
            InvManipulationBehaviour self = (InvManipulationBehaviour) (Object) this;
            return ItemHelper.extract(inventory, MalformedExtractionDiagnostics.itemStackOnlyFilter(self, inventory, filter),
                    mode, amount, simulate);
        } catch (ClassCastException exception) {
            if (!MalformedExtractionDiagnostics.isComponentMapCast(exception)) {
                throw exception;
            }

            MalformedExtractionDiagnostics.logMalformedExtraction(
                    (InvManipulationBehaviour) (Object) this, inventory, null, exception);
            return ItemStack.EMPTY;
        }
    }
}
