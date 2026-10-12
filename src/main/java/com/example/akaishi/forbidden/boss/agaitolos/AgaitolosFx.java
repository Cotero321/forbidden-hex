package com.example.akaishi.forbidden.boss.agaitolos;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * 阿盖托洛丝的表现层：出场爆发 + 出场三段演出（粒子柱 / 落地冲击环）、死亡爆发、蓄力光球等纯粒子演出。
 * <p>
 * <b>为什么单独成类</b>：粒子只跟"什么时候放、放成什么样"有关，与状态机、伤害口径、
 * 阶段推进毫无耦合。塞进 {@code AgaitolosEntity} 会让本就承担编排的实体继续变胖，
 * 也让以后调手感（换个粒子/改个数量）必须去改实体。这里只暴露语义入口，
 * 实体侧各调一行，两侧互不牵制。演出<b>时节</b>（哪一段、演到第几 tick）由实体掌握，
 * 本类只按传入的年龄做窗口判定与节流（时间窗常量仍是实体那一份，本类只读）。
 * <p>
 * <b>为什么全部只在服务端做</b>：
 * <ol>
 *   <li>触发时机来自服务端状态机（{@code aiStep} 的蓄力分支 / {@code die()}），客户端没有这些时刻；</li>
 *   <li>服务端 {@code sendParticles} 会自动逐玩家下发 {@code ClientboundLevelParticlesPacket}，
 *       不用自己写同步；客户端 {@code addParticle} 则只有本机看得见，多人局里是"主机独享特效"。</li>
 * </ol>
 * 因此每个入口都用 {@code boss.level() instanceof ServerLevel level} 兜底，非服务端直接空转 ——
 * 这层兜底不是防御性冗余：少了它，客户端调用会在强转处抛 {@code ClassCastException}。
 * <p>
 * 所有常量均为<b>待调手感值</b>（P8 转配置项）。位置的绝对精度受限于服务端拿不到骨骼坐标，
 * 一律按碰撞箱 + 朝向做近似（详见 {@link #orbPosition}）。
 * <p>
 * <b>与本类并列的还有 {@link AgaitolosActionFx}</b>：那边是"挥刀 / 格挡 / 受击"这类由技能结算点驱动的
 * <b>动作</b>粒子，本类只留"出场 / 蓄力 / 死亡"这类由阶段机与计时器驱动的<b>演出</b>粒子；
 * 拆成两个类是为了各自不过长（RULES §8），两类的服务端限定、无状态、识别色口径完全一致。
 */
public final class AgaitolosFx {

    /** 蓄力光球的发射节流间隔（tick）：2 —— 每 2 tick 一簇，约 10 簇/s。待调手感值 / P8 转配置项 */
    public static final int CHARGE_ORB_INTERVAL_TICKS = 2;

    /** 光球每簇粒子数：3。待调手感值 / P8 转配置项 */
    public static final int CHARGE_ORB_COUNT = 3;

    /** 光球粒子的扩散速度（格）：0.16。待调手感值 / P8 转配置项 */
    public static final double CHARGE_ORB_SPREAD = 0.16D;

    /** 光球粒子的初速度：0.01（几乎原地，靠 PORTAL 自身的上飘体现"能量在聚集"）。待调手感值 / P8 转配置项 */
    public static final double CHARGE_ORB_SPEED = 0.01D;

    /** 光球高度 = 身高 × 该比例：0.95（身高 2.2 ⇒ 约 2.09 格，头顶略上，像举在头前的手上）。待调手感值 / P8 转配置项 */
    public static final double CHARGE_ORB_HEIGHT_RATIO = 0.95D;

    /** 光球沿朝向的前移距离（格）：0.45（贴着头前，不至于飘进胸口里）。待调手感值 / P8 转配置项 */
    public static final double CHARGE_ORB_FORWARD = 0.45D;

    /** 汇聚粒子相对光球中心的下沉量（格）：1.2（让球心略低于脚，视觉上像从地面被吸上来）。待调手感值 / P8 转配置项 */
    public static final double CHARGE_ORB_CONVERGE_DROP = 1.2D;

    /** 汇聚粒子的扩散速度（格）：0.35。待调手感值 / P8 转配置项 */
    public static final double CHARGE_ORB_CONVERGE_SPREAD = 0.35D;

    /** 汇聚粒子每簇数量：2。待调手感值 / P8 转配置项 */
    public static final int CHARGE_ORB_CONVERGE_COUNT = 2;

    /** 蓄力<b>强化段</b>的发射间隔（tick）：4 —— 比光球本体（2）稀一档，亮核与汇聚环保留"点缀"定位。待调手感值 / P8 转配置项 */
    public static final int CHARGE_UPGRADE_INTERVAL_TICKS = 4;

    /** 蓄力亮核每簇粒子数：1（END_ROD 是白亮星点，多放就晃眼）。待调手感值 / P8 转配置项 */
    public static final int CHARGE_CORE_COUNT = 1;

    /** 蓄力亮核的扩散（格）：0.05（钉在球心，给"球在发亮"而不是"球在冒白点"）。待调手感值 / P8 转配置项 */
    public static final double CHARGE_CORE_SPREAD = 0.05D;

    /** 蓄力汇聚环的采样点数：8（手写取点才能成环，理由同 {@link #introShockRing}）。待调手感值 / P8 转配置项 */
    public static final int CHARGE_RING_POINTS = 8;

    /** 蓄力汇聚环半径（格）：2.4（约三倍躯干宽，读作"能量从四周被吸来"）。待调手感值 / P8 转配置项 */
    public static final double CHARGE_RING_RADIUS = 2.4D;

    /** 蓄力汇聚环相对球心的下沉量（格）：2.2（环取低处，才有"往球心收"的方向感）。待调手感值 / P8 转配置项 */
    public static final double CHARGE_RING_DROP = 2.2D;

    /** 汇聚环每点粒子数：1。待调手感值 / P8 转配置项 */
    public static final int CHARGE_RING_COUNT = 1;

    /** 汇聚环点的扩散（格）：0.15（保持"点"，连成环而不是糊成盘）。待调手感值 / P8 转配置项 */
    public static final double CHARGE_RING_SPREAD = 0.15D;

    /** 出场爆发的粒子数：60。待调手感值 / P8 转配置项 */
    public static final int INTRO_BURST_COUNT = 60;

    /** 出场爆发的扩散速度（格）：1.4。待调手感值 / P8 转配置项 */
    public static final double INTRO_BURST_SPREAD = 1.4D;

    /** 出场爆发的初速度：0.06。待调手感值 / P8 转配置项 */
    public static final double INTRO_BURST_SPEED = 0.06D;

    /** 死亡爆发的粒子数：160。待调手感值 / P8 转配置项 */
    public static final int DEATH_BURST_COUNT = 160;

    /** 死亡爆发的扩散速度（格）：3.0。待调手感值 / P8 转配置项 */
    public static final double DEATH_BURST_SPREAD = 3.0D;

    /** 死亡爆发的初速度：0.09。待调手感值 / P8 转配置项 */
    public static final double DEATH_BURST_SPEED = 0.09D;

    /** 爆发粒子的高度 = 身高 × 该比例：0.6（躯干中部，而不是脚底或头顶）。待调手感值 / P8 转配置项 */
    public static final double BURST_HEIGHT_RATIO = 0.6D;

    /** 出场粒子柱（① 段）的发射节流间隔（tick）：2 —— 与 {@link #CHARGE_ORB_INTERVAL_TICKS} 同款口径。待调手感值 / P8 转配置项 */
    public static final int INTRO_PILLAR_INTERVAL_TICKS = 2;

    /** 出场粒子柱的层数：3（脚 / 腰 / 头，围成一段"柱"而不是一圈盘）。待调手感值 / P8 转配置项 */
    public static final int INTRO_PILLAR_LAYERS = 3;

    /** 出场粒子柱的层间距（格）：0.9（三层合起来约 1.8 格，覆盖身高 2.9 的中段）。待调手感值 / P8 转配置项 */
    public static final double INTRO_PILLAR_LAYER_HEIGHT = 0.9D;

    /** 出场粒子柱在 ① 段内整体上抬的高度（格）：1.2 —— 上升感只能靠逐帧抬高发射高度（见 {@link #introPillar}）。待调手感值 / P8 转配置项 */
    public static final double INTRO_PILLAR_RISE = 1.2D;

    /** 出场粒子柱每层每簇的粒子数：6。待调手感值 / P8 转配置项 */
    public static final int INTRO_PILLAR_COUNT = 6;

    /** 出场粒子柱的<b>亮芯</b>每层粒子数：1（END_ROD 白亮星，柱心一亮，整根柱才不显得只是青焰在飘）。待调手感值 / P8 转配置项 */
    public static final int INTRO_PILLAR_CORE_COUNT = 1;

    /** 出场粒子柱亮芯的扩散（格）：0.08（钉在轴上，不参与环绕）。待调手感值 / P8 转配置项 */
    public static final double INTRO_PILLAR_CORE_SPREAD = 0.08D;

    /** 出场粒子柱的环绕半径（格）：1.1（略大于碰撞箱半宽 0.8，贴着周身而不穿进模型）。待调手感值 / P8 转配置项 */
    public static final double INTRO_PILLAR_RADIUS = 1.1D;

    /** 出场粒子柱的竖直扩散（格）：0.25（层内压成盘，层与层之间才连得成柱）。待调手感值 / P8 转配置项 */
    public static final double INTRO_PILLAR_SPREAD_Y = 0.25D;

    /** 出场粒子柱的初速度：0.02（近乎原地，上升交给发射高度）。待调手感值 / P8 转配置项 */
    public static final double INTRO_PILLAR_SPEED = 0.02D;

    /** 落地冲击环（③ 段）的发射节流间隔（tick）：2。待调手感值 / P8 转配置项 */
    public static final int INTRO_SHOCK_INTERVAL_TICKS = 2;

    /** 落地冲击环的采样点数（"一圈"由这些点连成）：8。待调手感值 / P8 转配置项 */
    public static final int INTRO_SHOCK_POINTS = 8;

    /** 落地冲击环的最大半径（格）：6（到 ③ 段末尾达到）。待调手感值 / P8 转配置项 */
    public static final double INTRO_SHOCK_MAX_RADIUS = 6.0D;

    /** 冲击环每个采样点的粒子数：2。待调手感值 / P8 转配置项 */
    public static final int INTRO_SHOCK_RING_COUNT = 2;

    /** 冲击环采样点的扩散（格）：0.2（保持"点"，连成环而不是糊成圆盘）。待调手感值 / P8 转配置项 */
    public static final double INTRO_SHOCK_RING_SPREAD = 0.2D;

    /** 冲击环粒子的初速度：0.05（略外扩，读作"冲击波推出去"）。待调手感值 / P8 转配置项 */
    public static final double INTRO_SHOCK_RING_SPEED = 0.05D;

    /** 冲击环的离地抬升（格）：0.1 —— 环贴在方块顶面之上，不埋进地里。待调手感值 / P8 转配置项 */
    public static final double INTRO_SHOCK_GROUND_LIFT = 0.1D;

    /** 落地地面尘的粒子数：6（与环同帧、按当前半径铺开，读作"尘随环起"）。待调手感值 / P8 转配置项 */
    public static final int INTRO_SHOCK_DUST_COUNT = 6;

    /** 地面尘的竖直扩散（格）：0.3。待调手感值 / P8 转配置项 */
    public static final double INTRO_SHOCK_DUST_SPREAD_Y = 0.3D;

    /** 地面尘的初速度：0.01（几乎只就地散开，避免被吹成一团飘云）。待调手感值 / P8 转配置项 */
    public static final double INTRO_SHOCK_DUST_SPEED = 0.01D;

    private AgaitolosFx() {
    }

    /**
     * 「恶怨倒转」蓄力期间的紫光球（由 {@code AgaitolosEntity#tickCharge} 每 tick 调用）。
     * <p>
     * <b>节流放在这里而不是实体里</b>：{@code boss.tickCount % CHARGE_ORB_INTERVAL_TICKS} 是无状态判据，
     * 本类因此不需要自持任何计时字段/实例 —— 工具类保持无状态，重进存档也不会出现"计时器没复位"的错位。
     * <p>
     * <b>为什么选 PORTAL</b>：它是终界/末影人那类紫罗兰旋涡点，慢速外扩 + 缓慢上飘、不瞬间消失，
     * 是原版粒子里最像"悬停的紫色能量球"的；{@code DRAGON_BREATH} 虽是淡紫软雾但寿命短、会淡出，
     * 单独用来做球心会显得虚，所以留给下方那一簇"能量汇聚"（下沉 {@link #CHARGE_ORB_CONVERGE_DROP} 格），
     * 两簇叠加才有"球心实、外围在往里收"的层次。
     * <p>
     * <b>强化段（每 {@link #CHARGE_UPGRADE_INTERVAL_TICKS} tick 一次）</b>由两簇组成，用来把 12s 的蓄力
     * 从"一直有个紫球"抬到"能量在往里灌、且越举越亮"：
     * <ul>
     *   <li><b>亮核</b> {@code END_ROD}：原版唯一的白亮星点，钉在球心给"发光"的读数 ——
     *       纯紫粒子在昏暗战场上会被环境压住，加一颗冷白才有亮度锚点（与识别色同族，不引入暖色）；</li>
     *   <li><b>汇聚环</b>：球心下方 {@link #CHARGE_RING_DROP} 格、半径 {@link #CHARGE_RING_RADIUS} 格的
     *       一圈采样点。{@code sendParticles} 给不了"朝内的初速"（偏移是高斯位置扰动），故"往里收"
     *       只能靠<b>环在低处、球在高处</b>这一位置关系读出方向（视觉上像被吸上去）。
     *       采样点手写而非交给高斯偏移，理由同 {@link #introShockRing}。</li>
     * </ul>
     */
    public static void chargedOrb(AgaitolosEntity boss) {
        if (!(boss.level() instanceof ServerLevel level)) {
            return;
        }
        if (boss.tickCount % CHARGE_ORB_INTERVAL_TICKS != 0) {
            return;
        }
        Vec3 orb = orbPosition(boss);
        level.sendParticles(ParticleTypes.PORTAL, orb.x, orb.y, orb.z, CHARGE_ORB_COUNT,
                CHARGE_ORB_SPREAD, CHARGE_ORB_SPREAD, CHARGE_ORB_SPREAD, CHARGE_ORB_SPEED);
        level.sendParticles(ParticleTypes.DRAGON_BREATH, orb.x, orb.y - CHARGE_ORB_CONVERGE_DROP, orb.z,
                CHARGE_ORB_CONVERGE_COUNT, CHARGE_ORB_CONVERGE_SPREAD, CHARGE_ORB_CONVERGE_SPREAD,
                CHARGE_ORB_CONVERGE_SPREAD, CHARGE_ORB_SPEED);
        // 强化段另按更稀的间隔发射：与本体同频会让球心过曝、也会把每 tick 的包量抬上去
        if (boss.tickCount % CHARGE_UPGRADE_INTERVAL_TICKS != 0) {
            return;
        }
        level.sendParticles(ParticleTypes.END_ROD, orb.x, orb.y, orb.z, CHARGE_CORE_COUNT,
                CHARGE_CORE_SPREAD, CHARGE_CORE_SPREAD, CHARGE_CORE_SPREAD, CHARGE_ORB_SPEED);
        double ringY = orb.y - CHARGE_RING_DROP;
        for (int i = 0; i < CHARGE_RING_POINTS; ++i) {
            double angle = (Math.PI * 2.0D) * i / CHARGE_RING_POINTS;
            level.sendParticles(ParticleTypes.DRAGON_BREATH,
                    orb.x + Math.cos(angle) * CHARGE_RING_RADIUS, ringY,
                    orb.z + Math.sin(angle) * CHARGE_RING_RADIUS,
                    CHARGE_RING_COUNT, CHARGE_RING_SPREAD, CHARGE_RING_SPREAD, CHARGE_RING_SPREAD,
                    CHARGE_ORB_SPEED);
        }
    }

    /**
     * 出场爆发（首次进入复活阶段那一瞬间调一次）。
     * <p>
     * 存在的意义是让"快速回复血量"这件事<b>被看见</b>：首次进复活前血量被压到 25%
     * （{@code AgaitolosEntity#INTRO_HEALTH_RATIO}），随后 80 tick 回满 —— 这段过程若没有任何表现，
     * 玩家只会觉得"血怎么自己涨了"。爆发粒子充当那次压血/回血的视觉锚点。
     */
    public static void introBurst(AgaitolosEntity boss) {
        burst(boss, INTRO_BURST_COUNT, INTRO_BURST_SPREAD, INTRO_BURST_SPEED);
    }

    /**
     * 出场 ① 段（0~20t）的粒子柱：BOSS 周身的青蓝光焰，由 {@code AgaitolosEntity#tickIntro} 每 tick 调用。
     * <p>
     * <b>为什么还是 SOUL_FIRE_FLAME</b>：与 {@link #introBurst} / {@link #deathBurst} 同一选型
     * （青蓝焰点 = BOSS 识别色），不另开一套粒子把识别色割裂成两种。
     * <p>
     * <b>"升起"从哪来（已核对 1.20.1 源码，不是推测）</b>：{@code SOUL_FIRE_FLAME} 的粒子类是
     * {@code FlameParticle}（继承 {@code RisingParticle}），而 {@code RisingParticle} 的构造只设
     * {@code friction = 0.96} 与初速，<b>没有任何 gravity 赋值</b>（{@code Particle#gravity} 默认 0）
     * ⇒ 它自身既不下沉也不上升，只会随机飘移。故"升起"不能指望粒子物理，只能靠
     * <b>逐帧抬高发射高度</b>（{@code progress * }{@link #INTRO_PILLAR_RISE}）加多层位置差造出柱体；
     * 把 {@link #INTRO_PILLAR_RISE} 调成 0 会退化成"原地一团闪烁"。
     * <p>
     * 与 {@link #chargedOrb} 一样按 {@code 年龄 % 间隔} 节流，本类因此仍是无状态工具类。
     *
     * @param introAgeTicks 出场已演出的 tick 数（0 起，由实体传入）；不在 ① 段内直接空转
     */
    public static void introPillar(AgaitolosEntity boss, int introAgeTicks) {
        if (!(boss.level() instanceof ServerLevel level)) {
            return;
        }
        if (introAgeTicks < 0 || introAgeTicks >= AgaitolosEntity.INTRO_PILLAR_END_TICKS
                || introAgeTicks % INTRO_PILLAR_INTERVAL_TICKS != 0) {
            return;
        }
        double progress = introAgeTicks / (double) AgaitolosEntity.INTRO_PILLAR_END_TICKS;
        double rise = progress * INTRO_PILLAR_RISE;
        for (int layer = 0; layer < INTRO_PILLAR_LAYERS; ++layer) {
            double layerY = boss.getY() + layer * INTRO_PILLAR_LAYER_HEIGHT + rise;
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, boss.getX(), layerY, boss.getZ(),
                    INTRO_PILLAR_COUNT, INTRO_PILLAR_RADIUS, INTRO_PILLAR_SPREAD_Y, INTRO_PILLAR_RADIUS,
                    INTRO_PILLAR_SPEED);
            // 亮芯：同样逐层抬高（与柱体同一 rise），只加白亮星不改柱的解构 —— 强化段刻意只做"加亮"，
            // 不动层数与半径：那两个是柱体形状的来源，改了就是换设计而不是强化
            level.sendParticles(ParticleTypes.END_ROD, boss.getX(), layerY, boss.getZ(), INTRO_PILLAR_CORE_COUNT,
                    INTRO_PILLAR_CORE_SPREAD, INTRO_PILLAR_CORE_SPREAD, INTRO_PILLAR_CORE_SPREAD,
                    INTRO_PILLAR_SPEED);
        }
    }

    /**
     * 出场 ③ 段（40~60t）的落地冲击：一圈向外扩散的青蓝粒子环 + 地面尘，由 {@code AgaitolosEntity#tickIntro} 每 tick 调用。
     * <p>
     * <b>为什么要手写 8 个采样点</b>：{@code ServerLevel#sendParticles} 的偏移量是<b>高斯</b>偏移，
     * 无论怎么给参都只会得到一个中心最密的球/盘，造不出中空的"环"⇒ 只能沿圆周取点、逐点发射
     * （{@link #INTRO_SHOCK_POINTS} 个点 × 1 帧）。点数与节流因此是<b>性能参数</b>，不纯是观感参数：
     * 每点一次 {@code sendParticles} 就是一轮"给追踪到该位置的每个玩家发一个粒子包"。
     * <p>
     * <b>环的高度</b>取"BOSS 脚部 - {@link AgaitolosMoveControl#HOVER_HEIGHT}"：降临结束时 BOSS 恰好落在
     * 悬停高度上（见 {@code AgaitolosEntity#tickIntro}），故此式即脚下方块顶面 + {@link #INTRO_SHOCK_GROUND_LIFT}，
     * 复用悬停高度常量而不是再扫一遍地面。
     * <p>
     * 地面尘选 {@code CLOUD}：它是原版唯一的"白灰软雾"粒子（低速度时读作扬尘），青蓝与白灰一冷一暖，
     * 环是能量、尘是被震起的地面。
     *
     * @param introAgeTicks 出场已演出的 tick 数（0 起，由实体传入）；不在 ③ 段内直接空转
     */
    public static void introShockRing(AgaitolosEntity boss, int introAgeTicks) {
        if (!(boss.level() instanceof ServerLevel level)) {
            return;
        }
        if (introAgeTicks < AgaitolosEntity.INTRO_DESCENT_TICKS
                || introAgeTicks >= AgaitolosEntity.INTRO_SHOCK_END_TICKS
                || introAgeTicks % INTRO_SHOCK_INTERVAL_TICKS != 0) {
            return;
        }
        // 环半径随 ③ 段进度线性外扩；分母为段长常量，恒 > 0
        double progress = (introAgeTicks - AgaitolosEntity.INTRO_DESCENT_TICKS)
                / (double) (AgaitolosEntity.INTRO_SHOCK_END_TICKS - AgaitolosEntity.INTRO_DESCENT_TICKS);
        double radius = INTRO_SHOCK_MAX_RADIUS * progress;
        double groundY = boss.getY() - AgaitolosMoveControl.HOVER_HEIGHT + INTRO_SHOCK_GROUND_LIFT;
        for (int i = 0; i < INTRO_SHOCK_POINTS; ++i) {
            double angle = (Math.PI * 2.0D) * i / INTRO_SHOCK_POINTS;
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    boss.getX() + Math.cos(angle) * radius, groundY, boss.getZ() + Math.sin(angle) * radius,
                    INTRO_SHOCK_RING_COUNT, INTRO_SHOCK_RING_SPREAD, INTRO_SHOCK_RING_SPREAD,
                    INTRO_SHOCK_RING_SPREAD, INTRO_SHOCK_RING_SPEED);
        }
        level.sendParticles(ParticleTypes.CLOUD, boss.getX(), groundY, boss.getZ(),
                INTRO_SHOCK_DUST_COUNT, radius, INTRO_SHOCK_DUST_SPREAD_Y, radius, INTRO_SHOCK_DUST_SPEED);
    }

    /**
     * 死亡爆发（{@code die()} 里调一次）。
     * <p>数量与扩散刻意远大于出场（160 / 3.0 对 60 / 1.4），让"这一战结束了"有明确的量级差。
     */
    public static void deathBurst(AgaitolosEntity boss) {
        burst(boss, DEATH_BURST_COUNT, DEATH_BURST_SPREAD, DEATH_BURST_SPEED);
    }

    /**
     * 爆发粒子的共用实现。
     * <p>
     * <b>为什么选 SOUL_FIRE_FLAME</b>：青蓝焰点，与 BOSS 的识别色 #4FE3D0 同族，
     * 死亡/出场的"灵魂外泄"观感直接成型；用普通 FLAME 会变成橙红，与识别色撞车。
     * <p>
     * 位置取碰撞箱躯干中部（身高 × {@link #BURST_HEIGHT_RATIO}）而不是脚底：
     * 粒子若从脚底炸开会像"脚下冒泡"，从躯干炸开才像"体内迸出来"。
     */
    private static void burst(AgaitolosEntity boss, int count, double spread, double speed) {
        if (!(boss.level() instanceof ServerLevel level)) {
            return;
        }
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, boss.getX(),
                boss.getY() + boss.getBbHeight() * BURST_HEIGHT_RATIO, boss.getZ(),
                count, spread, spread, spread, speed);
    }

    /**
     * 蓄力光球的世界坐标（近似）。
     * <p>
     * <b>为什么是近似</b>：粒子由服务端下发，而服务端<b>拿不到 GeckoLib 的骨骼坐标</b>
     * （骨骼只在客户端渲染阶段存在）。故只能按"碰撞箱 + 视线朝向"推算：
     * 高度取身高比例 0.95（略高于头顶，像举在头前的手上），再沿水平朝向前移 0.45 格，
     * <b>竖直方向不随俯仰移动</b> —— 若跟着视线上下跑，俯冲时球会甩到脚下或头顶，反而更假。
     * <p>
     * 因此模型比例若与"身高 2.2 格"不符，光球会偏进头里或偏到空处，
     * 需要实机微调 {@link #CHARGE_ORB_HEIGHT_RATIO} 与 {@link #CHARGE_ORB_FORWARD}。
     */
    private static Vec3 orbPosition(AgaitolosEntity boss) {
        Vec3 look = boss.getLookAngle();
        return new Vec3(
                boss.getX() + look.x * CHARGE_ORB_FORWARD,
                boss.getY() + boss.getBbHeight() * CHARGE_ORB_HEIGHT_RATIO,
                boss.getZ() + look.z * CHARGE_ORB_FORWARD);
    }
}
