package com.example.akaishi.forbidden.magic.particle;

import com.example.akaishi.forbidden.magic.table.MagicIds;
import com.example.akaishi.forbidden.magic.table.MagicInkTable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>墨的运动个性表</b>（11 行）：回答"这种墨的粒子该怎么动"。
 *
 * <p><b>为什么必须有一张这样的表</b>：只换贴图的"多样化"是假的 ——
 * 同一张图配不同的<b>运动</b>（上飘 / 下沉 / 缠绕 / 直线 / 抖动）才读得出"这是另一种魔法"。
 * 光有 11 张贴图、运动全一样，玩家看到的仍然是"一团东西在飘"。
 *
 * <p>行序 = {@link MagicInkTable#order()}（与粒子形态下标严格对齐，改序等于改所有粒子外观）。
 *
 * @param gravity   与 {@code Particle#gravity} 同义：<b>正 = 下沉、负 = 上飘</b>
 * @param drag      速度保留率 /tick（越小停得越快；0.99 ≈ 几乎不减速）
 * @param swirl     水平速度的旋转角（度 / tick；0 = 直线，大值 = 缠绕 / 乱窜）
 * @param sizeMul   尺寸倍率
 * @param lifeMul   寿命倍率
 * @param emitMul   每次发射的<b>数量</b>倍率（>1 更密，<1 更稀）
 * @param spreadMul 初速散布倍率（>1 更炸，<1 更收拢）
 */
public record MagicInkStyle(float gravity, float drag, float swirl, float sizeMul, float lifeMul,
        float emitMul, float spreadMul) {

    /** 未知墨 / 无可查时的中性默认（= 不额外加任何个性） */
    public static final MagicInkStyle NEUTRAL = new MagicInkStyle(0f, 0.98f, 0f, 1f, 1f, 1f, 1f);

    private static final Map<String, MagicInkStyle> BY_ID;

    /** 行序（= 粒子形态下标基准；只读） */
    private static final List<String> ORDER;

    static {
        Map<String, MagicInkStyle> m = new LinkedHashMap<>();

        // 炽：窜的火星 —— 强上飘、快而短命、数量多
        m.put(MagicIds.Ink.CHI, new MagicInkStyle(-0.060f, 0.985f, 0f, 1.00f, 0.85f, 1.30f, 1.20f));
        // 壤：压下来的土石 —— 下沉、沉重大颗、稀
        m.put(MagicIds.Ink.RANG, new MagicInkStyle(0.070f, 0.900f, 0f, 1.25f, 0.80f, 0.80f, 0.70f));
        // 飓：拉成线 —— 不减速、细、散布极小
        m.put(MagicIds.Ink.JU, new MagicInkStyle(0f, 0.992f, 0f, 0.85f, 1.10f, 1.20f, 0.45f));
        // 蔓：绕着爬 —— 缠绕、慢慢长大、长寿
        m.put(MagicIds.Ink.MAN, new MagicInkStyle(-0.010f, 0.945f, 2.60f, 1.05f, 1.30f, 0.90f, 0.80f));
        // 蚀：剥落即散 —— 缓沉、短命
        m.put(MagicIds.Ink.SHI, new MagicInkStyle(0.035f, 0.920f, 0f, 0.95f, 0.70f, 0.90f, 1.00f));
        // 序：工整 —— 零抖动、零散布、规整的阵列
        m.put(MagicIds.Ink.XU, new MagicInkStyle(0f, 0.970f, 0f, 1.00f, 1.20f, 0.90f, 0.35f));
        // 混：乱窜 —— 强旋、散布最大、数量多
        m.put(MagicIds.Ink.HUN, new MagicInkStyle(0f, 0.930f, 3.40f, 1.10f, 1.00f, 1.40f, 1.80f));
        // 渊：坠落的黑点 —— 大而稀、下沉、慢
        m.put(MagicIds.Ink.YUAN, new MagicInkStyle(0.080f, 0.880f, 0.80f, 1.40f, 1.20f, 0.60f, 0.60f));
        // 耀：升起的光 —— 上飘、略大
        m.put(MagicIds.Ink.YAO, new MagicInkStyle(-0.045f, 0.950f, 0f, 1.10f, 1.00f, 1.10f, 0.90f));
        // 黯：阴沉的沉落 —— 缓沉 + 轻旋
        m.put(MagicIds.Ink.AN, new MagicInkStyle(0.030f, 0.900f, 1.20f, 1.15f, 0.90f, 0.90f, 0.80f));
        // 星：悬停的星尘 —— 极缓上浮、最长的寿命
        m.put(MagicIds.Ink.XING, new MagicInkStyle(-0.012f, 0.972f, 0.60f, 1.00f, 1.60f, 0.80f, 1.00f));

        BY_ID = Collections.unmodifiableMap(m);
        ORDER = List.copyOf(m.keySet());
    }

    /** 表序 id（自检 / 下标对齐用） */
    public static List<String> order() {
        return ORDER;
    }

    /** 按墨 id 取个性；未知 id 返回 {@link #NEUTRAL}（绝不返回 null） */
    public static MagicInkStyle get(String inkId) {
        MagicInkStyle style = inkId == null ? null : BY_ID.get(inkId);
        return style != null ? style : NEUTRAL;
    }

    /** 按表序下标取个性（粒子端只有下标可查）；越界返回 {@link #NEUTRAL} */
    public static MagicInkStyle byIndex(int index) {
        if (index < 0 || index >= ORDER.size()) {
            return NEUTRAL;
        }
        return get(ORDER.get(index));
    }

    /** 表内条目数（自检断言用，应为 11） */
    public static int size() {
        return BY_ID.size();
    }
}
