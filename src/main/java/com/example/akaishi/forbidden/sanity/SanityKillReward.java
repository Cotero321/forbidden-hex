package com.example.akaishi.forbidden.sanity;

import com.example.akaishi.forbidden.api.sanity.SanityChangeSource;
import com.example.akaishi.config.ModConfig;
import com.example.akaishi.forbidden.sanity.shadow.ShadowEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * 击杀恢复：击杀生物回 SAN，并减免一部分临时上限削减（阈值惩罚的"回血"出口）。
 *
 * <p><b>类别判据（只认"威胁"，不再给被动/中立生物奖励）</b>：
 * <ul>
 *   <li><b>敌对</b> = {@code victim instanceof Enemy} —— 原版"主动敌对生物"标记接口
 *       （{@code Monster} / {@code Slime} / {@code Piglin} / {@code Hoglin} 等实现它）。
 *       选它而不是 {@code MobCategory.MONSTER} 的理由：后者是<b>生成分类</b>，
 *       与"会不会主动攻击/是否算敌人"并非同一件事，而 {@code Enemy} 正是原版给"敌人"打的标记
 *       （例如敌对生物在和平难度下按 {@code Enemy} 退场）。</li>
 *   <li><b>影怪</b> = {@link #isShadowMob(LivingEntity)} —— {@code forbidden_hex:shadow} 实体。</li>
 *   <li><b>其余一律 0</b>（被动/中立生物、村民、盔甲架等）。</li>
 * </ul>
 *
 * <p><b>为什么取消"中立 +2"</b>：原口径 {@code victim instanceof Enemy ? 3 : 2} 会把鸡/牛/羊也算作
 * "中立"，于是<b>养一圈牲畜即可无限回理智、并把上限削减刷干净</b>（每只 +2 SAN 且账本 −5）。
 * 改为"只认威胁"后，被动生物不再是奖励来源，刷怪场也只剩"真敌怪"这一条（且被下面的每日上限兜住）。
 * 语义上也更站得住：缓解"精神污染"靠的是<b>清除威胁</b>，不是屠杀家畜。
 *
 * <p><b>每日上限（节流）</b>：击杀回 SAN 与账本减免<b>共享同一份额度</b>
 * （{@link #DAILY_SAN_CAP}，按游戏日重置，见 {@link #apply}）。额度用尽后连 {@code relieve} 也停发 ——
 * 否则"可无限刷的减免"仍会把上限削减抹平。额度落盘（见 {@code SanityState}），重登/重启不刷新；
 * 死亡<b>不</b>重置（否则自杀重登成了新刷法）。
 *
 * <p><b>为什么玩家击杀不算</b>：PVP 击杀若按"敌对 +3"处理会变成刷理智的手段，且语义上
 * "精神污染"不应靠杀人缓解 —— 故玩家一律不计。
 *
 * <p><b>"恢复 5 点临时 SANC"的落地口径</b>：临时上限的唯一权威是阈值削减账本
 * （{@code tempCut} 每秒被账本总量覆盖，直接 {@code addTempCut} 会被立刻冲掉），
 * 故实现为"把账本总量减 5"（从最深档开始扣，见 {@link SanityCutLedger#relieve(float)}）。
 * 账本已空时该部分自然无效果 —— 不会旁路写出一个马上被覆盖的假值。
 *
 * <p>所有数值均为<b>待调手感值</b>。
 */
public final class SanityKillReward {

    /** 击杀敌对生物：SAN +3（待调手感值） */
    public static final float SAN_HOSTILE = 3f;
    /** 击杀影怪：SAN +5（待调手感值） */
    public static final float SAN_SHADOW = 5f;
    /** 每次击杀减免的临时上限削减（"恢复 5 点临时 SANC"；待调手感值） */
    public static final float TEMP_SANC_RELIEF = 5f;
    /**
     * 每个游戏日可获得的击杀恢复 SAN 上限（待调手感值）。
     *
     * <p>额度用尽后 SAN 与账本减免<b>一并停发</b>（同一节流，见 {@link #apply}）：
     * 敌对 +3 ⇒ 约 10 只真敌怪打满；影怪 +5 ⇒ 约 6 只。被动/中立生物不计（见类注释）。
     */
    public static final float DAILY_SAN_CAP = 30f;

    private SanityKillReward() {
    }

    /**
     * 结算一次击杀（仅服务端；由 forge 的 {@code LivingDeathEvent} 挂点按"责任实体是玩家"归因后调用）。
     *
     * <p>节流口径：按<b>主世界游戏日</b>（与睡眠剥夺同一口径，见
     * {@code SanitySleepDeprivation#gameDay}）累计当日已发放量，达 {@link #DAILY_SAN_CAP} 即整段跳过
     * （SAN 不加、{@code relieve} 不做）。跨日自动重置；额度落盘，重登不刷新。
     *
     * @param killer 击杀者（伤害的责任实体；弓箭等投射物击杀也算到射手头上）
     * @param victim 被击杀的生物
     */
    public static void apply(ServerPlayer killer, LivingEntity victim) {
        if (killer == null || victim == null || killer.level().isClientSide || !ModConfig.sanityEnabled) {
            return;
        }
        SanityState state = SanityServiceImpl.state(killer);
        if (state == null) {
            return;
        }
        float san = sanFor(victim);
        if (san <= 0f) {
            return; // 不计的生物（被动/中立/玩家）：连账本减免也不给
        }
        MinecraftServer server = killer.getServer();
        if (server == null) {
            return;
        }
        // 每日额度：换日即重置（额度与"当日已发放量"一起变，故统一在此处判日）
        long day = SanitySleepDeprivation.gameDay(server);
        if (state.killRewardDay() != day) {
            state.setKillRewardDay(day);
            state.setKillRewardSanToday(0f);
        }
        if (state.killRewardSanToday() >= DAILY_SAN_CAP) {
            return; // 本日额度用尽：SAN 与账本减免一并停发（同一节流）
        }
        state.setKillRewardSanToday(state.killRewardSanToday() + san);
        SanityServiceImpl.instance().addSanInternal(killer, san, SanityChangeSource.INTERNAL);
        if (TEMP_SANC_RELIEF > 0f) {
            state.cutLedger().relieve(TEMP_SANC_RELIEF);
        }
        state.markDirty();
    }

    /** 该击杀给多少 SAN（0 = 不计：被动/中立/玩家） */
    public static float sanFor(LivingEntity victim) {
        if (victim == null || victim instanceof Player) {
            return 0f; // 玩家击杀不计（见类注释：PVP 刷理智）
        }
        if (isShadowMob(victim)) {
            return SAN_SHADOW;
        }
        return victim instanceof Enemy ? SAN_HOSTILE : 0f; // 中立档已取消（见类注释：家畜刷分）
    }

    /**
     * 是否影怪（{@code forbidden_hex:shadow}）。
     *
     * <p><b>为什么按实体类判而不是按 tag / 类别别</b>：影怪不是 {@code Mob}（见
     * {@code ShadowEntity} 的类注释：继承 {@code LivingEntity} 以绕开衰减区的转化与禁刷），
     * 因此既没有 {@code MobType} 也拿不到 mobcap 语义；直接用类判据是本项目里最直白、也最难写错的一条，
     * 且与 {@code EntityType} 一一对应。
     *
     * <p>调用方（{@link #sanFor(LivingEntity)}）与常量（{@link #SAN_SHADOW}）都不必改：
     * 影怪消散时也走同一入口（{@code ShadowEntity#settleThenRemove}），因此"击杀影怪"的数值口径
     * 依旧只有 {@link #apply} 一处。
     */
    private static boolean isShadowMob(LivingEntity victim) {
        return victim instanceof ShadowEntity;
    }
}
