package com.example.akaishi.forbidden.magic.particle;

import com.example.akaishi.forbidden.magic.visual.SpellCarrierForm;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * <b>载体的运动修正表</b>（6 行）：回答"换个交付方式，同一团粒子该怎么变"。
 *
 * <p><b>与 {@link MagicInkStyle} 的关系是相乘</b>：墨给"这张图的性格"（上飘 / 下沉 / 缠绕…），
 * 载体给"送出去的方式带来的修正"（束要沿线铺密、印记要挂着不动、连发要更快更小…）。
 * 两者相乘 ⇒ <b>11 × 6 = 66 种行为</b>，而表只需维护 11 + 6 行
 * （比写 66 行更好改，也不会出现"两个表互相矛盾"）。
 *
 * <p>枚举序 = {@link SpellCarrierForm} 序（束 / 核心 / 印记 / 场 / 残影 / 齐射），
 * 与粒子贴图下标严格对齐。
 *
 * @param emitMul   数量倍率（束要沿线密铺；印记挂一颗就够）
 * @param spreadMul 散布倍率
 * @param speedMul  初速倍率
 * @param lifeMul   寿命倍率
 * @param sizeMul   尺寸倍率
 */
public record SpellCarrierStyle(float emitMul, float spreadMul, float speedMul,
        float lifeMul, float sizeMul) {

    /** 未知载体 / 无可查时的中性默认 */
    public static final SpellCarrierStyle NEUTRAL = new SpellCarrierStyle(1f, 1f, 1f, 1f, 1f);

    private static final Map<SpellCarrierForm, SpellCarrierStyle> BY_FORM;

    static {
        Map<SpellCarrierForm, SpellCarrierStyle> m = new EnumMap<>(SpellCarrierForm.class);
        // 束：沿线密铺、收拢（散开就看不出"一条"了）、略快、短命
        m.put(SpellCarrierForm.BEAM, new SpellCarrierStyle(1.50f, 0.50f, 1.30f, 0.80f, 0.90f));
        // 核心：基准
        m.put(SpellCarrierForm.ORB, new SpellCarrierStyle(1.00f, 0.80f, 1.00f, 1.00f, 1.00f));
        // 印记：挂着不动 —— 慢、久、少而稳
        m.put(SpellCarrierForm.MARK, new SpellCarrierStyle(0.70f, 0.50f, 0.40f, 1.40f, 1.10f));
        // 场：铺在地上缓慢铺开
        m.put(SpellCarrierForm.FIELD, new SpellCarrierStyle(0.90f, 0.70f, 0.50f, 1.20f, 1.10f));
        // 残影：散成雾（"残"就该是散的）
        m.put(SpellCarrierForm.AFTERIMAGE, new SpellCarrierStyle(0.80f, 1.40f, 0.70f, 1.30f, 1.20f));
        // 齐射：单枚小而快而短（多枚错开才是它的样子）
        m.put(SpellCarrierForm.SALVO, new SpellCarrierStyle(0.80f, 1.20f, 1.50f, 0.70f, 0.80f));
        BY_FORM = Collections.unmodifiableMap(m);
    }

    /** 取该载体的修正；{@code null} 或未知返回 {@link #NEUTRAL} */
    public static SpellCarrierStyle get(SpellCarrierForm form) {
        SpellCarrierStyle style = form == null ? null : BY_FORM.get(form);
        return style != null ? style : NEUTRAL;
    }

    /** 按枚举下标取（粒子端只有下标可查）；越界返回 {@link #NEUTRAL} */
    public static SpellCarrierStyle byIndex(int ordinal) {
        SpellCarrierForm[] forms = SpellCarrierForm.values();
        return ordinal < 0 || ordinal >= forms.length ? NEUTRAL : get(forms[ordinal]);
    }

    /** 表内条目数（自检断言用，应为 6） */
    public static int size() {
        return BY_FORM.size();
    }
}
