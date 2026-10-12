package com.example.akaishi.forbidden.magic.cast;

import com.example.akaishi.forbidden.magic.table.MagicSource;
import net.minecraft.world.entity.player.Player;

/**
 * 载体（法杖）挂在施法链与结算节拍上的<b>常驻钩子</b>。
 *
 * <p><b>与 {@link CastAdjustments} 的分工</b>：那是"一次施法"的临时参数；这是
 * "只要载体在场就持续生效"的行为 —— 额外池先扣、循柄返还、主池恢复修正（永动 / 蓄）与散热修正（寂）。
 *
 * <p><b>注入</b>：载体模块启动时 {@link MagicPayHooks#install}；未安装时 {@code get()} 为 null，
 * 所有调用点走原版语义（魔法侧单装照常工作）。
 */
public interface MagicCarrierHook {

    /**
     * 逐段扣费前的<b>额外池先扣</b>（定案 3.3）：从该玩家在施的载体额外池扣，返回实付量（≤ amount）。
     * 没有载体在施 / 余额不足时返回能付的部分（可为 0）。
     */
    float payFromExtra(Player player, MagicSource source, float amount);

    default PaymentReceipt.CarrierAccount paymentAccount(Player player, MagicSource source) {
        return null;
    }

    default void onCastEnded(Player player, java.util.List<PaymentReceipt> successfulPayments,
            PaymentReceipt firstPayment, boolean completed, float cooldownSeconds) {
        onCastEnded(player, (float) successfulPayments.stream().mapToDouble(PaymentReceipt::totalPaid).sum(),
                completed, cooldownSeconds);
    }

    /**
     * 一次施法结束（正常完成或被打断）。
     *
     * @param totalPaid       本次施法已实付总量（首段 + 已结算的持续段）
     * @param completed       true = 自然完成（循柄的"施法后返还"只认它）
     * @param cooldownSeconds 本次冷却总时长（自然完成时 &gt; 0；打断为 0）——
     *                        载体据此打<b>原版物品冷却</b>（客户端据此在 HUD 环内算"实际剩余秒数"）
     */
    void onCastEnded(Player player, float totalPaid, boolean completed, float cooldownSeconds);

    /** 主池自然恢复的乘子（永动核：额外池耗尽时 ×2；含 MAIN_REGEN 词条） */
    float mainRegenMultiplier(Player player);

    /** 主池已满时的溢流入额外池（蓄杖柄；返回实际存入量） */
    float overflowToExtra(Player player, MagicSource source, float amount);

    /** 自然散热乘子（寂杖端：过热热值消散 +50%） */
    float heatDissipationMultiplier(Player player);
}
