package com.example.akaishi.forbidden.boss.agaitolos.skill;

import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosEntity;
import com.example.akaishi.forbidden.boss.agaitolos.entity.AgaitolosWitherSkull;
import com.example.akaishi.forbidden.entity.AkaishiForbiddenEntities;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 一阶段远程攻击：召唤凋零头颅（设计文档 §0「远程攻击」/§1 第 20 条）。
 * <p>
 * 本类只负责「怎么发射」；命中结算（5 爆炸 + 凋零 III 2s + 2 真实伤害）在弹体
 * {@link AgaitolosWitherSkull} 内完成。伤害常量因此放在弹体类、发射参数放在本类，
 * 两个类单向引用（本类 → 弹体），不互相引用。
 */
public final class AgaitolosSkullSkill {

    /**
     * 远程射程（格）：24 —— <b>与原版 {@code RangedAttackGoal} 的 {@code attackRadius} 同值</b>
     * （寄存器里那个 24.0F 字面量随该 Goal 一起搬到这里）。
     * <p>由 {@code AgaitolosSkillDirector} 消费：超出这个距离就不再给远程打分
     * （与"发射后弹体还能飞多远"无关，这是"BOSS 会不会开火"的门槛）。待调手感值 / P8 转配置项
     */
    public static final double SKULL_ATTACK_RADIUS = 24.0D;

    /**
     * 远程出手间隔（tick）：60 = 3s —— <b>与原版 {@code RangedAttackGoal} 的 {@code attackIntervalMin} 同值</b>
     * （理由同 {@link #SKULL_ATTACK_RADIUS}：原先进攻间隔由那个 Goal 自己持有，
     * 现在节奏由 {@code AgaitolosSkillDirector} 与 {@code AgaitolosEntity#performRangedAttack} 收口，
     * 必须在这里显式留一份）。由实体按阶段折算后写回 {@code rangedCooldownTicks}。待调手感值 / P8 转配置项
     */
    public static final int SKULL_COOLDOWN_TICKS = 60;

    /** 弹体初速（格/tick）0.8 ≈ 16 格/s。待调手感值 / P8 转配置项 */
    public static final float SKULL_VELOCITY = 0.8F;

    /** 弹体散布（原版 shoot 的 inaccuracy 参数，弧度）。待调手感值 / P8 转配置项 */
    public static final float SKULL_INACCURACY = 1.0F;

    private AgaitolosSkullSkill() {
    }

    /**
     * 朝目标发射一发凋零头颅（仅服务端生成，由 {@code AgaitolosEntity#performRangedAttack} 调用）。
     */
    public static void fire(AgaitolosEntity boss, LivingEntity target) {
        fireSpread(boss, target, 0.0F, 1.0F);
    }

    /**
     * 带「扇面偏角 + 破防比例」的发射 —— 原远程攻击与阶段三「三重投掷」<b>共用这一段发射逻辑</b>。
     * <p>
     * 只多两个参数、不复制第二份瞄准代码：三重投掷要的正是"同一个凋零头，偏一个角度、多破一点甲"，
     * 若另写一份 {@code shoot} 调用，将来改初速/散布/瞄准点就会漏掉一处（本项目反复出现的"两套口径"病）。
     * <ul>
     *   <li>{@code yawOffsetDegrees} —— 绕 Y 轴的扇面偏角（度）：0 = 原样瞄准（远程攻击）；
     *       -14 / 0 / +14 = 三重投掷的三发扇面；</li>
     *   <li>{@code armorKeptRatio} —— 破防比例：1.0 = 不破防（远程攻击 / 饱和轰炸，行为逐位不变）；
     *       0.7 = 无视 30% 防御（三重投掷），落点在 {@code AgaitolosWitherSkull#onHitEntity}。</li>
     * </ul>
     *
     * @return 生成的弹体 —— <b>只</b>供调用方做一个纯表现标记（当前唯一消费点是
     *         {@code AgaitolosBombardSkill#fire} 的"高空投弹"标记，用于落地时补一圈冲击环）；
     *         客户端分支返回 {@code null}（那里不生成任何东西）。既有调用方
     *         （本类的 {@link #fire} 与 {@code AgaitolosTripleThrowSkill#fireBeat}）忽略返回值，
     *         发射行为与改动前逐位相同。
     */
    public static AgaitolosWitherSkull fireSpread(AgaitolosEntity boss, LivingEntity target,
                                                  float yawOffsetDegrees, float armorKeptRatio) {
        if (boss.level().isClientSide()) {
            return null;
        }
        AgaitolosWitherSkull skull = new AgaitolosWitherSkull(AkaishiForbiddenEntities.AGAITOLOS_WITHER_SKULL.get(), boss.level());
        // owner = BOSS：既是击杀归属，也是「是否被反弹」的判据来源（被反弹后 owner 会变成玩家）
        skull.setOwner(boss);
        // 不可破坏地形：dangerous=false 降低飞行惯性、取消对火方块的额外破坏。
        // 场地保护：设计 §4.3 的下界牢狱场地整块尚未实现，待其落地后由该场地方块的爆炸抗性承载
        skull.setDangerous(false);
        // 破防只在需要时写（默认就是 1.0 = 不破防），避免给原远程攻击留一条无意义的分支
        if (armorKeptRatio < 1.0F) {
            skull.setArmorKeptRatio(armorKeptRatio);
        }
        Vec3 origin = new Vec3(boss.getX(), boss.getEyeY(), boss.getZ());
        // 瞄准目标身体中部而非脚底，避免目标贴地时弹体一路撞地板
        Vec3 aim = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D).subtract(origin);
        aim = rotateY(aim, yawOffsetDegrees);
        skull.setPos(origin.x, origin.y, origin.z);
        skull.shoot(aim.x, aim.y, aim.z, SKULL_VELOCITY, SKULL_INACCURACY);
        boss.level().addFreshEntity(skull);
        return skull;
    }

    /**
     * 把瞄准向量绕 Y 轴旋转一个角度（度）：三重投掷的"三连扇面"。
     * <p>用标准旋转矩阵而不是改 BOSS 的 yaw 再取视线：后者会改朝向（连带影响格挡锥 / 镰扫扇面 / 转向动画），
     * 而"扇面"要的只是三发弹体的落点散开，BOSS 的朝向应当全程锁在目标上。
     */
    private static Vec3 rotateY(Vec3 vector, float degrees) {
        if (degrees == 0.0F) {
            return vector;
        }
        double radians = Math.toRadians(degrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vec3(vector.x * cos - vector.z * sin, vector.y, vector.x * sin + vector.z * cos);
    }
}
