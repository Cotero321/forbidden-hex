package com.example.akaishi.forbidden.forge.magic.visual;

import com.example.akaishi.forbidden.magic.table.MagicSource;
import com.example.akaishi.forbidden.magic.visual.SpellVisual;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

import java.util.Objects;

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
        paint(poseStack, buffers, visual, ageSeconds, billboard, 1f);
    }

    public static void paint(PoseStack poseStack, MultiBufferSource buffers, SpellVisual visual,
            float ageSeconds, Quaternionf billboard, float detail) {
        if (!Float.isFinite(ageSeconds) || ageSeconds < 0f || ageSeconds >= LINGER_SECONDS || detail <= 0f) {
            return;
        }
        float size = Math.max(0.35f, visual.baseSize());
        int ink = visual.inkRgb();
        int aura = visual.sourceRgb();
        float life = ageSeconds / LINGER_SECONDS;
        MagicSource source = MagicSource.byIdOrDefault(visual.sourceId());
        boolean soft = switch (visual.form().kind()) {
            case MIST, VORTEX, RIPPLE, GLOW_DISC -> true;
            default -> false;
        };
        float turn = visual.form().inverted() ? -1f : 1f;
        float spread = soft ? 0.78f : 1f;
        float sourceRadius = source == MagicSource.DARK ? 1.45f - life * 1.10f : 0.75f + 1.10f * life;
        poseStack.pushPose();
        poseStack.mulPose(Objects.requireNonNull(billboard));
        SpellSourceVfx.paint(poseStack, buffers, source,
                aura, ink, ageSeconds, size * sourceRadius,
                (1f - life) * (1f - life) * 0.85f, detail);
        poseStack.popPose();

        if (ageSeconds <= BURST_SECONDS) {
            float progress = Mth.clamp(ageSeconds / BURST_SECONDS, 0f, 1f);
            float fade = 1f - progress;
            float waveRadius = source == MagicSource.DARK ? 2.30f - 1.75f * progress : 0.55f + 2.30f * progress;
            SpellVisualPainter.flatQuad(buffers, poseStack, SpellVfx.RING_PULSE, billboard,
                    size * waveRadius, fade * 0.72f, aura);
            // ② 源专属闪光（墨色）：身份感
            SpellVisualPainter.flatQuad(buffers, poseStack, SpellVfx.flare(source), billboard,
                    size * (0.45f + 0.95f * progress), fade * fade * 0.95f, ink);
            // ③ 放射星芒（墨色）：力度
            if (detail > 0.5f) {
                SpellVisualPainter.flatQuad(buffers, poseStack, soft ? SpellVfx.WISP : SpellVfx.NOVA,
                        new Quaternionf(billboard).rotateZ(turn * progress * (soft ? 0.35f : 0.65f)),
                        size * (0.30f + 1.70f * progress) * spread, fade * (soft ? 0.38f : 0.62f), ink);
                float echo = Mth.clamp((progress - 0.16f) / 0.84f, 0f, 1f);
                float echoFade = com.example.akaishi.forbidden.magic.visual.SpellCircleAnimation.smooth(echo / 0.22f)
                        * (1f - echo) * (1f - echo);
                SpellVisualPainter.flatQuad(buffers, poseStack, SpellVfx.RING_PULSE,
                        new Quaternionf(billboard).rotateZ(-turn * progress * 0.45f),
                        size * (0.40f + 1.85f * echo) * spread, echoFade * 0.35f, ink);
                SpellVisualPainter.flatQuad(buffers, poseStack, SpellVfx.RUNE,
                        new Quaternionf(billboard).rotateZ(-progress * 0.5f),
                        size * (0.65f + 0.75f * progress), fade * fade * 0.32f, aura);
            }
        }

        float linger = Mth.clamp((ageSeconds - 0.16f) / (LINGER_SECONDS - 0.16f), 0f, 1f);
        float fade = com.example.akaishi.forbidden.magic.visual.SpellCircleAnimation.smooth(linger / 0.30f)
                * (1f - linger) * (1f - linger);
        float drift = source == MagicSource.DARK ? -0.08f : source == MagicSource.LIFE ? 0.36f : 0.20f;
        float radius = source == MagicSource.DARK ? 1.05f - 0.50f * linger : 0.55f + 0.85f * linger;
        poseStack.pushPose();
        poseStack.translate(0f, linger * drift, 0f);
        SpellVisualPainter.flatQuad(buffers, poseStack, SpellVfx.GLOW, billboard,
                size * radius * spread, fade * 0.28f, ink);
        if (detail > 0.5f) {
            SpellVisualPainter.flatQuad(buffers, poseStack, SpellVfx.WISP,
                    new Quaternionf(billboard).rotateZ(turn * linger * 0.25f),
                    size * (0.45f + 0.80f * linger) * spread, fade * 0.22f, aura);
        }
        poseStack.popPose();
    }
}
