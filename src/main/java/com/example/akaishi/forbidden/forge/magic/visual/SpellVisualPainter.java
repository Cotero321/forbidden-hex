package com.example.akaishi.forbidden.forge.magic.visual;

import com.example.akaishi.forbidden.magic.table.MagicIds;
import com.example.akaishi.forbidden.magic.visual.SpellCarrierForm;
import com.example.akaishi.forbidden.magic.visual.SpellGeometryKind;
import com.example.akaishi.forbidden.magic.visual.SpellVisual;
import com.example.akaishi.forbidden.magic.visual.SpellVisualSpecs;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import net.minecraft.world.phys.Vec3;

/**
 * 把一份 {@link SpellVisual} <b>组装</b>成实际的几何（设计记忆 §7 / §19.5 / §19.3）。
 *
 * <h3>组装规则（这就是"模块化"的全部）</h3>
 * <ol>
 *   <li><b>载体定朝向与尺寸</b>（{@link SpellCarrierForm}）：场域贴地平放、其余竖立；
 *       连发更细、布场更大；</li>
 *   <li><b>形态定零件与运动</b>（{@link SpellGeometryKind}）：由 E 决定画哪些零件、
 *       层数 / 自转 / 脉动 / 张缩 / 强调件数各是多少；</li>
 *   <li><b>源色做外层光晕</b>（{@link SpellVisualSpecs.Source}）：一层更大更淡的源色盘 ——
 *       这一层是让"换了源就不一样"真正可见的地方；</li>
 *   <li><b>墨色做内层主体</b>：形态几何用墨的识别色画 ——
 *       这一层是让"换了墨就不一样"真正可见的地方；</li>
 *   <li><b>数值定尺度</b>：威力 → 体积与亮度，词数 → 繁复度。</li>
 * </ol>
 *
 * <p>四者相乘之后，同一份绘制代码在不同输入下必然画出不同的东西 ——
 * 不需要也不允许"每条法术写一套渲染"。
 *
 * <p><b>坐标系约定</b>：本类画的几何以<b>局部原点为中心</b>；
 * 位置由调用方（实体渲染器 / 世界渲染器）在 PoseStack 上先摆好。
 */
public final class SpellVisualPainter {

    /** 每层的环段数（够圆又不过量） */
    private static final int RING_SEGMENTS = 32;

    /** 源色光晕的半径倍率 */
    private static final float AURA_SCALE = 1.5f;

    private SpellVisualPainter() {
    }

    /**
     * 画一次法术的完整外观。
     *
     * @param time     已存在的时间（秒）——自转 / 脉动 / 张缩 / 涟漪都由它驱动
     * @param alphaMul 整体透明度乘子（1 = 全亮；法阵临终淡出、蓄力渐显都靠它）
     */
    public static void paint(PoseStack poseStack, MultiBufferSource buffers, SpellVisual visual,
            float time, float alphaMul) {
        SpellVisualSpecs.Form base = visual.form();
        SpellVisualSpecs.Modifier modifier = visual.modifier();
        SpellVisualSpecs.Form form = new SpellVisualSpecs.Form(base.kind(),
                Math.max(1, base.layers() + modifier.extraLayers()), base.spin() * modifier.spinMul(),
                base.pulse(), base.expand(), Math.max(0, base.detail() + modifier.extraDetail()), base.inverted());
        SpellVisualSpecs.Carrier carrier = visual.carrierSpec();
        SpellVisualSpecs.Source body = visual.body();

        VertexConsumer consumer = buffers.getBuffer(SpellGeometry.energyType());

        float size = visual.baseSize() * carrier.sizeMul() * body.sizeMul() * modifier.sizeMul();
        float alpha = clamp(0.85f * body.alpha() * alphaMul * modifier.alphaMul(), 0f, 1f);
        float bright = visual.brightness();
        float complexity = visual.complexity();
        // 脉动频率同时受源影响：生命源跳得快、暗源缓慢涌动
        float pulseRate = form.pulse() * body.pulseMul();

        int accent = visual.inkRgb();
        int aura = visual.sourceRgb();

        poseStack.pushPose();
        // ① 载体定朝向：场域是"铺在地上的一块"，其余是"悬在空中的一团"
        if (carrier.form() == SpellCarrierForm.FIELD) {
            poseStack.mulPose(Axis.XP.rotationDegrees(-90f));
        }
        // ② 源色光晕（外层）：更大更淡，负责"能量体质"
        SpellGeometry.disc(consumer, poseStack.last().pose(), size * AURA_SCALE, 20,
                alpha * 0.30f * bright, 0f, red(aura), green(aura), blue(aura));
        // ③ 墨色主体（内层）：形态由 E 决定，负责"这是什么法术"
        paintForm(consumer, poseStack, visual.effectId(), form, time, size, alpha * bright, complexity, pulseRate, accent);
        poseStack.popPose();
    }

    /** 按 X 载体组装端点束流与 E 主体，端点位置由调用方预先放置。 */
    public static void paintCarrier(PoseStack poseStack, MultiBufferSource buffers, SpellVisual visual,
            Vec3 line, float time, float alphaMul, Quaternionf billboard) {
        SpellVisualSpecs.Carrier carrier = visual.carrierSpec();
        VertexConsumer consumer = buffers.getBuffer(SpellGeometry.energyType());
        float r = red(visual.inkRgb());
        float g = green(visual.inkRgb());
        float b = blue(visual.inkRgb());
        float alpha = clamp(alphaMul * visual.brightness(), 0f, 1f);
        float length = (float) line.length();
        boolean beam = carrier.form() == SpellCarrierForm.BEAM || carrier.form() == SpellCarrierForm.SALVO;
        if (beam) {
            int strands = carrier.sustained() ? 2 : carrier.staggered() ? 3 : 1;
            int beads = carrier.staggered() ? 6 : carrier.sustained() ? 12 : 8;
            float radius = carrier.staggered() ? 0.045f : carrier.sustained() ? 0.075f : 0.055f;
            for (int strand = 0; strand < strands; strand++) {
                poseStack.pushPose();
                float offset = (strand - (strands - 1) / 2f) * (carrier.sustained() ? 0.14f : 0.11f);
                poseStack.translate(offset, 0f, 0f);
                SpellGeometry.beadLine(consumer, poseStack, (float) line.x, (float) line.y,
                        (float) line.z, 0f, 0f, 0f, beads, radius, alpha,
                        r, g, b);
                poseStack.popPose();
            }
            // 光丝缀饰：沿线铺几片光带贴图，给串珠"丝缕"质感（纹样交给贴图，走向仍由串珠承担）
            VertexConsumer filament = buffers.getBuffer(SpellGeometry.textureType(SpellVfx.FILAMENT));
            int filaments = Math.min(24, Math.max(2, Math.round(length / 0.9f)));
            for (int i = 0; i < filaments; i++) {
                float t = (i + 0.5f) / filaments;
                poseStack.pushPose();
                poseStack.translate((float) line.x * t, (float) line.y * t, (float) line.z * t);
                poseStack.mulPose(billboard);
                float size = 0.16f + 0.05f * (float) Math.sin(time * 9f + i * 1.7f);
                SpellGeometry.texturedQuad(filament, poseStack.last().pose(), size,
                        alpha * 0.75f, r, g, b);
                poseStack.popPose();
            }
            if (carrier.staggered()) {
                poseStack.pushPose();
                poseStack.translate(0f, 0.12f * (float) Math.sin(time * 8f), 0f);
                poseStack.mulPose(billboard);
                paint(poseStack, buffers, visual, time + length * 0.04f, alphaMul * 0.8f);
                poseStack.popPose();
            }
            // 词模块挂载到束流：每个词在时间轴上占一个节点，连发会形成错开的词符脉冲。
            paintWordModules(buffers.getBuffer(SpellGeometry.energyType()), poseStack, visual, line,
                    time, alpha, carrier.staggered(), billboard);
        }
        // 载体专属贴图层：给"用什么把法术送出去"一个可见的纹样（顶点色几何画不出纹样）
        paintCarrierLayers(poseStack, buffers, visual, line, time, alpha, billboard);
        poseStack.pushPose();
        // 落点这一侧<b>一律面向相机</b>：E 的形态几何（弧 / 尖刺 / 环）本质是平面图形，
        // 不面向相机时从多数角度看就是"一条线" —— 这正是"结丝（LINK + 束）"看起来很差的原因。
        poseStack.mulPose(billboard);
        // 印记底纹：X4 附着的"被标记"感由符印承担 —— 顶点色几何画不出纹样
        if (!beam && carrier.form() == SpellCarrierForm.MARK) {
            VertexConsumer sigil = buffers.getBuffer(SpellGeometry.textureType(SpellVfx.SIGIL));
            float markSize = Math.max(0.35f, visual.baseSize() * 0.9f);
            SpellGeometry.texturedQuad(sigil, poseStack.last().pose(), markSize, alpha * 0.9f, r, g, b);
        }
        // 投射物的"飞行体"由实体渲染器画过了，事件层再画一遍就成了两个球 —— 故 ORB 只出收束与冲击
        if (carrier.form() != SpellCarrierForm.ORB) {
            paint(poseStack, buffers, visual, time, alphaMul);
        }
        if (!beam && !visual.glyphs().isEmpty()) {
            // X4 附着 / X6 回响 / X2 投射物：词符围绕载体中心显示。
            SpellGeometry.runeRing(buffers.getBuffer(SpellGeometry.energyType()), poseStack.last().pose(), visual.glyphs(),
                    Math.max(0.12f, visual.baseSize() * 0.55f),
                    Math.max(0.035f, visual.baseSize() * 0.10f), time * 18f,
                    alpha * 0.9f, r, g, b);
        }
        poseStack.popPose();
    }

    /**
     * 载体专属的<b>贴图层</b>：四种"送出去的方式"各配一套纹样。
     *
     * <p><b>为什么按载体分而不是按效果 E 分</b>：E 的差异已由主体几何（形态零件）承担；
     * 而"这一发是怎么送出去的"在几何上很难表达 —— 束和投射物都能用环/盘画出来。
     * 贴图的<b>纹样</b>正好补这一块：光带读成"流"、雾缕读成"残"、符印读成"标记"。
     *
     * <p>色分工与主体一致：<b>墨色 = 身份</b>、<b>源色 = 体质</b>。
     */
    private static void paintCarrierLayers(PoseStack poseStack, MultiBufferSource buffers,
            SpellVisual visual, Vec3 line, float time, float alpha, Quaternionf billboard) {
        int ink = visual.inkRgb();
        int aura = visual.sourceRgb();
        float size = visual.baseSize() * visual.carrierSpec().sizeMul();
        switch (visual.carrier()) {
            case BEAM -> {
                // 「结丝（link）」是"连接两方"：给它两条**反向滚动**的丝 —— 读作绞在一起的丝线，
                // 而不是一根直挺挺的光带。其他束法术保持单带（不该所有法术都变绞丝）。
                boolean link = MagicIds.Effect.LINK.equals(visual.effectId());
                float roll = link ? time * 2.6f : 0f;
                ribbon(buffers, poseStack, line, size * (link ? 0.15f : 0.10f), alpha * 0.55f,
                        link ? aura : ink, SpellVfx.STREAK, roll);
                if (link) {
                    ribbon(buffers, poseStack, line, size * 0.11f, alpha * 0.50f,
                            ink, SpellVfx.FILAMENT, -roll);
                }
                flatQuad(buffers, poseStack, SpellVfx.HALO, billboard, size * 0.85f, alpha * 0.45f, aura);
                flatQuad(buffers, poseStack, SpellVfx.SPARK, billboard, size * 0.45f, alpha * 0.85f, ink);
            }
            case SALVO -> {
                // 连发：单枚最细，靠"喷洒贴图 + 沿线光带"表达"多枚错开"
                ribbon(buffers, poseStack, line, size * 0.06f, alpha * 0.42f, ink, SpellVfx.SPRAY, 0f);
                flatQuad(buffers, poseStack, SpellVfx.SPARK, billboard, size * 0.40f, alpha * 0.80f, ink);
            }
            case MARK -> {
                // 印记：底光 + 符文圈（符印由调用方随后画）= 三层叠出"被标记"
                flatQuad(buffers, poseStack, SpellVfx.HALO, billboard, size * 1.05f, alpha * 0.50f, aura);
                flatQuad(buffers, poseStack, SpellVfx.RUNE, billboard, size * 0.95f, alpha * 0.80f, ink);
            }
            case AFTERIMAGE -> {
                // 回响：雾缕 + 底光 —— 残影该是"散"的，不是"实"的
                flatQuad(buffers, poseStack, SpellVfx.WISP, billboard, size * 1.15f, alpha * 0.70f, ink);
                flatQuad(buffers, poseStack, SpellVfx.HALO, billboard, size * 0.80f, alpha * 0.35f, aura);
            }
            case ORB -> {
                // 投射物：飞行体已由实体渲染器画过，事件层再画一遍就是"两个球"——
                // 故这里只出"到达那一下"：源色柔光收束 + 墨色彗头炸开
                flatQuad(buffers, poseStack, SpellVfx.HALO, billboard, size * 1.10f, alpha * 0.55f, aura);
                flatQuad(buffers, poseStack, SpellVfx.COMET, billboard, size * 0.80f, alpha * 0.90f, ink);
            }
            default -> {
                // FIELD（X5）：由法阵实体渲染器整程负责，事件层对它不派发，这里不会走到
            }
        }
        // 持续段的"重音"：引导 / 布场每秒结算一段 —— 视觉上也该有节拍，
        // 否则持续施法看起来只是"一直亮着"，玩家读不出"又结算了一段"
        if (visual.carrierSpec().sustained()) {
            float segmentPhase = time - (float) Math.floor(time);       // 0..1（每秒一轮）
            float accent = 1f - segmentPhase;                            // 刚过段界最亮
            if (accent > 0.02f) {
                flatQuad(buffers, poseStack, SpellVfx.RING_PULSE, billboard,
                        size * (0.90f + 1.50f * (1f - accent)), alpha * 0.55f * accent * accent, ink);
            }
        }
    }

    /**
     * 一枚面向相机（或给定朝向）的贴图方片。
     *
     * @param billboard 朝向旋转；{@code null} = 不旋转（贴在当前局部平面）
     */
    public static void flatQuad(MultiBufferSource buffers, PoseStack poseStack, ResourceLocation texture,
            Quaternionf billboard, float halfSize, float alpha, int rgb) {
        if (halfSize <= 0f || alpha <= 0.01f) {
            return;
        }
        poseStack.pushPose();
        if (billboard != null) {
            poseStack.mulPose(billboard);
        }
        VertexConsumer consumer = buffers.getBuffer(SpellGeometry.textureType(texture));
        SpellGeometry.texturedQuad(consumer, poseStack.last().pose(), halfSize, alpha,
                red(rgb), green(rgb), blue(rgb));
        poseStack.popPose();
    }

    /**
     * 沿 {@code line} 铺一条贴图光带：<b>交叉两个面</b>（互相垂直、都垂直于主线方向）。
     *
     * <p>与 {@link SpellGeometry#cage} 用正交三环换"不必面向相机"同一思路 ——
     * 单面光带在某些角度会变成一条线，双面无论从哪看都有可见面积。
     *
     * @param roll 把两个侧向绕主线滚动的角度（弧度）—— <b>滚起来就读作"绞丝"</b>；
     *             固定不动则是普通光带。传 {@code 0} 即不滚。
     */
    public static void ribbon(MultiBufferSource buffers, PoseStack poseStack, Vec3 line,
            float halfWidth, float alpha, int rgb, ResourceLocation texture, float roll) {
        double length = line.length();
        if (length < 1.0e-4d || halfWidth <= 0f || alpha <= 0.01f) {
            return;
        }
        float dx = (float) (line.x / length);
        float dy = (float) (line.y / length);
        float dz = (float) (line.z / length);
        // 取一个与主线不平行的参考轴，叉出第一条侧向
        float ux = 0f, uy = 1f, uz = 0f;
        if (Math.abs(dy) > 0.9f) {
            ux = 1f;
            uy = 0f;
            uz = 0f;
        }
        float s1x = dy * uz - dz * uy;
        float s1y = dz * ux - dx * uz;
        float s1z = dx * uy - dy * ux;
        float s1len = (float) Math.sqrt(s1x * s1x + s1y * s1y + s1z * s1z);
        if (s1len < 1.0e-4f) {
            return;
        }
        s1x /= s1len;
        s1y /= s1len;
        s1z /= s1len;
        // 第二条侧向 = 主线 × 第一条侧向
        float s2x = s1y * dz - s1z * dy;
        float s2y = s1z * dx - s1x * dz;
        float s2z = s1x * dy - s1y * dx;
        if (roll != 0f) {
            // 两个侧向在"垂直于主线的平面"内转一个角 —— 十字光带滚起来就是绞丝
            float cos = (float) Math.cos(roll);
            float sin = (float) Math.sin(roll);
            float r1x = s1x * cos + s2x * sin;
            float r1y = s1y * cos + s2y * sin;
            float r1z = s1z * cos + s2z * sin;
            float r2x = s2x * cos - s1x * sin;
            float r2y = s2y * cos - s1y * sin;
            float r2z = s2z * cos - s1z * sin;
            s1x = r1x;
            s1y = r1y;
            s1z = r1z;
            s2x = r2x;
            s2y = r2y;
            s2z = r2z;
        }

        float half = (float) (length * 0.5d);
        float fr = red(rgb);
        float fg = green(rgb);
        float fb = blue(rgb);
        VertexConsumer consumer = buffers.getBuffer(SpellGeometry.textureType(texture));
        poseStack.pushPose();
        poseStack.translate(line.x * 0.5d, line.y * 0.5d, line.z * 0.5d);
        Matrix4f matrix = poseStack.last().pose();
        SpellGeometry.texturedRibbon(consumer, matrix, dx, dy, dz, s1x, s1y, s1z,
                half, halfWidth, alpha, fr, fg, fb);
        SpellGeometry.texturedRibbon(consumer, matrix, dx, dy, dz, s2x, s2y, s2z,
                half, halfWidth, alpha, fr, fg, fb);
        poseStack.popPose();
    }

    private static void paintWordModules(VertexConsumer consumer, PoseStack poseStack, SpellVisual visual,
            Vec3 line, float time, float alpha, boolean staggered, Quaternionf billboard) {
        String glyphs = visual.glyphs();
        if (glyphs == null || glyphs.isEmpty()) {
            return;
        }
        int[] codePoints = glyphs.codePoints().toArray();
        int count = codePoints.length;
        int nodes = Math.min(16, Math.max(1, count));
        for (int i = 0; i < nodes; i++) {
            float t = nodes == 1 ? 0.5f : (float) i / (nodes - 1);
            float phase = staggered ? (time * 2.5f + i * 0.18f) % 1f : t;
            float x = (float) line.x * phase;
            float y = (float) line.y * phase;
            float z = (float) line.z * phase;
            poseStack.pushPose();
            poseStack.translate(x, y, z);
            // 词符是"平面纹样"：必须先面向相机（否则沿束飞的一串词符从侧面看就是一根根竖线），
            // 再在**画面内**自转 —— 这样"每个词都不一样、而且看得出在转"。
            poseStack.mulPose(billboard);
            poseStack.mulPose(Axis.ZP.rotationDegrees(time * 40f + i * 31f));
            int code = codePoints[i];
            float mark = 0.045f + Math.floorMod(code, 4) * 0.012f;
            SpellGeometry.runeMark(consumer, poseStack.last().pose(), code, mark,
                    alpha * (staggered ? 0.75f : 0.9f), red(visual.inkRgb()),
                    green(visual.inkRgb()), blue(visual.inkRgb()));
            poseStack.popPose();
        }
    }

    /** 便利重载：不额外压透明度 */
    public static void paint(PoseStack poseStack, MultiBufferSource buffers, SpellVisual visual, float time) {
        paint(poseStack, buffers, visual, time, 1f);
    }

    /**
     * 按几何原型画出主体。
     *
     * <p>每个分支都只做两件事：<b>挑零件</b> + <b>给参数</b>。
     * 零件的实现全在 {@link SpellGeometry}，因此这里永远不会长成"一堆画法"。
     */
    private static void paintForm(VertexConsumer consumer, PoseStack poseStack, String effectId, SpellVisualSpecs.Form form,
            float time, float size, float alpha, float complexity, float pulseRate, int color) {
        float r = red(color);
        float g = green(color);
        float b = blue(color);
        // 反转形态取负自转 —— "封印 / 开启"这类对立在视觉上就是转向相反
        float spin = time * form.spin() * 20f * (form.inverted() ? -1f : 1f);
        float pulse = pulseRate == 0f ? 1f : 0.85f + 0.15f * (float) Math.sin(time * pulseRate * Math.PI * 2d);
        float a = alpha * pulse;
        float expand = clamp(form.expand() * time, -0.6f * size, 1.5f * size);
        int layers = Math.max(1, Math.round(form.layers() * complexity));
        int detail = Math.max(2, Math.round(form.detail() * complexity));

        switch (form.kind()) {
            case GLOW_DISC -> SpellGeometry.disc(consumer, poseStack.last().pose(), size, 24, a, 0f, r, g, b);

            case RING -> {
                for (int i = 0; i < layers; i++) {
                    float t = layers <= 1 ? 0f : (float) i / (layers - 1);
                    float radius = size * (0.55f + 0.45f * t) + expand;
                    float thickness = size * 0.07f;
                    if (i % 2 == 0) {
                        // 整圈：稳定、像"法阵底座"
                        SpellGeometry.ringBand(consumer, poseStack.last().pose(),
                                radius - thickness, radius, RING_SEGMENTS, 0f, 360f, a, a * 0.35f, r, g, b);
                    } else {
                        // 缺口圈：自转时肉眼可见（整圈转了等于没转）
                        SpellGeometry.ringBand(consumer, poseStack.last().pose(),
                                radius - thickness, radius, RING_SEGMENTS, spin * (i + 1), 280f, a, a * 0.35f, r, g, b);
                    }
                }
            }

            case SPIKE -> SpellGeometry.starBand(consumer, poseStack.last().pose(),
                    size * 0.22f, size + expand, detail, spin, 360f, a, r, g, b);

            case SHELL -> {
                if ("ward".equals(effectId)) {
                    SpellGeometry.cage(consumer, poseStack, size + expand, size * 0.08f, 20, a, r, g, b);
                } else {
                    poseStack.pushPose();
                    poseStack.mulPose(Axis.ZP.rotationDegrees(45f));
                    SpellGeometry.ringBand(consumer, poseStack.last().pose(), size * 0.72f, size * 0.82f,
                            4, spin, 360f, a, a * 0.45f, r, g, b);
                    poseStack.popPose();
                }
            }

            case MIST -> {
                for (int i = 0; i <= layers; i++) {
                    float radius = size * (0.85f + 0.30f * i);
                    // 缓慢游移：雾不该是静止的
                    float drift = (float) Math.sin(time * 0.6d + i * 2.1d) * size * 0.30f;
                    poseStack.pushPose();
                    poseStack.translate(drift, drift * 0.6f, 0f);
                    SpellGeometry.disc(consumer, poseStack.last().pose(), radius, 18, a * 0.22f, 0f, r, g, b);
                    poseStack.popPose();
                }
            }

            case VORTEX -> SpellGeometry.spiralArms(consumer, poseStack.last().pose(),
                    Math.max(0.05f, size * 0.15f + expand * 0.3f), size, detail,
                    1.1f, 10, spin, a, r, g, b);

            case ARC -> {
                // 两道对着的弧：像"一刀斩过"留下的两半，而不是一整圈
                SpellGeometry.ringBand(consumer, poseStack.last().pose(),
                        size * 0.55f, size + expand * 0.5f, RING_SEGMENTS, spin, 130f, a * 0.2f, a, r, g, b);
                SpellGeometry.ringBand(consumer, poseStack.last().pose(),
                        size * 0.55f, size + expand * 0.5f, RING_SEGMENTS, spin + 180f, 130f, a * 0.2f, a, r, g, b);
            }

            case PILLAR -> {
                float height = size * (1.4f + 0.4f * pulse);
                SpellGeometry.cylinder(consumer, poseStack, size * 0.42f, 0f, height, 18, a * 0.55f, a * 0.30f, r, g, b);
                // 底部的实心盘：让光柱"落在地上"而不是凭空浮起
                SpellGeometry.disc(consumer, poseStack.last().pose(), size * 0.42f, 18, a * 0.5f, 0f, r, g, b);
            }

            case SIGIL -> {
                SpellGeometry.ringBand(consumer, poseStack.last().pose(),
                        size * 0.85f, size, RING_SEGMENTS, spin, 360f, a * 0.5f, a, r, g, b);
                for (int i = 0; i < detail; i++) {
                    float deg = spin + 360f / detail * i;
                    double rad = Math.toRadians(deg);
                    poseStack.pushPose();
                    poseStack.translate((float) Math.cos(rad) * size * 0.92f,
                            (float) Math.sin(rad) * size * 0.92f, 0f);
                    SpellGeometry.disc(consumer, poseStack.last().pose(), size * 0.14f, 10, a, 0f, r, g, b);
                    poseStack.popPose();
                }
            }

            case SHARD -> {
                for (int i = 0; i < detail; i++) {
                    float deg = spin + 360f / detail * i;
                    double rad = Math.toRadians(deg);
                    // 三档距离交替：碎片的"炸开"不是一个整齐的圆
                    float distance = size * (0.5f + 0.35f * (i % 3)) + expand;
                    poseStack.pushPose();
                    poseStack.translate((float) Math.cos(rad) * distance,
                            (float) Math.sin(rad) * distance, 0f);
                    poseStack.mulPose(Axis.ZP.rotationDegrees(deg));
                    SpellGeometry.starBand(consumer, poseStack.last().pose(),
                            size * 0.05f, size * 0.22f, 3, 0f, 360f, a, r, g, b);
                    poseStack.popPose();
                }
            }

            case CHAIN -> {
                // 沿 +Y 串起的环扣，相邻两枚互相垂直 —— 一眼就是"锁链"
                for (int i = 0; i < detail; i++) {
                    float offset = size * (0.30f + 0.30f * i);
                    poseStack.pushPose();
                    poseStack.translate(0f, offset, 0f);
                    poseStack.mulPose(Axis.YP.rotationDegrees(i % 2 == 0 ? 0f : 90f));
                    SpellGeometry.ringBand(consumer, poseStack.last().pose(),
                            size * 0.19f, size * 0.25f, 18, spin * (i + 1), 360f, a, a * 0.4f, r, g, b);
                    poseStack.popPose();
                }
            }

            case RIPPLE -> {
                // 相位错开的多重圈：一圈推一圈往外散
                for (int i = 0; i < layers; i++) {
                    float phase = (time * 0.7f + (float) i / layers) % 1f;
                    float radius = size * (0.35f + 0.95f * phase) + expand * 0.3f;
                    float ringAlpha = a * (1f - phase) * 0.9f;
                    SpellGeometry.ringBand(consumer, poseStack.last().pose(),
                            radius - size * 0.05f, radius, RING_SEGMENTS, spin, 360f,
                            ringAlpha * 0.3f, ringAlpha, r, g, b);
                }
            }
        }
    }

    // ==================== 颜色 / 数值工具 ====================

    /** 取 0xRRGGBB 的红分量（0~1）—— 供渲染器画覆盖圈等附属几何时复用 */
    public static float red(int rgb) {
        return (rgb >> 16 & 0xFF) / 255f;
    }

    /** 取 0xRRGGBB 的绿分量（0~1） */
    public static float green(int rgb) {
        return (rgb >> 8 & 0xFF) / 255f;
    }

    /** 取 0xRRGGBB 的蓝分量（0~1） */
    public static float blue(int rgb) {
        return (rgb & 0xFF) / 255f;
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }
}
