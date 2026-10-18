package com.example.akaishi.forbidden.forge.magic.visual;

import com.example.akaishi.forbidden.magic.table.MagicGlyphs;
import com.example.akaishi.forbidden.magic.table.MagicIds;
import com.example.akaishi.forbidden.magic.visual.SpellCircleAnimation;
import com.example.akaishi.forbidden.magic.visual.SpellCircleLayers;
import com.example.akaishi.forbidden.magic.visual.SpellVisual;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

/** Fine-line seals and real word glyphs, shared by standing and placed circles. */
public final class SpellCircleArt {
    private SpellCircleArt() { }

    public static void foundation(PoseStack poses, MultiBufferSource buffers, SpellVisual visual,
            float time, float alpha, float progress, float detail, boolean ground, float baseRadius) {
        float opened = SpellCircleAnimation.layerProgress(1, progress);
        if (opened <= 0f) return;
        float radius = Math.max(0.1f, baseRadius) * (0.55f + 0.45f * opened);
        float opacity = alpha * opened;
        poseSeal(poses, ground, time);
        VertexConsumer lines = buffers.getBuffer(SpellRenderTypes.glow(SpellGeometry.WHITE));
        Matrix4f matrix = poses.last().pose();
        int color = luminous(visual.inkRgb(), 0.16f);
        float width = radius * 0.0105f;
        int points = switch (visual.effectId()) {
            case MagicIds.Effect.SUMMON -> 5;
            case MagicIds.Effect.WARD, MagicIds.Effect.VESSEL, MagicIds.Effect.BOOST -> 6;
            case MagicIds.Effect.BIND, MagicIds.Effect.LINK, MagicIds.Effect.FIELD,
                    MagicIds.Effect.MOVE, MagicIds.Effect.BREAK_TERMINATE, MagicIds.Effect.TEMPORAL -> 4;
            case MagicIds.Effect.MIND, MagicIds.Effect.WEAKEN -> 3;
            default -> 8;
        };
        float orientation = visual.form().inverted() ? -90f : 90f;
        polygon(lines, matrix, radius * 0.66f, points, points == 8 ? 3 : points == 6 || points == 5 ? 2 : 1,
                orientation, width, opacity * 0.72f, color);
        polygon(lines, matrix, radius * 0.66f, points, 1, orientation, width * 0.65f, opacity * 0.42f, color);
        if (detail > 0.5f) {
            polygon(lines, matrix, radius * 0.48f, points, 1, 90f + 180f / points,
                    width * 0.7f, opacity * 0.52f, visual.sourceRgb());
            for (int i = 0; i < points; i++) {
                float angle = 90f + i * 360f / points;
                radial(lines, matrix, radius * 0.67f, radius * 0.89f, angle, width,
                        opacity * 0.48f, color);
                diamond(lines, polar(matrix, radius * 0.72f, angle), radius * 0.033f,
                        width * 0.7f, opacity * 0.85f, color);
            }
        }
        sourceSeal(lines, matrix, visual, radius * 0.36f, time, opacity, detail);
        center(lines, matrix, visual, radius * 0.3f, time, opacity, detail);
        poses.popPose();
        if (detail > 0.5f) {
            poses.pushPose();
            poses.translate(0f, 0f, ground ? 0.10f : -0.28f);
            poses.mulPose(Axis.ZP.rotationDegrees(-time * 4f));
            SpellVisualPainter.flatQuad(buffers, poses, SpellVfx.RUNE, null,
                    radius * 0.96f, opacity * 0.36f, visual.inkRgb());
            poses.translate(0f, 0f, 0.003f);
            SpellVisualPainter.flatQuad(buffers, poses, SpellVfx.SIGIL, null,
                    radius * 0.48f, opacity * 0.24f, visual.sourceRgb());
            poses.popPose();
        }
        currents(poses, buffers, visual, time, opacity, radius, ground, detail);
    }

    public static void engraving(VertexConsumer consumer, Matrix4f matrix, SpellCircleLayers.Layer layer,
            float radius, float alpha, float detail) {
        int color = luminous(layer.inkRgb(), 0.12f);
        float width = radius * 0.009f;
        int divisions = detail > 0.72f ? 48 : 24;
        for (int mark = 0; mark < divisions; mark++) {
            float angle = mark * 360f / divisions;
            float length = mark % 3 == 0 ? 0.055f : 0.024f;
            radial(consumer, matrix, radius * (0.985f - length), radius * 0.985f,
                    angle, width, alpha * (mark % 3 == 0 ? 0.8f : 0.38f), color);
        }
        for (int slot = 0; slot < SpellCircleLayers.SLOTS_PER_LAYER; slot++) {
            var data = layer.slot(slot);
            float angle = 90f + slot * 180f;
            Matrix4f local = polar(matrix, radius * 0.81f, angle);
            float strength = alpha * (data.occupied() ? 0.85f : 0.18f);
            int ink = luminous(data.inkRgb(), 0.22f);
            diamond(consumer, local, radius * 0.15f, width * 0.8f, strength * 0.55f, ink);
            if (data.occupied()) {
                radial(consumer, matrix, radius * 0.63f, radius * 0.69f, angle,
                        width * 1.5f, strength, ink);
                for (int sign = -1; sign <= 1; sign += 2) {
                    radial(consumer, matrix, radius * 0.78f, radius * 0.86f, angle + sign * 16f,
                            width, strength * 0.7f, ink);
                }
            }
        }
        if (detail > 0.5f && layer.occupied()) {
            int corners = 4 + layer.index() * 2;
            polygon(consumer, matrix, radius * 0.72f, corners, 1, 45f,
                    width * 0.65f, alpha * 0.34f, color);
        }
    }

    public static void glyphs(PoseStack poses, MultiBufferSource buffers, SpellCircleLayers layers,
            float time, float alpha, float progress, float detail, boolean ground, float baseRadius) {
        Font font = Minecraft.getInstance().font;
        for (int index = 0; index < SpellCircleLayers.LAYER_COUNT; index++) {
            var layer = layers.layer(index);
            float opened = SpellCircleAnimation.layerProgress(index, progress);
            if (opened <= 0f) continue;
            float radius = SpellCircleAnimation.radius(index, baseRadius, progress);
            poses.pushPose();
            poses.translate(0f, 0f, SpellCircleAnimation.depth(index, time, ground));
            poses.mulPose(Axis.XP.rotationDegrees(SpellCircleAnimation.tiltX(index, time, ground, radius)));
            poses.mulPose(Axis.YP.rotationDegrees(SpellCircleAnimation.tiltY(index, time, ground, radius)));
            poses.mulPose(Axis.ZP.rotationDegrees(SpellCircleAnimation.spin(index, time)));
            for (int slot = 0; slot < SpellCircleLayers.SLOTS_PER_LAYER; slot++) {
                if (!layer.slot(slot).occupied()) continue;
                float angle = 90f + slot * 180f;
                double radians = Math.toRadians(angle);
                poses.pushPose();
                poses.translate(Math.cos(radians) * radius * 0.81f,
                        Math.sin(radians) * radius * 0.81f, 0.006f);
                poses.mulPose(Axis.ZP.rotationDegrees(angle - 90f));
                glyph(font, buffers, poses, layer.wordIds().get(slot), radius * 0.245f,
                        alpha * opened, luminous(layer.slot(slot).inkRgb(), 0.3f));
                poses.popPose();
            }
            poses.popPose();
        }
        if (detail > 0.72f) {
            float opened = SpellCircleAnimation.layerProgress(0, progress);
            if (opened <= 0f) return;
            poseSeal(poses, ground, time);
            float radius = Math.max(0.1f, baseRadius);
            for (int repeat = 0; repeat < 3; repeat++) {
                for (int slot = 0; slot < SpellCircleLayers.SLOT_COUNT; slot++) {
                    var layer = layers.layer(slot / 2);
                    if (!layer.slot(slot % 2).occupied()) continue;
                    float angle = 15f + (repeat * 8 + slot) * 15f;
                    double radians = Math.toRadians(angle);
                    poses.pushPose();
                    poses.translate(Math.cos(radians) * radius * 0.94f,
                            Math.sin(radians) * radius * 0.94f, 0.008f);
                    poses.mulPose(Axis.ZP.rotationDegrees(angle - 90f));
                    glyph(font, buffers, poses, layer.wordIds().get(slot % 2), radius * 0.068f,
                            alpha * opened * 0.64f, luminous(layer.slot(slot % 2).inkRgb(), 0.2f));
                    poses.popPose();
                }
            }
            poses.popPose();
        }
    }

    private static void sourceSeal(VertexConsumer consumer, Matrix4f matrix, SpellVisual visual,
            float radius, float time, float alpha, float detail) {
        Matrix4f local = new Matrix4f(matrix).scale(radius);
        float width = 0.022f;
        int color = luminous(visual.sourceRgb(), 0.24f);
        switch (visual.sourceId()) {
            case "life" -> {
                for (int i = 0; i < 4; i++) {
                    Matrix4f petal = new Matrix4f(local).rotateZ((float) Math.toRadians(i * 90f + 25f));
                    arc(consumer, petal, 1f, 4f, 62f, width, alpha * 0.7f, color);
                    radial(consumer, petal, 1f, 1.23f, 25f, width, alpha * 0.8f, color);
                    if (detail > 0.5f) diamond(consumer, polar(petal, 1.23f, 25f), 0.065f, width, alpha, color);
                }
            }
            case "dark" -> {
                for (int i = 0; i < 5; i++) {
                    float angle = i * 72f - time * 2f;
                    arc(consumer, local, 1f, angle + 5f, 49f, width * 1.4f, alpha * 0.75f, color);
                    radial(consumer, local, 0.89f, 1.16f, angle + 31f, width, alpha * 0.6f, color);
                }
            }
            case "white" -> {
                for (int i = 0; i < 6; i++) {
                    Matrix4f ray = polar(local, 1.04f, 30f + i * 60f);
                    ray.rotateZ((float) Math.toRadians(i * 60f + 30f));
                    polygon(consumer, new Matrix4f(ray).scale(1f, 0.4f, 1f), 0.18f,
                            4, 1, 0f, width, alpha * 0.9f, color);
                }
            }
            default -> {
                for (int i = 0; i < 6; i++) {
                    float angle = i * 60f + (i % 2 == 0 ? 4f : 10f);
                    arc(consumer, local, i % 2 == 0 ? 1f : 1.045f, angle, 43f,
                            width, alpha * 0.72f, color);
                    radial(consumer, local, 0.94f, 1.12f, angle + 21f, width, alpha * 0.6f, color);
                }
            }
        }
    }

    private static void center(VertexConsumer consumer, Matrix4f matrix, SpellVisual visual,
            float radius, float time, float alpha, float detail) {
        int ink = luminous(visual.inkRgb(), 0.24f);
        float width = radius * 0.034f;
        if (core(consumer, matrix, visual, radius, time, alpha, width, ink, detail)) return;
        switch (visual.form().kind()) {
            case SPIKE, SHARD -> {
                polygon(consumer, matrix, radius, 8, 3, time * 9f, width, alpha * 0.85f, ink);
                diamond(consumer, matrix, radius * 0.42f, width, alpha, ink);
            }
            case SHELL -> {
                polygon(consumer, matrix, radius, 6, 1, 90f, width, alpha, ink);
                polygon(consumer, matrix, radius * 0.72f, 3, 1, -90f, width, alpha * 0.8f, ink);
                polygon(consumer, matrix, radius * 0.72f, 3, 1, 90f, width, alpha * 0.8f, ink);
            }
            case VORTEX, MIST -> {
                SpellGeometry.spiralArms(consumer, matrix, radius * 0.18f, radius,
                        visual.form().detail() == 4 ? 4 : 3, visual.form().inverted() ? -0.65f : 0.65f,
                        detail > 0.5f ? 22 : 12, time * visual.form().spin(), alpha * 0.6f,
                        SpellVisualPainter.red(ink), SpellVisualPainter.green(ink), SpellVisualPainter.blue(ink));
                diamond(consumer, matrix, radius * 0.17f, width, alpha, ink);
            }
            case ARC, CHAIN -> {
                polygon(consumer, matrix, radius, 4, 1, 45f, width, alpha * 0.8f, ink);
                polygon(consumer, matrix, radius * 0.85f, 4, 1, 0f, width, alpha * 0.8f, ink);
                for (int i = 0; i < 4; i++) {
                    diamond(consumer, polar(matrix, radius * 0.62f, 45f + i * 90f),
                            radius * 0.23f, width, alpha, ink);
                }
            }
            case RIPPLE -> {
                polygon(consumer, matrix, radius, 8, 1, 22.5f, width, alpha * 0.75f, ink);
                for (int i = 0; i < 4; i++) {
                    radial(consumer, matrix, radius * 0.18f, radius * 0.88f,
                            time * 8f + i * 90f, width, alpha, ink);
                }
                diamond(consumer, matrix, radius * 0.26f, width, alpha, ink);
            }
            default -> {
                polygon(consumer, matrix, radius, 6, 2, time * 5f + 90f, width, alpha * 0.85f, ink);
                polygon(consumer, matrix, radius * 0.55f, 6, 1, -time * 7f, width, alpha * 0.65f, ink);
                diamond(consumer, matrix, radius * 0.2f, width, alpha, ink);
            }
        }
    }

    private static boolean core(VertexConsumer consumer, Matrix4f matrix, SpellVisual visual,
            float radius, float time, float alpha, float width, int ink, float detail) {
        Matrix4f local = new Matrix4f(matrix).scale(radius);
        float stroke = width / radius;
        int source = luminous(visual.sourceRgb(), 0.28f);
        switch (visual.effectId()) {
            case MagicIds.Effect.DAMAGE -> {
                local.rotateZ((float) Math.toRadians(time * 6f));
                for (int i = 0; i < 8; i++) {
                    Matrix4f ray = new Matrix4f(local).rotateZ((float) Math.toRadians(i * 45f));
                    line(consumer, ray, -0.13f, 0.3f, 0f, 1f, stroke, alpha, ink);
                    line(consumer, ray, 0f, 1f, 0.13f, 0.3f, stroke, alpha, ink);
                }
                diamond(consumer, local, 0.26f, stroke, alpha, source);
            }
            case MagicIds.Effect.BREAK_SHIELD -> {
                for (int i = 0; i < 6; i++) {
                    float angle = i * 60f + time * 3f;
                    arc(consumer, local, 0.62f, angle + 8f, 38f, stroke, alpha * 0.8f, ink);
                    Matrix4f shard = polar(local, 0.83f, angle);
                    shard.rotateZ((float) Math.toRadians(angle - 90f));
                    polygon(consumer, new Matrix4f(shard).scale(0.45f, 1f, 1f),
                            0.22f, 4, 1, 0f, stroke, alpha, source);
                }
            }
            case MagicIds.Effect.TELEPORT -> {
                for (int i = 0; i < 3; i++) {
                    float ring = 0.92f - i * 0.24f;
                    arc(consumer, local, ring, 30f + i * 60f - time * 5f, 132f,
                            stroke, alpha * (1f - i * 0.16f), ink);
                    arc(consumer, local, ring, 210f + i * 60f - time * 5f, 132f,
                            stroke, alpha * (1f - i * 0.16f), ink);
                }
                diamond(consumer, local, 0.12f, stroke, alpha, source);
            }
            case MagicIds.Effect.GATHER -> {
                SpellGeometry.spiralArms(consumer, local, 0.18f, 0.94f, 4, -0.85f,
                        detail > 0.5f ? 22 : 12, -time * 12f, alpha * 0.8f,
                        SpellVisualPainter.red(ink), SpellVisualPainter.green(ink), SpellVisualPainter.blue(ink));
                for (int i = 0; i < 4; i++) {
                    Matrix4f inward = new Matrix4f(local).rotateZ((float) Math.toRadians(i * 90f - time * 12f));
                    line(consumer, inward, -0.16f, 0.42f, 0f, 0.2f, stroke, alpha, source);
                    line(consumer, inward, 0f, 0.2f, 0.16f, 0.42f, stroke, alpha, source);
                }
            }
            case MagicIds.Effect.BIND -> {
                for (int i = 0; i < 6; i++) {
                    float angle = i * 60f;
                    Matrix4f link = polar(local, 0.66f, angle);
                    link.rotateZ((float) Math.toRadians(angle));
                    polygon(consumer, new Matrix4f(link).scale(0.52f, 1f, 1f),
                            0.38f, 4, 1, 0f, stroke, alpha, ink);
                }
                line(consumer, local, -0.24f, 0f, 0.24f, 0f, stroke, alpha, source);
                line(consumer, local, 0f, -0.24f, 0f, 0.24f, stroke, alpha, source);
            }
            case MagicIds.Effect.WARD -> {
                polygon(consumer, local, 1f, 6, 1, 90f, stroke, alpha, ink);
                polygon(consumer, local, 0.72f, 3, 1, -90f, stroke, alpha * 0.8f, source);
                polygon(consumer, local, 0.72f, 3, 1, 90f, stroke, alpha * 0.8f, source);
                arc(consumer, local, 1.08f, 58f, 64f, stroke, alpha * 0.65f, ink);
                arc(consumer, local, 1.08f, 238f, 64f, stroke, alpha * 0.65f, ink);
            }
            case MagicIds.Effect.MOVE, MagicIds.Effect.BOOST -> {
                boolean boost = MagicIds.Effect.BOOST.equals(visual.effectId());
                if (!boost) local.rotateZ((float) Math.toRadians(-90f));
                for (int i = 0; i < 3; i++) {
                    float y = -0.65f + i * 0.52f;
                    line(consumer, local, -0.48f, y, 0f, y + 0.36f, stroke, alpha, ink);
                    line(consumer, local, 0f, y + 0.36f, 0.48f, y, stroke, alpha, ink);
                }
                if (boost) {
                    line(consumer, local, -0.72f, 0f, 0.72f, 0f, stroke * 0.7f, alpha * 0.6f, source);
                    line(consumer, local, 0f, -0.82f, 0f, 0.92f, stroke * 0.7f, alpha * 0.6f, source);
                }
            }
            case MagicIds.Effect.MIND, MagicIds.Effect.SENSE -> {
                eye(consumer, local, 0f, stroke, alpha, ink);
                boolean mind = MagicIds.Effect.MIND.equals(visual.effectId());
                polygon(consumer, local, mind ? 0.28f : 0.18f, mind ? 3 : 8, 1,
                        mind ? -time * 8f : 22.5f, stroke, alpha, source);
                if (mind) eye(consumer, local, 0.28f, stroke * 0.6f, alpha * 0.5f, source);
                else for (int i = 0; i < 8; i++) radial(consumer, local, 0.75f, 1f,
                        i * 45f, stroke, alpha * 0.8f, ink);
            }
            case MagicIds.Effect.BREAK_UNLOCK -> {
                arc(consumer, local, 0.86f, time * 8f + 18f, 142f, stroke, alpha, ink);
                arc(consumer, local, 0.86f, time * 8f + 198f, 142f, stroke, alpha, ink);
                line(consumer, local, 0f, -0.78f, 0f, 0.06f, stroke, alpha, source);
                diamond(consumer, new Matrix4f(local).translate(0f, 0.3f, 0f), 0.28f, stroke, alpha, source);
                line(consumer, local, 0f, -0.53f, 0.28f, -0.53f, stroke, alpha, source);
            }
            case MagicIds.Effect.BREAK_TERMINATE -> {
                arc(consumer, local, 0.8f, 15f, 150f, stroke, alpha, ink);
                arc(consumer, local, 0.8f, 195f, 150f, stroke, alpha, ink);
                line(consumer, local, -0.8f, 0.22f, 0.8f, -0.22f, stroke * 1.8f, alpha, source);
                line(consumer, local, -0.26f, -0.54f, 0.26f, 0.54f, stroke, alpha, ink);
            }
            case MagicIds.Effect.LINK -> {
                for (int i = 0; i < 3; i++) {
                    float angle = i * 120f + time * 7f;
                    diamond(consumer, polar(local, 0.72f, angle), 0.22f, stroke, alpha, ink);
                    radial(consumer, local, 0.12f, 0.58f, angle, stroke, alpha, source);
                }
                polygon(consumer, local, 0.72f, 3, 1, time * 7f, stroke * 0.6f, alpha * 0.45f, source);
            }
            case MagicIds.Effect.TEMPORAL -> {
                polygon(consumer, local, 0.94f, 4, 1, 45f, stroke, alpha * 0.65f, ink);
                line(consumer, local, -0.48f, 0.64f, 0.48f, 0.64f, stroke, alpha, ink);
                line(consumer, local, -0.48f, -0.64f, 0.48f, -0.64f, stroke, alpha, ink);
                line(consumer, local, -0.48f, 0.64f, 0.48f, -0.64f, stroke, alpha, source);
                line(consumer, local, 0.48f, 0.64f, -0.48f, -0.64f, stroke, alpha, source);
                diamond(consumer, local, 0.12f + 0.06f * (float) Math.sin(time * 2f), stroke, alpha, ink);
            }
            case MagicIds.Effect.VESSEL -> {
                arc(consumer, local, 0.72f, 180f, 180f, stroke * 1.4f, alpha, ink);
                line(consumer, local, -0.72f, 0f, -0.72f, 0.48f, stroke, alpha, ink);
                line(consumer, local, 0.72f, 0f, 0.72f, 0.48f, stroke, alpha, ink);
                line(consumer, local, -0.84f, 0.48f, 0.84f, 0.48f, stroke, alpha, source);
                diamond(consumer, local, 0.24f, stroke, alpha, source);
                line(consumer, local, 0f, -0.72f, 0f, -0.94f, stroke, alpha, ink);
            }
            case MagicIds.Effect.WEAKEN, MagicIds.Effect.TOLL -> {
                boolean toll = MagicIds.Effect.TOLL.equals(visual.effectId());
                polygon(consumer, local, 0.92f, toll ? 8 : 3, 1, toll ? -time * 5f : -90f,
                        stroke, alpha * 0.8f, ink);
                if (toll) {
                    line(consumer, local, -0.15f, 0.85f, 0.2f, 0.18f, stroke * 1.4f, alpha, source);
                    line(consumer, local, 0.2f, 0.18f, -0.24f, -0.16f, stroke * 1.4f, alpha, source);
                    line(consumer, local, -0.24f, -0.16f, 0.12f, -0.88f, stroke * 1.4f, alpha, source);
                } else for (int i = 0; i < 3; i++) radial(consumer, local, 0.16f, 0.56f,
                        -90f + i * 120f, stroke, alpha, source);
            }
            case MagicIds.Effect.SUMMON -> {
                polygon(consumer, local, 1f, 5, 2, 90f + time * 4f, stroke, alpha, ink);
                arc(consumer, local, 0.62f, -time * 7f, 300f, stroke, alpha * 0.7f, source);
                diamond(consumer, local, 0.24f, stroke, alpha, source);
            }
            case MagicIds.Effect.FIELD -> {
                polygon(consumer, local, 1f, 4, 1, 45f, stroke, alpha, ink);
                polygon(consumer, local, 0.72f, 4, 1, -45f + time * 3f, stroke, alpha * 0.8f, source);
                for (int i = 0; i < 4; i++) diamond(consumer, polar(local, 0.75f, i * 90f),
                        0.12f, stroke, alpha, source);
            }
            case MagicIds.Effect.CONCEAL -> {
                for (int i = 0; i < 3; i++) {
                    Matrix4f offset = new Matrix4f(local).translate((i - 1) * 0.22f, (i - 1) * 0.12f, 0f);
                    arc(consumer, offset, 0.66f, time * 4f + i * 36f + 50f, 212f,
                            stroke * (1f - i * 0.15f), alpha * (0.9f - i * 0.22f), ink);
                }
                line(consumer, local, -0.6f, -0.6f, 0.6f, 0.6f, stroke, alpha * 0.55f, source);
            }
            default -> { return false; }
        }
        if (detail > 0.72f) arc(consumer, local, 1.1f, time * 4f, 68f, stroke * 0.6f, alpha * 0.4f, source);
        return true;
    }

    private static void arc(VertexConsumer consumer, Matrix4f matrix, float radius, float angle,
            float degrees, float width, float alpha, int color) {
        SpellGeometry.ringBand(consumer, matrix, radius - width * 0.5f, radius + width * 0.5f,
                Math.max(12, Math.round(degrees / 9f)), angle, degrees, alpha, alpha,
                SpellVisualPainter.red(color), SpellVisualPainter.green(color), SpellVisualPainter.blue(color));
    }

    private static void eye(VertexConsumer consumer, Matrix4f matrix, float offset, float width, float alpha, int color) {
        for (int sign = -1; sign <= 1; sign += 2) {
            float lastX = -0.92f, lastY = offset;
            for (int i = 1; i <= 12; i++) {
                float x = -0.92f + i * 1.84f / 12f;
                float y = offset + sign * 0.4f * (float) Math.sin(Math.PI * i / 12f);
                line(consumer, matrix, lastX, lastY, x, y, width, alpha, color);
                lastX = x;
                lastY = y;
            }
        }
    }

    private static void currents(PoseStack poses, MultiBufferSource buffers, SpellVisual visual,
            float time, float alpha, float radius, boolean ground, float detail) {
        if (detail <= 0.5f) return;
        poseSeal(poses, ground, time);
        VertexConsumer flow = buffers.getBuffer(SpellRenderTypes.flow(SpellVfx.FILAMENT));
        for (int arm = 0; arm < 6; arm++) {
            float angle = arm * 60f + time * 8f;
            float phase = (time * 0.28f + arm / 6f) % 1f;
            float position = visual.form().inverted() ? 1f - phase : phase;
            float from = radius * (0.3f + position * 0.48f);
            double radians = Math.toRadians(angle);
            float x = (float) Math.cos(radians), y = (float) Math.sin(radians);
            Matrix4f local = new Matrix4f(poses.last().pose()).translate(x * from, y * from, 0.012f);
            SpellGeometry.texturedRibbon(flow, local, x, y, 0f, -y, x, 0f,
                    radius * 0.12f, radius * 0.013f,
                    alpha * (float) Math.sin(Math.PI * phase) * 0.65f,
                    SpellVisualPainter.red(visual.sourceRgb()), SpellVisualPainter.green(visual.sourceRgb()),
                    SpellVisualPainter.blue(visual.sourceRgb()));
        }
        poses.popPose();
    }

    private static void glyph(Font font, MultiBufferSource buffers, PoseStack poses, String word,
            float height, float alpha, int color) {
        if (alpha <= 0.01f) return;
        Component glyph = MagicGlyphs.component(word);
        if (glyph.getString().isEmpty()) return;
        float scale = height / 12f;
        int argb = Math.max(4, Math.min(255, Math.round(alpha * 255f))) << 24 | (color & 0xFFFFFF);
        for (int side = 0; side < 2; side++) {
            poses.pushPose();
            if (side == 1) poses.mulPose(Axis.YP.rotationDegrees(180f));
            poses.translate(0f, 0f, 0.001f);
            poses.scale(scale, -scale, scale);
            font.drawInBatch(glyph, -font.width(glyph) * 0.5f, -3f, argb,
                    false, poses.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            poses.popPose();
        }
    }

    private static void poseSeal(PoseStack poses, boolean ground, float time) {
        poses.pushPose();
        poses.translate(0f, 0f, ground ? 0.12f : -0.29f);
        poses.mulPose(Axis.ZP.rotationDegrees(time * 3f));
    }

    private static Matrix4f polar(Matrix4f matrix, float radius, float angle) {
        double radians = Math.toRadians(angle);
        return new Matrix4f(matrix).translate((float) Math.cos(radians) * radius,
                (float) Math.sin(radians) * radius, 0.002f);
    }

    private static void diamond(VertexConsumer consumer, Matrix4f matrix, float radius,
            float width, float alpha, int color) {
        polygon(consumer, matrix, radius, 4, 1, 0f, width, alpha, color);
    }

    private static void polygon(VertexConsumer consumer, Matrix4f matrix, float radius, int points,
            int step, float angle, float width, float alpha, int color) {
        for (int i = 0; i < points; i++) {
            double from = Math.toRadians(angle + i * 360f / points);
            double to = Math.toRadians(angle + (i + step) * 360f / points);
            line(consumer, matrix, (float) Math.cos(from) * radius, (float) Math.sin(from) * radius,
                    (float) Math.cos(to) * radius, (float) Math.sin(to) * radius, width, alpha, color);
        }
    }

    private static void radial(VertexConsumer consumer, Matrix4f matrix, float inner, float outer,
            float angle, float width, float alpha, int color) {
        double radians = Math.toRadians(angle);
        float x = (float) Math.cos(radians), y = (float) Math.sin(radians);
        line(consumer, matrix, x * inner, y * inner, x * outer, y * outer, width, alpha, color);
    }

    private static void line(VertexConsumer consumer, Matrix4f matrix, float x0, float y0,
            float x1, float y1, float width, float alpha, int color) {
        float dx = x1 - x0, dy = y1 - y0;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length <= 0.00001f) return;
        float ox = -dy / length * width * 0.5f, oy = dx / length * width * 0.5f;
        float red = SpellVisualPainter.red(color), green = SpellVisualPainter.green(color), blue = SpellVisualPainter.blue(color);
        SpellGeometry.vertex(consumer, matrix, x0 - ox, y0 - oy, 0f, 0f, 0f, red, green, blue, alpha);
        SpellGeometry.vertex(consumer, matrix, x1 - ox, y1 - oy, 0f, 1f, 0f, red, green, blue, alpha);
        SpellGeometry.vertex(consumer, matrix, x1 + ox, y1 + oy, 0f, 1f, 1f, red, green, blue, alpha);
        SpellGeometry.vertex(consumer, matrix, x0 + ox, y0 + oy, 0f, 0f, 1f, red, green, blue, alpha);
    }

    private static int luminous(int color, float white) {
        int red = Math.round((color >> 16 & 255) * (1f - white) + 255f * white);
        int green = Math.round((color >> 8 & 255) * (1f - white) + 255f * white);
        int blue = Math.round((color & 255) * (1f - white) + 255f * white);
        return red << 16 | green << 8 | blue;
    }
}
