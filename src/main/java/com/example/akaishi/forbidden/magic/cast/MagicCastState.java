package com.example.akaishi.forbidden.magic.cast;

/**
 * 施法状态机的状态（设计记忆 §21.1）。
 *
 * <p><b>为什么只有四态，而 §21.1 表里有六行</b>：
 * <ul>
 *   <li>{@code IDLE} —— "<b>没有会话</b>"就是空闲（会话被移除），故不需要真存一个字段；</li>
 *   <li>{@code COOLDOWN} —— §21.1 明写<b>可与 IDLE 并存</b>，它只是一个时间戳
 *       （在 {@code MagicState#cooldownUntil}），不是"进行中的施法"，故不入状态机；</li>
 *   <li>{@code OVERLOAD} —— 同为 {@code MagicState} 上的时间戳（{@code overloadUntil}），
 *       由 {@code MagicHeatSettlement} 推进，也不阻塞状态本身的表达。</li>
 * </ul>
 * 把这三种"外部条件"从状态机里剔出去，状态机就只剩<b>真正互斥的三态</b>，
 * 流转图不必再画"任意态 → 过载"这种会污染其余分支的边。
 *
 * <p>三者仍是施法入口的<b>并列闸门</b>：冷却中 / 过载中 / 已有会话，任一成立都拒绝新施法
 * （见 {@code MagicServiceImpl#decide} 的 ⑤）。
 */
public enum MagicCastState {

    /** 空闲（= 无会话；仅作日志 / 界面显示用，不落库） */
    IDLE,

    /** 前摇：释放时间内，尚未生效 */
    CHARGING,

    /** 引导中（X3）：持续段，每秒结算一段，可被打断 */
    CHANNELING,

    /** 连发中（X7）：每 0.1s 一发，可被打断 */
    VOLLEYING;

    /** 是否为"进行中"（占着单施法槽，拒绝新施法） */
    public boolean isActive() {
        return this != IDLE;
    }
}
