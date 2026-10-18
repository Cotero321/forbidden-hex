package com.example.akaishi.forbidden.forge.magic.visual;

import com.example.akaishi.forbidden.magic.visual.SpellCircleAnimation;
import com.example.akaishi.forbidden.magic.visual.SpellCircleLayers;
import com.example.akaishi.forbidden.magic.visual.SpellVisual;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;

/** Draws the four fixed physical-slot layers as an animated 3D spell circle. */
public final class SpellCircleLayerPainter {
    private static final int SEGMENTS = 40;

    private SpellCircleLayerPainter() {
    }

    public static void paint(PoseStack poseStack, MultiBufferSource buffers, SpellVisual visual,
            SpellCircleLayers layers, float time, float alpha, float progress,
            float detail, boolean ground, float baseRadius) {
        if (visual == null || layers == null || alpha <= 0.005f || detail <= 0f) return;
        SpellCircleArt.foundation(poseStack, buffers, visual, time, alpha, progress, detail, ground, baseRadius);
        VertexConsumer consumer = buffers.getBuffer(SpellRenderTypes.glow(SpellGeometry.WHITE));
        float radiusScale = Math.max(0.1f, baseRadius) / 1.08f;
        int segments = Math.max(16, Math.round(SEGMENTS * detail));

        for (int index = 0; index < SpellCircleLayers.LAYER_COUNT; index++) {
            SpellCircleLayers.Layer layer = layers.layer(index);
            float phase = index * 1.37f;
            float pulse = 0.82f + 0.18f * (float) Math.sin(time * 3.2f + phase);
            float spin = SpellCircleAnimation.spin(index, time);
            float occupiedAlpha = layer.occupied() ? 0.82f : 0.18f;
            float layerProgress = SpellCircleAnimation.layerProgress(index, progress);
            if (layerProgress <= 0f) continue;
            float layerAlpha = alpha * occupiedAlpha * pulse * layerProgress;
            float radius = SpellCircleAnimation.radius(index, baseRadius, progress);

            poseStack.pushPose();
            poseStack.translate(0f, 0f, SpellCircleAnimation.depth(index, time, ground));
            poseStack.mulPose(Axis.XP.rotationDegrees(SpellCircleAnimation.tiltX(index, time, ground, radius)));
            poseStack.mulPose(Axis.YP.rotationDegrees(SpellCircleAnimation.tiltY(index, time, ground, radius)));
            poseStack.mulPose(Axis.ZP.rotationDegrees(spin));
            Matrix4f matrix = poseStack.last().pose();

            float red = SpellVisualPainter.red(layer.inkRgb());
            float green = SpellVisualPainter.green(layer.inkRgb());
            float blue = SpellVisualPainter.blue(layer.inkRgb());
            float inkMix = layer.occupied() ? 1f : 0.72f;
            red = red * inkMix + SpellVisualPainter.red(visual.sourceRgb()) * (1f - inkMix);
            green = green * inkMix + SpellVisualPainter.green(visual.sourceRgb()) * (1f - inkMix);
            blue = blue * inkMix + SpellVisualPainter.blue(visual.sourceRgb()) * (1f - inkMix);

            SpellGeometry.ringBand(consumer, matrix, radius * 0.981f, radius,
                    segments, 0f, 360f, layerAlpha * 0.48f, layerAlpha * 0.78f, red, green, blue);
            SpellGeometry.ringBand(consumer, matrix, radius * 0.900f, radius * 0.916f,
                    segments, 0f, 360f, layerAlpha * 0.28f, layerAlpha * 0.54f,
                    red, green, blue);
            paintSlots(consumer, matrix, layer, radius, layerAlpha, segments);
            SpellCircleArt.engraving(consumer, matrix, layer, radius, layerAlpha, detail);
            poseStack.popPose();
        }

        if (detail > 0.72f) {
            paintConnectors(poseStack, consumer, visual, time, alpha, baseRadius, progress, ground);
            float outerProgress = SpellCircleAnimation.layerProgress(0, progress);
            paintPulse(buffers, poseStack, visual, time, alpha * outerProgress,
                    radiusScale * (0.3f + 0.7f * outerProgress));
        }
        SpellCircleArt.glyphs(poseStack, buffers, layers, time, alpha, progress, detail, ground, baseRadius);
    }

    private static void paintSlots(VertexConsumer consumer, Matrix4f matrix, SpellCircleLayers.Layer layer,
            float radius, float alpha, int segments) {
        for (int slot = 0; slot < SpellCircleLayers.SLOTS_PER_LAYER; slot++) {
            var data = layer.slot(slot);
            float angle = 90f + slot * 180f;
            float red = SpellVisualPainter.red(data.inkRgb());
            float green = SpellVisualPainter.green(data.inkRgb());
            float blue = SpellVisualPainter.blue(data.inkRgb());
            float slotAlpha = alpha * (data.occupied() ? 1f : 0.25f);
            SpellGeometry.ringBand(consumer, matrix, radius * 0.856f, radius * 0.880f,
                    segments, angle - 56f, 112f, slotAlpha * 0.36f, slotAlpha * 0.76f, red, green, blue);
        }
    }

    private static void paintConnectors(PoseStack poseStack, VertexConsumer consumer, SpellVisual visual,
            float time, float alpha, float baseRadius, float progress, boolean ground) {
        float red = SpellVisualPainter.red(visual.sourceRgb());
        float green = SpellVisualPainter.green(visual.sourceRgb());
        float blue = SpellVisualPainter.blue(visual.sourceRgb());
        float beadRadius = ground ? 0.018f : Math.max(0.018f, baseRadius * 0.018f);
        for (int layer = 0; layer < SpellCircleLayers.LAYER_COUNT - 1; layer++) {
            float visible = Math.min(SpellCircleAnimation.layerProgress(layer, progress),
                    SpellCircleAnimation.layerProgress(layer + 1, progress));
            if (visible <= 0f) continue;
            for (int connector = 0; connector < 4; connector++) {
                float angle = connector * 90f;
                var from = SpellCircleAnimation.point(layer, time, ground, baseRadius, progress, angle, 0.94f);
                var to = SpellCircleAnimation.point(layer + 1, time, ground, baseRadius, progress, angle, 0.94f);
                for (int bead = 0; bead < 2; bead++) {
                    float travel = (time * 0.32f + bead * 0.5f + connector * 0.17f) % 1f;
                    var point = from.lerp(to, travel);
                    float glow = (float) Math.sin(Math.PI * travel);
                    poseStack.pushPose();
                    poseStack.translate(point.x, point.y, point.z);
                    SpellGeometry.cage(consumer, poseStack, beadRadius, beadRadius * 0.25f, 6,
                            alpha * visible * glow * 0.42f, red, green, blue);
                    poseStack.popPose();
                }
            }
        }
    }

    private static void paintPulse(MultiBufferSource buffers, PoseStack poseStack, SpellVisual visual,
            float time, float alpha, float radius) {
        float phase = (time * 0.42f) % 1f;
        float pulseRadius = radius * (0.28f + phase * 0.76f);
        float pulseAlpha = alpha * (1f - phase) * 0.52f;
        if (pulseAlpha <= 0.01f) return;
        VertexConsumer pulse = buffers.getBuffer(SpellGeometry.textureType(SpellVfx.RING_PULSE));
        poseStack.pushPose();
        poseStack.mulPose(Axis.ZP.rotationDegrees(-time * 64f));
        SpellGeometry.texturedQuad(pulse, poseStack.last().pose(), pulseRadius,
                pulseAlpha, SpellVisualPainter.red(visual.inkRgb()),
                SpellVisualPainter.green(visual.inkRgb()), SpellVisualPainter.blue(visual.inkRgb()));
        poseStack.popPose();
    }

}
