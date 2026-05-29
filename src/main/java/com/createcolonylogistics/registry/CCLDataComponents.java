package com.createcolonylogistics.registry;

import com.createcolonylogistics.CreateColonyLogistics;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CCLDataComponents {
    private static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(CreateColonyLogistics.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> IMPORTANT_ONLY =
            DATA_COMPONENTS.registerComponentType("important_only",
                    builder -> builder.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    private CCLDataComponents() {
    }

    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }
}
