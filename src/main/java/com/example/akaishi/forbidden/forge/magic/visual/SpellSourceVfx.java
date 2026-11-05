package com.example.akaishi.forbidden.forge.magic.visual;

import com.example.akaishi.forbidden.magic.particle.AkaishiMagicParticles;
import com.example.akaishi.forbidden.magic.table.MagicSource;
import com.example.akaishi.forbidden.magic.visual.SpellVisualSpecs;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;

import java.util.Objects;

/** 客户端源纹饰；无额外实体、粒子或跨世界状态，所有纹饰收在给定半径内。 */
public final class SpellSourceVfx {
    private SpellSourceVfx() { }

    public static void paint(PoseStack pose, MultiBufferSource buffers, MagicSource source,
            int sourceRgb, int inkRgb, float time, float radius, float alpha, float detail) {
        if (source == null || !Float.isFinite(time) || !Float.isFinite(radius)
                || !Float.isFinite(alpha) || !Float.isFinite(detail)
                || radius <= 0f || alpha <= 0.01f || detail <= 0.5f) return;
        alpha = Mth.clamp(alpha, 0f, 1f);
        time = time % 3600f * SpellVisualSpecs.source(source.id()).pulseMul();
        VertexConsumer consumer = buffers.getBuffer(Objects.requireNonNull(SpellGeometry.energyType()));
        float r = red(sourceRgb), g = green(sourceRgb), b = blue(sourceRgb);
        int photon = AkaishiMagicParticles.photonRgb(inkRgb);
        int segments = detail > 0.72f ? 24 : 16;
        pose.pushPose();
        switch (source) {
            case SAN -> {
                pose.mulPose(Objects.requireNonNull(Axis.ZP.rotationDegrees(time * 21f)));
                for (int arc = 0; arc < 3; arc++) {
                    SpellGeometry.ringBand(consumer, pose.last().pose(), radius * 0.78f, radius * 0.80f,
                            segments, arc * 120f, 72f, alpha * 0.60f, alpha * 0.10f, r, g, b);
                }
                int count = detail > 0.72f ? 6 : 3;
                for (int i = 0; i < count; i++) {
                    float angle = i * 360f / count - time * 48f;
                    float orbit = radius * (0.58f + 0.10f * (float) Math.sin(time * 2f + i));
                    mote(consumer, pose, angle, orbit, radius * 0.055f,
                            alpha * (0.50f + 0.20f * (float) Math.sin(time * 3f + i)), photon);
                }
            }
            case LIFE -> {
                float beat = 0.90f + 0.10f * (float) Math.sin(time * 5.5f);
                if (detail > 0.72f) {
                    float cycle = time * 0.55f - (float) Math.floor(time * 0.55f);
                    float growth = radius * (0.40f + 0.48f * cycle);
                    for (int i = 0; i < 2; i++) {
                        SpellGeometry.ringBand(consumer, pose.last().pose(), growth * 0.97f, growth,
                                segments, -time * 23f + i * 180f, 105f,
                                alpha * com.example.akaishi.forbidden.magic.visual.SpellCircleAnimation.smooth(cycle / 0.18f)
                                        * (1f - cycle) * (1f - cycle) * 0.40f, 0f, r, g, b);
                    }
                }
                SpellGeometry.spiralArms(consumer, pose.last().pose(), radius * 0.25f, radius * 0.76f,
                        3, 0.25f, detail > 0.72f ? 10 : 6, -time * 23f,
                        alpha * 0.45f, r, g, b);
                for (int i = 0; i < 4; i++) {
                    float angle = time * 32f + i * 90f;
                    mote(consumer, pose, angle, radius * 0.76f * beat, radius * 0.10f,
                            alpha * 0.75f, sourceRgb);
                    if (detail > 0.72f) {
                        mote(consumer, pose, angle - 14f, radius * 0.63f * beat, radius * 0.038f,
                                alpha * 0.65f, photon);
                    }
                }
            }
            case DARK -> {
                int rim = AkaishiMagicParticles.photonRgb(sourceRgb);
                for (int i = 0; i < 3; i++) {
                    float start = -time * 37f + i * 120f;
                    SpellGeometry.ringBand(consumer, pose.last().pose(), radius * 0.72f, radius * 0.80f,
                            segments, start, 82f, alpha * 0.70f, alpha * 0.08f, r, g, b);
                    SpellGeometry.ringBand(consumer, pose.last().pose(), radius * 0.80f, radius * 0.815f,
                            segments, start + 8f, 55f, alpha * 0.38f, alpha * 0.08f,
                            red(rim), green(rim), blue(rim));
                    float cycle = time * 0.7f + i / 3f;
                    cycle -= (float) Math.floor(cycle);
                    mote(consumer, pose, start + cycle * 95f, radius * (0.85f - cycle * 0.62f),
                            radius * 0.065f, alpha * (float) Math.sin(cycle * Math.PI) * 0.65f, inkRgb);
                }
            }
            case WHITE -> {
                pose.mulPose(Objects.requireNonNull(Axis.ZP.rotationDegrees(-time * 17f)));
                for (int i = 0; i < 4; i++) {
                    SpellGeometry.ringBand(consumer, pose.last().pose(), radius * 0.69f, radius * 0.71f,
                            segments, i * 90f + 14f, 48f, alpha * 0.65f, alpha * 0.15f, r, g, b);
                    mote(consumer, pose, i * 90f, radius * 0.79f, radius * 0.12f,
                            alpha * 0.78f, sourceRgb);
                    if (detail > 0.72f) {
                        mote(consumer, pose, i * 90f + 45f + time * 35f, radius * 0.48f,
                                radius * 0.035f, alpha * 0.60f, photon);
                    }
                }
            }
        }
        pose.popPose();
    }

    private static void mote(VertexConsumer consumer, PoseStack pose, float angle, float orbit,
            float size, float alpha, int rgb) {
        pose.pushPose();
        pose.mulPose(Objects.requireNonNull(Axis.ZP.rotationDegrees(angle)));
        pose.translate(0f, orbit, 0.008f);
        var matrix = pose.last().pose();
        float r = red(rgb), g = green(rgb), b = blue(rgb);
        SpellGeometry.vertex(consumer, matrix, 0f, size, 0f, 0.5f, 0f, r, g, b, alpha);
        SpellGeometry.vertex(consumer, matrix, size * 0.38f, 0f, 0f, 1f, 0.5f, r, g, b, alpha * 0.45f);
        SpellGeometry.vertex(consumer, matrix, 0f, -size, 0f, 0.5f, 1f, r, g, b, alpha * 0.15f);
        SpellGeometry.vertex(consumer, matrix, -size * 0.38f, 0f, 0f, 0f, 0.5f, r, g, b, alpha * 0.45f);
        pose.popPose();
    }

    private static float red(int rgb) { return ((rgb >> 16) & 255) / 255f; }
    private static float green(int rgb) { return ((rgb >> 8) & 255) / 255f; }
    private static float blue(int rgb) { return (rgb & 255) / 255f; }
}
