package com.example.akaishi.forbidden.magic.cast;

/**
 * 施法被打断的原因（设计记忆 §21.1「终止源清单」）。
 *
 * <p><b>为什么要分类而不是只记"打断了"</b>：§21.1 对"是否返还"的裁定完全取决于原因 ——
 * 自然放完<b>不退</b>（引导时长本就是玩家自己选的），被动打断<b>退 50%</b>。
 * 若不分因，返还与否就只能拍脑袋，必然与设计漂移。
 */
public enum MagicCastInterrupt {

    /** 正常放完（一次性投送完成 / X3 松手 / X7 发完） */
    NATURAL(false),

    /** 超出上限时长（引导 6s / 连发 10 发）—— 属于自然终止，不返还 */
    OVER_LIMIT(false),

    /** 能量 / 理智不足（逐段结算时扣不出）—— 自然终止，不返还 */
    INSUFFICIENT(false),

    /** 施法者死亡 */
    DEATH(true),

    /** 换维度 / 退出游戏 */
    LOGOUT(true),

    /** 受击打断（单次受到 ≥ 阈值伤害） */
    HIT(true);

    private final boolean refunds;

    MagicCastInterrupt(boolean refunds) {
        this.refunds = refunds;
    }

    /** 本次打断是否返还"未消耗部分"的 50%（§21.6 软口径） */
    public boolean refunds() {
        return refunds;
    }
}
