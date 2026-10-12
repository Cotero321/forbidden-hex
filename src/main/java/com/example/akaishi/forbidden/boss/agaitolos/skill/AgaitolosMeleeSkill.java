package com.example.akaishi.forbidden.boss.agaitolos.skill;

import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosCombat;
import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosDoom;
import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosEntity;
import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosPsychic;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * 一阶段普通攻击：挥舞武器，造成凋零 III（1s）并追加 4 点真实伤害；
 * 若目标在命中<b>前</b>已带凋零，则真实伤害翻倍。
 * <p>
 * 结算顺序不可调换：先记录"是否已凋零"，再施加新凋零——否则新效果会先落地，判定恒为 true。
 * <p>
 * <b>阶段三（2026-09-21）</b>：减益本身改由 {@link AgaitolosDoom#applyWitherOrDoom} 施加
 * （阶段三 ⇒ 凋亡），故这里的"是否已带凋零"也必须换成 {@link AgaitolosDoom#isWitheredOrDoomed}，
 * 否则阶段三会凭空掉掉"带凋零则真伤双倍"这条规则。
 */
public final class AgaitolosMeleeSkill {

    /**
     * 普攻出手间隔（tick）：20 —— <b>与原版 {@code MeleeAttackGoal} 内部的 {@code attackInterval} 同值</b>。
     * <p>
     * <b>为什么这个常量必须存在</b>：原先进攻间隔完全由原版 Goal 自己持有；本轮普攻的出手闸收进
     * {@code AgaitolosSkillDirector} 与 {@code AgaitolosEntity#doHurtTarget}（决策层也会直接调它），
     * 若这里不显式给一份，"每 20 tick 一刀"这条既有手感就会在两个调用方叠加成两倍频率。
     * <p>由 {@code AgaitolosEntity#doHurtTarget} 消费，再经 {@code AgaitolosPace#scaledCooldown} 按阶段折算
     * ⇒ 二/三阶段的普攻更密，与"二阶段比一阶段更加快速"同向。待调手感值 / P8 转配置项
     */
    public static final int MELEE_INTERVAL_TICKS = 20;

    /** 凋零持续时长（tick）：1s。P8 转配置项 */
    public static final int WITHER_DURATION_TICKS = 20;

    /** 凋零等级放大器：2 = 凋零 III（amplifier 从 0 起算）。P8 转配置项 */
    public static final int WITHER_AMPLIFIER = 2;

    /** 真实伤害基础值。P8 转配置项 */
    public static final float TRUE_DAMAGE = 4.0F;

    /** 目标已带凋零时的真实伤害倍率。P8 转配置项 */
    public static final float TRUE_DAMAGE_MULTIPLIER_WHEN_WITHERED = 2.0F;

    private AgaitolosMeleeSkill() {
    }

    /**
     * 执行一次普攻（仅服务端有效，由 {@link AgaitolosEntity#doHurtTarget} 调用）。
     *
     * @return 是否真正打出这一击（目标非生物或物理段未命中时为 false）
     */
    public static boolean perform(AgaitolosEntity boss, Entity target) {
        if (!(target instanceof LivingEntity living)) {
            return false;
        }
        // 必须在施加新凋零之前判定，否则永远为 true（新效果已落地）。
        // "凋亡也算"：阶段三 BOSS 施加的是凋亡，这条判据必须一并认下（见类注释）
        boolean alreadyWithered = AgaitolosDoom.isWitheredOrDoomed(living);

        // 物理段：单走原版 Mob#doHurtTarget（攻击力 / 附魔 / 击退等原版结算）
        if (!boss.doHurtTargetPhysical(target)) {
            return false;
        }

        // 凋零/凋亡段：阶段分流收在 AgaitolosDoom（阶段一/二施加凋零 III，阶段三施加凋亡 III），
        // 施加者记为本 BOSS，保证击杀归属正确
        AgaitolosDoom.applyWitherOrDoom(boss, living, WITHER_DURATION_TICKS, WITHER_AMPLIFIER);

        // 真实段：无视护甲/抗性/附魔，且因 bypasses_cooldown 不会被物理段的无敌帧吞掉。
        // 必须带 causingEntity = BOSS（弹体传 null）：否则 source.getEntity() 为 null，
        // 原版只会取"无攻击者"的死亡消息，death.attack.forbidden_hex.true_damage.player 这条双语键永远用不到，
        // 且击杀归属丢失。
        // 阶段三「天魔＊灾」之后：受击方已被改写时本段整体换成精神伤害（口径唯一收在 AgaitolosPsychic）。
        // ⚠ 物理段（上面的 doHurtTargetPhysical）改不了 —— 它由原版 Mob#doHurtTarget 内部生成伤害源，
        //    见 AgaitolosPsychic 的类注释"未覆盖的一项"。
        // 随在场玩家数增强：本段是<b>固定数值</b>（4 点，带凋零翻倍），故乘人数系数；
        // 物理段不在此处乘 —— 它吃的是 ATTACK_DAMAGE 属性，已由 AgaitolosEntity 的缩放修饰符抬高，
        // 两处各乘一次会把普攻变成"物理 + 真实"双份加成。
        float amount = (float) (TRUE_DAMAGE * (alreadyWithered ? TRUE_DAMAGE_MULTIPLIER_WHEN_WITHERED : 1.0F)
                * boss.getDamageScale());
        living.hurt(AgaitolosPsychic.forVictim(
                AgaitolosCombat.trueDamage(boss.level(), null, boss), living, boss.level().getGameTime()), amount);
        return true;
    }
}
