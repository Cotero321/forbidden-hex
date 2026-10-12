package com.example.akaishi.forbidden.sanity;

import com.example.akaishi.config.ModConfig;
import com.example.akaishi.forbidden.effect.AkaishiForbiddenEffects;
import com.example.akaishi.forbidden.sanity.content.SanityBuiltinThresholdCuts;
import com.example.akaishi.forbidden.sanity.shadow.ShadowSpawner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 阈值惩罚的<b>周期性落地</b>（每秒一次，与 {@link SanityEnvironmentSettlement} 同一节拍）。
 *
 * <p><b>为什么把"落地"单独放一层、而不写在阈值回调里</b>：
 * <ol>
 *   <li>API 明文禁止在阈值回调里写理智值（{@code tempCut} 也是理智值之一）——
 *       {@code SanityServiceImpl#setTempCut} 内部会再跑一次
 *       {@link SanityThresholds#evaluate}，在回调里写就形成"回调→写值→再派发回调"的递归；</li>
 *   <li>持续类惩罚（挖掘疲劳、不可名状、攻速修饰符）天然是"按节拍维持"的量，
 *       放回调里表达不了"离开档位后停手"；</li>
 *   <li>一次性跨档与持续维持分成两处后，两处的判据都是同一个纯函数
 *       （{@link SanityPenalties#tierOf(SanityState)} / {@link SanityPenalties#percentBase}），
 *       因此重复、乱序、漏拍都不会造出自相矛盾的状态。</li>
 * </ol>
 *
 * <p><b>不可逆惩罚呢</b>：本类不写 SAN/SANC/COG（伤害类惩罚走 forge 伤害事件、食补作废走食补入口），
 * 唯一写的是 {@code tempCut}（账本总量），且只在本节拍写一次 —— 一处写入、一条链路。
 */
public final class SanityPenaltySettlement {

    private static final Logger LOGGER = LogManager.getLogger("akaishi.sanity");

    /** 浮点比较容差（账本总量与 tempCut 都是累加出来的小数） */
    private static final float EPSILON = 1e-3f;

    private SanityPenaltySettlement() {
    }

    /** 服务端每个维度每 tick 调用（由 {@code AkaishiMod.init} 的 SERVER_LEVEL_POST 驱动） */
    public static void serverTick(ServerLevel level) {
        if (!ModConfig.sanityEnabled) {
            return; // 总开关关闭：整套惩罚不生效（含精神化标记的刷新）
        }
        long now = level.getGameTime();
        if (now % SanityEnvironmentSettlement.SETTLE_PERIOD_TICKS != 0) {
            return; // 节拍未到：本 tick 零成本
        }
        for (ServerPlayer player : level.players()) {
            try {
                settle(player, now);
            } catch (Throwable t) {
                // 单个玩家出错不得拖垮整个服务端 tick（错题隔离，日志留证）
                LOGGER.warn("[akaishi] 理智阈值惩罚结算异常（已隔离）: {}", player.getUUID(), t);
            }
        }
    }

    private static void settle(ServerPlayer player, long now) {
        SanityState state = SanityServiceImpl.state(player);
        if (state == null) {
            return;
        }
        // 1) 账本复核：补齐漏发档、撤销已离开档、清掉到期档（内部含 credited 位掩码防补发）
        SanityBuiltinThresholdCuts.reconcile(state, now);
        // 2) tempCut 落地 = 账本总量。只在"我们拥有这个值"或"总量非零"时写，
        //    避免凭空清掉第三方经 API 写入的 tempCut（账本为空且值不是我们写的 ⇒ 不动）
        float total = state.cutLedger().total();
        float applied = state.cutLedger().applied();
        boolean ours = Math.abs(state.tempCut() - applied) <= EPSILON;
        if (Math.abs(total - state.tempCut()) > EPSILON && (total > 0f || ours)) {
            SanityServiceImpl.instance().setTempCut(player, total);
        }
        state.cutLedger().setApplied(total);
        // 3) 持续类惩罚
        int tier = SanityPenalties.tierOf(state);
        applyPeriodicDebuff(player, tier, now);
        applyLowTierPenalties(player, tier);
        // 4) 精神化标记（0% 档的伤害换壳判据；读端在 forge 伤害事件）
        state.setLowSanPsychic(SanityPenalties.isLowSanPsychic(tier));
    }

    /** 60% 档及以下：每 {@code DEBUFF_PERIOD_TICKS} 按概率施加一个随机减益（中毒/缓慢/虚弱/凋零） */
    private static void applyPeriodicDebuff(ServerPlayer player, int tier, long now) {
        if (tier < SanityPenalties.TIER_60) {
            return;
        }
        if (now % SanityPenalties.DEBUFF_PERIOD_TICKS != 0) {
            return; // 用游戏刻对齐做节流：无需每玩家计时状态，也就不会泄漏
        }
        if (player.getRandom().nextFloat() >= SanityPenalties.DEBUFF_CHANCE) {
            return;
        }
        MobEffect[] pool = {MobEffects.POISON, MobEffects.MOVEMENT_SLOWDOWN, MobEffects.WEAKNESS, MobEffects.WITHER};
        MobEffect picked = pool[player.getRandom().nextInt(pool.length)];
        player.addEffect(new MobEffectInstance(picked, SanityPenalties.DEBUFF_DURATION_TICKS, 0, false, true));
    }

    /** 20% / 0% 档：挖掘疲劳、攻速减慢、（0% 档）持续维持不可名状；40% 档及以下：影怪（P4） */
    private static void applyLowTierPenalties(ServerPlayer player, int tier) {
        // 影怪（P4）：40% 档及以下"视野中出现"，升回 40% 以上即清扫存量。
        // 生成/清扫是表现层动作，与下面的持续效果互不影响，故放在最前、单独成段。
        try {
            if (tier >= SanityPenalties.TIER_40) {
                ShadowSpawner.settle(player, tier);
            } else {
                ShadowSpawner.dissipateAll(player);
            }
            // 0% 档：附近的幻翼转为"自杀式俯冲"（标记 + 逐 tick 助推在 SanityPhantomDive 内）
            if (SanityPenalties.isLowSanPsychic(tier)) {
                SanityPhantomDive.mark(player);
            }
        } catch (Throwable t) {
            // 表现层失败不得影响阈值惩罚主流程（错题隔离，日志留证）
            LOGGER.warn("[akaishi] 影怪/幻翼表现层结算异常（已隔离）: {}", player.getUUID(), t);
        }
        if (tier >= SanityPenalties.TIER_20) {
            // 挖掘疲劳：每秒以 3s 时长刷新，离开档位后自然在 ≤3s 内消退
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN,
                    SanityPenalties.DIG_SLOW_TICKS, 0, false, false));
            applyAttackSpeedSlow(player);
        } else {
            clearAttackSpeedSlow(player);
        }
        if (!SanityPenalties.isLowSanPsychic(tier)) {
            return;
        }
        // 不可名状：每秒以 5s 时长刷新。回到 20% 以上即停止刷新 ⇒ 效果在 ≤5s 内自然过期，
        // 即设计要求的"周期性检查、5 秒后移除、不是立即"；也不会缩短外部更长的施加（BOSS 的 30s）
        MobEffect unnameable = AkaishiForbiddenEffects.UNNAMEABLE == null ? null : AkaishiForbiddenEffects.UNNAMEABLE.get();
        if (unnameable != null) {
            player.addEffect(new MobEffectInstance(unnameable, SanityPenalties.UNNAMEABLE_TICKS, 0, false, false));
        }
    }

    /** 攻速减慢：固定 UUID 的瞬时修饰符（幂等——已存在就不重复叠加） */
    private static void applyAttackSpeedSlow(ServerPlayer player) {
        AttributeInstance instance = player.getAttribute(Attributes.ATTACK_SPEED);
        if (instance == null || instance.getModifier(SanityPenalties.ATTACK_SPEED_UUID) != null) {
            return;
        }
        instance.addTransientModifier(new AttributeModifier(SanityPenalties.ATTACK_SPEED_UUID,
                SanityPenalties.ATTACK_SPEED_NAME, SanityPenalties.ATTACK_SPEED_AMOUNT,
                AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    /** 回到 20% 以上：移除攻速修饰符（不残留、不靠自动过期） */
    private static void clearAttackSpeedSlow(ServerPlayer player) {
        AttributeInstance instance = player.getAttribute(Attributes.ATTACK_SPEED);
        if (instance != null && instance.getModifier(SanityPenalties.ATTACK_SPEED_UUID) != null) {
            instance.removeModifier(SanityPenalties.ATTACK_SPEED_UUID);
        }
    }
}
