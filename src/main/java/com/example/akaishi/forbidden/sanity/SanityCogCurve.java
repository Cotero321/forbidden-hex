package com.example.akaishi.forbidden.sanity;

/**
 * COG（认知值）两张系数表的<b>唯一查询口径</b>（纯函数、无状态、线程安全）。
 *
 * <p><b>为什么把两张表收在一个类里</b>：它们都以 COG 为自变量，且都属于"认知值如何影响理智系统"；
 * 分散到各结算类会让"改认知平衡"要满仓库找系数。收口后：调手感只改本类的常量数组，
 * 结算层只问"当前 COG 的系数是多少"，不出现第二份表。
 *
 * <p><b>表一（档位表）</b>：四列同档位切换，档位由 COG 阈值决定，<b>无插值</b>：
 * <pre>
 * COG     环境扣除系数  SANC 效力  食补效力  自然恢复倍率
 * &lt; 50       1.00       100%     100%       ×1
 * ≥ 50       0.60        90%     100%       ×1
 * ≥ 100      0.40        80%      80%       ×1
 * ≥ 150      0.30        70%      70%      ×1.5
 * ≥ 200      0.20        60%      60%       ×2
 * </pre>
 * 环境扣除系数作用于 {@link com.example.akaishi.forbidden.api.sanity.ISanityRule#amountPerPeriod()}（扣减统一乘系数）；
 * 食补效力作用于 {@link com.example.akaishi.forbidden.api.sanity.ISanityFoodProfile} 的补量；
 * SANC 效力与自然恢复倍率由后续段落（上限恢复 / 自然回复）取用。
 *
 * <p><b>表二（精神减免）</b>：COG &lt; 100 无减免；100 → 200 线性 20% → 50%；≥ 200 保持 50%。
 * 与表一的档位切换不同，这一张<b>线性插值</b>——它是防御属性，突跳会让"再吃一口认知药"瞬间多挡一次伤害。
 *
 * <p>所有数值均为<b>待调手感值</b>（首版按设计口径落地，未做平衡性验证）。
 */
public final class SanityCogCurve {

    // ===== 表一：档位阈值与四列系数（待调手感值）=====

    /** 档位 1~4 的 COG 下界（升序）；低于首项即档位 0 */
    private static final float[] TIER_MIN_COG = {50f, 100f, 150f, 200f};

    /** 环境扣除系数（待调手感值）：档位 0~4，作用于规则自述的原始扣量 */
    private static final float[] ENV_DEDUCTION = {1.00f, 0.60f, 0.40f, 0.30f, 0.20f};

    /** SANC 效力（待调手感值）：档位 0~4，作用于理智上限类增减的施加比例 */
    private static final float[] SANC_EFFICIENCY = {1.00f, 0.90f, 0.80f, 0.70f, 0.60f};

    /** 食补效力（待调手感值）：档位 0~4，作用于食补档位的补量 */
    private static final float[] FOOD_EFFICIENCY = {1.00f, 1.00f, 0.80f, 0.70f, 0.60f};

    /** 自然恢复倍率（待调手感值）：档位 0~4，作用于理智自然回复速率（内容在后续段落） */
    private static final float[] NATURAL_REGEN = {1.0f, 1.0f, 1.0f, 1.5f, 2.0f};

    // ===== 表二：精神伤害减免（待调手感值）=====

    /** 减免起始 COG：低于此值完全无减免 */
    private static final float PSYCHIC_MIN_COG = 100f;
    /** 减免封顶 COG：达到此值即取最大减免 */
    private static final float PSYCHIC_MAX_COG = 200f;
    /** 起始减免比例（COG = 100 时） */
    private static final float PSYCHIC_REDUCTION_AT_MIN = 0.20f;
    /** 最大减免比例（COG ≥ 200 时） */
    private static final float PSYCHIC_REDUCTION_MAX = 0.50f;

    private SanityCogCurve() {
    }

    /** 环境扣除系数：规则原始扣量 × 本系数 = 实际扣量 */
    public static float envDeductionFactor(float cog) {
        return ENV_DEDUCTION[tierOf(cog)];
    }

    /** SANC 效力：理智上限类增减的施加比例 */
    public static float sancEfficiency(float cog) {
        return SANC_EFFICIENCY[tierOf(cog)];
    }

    /** 食补效力：食补档位补量的施加比例 */
    public static float foodEfficiency(float cog) {
        return FOOD_EFFICIENCY[tierOf(cog)];
    }

    /** 自然恢复倍率：理智自然回复速率的倍率 */
    public static float naturalRegenMultiplier(float cog) {
        return NATURAL_REGEN[tierOf(cog)];
    }

    /**
     * 精神伤害减免比例（0~0.5）：对 {@code forbidden_hex:psychic} 伤害按此比例减伤。
     *
     * <p>COG &lt; 100 直接返回 0（不做插值，避免"低认知也有微量减免"这种读不出来的效果）。
     */
    public static float psychicDamageReduction(float cog) {
        if (!(cog >= PSYCHIC_MIN_COG)) {
            // NaN 也走这条：NaN 比较恒假，故用反向判断兜底
            return 0f;
        }
        if (cog >= PSYCHIC_MAX_COG) {
            return PSYCHIC_REDUCTION_MAX;
        }
        float progress = (cog - PSYCHIC_MIN_COG) / (PSYCHIC_MAX_COG - PSYCHIC_MIN_COG);
        return PSYCHIC_REDUCTION_AT_MIN + progress * (PSYCHIC_REDUCTION_MAX - PSYCHIC_REDUCTION_AT_MIN);
    }

    /** COG → 档位下标（0~4）；NaN / 负值一律归入档位 0（不做惩罚性外推） */
    private static int tierOf(float cog) {
        int tier = 0;
        for (float threshold : TIER_MIN_COG) {
            if (cog >= threshold) {
                tier++;
            } else {
                break;
            }
        }
        return tier;
    }
}
