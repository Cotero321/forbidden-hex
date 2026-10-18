package com.example.akaishi.forbidden.visual;

import com.example.akaishi.forbidden.magic.cast.MagicCastState;

/** Tick-driven presentation timing; reading it cannot advance a cast or confirm a hit. */
public final class CastVisualTimeline {
    private MagicCastState state = MagicCastState.IDLE;
    private int age;
    private int transitionAge = 20;

    public void tick(MagicCastState next) {
        if (next != state) {
            state = next;
            age = 0;
            transitionAge = 0;
        } else {
            age = Math.min(72000, age + 1);
            transitionAge = Math.min(20, transitionAge + 1);
        }
    }

    public void tick(MagicCastState next, boolean channel, boolean usingWand) {
        tick(channel && !usingWand ? MagicCastState.IDLE : next);
    }

    public MagicCastState state() { return state; }

    public float age(float partialTick) {
        return age + unit(partialTick);
    }

    public float chargeProgress(float durationTicks, float partialTick) {
        if (state != MagicCastState.CHARGING || !Float.isFinite(durationTicks) || durationTicks <= 0f) return 0f;
        return Math.min(0.95f, age(partialTick) / durationTicks);
    }

    public float transition(float partialTick) {
        return Math.max(0f, 1f - (transitionAge + unit(partialTick)) / 8f);
    }

    public float segmentPhase(float intervalTicks, float partialTick) {
        if (!state.isActive() || !Float.isFinite(intervalTicks) || intervalTicks <= 0f) return 0f;
        float cycles = age(partialTick) / intervalTicks;
        return cycles - (float) Math.floor(cycles);
    }

    public void reset() {
        state = MagicCastState.IDLE;
        age = 0;
        transitionAge = 20;
    }

    private static float unit(float value) {
        return Float.isFinite(value) ? Math.max(0f, Math.min(1f, value)) : 0f;
    }
}
