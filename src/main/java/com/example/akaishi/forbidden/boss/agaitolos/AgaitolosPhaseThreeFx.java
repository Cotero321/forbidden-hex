package com.example.akaishi.forbidden.boss.agaitolos;

import com.example.akaishi.forbidden.boss.agaitolos.skill.AgaitolosCalamitySkill;
import com.example.akaishi.forbidden.boss.agaitolos.skill.AgaitolosTripleThrowSkill;
import com.example.akaishi.effect.ScreenFlashS2C;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 阿盖托洛丝<b>阶段三五招</b>的专属粒子表现层：三重投掷的三连扇面、饱和轰炸的下落拖尾与落地冲击、
 * 投技①的镰扫出刀与钉住压迫、投技②的碎骨砸击、天魔＊灾的不可名状氛围与改写落地。
 * <p>
 * <b>为什么不继续堆进 {@link AgaitolosActionFx}</b>（选择本类作为承载处的理由）：
 * <ol>
 *   <li><b>体量</b>：既有两类实为 455 / 304 行，本批是 8 个入口 + 约 35 个新常量（本类 476 行），
 *       堆进 {@code AgaitolosActionFx} 会把它推到约 700 行，越过 RULES §8 的 500 行线；</li>
 *   <li><b>触发源不同</b>：{@code AgaitolosActionFx} 是"挥刀 / 格挡 / 受击 / 死亡"这类<b>全阶段通用</b>动作，
 *       本类是"阶段三五招"这一组专属节拍（三连 6/12/18t、投弹每 20t、出刀 10t、劈击 12t、改写 10t），
 *       常量与调用点都只服务那五招，独立成类后"改哪一招翻哪一个文件"。</li>
 * </ol>
 * <b>与既有两个类完全一致的四条口径</b>（照抄，不另立一套）：
 * <ol>
 *   <li><b>只在服务端发射</b>：每个入口都用 {@code boss.level() instanceof ServerLevel} 兜底
 *       （少了它客户端调用会在强转处抛 {@code ClassCastException}），服务端 {@code sendParticles}
 *       自动逐玩家下发，不需要自写同步；</li>
 *   <li><b>无状态</b>：节流一律 {@code boss.tickCount % 间隔}，本类不持任何计时字段
 *       ⇒ 重进存档 / 区块卸载重建都不会出现"计时器没复位"；</li>
 *   <li><b>识别色守恒</b>：青蓝族（{@code SOUL_FIRE_FLAME} / {@code SOUL} / {@code SWEEP_ATTACK}）、
 *       紫族（{@code PORTAL} / {@code DRAGON_BREATH}）、白亮星 {@code END_ROD}、地面尘 {@code CLOUD}，
 *       不引入橙红（{@code FLAME} / {@code CRIT} 之类一律不用）；</li>
 *   <li><b>不新增 ParticleType</b>：原版粒子够用，注册新粒子要配套资源与语言键，属负收益。</li>
 * </ol>
 * <p>
 * <b>性能口径（点数与间隔为什么这么保守）</b>：{@code ServerLevel#sendParticles} <b>每调用一次</b>
 * 就向追踪范围内每个玩家下发一个 {@code ClientboundLevelParticlesPacket} ⇒ 真正的成本是
 * 「调用次数 × 玩家数」，与 {@code count} 参数无关（{@code count} 只决定客户端画几个）。
 * 故环状效果一律手写采样点并把点数压在 8，持续类一律 {@code % 间隔} 节流；
 * 最坏单 tick 调用数见各方法注记：本类最坏是 {@link #calamityAura} 的 9 次/帧（平均 2.25 次/tick），
 * 仍低于既有"恶怨倒转"蓄力态（{@code AgaitolosFx#chargedOrb} 在 {@code tickCount % 4 == 0} 那一帧
 * 本体 2 次 + 强化段 9 次 = 11 次）。
 * <p>
 * 位置精度与既有两类同一局限：服务端拿不到 GeckoLib 骨骼坐标，一律按碰撞箱 + 视线朝向近似。
 * 所有数值均为<b>待调手感值</b>（P8 转配置项）。
 */
public final class AgaitolosPhaseThreeFx {

    /** 方向退化阈值（平方）：手与目标几乎重合时不画方向性拖尾（口径同 {@code AgaitolosActionFx#HORIZONTAL_EPSILON_SQR}） */
    private static final double DIRECTION_EPSILON_SQR = 1.0E-6D;

    // ---------------------------------------------------------------- 三重投掷（起手后 6 / 12 / 18t）

    /** 每拍拖尾采样点数：3 —— 三拍同点数（扇面感靠偏角不同表达，不让第三发比第一发贵三倍）。待调手感值 */
    public static final int TRIPLE_THROW_TRAIL_POINTS = 3;

    /** 拖尾点间距（格）：0.7（3 点约 2.1 格 = "刚离手的那一段"）。待调手感值 */
    public static final double TRIPLE_THROW_TRAIL_STEP = 0.7D;

    /** 掌心前兆每簇粒子数：2（紫雾一点，读作"这一拍要出手"）。待调手感值 */
    public static final int TRIPLE_THROW_GATHER_COUNT = 2;

    /** 前兆与拖尾的统一扩散（格）：0.08（点要小才读得出方向）。待调手感值 */
    public static final double TRIPLE_THROW_SPREAD = 0.08D;

    /** 前兆与拖尾的初速：0.03（近乎原地，方向感交给取点位置）。待调手感值 */
    public static final double TRIPLE_THROW_SPEED = 0.03D;

    // ---------------------------------------------------------------- 饱和轰炸（10t 首弹，之后每 20t）

    /** 每发离手线的采样点数：4。待调手感值 */
    public static final int BOMBARD_TRAIL_POINTS = 4;

    /**
     * 离手线点间距（格）：1.5（4 点约 6 格）。
     * <p><b>为什么是"一条即显的线"而不是逐帧拖尾</b>：6s 里有 6 发弹体同时在空中，
     * 逐帧跟踪它们等于每 tick 至多 6 次调用（6s ≈ 720 次），而这条线一次 4 次调用就能给出同样的"从天而降"读数。
     */
    public static final double BOMBARD_TRAIL_STEP = 1.5D;

    /** 掌心前兆每簇粒子数：3（比三重投掷重一档：这一发更大）。待调手感值 */
    public static final int BOMBARD_GATHER_COUNT = 3;

    /** 离手线的扩散（格）：0.12。待调手感值 */
    public static final double BOMBARD_SPREAD = 0.12D;

    /** 离手线的初速：0.04。待调手感值 */
    public static final double BOMBARD_SPEED = 0.04D;

    /** 落地冲击环采样点数：8。待调手感值 */
    public static final int BOMBARD_IMPACT_POINTS = 8;

    /** 落地冲击环半径（格）：1.9（比镰扫环略小：单发弹体，不是横扫）。待调手感值 */
    public static final double BOMBARD_IMPACT_RADIUS = 1.9D;

    /** 落地冲击环每点粒子数：1。待调手感值 */
    public static final int BOMBARD_IMPACT_COUNT = 1;

    /** 落地环点的扩散（格）：0.22（保持"点"，连成环而不是糊成盘）。待调手感值 */
    public static final double BOMBARD_IMPACT_SPREAD = 0.22D;

    /** 落地环粒子的初速：0.05（略外扩，读作冲击波）。待调手感值 */
    public static final double BOMBARD_IMPACT_SPEED = 0.05D;

    /** 落地尘的粒子数：8。待调手感值 */
    public static final int BOMBARD_IMPACT_DUST_COUNT = 8;

    /** 落地尘的竖直扩散（格）：0.35。待调手感值 */
    public static final double BOMBARD_IMPACT_DUST_SPREAD_Y = 0.35D;

    /** 落地环的抬升（格）：0.1（贴着命中位置稍上，不埋进地里）。待调手感值 */
    public static final double BOMBARD_IMPACT_LIFT = 0.1D;

    // ---------------------------------------------------------------- 投技①（踩住 + 镰扫，出刀在第 10t）

    /** 出刀弧采样点数：9（口径同普攻斩击弧）。待调手感值 */
    public static final int GRAB_SWEEP_ARC_POINTS = 9;

    /** 出刀弧总角度（度）：180（与"扫倒"的横向语义同宽）。待调手感值 */
    public static final double GRAB_SWEEP_ARC_DEGREES = 180.0D;

    /** 出刀弧半径（格）：2.2（被踩住的人就在脚下，刃痕贴地横扫）。待调手感值 */
    public static final double GRAB_SWEEP_ARC_RADIUS = 2.2D;

    /** 出刀弧高度 = 身高 × 该比例：0.45（低扫 —— 目标是脚边的人，不是胸口）。待调手感值 */
    public static final double GRAB_SWEEP_ARC_HEIGHT_RATIO = 0.45D;

    /** 出刀扬尘粒子数：10（贴地一圈）。待调手感值 */
    public static final int GRAB_SWEEP_DUST_COUNT = 10;

    /** 出刀扬尘半径（格）：1.6。待调手感值 */
    public static final double GRAB_SWEEP_DUST_RADIUS = 1.6D;

    /** 出刀扬尘的竖直扩散（格）：0.3。待调手感值 */
    public static final double GRAB_SWEEP_DUST_SPREAD_Y = 0.3D;

    /** 出刀扬尘的离地抬升（格）：0.1（不埋进地里）。待调手感值 */
    public static final double GRAB_SWEEP_GROUND_LIFT = 0.1D;

    /** 出刀扬尘的初速：0.03（几乎只就地散开，避免被吹成一团飘云）。待调手感值 */
    public static final double GRAB_SWEEP_DUST_SPEED = 0.03D;

    /**
     * 钉住期压迫感的发射间隔（tick）：4 —— 22t 的锁定只出约 5 帧。
     * <p>**刻意不做逐 tick**：钉住是"逐 tick 覆写玩家位置"的强控，若表现也逐 tick 刷，
     * 玩家在被压制的 1.1s 里会被自己的屏幕边缘粒子糊住，读作"卡"而不是"被压住"。
     */
    public static final int GRAB_HOLD_INTERVAL_TICKS = 4;

    /** 钉住期每帧的紫雾粒子数：2（悬在被钉者上方，读作威压）。待调手感值 */
    public static final int GRAB_HOLD_PRESSURE_COUNT = 2;

    /** 钉住期紫雾相对被钉者躯干的抬升（格）：0.8（从上方压下来，而不是糊在脸上）。待调手感值 */
    public static final double GRAB_HOLD_PRESSURE_RISE = 0.8D;

    /** 钉住期表现的高度基准 = 被钉者身高 × 该比例：0.5（躯干中部）。待调手感值 */
    public static final double GRAB_HOLD_HEIGHT_RATIO = 0.5D;

    /** 钉住期紫雾扩散（格）：0.3。待调手感值 */
    public static final double GRAB_HOLD_SPREAD = 0.3D;

    /** 钉住期脚下扬尘粒子数：1（每帧一缕，表明人还在地面上挣扎）。待调手感值 */
    public static final int GRAB_HOLD_DUST_COUNT = 1;

    // ---------------------------------------------------------------- 投技②（特写 + 劈击，命中在第 12t）

    /** 骨屑（灰白软雾）粒子数：10。待调手感值 */
    public static final int SMASH_CHIP_COUNT = 10;

    /** 魂点（SOUL）粒子数：6。待调手感值 */
    public static final int SMASH_SOUL_COUNT = 6;

    /** 碎屑的统一扩散（格）：0.5（裹住被劈中的躯体）。待调手感值 */
    public static final double SMASH_SPREAD = 0.5D;

    /** 碎屑的高度 = 目标身高 × 该比例：0.5（躯干中部）。待调手感值 */
    public static final double SMASH_HEIGHT_RATIO = 0.5D;

    /** 碎屑初速：0.07。待调手感值 */
    public static final double SMASH_SPEED = 0.07D;

    /** 碎地环采样点数：8。待调手感值 */
    public static final int SMASH_RING_POINTS = 8;

    /** 碎地环每点粒子数：1。待调手感值 */
    public static final int SMASH_RING_COUNT = 1;

    /** 碎地环半径（格）：1.7。待调手感值 */
    public static final double SMASH_RING_RADIUS = 1.7D;

    /** 碎地环点扩散（格）：0.2。待调手感值 */
    public static final double SMASH_RING_SPREAD = 0.2D;

    /** 碎地环初速：0.06（外扩读作地面被砸裂）。待调手感值 */
    public static final double SMASH_RING_SPEED = 0.06D;

    /** 碎地尘粒子数：8。待调手感值 */
    public static final int SMASH_DUST_COUNT = 8;

    /** 碎地尘竖直扩散（格）：0.35。待调手感值 */
    public static final double SMASH_DUST_SPREAD_Y = 0.35D;

    /** 碎地环的离地抬升（格）：0.1。待调手感值 */
    public static final double SMASH_GROUND_LIFT = 0.1D;

    // ---------------------------------------------------------------- 天魔＊灾（施法 44t，改写在第 10t）

    /** 不可名状氛围的发射间隔（tick）：4 —— 44t 约 11 帧。待调手感值 */
    public static final int CALAMITY_AURA_INTERVAL_TICKS = 4;

    /** 氛围环采样点数：8。待调手感值 */
    public static final int CALAMITY_AURA_POINTS = 8;

    /** 氛围环每点粒子数：1。待调手感值 */
    public static final int CALAMITY_AURA_RING_COUNT = 1;

    /** 氛围环的最大半径（格）：5.0（随施法进度外扩到这么大，"大范围"的落点）。待调手感值 */
    public static final double CALAMITY_AURA_MAX_RADIUS = 5.0D;

    /** 氛围环的最小绘制半径（格）：0.6（小于它就不画 —— 8 个点挤在一点纯属浪费）。待调手感值 */
    public static final double CALAMITY_AURA_MIN_RADIUS = 0.6D;

    /** 氛围环高度 = 身高 × 该比例：0.4（躯干下半，环从腰际铺出去）。待调手感值 */
    public static final double CALAMITY_AURA_HEIGHT_RATIO = 0.4D;

    /** 氛围环随进度整体抬升（格）：1.0（外扩 + 微升，读作"领域在张开"）。待调手感值 */
    public static final double CALAMITY_AURA_RISE = 1.0D;

    /** 氛围环点扩散（格）：0.2。待调手感值 */
    public static final double CALAMITY_AURA_SPREAD = 0.2D;

    /** 氛围环初速：0.03（近乎原地）。待调手感值 */
    public static final double CALAMITY_AURA_SPEED = 0.03D;

    /** 躯干中心紫涡粒子数：2（环心要有"源"，否则只是一圈雾）。待调手感值 */
    public static final int CALAMITY_AURA_CORE_COUNT = 2;

    /** 改写落地紫雾粒子数：12。待调手感值 */
    public static final int CALAMITY_APPLY_BREATH_COUNT = 12;

    /** 改写落地紫涡粒子数：8。待调手感值 */
    public static final int CALAMITY_APPLY_PORTAL_COUNT = 8;

    /** 改写落地白亮星点数：2（纯紫在昏暗场景里会被压住，加一颗冷白作亮度锚点）。待调手感值 */
    public static final int CALAMITY_APPLY_CORE_COUNT = 2;

    /** 改写落地扩散（格）：0.5。待调手感值 */
    public static final double CALAMITY_APPLY_SPREAD = 0.5D;

    /** 改写落地初速：0.08。待调手感值 */
    public static final double CALAMITY_APPLY_SPEED = 0.08D;

    /** 改写落地的高度 = 受污染者身高 × 该比例：0.5（躯干中部）。待调手感值 */
    public static final double CALAMITY_APPLY_HEIGHT_RATIO = 0.5D;

    /** 屏幕表现的持续时长（tick）：40 = 2s。待调手感值 */
    public static final int CALAMITY_FLASH_TICKS = 40;

    /**
     * 屏幕表现的峰值强度：0.45。
     * <p>刻意低于侵蚀跑满用的 1.0：那一条是"持续伤害的警告"，这里只是"被污染的那一瞬间"的读数，
     * 压到 0.45 才不至于把战斗画面糊住。
     */
    public static final float CALAMITY_FLASH_INTENSITY = 0.45F;

    private AgaitolosPhaseThreeFx() {
    }

    // ---------------------------------------------------------------- 语义入口（五招）

    /**
     * 三重投掷的一拍（起手后 6 / 12 / 18t，与 {@code AgaitolosTripleThrowSkill#BEAT_TICKS} 逐字对齐）：
     * 掌心一簇紫雾（前兆）+ 沿该拍扇面方向的一段青蓝焰点（离手）。
     * <p>
     * <b>三发的"节拍差异"靠偏角而不是靠点数</b>：偏角直接读技能类的 {@link AgaitolosTripleThrowSkill#FAN_YAW_DEGREES}
     * （-14 / 0 / +14），与弹体的真实落点<b>同源</b> —— 特效与弹道不会分叉；
     * 若改成"每拍多点几个粒子"，第三发会比第一发贵三倍，而玩家感知到的差别远小于成本。
     * <p>挂在 {@code AgaitolosPhaseThreeState#tickTripleThrow} 的出手分支（{@code fireBeat} 之后）：
     * 那里是"这一发真的打出去了"的唯一语义点，粒子只做既成事实的注脚。
     * <p><b>最坏单 tick：4 次调用</b>（前兆 1 + 拖尾 3），三拍合计 12 次 / 40t。
     *
     * @param beatIndex 第几拍（0/1/2）；越界或目标已死则空转 —— 与 {@code fireBeat} 的守卫同款，
     *                  保证不会出现"有特效没弹体"
     */
    public static void tripleThrowBeat(AgaitolosEntity boss, LivingEntity target, int beatIndex) {
        if (!(boss.level() instanceof ServerLevel level)) {
            return;
        }
        if (beatIndex < 0 || beatIndex >= AgaitolosTripleThrowSkill.BEAT_TICKS.length
                || target == null || !target.isAlive()) {
            return;
        }
        Vec3 hand = AgaitolosActionFx.handPosition(boss);
        level.sendParticles(ParticleTypes.DRAGON_BREATH, hand.x, hand.y, hand.z, TRIPLE_THROW_GATHER_COUNT,
                TRIPLE_THROW_SPREAD, TRIPLE_THROW_SPREAD, TRIPLE_THROW_SPREAD, TRIPLE_THROW_SPEED);
        Vec3 direction = aimDirection(hand, target, AgaitolosTripleThrowSkill.FAN_YAW_DEGREES[beatIndex]);
        if (direction == null) {
            return;
        }
        emitLine(level, hand, direction, TRIPLE_THROW_TRAIL_POINTS, TRIPLE_THROW_TRAIL_STEP,
                TRIPLE_THROW_SPREAD, TRIPLE_THROW_SPEED, ParticleTypes.SOUL_FIRE_FLAME);
    }

    /**
     * 饱和轰炸的一发（起手后 10t，之后每 20t，与 {@code AgaitolosBombardSkill#FIRST_SHOT_TICKS/BEAT_TICKS} 对齐）：
     * 掌心紫雾 + 沿弹道方向的一条下落线。
     * <p>这条线是<b>离手即显</b>的近似拖尾（不是逐帧跟踪弹体），理由见 {@link #BOMBARD_TRAIL_STEP}；
     * 真正的"落地冲击"由弹体侧在命中那一刻补（{@link #bombardImpact}），两段合起来才是完整的"高空投弹"。
     * <p>挂在 {@code AgaitolosPhaseThreeState#tickBombard} 的投弹分支（{@code fire} 之后）。
     * <p><b>最坏单 tick：5 次调用</b>（前兆 1 + 线 4）；本招每 20t 才有一帧，平均 0.25 次/tick
     * —— 循环招式里最省的一个。
     */
    public static void bombardRelease(AgaitolosEntity boss, LivingEntity target) {
        if (!(boss.level() instanceof ServerLevel level)) {
            return;
        }
        if (target == null || !target.isAlive()) {
            return;
        }
        Vec3 hand = AgaitolosActionFx.handPosition(boss);
        level.sendParticles(ParticleTypes.DRAGON_BREATH, hand.x, hand.y, hand.z, BOMBARD_GATHER_COUNT,
                BOMBARD_SPREAD, BOMBARD_SPREAD, BOMBARD_SPREAD, BOMBARD_SPEED);
        Vec3 direction = aimDirection(hand, target, 0.0F);
        if (direction == null) {
            return;
        }
        emitLine(level, hand, direction, BOMBARD_TRAIL_POINTS, BOMBARD_TRAIL_STEP,
                BOMBARD_SPREAD, BOMBARD_SPEED, ParticleTypes.SOUL_FIRE_FLAME);
    }

    /**
     * 饱和轰炸的<b>落地冲击</b>（由弹体 {@code AgaitolosWitherSkull#onHitEntity} 在命中那一刻调用，
     * 仅对标记为"高空投弹"的那一族生效）：命中点一圈青蓝环 + 一圈地面尘。
     * <p><b>为什么冲击落在弹体侧而不是状态机侧</b>：投弹与落地之间隔着约 1s 的飞行
     * （6 格高 + 目标常有 10 格以上距离），状态机那一拍根本不知道弹体何时、在哪里落地；
     * 硬要在状态机里按"距离 ÷ 初速"估时估点，在目标走位后必然错位 —— 而弹体命中处就是唯一真源。
     * <p>顺带说明：本 BOSS 的凋零头<b>刻意不生成爆炸</b>（规格要求不破坏地形，见 {@code AgaitolosWitherSkull#onHit}），
     * 因此原版那套"爆炸声 + 爆炸粒子"在本项目里根本不存在，落地表现只能自己给。
     * <p><b>最坏单 tick：9 次调用</b>（环 8 + 尘 1），且只在命中那一 tick。
     *
     * @param at 命中点（弹体自身坐标，就是"落地"的位置）
     */
    public static void bombardImpact(ServerLevel level, Vec3 at) {
        if (level == null || at == null) {
            return;
        }
        double y = at.y + BOMBARD_IMPACT_LIFT;
        emitRing(level, at.x, y, at.z, BOMBARD_IMPACT_RADIUS, BOMBARD_IMPACT_POINTS, BOMBARD_IMPACT_COUNT,
                BOMBARD_IMPACT_SPREAD, BOMBARD_IMPACT_SPEED, ParticleTypes.SOUL_FIRE_FLAME);
        level.sendParticles(ParticleTypes.CLOUD, at.x, y, at.z, BOMBARD_IMPACT_DUST_COUNT,
                BOMBARD_IMPACT_RADIUS, BOMBARD_IMPACT_DUST_SPREAD_Y, BOMBARD_IMPACT_RADIUS, BOMBARD_IMPACT_SPEED);
    }

    /**
     * 投技①的<b>出刀瞬间</b>（起手后 10t = clip 的 0.5s，与 {@code AgaitolosGrabSkill#HOLD_DAMAGE_TICK} 对齐）：
     * 低扫的镰刀弧（青蓝采样弧 + 原版 {@code SWEEP_ATTACK}）+ 贴地扬尘；<b>真的打中了</b>才额外补一圈冲击环。
     * <p><b>为什么空挥也出刀光、但不出冲击</b>：与俯冲镰扫同一取舍（{@code AgaitolosActionFx#diveSweepImpact}）——
     * 被盾牌挡下或目标恰好躲开时，刀是挥出去了（音效照播），但"打到人"的冲击不该出现，
     * 玩家据此能分辨"这一刀落没落空"。故 {@code struck} 只喂给表现层，结算与否仍唯一由技能类决定。
     * <p>挂在 {@code AgaitolosPhaseThreeState#tickGrab} 的出刀分支（{@code sweepStrike} 之后）。
     * <p><b>最坏单 tick：11 次调用</b>（弧 9 + SWEEP_ATTACK 1 + 尘 1），整招只有这一帧。
     *
     * @param struck 这一刀是否真的结算了伤害（{@code AgaitolosGrabSkill#sweepStrike} 的返回值）
     */
    public static void grabSweepStrike(AgaitolosEntity boss, LivingEntity target, boolean struck) {
        if (!(boss.level() instanceof ServerLevel level)) {
            return;
        }
        AgaitolosActionFx.emitArc(level, boss, AgaitolosActionFx.facingYaw(boss), GRAB_SWEEP_ARC_DEGREES,
                GRAB_SWEEP_ARC_RADIUS, GRAB_SWEEP_ARC_HEIGHT_RATIO, 0.0D, GRAB_SWEEP_ARC_POINTS,
                ParticleTypes.SOUL_FIRE_FLAME, 1);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, boss.getX(),
                boss.getY() + boss.getBbHeight() * GRAB_SWEEP_ARC_HEIGHT_RATIO, boss.getZ(),
                1, 0.0D, 0.0D, 0.0D, 0.0D);
        if (!struck || target == null) {
            return;
        }
        double groundY = boss.getY() + GRAB_SWEEP_GROUND_LIFT;
        level.sendParticles(ParticleTypes.CLOUD, target.getX(), groundY, target.getZ(), GRAB_SWEEP_DUST_COUNT,
                GRAB_SWEEP_DUST_RADIUS, GRAB_SWEEP_DUST_SPREAD_Y, GRAB_SWEEP_DUST_RADIUS, GRAB_SWEEP_DUST_SPEED);
    }

    /**
     * 投技①的<b>钉住期压迫感</b>（每 tick 由 {@code tickGrab} 调用，内部 {@code % 4} 节流）：
     * 被踩住的人上方一簇紫雾（威压）+ 脚下一缕扬尘（还在地面上挣扎）。
     * <p><b>为什么必须节流</b>：钉住本身是逐 tick 覆写玩家位置的强控，若粒子也逐 tick 刷，
     * 玩家会在一屏幕粒子中央被搬来搬去，读作"卡顿"而不是"被压住"；22t 只出约 5 帧正好够读出"一直被踩着"。
     * <p><b>最坏单 tick：2 次调用</b>（且仅在 {@code tickCount % 4 == 0} 的那一帧），整招约 10 次调用。
     */
    public static void grabHoldPressure(AgaitolosEntity boss, LivingEntity target) {
        if (!(boss.level() instanceof ServerLevel level)) {
            return;
        }
        if (target == null || !target.isAlive() || boss.tickCount % GRAB_HOLD_INTERVAL_TICKS != 0) {
            return;
        }
        level.sendParticles(ParticleTypes.DRAGON_BREATH, target.getX(),
                target.getY() + target.getBbHeight() * GRAB_HOLD_HEIGHT_RATIO + GRAB_HOLD_PRESSURE_RISE, target.getZ(),
                GRAB_HOLD_PRESSURE_COUNT, GRAB_HOLD_SPREAD, GRAB_HOLD_SPREAD, GRAB_HOLD_SPREAD,
                CALAMITY_AURA_SPEED);
        level.sendParticles(ParticleTypes.CLOUD, target.getX(), target.getY() + GRAB_SWEEP_GROUND_LIFT,
                target.getZ(), GRAB_HOLD_DUST_COUNT, GRAB_HOLD_SPREAD, GRAB_SWEEP_DUST_SPREAD_Y,
                GRAB_HOLD_SPREAD, CALAMITY_AURA_SPEED);
    }

    /**
     * 投技②的<b>劈击命中</b>（起手后 12t = clip 的 0.6s，与 {@code AgaitolosGrabSkill#SMASH_DAMAGE_TICK} 对齐）：
     * 被劈中者躯干迸出灰白碎屑 + 魂点，脚下炸开一圈碎地环与尘。
     * <p><b>为什么用 CLOUD 做"骨屑"</b>：原版没有骨头/碎块粒子，识别色族里唯一"灰白碎屑"读感的是
     * {@code CLOUD}（软雾，低速度时读作粉尘），配 {@code SOUL} 的魂点即"骨头被打碎了"；
     * 若实机要求更像骨渣，需要一枚自定义粒子（本轮未做，如实记录）。
     * <p>挂在 {@code tickSmash} 的结算分支（且<b>仅在真的打中时</b>：{@code smashStrike} 返回 false 时不放重击特效）。
     * <p><b>最坏单 tick：11 次调用</b>（屑 1 + 魂 1 + 环 8 + 尘 1），整招只有这一帧。
     */
    public static void grabSmashStrike(AgaitolosEntity boss, LivingEntity target) {
        if (!(boss.level() instanceof ServerLevel level)) {
            return;
        }
        if (target == null) {
            return;
        }
        double torsoY = target.getY() + target.getBbHeight() * SMASH_HEIGHT_RATIO;
        level.sendParticles(ParticleTypes.CLOUD, target.getX(), torsoY, target.getZ(), SMASH_CHIP_COUNT,
                SMASH_SPREAD, SMASH_SPREAD, SMASH_SPREAD, SMASH_SPEED);
        level.sendParticles(ParticleTypes.SOUL, target.getX(), torsoY, target.getZ(), SMASH_SOUL_COUNT,
                SMASH_SPREAD, SMASH_SPREAD, SMASH_SPREAD, SMASH_SPEED);
        double groundY = target.getY() + SMASH_GROUND_LIFT;
        emitRing(level, target.getX(), groundY, target.getZ(), SMASH_RING_RADIUS, SMASH_RING_POINTS,
                SMASH_RING_COUNT, SMASH_RING_SPREAD, SMASH_RING_SPEED, ParticleTypes.SOUL_FIRE_FLAME);
        level.sendParticles(ParticleTypes.CLOUD, target.getX(), groundY, target.getZ(), SMASH_DUST_COUNT,
                SMASH_RING_RADIUS, SMASH_DUST_SPREAD_Y, SMASH_RING_RADIUS, SMASH_RING_SPEED);
    }

    /**
     * 天魔＊灾的<b>施法氛围</b>（整段 44t 每 tick 由 {@code tickCalamity} 调用，内部 {@code % 4} 节流）：
     * 一圈随进度外扩的紫雾 + 躯干中心的紫色涡核。
     * <p><b>"大范围"怎么读出来</b>：环半径从 0 线性长到 {@link #CALAMITY_AURA_MAX_RADIUS}（5 格），
     * 同时整体微升 —— 读作"领域正在张开"；因为环由 8 个手写采样点连成（{@code sendParticles} 的偏移是高斯分布，
     * 造不出中空环，理由同 {@code AgaitolosFx#introShockRing}）。
     * <p><b>最坏单 tick：9 次调用</b>（环 8 + 核 1），全程 44t 约 11 帧 ≈ 99 次调用（平均 2.25 次/tick）
     * —— 仍低于既有蓄力态（{@code AgaitolosFx#chargedOrb} 在 {@code tickCount % 4 == 0} 那一帧共 11 次调用）。
     *
     * @param elapsedTicks 本次施法已进行的 tick 数（1 起，由 {@code tickCalamity} 传入）；用于推进环半径
     */
    public static void calamityAura(AgaitolosEntity boss, int elapsedTicks) {
        if (!(boss.level() instanceof ServerLevel level)) {
            return;
        }
        if (boss.tickCount % CALAMITY_AURA_INTERVAL_TICKS != 0) {
            return;
        }
        // 分母为施法时长常量，恒 > 0
        double progress = Math.min(1.0D, Math.max(0, elapsedTicks) / (double) AgaitolosCalamitySkill.CAST_TICKS);
        double radius = CALAMITY_AURA_MAX_RADIUS * progress;
        double y = boss.getY() + boss.getBbHeight() * CALAMITY_AURA_HEIGHT_RATIO + progress * CALAMITY_AURA_RISE;
        if (radius >= CALAMITY_AURA_MIN_RADIUS) {
            emitRing(level, boss.getX(), y, boss.getZ(), radius, CALAMITY_AURA_POINTS, CALAMITY_AURA_RING_COUNT,
                    CALAMITY_AURA_SPREAD, CALAMITY_AURA_SPEED, ParticleTypes.DRAGON_BREATH);
        }
        level.sendParticles(ParticleTypes.PORTAL, boss.getX(), y, boss.getZ(), CALAMITY_AURA_CORE_COUNT,
                CALAMITY_AURA_SPREAD * 2.0D, CALAMITY_AURA_SPREAD * 2.0D, CALAMITY_AURA_SPREAD * 2.0D,
                CALAMITY_AURA_SPEED);
    }

    /**
     * 天魔＊灾的<b>改写落地</b>（起手后 10t = clip 的 0.5s，与 {@code AgaitolosCalamitySkill#APPLY_TICK} 对齐）：
     * 受污染者身上炸开紫雾 + 紫涡 + 两颗白亮星，并推一次<b>客户端屏幕表现</b>。
     * <p>
     * <b>复用的是既有 {@code ScreenFlashS2C}，不是新包</b>：那条链路（common 的
     * {@code ScreenFlashS2C#sendToPlayer} → 客户端 {@code ScreenFlashClient} 包络 →
     * forge 的 {@code AkaishiErosionFlashOverlay}）本来就在跑（侵蚀跑满 / 巨坛吸取在用），
     * 语义也正好同族 —— 那个 overlay 的噪点贴图在代码注释里就写明是"与不可名状表现同源、染成血色"，
     * 即"不可名状"在项目里的既有视觉语言。故这里<b>零资源、零新包</b>：
     * <ol>
     *   <li><b>改谁</b>：只推给<b>被改写的那名玩家</b>（规格是"对玩家施加"，其他人不该跟着红屏）；</li>
     *   <li><b>多强</b>：{@link #CALAMITY_FLASH_INTENSITY} = 0.45，只有侵蚀跑满（1.0）的不到一半 ——
     *       那一条是持续伤害警告，这一条只是"被污染瞬间"的读数；</li>
     *   <li><b>多久</b>：{@link #CALAMITY_FLASH_TICKS} = 40t；且客户端包络本身对重复触发取较强的一次，
     *       多只 BOSS 连放不会叠成超长红屏。</li>
     * </ol>
     * 已知取舍（如实记录）：那层 overlay 的色相是写死的血色（{@code 0x8C0A12}），与本 BOSS 的青蓝识别色不同源；
     * 若要改成青蓝/紫，需给该 overlay 加一个色参（属既有表现体系改造，不在本轮改动面内）。
     * <p><b>最坏单 tick：3 次粒子调用 + 1 个包</b>（紫雾 1 + 紫涡 1 + 亮星 1 + 屏幕表现 1），整招只有这一帧。
     *
     * @param target 被成功改写的目标（{@code AgaitolosCalamitySkill#apply} 返回 true 时才会调到这里，
     *               故必为玩家；非玩家时静默跳过屏幕表现）
     */
    public static void calamityApplied(AgaitolosEntity boss, LivingEntity target) {
        if (!(boss.level() instanceof ServerLevel level)) {
            return;
        }
        if (target == null) {
            return;
        }
        double torsoY = target.getY() + target.getBbHeight() * CALAMITY_APPLY_HEIGHT_RATIO;
        level.sendParticles(ParticleTypes.DRAGON_BREATH, target.getX(), torsoY, target.getZ(),
                CALAMITY_APPLY_BREATH_COUNT, CALAMITY_APPLY_SPREAD, CALAMITY_APPLY_SPREAD,
                CALAMITY_APPLY_SPREAD, CALAMITY_APPLY_SPEED);
        level.sendParticles(ParticleTypes.PORTAL, target.getX(), torsoY, target.getZ(),
                CALAMITY_APPLY_PORTAL_COUNT, CALAMITY_APPLY_SPREAD, CALAMITY_APPLY_SPREAD,
                CALAMITY_APPLY_SPREAD, CALAMITY_APPLY_SPEED);
        level.sendParticles(ParticleTypes.END_ROD, target.getX(), torsoY, target.getZ(),
                CALAMITY_APPLY_CORE_COUNT, CALAMITY_APPLY_SPREAD, CALAMITY_APPLY_SPREAD,
                CALAMITY_APPLY_SPREAD, CALAMITY_APPLY_SPEED);
        if (target instanceof ServerPlayer player) {
            ScreenFlashS2C.sendToPlayer(player, CALAMITY_FLASH_TICKS, CALAMITY_FLASH_INTENSITY);
        }
    }

    // ---------------------------------------------------------------- 共用实现

    /**
     * 沿一条方向发射等距采样点（拖尾/线的共用实现）。
     * <p>{@code direction} 必须已归一化（由 {@link #aimDirection} 保证）。
     */
    private static void emitLine(ServerLevel level, Vec3 origin, Vec3 direction, int points, double step,
                                 double spread, double speed, ParticleOptions type) {
        for (int i = 1; i <= points; ++i) {
            double distance = i * step;
            level.sendParticles(type, origin.x + direction.x * distance, origin.y + direction.y * distance,
                    origin.z + direction.z * distance, 1, spread, spread, spread, speed);
        }
    }

    /**
     * 沿圆周发射等距采样点（环的共用实现）。
     * <p>手写取点的理由见 {@link AgaitolosPhaseThreeFx} 的类注释（高斯偏移造不出中空环）；
     * 点数因此是<b>性能参数</b>：每点一次就是一轮"给追踪到该位置的每个玩家发一个粒子包"。
     */
    private static void emitRing(ServerLevel level, double x, double y, double z, double radius, int points,
                                 int countPerPoint, double spread, double speed, ParticleOptions type) {
        for (int i = 0; i < points; ++i) {
            double angle = (Math.PI * 2.0D) * i / points;
            level.sendParticles(type, x + Math.cos(angle) * radius, y, z + Math.sin(angle) * radius,
                    countPerPoint, spread, spread, spread, speed);
        }
    }

    /**
     * 手 → 目标身体中部的<b>归一化</b>方向，并绕 Y 轴偏一个扇面角（度）。
     * <p>瞄准口径与 {@code AgaitolosSkullSkill#fireSpread} 逐字一致（手位也用同一组常量，
     * 见 {@link AgaitolosActionFx#handPosition}），否则特效会与弹道分叉。
     * <p>绕 Y 旋转的算式与 {@code AgaitolosSkullSkill#rotateY} 同源（那边是 private，两处都只有三行，
     * 复制比放宽可见性更省心）。
     *
     * @return 归一化方向；手与目标几乎重合（退化）时返回 {@code null}，由调用方跳过方向性拖尾
     */
    private static Vec3 aimDirection(Vec3 hand, LivingEntity target, float yawOffsetDegrees) {
        Vec3 aim = new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ()).subtract(hand);
        if (yawOffsetDegrees != 0.0F) {
            double radians = Math.toRadians(yawOffsetDegrees);
            double cos = Math.cos(radians);
            double sin = Math.sin(radians);
            aim = new Vec3(aim.x * cos - aim.z * sin, aim.y, aim.x * sin + aim.z * cos);
        }
        if (aim.lengthSqr() <= DIRECTION_EPSILON_SQR) {
            return null;
        }
        return aim.normalize();
    }
}
