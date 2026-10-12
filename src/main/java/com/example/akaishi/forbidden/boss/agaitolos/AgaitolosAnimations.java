package com.example.akaishi.forbidden.boss.agaitolos;

import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

/**
 * 阿盖托洛丝的动画名常量与控制器注册。
 * <p>
 * 常量必须与 {@code assets/forbidden_hex/animations/entity/agaitolos_stage1.animation.json} 中的 clip 名逐字一致，
 * 写错只会在实机渲染时抛 GeckoLibException，编译期查不出来。
 * <p>
 * GeckoLib 每个控制器同一时刻只能播一条 clip，故按用途拆成六个：
 * <ul>
 *   <li>{@link #CONTROLLER_MAIN} —— 常驻待机，按地面档状态在「飞行待机 / 落地过渡 / 贴地待机 / 升空过渡」四条
 *       入口间切换（见 {@code groundAwareIdle}）；死亡、格挡架势、蓄力或出场演出时停播，把骨骼让给对应控制器</li>
 *   <li>{@link #CONTROLLER_ACTION} —— 触发式动作（普攻 / 召唤凋零头 / 受击），由服务端 {@code triggerAnim} 派发</li>
 *   <li>{@link #CONTROLLER_DEATH} —— 死亡动画，用 GeckoLib 推荐的「状态轮询」而非触发器</li>
 *   <li>{@link #CONTROLLER_GUARD} —— 格挡架势，同为状态轮询式（架势是状态，不是一次性动作）</li>
 *   <li>{@link #CONTROLLER_CHARGE} —— 恶怨倒转蓄力，同为状态轮询式（蓄力是状态，不是一次性动作）</li>
 *   <li>{@link #CONTROLLER_INTRO} —— 出场演出，同为状态轮询式（出场是 4s 的状态，不是一次性动作）</li>
 *   <li>{@link #CONTROLLER_BOMBARD} —— 饱和轰炸（阶段三），同为状态轮询式（轰炸是一段持续状态，
 *       且唯一那条 clip 是 loop 的，触发式播放没有停止口）</li>
 * </ul>
 * 前六个控制器名自建立以来<b>一个字都没改</b>：阶段三五招里四条一次性 clip 复用 {@link #CONTROLLER_ACTION}，
 * 只有循环的 {@code bombard} 另开第七个控制器（纯新增，不动既有名与语义）。
 * 动画名与控制器名都收在本类，实体侧只调语义方法（{@link #playAttack} 等），不硬写字符串。
 */
public final class AgaitolosAnimations {

    /** 常驻飞行待机（loop 4s） */
    public static final String IDLE_FLIGHT = "animation.agaitolos.idle_flight";
    /** 地面待机（loop 4s，与 {@link #IDLE_FLIGHT} 同长）：地面档期间常驻 */
    public static final String IDLE_GROUND = "animation.agaitolos.idle_ground";
    /**
     * 落地过渡（PLAY_ONCE，0.6s = 12 tick）。
     * <p>与 {@link #IDLE_GROUND} <b>构造级对齐</b>：本 clip 的末帧姿态 == {@code idle_ground} 的首帧姿态，
     * 故「land → idle_ground」串接不会跳变（时长也与实体侧落地过渡窗口逐字对齐）。
     */
    public static final String LAND = "animation.agaitolos.land";
    /**
     * 升空过渡（PLAY_ONCE，0.6s = 12 tick）。
     * <p>与 {@link #IDLE_FLIGHT} <b>构造级对齐</b>：本 clip 的末帧姿态 == {@code idle_flight} 的首帧姿态，
     * 故「takeoff → idle_flight」串接不会跳变（时长也与实体侧升空过渡窗口逐字对齐）。
     */
    public static final String TAKEOFF = "animation.agaitolos.takeoff";
    /** 普攻挥舞 */
    public static final String ATTACK_SLASH = "animation.agaitolos.attack_slash";
    /** 召唤凋零头颅 */
    public static final String CAST_SKULL = "animation.agaitolos.cast_skull";
    /** 俯冲镰扫 */
    public static final String DIVE_SWEEP = "animation.agaitolos.dive_sweep";
    /** 蓄力（loop 2s） */
    public static final String CHARGE = "animation.agaitolos.charge";
    /** 格挡架势 */
    public static final String GUARD = "animation.agaitolos.guard";
    /** 受击 */
    public static final String HURT = "animation.agaitolos.hurt";
    /** 死亡 */
    public static final String DEATH = "animation.agaitolos.death";
    /**
     * 出场演出（PLAY_ONCE，4.0s = 80 tick）。
     * <p>⚠ 这条 clip 由建模任务产出，<b>名字必须逐字为 {@code animation.agaitolos.intro}</b>：
     * 写错只会在实机渲染时抛 GeckoLibException，编译期查不出来（同本类顶部说明）。
     */
    public static final String INTRO = "animation.agaitolos.intro";

    // ---------------------------------------------------------------- 阶段三五招（2026-09-21 补）

    /**
     * 三重投掷（PLAY_ONCE，2.0s = 40 tick）：空中三连扇形弹体。
     * <p><b>节拍必须与实体侧逐字对齐</b>：三次出手落在 0.3 / 0.6 / 0.9s = <b>6 / 12 / 18 tick</b>，
     * 实体侧的发弹节拍常量（见 {@code AgaitolosTripleThrowSkill#BEAT_TICKS}）改一次就要同步看这里。
     */
    public static final String TRIPLE_THROW = "animation.agaitolos.triple_throw";

    /**
     * 投技①「踩住 + 镰刀横扫」（PLAY_ONCE，1.1s = 22 tick）。
     * <p>抓取态的锁定期与实体侧逐字对齐：抓取持续到本 clip 播完（{@code AgaitolosGrabSkill#HOLD_TICKS}），
     * 25% 最大生命值伤害落在 clip 的 0.5s = 10 tick（{@code #HOLD_DAMAGE_TICK}）。
     */
    public static final String GRAB_SWEEP = "animation.agaitolos.grab_sweep";

    /**
     * 投技②「抓摔 + 劈击」（PLAY_ONCE，1.4s = 28 tick）：0.6s = 12 tick 为发力峰值。
     * <p>只接<b>服务端伤害结算</b>（{@code AgaitolosGrabSkill#SMASH_DAMAGE_TICK}）。
     */
    public static final String GRAB_SMASH = "animation.agaitolos.grab_smash";

    /** 天魔＊灾（PLAY_ONCE，2.2s = 44 tick）：施放不可名状 + 精神伤害改写 */
    public static final String CALAMITY_CAST = "animation.agaitolos.calamity_cast";

    /**
     * 饱和轰炸（<b>loop</b>，2.0s = 40 tick）：两拍投弹落在 0.5 / 1.5s = <b>10 / 30 tick</b>，
     * 即循环起来每 20 tick 一发（实体侧 {@code AgaitolosBombardSkill#BEAT_TICKS}）。
     * <p>因为是循环 clip，它<b>不能</b>走动作控制器的触发式播放（触发式循环没有停止口），
     * 与 charge 同款走<b>状态轮询</b>：见 {@link #CONTROLLER_BOMBARD}。
     */
    public static final String BOMBARD = "animation.agaitolos.bombard";

    /** 主控制器名：常驻待机 */
    public static final String CONTROLLER_MAIN = "main";
    /** 动作控制器名：触发式动画（普攻 / 召唤 / 受击）共用 */
    public static final String CONTROLLER_ACTION = "action";
    /** 死亡控制器名：状态轮询式 */
    public static final String CONTROLLER_DEATH = "death";
    /** 格挡控制器名：状态轮询式（架势由 DATA_GUARDING 同步，两端各自判定） */
    public static final String CONTROLLER_GUARD = "guard";
    /** 蓄力控制器名：状态轮询式（蓄力由 DATA_CHARGING 同步，两端各自判定） */
    public static final String CONTROLLER_CHARGE = "charge";
    /** 出场控制器名：状态轮询式（出场由 DATA_INTRO 同步，两端各自判定） */
    public static final String CONTROLLER_INTRO = "intro";

    /**
     * 饱和轰炸控制器名：状态轮询式（轰炸由 DATA_BOMBARDING 同步，两端各自判定）。
     * <p>唯一一条<b>循环</b>的攻击 clip：轰炸是一段持续状态（最长 {@code BOMBARD_DURATION_TICKS}），
     * 而不是"某刻触发一次"的动作，故与 guard/charge/intro 同款走状态轮询，
     * 既有六个控制器名与它们的语义一个都没动。
     */
    public static final String CONTROLLER_BOMBARD = "bombard";

    /** 控制器过渡时长（tick）：避免姿态硬切 */
    public static final int TRANSITION_TICKS = 5;

    /** 动作控制器过渡时长：动作本身只有 0.5~1.2s，过渡必须更短，否则首尾糊成一团 */
    public static final int ACTION_TRANSITION_TICKS = 2;

    /**
     * 触发键名。刻意与 clip 同名：{@code triggerAnim} 传错名字时不会报错、只会静默不播，
     * 同名时「动画没出来」与「json 里没这条 clip」能一眼对上。
     */
    private static final String TRIGGER_ATTACK = "attack_slash";
    private static final String TRIGGER_CAST = "cast_skull";
    private static final String TRIGGER_DIVE_SWEEP = "dive_sweep";
    private static final String TRIGGER_HURT = "hurt";
    private static final String TRIGGER_TRIPLE_THROW = "triple_throw";
    private static final String TRIGGER_GRAB_SWEEP = "grab_sweep";
    private static final String TRIGGER_GRAB_SMASH = "grab_smash";
    private static final String TRIGGER_CALAMITY_CAST = "calamity_cast";

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(IDLE_FLIGHT);
    // 地面档三条：与上面 IDLE 同型（常驻 → 循环），只是把"待机 clip"换成 idle_ground；
    // 两个过渡态各用 thenPlay 打头带一条 PLAY_ONCE clip，播完由 GeckoLib 自己的动画队列推进到后面的 loop 阶段。
    // 串接不跳变的前提有两条，都已满足：
    //   ① 过渡 clip 末帧 == 后接待机 clip 首帧（构造级对齐）；
    //   ② 过渡态与稳态的切换点落在 clip 边界上 —— 实体侧的过渡窗口（LAND/TAKEOFF_TRANSITION_TICKS）
    //      与 clip 等长，且"进入过渡 / 退出过渡"两次写入走的是同一份同步数据（同一实体的数据更新有序到达），
    //      故客户端观测到的间隔恒等于服务端间隔，不随延迟抖动。
    private static final RawAnimation GROUND_ANIM = RawAnimation.begin().thenLoop(IDLE_GROUND);
    private static final RawAnimation LAND_ANIM = RawAnimation.begin().thenPlay(LAND).thenLoop(IDLE_GROUND);
    private static final RawAnimation TAKEOFF_ANIM = RawAnimation.begin().thenPlay(TAKEOFF).thenLoop(IDLE_FLIGHT);
    // 四条动作 clip 在 json 里 loop 均为缺省（PLAY_ONCE）：thenPlay 播完自停，控制器随即交还给状态回调
    private static final RawAnimation ATTACK_ANIM = RawAnimation.begin().thenPlay(ATTACK_SLASH);
    private static final RawAnimation CAST_ANIM = RawAnimation.begin().thenPlay(CAST_SKULL);
    private static final RawAnimation DIVE_SWEEP_ANIM = RawAnimation.begin().thenPlay(DIVE_SWEEP);
    private static final RawAnimation HURT_ANIM = RawAnimation.begin().thenPlay(HURT);
    private static final RawAnimation DEATH_ANIM = RawAnimation.begin().thenPlay(DEATH);
    // guard 在 json 里是 1.2s 非循环 clip ⇒ 与其同步的 GUARD_DURATION_TICKS=24，thenPlay 正好播完一次
    private static final RawAnimation GUARD_ANIM = RawAnimation.begin().thenPlay(GUARD);
    // charge 在 json 里 loop=true（2s 循环）⇒ 必须用 thenLoop 而非 thenPlay，
    // 否则一个循环播完就会停在末帧，而蓄力要持续 12s
    private static final RawAnimation CHARGE_ANIM = RawAnimation.begin().thenLoop(CHARGE);
    // 出场在 json 里是 PLAY_ONCE（4.0s = INTRO_DURATION_TICKS=80 tick，逐字对齐）
    // ⇒ 必须用 thenPlay：控制器与节拍都靠 DATA_INTRO 轮询，播完一遍刚好落在实体清状态那一 tick
    private static final RawAnimation INTRO_ANIM = RawAnimation.begin().thenPlay(INTRO);

    // 阶段三五招：四条一次性 clip 与既有动作同款（json 里 loop 缺省 = PLAY_ONCE ⇒ thenPlay 播完自停），
    // 一条循环 clip（bombard，json 里 loop=true）必须用 thenLoop，且只能由状态控制器驱动（见下方注册）
    private static final RawAnimation TRIPLE_THROW_ANIM = RawAnimation.begin().thenPlay(TRIPLE_THROW);
    private static final RawAnimation GRAB_SWEEP_ANIM = RawAnimation.begin().thenPlay(GRAB_SWEEP);
    private static final RawAnimation GRAB_SMASH_ANIM = RawAnimation.begin().thenPlay(GRAB_SMASH);
    private static final RawAnimation CALAMITY_CAST_ANIM = RawAnimation.begin().thenPlay(CALAMITY_CAST);
    // 与 CHARGE_ANIM 同一取舍：loop=true 的 clip 用 thenPlay 会停在末帧，必须 thenLoop
    private static final RawAnimation BOMBARD_ANIM = RawAnimation.begin().thenLoop(BOMBARD);

    private AgaitolosAnimations() {
    }

    // ---------------------------------------------------------------- 语义播放入口

    /** 近战普攻命中：播一次挥砍。仅服务端调用，由 GeckoLib 自动同步给追踪客户端 */
    public static void playAttack(AgaitolosEntity boss) {
        boss.triggerAnim(CONTROLLER_ACTION, TRIGGER_ATTACK);
    }

    /** 发射凋零头颅：播一次施法。仅服务端调用，同步方式同上 */
    public static void playCast(AgaitolosEntity boss) {
        boss.triggerAnim(CONTROLLER_ACTION, TRIGGER_CAST);
    }

    /** 受击真正吃下伤害：播一次受击。仅服务端调用，同步方式同上 */
    public static void playHurt(AgaitolosEntity boss) {
        boss.triggerAnim(CONTROLLER_ACTION, TRIGGER_HURT);
    }

    /** 俯冲镰扫结算到人：播一次横扫（1.5s 非循环 clip）。仅服务端调用，同步方式同上 */
    public static void playDiveSweep(AgaitolosEntity boss) {
        boss.triggerAnim(CONTROLLER_ACTION, TRIGGER_DIVE_SWEEP);
    }

    /** 三重投掷起手：播一次三连出手（2.0s 非循环 clip，三次出手落在 clip 的 6/12/18 tick）。仅服务端调用 */
    public static void playTripleThrow(AgaitolosEntity boss) {
        boss.triggerAnim(CONTROLLER_ACTION, TRIGGER_TRIPLE_THROW);
    }

    /** 投技①起手：播一次踩住镰扫（1.1s 非循环 clip）。仅服务端调用 */
    public static void playGrabSweep(AgaitolosEntity boss) {
        boss.triggerAnim(CONTROLLER_ACTION, TRIGGER_GRAB_SWEEP);
    }

    /** 投技②起手：播一次抓摔劈击（1.4s 非循环 clip）。仅服务端调用 */
    public static void playGrabSmash(AgaitolosEntity boss) {
        boss.triggerAnim(CONTROLLER_ACTION, TRIGGER_GRAB_SMASH);
    }

    /** 天魔＊灾起手：播一次施法（2.2s 非循环 clip）。仅服务端调用 */
    public static void playCalamityCast(AgaitolosEntity boss) {
        boss.triggerAnim(CONTROLLER_ACTION, TRIGGER_CALAMITY_CAST);
    }

    // ---------------------------------------------------------------- 控制器注册

    /**
     * 注册动画控制器。
     * <p>
     * 死亡动画走「状态轮询」而非触发器：这是 GeckoLib 自己的推荐做法
     * （见 4.4.9 {@code DefaultAnimations#genericDeathController}：{@code isDeadOrDying() ? setAndContinue(DIE) : STOP}，transition 0）。
     * 好处是死亡状态由 {@code LivingEntity#isDeadOrDying()}（= 生命值 ≤ 0，原版本就同步）驱动，
     * 服务端/客户端各自都能判定，不需要额外的触发同步，也不需要改 renderer。
     */
    public static void registerControllers(AgaitolosEntity entity, AnimatableManager.ControllerRegistrar controllers) {
        // 常驻待机：死亡 / 格挡架势 / 蓄力 / 出场演出时主动 STOP —— 多个控制器会同时驱动同一批骨骼，
        // 不停播会和死亡姿势 / 格挡架势 / 蓄力姿势 / 出场姿势互相拉扯（共用同一批骨骼，必须靠状态互斥）。
        // 出场也要列在这里：出场那 4s 一直在播，若 MAIN 不停，待机与降临动作会抢同一批骨骼。
        // 过渡时长沿用 TRANSITION_TICKS=5：真正需要混合的只有"飞行待机中途 → 落地过渡起手"这一处
        // （两条 clip 在那个接缝上没有对齐关系），用 5 tick 盖住姿态差；
        // 而 land→idle_ground / takeoff→idle_flight 两个接缝的端点已构造级对齐，5 tick 混合等于"同姿态混同姿态"，无副作用，
        // 故不必为地面档单开一档过渡时长。
        controllers.add(new AnimationController<>(entity, CONTROLLER_MAIN, TRANSITION_TICKS,
                state -> state.getAnimatable().isDeadOrDying() || state.getAnimatable().isGuarding()
                        || state.getAnimatable().isCharging() || state.getAnimatable().isIntroPlaying()
                        || state.getAnimatable().isBombarding()
                        ? PlayState.STOP
                        : state.setAndContinue(groundAwareIdle(state.getAnimatable()))));

        // 动作控制器：状态回调只在「没有触发动画在播」时才会走到这里，此时无事可做 ⇒ STOP 等下一次 triggerAnim。
        // 不能返回 CONTINUE，否则控制器永远停不下来，触发式动画结束后会卡在最后一帧。
        controllers.add(new AnimationController<>(entity, CONTROLLER_ACTION, ACTION_TRANSITION_TICKS,
                state -> PlayState.STOP)
                .triggerableAnim(TRIGGER_ATTACK, ATTACK_ANIM)
                .triggerableAnim(TRIGGER_CAST, CAST_ANIM)
                .triggerableAnim(TRIGGER_DIVE_SWEEP, DIVE_SWEEP_ANIM)
                .triggerableAnim(TRIGGER_HURT, HURT_ANIM)
                .triggerableAnim(TRIGGER_TRIPLE_THROW, TRIPLE_THROW_ANIM)
                .triggerableAnim(TRIGGER_GRAB_SWEEP, GRAB_SWEEP_ANIM)
                .triggerableAnim(TRIGGER_GRAB_SMASH, GRAB_SMASH_ANIM)
                .triggerableAnim(TRIGGER_CALAMITY_CAST, CALAMITY_CAST_ANIM));

        // 死亡：transition 照上游取 0，死亡瞬间不需要过渡
        controllers.add(new AnimationController<>(entity, CONTROLLER_DEATH, 0,
                state -> state.getAnimatable().isDeadOrDying() ? state.setAndContinue(DEATH_ANIM) : PlayState.STOP));

        // 格挡架势：与死亡同理走「状态轮询」而不是触发器 —— 架势是持续状态（由 DATA_GUARDING 同步），
        // 两端各自轮询即可，不需要一次性的触发同步，实体侧也就不用加 playGuard 入口。
        // 过渡取动作级的 2 tick：姿态只有 24 tick，过渡太长会把起手/收手糊掉。
        controllers.add(new AnimationController<>(entity, CONTROLLER_GUARD, ACTION_TRANSITION_TICKS,
                state -> state.getAnimatable().isGuarding() ? state.setAndContinue(GUARD_ANIM) : PlayState.STOP));

        // 蓄力（恶怨倒转）：与 guard 同款「状态轮询」——蓄力是持续状态（由 DATA_CHARGING 同步），
        // 两端各自轮询即可，不需要一次性的触发同步，实体侧也就不用加 playCharge 入口。
        // 过渡同样取动作级的 2 tick：蓄力起手要利落，过渡太长会把"猛地举起球"糊掉。
        controllers.add(new AnimationController<>(entity, CONTROLLER_CHARGE, ACTION_TRANSITION_TICKS,
                state -> state.getAnimatable().isCharging() ? state.setAndContinue(CHARGE_ANIM) : PlayState.STOP));

        // 出场：与 guard/charge 同款「状态轮询」——出场是<b>持续状态</b>（固定 4s，由 DATA_INTRO 同步），
        // 不是"某刻触发一次"的动作，两端各自轮询即可，实体侧也就不需要 playIntro 触发入口。
        // 过渡取 0（而非动作级的 2）：召唤那一刻 idle 才刚起，没有任何既存姿态值得混合；
        // 用 2 tick 反而会把"从空中降临"的头两帧糊在待机姿态上，与召唤瞬间的粒子爆发错开。
        controllers.add(new AnimationController<>(entity, CONTROLLER_INTRO, 0,
                state -> state.getAnimatable().isIntroPlaying() ? state.setAndContinue(INTRO_ANIM) : PlayState.STOP));

        // 饱和轰炸：与 charge 逐行同款「状态轮询」——轰炸是一段持续状态（由 DATA_BOMBARDING 同步），
        // 两端各自轮询即可，不需要一次性的触发同步（触发式的循环 clip 没有停止口）。
        // 过渡取动作级的 2 tick：投弹动作要利落，过渡太长会把"抬手→甩头"的节拍糊掉。
        controllers.add(new AnimationController<>(entity, CONTROLLER_BOMBARD, ACTION_TRANSITION_TICKS,
                state -> state.getAnimatable().isBombarding() ? state.setAndContinue(BOMBARD_ANIM) : PlayState.STOP));
    }

    // ---------------------------------------------------------------- 地面档分支

    /**
     * 按地面档状态挑「常驻待机」该播哪一条入口。
     * <p>
     * <b>为什么读同步状态而不是 {@code isPerched()} 布尔</b>：那个布尔只表达"当前垂直高度档"（空中 / 贴地），
     * 而动画还要区分"正处在过渡中"——落地那 0.6s 必须播 {@code land}、升空那 0.6s 必须播 {@code takeoff}，
     * 否则这两条过渡 clip 永远不会被播到（等于白做）。四态 int（见 {@code AgaitolosEntity#getPerchState()}）
     * 一次把"稳态还是过渡"与"朝哪个方向"都带出来，客户端只需一个 accessor、一次比较。
     * <p>
     * 纯空中档返回的是既有的 {@link #IDLE}（{@code thenLoop(idle_flight)}），
     * 与地面档接入前逐字相同 ⇒ 飞行待机链路不受影响。
     */
    private static RawAnimation groundAwareIdle(AgaitolosEntity boss) {
        switch (boss.getPerchState()) {
            case AgaitolosEntity.PERCH_STATE_LANDING:
                return LAND_ANIM;
            case AgaitolosEntity.PERCH_STATE_PERCHED:
                return GROUND_ANIM;
            case AgaitolosEntity.PERCH_STATE_TAKEOFF:
                return TAKEOFF_ANIM;
            default:
                return IDLE;
        }
    }
}
