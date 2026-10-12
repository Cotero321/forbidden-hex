package com.example.akaishi.forbidden.boss.agaitolos;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 阿盖托洛丝的<b>动作</b>粒子表现层：普攻斩击弧、召唤凋零头（掌心聚集 + 离手）、俯冲镰扫（拖尾 + 冲击）、
 * 格挡架势（护盾纹 + 火花）、受击反馈、死亡灵魂外逸、召唤物消散。
 * <p>
 * <b>为什么与 {@link AgaitolosFx} 分成两个类</b>：两者的触发源根本不同 —— {@code AgaitolosFx} 是
 * "出场 / 死亡爆发 / 蓄力光球"这类由阶段机与计时器驱动的<b>演出</b>；本类是"挥了一刀 / 挡了一下 /
 * 被砍中了"这类由<b>技能结算点</b>驱动的战斗动作。堆进同一个类会迅速越过 500 行（RULES §8），
 * 也会让调用方在一堆演出常量里翻找战斗常量。本类只暴露语义入口，实体侧各调一行。
 * <p>
 * <b>为什么也只在服务端发射</b>（与 {@link AgaitolosFx} 同一口径）：
 * <ol>
 *   <li>触发时机全部长在服务端结算分支里（{@code doHurtTarget} / {@code finishDive} / {@code hurt} 等），
 *       客户端没有这些时刻；</li>
 *   <li>服务端 {@code sendParticles} 会自动逐玩家下发，不必自写同步；客户端 {@code addParticle} 只有本机可见。</li>
 * </ol>
 * 故每个入口都用 {@code boss.level() instanceof ServerLevel} 兜底，非服务端直接空转 ——
 * 这层兜底不是冗余：少了它客户端调用会在强转处抛 {@code ClassCastException}。
 * <p>
 * <b>无状态</b>：节流一律用 {@code boss.tickCount % 间隔} 或调用方传入的年龄，
 * 本类不持任何计时字段 ⇒ 重进存档、区块卸载重建都不会出现"计时器没复位"的错位。
 * <p>
 * <b>识别色一致</b>：只用青蓝族（{@code SOUL_FIRE_FLAME} / {@code SOUL} / {@code ELECTRIC_SPARK} /
 * {@code SWEEP_ATTACK}）与紫族（{@code DRAGON_BREATH}），与 {@link AgaitolosFx} 选型同源；
 * 橙红的 {@code FLAME} / {@code CRIT} 之类一律不用，避免与 BOSS 识别色撞车。
 * <p>
 * 所有常量均为<b>待调手感值</b>（P8 转配置项）。位置的绝对精度受限于服务端拿不到骨骼坐标，
 * 一律按碰撞箱 + 朝向做近似（与 {@code AgaitolosFx#orbPosition} 同一局限）。
 */
public final class AgaitolosActionFx {

    /** 水平方向的退化阈值（平方）：小于此值视为"视线几乎垂直"，水平朝向改用 yaw 推出（yaw 恒有定义） */
    private static final double HORIZONTAL_EPSILON_SQR = 1.0E-6D;

    /** 弧线采样点的统一扩散（格）：0.12 —— 弧靠"点"连成，扩散给大会糊成扇形。待调手感值 / P8 转配置项 */
    private static final double ARC_POINT_SPREAD = 0.12D;

    /** 弧线采样点的统一初速：0.03（近乎原地，弧形靠取点位置表达）。待调手感值 / P8 转配置项 */
    private static final double ARC_POINT_SPEED = 0.03D;

    // ---------------------------------------------------------------- 普攻斩击弧

    /** 斩击弧采样点数：9。待调手感值 / P8 转配置项 */
    public static final int SLASH_ARC_POINTS = 9;

    /** 斩击弧总角度（度）：120（与格挡锥同宽，读作"一刀扫过正面"）。待调手感值 / P8 转配置项 */
    public static final double SLASH_ARC_DEGREES = 120.0D;

    /** 斩击弧半径（格）：1.8（弯刀臂展量级，贴着躯体前缘）。待调手感值 / P8 转配置项 */
    public static final double SLASH_ARC_RADIUS = 1.8D;

    /** 斩击弧高度 = 身高 × 该比例：0.62（胸口高度挥出，与躯干中部同层）。待调手感值 / P8 转配置项 */
    public static final double SLASH_ARC_HEIGHT_RATIO = 0.62D;

    /** 斩击弧沿朝向前移（格）：0.3（刃痕落在体前，不穿进胸腔）。待调手感值 / P8 转配置项 */
    public static final double SLASH_ARC_FORWARD = 0.3D;

    // ---------------------------------------------------------------- 召唤凋零头

    /** 掌心点高度 = 身高 × 该比例：0.95（略高过头顶，与蓄力球的取高口径一致）。待调手感值 / P8 转配置项 */
    public static final double CAST_HAND_HEIGHT_RATIO = 0.95D;

    /** 掌心点沿朝向前移（格）：0.5（贴在手前方一点）。待调手感值 / P8 转配置项 */
    public static final double CAST_HAND_FORWARD = 0.5D;

    /** 掌心聚集每簇粒子数：4。待调手感值 / P8 转配置项 */
    public static final int CAST_GATHER_COUNT = 4;

    /** 掌心聚集的扩散（格）：0.16（聚成一小团，而不是散成雾）。待调手感值 / P8 转配置项 */
    public static final double CAST_GATHER_SPREAD = 0.16D;

    /** 聚集粒子的初速：0.02（几乎原地，靠 DRAGON_BREATH 自身上飘体现"能量在凝"）。待调手感值 / P8 转配置项 */
    public static final double CAST_GATHER_SPEED = 0.02D;

    /** 离手轨迹采样点数：4。待调手感值 / P8 转配置项 */
    public static final int CAST_TRAIL_POINTS = 4;

    /** 离手轨迹点间距（格）：0.45（4 点约 1.8 格，"离手"到"上膛"之间的一段）。待调手感值 / P8 转配置项 */
    public static final double CAST_TRAIL_STEP = 0.45D;

    /** 离手轨迹点的扩散（格）：0.05（点要小，才读得出方向）。待调手感值 / P8 转配置项 */
    public static final double CAST_TRAIL_SPREAD = 0.05D;

    // ---------------------------------------------------------------- 俯冲镰扫

    /** 俯冲拖尾的发射间隔（tick）：1 —— 冲锋只有 1s（20 tick），稀了就断成几截。待调手感值 / P8 转配置项 */
    public static final int DIVE_TRAIL_INTERVAL_TICKS = 1;

    /** 俯冲拖尾每簇粒子数：8。待调手感值 / P8 转配置项 */
    public static final int DIVE_TRAIL_COUNT = 8;

    /** 俯冲拖尾的扩散（格）：0.35（速度越快越像一条带）。待调手感值 / P8 转配置项 */
    public static final double DIVE_TRAIL_SPREAD = 0.35D;

    /** 拖尾发射点沿速度反方向后退（格）：0.6（点在身后，不在身上冒）。待调手感值 / P8 转配置项 */
    public static final double DIVE_TRAIL_BACK = 0.6D;

    /** 拖尾高度 = 身高 × 该比例：0.6（躯干中部，与爆发口径一致）。待调手感值 / P8 转配置项 */
    public static final double DIVE_TRAIL_HEIGHT_RATIO = 0.6D;

    /** 镰扫弧采样点数：11（比普攻更密：这一刀扫得更远，点多才不显稀）。待调手感值 / P8 转配置项 */
    public static final int SWEEP_ARC_POINTS = 11;

    /** 镰扫弧总角度（度）：180（与横扫 180° 判定同宽，"扫到哪就亮到哪"）。待调手感值 / P8 转配置项 */
    public static final double SWEEP_ARC_DEGREES = 180.0D;

    /** 镰扫弧半径（格）：2.6（介于刀身长度与 4.0 的判定半径之间）。待调手感值 / P8 转配置项 */
    public static final double SWEEP_ARC_RADIUS = 2.6D;

    /** 镰扫弧高度 = 身高 × 该比例：0.55（略低于胸，读作横向扫过）。待调手感值 / P8 转配置项 */
    public static final double SWEEP_ARC_HEIGHT_RATIO = 0.55D;

    /** 命中冲击环采样点数：8。待调手感值 / P8 转配置项 */
    public static final int SWEEP_IMPACT_POINTS = 8;

    /** 命中冲击环半径（格）：2.4。待调手感值 / P8 转配置项 */
    public static final double SWEEP_IMPACT_RADIUS = 2.4D;

    /** 冲击环每点粒子数：2。待调手感值 / P8 转配置项 */
    public static final int SWEEP_IMPACT_COUNT = 2;

    /** 冲击环每点的扩散（格）：0.25（保持"点"，连成环而不是糊成圆盘）。待调手感值 / P8 转配置项 */
    public static final double SWEEP_IMPACT_SPREAD = 0.25D;

    /** 冲击环粒子的初速：0.06（略外扩，读作"冲击波推出去"）。待调手感值 / P8 转配置项 */
    public static final double SWEEP_IMPACT_SPEED = 0.06D;

    /** 冲击地面尘的粒子数：8。待调手感值 / P8 转配置项 */
    public static final int SWEEP_IMPACT_DUST_COUNT = 8;

    /** 冲击尘的竖直扩散（格）：0.3。待调手感值 / P8 转配置项 */
    public static final double SWEEP_IMPACT_DUST_SPREAD_Y = 0.3D;

    /** 冲击环的离脚抬升（格）：0.1（贴着脚下方块顶面之上，不埋进地里）。待调手感值 / P8 转配置项 */
    public static final double SWEEP_IMPACT_GROUND_LIFT = 0.1D;

    // ---------------------------------------------------------------- 格挡架势

    /** 护盾纹的发射间隔（tick）：2（架势 24 tick ⇒ 约 12 帧，够成"纹"又不刷屏）。待调手感值 / P8 转配置项 */
    public static final int GUARD_AURA_INTERVAL_TICKS = 2;

    /** 护盾纹采样点数：5。待调手感值 / P8 转配置项 */
    public static final int GUARD_ARC_POINTS = 5;

    /** 护盾纹总角度（度）：120（与格挡判定锥逐字同宽 —— 挡得住哪一面，就亮哪一面）。待调手感值 / P8 转配置项 */
    public static final double GUARD_ARC_DEGREES = 120.0D;

    /** 护盾纹半径（格）：1.7。待调手感值 / P8 转配置项 */
    public static final double GUARD_ARC_RADIUS = 1.7D;

    /** 护盾纹高度 = 身高 × 该比例：0.7（架势是举镰在身前，纹随镰刀高度）。待调手感值 / P8 转配置项 */
    public static final double GUARD_ARC_HEIGHT_RATIO = 0.7D;

    /** 格挡火花粒子数：10。待调手感值 / P8 转配置项 */
    public static final int GUARD_SPARK_COUNT = 10;

    /** 格挡火花扩散（格）：0.3。待调手感值 / P8 转配置项 */
    public static final double GUARD_SPARK_SPREAD = 0.3D;

    /** 格挡火花初速：0.12（有溅射感才像"挡下了"。待调手感值 / P8 转配置项 */
    public static final double GUARD_SPARK_SPEED = 0.12D;

    // ---------------------------------------------------------------- 受击反馈

    /** 受击反馈粒子数：10。待调手感值 / P8 转配置项 */
    public static final int HURT_FEEDBACK_COUNT = 10;

    /** 受击反馈的扩散（格）：0.5。待调手感值 / P8 转配置项 */
    public static final double HURT_FEEDBACK_SPREAD = 0.5D;

    /** 受击反馈的初速：0.08。待调手感值 / P8 转配置项 */
    public static final double HURT_FEEDBACK_SPEED = 0.08D;

    /** 受击反馈高度 = 身高 × 该比例：0.6（躯干中部迸出，同爆发口径）。待调手感值 / P8 转配置项 */
    public static final double HURT_FEEDBACK_HEIGHT_RATIO = 0.6D;

    /** 受击反馈里青蓝焰点的数量：4（主粒子之外的识别色补充）。待调手感值 / P8 转配置项 */
    public static final int HURT_FEEDBACK_FLAME_COUNT = 4;

    // ---------------------------------------------------------------- 召唤物消散（技能结束整队回收）

    /** 消散魂点数量：6。待调手感值 / P8 转配置项 */
    public static final int MINION_DISSOLVE_SOUL_COUNT = 6;

    /** 消散青蓝焰点数量：4。待调手感值 / P8 转配置项 */
    public static final int MINION_DISSOLVE_FLAME_COUNT = 4;

    /** 消散粒子的扩散（格）：0.35（裹住整具躯体，而不是从一点冒出）。待调手感值 / P8 转配置项 */
    public static final double MINION_DISSOLVE_SPREAD = 0.35D;

    /** 消散粒子的初速：0.04（几乎原地飘散）。待调手感值 / P8 转配置项 */
    public static final double MINION_DISSOLVE_SPEED = 0.04D;

    /** 消散粒子高度 = 召唤物身高 × 该比例：0.5（躯干中部，与死亡灵魂外逸同口径）。待调手感值 / P8 转配置项 */
    public static final double MINION_DISSOLVE_HEIGHT_RATIO = 0.5D;

    // ---------------------------------------------------------------- 死亡灵魂外逸

    /** 灵魂外逸的发射间隔（tick）：2（死亡演出 50 tick ⇒ 约 25 帧，铺满整段演出）。待调手感值 / P8 转配置项 */
    public static final int DEATH_SOUL_INTERVAL_TICKS = 2;

    /** 灵魂外逸每簇粒子数：4。待调手感值 / P8 转配置项 */
    public static final int DEATH_SOUL_COUNT = 4;

    /** 灵魂外逸的扩散（格）：0.4。待调手感值 / P8 转配置项 */
    public static final double DEATH_SOUL_SPREAD = 0.4D;

    /** 灵魂外逸的初速：0.05。待调手感值 / P8 转配置项 */
    public static final double DEATH_SOUL_SPEED = 0.05D;

    /** 灵魂外逸的高度 = 身高 × 该比例：0.5（从躯干起，不从脚底起）。待调手感值 / P8 转配置项 */
    public static final double DEATH_SOUL_HEIGHT_RATIO = 0.5D;

    /** 演出全程灵魂抬升的总高度（格）：2.4（末帧比首帧高这么多，"上升"靠发射高度递增表达）。待调手感值 / P8 转配置项 */
    public static final double DEATH_SOUL_RISE = 2.4D;

    private AgaitolosActionFx() {
    }

    // ---------------------------------------------------------------- 语义入口

    /**
     * 普攻命中：正面一道弧形刃痕 + 一记原版 {@code SWEEP_ATTACK}。
     * <p>
     * <b>为什么既画采样弧又放 SWEEP_ATTACK</b>：采样弧负责<b>朝向与颜色</b>（沿 BOSS 正面铺点，
     * 用识别色青蓝），{@code SWEEP_ATTACK} 是原版唯一的"挥砍轨迹"粒子（原版横扫之刃同款），
     * 负责<b>形状</b>——它由客户端绘制成一个固定的弧片，给出"刀已经挥完"的瞬时读数。
     * 二者缺一个都会退化成"一圈发光点"或"凭空的白色弧"。
     * <p>
     * 挂在 {@code AgaitolosEntity#doHurtTarget} 命中分支（与 {@code playAttack} 同刻）：
     * 那里是"这一刀真的造成了伤害"的唯一语义点；挂在动画触发处会让空挥也冒特效。
     */
    public static void meleeSlash(AgaitolosEntity boss) {
        if (!(boss.level() instanceof ServerLevel level)) {
            return;
        }
        double yaw = facingYaw(boss);
        emitArc(level, boss, yaw, SLASH_ARC_DEGREES, SLASH_ARC_RADIUS, SLASH_ARC_HEIGHT_RATIO,
                SLASH_ARC_FORWARD, SLASH_ARC_POINTS, ParticleTypes.SOUL_FIRE_FLAME, 1);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, boss.getX(),
                boss.getY() + boss.getBbHeight() * SLASH_ARC_HEIGHT_RATIO, boss.getZ(),
                1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    /**
     * 召唤凋零头：掌心聚集（紫雾）+ 离手弹道（青蓝焰点串）。
     * <p>
     * <b>为什么聚集与离手合成一个入口</b>：本招由 {@code AgaitolosSkillDirector} 在选中"远程"的那一拍
     * 一次性发出（间隔由 {@code AgaitolosEntity#rangedCooldownTicks} 持有，状态机里<b>不存在</b>
     * "抬手蓄势"的窗口），服务端无从分帧表现"聚集 → 离手"。故两段同刻发射：掌心簇读作"能量在掌中成形"，
     * 弹道串读作"离手飞出"，真正的抬手动作是 {@code cast_skull} 动画的职责（分工与 {@link AgaitolosFx} 一致：
     * 骨骼归动画，粒子归本类）。
     * <p>
     * 挂在 {@code AgaitolosEntity#performRangedAttack}（弹体生成之后）：发射是既成事实，
     * 粒子只做"已发生"的注脚；放在弹体生成前若中途 return 会留下无弹体的空手特效。
     */
    public static void skullCast(AgaitolosEntity boss, LivingEntity target) {
        if (!(boss.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 hand = handPosition(boss);
        level.sendParticles(ParticleTypes.DRAGON_BREATH, hand.x, hand.y, hand.z, CAST_GATHER_COUNT,
                CAST_GATHER_SPREAD, CAST_GATHER_SPREAD, CAST_GATHER_SPREAD, CAST_GATHER_SPEED);
        // 离手方向：手 → 目标身体中部（与 AgaitolosSkullSkill#fire 的瞄准口径一致，特效才不会与弹体分叉）
        Vec3 aim = new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ()).subtract(hand);
        if (aim.lengthSqr() <= HORIZONTAL_EPSILON_SQR) {
            return;
        }
        Vec3 direction = aim.normalize();
        for (int i = 1; i <= CAST_TRAIL_POINTS; ++i) {
            double distance = i * CAST_TRAIL_STEP;
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    hand.x + direction.x * distance, hand.y + direction.y * distance, hand.z + direction.z * distance,
                    1, CAST_TRAIL_SPREAD, CAST_TRAIL_SPREAD, CAST_TRAIL_SPREAD, ARC_POINT_SPEED);
        }
    }

    /**
     * 俯冲拖尾：冲锋期间每 tick 在<b>身后</b>撒一簇青蓝焰点（由 {@code AgaitolosEntity#tickDiveCharge} 调用）。
     * <p>
     * <b>为什么按速度反方向后退发射点</b>：拖尾的意义是"人过去了、光还在"，点在身上冒只会变成
     * "一团自己在闪"。速度取自 {@code getDeltaMovement()}（冲锋每 tick 都写入），
     * 退化（速度≈0，例如被地形卡住）时就地发射，不额外扫描地形。
     */
    public static void diveTrail(AgaitolosEntity boss) {
        if (!(boss.level() instanceof ServerLevel level)) {
            return;
        }
        if (boss.tickCount % DIVE_TRAIL_INTERVAL_TICKS != 0) {
            return;
        }
        double x = boss.getX();
        double y = boss.getY() + boss.getBbHeight() * DIVE_TRAIL_HEIGHT_RATIO;
        double z = boss.getZ();
        Vec3 motion = boss.getDeltaMovement();
        if (motion.lengthSqr() > HORIZONTAL_EPSILON_SQR) {
            Vec3 back = motion.normalize().scale(-DIVE_TRAIL_BACK);
            x += back.x;
            y += back.y;
            z += back.z;
        }
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, DIVE_TRAIL_COUNT,
                DIVE_TRAIL_SPREAD, DIVE_TRAIL_SPREAD, DIVE_TRAIL_SPREAD, CAST_GATHER_SPEED);
    }

    /**
     * 俯冲收尾：横向扫过的镰刀弧；<b>有人被扫到</b>时再叠一圈冲击环 + 地面尘。
     * <p>
     * <b>为什么环的高度不能用"悬停高度"换算</b>：{@link AgaitolosFx#introShockRing} 用
     * {@code 脚部 - 悬停高度} 是因为出场演出结束时 BOSS 恰好停在悬停高度上；而俯冲收尾时
     * BOSS 已降到目标身体中部（约地面），若照抄那条换算会把整圈粒子埋进地里 2 格。
     * 故这里以<b>当前脚部 + {@link #SWEEP_IMPACT_GROUND_LIFT}</b> 为准（收尾高度即冲击高度）。
     *
     * @param hit 本次横扫是否扫到玩家（由 {@code AgaitolosDiveSweepSkill#perform} 的返回值给出）——
     *            空挥只出刀光，扫到了才有冲击，玩家才能从粒子分辨"这一刀落没落空"
     */
    public static void diveSweepImpact(AgaitolosEntity boss, boolean hit) {
        if (!(boss.level() instanceof ServerLevel level)) {
            return;
        }
        double yaw = facingYaw(boss);
        emitArc(level, boss, yaw, SWEEP_ARC_DEGREES, SWEEP_ARC_RADIUS, SWEEP_ARC_HEIGHT_RATIO,
                0.0D, SWEEP_ARC_POINTS, ParticleTypes.SOUL_FIRE_FLAME, 1);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, boss.getX(),
                boss.getY() + boss.getBbHeight() * SWEEP_ARC_HEIGHT_RATIO, boss.getZ(),
                1, 0.0D, 0.0D, 0.0D, 0.0D);
        if (!hit) {
            return;
        }
        // 冲击环：手写采样点（sendParticles 的偏移是高斯分布，永远造不出中空的"环"，理由同 AgaitolosFx#introShockRing）
        double groundY = boss.getY() + SWEEP_IMPACT_GROUND_LIFT;
        for (int i = 0; i < SWEEP_IMPACT_POINTS; ++i) {
            double angle = (Math.PI * 2.0D) * i / SWEEP_IMPACT_POINTS;
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    boss.getX() + Math.cos(angle) * SWEEP_IMPACT_RADIUS, groundY,
                    boss.getZ() + Math.sin(angle) * SWEEP_IMPACT_RADIUS,
                    SWEEP_IMPACT_COUNT, SWEEP_IMPACT_SPREAD, SWEEP_IMPACT_SPREAD, SWEEP_IMPACT_SPREAD,
                    SWEEP_IMPACT_SPEED);
        }
        level.sendParticles(ParticleTypes.CLOUD, boss.getX(), groundY, boss.getZ(), SWEEP_IMPACT_DUST_COUNT,
                SWEEP_IMPACT_RADIUS, SWEEP_IMPACT_DUST_SPREAD_Y, SWEEP_IMPACT_RADIUS, CAST_GATHER_SPEED);
    }

    /**
     * 格挡架势的护盾纹：架势期间每 tick 刷新一次正面弧上的青蓝点（由 {@code AgaitolosEntity#tickGuard} 调用）。
     * <p>
     * <b>为什么角度与格挡锥同为 120°</b>：架势能挡下的是正面 120°（{@code AgaitolosGuardSkill#GUARD_ARC_DEGREES}），
     * 纹路若比判定宽，玩家会以为"背后也挡得住"；窄了又会误判"正面漏挡"。两者同宽即"亮到哪里挡到哪里"。
     * <p>只按 {@code tickCount} 节流，不额外读架势剩余 tick：架势是状态轮询式的，
     * 实体在本 tick 撤了架势就不会调到这里（调用点在撤架势分支之后），本类无需知道还剩几 tick。
     */
    public static void guardAura(AgaitolosEntity boss) {
        if (!(boss.level() instanceof ServerLevel level)) {
            return;
        }
        if (boss.tickCount % GUARD_AURA_INTERVAL_TICKS != 0) {
            return;
        }
        emitArc(level, boss, facingYaw(boss), GUARD_ARC_DEGREES, GUARD_ARC_RADIUS, GUARD_ARC_HEIGHT_RATIO,
                0.0D, GUARD_ARC_POINTS, ParticleTypes.SOUL_FIRE_FLAME, 1);
    }

    /**
     * 格挡成功的火花：在<b>伤害来源位置</b>炸一簇电火花（由 {@code AgaitolosEntity#hurt} 的格挡分支调用）。
     * <p>
     * <b>为什么取 {@code source.getSourcePosition()} 而不是玩家位置</b>：那是原版"伤害从哪来"的定义，
     * 与 {@code AgaitolosGuardSkill#isWithinFrontArc} 的判据同源 ⇒ 火花一定落在被挡下的那一侧，
     * 玩家能直接看出"是从这个方向被打掉的"。
     * <p>退化为 null（三参构造器的经典坑）时就地以躯干为中心发射，绝不静默不发光。
     */
    public static void guardSpark(AgaitolosEntity boss, DamageSource source) {
        if (!(boss.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 from = source.getSourcePosition();
        double x = from != null ? from.x : boss.getX();
        double y = from != null ? from.y : boss.getY() + boss.getBbHeight() * HURT_FEEDBACK_HEIGHT_RATIO;
        double z = from != null ? from.z : boss.getZ();
        // ELECTRIC_SPARK：原版青蓝电火花，是识别色族里最像"金属相击"的一个
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, GUARD_SPARK_COUNT,
                GUARD_SPARK_SPREAD, GUARD_SPARK_SPREAD, GUARD_SPARK_SPREAD, GUARD_SPARK_SPEED);
    }

    /**
     * 受击反馈：伤害真正落地时躯干迸出一簇魔法命中点 + 几颗青蓝焰点
     * （由 {@code AgaitolosEntity#hurt} 的 {@code applied} 分支调用，与 {@code playHurt} 同刻）。
     * <p>选 {@code ENCHANTED_HIT}（附魔命中，蓝白）而非 {@code CRIT}（偏暖）：前者落在识别色族内，
     * 后者会把这套冷色体系拉出橙调。
     */
    public static void hurtFeedback(AgaitolosEntity boss) {
        if (!(boss.level() instanceof ServerLevel level)) {
            return;
        }
        double torsoY = boss.getY() + boss.getBbHeight() * HURT_FEEDBACK_HEIGHT_RATIO;
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, boss.getX(), torsoY, boss.getZ(), HURT_FEEDBACK_COUNT,
                HURT_FEEDBACK_SPREAD, HURT_FEEDBACK_SPREAD, HURT_FEEDBACK_SPREAD, HURT_FEEDBACK_SPEED);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, boss.getX(), torsoY, boss.getZ(),
                HURT_FEEDBACK_FLAME_COUNT, HURT_FEEDBACK_SPREAD, HURT_FEEDBACK_SPREAD, HURT_FEEDBACK_SPREAD,
                HURT_FEEDBACK_SPEED);
    }

    /**
     * 死亡演出期间的灵魂外逸：青色魂点从躯干逐帧升高地逸出（由 {@code AgaitolosEntity#tickDeath} 每 tick 调用）。
     * <p>
     * <b>为什么"上升"要靠发射高度递增</b>：原版 {@code SOUL} 粒子自身只做随机飘移、并<b>没有</b>持续的向上加速度
     * （与 {@code SOUL_FIRE_FLAME} 同属"不设 gravity"的那一类），指望粒子物理往上跑会看到"魂在原地打转"；
     * 故按演出进度 {@code progress × }{@link #DEATH_SOUL_RISE} 逐帧抬高发射点，30 帧下来读作一条向上逸散的灵魂带。
     * <p>
     * 死亡爆发（{@link AgaitolosFx#deathBurst}）仍是死亡瞬间的那一次性爆发，本入口负责它之后的整段演出，
     * 两者不重叠（一个在某 tick 只发一次，一个逐帧）。
     *
     * @param deathAgeTicks 已死亡 tick 数（{@code entity.deathTime}，0 起），用于推进上升高度与节流
     */
    public static void deathSoulRise(AgaitolosEntity boss, int deathAgeTicks) {
        if (!(boss.level() instanceof ServerLevel level)) {
            return;
        }
        if (deathAgeTicks < 0 || deathAgeTicks % DEATH_SOUL_INTERVAL_TICKS != 0) {
            return;
        }
        // 分母为演出时长常量（= 50），恒 > 0
        double progress = Math.min(1.0D, deathAgeTicks / (double) AgaitolosEntity.DEATH_TICKS);
        level.sendParticles(ParticleTypes.SOUL, boss.getX(),
                boss.getY() + boss.getBbHeight() * DEATH_SOUL_HEIGHT_RATIO + progress * DEATH_SOUL_RISE, boss.getZ(),
                DEATH_SOUL_COUNT, DEATH_SOUL_SPREAD, DEATH_SOUL_SPREAD, DEATH_SOUL_SPREAD, DEATH_SOUL_SPEED);
    }

    /**
     * 召唤物消散：整队回收（{@code AgaitolosMinionSkill#dismissAll}）时在每只召唤物躯干处放一簇魂焰，
     * 让"它们是被技能收走的，不是凭空不见的"这件事可见。
     * <p>
     * <b>为什么复用 {@code SOUL} + {@code SOUL_FIRE_FLAME}</b>：与死亡灵魂外逸
     * （{@link #deathSoulRise}）同一选型 —— 都是"灵魂在逸散"，且同属 BOSS 识别色族；
     * 不新增 {@code ParticleType}（原版粒子够用，注册新粒子要配套资源与语言键，属负收益）。
     * <p>
     * 与 {@code AgaitolosReversalSkill#drainMinions} 的 {@code kill()} <b>不重叠</b>：
     * 那条路径走完整死亡流程（自带死亡粒子/音效），本入口只服务 {@code discard()} 这种静默回收
     * （被吸走的那些小怪走的是原版死亡表现，不会再叠一簇消散魂点）。
     *
     * @param minion 被回收的召唤物（取它自己的世界与服务端判定，不依赖 BOSS 还活着）
     */
    public static void minionDissolve(LivingEntity minion) {
        if (!(minion.level() instanceof ServerLevel level)) {
            return;
        }
        double torsoY = minion.getY() + minion.getBbHeight() * MINION_DISSOLVE_HEIGHT_RATIO;
        level.sendParticles(ParticleTypes.SOUL, minion.getX(), torsoY, minion.getZ(),
                MINION_DISSOLVE_SOUL_COUNT, MINION_DISSOLVE_SPREAD, MINION_DISSOLVE_SPREAD,
                MINION_DISSOLVE_SPREAD, MINION_DISSOLVE_SPEED);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, minion.getX(), torsoY, minion.getZ(),
                MINION_DISSOLVE_FLAME_COUNT, MINION_DISSOLVE_SPREAD, MINION_DISSOLVE_SPREAD,
                MINION_DISSOLVE_SPREAD, MINION_DISSOLVE_SPEED);
    }

    // ---------------------------------------------------------------- 共用实现

    /**
     * 沿"正面扇面"采样发射粒子弧。
     * <p>
     * <b>为什么手写采样点</b>：{@code ServerLevel#sendParticles} 的位置偏移是<b>高斯</b>偏移，
     * 无论怎么给参都只会得到一个中心最密的团/盘，造不出有朝向的"弧"⇒ 只能沿弧取点、逐点发射。
     * 点数因此也是性能参数：每点一次就是一轮"给追踪到该位置的每个玩家发一个粒子包"。
     *
     * @param yawRadians  正面朝向（弧度，约定与 {@code Entity#getYRot} 一致：方向 = (-sin, cos)）
     * @param arcDegrees  弧的总张角（度）
     * @param radius      弧半径（格）
     * @param heightRatio 弧高 = 身高 × 该比例
     * @param forward     整条弧沿朝向前移的距离（格）
     * @param points      采样点数（1 时退化为弧中点）
     * @param countPerPoint 每点粒子数
     * <p>可见性为包内可见（而非 private）：阶段三五招的表现层
     * （{@code AgaitolosPhaseThreeFx} 的镰扫出刀弧）复用这同一段取点算式，
     * 而不是复制第二份"沿扇面采样"的实现（本项目"同一口径只有一处实现"的常规做法）。
     */
    static void emitArc(ServerLevel level, AgaitolosEntity boss, double yawRadians, double arcDegrees,
                               double radius, double heightRatio, double forward, int points,
                               ParticleOptions type, int countPerPoint) {
        double halfArc = Math.toRadians(arcDegrees) * 0.5D;
        Vec3 look = boss.getLookAngle();
        double y = boss.getY() + boss.getBbHeight() * heightRatio;
        for (int i = 0; i < points; ++i) {
            // points == 1 时取弧中点（t = 0.5），避免除零
            double t = points == 1 ? 0.5D : i / (double) (points - 1);
            double angle = yawRadians + (t - 0.5D) * 2.0D * halfArc;
            double dirX = -Math.sin(angle);
            double dirZ = Math.cos(angle);
            level.sendParticles(type,
                    boss.getX() + dirX * radius + look.x * forward,
                    y,
                    boss.getZ() + dirZ * radius + look.z * forward,
                    countPerPoint, ARC_POINT_SPREAD, ARC_POINT_SPREAD, ARC_POINT_SPREAD, ARC_POINT_SPEED);
        }
    }

    /**
     * BOSS 的水平朝向角（弧度，方向 = {@code (-sin, cos)}，与 {@code Entity#getYRot} 同约定）。
     * <p>
     * 退化处理与 {@code AgaitolosDiveSweepSkill#isWithinSweep} 同款：视线水平分量≈0（俯冲/仰视到近乎垂直）时
     * 回退到 yaw 推出的水平朝向 —— yaw 恒有定义，避免"整整一招的特效全挤在一个点上"这种静默失效。
     * 返回基本类型而非向量：呼叫点都在逐帧路径上，不额外产生小对象。
     * <p>包内可见：阶段三五招的镰扫出刀弧（{@code AgaitolosPhaseThreeFx}）复用同一份朝向算式。
     */
    static double facingYaw(AgaitolosEntity boss) {
        Vec3 look = boss.getLookAngle();
        if (look.x * look.x + look.z * look.z < HORIZONTAL_EPSILON_SQR) {
            return Math.toRadians(boss.getYRot());
        }
        return Math.atan2(-look.x, look.z);
    }

    /**
     * 掌心（施法点）的世界坐标近似。
     * <p>
     * 与 {@link AgaitolosFx#orbPosition} 是同一局限下的同一手法（服务端拿不到 GeckoLib 骨骼坐标，
     * 只能按碰撞箱 + 视线朝向推算），但取<b>各自的常量</b>：蓄力球与凋零头的手位会分别微调手感，
     * 共用一个常量会让"只调其中一招"变成改另一个。
     * <p>包内可见：三重投掷/饱和轰炸的离手点（{@code AgaitolosPhaseThreeFx}）复用同一份手位算式
     * 与同一组 {@link #CAST_HAND_HEIGHT_RATIO}/{@link #CAST_HAND_FORWARD} 常量 ——
     * 手位一变，弹道与三处特效同时跟着变，不会分叉。
     */
    static Vec3 handPosition(AgaitolosEntity boss) {
        Vec3 look = boss.getLookAngle();
        return new Vec3(
                boss.getX() + look.x * CAST_HAND_FORWARD,
                boss.getY() + boss.getBbHeight() * CAST_HAND_HEIGHT_RATIO,
                boss.getZ() + look.z * CAST_HAND_FORWARD);
    }
}
