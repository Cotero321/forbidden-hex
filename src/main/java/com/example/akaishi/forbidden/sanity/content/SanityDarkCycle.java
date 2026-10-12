package com.example.akaishi.forbidden.sanity.content;

import com.example.akaishi.forbidden.api.sanity.SanityContext;
import com.example.akaishi.config.ModConfig;
import com.example.akaishi.forbidden.sanity.SanityServiceImpl;
import com.example.akaishi.forbidden.sanity.SanityState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/**
 * 暗处机制的<b>状态机</b>：暴露周期（开始 / 结束）与"重见光明 15 分钟冷却"的唯一推进点。
 *
 * <p><b>为什么状态推进不放在规则里</b>：{@link com.example.akaishi.forbidden.api.sanity.ISanityRule#applies}
 * 被约定为"只读、可重复调用、无副作用"，把"改周期标记/写冷却时刻"塞进去会同时踩三条：
 * ① 同一次结算内可能被调用多次（规则被重复求值时状态会被推进多次）；
 * ② 附属按 API 约定实现的规则不该改世界状态；
 * ③ 两条暗处规则各自推进会把"一个暴露周期"拆成两套口径。
 * 因此规则只<b>读</b> {@link #darkDeductionAllowed} / {@link #inDark}，推进统一走
 * {@link #step}，由 {@code SanityEnvironmentSettlement} 在每条规则结算完之后调用一次（结算后推进）。
 *
 * <p><b>状态语义</b>（沿用 P1 已落盘的三个键，不新增 NBT 键）：
 * <ul>
 *   <li>{@code dark_cycle_ended} = 当前暴露周期是否已结束（结束时起算冷却）；</li>
 *   <li>{@code dark_cooldown_until} = 冷却结束刻（0 = 无冷却）；</li>
 *   <li>{@code dark_exposure} = 本周期已累计的<b>原始扣量</b>（供 HUD/调试读取，也是"是否真的暴露过"的判据）。</li>
 * </ul>
 *
 * <p><b>三态转换</b>：
 * <pre>
 * 暗处暴露（无夜视、不在冷却） --[重见光明：canSeeSky 且白天]--&gt; 冷却中（15min）
 * 冷却中 --[冷却结束且仍在暗处]--&gt; 新暴露周期（累计清零）
 * 冷却中 --[持续在光下]--&gt; 冷却走完（不再刷新，只在"重见光明"那一刻起算一次）
 * </pre>
 */
public final class SanityDarkCycle {

    /** 暗处亮度上界（含）：方块光 ≤ 该值即算"暗"（与两条暗处规则的区间上界同源；待调手感值） */
    public static final int DARK_MAX_LIGHT = 7;

    private SanityDarkCycle() {
    }

    /**
     * 夜视免疫：身上有原版夜视效果时，两条暗处规则都不生效（不新增任何自定义效果）。
     *
     * <p>语义提醒：夜视期间 {@code applies} 为假 ⇒ 结算层按 API 约定清零该规则的暴露累计
     * （"暴露中断即清零"），因此夜视等于一次"暂停并重置额度"；这是 {@link com.example.akaishi.forbidden.api.sanity.ISanityRule}
     * 的既有承诺，不额外改写。
     */
    public static boolean nightVisionImmune(Player player) {
        return player != null && player.hasEffect(MobEffects.NIGHT_VISION);
    }

    /** 暗处扣减是否被允许：无夜视 + 不在"重见光明"冷却中（规则侧读这一条） */
    public static boolean darkDeductionAllowed(SanityContext ctx) {
        Player player = ctx.player();
        if (player == null || nightVisionImmune(player)) {
            return false;
        }
        SanityState state = SanityServiceImpl.state(player);
        if (state == null) {
            return false;
        }
        return ctx.level().getGameTime() >= state.darkLightCooldownUntil();
    }

    /** 是否身处"暗处"（Y&lt;0 且方块光 ≤ {@link #DARK_MAX_LIGHT}；不含夜视/冷却判定） */
    public static boolean inDark(SanityContext ctx) {
        return ctx.isUnderY0() && ctx.blockLight() <= DARK_MAX_LIGHT;
    }

    /** 是否"重见光明"：直见天空且主世界昼间（非主世界 {@code isDay()} 恒 false，故天然排除） */
    public static boolean regainedLight(SanityContext ctx) {
        return ctx.canSeeSky() && ctx.isDay();
    }

    /**
     * 状态推进（每次环境结算之后调用一次，玩家级）。
     *
     * <p>只在"确实产生过暴露累计"时起算冷却（{@code darkExposure > 0}）：否则一名玩家登录后
     * 站在白天，第一次结算就会被记成"刚重见光明"，白拿 15 分钟免疫。
     */
    public static void step(SanityContext ctx, SanityState state, long now) {
        if (state == null) {
            return;
        }
        if (regainedLight(ctx)) {
            if (!state.darkCycleEnded() && state.darkExposure() > 0f) {
                // 重见光明：结束本次暴露周期并起算 15min 冷却（只在"暗→光"这一刻起算一次，不随光下停留刷新）
                state.setDarkCycleEnded(true);
                state.setDarkExposure(0f);
                state.setDarkLightCooldownUntil(now + darkLightCooldownTicks());
                resetDarkRuntimes(state);
            }
            return;
        }
        if (!inDark(ctx)) {
            // 夜视或不在暗处：周期标记保持不动（两条规则各自的额度由结算层按 applies 结果清零）
            return;
        }
        if (now < state.darkLightCooldownUntil()) {
            return; // 冷却中：不开新周期（规则侧同样判，双保险）
        }
        if (state.darkCycleEnded()) {
            // 冷却结束且仍在暗处 ⇒ 重新开始一次暴露周期（累计清零）
            state.setDarkCycleEnded(false);
            state.setDarkLightCooldownUntil(0L);
            resetDarkRuntimes(state);
        }
        // 记录本周期累计（原始量合计），供 HUD/调试读取；该字段不参与扣减判定
        state.setDarkExposure(accumulated(state));
    }

    /** 冷却剩余 tick（0 = 无冷却）；调试指令用 */
    public static long cooldownRemaining(SanityState state, long now) {
        return state == null ? 0L : Math.max(0L, state.darkLightCooldownUntil() - now);
    }

    /** 本周期暗处已累计的原始扣量（两条暗处规则各自累计之和） */
    public static float accumulated(SanityState state) {
        float sum = 0f;
        for (ResourceLocation id : SanityBuiltinRules.darkRuleIds()) {
            sum += state.ruleRuntime(id.toString()).accumulated();
        }
        return sum;
    }

    /** 暴露周期结束/重启时清除两条暗处规则的额度（累计 + 节流一起归零，重新进入可再扣满一轮） */
    private static void resetDarkRuntimes(SanityState state) {
        for (ResourceLocation id : SanityBuiltinRules.darkRuleIds()) {
            state.ruleRuntime(id.toString()).resetExposure();
        }
    }

    /** 冷却时长（tick）：外露配置优先，0 视为"用内置默认"（{@link SanityState#DARK_LIGHT_COOLDOWN_TICKS}） */
    private static int darkLightCooldownTicks() {
        return ModConfig.sanityDarkLightCooldownTicks > 0
                ? ModConfig.sanityDarkLightCooldownTicks
                : SanityState.DARK_LIGHT_COOLDOWN_TICKS;
    }
}
