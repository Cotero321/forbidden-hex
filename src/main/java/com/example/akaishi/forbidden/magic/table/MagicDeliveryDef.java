package com.example.akaishi.forbidden.magic.table;

/**
 * 交付方式 X 的静态定义（一行 = 一种 X）。
 *
 * <p>对应设计记忆 §19.3（玩家动作 / 时序 / 反馈点 / 手感画像）与 §21.2（修正系数表）。
 *
 * <p><b>为什么 X 是"手感"的真正来源</b>：同一 {@link MagicEffectDef} 配不同 X，
 * 玩法就是两回事 —— 直射只要点一下，引导要你按住并承担被打断的风险。
 * 这是数值调不出来的体验差异（§19.3 关键判据），也是本系统"词数多"的正当性所在（§19.4）。
 *
 * <p><b>X 只改「怎么送」的四项：消耗 / 冷却 / 释放时间 / 距离 —— 不改威力、持续、半径。</b>
 * 这条是从 §21.2 的列定义抄正过来的（该表只有上述四列）。
 * {@link #costMul} 必须与 {@link #pricing} 一起读：引导的 ×0.30 是<b>每秒</b>价、
 * 连发的 ×0.40 是<b>每发</b>价，拆开单看会把它们误读成"便宜十倍"。
 *
 * @param costMul    消耗修正（与 §8.5 连乘）
 * @param pricing    <b>计价方式</b>：一次性按次、引导按秒、连发按发 —— 决定总消耗怎么结算
 * @param cooldownMul 冷却修正（乘在墨冷却 × 规模修正之上）
 * @param castTimeMul 释放时间修正（= 前摇）
 * @param rangeMul    距离修正（规模修正<b>不放大距离</b>，§8.4，故只乘墨基准）
 * @param continuous 是否<b>持续型</b>（X3 引导 / X7 连发）
 *                   —— ⚠ <b>当前无消费方</b>：它的两个用途（§21.6 混合扣费「预扣首段 + 逐 tick 结算」
 *                   与 §24.1 持续段加热「暂停散热 +2/秒」）都依赖 P5 的施法状态机。
 *                   本列作为<b>表的既有事实</b>保留，P5 落地时消费；**尚未实现，不是"已生效"**。
 * @param rule       附加规则（空串 = 无）
 */
public record MagicDeliveryDef(
        String id,
        String label,
        String playerAction,
        String timing,
        String feedback,
        String feel,
        float costMul,
        Pricing pricing,
        float cooldownMul,
        float castTimeMul,
        float rangeMul,
        boolean continuous,
        String rule) {

    /** 计价方式 —— 决定"总消耗"是乘一次、乘秒数还是乘发数 */
    public enum Pricing {
        /** 单次：总消耗 = 基准 × 系数 */
        PER_CAST,
        /** 每秒：总消耗 = 基准 × 系数 × 持续秒数（运行时） */
        PER_SECOND,
        /** 每发：总消耗 = 基准 × 系数 × 发数（运行时） */
        PER_SHOT
    }
}
