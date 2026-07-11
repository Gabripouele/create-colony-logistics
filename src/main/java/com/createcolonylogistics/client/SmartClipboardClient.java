package com.createcolonylogistics.client;

import com.createcolonylogistics.clipboard.SmartClipboardReport;
import com.minecolonies.api.colony.IColonyView;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import com.minecolonies.api.items.component.ColonyId;

import java.util.List;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public final class SmartClipboardClient {
    private static SmartClipboardScreen pendingColonyMapReturnScreen;
    private static boolean colonyMapWindowSeen;

    private SmartClipboardClient() {
    }

    public static void openLoading() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof SmartClipboardScreen screen) {
            screen.setLoading();
        } else {
            minecraft.setScreen(new SmartClipboardScreen(loadingReport(), true));
        }
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

    public static void openColonyMap(ItemStack colonyMap) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof SmartClipboardScreen screen) {
            screen.preserveStateForReturn();
            pendingColonyMapReturnScreen = screen;
            colonyMapWindowSeen = false;
        }
        if (!openMineColoniesColonyMap(colonyMap)) {
            pendingColonyMapReturnScreen = null;
            colonyMapWindowSeen = false;
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.translatable("com.minecolonies.core.item.colonymap.needcolony"), true);
            }
        }
    }

    public static void onClientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (pendingColonyMapReturnScreen == null) {
            return;
        }
        if (minecraft.screen != null && minecraft.screen != pendingColonyMapReturnScreen) {
            colonyMapWindowSeen = true;
        }
        if (colonyMapWindowSeen && minecraft.screen == null) {
            minecraft.setScreen(pendingColonyMapReturnScreen);
            pendingColonyMapReturnScreen = null;
            colonyMapWindowSeen = false;
        }
    }

    private static SmartClipboardReport loadingReport() {
        return new SmartClipboardReport("", 0, 0, 0, false, false, List.of(), ItemStack.EMPTY, List.of());
    }

    private static boolean openMineColoniesColonyMap(ItemStack colonyMap) {
        if (colonyMap == null || colonyMap.isEmpty()) {
            return false;
        }
        try {
            IColonyView colonyView = ColonyId.readColonyViewFromItemStack(colonyMap);
            if (colonyView == null || colonyView.getTownHall() == null) {
                return false;
            }
            Class<?> townHallViewClass = Class.forName("com.minecolonies.api.colony.buildings.workerbuildings.ITownHallView");
            Class<?> windowClass = Class.forName("com.minecolonies.core.client.gui.map.WindowColonyMap");
            Constructor<?> constructor = windowClass.getConstructor(boolean.class, townHallViewClass);
            Object window = constructor.newInstance(false, colonyView.getTownHall());
            windowClass.getMethod("open").invoke(window);
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
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
