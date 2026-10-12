package com.example.akaishi.forbidden.boss.agaitolos;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 阿盖托洛丝【下界本源】实体主体。
 * <p>
 * P1 交付骨架（属性 + 受击管线 + GeckoLib 可见渲染），P3 在本类上补<b>编排</b>：
 * 阶段机（阈值判定 → 推进）、复活阶段（无敌 + 回血 + 结束击飞）；
 * P5 再补<b>二阶段两招</b>（瞬击 / 高速踢击）与「二阶段比一阶段更快速」的阶段节奏倍率
 * （倍率表收在 {@link AgaitolosPace}，本类只接线）。
 * 阶段独立成类（{@link AgaitolosPhase}），本类只做编排，不把逻辑堆成巨型类。
 * <p>
 * <b>血条不由本类持有</b>：显示职责整体移交客户端自定义 overlay（forge 侧
 * {@code AgaitolosBossBarOverlay}）。原版 {@code ServerBossEvent} 血条路线（早期那个只做参与者对账的
 * 服务端血条类）已整体删除，本目录下不再有任何服务端血条代码 ——
 * 它既画不出需求里的铭牌贴图与阶段换皮，留着还会与自定义血条<b>同时显示两条</b>，
 * 属于典型「看得到用不到」的残留。本类只负责把阶段/复活状态同步出去供客户端读取。
 * <p>
 * <b>2026-09-21 补：战斗决策层与"不还手"根因修复</b>
 * <ul>
 *   <li><b>根因</b>：原版 {@code MeleeAttackGoal} 判可达距离时只用自己的 {@code getBbWidth()}²
 *       （实测字节码，不读 {@link #getMeleeAttackRangeSqr}），而本 BOSS 常态悬停在玩家上方 2 格 ⇒
 *       光竖直差就 4.0 &gt; 3.84，<b>近战一次都挥不出</b>；同时远程的 {@code RangedAttackGoal}
 *       与近战 Goal 抢同一组 Flag、优先级又更低 ⇒ 被永久饿死。两者叠加 = 玩家贴脸打它、它只会飘着。
 *       修法见 {@link AgaitolosMeleeAttackGoal}（近战判据）+ {@code AgaitolosSkillDirector}（远程改由决策层放）。</li>
 *   <li><b>决策层</b>：起手判定从各 {@code tickXxx} 的顺次闸整体上移到 {@link AgaitolosSkillDirector}
 *       （距离分层 + 权重随机 + 全局动作锁 + 阶段/玩家状态修正）；本类只保留<b>进行中状态</b>
 *       （{@code tickChargeState}/{@code tickGuardState}/{@code tickDiveState}）与<b>执行入口</b>
 *       （{@code startGuard}/{@code startCharge}/{@code startDiveSweep}/{@code startBlink}/{@code startKick}）。</li>
 *   <li><b>报复与脱战</b>：{@link #hurt} 一进来就 {@code retaliate}（被打即锁定攻击者）+
 *       目标选择器新增 {@code HurtByTargetGoal}；脱战低空巡逻、卡住重寻路都在决策层里。</li>
 * </ul>
 * <p>
 * <b>2026-09-21 补（第二轮：生存实测四项反馈）</b>
 * <ul>
 *   <li><b>召唤太频繁</b>：{@link AgaitolosGuardCharge#MINION_COOLDOWN_TICKS} 300 → 900（阶段分化仍走 {@link AgaitolosPace}，
 *       见该常量的改前/改后表）；叠加"整队回收"后场上不再叠罗汉。</li>
 *   <li><b>召唤物内斗</b>：{@code AgaitolosMinionSkill} 用原版 Team 把 BOSS 与召唤物放进同一支队伍
 *       （原版唯一的友军判定），并让 BOSS 自己的凋零头不再命中自家召唤物。</li>
 *   <li><b>技能结束召唤物不消失</b>：四个出口统一调 {@code AgaitolosMinionSkill#dismissAll}
 *       —— {@link AgaitolosGuardCharge#endChargeCompleted} / {@link AgaitolosGuardCharge#endChargeInterrupted} /
 *       {@link AgaitolosPhaseMachine#enterRespawn} 与 {@link #die}、{@link #remove}
 *       （区块卸载那一支由召唤物自身的看门狗 {@code AgaitolosMinion} 兜）。</li>
 *   <li><b>多待在地上</b>：新增<b>地面档</b> {@link #isPerched()}（近身缠斗时贴地，最短 3s、最迟 10s 后升空），
 *       高度消费在 {@code AgaitolosMoveControl#PERCH_HEIGHT}，切换节律见 {@link AgaitolosPerchMachine#tickPerchState}。</li>
 *   <li><b>贴地不再播飞行待机（第三轮）</b>：地面档三件套 clip（{@code land} / {@code idle_ground} /
 *       {@code takeoff}）已接入动画 MAIN 控制器，客户端靠同步状态 {@link #getPerchState()} 分支，
 *       见 {@code AgaitolosAnimations#groundAwareIdle}。</li>
 * </ul>
 * <p>
 * <b>2026-09-21 补（阶段三三项"无新动画"内容）</b>
 * <ul>
 *   <li><b>凋亡</b>：阶段三起，BOSS 的三处"上凋零"全部改施加 {@code forbidden_hex:doom}
 *       （统一入口 {@link AgaitolosDoom}，读凋零的结算也一并认凋亡），效果定义见 {@code DoomEffect}；</li>
 *   <li><b>免疫远程</b>：见 {@link #isProjectileImmune()} 与 {@link AgaitolosDamageRules#resolve} 的第 ⑥ 闸
 *       （弹射物完全免伤，但"被玩家打回来的凋零头"走自伤通道、不受此闸约束）；</li>
 *   <li><b>超低空悬停</b>：阶段三空中档降到 {@code AgaitolosMoveControl#PHASE_3_HOVER_HEIGHT}（2.0 → 1.0），
 *       近战可达尺子仍按<b>最大</b>空中档折算（见 {@link #getMeleeAttackRangeSqr}）。</li>
 * </ul>
 * <p>
 * <b>2026-09-21 补（阶段三五招：真有 clip 了）</b>
 * <ul>
 *   <li><b>五条新 clip</b>（{@code triple_throw} / {@code grab_sweep} / {@code grab_smash} /
 *       {@code calamity_cast} / {@code bombard}）接进既有控制器：四条一次性动作复用 ACTION 触发式，
 *       循环的 {@code bombard} 另开第七个<b>状态轮询</b>控制器（同步位 {@code DATA_BOMBARDING}）；</li>
 *   <li><b>状态机收在 {@code AgaitolosPhaseThreeState}</b>：三连出手节拍 / 高空轰炸 /
 *       抓取态（踩住）/ 劈击 / 施法，本类只保留同步位、转发入口与每 tick 一次推进；</li>
 *   <li><b>起手仍归决策层</b>：五招以"仅阶段三 + 吃大招封印 + 各自的距离层"接进
 *       {@code AgaitolosSkillDirector} 的权重表，保命招硬闸与距离分层一字未动；</li>
 *   <li><b>精神伤害改写</b>（天魔＊灾）：唯一口径在 {@code AgaitolosPsychic}，
 *       玩家侧持久标记落在既有 capability（死亡/换维度/重启都不丢）。</li>
 * </ul>
 * <p>
 * <b>2026-09-24 拆分（SRP，本类不再堆逻辑）</b>：本类只保留同步数据与访问器、状态字段、
 * {@code aiStep} 编排、属性注册与原版覆写的一行委托；各块职责的实体侧实现分家为同包协作类 ——
 * 计时/冷却 {@link AgaitolosTimers}、地面档 {@link AgaitolosPerchMachine}、出场演出 {@link AgaitolosIntroMachine}、
 * 架势/蓄力 {@link AgaitolosGuardCharge}、俯冲 {@link AgaitolosDiveMachine}、阶段/复活 {@link AgaitolosPhaseMachine}、
 * 二阶段两招 {@link AgaitolosPhaseTwoSkills}、存档 {@link AgaitolosNbtCodec}、
 * 受击/出手钩子 {@link AgaitolosCombatHooks}、死亡/移除 {@link AgaitolosLifecycle}、飞行物理 {@link AgaitolosMotion}。
 * 全部为静态协作类（实体作参数，同 {@link AgaitolosDamageRules} / {@link AgaitolosScaling} 的既有写法），
 * 逻辑与数值逐位未动。
 */
public class AgaitolosEntity extends Monster implements GeoEntity, RangedAttackMob {

    /** 同步数据：当前阶段序号（0/1/2 = PHASE_1/2/3）；客户端 P7 起据此选模型与动画 */
    private static final EntityDataAccessor<Integer> DATA_PHASE =
            SynchedEntityData.defineId(AgaitolosEntity.class, EntityDataSerializers.INT);

    /** 同步数据：是否处于复活阶段；客户端据此切演出（P7）与模型复位 */
    private static final EntityDataAccessor<Boolean> DATA_RESPAWNING =
            SynchedEntityData.defineId(AgaitolosEntity.class, EntityDataSerializers.BOOLEAN);

    /** 同步数据：是否处于格挡架势；动画的 guard 控制器两端各自轮询它（无需额外触发同步） */
    private static final EntityDataAccessor<Boolean> DATA_GUARDING =
            SynchedEntityData.defineId(AgaitolosEntity.class, EntityDataSerializers.BOOLEAN);

    /** 同步数据：是否处于「恶怨倒转」蓄力；动画的 charge 控制器两端各自轮询它（同 DATA_GUARDING 的分工） */
    private static final EntityDataAccessor<Boolean> DATA_CHARGING =
            SynchedEntityData.defineId(AgaitolosEntity.class, EntityDataSerializers.BOOLEAN);

    /**
     * 同步数据：是否正在播出场演出。
     * <p>与架势 / 蓄力同属<b>状态轮询式</b>，故走同步数据而不是一次性触发同步：出场是一个持续 4s 的状态，
     * 两端各自读它来驱动动画控制器（见 {@code AgaitolosAnimations#CONTROLLER_INTRO}），
     * 服务端结算（位移/闸门）也读同一份真源，不需要额外的触发包。
     */
    private static final EntityDataAccessor<Boolean> DATA_INTRO =
            SynchedEntityData.defineId(AgaitolosEntity.class, EntityDataSerializers.BOOLEAN);

    /**
     * 同步数据：<b>地面档展示状态</b>（四态，见 {@link #PERCH_STATE_AIRBORNE} 等常量）。
     * <p>
     * 与架势 / 蓄力 / 出场同属<b>状态轮询式</b>：状态由服务端写、客户端读（{@link #getPerchState()}），
     * 动画的 MAIN 控制器两端各自轮询同一份真源（见 {@code AgaitolosAnimations#groundAwareIdle}），
     * 不需要一次性的触发同步，实体侧也就不必加 playXxx 入口。
     * <p>
     * <b>为什么不直接同步 {@link #isPerched()} 那个布尔</b>：高度档布尔只够驱动位移（空中 2 格 / 贴地 0 格），
     * 而动画还要知道"正在落地过渡还是正在升空过渡"才能播 land / takeoff 两条 0.6s clip；
     * 用四态 int 一个 accessor 同时表达"稳态/过渡"与"方向"，且整轮节律里只写 4 次
     * （见 {@link AgaitolosPerchMachine#tickPerchState}）。
     */
    private static final EntityDataAccessor<Integer> DATA_PERCH_STATE =
            SynchedEntityData.defineId(AgaitolosEntity.class, EntityDataSerializers.INT);

    /**
     * 同步数据：是否正在「饱和轰炸」（阶段三）。
     * <p>与架势 / 蓄力 / 出场同属<b>状态轮询式</b>：轰炸是一段最长 {@code BOMBARD_DURATION_TICKS} 的持续状态，
     * 客户端动画控制器轮询它来驱动<b>那条 loop clip</b>（见 {@code AgaitolosAnimations#CONTROLLER_BOMBARD}）
     * —— 触发式播放对循环 clip 没有停止口，故只能走状态。
     * <p>写方只有一个：{@code AgaitolosPhaseThreeState}（起手立、期满/中止撤），故两端不会读到中间态。
     */
    private static final EntityDataAccessor<Boolean> DATA_BOMBARDING =
            SynchedEntityData.defineId(AgaitolosEntity.class, EntityDataSerializers.BOOLEAN);

    /**
     * 俯冲镰扫的冲锋起手最大水平距离（格）：更远就交给飞行巡航接近。待调手感值 / P8 转配置项
     * <p>留在实体上：{@code AgaitolosSkillDirector} 的距离分层直接引用它（{@code TIER_MID_MAX}）。
     * 其余俯冲手感常量在 {@link AgaitolosDiveMachine}。
     */
    public static final double DIVE_TRIGGER_RANGE = 16.0D;

    // ---------------------------------------------------------------- 出场演出分镜常量（消费方见下注）

    /**
     * 出场 ① 段（粒子柱）的结束 tick：0~20t。待调手感值 / P8 转配置项
     * <p>留在实体上：{@code AgaitolosFx#introPillar / introShockRing} 的分镜窗口直接引用它
     * （演出编排本体在 {@link AgaitolosIntroMachine}）。
     */
    public static final int INTRO_PILLAR_END_TICKS = 20;

    /**
     * 出场 ② 段（降临位移）的时长（tick）：0~40t 从"悬停高度上方 {@link AgaitolosIntroMachine#INTRO_DESCENT_HEIGHT} 格"降到悬停高度。
     * <p>这个常量同时是 ③ 段的起点（落地冲击环在降临完成那一 tick 起爆）——刻意只留一份分界，
     * 两处各写 40 迟早会漂移成"还没落地就起环"。待调手感值 / P8 转配置项
     */
    public static final int INTRO_DESCENT_TICKS = 40;

    /** 出场 ③ 段（落地冲击环）的结束 tick：40~60t（起点复用 {@link #INTRO_DESCENT_TICKS}）。待调手感值 / P8 转配置项 */
    public static final int INTRO_SHOCK_END_TICKS = 60;

    /**
     * 死亡演出时长（tick）：50 = 2.5s，<b>与 death clip 时长对齐 —— 改 clip 长度要同步改这里</b>。
     * <p>原版 {@code LivingEntity#tickDeath()} 在 {@code deathTime >= 20}（1s）就 {@code remove(KILLED)}，
     * 2.5s 的死亡动画只能看到前 40%；本类把收尾门槛抬到本常量（见 {@link AgaitolosLifecycle#tickDeath}）。
     * 留在实体上：{@code AgaitolosActionFx#deathSoulRise} 直接引用它。待调手感值 / P8 转配置项
     */
    public static final int DEATH_TICKS = 50;

    /**
     * 主动索敌锁定后、<b>失去视线仍继续追击</b>的容忍时长（tick）：200 = 10s。<b>待调手感值 / P8 转配置项</b>
     * <p>取值依据：它由 {@code TargetGoal#setUnseenMemoryTicks} 消费（原版默认 60 = 3s）。
     * 60 tick 太短——玩家绕一根柱子、或者跳下一个平台，BOSS 就会"跟丢"并停在原地；
     * 而本 BOSS 是飞行单位、本来就该咬得住。10s 足够玩家做一次完整的走位拉扯，
     * 又不至于让它隔着半个牢狱死追（超过 {@code FOLLOW_RANGE} 仍会正常脱锁）。
     * <p>注意：这一项只影响"<b>锁定之后</b>能不能看不见"，<b>索敌</b>本身仍要求视线
     * （{@code TargetingConditions#checkLineOfSight} 默认为真，且不随 mustSee 改变）；
     * "被打就锁定"那条路径由 {@code HurtByTargetGoal} 负责，它内部固定 300 tick。
     */
    public static final int TARGET_UNSEEN_MEMORY_TICKS = 200;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** 上次成功受击的游戏刻。初值取远小于当前刻的负数，保证首次受击不被 0.2s 冷却拦下 */
    long lastHurtGameTime = -100L; // 由 AgaitolosCombatHooks 协作类读写

    /** 复活阶段剩余 tick（服务端权威；不参与同步，客户端只需要"是否在复活"） */
    int respawnTicks; // 由 AgaitolosPhaseMachine / AgaitolosNbtCodec 协作类读写

    /**
     * 「召唤即进入复活阶段」这一演出是否已发生（落盘）。
     * 必须持久化：否则读档后 {@code tickCount} 归零会重演一次满血复活 + 全场击飞。
     */
    boolean initialRespawnDone; // 由 AgaitolosPhaseMachine / AgaitolosNbtCodec 协作类读写

    /** 格挡架势剩余 tick（服务端权威；客户端只需要"是否在架势"，同 respawning 的分工） */
    int guardTicks; // 由 AgaitolosGuardCharge / AgaitolosNbtCodec 协作类读写

    /** 架势结束后的冷却剩余 tick（服务端权威，不参与同步） */
    int guardCooldownTicks; // 由 AgaitolosTimers / AgaitolosGuardCharge / AgaitolosNbtCodec 协作类读写

    /** 俯冲镰扫的冷却剩余 tick（服务端权威，不参与同步；已落盘，见 {@link AgaitolosNbtCodec#NBT_DIVE_SWEEP_COOLDOWN}） */
    int diveSweepCooldownTicks; // 由 AgaitolosTimers / AgaitolosDiveMachine / AgaitolosNbtCodec 协作类读写

    /** 冲锋剩余 tick（服务端权威，不参与同步）：> 0 即"正在俯冲位移"，见 {@link #isDiving()} */
    int diveTicks; // 由 AgaitolosDiveMachine / AgaitolosTimers / AgaitolosNbtCodec 协作类读写

    /** 禁飞剩余 tick（服务端权威，不参与同步）：被玩家格挡成功后授予，见 {@link AgaitolosTimers#onSweepBlocked} */
    int groundedTicks; // 由 AgaitolosTimers / AgaitolosNbtCodec 协作类读写

    /** 镰扫封印剩余 tick（服务端权威，不参与同步）：封印期内不再起手俯冲镰扫 */
    int scytheSealTicks; // 由 AgaitolosTimers / AgaitolosNbtCodec 协作类读写

    /** 「恶怨倒转」蓄力剩余 tick（服务端权威；客户端只需要"是否在蓄力"，同 guard 的分工） */
    int chargeTicks; // 由 AgaitolosGuardCharge / AgaitolosNbtCodec 协作类读写

    /** 「恶怨倒转」冷却剩余 tick（服务端权威，不参与同步；蓄力结束才开始计时） */
    int minionCooldownTicks; // 由 AgaitolosTimers / AgaitolosGuardCharge / AgaitolosNbtCodec 协作类读写

    /**
     * 「恶怨倒转」蓄力期间累计吃下的伤害（服务端权威，不参与同步）：达到
     * {@code getMaxHealth() × }{@link AgaitolosGuardCharge#CHARGE_BREAK_DAMAGE_RATIO} 即视为"足量的伤害"、打断蓄力。
     * <p>只在伤害<b>真正落地</b>的分支累加（见 {@link #hurt} 的 {@code if (applied)}），
     * 被格挡 / 被免伤（非玩家来源、复活无敌）/ 被 0.2s 冷却挡下的部分一律不计入。
     */
    float chargeDamageTaken; // 由 AgaitolosGuardCharge / AgaitolosCombatHooks / AgaitolosNbtCodec 协作类读写

    /** 瞬击（二阶段）冷却剩余 tick（服务端权威，不参与同步；已落盘，同俯冲镰扫） */
    int blinkCooldownTicks; // 由 AgaitolosTimers / AgaitolosPhaseTwoSkills / AgaitolosNbtCodec 协作类读写

    /** 高速踢击（二阶段）冷却剩余 tick（服务端权威，不参与同步；已落盘，同上） */
    int kickCooldownTicks; // 由 AgaitolosTimers / AgaitolosPhaseTwoSkills / AgaitolosNbtCodec 协作类读写

    /**
     * 普攻间隔剩余 tick（服务端权威，不参与同步；已落盘）。
     * <p>
     * <b>为什么实体必须显式持有它</b>：本轮之前这个间隔藏在原版 {@code MeleeAttackGoal} 的
     * {@code attackInterval}（实测 = 20 tick）里。现在普攻的出手闸收进 {@link AgaitolosSkillDirector}
     * 与 {@link #doHurtTarget}（决策层也会直接调 {@code doHurtTarget}），若不在实体侧也留一份，
     * 决策层会每个节拍都放普攻、把普攻速度凭空翻倍。
     * <p>基准值取 {@code AgaitolosMeleeSkill#MELEE_INTERVAL_TICKS}（与原版同值 20），
     * 再按阶段折算（{@code AgaitolosPace#scaledCooldown}）⇒ 二、三阶段的普攻更密，与"二阶段更快速"同向。
     */
    int meleeCooldownTicks; // 由 AgaitolosTimers / AgaitolosCombatHooks / AgaitolosNbtCodec 协作类读写

    /**
     * 远程凋零头冷却剩余 tick（服务端权威，不参与同步；已落盘）。
     * <p>基准值取 {@code AgaitolosSkullSkill#SKULL_COOLDOWN_TICKS}（= 原 {@code RangedAttackGoal} 的
     * 60 tick 间隔字面量，随该 Goal 一起搬进技能类），按阶段折算。
     */
    int rangedCooldownTicks; // 由 AgaitolosTimers / AgaitolosCombatHooks / AgaitolosNbtCodec 协作类读写

    /**
     * 已定案的「场内人数」n（服务端权威，不参与同步；<b>落盘</b>）。
     * <p>
     * <b>只在两个时刻重算</b>（用户拍板：不做实时跟随）：① BOSS 入场（首次 summon 分支）；
     * ② 每次进阶段（{@link AgaitolosPhaseMachine#advancePhase}）。玩家中途进出<b>不</b>改动本值 ——
     * 否则血量上限会随进出忽大忽小，BossBar 与"每失去一半血进阶段"的节奏全部失真。
     * <p>必须落盘：区块卸载/重启会重建实体，不落盘则"已按 3 人算好的血量上限"会跟着实体的
     * 属性修饰符一起读回、而伤害系数退回 1.0 —— 两条口径立刻分叉（血量按 3 人、伤害按 1 人）。
     */
    int scaledPlayerCount = 1; // 由 AgaitolosScaling / AgaitolosNbtCodec 协作类读写

    /**
     * 战斗决策层：<b>选招的唯一入口</b>（"这一拍放哪一招"）。
     * <p>
     * <b>为什么用字段初始化而不是在 {@link #registerGoals()} 里 new 一个传给 Goal</b>：
     * {@code registerGoals()} 是由 {@code Mob} 的<b>构造器</b>回调的，早于本类的字段初始化，
     * 那时任何"构造时注入"的写法拿到的都是 {@code null}（且只会在运行期某次空指针时才暴露）。
     * 本类与决策层的交互全部走方法调用（{@link #aiStep} 主动 tick、Goal 不持有它），故无此风险。
     */
    private final AgaitolosSkillDirector skillDirector = new AgaitolosSkillDirector();

    /**
     * 阶段三五招的状态机（三重投掷 / 饱和轰炸 / 投技①② / 天魔＊灾）。
     * <p>与 {@link #skillDirector} 同一分工：本类不把它们的计时塞进自己的字段，
     * 只每 tick 调一次 {@code phaseThree.tick(this)} 并把起手入口转发出去
     * （理由与"这些字段为什么单独一个类"见 {@code AgaitolosPhaseThreeState} 的类注释）。
     */
    private final AgaitolosPhaseThreeState phaseThree = new AgaitolosPhaseThreeState();

    /** 阶段三状态机的同包只读出口：NBT 编解码 / 计时递减 / 蓄力节律等协作类经此访问（字段本身保持私有） */
    AgaitolosPhaseThreeState phaseThreeState() {
        return this.phaseThree;
    }

    /** 出场演出剩余 tick（服务端权威；客户端只需要"是否在出场"，同 respawning 的分工）。<b>不落盘</b>，理由见 {@link #readAdditionalSaveData} */
    int introTicks; // 由 AgaitolosIntroMachine / AgaitolosNbtCodec 协作类读写

    /**
     * 出场降临的目标 Y（服务端权威；开演时按 {@link AgaitolosMoveControl#hoverY} 定一次，之后不再重算）。
     * <p>整个过程不落盘、也不参与同步：它只是本段插值的临时基准，重进存档时演出已按"不重放"处理。
     */
    double introDescentTargetY; // 由 AgaitolosIntroMachine 协作类读写

    /**
     * 是否处于<b>地面档</b>（服务端权威；不落盘）。
     * <p>档位的唯一消费点是 {@code AgaitolosMoveControl} ③（垂直目标高度），故这个布尔本身不需要同步：
     * 位置由原版实体同步，高度已经体现在坐标里。客户端动画要用的"展示状态"另走 {@link #DATA_PERCH_STATE}
     * （多带"过渡中/朝哪个方向"，见 {@link #getPerchState()}）。
     * <p>不落盘：读档/区块卸载后从"空中档"重新开始（最多 4s 后再落地），不会出现"读档后还记得
     * 上一轮站在地上"这种无法解释的行为，与决策层的战术记忆同一口径。
     */
    boolean perched; // 由 AgaitolosPerchMachine 协作类读写

    /** 已在地面档停留的 tick 数（迟滞用；进入地面档时归零） */
    int perchedTicks; // 由 AgaitolosPerchMachine 协作类读写

    /** 已离开地面档的 tick 数（节律用；<b>封顶</b>在 {@link AgaitolosPerchMachine#PERCH_AIRBORNE_MIN_TICKS}，故不会溢出） */
    int airborneTicks; // 由 AgaitolosPerchMachine 协作类读写

    /**
     * 落地 / 升空过渡的剩余 tick（只在 {@link #PERCH_STATE_LANDING} /
     * {@link #PERCH_STATE_TAKEOFF} 期间递减）。
     * <p><b>两个消费点</b>：
     * <ol>
     *   <li>动画展示：过渡态 → 稳态的切换点（闸门）；</li>
     *   <li><b>升降位移的匀速分母</b>（2026-09-21 对齐）：{@code AgaitolosMoveControl} ③-a 在窗口内改用
     *       "剩余高度 ÷ 剩余 tick" 的匀速收敛，使"位置到位"与 12t 的过渡 clip <b>严格同刻</b>结束。
     *       稳态下的高度收敛（③-b 的比例 + 限速）不读它。</li>
     * </ol>
     */
    int perchTransitionTicks; // 由 AgaitolosPerchMachine 协作类读写

    public AgaitolosEntity(EntityType<? extends AgaitolosEntity> type, Level level) {
        super(type, level);
        // 低空飞行移动控制器。原版 Mob 没有 createMoveControl() 钩子（凋灵/幻翼也都是构造器里直接赋值），故在此替换
        this.moveControl = new AgaitolosMoveControl(this);
    }

    /**
     * 基础属性。
     * <p>两个基础值取 {@link AgaitolosScaling} 的常量（单一真源）：随在场玩家数增强时，
     * 缩放量 = {@code 基础值 × (缩放系数 − 1)}，故基础值必须与缩放算式同源，否则同一份加成会被算成两个数。
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, AgaitolosScaling.BASE_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, AgaitolosScaling.BASE_ATTACK_DAMAGE)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    // ---------------------------------------------------------------- 同步数据

    @Override
    protected void defineSynchedData() {
        // 必须保留父类（Entity/LivingEntity/Mob）已定义的数据项
        super.defineSynchedData();
        // 默认 = PHASE_1 的序号（复活阶段由 aiStep 的首次 tick 推进去，见下）
        this.entityData.define(DATA_PHASE, AgaitolosPhase.PHASE_1.combatOrdinal());
        this.entityData.define(DATA_RESPAWNING, Boolean.FALSE);
        this.entityData.define(DATA_GUARDING, Boolean.FALSE);
        this.entityData.define(DATA_CHARGING, Boolean.FALSE);
        // 出场默认 false：读档重建的实体不从存档恢复演出（见 readAdditionalSaveData），
        // 只有"首次召唤分支"会把它立起来
        this.entityData.define(DATA_INTRO, Boolean.FALSE);
        // 地面档展示状态默认 = 空中档（不落盘，理由同 perched 字段：读档/区块卸载后从空中档重新开始）
        this.entityData.define(DATA_PERCH_STATE, PERCH_STATE_AIRBORNE);
        // 轰炸默认 false：读档重建的实体不从存档恢复"正在轰炸"（进行中状态一律不落盘，见 AgaitolosPhaseThreeState）
        this.entityData.define(DATA_BOMBARDING, Boolean.FALSE);
    }

    /** 当前阶段（服务端读权威值，客户端读同步值） */
    public AgaitolosPhase getPhase() {
        return AgaitolosPhase.byCombatOrdinal(this.entityData.get(DATA_PHASE));
    }

    /** 切换阶段（P4~P6 解锁招式、P7 换模型时按阶段分支） */
    public void setPhase(AgaitolosPhase phase) {
        this.entityData.set(DATA_PHASE, phase.combatOrdinal());
    }

    /** 是否处于复活阶段：全程无敌 + 快速回血，结束瞬间击飞周围玩家 */
    public boolean isRespawning() {
        return this.entityData.get(DATA_RESPAWNING);
    }

    /** 复活阶段只由阶段机（{@link AgaitolosPhaseMachine}）开合，外部（技能）只读 */
    void setRespawning(boolean respawning) { // 包私有：由 AgaitolosPhaseMachine / AgaitolosNbtCodec 协作类写
        this.entityData.set(DATA_RESPAWNING, respawning);
    }

    /** 是否处于格挡架势（服务端读权威值，客户端读同步值）：动画与受击判定两端共用 */
    public boolean isGuarding() {
        return this.entityData.get(DATA_GUARDING);
    }

    /** 架势由决策层经 {@link AgaitolosGuardCharge#startGuard} 开启、由 {@link AgaitolosGuardCharge#tickGuardState} 与 {@link #endGuard()} 收合，技能不自持计时 */
    void setGuarding(boolean guarding) { // 包私有：由 AgaitolosGuardCharge / AgaitolosNbtCodec 协作类写
        this.entityData.set(DATA_GUARDING, guarding);
    }

    /** 立即收势：实现与完整口径见 {@link AgaitolosGuardCharge#endGuard}（格挡反击的出口）。 */
    public void endGuard() {
        AgaitolosGuardCharge.endGuard(this);
    }

    /** 是否处于「恶怨倒转」蓄力（服务端读权威值，客户端读同步值）：动画与出手判定两端共用 */
    public boolean isCharging() {
        return this.entityData.get(DATA_CHARGING);
    }

    /** 蓄力由决策层经 {@link AgaitolosGuardCharge#startCharge} 开启、由 {@link AgaitolosGuardCharge#tickChargeState} / {@link AgaitolosGuardCharge#finishCharge} 收合（同架势） */
    void setCharging(boolean charging) { // 包私有：由 AgaitolosGuardCharge / AgaitolosNbtCodec 协作类写
        this.entityData.set(DATA_CHARGING, charging);
    }

    /** 是否正在播出场演出（服务端读权威值，客户端读同步值）：动画与"一律不起手"的闸门两端共用 */
    public boolean isIntroPlaying() {
        return this.entityData.get(DATA_INTRO);
    }

    /** 出场只由 {@link AgaitolosIntroMachine} 的 startIntro / tickIntro 开合，技能不自持计时（同架势/蓄力） */
    void setIntroPlaying(boolean intro) { // 包私有：由 AgaitolosIntroMachine / AgaitolosNbtCodec 协作类写
        this.entityData.set(DATA_INTRO, intro);
    }

    /**
     * 是否正在「饱和轰炸」（阶段三）：服务端读权威值，客户端读同步值。
     * <p>三处消费：动画的 bombard 控制器（两端各自轮询）、MAIN 控制器的互斥停播、以及
     * {@code AgaitolosMoveControl} 的"高空档"（{@code BOMBARD_HOVER_HEIGHT}）。
     * <p><b>与 {@code AgaitolosPhaseThreeState} 的分工</b>：本方法只表达"这一刻是不是在轰炸"，
     * 计时/节拍/冷却全在状态机那一侧（与 {@code isGuarding()} ↔ {@code tickGuardState()} 同款分工）。
     */
    public boolean isBombarding() {
        return this.entityData.get(DATA_BOMBARDING);
    }

    /** 轰炸同步位只由 {@code AgaitolosPhaseThreeState} 开合（起手立、期满/整段中止撤），技能不自持计时 */
    void setBombarding(boolean bombarding) {
        this.entityData.set(DATA_BOMBARDING, bombarding);
    }

    // ---------------------------------------------------------------- 冲锋 / 禁飞 / 封印状态（探针，实现随职责搬迁）

    /** 是否正在冲锋：实现与口径见 {@link AgaitolosDiveMachine#isDiving}。 */
    public boolean isDiving() {
        return AgaitolosDiveMachine.isDiving(this);
    }

    /**
     * 是否被禁飞（被玩家格挡的惩罚）：实现与"禁飞期观感"的完整口径见
     * {@link AgaitolosTimers#isGrounded}。
     */
    public boolean isGrounded() {
        return AgaitolosTimers.isGrounded(this);
    }

    /**
     * 镰扫是否处于封印期（被玩家格挡的惩罚）：封印的作用范围与理由（只封"大招"）
     * 见 {@link AgaitolosTimers#isScytheSealed}。
     */
    public boolean isScytheSealed() {
        return AgaitolosTimers.isScytheSealed(this);
    }

    /**
     * 俯冲镰扫被玩家格挡成功的上报口（禁飞 30s + 镰扫封印 30s，同刻授予）。
     * 拆分后本体在 {@link AgaitolosTimers#onSweepBlocked}；本委托保留原公开签名，
     * 供 {@code skill/AgaitolosDiveSweepSkill}（子包）跨包调用。
     */
    public void onSweepBlocked() {
        AgaitolosTimers.onSweepBlocked(this);
    }

    /** 只读探针：某一招的剩余冷却（供决策层打分）。实现与口径见 {@link AgaitolosTimers#remainingCooldown}。 */
    int remainingCooldown(AgaitolosSkillDirector.Move move) {
        return AgaitolosTimers.remainingCooldown(this, move);
    }

    // ---------------------------------------------------------------- 地面档（"多待在地上"，2026-09-21 补）

    /** 地面档展示状态：<b>空中档</b>（纯飞行待机，MAIN 播 idle_flight）。留在实体上：{@code AgaitolosAnimations} 的四态 switch 直接引用 */
    public static final int PERCH_STATE_AIRBORNE = 0;

    /** 地面档展示状态：<b>落地过渡</b>（12t，MAIN 播 land，播完接 idle_ground）。留在实体上：同上 */
    public static final int PERCH_STATE_LANDING = 1;

    /** 地面档展示状态：<b>贴地稳态</b>（MAIN 播 idle_ground 循环）。留在实体上：同上 */
    public static final int PERCH_STATE_PERCHED = 2;

    /** 地面档展示状态：<b>升空过渡</b>（12t，MAIN 播 takeoff，播完接 idle_flight）。留在实体上：同上 */
    public static final int PERCH_STATE_TAKEOFF = 3;

    /** 是否处于地面档（近身缠斗时贴地）：节律口径与"落地后近战尺不缩短"的取舍见 {@link AgaitolosPerchMachine#isPerched}。 */
    public boolean isPerched() {
        return AgaitolosPerchMachine.isPerched(this);
    }

    /**
     * 地面档的<b>展示状态</b>（服务端写、客户端读；动画 MAIN 控制器轮询它，见 {@code AgaitolosAnimations#groundAwareIdle}）。
     * <p>
     * 四态：{@link #PERCH_STATE_AIRBORNE} / {@link #PERCH_STATE_LANDING}
     * / {@link #PERCH_STATE_PERCHED} / {@link #PERCH_STATE_TAKEOFF}。
     * <p>
     * <b>为什么是四态 int 而不是"布尔 + 过渡标志"</b>：动画需要在两个方向上各播一条过渡 clip，
     * 而"过渡中"与"朝哪个方向"合起来正好是 4 个互斥取值；用一个 INT 一次带走，
     * 客户端只需一个 accessor 与一次比较（省掉第二个同步字段，也省掉"两字段不一致"的可能）。
     * 与 {@link #isPerched()} 的关系是纯派生：LANDING/PERCHED ⇒ perched 为 true（高度已朝地面收敛），
     * AIRBORNE/TAKEOFF ⇒ perched 为 false（高度已朝空中收敛）——两条边在同一 tick 一起翻，
     * 故过渡 clip 与升降位移是同刻起跑的（见 {@link AgaitolosPerchMachine#tickPerchState}）。
     */
    public int getPerchState() {
        return this.entityData.get(DATA_PERCH_STATE);
    }

    /** 只读探针：过渡窗口的剩余 tick（0 = 不在过渡中）。实现与消费点见 {@link AgaitolosPerchMachine#getPerchTransitionTicks}。 */
    int getPerchTransitionTicks() {
        return AgaitolosPerchMachine.getPerchTransitionTicks(this);
    }

    /**
     * 写地面档展示状态。<b>只在值时显式判等后写</b>：整轮节律只翻 4 次
     * （LANDING → PERCHED → TAKEOFF → AIRBORNE），不存在每 tick 反复 set 造成的无谓同步量；
     * 判等也让"同步量"这件事不依赖原版 {@code SynchedEntityData#set} 对同值是否短路。
     * <p>包私有：唯一写方是 {@link AgaitolosPerchMachine#tickPerchState}（同步位读写按约定留在实体上）。
     */
    void setPerchState(int state) {
        if (this.getPerchState() == state) {
            return;
        }
        this.entityData.set(DATA_PERCH_STATE, state);
    }

    // ---------------------------------------------------------------- 存档（具体读写见 AgaitolosNbtCodec）

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        AgaitolosNbtCodec.save(this, tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        AgaitolosNbtCodec.load(this, tag);
    }

    // ---------------------------------------------------------------- 随在场玩家数增强（§0 第 71 行 / §1 定案表第 11 条）

    /** 已定案的场内人数 n（落盘；客户端读到的是默认 1 —— 本值只在服务端被消费） */
    public int getScaledPlayerCount() {
        return this.scaledPlayerCount;
    }

    /**
     * <b>固定数值伤害</b>的缩放系数（1 人 = 1.0）：供普攻真实段与凋零头弹体读取。
     * <p>百分比最大生命的招式（俯冲镰扫/踢击/投技）<b>不</b>读它，理由见 {@link AgaitolosScaling} 的类注释。
     */
    public double getDamageScale() {
        return AgaitolosScaling.damageScale(this.scaledPlayerCount);
    }

    // ---------------------------------------------------------------- 每 tick 编排（原 aiStep，逐行保留顺序）

    @Override
    public void aiStep() {
        super.aiStep();
        // 阶段机是服务端权威，客户端只消费同步数据（血条已在客户端 overlay 侧自绘，本类不再持有）
        if (this.level().isClientSide()) {
            return;
        }
        // 规格（设计文档 §1 第 14 条）：召唤之后先进复活阶段快速回复血量。
        // 只在实例的最初 tick 触发一次；initialRespawnDone 落盘，避免读档重演。
        if (this.tickCount <= 1 && !this.initialRespawnDone) {
            AgaitolosIntroMachine.beginFirstSummon(this);
        }
        // 复活阶段内不再判阶段阈值，否则刚进 PHASE_2 就会被残留的低血量直接推到 PHASE_3
        if (this.isRespawning()) {
            AgaitolosPhaseMachine.tickRespawn(this);
        } else {
            AgaitolosPhaseMachine.checkPhaseAdvance(this);
        }
        // ---- 计时（与"选招"无关，一律无条件推进）----
        // 惩罚计时（禁飞/封印/召唤冷却）与招式冷却分开：前者是玩家争取来的窗口，后者是 BOSS 自己的节奏，
        // 混在一个方法里改一处很容易把另一处的口径带偏（历史上"改一招忘一招"的常见来源）。
        AgaitolosTimers.tickPenaltyTimers(this);
        AgaitolosTimers.tickActionCooldowns(this);
        // ---- 进行中状态（只推进"已经在演"的那一招，不再承担起手判定）----
        // 起手判定已整体移交 AgaitolosSkillDirector：这三条只剩倒计时、打断出口与冲锋位移，
        // 顺序不再需要"谁先立状态"这种约定（状态互斥由决策层的全局动作锁保证，比原先的顺次闸更硬）
        AgaitolosGuardCharge.tickChargeState(this);
        AgaitolosGuardCharge.tickGuardState(this);
        AgaitolosDiveMachine.tickDiveState(this);
        // ---- 阶段三五招的进行中状态（三重投掷节拍 / 高空轰炸 / 抓取态 / 劈击 / 施法）----
        // 与上面三条同一档：只推进"已经在演"的那一招，起手判定归决策层；
        // 放在决策层之前，保证"本 tick 刚结束的状态"已释放全局动作锁，决策层不会白等一拍。
        // 抓取态（投技①）的钉住动作也在这里逐 tick 执行，故它不持有任何玩家侧状态。
        this.phaseThree.tick(this);
        // ---- 地面档：只切"垂直高度档位"（空中 2 格 / 贴地 0 格），不碰任何招式状态 ----
        // 放在状态推进之后、决策层之前：本 tick 切出来的档位会被下一 tick 的
        // AgaitolosMoveControl ③ 读到（moveControl.tick 在 super.aiStep() 内已跑过，故当帧不生效，
        // 这与"实体写 deltaMovement、下一帧 travel 消费"是同一个半拍延迟，肉眼不可见）
        AgaitolosPerchMachine.tickPerchState(this);
        // ---- 战斗决策层：这一拍放哪一招（唯一入口）----
        // 放在所有状态推进之后：本 tick 刚结束的招已释放动作锁，决策层能立刻看到最新状态；
        // 也保证"某招在本 tick 被中断"时不会再被决策层当成"正在演"而白等一拍
        this.skillDirector.tick(this);
        // 出场演出排在<b>最末</b>：它要独占位移（写竖直分量），必须等所有可能改速度的状态都跑完；
        // 放在这里也保证"被决策层拦住的动作"先被判一遍，本 tick 不会既起手又降临
        AgaitolosIntroMachine.tickIntro(this);
    }

    // ---------------------------------------------------------------- 架势 / 蓄力 / 俯冲 / 二阶段两招：执行入口与距离探针（实现随职责搬迁）

    /**
     * 执行入口：起手格挡架势。实现与朝向锁定口径见 {@link AgaitolosGuardCharge#startGuard}。
     *
     * @return 是否真的起手（冷却中/距离不满足/已被别的状态占用时为 false，此时不消耗全局节拍）
     */
    boolean startGuard(LivingEntity target) {
        return AgaitolosGuardCharge.startGuard(this, target);
    }

    /** 只读探针：是否该起手格挡（目标存活 + 水平距离已进近战可达）。实现见 {@link AgaitolosGuardCharge#isTargetWithinGuardRange}。 */
    boolean isTargetWithinGuardRange(LivingEntity target) {
        return AgaitolosGuardCharge.isTargetWithinGuardRange(this, target);
    }

    /** 只读探针：目标是否已进近战可达距离（3D，含悬停高度折算）。实现见 {@link AgaitolosGuardCharge#isTargetWithinMeleeReach}。 */
    boolean isTargetWithinMeleeReach(LivingEntity target) {
        return AgaitolosGuardCharge.isTargetWithinMeleeReach(this, target);
    }

    /**
     * 执行入口：起手「恶怨倒转」。实现与"先召唤再立状态"的口径见 {@link AgaitolosGuardCharge#startCharge}。
     *
     * @return 是否真的起手（冷却中/目标无效时为 false，此时不消耗全局节拍）
     */
    boolean startCharge(LivingEntity target) {
        return AgaitolosGuardCharge.startCharge(this, target);
    }

    /**
     * 执行入口：起手俯冲镰扫。实现与封印复校见 {@link AgaitolosDiveMachine#startDiveSweep}。
     *
     * @return 是否真的起手（被任一条挡下时为 false，此时不消耗全局节拍）
     */
    boolean startDiveSweep(LivingEntity target) {
        return AgaitolosDiveMachine.startDiveSweep(this, target);
    }

    /** 只读探针：是否满足冲锋起手距离（水平距离 ∈ [AgaitolosDiveMachine.DIVE_MIN_RANGE, {@link #DIVE_TRIGGER_RANGE}]）。实现见 {@link AgaitolosDiveMachine#isWithinDiveTriggerRange}。 */
    boolean isWithinDiveTriggerRange(LivingEntity target) {
        return AgaitolosDiveMachine.isWithinDiveTriggerRange(this, target);
    }

    /**
     * 执行入口：起手「瞬击」。实现与"禁飞窗口才是起手条件"的口径见 {@link AgaitolosPhaseTwoSkills#startBlink}。
     *
     * @return 是否真的起手（阶段未开放/状态冲突/不在地面/落点校验失败时为 false）
     */
    boolean startBlink(LivingEntity target) {
        return AgaitolosPhaseTwoSkills.startBlink(this, target);
    }

    /**
     * 执行入口：起手「高速踢击」。实现与"任何空间状态可用"的口径见 {@link AgaitolosPhaseTwoSkills#startKick}。
     *
     * @return 是否真的起手（阶段未开放/状态冲突/距离不够时为 false）
     */
    boolean startKick(LivingEntity target) {
        return AgaitolosPhaseTwoSkills.startKick(this, target);
    }

    /**
     * 是否处于「免疫远程」的阶段（<b>阶段三专属</b>，规格："BOSS 免疫远程攻击"）。
     * 实现与判据口径见 {@link AgaitolosPhaseMachine#isProjectileImmune}。
     */
    public boolean isProjectileImmune() {
        return AgaitolosPhaseMachine.isProjectileImmune(this);
    }

    // ---------------------------------------------------------------- 阶段三五招（2026-09-21 补）

    /**
     * 只读探针：阶段三五招是否有招在演（全局动作锁的一段）。
     * <p>两个消费点：{@code AgaitolosSkillDirector#isBusy}（决定"这一拍还派不派新招"）与
     * {@link #doHurtTarget}（拦住原版 {@code MeleeAttackGoal} 那条旁路）。
     * <p>计时与节拍的持有者在 {@code AgaitolosPhaseThreeState}，本方法只是转发
     * （与 {@code isGuarding()} ↔ {@code tickGuardState()} 同款分工）。
     */
    boolean isPhaseThreeBusy() {
        return this.phaseThree.isBusy();
    }

    /**
     * 执行入口：起手「三重投掷」（由 {@link AgaitolosSkillDirector} 在"这一拍选中三重投掷"时调用）。
     * <p>本类只转发给状态机，起手复校（冷却 / 封印 / 状态互斥 / 阶段三 / 空中 / 射程）在
     * {@code AgaitolosPhaseThreeState#startTripleThrow} 里，节拍与 clip 的对齐关系见
     * {@code AgaitolosTripleThrowSkill}（三次出手落在 clip 的 6 / 12 / 18 tick）。
     *
     * @return 是否真的起手（未过复校时为 false，此时不消耗全局节拍）
     */
    boolean startTripleThrow(LivingEntity target) {
        return this.phaseThree.startTripleThrow(this, target);
    }

    /**
     * 执行入口：起手「饱和轰炸」（阶段三高空投弹，唯一一条循环 clip 驱动的持续状态）。
     * <p>起手成功后同步位 {@code DATA_BOMBARDING} 立起（动画与高空档都靠它），期满或整段中止时撤销。
     * <p>「被反弹则自伤」不需要本招额外接线：投出的仍是自家凋零头，玩家打回后 owner 变玩家，
     * 命中 BOSS 时自然落进 {@code AgaitolosDamageRules} 的 ⑤ 自伤通道（详见 {@code AgaitolosBombardSkill}）。
     */
    boolean startBombard(LivingEntity target) {
        return this.phaseThree.startBombard(this, target);
    }

    /**
     * 执行入口：起手「投技①（踩住 + 镰扫）」（阶段三、BOSS 在地面档、目标贴地且贴身）。
     * <p>抓取态的四条解除出口与"锁位移 + 跟随"的实现在状态机与 {@code AgaitolosGrabSkill} 里，
     * 本类只转发（<b>抓取不往玩家侧写任何状态</b>，故不可能永久锁人）。
     */
    boolean startGrabSweep(LivingEntity target) {
        return this.phaseThree.startGrabSweep(this, target);
    }

    /**
     * 执行入口：起手「投技②（抓摔 + 劈击）」。
     * <p>clip 播到 0.6s 时结算 20% 最大生命的真实伤害；本入口与状态机不碰任何客户端状态，
     * 也不改变任何伤害/冷却口径。（2026-09-24：原「BOSS 特写」相机表现已彻底删除。）
     */
    boolean startGrabSmash(LivingEntity target) {
        return this.phaseThree.startGrabSmash(this, target);
    }

    /**
     * 执行入口：起手「天魔＊灾」（阶段三，24 格内隔空锁定）。
     * <p>0.5s 处落地"不可名状 + 精神伤害改写"，改写口径（含"此后永久"与玩家侧落盘）唯一收在
     * {@code AgaitolosPsychic}；本入口不重复任何一条那条口径。
     */
    boolean startCalamity(LivingEntity target) {
        return this.phaseThree.startCalamity(this, target);
    }

    // ---------------------------------------------------------------- 受击 / 生命周期（钩子实现随职责搬迁）

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return AgaitolosCombatHooks.hurt(this, source, amount);
    }

    /** 只执行原版受击结算（super.hurt），供 {@link AgaitolosCombatHooks} 拼装受击管线使用（同 {@link #doHurtTargetPhysical} 的手法：super 调用只能在子类内部）。 */
    public boolean doSuperHurt(DamageSource source, float amount) {
        return super.hurt(source, amount);
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return AgaitolosCombatHooks.canBeAffected(this, effect);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        return AgaitolosCombatHooks.doHurtTarget(this, target);
    }

    /**
     * 只执行原版物理一击，供 {@link AgaitolosMeleeSkill} 拼装普攻使用。
     * <p>必须由子类暴露成方法：{@code super.doHurtTarget} 只能在子类内部调用。
     */
    public boolean doHurtTargetPhysical(Entity target) {
        return super.doHurtTarget(target);
    }

    @Override
    public void performRangedAttack(LivingEntity target, float velocity) {
        AgaitolosCombatHooks.performRangedAttack(this, target, velocity);
    }

    @Override
    public void die(DamageSource source) {
        AgaitolosLifecycle.die(this, source);
    }

    /** 只执行原版死亡结算（super.die），供 {@link AgaitolosLifecycle} 使用（同 {@link #doHurtTargetPhysical} 的手法）。 */
    public void doSuperDie(DamageSource source) {
        super.die(source);
    }

    @Override
    protected void tickDeath() {
        AgaitolosLifecycle.tickDeath(this);
    }

    @Override
    public void remove(RemovalReason reason) {
        AgaitolosLifecycle.remove(this, reason);
    }

    /** 只执行原版移除结算（super.remove），供 {@link AgaitolosLifecycle} 使用（同 {@link #doHurtTargetPhysical} 的手法）。 */
    public void doSuperRemove(RemovalReason reason) {
        super.remove(reason);
    }

    @Override
    public void knockback(double strength, double x, double z) {
        // 规格：免疫击退
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void checkDespawn() {
        // BOSS 不自然消失（配合 removeWhenFarAway=false）
    }

    // ---------------------------------------------------------------- 低空飞行（P4-c，实现见 AgaitolosMotion）

    @Override
    protected PathNavigation createNavigation(Level level) {
        return AgaitolosMotion.createNavigation(this, level);
    }

    @Override
    public void travel(Vec3 travelVector) {
        AgaitolosMotion.travel(this, travelVector);
    }

    /** 只执行原版带重力的 travel（禁飞例外），供 {@link AgaitolosMotion} 使用（同 {@link #doHurtTargetPhysical} 的手法）。 */
    public void doSuperTravel(Vec3 travelVector) {
        super.travel(travelVector);
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return AgaitolosMotion.causeFallDamage(this, fallDistance, multiplier, source);
    }

    /** 近战可达距离：实现与字节码取证口径见 {@link AgaitolosMotion#meleeAttackRangeSqr}。 */
    @Override
    public double getMeleeAttackRangeSqr(LivingEntity target) {
        return AgaitolosMotion.meleeAttackRangeSqr(this, target);
    }

    /** 只执行原版近战可达基式（super.getMeleeAttackRangeSqr），供 {@link AgaitolosMotion} 使用（同 {@link #doHurtTargetPhysical} 的手法）。 */
    public double doSuperMeleeAttackRangeSqr(LivingEntity target) {
        return super.getMeleeAttackRangeSqr(target);
    }

    @Override
    protected void registerGoals() {
        // 近战 + 追击。必须用本项目的子类：原版 MeleeAttackGoal 按自己算的 getBbWidth()² 判可达距离，
        // 不读 getMeleeAttackRangeSqr，而本 BOSS 悬停在玩家上方 2.0 格 ⇒ 竖直差一项就顶破门槛、
        // 贴到脸上也挥不出一刀（"玩家打它却不还手"的直接原因，证据与推导见 AgaitolosMeleeAttackGoal 注释）。
        this.goalSelector.addGoal(2, new AgaitolosMeleeAttackGoal(this, 1.0D, true));
        // 远程凋零头（60 tick = 3s 一发、24 格内可放）<b>不再用原版 RangedAttackGoal</b>：
        // 它与 MeleeAttackGoal 抢同一组 Flag（实测两者都 setFlags(MOVE, LOOK)），而 MeleeAttackGoal 优先级 2 更高、
        // 且它的 canUse 只要求"有存活目标"⇒ 永远成立 ⇒ 优先级 3 的远程 Goal 被<b>永久饿死、一次都放不出来</b>
        //（原注释写的"目标脱离近战距离时本条接管"不成立：MeleeAttackGoal 不会因为距离远就停）。
        // 现在远程与普攻一起由 AgaitolosSkillDirector 决定"这一拍放哪个"，节奏常量已搬进 AgaitolosSkullSkill。
        // 观察距离 32 格；不加这条 BOSS 不会转头看人，观感很呆
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 32.0F));
        // 受击报复：优先级 0（高于主动索敌）。未被打时它的 canUse 恒假，不影响正常索敌。
        this.targetSelector.addGoal(0, new HurtByTargetGoal(this));
        // 主动索敌：mustSee=true ⇒ <b>索敌</b>要求视线（逃不掉，TargetingConditions 的 checkLineOfSight 默认为真），
        // 但它同时决定"锁定后能容忍多久看不见"——TargetGoal 的 unseenTicks/unseenMemoryTicks 机制。
        // 显式调长该记忆时长，让玩家绕柱子、跳下平台时 BOSS 不会立刻放弃追击。
        NearestAttackableTargetGoal<Player> nearestPlayer = new NearestAttackableTargetGoal<>(this, Player.class, true);
        nearestPlayer.setUnseenMemoryTicks(TARGET_UNSEEN_MEMORY_TICKS);
        this.targetSelector.addGoal(1, nearestPlayer);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AgaitolosAnimations.registerControllers(this, controllers);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
