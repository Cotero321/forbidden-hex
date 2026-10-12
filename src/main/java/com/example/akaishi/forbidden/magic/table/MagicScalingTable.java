package com.example.akaishi.forbidden.magic.table;

/**
 * <b>规模 / 阶位 / 衰减系数表</b>（设计记忆 §8.4 + §26.1 + §26.3）。
 *
 * <p>本类只放<b>系数与查表函数</b>；把这些系数<b>组合</b>成一次施法的完整结算属于
 * {@code MagicFormula}（P4）的职责 —— 分开是为了让本表可被单独自检（单调性、区间边界）
 * 而不需要跑整个公式。
 *
 * <p>三条口径必须先记住，否则读系数会读反：
 * <ol>
 *   <li>{@link #tierMultiplier 阶位强度系数}与{@link #sameSourceDecay 同源衰减}
 *       <b>只作用于输出轴</b>（威力 / 持续 / 半径），<b>不给消耗打折</b>（§8.4 v13 口径）。</li>
 *   <li>消耗与冷却的规模修正<b>不同形</b>：消耗线性、冷却<b>二次</b> —— 所以长句"更贵"远不如
 *       "更久不能再用"来得狠（§26.2 补正 ② 正是靠这一点推翻了"入热 ×4 能让长句爆热"的直觉）。</li>
 *   <li>阶位强度系数是<b>分段线性</b>而非四档阶跃，见 {@link #tierMultiplier} 的说明。</li>
 * </ol>
 */
public final class MagicScalingTable {

    /** 句内词数上限（= 四段各 2 词 · §5） */
    public static final int MAX_WORDS = 8;

    // ---- 消耗：线性 ----
    public static final float COST_LINEAR = 0.05f;
    public static final float COST_PER_OVERLAY_INK = 0.10f;

    // ---- 冷却：二次（长句真正的痛点是"不能常用"）----
    public static final float COOLDOWN_QUADRATIC = 0.06f;
    public static final float COOLDOWN_PER_OVERLAY_INK = 0.25f;

    // ---- 释放时间：线性 ----
    public static final float CAST_TIME_LINEAR = 0.15f;
    public static final float CAST_TIME_PER_OVERLAY_INK = 0.10f;

    // ---- 输出轴：威力 / 持续 / 半径 ----
    //
    // ★v20 变更：多词条威力共享从「加法叠加（各词 baseP 独立加入 powerAxis）」
    //   改为「百分比加成（修饰词按百分比加成核心词威力）」。
    //   - 原模式：powerAxis += baseP × wP × contribution ⇒ 每加一个 DAMAGE 词就叠一份 6 HP，
    //     修饰词按 0.25 权重也有 1.5 HP，多词法术伤害膨胀不可控。
    //   - 新模式：powerAxis 只取核心词的 baseP × wP；修饰词通过 POWER_BONUS_PER_WORD 给百分比加成。
    //     即「核心 6 HP + 每多一个伤害贡献词 +15%」⇒ 2 词 6.9、4 词 7.95… 增长平缓可控。
    //
    /** 每多一个威力轴贡献词，给核心威力加多少百分比（v20 百分比加成模式） */
    public static final float POWER_BONUS_PER_WORD = 0.20f;
    /** 覆盖墨给威力的百分比加成（每种） */
    public static final float POWER_PER_OVERLAY_INK = 0.08f;
    /** 持续轴仍沿用线性叠加（持续/半径轴不变，只有威力轴改为百分比加成） */
    public static final float DURATION_LINEAR = 0.08f;

    /** 同源衰减曲线（§26.3）：按 E 分组后，组内第 1/2/3/4+ 个词的倍率 */
    public static final float[] SAME_SOURCE_DECAY = { 1.0f, 0.7f, 0.4f, 0.1f };

    // ---- 词的角色权重（§8.5 的「角色权重」，v18 首次落地）----

    /** 核心词（§1 选出的那一个）的角色权重 —— 基准 */
    public static final float ROLE_WEIGHT_CORE = 1.00f;

    /**
     * <b>修饰词</b>的角色权重（§1「其余词为修饰」+ §5「首词为主，其余降修饰」）。
     *
     * <p>取值必须<b>显著低于</b> {@link #SECOND_CORE_MULTIPLIER}，否则 6~7 词阶的「双核心」
     * 相比低阶的"主从"就没有任何增益，那项能力等于不存在。
     * 取 0.25 ⇒「核心 + 1 修饰」= 1.25 倍单核；双核（1.00 + 0.50）为 1.50 倍 ⇒ 差距可见。
     */
    public static final float ROLE_WEIGHT_MODIFIER = 0.25f;

    /**
     * <b>第二核心</b>的输出倍率（§23.4 用户裁定 v17：50%）。
     * 只作用于输出轴；该词的消耗 / 冷却 / 释放 / 距离仍按全额计入。
     */
    public static final float SECOND_CORE_MULTIPLIER = 0.50f;

    /** 双核心的解锁词数下界（§23.4：6~7 词阶；8 词为"同上 + 质变"，故一并适用） */
    public static final int SECOND_CORE_MIN_WORDS = 6;

    /**
     * 阶位强度系数的<b>锚点</b>（N → m）。见设计记忆 §26.1。
     *
     * <p><b>为什么是分段线性而不是四档阶跃</b>：用户原始方案把 1~3 词定为 1.0、4~5 定 0.8、
     * 6~7 定 0.55、8 定 0.45。但阶跃会在<b>边界上产生悬崖</b> —— 验算输出 ∝ {@code N × 威力系数 × m}：
     * N=5 得 5.92、N=6 只有 5.28（<b>加一个词反而掉 11%</b>），玩家会算出"第 6 个词是负收益"。
     * 区间内线性插值后 m 单调不增，输出即单调不减。
     */
    private static final int[] TIER_ANCHOR_N = { 1, 3, 5, 7, 8 };
    private static final float[] TIER_ANCHOR_M = { 1.00f, 1.00f, 0.80f, 0.55f, 0.45f };

    private MagicScalingTable() {
    }

    /**
     * 阶位强度系数 {@code m(N)}（**只作用于输出轴**）。
     *
     * @param words 句内词数（&lt;1 按 1 计；&gt;8 按 8 计 —— 词数上限本由 §10 等级卡住，此处兜底不抛异常）
     */
    public static float tierMultiplier(int words) {
        int n = Math.max(1, Math.min(MAX_WORDS, words));
        for (int i = 1; i < TIER_ANCHOR_N.length; i++) {
            if (n <= TIER_ANCHOR_N[i]) {
                int n0 = TIER_ANCHOR_N[i - 1];
                int n1 = TIER_ANCHOR_N[i];
                float m0 = TIER_ANCHOR_M[i - 1];
                float m1 = TIER_ANCHOR_M[i];
                return m0 + (m1 - m0) * (float) (n - n0) / (float) (n1 - n0);
            }
        }
        return TIER_ANCHOR_M[TIER_ANCHOR_M.length - 1];
    }

    /**
     * 同源衰减：某 E 名下第 {@code ordinal} 个词（**从 0 起**）的倍率。
     * 第 4 个及以后一律取曲线末值（§26.3）。
     */
    public static float sameSourceDecay(int ordinal) {
        int i = Math.max(0, ordinal);
        return i < SAME_SOURCE_DECAY.length ? SAME_SOURCE_DECAY[i] : SAME_SOURCE_DECAY[SAME_SOURCE_DECAY.length - 1];
    }

    // ---- 规模修正（§8.4）。K = 逐词覆盖墨种数，见 §21.3 ----

    /** 消耗规模修正 */
    public static float costScale(int words, int overlayInks) {
        return (1f + COST_LINEAR * (words - 1)) * (1f + COST_PER_OVERLAY_INK * overlayInks);
    }

    /** 冷却规模修正（**二次**） */
    public static float cooldownScale(int words, int overlayInks) {
        int d = words - 1;
        return (1f + COOLDOWN_QUADRATIC * d * d) + COOLDOWN_PER_OVERLAY_INK * overlayInks;
    }

    /** 释放时间规模修正（返回<b>加数</b>之外的乘区部分；固定加秒由 §8.4 单列） */
    public static float castTimeScale(int words, int overlayInks) {
        return (1f + CAST_TIME_LINEAR * (words - 1)) + CAST_TIME_PER_OVERLAY_INK * overlayInks;
    }

    /**
     * 威力规模修正（v20 百分比加成模式）。
     *
     * <p>★v20 变更：从 {@code (1 + POWER_LINEAR × (N-1)) × (1 + POWER_PER_OVERLAY_INK × K)}
     * 改为 {@code (1 + POWER_BONUS_PER_WORD × (N-1)) × (1 + POWER_PER_OVERLAY_INK × K)}。
     * 语义从"每词独立叠 baseP 的加法模式"变为"修饰词按百分比加成核心威力"。
     *
     * <p>幂等性：powerScale 只管规模系数；公式层已不再把多个词的 baseP 叠入 powerAxis，
     * 而是只取核心词 baseP，再乘此规模修正 ⇒ 自然实现"百分比加成"。
     */
    public static float powerScale(int words, int overlayInks) {
        return (1f + POWER_BONUS_PER_WORD * (words - 1)) * (1f + POWER_PER_OVERLAY_INK * overlayInks);
    }

    /** 持续规模修正 */
    public static float durationScale(int words) {
        return 1f + DURATION_LINEAR * (words - 1);
    }
}
