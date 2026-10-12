package com.example.akaishi.forbidden.boss.agaitolos;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

/**
 * 阿盖托洛丝的近战 Goal：<b>把"打得着"的判据换成实体自己那把尺子</b>。
 * <p>
 * <b>为什么必须新建这个类（这是"玩家打它却不还手"的第一根因）</b>：
 * 原版 {@code MeleeAttackGoal} 判断能否挥出这一击用的是它<b>自己算的</b>可达距离
 * （实测 Forge 1.20.1-47.3.0 字节码，{@code MeleeAttackGoal#getAttackReachSqr}）：
 * <pre>
 *   protected double getAttackReachSqr(LivingEntity target) {
 *      return this.mob.getBbWidth() * 2.0F * this.mob.getBbWidth() * 2.0F + target.getBbWidth();
 *   }
 * </pre>
 * 它<b>不调用</b> {@code LivingEntity/Mob#getMeleeAttackRangeSqr}——无论实体怎么重写那个方法，
 * 对本 Goal 都毫无影响（这正是 {@code AgaitolosEntity#getMeleeAttackRangeSqr} 里那段
 * "把悬停高度折算进可达距离"的**修复意图落空**的原因：它只被实体自己的
 * {@code isTargetWithinGuardRange} 与 {@code AgaitolosKickSkill} 消费，近战判定根本读不到）。
 * <p>
 * 于是本 BOSS 的实际情况是：本体碰撞箱宽 0.9 ⇒ 原版可达² = (0.9×2)² + 0.6 = <b>3.84</b>（≈ 1.96 格），
 * 而它常态悬停在玩家脚部上方 {@code AgaitolosMoveControl.HOVER_HEIGHT} = 2.0 格 ⇒
 * 光竖直差就给出 3D 距离² = <b>4.00</b> &gt; 3.84：<b>哪怕贴到脸上，近战判定也永远不成立</b>。
 * 表现就是"BOSS 怼着你飞，却一刀不出"。
 * <p>
 * 本类只改这一处判据（其余追人、算路、出手间隔一律沿用原版的成熟实现）：
 * 可达距离改读实体自己的 {@link AgaitolosEntity#getMeleeAttackRangeSqr}，
 * 让"策划写下的可达距离"真正成为唯一真源，也让"格挡起手 / 踢击可达 / 近战可达"三处共用同一把尺子。
 */
public class AgaitolosMeleeAttackGoal extends MeleeAttackGoal {

    /**
     * @param mob                     本 BOSS（实体侧已把悬停高度折算进 {@code getMeleeAttackRangeSqr}）
     * @param speedModifier           追击速度系数（仍乘阶段倍率，见 {@code AgaitolosPace}）
     * @param followingTargetEvenIfNotSeen 失去视线是否继续追（传 true：BOSS 不该因为玩家躲一下柱子就发呆）
     */
    public AgaitolosMeleeAttackGoal(PathfinderMob mob, double speedModifier, boolean followingTargetEvenIfNotSeen) {
        super(mob, speedModifier, followingTargetEvenIfNotSeen);
    }

    /**
     * 可达距离² ⇒ 改用实体自己的口径（含悬停高度折算）。
     * <p>原版该方法与 {@code getBbWidth()} 绑死，见类注释里的字节码证据。
     */
    @Override
    protected double getAttackReachSqr(LivingEntity target) {
        return this.mob.getMeleeAttackRangeSqr(target);
    }
}
