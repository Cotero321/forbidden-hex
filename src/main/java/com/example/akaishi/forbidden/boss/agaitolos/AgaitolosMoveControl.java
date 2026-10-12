package com.example.akaishi.forbidden.boss.agaitolos;

import com.example.akaishi.forbidden.boss.agaitolos.skill.AgaitolosBombardSkill;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 阿盖托洛丝的低空飞行移动控制器：<b>低空悬停巡航 + 直线追击</b>，刻意不做寻路/避障（设计文档明确不追求）。
 * <p>
 * 蓝本 = 原版 {@code Ghast.GhastMoveControl}（飞行怪直接改写 {@code deltaMovement} 的最小做法）。
 * 每 tick 只做三件事：
 * <ol>
 *   <li><b>水平</b>：导航（{@code AgaitolosMeleeAttackGoal} 与决策层的脱困重寻路经 {@code FlyingPathNavigation}）
 *       下发移动指令时朝目标点的水平方向加速，加速度按 {@code speedModifier} 缩放；</li>
 *   <li><b>垂直</b>：无论有无指令都恒定维持「脚下地面顶面 + 当前高度档」——
 *       空中档按阶段取 {@link #HOVER_HEIGHT}（一/二阶段 2.0）或 {@link #PHASE_3_HOVER_HEIGHT}（三阶段 1.0，
 *       规格"低空飞行位置更低"），贴地档取 {@link #PERCH_HEIGHT}（近身缠斗时，见 {@link #isPerched()}）；</li>
 *   <li>把结果写回 {@code deltaMovement}，由 {@code AgaitolosEntity#travel} 消费（该 travel 不含重力项）。</li>
 * </ol>
 * 刻意不消费 {@code wantedY}：常态低空悬停、不俯冲，高度完全由脚下地形决定
 * （俯冲镰扫的冲锋位移不在这里，见下）。
 * <p><b>四类例外</b>：
 * <ul>
 *   <li>冲锋中（{@code boss.isDiving()}）：整体让位，水平与垂直都不写，位移由 {@code AgaitolosEntity#tickDiveCharge} 驱动
 *       （仅 ⓪'' 的转向照常 —— 冲锋落点那一记镰扫的扇面读的就是身体 yaw）；</li>
 *   <li>出场演出中（{@code boss.isIntroPlaying()}）：整体让位，水平与垂直都不写，位移由 {@code AgaitolosEntity#tickIntro} 驱动
 *       （转向照常，让降临中的朝向也跟得上目标）；</li>
 *   <li>禁飞中（{@code boss.isGrounded()}，注意这是"被格挡后的惩罚"、与地面档无关）：只做水平，
 *       垂直分量原样保留（交给 {@code LivingEntity#travel} 的重力把 BOSS 拉下地面）；</li>
 *   <li>格挡架势（{@code boss.isGuarding()}）：水平归零站定，垂直按当前高度档照常，
 *       并且<b>跳过 ⓪'' 的转向</b>（架势朝向由实体起手锁定，见下）；</li>
 *   <li>蓄力（{@code boss.isCharging()}，恶怨倒转）：同样水平归零站定、垂直按当前高度档照常
 *       —— 理由与架势相同（"举球蓄力要站定"，且蓄力姿势不该一边飘一边放）。</li>
 * </ul>
 * <p><b>地面档（2026-09-21 补）</b>：高度不再是常量，而是"由实体持有的档位"——本类只<b>读</b>
 * {@link AgaitolosEntity#isPerched()}，档位的进入/退出节律（迟滞、最短停留）全在实体侧
 * （{@code AgaitolosEntity#tickPerchState}）。分工与架势/蓄力一致：状态归实体，消费在本类。
 * 档位切换<b>不做瞬移</b>，仍走 ③ 的"按高度差收敛 + {@link #MAX_VERTICAL_SPEED} 限速"，
 * 故落地/升空各是一段约 10 tick 的可见过程，不会闪一下。
 * <p><b>身体 yaw（2026-09-21 根因修复）</b>：本类原先只写 {@code deltaMovement}，于是
 * <b>整个 BOSS 没有任何地方写 {@code yRot}</b> —— 原版唯一写身体 yaw 的正是 {@code MoveControl#tick}，
 * 本类整体让位后 {@code yRot} 就永远停在上一次显式写入的残留角（出生角 / 上次瞬击角）。
 * <b>字节码实证（Forge 1.20.1-47.3.0）</b>：
 * <ul>
 *   <li>{@code MoveControl#tick} 全方法只有一处写旋转 —— {@code MOVE_TO} 分支里的
 *       {@code Mob.setYRot(this.rotlerp(this.mob.getYRot(), f9, 90.0F))}（另两处是 {@code setZza/setSpeed}）；
 *       {@code STRAFE} 分支只写 {@code zza/xxa}，{@code JUMPING} 只写速度；</li>
 *   <li>{@code LookControl#tick} 只写 {@code mob.yHeadRot} 与 {@code mob.setXRot(...)}（{@code yRot} 只被读、从不被写）；</li>
 *   <li>{@code BodyRotationControl}（1.20.1 该类只有 {@code clientTick()}，由 {@code Mob#tickHeadTurn} 调用）
 *       只写 {@code yBodyRot}/{@code yHeadRot}。</li>
 * </ul>
 * 后果：所有"读朝向"的判据（格挡锥、镰扫扇面、粒子弧、护盾纹）与玩家看到的身体朝向脱节。
 * 现补 ⓪'' 一步：每 tick 朝「当前攻击目标」（无目标时退化为导航目标点）<b>限速</b>转动身体 yaw，
 * 转速 {@link #MAX_YAW_SPEED_DEGREES}（度/tick，乘阶段倍率），转角公式复用原版 {@code rotlerp}（不做瞬间甩头）。
 * 转向<b>不会反过来改位移</b>：本类从不调 {@code setZza/setXxa/setSpeed}，{@code travel} 收到的
 * {@code travelVector} 恒为 0 ⇒ 位移只由本类直接写的 {@code deltaMovement} 决定，与"刻意不做避障"的设计不冲突。
 */
public class AgaitolosMoveControl extends MoveControl {

    /**
     * 空中档悬停高度（格）：以「脚下地面顶面」为基准的抬升量。<b>阶段一/二的常态高度</b>。
     * <p>同时也是 {@code AgaitolosEntity#getMeleeAttackRangeSqr} 折近战可达距离时用的<b>最大</b>高度
     * （阶段三的 {@link #PHASE_3_HOVER_HEIGHT} 更低）—— 详见该方法的注释。
     * 手感值，待实机调 / P8 转配置项
     */
    public static final double HOVER_HEIGHT = 2.0D;

    /**
     * 阶段三的空中档悬停高度（格）：1.0。<b>待调手感值 / P8 转配置项</b>
     * <p>
     * 规格（设计文档 §0 阶段三）：「低空飞行位置更低，速度更快」——"速度更快"由
     * {@link AgaitolosPace#moveSpeed}（阶段三 1.4）承担，本常量承担"位置更低"。
     * <p>
     * <b>取 1.0 而不是更低的理由</b>：本 BOSS 的碰撞箱高度与"脚部"定义不变，1.0 格仍在
     * "玩家站着能被它打到、它也不至于被一格台阶卡住"的区间内（真正贴地的 0 格由
     * {@link #PERCH_HEIGHT} 那条<b>地面档</b>负责，两者是不同的状态，不能混为一谈）；
     * 同时保留 1 格净空，避免阶段三因为降高而频繁与地形发生碰撞、把"压迫感"做成"卡墙"。
     * <p>
     * <b>为什么不改 {@link #HOVER_HEIGHT} 本身</b>：那个值是一、二阶段的手感基线，也是近战可达
     * 尺子的换算基准（见 {@code AgaitolosEntity#getMeleeAttackRangeSqr}）。按阶段取值、
     * 由 {@link #airborneHeight(AgaitolosPhase)} 一处出口，胜过"改常量 + 到处找消费点"。
     */
    public static final double PHASE_3_HOVER_HEIGHT = 1.0D;

    /**
     * 地面档高度（格）：<b>0 = 真正贴地</b>（脚部落在脚下方块顶面上），对应"多待在地上"的手感要求。
     * <p>
     * <b>为什么不取一个小正数（例如 0.5）</b>：0.5 与 0 在"能不能跨过一格台阶"上没有区别
     * （碰撞都发生在躯体下缘 vs 高一格的方块），却会让模型脚底与地面之间留出一条可见缝，
     * 反而读作"还在飘"。故取真正的 0，把"会不会被地形卡住"交给两条既有机制兜：
     * ① 地面档本身是<b>限时</b>的（见 {@code AgaitolosEntity#PERCH_MAX_TICKS}），到点就升空脱离；
     * ② 决策层的卡住脱困会重算路径。手感值，待实机调 / P8 转配置项
     */
    public static final double PERCH_HEIGHT = 0.0D;

    /**
     * 该阶段的<b>空中档</b>高度（格）：<b>高度档按阶段取值的唯一出口</b>。
     * <p>
     * 阶段一/二（含复活阶段）→ {@link #HOVER_HEIGHT}（2.0）；阶段三 → {@link #PHASE_3_HOVER_HEIGHT}（1.0）。
     * 消费点两处：本类 ③ 的垂直目标高度、以及出场演出的降临目标高度
     * （{@code AgaitolosEntity#startIntro}）—— 后者必须是"演出结束后 MoveControl 会维持的那个高度"，
     * 故两处必须读同一个方法，否则会出现"降临完又被修正拉一下"的二次升降。
     * <p>注意：地面档（{@link #PERCH_HEIGHT}）<b>不</b>分阶段 —— "贴身贴地"这条语义与阶段无关。
     */
    public static double airborneHeight(AgaitolosPhase phase) {
        return phase == AgaitolosPhase.PHASE_3 ? PHASE_3_HOVER_HEIGHT : HOVER_HEIGHT;
    }

    /** 水平加速度（格/tick，再乘 speedModifier 与阶段倍率后作用于水平速度）——待调手感值 */
    private static final double HORIZONTAL_ACCELERATION = 0.05D;

    /** 水平速度上限（格/tick）：空气阻力只有 0.91，不夹住会越飞越快；同样乘阶段倍率——待调手感值 */
    private static final double MAX_HORIZONTAL_SPEED = 0.25D;

    /** 到导航目标点的水平死区（格）：进入后不再加速，避免在目标点上方来回抖 */
    private static final double HORIZONTAL_DEAD_ZONE = 0.5D;

    /** 高度差 → 垂直速度的比例系数（再经 {@link #MAX_VERTICAL_SPEED} 限速） */
    private static final double VERTICAL_GAIN = 0.25D;

    /** 垂直速度上限（格/tick）：限制上升/下降速率，避免瞬移感；乘阶段倍率后阶段二三升降更快——待调手感值 */
    private static final double MAX_VERTICAL_SPEED = 0.2D;

    /** 高度差死区（格）：差量小于此值即视为已在悬停位，垂直速度归零以抑制抖动 */
    private static final double VERTICAL_DEAD_ZONE = 0.05D;

    /**
     * 身体 yaw 的最大转速（度/tick）：12。<b>待调手感值 / P8 转配置项</b>
     * <p>
     * 取值的两重依据：① 180°（正后方绕一圈）用 15 tick = 0.75s 转完 —— 玩家放风筝时能看出它"正在跟头"，
     * 又不会甩到追不上走位；② 与"玩家可读的预警"对齐：格挡架势 / 镰扫扇面都以身体 yaw 为基准，
     * 转速太慢会让起手朝向永远滞后于走位，太快（原版 MOVE_TO 的 90°/tick）则等于瞬间甩头、失去预告意义。
     * 乘阶段倍率 {@link #pace()}：与水平/垂直限速同一条口径（二阶段起整体更快，转向也是"更快"的一部分）。
     */
    private static final float MAX_YAW_SPEED_DEGREES = 12.0F;

    /**
     * 朝向退化阈值（平方）：水平差 <b>1 毫米</b> 以内即视为"与朝向目标在同一垂直线上"。
     * <p>此时 {@code atan2(0, 0)} 给出的是任意角，写进去只会把朝向甩偏 ⇒ 本 tick 不写（保持当前朝向）。
     * 与 {@code AgaitolosGuardSkill} / {@code AgaitolosDiveSweepSkill} 的同名阈值同一口径（1.0E-6 = 1 毫米²）。
     */
    private static final double FACING_EPSILON_SQR = 1.0E-6D;

    /**
     * 过渡窗口内匀速收敛的<b>垂直速度上限</b>（格/tick，0.5 = 基准 {@link #MAX_VERTICAL_SPEED} 的 2.5 倍）。
     * <p>纯粹的安全护栏：正常升降的高度差恒为「空中档 − 地面档」= 一/二阶段 {@code HOVER_HEIGHT - PERCH_HEIGHT = 2.0}
     * 格、三阶段 {@code PHASE_3_HOVER_HEIGHT - PERCH_HEIGHT = 1.0} 格
     * ⇒ 过渡期速度分别约 0.167 / 0.083，护栏都用不到；只有"被击退/地形把 BOSS 掀到很高处后恰好进过渡窗口"
     * 这种异常组合下才需要拦住"高度差 ÷ 12"算出的过大速度（否则会读成瞬移）。<b>待调手感值 / P8 转配置项</b>
     */
    private static final double MAX_TRANSITION_VERTICAL_SPEED = 0.5D;

    /** 向下探测脚下地面的最大格数；范围内找不到地面（虚空）则本 tick 不做垂直修正 */
    private static final int GROUND_SCAN_RANGE = 16;

    public AgaitolosMoveControl(Mob mob) {
        super(mob);
    }

    @Override
    public void tick() {
        // ⓪'' 身体 yaw 转向（2026-09-21 根因修复）：刻意放在所有让位分支<b>之前</b> ——
        //      "朝哪一边"与"位移由谁驱动"是两件事：冲锋中的镰扫扇面、降临时的入场朝向、禁飞期的格挡锥
        //      都读 yRot，都需要它在这些阶段继续跟着目标。若放在 ⓪/⓪' 之后，冲锋与出场期间朝向就又没人写了。
        //      唯独格挡架势必须跳过：架势的朝向是起手锁死的"承诺"（可惩罚面，见 AgaitolosEntity#startGuard），
        //      若在这里继续追人，玩家从背后绕过去就再也打不穿（等于把 120° 锥偷偷放大到 360°）。
        if (!isGuarding()) {
            this.turnTowardsFacing();
        }

        // ⓪ 冲锋中：<b>水平与垂直都完全不写</b>，整体让位。
        //    冲锋期间 deltaMovement 由 AgaitolosEntity#tickDiveCharge 每 tick 直接写（朝目标 3D 方向 × DIVE_SPEED）。
        //    若这里继续写：水平会被"导航目标点死区 + 限速"抹平，垂直会被 ③ 的悬停修正拉回 HOVER_HEIGHT，
        //    结果是俯冲永远贴近不了地面目标、抵达判定次次超时。
        //    与既有"架势掐断水平"同理，必须在控制器层让位 —— 本 tick 的 travel 在随后执行，实体侧改写来不及。
        if (isDiving()) {
            return;
        }

        // ⓪' 出场演出（首次召唤的 0~80t）：同样<b>整体让位</b>。
        //    出场期间位移由 AgaitolosEntity#tickIntro 逐 tick 直接写竖直分量（从悬停高度上方 3.5 格拉降下来）。
        //    这里若继续跑：③ 的悬停修正每 tick 都会把竖直速度改写成"朝悬停高度收敛"（offset 3.5 ⇒ 恒 -0.2），
        //    与降临插值互相抵消，BOSS 会以 MoveControl 的速度（约 17.5 tick 到底）落下来，40t 的编排被抹平；
        //    水平同样要站定（演出不接受导航加速）。
        //    与 ⓪ 的差别只在"谁接手"：⓪ 由 tickDiveCharge 写 3D 速度去追人，本处由 tickIntro 只写竖直分量原地降临。
        if (isIntroPlaying()) {
            return;
        }

        Vec3 motion = this.mob.getDeltaMovement();
        double motionX = motion.x;
        double motionZ = motion.z;

        // ① 水平追击：导航每 tick 都会重新下发 setWantedPosition，故这里只按"当前是否有指令"处理，不把 operation 改回 WAIT。
        //    用 hasWanted() 而不是直接比 Operation：Operation 是 MoveControl 的 protected 嵌套枚举，跨包不便引用。
        //    格挡架势 / 蓄力例外：两者都要站定，既不产生水平加速、也抹掉残留惯性（垂直悬停照常，见 ③）。
        //    必须在这一层改而不能在实体侧改：MoveControl#tick 由 Mob#serverAiStep 调用、位于 travel 之前，
        //    而 travel 已经在 super.aiStep() 内部执行过，实体侧 post-super 清速度对当帧无效。
        if (isGuarding() || isCharging()) {
            motionX = 0.0D;
            motionZ = 0.0D;
        } else if (this.hasWanted()) {
            double deltaX = this.wantedX - this.mob.getX();
            double deltaZ = this.wantedZ - this.mob.getZ();
            double horizontal = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
            if (horizontal > HORIZONTAL_DEAD_ZONE) {
                double acceleration = HORIZONTAL_ACCELERATION * this.speedModifier * pace();
                motionX += deltaX / horizontal * acceleration;
                motionZ += deltaZ / horizontal * acceleration;
            }
        }

        // ② 水平限速（加速度 + 空气阻力的平衡速度会超过上限，这里夹住）；上限同样乘阶段倍率
        double maxHorizontalSpeed = MAX_HORIZONTAL_SPEED * pace();
        double horizontalSpeed = Math.sqrt(motionX * motionX + motionZ * motionZ);
        if (horizontalSpeed > maxHorizontalSpeed) {
            double scale = maxHorizontalSpeed / horizontalSpeed;
            motionX *= scale;
            motionZ *= scale;
        }

        // ③ 垂直（悬停/贴地）：无移动指令时也执行（否则会随空气阻力缓慢丢失高度）。
        //    目标高度 = 脚下地面顶面 + 当前高度档（空中档按阶段 2.0 / 1.0，地面档 0.0），两档共用同一个目标高度真源（hoverY）；
        //    但"怎么逼近"分两条：过渡窗口内匀速（③-a，与过渡 clip 等长）、稳态按比例 + 限速（③-b）。
        //    两条都只写 speed、不做瞬移 ⇒ 档位切换是一段看得见的平滑升降。
        double motionY;
        if (isGrounded()) {
            // 禁飞（被格挡惩罚）：<b>不写垂直分量</b>，原样保留 travel 里累积的重力速度，让 BOSS 自然下坠。
            // 这里绝不能写 0：本控制器在 travel 之前执行、且每 tick 都会 setDeltaMovement，
            // 写 0 会把上一 tick 累积的下坠速度清零 ⇒ 重力永远积不起来，BOSS 会一直挂在半空，禁飞形同虚设。
            motionY = motion.y;
        } else {
            motionY = 0.0D;
            double hoverY = this.hoverY();
            if (!Double.isNaN(hoverY)) {
                double offset = hoverY - this.mob.getY();
                int transitionTicks = this.perchTransitionTicks();
                if (transitionTicks > 0) {
                    // ③-a 过渡窗口（落地 / 升空各 12t，与过渡 clip 等长）：改用「剩余高度 ÷ 剩余 tick」的<b>匀速</b>收敛，
                    //     让"位置到位"与"clip 播完"严格落在同一 tick（与本项目出场降临 tickIntro 同一手法）。
                    //     为什么不能继续用 ③-b 那条式子：它是"首段恒速 + 尾段指数趋近"，永远只做渐近逼近 ——
                    //     实测残差：12t 时阶段一还差 0.142 格、二阶段 0.100 格、三阶段 0.088 格
                    //     （离散实测收敛到死区需 17 / 16 / 15 tick）。此时 clip 已经播完、idle_ground 已接手，
                    //     而身子还在往下沉 —— 这正是"动画还差最后几帧、位置已到位"的错位，且残差随阶段漂移（口径不一致）。
                    //     匀速式子里没有 pace：分母恒为"窗口剩余 tick"，三种阶段逐 tick 同值 ⇒ 与固定 12t 的 clip 恒对齐；
                    //     只剩 VERTICAL_DEAD_ZONE 的 0.05 格容差被彻底消掉（第 12 tick 精确落到目标高度）。
                    //     读到的剩余 tick 比真实值晚一帧（本方法在 super.aiStep() 内、实体侧窗口在之后递减），
                    //     但"速度 = 剩余距离 ÷ 剩余 tick"每帧重算，这个一帧滞后恰好被分子吸收 ⇒ 全程恒速，不会走成阶梯。
                    motionY = Mth.clamp(offset / transitionTicks,
                            -MAX_TRANSITION_VERTICAL_SPEED, MAX_TRANSITION_VERTICAL_SPEED);
                } else if (Math.abs(offset) > VERTICAL_DEAD_ZONE) {
                    // ③-b 稳态：按高度差比例收敛并限速（乘阶段倍率）
                    double maxVerticalSpeed = MAX_VERTICAL_SPEED * pace();
                    motionY = Mth.clamp(offset * VERTICAL_GAIN, -maxVerticalSpeed, maxVerticalSpeed);
                }
            }
        }

        this.mob.setDeltaMovement(motionX, motionY, motionZ);
    }

    /**
     * 朝"当前该看的方向"<b>限速</b>转动身体 yaw —— 本类、也是整个 BOSS 唯一逐 tick 写 {@code yRot} 的地方。
     * <p>
     * <b>为什么必须有这一步（根因）</b>：原版 {@code MoveControl#tick} 是 vanilla 里唯一写身体 yaw 的地方
     * （字节码见类 javadoc），本类整体改写了它（{@code tick()} 不调 {@code super}）⇒ 让位期间没有任何地方写
     * {@code yRot}，朝向会停在上一次显式写入的残留角（出生角 / 上次瞬击角），
     * 于是格挡锥、镰扫扇面、护盾纹、粒子弧全部与玩家看到的身体朝向脱节。
     * <p>
     * <b>为什么朝向取"当前攻击目标"而不是"导航目标点"</b>：读朝向的判据全是"对着谁"语义
     * （格挡锥 120°、镰扫扇面 180°、挥砍/横扫粒子弧、护盾纹），要的答案是"我在打谁"，
     * 而不是"这一帧脚下要往哪个坐标去"（后退或绕行时两者方向相反）。只有在没有目标时才退化为导航目标点
     * （巡航朝向），两者都没有就不写（保持原朝向，也就不产生任何同步量）。
     * <p>
     * <b>为什么用 {@code rotlerp} 而不是自己夹角</b>：它内部走 {@code Mth.wrapDegrees}，
     * 自带 ±360° 环绕归一 —— 跨 0° 边界（例如 yaw 179° → 目标 −179°）时只转 2° 而不是倒着转 358°。
     */
    private void turnTowardsFacing() {
        LivingEntity target = this.mob.getTarget();
        double aimX;
        double aimZ;
        if (target != null && target.isAlive()) {
            aimX = target.getX();
            aimZ = target.getZ();
        } else if (this.hasWanted()) {
            aimX = this.wantedX;
            aimZ = this.wantedZ;
        } else {
            return;
        }
        double deltaX = aimX - this.mob.getX();
        double deltaZ = aimZ - this.mob.getZ();
        // 与朝向目标落在同一垂直线上（水平差 < 1 毫米）：atan2(0, 0) 是任意角，写了只会把朝向甩偏 ⇒ 本 tick 不写
        if (deltaX * deltaX + deltaZ * deltaZ < FACING_EPSILON_SQR) {
            return;
        }
        // 与 AgaitolosEntity#yawTowards / AgaitolosBlinkSkill#faceTarget 同一公式（原版惯例：atan2(dz, dx) 转角度后 −90°）
        float wantedYaw = (float) (Mth.atan2(deltaZ, deltaX) * (180.0D / Math.PI)) - 90.0F;
        this.mob.setYRot(this.rotlerp(this.mob.getYRot(), wantedYaw, MAX_YAW_SPEED_DEGREES * (float) this.pace()));
    }

    /**
     * 当前过渡窗口（落地 / 升空）的剩余 tick；0 = 不在过渡中。
     * <p>
     * 只<b>读</b>实体侧的计数（{@code AgaitolosEntity#getPerchTransitionTicks()}）：节律的进入/退出仍归实体
     * （见 {@link #isPerched()} 的分工），本类只是拿它当 ③-a 匀速收敛的分母。
     * 非本 BOSS 生物回退 0（走 ③-b 常态收敛），与本类其它 {@code instanceof} 兜底同款。
     */
    private int perchTransitionTicks() {
        return this.mob instanceof AgaitolosEntity boss ? boss.getPerchTransitionTicks() : 0;
    }

    /**
     * 所属生物是否正在俯冲冲锋。为 true 时本控制器整体让位（见 {@link #tick()} ⓪）。
     * <p>{@code instanceof} 仅作类型兜底（防将来被复用到别的生物上），同 {@link #isGuarding()}。
     */
    private boolean isDiving() {
        return this.mob instanceof AgaitolosEntity boss && boss.isDiving();
    }

    /**
     * 所属生物是否被禁飞（被玩家格挡后 30s）。为 true 时停止悬停修正，交给重力。
     * <p>禁飞结束无需额外处理：本方法转 false 后 ③ 会照常把 BOSS 收敛回
     * 「脚下地面顶面 + 当前阶段的空中档高度」，即自动升回本阶段的常态悬停高度。
     */
    private boolean isGrounded() {
        return this.mob instanceof AgaitolosEntity boss && boss.isGrounded();
    }

    /**
     * 所属生物是否正在表演出场（首次召唤的 0~80t）。为 true 时本控制器整体让位（见 {@link #tick()} ⓪'）。
     * <p>{@code instanceof} 仅作类型兜底（防将来被复用到别的生物上），与本类其它判定同款。
     */
    private boolean isIntroPlaying() {
        return this.mob instanceof AgaitolosEntity boss && boss.isIntroPlaying();
    }

    /**
     * 所属生物是否正在摆格挡架势。
     * <p>本控制器只装配给 {@link AgaitolosEntity}，{@code instanceof} 仅作类型兜底（防将来被复用到别的生物上）。
     */
    private boolean isGuarding() {
        return this.mob instanceof AgaitolosEntity boss && boss.isGuarding();
    }

    /**
     * 所属生物是否正在「恶怨倒转」蓄力。为 true 时水平归零站定（垂直悬停照常）。
     * <p>与 {@link #isGuarding()} 同一机制、同一理由：蓄力要举球站定，不能一边飘一边蓄。
     * <p>{@code instanceof} 仅作类型兜底（防将来被复用到别的生物上）。
     */
    private boolean isCharging() {
        return this.mob instanceof AgaitolosEntity boss && boss.isCharging();
    }

    /**
     * 当前阶段的移动速度倍率（{@link AgaitolosPace#moveSpeed(AgaitolosEntity)}）：
     * 规格"二阶段比一阶段更加快速"的<b>唯一</b>落点之一（另一处是冲锋速度，见 {@code AgaitolosEntity#tickDiveCharge}）。
     * <p>阶段一与复活阶段恒为 1.0 ⇒ 乘算后与原值逐位相同，一阶段手感不受影响。
     * <p>{@code instanceof} 兜底：非本 BOSS 生物回退 1.0（将来若复用本控制器不会莫名提速）。
     */
    private double pace() {
        return this.mob instanceof AgaitolosEntity boss ? AgaitolosPace.moveSpeed(boss) : 1.0D;
    }

    /**
     * 所属生物是否处于<b>地面档</b>（近身缠斗时贴地）。为 true 时 ③ 的目标高度改用 {@link #PERCH_HEIGHT}。
     * <p>档位的进入/退出节律归实体（见 {@code AgaitolosEntity#tickPerchState}），本类只读。
     * <p>⚠ 与本类的 {@link #isGrounded()}（= 被格挡后的禁飞惩罚）<b>不是一回事</b>：那一条是"不写垂直分量、
     * 交给重力"，这一条是"垂直修正到一个更低的目标高度"。两者可以同时成立（禁飞期重力已把 BOSS 压在地上，
     * 地面档只是让它在惩罚结束后不要立刻弹回空中）。
     * <p>{@code instanceof} 仅作类型兜底（防将来被复用到别的生物上），与本类其它判定同款。
     */
    private boolean isPerched() {
        return this.mob instanceof AgaitolosEntity boss && boss.isPerched();
    }

    /**
     * 计算本 tick 的垂直目标 Y（实体脚部坐标）。
     * <p>
     * 从脚部所在方块向下限程扫描，取第一个「有碰撞形状或含流体」的方块作为脚下地面
     * （判据与原版 {@code Heightmap.Types.MOTION_BLOCKING_NO_LEAVES} 同一口径），
     * 目标高度 = 该方块顶面 Y + 当前高度档（{@link #isPerched()} 决定空中档还是地面档；
     * 空中档的具体数值再按阶段取，见 {@link #airborneHeight()}）。
     * <p>
     * 用向下扫描而不是 {@code Level#getHeight(Heightmap.Types, int, int)}：后者取的是整列最高遮挡方块，
     * 在地下/洞穴里会返回地表 Y，把 BOSS 往天上拉。
     *
     * @return 垂直目标 Y；{@link #GROUND_SCAN_RANGE} 格内无地面（虚空/深井）时返回 {@link Double#NaN}，表示本 tick 不修正高度
     */
    private double hoverY() {
        return hoverY(this.mob.level(), this.mob.getX(), this.mob.getY(), this.mob.getZ(),
                isPerched() ? PERCH_HEIGHT : airborneHeight());
    }

    /**
     * 所属生物<b>当前</b>的空中档高度（格）。
     * <p>三档优先级：<b>饱和轰炸的高空档</b>（阶段三，{@code AgaitolosBombardSkill#HOVER_HEIGHT}）⇒
     * 本阶段常态空中档（{@link #airborneHeight(AgaitolosPhase)}）⇒ 非本 BOSS 生物兜底的 {@link #HOVER_HEIGHT}。
     * <p>轰炸档排在最前、但<b>只是叠在常态之上的一层临时高度</b>：状态一撤（{@code isBombarding()} 转假），
     * ③ 的悬停修正立刻按本阶段常态档（阶段三 = {@link #PHASE_3_HOVER_HEIGHT} 的超低空）收敛回去，
     * 故"阶段三超低空"这条既有口径没有被改掉，只是被轰炸临时借用了几秒。
     * <p>高度常量刻意<b>引用技能类</b>而不是在本类再写一个 6.0：与 {@code TIER_MID_MAX} 引用
     * {@code DIVE_TRIGGER_RANGE} 同一手法 —— 一处改动不会漂移成"动画抬到 6 格、位移只到 4 格"。
     */
    private double airborneHeight() {
        if (!(this.mob instanceof AgaitolosEntity boss)) {
            return HOVER_HEIGHT;
        }
        if (boss.isBombarding()) {
            return AgaitolosBombardSkill.HOVER_HEIGHT;
        }
        return airborneHeight(boss.getPhase());
    }

    /**
     * 垂直目标高度的<b>唯一实现</b>（上面的重载只是把状态/档位喂进来）。
     *
     * @param heightOffset 高度档（格）：空中档 {@link #airborneHeight(AgaitolosPhase)} 或地面档 {@link #PERCH_HEIGHT}
     * @return 该方块顶面 + 高度档；{@link #GROUND_SCAN_RANGE} 格内无地面时返回 {@link Double#NaN}
     */
    public static double hoverY(Level level, double x, double y, double z, double heightOffset) {
        double groundTopY = groundTopY(level, x, y, z);
        return Double.isNaN(groundTopY) ? Double.NaN : groundTopY + heightOffset;
    }

    /**
     * 脚下地面的<b>顶面 Y</b>（即"站在地面上时脚部该在的 Y"）。
     * <p>从脚部所在方块向下限程扫描，取第一个「有碰撞形状或含流体」的方块，
     * 返回其顶面（该方块 y + 1）。判据与原版 {@code Heightmap.Types.MOTION_BLOCKING_NO_LEAVES} 同一口径。
     *
     * @return 地面顶面 Y；{@link #GROUND_SCAN_RANGE} 格内无地面（虚空/深井）时返回 {@link Double#NaN}
     */
    private static double groundTopY(Level level, double x, double y, double z) {
        int blockX = Mth.floor(x);
        int blockZ = Mth.floor(z);
        int lowestY = Math.max(level.getMinBuildHeight(), Mth.floor(y) - GROUND_SCAN_RANGE);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int scanY = Mth.floor(y); scanY >= lowestY; --scanY) {
            cursor.set(blockX, scanY, blockZ);
            BlockState state = level.getBlockState(cursor);
            // 无碰撞（空气、草、火把…）且不含流体 ⇒ 不是地面，继续往下找
            if (state.getCollisionShape(level, cursor).isEmpty() && state.getFluidState().isEmpty()) {
                continue;
            }
            return scanY + 1; // 该方块顶面
        }
        return Double.NaN;
    }
}
