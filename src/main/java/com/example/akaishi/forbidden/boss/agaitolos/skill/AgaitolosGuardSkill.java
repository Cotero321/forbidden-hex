package com.example.akaishi.forbidden.boss.agaitolos.skill;

import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 一阶段技能「格挡」（设计文档 §0 / §1 第 23 条 / §3.3）的<b>唯一判定器</b>。
 * <p>
 * 规格：BOSS 举起镰刀摆架势，<b>正面一定角度内的近战攻击被完全格挡</b>，并把攻击者击退；
 * 架势有持续与冷却，不是常驻免伤。
 * <p>
 * <b>为什么"唯一判定器"这件事必须由本类独立承担</b>：格挡要被两侧同时消费 ——
 * 承伤侧（{@code AgaitolosDamageRules#resolve} 的架势闸）与输出侧
 * （{@link AgaitolosDiveSweepSkill#perform} 的"被格挡则本次攻击无效"）。
 * 若两边各写一套"朝向 + 是否近战"的判据，任何一侧调角度都会让另一侧静默失配
 * （典型症状：横扫说被挡了、承伤却照吃伤害）。故判据只此一处，两侧都调它。
 * <p>
 * 本类<b>不自持任何状态</b>：架势的剩余 tick / 冷却归实体自己持有（{@code AgaitolosEntity#tickGuard}），
 * 本类只做"这一下算不算被挡"与"挡下之后怎么反击"。
 */
public final class AgaitolosGuardSkill {

    /** 正面锥总角度（度）：120°（半角 60°）。待调手感值 / P8 转配置项 */
    public static final double GUARD_ARC_DEGREES = 120.0D;

    /** 架势持续（tick）：24 —— 与 guard clip 1.2s 对齐。待调手感值 / P8 转配置项 */
    public static final int GUARD_DURATION_TICKS = 24;

    /** 架势冷却（tick）：60 = 3s。待调手感值 / P8 转配置项 */
    public static final int GUARD_COOLDOWN_TICKS = 60;

    /** 反击的水平击退力度。待调手感值 / P8 转配置项 */
    public static final double COUNTER_KNOCKBACK_HORIZONTAL = 1.2D;

    /** 反击的垂直上抛力度。待调手感值 / P8 转配置项 */
    public static final double COUNTER_KNOCKBACK_VERTICAL = 0.9D;

    /** 半角 60° 的余弦阈值 = cos(60°) = 0.5。用点积比较代替 acos 求夹角，省一次反三角 */
    private static final double GUARD_HALF_ARC_COS = Math.cos(Math.toRadians(GUARD_ARC_DEGREES * 0.5D));

    /**
     * 水平方向的退化阈值（平方）：小于此值视为「与 BOSS 落在同一垂直线上」。
     * <p>即水平距离 &lt; {@code 1.0E-3} 格（1 毫米）：两个水平分量都已小到无法归一化出可信方向。
     * 该阈值同时用于"伤害来源"与"BOSS 朝向"两侧（口径一致，见 {@link #isWithinFrontArc}）。
     */
    private static final double HORIZONTAL_EPSILON_SQR = 1.0E-6D;

    private AgaitolosGuardSkill() {
    }

    /**
     * 这一下攻击是否被挡下（攻守两侧共用的唯一真源）。
     * <p>
     * 两侧口径刻意不同，因为它们要回答的问题不同：
     * <ul>
     *   <li><b>BOSS 侧</b>：架势中 + 近战 + 落在正面 120° 锥内 ⇒ 挡下。三者缺一不可，
     *       所以从背后打、用弓箭射都能穿透（这是架势的可惩罚面）。</li>
     *   <li><b>玩家侧</b>：直接读原版 {@link LivingEntity#isDamageSourceBlocked(DamageSource)}，
     *       <b>不另造一套</b> —— 盾牌的举盾时长、穿透箭排除、破盾标签等原版语义全在里面，
     *       复刻只会漏掉其中几条。</li>
     * </ul>
     * 注意两侧的朝向口径本就不同：本类 BOSS 侧是<b>正面 120°</b>，而原版盾是<b>前半球 180°</b>
     * （把方向 y 归零后点积 &lt; 0 即算挡住）。这是有意的差异，不是不一致。
     */
    public static boolean isBlocking(LivingEntity blocker, DamageSource source) {
        if (blocker instanceof AgaitolosEntity boss) {
            return boss.isGuarding() && isMelee(source) && isWithinFrontArc(boss, source);
        }
        return blocker.isDamageSourceBlocked(source);
    }

    /**
     * 是否近战（判 {@code directEntity} 本身是不是生物）。
     * <p>
     * 用 {@code directEntity} 而不是 {@code getEntity()}：要排除的是"飞过来的东西"（箭、火球、爆炸），
     * 而它们虽然 {@code causingEntity} 是生物、{@code directEntity} 却是弹体。
     * <p>顺带一个期望内的结果：BOSS <b>自己射出的凋零头</b>被玩家打回来时 directEntity 也是弹体 ⇒
     * 不被架势吃下，即"反弹回来的头"是这套架势的克制手段。
     */
    private static boolean isMelee(DamageSource source) {
        return source.getDirectEntity() instanceof LivingEntity;
    }

    /**
     * 伤害来源是否落在 BOSS 正面的 {@link #GUARD_ARC_DEGREES} 度锥内。
     * <p>
     * 判据是"来源位置的水平单位方向"与"BOSS 水平朝向单位向量"的点积 ≥ cos(半角)，
     * 含边界角 ⇒ 正好 ±60°。
     * <p>
     * <b>两处退化都必须给出确定性结果，不允许"恰好判成非正面"</b>（2026-09-21 修正）：
     * 原先两处退化都返回 {@code false}（= 不算正面 ⇒ 不格挡），于是玩家几乎贴在 BOSS
     * <b>正下方</b>（悬停 2 格时的常态站位）时，水平分量趋近 0 ⇒ 判成"背后偷袭" ⇒
     * <b>明明在架盾却挡不住</b>。现在两侧都按"退化即视为正面/挡下"取向收口：
     * <ol>
     *   <li><b>来源水平分量 ≈ 0</b>（伤害来自正上/正下方）：水平面上根本不存在"前/后"这一对概念 ⇒
     *       判 <b>{@code true}（算正面）</b>。头顶脚下不可能是绕后偷袭，判 false 只会让贴脸/脚底
     *       打它时"看不到它架盾"。阈值 {@link #HORIZONTAL_EPSILON_SQR} = 1 毫米。</li>
     *   <li><b>视线水平分量 ≈ 0</b>（俯仰 ≈ ±90°）：{@code getLookAngle()} 的水平分量是
     *       {@code cos(俯仰) × 水平朝向}，垂直时被压成 0，但<b>yaw 本身仍在</b> ⇒ 回退到
     *       {@code yaw} 推出的水平朝向 {@code (-sin(yaw), cos(yaw))}（与 {@code Entity#getYRot}
     *       同约定），绝不因"视线垂直"整下判不出朝向。同一回退手法已在
     *       {@code AgaitolosDiveSweepSkill#isWithinSweep} 与 {@code AgaitolosActionFx#facingYaw} 使用。</li>
     * </ol>
     * 注意这里与俯冲镰扫的退化取向已经<b>一致</b>（都是"退化即视为命中"）：漏挡与漏打都是静默失效，
     * 两侧没有理由相反。锥角仍严格是 {@link #GUARD_ARC_DEGREES} 度，<b>没有</b>放大到 360°。
     * <p>
     * <b>朝向基准刻意取身体 yaw（{@code getLookAngle()} 的水平分量）而不是头部朝向 {@code yHeadRot}</b>：
     * 头部每一 tick 都被 {@code MeleeAttackGoal}/{@code LookAtPlayerGoal} 对准当前目标，
     * 拿它当基准等于"360° 无死角"，从背后永远打不进去；身体 yaw 是架势自己锁定的朝向
     * （起手时对准目标，见 {@code AgaitolosEntity#startGuard} 的朝向锁定），
     * 从背后绕过去仍能穿透 —— 这正是架势的可惩罚面。
     * <p>
     * 用 {@code getSourcePosition()} 而非直接伤害实体位置：该方法优先取
     * {@code damageSourcePosition}，其次才是 {@code directEntity.position()}，语义就是"伤害来自哪里"。
     * {@code null} 分支对本 BOSS 不可达（{@link #isMelee} 已保证 {@code directEntity} 是实体），
     * 保留仅作兜底：判不出方向时按既有"非正面"处理，不引入免伤。
     */
    private static boolean isWithinFrontArc(AgaitolosEntity boss, DamageSource source) {
        Vec3 sourcePos = source.getSourcePosition();
        if (sourcePos == null) {
            return false;
        }
        double deltaX = sourcePos.x - boss.getX();
        double deltaZ = sourcePos.z - boss.getZ();
        double horizontalSqr = deltaX * deltaX + deltaZ * deltaZ;
        // 退化 ①：来源与 BOSS 同一垂直线（正上/正下方）⇒ 水平面上无"前/后"可言 ⇒ 视为正面
        if (horizontalSqr < HORIZONTAL_EPSILON_SQR) {
            return true;
        }
        double inverse = 1.0D / Math.sqrt(horizontalSqr);
        double toSourceX = deltaX * inverse;
        double toSourceZ = deltaZ * inverse;
        // 水平朝向：优先取视线的水平分量；视线近乎垂直（俯仰≈±90°）时该分量退化，回退到 yaw 推出的水平朝向
        Vec3 look = boss.getLookAngle();
        double lookHorizontalSqr = look.x * look.x + look.z * look.z;
        double facingX;
        double facingZ;
        if (lookHorizontalSqr < HORIZONTAL_EPSILON_SQR) {
            double yaw = Math.toRadians(boss.getYRot());
            facingX = -Math.sin(yaw);
            facingZ = Math.cos(yaw);
        } else {
            double lookInverse = 1.0D / Math.sqrt(lookHorizontalSqr);
            facingX = look.x * lookInverse;
            facingZ = look.z * lookInverse;
        }
        // 两个水平单位向量的点积即夹角余弦（与扫面的同款写法）
        return toSourceX * facingX + toSourceZ * facingZ >= GUARD_HALF_ARC_COS;
    }

    /**
     * 格挡成功的反击：把攻击者沿"BOSS → 攻击者"的水平方向推出去并上抛，随即收势。
     * <p>
     * <b>为什么服务端才做</b>：改速度与改架势都要权威执行 —— 速度靠 {@code hurtMarked} 下发，
     * 架势靠同步数据下发，客户端各做一份只会打架。
     * <p>
     * <b>为什么必须置 {@code hurtMarked = true}</b>：原版 {@code ServerEntity#sendChanges} 只在
     * {@code entity.hurtMarked} 为真时才广播 {@code ClientboundSetEntityMotionPacket}；
     * 服务端直接 {@code setDeltaMovement} 而不置该标志，客户端根本收不到新的速度
     * （原版 {@code LivingEntity#knockback} 也不设它，是另一条路径），反击会"算出来了但看不见"。
     * <p>
     * 另外这一下是<b>直接改速度</b>而不是调 {@code knockback}：被挡下的攻击在实体侧会
     * <b>提前 return false</b>（见 {@code AgaitolosEntity#hurt}），玩家不会进"打中了"的分支，
     * 所以不会被玩家自己的攻击后摇/疾跑中断覆盖掉这次击退。
     * <p>
     * 最后收势由 {@link AgaitolosEntity#endGuard()} 负责（写冷却 + 关同步位），
     * 本类不自持计时，与"状态归实体持有"的分工一致。
     */
    public static void counterAttack(AgaitolosEntity boss, DamageSource source) {
        if (boss.level().isClientSide()) {
            return;
        }
        if (source.getDirectEntity() instanceof LivingEntity attacker) {
            Vec3 offset = attacker.position().subtract(boss.position());
            Vec3 horizontal = new Vec3(offset.x, 0.0D, offset.z);
            // 攻击者与 BOSS 完全重合时水平向量无法归一化，退化为 Vec3.ZERO（纯上抛，防除零）
            Vec3 direction = horizontal.lengthSqr() > HORIZONTAL_EPSILON_SQR ? horizontal.normalize() : Vec3.ZERO;
            attacker.setDeltaMovement(direction.x * COUNTER_KNOCKBACK_HORIZONTAL,
                    COUNTER_KNOCKBACK_VERTICAL,
                    direction.z * COUNTER_KNOCKBACK_HORIZONTAL);
            attacker.hurtMarked = true;
        }
        boss.endGuard();
    }
}
