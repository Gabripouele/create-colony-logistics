package com.undeadhorsecompat.horse;

import com.undeadhorsecompat.config.UHCConfig;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.item.AnimalArmorItem;
import net.minecraft.world.item.ItemStack;

public final class HorseArmorCompat {
    private HorseArmorCompat() {
    }

    public static boolean supportsBodyArmor(AbstractHorse horse) {
        EntityType<?> type = horse.getType();
        if (type == EntityType.ZOMBIE_HORSE) {
            return UHCConfig.ENABLE_ZOMBIE_HORSE_ARMOR.get();
        }
        if (type == EntityType.SKELETON_HORSE) {
            return UHCConfig.ENABLE_SKELETON_HORSE_ARMOR.get();
        }
        if (type == EntityType.DONKEY) {
            return UHCConfig.ENABLE_DONKEY_ARMOR.get();
        }
        if (type == EntityType.MULE) {
            return UHCConfig.ENABLE_MULE_ARMOR.get();
        }
        return false;
    }

    public static boolean isEquestrianArmor(ItemStack stack) {
        return stack.getItem() instanceof AnimalArmorItem armorItem
                && armorItem.getBodyType() == AnimalArmorItem.BodyType.EQUESTRIAN;
    }
}
