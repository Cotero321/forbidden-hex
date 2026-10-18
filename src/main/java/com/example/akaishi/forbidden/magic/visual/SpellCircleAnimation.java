package com.example.akaishi.forbidden.magic.visual;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Shared geometry for layer rendering, connector anchors and ground-clearance checks. */
public final class SpellCircleAnimation {
    private static final float[] RADII = {1.0f, 0.78f, 0.56f, 0.34f};
    private static final float[] DEPTHS = {-0.24f, -0.08f, 0.08f, 0.24f};

    private SpellCircleAnimation() {
    }

    public static float layerProgress(int index, float progress) {
        return smooth((progress - (3 - index) * 0.18f) / 0.46f);
    }

    public static float radius(int index, float baseRadius, float progress) {
        return Math.max(0.1f, baseRadius) / 1.08f * RADII[index]
                * (0.45f + 0.55f * layerProgress(index, progress));
    }

    public static float depth(int index, float time, boolean ground) {
        return (ground ? 0.18f + index * 0.12f : DEPTHS[index])
                + (ground ? 0.02f : 0.035f) * (float) Math.sin(time * 1.7f + index * 1.37f);
    }

    public static float spin(int index, float time) {
        return direction(index) * (time * (22f + index * 5f) + index * 1.37f * 18f);
    }

    public static float tiltX(int index, float time, boolean ground, float radius) {
        float amplitude = ground ? groundTilt(radius) : 7f + index * 2f;
        return direction(index) * amplitude * (0.8f + 0.2f * (float) Math.sin(time * 0.65f + index));
    }

    public static float tiltY(int index, float time, boolean ground, float radius) {
        float amplitude = ground ? groundTilt(radius) : 10f + index * 1.5f;
        return -direction(index) * amplitude * (0.8f + 0.2f * (float) Math.cos(time * 0.57f + index));
    }

    public static Vec3 point(int index, float time, boolean ground, float baseRadius,
            float progress, float angleDegrees, float radiusFraction) {
        float radius = radius(index, baseRadius, progress);
        double angle = Math.toRadians(angleDegrees + spin(index, time));
        double x = Math.cos(angle) * radius * radiusFraction;
        double y = Math.sin(angle) * radius * radiusFraction;
        double tiltY = Math.toRadians(tiltY(index, time, ground, radius));
        double tiltedX = x * Math.cos(tiltY);
        double z = -x * Math.sin(tiltY);
        double tiltX = Math.toRadians(tiltX(index, time, ground, radius));
        return new Vec3(tiltedX, y * Math.cos(tiltX) - z * Math.sin(tiltX),
                y * Math.sin(tiltX) + z * Math.cos(tiltX) + depth(index, time, ground));
    }

    public static float smooth(float value) {
        float clamped = Mth.clamp(value, 0f, 1f);
        return clamped * clamped * (3f - 2f * clamped);
    }

    private static float groundTilt(float radius) {
        return (float) Math.toDegrees(Math.atan2(0.04f, Math.max(0.1f, radius * 1.08f)));
    }

    private static float direction(int index) {
        return index % 2 == 0 ? 1f : -1f;
    }
}
