package com.example.akaishi.forbidden.forge.magic.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * 法术几何的<b>零件库</b>（设计记忆 §7「先图后码」）。
 *
 * <p><b>为什么要有这一层</b>：20 条 E 的形态各不相同，但它们的几何构成反复用到同一小撮零件 ——
 * 盘、环、星环、笼、柱、螺旋臂。把零件抽出来之后，
 * "每条 E 不同"就退化成"选哪些零件 + 给什么参数"，而不是 20 份互不相干的绘制代码。
 *
 * <p><b>统一约定</b>：
 * <ul>
 *   <li>所有零件都画在<b>局部 XY 平面</b>（朝 +Z），圆心在局部原点 ——
 *       要怎么摆、要不要放平、要不要沿轴串起来，由 {@link SpellVisualPainter} 用 PoseStack 决定；</li>
 *   <li>颜色一律<b>逐顶点</b>给（{@code color(r,g,b,a)}），因此<b>不需要任何专属贴图</b>：
 *       贴图统一用原版 {@code textures/misc/white.png}（纯白，乘法后即顶点色）；</li>
 *   <li>渲染类型由调用方给（见 {@link SpellRenderTypes}），零件只负责顶点；</li>
 *   <li>自发光、无叠加层 —— 能量不该被世界光照压暗。</li>
 * </ul>
 */
public final class SpellGeometry {

    /** 零件统一使用的原版纯白贴图（零新增贴图；顶点色会把它乘成任意颜色） */
    public static final ResourceLocation WHITE =
            new ResourceLocation("minecraft", "textures/misc/white.png");

    /**
     * 能量体统一使用的渲染类型。
     *
     * <p><b>为什么是 {@code entityTranslucentEmissive}</b>：
     * <ul>
     *   <li>{@code emissive} ⇒ 满亮，不被世界光照压暗（能量自己发光）；</li>
     *   <li>{@code translucent} ⇒ 逐顶点 alpha 生效，才有"由中心淡出"的观感；</li>
     *   <li><b>带深度测试</b> ⇒ 法术会被墙正确地挡住。这一条很重要：
     *       用 {@code lightning()} 那种无深度测试的类型会让法术穿墙可见，看起来像 bug。</li>
     * </ul>
     */
    public static RenderType energyType() {
        return RenderType.entityTranslucentEmissive(WHITE);
    }

    /**
     * 贴图 VFX 的渲染类型：与 {@link #energyType()} 同款
     * （自发光、逐顶点 alpha、有深度测试、无背面剔除），只是换成绑定指定贴图。
     */
    public static RenderType textureType(ResourceLocation texture) {
        return RenderType.entityTranslucentEmissive(texture);
    }

    /**
     * 贴图四边形：局部 XY 平面上以原点为中心的整幅方形（UV 0..1）。
     *
     * <p>给"环 / 符印 / 光丝"这类<b>纹样</b>用的最小零件 —— 纹样交给贴图，
     * 摆位 / 旋转 / 缩放交给 PoseStack，染色靠顶点色（贴图已去色为白）。
     */
    public static void texturedQuad(VertexConsumer consumer, Matrix4f matrix,
            float halfSize, float alpha, float r, float g, float b) {
        vertex(consumer, matrix, -halfSize, -halfSize, 0f, 0f, 1f, r, g, b, alpha);
        vertex(consumer, matrix, halfSize, -halfSize, 0f, 1f, 1f, r, g, b, alpha);
        vertex(consumer, matrix, halfSize, halfSize, 0f, 1f, 0f, r, g, b, alpha);
        vertex(consumer, matrix, -halfSize, halfSize, 0f, 0f, 0f, r, g, b, alpha);
    }

    /**
     * <b>贴图光带</b>：以局部原点为中心、沿 {@code (dx,dy,dz)} 拉伸、沿 {@code (sx,sy,sz)} 展宽的长条四边形。
     *
     * <p><b>为什么需要它</b>：{@link #texturedQuad} 只能贴在局部 XY 平面上，
     * 而尾迹 / 束流要沿"任意方向"拉伸。调用它的地方会<b>交叉铺两条</b>
     * （两条方向互相垂直、都垂直于主方向）—— 这样不必面向相机，任何角度看都在，
     * 与 {@link #cage} 用三环换"不必面向相机"是同一个思路。
     *
     * <p>UV：u 沿长度、v 横跨宽度 —— 斜向贴图（如光带）会被拉成长条状，读起来是"流过"而不是"贴片"。
     */
    public static void texturedRibbon(VertexConsumer consumer, Matrix4f matrix,
            float dx, float dy, float dz, float sx, float sy, float sz,
            float halfLength, float halfWidth, float alpha, float r, float g, float b) {
        float ax = dx * halfLength, ay = dy * halfLength, az = dz * halfLength;
        float bx = sx * halfWidth, by = sy * halfWidth, bz = sz * halfWidth;
        vertex(consumer, matrix, -ax - bx, -ay - by, -az - bz, 0f, 0f, r, g, b, alpha);
        vertex(consumer, matrix, -ax + bx, -ay + by, -az + bz, 0f, 1f, r, g, b, alpha);
        vertex(consumer, matrix, ax + bx, ay + by, az + bz, 1f, 1f, r, g, b, alpha);
        vertex(consumer, matrix, ax - bx, ay - by, az - bz, 1f, 0f, r, g, b, alpha);
    }

    private SpellGeometry() {
    }

    // ==================== 顶点 ====================

    /**
     * 写一个顶点。
     *
     * <p>法线恒取 +Y：能量体的观感是"自发光"，不吃方向光；
     * 但要写完 {@code normal} 才能满足 entity 顶点格式（缺项会整个批次画不出来）。
     */
    public static void vertex(VertexConsumer consumer, Matrix4f matrix,
            float x, float y, float z, float u, float v, float r, float g, float b, float a) {
        consumer.vertex(matrix, x, y, z)
                .color(r, g, b, a)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(0f, 1f, 0f)
                .endVertex();
    }

    // ==================== 零件 ====================

    /**
     * 光晕盘：由中心向外淡出的实心圆（三角扇）。
     *
     * <p>「由中心淡出」是它像"光"而不像"饼"的关键 —— 中心不透明、边缘全透，
     * 于是<b>一张纯白贴图 + 逐顶点 alpha 就画出了径向渐变</b>，不需要任何渐变贴图。
     */
    public static void disc(VertexConsumer consumer, Matrix4f matrix,
            float radius, int segments, float centerAlpha, float rimAlpha, float r, float g, float b) {
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2d / segments * i;
            double a1 = Math.PI * 2d / segments * (i + 1);
            // entityTranslucentEmissive 是 QUADS：每个三角扇片补一个重复顶点，
            // 形成退化四边形，不能把 3 顶点直接塞进 QUADS，否则下一片会跨片连接。
            vertex(consumer, matrix, 0f, 0f, 0f, 0.5f, 0.5f, r, g, b, centerAlpha);
            vertex(consumer, matrix,
                    (float) Math.cos(a0) * radius, (float) Math.sin(a0) * radius, 0f, 1f, 1f, r, g, b, rimAlpha);
            vertex(consumer, matrix,
                    (float) Math.cos(a1) * radius, (float) Math.sin(a1) * radius, 0f, 1f, 1f, r, g, b, rimAlpha);
            vertex(consumer, matrix,
                    (float) Math.cos(a1) * radius, (float) Math.sin(a1) * radius, 0f, 1f, 1f, r, g, b, rimAlpha);
        }
    }

    /**
     * 环带：内外半径之间的圆环（四边形带），可只画一段弧。
     *
     * @param arcStart 起始角（度）
     * @param arcSpan  张开角（度）；360 = 整圈
     */
    public static void ringBand(VertexConsumer consumer, Matrix4f matrix,
            float rInner, float rOuter, int segments, float arcStart, float arcSpan,
            float alphaInner, float alphaOuter, float r, float g, float b) {
        int steps = Math.max(3, Math.round(segments * Math.abs(arcSpan) / 360f));
        for (int i = 0; i < steps; i++) {
            double a0 = Math.toRadians(arcStart + arcSpan * i / steps);
            double a1 = Math.toRadians(arcStart + arcSpan * (i + 1) / steps);
            float x0i = (float) Math.cos(a0) * rInner;
            float y0i = (float) Math.sin(a0) * rInner;
            float x0o = (float) Math.cos(a0) * rOuter;
            float y0o = (float) Math.sin(a0) * rOuter;
            float x1i = (float) Math.cos(a1) * rInner;
            float y1i = (float) Math.sin(a1) * rInner;
            float x1o = (float) Math.cos(a1) * rOuter;
            float y1o = (float) Math.sin(a1) * rOuter;
            vertex(consumer, matrix, x0i, y0i, 0f, 0f, 0f, r, g, b, alphaInner);
            vertex(consumer, matrix, x0o, y0o, 0f, 1f, 0f, r, g, b, alphaOuter);
            vertex(consumer, matrix, x1o, y1o, 0f, 1f, 1f, r, g, b, alphaOuter);
            vertex(consumer, matrix, x1i, y1i, 0f, 0f, 1f, r, g, b, alphaInner);
        }
    }

    /**
     * 星环：内外半径<b>交替</b>的放射结构 —— 一个零件同时充当"尖刺冠"与"涡旋臂"。
     *
     * @param points   尖角数（= {@code detail}）
     * @param arcSpan  张开角（度）；360 = 一圈尖刺；小于 360 = 几道旋臂
     */
    public static void starBand(VertexConsumer consumer, Matrix4f matrix,
            float rInner, float rOuter, int points, float arcStart, float arcSpan,
            float alpha, float r, float g, float b) {
        int n = Math.max(3, points * 2);
        for (int i = 0; i < n; i++) {
            double a0 = Math.toRadians(arcStart + arcSpan * i / n);
            double a1 = Math.toRadians(arcStart + arcSpan * (i + 1) / n);
            float r0 = (i % 2 == 0) ? rOuter : rInner;
            float r1 = (i % 2 == 0) ? rInner : rOuter;
            vertex(consumer, matrix, 0f, 0f, 0f, 0.5f, 0.5f, r, g, b, alpha * 0.85f);
            vertex(consumer, matrix,
                    (float) Math.cos(a0) * r0, (float) Math.sin(a0) * r0, 0f, 1f, 0f, r, g, b, alpha * 0.15f);
            vertex(consumer, matrix,
                    (float) Math.cos(a1) * r1, (float) Math.sin(a1) * r1, 0f, 1f, 1f, r, g, b, alpha * 0.15f);
            vertex(consumer, matrix, 0f, 0f, 0f, 0.5f, 0.5f, r, g, b, alpha * 0.85f);
        }
    }

    /** 正交三环：三个互相垂直的环 —— 从任何角度看都成"笼"，因此不需要面向摄像机 */
    public static void cage(VertexConsumer consumer, PoseStack poseStack,
            float radius, float thickness, int segments, float alpha, float r, float g, float b) {
        ringBand(consumer, poseStack.last().pose(), radius - thickness, radius, segments,
                0f, 360f, alpha, alpha * 0.35f, r, g, b);
        poseStack.pushPose();
        poseStack.mulPose(Axis.XP.rotationDegrees(90f));
        ringBand(consumer, poseStack.last().pose(), radius - thickness, radius, segments,
                0f, 360f, alpha, alpha * 0.35f, r, g, b);
        poseStack.popPose();
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(90f));
        ringBand(consumer, poseStack.last().pose(), radius - thickness, radius, segments,
                0f, 360f, alpha, alpha * 0.35f, r, g, b);
        poseStack.popPose();
    }

    /**
     * 柱：沿局部 +Y 的圆筒侧面 + 顶盖。
     *
     * <p>顶盖用 {@code pushPose → 绕 X 转 −90° → 上移} 得到（把 XY 平面的盘放平到顶面），
     * 而<b>不是</b>新建一个 {@code PoseStack} —— 那样会丢掉外层的位置与朝向，柱顶会跑到世界原点去。
     */
    public static void cylinder(VertexConsumer consumer, PoseStack poseStack,
            float radius, float bottomY, float topY, int segments,
            float alphaSide, float alphaTop, float r, float g, float b) {
        Matrix4f matrix = poseStack.last().pose();
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2d / segments * i;
            double a1 = Math.PI * 2d / segments * (i + 1);
            float x0 = (float) Math.cos(a0) * radius;
            float z0 = (float) Math.sin(a0) * radius;
            float x1 = (float) Math.cos(a1) * radius;
            float z1 = (float) Math.sin(a1) * radius;
            // 侧面：底边 alpha 高、顶边 alpha 低（向上淡出，像"升起的光"）
            vertex(consumer, matrix, x0, bottomY, z0, 0f, 0f, r, g, b, alphaSide);
            vertex(consumer, matrix, x1, bottomY, z1, 1f, 0f, r, g, b, alphaSide);
            vertex(consumer, matrix, x1, topY, z1, 1f, 1f, r, g, b, alphaSide * 0.1f);
            vertex(consumer, matrix, x0, topY, z0, 0f, 1f, r, g, b, alphaSide * 0.1f);
        }
        poseStack.pushPose();
        poseStack.translate(0f, topY, 0f);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90f));
        disc(consumer, poseStack.last().pose(), radius, segments, alphaTop, 0f, r, g, b);
        poseStack.popPose();
    }

    /**
     * 螺旋臂：若干条向外旋出的臂（涡旋 / 心智 / 聚拢）。
     *
     * @param arms   臂数
     * @param turns  旋出的圈数
     */
    public static void spiralArms(VertexConsumer consumer, Matrix4f matrix,
            float rInner, float rOuter, int arms, float turns, int stepsPerArm,
            float arcStart, float alpha, float r, float g, float b) {
        for (int arm = 0; arm < arms; arm++) {
            float armPhase = 360f / arms * arm + arcStart;
            for (int i = 0; i < stepsPerArm; i++) {
                float t0 = (float) i / stepsPerArm;
                float t1 = (float) (i + 1) / stepsPerArm;
                double a0 = Math.toRadians(armPhase + turns * 360f * t0);
                double a1 = Math.toRadians(armPhase + turns * 360f * t1);
                float r0 = rInner + (rOuter - rInner) * t0;
                float r1 = rInner + (rOuter - rInner) * t1;
                float w0 = 0.10f * r0;
                float w1 = 0.10f * r1;
                vertex(consumer, matrix, (float) Math.cos(a0) * r0, (float) Math.sin(a0) * r0, 0f,
                        0f, 0f, r, g, b, alpha);
                vertex(consumer, matrix, (float) Math.cos(a1) * r1, (float) Math.sin(a1) * r1, 0f,
                        1f, 0f, r, g, b, alpha);
                vertex(consumer, matrix,
                        (float) Math.cos(a1) * (r1 - w1), (float) Math.sin(a1) * (r1 - w1), 0f,
                        1f, 1f, r, g, b, alpha * 0.2f);
                vertex(consumer, matrix,
                        (float) Math.cos(a0) * (r0 - w0), (float) Math.sin(a0) * (r0 - w0), 0f,
                        0f, 1f, r, g, b, alpha * 0.2f);
            }
        }
    }

    /**
     * 沿一条线串起的球珠（束流 / 链节）：每颗都是正交三环，因此任何角度都看得见
     */
    public static void beadLine(VertexConsumer consumer, PoseStack poseStack,
            float fromX, float fromY, float fromZ, float toX, float toY, float toZ,
            int beads, float radius, float alpha, float r, float g, float b) {
        for (int i = 0; i < beads; i++) {
            float t = beads <= 1 ? 0f : (float) i / (beads - 1);
            float x = fromX + (toX - fromX) * t;
            float y = fromY + (toY - fromY) * t;
            float z = fromZ + (toZ - fromZ) * t;
            // 中段最亮、两端淡出，读起来是"流过去"而不是"串珠子"
            float fade = (float) Math.sin(Math.PI * (0.15f + 0.85f * t));
            poseStack.pushPose();
            poseStack.translate(x, y, z);
            cage(consumer, poseStack, radius * (0.6f + 0.4f * fade), radius * 0.25f, 8,
                    alpha * fade, r, g, b);
            poseStack.popPose();
        }
    }

    /**
     * 词符环：把本句<b>每个词</b>刻成一枚各不相同的小符，等分排布在环上。
     *
     * <p><b>为什么不用字体渲染真的符号</b>：§4.5 的符号是 𝔞~𝔷 / 𝔄~𝔜（Unicode 数学 Fraktur 区），
     * 原版字体<b>不覆盖</b>这一区 —— 直接 drawString 会得到一片豆腐块
     * （这正是 §11 要求"符号必须经 {@code MagicGlyphs} 唯一入口"的原因，而那个入口尚未实现）。
     * 故这里改走<b>程序化刻符</b>：由字符码推出尖角数与朝向，
     * <b>每个词天然不同、且完全不依赖字体</b>。等字形方案落地后，这一环可以原样换成真符号。
     *
     * @param glyphs 本句全部词的符号串（长度 = 词数；空串则整环不画）
     */
    public static void runeRing(VertexConsumer consumer, Matrix4f matrix, String glyphs,
            float radius, float markSize, float baseAngle, float alpha, float r, float g, float b) {
        if (glyphs == null || glyphs.isEmpty()) {
            return;
        }
        int[] codePoints = glyphs.codePoints().toArray();
        int count = codePoints.length;
        for (int i = 0; i < count; i++) {
            int code = codePoints[i];
            double angle = Math.toRadians(baseAngle + 360d / count * i);
            float x = (float) Math.cos(angle) * radius;
            float y = (float) Math.sin(angle) * radius;
            Matrix4f local = new Matrix4f(matrix).translate(x, y, 0f);
            runeMark(consumer, local, code, markSize, alpha, r, g, b);
        }
    }

    /** 统一的程序化词符零件；束流节点与词符环必须保持同一字形规则。 */
    public static void runeMark(VertexConsumer consumer, Matrix4f matrix, int code, float markSize,
            float alpha, float r, float g, float b) {
        int points = 3 + Math.floorMod(code, 5);
        float spin = Math.floorMod(code, 37) * 10f;
        starBand(consumer, matrix, markSize * 0.18f, markSize, points, spin, 360f, alpha, r, g, b);
    }
}
