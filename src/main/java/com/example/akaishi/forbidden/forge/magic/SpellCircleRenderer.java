package com.example.akaishi.forbidden.forge.magic;

import com.example.akaishi.forbidden.forge.magic.visual.SpellGeometry;
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

/**
 * 法阵渲染器（设计记忆 §36.2 / §7）。
 *
 * <p><b>它画三层，每层的来源不同</b> —— 这正是"模块化组装"在载体侧的体现：
 * <ol>
 *   <li><b>主体</b>：交给 {@link SpellVisualPainter} —— 形态由 E 决定、配色由源与墨决定、
 *       尺度由数值决定、朝向由载体（FIELD ⇒ 放平）决定。渲染器自己<b>不认识任何一条 E</b>；</li>
 *   <li><b>覆盖圈</b>：按实体的作用半径画的一整圈 —— 让玩家看得见"这一块到底盖到哪"。
 *       没有它，§36.1 的"区域上限 8"对玩家就是不可见的天书；</li>
 *   <li><b>词符环</b>：本句<b>每个词</b>一枚符，缓慢自转
 *       （由字符码推出形状，见 {@link SpellGeometry#runeRing}）——
 *       于是"同一句法阵"两次画出来一样、<b>不同句子必然不同</b>。</li>
 * </ol>
 *
 * <p><b>为什么不再用服务端撒粒子围圈</b>：粒子无法表达几何形态与自转，
 * 也就无法承载"20 条 E 各不相同"这件事。粒子留着做点缀（见各形态内部的雾/涟漪），
 * 但主体必须是真几何。
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
        float radius = Math.max(0.5f, entity.effectRadius());
        return frustum.isVisible(entity.getBoundingBox().inflate(radius));
    }

    @Override
    public void render(SpellCircleEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight) {
        SpellVisual visual = entity.visual();
        if (visual == null) {
            return;
        }
        float time = (entity.tickCount + partialTick) / 20f;
        float fade = entity.fadeAlpha();

        // ① 主体：形态(E) × 载体(FIELD) × 配色(源×墨) × 尺度(数值)
        poseStack.pushPose();
        poseStack.translate(0d, GROUND_LIFT, 0d);
        SpellVisualPainter.paint(poseStack, buffers, visual, time, fade);
        poseStack.popPose();

        // ② + ③ 铺在地上的两个圈
        VertexConsumer consumer = buffers.getBuffer(SpellGeometry.energyType());
        int ink = visual.inkRgb();
        float r = SpellVisualPainter.red(ink);
        float g = SpellVisualPainter.green(ink);
        float b = SpellVisualPainter.blue(ink);
        float radius = entity.effectRadius();
        float alpha = fade * 0.9f;

        poseStack.pushPose();
        poseStack.translate(0d, GROUND_LIFT - 0.01d, 0d);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90f));
        // ②底纹（外）：实线环纹给法阵一个稳定的边界（缓慢自转保持活性）
        flatTexture(buffers, poseStack, SpellVfx.RING, radius * 1.08f, fade * 0.85f, r, g, b, time * 4f);
        // ③脉动（中）：虚线断环反向缓转 + 呼吸 —— 读作"这一块正在运作"
        flatTexture(buffers, poseStack, SpellVfx.RING_PULSE, radius * 1.16f,
                fade * (0.34f + 0.26f * (float) Math.sin(time * 3.4d)), r, g, b, -time * 7f);
        // ④符文（内）：符环反向慢转，与本句词符环同心 —— "这是一句被写下来的法阵"
        flatTexture(buffers, poseStack, SpellVfx.RUNE, radius * 0.78f, fade * 0.55f, r, g, b, time * 2.5f);
        Matrix4f matrix = poseStack.last().pose();
        // 覆盖圈（外）：内缘淡、外缘实，像一道"围栏"
        SpellGeometry.ringBand(consumer, matrix, Math.max(0.1f, radius - 0.08f), radius,
                BOUNDARY_SEGMENTS, 0f, 360f, alpha * 0.2f, alpha, r, g, b);
        // 词符环（内）：本句每个词一枚，缓慢自转
        SpellGeometry.runeRing(consumer, matrix, visual.glyphs(), radius * 0.62f,
                Math.max(0.06f, radius * 0.11f), time * 18f, alpha * 0.9f, r, g, b);
        poseStack.popPose();
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
