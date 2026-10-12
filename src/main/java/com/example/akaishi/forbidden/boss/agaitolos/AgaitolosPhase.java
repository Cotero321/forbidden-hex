package com.example.akaishi.forbidden.boss.agaitolos;

/**
 * 阿盖托洛丝的阶段（纯数据：无逻辑、无副作用）。
 * <p>
 * 复活阶段也是「阶段」之一（设计文档 §1 第 13 条 {@code Phases.RESPAWN}），而不是另开一个布尔开关：
 * 受击口径、阶段推进、将来 P7 的表现层分支都只读同一个字段，不会长出「两套口径」。
 */
public enum AgaitolosPhase {

    /** 复活阶段（不属于战斗阶段）：全程无敌 + 快速回满生命，结束瞬间击飞周围玩家 */
    RESPAWN(0),
    /** 一阶段：生命比例跌破 50% 时进入 PHASE_2 */
    PHASE_1(1),
    /** 二阶段：生命比例跌破 25% 时进入 PHASE_3 */
    PHASE_2(2),
    /** 三阶段（最终）：不再进入下一阶段 */
    PHASE_3(3);

    /** 进入 PHASE_2 所需的剩余生命比例：最大生命的 50% */
    private static final double PHASE_1_THRESHOLD_RATIO = 0.5D;

    /** 进入 PHASE_3 所需的剩余生命比例：最大生命的 25% */
    private static final double PHASE_2_THRESHOLD_RATIO = 0.25D;

    /** 无阈值：不再进入下一阶段 */
    private static final double NO_THRESHOLD = -1.0D;

    /** 序数 → 阶段查表（{@code values()} 每次调用都会克隆数组，故缓存） */
    private static final AgaitolosPhase[] VALUES = values();

    /** 战斗阶段序数：0 让给 RESPAWN，故 PHASE_1 从 1 起；同步数据与存档按它读写 */
    private final int combatOrdinal;

    AgaitolosPhase(int combatOrdinal) {
        this.combatOrdinal = combatOrdinal;
    }

    /** 战斗阶段序数：RESPAWN = 0、PHASE_1 = 1、PHASE_2 = 2、PHASE_3 = 3 */
    public int combatOrdinal() {
        return this.combatOrdinal;
    }

    /**
     * 下一个战斗阶段：PHASE_1 → PHASE_2 → PHASE_3 → PHASE_3。
     * <p>
     * RESPAWN 不是战斗阶段，对它调用属于用法错误；这里返回 PHASE_1 兜底而不是抛异常，避免战斗中崩服。
     */
    public AgaitolosPhase nextCombatPhase() {
        switch (this) {
            case PHASE_1:
                return PHASE_2;
            case PHASE_2:
                return PHASE_3;
            case PHASE_3:
                return PHASE_3;
            default:
                return PHASE_1;
        }
    }

    /**
     * 进入下一阶段所需的<b>剩余生命比例</b>（当前生命 / 最大生命 &lt;= 该值时推进）。
     * <p>
     * 规格书里写的门槛是 722 / 361，那只是 1444 的 50% / 25%；这里按<b>比例</b>写而不是写死这两个数——
     * 将来最大生命会随在场玩家数增强（设计文档 §1 第 11 条）而变化，写死数值会立刻失配。
     *
     * @return PHASE_1 = 0.5、PHASE_2 = 0.25；PHASE_3 与 RESPAWN 返回 -1，表示无阈值、不再进阶段
     */
    public double healthThresholdRatio() {
        switch (this) {
            case PHASE_1:
                return PHASE_1_THRESHOLD_RATIO;
            case PHASE_2:
                return PHASE_2_THRESHOLD_RATIO;
            default:
                return NO_THRESHOLD;
        }
    }

    /** 序数反查（同步数据与存档读取的兜底入口）；越界回退 PHASE_1，防止坏数据把阶段机推进非法状态 */
    public static AgaitolosPhase byCombatOrdinal(int combatOrdinal) {
        for (AgaitolosPhase phase : VALUES) {
            if (phase.combatOrdinal == combatOrdinal) {
                return phase;
            }
        }
        return PHASE_1;
    }
}
