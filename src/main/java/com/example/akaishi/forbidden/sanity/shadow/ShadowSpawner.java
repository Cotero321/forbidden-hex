package com.example.akaishi.forbidden.sanity.shadow;

import com.example.akaishi.forbidden.entity.AkaishiForbiddenEntities;
import com.example.akaishi.forbidden.sanity.SanityPenalties;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 影怪的生成与清扫（P4 的"表现层"落点，由 {@code SanityPenaltySettlement#applyLowTierPenalties} 的 1s 节拍驱动）。
 *
 * <p><b>用户拍板的生成条件（逐条落地）</b>：
 * <ul>
 *   <li><b>任意环境 + 低 SAN，不限亮度</b>：本类不看光照、不看群系、不看 Y、不看是否露天；</li>
 *   <li><b>SAN 低于 40%</b>（基础百分比 ≤ 40，即档位 ≥ {@link SanityPenalties#TIER_40}）开始可能生成；</li>
 *   <li><b>20% 档更频繁且更强</b>：更短的掷点周期 + 更高的概率 + 更大的同时在场上限
 *       （更强由 {@code ShadowCombat} 按档位乘倍率实现）；</li>
 *   <li><b>回升到 40% 以上不再生成</b>：档位退回 40% 以上时调 {@link #dissipateAll}，
 *       存量影怪走同一条 vanish 演出自然消散（不给奖励）；</li>
 *   <li><b>不生成在被观察的视野正中央</b>：候选方向落在玩家水平视线正前方 ±60° 锥内一律丢弃，
 *       只从侧面与背后浮现（见 {@link #isInFrontCone}）。</li>
 * </ul>
 *
 * <p><b>为什么用 {@code gameTime % period} 而不是每玩家计时器</b>：与 {@code SanityPenaltySettlement} 的
 * 60 档周期减益同一手法 —— 投影到游戏刻上，就不存在"每玩家一个计时字段"的持有与清理问题
 * （不泄漏、不随玩家数增长），代价只是同一秒内多名玩家共用一次掷点窗口，对本机制无影响。
 *
 * <p>数值均为<b>待调手感值</b>。
 */
public final class ShadowSpawner {

    // ===== 手感常量（待调手感值）=====

    /** 40% 档掷点周期（tick）：400 = 20s 掷一次 */
    public static final int ROLL_PERIOD_40 = 400;
    /** 20% 档掷点周期（tick）：200 = 10s 掷一次（更频繁） */
    public static final int ROLL_PERIOD_20 = 200;
    /** 40% 档单次掷点命中概率 */
    public static final float CHANCE_40 = 0.35F;
    /** 20% 档单次掷点命中概率 */
    public static final float CHANCE_20 = 0.55F;
    /** 40% 档同时在场上限（按玩家计） */
    public static final int CAP_40 = 2;
    /** 20% 档同时在场上限（按玩家计） */
    public static final int CAP_20 = 4;

    /** 生成距离下限（格）：不能贴脸 */
    public static final double SPAWN_MIN_DISTANCE = 8.0D;
    /** 生成距离上限（格）：也不能远到看不见 */
    public static final double SPAWN_MAX_DISTANCE = 18.0D;
    /** 垂直候选范围（格）：以玩家高度为基准上下各探这么多格 */
    private static final int VERTICAL_SPAN = 3;

    /**
     * "怼脸锥"阈值：候选方向与玩家水平视线的夹角小于 arccos(0.5) = 60° 即视为正前方，丢弃。
     * <p>取 0.5 ⇒ 正前方 120° 被排除、侧面与背后的 240° 可用 —— "从边缘/背后浮现"才成立。
     */
    public static final double FRONT_CONE_COS = 0.5D;

    /** 候选重抽次数：全部不过校验就放弃本次生成 */
    public static final int ATTEMPTS = 12;

    /** 统计/清扫半径（格）：比 {@link ShadowEntity#DESPAWN_DISTANCE} 略大，保证"跟丢前一定被扫到" */
    public static final double SCAN_RANGE = 40.0D;

    private ShadowSpawner() {
    }

    /**
     * 1s 节拍：档位门控 + 上限 + 掷点 + 落点筛选。
     *
     * @param tier 玩家当前档位（由调用方给出，保证与其它档位惩罚读同一份判据）
     */
    public static void settle(ServerPlayer player, int tier) {
        ServerLevel level = player.serverLevel();
        List<ShadowEntity> alive = boundShadows(level, player);
        boolean deep = tier >= SanityPenalties.TIER_20;
        int cap = deep ? CAP_20 : CAP_40;
        if (alive.size() >= cap) {
            return; // 上限已满：不掷点（也就不用扫落点）
        }
        if (level.getGameTime() % (deep ? ROLL_PERIOD_20 : ROLL_PERIOD_40) != 0) {
            return;
        }
        if (player.getRandom().nextFloat() >= (deep ? CHANCE_20 : CHANCE_40)) {
            return;
        }
        spawnOne(player, level);
    }

    /** SAN 回升到 40% 以上：存量影怪全部自然消散（同一条 vanish 演出，不给奖励） */
    public static void dissipateAll(ServerPlayer player) {
        for (ShadowEntity shadow : boundShadows(player.serverLevel(), player)) {
            shadow.dissipate();
        }
    }

    /** 该玩家名下的在场影怪（按绑定关系配对，不会误扫到别人的） */
    private static List<ShadowEntity> boundShadows(ServerLevel level, ServerPlayer player) {
        return level.getEntitiesOfClass(ShadowEntity.class,
                player.getBoundingBox().inflate(SCAN_RANGE), shadow -> shadow.isBoundTo(player));
    }

    /** 抽一个落点并生成；找不到合法落点就放弃本轮（下一轮再试） */
    private static boolean spawnOne(ServerPlayer player, ServerLevel level) {
        for (int i = 0; i < ATTEMPTS; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            double distance = Mth.nextDouble(level.random, SPAWN_MIN_DISTANCE, SPAWN_MAX_DISTANCE);
            double x = player.getX() + Math.cos(angle) * distance;
            double z = player.getZ() + Math.sin(angle) * distance;
            if (isInFrontCone(player, x, z)) {
                continue; // 不怼脸
            }
            Vec3 spot = resolveSpot(level, x, z, player.getY());
            if (spot == null) {
                continue;
            }
            ShadowEntity shadow = new ShadowEntity(AkaishiForbiddenEntities.SHADOW.get(), level);
            shadow.setPos(spot.x, spot.y, spot.z);
            // 落点校验用实体自身碰撞箱（尺寸来自 EntityType），比手写判据更贴合"它塞得进去吗"
            if (!level.noCollision(shadow)) {
                continue;
            }
            shadow.bindTo(player);
            // 出现即面向玩家（否则会以默认朝向"背面"出现）
            shadow.faceTarget(player);
            level.addFreshEntity(shadow);
            return true;
        }
        return false;
    }

    /** 候选方向是否落在玩家水平视线的正前方锥内（视角几乎垂直时以 yaw 兜底，与 AgaitolosBlinkSkill 同款） */
    private static boolean isInFrontCone(ServerPlayer player, double x, double z) {
        double deltaX = x - player.getX();
        double deltaZ = z - player.getZ();
        double length = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        if (length < 1.0E-6D) {
            return true;
        }
        Vec3 look = player.getLookAngle();
        double lookLength = Math.sqrt(look.x * look.x + look.z * look.z);
        double lookX;
        double lookZ;
        if (lookLength < 1.0E-6D) {
            double yaw = Math.toRadians(player.getYRot());
            lookX = -Math.sin(yaw);
            lookZ = Math.cos(yaw);
        } else {
            lookX = look.x / lookLength;
            lookZ = look.z / lookLength;
        }
        return (deltaX * lookX + deltaZ * lookZ) / length > FRONT_CONE_COS;
    }

    /** 在候选 (x,z) 上探高度：以玩家高度为基准上下各 {@link #VERTICAL_SPAN} 格，取第一处无碰撞且不在流体中的位置 */
    private static Vec3 resolveSpot(ServerLevel level, double x, double z, double baseY) {
        int floorY = Mth.floor(baseY);
        for (int offset = VERTICAL_SPAN; offset >= -VERTICAL_SPAN; offset--) {
            double y = floorY + offset;
            if (y <= level.getMinBuildHeight() + 1) {
                continue; // 不生成在虚空
            }
            BlockPos pos = BlockPos.containing(x, y, z);
            if (!level.hasChunkAt(pos)) {
                continue; // 未加载区块：不生成（避免凭空加载远端区块）
            }
            if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty()) {
                continue; // 不生成在流体里
            }
            return new Vec3(x, y, z);
        }
        return null;
    }
}
