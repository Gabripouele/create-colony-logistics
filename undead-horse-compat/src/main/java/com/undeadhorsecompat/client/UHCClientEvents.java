package com.undeadhorsecompat.client;

import com.undeadhorsecompat.UndeadHorseCompat;
import net.minecraft.client.renderer.entity.ChestedHorseRenderer;
import net.minecraft.client.renderer.entity.UndeadHorseRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.AbstractChestedHorse;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = UndeadHorseCompat.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class UHCClientEvents {
    private UHCClientEvents() {
    }

    @SubscribeEvent
    public static void addEntityRenderLayers(EntityRenderersEvent.AddLayers event) {
        addUndeadHorseArmorLayer(event, EntityType.ZOMBIE_HORSE);
        addUndeadHorseArmorLayer(event, EntityType.SKELETON_HORSE);
        addChestedHorseArmorLayer(event, EntityType.DONKEY);
        addChestedHorseArmorLayer(event, EntityType.MULE);
    }

    private static void addUndeadHorseArmorLayer(EntityRenderersEvent.AddLayers event, EntityType<? extends AbstractHorse> type) {
        UndeadHorseRenderer renderer = event.getRenderer(type);
        if (renderer != null) {
            renderer.addLayer(new CompatibleHorseArmorLayer<>(renderer, event.getEntityModels()));
        }
    }

    private static <T extends AbstractChestedHorse> void addChestedHorseArmorLayer(
            EntityRenderersEvent.AddLayers event,
            EntityType<T> type
    ) {
        ChestedHorseRenderer<T> renderer = event.getRenderer(type);
        if (renderer != null) {
            renderer.addLayer(new CompatibleHorseArmorLayer<>(renderer, event.getEntityModels()));
        }
    }
}
