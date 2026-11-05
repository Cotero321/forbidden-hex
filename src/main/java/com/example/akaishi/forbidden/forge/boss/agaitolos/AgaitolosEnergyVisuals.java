package com.example.akaishi.forbidden.forge.boss.agaitolos;

import com.example.akaishi.forbidden.forge.magic.visual.SpellGeometry;
import com.example.akaishi.forbidden.forge.magic.visual.SpellRenderTypes;
import com.example.akaishi.forbidden.forge.magic.visual.SpellVfx;
import com.example.akaishi.forbidden.forge.magic.visual.SpellVisualPainter;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Quaternionf;

/** 黑金镰刃与怨咒法球共用的视觉库组装，不持有渲染目标或独立着色器。 */
public final class AgaitolosEnergyVisuals {
    public static final int GOLD = 0xDDB76A;
    public static final int CORE = 0xFFF1C7;
    public static final int AURA = 0x8963B8;

    private AgaitolosEnergyVisuals() { }

    public static void orb(PoseStack pose, MultiBufferSource buffers, Quaternionf camera,
                           float radius, float time, float alpha, float detail) {
        if (radius <= 0f || alpha <= 0f) return;
        float pulse = 1f + 0.035f * (float) Math.sin(time * 3.2f);
        SpellVisualPainter.flatQuad(buffers, pose, SpellVfx.HALO, camera,
                radius * 1.8f * pulse, alpha * 0.18f, AURA);
        SpellVisualPainter.flatQuad(buffers, pose, SpellVfx.COMET, camera,
                radius * 0.78f, alpha * 0.88f, GOLD);
        SpellVisualPainter.flatQuad(buffers, pose, SpellVfx.GLOW, camera,
                radius * 0.32f * pulse, alpha, CORE);
        if (detail <= 0.5f) return;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotation(time * 1.05f));
        pose.mulPose(Axis.XP.rotationDegrees(37f));
        VertexConsumer energy = buffers.getBuffer(SpellGeometry.energyType());
        SpellGeometry.ringBand(energy, pose.last().pose(), radius * 0.92f, radius,
                24, time * 19f, 275f, alpha * 0.58f, alpha * 0.18f,
                SpellVisualPainter.red(GOLD), SpellVisualPainter.green(GOLD), SpellVisualPainter.blue(GOLD));
        pose.mulPose(Axis.YP.rotationDegrees(68f));
        SpellGeometry.ringBand(energy, pose.last().pose(), radius * 1.08f, radius * 1.13f,
                24, -time * 26f, 230f, alpha * 0.36f, 0f,
                SpellVisualPainter.red(AURA), SpellVisualPainter.green(AURA), SpellVisualPainter.blue(AURA));
        if (detail > 0.75f) {
            pose.mulPose(Axis.ZP.rotation(-time * 0.8f));
            SpellGeometry.texturedQuad(buffers.getBuffer(SpellRenderTypes.glow(SpellVfx.RUNE)),
                    pose.last().pose(), radius * 1.17f, alpha * 0.22f,
                    SpellVisualPainter.red(GOLD), SpellVisualPainter.green(GOLD), SpellVisualPainter.blue(GOLD));
        }
        pose.popPose();
    }
}
