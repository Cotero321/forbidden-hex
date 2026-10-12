package com.example.akaishi.forbidden.magic.cast;

import com.example.akaishi.forbidden.api.sanity.SanityServices;
import com.example.akaishi.forbidden.magic.MagicState;
import com.example.akaishi.forbidden.magic.MagicStates;
import com.example.akaishi.forbidden.magic.table.MagicSource;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 服务端主线程支付：先检查全部通道，成功后才写入；异常回滚原余额。 */
public final class MagicPayment {
    private static final Logger LOGGER = LoggerFactory.getLogger(MagicPayment.class);
    private MagicPayment() {}

    public static PaymentReceipt pay(Player player, MagicSource source, float cost) {
        try {
            MagicCarrierHook hook = MagicPayHooks.get();
            return pay(player, source, cost, hook == null ? null : hook.paymentAccount(player, source));
        } catch (RuntimeException e) {
            LOGGER.error("[AkaishiMagic] 支付账户获取失败", e);
            return null;
        }
    }

    public static PaymentReceipt pay(Player player, MagicSource source, float cost,
            PaymentReceipt.CarrierAccount account) {
        if (player == null || player.level().isClientSide || !Float.isFinite(cost) || cost < 0f) return null;
        synchronized (player) {
            MagicState state = MagicStates.state(player);
            if (state == null || !player.isAlive()) return null;
            int index = MagicStates.poolIndexOf(source);
            float extraBefore = 0f;
            float poolBefore = index >= 0 ? state.pool(index) : 0f;
            float sanBefore = 0f;
            float healthBefore = player.getHealth();
            boolean extraTouched = false, poolTouched = false, sanTouched = false, healthTouched = false;
            try {
                extraBefore = account == null ? 0f : Math.max(0f, account.available());
                PaymentPlan plan = PaymentPlan.calculate(source, cost, extraBefore);
                float extra = plan.extra(), main = plan.main(), san = plan.sanity(), hp = plan.health();
                if (!Float.isFinite(extraBefore) || (index >= 0 && (!Float.isFinite(poolBefore) || poolBefore < main))
                        || !Float.isFinite(healthBefore) || healthBefore < hp) return null;
                if (san > 0f) {
                    if (!SanityServices.isAvailable()) return null;
                    sanBefore = SanityServices.get().getSan(player);
                    if (!Float.isFinite(sanBefore) || sanBefore < san) return null;
                }
                if (san > 0f) {
                    sanTouched = true;
                    SanityServices.get().setSan(player, sanBefore - san);
                    if (Math.abs(SanityServices.get().getSan(player) - (sanBefore - san)) > 0.0001f)
                        throw new IllegalStateException("SAN payment did not commit");
                }
                if (index >= 0 && main > 0f) {
                    poolTouched = true;
                    state.setPool(index, poolBefore - main);
                }
                if (extra > 0f) {
                    extraTouched = true;
                    account.setBalance(extraBefore - extra);
                    if (Math.abs(account.available() - (extraBefore - extra)) > 0.0001f)
                        throw new IllegalStateException("Extra payment did not commit");
                }
                if (hp > 0f) {
                    // 生命代价不是受击，不经过 hurt 的护甲、吸收和无敌帧。
                    healthTouched = true;
                    player.setHealth(healthBefore - hp);
                    if (Math.abs(player.getHealth() - (healthBefore - hp)) > 0.0001f)
                        throw new IllegalStateException("Health payment did not commit");
                }
                return new PaymentReceipt(source, account, extra, main, san, hp);
            } catch (RuntimeException e) {
                LOGGER.error("[AkaishiMagic] 支付失败，回滚玩家={} 源={}", player.getUUID(), source, e);
                if (healthTouched && player.isAlive() && !player.isRemoved()) {
                    try { player.setHealth(healthBefore); }
                    catch (RuntimeException rollback) { LOGGER.error("[AkaishiMagic] 生命回滚失败", rollback); }
                }
                if (extraTouched) {
                    try { account.setBalance(extraBefore); }
                    catch (RuntimeException rollback) { LOGGER.error("[AkaishiMagic] 载体回滚失败", rollback); }
                }
                if (poolTouched) {
                    try { state.setPool(index, poolBefore); }
                    catch (RuntimeException rollback) { LOGGER.error("[AkaishiMagic] 主池回滚失败", rollback); }
                }
                if (sanTouched) {
                    try { SanityServices.get().setSan(player, sanBefore); }
                    catch (RuntimeException rollback) { LOGGER.error("[AkaishiMagic] 理智回滚失败", rollback); }
                }
                return null;
            }
        }
    }

    public static void refund(Player player, PaymentReceipt receipt, float ratio) {
        if (player == null || receipt == null || !Float.isFinite(ratio) || !receipt.claimRefund()) return;
        float fraction = Math.max(0f, Math.min(1f, ratio));
        synchronized (player) {
            if (receipt.account() != null && receipt.extraPaid() > 0f)
                receipt.account().setBalance(receipt.account().available() + receipt.extraPaid() * fraction);
            MagicState state = MagicStates.state(player);
            int index = MagicStates.poolIndexOf(receipt.source());
            if (state != null && index >= 0)
                state.setPool(index, state.pool(index) + receipt.mainPaid() * fraction);
            if (receipt.sanityPaid() > 0f)
                SanityServices.get().addSan(player, receipt.sanityPaid() * fraction);
            if (receipt.healthPaid() > 0f && player.isAlive() && !player.isRemoved())
                player.setHealth(PaymentPlan.refundableHealth(player.getHealth(), player.getMaxHealth(),
                        receipt.healthPaid(), fraction, player.isAlive(), player.isRemoved()));
        }
    }
}
