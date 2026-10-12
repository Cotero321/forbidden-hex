package com.example.akaishi.forbidden.boss.agaitolos;

import net.minecraft.world.entity.LivingEntity;

/**
 * 阿盖托洛丝的<b>地面档状态机</b>（"多待在地上"，2026-09-21 补）：切换节律
 * （最短停留迟滞 → 强制升空 → 最短空中停留）与落地/升空过渡窗口的唯一推进口。
 * <p>
 * 从 {@link AgaitolosEntity} 机械搬出（2026-09-24 拆分轮）：地面档只动"高度档位"一个布尔、
 * 展示状态一个 int 与三个计数，与招式状态完全正交（不占决策层的全局动作锁、不影响冷却），
 * 单独成类后与 {@code AgaitolosMoveControl} 的高度消费清晰分界 —— 控制器只读状态，节律归本类。
 * 状态字段仍归实体持有（包私有，由本类读写）；同步位（四态展示状态）的读写仍留在实体上。
 */
public final class AgaitolosPerchMachine {

    private AgaitolosPerchMachine() {
    }

    // ---------------------------------------------------------------- 节律常量

    /**
     * 地面档的<b>最短停留</b>（tick）：60 = 3s。<b>待调手感值 / P8 转配置项</b>
     * <p>作用 = 迟滞：落地后 3s 内无论目标怎么走都不起飞，避免"目标一抖动就升降"的每 tick 抖动。
     */
    public static final int PERCH_MIN_TICKS = 60;

    /**
     * 地面档的<b>最长停留</b>（tick）：200 = 10s。<b>待调手感值 / P8 转配置项</b>
     * <p>作用 = 强制节律：到点必升空一次，保证"落地 ↔ 升空"是一个玩家看得见的循环，
     * 而不是"一旦贴脸就永远趴在地上"（那会丢掉本 BOSS 的飞行辨识度，也会让部分需要空间的招失去意义）。
     * <p>同时也是"贴地若被地形卡住"的自解出口：最多 10s 就升空脱离（见 {@code AgaitolosMoveControl#PERCH_HEIGHT}）。
     */
    public static final int PERCH_MAX_TICKS = 200;

    /**
     * 地面档之后的<b>最短空中停留</b>（tick）：80 = 4s。<b>待调手感值 / P8 转配置项</b>
     * <p>作用 = 节律的下半段：升空后至少飞 4s 才允许再落地，避免"落-起-落"高频抖动。
     * <p>本常量同时是实体 {@code airborneTicks} 的封顶值（判据只需"是否已满"，不必真计时，故不会溢出）。
     */
    public static final int PERCH_AIRBORNE_MIN_TICKS = 80;

    /**
     * 地面档的<b>脱离距离</b>（格）：4.5。<b>待调手感值 / P8 转配置项</b>
     * <p>
     * 与进入条件（{@code isTargetWithinMeleeReach}，含悬停折算约 3.18 格）刻意留出差值，
     * 形成迟滞区间：目标在 3.18~4.5 格之间来回走位时档位不变，避免在阈值上反复横跳。
     */
    public static final double PERCH_RELEASE_RANGE = 4.5D;

    /**
     * 落地过渡窗口（tick）：12 = 0.6s，与 clip {@code animation.agaitolos.land} <b>逐字对齐</b>。
     * <p>改 clip 时长必须同步改这里：窗口是"过渡态 → 贴地稳态"的切换点，短了会把 land 掐掉一截、
     * 长了会让 land 播完停在末帧（末帧 == idle_ground 首帧，观感上仍不跳，但会显得落地后僵一下）。<b>待调手感值</b>
     */
    public static final int LAND_TRANSITION_TICKS = 12;

    /** 升空过渡窗口（tick）：12 = 0.6s，与 clip {@code animation.agaitolos.takeoff} 逐字对齐（口径同上） */
    public static final int TAKEOFF_TRANSITION_TICKS = 12;

    // ---------------------------------------------------------------- 只读探针
    /**
     * 是否处于地面档（近身缠斗时贴地）。<b>服务端权威</b>，唯一消费点是
     * {@code AgaitolosMoveControl} ③ 的垂直目标高度（空中档 vs 地面档）。
     * <p>
     * <b>切换节律（全部由 {@link #tickPerchState} 推进，这里只列口径）</b>：
     * <ol>
     *   <li><b>落地</b>：已在空中满 {@link #PERCH_AIRBORNE_MIN_TICKS}（4s）<b>且</b>目标进入近战可达
     *       （复用 {@code isTargetWithinMeleeReach} —— 与普攻/格挡/踢击同一把尺子，不另立距离口径）；</li>
     *   <li><b>保持</b>：至少 {@link #PERCH_MIN_TICKS}（3s），之后满足任一即<b>升空</b>：
     *       ① 目标超出 {@link #PERCH_RELEASE_RANGE}（4.5 格）或失去目标；
     *       ② 已待满 {@link #PERCH_MAX_TICKS}（10s）；</li>
     *   <li><b>升降过程</b>：不做瞬移 —— {@code AgaitolosMoveControl} 仍走"按高度差收敛 + 限速"，
     *       2 格高度差约 10~16 tick 走完，与落地/升空过渡 clip 的 12t 大体同步（见下）。</li>
     * </ol>
     * <b>与 {@link AgaitolosEntity#isGrounded()}（被格挡后的禁飞惩罚）是两个概念，刻意不合并</b>：禁飞是"不写垂直分量、
     * 交给重力"，地面档是"垂直收敛到更低的档位"；两者可以叠加（禁飞期本来就在地上，地面档让惩罚结束后
     * 不要立刻弹回空中）。混用会让"瞬击只在禁飞窗口可用"这条规则连带被改坏。
     * <p>
     * <b>为什么落地位不缩短近战可达尺子</b>：{@link AgaitolosEntity#getMeleeAttackRangeSqr} 仍按最大悬停高度
     * （{@code AgaitolosMoveControl.HOVER_HEIGHT}）折算。落地后这把尺子等于放宽了约 2 格，
     * 属"保守侧"——刚修好的还手能力不会因为落地而变弱（宁可多够 2 格，也不要出现"落地后反倒打不着"）。
     * 若实机觉得落地后够得太远，再改成按当前档位折算。
     * <p>
     * <b>动画（2026-09-21 已接入）</b>：地面档不再是"人站在地上、姿态还在飘"——MAIN 控制器按
     * 同步状态 {@link AgaitolosEntity#getPerchState()} 分支，落地过渡播 {@code land}、贴地播 {@code idle_ground}、
     * 升空过渡播 {@code takeoff}（常量与分支见 {@code AgaitolosAnimations}）。
     * 本布尔仍只管"高度档"，不参与动画判定（动画需要四态，见 {@link AgaitolosEntity#getPerchState()}）。
     */
    public static boolean isPerched(AgaitolosEntity boss) {
        return boss.perched;
    }

    /**
     * 只读探针：过渡窗口的剩余 tick（0 = 不在过渡中）。
     * <p>消费点只有 {@code AgaitolosMoveControl} ③-a（当匀速收敛的分母）。
     * 节律（进入 / 递减 / 退出）仍由本类独占（{@link #tickPerchState}），控制器只读 ——
     * 与 {@link #isPerched}（高度档）和 {@link AgaitolosEntity#getPerchState()}（展示状态）的既有分工一致。
     * <p>无需同步：只有服务端控制器读它（客户端只读 {@link AgaitolosEntity#getPerchState()} 那条四态数据去选 clip）。
     */
    static int getPerchTransitionTicks(AgaitolosEntity boss) {
        return boss.perchTransitionTicks;
    }

    // ---------------------------------------------------------------- 每 tick 推进（aiStep 调用）

    /**
     * 地面档的状态推进（服务端权威，每 tick 一次）：<b>过渡窗口 + 落地条件 + 三段迟滞 + 强制升空</b>。
     * <p>
     * 只动"高度档位"一个布尔、展示状态一个 int 与三个计数，不碰任何招式状态（不占决策层的全局动作锁、
     * 不影响冷却），故与 {@link AgaitolosSkillDirector} 的仲裁完全正交：落地期间照样能普攻 / 格挡 / 蓄力 / 被踢击，
     * 也照样能被决策层派招（俯冲的位移由 {@code tickDiveCharge} 独占，地面档不参与）。
     * <p>
     * 演出与死亡期间整段跳过：那几段的位移由 {@code tickIntro} / 回复演出独占，
     * {@code AgaitolosMoveControl} 也已整体让位，此时切档没有任何意义（只会让计数白白推进）。
     * <p>
     * <b>本方法只在服务端跑</b>：唯一调用点 {@link AgaitolosEntity#aiStep()} 在客户端已提前 return，
     * 故对同步数据 {@code DATA_PERCH_STATE} 的写入天然是"服务端权威"（客户端只读不写）。
     */
    static void tickPerchState(AgaitolosEntity boss) {
        if (boss.isDeadOrDying() || boss.isRespawning() || boss.isIntroPlaying()) {
            return;
        }
        // ① 过渡窗口（落地 / 升空各 12t，与对应 clip 等长）：到期即切到稳态，供动画把"过渡 clip → 稳态循环"接上。
        //    与下面的档位判定互不干扰：窗口只影响展示状态，高度档（perched）在过渡的第一 tick 就已经翻好了。
        //    只在过渡态里递减（稳态下计数器不参与，也就不会长跑到负值）
        int perchState = boss.getPerchState();
        if ((perchState == AgaitolosEntity.PERCH_STATE_LANDING || perchState == AgaitolosEntity.PERCH_STATE_TAKEOFF)
                && --boss.perchTransitionTicks <= 0) {
            boss.setPerchState(perchState == AgaitolosEntity.PERCH_STATE_LANDING
                    ? AgaitolosEntity.PERCH_STATE_PERCHED : AgaitolosEntity.PERCH_STATE_AIRBORNE);
        }
        LivingEntity target = boss.getTarget();
        if (boss.perched) {
            // 地面档：先满最短停留（迟滞），再判"目标走远"或"待太久"
            if (++boss.perchedTicks < PERCH_MIN_TICKS) {
                return;
            }
            // 抓取态（投技①）期间<b>不升空</b>：那一招的语义是"把玩家踩在脚下"，
            // 若恰好卡在 PERCH_MAX_TICKS 的升空点上，就会把"踩住"变成"拎着人上天"，
            // 而且松手时玩家会被留在半空吃一段下落伤害 —— 与"压制"的语义相反。
            // 抓取最长只有 HOLD_TICKS(22t)，远小于 PERCH_MAX_TICKS 的量级，故这点延迟肉眼不可见。
            if (boss.phaseThreeState().isHoldingTarget()) {
                return;
            }
            boolean targetLeft = target == null || !target.isAlive()
                    || boss.distanceTo(target) > PERCH_RELEASE_RANGE;
            if (targetLeft || boss.perchedTicks >= PERCH_MAX_TICKS) {
                boss.perched = false;
                boss.perchedTicks = 0;
                boss.airborneTicks = 0;
                // 高度档与展示状态同刻翻：升空过渡 clip 与"朝空中档收敛"是同一 tick 起跑的两件事
                boss.perchTransitionTicks = TAKEOFF_TRANSITION_TICKS;
                boss.setPerchState(AgaitolosEntity.PERCH_STATE_TAKEOFF);
            }
            return;
        }
        // 空中档：先攒满最短空中停留（保证升空是一段可感知的节律，而不是刚起飞又落），再判能否落地。
        // airborneTicks 封顶在阈值上：后半段判据只需"是否已满"，不需要真实时长，也就不存在溢出
        if (boss.airborneTicks < PERCH_AIRBORNE_MIN_TICKS) {
            ++boss.airborneTicks;
            return;
        }
        if (target != null && target.isAlive() && boss.isTargetWithinMeleeReach(target)) {
            boss.perched = true;
            boss.perchedTicks = 0;
            boss.perchTransitionTicks = LAND_TRANSITION_TICKS;
            boss.setPerchState(AgaitolosEntity.PERCH_STATE_LANDING);
        }
    }
}
