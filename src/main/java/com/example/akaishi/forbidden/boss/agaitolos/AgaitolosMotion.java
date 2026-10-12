package com.example.akaishi.forbidden.boss.agaitolos;

import com.example.akaishi.forbidden.boss.agaitolos.skill.AgaitolosKickSkill;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 阿盖托洛丝的<b>低空飞行物理协作类</b>：飞行导航替换、去重力的 {@code travel}（含禁飞例外）、
 * 摔落伤害否决与近战可达尺子。
 * <p>
 * 从 {@link AgaitolosEntity} 机械搬出（2026-09-24 拆分轮）：这一块是"本 BOSS 怎么移动"的原版覆写层，
 * 与战斗状态机无耦合；收进本类后与 {@code AgaitolosMoveControl}（速度写入方）分界清晰
 * —— 控制器写 deltaMovement，本类决定 travel 怎么消费它。
 * 实体覆写只留一行委托；{@code super.travel} / {@code super.getMeleeAttackRangeSqr} 经实体的
 * {@code doSuperTravel} / {@code doSuperMeleeAttackRangeSqr} 钩子调用
 * （同 {@code doHurtTargetPhysical} 的既有手法：super 调用只能在子类内部）。
 */
public final class AgaitolosMotion {

    private AgaitolosMotion() {
    }

    // ---------------------------------------------------------------- 导航 / 摔落

    /**
     * 飞行寻路：替换 {@link Monster} 默认的 {@code GroundPathNavigation}。
     * <p>配置照抄原版凋灵（同为 Monster 系飞行怪）：不开门、可浮水；不再调 {@code setCanPassDoors(true)}，
     * 因为 {@code FlyingPathNavigation#createPathFinder} 已默认开启。
     * {@code AgaitolosMeleeAttackGoal} / 决策层的脱困重寻路走的是 {@code createPath(Entity)} / {@code moveTo(Entity)}，
     * 在飞行导航下照常成立（{@code canUpdatePath()} 恒真），故近战 Goal 无需改动导航。
     */
    static PathNavigation createNavigation(AgaitolosEntity boss, Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(boss, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        return navigation;
    }

    /**
     * 不吃摔落伤害：直接否决摔落结算。
     * <p>不清空 {@code checkFallDamage}（{@code FlyingMob} 的路子）：那条路径还兼管水中状态与落地粒子/音效，
     * 本类继承自 {@link Monster}，保留原版链、只否决伤害更安全。
     */
    static boolean causeFallDamage(AgaitolosEntity boss, float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    // ---------------------------------------------------------------- travel（去重力飞行）

    /**
     * 飞行移动：整体替换 {@code LivingEntity#travel}，去掉其中的重力项，只保留水/岩浆阻力与空气摩擦。
     * <p>蓝本 = 原版 {@code FlyingMob#travel}（恶魂、幻翼靠它做到不受重力）。本类继承自 {@link Monster} 无法复用该类，故照抄其行为。
     * <p><b>被格挡后的禁飞例外</b>：{@link AgaitolosEntity#isGrounded()} 时交回原版 {@code super.travel}，让重力把 BOSS 拉向地面。
     * 走哪一层已核对 1.20.1 源码：{@code Mob} / {@code PathfinderMob} / {@code Monster} <b>都没有</b>覆写 {@code travel}
     * （只有 {@code LivingEntity} 声明），故这里的 {@code super.travel} 直达 {@code LivingEntity#travel}；
     * 其"普通移动"分支（非水/岩浆/鞘翅）里重力就在这一行：
     * <pre>
     *   d2 = handleRelativeFrictionAndCalculateMovement(travelVector, f2).y;
     *   ...
     *   else if (!this.isNoGravity()) { d2 -= d0; }   // d0 = 重力属性值（forge 的 ENTITY_GRAVITY，默认 0.08）
     *   this.setDeltaMovement(vec35.x * f3, d2 * 0.98, vec35.z * f3);
     * </pre>
     * 本类未设 {@code noGravity}，故该项确实生效，实体每 tick 净增约 -0.08 的竖直速度并不断累积直到落地。
     * 水平方向照旧消费 {@code AgaitolosMoveControl} 写入的速度（{@code travelVector} 对本实体恒为 0，Mob 不产生移动输入）。
     */
    static void travel(AgaitolosEntity boss, Vec3 travelVector) {
        if (boss.isGrounded()) {
            boss.doSuperTravel(travelVector);
            return;
        }
        if (boss.isControlledByLocalInstance()) {
            if (boss.isInWater()) {
                boss.moveRelative(0.02F, travelVector);
                boss.move(MoverType.SELF, boss.getDeltaMovement());
                boss.setDeltaMovement(boss.getDeltaMovement().scale(0.8F));
            } else if (boss.isInLava()) {
                boss.moveRelative(0.02F, travelVector);
                boss.move(MoverType.SELF, boss.getDeltaMovement());
                boss.setDeltaMovement(boss.getDeltaMovement().scale(0.5D));
            } else {
                // 空中摩擦固定 0.91（取原版 Allay#travel 的写法）：
                // FlyingMob 会再查一遍脚下方块摩擦，但那个重载 getFriction(Level,BlockPos,Entity) 只存在于 forge 侧，
                // common 编译面没有；且本 BOSS 的位移完全由 AgaitolosMoveControl 改写 deltaMovement 驱动，
                // travelVector 恒为 0，贴地/空中的输入系数差异无实际影响。
                boss.moveRelative(0.02F, travelVector);
                boss.move(MoverType.SELF, boss.getDeltaMovement());
                boss.setDeltaMovement(boss.getDeltaMovement().scale(0.91F));
            }
        }
        boss.calculateEntityAnimation(false);
    }

    // ---------------------------------------------------------------- 近战可达尺子

    /**
     * 近战可达距离：把常态悬停高度折算进来。
     * <p>
     * <b>⚠ 原注释里的一个错误假设（已按字节码实测修正，也是"打它不还手"的根因）</b>：
     * 这里曾写着"原版 {@code MeleeAttackGoal} 的判据是 3D 距离 ≤ {@code getMeleeAttackRangeSqr}"——
     * <b>不成立</b>。实测 Forge 1.20.1-47.3.0 的 {@code MeleeAttackGoal#getAttackReachSqr} 是：
     * <pre>
     *   return this.mob.getBbWidth() * 2.0F * this.mob.getBbWidth() * 2.0F + target.getBbWidth();
     * </pre>
     * 它<b>根本不读本方法</b>。于是本方法当时的"修复"完全落空：0.9 宽时原版门槛 = 1.8² + 0.6 = 3.84（≈1.96 格），
     * 而本 BOSS 悬停在玩家脚上 {@link AgaitolosMoveControl#HOVER_HEIGHT}=2.0 格，光竖直差就是 2.0² = 4.0 &gt; 3.84
     * ⇒ <b>普攻永远触发不了</b>（远程又被同 Flag 的 Goal 饿死，故表现是"完全不还手"）。
     * <p>
     * 真正的落地方式是让近战 Goal 走本方法：见 {@link AgaitolosMeleeAttackGoal}（只改那一处判据）。
     * 本方法因此有三个消费方，口径必须只有这一份：近战 Goal 的出手判定、
     * 决策层 {@link AgaitolosSkillDirector} 的普攻打分、以及 {@link AgaitolosKickSkill} 的踢击可达。
     * <p>
     * <b>折算基准取「最大空中档高度」而不是"当前阶段的高度"（2026-09-21 阶段三降高时复核）</b>：
     * 本式用的是 {@link AgaitolosMoveControl#HOVER_HEIGHT}（= 一/二阶段的常态高度 2.0），
     * 而阶段三的实际空中档是 {@link AgaitolosMoveControl#PHASE_3_HOVER_HEIGHT}（1.0）、
     * 地面档更是 0.0 ⇒ 这把尺子在任何阶段都<b>不小于</b>该阶段的实际所需，属保守侧：
     * <ul>
     *   <li>阶段三：实际竖直差只有 1.0，而折算项按 2.0 给 ⇒ 多让约 1 格水平余量 ——
     *       不会出现"降了高度反而打不着"，最多是"够得着一点点就挥刀"；</li>
     *   <li>地面档：竖直差 0 ⇒ 同理偏宽松。这正是 {@link AgaitolosEntity#isPerched()} 注释里写明的既有取舍
     *       （宁可多够 2 格，也不要出现"落地后反倒打不着"）。</li>
     * </ul>
     * 若实机觉得阶段三"够得太远"，改法是把本式换成按当前档位折算（读
     * {@code AgaitolosMoveControl#airborneHeight(getPhase())} / {@link AgaitolosEntity#isPerched()}），
     * <b>而不是</b>去动 {@code HOVER_HEIGHT} —— 后者会连带改掉一、二阶段的悬停手感。
     */
    static double meleeAttackRangeSqr(AgaitolosEntity boss, LivingEntity target) {
        double reachAllowance = AgaitolosMoveControl.HOVER_HEIGHT + 0.5D;
        return boss.doSuperMeleeAttackRangeSqr(target) + reachAllowance * reachAllowance;
    }
}
