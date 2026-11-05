package com.example.akaishi.forbidden.forge.magic.visual;

import com.example.akaishi.forbidden.magic.table.MagicSource;
import com.example.akaishi.forbidden.magic.visual.SpellInkMaterial;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** 法术独占的立体主体：源定骨架，墨定材质与局部流动。局部 +Z 为飞行轴。 */
public final class SpellBodyPainter {
    public enum Stage { CHARGE, FLIGHT, IMPACT, SUSTAIN }

    private SpellBodyPainter() { }

    public static void paint(PoseStack pose, MultiBufferSource buffers, MagicSource source,
            String inkId, int rgb, float time, float radius, float alpha, float detail,
            Stage stage, float progress) {
        if (source == null || stage == null || !Float.isFinite(time) || !Float.isFinite(radius)
                || !Float.isFinite(alpha) || !Float.isFinite(detail) || radius <= 0f || alpha <= 0.01f || detail <= 0f) return;
        radius = Math.min(radius, 4f);
        alpha = Mth.clamp(alpha, 0f, 0.90f);
        progress = Float.isFinite(progress) ? Mth.clamp(progress, 0f, 1f) : 0f;
        SpellInkMaterial material = SpellInkMaterial.get(inkId);
        float clock = time % 3600f;
        float flow = clock * material.flowSpeed();
        int rim = material.rimRgb(rgb);
        pose.pushPose();
        pose.scale(radius, radius, radius);
        if (stage == Stage.CHARGE) {
            float scale = 0.72f + progress * 0.28f;
            pose.scale(scale, scale, 1f);
        } else if (stage == Stage.FLIGHT) {
            pose.scale(0.88f, 0.88f, Math.min(1.45f, material.elongation()));
        }
        float spread = stage == Stage.IMPACT ? progress : 0f;
        for (int pass = 0; pass < 2; pass++) {
            boolean glow = pass == 1;
            Layer layer = new Layer(buffers.getBuffer(SpellRenderTypes.volume(glow)), glow);
            switch (source) {
                case SAN -> fragments(layer, pose, material, rgb, rim, flow, alpha, detail, spread);
                case LIFE -> clusters(layer, pose, material, rgb, rim, flow, alpha, detail, spread);
                case DARK -> coils(layer, pose, material, rgb, rim, flow, alpha, detail, spread);
                case WHITE -> blades(layer, pose, material, rgb, rim, flow, alpha, detail, spread);
            }
            if (detail > 0.5f) matter(layer, pose, inkId, material, rgb, rim, flow, alpha, detail, stage, progress);
        }
        pose.popPose();
    }

    private static void fragments(Layer layer, PoseStack pose, SpellInkMaterial material,
            int rgb, int rim, float time, float alpha, float detail, float spread) {
        int count = detail > 0.72f ? 6 : detail > 0.5f ? 4 : 3;
        float step = (float) Math.floor(time * 5f) * 0.08f;
        for (int i = 0; i < count; i++) {
            double angle = i * 2.399963d + time * 0.36f;
            float offset = 0.28f + 0.12f * (float) Math.sin(i * 2.1f + step) + spread * 0.72f;
            float x = (float) Math.cos(angle) * offset;
            float y = (float) Math.sin(angle) * offset;
            float z = (i - (count - 1) * 0.5f) * 0.20f;
            pose.pushPose();
            pose.translate(x, y, z);
            pose.mulPose(Axis.ZP.rotationDegrees(i * 71f + step * 57f));
            pose.mulPose(Axis.XP.rotationDegrees(38f + i * 31f));
            float flicker = 0.76f + 0.24f * (float) Math.sin(time * 7f + i * 2.3f);
            crystal(layer, pose, 0.145f, 0.34f, alpha * flicker, i % 2 == 0 ? rgb : rim);
            if (layer.glow() && detail > 0.72f && i % 2 == 0) {
                pose.translate(0.10f + material.turbulence(), -0.04f, -0.16f);
                crystal(layer, pose, 0.09f, 0.30f, alpha * 0.18f, rim);
            }
            pose.popPose();
        }
    }

    private static void clusters(Layer layer, PoseStack pose, SpellInkMaterial material,
            int rgb, int rim, float time, float alpha, float detail, float spread) {
        int count = detail > 0.72f ? 4 : detail > 0.5f ? 3 : 2;
        for (int i = 0; i < count; i++) {
            double angle = i * Math.PI * 2d / count + time * 0.55f;
            float breath = 0.94f + 0.08f * (float) Math.sin(time * 3f - i * 0.7f);
            float orbit = 0.23f + spread * 0.65f;
            pose.pushPose();
            pose.translate(Math.cos(angle) * orbit, Math.sin(angle) * orbit,
                    (i - (count - 1) * 0.5f) * 0.23f + Math.sin(time + i) * material.turbulence());
            pose.mulPose(Axis.ZP.rotationDegrees((float) Math.toDegrees(angle)));
            pose.scale(breath * 0.85f, breath, 1.12f);
            blob(layer, pose, 0.40f, 0.50f, alpha * 0.74f, rgb, rim, detail, time + i);
            pose.popPose();
        }
        if (detail > 0.5f) {
            for (int i = 0; i < 2; i++) {
                curve(layer, pose, time + i * 3.141593f, 0.44f + spread * 0.4f,
                        0.025f, 8, alpha * 0.42f, rim, false);
            }
        }
    }

    private static void coils(Layer layer, PoseStack pose, SpellInkMaterial material,
            int rgb, int rim, float time, float alpha, float detail, float spread) {
        int count = detail > 0.72f ? 3 : 2;
        int steps = detail > 0.72f ? 16 : detail > 0.5f ? 12 : 7;
        for (int i = 0; i < count; i++) {
            float phase = -time * 1.9f + i * (float) (Math.PI * 2d / count);
            curve(layer, pose, phase, 0.78f - spread * 0.33f,
                    0.11f + material.turbulence() * 0.22f, steps, alpha * 0.78f, rgb, true);
        }
        blob(layer, pose, 0.24f, 0.43f, alpha * 0.76f, rgb, rim, 0.5f, time);
    }

    private static void blades(Layer layer, PoseStack pose, SpellInkMaterial material,
            int rgb, int rim, float time, float alpha, float detail, float spread) {
        pose.pushPose();
        pose.mulPose(Axis.XP.rotationDegrees(90f));
        crystal(layer, pose, 0.16f, 0.85f, alpha, rgb);
        pose.popPose();
        int count = detail > 0.5f ? 4 : 2;
        for (int i = 0; i < count; i++) {
            float angle = i * 360f / count + time * 12f;
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(angle));
            pose.translate(0f, 0.35f + spread * 0.8f, -0.12f);
            pose.mulPose(Axis.XP.rotationDegrees(35f + spread * 28f));
            float length = 0.46f + 0.04f * (float) Math.sin(time * 3f);
            crystal(layer, pose, 0.12f, length * material.elongation(), alpha * 0.82f, rgb);
            pose.popPose();
        }
    }

    private static void matter(Layer layer, PoseStack pose, String inkId, SpellInkMaterial material,
            int rgb, int rim, float time, float alpha, float detail, Stage stage, float progress) {
        int count = detail > 0.72f ? 3 : 2;
        for (int i = 0; i < count; i++) {
            float phase = time * 0.65f + i * 1.618f;
            float cycle = phase - (float) Math.floor(phase);
            double angle = i * 2.399963d + time * 0.4f;
            float orbit = stage == Stage.IMPACT ? 0.35f + progress * 1.15f : 0.65f;
            pose.pushPose();
            pose.translate(Math.cos(angle) * orbit, Math.sin(angle) * orbit + cycle * material.rise() * 0.32f,
                    0.25f - cycle * 0.85f);
            float envelope = Mth.sin(cycle * Mth.PI);
            float fade = alpha * envelope * envelope * 0.55f;
            switch (inkId == null ? "" : inkId) {
                case "yuan" -> {
                    pose.scale(0.70f, 1.35f, 0.70f);
                    blob(layer, pose, 0.065f, 0.10f, fade, rgb, rim, 0.5f, time + i);
                }
                case "chi", "ju" -> {
                    pose.mulPose(Axis.XP.rotationDegrees(25f));
                    crystal(layer, pose, 0.025f, 0.12f + cycle * 0.10f, fade, rim);
                }
                case "rang", "xu" -> {
                    pose.mulPose(Axis.ZP.rotationDegrees(i * 47f + time * 15f));
                    crystal(layer, pose, 0.055f, 0.065f, fade * 1.2f, rgb);
                }
                case "man", "shi" -> {
                    pose.mulPose(Axis.ZP.rotationDegrees(i * 67f + time * 20f));
                    blob(layer, pose, 0.045f, 0.10f, fade, rgb, rim, 0.5f, time);
                }
                case "an" -> curve(layer, pose, phase, 0.09f,
                        0.025f, 5, fade * 0.7f, rim, true);
                default -> {
                    pose.mulPose(Axis.ZP.rotationDegrees(time * 40f + i * 90f));
                    crystal(layer, pose, 0.025f, 0.09f, fade, rim);
                }
            }
            pose.popPose();
        }
    }

    private record Layer(VertexConsumer consumer, boolean glow) { }

    private static void crystal(Layer layer, PoseStack pose, float radius,
            float height, float alpha, int rgb) {
        SpellVolumeGeometry.crystal(layer.consumer(), pose, radius, height, alpha, rgb, rgb, layer.glow());
    }

    private static void blob(Layer layer, PoseStack pose, float radius, float height,
            float alpha, int rgb, int rim, float detail, float time) {
        SpellVolumeGeometry.ellipsoid(layer.consumer(), pose, radius, height, alpha, rgb, rim,
                detail > 0.72f ? 8 : 6, detail > 0.72f ? 4 : 3, time, layer.glow());
    }

    private static void curve(Layer layer, PoseStack pose, float phase, float radius,
            float width, int steps, float alpha, int rgb, boolean inward) {
        for (int i = 0; i < steps; i++) {
            float t0 = (float) i / steps;
            float t1 = (float) (i + 1) / steps;
            float angle0 = phase + t0 * Mth.PI * (inward ? 3.5f : 2f);
            float angle1 = phase + t1 * Mth.PI * (inward ? 3.5f : 2f);
            float envelope0 = Mth.sin(t0 * Mth.PI);
            float envelope1 = Mth.sin(t1 * Mth.PI);
            var side0 = new Vec3(Mth.cos(angle0), Mth.sin(angle0), 0);
            var side1 = new Vec3(Mth.cos(angle1), Mth.sin(angle1), 0);
            float radial0 = radius * (inward ? 1f - t0 * 0.65f : 0.88f + envelope0 * 0.12f);
            float radial1 = radius * (inward ? 1f - t1 * 0.65f : 0.88f + envelope1 * 0.12f);
            var from = side0.scale(radial0).add(0, 0, (t0 - 0.5f) * 1.35f);
            var to = side1.scale(radial1).add(0, 0, (t1 - 0.5f) * 1.35f);
            float turn = Mth.PI * (inward ? 3.5f : 2f);
            Vec3 up0 = new Vec3(-side0.y * radial0 * turn, side0.x * radial0 * turn, 1.35d).cross(side0).normalize();
            Vec3 up1 = new Vec3(-side1.y * radial1 * turn, side1.x * radial1 * turn, 1.35d).cross(side1).normalize();
            SpellVolumeGeometry.tube(layer.consumer(), pose, from, to, side0, side1, up0, up1,
                    width * (0.25f + 0.75f * envelope0), width * (0.25f + 0.75f * envelope1), 0.32f,
                    alpha * (0.55f + 0.45f * envelope0), alpha * (0.55f + 0.45f * envelope1),
                    rgb, rgb, layer.glow(), i == 0, i == steps - 1);
        }
    }
}
