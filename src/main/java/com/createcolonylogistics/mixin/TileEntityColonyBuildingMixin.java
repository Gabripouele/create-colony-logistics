package com.createcolonylogistics.mixin;

import com.createcolonylogistics.item.SmartClipboardColonyLinkGuard;
import com.createcolonylogistics.registry.CCLItems;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TileEntityColonyBuilding.class)
public abstract class TileEntityColonyBuildingMixin {
    @Inject(method = "writeColonyToItemStack", at = @At("HEAD"), cancellable = true, remap = false)
    private void create_colony_logistics$blockPassiveSmartClipboardBinding(ItemStack stack, CallbackInfo ci) {
        if (stack.is(CCLItems.SMART_COLONY_CLIPBOARD.get()) && !SmartClipboardColonyLinkGuard.explicitLinkAllowed()) {
            ci.cancel();
        }
    }
}
