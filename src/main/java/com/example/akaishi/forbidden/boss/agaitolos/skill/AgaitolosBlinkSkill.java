package com.example.akaishi.forbidden.boss.agaitolos.skill;

import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosAnimations;
import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 二阶段技能「瞬击」的<b>位移面</b>（设计文档 §0 阶段二 / §1 定案表第 25 条，P5）。
 * <p>
 * 规格原文：「瞬击：如 BOSS 不处于飞行状态时便可以使用，瞬移到周边进行攻击」；
 * 定案表补充：「距离 &gt; N 或玩家拉开时才用；瞬移落点 = 玩家背后 2 格，落点校验（不卡墙）」。
 * <p>
 * <b>设计意图 —— 这是一招"被惩罚期间的补偿手段"</b>：
 * 本 BOSS 常态低空悬停，唯一的"不飞行"窗口来自<b>玩家格挡成功俯冲镰扫</b>之后授予的 30s 禁飞
 * （{@code AgaitolosEntity#isGrounded()}，见 {@code onSweepBlocked}）。禁飞期间 BOSS 被重力拉在地面，
 * 而目标玩家通常还在悬停高度之外跑动 —— 没有这一招，惩罚期里的 BOSS 就是个近不了身的木桩。
 * 瞬击正是"用一次短距离绕后瞬移把距离重新拉回近战范围"的手段，让格挡的惩罚保持有意义、
 * 又不至于让 BOSS 在 30s 里彻底失去威胁。
 * <p>
 * <b>本类只负责"怎么瞬"与"能不能瞬"</b>：起手闸（状态互斥 / 阶段门 / 封印 / 是否禁飞 / 冷却）
 * 一律由 {@code AgaitolosEntity#startBlink} 持有（节拍与冷却由 {@code AgaitolosSkillDirector} 仲裁），
 * 与 {@code tickDiveState} 的分工一致 ——
 * 技能不自持计时、不碰实体状态字段。
 * <p>
 * <b>瞬击本身不结算伤害</b>：规格与定案表都只写"瞬移 + 落点校验"，没有给伤害数字；
 * 落点选在目标背后 2 格（已在近战可达范围内）⇒ 落地后既有的 {@code MeleeAttackGoal} 普攻
 * 与二阶段的「高速踢击」自然接手，符合"瞬移到周边进行攻击"的语义。
 * 不硬造一个规格外的伤害值，是本节最刻意的克制。
 */
public final class AgaitolosBlinkSkill {

    // ---------------------------------------------------------------- 手感常量（待调手感值 / P8 转配置项）

    /**
     * 起手最小水平距离（格）：8.0。
     * <p>
     * 取值的两重依据：① 规格"玩家拉开时才用"—— 贴脸再闪没有意义，只会把贴身战搅成瞬移乱斗；
     * ② <b>≥ 8 格才能拿到原版"真瞬移"的表现</b>：{@code ServerEntity#sendChanges} 只在
     * {@code VecDeltaCodec} 编码后的位移超出 short 范围（≈ 每轴 8 格）时才发
     * {@code ClientboundTeleportEntityPacket}（客户端 {@code lerpTo(..., teleport=true)} 直接落位）；
     * 不足 8 格时走普通 {@code ClientboundMoveEntityPacket.Pos}，客户端会按 3 tick 插值过去，
     * 观感是"高速冲刺"而不是"瞬移"。取 8.0 让绝大多数瞬击落在"真瞬移"分支上（无需新增网络包）。
     */
    public static final double MIN_DISTANCE = 8.0D;

    /** 起手最大水平距离（格）：24.0 —— 再远就不是规格里的"瞬移到<b>周边</b>"，而是跨场位移，留给飞行巡航。待调手感值 / P8 转配置项 */
    public static final double MAX_DISTANCE = 24.0D;

    /** 落点：沿目标<b>朝向反方向</b>的水平距离（格）——规格"瞬移落点 = 玩家背后 2 格"。待调手感值 / P8 转配置项 */
    public static final double BEHIND_DISTANCE = 2.0D;

    /** 瞬击冷却（tick）：60 = 3s（定案表"短冷却"；对比俯冲镰扫 10s / 恶怨倒转 15s）。待调手感值 / P8 转配置项 */
    public static final int BLINK_COOLDOWN_TICKS = 60;

    /**
     * 落点校验失败后的<b>重试窗口</b>（tick）：10 = 0.5s。
     * <p>失败时不给完整冷却（否则"目标恰好在半空 → 白吃 3s 冷却"这种惩罚性扣冷却说不通），
     * 但也不能 0 冷却 —— 那会变成每 tick 重扫一遍落点方块。
     */
    public static final int BLINK_FAILED_RETRY_TICKS = 10;

    /** 水平方向的退化阈值（平方）：小于此值视为"目标视线几乎垂直"，朝向水平分量无从谈起 */
    private static final double HORIZONTAL_EPSILON_SQR = 1.0E-6D;

    private AgaitolosBlinkSkill() {
    }

    /**
     * 与目标的<b>水平</b>距离是否落在 [{@link #MIN_DISTANCE}, {@link #MAX_DISTANCE}]。
     * <p>用水平距离而非 3D 距离：瞬击的落点本身就是"水平绕后"，垂直差由落点校验单独处理
     * （沿用俯冲镰扫起手判定的口径）。
     */
    public static boolean canBlink(AgaitolosEntity boss, LivingEntity target) {
        double deltaX = target.getX() - boss.getX();
        double deltaZ = target.getZ() - boss.getZ();
        double distanceSqr = deltaX * deltaX + deltaZ * deltaZ;
        return distanceSqr >= MIN_DISTANCE * MIN_DISTANCE && distanceSqr <= MAX_DISTANCE * MAX_DISTANCE;
    }

    /**
     * 执行一次瞬击：算落点 → 校验 → 瞬移 → 面向目标（仅服务端权威）。
     * <p>
     * 校验失败一律<b>放弃本次瞬击</b>：不传送、不改朝向、不动速度（绝不做"校验不过就硬传"的兜底，
     * 那会把 BOSS 塞进墙里或虚空）。调用方据此只给一个短重试窗口。
     *
     * @return 是否真的瞬移成功（false = 落点校验没过，本次放弃）
     */
    public static boolean perform(AgaitolosEntity boss, LivingEntity target) {
        if (boss.level().isClientSide()) {
            // 仅服务端权威：客户端只消费原版实体同步（不新增网络包）
            return false;
        }
        Vec3 landing = findLandingSpot(boss, target);
        if (landing == null) {
            return false;
        }
        // 先掐断寻路，再落位：否则本 tick 已下发的导航目标点会让 BOSS 落地后立刻往回飘
        boss.getNavigation().stop();
        // 原版传送入口（内部 = moveTo + teleportPassengers）：位置同步交给实体追踪器，
        // 不需要我们发任何包 —— 位移 ≥ 8 格时原版会发 ClientboundTeleportEntityPacket（见 MIN_DISTANCE 的说明）
        boss.teleportTo(landing.x, landing.y, landing.z);
        faceTarget(boss, target);
        // 清掉瞬移前的残余速度：带着冲刺惯性落位会让"落点"名不副实
        boss.setDeltaMovement(Vec3.ZERO);
        // 纯表现：本招<b>无专属 clip</b>，按任务口径复用 attack_slash（同一触发键，落位瞬间挥一下）
        AgaitolosAnimations.playAttack(boss);
        return true;
    }

    /**
     * 求落点：沿目标朝向的反方向退 {@link #BEHIND_DISTANCE} 格，水平 2 格、垂直不额外偏移
     * （Y 取目标脚部所在方块层，理由见方法内注释），并做四项校验。
     *
     * @return 通过校验的落点；任一项校验失败返回 {@code null}（调用方放弃本次瞬击）
     */
    private static Vec3 findLandingSpot(AgaitolosEntity boss, LivingEntity target) {
        Level level = boss.level();

        // 目标朝向的水平分量。几乎垂直俯视/仰视时该分量退化 ⇒ 回退到 yaw 推出的水平朝向
        // （与 AgaitolosDiveSweepSkill#isWithinSweep 的兜底同款：yaw 恒有定义，避免"整整一招静默失效"）
        Vec3 look = target.getLookAngle();
        double lookHorizontalSqr = look.x * look.x + look.z * look.z;
        double behindX;
        double behindZ;
        if (lookHorizontalSqr < HORIZONTAL_EPSILON_SQR) {
            double yaw = Math.toRadians(target.getYRot());
            behindX = Math.sin(yaw);
            behindZ = -Math.cos(yaw);
        } else {
            double inverse = 1.0D / Math.sqrt(lookHorizontalSqr);
            // 取反 ⇒ "背后"：目标朝向的反方向
            behindX = -look.x * inverse;
            behindZ = -look.z * inverse;
        }
        double x = target.getX() + behindX * BEHIND_DISTANCE;
        double z = target.getZ() + behindZ * BEHIND_DISTANCE;

        // Y = 目标脚部所在方块层（floor）：规格的"背后 2 格"是<b>水平</b>绕后，本招不做垂直偏移。
        // 代价（诚实记录）：目标腾空（起跳/坠落）时脚下无支撑 ⇒ 校验必然失败 ⇒ 本 tick 放弃，
        // 着地后在重试窗口到期时自然补上；换来的好处是落点始终与目标同一地面层，不会闪现到头顶或脚下。
        double y = Mth.floor(target.getY());
        BlockPos pos = BlockPos.containing(x, y, z);

        // ③ 不在流体里：脚部与头部两格都查（BOSS 高 2.2，正好占满这两格）
        if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty()) {
            return null;
        }
        // ① 脚下方块可站立：用原版"支撑面"判据（isFaceSturdy + UP），与 isValidSpawn 同一口径
        BlockPos below = pos.below();
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
            return null;
        }
        // ②+④ 两格高空间无碰撞 / 不卡墙：直接用 BOSS 自己的碰撞箱做一次原版碰撞查询
        //      （方块碰撞 + 可碰撞实体），比逐格手写判据更贴合"它塞得进去吗"这个真问题
        AABB landingBox = boss.getType().getDimensions().makeBoundingBox(x, y, z);
        if (!level.noCollision(landingBox)) {
            return null;
        }
        return new Vec3(x, y, z);
    }

    /**
     * 落位后立刻面向目标（水平对准目标、俯仰对准其眼睛高度）。
     * <p>
     * 三个旋转都要显式写：{@code Entity#moveTo} 只落 {@code yRot}/{@code xRot}，
     * 而渲染的头部朝向读 {@code yHeadRot}（不写会看到"身子转了、头还在看原来的方向"）。
     * 刻意用直接赋值而不是 {@code LookControl#setLookAt}：那套是<b>逐 tick 插值</b>的平滑转向，
     * 对瞬移这种一次性事件要的是"落地即面向"，插值会让绕后瞬间对着空气站两拍。
     * <p>yaw 公式 = 原版惯例 {@code atan2(dz, dx)} 转角度后 -90°（MC 里 yaw 0 = 朝 +Z、-90 = 朝 +X）。
     */
    private static void faceTarget(AgaitolosEntity boss, LivingEntity target) {
        double deltaX = target.getX() - boss.getX();
        double deltaZ = target.getZ() - boss.getZ();
        float yaw = (float) (Mth.atan2(deltaZ, deltaX) * (180.0D / Math.PI)) - 90.0F;
        double horizontal = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        double deltaY = target.getEyeY() - boss.getEyeY();
        // xRot 正 = 低头（与原版一致），故取负号
        float pitch = (float) (-(Mth.atan2(deltaY, horizontal) * (180.0D / Math.PI)));
        boss.setYRot(yaw);
        boss.setXRot(pitch);
        boss.setYHeadRot(yaw);
    }
}
