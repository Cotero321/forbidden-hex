package com.example.akaishi.forbidden.boss.agaitolos.skill;

import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosCombat;
import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosDoom;
import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosEntity;
import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosPsychic;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 一阶段技能「俯冲镰扫」的<b>伤害面</b>（设计文档 §0 一阶段）。
 * <p>
 * 规格：快速飞行至玩家处后横扫，伤害 = 玩家最大生命 30%，并<b>无视 30% 护甲与韧性</b>；
 * 玩家若带凋零则改判<b>魔法伤害</b>；玩家若<b>格挡成功</b>则本次攻击无效。
 * <p>
 * 本轮补上了位移与惩罚的<b>接线</b>，但本类自身仍是<b>纯伤害结算</b>：冲锋位移由
 * {@code AgaitolosEntity#tickDiveCharge} 驱动；格挡成功后的惩罚（解除飞行 30s + 封印本技能）归
 * {@link AgaitolosEntity#onSweepBlocked()} 持有，本类只在判定到格挡时<b>上报</b>，不碰位移与状态计时。
 * <p>
 * <b>破甲折算口径（用户拍板，不可擅改）</b>：不是去改玩家属性，而是"精确折算"——
 * 先按「护甲 × {@link #ARMOR_KEPT_RATIO}、韧性 × {@link #ARMOR_KEPT_RATIO}」跑一遍原版护甲公式
 * （算法在 {@link AgaitolosCombat#damageAfterPartialArmorBypass}，
 * 与二阶段「高速踢击」的 40% <b>共用同一段代码</b>）算出应受伤害，
 * 再以带 {@code bypasses_armor} 标签的 {@code forbidden_hex:scythe_sweep} 施加。
 * 这样原版 {@code LivingEntity#getDamageAfterArmorAbsorb} 会因该标签整段跳过护甲步骤
 * （不会二次减免），而 {@code getDamageAfterMagicAbsorb}（抗性提升 + 保护附魔）照常执行 ——
 * 正合规格"只破 30% 护甲与韧性、抗性与附魔照旧"。
 */
public final class AgaitolosDiveSweepSkill {

    // ---------------------------------------------------------------- 手感常量（待调手感值 / P8 转配置项）

    /** 伤害 = 目标最大生命 × 该比例。待调手感值 / P8 转配置项 */
    public static final float BASE_DAMAGE_MAX_HEALTH_RATIO = 0.3F;

    /** 生效护甲比例：无视 30% ⇒ 只剩 70% 参与减免（韧性同理）。待调手感值 / P8 转配置项 */
    public static final float ARMOR_KEPT_RATIO = 0.7F;

    /** 横扫水平半径（格）。待调手感值 / P8 转配置项 */
    public static final double SWEEP_RADIUS = 4.0D;

    /** 横扫垂直容差（格）：大于悬停高度 {@code AgaitolosMoveControl.HOVER_HEIGHT}=2.0，保证地面玩家仍在带内。待调手感值 / P8 转配置项 */
    public static final double SWEEP_VERTICAL_TOLERANCE = 2.5D;

    /** 横扫扇面总角度（度）：正面 180°（半角 90°）。待调手感值 / P8 转配置项 */
    public static final double SWEEP_ARC_DEGREES = 180.0D;

    /** 横扫冷却（tick）：10s。待调手感值 / P8 转配置项 */
    public static final int SWEEP_COOLDOWN_TICKS = 200;

    /** 扇面半角的余弦阈值 = cos(90°)。用点积比较代替 acos 求夹角，与 {@link AgaitolosGuardSkill} 同思路 */
    private static final double SWEEP_HALF_ARC_COS = Math.cos(Math.toRadians(SWEEP_ARC_DEGREES * 0.5D));

    /** 水平方向的退化阈值（平方）：小于此值视为「与 BOSS 同一垂直线上」 */
    private static final double HORIZONTAL_EPSILON_SQR = 1.0E-6D;

    private AgaitolosDiveSweepSkill() {
    }

    /**
     * 结算一次俯冲镰扫的伤害（仅服务端；由 {@link AgaitolosEntity} 的冲锋收尾处调用，见其 {@code finishDive}）。
     *
     * @return 是否<b>有玩家落入横扫范围</b>（含被格挡者）—— 调用方据此进冷却（动画已在冲锋起手时播，
     *         见 {@code AgaitolosEntity#startDiveSweep}，此处不再重复触发）。
     *         被格挡也算"这一刀挥出去了"，否则格挡成功后 BOSS 会每 tick 空挥（无冷却）。
     */
    public static boolean perform(AgaitolosEntity boss) {
        if (boss.level().isClientSide()) {
            return false;
        }
        boolean anyInSweep = false;
        // 先用包围盒粗筛（与实体侧 knockbackNearbyPlayers 同一手法），再逐玩家做精确的球半径 + 垂直带 + 正面锥筛选
        for (Player player : boss.level().getEntitiesOfClass(Player.class, boss.getBoundingBox().inflate(SWEEP_RADIUS))) {
            if (!player.isAlive() || !isWithinSweep(boss, player)) {
                continue;
            }
            anyInSweep = true;

            // a. 伤害源：带凋零 ⇒ 魔法伤害（规格"如玩家拥有凋零效果便会造成魔法伤害"）；否则 ⇒ 破甲镰扫。
            //    魔法分支用原版 minecraft:magic 而非自制类型：vanilla 已把 minecraft:magic 放进 bypasses_armor
            //    （实测 data/minecraft/tags/damage_type/bypasses_armor.json 含 "minecraft:magic"），
            //    即"魔法伤害不吃护甲点数"是原版既定语义，与本技能要表达的"魔法伤害"一致，无需另造数据。
            //    "带凋零"由 AgaitolosDoom#isWitheredOrDoomed 判定：阶段三 BOSS 施加的是凋亡，须一并认下
            //    （否则阶段三这条"带凋零转魔法伤害"会静默失效）
            boolean withered = AgaitolosDoom.isWitheredOrDoomed(player);
            DamageSource source = withered
                    ? magicSweep(boss)
                    : AgaitolosCombat.scytheSweep(player.level(), null, boss);

            // b. 伤害量：基础 = 目标最大生命 × 30%
            float damage = player.getMaxHealth() * BASE_DAMAGE_MAX_HEALTH_RATIO;
            if (!withered) {
                // 精确折算「无视 30% 护甲与韧性」：算法与常量口径<b>整体收在</b>
                // AgaitolosCombat#damageAfterPartialArmorBypass（二阶段「高速踢击」的 40% 与本处 30%
                // 共用同一段代码，只有比例参数不同 —— 详见该方法 javadoc 里的公式依据）。
                // 本处传 ARMOR_KEPT_RATIO = 0.7F，与抽取前逐项相同（护甲取 getArmorValue()、
                // 韧性取 getAttributeValue(ARMOR_TOUGHNESS) 后显式窄化），行为不变。
                damage = AgaitolosCombat.damageAfterPartialArmorBypass(player, damage, ARMOR_KEPT_RATIO);
            }
            // 魔法分支不折算：原版 magic 本就不吃护甲点数，直接用基础值（再折算等于凭空多减一次）。

            // c. 格挡：攻守共用 AgaitolosGuardSkill#isBlocking（设计文档 §3.3 唯一判定器，禁止另造一套）。
            //    命中 ⇒ 本次攻击无效（直接 continue，伤害完全不落地）。
            //
            //    ⚠ 为什么"判定用的源"可以与"施加用的源"不同（这不是取巧，是原版数据层的连带效应所迫）：
            //      vanilla 的 #minecraft:bypasses_shield 标签里直接引用了 #minecraft:bypasses_armor，
            //      而 forbidden_hex:scythe_sweep 为了"只保留 70% 护甲收益"必须进 bypasses_armor，
            //      于是被传递性判成"无视盾牌" ⇒ 拿真实源去调 isDamageSourceBlocked 恒为 false，
            //      规格的「格挡成功 ⇒ 本次攻击无效」永远触发不了（这条交互是俯冲镰扫的核心惩罚机制）。
            //      解法：只把**判定**换成等价探针源 mobAttack（同样携带攻击者位置、能走完原版朝向锥，但不在破甲标签里），
            //      实际施加的伤害源仍是 scythe_sweep，护甲口径不受影响；判定函数仍是同一个 isBlocking。
            //    魔法分支刻意不换探针：原版 minecraft:magic 本就无视盾牌，让它可被格挡反而不符原版直觉。
            DamageSource judgementSource = withered ? source : boss.damageSources().mobAttack(boss);
            if (AgaitolosGuardSkill.isBlocking(player, judgementSource)) {
                // 本次只做"攻击无效"：伤害口径完全不变（直接 continue，伤害不落地）。
                // 惩罚（解除飞行 30s = 禁飞 + 封印本技能 30s）只<b>上报</b>，计时与状态归实体自己持有
                // （与 AgaitolosGuardSkill#counterAttack → AgaitolosEntity#endGuard 同一手法）。
                // 逐人调用：一次横扫可能被多个玩家同时格挡，onSweepBlocked 幂等，只刷新计时。
                boss.onSweepBlocked();
                continue;
            }
            // 阶段三「天魔＊灾」之后：受击方已被改写时，本次横扫整体改判精神伤害
            // （口径唯一收在 AgaitolosPsychic）。优先级刻意取"改写 > 带凋零转魔法"：
            // 规格说的是"BOSS 的伤害类型替换为精神伤害"，那是全局替换，不该被"这个人恰好带着凋零"顶掉。
            player.hurt(AgaitolosPsychic.forVictim(source, player, boss.level().getGameTime()), damage);
        }
        return anyInSweep;
    }

    /**
     * 构造魔法分支的伤害源：{@code minecraft:magic} + 归属实体 BOSS。
     * <p>
     * 位置补偿理由同 {@link AgaitolosCombat#scytheSweep}：三参构造器在 {@code directEntity == null} 时
     * {@code getSourcePosition()} 为 null，会让玩家侧盾牌朝向判定失效；故用两参重载
     * {@code DamageSource(Holder, Entity)}（内部 {@code this(type, entity, entity)}）补齐来源位置与归属。
     */
    private static DamageSource magicSweep(AgaitolosEntity boss) {
        if (boss.level() instanceof ServerLevel serverLevel) {
            Registry<DamageType> registry = serverLevel.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
            return new DamageSource(registry.getHolderOrThrow(DamageTypes.MAGIC), boss);
        }
        return boss.level().damageSources().magic();
    }

    /**
     * 玩家是否落在横扫范围内：水平半径 {@link #SWEEP_RADIUS} + 垂直容差 {@link #SWEEP_VERTICAL_TOLERANCE}
     * + 正面 {@link #SWEEP_ARC_DEGREES} 度扇面。
     * <p>
     * 刻意不复用 {@link AgaitolosGuardSkill} 内的朝向判定：那一套是 private、且判据是"伤害源位置"（用于承伤），
     * 这里判的是"玩家自身位置"（用于输出），两者输入根本不同；而<b>格挡判定本身仍必须走
     * {@link AgaitolosGuardSkill#isBlocking}</b>，本方法只做范围筛选，不承担任何格挡语义。
     */
    private static boolean isWithinSweep(AgaitolosEntity boss, Player player) {
        double deltaY = player.getY() - boss.getY();
        if (Math.abs(deltaY) > SWEEP_VERTICAL_TOLERANCE) {
            return false;
        }
        double deltaX = player.getX() - boss.getX();
        double deltaZ = player.getZ() - boss.getZ();
        double horizontalSqr = deltaX * deltaX + deltaZ * deltaZ;
        if (horizontalSqr > SWEEP_RADIUS * SWEEP_RADIUS) {
            return false;
        }
        if (horizontalSqr < HORIZONTAL_EPSILON_SQR) {
            // BOSS 正下方/正上方：180° 扇面在水平面上退化为一点，朝向无从谈起 ⇒ 判为<b>命中</b>
            //（漏判等于悬停正上方时白扫一刀，而脚下站的就是目标）。
            // 注：承伤侧的 {@code AgaitolosGuardSkill#isWithinFrontArc} 自 2026-09-21 起也改成"退化即视为正面/
            // 挡下"，两侧取向已一致 —— 漏打与漏挡同属静默失效，没有理由相反。
            return true;
        }
        double inverse = 1.0D / Math.sqrt(horizontalSqr);
        double toPlayerX = deltaX * inverse;
        double toPlayerZ = deltaZ * inverse;

        // 水平朝向：优先取视线水平分量；俯冲/仰视到几乎垂直时该分量退化，回退到 yaw 推出的水平朝向
        // （yaw 恒有定义，避免"整整一扫全落空"这种静默失效）
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
        // toPlayer 已是单位向量，点积即夹角余弦；≥ cos(半角) 即在扇面内
        return toPlayerX * facingX + toPlayerZ * facingZ >= SWEEP_HALF_ARC_COS;
    }
}
