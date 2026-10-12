package com.example.akaishi.forbidden.magic.table;

/**
 * 效果语义 E 的静态定义（一行 = 一条 E）。
 *
 * <p>对应设计记忆 §8.2（原语基准表的数值底稿）+ §19.5（拆合为 E）+ §19.9（数值挂点改挂 E）。
 *
 * <p><b>为什么不直接照抄 §8.2 的 20 原语</b>：那 20 条把「效果语义」与「交付方式」焊死在一格里
 * （1~4 号都是"造成伤害"，差别全在怎么送出去）。拆开后 {@code DAMAGE} 只需一行基准，
 * 交付差异由 {@link MagicDeliveryDef} 的修正系数承担 —— 这样"同一 E × 不同 X"才成为乘法关系。
 *
 * @param cost        基准消耗（池量；P=1.0 时）
 * @param baseP       P=1.0 时的量化数值（单位见 {@link Unit}）
 * @param unit        量化单位（决定 baseP 该被读成 HP 还是秒还是格）
 * @param quantumText 人类可读量化（手册 / 调试输出用，与 baseP 同义）
 * @param duration    D 基准（秒；0 = 瞬发）
 * @param radius      R 基准（格；0 = 单体 / 自身）
 * @param trend       强度随 P 的增长形态
 * @param rule        附加规则（空串 = 无）
 * @param category    攻防分类（法杖记忆 §11：崩解/壁垒核与源表按它分拣；判定对象 = 核心词的 E）
 */
public record MagicEffectDef(
        String id,
        String label,
        int cost,
        float baseP,
        Unit unit,
        String quantumText,
        float duration,
        float radius,
        Trend trend,
        String rule,
        Category category) {

    /**
     * 攻防三分类（法杖记忆 §11）：
     * <b>攻击</b> = 伤害 / 负面 / 控制；<b>防御</b> = 保护增益自身友方（含解除自身负面、隐匿自保）；
     * <b>中性</b> = 与敌我立场无关的效用（位移 / 信息 / 承载 / 时序 / 代价 / 召唤 / 场地等）。
     * 中性在崩解/壁垒下不吃惩罚也不吃加成。
     */
    public enum Category {
        /** 攻击：damage / break_shield / bind / weaken / mind / link */
        ATTACK,
        /** 防御：boost / ward / conceal / break_unlock */
        DEFENSE,
        /** 中性：其余 10 条 */
        NEUTRAL
    }

    /** 量化的物理单位 —— baseP 的数字含义由它决定 */
    public enum Unit {
        /** 生命值（伤害 / 治疗 / 吸收） */
        HP,
        /** 每秒生命值（持续伤害 / 传导） */
        HP_PER_SEC,
        /** 格（位移 / 传送距离） */
        CELLS,
        /** 秒（挣脱时长等） */
        SECONDS,
        /** 百分比（属性增减 / 复现比例） */
        PERCENT,
        /** 层数 / 个数 */
        COUNT,
        /** 档位（P 跨阈时换挡，非线性） */
        TIER,
        /** 反比系数（值 = 常数 ÷ P） */
        FACTOR,
        /** 池量 */
        POOL,
        /** 无数值量化（纯开关型） */
        NONE
    }

    /** 强度增长形态 */
    public enum Trend {
        /** 线性：效果 ∝ P */
        LINEAR,
        /** 档位：P 跨阈时跳档（阈值见 {@link #rule}） */
        TIERED
    }
}
