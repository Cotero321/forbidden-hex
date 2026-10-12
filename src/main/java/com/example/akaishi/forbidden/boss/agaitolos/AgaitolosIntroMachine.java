package com.example.akaishi.forbidden.boss.agaitolos;

import com.example.akaishi.forbidden.boss.agaitolos.arena.NetherPrisonArena;
import net.minecraft.world.phys.Vec3;

/**
 * 阿盖托洛丝的<b>出场演出编排</b>（首次召唤，0~80t）：三段编排（粒子柱 → 降临位移 → 落地冲击环）
 * 与「召唤入场」的一次性编排（压血 → 进复活 → 铺牢狱 → 定案人数 → 爆发 → 开演）的唯一推进口。
 * <p>
 * 从 {@link AgaitolosEntity} 机械搬出（2026-09-24 拆分轮）：出场是一段独占位移的演出状态机
 * （写竖直分量、MoveControl 整体让位），与战斗状态无耦合；"首次召唤"那一 tick 的编排顺序
 * （牢狱先于开演、缩放先于压血回满）是 §39/§41 踩实的关键口径，收进本类后调用顺序只有一处可看。
 * 演出计时字段仍归实体持有（包私有，由本类读写）；同步位 DATA_INTRO 的读写仍留在实体上。
 */
public final class AgaitolosIntroMachine {

    private AgaitolosIntroMachine() {
    }

    // ---------------------------------------------------------------- 出场常量（首次召唤，0~80t）

    /**
     * 出场压血比例：25% —— <b>待调手感值 / P8 转配置项</b>。
     * <p>取值依据：规格 §0 要求「召唤之后进入复活阶段快速回复血量」，但实体生成时血量本来就是满的，
     * 复活阶段的每 tick {@code heal} 会被原版按最大生命夹取，画面上<b>什么都看不到</b>。
     * 故开场先把血量压到该比例，再交给复活阶段在 4s 内回满 —— 这段"快速回复"才真正可见。
     * 只压这一次（见 {@link #beginFirstSummon}）：50% / 25% 处的常规阶段推进不压血，保持原有口径。
     */
    public static final float INTRO_HEALTH_RATIO = 0.25F;

    /**
     * 出场演出总时长（tick）：<b>严格等于复活阶段</b>
     * （{@link AgaitolosPhaseMachine#RESPAWN_DURATION_TICKS} = 80 = 4.0s）。
     * <p>为什么直接引用而不是再写一个 80：两者必须同窗——首次召唤那一 tick 同时进复活阶段与出场演出，
     * 演出结束时血量刚回满、复活的无敌与击飞也刚好收尾，玩家看到的是"BOSS 从空中降临并回满血"这一件事。
     * 各写一份常量迟早会漂移成"降临完了还在回血"或"血回满了还在空中"。
     * <p>同时也是 {@code animation.agaitolos.intro} clip 的长度（4.0s）：改 clip 长度要同步改这里。待调手感值 / P8 转配置项
     */
    public static final int INTRO_DURATION_TICKS = AgaitolosPhaseMachine.RESPAWN_DURATION_TICKS;

    /** 出场 ① 段（粒子柱）的结束 tick、② 段（降临位移）时长 40t 与 ③ 段（落地冲击环）结束 tick 60t：
     * 三个分镜常量在 {@link AgaitolosEntity} 上（{@code AgaitolosFx} 的分镜窗口直接引用），本类只消费。 */

    /**
     * 降临起点相对悬停高度的抬升量（格）：3.5。
     * <p>起点 = 本 tick 的<b>真实悬停高度</b> + 该值（不是"当前坐标 + 该值"）：悬停高度由
     * {@link AgaitolosMoveControl#hoverY} 唯一计算，这样 40t 内落到的终点就是 BOSS 之后该待的位置，
     * 演出结束时 MoveControl 接手不会再有"落地又弹一下"的二次修正。待调手感值 / P8 转配置项
     */
    public static final double INTRO_DESCENT_HEIGHT = 3.5D;

    // ---------------------------------------------------------------- 召唤入场编排（aiStep 首次 tick 调用）

    /**
     * 「召唤入场」的一次性编排（原 {@code aiStep} 首次召唤分支的整段，逐语句搬迁）：
     * 压血 → 进复活阶段 → 铺牢狱 → 定案人数 → 表现爆发 → 开演。
     * <p>各步先后顺序都有口径（见行内注释），本类不再新增或调整任何一步。
     */
    static void beginFirstSummon(AgaitolosEntity boss) {
        // P2 接入点：此处之后还要铺下界牢狱场地，并在复活结束后对牢狱内生物施放凋零 III
        // 先把血量压到 INTRO_HEALTH_RATIO 再进复活阶段：满血进场时复活阶段的回血在画面上完全不可见
        // （详见 INTRO_HEALTH_RATIO 的注释）。只此一处压血，阶段推进触发的复活不压。
        boss.setHealth(boss.getMaxHealth() * INTRO_HEALTH_RATIO);
        AgaitolosPhaseMachine.enterRespawn(boss);
        // 铺下界牢狱场地（分级施工/快照落盘/重启自愈全在 NetherPrisonArena 内，实体侧只留这一个入口）。
        // 必须在 startIntro() 之前调用：场地中心取"召唤点"（blockPosition），
        // 而出场演出第一件事就是把 BOSS 抬到悬停高度上方，放在后面会把中心抬高 3.5 格。
        NetherPrisonArena.begin(boss);
        // 随在场玩家数增强：<b>入场即定案一次</b>（§0 第 71 行 / §1 第 11 条，用户拍板不做实时跟随）。
        // 必须排在 begin 之后：牢狱记录此刻已建立 ⇒ 人数判据走"牢狱 box"这条正路
        // （排在 begin 之前只能退化为半径兜底，还会把刚压好的 25% 血算成另一套上限）。
        // 也必须排在 startIntro 之前：出场降临的目标高度按新的悬停几何算，不受本次缩放影响，但
        // setHealth 的基准（getMaxHealth）必须已经是缩放后的值，否则开场血线走形
        AgaitolosScaling.applyPlayerCountScaling(boss);
        // 纯表现：出场瞬间的一次性青蓝爆发（识别色同族），不参与任何结算
        AgaitolosFx.introBurst(boss);
        // 出场演出（三段编排）与复活阶段同刻开演、同窗结束：开演那一刻就把 BOSS 抬到悬停高度上方，
        // 之后 40t 降临、40~60t 落地冲击。只在此分支起手 ⇒ 阶段推进触发的复活不会重放（见 tickIntro）
        startIntro(boss);
    }

    // ---------------------------------------------------------------- 演出推进（aiStep 最末调用）

    /**
     * 出场演出的<b>服务端编排</b>（唯一入口，见 {@link AgaitolosEntity#aiStep()} 的首次召唤分支），三段：
     * <ol>
     *   <li><b>0~{@link AgaitolosEntity#INTRO_PILLAR_END_TICKS}t 粒子柱</b>：BOSS 周身青蓝光焰（{@link AgaitolosFx#introPillar}）；</li>
     *   <li><b>0~{@link AgaitolosEntity#INTRO_DESCENT_TICKS}t 降临位移</b>：从"悬停高度 + {@link #INTRO_DESCENT_HEIGHT}"逐 tick 插值降到悬停高度；</li>
     *   <li><b>{@link AgaitolosEntity#INTRO_DESCENT_TICKS}~{@link AgaitolosEntity#INTRO_SHOCK_END_TICKS}t 落地冲击环</b>：向外扩散的粒子环 + 地面尘
     *       （{@link AgaitolosFx#introShockRing}）。</li>
     * </ol>
     * 全段由 {@link AgaitolosEntity#isIntroPlaying()}（同步数据 DATA_INTRO）对外表达，客户端动画控制器轮询同一个状态。
     * 数值一律为待调手感值 / P8 转配置项。
     * <p>
     * <b>为什么是"逐 tick 插值速度"而不是直接 {@code setPos}</b>：与 {@code tickDiveCharge} 同一手法 ——
     * 写 {@code deltaMovement} 后由 {@code travel} 走 {@code move(MoverType.SELF, …)}，位移会<b>经过碰撞</b>
     * （撞到地形就停下），而 {@code setPos} 是无碰撞的瞬移，降临途中可能把 BOSS 塞进方块里。
     * 代价只是"本 tick 写的速度由下一 tick 的 travel 消费"这半拍延迟，与冲锋完全一致、肉眼不可见。
     * <p>
     * <b>与 {@link AgaitolosMoveControl} 的共存</b>：MoveControl 在出场期间<b>整体让位</b>
     * （见其 {@code tick()} ⓪'），故本方法写的竖直速度不会被悬停修正改写、水平也不会被导航加速。
     * 纯按"剩余距离 / 剩余 tick"递推 ⇒ 每 tick 步长恒等于 {@code INTRO_DESCENT_HEIGHT / INTRO_DESCENT_TICKS}
     * （3.5/40 = 0.0875 格/tick，匀速、无抖），且 40 tick 内<b>精确</b>落在目标高度上（最后一步剩余距离恰好一步走完）。
     */
    static void tickIntro(AgaitolosEntity boss) {
        if (!boss.isIntroPlaying()) {
            return;
        }
        // 已演出 tick 数（0 起）：起手那一 tick 为 0，与两段常量的时间窗口径一致
        int introAge = INTRO_DURATION_TICKS - boss.introTicks;
        AgaitolosFx.introPillar(boss, introAge);
        // ② 降临：40t 内线性收敛；40t 之后必须继续写竖直 0（见下）
        double motionY = 0.0D;
        if (introAge < AgaitolosEntity.INTRO_DESCENT_TICKS) {
            int remaining = AgaitolosEntity.INTRO_DESCENT_TICKS - introAge;
            motionY = (boss.introDescentTargetY - boss.getY()) / remaining;
        }
        // 水平恒 0：演出要原地降临（MoveControl 已让位，这里把残余水平速度也一并清掉，避免被击退/惯性带偏）。
        // 竖直在 ② 段之后仍写 0 是<b>必须</b>的：travel 会把上一 tick 的速度乘 0.91 留到下一 tick，
        // 若 40t 后撒手不管，末段那 0.0875 的残余速度会继续下沉（几何级数合计约 0.8 格），
        // 观感是"落地后又沉一下，随后被悬停修正拉回"。待调手感值 / P8 转配置项
        boss.setDeltaMovement(0.0D, motionY, 0.0D);
        AgaitolosFx.introShockRing(boss, introAge);
        if (--boss.introTicks <= 0) {
            // 只出场一次：计时期满即撤状态，之后（含阶段推进触发的复活）不会再回到这里
            boss.setIntroPlaying(false);
        }
    }

    /**
     * 开演：把 BOSS 抬到"悬停高度 + {@link #INTRO_DESCENT_HEIGHT}"作为降临起点，并立起状态与计时。
     * <p>
     * 目标高度取 {@link AgaitolosMoveControl#hoverY}（唯一的悬停高度算法，MoveControl 每 tick 用的是同一份），
     * 不用"当前坐标 + 3.5"：召唤坐标未必正好在悬停位（例如被指令放到地面、或第一 tick 只被修正了 0.2 格），
     * 用真实悬停高度才能保证 40t 后落点就是它之后该待的位置，演出结束不出现二次升降。
     * <p>
     * 抬升用 {@code setPos} 直接落位（同"落点"语义，不需要 {@code teleportTo} 的乘客/朝向处理）：
     * 一次性瞬抬 3.5 格由原版位置包下发给客户端，客户端会插值过去，读作"闪现在空中"。
     */
    private static void startIntro(AgaitolosEntity boss) {
        // 目标高度取「本阶段真正的空中档高度」（AgaitolosMoveControl#airborneHeight）：
        // 与 MoveControl ③ 每 tick 维持的高度是同一个出口，演出结束时落点就是它之后该待的位置。
        // 出场必然发生在阶段一（首次召唤那一 tick），但这里刻意按阶段取而不是写死 HOVER_HEIGHT ——
        // 阶段三降高之后，两处若各写一份，将来任何"阶段切换后再演出"的场景都会漂移出一次二次升降。
        double hoverY = AgaitolosMoveControl.hoverY(boss.level(), boss.getX(), boss.getY(), boss.getZ(),
                AgaitolosMoveControl.airborneHeight(boss.getPhase()));
        // 虚空/深井里扫不到地面（NaN）时不强行算高度，退化为"原地不动"，演出其余两段照常
        boss.introDescentTargetY = Double.isNaN(hoverY) ? boss.getY() : hoverY;
        boss.setPos(boss.getX(), boss.introDescentTargetY + INTRO_DESCENT_HEIGHT, boss.getZ());
        // 清掉召唤瞬间可能残留的速度：起点必须是干净的，否则 ② 的匀速插值会被叠加上一段漂移
        boss.setDeltaMovement(Vec3.ZERO);
        boss.introTicks = INTRO_DURATION_TICKS;
        boss.setIntroPlaying(true);
    }
}
