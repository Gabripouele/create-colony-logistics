package com.undeadhorsecompat.horse;

import com.undeadhorsecompat.config.UHCConfig;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.SkeletonHorse;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class UndeadHorseTamingHandler {
    private UndeadHorseTamingHandler() {
    }

    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Entity target = event.getTarget();
        if (!(target instanceof AbstractHorse horse) || horse.isTamed()) {
            return;
        }
        if (!canAutoTame(horse)) {
            return;
        }

        if (!horse.level().isClientSide()) {
            horse.tameWithName(event.getEntity());
        }
        event.setCancellationResult(InteractionResult.sidedSuccess(horse.level().isClientSide()));
        event.setCanceled(true);
    }

    private static boolean canAutoTame(AbstractHorse horse) {
        EntityType<?> type = horse.getType();
        if (type == EntityType.ZOMBIE_HORSE) {
            return UHCConfig.ENABLE_ZOMBIE_HORSE_TAMING.get();
        }
        if (type == EntityType.SKELETON_HORSE) {
            return UHCConfig.ENABLE_SKELETON_HORSE_TAMING.get()
                    && (!(horse instanceof SkeletonHorse skeletonHorse) || !skeletonHorse.isTrap());
        }
        return false;
    }
}
