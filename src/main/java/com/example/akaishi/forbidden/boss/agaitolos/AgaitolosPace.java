package com.example.akaishi.forbidden.boss.agaitolos;

/**
 * 阿盖托洛丝的<b>阶段节奏表</b>：把规格「二阶段拥有一阶段所有技能，且比一阶段更加快速」
 * （设计文档 §0 阶段二）收成<b>唯一一份</b>「按阶段取值」的倍率。
 * <p>
 * <b>为什么必须集中在一处</b>：本表同时被移动控制器（{@code AgaitolosMoveControl} 的水平/垂直限速）、
 * 冲锋速度（{@code AgaitolosEntity#tickDiveCharge}）以及各技能冷却（俯冲镰扫 / 格挡 / 恶怨倒转 /
 * 瞬击 / 高速踢击）消费。倍率若散写进各个技能，调一次"阶段二多快"要改五处，
 * 必然出现某招漏改 —— 这正是本项目历史上反复出现的"两套口径"病。
 * <p>
 * <b>阶段一恒为 1.0</b>：规格只要求"二阶段更快速"，一阶段是既定基线、手感不得被本轮波及。
 * 两项倍率在阶段一都取 1.0，乘算后与原值逐位相同（{@link #scaledCooldown} 用
 * {@code Math.round(base × 1.0) == base}）。复活阶段（RESPAWN）不是战斗阶段，同样按 1.0 处理
 * —— 无敌演出期间不该悄悄提速。
 * <p>
 * <b>只作用于"出手节奏"，不作用于"惩罚时长"</b>：被格挡后的禁飞/封印
 * （{@code AgaitolosEntity#onSweepBlocked}）是玩家争取来的收益窗口，属于惩罚语义而非节奏，
 * 阶段二不该把它缩短（缩短等于变相削弱玩家的格挡收益），故刻意不走本表。
 */
public final class AgaitolosPace {

    // ---------------------------------------------------------------- 移动速度倍率（待调手感值 / P8 转配置项）

    /** 阶段一移动速度倍率：<b>基准 1.0，不可改</b>（"更快速"只针对二阶段起） */
    private static final double PHASE_1_MOVE_SPEED = 1.0D;

    /** 阶段二移动速度倍率 1.25（+25%）：明显更快，又不至于变成"换了一只怪" */
    private static final double PHASE_2_MOVE_SPEED = 1.25D;

    /** 阶段三移动速度倍率 1.4（+40%）：与规格阶段三"速度更快"同向 */
    private static final double PHASE_3_MOVE_SPEED = 1.4D;

    // ---------------------------------------------------------------- 技能冷却系数（待调手感值 / P8 转配置项）

    /** 阶段一技能冷却系数：<b>基准 1.0，不可改</b> */
    private static final double PHASE_1_COOLDOWN_SCALE = 1.0D;

    /** 阶段二技能冷却系数 0.75（冷却缩短 25% ⇒ 出手更密） */
    private static final double PHASE_2_COOLDOWN_SCALE = 0.75D;

    /** 阶段三技能冷却系数 0.6（冷却缩短 40%） */
    private static final double PHASE_3_COOLDOWN_SCALE = 0.6D;

    private AgaitolosPace() {
    }

    /**
     * 该阶段的移动速度倍率（含冲锋速度与悬停升降速率）。
     * <p>乘数落点（三处，全部读本方法，不各自硬写）：
     * {@code AgaitolosMoveControl} 的水平加速度 / 水平限速 / 垂直限速、
     * {@code AgaitolosEntity#tickDiveCharge} 的 {@code DIVE_SPEED}。
     */
    public static double moveSpeed(AgaitolosPhase phase) {
        switch (phase) {
            case PHASE_2:
                return PHASE_2_MOVE_SPEED;
            case PHASE_3:
                return PHASE_3_MOVE_SPEED;
            default:
                // PHASE_1 与 RESPAWN：基准 1.0
                return PHASE_1_MOVE_SPEED;
        }
    }

    /**
     * 该阶段的技能冷却系数（&lt; 1 表示冷却更短）。
     * <p>消费方：俯冲镰扫 / 格挡 / 恶怨倒转 / 瞬击 / 高速踢击（各技能类里的 COOLDOWN 常量经
     * {@link #scaledCooldown} 折算）、普攻间隔（{@code AgaitolosMeleeSkill#MELEE_INTERVAL_TICKS}）、
     * 远程间隔（{@code AgaitolosSkullSkill#SKULL_COOLDOWN_TICKS}），以及
     * {@code AgaitolosSkillDirector} 的出手节拍（{@code BEAT_TICKS_BASE}）。
     * <p>
     * <b>2026-09-21 补</b>：普攻/远程的间隔原先由原版 {@code MeleeAttackGoal}/{@code RangedAttackGoal}
     * 内部计时（本表管不到，属"二阶段更快"的一个缺口）；现在两个间隔都由 {@code AgaitolosEntity}
     * 显式持有，于是"二阶段更快"覆盖<b>移动 + 全部招式冷却 + 出手节拍</b>三件事，没有漏网的招。
     */
    public static double cooldownScale(AgaitolosPhase phase) {
        switch (phase) {
            case PHASE_2:
                return PHASE_2_COOLDOWN_SCALE;
            case PHASE_3:
                return PHASE_3_COOLDOWN_SCALE;
            default:
                return PHASE_1_COOLDOWN_SCALE;
        }
    }

    /** 移动速度倍率（实体重载：控制器与冲锋处直接传实体） */
    public static double moveSpeed(AgaitolosEntity boss) {
        return moveSpeed(boss.getPhase());
    }

    /**
     * 把技能的基础冷却（tick）按当前阶段折算。
     * <p>
     * 下限夹到 1 tick：倍率将来若被配置项调成极端值（例如 0.1），也绝不出现"0 冷却 ⇒ 每 tick 起手"
     * 这种失控形态；阶段一返回原值（{@code round(base × 1.0) == base}）。
     */
    public static int scaledCooldown(AgaitolosEntity boss, int baseTicks) {
        return Math.max(1, (int) Math.round(baseTicks * cooldownScale(boss.getPhase())));
    }
}
