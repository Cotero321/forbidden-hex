package com.example.akaishi.forbidden.forge.magic.visual;

import com.example.akaishi.forbidden.forge.client.visual.ClientVisualBudget;
import com.example.akaishi.forbidden.magic.circle.SpellCircleEntity;
import com.example.akaishi.forbidden.magic.table.MagicSource;
import com.example.akaishi.forbidden.magic.visual.SpellInkMaterial;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

/** 独立于法阵平面的场域物质流，只读现有范围与淡出，不生成粒子实体。 */
public final class SpellFieldVfx {
    private SpellFieldVfx() { }

    public static void paint(PoseStack pose, MultiBufferSource buffers, ClientLevel level,
            Vec3 camera, float partialTick) {
        int fields = 0;
        for (var entity : level.entitiesForRendering()) {
            if (!(entity instanceof SpellCircleEntity circle) || circle.followsCaster() || circle.isRemoved()) continue;
            float detail = ClientVisualBudget.detailScale(level, circle.position());
            double distance = circle.position().distanceToSqr(camera);
            if (detail <= 0.5f || distance > 1024d) continue;
            if (++fields > 12) break;
            var visual = circle.visual();
            float radius = circle.effectRadius();
            if (!Float.isFinite(radius) || radius < 0.1f || visual.effectId().isEmpty()) continue;
            float alpha = circle.fadeAlpha(partialTick) * circle.introProgress(partialTick);
            if (alpha <= 0.01f) continue;
            float time = circle.animationTime(partialTick);
            var material = SpellInkMaterial.get(visual.inkId());
            float size = Math.min(0.14f, radius * 0.055f);
            int nodes = detail > 0.72f && distance <= 256d ? 6 : 3;
            pose.pushPose();
            pose.translate(circle.getX() - camera.x, circle.getY() - camera.y, circle.getZ() - camera.z);
            for (int i = 0; i < nodes; i++) {
                float cycle = (time * 0.27f * material.flowSpeed() + (float) i / nodes) % 1f;
                double angle = i * 2.399963d + time * 0.16f;
                float radial = radius * (0.25f + 0.35f * cycle);
                float height = 0.18f + cycle * 0.65f;
                pose.pushPose();
                pose.translate(Math.cos(angle) * radial, height, Math.sin(angle) * radial);
                SpellBodyPainter.paint(pose, buffers, MagicSource.byIdOrDefault(visual.sourceId()),
                        visual.inkId(), visual.inkRgb(), time + i, size,
                        alpha * (float) Math.sin(cycle * Math.PI) * 0.48f, 0.5f,
                        SpellBodyPainter.Stage.SUSTAIN, cycle);
                pose.popPose();
            }
            pose.popPose();
        }
    }
}
