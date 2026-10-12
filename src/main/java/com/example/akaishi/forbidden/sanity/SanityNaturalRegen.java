package com.example.akaishi.forbidden.sanity;

import com.example.akaishi.forbidden.api.sanity.SanityChangeSource;
import com.example.akaishi.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 理智<b>自然恢复</b>：在"安全的室内环境"里缓慢回理智（P6）。
 *
 * <p><b>生效条件（三条同时满足）</b>：
 * <ol>
 *   <li><b>有顶</b>：{@code !level.canSeeSky(脚部)}（头顶有遮挡）；</li>
 *   <li><b>在地面</b>：{@code player.onGround()}；</li>
 *   <li><b>附近有光源</b>：脚部<b>方块光</b> &gt; {@link #DEFAULT_LIGHT_MIN}（等效"点了火把"）。
 *       取方块光而非综合亮度，理由与既有环境规则一致：混入天空光会让"白天露天"被误判成亮处。</li>
 * </ol>
 *
 * <p><b>效率加成（乘在恢复速度上，与基础速率同一条乘链）</b>：
 * <pre>
 * 总效率 = COG 自然恢复倍率 × (1 + 花丛加成 + 饱食加成)
 * </pre>
 * 花丛加成与饱食加成各为 {@link #DEFAULT_FLOWER_BONUS}/{@link #DEFAULT_SATIATED_BONUS}（+10%），
 * <b>花丛只判一次</b>（数量超阈值即 +10%，不随朵数叠加）；COG 倍率来自
 * {@link SanityCogCurve#naturalRegenMultiplier(float)}（本类是该系数的<b>首个消费方</b>）。
 *
 * <p><b>为什么用进度累积器而不是"周期计数器"</b>：基数写"每 5 分钟 +1"，若用
 * "数满 periodTicks 就 +1"的整数周期，+10% 会被取整抹平（5 分钟 × 1.1 仍记 5 分钟），
 * 效率变成读不出来的死数据。累积器把"1 点 SAN 需要多少 tick"变成连续量：
 * 每 tick 累加 {@code 1 / (基础周期 / 总效率)}，攒满 1.0 就 +1 SAN 并减去 1.0。
 * 于是 +10% 体现为 300s → 272.7s 一点，×1.5 → 200s 一点，×2 → 150s 一点，可被实机读出。
 *
 * <p><b>精度与落盘</b>：累积器用 {@code double}（单精度在 0.0037/tick 量级的累加下
 * 会有可见的相对误差累积），并按<b>每个整点 SAN</b> 落盘（{@code SanityState#naturalRegenProgress}，
 * NBT 键 {@code nat_regen_progress}）。<b>选择落盘</b>的原因：进度是跨分钟级的连续量，
 * 不落盘时"每几分钟重登一次"的玩家会永远攒不满一点，等于被静默取消该机制；
 * 而一个 double 键的成本几乎为零，且与项目既有"只在有值时写入"的口径一致。
 *
 * <p><b>边界</b>：
 * <ul>
 *   <li>条件不满足时<b>进度保留</b>（不回零）：恢复是"在安逸环境里慢慢回"，起身拿个东西
 *       不该抹掉几分钟积累；否则玩家会为了不丢进度而不敢动，行为反而被机制牵着走。</li>
 *   <li>SAN 已到硬上限时<b>暂停累积</b>（不攒、不消耗）：避免"满了还在攒、攒满就被夹掉"的浪费。</li>
 *   <li>不受食补/战斗类惩罚影响（那些只管自己那条链）；{@code sanityEnabled=false} 时整体暂停。</li>
 * </ul>
 *
 * <p>数值均为<b>待调手感值</b>。
 */
public final class SanityNaturalRegen {

    // ===== 手感常量（待调手感值）=====

    /** 基础周期：每 5 分钟 +1 SAN */
    public static final int DEFAULT_PERIOD_TICKS = 5 * 60 * 20;
    /** 方块光下界（不含）：脚部方块光 &gt; 该值才算"附近有光源"（火把级光照） */
    public static final int DEFAULT_LIGHT_MIN = 10;
    /** 花朵数量阈值（不含）：周围朵数 &gt; 该值给花丛加成 */
    public static final int DEFAULT_FLOWER_THRESHOLD = 10;
    /** 花丛加成（不可叠加） */
    public static final float DEFAULT_FLOWER_BONUS = 0.10f;
    /** 饱食度满（20）加成 */
    public static final float DEFAULT_SATIATED_BONUS = 0.10f;
    /** 花朵扫描半径（水平，格）：半径 4 ⇒ 9×9 水平面 */
    public static final int FLOWER_SCAN_RADIUS = 4;
    /** 花朵扫描垂直范围（格）：±1 ⇒ 3 层，合计 9×3×9 = 243 次方块读取 */
    public static final int FLOWER_SCAN_DY = 1;
    /** 花朵扫描节流（tick）：5s 一次（一次扫描结果为该窗口内所有 tick 复用） */
    public static final int FLOWER_SCAN_INTERVAL_TICKS = 100;
    /** 花朵扫描缓存淘汰时长（tick）：超过此时长未刷新直接丢弃（防玩家下线后长期钉住维度引用） */
    private static final int FLOWER_CACHE_EVICT_TICKS = 200;

    /** 玩家 → 花朵计数缓存（仅服务端主线程写读；键为 UUID，不持有世界对象） */
    private static final Map<UUID, FlowerCache> FLOWER_COUNTS = new ConcurrentHashMap<>();

    private SanityNaturalRegen() {
    }

    /** 服务端每个维度每 tick 调用（由 {@code AkaishiMod.init} 的 SERVER_LEVEL_POST 驱动） */
    public static void serverTick(ServerLevel level) {
        if (!ModConfig.sanityEnabled) {
            return; // 总开关关闭：不累积（进度原样保留，重新打开可接续）
        }
        long now = level.getGameTime();
        if (now % SanityEnvironmentSettlement.SETTLE_PERIOD_TICKS != 0) {
            return; // 与既有结算同一 1s 节拍：本 tick 零成本
        }
        evictStale(now);
        for (ServerPlayer player : level.players()) {
            settle(player, now);
        }
    }

    /**
     * 单个玩家一步累积。
     *
     * <p>按"整拍一次、累加 1 拍的量"推进（而不是每 tick 一次）：结果与逐 tick 累加<b>逐位等价</b>
     * （同一乘链、同一顺序，只是把 20 次加法合并成 1 次乘 20），因此"+10% 不被取整抹掉"的承诺不变，
     * 却省掉 19/20 的玩家遍历成本。
     */
    private static void settle(ServerPlayer player, long now) {
        SanityState state = SanityServiceImpl.state(player);
        if (state == null) {
            return;
        }
        if (state.san() >= state.effectiveMax()) {
            return; // 已满：暂停累积（不攒不耗，避免攒满被夹掉）
        }
        if (!applies(player)) {
            return; // 条件不满足：进度保留（理由见类注释）
        }
        double period = periodTicks();
        double rate = SanityEnvironmentSettlement.SETTLE_PERIOD_TICKS * efficiency(player, now) / period;
        double progress = state.naturalRegenProgress() + rate;
        int gained = (int) progress;
        if (gained <= 0) {
            state.setNaturalRegenProgress(progress);
            return;
        }
        // 满 1.0 ⇒ +1 SAN（减掉已兑换的部分；累加限制保证这里最多 1 点）
        state.setNaturalRegenProgress(progress - gained);
        // 非扣减写入走 addSanInternal：夹取/回调/阈值/同步全由写入漏斗统一处理
        SanityServiceImpl.instance().addSanInternal(player, gained, SanityChangeSource.INTERNAL);
    }

    /** 三条生效条件（有顶 + 在地面 + 附近有光源） */
    private static boolean applies(ServerPlayer player) {
        if (!player.onGround()) {
            return false;
        }
        BlockPos pos = player.blockPosition();
        ServerLevel level = player.serverLevel();
        if (level.canSeeSky(pos)) {
            return false; // 见天 ⇒ 无顶
        }
        return level.getBrightness(LightLayer.BLOCK, pos) > DEFAULT_LIGHT_MIN;
    }

    /** 总效率倍率 = COG 自然恢复倍率 × (1 + 花丛 + 饱食) */
    private static double efficiency(ServerPlayer player, long now) {
        SanityState state = SanityServiceImpl.state(player);
        double cogMultiplier = state == null ? 1.0 : SanityCogCurve.naturalRegenMultiplier(state.cog());
        float bonus = 0f;
        if (flowerCount(player, now) > flowerThreshold()) {
            bonus += DEFAULT_FLOWER_BONUS;
        }
        if (player.getFoodData().getFoodLevel() >= 20) {
            bonus += DEFAULT_SATIATED_BONUS;
        }
        return cogMultiplier * (1.0 + bonus);
    }

    /**
     * 周围花朵计数（带节流）。
     *
     * <p><b>节流口径</b>：每玩家每 {@link #FLOWER_SCAN_INTERVAL_TICKS}（5s）最多扫一次，
     * 结果在窗口内复用；窗口外或跨维度、跨区块段立即重扫。由于"是否 &gt; 阈值"决定 +10%，
     * 5s 的判定粒度对"每 5 分钟 +1"的恢复速率完全不可感知。
     *
     * <p><b>扫描口径</b>：以脚部方块为中心的 9×3×9 盒（{@link #FLOWER_SCAN_RADIUS} /
     * {@link #FLOWER_SCAN_DY}），命中即计数，<b>一旦超过阈值就提前返回</b>——只需回答
     * "够不够 10 朵"，不必数完。花朵判据用原版方块标签 {@code minecraft:flowers}
     * （含小花/高花/开花的杜鹃等），不自行列举方块、不新增标签。
     */
    private static int flowerCount(ServerPlayer player, long now) {
        BlockPos pos = player.blockPosition();
        ServerLevel level = player.serverLevel();
        long section = SectionPos.asLong(pos);
        FlowerCache cached = FLOWER_COUNTS.get(player.getUUID());
        if (cached != null && cached.matches(level.dimension(), section, now)) {
            return cached.count();
        }
        int count = scanFlowers(level, pos);
        FLOWER_COUNTS.put(player.getUUID(), new FlowerCache(level.dimension(), section, now, count));
        return count;
    }

    /** 实际的方块扫描（提前退出：只需知道"是否超过阈值"，故最多数到阈值 +1） */
    private static int scanFlowers(ServerLevel level, BlockPos center) {
        int stopAt = flowerThreshold();
        int count = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -FLOWER_SCAN_RADIUS; dx <= FLOWER_SCAN_RADIUS; dx++) {
            for (int dz = -FLOWER_SCAN_RADIUS; dz <= FLOWER_SCAN_RADIUS; dz++) {
                for (int dy = -FLOWER_SCAN_DY; dy <= FLOWER_SCAN_DY; dy++) {
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (level.getBlockState(cursor).is(BlockTags.FLOWERS) && ++count > stopAt) {
                        return count;
                    }
                }
            }
        }
        return count;
    }

    /** 丢弃过期缓存（O(条目数)，无世界查询） */
    private static void evictStale(long now) {
        if (FLOWER_COUNTS.isEmpty()) {
            return;
        }
        FLOWER_COUNTS.entrySet().removeIf(entry -> now - entry.getValue().tick() > FLOWER_CACHE_EVICT_TICKS);
    }

    /** 基础周期（tick）：外露配置优先，0 视为"用内置默认" */
    private static int periodTicks() {
        return ModConfig.sanityNaturalRegenPeriodTicks > 0
                ? ModConfig.sanityNaturalRegenPeriodTicks
                : DEFAULT_PERIOD_TICKS;
    }

    /** 花朵数量阈值：外露配置优先，0 视为"用内置默认" */
    private static int flowerThreshold() {
        return ModConfig.sanityFlowerCountThreshold > 0
                ? ModConfig.sanityFlowerCountThreshold
                : DEFAULT_FLOWER_THRESHOLD;
    }

    /** 花朵计数缓存条目：维度 + 区块段 + 构建刻共同构成有效性判据 */
    private record FlowerCache(ResourceKey<Level> dimension, long section, long tick, int count) {

        boolean matches(ResourceKey<Level> current, long currentSection, long now) {
            return dimension == current && section == currentSection && now - tick <= FLOWER_SCAN_INTERVAL_TICKS;
        }
    }
}
