package com.example.akaishi.forbidden.boss.agaitolos;

import com.example.akaishi.forbidden.boss.agaitolos.skill.AgaitolosBlinkSkill;
import com.example.akaishi.forbidden.boss.agaitolos.skill.AgaitolosBombardSkill;
import com.example.akaishi.forbidden.boss.agaitolos.skill.AgaitolosCalamitySkill;
import com.example.akaishi.forbidden.boss.agaitolos.skill.AgaitolosGrabSkill;
import com.example.akaishi.forbidden.boss.agaitolos.skill.AgaitolosKickSkill;
import com.example.akaishi.forbidden.boss.agaitolos.skill.AgaitolosSkullSkill;
import com.example.akaishi.forbidden.boss.agaitolos.skill.AgaitolosTripleThrowSkill;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 阿盖托洛丝的<b>战斗决策层</b>：集中决定「这一拍放哪一招」。
 * <p>
 * <b>为什么必须新增这一层（缺陷根因驱动）</b>：在本轮之前，"什么时候放哪一招"是散落在
 * {@link AgaitolosEntity} 的若干 {@code tickXxx} 里的<b>顺次起手闸</b>（
 * {@code tickCharge} → {@code tickGuard} → {@code tickDiveSweep} → {@code tickBlink} → {@code tickKick}）：
 * 每招各自判断"我能不能放"，先到先得、全包零随机、彼此之间只靠 {@code isXxx()} 互斥。由此产生两个真实缺陷：
 * <ol>
 *   <li><b>节奏完全可预测</b>：同一距离下永远是"冷却一到就放的那一招"，没有权重与随机的取舍；</li>
 *   <li><b>没有全局仲裁</b>：一招正在演时，别的招只能靠自身的 {@code !isDiving()} 之类条件被动让位，
 *       新招加进来就要再补一遍全部互斥条件（历史上已经出现过"改一招忘一招"）。</li>
 * </ol>
 * 本类把"<b>选哪一招</b>"收成唯一入口：<b>副作用（状态、冷却、位移、结算）一概不持有</b>，
 * 全部留在实体与 {@code skill/} 里，本类只做<b>读状态 → 保命招硬闸 → 打分 → 掷骰 → 调用执行入口</b>五件事。
 * <p>
 * <b>五段判据（按顺序）</b>：
 * <ol>
 *   <li><b>全局动作锁</b>（{@link #isBusy(AgaitolosEntity)} + {@link #beatTicks}）：
 *       同一时刻只允许一招在演；一招结束/起手成功后要等满一个节拍（{@link #BEAT_TICKS_BASE} 乘阶段冷却系数）
 *       才允许下一招。这条同时替代了原先散落各处的 {@code !isDiving()/!isGuarding()/!isCharging()} 互斥网。</li>
 *   <li><b>保命招硬闸</b>（{@link #shouldGuardForced(AgaitolosEntity, LivingEntity)}）：
 *       目标已进"马上要挨打"的距离、且架势冷却已好 ⇒ <b>必定</b>架势。<b>不参与权重随机、也不被同层其它招抢走</b>
 *       （姿态性的防御取舍一旦交给掷骰，就会退化成"该架的时候有 70% 概率不架"，见该方法的 javadoc）；</li>
 *   <li><b>距离分层</b>（{@link DistanceTier}）：贴身 / 近 / 中 / 远 / 超远，各层只放得出该层合理的招；</li>
 *   <li><b>优先级 + 权重随机</b>（{@link #baseWeight}）：同层内按"大招 &gt; 中招 &gt; 基础手段"给基准权重，
 *       再用 {@code boss.getRandom()} 加权掷骰；<b>连续放同一招</b>要吃 {@link #ANTI_REPEAT_FACTOR} 惩罚
 *       （普攻与远程除外：这两招本来就该连发，惩罚它们等于自废输出）；</li>
 *   <li><b>阶段与玩家状态修正</b>（{@link #stateFactor}）：阶段二三开放瞬击/踢击并缩短节拍；
 *       玩家<b>举盾</b>则压低会被格挡的近战/横扫、抬高远程与召唤；玩家<b>在空中</b>则压低横扫（垂直容差够不到）。</li>
 * </ol>
 * <p>
 * <b>2026-09-21 补：阶段三五招（天魔＊灾 / 饱和轰炸 / 三重投掷 / 投技①②）</b>。
 * 五招接进同一套五段判据里，不新开仲裁路径：
 * <ul>
 *   <li><b>阶段门</b>：{@link #phaseThreeOnly}（仅阶段三；复活阶段序数 0 天然不在内）；</li>
 *   <li><b>封印</b>：五招都是"大招"，与俯冲镰扫同吃 {@code onSweepBlocked} 的 30s 封印
 *       （作用范围见 {@code AgaitolosEntity#isScytheSealed()} 的 javadoc）；</li>
 *   <li><b>可用距离层</b>：投技①②只在贴身（地面档 + 2.5/3.5 格），三重投掷/饱和轰炸在近到远（空中档专属），
 *       天魔＊灾隔空锁定、五层皆可但权重随距离变化 —— 见 {@link #baseWeight} 的 case；</li>
 *   <li><b>占用全局动作锁</b>：全部占用（{@code isBusy} 经 {@code isPhaseThreeBusy()} 读到"正在演"），
 *       起手成功后同样吃满一个节拍；</li>
 *   <li><b>与既有招的优先级</b>：同层同权重时按 {@link Move} 的枚举顺序取靠前者
 *       （大招在前、基础手段在后）；本轮把五招插在 REVERSAL 与 KICK 之间，
 *       故"同时可用"时它们的偏好次序为 天魔灾 → 饱和轰炸 → 三重投掷 → 投技① → 投技② → 踢击。</li>
 * </ul>
 * <b>保命招硬闸不受影响</b>：{@code shouldGuardForced} 仍在掷骰之前、且先于本表的五招生效
 * （五招都只是往权重表里加 case，没有动硬闸的求值位置与判据）。
 * <p>
 * <b>与其它类的分工（SRP）</b>：
 * <ul>
 *   <li>{@link AgaitolosEntity}：持有招式状态与计时，向本类暴露只读探针
 *       （{@link AgaitolosEntity#remainingCooldown} / {@link AgaitolosEntity#isTargetWithinMeleeReach} 等）
 *       与执行入口（{@code startGuard}/{@code startCharge}/…）；</li>
 *   <li>{@code skill/} 各技能类：招式自身的伤害与结算（本类不碰）；</li>
 *   <li>{@link AgaitolosPace}：阶段节奏倍率的唯一真源（本类只读，不另立一份）。</li>
 * </ul>
 * <p>
 * <b>本类不落盘</b>：{@code beatTicks}/{@code lastMove}/巡逻与脱困计时都是"当场的战术记忆"，
 * 读档后从零开始反而更自然（不会出现"读档后还记得上一招"这种无法解释的行为）。
 * 真正需要跨存档的招式状态（冷却/禁飞/封印/蓄力）本来就都在实体侧落盘。
 */
public final class AgaitolosSkillDirector {

    // ---------------------------------------------------------------- 招式枚举

    /**
     * 可被调度的招式。<b>只表达"选哪一招"</b>，不含任何状态——
     * 各招的状态与结算仍在 {@link AgaitolosEntity}（计时/互斥）与 {@code skill/}（伤害）里。
     * <p>
     * 顺序即"同权重下的偏好顺序"（{@link #pick} 用严格大于比较累加，故靠前的先被选中）：
     * 大招在前、基础手段在后。
     */
    public enum Move {
        /** 俯冲镰扫（含冲锋位移）：一阶段大招，被格挡要付禁飞 + 封印 30s */
        DIVE_SWEEP,
        /** 恶怨倒转（召唤凋零骷髅分队 + 12s 蓄力）：一阶段大招，被打够伤害会中断 */
        REVERSAL,
        // ---- 阶段三五招（2026-09-21 补）：全部<b>仅阶段三可用</b>，且都吃"大招封印"（见 isScytheSealed 的作用范围）----
        /**
         * 天魔＊灾：给玩家挂不可名状 + 把 BOSS 的伤害类型改写成精神伤害（30s，此后永久）。
         * <p>隔空锁定、不限空间状态 ⇒ 五层距离里都能派（权重随距离递减）。
         */
        CALAMITY,
        /** 饱和轰炸：高空悬停 + 每 20 tick 一发凋零头（被反弹则 BOSS 自伤）。只在远距离值得放 */
        BOMBARD,
        /** 三重投掷：空中三连扇形弹体（无视 30% 防御）。空中档专属 */
        TRIPLE_THROW,
        /** 投技①：踩住 + 镰扫（25% 最大生命）。地面档 + 贴身专属 */
        GRAB_SWEEP,
        /** 投技②：特写 + 劈击（20% 最大生命真实伤害；本轮只接服务端结算）。地面档 + 贴身专属 */
        GRAB_SMASH,
        /** 高速踢击：二阶段，任何空间状态可用 */
        KICK,
        /** 瞬击：二阶段，只在禁飞窗口（= 被格挡后的惩罚期）可用 */
        BLINK,
        /** 格挡架势：防御性取舍，架势期间不出手 */
        GUARD,
        /** 远程凋零头颅 */
        RANGED,
        /** 普攻（挥舞武器 + 凋零 III + 真实伤害） */
        MELEE
    }

    /** 招式枚举缓存（{@code values()} 每次都克隆数组，故缓存；与 {@link AgaitolosPhase} 同手法） */
    private static final Move[] MOVES = Move.values();

    /** 距离分层。阈值见 {@link #TIER_CLOSE_MAX} 等常量 */
    public enum DistanceTier {
        /** 贴身：已进 BOSS 自己的近战可达距离（含悬停高度折算） */
        POINT_BLANK,
        /** 近：可达距离 ~ {@link #TIER_CLOSE_MAX} 格 */
        CLOSE,
        /** 中：~ {@link #TIER_MID_MAX} 格（俯冲镰扫的起手上界） */
        MID,
        /** 远：~ {@link #TIER_FAR_MAX} 格 */
        FAR,
        /** 超远：以上（只剩远程与召唤有意义，其余靠追击拉近） */
        VERY_FAR
    }

    // ---------------------------------------------------------------- 全局节拍（待调手感值 / P8 转配置项）

    /**
     * 基础节拍（tick）：10 = 0.5s。
     * <p>含义：<b>同一时刻只允许一招在演</b>——一招起手成功后，至少隔这么久才允许选下一招
     * （起手失败不占节拍，见 {@link #BEAT_RETRY_TICKS}）。
     * <p>阶段二三按 {@link AgaitolosPace#cooldownScale} 缩短（阶段二 0.75 ⇒ 8 tick、阶段三 0.6 ⇒ 6 tick），
     * 与"二阶段比一阶段更快速"的既有口径共用同一份倍率，不另立一份。
     */
    public static final int BEAT_TICKS_BASE = 10;

    /** 起手失败后的重试窗口（tick）：5。落点校验失败/寻路拿不到时不必空等一整拍。待调手感值 */
    public static final int BEAT_RETRY_TICKS = 5;

    /**
     * 基础节拍（tick）的<b>阶段折算</b>：与各招冷却共用 {@link AgaitolosPace#cooldownScale}
     * —— 阶段一 10、阶段二 8、阶段三 6。
     * <p>刻意不另立一份"决策层倍率表"：规格只说了"二阶段比一阶段更加快速"，
     * 出手节奏快多少必须与冷却缩短多少同源，否则会出现"冷却变短了、但决策层每 10 tick 才放一次"的抵消。
     */
    private static int beatTicksFor(AgaitolosEntity boss) {
        return Math.max(1, (int) Math.round(BEAT_TICKS_BASE * AgaitolosPace.cooldownScale(boss.getPhase())));
    }

    // ---------------------------------------------------------------- 距离分层阈值（待调手感值 / P8 转配置项）

    /** 「近」层上界（格）：6.0 —— 一两个身位，跳一下够得着 */
    public static final double TIER_CLOSE_MAX = 6.0D;

    /**
     * 「中」层上界（格）：直接引用俯冲镰扫的起手上界（{@link AgaitolosEntity#DIVE_TRIGGER_RANGE} = 16）。
     * <p>刻意引用而不是再写一个 16：中层就是"俯冲还够得着"的那一层，两处一旦分家，
     * 会出现"中层放俯冲、但俯冲的距离闸不通过"的空放。
     */
    public static final double TIER_MID_MAX = AgaitolosEntity.DIVE_TRIGGER_RANGE;

    /** 「远」层上界（格）：32.0 —— 仍在天际线内，远程/瞬击还能接上 */
    public static final double TIER_FAR_MAX = 32.0D;

    // ---------------------------------------------------------------- 反重复与玩家状态修正（待调手感值 / P8 转配置项）

    /** 连续同招的权重惩罚：0.35（连放同一招的吸引力降到三分之一） */
    private static final double ANTI_REPEAT_FACTOR = 0.35D;

    /** 连放三拍以上同一招的权重惩罚：0.1（基本掐掉第三条腿） */
    private static final double ANTI_REPEAT_FACTOR_HARD = 0.1D;

    /** 玩家举盾时普攻的权重系数：0.4 —— 正面近战会被 {@link com.example.akaishi.forbidden.boss.agaitolos.skill.AgaitolosGuardSkill} 判为格挡 */
    private static final double SHIELD_MELEE_FACTOR = 0.4D;

    /**
     * 玩家举盾时俯冲镰扫的权重系数：0.2。
     * <p>压得比普攻更狠是有理由的：横扫被格挡的代价是<b>禁飞 + 封印 30s</b>
     * （{@link AgaitolosEntity#onSweepBlocked()}），拿大招去撞盾等于白送对方一个惩罚窗口。
     */
    private static final double SHIELD_DIVE_FACTOR = 0.2D;

    /** 玩家举盾时远程的权重系数：1.6 —— 凋零头不吃盾牌朝向，是破盾的正解 */
    private static final double SHIELD_RANGED_FACTOR = 1.6D;

    /** 玩家举盾时召唤（恶怨倒转）的权重系数：1.5 —— 举盾意味着放弃输出，正是召唤分队的窗口 */
    private static final double SHIELD_REVERSAL_FACTOR = 1.5D;

    /** 玩家在空中时俯冲镰扫的权重系数：0.3 —— 横扫的垂直容差只有 2.5 格，打不到高处 */
    private static final double AIRBORNE_DIVE_FACTOR = 0.3D;

    /** 玩家在空中时远程的权重系数：1.5 */
    private static final double AIRBORNE_RANGED_FACTOR = 1.5D;

    /** 玩家在空中时踢击的权重系数：1.2 —— 踢击不吃地面限制 */
    private static final double AIRBORNE_KICK_FACTOR = 1.2D;

    /**
     * 「玩家在空中」的判定高度（格）：2.0。玩家脚部比 BOSS 脚部高过该值即算在空中。
     * <p>取 2.0 = 常态悬停高度（{@link AgaitolosMoveControl#HOVER_HEIGHT}）：玩家站在地面时两者差值恰好就是它，
     * 故用同一个数当分界，不会把"站在地上的玩家"误判成空中。
     */
    private static final double AIRBORNE_HEIGHT = AgaitolosMoveControl.HOVER_HEIGHT;

    // ---------------------------------------------------------------- 巡逻与脱困（待调手感值 / P8 转配置项）

    /** 无目标时的巡逻间隔（tick）：120 = 6s。到点就换一个巡航点，避免原地悬停 */
    public static final int PATROL_INTERVAL_TICKS = 120;

    /** 巡逻半径（格）：12.0 —— 在召唤点周边小范围游弋，不追出牢狱范围 */
    public static final double PATROL_RADIUS = 12.0D;

    /** 巡逻移动速度系数（传给导航，最终还乘阶段倍率） */
    public static final double PATROL_SPEED = 0.8D;

    /**
     * 判定"卡住"所需的无进展时长（tick）：40 = 2s。
     * <p>刻意比一次寻路重算（4~11 tick）长一个数量级：正常的路径重算期间本来就可能短时间不动，
     * 门槛太短会把"正在转弯"误判成"被卡住"，把 BOSS 抖成筛子。
     */
    public static final int STUCK_TICKS = 40;

    /** 每 tick 至少移动这么多格（水平）才算"有进展" —— 待调手感值 */
    private static final double STUCK_MIN_PROGRESS = 0.02D;

    /** 脱困时朝目标直推的水平速度系数（仅在导航完全拿不到路径时用；不做避障，与既有设计一致） */
    private static final double RESCUE_DIRECT_SPEED = 1.0D;

    // ---------------------------------------------------------------- 战术记忆（不落盘）

    /** 距离下一拍还能出招的剩余 tick；> 0 表示全局动作锁仍被占 */
    private int beatTicks;

    /** 上一拍放的招（反重复用）；无目标时清空 */
    private Move lastMove;

    /** 连续放同一招的次数（反重复用） */
    private int repeatStreak;

    /** 位置无进展的累计 tick（脱困用） */
    private int stuckTicks;

    /** "上一次的位置基准"（脱困用）：与当前水平位置比较，判断这一 tick 有没有挪窝 */
    private double progressRefX;
    private double progressRefZ;

    /** 巡逻倒计时与当前巡航点（水平坐标；高度交给 {@code AgaitolosMoveControl} 的悬停） */
    private int patrolTicks;
    private double patrolX;
    private double patrolZ;

    /**
     * 打分用的权重暂存数组（长度 = {@link #MOVES} 条数）。
     * <p>做成字段而不是每 tick 新建：本类实例与实体一一对应、只在服务端主线程被访问，
     * 复用一份暂存既省一次每 tick 的数组分配，也不引入任何共享（同一个 BOSS 的两次 tick 不会并发）。
     */
    private final double[] weights = new double[MOVES.length];

    // ---------------------------------------------------------------- 每 tick 入口

    /**
     * 决策层主循环（服务端权威；由 {@link AgaitolosEntity#aiStep} 在<b>所有状态推进之后</b>调用一次）。
     * <p>
     * 顺序：<b>递减节拍 → 采集"是否卡住" → 分流（无目标巡逻 / 有目标决策）</b>。
     * 有目标时的决策顺序见类注释的"五段判据"：全局动作锁 → <b>保命招硬闸</b> → 距离分层 → 权重掷骰 → 执行。
     * <p>客户端直接返回：状态全是服务端权威，本类不参与同步（与 {@code AgaitolosEntity} 的其它服务端逻辑同一分工）。
     */
    public void tick(AgaitolosEntity boss) {
        if (this.beatTicks > 0) {
            --this.beatTicks;
        }
        if (boss.level().isClientSide()) {
            return;
        }
        LivingEntity target = boss.getTarget();
        // 目标死亡/已被清除 ⇒ 立刻放下，交给目标选择器重新选人（NearestAttackableTargetGoal 会在目标为空时重扫）
        if (target != null && !target.isAlive()) {
            boss.setTarget(null);
            target = null;
        }
        this.trackProgress(boss);
        if (target == null) {
            // 脱战：清战术记忆 + 低空巡逻（不做原地悬停）
            this.forgetTactics();
            this.patrol(boss);
            return;
        }
        // 卡住补救只在"没在演招"时做：架势/蓄力是刻意站定，演出/复活另有位移来源，都不该被判定成卡住
        if (!this.isBusy(boss)) {
            this.rescueIfStuck(boss, target);
        }
        // ① 全局动作锁：有招在演、或还没到下一拍 ⇒ 本 tick 不决策
        if (this.isBusy(boss) || this.beatTicks > 0) {
            return;
        }
        // ①' 保命招硬闸：<b>先于掷骰</b>。目标"马上要挨打"且冷却已好 ⇒ 必定架势，不参与权重抽取。
        //     放在锁判定<b>之后</b>是刻意的：硬闸起手与别的招<b>吃同一套节拍/冷却仲裁</b>
        //     （同样占满一个 beatTicks），不另开一条"随时可插入"的旁路——否则会出现
        //     "上一招刚起手、架势又插进来"的双状态并存，把 9 条 clip 的互斥前提破坏掉。
        //     代价只是"冷却一到，最迟再等一个节拍（10/8/6 tick）架起来"，肉眼不可见。
        if (this.shouldGuardForced(boss, target)) {
            if (this.execute(boss, target, Move.GUARD)) {
                this.commit(Move.GUARD);
                this.beatTicks = beatTicksFor(boss);
                return;
            }
            // 硬闸条件与 startGuard 的前置复校完全同源，正常流程不可达；
            // 万一将来 startGuard 里加了新前置而此处没同步，也绝不能空占一拍或死循环 —— 直接落到正常掷骰
        }
        // ② 打分 ③ 掷骰 ④ 调用执行入口
        Move move = this.pick(boss, target);
        if (move == null) {
            return;
        }
        if (this.execute(boss, target, move)) {
            this.commit(move);
            this.beatTicks = beatTicksFor(boss);
        } else {
            // 起手失败（落点校验没过、召唤异常等）：只给一个短重试窗口，不空等一整拍
            this.beatTicks = BEAT_RETRY_TICKS;
        }
    }

    /** 记下这一拍放了什么（反重复惩罚的依据） */
    private void commit(Move move) {
        if (move == this.lastMove) {
            ++this.repeatStreak;
        } else {
            this.lastMove = move;
            this.repeatStreak = 1;
        }
    }

    /** 脱战：清空"当场战术记忆"，让下场战斗从零开始（不落盘，理由见类注释） */
    private void forgetTactics() {
        this.lastMove = null;
        this.repeatStreak = 0;
        this.stuckTicks = 0;
    }

    /**
     * 是否有招正在演（全局动作锁的状态面）。
     * <p>这六个状态与"死亡/复活/出场演出"同属"BOSS 正在做别的事"，任一成立都不该再起手：
     * 它们各自独占位移或姿态（冲锋写 3D 速度、架势/蓄力掐断水平、出场写竖直），
     * 同时放第二招会互相拉扯（共用同一批骨骼动画，见 {@code AgaitolosAnimations}）。
     * <p><b>阶段三五招（2026-09-21 补）</b>整段收在 {@link AgaitolosEntity#isPhaseThreeBusy()} 一个探针里
     * （三连出手 / 轰炸 / 抓取 / 劈击 / 施法），故这里只多一项 —— 不会因为"多了五招"就长出五条互斥条件
     * （那正是本轮引入全局动作锁要治的病）。
     */
    private boolean isBusy(AgaitolosEntity boss) {
        return boss.isDeadOrDying() || boss.isRespawning() || boss.isIntroPlaying()
                || boss.isDiving() || boss.isGuarding() || boss.isCharging()
                || boss.isPhaseThreeBusy();
    }

    // ---------------------------------------------------------------- 保命招硬闸（格挡，2026-09-21 补）

    /**
     * 保命招硬闸：<b>「该架就架」的确定性触发条件</b>（不掷骰、不被其它招抢）。
     * <p>
     * <b>为什么必须把格挡从掷骰里摘出来（缺陷根因）</b>：在本轮之前，"要不要架势"由 {@link #baseWeight}
     * 的权重决定——贴身层 GUARD = 4、同层 MELEE = 10（再加远程 1），即<b>每拍只有约 27% 架盾</b>；
     * 实机因此几乎看不到它举镰刀。而这招的性质与"这一拍放哪个大招"根本不同：它是<b>对"马上要挨打"
     * 这一事实的姿态性响应</b>，玩家能预期（贴上去 ⇒ 它架盾 ⇒ 我得绕后或换远程），
     * 一旦交给随机就退化成"该架的时候多半不架"，玩家既读不出来也惩罚不到它。
     * <p>
     * <b>判据（三条同时成立）</b>：
     * <ol>
     *   <li><b>目标已进"马上要挨打"的距离</b> —— 复用 {@link AgaitolosEntity#isTargetWithinGuardRange}
     *       （水平距离 ≤ 近战可达尺子 {@code getMeleeAttackRangeSqr} ≈ 3.18 格）。取<b>水平</b>口径而不取 3D，
     *       是沿用起手格挡的既有口径、也是<b>偏保守的一侧</b>：悬停 2 格时玩家头顶脚下那点竖直差不会把架势压掉
     *       （宁可早半格架、也不要"人已经贴到脸上还不架"）。</li>
     *   <li><b>架势冷却已好</b> —— 同 {@link AgaitolosEntity#remainingCooldown}，即
     *       {@link com.example.akaishi.forbidden.boss.agaitolos.skill.AgaitolosGuardSkill#GUARD_COOLDOWN_TICKS}
     *       经 {@link AgaitolosPace#scaledCooldown} 的阶段折算（60 / 45 / 36）。</li>
     *   <li><b>不在死亡/复活/出场/冲锋/蓄力/已在架势中</b> —— 不在这里判：由调用点的
     *       {@link #isBusy(AgaitolosEntity)} 全局动作锁提前拦掉（本方法只表达"姿态该不该起"，
     *       不重复动作锁那套判据，避免两处各写一份"哪些状态不可介入"）。</li>
     * </ol>
     * <p>
     * <b>与全局节拍 / 冷却仲裁的关系</b>：硬闸<b>在节拍闸门之后</b>求值，故它<b>同样吃整拍</b>——
     * 起手成功占满一个 {@link #BEAT_TICKS_BASE}（阶段折算 10/8/6 tick），且不再走权重抽取；
     * 起手后架势自身靠 {@code isGuarding()} 占住动作锁，直到 24 tick 期满或被反击打断（{@code endGuard} 写冷却）。
     * 于是"架势"与其它招共用<b>同一套</b>互斥与节拍，不引入第二条仲裁路径。
     * <p>
     * <b>可观察性（贴脸平A时的预期频率，按 24t 架势 + 60t 冷却 + 节拍上限 10t 推算）</b>：
     * <ul>
     *   <li><b>架势被普攻打到而提前收势</b>（最常态：1.2s 架势内玩家至少落一刀；收势那一刻才起算冷却）
     *       ⇒ 周期 ≈ 10 + 60 + ≤10 ≈ <b>3.8~4.0s 一次</b>（阶段二 45t/8t ⇒ 3.2s、阶段三 36t/6t ⇒ 2.6s）；</li>
     *   <li><b>整段没人碰它</b>（玩家在绕后/走位）⇒ 周期 ≈ 24 + 60 + ≤10 ≈ <b>4.3~4.7s 一次</b>
     *       （阶段二 3.7s、阶段三 3.1s）；</li>
     *   <li>两端的架势占空比 = 前者约 13%（10/75）、后者约 26%（24/94）—— <b>一阶段每 4~5 秒必见一次举镰</b>，
     *       二/三阶段更密，且不再受随机影响。</li>
     * </ul>
     * <p>
     * <b>为什么不做成"权重 ×N"（例如把 GUARD 权重从 4 抬到 40）</b>：那样在贴身层确实会频繁架，
     * 但 ① 仍然受随机，实战里依旧会连出三刀不架盾；② 一旦抬权重就得同时抬"近"层，
     * 拉开一点距离（3.2~6 格，其实还够不着它）也会开始乱架盾。硬闸只认"马上要挨打"这一个事实，
     * 距离一变立刻不成立，方向性正好相反。
     * <p>
     * 与俯冲镰扫的距离闸有极小重叠（{@code DIVE_MIN_RANGE} = 3.0 &lt; 守卫口径 3.18）：该薄片内硬闸优先。
     * 这是期望行为——3 格几乎已经是贴脸，此时"先架住"比"起一段冲锋"合理，且冲锋本就要拉开距离才有意义。
     */
    private boolean shouldGuardForced(AgaitolosEntity boss, LivingEntity target) {
        return boss.remainingCooldown(Move.GUARD) <= 0 && boss.isTargetWithinGuardRange(target);
    }

    // ---------------------------------------------------------------- 打分与掷骰

    /**
     * 选招：对全部招式打分（不可用 = 0），再做一次加权掷骰。
     *
     * @return 选中的招；<b>全部为 0 时返回 null</b>（此时只管追击，不空放招）
     */
    private Move pick(AgaitolosEntity boss, LivingEntity target) {
        DistanceTier tier = this.tierOf(boss, target);
        double total = 0.0D;
        double[] weights = this.weights;
        for (int i = 0; i < MOVES.length; ++i) {
            double weight = this.effectiveWeight(boss, target, MOVES[i], tier);
            weights[i] = weight;
            total += weight;
        }
        if (total <= 0.0D) {
            return null;
        }
        // roll ∈ [0, total)；只对<b>可用</b>（权重 > 0）的招扣减，故不会选中权重为 0 的招，
        // 也不会因为 nextDouble() 恰好返回 0.0 而落到"第一项权重为 0 ⇒ 本轮空过"
        double roll = boss.getRandom().nextDouble() * total;
        for (int i = 0; i < MOVES.length; ++i) {
            if (weights[i] <= 0.0D) {
                continue;
            }
            roll -= weights[i];
            if (roll <= 0.0D) {
                return MOVES[i];
            }
        }
        // total > 0 时理论上不可达（浮点累加误差兜底）：退化为"权重最大的那一招"
        Move best = null;
        double bestWeight = 0.0D;
        for (int i = 0; i < MOVES.length; ++i) {
            if (weights[i] > bestWeight) {
                bestWeight = weights[i];
                best = MOVES[i];
            }
        }
        return best;
    }

    /** 基准权重 × 状态修正；任一步为 0 即"这一拍不选它" */
    private double effectiveWeight(AgaitolosEntity boss, LivingEntity target, Move move, DistanceTier tier) {
        double weight = this.baseWeight(boss, target, move, tier);
        if (weight <= 0.0D) {
            return 0.0D;
        }
        weight *= this.stateFactor(boss, target, move);
        weight *= this.antiRepeatFactor(move);
        return weight;
    }

    /**
     * 基准权重表：<b>距离分层 × 招式</b>。
     * <p>权重本身没有绝对意义，只有<b>同层内的比值</b>有意义（"这一拍更可能放什么"）。
     * 全部数值都是待调手感值 / P8 转配置项。
     * <p>返回 0 的两类原因要分清：<b>距离不合适</b>（例如超远距离放不出俯冲）与
     * <b>前置状态不满足</b>（冷却中、被封印、阶段未开放）—— 后者刻意也做成 0，
     * 因为"能不能放"本来就是权重的下限，分两张表迟早出现"打分说能放、执行却被闸掉"的空放。
     * <p><b>第三类（2026-09-21 补）</b>：<b>招式由硬闸独占</b> —— 目前只有 GUARD，
     * 见 {@link #shouldGuardForced}（表格里恒 0 = "不参与掷骰"，不是"不可用"）。
     */
    private double baseWeight(AgaitolosEntity boss, LivingEntity target, Move move, DistanceTier tier) {
        switch (move) {
            case MELEE:
                // 近战可达（含悬停高度折算）才谈得上普攻；距离由近到远递减（远的交给追击）。
                // 冷却判据必须与 {@link AgaitolosEntity#doHurtTarget} 的出手闸同源：那一边在
                // {@code meleeCooldownTicks > 0} 时直接 return false；若打分不查冷却，就会出现
                // "打分放行 → 执行（恒 true）→ 占掉整拍，却没造成任何伤害"的空占（贴脸时约每 20t 白吃一拍）
                if (boss.remainingCooldown(Move.MELEE) > 0 || !boss.isTargetWithinMeleeReach(target)) {
                    return 0.0D;
                }
                switch (tier) {
                    case POINT_BLANK:
                        return 10.0D;
                    case CLOSE:
                        return 6.0D;
                    case MID:
                        return 2.0D;
                    default:
                        return 0.0D;
                }
            case RANGED:
                if (boss.remainingCooldown(Move.RANGED) > 0
                        || boss.distanceTo(target) > AgaitolosSkullSkill.SKULL_ATTACK_RADIUS) {
                    return 0.0D;
                }
                switch (tier) {
                    case POINT_BLANK:
                        return 1.0D;
                    case CLOSE:
                        return 5.0D;
                    case MID:
                        return 8.0D;
                    default:
                        return 9.0D;
                }
            case DIVE_SWEEP:
                // 距离闸复用实体自己那把尺子（[DIVE_MIN_RANGE, DIVE_TRIGGER_RANGE] 的<b>水平</b>距离）；
                // 封印期与被格挡惩罚期内一律不放（封印只封"大招"，见 AgaitolosEntity#isScytheSealed 的 javadoc）
                if (boss.remainingCooldown(Move.DIVE_SWEEP) > 0 || boss.isScytheSealed()
                        || !boss.isWithinDiveTriggerRange(target)) {
                    return 0.0D;
                }
                switch (tier) {
                    case CLOSE:
                        return 4.0D;
                    case MID:
                        // 中层是俯冲的主场：刚好在"还得飞一段"的距离上
                        return 8.0D;
                    default:
                        return 0.0D;
                }
            case REVERSAL:
                // 召唤 + 12s 蓄力的"大投入"：只在拉开到中层以上时才值得（贴脸放等于站着挨打断）
                if (boss.remainingCooldown(Move.REVERSAL) > 0 || tier == DistanceTier.POINT_BLANK
                        || tier == DistanceTier.CLOSE) {
                    return 0.0D;
                }
                switch (tier) {
                    case MID:
                        return 3.0D;
                    case FAR:
                        return 3.0D;
                    default:
                        return 2.0D;
                }
            case BLINK:
                // 二阶段才解锁；且规格限定"不处于飞行状态时可用"⇒ 只有禁飞窗口才放得出（见 AgaitolosBlinkSkill）。
                // 冷却判据（与 KICK / RANGED 同款）是把 {@code startBlink} 写下的冷却<b>真正消费掉</b>的唯一入口：
                // 成功进完整冷却（{@code BLINK_COOLDOWN_TICKS} 按阶段折算），落点校验失败只写短重试窗口
                // （{@code BLINK_FAILED_RETRY_TICKS}）—— 两者都靠这一行封住"禁飞窗口内每拍都能再闪"的连发
                if (!this.phaseTwoOrLater(boss) || boss.remainingCooldown(Move.BLINK) > 0
                        || !boss.isGrounded() || !AgaitolosBlinkSkill.canBlink(boss, target)) {
                    return 0.0D;
                }
                switch (tier) {
                    case CLOSE:
                        return 3.0D;
                    case MID:
                        return 5.0D;
                    case FAR:
                        return 5.0D;
                    default:
                        return 0.0D;
                }
            case GUARD:
                // 架势<b>已从权重表摘出</b>，由 {@code shouldGuardForced} 的保命招硬闸独占（2026-09-21 补）。
                // 这里恒返回 0 而不是删掉本 case：① 让"格挡为什么不在这张表里"写在表内，后来者一眼可见；
                // ② 硬闸的判据（冷却 + "马上要挨打"）与本 case 原本的判据完全同源，
                //    若将来要把架势交回掷骰，改这一处 + 删硬闸即可（两处必须同时动，否则硬闸会先把权重表饿死）
                return 0.0D;
            // ---------------- 阶段三五招（2026-09-21 补）----------------
            // 五招共用同一段三段式判据：① 阶段门（仅阶段三）② 冷却 + 封印（它们都是"大招"，吃 onSweepBlocked 的封印）
            // ③ 空间条件（由各技能类的 canXxx 判，与实体侧执行入口的复校完全同源 —— 不写第二份距离口径）。
            // 权重只在"可用距离层"内给：不可用即 0，不靠 stateFactor 事后压（压不干净会留下"打分说能放、执行却被闸掉"的空放）。
            case CALAMITY:
                if (!this.phaseThreeOnly(boss) || boss.remainingCooldown(Move.CALAMITY) > 0 || boss.isScytheSealed()
                        || !AgaitolosCalamitySkill.canCast(boss, target)) {
                    return 0.0D;
                }
                switch (tier) {
                    // 隔空锁定：贴脸放没有位移/距离收益，越远越像"它盯上你了"
                    case POINT_BLANK:
                        return 2.0D;
                    case CLOSE:
                        return 3.0D;
                    case MID:
                    case FAR:
                        return 4.0D;
                    default:
                        return 2.0D;
                }
            case BOMBARD:
                if (!this.phaseThreeOnly(boss) || boss.remainingCooldown(Move.BOMBARD) > 0 || boss.isScytheSealed()
                        || !AgaitolosBombardSkill.canBombard(boss, target)) {
                    return 0.0D;
                }
                switch (tier) {
                    // 飞到高空去轰炸：贴脸/近身时它自己在地面档，本来就放不出（canBombard 已挡），
                    // 权重的意义只在"中层以上更值得"——它是一段 6s 的压制窗口，近距离有更划算的招
                    case MID:
                        return 2.0D;
                    case FAR:
                        return 4.0D;
                    default:
                        return 3.0D;
                }
            case TRIPLE_THROW:
                if (!this.phaseThreeOnly(boss) || boss.remainingCooldown(Move.TRIPLE_THROW) > 0 || boss.isScytheSealed()
                        || !AgaitolosTripleThrowSkill.canThrow(boss, target)) {
                    return 0.0D;
                }
                switch (tier) {
                    // 与远程同档：空中三连是"远程的加强版"，中层最舒服（扇面有散开空间，又不容易被贴身打断）
                    case POINT_BLANK:
                        return 2.0D;
                    case CLOSE:
                        return 5.0D;
                    case MID:
                        return 7.0D;
                    default:
                        return 6.0D;
                }
            case GRAB_SWEEP:
                if (!this.phaseThreeOnly(boss) || boss.remainingCooldown(Move.GRAB_SWEEP) > 0 || boss.isScytheSealed()
                        || !AgaitolosGrabSkill.canGrab(boss, target)) {
                    return 0.0D;
                }
                switch (tier) {
                    // 抓取只能贴身：POINT_BLANK 是它的主场（canGrab 的 2.5 格比贴身层还紧，基本只剩这一层）
                    case POINT_BLANK:
                        return 6.0D;
                    case CLOSE:
                        return 2.0D;
                    default:
                        return 0.0D;
                }
            case GRAB_SMASH:
                if (!this.phaseThreeOnly(boss) || boss.remainingCooldown(Move.GRAB_SMASH) > 0 || boss.isScytheSealed()
                        || !AgaitolosGrabSkill.canSmash(boss, target)) {
                    return 0.0D;
                }
                switch (tier) {
                    // 比投技①略低：真伤不吃任何防护，出现频率压低一点（15s 冷却也在同一方向上）
                    case POINT_BLANK:
                        return 5.0D;
                    case CLOSE:
                        return 1.0D;
                    default:
                        return 0.0D;
                }
            case KICK:
                // 二阶段才解锁；距离复用普攻那把尺子（"踢得着"与"打得着"同一把尺）
                if (!this.phaseTwoOrLater(boss) || boss.remainingCooldown(Move.KICK) > 0
                        || !AgaitolosKickSkill.isWithinKickRange(boss, target)) {
                    return 0.0D;
                }
                switch (tier) {
                    case POINT_BLANK:
                        return 5.0D;
                    case CLOSE:
                        return 3.0D;
                    default:
                        return 0.0D;
                }
            default:
                return 0.0D;
        }
    }

    /**
     * 玩家状态修正：<b>同一个距离下，玩家怎么站着会改变 BOSS 的取舍</b>。
     * <p>只做乘法系数、不做硬闸——硬闸会让"玩家一直举盾 ⇒ BOSS 彻底不出招"，
     * 那是另一种形态的"不还手"。系数让偏好变，但每种手段都还留着被选中的可能。
     * <p>"在远处放风筝"这一项不另做速度判定：它已经由 {@link DistanceTier} 表达
     * （中层以上俯冲/远程占优、贴身层只剩近战系），再叠一层速度判定只会互相打架。
     */
    private double stateFactor(AgaitolosEntity boss, LivingEntity target, Move move) {
        double factor = 1.0D;
        boolean shielding = target instanceof Player player && player.isBlocking();
        if (shielding) {
            switch (move) {
                case MELEE:
                    factor *= SHIELD_MELEE_FACTOR;
                    break;
                case DIVE_SWEEP:
                    factor *= SHIELD_DIVE_FACTOR;
                    break;
                case RANGED:
                    factor *= SHIELD_RANGED_FACTOR;
                    break;
                case REVERSAL:
                    factor *= SHIELD_REVERSAL_FACTOR;
                    break;
                default:
                    break;
            }
        }
        boolean airborne = target.getY() > boss.getY() + AIRBORNE_HEIGHT;
        if (airborne) {
            switch (move) {
                case DIVE_SWEEP:
                    factor *= AIRBORNE_DIVE_FACTOR;
                    break;
                case RANGED:
                    factor *= AIRBORNE_RANGED_FACTOR;
                    break;
                case KICK:
                    factor *= AIRBORNE_KICK_FACTOR;
                    break;
                default:
                    break;
            }
        }
        return factor;
    }

    /**
     * 反重复惩罚：<b>连续放同一招就要吃系数</b>，这是"打破全包零随机"的最后一道保险。
     * <p>普攻与远程刻意<b>不</b>吃惩罚：这两招是持续输出的骨架，本来就应该连发；
     * 给它们加反重复，等于把 BOSS 的输出节奏自己掐断（在"玩家打它却不还手"这类手感问题上，
     * 这一条会直接变成新的病灶）。
     */
    private double antiRepeatFactor(Move move) {
        if (move == Move.MELEE || move == Move.RANGED || move != this.lastMove) {
            return 1.0D;
        }
        return this.repeatStreak >= 2 ? ANTI_REPEAT_FACTOR_HARD : ANTI_REPEAT_FACTOR;
    }

    /** 距离分层：贴身层用 BOSS 自己那把尺子（含悬停高度折算），其余按常量切分 */
    private DistanceTier tierOf(AgaitolosEntity boss, LivingEntity target) {
        double distanceSqr = boss.distanceToSqr(target);
        if (distanceSqr <= boss.getMeleeAttackRangeSqr(target)) {
            return DistanceTier.POINT_BLANK;
        }
        double distance = Math.sqrt(distanceSqr);
        if (distance <= TIER_CLOSE_MAX) {
            return DistanceTier.CLOSE;
        }
        if (distance <= TIER_MID_MAX) {
            return DistanceTier.MID;
        }
        return distance <= TIER_FAR_MAX ? DistanceTier.FAR : DistanceTier.VERY_FAR;
    }

    /** 是否已进入二阶段（含三阶段）：瞬击/踢击的阶段门，口径与实体侧一致 */
    private boolean phaseTwoOrLater(AgaitolosEntity boss) {
        return boss.getPhase().combatOrdinal() >= AgaitolosPhase.PHASE_2.combatOrdinal();
    }

    /**
     * 是否已进入<b>阶段三</b>（阶段三五招的阶段门）。
     * <p>与 {@link #phaseTwoOrLater} 同一写法；复活阶段序数为 0，天然不在此列。
     * <p>为什么五招合并成一个判定而不是各自写 {@code getPhase().combatOrdinal() >= 3}：
     * 将来若把某一招下放到阶段二（例如规格表第 26 条把三重投掷列在"阶段二"条目里），
     * 只需要在这一处开口子，不必回头在五段判据里找。
     */
    private boolean phaseThreeOnly(AgaitolosEntity boss) {
        return boss.getPhase().combatOrdinal() >= AgaitolosPhase.PHASE_3.combatOrdinal();
    }

    // ---------------------------------------------------------------- 执行入口（全部转发给实体，本类不碰状态）

    /**
     * 执行选中的招式。
     * <p>返回"这一招是否真的起手了"：<b>只有起手成功才消耗整拍</b>，
     * 失败（瞬击落点校验没过、召唤异常等）只吃一个短重试窗口，避免"想放的招放不出来、还把节拍占满"。
     * <p>普攻特殊：它的出手闸在 {@link AgaitolosEntity#doHurtTarget} 内部（
     * 因为原版 {@code MeleeAttackGoal} 也走同一个方法），此处只负责"选中它"；
     * 无论是否命中都算这一拍用过（"这一刀挥出去了"的既有口径）。
     */
    private boolean execute(AgaitolosEntity boss, LivingEntity target, Move move) {
        switch (move) {
            case MELEE:
                boss.doHurtTarget(target);
                return true;
            case RANGED:
                boss.performRangedAttack(target, 1.0F);
                return true;
            case DIVE_SWEEP:
                return boss.startDiveSweep(target);
            case GUARD:
                return boss.startGuard(target);
            case REVERSAL:
                return boss.startCharge(target);
            case BLINK:
                return boss.startBlink(target);
            case KICK:
                return boss.startKick(target);
            // 阶段三五招：状态机在实体侧（AgaitolosPhaseThreeState），本类只转发执行入口。
            // 返回值语义与其它招一致：false = 没起手（复校没过），调用方只给一个短重试节拍
            case CALAMITY:
                return boss.startCalamity(target);
            case BOMBARD:
                return boss.startBombard(target);
            case TRIPLE_THROW:
                return boss.startTripleThrow(target);
            case GRAB_SWEEP:
                return boss.startGrabSweep(target);
            case GRAB_SMASH:
                return boss.startGrabSmash(target);
            default:
                return false;
        }
    }

    // ---------------------------------------------------------------- 巡逻与脱困

    /**
     * 脱战巡逻：<b>无目标时不再原地悬停</b>，到点换一个周边的低空巡航点。
     * <p>高度不参与：{@code AgaitolosMoveControl} 恒定维持"脚下地面 + 悬停高度"，
     * 这里只管水平目标点（多给一个 y 只是为了让导航建得出路径）。
     * <p>导航拿不到路径时静默跳过（下一轮再换点），不抛异常也不强推——
     * 巡逻是"顺手不发呆"，不该为它引入任何异常路径。
     */
    private void patrol(AgaitolosEntity boss) {
        if (this.patrolTicks > 0) {
            --this.patrolTicks;
            return;
        }
        this.patrolTicks = PATROL_INTERVAL_TICKS;
        double angle = boss.getRandom().nextDouble() * Math.PI * 2.0D;
        double radius = PATROL_RADIUS * (0.4D + boss.getRandom().nextDouble() * 0.6D);
        this.patrolX = boss.getX() + Math.cos(angle) * radius;
        this.patrolZ = boss.getZ() + Math.sin(angle) * radius;
        boss.getNavigation().moveTo(this.patrolX, boss.getY(), this.patrolZ, PATROL_SPEED);
    }

    /** 记录这一 tick 的水平位移（脱困判据的唯一输入） */
    private void trackProgress(AgaitolosEntity boss) {
        double movedX = Math.abs(boss.getX() - this.progressRefX);
        double movedZ = Math.abs(boss.getZ() - this.progressRefZ);
        if (movedX + movedZ < STUCK_MIN_PROGRESS) {
            if (this.stuckTicks < STUCK_TICKS + 1) {
                ++this.stuckTicks;
            }
        } else {
            this.stuckTicks = 0;
            this.progressRefX = boss.getX();
            this.progressRefZ = boss.getZ();
        }
    }

    /**
     * 卡住补救：<b>有目标却持续没有位移</b>时重新要一条路径。
     * <p>
     * <b>为什么不在这里重写寻路/加避障</b>：{@code AgaitolosMoveControl} 刻意不做避障、
     * 只做"直线追击"，是设计里的既定取舍（工艺上更顺、不会被大体积 BOSS 的贴墙抖动拖住），
     * 本轮只在"卡住"这一层加补救，不倒过来推翻它。
     * <p>
     * 两级补救：
     * <ol>
     *   <li>先让 {@code FlyingPathNavigation} 重算一条到目标的路径（正常情况到这一步就好）；</li>
     *   <li>路径确实拿不到（目标在导航不可达的位置）时，退化为"朝目标的直线目标点"——
     *       这与 MoveControl 本身的直线追击语义同源，不引入第二套寻路。</li>
     * </ol>
     * 两级都做完就把基准点重置，避免同一处卡点被反复触发（下一次判定需要再攒满 {@link #STUCK_TICKS}）。
     */
    private void rescueIfStuck(AgaitolosEntity boss, LivingEntity target) {
        // 已经站在近战可达范围里"不动"是正常的（那是在贴脸输出），不是卡住 —— 不加这一条，
        // 每次贴脸站满 2s 都会白重算一次路径（虽然无害，但属于把正常行为误判成异常）
        if (boss.isTargetWithinMeleeReach(target) || this.stuckTicks < STUCK_TICKS) {
            return;
        }
        this.stuckTicks = 0;
        this.progressRefX = boss.getX();
        this.progressRefZ = boss.getZ();
        double speed = AgaitolosPace.moveSpeed(boss);
        if (boss.getNavigation().moveTo(target, RESCUE_DIRECT_SPEED * speed)) {
            return;
        }
        boss.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), RESCUE_DIRECT_SPEED * speed);
    }
}
