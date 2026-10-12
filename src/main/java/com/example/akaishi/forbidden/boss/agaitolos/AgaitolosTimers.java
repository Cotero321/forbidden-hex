package com.example.akaishi.forbidden.boss.agaitolos;

/**
 * 阿盖托洛丝的<b>计时协作类</b>：惩罚窗口（禁飞 / 镰扫封印 / 召唤冷却）的授予与递减、
 * 招式冷却的统一递减与只读探针（供 {@link AgaitolosSkillDirector} 打分消费）、
 * 以及"被玩家格挡惩罚"的两个状态探针（禁飞 / 封印）。
 * <p>
 * 从 {@link AgaitolosEntity} 机械搬出（2026-09-24 拆分轮）：这一块只围绕
 * 「冷却字段逐 tick 递减 → 决策层打分前读」一件事，与同步数据 / 阶段机 / 招式状态机无耦合；
 * 状态字段仍归实体持有（包私有，由本类读写），调用顺序（aiStep 在决策层之前递减）与拆分前逐位一致。
 */
public final class AgaitolosTimers {

    private AgaitolosTimers() {
    }

    // ---------------------------------------------------------------- 惩罚窗口常量（被玩家格挡成功后授予）

    /** 被玩家格挡成功后的禁飞时长（tick）：30s（规格明确）。待调手感值 / P8 转配置项 */
    public static final int GROUNDED_DURATION_TICKS = 600;

    /**
     * 被玩家格挡成功后的镰扫封印时长（tick）：与禁飞同窗 30s。
     * <p>刻意做成<b>限时</b>而非永久：设计文档 §0 规定「二阶段拥有一阶段所有技能」，
     * 若永久封印，后续阶段会永久少掉"俯冲镰扫"这一招、破坏阶段契约；
     * 限时封印既保留"格挡成功有实际收益"的惩罚语义，又不至于让后续阶段少一招。待调手感值 / P8 转配置项
     */
    public static final int SCYTHE_SEAL_TICKS = 600;

    // ---------------------------------------------------------------- 惩罚状态探针

    /**
     * 是否被禁飞（被玩家格挡的惩罚）：禁飞期间不再悬停，改走带重力的原版 travel 落到地面。
     * <p>禁飞结束时无需任何额外处理：{@code AgaitolosMoveControl} ③ 的垂直修正会自动把它升回
     * 当前高度档（空中档取本阶段的高度，见 {@code AgaitolosMoveControl#airborneHeight}；若此刻正处地面档
     * {@link AgaitolosEntity#isPerched()} 则维持贴地）。
     * <p><b>禁飞期的动画（2026-09-21 补）</b>：禁飞只是"不给垂直分量、交给重力"，落地位置随机，
     * 故没有（也不该有）专门的禁飞姿态 —— 它自己不驱动动画。实际观感由地面档接管：
     * 玩家刚挡下横扫、人就贴在旁边，禁飞落地的 BOSS 会在 {@link AgaitolosPerchMachine#PERCH_AIRBORNE_MIN_TICKS}（4s）内
     * 被 {@link AgaitolosPerchMachine#tickPerchState} 判为可贴地并切进地面档，此后就播 {@code idle_ground} 而不再是
     * {@code idle_flight}（地面档动画见 {@link AgaitolosEntity#isPerched()}）。禁飞期若目标恰好不在近战可达内，
     * 则这段时间仍会播飞行待机 —— 属"没有禁飞专属姿态"的已知取舍，不是失效。
     */
    public static boolean isGrounded(AgaitolosEntity boss) {
        return boss.groundedTicks > 0;
    }

    /**
     * 镰扫是否处于封印期（被玩家格挡的惩罚）。
     * <p>
     * <b>封印的作用范围（2026-09-20 用户拍板）</b>：封印<b>只封"大招"</b>，不封基础手段。
     * 原因是禁飞与封印由 {@link #onSweepBlocked} 同刻授予、且同为 600 tick，
     * 而瞬击的唯一起手条件恰恰是"不处于飞行状态"（= 禁飞窗口）—— 若封印也盖住瞬击，
     * 它的起手窗口会被完全覆盖、一次也放不出来，规格原文「如 BOSS 不处于飞行状态时便可以使用」
     * 就成了死条文。
     * <ul>
     *   <li><b>吃封印</b>：俯冲镰扫（决策层打分与 {@link AgaitolosDiveMachine#startDiveSweep} 复校里各判一次）；后续"大招"——
     *       三重投掷 / 天魔灾 / 投技 —— 接入时请走含封印的起手闸；</li>
     *   <li><b>不吃封印</b>：瞬击（惩罚期的位移补偿，本身不造成伤害）、
     *       高速踢击（规格原文"<b>任何状态下可用</b>"，"任何状态"含封印期）。</li>
     * </ul>
     * ⚠ 待用户确认：恶怨倒转<b>也未被封印</b>（既有口径即如此），是否要把它纳入"大招"名单。
     */
    public static boolean isScytheSealed(AgaitolosEntity boss) {
        return boss.scytheSealTicks > 0;
    }

    // ---------------------------------------------------------------- 惩罚授予

    /**
     * 授予禁飞。幂等：重复调用只刷新计时（取较大值，不会把已更长的窗口缩短），无累积副作用
     * —— 一次横扫可能被多个玩家同时格挡，逐人上报会重复调用。
     */
    private static void applyGrounded(AgaitolosEntity boss, int ticks) {
        boss.groundedTicks = Math.max(boss.groundedTicks, ticks);
    }

    /**
     * 横扫被玩家格挡成功后的惩罚入口（由 {@link AgaitolosDiveSweepSkill#perform} 逐人上报）。
     * <p>状态与计时归实体自己持有，技能只上报"这一击被格挡了"（与 {@link AgaitolosGuardCharge#endGuard} 同一手法，
     * 技能不碰实体的计数字段）。规格：本次攻击无效 + 解除飞行状态 30s + 封印该技能。
     * <p>幂等：重复调用只是刷新计时，无累积副作用（横扫可能被多个玩家同时格挡）。
     */
    public static void onSweepBlocked(AgaitolosEntity boss) {
        // 打断正在进行的冲锋：被挡下后不该继续冲向对方。
        // 正常流程里 perform 由冲锋收尾处调用、此刻 diveTicks 已为 0，此处属防御性归零。
        boss.diveTicks = 0;
        applyGrounded(boss, GROUNDED_DURATION_TICKS);
        boss.scytheSealTicks = SCYTHE_SEAL_TICKS;
    }

    // ---------------------------------------------------------------- 每 tick 递减（aiStep 在决策层之前调用）

    /** 禁飞 / 封印 / 召唤冷却的服务端计时递减（每 tick 一次，不为负） */
    static void tickPenaltyTimers(AgaitolosEntity boss) {
        if (boss.groundedTicks > 0) {
            --boss.groundedTicks;
        }
        if (boss.scytheSealTicks > 0) {
            --boss.scytheSealTicks;
        }
        if (boss.minionCooldownTicks > 0) {
            --boss.minionCooldownTicks;
        }
    }

    /**
     * 招式冷却的服务端计时递减（每 tick 一次，不为负）。
     * <p>
     * <b>为什么集中在一个方法里</b>：这些计数原先各自藏在 {@code tickGuard}/{@code tickDiveSweep}/
     * {@code tickBlink}/{@code tickKick} 内部，起手判定被移交给 {@link AgaitolosSkillDirector} 之后，
     * 递减若继续留在那几条方法里，就会出现"决策层先读冷却、状态推进后递减"的顺序依赖
     * （同一 tick 内读到的可能是还没减的旧值）。集中到一处、由 {@link AgaitolosEntity#aiStep()} 在决策层之前调用，
     * 顺序就只有一种。
     * <p>与 {@link #tickPenaltyTimers} 的分工：那一条是<b>玩家争取来的惩罚窗口</b>（禁飞/封印/召唤冷却），
     * 这一条是<b>BOSS 自己的出手节奏</b>（架势/横扫/瞬击/踢击/普攻/远程）。
     */
    static void tickActionCooldowns(AgaitolosEntity boss) {
        if (boss.guardCooldownTicks > 0) {
            --boss.guardCooldownTicks;
        }
        if (boss.diveSweepCooldownTicks > 0) {
            --boss.diveSweepCooldownTicks;
        }
        if (boss.blinkCooldownTicks > 0) {
            --boss.blinkCooldownTicks;
        }
        if (boss.kickCooldownTicks > 0) {
            --boss.kickCooldownTicks;
        }
        if (boss.meleeCooldownTicks > 0) {
            --boss.meleeCooldownTicks;
        }
        if (boss.rangedCooldownTicks > 0) {
            --boss.rangedCooldownTicks;
        }
        // 阶段三五招的冷却也收在这一个调用点（口径同上：所有冷却都在决策层读之前统一递减一次）。
        // 它们<b>逐招写在本类会再长 5 个字段</b>，故计时器归 AgaitolosPhaseThreeState 持有，
        // 但"什么时候减"仍只有这一个入口 —— 顺序依赖问题与上面五招完全同款地不存在。
        boss.phaseThreeState().tickCooldowns();
    }

    /**
     * 只读探针：某一招的剩余冷却（供 {@link AgaitolosSkillDirector} 判断"这一拍选不选它"）。
     * <p><b>只读不写</b>：计时的持有与递减仍归实体/本类（{@link #tickActionCooldowns}），
     * 决策层只消费状态——"状态归实体持有"这条既有分工不因为引入决策层而改变，
     * 否则又会退化成"两个地方都能改冷却"的两套口径。
     */
    static int remainingCooldown(AgaitolosEntity boss, AgaitolosSkillDirector.Move move) {
        switch (move) {
            case MELEE:
                return boss.meleeCooldownTicks;
            case RANGED:
                return boss.rangedCooldownTicks;
            case DIVE_SWEEP:
                return boss.diveSweepCooldownTicks;
            case GUARD:
                return boss.guardCooldownTicks;
            case REVERSAL:
                return boss.minionCooldownTicks;
            case BLINK:
                return boss.blinkCooldownTicks;
            case KICK:
                return boss.kickCooldownTicks;
            // 阶段三五招：计时器归 AgaitolosPhaseThreeState 持有，这里只是只读转发
            // （默认分支的 Integer.MAX_VALUE 会把这些招判成"永远在冷却中"，
            //  漏一个 case 就表现为"某一招永远不出现"，故五招必须逐一列出）
            case TRIPLE_THROW:
            case BOMBARD:
            case GRAB_SWEEP:
            case GRAB_SMASH:
            case CALAMITY:
                return boss.phaseThreeState().remainingCooldown(move);
            default:
                return Integer.MAX_VALUE;
        }
    }
}
