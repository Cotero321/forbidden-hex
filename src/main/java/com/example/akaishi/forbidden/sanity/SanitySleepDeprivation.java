package com.example.akaishi.forbidden.sanity;

import com.example.akaishi.forbidden.api.sanity.SanityChangeSource;
import com.example.akaishi.config.ModConfig;
import com.example.akaishi.forbidden.sanity.shadow.ShadowCombat;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 睡眠相关的理智机制（P6）：<b>睡过整夜 +3</b> 与 <b>连续多日不睡的每日扣减 + 幻翼附带精神伤害</b>。
 *
 * <p><b>1) 睡过整夜 +3</b>：钩子事件是 {@code PlayerWakeUpEvent}（forge），它由原版
 * {@code Player#stopSleepInBed(boolean wakeImmediately, boolean updateLevelForSleep)} 开头发射
 * （见 Forge 1.20.1 的 {@code Player.java.patch}：{@code ForgeEventFactory.onPlayerWakeup(this, p_36226_, p_36227_)}）。
 * 判据取 {@code !event.wakeImmediately()}：该布尔为 true 的路径是"被打醒 / 被强制唤醒"
 * （{@code Player#hurt} 内的 {@code stopSleepInBed(true, true)}、床被破坏时的 {@code stopSleepInBed(true, false)}），
 * 而"睡到天亮"这条路径（{@code Player#tick} 内睡时检查通过后的 {@code stopSleepInBed(false, true)}、
 * 以及夜间跳过后的 {@code ServerLevel#wakeUpAllPlayers} → {@code stopSleepInBed(false, false)}）恒为 false。
 * 因此 {@code !wakeImmediately()} 就是"确实睡过整夜"的可用时机。
 *
 * <p><b>为什么不用 {@code SleepFinishedTimeEvent}</b>：那个事件是<b>维度级</b>的
 * （在 {@code ServerLevel} 里、且要求 {@code areEnoughSleeping && areEnoughDeepSleeping} 与
 * {@code doDaylightCycle=true} 才发），只管"这一夜被跳过"，不告诉是<b>哪名玩家</b>睡过；
 * 在"多人但没睡够比例"的服务器上它一次都不发，而睡着的玩家次日清晨仍会被原版正常唤醒 ——
 * 用维度事件做"每人 +3"会漏掉这一整类情况。{@code PlayerWakeUpEvent} 逐玩家、且与"是否真睡过夜"同源。
 *
 * <p><b>"周围没有怪物"</b>：以玩家为心 {@link #MONSTER_RADIUS} 格内扫描 {@link Mob}，
 * 只认实现了原版 {@link Enemy} 接口者（僵尸/骷髅/苦力怕/幻翼/史莱姆等"敌对意图"生物），
 * 中立生物（狼/蜜蜂/末影人）不算 —— 与项目"用 Enemy 接口判敌对"的既有口径一致。
 * 有怪物时<b>不给 +3，但睡眠仍然有效</b>（游标照常重置，剥夺计时停止）。
 *
 * <p><b>2) 连续 {@code N} 个游戏日不睡 ⇒ 每个游戏日 −{@code M} SAN</b>：
 * <ul>
 *   <li><b>"游戏日"口径</b>：取<b>主世界</b>的 {@code getDayTime() / 24000}（向下取整）。
 *       每个维度的 {@code dayTime} 各自独立计数，取"当前维度"会在跨维度（往返下界/末地）时出现
 *       回退或跳变，进而重复扣或漏扣；主世界时间同时也是"睡觉跳夜"作用的唯一对象，口径统一。
 *       因此每日结算只在主世界维度的 tick 上跑，但遍历的是<b>全服玩家</b>（人在下界也照常计入）。</li>
 *   <li><b>游标</b>：{@code last_sleep_day}（上次睡过整夜的游戏日）与 {@code punished_day}
 *       （已结算惩罚到的游戏日），两者都落盘（见 {@link SanityState}），故重登/重启既不会重复扣
 *       也不会漏扣。<b>离线跨越的日界不追罚</b>（每个"在线观测到的日界"最多扣一次），
 *       否则离线一周回来会被一次性清空理智。时间被人为回拨（{@code /time set}）时游标只前进不回退。</li>
 *   <li>未初始化（新玩家 / 老存档升级）时把两个游标对齐到当前游戏日：倒计时从"该玩家首次被观测"起算。</li>
 * </ul>
 *
 * <p><b>3) 剥夺状态下幻翼伤害附带精神伤害</b>：由平台侧处理器在 {@code LivingHurtEvent} 里识别
 * "幻翼的原版咬击"（源实体是 {@link Phantom} 且伤害类型<b>不是</b> {@code forbidden_hex:psychic}），
 * 转交 {@link #queuePhantomPsychic} 投递一段额外的 {@code forbidden_hex:psychic} 伤害。
 *
 * <p><b>为什么不直接在事件里追加一次 hurt</b>：{@code LivingEntity#hurt} 在真正结算<b>之前</b>
 * 就把 {@code invulnerableTime = 20}，所以从伤害事件内部再调 {@code hurt()} 会被无敌帧吞掉
 * （与 P4 注释里"psychic 不在 bypasses_cooldown ⇒ 嵌套 hurt 会被挡掉"同一条原版事实）。
 * 因此这里改成<b>待投递队列</b>：先记一笔，等玩家的 {@code invulnerableTime} 归零的那个 tick
 * 再投递；投递失败（仍未清空等原因）则保留重试，超过 {@link #HIT_TTL_TICKS} 或玩家离线则丢弃。
 *
 * <p><b>与 P4「0% 档幻翼自杀式俯冲」的去重</b>：P4 的撞击结算走的是
 * {@code player.hurt(psychic, IMPACT_DAMAGE)}——<b>伤害类型本身就是 psychic</b>，
 * 被上一条判据直接排除，因此不会触发本条附加；反之本条的附加也只是一次独立的 psychic 伤害，
 * 不改变 P4 的俯冲、不自毁幻翼。两者条件不同（0% 档 vs 剥夺状态）、可同时成立，但<b>互不重复结算</b>。
 *
 * <p>数值均为<b>待调手感值</b>。
 */
public final class SanitySleepDeprivation {

    // ===== 手感常量（待调手感值）=====

    /** 睡过整夜的理智奖励 */
    public static final float SLEEP_REWARD = 3.0f;
    /** "周围没有怪物"的判定半径（格） */
    public static final double MONSTER_RADIUS = 8.0D;
    /** 默认：连续多少个游戏日不睡开始惩罚 */
    public static final int DEFAULT_THRESHOLD_DAYS = 5;
    /** 默认：剥夺状态下每个游戏日扣多少 SAN */
    public static final float DEFAULT_DAILY_DEBIT = 10.0f;
    /** 幻翼咬击附带的精神伤害（独立一段 psychic，吃既有 COG 精神减免） */
    public static final float PHANTOM_PSYCHIC_DAMAGE = 2.0f;
    /** 待投递精神伤害的寿命（tick）：5s 内还没投出去就放弃（防条目长期驻留） */
    private static final int HIT_TTL_TICKS = 100;

    /** 玩家 → 待投递的幻翼附加精神伤害（仅服务端主线程写读；键为 UUID，值不持有玩家对象） */
    private static final Map<UUID, PendingHit> PENDING = new ConcurrentHashMap<>();

    private SanitySleepDeprivation() {
    }

    // ------------------------------------------------------------------
    // 结算入口
    // ------------------------------------------------------------------

    /** 服务端每个维度每 tick 调用（由 {@code AkaishiMod.init} 的 SERVER_LEVEL_POST 驱动） */
    public static void serverTick(ServerLevel level) {
        if (!ModConfig.sanityEnabled) {
            PENDING.clear(); // 总开关关闭：整套停摆（含尚未投递的附加伤害）
            return;
        }
        long now = level.getGameTime();
        // 附加伤害投递必须逐 tick 尝试（等无敌帧清空），故放在节拍门之前
        flushPending(level, now);
        if (now % SanityEnvironmentSettlement.SETTLE_PERIOD_TICKS != 0) {
            return;
        }
        // 游戏日只在主世界口径下推进（跨维度一致，见类注释）；但结算对象是全服玩家
        if (level.dimension() != Level.OVERWORLD) {
            return;
        }
        MinecraftServer server = level.getServer();
        long day = gameDay(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            settleDaily(player, day);
        }
    }

    /**
     * 睡醒（由 forge 的 {@code PlayerWakeUpEvent} 调用）。
     *
     * @param wakeImmediately true = 被打醒/被强制唤醒，不算"睡过整夜"（不奖励、不重置剥夺游标）
     */
    public static void onWakeUp(ServerPlayer player, boolean wakeImmediately) {
        if (player == null || !ModConfig.sanityEnabled || wakeImmediately) {
            return;
        }
        SanityState state = SanityServiceImpl.state(player);
        MinecraftServer server = player.getServer();
        if (state == null || server == null) {
            return;
        }
        long day = gameDay(server);
        // 睡过整夜：游标前进（剥夺计时归零；punished_day 同步前进，避免下一天补扣"本该睡的那天"）
        state.setLastSleepDay(day);
        state.setPunishedDay(day);
        if (monstersNearby(player)) {
            return; // 身边有敌人：不给奖励（睡眠本身仍然有效）
        }
        // 非扣减写入走写入漏斗（夹取/回调/阈值/同步统一处理）
        SanityServiceImpl.instance().addSanInternal(player, SLEEP_REWARD, SanityChangeSource.INTERNAL);
    }

    /** 记录一段"幻翼咬击附带的精神伤害"（由 forge 侧在伤害事件里调用；实际投递在下一个 tick） */
    public static void queuePhantomPsychic(ServerPlayer player, Phantom phantom) {
        if (player == null || phantom == null || !ModConfig.sanityEnabled) {
            return;
        }
        ServerLevel level = player.serverLevel();
        PENDING.put(player.getUUID(), new PendingHit(level.dimension(), phantom.getUUID(),
                level.getGameTime() + HIT_TTL_TICKS));
    }

    /** 该玩家此刻是否处于"睡眠剥夺"状态（供伤害附加、调试读取） */
    public static boolean isSleepDeprived(Player player) {
        if (player == null || player.level() == null || player.level().isClientSide || !ModConfig.sanityEnabled) {
            return false;
        }
        SanityState state = SanityServiceImpl.state(player);
        MinecraftServer server = player.getServer();
        if (state == null || server == null) {
            return false;
        }
        return isSleepDeprived(state, gameDay(server));
    }

    /** 游戏日序号：主世界 {@code dayTime / 24000}（向下取整；负数时间也能正确归日） */
    public static long gameDay(MinecraftServer server) {
        return server == null ? 0L : Math.floorDiv(server.overworld().getDayTime(), 24000L);
    }

    // ------------------------------------------------------------------
    // 每日结算
    // ------------------------------------------------------------------

    private static void settleDaily(ServerPlayer player, long day) {
        SanityState state = SanityServiceImpl.state(player);
        if (state == null) {
            return;
        }
        if (state.lastSleepDay() == SanityState.NO_DAY_YET) {
            // 首次观测（新玩家 / 老存档升级）：倒计时从此刻起算，不追溯补罚
            state.setLastSleepDay(day);
            state.setPunishedDay(day);
            return;
        }
        if (day <= state.punishedDay()) {
            return; // 同一天重复结算 / 时间被回拨：游标不动、不重复扣
        }
        boolean deprived = isSleepDeprived(state, day);
        state.setPunishedDay(day); // 无论是否扣减，本日界都已结算（离线跨日因此不会补扣）
        if (!deprived) {
            return;
        }
        // 扣减走 debitInternal：临时保护优先抵扣（剥夺惩罚与其它扣减同一条链）
        SanityServiceImpl.instance().debitInternal(player, dailyDebit(), SanityChangeSource.INTERNAL);
    }

    private static boolean isSleepDeprived(SanityState state, long day) {
        if (state == null || state.lastSleepDay() == SanityState.NO_DAY_YET) {
            return false; // 尚未初始化：不判剥夺
        }
        return day - state.lastSleepDay() >= thresholdDays();
    }

    /** 半径内是否有敌对生物（只认 {@link Enemy} 接口，中立生物不算） */
    private static boolean monstersNearby(ServerPlayer player) {
        return !player.level()
                .getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(MONSTER_RADIUS),
                        mob -> mob instanceof Enemy)
                .isEmpty();
    }

    // ------------------------------------------------------------------
    // 附加精神伤害投递
    // ------------------------------------------------------------------

    /**
     * 逐 tick 尝试投递。
     *
     * <p>只在玩家 {@code invulnerableTime == 0} 时投递：一是保证这段精神伤害<b>不被打折、
     * 也不被无敌帧整段吞掉</b>（原版 {@code hurt} 在 {@code invulnerableTime > 10} 时只补差额、
     * 差额不足则直接返回 false）；二是避免我们的追加伤害去抵消玩家本应吃到的其它伤害。
     */
    private static void flushPending(ServerLevel level, long now) {
        if (PENDING.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, PendingHit> entry : PENDING.entrySet()) {
            PendingHit hit = entry.getValue();
            if (!hit.dimension().equals(level.dimension())) {
                continue; // 别的维度的条目由那个维度自己的 tick 处理
            }
            UUID playerId = entry.getKey();
            if (!(level.getPlayerByUUID(playerId) instanceof ServerPlayer player)
                    || player.isDeadOrDying() || player.isCreative() || player.isSpectator()) {
                PENDING.remove(playerId);
                continue;
            }
            if (now > hit.expireTick()) {
                PENDING.remove(playerId); // 超时：放弃这一段（不跨"很久以后"补刀）
                continue;
            }
            if (player.invulnerableTime > 0) {
                continue; // 等无敌帧清空（下个 tick 再试）
            }
            if (!isSleepDeprived(player)) {
                PENDING.remove(playerId); // 中途睡觉了：不再附伤害
                continue;
            }
            Entity source = level.getEntity(hit.phantomId());
            DamageSource damage = source instanceof Phantom phantom
                    ? ShadowCombat.psychic(level, phantom, phantom)
                    : ShadowCombat.psychic(level, null, null); // 幻翼已消散：退化为"无归属的 psychic"
            if (player.hurt(damage, PHANTOM_PSYCHIC_DAMAGE)) {
                PENDING.remove(playerId);
            }
        }
    }

    /** 剥夺惩罚的游戏日阈值：外露配置优先，0 视为"用内置默认" */
    private static int thresholdDays() {
        return ModConfig.sanitySleepDeprivationDays > 0
                ? ModConfig.sanitySleepDeprivationDays
                : DEFAULT_THRESHOLD_DAYS;
    }

    /** 剥夺状态下每个游戏日的扣减量：外露配置优先，0 视为"用内置默认" */
    private static float dailyDebit() {
        return ModConfig.sanitySleepDeprivationDailyDebit > 0.0
                ? (float) ModConfig.sanitySleepDeprivationDailyDebit
                : DEFAULT_DAILY_DEBIT;
    }

    /** 一条待投递的附加精神伤害（不持有实体对象，只存 id 与到期刻） */
    private record PendingHit(ResourceKey<Level> dimension, UUID phantomId, long expireTick) {
    }
}
