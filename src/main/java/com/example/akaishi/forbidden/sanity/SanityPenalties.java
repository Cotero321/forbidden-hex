package com.example.akaishi.forbidden.sanity;

import com.example.akaishi.config.ModConfig;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * 阈值惩罚的<b>档位判定与数值表唯一口径</b>（纯函数、无状态、线程安全）。
 *
 * <p><b>档位怎么定义</b>：档位是 5 个百分比分段，下标越大越差：
 * <pre>
 * 下标  档位    触发区间（基础百分比 percentBase）
 * 0     正常    percent &gt; 80
 * 1     80%     60 &lt; percent ≤ 80
 * 2     60%     40 &lt; percent ≤ 60
 * 3     40%     20 &lt; percent ≤ 40
 * 4     20%     0  &lt; percent ≤ 20
 * 5     0%      percent == 0
 * </pre>
 * 「某档的惩罚只在对应档位及以上生效」= 只按下标取<b>最深</b>命中档的一套值（不连乘）：
 * 例如 0% 档不会同时吃 90%/80%/50% 三次护甲折扣 —— 连乘会把数值放大到读不出单项效果。
 *
 * <p><b>为什么档位基准用 SAN/SANC 而不是硬上限</b>（与 {@code SanityThresholds} 的边沿判定基准不同）：
 * 阈值削减账本会改 {@code tempCut}，而 {@code tempCut} 又是硬上限的一部分 ——
 * 若档位按「硬上限百分比」算，就会出现<b>回环</b>：进入 60% 档 ⇒ 削 10 ⇒ 硬上限变小 ⇒
 * 百分比回升到 64.7% ⇒ 判定"离开 60% 档" ⇒ 取消这一份 ⇒ 百分比又跌回 57.9% ⇒
 * 再次进入 …… 每 tick 来回震荡（这正是"判定风暴"的成因）。
 * 用不含 {@code tempCut} 的 {@code SAN/SANC} 作档位基准后，档位是 {@code (SAN, SANC)} 的纯函数，
 * 与削减本身无关，回环从根上消失（玩家的体感百分比也恰好是 SAN/SANC —— HUD 的填充长度就是它）。
 * {@code SanityThresholds} 的边沿派发仍然照旧消费，只作为"立刻建账/立刻取消"的触发器，
 * 且每次都被本类的基准复核一次（详见 {@code SanityBuiltinThresholdCuts}）。
 *
 * <p>所有数值均为<b>待调手感值</b>（首版按用户口径落地，未做平衡验证）。
 */
public final class SanityPenalties {

    // ===== 档位下标常量（可读性：调用方不要写裸数字）=====

    public static final int TIER_NORMAL = 0;
    public static final int TIER_80 = 1;
    public static final int TIER_60 = 2;
    public static final int TIER_40 = 3;
    public static final int TIER_20 = 4;
    public static final int TIER_0 = 5;

    // ===== 攻击效能（玩家造成的伤害倍率；待调手感值）=====

    /** 80% 档攻击倍率（90% 攻击效能） */
    public static final float ATTACK_MULT_80 = 0.9f;
    /** 60% 档及以下攻击倍率（80% 攻击效能） */
    public static final float ATTACK_MULT_60 = 0.8f;

    // ===== 护甲效能（"护甲提供的那部分减伤只有 e 有效"；待调手感值）=====

    /** 80% 档护甲效能 */
    public static final float ARMOR_EFF_80 = 0.90f;
    /** 60% 档护甲效能 */
    public static final float ARMOR_EFF_60 = 0.80f;
    /**
     * 0% 档护甲效能。
     *
     * <p><b>本期实际不可达</b>：0% 档同时有"受到伤害全部转为精神伤害"，而精神伤害不吃护甲，
     * 严格强于"护甲 50% 生效"。常量保留以便单独调整/关闭精神化时回退，代码里不做二次折扣。
     */
    public static final float ARMOR_EFF_0 = 0.50f;

    // ===== 40% 档易伤（待调手感值）=====

    /** 40% 档及以下的受到伤害倍率（+20% 易伤） */
    public static final float VULNERABLE_MULT_40 = 1.2f;

    // ===== 80% 档饱食度消耗（待调手感值）=====

    /** 80% 档及以下的饱食度消耗倍率（+100%，即消耗翻倍） */
    public static final float EXHAUSTION_MULT_80 = 2.0f;

    // ===== 60% 档周期减益（待调手感值）=====

    /** 周期节流：每 200 tick（10s）掷一次 */
    public static final int DEBUFF_PERIOD_TICKS = 200;
    /** 单周期触发概率：30% 命中一个随机减益 */
    public static final float DEBUFF_CHANCE = 0.30f;
    /** 减益持续时长：200 tick（10s）、I 级 */
    public static final int DEBUFF_DURATION_TICKS = 200;

    // ===== 20% 档（待调手感值）=====

    /** 进食理智收益作废概率：35% */
    public static final float FOOD_FAIL_CHANCE = 0.35f;
    /** 挖掘疲劳刷新时长：60 tick（3s，随结算每秒刷新） */
    public static final int DIG_SLOW_TICKS = 60;
    /** 攻击速度修饰符 UUID（固定，幂等去重的唯一依据） */
    public static final UUID ATTACK_SPEED_UUID = UUID.fromString("0a1b2c3d-3001-4000-8000-000000000001");
    /** 攻击速度修饰符数值：-25% 攻速（MULTIPLY_TOTAL） */
    public static final double ATTACK_SPEED_AMOUNT = -0.25d;
    /** 攻击速度修饰符名（仅用于调试显示） */
    public static final String ATTACK_SPEED_NAME = "akaishi_sanity_low";

    // ===== 0% 档（待调手感值）=====

    /** 0% 档施加的不可名状刷新时长：100 tick（5s，随结算每秒刷新；恢复后自然在 ≤5s 内过期） */
    public static final int UNNAMEABLE_TICKS = 100;
    /** 0% 档骷髅攻击附带的凋零时长/等级 */
    public static final int SKELETON_WITHER_TICKS = 200;
    public static final int SKELETON_WITHER_AMPLIFIER = 0;

    // ===== 影怪（P4；待调手感值）=====

    /**
     * 影怪在 40% 档的伤害倍率（基准 1.0）。
     * <p>只影响影怪的近战/远程伤害，与玩家的攻击效能倍率无关；消费点见
     * {@code com.example.akaishi.forbidden.sanity.shadow.ShadowCombat}。
     */
    public static final float SHADOW_DAMAGE_MULT_40 = 1.0f;
    /** 影怪在 20% 档的伤害倍率（"影怪伤害更高"的落点） */
    public static final float SHADOW_DAMAGE_MULT_20 = 1.5f;

    /**
     * 影怪在 20% 档的出手间隔倍率（&lt; 1 = 更频繁，"影怪更频繁"的落点）。
     * <p>近战 40t → 24t（2s → 1.2s）、远程 60t → 36t（3s → 1.8s）。
     */
    public static final float SHADOW_INTERVAL_MULT_20 = 0.6f;

    // ===== 恢复语义（待调手感值）=====

    /**
     * 「回到 20% 以上后 5 秒移除不可名状」的落地时延。
     *
     * <p>实现口径：0% 档期间每秒以 {@link #UNNAMEABLE_TICKS}（= 5s）刷新不可名状；
     * 一旦升回 20% 以上就<b>停止刷新</b>，效果自然在 ≤5s 内过期 ——
     * 等价于"周期性检查、5 秒后移除、不是立即"，且不会与外部更长的施加（BOSS 的 30s）打架。
     */
    public static final int UNNAMEABLE_RELEASE_DELAY_TICKS = 100;

    private SanityPenalties() {
    }

    // ------------------------------------------------------------------
    // P4 扩展点清单（2026-09-21 更新：①②③④ 已落地，⑤⑥ 仍跳过）
    // ------------------------------------------------------------------
    // ① 40% 档"视野中开始出现影怪"        → 已落地：SanityPenaltySettlement.applyLowTierPenalties
    //                                        → shadow.ShadowSpawner.settle/dissipateAll（档位门 + 上限 + 掷点 + 不怼脸）
    // ② 20% 档"影怪伤害更高更频繁"        → 已落地：本类 SHADOW_DAMAGE_MULT_20 / SHADOW_INTERVAL_MULT_20
    //                                        + shadowRangedUnlocked（20% 档起解锁远程精神弹）
    // ③ 0%  档"幻翼自杀式袭击"            → 已落地：SanityPhantomDive（本地标记、强制目标 + 俯冲助推 + 撞击自毁，
    //                                        不改 Phantom 类、不加 mixin，明确边界见该类注释）
    // ④ 0%  档"击杀影怪恢复"              → 已落地：SanityKillReward.isShadowMob 认 forbidden_hex:shadow
    //                                        （SAN +5、账本 relieve(5)，不旁路写真值）
    // ⑤ 40% 档"采下的花变成未知花朵"      → 用户已决定跳过（项目内无该物品，也不新建）
    // ⑥ "中立生物开始对你攻击"            → 本期跳过（见交接报告；需先定"激怒是永久还是随档位恢复"）

    // ------------------------------------------------------------------
    // 档位判定
    // ------------------------------------------------------------------

    /** 基础百分比 = SAN / SANC × 100（不含 tempCut，见类注释；SANC ≤ 0 时按 0） */
    public static float percentBase(SanityState state) {
        if (state == null) {
            return 0f;
        }
        return SanityThresholds.percentOf(state.san(), state.sanc());
    }

    /** 百分比 → 档位下标（0~5） */
    public static int tierOfPercent(float percent) {
        if (percent > 80f) {
            return TIER_NORMAL;
        }
        if (percent > 60f) {
            return TIER_80;
        }
        if (percent > 40f) {
            return TIER_60;
        }
        if (percent > 20f) {
            return TIER_40;
        }
        if (percent > 0f) {
            return TIER_20;
        }
        return TIER_0;
    }

    /** 状态 → 档位下标；无状态时按"正常"（不惩罚） */
    public static int tierOf(SanityState state) {
        return state == null ? TIER_NORMAL : tierOfPercent(percentBase(state));
    }

    /**
     * 玩家 → 档位下标（仅服务端有效；总开关关闭、无 capability、客户端一律返回"正常"）。
     *
     * <p>调用点（伤害事件、食补、mixin）都靠这一个入口取档位，避免各写一份百分比换算。
     */
    public static int tierOf(Player player) {
        if (player == null || player.level() == null || player.level().isClientSide || !ModConfig.sanityEnabled) {
            return TIER_NORMAL;
        }
        return tierOf(SanityServiceImpl.state(player));
    }

    /** 该档位下玩家造成的伤害倍率 */
    public static float attackMultiplier(int tier) {
        if (tier >= TIER_60) {
            return ATTACK_MULT_60;
        }
        return tier >= TIER_80 ? ATTACK_MULT_80 : 1f;
    }

    /** 该档位下护甲的有效比例（1 = 原样） */
    public static float armorEffectiveness(int tier) {
        if (tier >= TIER_0) {
            return ARMOR_EFF_0;
        }
        if (tier >= TIER_60) {
            return ARMOR_EFF_60;
        }
        return tier >= TIER_80 ? ARMOR_EFF_80 : 1f;
    }

    /** 该档位下玩家受到伤害的倍率（40% 档及以下 +20% 易伤） */
    public static float incomingMultiplier(int tier) {
        return tier >= TIER_40 ? VULNERABLE_MULT_40 : 1f;
    }

    /** 该档位下饱食度消耗倍率（80% 档及以下翻倍） */
    public static float exhaustionMultiplier(int tier) {
        return tier >= TIER_80 ? EXHAUSTION_MULT_80 : 1f;
    }

    /** 该档位是否进入"精神化"（0% 档：受到的伤害全部按精神伤害结算） */
    public static boolean isLowSanPsychic(int tier) {
        return tier >= TIER_0;
    }

    // ------------------------------------------------------------------
    // 影怪（P4）的口径
    // ------------------------------------------------------------------

    /**
     * 影怪的伤害倍率：只有 20% 档（含 0% 档）会放大。
     * <p>档位由调用方从<b>目标玩家此刻</b>的 SAN 状态取得（{@link #tierOf(Player)}），
     * 因此已经存在的影怪会随玩家理智继续下坠而自然变强，不需要影怪自己记录"我属于哪一档"。
     */
    public static float shadowDamageMultiplier(int tier) {
        return tier >= TIER_20 ? SHADOW_DAMAGE_MULT_20 : SHADOW_DAMAGE_MULT_40;
    }

    /** 影怪的出手间隔倍率：20% 档更频繁（40% 档为 1 = 不折算） */
    public static float shadowIntervalMultiplier(int tier) {
        return tier >= TIER_20 ? SHADOW_INTERVAL_MULT_20 : 1f;
    }

    /** 该档位是否解锁影怪的<b>远程</b>手段（用户拍板：40% 档只近战，20% 档追加远程） */
    public static boolean shadowRangedUnlocked(int tier) {
        return tier >= TIER_20;
    }
}
