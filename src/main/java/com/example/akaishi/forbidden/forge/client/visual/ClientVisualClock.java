package com.example.akaishi.forbidden.forge.client.visual;

import net.minecraft.client.Minecraft;

/** Animation clocks do not advance gameplay state when read. */
public final class ClientVisualClock {
    private static final long START = System.nanoTime();

    private ClientVisualClock() { }

    public static float realSeconds() {
        return (float) ((System.nanoTime() - START) / 1.0E9d % 300d);
    }

    public static float worldSeconds() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return realSeconds();
        }
        return (minecraft.level.getGameTime() % 72000L + minecraft.getFrameTime()) / 20f;
    }
}
