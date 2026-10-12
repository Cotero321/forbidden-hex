package com.example.akaishi.forbidden.sanity;

import com.example.akaishi.forbidden.api.sanity.ISanityRule;
import com.example.akaishi.forbidden.api.sanity.SanityCallbacks;
import com.example.akaishi.forbidden.api.sanity.SanityChangeSource;
import com.example.akaishi.forbidden.api.sanity.SanityContext;
import com.example.akaishi.forbidden.api.sanity.SanityFirstRegistry;
import com.example.akaishi.forbidden.api.sanity.SanityRuleRegistry;
import com.example.akaishi.config.ModConfig;
import com.example.akaishi.forbidden.sanity.content.SanityDarkCycle;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 环境结算骨架：每玩家每 {@link #SETTLE_PERIOD_TICKS} tick 遍历 {@link SanityRuleRegistry} 的规则并结算扣减。
 *
 * <p><b>完整链路</b>（内置规则见 {@code sanity.content.SanityBuiltinRules}，与附属规则同权）：
 * <ol>
 *   <li>全局节拍到点 → 取注册表快照（<b>空表直接返回</b>，连上下文都不构建 ⇒ 零世界查询）；
 *       总开关 {@code ModConfig.sanityEnabled} 为假时连节拍都不看，直接返回；</li>
 *   <li>按 {@code priority()} <b>降序</b>排序（数值大的先结算，供"豁免/增幅类"规则先行）；</li>
 *   <li>每玩家构建/复用 {@link SanityContext}（受 TTL + 区块段缓存节流）；</li>
 *   <li>逐规则：{@code applies(ctx)}（异常隔离为"不命中"）→ <b>不命中即清零该规则的暴露累计与节流</b>；</li>
 *   <li>命中 → 累加节流计数，每满 {@code periodTicks} 结一次（单次结算最多补
 *       {@link #MAX_SETTLE_PER_VISIT} 次，防附属写 period=1 的规则造成回调风暴）；</li>
 *   <li>结单次前：检查单次暴露累计上限（{@code capPerExposure}，0 = 不设限）；</li>
 *   <li>过 {@link SanityCallbacks#fireEnvironmentDebit} 否决门（返回 false ⇒ 本次不扣，且不计入累计）；</li>
 *   <li>扣量 × COG 环境扣除系数 → 写入 SAN（经临时保护优先抵扣）；
 *       数值真的变化时由 {@link SanityServiceImpl} 统一派发值变化回调与阈值边沿、并打同步脏标记；</li>
 *   <li><b>首见声明式轮询</b>：{@link SanityFirstEncounterSettlement#settle} 复用同一份上下文，
 *       对尚未触发过的首见条目求值（已触发条目只花一次集合查表）；</li>
 *   <li><b>结算后推进</b>：{@link SanityDarkCycle#step} 推进暗处机制的暴露周期/重见光明冷却
 *       （规则只读状态，推进只有这一处）。</li>
 * </ol>
 *
 * <p><b>为什么"全局节拍 + 每规则独立计时"而不是每规则一个定时器</b>：规则数量由附属任意注册，
 * 逐个挂调度器会让"注册/注销"变成有状态操作（注销漏了就永久泄漏一个调度器）；
 * 统一节拍下每条规则只是一份可随存档丢弃的小计数（{@link SanityState.RuleRuntime}），
 * 注册表变更天然被下一次结算看到（注册表本身不缓存快照）。
 *
 * <p><b>记账口径</b>：单次暴露累计值按<b>规则原始量</b>记（与 {@code amountPerPeriod} / {@code capPerExposure}
 * 同单位，附属可自洽推理）；COG 系数只影响真正落到 SAN 的量，不影响累计记账。
 */
public final class SanityEnvironmentSettlement {

    private static final Logger LOGGER = LogManager.getLogger("akaishi.sanity");

    /** 全局结算节拍（tick）：1s。规则自述的 periodTicks 是该节拍的细分（待调手感值） */
    public static final int SETTLE_PERIOD_TICKS = 20;

    /** 上下文缓存 TTL（tick）：2s——决定"结构/群系查询"的最坏频率（每玩家每 2 秒 ≤ 1 次） */
    public static final int CONTEXT_CACHE_TICKS = 40;

    /** 缓存淘汰：超过此时长未被刷新的上下文条目直接丢弃（防玩家下线后长期钉住世界引用） */
    private static final int CONTEXT_EVICT_TICKS = 60;

    /** 单次结算对同一规则最多补结算几次（防 periodTicks 极小造成的回调风暴） */
    private static final int MAX_SETTLE_PER_VISIT = 4;

    /** 玩家 → 缓存上下文（仅服务端主线程写读；UUID 键，不持有跨维度对象） */
    private static final Map<UUID, CachedContext> CONTEXTS = new ConcurrentHashMap<>();

    private SanityEnvironmentSettlement() {
    }

    /** 服务端每个维度每 tick 调用（由 AkaishiMod.init 的 SERVER_LEVEL_POST 驱动） */
    public static void serverTick(ServerLevel level) {
        if (!ModConfig.sanityEnabled) {
            return; // 理智系统总开关关闭：整套不结算（规则不推进、暗处状态机不推进）
        }
        long now = level.getGameTime();
        if (now % SETTLE_PERIOD_TICKS != 0) {
            return; // 节拍未到：本 tick 零成本
        }
        evictStaleContexts(now);
        Collection<ISanityRule> registered = SanityRuleRegistry.getAll();
        if (registered.isEmpty() && SanityFirstRegistry.getAll().isEmpty()) {
            return; // 两类注册表都为空：不构建上下文、不做任何世界查询（零开销承诺）
        }
        List<ISanityRule> rules = new ArrayList<>(registered);
        // 优先级降序（同值顺序不保证，符合 ISanityRule 的约定）
        rules.sort(Comparator.comparingInt(ISanityRule::priority).reversed());
        for (ServerPlayer player : level.players()) {
            settlePlayer(level, player, rules, now);
        }
    }

    /** 诊断/调试用：单独构建一份上下文（不走缓存、不做任何写入），供指令读取"规则此刻是否命中" */
    public static SanityContext debugContext(ServerLevel level, ServerPlayer player) {
        return new SanityContextImpl(level, player);
    }

    /**
     * 诊断/调试用：立刻强制结算某条规则一个周期。
     *
     * <p><b>绕过节流、不绕过上限与否决门</b>：便于"连点指令观察单次暴露累计爬到上限"的手感验证；
     * 若连上限一起绕过，就没法验证 {@code capPerExposure} 是否真的生效。
     *
     * @return 是否真的产生了扣减（未命中 / 已达上限 / 被否决 / 无 capability 均为 false）
     */
    public static boolean settleRuleNow(ServerPlayer player, ISanityRule rule) {
        SanityState state = SanityServiceImpl.state(player);
        if (state == null || rule == null) {
            return false;
        }
        SanityContext context = debugContext(player.serverLevel(), player);
        try {
            if (!rule.applies(context)) {
                return false;
            }
        } catch (Throwable t) {
            LOGGER.warn("[akaishi] 理智规则判定异常（调试强制结算按不命中处理）: {}", rule.id(), t);
            return false;
        }
        float before = state.san() + state.protection();
        float envFactor = SanityCogCurve.envDeductionFactor(state.cog());
        debitOnce(player, state, context, rule, envFactor, state.ruleRuntime(rule.id().toString()));
        return state.san() + state.protection() != before;
    }


    private static void settlePlayer(ServerLevel level, ServerPlayer player, List<ISanityRule> rules, long now) {
        SanityState state = SanityServiceImpl.state(player);
        if (state == null) {
            return; // 无 capability（异常/未挂载）：跳过本次结算
        }
        SanityContext context = contextFor(level, player, now);
        // 一次结算内所有规则共用同一份 COG 系数：中途有规则改了认知值也不会让同一次结算出现两套系数
        float envFactor = SanityCogCurve.envDeductionFactor(state.cog());
        for (ISanityRule rule : rules) {
            settleRule(player, state, context, rule, envFactor);
        }
        // 首见声明式轮询：与规则共用同一份上下文与节拍（不产生第二份群系/结构查询），
        // 未触发过的条目才跑判据（见 SanityFirstEncounterSettlement 的成本口径）
        SanityFirstEncounterSettlement.settle(player, context);
        // 结算后推进：暗处机制（暴露周期 / 重见光明冷却）的状态推进只在此处发生，规则侧只读
        SanityDarkCycle.step(context, state, now);
    }

    private static void settleRule(ServerPlayer player, SanityState state, SanityContext context,
                                   ISanityRule rule, float envFactor) {
        String ruleId = rule.id().toString();
        boolean applies;
        try {
            applies = rule.applies(context);
        } catch (Throwable t) {
            // 捕获 Throwable：附属类缺失时抛的是 NoClassDefFoundError，只捕 Exception 会崩掉整个服务端 tick
            LOGGER.warn("[akaishi] 理智规则判定异常（本次视为不命中）: {}", ruleId, t);
            applies = false;
        }
        SanityState.RuleRuntime runtime = state.ruleRuntime(ruleId);
        if (!applies) {
            // 暴露中断：累计清零（下次重新进入可再扣满一轮），节流计时也一并从头开始
            runtime.resetExposure();
            return;
        }
        runtime.addThrottle(SETTLE_PERIOD_TICKS);
        int period = rule.periodTicks();
        if (period <= 0) {
            return; // 注册表已保证 > 0，此处仅防御
        }
        int due = Math.min(runtime.throttle() / period, MAX_SETTLE_PER_VISIT);
        for (int i = 0; i < due; i++) {
            if (!runtime.consumeThrottle(period)) {
                break;
            }
            debitOnce(player, state, context, rule, envFactor, runtime);
        }
        runtime.clampThrottle(period);
    }

    /** 单次扣减（含上限检查与否决门） */
    private static void debitOnce(ServerPlayer player, SanityState state, SanityContext context,
                                  ISanityRule rule, float envFactor, SanityState.RuleRuntime runtime) {
        float raw = rule.amountPerPeriod();
        if (raw == 0f) {
            return;
        }
        // 单次暴露累计上限：只在"扣减方向"记账（回补不计入，否则回补会把额度刷掉）
        int cap = rule.capPerExposure();
        if (cap > 0 && raw > 0f && runtime.accumulated() >= cap) {
            return;
        }
        // 否决门：收到的是规则自述的原始量（乘 COG 系数之前），保持"规则量 → 系统统一乘系数"的单向数据流
        if (!SanityCallbacks.fireEnvironmentDebit(player, rule, context, raw)) {
            return;
        }
        float effective = raw * envFactor;
        if (effective == 0f) {
            return;
        }
        if (raw > 0f) {
            // 累计在"确实扣了"之后记：被否决的调用不应消耗本暴露周期的额度
            runtime.addAccumulated(raw);
        }
        // 扣减走 debitInternal：临时保护优先抵扣，余量才落到 SAN
        SanityServiceImpl.instance().debitInternal(player, effective, SanityChangeSource.ENVIRONMENT);
    }

    // ------------------------------------------------------------------
    // 上下文缓存
    // ------------------------------------------------------------------

    /**
     * 取本次结算用的上下文：同玩家、同维度、同 16³ 区块段且未超 TTL 时复用上一份。
     *
     * <p>缓存键是玩家 UUID，有效性由"维度 + 区块段 + TTL"共同决定：换维度或走出当前
     * 区块段立即重建（此时位置已变，环境事实本就该重取），否则最多复用 {@link #CONTEXT_CACHE_TICKS} tick。
     * 因此结构/群系这类较贵的查询是"位置变化时一次 + 每 2 秒一次"，而不是每 tick 一次。
     */
    private static SanityContext contextFor(ServerLevel level, ServerPlayer player, long now) {
        long section = SectionPos.asLong(player.blockPosition());
        CachedContext cached = CONTEXTS.get(player.getUUID());
        if (cached != null && cached.matches(level, now, section)) {
            return cached.context();
        }
        SanityContext context = new SanityContextImpl(level, player);
        CONTEXTS.put(player.getUUID(), new CachedContext(level, now, section, context));
        return context;
    }

    /** 丢弃过期上下文（O(缓存条目)，无世界查询；在节拍检查之后、空表早退之前执行，保证不会长期残留） */
    private static void evictStaleContexts(long now) {
        if (CONTEXTS.isEmpty()) {
            return;
        }
        CONTEXTS.entrySet().removeIf(entry -> now - entry.getValue().builtTick() > CONTEXT_EVICT_TICKS);
    }

    /** 上下文缓存条目：维度 + 区块段 + 构建刻 三者共同构成有效性判据 */
    private record CachedContext(ServerLevel level, long builtTick, long section, SanityContext context) {

        boolean matches(ServerLevel currentLevel, long now, long currentSection) {
            return level == currentLevel && section == currentSection && now - builtTick <= CONTEXT_CACHE_TICKS;
        }
    }
}
