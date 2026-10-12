package com.example.akaishi.forbidden.sanity.shadow;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 影怪的<b>受击瞬移</b>（"消失 → 在玩家周围另一个位置冒出"）。
 *
 * <p>校验口径照 {@code AgaitolosBlinkSkill#findLandingSpot} 的四项思路（本项目已验证过的那一套）：
 * ① 脚下有支撑面（{@code isFaceSturdy(UP)}）；② 落点两格高空间与影怪碰撞箱无碰撞（{@code noCollision}）；
 * ③ 脚部与头部不在流体里；④ 不在虚空（{@code minBuildHeight} 之上）。外加本条特有的一项：
 * ⑤ 与当前位置的水平位移不小于 {@link #MIN_MOVE} —— 否则"瞬移"会读成原地抖了一下。
 *
 * <p><b>找不到合法落点怎么办</b>：本轮<b>不传送</b>（原地留着，下一 tick 照常动作）。绝不做
 * "校验不过就硬传"的兜底 —— 那正是"卡进墙里 / 落进虚空"的来源（同 {@code AgaitolosBlinkSkill} 的取舍）。
 * 由于候选是按"围绕目标的角度"逐次重抽（{@link #ATTEMPTS} 次），实际极少连续失败。
 *
 * <p><b>为什么不做朝向插值</b>：瞬移是一次性事件，落位即面向目标；交给 {@code LookControl} 那套逐 tick
 * 插值反而会让它落地后先对着空气站两拍（同 {@code AgaitolosBlinkSkill#faceTarget} 的理由）。
 */
public final class ShadowBlink {

    /** 落点与目标玩家的水平距离下限（格）——待调手感值 */
    public static final double MIN_DISTANCE = 4.0D;
    /** 落点与目标玩家的水平距离上限（格）——待调手感值 */
    public static final double MAX_DISTANCE = 9.0D;
    /** 与<b>当前位置</b>的最小水平位移（格）：太近则看不出"换了个位置"——待调手感值 */
    public static final double MIN_MOVE = 2.5D;
    /** 候选重抽次数：全部不过校验就放弃本轮瞬移 */
    public static final int ATTEMPTS = 12;
    /** 垂直候选范围（格）：以目标脚部高度为基准，向上/向下各探这么多格 */
    private static final int VERTICAL_SPAN = 3;

    private ShadowBlink() {
    }

    /**
     * 执行一次瞬移（仅服务端）。
     *
     * @return 是否真的落位（false = 没找到合法落点，原地不动）
     */
    static boolean perform(ShadowEntity shadow, ServerPlayer target) {
        if (shadow.level().isClientSide()) {
            return false;
        }
        Vec3 from = shadow.position();
        Vec3 landing = findLandingSpot(shadow, target);
        if (landing == null) {
            return false;
        }
        // 原版传送入口（内部 = moveTo + teleportPassengers）；位置同步交给实体追踪器，不需要新增网络包
        shadow.teleportTo(landing.x, landing.y, landing.z);
        // 清掉瞬移前的残余速度，否则"落点"名不副实（下一步又被惯性带走）
        shadow.setDeltaMovement(Vec3.ZERO);
        shadow.faceTarget(target);
        if (shadow.level() instanceof ServerLevel serverLevel) {
            ShadowFx.blink(serverLevel, from, landing);
        }
        return true;
    }

    /** 围绕目标重抽候选点，返回第一处通过校验的落点；全部失败返回 {@code null} */
    private static Vec3 findLandingSpot(ShadowEntity shadow, ServerPlayer target) {
        Level level = shadow.level();
        for (int i = 0; i < ATTEMPTS; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            double distance = Mth.nextDouble(level.random, MIN_DISTANCE, MAX_DISTANCE);
            double x = target.getX() + Math.cos(angle) * distance;
            double z = target.getZ() + Math.sin(angle) * distance;
            double deltaX = x - shadow.getX();
            double deltaZ = z - shadow.getZ();
            if (deltaX * deltaX + deltaZ * deltaZ < MIN_MOVE * MIN_MOVE) {
                continue; // 与当前位置太近
            }
            Vec3 spot = resolveHeight(level, shadow, x, z, target.getY());
            if (spot != null) {
                return spot;
            }
        }
        return null;
    }

    /**
     * 在候选 (x,z) 上探高度：以目标脚部所在方块层为基准，自上而下取第一处合法高度。
     * <p>自上而下（先试高处）是有意的：影怪是悬停的虚影，出现在半空比贴地更符合观感，
     * 且高处更不容易刚好落在方块缝隙 / 一格高的洞里。
     */
    private static Vec3 resolveHeight(Level level, ShadowEntity shadow, double x, double z, double baseY) {
        int floorY = Mth.floor(baseY);
        for (int offset = VERTICAL_SPAN; offset >= -VERTICAL_SPAN; offset--) {
            double y = floorY + offset;
            if (y <= level.getMinBuildHeight() + 1) {
                continue; // ④ 虚空
            }
            BlockPos pos = BlockPos.containing(x, y, z);
            if (!level.hasChunkAt(pos)) {
                continue; // 未加载区块：不落（避免凭空加载远端区块）
            }
            // ③ 不在流体里：脚部与头部两格都查
            if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty()) {
                continue;
            }
            // ① 脚下方块可站立：用原版"支撑面"判据（与 isValidSpawn 同一口径）
            BlockPos below = pos.below();
            if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                continue;
            }
            // ② 不卡墙 / 不落进方块：直接用它自己的碰撞箱做一次原版碰撞查询
            AABB box = shadow.getType().getDimensions().makeBoundingBox(x, y, z);
            if (!level.noCollision(box)) {
                continue;
            }
            return new Vec3(x, y, z);
        }
        return null;
    }
}
