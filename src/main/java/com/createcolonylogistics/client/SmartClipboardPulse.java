package com.createcolonylogistics.client;

import net.minecraft.client.Minecraft;

public final class SmartClipboardPulse {
    private static final int COUNT_RGB = 0xFF4500;
    private static final int MIN_ALPHA = 0x59;
    private static final int MAX_ALPHA = 0xFF;
    private static final int ALPHA_RANGE = MAX_ALPHA - MIN_ALPHA;
    private static final int PULSE_CYCLE_TICKS = 60;
    private static final int PULSE_HOLD_TICKS = 20;
    private static final int PULSE_FADE_OUT_TICKS = 20;
    private static final int PULSE_FADE_IN_TICKS = 20;

    private SmartClipboardPulse() {
    }

    public static int pulsingCountColor() {
        Minecraft minecraft = Minecraft.getInstance();
        long tick = minecraft.level == null ? System.currentTimeMillis() / 50L : minecraft.level.getGameTime();
        long cycleTick = tick % PULSE_CYCLE_TICKS;
        int alpha;
        if (cycleTick < PULSE_HOLD_TICKS) {
            alpha = MAX_ALPHA;
        } else if (cycleTick < PULSE_HOLD_TICKS + PULSE_FADE_OUT_TICKS) {
            double progress = (cycleTick - PULSE_HOLD_TICKS) / (double) (PULSE_FADE_OUT_TICKS - 1);
            alpha = MAX_ALPHA - (int) Math.round(progress * ALPHA_RANGE);
        } else {
            double progress = (cycleTick - PULSE_HOLD_TICKS - PULSE_FADE_OUT_TICKS) / (double) (PULSE_FADE_IN_TICKS - 1);
            alpha = MIN_ALPHA + (int) Math.round(progress * ALPHA_RANGE);
        }
        return (alpha << 24) | COUNT_RGB;
    }
}
