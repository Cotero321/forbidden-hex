package com.example.akaishi.forbidden.magic.visual;

/** 有限幅度、短寿命且带冷却的纯视觉冲量，不依赖客户端或玩家状态。 */
public final class SpellFeedback {
    public static final int DURATION = 5;
    public static final int COOLDOWN = 10;
    public static final float MAX_ANGLE = 0.30f;

    private SpellFeedback() { }

    public static float strength(float power, double distanceSquared, boolean nearOrigin) {
        if (!Float.isFinite(power) || power < 8f || !Double.isFinite(distanceSquared)
                || distanceSquared < 0d || distanceSquared >= 144d) return 0f;
        float force = Math.min(1f, 0.2f + (power - 8f) / 24f);
        float proximity = 1f - (float) (Math.sqrt(distanceSquared) / 12d);
        return MAX_ANGLE * force * proximity * (nearOrigin ? 1f : 0.65f);
    }

    public static final class Pulse {
        private long started = Long.MIN_VALUE;
        private float strength;

        public boolean trigger(long tick, float next) {
            if (!Float.isFinite(next) || next <= 0f) return false;
            if (started != Long.MIN_VALUE && tick >= started && tick - started < COOLDOWN) return false;
            started = tick;
            strength = Math.min(next, MAX_ANGLE);
            return true;
        }

        public float sample(double tick) {
            if (started == Long.MIN_VALUE || !Double.isFinite(tick)) return 0f;
            double age = tick - started;
            if (age < 0d || age >= DURATION) return 0f;
            double fade = 1d - age / DURATION;
            return (float) (Math.sin(age * Math.PI * 0.8d) * fade * fade) * strength;
        }

        public void clear() {
            started = Long.MIN_VALUE;
            strength = 0f;
        }
    }

    public static boolean contractCheck() {
        Pulse pulse = new Pulse();
        if (strength(7f, 0d, true) != 0f || strength(100f, 144d, true) != 0f
                || strength(Float.NaN, 0d, true) != 0f || strength(20f, Double.NaN, true) != 0f
                || !pulse.trigger(100L, 4f) || pulse.trigger(101L, 0.1f)) return false;
        for (double t = 99d; t <= 111d; t += 0.125d) {
            float value = pulse.sample(t);
            if (!Float.isFinite(value) || Math.abs(value) > MAX_ANGLE
                    || (t < 100d || t >= 105d) && value != 0f) return false;
        }
        if (pulse.trigger(109L, 0.1f) || !pulse.trigger(110L, 0.1f)) return false;
        pulse.clear();
        return pulse.sample(110.5d) == 0f && pulse.trigger(110L, 0.1f);
    }
}
