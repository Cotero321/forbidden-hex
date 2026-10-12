package com.example.akaishi.forbidden.boss.agaitolos.skill;

import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosEntity;
import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosPhase;
import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosPsychic;
import net.minecraft.world.entity.LivingEntity;

/**
 * 阶段三技能「天魔＊灾」的<b>施放面</b>（设计文档 §0 阶段三 / §1 定案表第 31 条）。
 * <p>
 * 规格原文：「天魔＊灾：BOSS 对玩家施加不可名状效果，期间 BOSS 的伤害类型替换为精神伤害，
 * 持续 30s；<b>此技能结束后伤害类型不变</b>」。
 * <p>
 * <b>本类只做两件事</b>：起手条件 + 在 clip 的落点把球踢给 {@link AgaitolosPsychic}
 * （"谁被污染 / 窗口多长 / 何时转永久 / 怎么换壳"全部收在那一类里，本类不复制任何一条口径）。
 * 计时与"这一招演完了没"归 {@code AgaitolosPhaseThreeState}（状态归实体侧的既有分工）。
 * <p>
 * <b>与 clip 的节拍对齐</b>：{@code calamity_cast} 2.2s = 44 tick（{@link #CAST_TICKS}），
 * 改写落点取 clip 的 0.5s = 10 tick（{@link #APPLY_TICK}）—— 抬手即生效：
 * 这样<b>同一招后续造成的伤害就已经是精神伤害</b>，不需要等整个施法演完，读起来是"它盯上你了"。
 * <p>
 * <b>为什么 AOE 半径不单列</b>：规格写的是"对玩家施加"，落点是当时 BOSS 锁定的目标一人；
 * 多玩家同场时每个玩家各吃一次（谁被打到谁就被改），不做"全场一起污染"这种规格没有的扩写。
 */
public final class AgaitolosCalamitySkill {

    // ---------------------------------------------------------------- 手感常量（待调手感值 / P8 转配置项）

    /** 施法整段时长（tick）：44 = 2.2s，<b>与 {@code calamity_cast} clip 等长</b> */
    public static final int CAST_TICKS = 44;

    /** 改写落点（tick，相对起手）：10 = clip 的 0.5s（抬手那一刻） */
    public static final int APPLY_TICK = 10;

    /**
     * 冷却（tick）：600 = 30s。
     * <p>与改写窗口等长是<b>刻意</b>的：窗口本身 30s，冷却也 30s ⇒ 同一名玩家不会被同一只 BOSS
     * 连续刷两次"不可名状"（第二次必然落在前一次窗口之后，此时已经是永久态，无需再刷）。
     * 阶段三经 {@code AgaitolosPace} 折算后 ≈ 18s（可以比窗口更短，因为对已永久的人再刷没有副作用）。
     */
    public static final int COOLDOWN_TICKS = 600;

    /** 施法距离（格）：24 —— 与远程攻击同一把尺子（它是"隔空施法"，不是贴身技）。待调手感值 / P8 转配置项 */
    public static final double RANGE = AgaitolosSkullSkill.SKULL_ATTACK_RADIUS;

    private AgaitolosCalamitySkill() {
    }

    /**
     * 本招此刻是否可起手（阶段三 + 目标在 {@link #RANGE} 内且存活）。
     * <p>不限定飞行/地面：规格没有对空间状态作要求（这一招是"隔空锁定"）。
     * <p>决策层权重表与实体侧执行入口复校<b>共用本方法</b>。
     */
    public static boolean canCast(AgaitolosEntity boss, LivingEntity target) {
        if (target == null || !target.isAlive()) {
            return false;
        }
        if (boss.getPhase().combatOrdinal() < AgaitolosPhase.PHASE_3.combatOrdinal()) {
            return false;
        }
        return boss.distanceTo(target) <= RANGE;
    }

    /**
     * 落地改写（仅服务端；由状态机在起手后 {@link #APPLY_TICK} tick 调用一次）。
     *
     * @return 是否真的落下（目标已死 / 非玩家 / 无玩家侧持久载体时为 false —— 由 {@link AgaitolosPsychic} 判定）
     */
    public static boolean apply(AgaitolosEntity boss, LivingEntity target) {
        if (boss.level().isClientSide() || target == null || !target.isAlive()) {
            return false;
        }
        return AgaitolosPsychic.applyCalamity(boss, target);
    }
}
