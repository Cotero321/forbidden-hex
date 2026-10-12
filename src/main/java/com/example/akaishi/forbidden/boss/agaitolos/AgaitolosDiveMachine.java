package com.example.akaishi.forbidden.boss.agaitolos;

import com.example.akaishi.forbidden.boss.agaitolos.skill.AgaitolosDiveSweepSkill;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 阿盖托洛丝的<b>俯冲镰扫状态机</b>（一阶段：冲锋位移 + 惩罚收尾结算）：
 * 两段式里的"冲锋中"推进、抵达/超时收尾、执行入口与起手距离探针。
 * <p>
 * 从 {@link AgaitolosEntity} 机械搬出（2026-09-24 拆分轮）：冲锋是唯一一段"本类直接写 deltaMovement、
 * {@code AgaitolosMoveControl} 全让位"的招式位移，与伤害结算（{@link AgaitolosDiveSweepSkill#perform}）
 * 本就分属两侧；收进本类后"位移怎么写、何时结算、何时进冷却"只有一处可看。
 * 计时字段仍归实体持有（包私有，由本类读写）。
 * <p>起手上界常量 {@link AgaitolosEntity#DIVE_TRIGGER_RANGE} 仍在实体上（决策层打分层直接引用它），
 * 其余俯冲手感常量随本类搬迁。
 */
public final class AgaitolosDiveMachine {

    private AgaitolosDiveMachine() {
    }

    // ---------------------------------------------------------------- 冲锋常量

    /** 俯冲镰扫的冲锋起手最小水平距离（格）：太近就不俯冲，交给普攻/格挡。待调手感值 / P8 转配置项 */
    public static final double DIVE_MIN_RANGE = 3.0D;

    /** 冲锋最长持续（tick）：1s。到点无论是否抵达都收尾并结算一次横扫，避免冲锋把其它行为锁死。待调手感值 / P8 转配置项 */
    public static final int DIVE_MAX_TICKS = 20;

    /**
     * 冲锋速度（格/tick）：1.2。
     * <p>取值依据：规格要求"<b>快速</b>飞行至玩家处"，起手距离上限 16 格，需在 1 秒（20 tick）内跨越十几格
     * ⇒ 均速需 ≥ 16 / 20 = 0.8 格/tick；取 1.2 留出末段转向与绕障余量（16 格约 13 tick 抵达），
     * 又不至于瞬移（≈24 格/s，快于末影珍珠但可目视追踪）。待调手感值 / P8 转配置项
     */
    public static final double DIVE_SPEED = 1.2D;

    /** 冲锋抵达判定：与目标点的水平距离 ≤ 该值（格）即视为"已到玩家处"。待调手感值 / P8 转配置项 */
    public static final double DIVE_ARRIVE_DISTANCE = 1.0D;

    // ---------------------------------------------------------------- 状态探针

    /** 是否正在冲锋（俯冲位移中，服务端权威）：冲锋期间位移由本类直接写，{@code AgaitolosMoveControl} 全让位 */
    public static boolean isDiving(AgaitolosEntity boss) {
        return boss.diveTicks > 0;
    }

    /**
     * 只读探针：是否满足冲锋起手距离 —— 与目标的<b>水平</b>距离落在
     * [{@link #DIVE_MIN_RANGE}, {@link AgaitolosEntity#DIVE_TRIGGER_RANGE}]。
     * <p>用水平距离而非 3D 距离：悬停高度不该影响"够不够近"（沿用被替换掉的旧起手判定的口径）。
     * 超过上界交给飞行巡航接近；小于下界交给普攻/格挡 —— 贴脸再俯冲既无位移意义，也会把贴身战搅成连招。
     * <p>供 {@link AgaitolosSkillDirector} 打分与 {@link #startDiveSweep} 复校共用（同一口径，不复制第二份）。
     */
    static boolean isWithinDiveTriggerRange(AgaitolosEntity boss, LivingEntity target) {
        double deltaX = target.getX() - boss.getX();
        double deltaZ = target.getZ() - boss.getZ();
        double distanceSqr = deltaX * deltaX + deltaZ * deltaZ;
        return distanceSqr >= DIVE_MIN_RANGE * DIVE_MIN_RANGE
                && distanceSqr <= AgaitolosEntity.DIVE_TRIGGER_RANGE * AgaitolosEntity.DIVE_TRIGGER_RANGE;
    }

    // ---------------------------------------------------------------- 每 tick 推进（aiStep 调用）

    /**
     * 俯冲镰扫的<b>进行中状态</b>推进（服务端权威，每 tick 一次）：冲锋位移 → 抵达/超时收尾结算。
     * <p>
     * 两段式里的"冲锋中"这一段（{@link #isDiving} → {@link #tickDiveCharge}）留在这里；
     * <b>起手判定已移交</b> {@link AgaitolosSkillDirector}（"这一拍选不选俯冲"由决策层掷骰决定，
     * 冷却/封印/水平距离这三项也由它打分），执行入口见 {@link #startDiveSweep}。
     * <p>
     * 位移本体在 {@link #tickDiveCharge}（本类直接写 {@code deltaMovement}，{@code AgaitolosMoveControl} 全让位）；
     * 伤害结算与范围筛选全部收在 {@link AgaitolosDiveSweepSkill#perform(AgaitolosEntity)}。
     */
    static void tickDiveState(AgaitolosEntity boss) {
        // 死亡/复活/出场演出期间中断冲锋：不结算横扫（演出期间不该出手），也不让计时残留到下一条命
        if (boss.isDeadOrDying() || boss.isRespawning() || boss.isIntroPlaying()) {
            boss.diveTicks = 0;
            return;
        }
        if (isDiving(boss)) {
            tickDiveCharge(boss);
        }
    }

    /**
     * 执行入口：起手俯冲镰扫（由 {@link AgaitolosSkillDirector} 在"这一拍选中俯冲"时调用）。
     * <p>前置复校与决策层打分同源：冷却未过 / 正举着架势 / 蓄力中 / 被格挡后封印期内 /
     * 水平距离不在 [{@link #DIVE_MIN_RANGE}, {@link AgaitolosEntity#DIVE_TRIGGER_RANGE}] —— 任一成立都不起手。
     * 封印期只封这一招（普攻与凋零头照常），作用范围见 {@link AgaitolosEntity#isScytheSealed()} 的 javadoc。
     *
     * @return 是否真的起手（被上面任一条挡下时为 false，此时不消耗全局节拍）
     */
    static boolean startDiveSweep(AgaitolosEntity boss, LivingEntity target) {
        if (boss.diveSweepCooldownTicks > 0 || boss.isGuarding() || boss.isCharging()
                || boss.isScytheSealed() || boss.isRespawning() || boss.isIntroPlaying()
                || target == null || !target.isAlive() || !isWithinDiveTriggerRange(boss, target)) {
            return false;
        }
        boss.diveTicks = DIVE_MAX_TICKS;
        // 纯表现：冲锋起手即播。dive_sweep clip 本身覆盖「俯冲 + 横扫」两段，
        // 故冲锋收尾结算时<b>不再</b>重复触发（否则横扫段会被打断重播）。
        AgaitolosAnimations.playDiveSweep(boss);
        return true;
    }

    /**
     * 冲锋中每 tick：朝目标点（身体中部）高速推进。
     * <p>水平与垂直<b>都</b>朝目标收敛：只修水平的话，常态悬停的 2 格竖直差永远消不掉，
     * "抵达"判定不可能成立，冲锋会次次超时。垂直修正只在冲锋期间做，不破坏常态悬停
     * （常态悬停仍由 {@code AgaitolosMoveControl} ③ 负责，且它在冲锋期间整体让位）。
     * <p>写入时机：本方法在 {@code aiStep} 的 {@code super.aiStep()} 之后执行，而 {@code travel} 在
     * {@code super.aiStep()} 内部、{@code MoveControl#tick} 之后 —— 即本 tick 写的速度由下一 tick 的
     * {@code travel} 消费，与 {@code AgaitolosMoveControl} 的写入时机同层，不存在额外的"晚一帧"偏差。
     */
    private static void tickDiveCharge(AgaitolosEntity boss) {
        --boss.diveTicks;
        LivingEntity target = boss.getTarget();
        if (target == null || !target.isAlive() || boss.diveTicks <= 0) {
            // 目标丢失/目标死亡/超时：原地收尾并结算一次横扫。
            // 超时是"被地形挡住或目标一直跑"的兜底，保证冲锋不会永久锁住状态机。
            finishDive(boss);
            return;
        }
        Vec3 aim = diveAimPoint(target);
        // 纯表现：只在"真的在推进"的 tick 拖尾（上面的收尾分支已 return，故起手/收尾帧不会拖一条假尾）
        AgaitolosActionFx.diveTrail(boss);
        double deltaX = aim.x - boss.getX();
        double deltaY = aim.y - boss.getY();
        double deltaZ = aim.z - boss.getZ();
        double horizontalSqr = deltaX * deltaX + deltaZ * deltaZ;
        if (horizontalSqr <= DIVE_ARRIVE_DISTANCE * DIVE_ARRIVE_DISTANCE) {
            finishDive(boss);
            return;
        }
        // 按 3D 方向归一化后乘速度：竖直分量也在内，故水平速度会随俯仰角自然减小。
        // 速度再乘阶段倍率（AgaitolosPace）：规格"二阶段比一阶段更加快速"，阶段一恒为 1.0 不受影响
        double length = Math.sqrt(horizontalSqr + deltaY * deltaY);
        // length > DIVE_ARRIVE_DISTANCE > 0，不会除零
        double scale = DIVE_SPEED * AgaitolosPace.moveSpeed(boss) / length;
        boss.setDeltaMovement(deltaX * scale, deltaY * scale, deltaZ * scale);
    }

    /** 冲锋收尾：清零计时并结算一次横扫；有人落入范围才进冷却（口径沿用上一轮：被格挡也算"这一刀挥出去了"） */
    private static void finishDive(AgaitolosEntity boss) {
        boss.diveTicks = 0;
        boolean swept = AgaitolosDiveSweepSkill.perform(boss);
        // 纯表现：挥刀这一帧先落特效，再判冷却 —— 结算结果只决定"空挥还是命中"（特效强度），
        // 不影响冷却口径（仍是 swept 决定），故顺序只关乎观感：让刀光与伤害同帧出现
        AgaitolosActionFx.diveSweepImpact(boss, swept);
        if (swept) {
            // 冷却按阶段折算（二阶段起更短）：倍率表见 AgaitolosPace，阶段一恒为原值
            boss.diveSweepCooldownTicks = AgaitolosPace.scaledCooldown(boss, AgaitolosDiveSweepSkill.SWEEP_COOLDOWN_TICKS);
        }
    }

    /** 冲锋瞄准点：目标<b>身体中部</b>（脚 + 半身高）——瞄脚会让 BOSS 一路贴地，瞄眼睛又会悬太高 */
    private static Vec3 diveAimPoint(LivingEntity target) {
        return new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ());
    }
}
