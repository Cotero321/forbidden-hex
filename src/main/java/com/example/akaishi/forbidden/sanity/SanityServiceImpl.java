package com.example.akaishi.forbidden.sanity;

import com.example.akaishi.forbidden.api.sanity.ISanityFirstEncounter;
import com.example.akaishi.forbidden.api.sanity.ISanityService;
import com.example.akaishi.forbidden.api.sanity.SanityCallbacks;
import com.example.akaishi.forbidden.api.sanity.SanityChangeSource;
import com.example.akaishi.forbidden.api.sanity.SanityFirstRegistry;
import com.example.akaishi.forbidden.api.sanity.SanityStat;
import com.example.akaishi.forbidden.api.sanity.SanityValues;
import com.example.akaishi.forbidden.state.ForbiddenPlayerState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * {@link ISanityService} 的唯一实现：所有理智数值读写的<b>收敛点</b>。
 *
 * <p><b>为什么所有写入都必须过这里</b>：五个值互相耦合（上限被削减要连带下压当前值、
 * SAN 必须夹在 {@code [0, 硬上限]}、COG 有合法区间）。把这些规则收进一个实现后，
 * 附属经 API 怎么写都改不出自相矛盾的状态；同时"数值真的变了"与"该下发同步了"
 * 只有这一处能判断，不会出现某条路径改了值却没通知监听方。
 *
 * <p><b>权威性</b>：服务端真实生效；客户端读写走只读镜像 {@link ClientSanityData}，
 * 写方法静默无效（不抛异常、不上报），符合 API 承诺。
 *
 * <p><b>派发顺序</b>：先夹取落地 → 打同步脏标记 → 派发 {@code fireValueChanged}
 * → 最后做阈值边沿判定（跨档派发）。阈值判定放在最后，是因为它要以<b>落地后</b>的
 * 百分比为准；而同一次写入可能只触发一次跨档链（不会因为"先派发值变化"而重复跨档）。
 */
public final class SanityServiceImpl implements ISanityService {

    private static final Logger LOGGER = LogManager.getLogger("akaishi.sanity");

    /** 实现版本：数值口径 / 存档格式不兼容变动时自增 */
    public static final int IMPL_VERSION = 1;

    private static final SanityServiceImpl INSTANCE = new SanityServiceImpl();

    private SanityServiceImpl() {
    }

    public static SanityServiceImpl instance() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------
    // 状态访问（内部共用）
    // ------------------------------------------------------------------

    /** 取玩家理智状态；无 capability / 玩家为 null 返回 null（调用方判空） */
    public static SanityState state(Player player) {
        if (player == null) {
            return null;
        }
        ForbiddenPlayerState state = ForbiddenPlayerState.of(player);
        return state == null ? null : state.sanity();
    }

    /** 服务端权威状态：客户端或无 capability 一律返回 null（写路径据此前置返回） */
    private static SanityState serverState(Player player) {
        if (player == null || player.level() == null || player.level().isClientSide) {
            return null;
        }
        return state(player);
    }

    private static boolean isClient(Player player) {
        return player == null || player.level() == null || player.level().isClientSide;
    }

    /** 非法数值（NaN / Inf）一律拒绝：写进去会污染整条夹取链，且无法自愈 */
    private static boolean invalid(float value) {
        return !Float.isFinite(value);
    }

    // ------------------------------------------------------------------
    // 读
    // ------------------------------------------------------------------

    @Override
    public SanityValues snapshot(Player player) {
        if (isClient(player)) {
            return ClientSanityData.snapshot();
        }
        SanityState state = state(player);
        return state == null ? SanityValues.ZERO
                : new SanityValues(state.san(), state.sanc(), state.cog(), state.protection(), state.tempCut());
    }

    @Override
    public float getSan(Player player) {
        if (isClient(player)) {
            return ClientSanityData.snapshot().san();
        }
        SanityState state = state(player);
        return state == null ? 0f : state.san();
    }

    @Override
    public float getSanc(Player player) {
        if (isClient(player)) {
            return ClientSanityData.snapshot().sanc();
        }
        SanityState state = state(player);
        return state == null ? 0f : state.sanc();
    }

    @Override
    public float getCog(Player player) {
        if (isClient(player)) {
            return ClientSanityData.snapshot().cog();
        }
        SanityState state = state(player);
        return state == null ? 0f : state.cog();
    }

    @Override
    public float getProtection(Player player) {
        if (isClient(player)) {
            return ClientSanityData.snapshot().protection();
        }
        SanityState state = state(player);
        return state == null ? 0f : state.protection();
    }

    @Override
    public float getTempCut(Player player) {
        if (isClient(player)) {
            return ClientSanityData.snapshot().tempCut();
        }
        SanityState state = state(player);
        return state == null ? 0f : state.tempCut();
    }

    @Override
    public float getEffectiveMax(Player player) {
        if (isClient(player)) {
            return ClientSanityData.snapshot().effectiveMax();
        }
        SanityState state = state(player);
        return state == null ? 0f : state.effectiveMax();
    }

    // ------------------------------------------------------------------
    // 写（仅服务端生效）
    // ------------------------------------------------------------------

    @Override
    public void setSan(Player player, float value) {
        applySan(player, SanityChangeSource.EXTERNAL, value, false);
    }

    @Override
    public void setSanc(Player player, float value) {
        applySanc(player, SanityChangeSource.EXTERNAL, value, false);
    }

    @Override
    public void setCog(Player player, float value) {
        applyCog(player, SanityChangeSource.EXTERNAL, value, false);
    }

    @Override
    public void setProtection(Player player, float value) {
        applyProtection(player, SanityChangeSource.EXTERNAL, value, false);
    }

    @Override
    public void setTempCut(Player player, float value) {
        applyTempCut(player, SanityChangeSource.EXTERNAL, value, false);
    }

    @Override
    public void addSan(Player player, float delta) {
        applySan(player, SanityChangeSource.EXTERNAL, delta, true);
    }

    @Override
    public void addSanc(Player player, float delta) {
        applySanc(player, SanityChangeSource.EXTERNAL, delta, true);
    }

    @Override
    public void addCog(Player player, float delta) {
        applyCog(player, SanityChangeSource.EXTERNAL, delta, true);
    }

    @Override
    public void addProtection(Player player, float delta) {
        applyProtection(player, SanityChangeSource.EXTERNAL, delta, true);
    }

    @Override
    public void addTempCut(Player player, float delta) {
        applyTempCut(player, SanityChangeSource.EXTERNAL, delta, true);
    }

    @Override
    public boolean reportFirstEncounter(Player player, ResourceLocation encounterId) {
        if (encounterId == null) {
            return false;
        }
        SanityState state = serverState(player);
        if (state == null) {
            return false;
        }
        ISanityFirstEncounter encounter = SanityFirstRegistry.get(encounterId);
        if (encounter == null) {
            // 未注册：只记日志不抛异常（附属卸载后旧调用点仍可能在跑）
            LOGGER.warn("[akaishi] 理智首见上报的 id 未注册，已忽略：{}", encounterId);
            return false;
        }
        if (!state.markFirstSeen(encounterId.toString())) {
            return false; // 该 id 早已记录过（含附属卸载后重装的接续场景）
        }
        // 首见结算：上限与认知的一次性增减（走同一写入漏斗，故同样触发回调与阈值判定）
        applySanc(player, SanityChangeSource.FIRST_ENCOUNTER, encounter.sancDelta(), true);
        applyCog(player, SanityChangeSource.FIRST_ENCOUNTER, encounter.cogDelta(), true);
        SanityCallbacks.fireFirstEncounter(player, encounterId);
        return true;
    }

    @Override
    public int version() {
        return IMPL_VERSION;
    }

    // ------------------------------------------------------------------
    // 写入漏斗（内部结算层也用这几个方法，保证口径唯一）
    // ------------------------------------------------------------------

    /**
     * 增减 SAN（内部结算层用；{@code source} 用于回调打标）。
     *
     * <p>非扣减性质的写入（食补补量、自然恢复、上限回归补偿等）走这条；
     * 扣减性质请走 {@link #debitInternal}，否则会绕过"临时保护优先抵扣"。
     */
    public void addSanInternal(Player player, float delta, SanityChangeSource source) {
        applySan(player, source, delta, true);
    }

    /**
     * 增减理智上限（内部结算层用：首用来源的 SANC 增益走这条）。
     *
     * <p>与 {@link #addSanInternal} 同源，都过同一个写入漏斗（{@code >= 0} 夹取、连带下压超限的当前值、
     * 打脏、派发值变化与阈值边沿），避免内容层自己改 {@link SanityState} 而漏掉回调与同步。
     *
     * <p><b>上限回补的接入点（已预留）</b>：将来的「秘典回补路径」<b>直接调用既有的
     * {@link ISanityService#addSanc} 即可</b>（对外契约，不必新增任何 API），或走本方法。
     * 之所以在此标注：当前理智上限的净账为负——首见削减合计 −126、首用 +56、秘典 +28 ⇒ 净 −42
     * （全收集玩家上限永久缩到约 58，且<b>暂无回补路径</b>）。用户已拍板本轮只记账、不改数值；
     * 回补统一从这条既有漏斗进入（夹取/回调/阈值/同步一处不落），不必为它另开入口。
     */
    public void addSancInternal(Player player, float delta, SanityChangeSource source) {
        applySanc(player, source, delta, true);
    }

    /**
     * 增减临时理智保护（内部结算层用：食补窗口的保护量按 tick 分摊时走这条）。
     *
     * <p>与 {@link #addSanInternal} 同源，都过同一个写入漏斗（夹取 ≥ 0、打脏、派发值变化回调），
     * 避免食补路径自己改 {@link SanityState} 而漏掉回调与同步。
     */
    public void addProtectionInternal(Player player, float delta, SanityChangeSource source) {
        applyProtection(player, source, delta, true);
    }

    /**
     * 扣减 SAN（内部结算层用）：<b>先由临时保护抵扣，余量才落到 SAN</b>。
     *
     * <p>口径来自 API 层的公开承诺（{@code SanityValues}/{@code SanityState} 的 protection 语义）：
     * 保护是"结算时优先抵扣"的缓冲层，抵扣完才动正式值。若扣减路径绕过本方法，
     * protection 就会变成"只写得进、永远读不出效果"的坏数据，因此扣减必须走这一条。
     *
     * @param amount 正的扣减量（负数会被当作补量直接落到 SAN，不做保护抵扣）
     */
    public void debitInternal(Player player, float amount, SanityChangeSource source) {
        if (invalid(amount)) {
            return;
        }
        SanityState state = serverState(player);
        if (state == null) {
            return;
        }
        float remaining = amount;
        if (amount > 0f && state.protection() > 0f) {
            float absorbed = Math.min(state.protection(), amount);
            applyProtection(player, source, state.protection() - absorbed, false);
            remaining = amount - absorbed;
        }
        if (remaining != 0f) {
            applySan(player, source, remaining, true);
        }
    }

    private void applySan(Player player, SanityChangeSource source, float value, boolean delta) {
        if (invalid(value)) {
            return;
        }
        SanityState state = serverState(player);
        if (state == null) {
            return;
        }
        float before = SanityThresholds.percentOf(state);
        float old = state.san();
        float clamped = state.clampedSan(delta ? old + value : value);
        if (clamped != old) {
            state.setSan(clamped);
            state.markDirty();
            SanityCallbacks.fireValueChanged(player, SanityStat.SAN, old, clamped, source);
        }
        SanityThresholds.evaluate(player, before, SanityThresholds.percentOf(state));
    }

    private void applySanc(Player player, SanityChangeSource source, float value, boolean delta) {
        if (invalid(value)) {
            return;
        }
        SanityState state = serverState(player);
        if (state == null) {
            return;
        }
        float before = SanityThresholds.percentOf(state);
        float old = state.sanc();
        float target = Math.max(0f, delta ? old + value : value);
        if (target != old) {
            state.setSanc(target);
            state.markDirty();
            SanityCallbacks.fireValueChanged(player, SanityStat.SANC, old, target, source);
        }
        pressSanIntoLimit(player, state);
        SanityThresholds.evaluate(player, before, SanityThresholds.percentOf(state));
    }

    private void applyTempCut(Player player, SanityChangeSource source, float value, boolean delta) {
        if (invalid(value)) {
            return;
        }
        SanityState state = serverState(player);
        if (state == null) {
            return;
        }
        float before = SanityThresholds.percentOf(state);
        float old = state.tempCut();
        float target = Math.max(0f, delta ? old + value : value);
        if (target != old) {
            state.setTempCut(target);
            state.markDirty();
            SanityCallbacks.fireValueChanged(player, SanityStat.TEMP_CUT, old, target, source);
        }
        pressSanIntoLimit(player, state);
        SanityThresholds.evaluate(player, before, SanityThresholds.percentOf(state));
    }

    private void applyProtection(Player player, SanityChangeSource source, float value, boolean delta) {
        if (invalid(value)) {
            return;
        }
        SanityState state = serverState(player);
        if (state == null) {
            return;
        }
        float old = state.protection();
        float target = Math.max(0f, delta ? old + value : value);
        if (target != old) {
            state.setProtection(target);
            state.markDirty();
            SanityCallbacks.fireValueChanged(player, SanityStat.PROTECTION, old, target, source);
        }
        // 保护不参与上限夹取，故不动 SAN、也不影响阈值百分比
    }

    private void applyCog(Player player, SanityChangeSource source, float value, boolean delta) {
        if (invalid(value)) {
            return;
        }
        SanityState state = serverState(player);
        if (state == null) {
            return;
        }
        float old = state.cog();
        float target = Math.max(0f, Math.min(SanityState.COG_UPPER_BOUND, delta ? old + value : value));
        if (target != old) {
            state.setCog(target);
            state.markDirty();
            SanityCallbacks.fireValueChanged(player, SanityStat.COG, old, target, source);
        }
        // COG 只影响系数，不改 SAN 与上限，故不产生阈值跨越
    }

    /** 上限/削减变化后把超出新硬上限的当前值下压（保持快照自洽，回调打核心内部来源） */
    private void pressSanIntoLimit(Player player, SanityState state) {
        float old = state.san();
        float clamped = state.clampedSan(old);
        if (clamped != old) {
            state.setSan(clamped);
            state.markDirty();
            SanityCallbacks.fireValueChanged(player, SanityStat.SAN, old, clamped, SanityChangeSource.INTERNAL);
        }
    }
}
