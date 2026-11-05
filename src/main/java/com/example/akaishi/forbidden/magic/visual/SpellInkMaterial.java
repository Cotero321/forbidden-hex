package com.example.akaishi.forbidden.magic.visual;

import java.util.Map;

/** 法术专用墨材质，不改法阵、酿造或既有粒子变体。 */
public record SpellInkMaterial(float flowSpeed, float turbulence, float elongation,
        float trailWidth, float rise, float fadePower, int edgeRgb) {
    public static final Map<String, SpellInkMaterial> ALL = Map.ofEntries(
            Map.entry("chi", new SpellInkMaterial(1.70f, 0.16f, 1.35f, 0.85f, 0.65f, 2.7f, 0xFFC16A)),
            Map.entry("rang", new SpellInkMaterial(0.45f, 0.025f, 0.85f, 1.25f, -0.55f, 3.0f, 0xC49D70)),
            Map.entry("ju", new SpellInkMaterial(2.10f, 0.10f, 1.65f, 0.55f, 0.15f, 2.4f, 0xA4EAF2)),
            Map.entry("man", new SpellInkMaterial(0.75f, 0.08f, 1.10f, 1.05f, 0.30f, 1.6f, 0x95D976)),
            Map.entry("shi", new SpellInkMaterial(0.65f, 0.13f, 0.90f, 1.20f, -0.20f, 1.5f, 0xBBCB65)),
            Map.entry("xu", new SpellInkMaterial(0.80f, 0.015f, 1.20f, 0.65f, 0f, 2.2f, 0xD3DEEF)),
            Map.entry("hun", new SpellInkMaterial(1.40f, 0.22f, 1.05f, 1.10f, 0.12f, 1.7f, 0xEF95DF)),
            Map.entry("yuan", new SpellInkMaterial(1.10f, 0.10f, 1.20f, 1.20f, -0.40f, 1.8f, 0x548BBE)),
            Map.entry("yao", new SpellInkMaterial(1.25f, 0.025f, 1.45f, 0.60f, 0.15f, 2.8f, 0xFFE9A2)),
            Map.entry("an", new SpellInkMaterial(0.60f, 0.15f, 1.10f, 1.15f, 0.35f, 1.4f, 0xAD77C8)),
            Map.entry("xing", new SpellInkMaterial(0.85f, 0.18f, 1.25f, 0.75f, 0.05f, 1.9f, 0xB2A7FF)));
    private static final SpellInkMaterial DEFAULT = new SpellInkMaterial(1f, 0.06f, 1f, 1f, 0f, 2f, 0xA7B7CC);

    public static SpellInkMaterial get(String inkId) {
        return inkId == null ? DEFAULT : ALL.getOrDefault(inkId, DEFAULT);
    }

    public int rimRgb(int inkRgb) {
        return mix(inkRgb, edgeRgb, 0.62f);
    }

    public static int mix(int a, int b, float factor) {
        float t = Float.isFinite(factor) ? Math.max(0f, Math.min(1f, factor)) : 0f;
        int r = Math.round(((a >> 16) & 255) * (1f - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1f - t) + ((b >> 8) & 255) * t);
        int blue = Math.round((a & 255) * (1f - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | blue;
    }
}
