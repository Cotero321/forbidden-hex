package com.example.akaishi.forbidden.forge.magic.visual;

import com.example.akaishi.forbidden.magic.table.MagicSource;
import com.example.akaishi.forbidden.magic.visual.SpellVisual;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

/**
 * <b>命中冲击 + 余韵</b>（仅客户端）：一发法术打到点上之后的两段表现。
 *
 * <p><b>为什么单独一层而不是塞进 {@link SpellVisualPainter}</b>：
 * 主体是"这一发长什么样"（随载体存活），冲击是"打中的那一瞬"（0.42 秒冲刺）；
 * 混在一起会让"持续型载体"每一次脉冲都重放一遍冲击，看起来像在抽搐。
 * 故冲击<b>自有生命周期</b>（见 {@code SpellVisualEventHandler} 的 impacts 队列），
 * 比事件本身活得久一点 —— 这样才有"余韵"。
 *
 * <h3>第一段·冲刺（0 ~ 0.42s，三层由外到内）</h3>
 * <ol>
 *   <li><b>外扩虚环</b>（{@link SpellVfx#RING_PULSE}，源色）：把法阵那套"覆盖圈"语汇挪到命中处 ——
 *       一眼就知道"打到了、范围就这么大"；</li>
 *   <li><b>源专属闪光</b>（{@link SpellVfx#flare(MagicSource)}，墨色）：灰核是尘、绿核是绽、
 *       暗核是渊火、白核是圣光 —— 同一个"亮一下"，四种源长得不一样；</li>
 *   <li><b>放射星芒</b>（{@link SpellVfx#NOVA}，墨色）：最内一层的锐利爆点，负责"力度"。</li>
 * </ol>
 * 闪光走<b>平方衰减</b>（先亮后陡降），读起来才像"闪"而不是"渐隐"。
 *
 * <h3>第二段·余韵（0.42 ~ 1.20s）</h3>
 * 一层<b>缓慢上浮并扩散</b>的淡雾（墨色）+ 一层更淡的源色柔光。
 * 没有它，一击打完世界立刻回到原状 —— 玩家会觉得"刚才那下没打着"。
 */
public final class SpellImpactVfx {

    /** 冲刺段时长（秒）：再长就不像"命中"而像"留下了一个东西" */
    private static final float BURST_SECONDS = 0.42f;
    /** 余韵结束时刻（秒）：必须与 {@code SpellVisualEventHandler#IMPACT_LIFE_TICKS} 对齐 */
    public static final float LINGER_SECONDS = 1.20f;

    private SpellImpactVfx() {
    }

    /**
     * 画一次命中表现（冲刺 + 余韵）。
     *
     * @param ageSeconds 距命中已过的秒数；&gt; {@link #LINGER_SECONDS} 时本方法直接返回
     * @param billboard  面向相机的旋转（命中表现是"贴脸一闪"，必须始终正对玩家）
     */
    public static void paint(PoseStack poseStack, MultiBufferSource buffers, SpellVisual visual,
            float ageSeconds, Quaternionf billboard) {
        if (ageSeconds >= LINGER_SECONDS) {
            return;
        }
        float size = Math.max(0.35f, visual.baseSize());
        int ink = visual.inkRgb();
        int aura = visual.sourceRgb();

        if (ageSeconds <= BURST_SECONDS) {
            float progress = Mth.clamp(ageSeconds / BURST_SECONDS, 0f, 1f);
            float fade = 1f - progress;
            MagicSource source = MagicSource.byIdOrDefault(visual.sourceId());
            // ① 外扩虚环（源色）：范围感
            SpellVisualPainter.flatQuad(buffers, poseStack, SpellVfx.RING_PULSE, billboard,
                    size * (0.55f + 2.30f * progress), fade * 0.72f, aura);
            // ② 源专属闪光（墨色）：身份感
            SpellVisualPainter.flatQuad(buffers, poseStack, SpellVfx.flare(source), billboard,
                    size * (0.45f + 0.95f * progress), fade * fade * 0.95f, ink);
            // ③ 放射星芒（墨色）：力度
            SpellVisualPainter.flatQuad(buffers, poseStack, SpellVfx.NOVA, billboard,
                    size * (0.30f + 1.70f * progress), fade * 0.85f, ink);
            return;
        }

        // 余韵：上浮 + 扩散 + 渐隐（"还有余味"，而不是"打完了什么也没留下"）
        float linger = (ageSeconds - BURST_SECONDS) / (LINGER_SECONDS - BURST_SECONDS);
        float fade = (1f - linger) * (1f - linger);
        poseStack.pushPose();
        poseStack.translate(0f, linger * 0.55f, 0f);
        SpellVisualPainter.flatQuad(buffers, poseStack, SpellVfx.GLOW, billboard,
                size * (0.55f + 1.30f * linger), fade * 0.32f, ink);
        SpellVisualPainter.flatQuad(buffers, poseStack, SpellVfx.WISP, billboard,
                size * (0.45f + 1.05f * linger), fade * 0.26f, aura);
        poseStack.popPose();
    }
}
