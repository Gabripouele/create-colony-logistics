package com.createcolonylogistics.client;

import com.createcolonylogistics.clipboard.SmartClipboardReport;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public final class SmartClipboardClient {
    private SmartClipboardClient() {
    }

    public static void open(SmartClipboardReport report) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof SmartClipboardScreen screen) {
            screen.updateReport(report);
        } else {
            minecraft.setScreen(new SmartClipboardScreen(report));
        }
    }

    public static void applyCancelResult(SmartClipboardReport report, String requestToken, boolean accepted) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof SmartClipboardScreen screen) {
            screen.applyCancelResult(report, requestToken, accepted);
        } else {
            minecraft.setScreen(new SmartClipboardScreen(report));
        }
    }

    static boolean openMineColoniesClipboard() {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }

        return openMineColoniesClipboard(player.getItemInHand(InteractionHand.MAIN_HAND))
                || openMineColoniesClipboard(player.getItemInHand(InteractionHand.OFF_HAND));
    }

    private static boolean openMineColoniesClipboard(ItemStack stack) {
        try {
            Class<?> colonyIdClass = Class.forName("com.minecolonies.api.items.component.ColonyId");
            Method readColonyView = colonyIdClass.getMethod("readColonyViewFromItemStack", ItemStack.class);
            Object colonyView = readColonyView.invoke(null, stack);
            if (colonyView == null) {
                return false;
            }

            Class<?> colonyViewClass = Class.forName("com.minecolonies.api.colony.IColonyView");
            Class<?> windowClass = Class.forName("com.minecolonies.core.client.gui.WindowClipBoard");
            Constructor<?> constructor = windowClass.getConstructor(colonyViewClass);
            Object window = constructor.newInstance(colonyView);
            windowClass.getMethod("open").invoke(window);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }
}
