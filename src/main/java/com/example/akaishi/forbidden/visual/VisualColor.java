package com.example.akaishi.forbidden.visual;

/** RGB colors and absolute ARGB opacity share a finite, clamped conversion path. */
public final class VisualColor {
    private VisualColor() { }

    public static float red(int rgb) {
        return (rgb >>> 16 & 255) / 255f;
    }

    public static float green(int rgb) {
        return (rgb >>> 8 & 255) / 255f;
    }

    public static float blue(int rgb) {
        return (rgb & 255) / 255f;
    }

    public static int withAlpha(int rgb, float alpha) {
        return Math.round(unit(alpha) * 255f) << 24 | rgb & 0xFFFFFF;
    }

    public static int mix(int fromRgb, int toRgb, float amount) {
        float t = unit(amount);
        int r = channel(fromRgb >>> 16 & 255, toRgb >>> 16 & 255, t);
        int g = channel(fromRgb >>> 8 & 255, toRgb >>> 8 & 255, t);
        int b = channel(fromRgb & 255, toRgb & 255, t);
        return r << 16 | g << 8 | b;
    }

    public static int brighten(int rgb, float amount) {
        return mix(rgb, 0xFFFFFF, amount);
    }

    private static int channel(int from, int to, float amount) {
        return Math.round(from + (to - from) * amount);
    }

    private static float unit(float value) {
        return Float.isNaN(value) ? 0f : Math.max(0f, Math.min(1f, value));
    }
}
