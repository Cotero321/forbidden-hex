package com.example.akaishi.forbidden.forge.magic;

import com.example.akaishi.forbidden.forge.magic.visual.SpellGeometry;
import com.example.akaishi.forbidden.forge.magic.visual.SpellSourceVfx;
import com.example.akaishi.forbidden.magic.table.MagicSource;
import com.example.akaishi.forbidden.magic.particle.AkaishiMagicParticles;
import com.example.akaishi.forbidden.forge.magic.visual.SpellVfx;
import com.example.akaishi.forbidden.forge.magic.visual.SpellVisualPainter;
import com.example.akaishi.forbidden.magic.circle.SpellCircleEntity;
import com.example.akaishi.forbidden.magic.visual.SpellVisual;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.Objects;

/**
 * 法阵渲染器（设计记忆 §36.2 / §7）。
 *
 * <p>主体为四个固定槽位层：槽位 0..1、2..3、4..5、6..7 分别映射外到内四层，
 * 空槽保留轮廓，各层使用缓存的墨色与词符，由内向外错峰展开。
 *
 * <p>跟随法阵按施法者插值朝向竖立，细线术式与真实词符保留立体深度；
 * 放置法阵沿地面向上叠成立体四层，使用正深度与限幅微倾斜，并以实际作用半径绘制覆盖边界。
 * 全部几何共享距离细节预算、连续动画时钟与淡出收束。
 */
public class SpellCircleRenderer extends EntityRenderer<SpellCircleEntity> {

    /** 覆盖圈的环段数（比主体更圆 —— 它是玩家判断范围的依据） */
    private static final int BOUNDARY_SEGMENTS = 56;

    /** 抬离地面一点点，避免与地面 z-fighting */
    private static final double GROUND_LIFT = 0.06d;

    public SpellCircleRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(SpellCircleEntity entity, Frustum frustum,
            double camX, double camY, double camZ) {
        if (com.example.akaishi.forbidden.forge.client.visual.ClientVisualBudget.detailScale(
                entity.distanceToSqr(camX, camY, camZ)) <= 0f) return false;
        SpellVisual visual = entity.visual();
        float bodyRadius = visual.baseSize() * visual.carrierSpec().sizeMul()
                * visual.body().sizeMul() * visual.modifier().sizeMul() * 3f;
        float radius = Math.max(bodyRadius, Math.max(0.7f, entity.effectRadius()) * 1.2f);
        return frustum.isVisible(entity.getBoundingBox().inflate(radius));
    }

    @Override
    public void render(SpellCircleEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight) {
        SpellVisual visual = entity.visual();
        if (visual == null) {
            return;
        }
        float detail = com.example.akaishi.forbidden.forge.client.visual.ClientVisualBudget.detailScale(
                entity.distanceToSqr(net.minecraft.client.Minecraft.getInstance()
                        .gameRenderer.getMainCamera().getPosition()));
        if (detail <= 0f) return;
        float time = entity.animationTime(partialTick);
        float fade = entity.fadeAlpha(partialTick);
        boolean upright = entity.followsCaster();
        float progress = entity.introProgress(partialTick);
        float charge = entity.chargeProgress(partialTick);
        float overallAlpha = fade * progress * (upright ? 0.55f + 0.45f * charge : 1f);
        float contraction = 0.65f + 0.35f * fade;

        poseStack.pushPose();
        if (upright) {
            poseStack.mulPose(Axis.YP.rotationDegrees(-entity.yaw(partialTick)));
            poseStack.translate(0d, 0d, -0.03d);
            poseStack.scale(contraction, contraction, contraction);
            float baseRadius = Math.max(0.7f, visual.baseSize() * 0.82f);
            com.example.akaishi.forbidden.forge.magic.visual.SpellCircleLayerPainter.paint(
                    poseStack, buffers, visual, entity.layers(), time, fade, progress, detail, false, baseRadius);
            float radius = baseRadius * 0.27f;
            VertexConsumer consumer = buffers.getBuffer(SpellGeometry.energyType());
            SpellGeometry.ringBand(consumer, poseStack.last().pose(), radius * 0.91f, radius,
                    Math.max(16, Math.round(40 * detail)), 90f, 360f * charge,
                    overallAlpha * 0.28f, overallAlpha * 0.82f,
                    SpellVisualPainter.red(visual.inkRgb()), SpellVisualPainter.green(visual.inkRgb()),
                    SpellVisualPainter.blue(visual.inkRgb()));
            poseStack.translate(0f, 0f, 0.04f);
            SpellSourceVfx.paint(poseStack, buffers, MagicSource.byIdOrDefault(visual.sourceId()),
                    visual.sourceRgb(), visual.inkRgb(), time, baseRadius * 0.92f,
                    overallAlpha * 0.60f, detail);
            poseStack.popPose();
            return;
        }

        // PLACE uses the same four fixed layers, rotated flat onto the hit surface.
        poseStack.translate(0d, GROUND_LIFT, 0d);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90f));
        poseStack.scale(contraction, contraction, contraction);
        com.example.akaishi.forbidden.forge.magic.visual.SpellCircleLayerPainter.paint(
                poseStack, buffers, visual, entity.layers(), time, fade, progress, detail, true,
                entity.effectRadius());
        poseStack.popPose();

        int ink = visual.inkRgb();
        float r = SpellVisualPainter.red(ink);
        float g = SpellVisualPainter.green(ink);
        float b = SpellVisualPainter.blue(ink);
        float boundaryProgress = com.example.akaishi.forbidden.magic.visual.SpellCircleAnimation.layerProgress(0, progress);
        float radius = entity.effectRadius() * (0.65f + 0.35f * boundaryProgress);
        float alpha = fade * boundaryProgress * 0.9f;

        poseStack.pushPose();
        poseStack.translate(0d, GROUND_LIFT - 0.01d, 0d);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90f));
        if (detail > 0.72f) {
            flatTexture(buffers, poseStack, SpellVfx.RING, radius, alpha * 0.25f, r, g, b, time * 4f);
        }
        VertexConsumer consumer = buffers.getBuffer(SpellGeometry.energyType());
        Matrix4f matrix = poseStack.last().pose();
        SpellGeometry.ringBand(consumer, matrix, Math.max(0.01f, radius - 0.08f), radius,
                Math.max(16, Math.round(BOUNDARY_SEGMENTS * detail)),
                0f, 360f, alpha * 0.2f, alpha, r, g, b);
        poseStack.translate(0f, 0f, 0.025f);
        SpellSourceVfx.paint(poseStack, buffers, MagicSource.byIdOrDefault(visual.sourceId()),
                visual.sourceRgb(), ink, time, radius * 0.98f, alpha * 0.65f, detail);
        if (detail > 0.5f) {
            float phase = time * 0.55f - (float) Math.floor(time * 0.55f);
            float pulseRadius = radius * (0.30f + 0.65f * phase);
            SpellGeometry.ringBand(consumer, poseStack.last().pose(), pulseRadius * 0.99f, pulseRadius,
                    Math.max(16, Math.round(32 * detail)), -time * 22f, 360f,
                    alpha * (1f - phase) * 0.25f, alpha * (1f - phase) * 0.07f, r, g, b);
        }
        poseStack.popPose();
        if (detail > 0.72f) paintNodes(poseStack, buffers, visual, radius, alpha, time);
    }

    private static void paintNodes(PoseStack pose, MultiBufferSource buffers, SpellVisual visual,
            float radius, float alpha, float time) {
        if (radius <= 0f || alpha <= 0.01f) return;
        var consumer = buffers.getBuffer(Objects.requireNonNull(SpellGeometry.energyType()));
        int photon = AkaishiMagicParticles.photonRgb(visual.inkRgb());
        float size = Math.min(0.12f, radius * 0.05f);
        for (int i = 0; i < 4; i++) {
            double angle = Math.toRadians(time * 8f + i * 90f);
            float height = Math.min(0.40f, radius * 0.16f)
                    * (0.75f + 0.25f * (float) Math.sin(time * 2.2f + i * 1.5f));
            pose.pushPose();
            pose.translate(Math.cos(angle) * radius * 0.84f, GROUND_LIFT + height * 0.5f,
                    Math.sin(angle) * radius * 0.84f);
            for (int side = 0; side < 2; side++) {
                SpellGeometry.texturedRibbon(consumer, pose.last().pose(), 0f, 1f, 0f,
                        side == 0 ? 1f : 0f, 0f, side == 0 ? 0f : 1f,
                        height * 0.5f, size * 0.08f, alpha * 0.22f,
                        SpellVisualPainter.red(visual.sourceRgb()), SpellVisualPainter.green(visual.sourceRgb()),
                        SpellVisualPainter.blue(visual.sourceRgb()));
            }
            pose.translate(0f, height * 0.5f, 0f);
            pose.mulPose(Objects.requireNonNull(Axis.YP.rotationDegrees(time * 48f + i * 45f)));
            SpellGeometry.crystal(consumer, pose, size * 0.65f, size, alpha * 0.65f,
                    SpellVisualPainter.red(photon), SpellVisualPainter.green(photon), SpellVisualPainter.blue(photon));
            pose.popPose();
        }
    }

    @Override
    public ResourceLocation getTextureLocation(SpellCircleEntity entity) {
        // 几何全部用原版纯白贴图 + 顶点色绘制（零新增贴图）
        return SpellGeometry.WHITE;
    }

    /** 铺一层贴图纹样（绕本地 Z 轴自转 {@code spinDegrees} 度）—— 法阵的三层底纹共用 */
    private static void flatTexture(MultiBufferSource buffers, PoseStack poseStack, ResourceLocation texture,
            float halfSize, float alpha, float r, float g, float b, float spinDegrees) {
        if (alpha <= 0.01f || halfSize <= 0f) {
            return;
        }
        poseStack.pushPose();
        poseStack.mulPose(Axis.ZP.rotationDegrees(spinDegrees));
        VertexConsumer consumer = buffers.getBuffer(SpellGeometry.textureType(texture));
        SpellGeometry.texturedQuad(consumer, poseStack.last().pose(), halfSize, alpha, r, g, b);
        poseStack.popPose();
    }
}
