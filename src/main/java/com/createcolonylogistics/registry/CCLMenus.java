package com.createcolonylogistics.registry;

import com.createcolonylogistics.CreateColonyLogistics;
import com.createcolonylogistics.menu.SmartClipboardMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CCLMenus {
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, CreateColonyLogistics.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<SmartClipboardMenu>> SMART_CLIPBOARD = MENUS.register(
            "smart_clipboard",
            () -> IMenuTypeExtension.create(SmartClipboardMenu::new)
    );

    private CCLMenus() {
    }

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
