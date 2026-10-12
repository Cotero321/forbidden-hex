package com.example.akaishi.forbidden.sanity.content;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.api.sanity.ISanityThresholdHook;
import com.example.akaishi.forbidden.api.sanity.SanityThresholdRegistry;
import com.example.akaishi.config.ModConfig;
import com.example.akaishi.forbidden.sanity.SanityCogCurve;
import com.example.akaishi.forbidden.sanity.SanityCutLedger;
import com.example.akaishi.forbidden.sanity.SanityPenalties;
import com.example.akaishi.forbidden.sanity.SanityServiceImpl;
import com.example.akaishi.forbidden.sanity.SanityState;
import com.example.akaishi.forbidden.sanity.SanityThresholds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * 阈值惩罚的「上限削减」内容：<b>内置内容同样走对外 API</b>（{@link SanityThresholdRegistry}）注册。
 *
 * <p><b>职责边界</b>：本类只做一件事——把"跨档"翻译成账本上的<b>建账/取消</b>
 * （{@link SanityCutLedger}）。<b>它不写任何理智数值</b>：
 * <ul>
 *   <li>{@code tempCut} 的落地在每秒的惩罚结算（{@code SanityPenaltySettlement}），
 *       不在阈值回调里 —— API 明文禁止在回调里写理智值，因为 {@code applyTempCut}
 *       自身会再触发一次 {@link SanityThresholds#evaluate}，在回调里写就会形成
 *       "回调 → 写值 → 再派发回调 → 再写值"的递归/风暴；</li>
 *   <li>回调里只做 O(1) 的表更新，且每次都用<b>基础百分比</b>（SAN/SANC，不含 tempCut）复核：
 *       削减本身会改变硬上限百分比，由此产生的"伪跨档"被复核挡掉，账本恒等于基础百分比的纯函数
 *       （回环成因见 {@link SanityPenalties} 类注释）。</li>
 * </ul>
 *
 * <p><b>与每秒结算的分工</b>：回调负责"立刻建账/立刻取消"（响应快），
 * {@link #reconcile} 负责"补齐/过期/离开"（权威）——两条路径改的是同一张表、
 * 用的是同一个判据，因此重复调用是幂等的，不会互相打架。
 */
public final class SanityBuiltinThresholdCuts implements ISanityThresholdHook {

    /** 钩子 id（带本模组命名空间，符合 API 约定） */
    public static final ResourceLocation ID = new ResourceLocation(AkaishiMod.MOD_ID, "threshold_cut");

    private static final SanityBuiltinThresholdCuts INSTANCE = new SanityBuiltinThresholdCuts();

    private SanityBuiltinThresholdCuts() {
    }

    /** 注册进阈值钩子注册表（由 {@code AkaishiMod.init} 调用，与附属同权） */
    public static void register() {
        SanityThresholdRegistry.register(INSTANCE);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void onThreshold(Player player, float percentOfSanc, boolean entering) {
        if (player == null || !ModConfig.sanityEnabled) {
            return;
        }
        int index = SanityCutLedger.indexOfLevel(percentOfSanc);
        if (index < 0) {
            return; // 非核心档位（未来扩档或附属自定义）：不参与核心记账
        }
        SanityState state = SanityServiceImpl.state(player);
        if (state == null) {
            return;
        }
        float percent = SanityPenalties.percentBase(state);
        SanityCutLedger ledger = state.cutLedger();
        long now = player.level().getGameTime();
        if (entering) {
            // 只有"基础百分比确实落在该档"时才建账；否则这条边沿是削减自身推高百分比造成的伪跨档
            if (percent <= SanityThresholds.LEVELS[index]) {
                grant(ledger, state, index, now);
            }
        } else if (percent > SanityThresholds.LEVELS[index]) {
            // 只有"基础百分比确实离开了该档"才取消那一份（同理挡掉伪边沿）
            if (ledger.revoke(index)) {
                state.markDirty();
            }
            ledger.setCredited(index, false);
        }
    }

    /**
     * 每秒复核（由惩罚结算调用）：以基础百分比为准，补齐漏发的档、撤销已离开的档、清掉过期的档。
     *
     * <p>补齐用的是 {@code credited} 位掩码：<b>已发过一份且已到期的档不会被补发</b>，
     * 只有离开该档（掩码被清）后再回来才会重新发一份 —— 这正是"5 分钟后自动移除"能成立的前提。
     */
    public static void reconcile(SanityState state, long now) {
        if (state == null) {
            return;
        }
        SanityCutLedger ledger = state.cutLedger();
        float percent = SanityPenalties.percentBase(state);
        for (int i = 0; i < SanityThresholds.LEVELS.length; i++) {
            boolean desired = percent <= SanityThresholds.LEVELS[i];
            if (desired) {
                if (!ledger.credited(i)) {
                    grant(ledger, state, i, now);
                }
            } else if (ledger.credited(i)) {
                ledger.revoke(i);
                ledger.setCredited(i, false);
                state.markDirty();
            }
        }
        // 过期判定（唯一落点）：到期的档移除，credited 位保留 ⇒ 待在同档不会反复补发
        int before = ledger.creditedMask();
        float totalBefore = ledger.total();
        ledger.pruneExpired(now);
        if (before != ledger.creditedMask() || totalBefore != ledger.total()) {
            state.markDirty();
        }
    }

    /**
     * 建账/刷新一份：金额 = 档位基数 × SANC 效力（COG 越高削得越少），并刷新 5 分钟计时。
     *
     * <p><b>唯一的不建账分支</b>："这一档已经发过一份、且那份已经到期"（credited 为真但条目不在）。
     * 此时不补发 —— 否则"5 分钟后自动移除"会被每次复核立刻撤销，计时形同虚设。
     * 想再拿一份必须真的离开该档（离开时 credited 位被清）后再跌回来。
     */
    private static void grant(SanityCutLedger ledger, SanityState state, int index, long now) {
        if (ledger.credited(index) && !ledger.has(index)) {
            return;
        }
        float amount = SanityCutLedger.CUT_BASE[index] * SanityCogCurve.sancEfficiency(state.cog());
        if (!(amount > 0f)) {
            return;
        }
        ledger.grant(index, amount, now + SanityCutLedger.CUT_DURATION_TICKS);
        ledger.setCredited(index, true);
        state.markDirty();
    }
}
