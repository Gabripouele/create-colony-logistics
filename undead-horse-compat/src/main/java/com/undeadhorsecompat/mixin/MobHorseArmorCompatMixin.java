package com.undeadhorsecompat.mixin;

import com.undeadhorsecompat.horse.HorseArmorCompat;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class MobHorseArmorCompatMixin {
    @Inject(method = "canUseSlot", at = @At("HEAD"), cancellable = true)
    private void undeadHorseCompat$allowConfiguredHorseBodyArmor(EquipmentSlot slot, CallbackInfoReturnable<Boolean> callback) {
        if (slot == EquipmentSlot.BODY && (Object)this instanceof AbstractHorse horse && HorseArmorCompat.supportsBodyArmor(horse)) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "isBodyArmorItem", at = @At("HEAD"), cancellable = true)
    private void undeadHorseCompat$acceptConfiguredHorseArmor(ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        if ((Object)this instanceof AbstractHorse horse
                && HorseArmorCompat.supportsBodyArmor(horse)
                && HorseArmorCompat.isEquestrianArmor(stack)) {
            callback.setReturnValue(true);
        }
    }
}
