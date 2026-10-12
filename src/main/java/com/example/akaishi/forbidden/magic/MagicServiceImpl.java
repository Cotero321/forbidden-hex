package com.example.akaishi.forbidden.magic;

import com.example.akaishi.forbidden.api.magic.CastResult;
import com.example.akaishi.forbidden.api.magic.IMagicService;
import com.example.akaishi.forbidden.api.magic.MagicValues;
import com.example.akaishi.forbidden.api.magic.SpellSnapshot;
import com.example.akaishi.forbidden.api.magic.exec.MagicContext;
import com.example.akaishi.forbidden.magic.cast.CastAdjustments;
import com.example.akaishi.forbidden.magic.cast.MagicCastManager;
import com.example.akaishi.forbidden.magic.judge.ParsedSpell;
import com.example.akaishi.forbidden.magic.table.MagicEffectDef;
import com.example.akaishi.forbidden.magic.table.MagicEffectTable;
import com.example.akaishi.forbidden.magic.table.MagicInkDef;
import com.example.akaishi.forbidden.magic.table.MagicInkTable;
import com.example.akaishi.forbidden.magic.table.MagicSource;
import com.example.akaishi.forbidden.magic.table.MagicWord;

import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 魔法服务实现（设计记忆 §11 契约 / §17.2 P3）。
 *
 * <p>职责：<b>查询状态</b>、<b>变更进度</b>（解锁词 / 等级 / 补池）、以及<b>一次施法的受理</b>。
 * 它<b>不产生实际效果</b> —— 施法只做「解析 → 判定 → 公式 → 门槛 → 扣费 → 返回快照」，
 * 效果由执行层（P5）据快照产生。
 *
 * <h3>为什么把施法拆成 {@link #cast} 与 {@link #decide}</h3>
 * {@code decide} 只依赖 {@link MagicState} 与几个标量，<b>不碰 Player</b>，
 * 因此可以在没有世界、没有实体的情况下逐条验证门槛顺序与扣费；
 * {@code cast} 只是"取状态 → 调 decide → 落实与玩家相关的付账（SAN / 掉血）"的薄壳。
 * 若不拆，这段最需要验证的逻辑就只能靠实机试，而实机试不全分支。
 *
 * <h3>施法的校验顺序（先廉后贵；拒绝一律发生在扣费之前）</h3>
 * <ol>
 *   <li><b>句法</b>：空 / 未知词 / 段溢出 / 无核心 ⇒ {@link CastResult#NOT_EXISTS}</li>
 *   <li><b>冲突</b>：对立词轴 / 同词重复 / 墨轴 / 星墨独占 ⇒ {@link CastResult#CONFLICT}</li>
 *   <li><b>解锁</b>：含未解锁词 ⇒ {@link CastResult#NOT_UNLOCKED}</li>
 *   <li><b>词数上限</b>（按等级）⇒ {@link CastResult#WORD_LIMIT_EXCEEDED}</li>
 *   <li><b>单施法槽 / 冷却 / 过载</b> ⇒ {@link CastResult#BUSY} / {@link CastResult#COOLDOWN} / {@link CastResult#OVERLOADED}</li>
 *   <li><b>星墨源约束</b>（星墨只认灰源）⇒ {@link CastResult#STAR_SOURCE_INVALID}</li>
 *   <li><b>支付门槛</b>（池 / SAN，按<b>首段</b>查）⇒ {@link CastResult#POOL_INSUFFICIENT}</li>
 * </ol>
 * <p>通过之后才 <b>扣池（首段）+ 入热</b>并返回快照；
 * <b>冷却起算与真正的效果</b>都交给 {@code MagicCastManager} 的状态机（§21.1：前摇不吃冷却）。
 */
public final class MagicServiceImpl implements IMagicService {

    /** SAN 系统缺席时的"无限理智"哨兵（表示不设门槛） */
    public static final float UNLIMITED_SAN = Float.MAX_VALUE;

    /** 单例（服务无状态，全部状态在玩家的 {@link MagicState} 里） */
    private static final MagicServiceImpl INSTANCE = new MagicServiceImpl();

    private MagicServiceImpl() {
    }

    public static MagicServiceImpl instance() {
        return INSTANCE;
    }

    // ==================== 查询 ====================

    @Override
    public MagicValues values(Player player) {
        MagicState state = MagicStates.state(player);
        if (state == null) {
            return MagicValues.ABSENT;
        }
        return new MagicValues(
                state.level(),
                MagicValues.wordLimitForLevel(state.level()),
                state.pool(MagicStates.POOL_LIFE),
                state.pool(MagicStates.POOL_DARK),
                state.pool(MagicStates.POOL_WHITE),
                state.poolCapacity(),
                state.heat(),
                state.overloaded(gameTime(player)),
                state.sourceId());
    }

    @Override
    public Set<String> unlockedWords(Player player) {
        MagicState state = MagicStates.state(player);
        return state == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(state.words()));
    }

    @Override
    public boolean isUnlocked(Player player, String wordId) {
        MagicState state = MagicStates.state(player);
        return state != null && state.hasWord(wordId);
    }

    // ==================== 变更 ====================

    @Override
    public boolean unlockWord(Player player, String wordId) {
        MagicState state = MagicStates.state(player);
        // 词表是闭集：未见过的 id 一律拒绝，避免附属塞进"永远用不了"的孤儿词
        if (state == null || !MagicWord.exists(wordId)) {
            return false;
        }
        return state.unlockWord(wordId);
    }

    @Override
    public boolean setLevel(Player player, int level) {
        MagicState state = MagicStates.state(player);
        if (state == null) {
            return false;
        }
        int before = state.level();
        state.setLevel(level);
        return state.level() != before;
    }

    @Override
    public void refillPools(Player player, float ratio) {
        MagicState state = MagicStates.state(player);
        if (state == null) {
            return;
        }
        float clampedRatio = Float.isFinite(ratio) ? Math.max(0f, Math.min(1f, ratio)) : 0f;
        float target = state.poolCapacity() * clampedRatio;
        for (int i = 0; i < MagicValues.POOL_COUNT; i++) {
            // 只补不削：ratio 是"至少补到这个比例"，不是"设为这个比例"
            state.setPool(i, Math.max(state.pool(i), target));
        }
    }

    // ==================== 施法 ====================

    @Override
    public CastOutcome cast(Player player, List<String> wordIds, String mainInkId, int overlayInks,
            float channelSeconds, int volleyShots) {
        MagicState state = MagicStates.state(player);
        if (state == null) {
            return CastOutcome.rejected(CastResult.SERVICE_UNAVAILABLE);
        }
        long now = gameTime(player);
        Float san = querySanity(player);
        float availableSan = san == null ? UNLIMITED_SAN : san;
        // 单施法槽（§21.1 规则 1）：以"是否已有进行中的会话"为判据，作为标量传进纯决策
        boolean busy = MagicCastManager.isCasting(player);

        // 调试入口：源仍读玩家状态里的旧 source 键（C4 退役只针对正式入口，见法杖记忆 §12）
        CastOutcome outcome = decide(state, now, wordIds, mainInkId, overlayInks,
                channelSeconds, volleyShots, availableSan, busy, null, null);
        if (outcome.ok()) {
            return beginPaid(player, state, buildContext(player, state, wordIds, mainInkId, outcome.snapshot()), outcome);
        }
        return outcome;
    }

    /**
     * 法杖正式入口（C4，§44.4）：源由咒核决定、逐词墨参与判定、载体词条调整数值、
     * 额外池先扣（定案 3.3）。
     *
     * @param adjustments 载体调整（法杖侧组装；见 {@link CastAdjustments}）
     */
    public CastOutcome castWithWand(Player player, List<String> wordIds, String mainInkId,
            CastAdjustments adjustments) {

        MagicState state = MagicStates.state(player);
        if (state == null) return CastOutcome.rejected(CastResult.SERVICE_UNAVAILABLE);
        long now = gameTime(player);
        Float san = querySanity(player);
        float availableSan = san == null ? UNLIMITED_SAN : san;
        boolean busy = MagicCastManager.isCasting(player);
        CastOutcome outcome = decide(state, now, wordIds, mainInkId,
                Math.max(0, adjustments.involvedInks().size() - 1), 0f, 0, availableSan, busy,
                adjustments.source(), adjustments);
        if (outcome.ok()) {
            return beginPaid(player, state, buildContext(player, state, wordIds,
                    adjustments.source().id(), mainInkId, adjustments.wordInks(), outcome.snapshot()), outcome);
        }
        return outcome;
    }

    /**
     * 组装执行上下文（§20.9 的参数面）：词 / 墨 / 源 / 等级 / 数值快照。
     *
     * <p>词按<b>四段顺序</b>拼接（{@link ParsedSpell#allWords()}）而非玩家的书写顺序 ——
     * 执行层要用它做"有没有某个词"的措辞与豁免判断，段序与书写序对它等价，
     * 但段序与法阵显示顺序一致，未来做界面时不必再排一次。
     */
    private static MagicContext buildContext(Player player, MagicState state, List<String> wordIds,
            String mainInkId, SpellSnapshot snapshot) {
        return buildContext(player, state, wordIds, snapshot.sourceId(), mainInkId,
                List.of(mainInkId), snapshot);
    }

    /** 法杖路径的组装重载：源与墨集合由载体给定（墨集合含逐词墨 —— 墨协同与墨轴判定按全量） */
    private static MagicContext buildContext(Player player, MagicState state, List<String> wordIds,
            String sourceId, String mainInkId, List<String> inkIds, SpellSnapshot snapshot) {
        List<String> words = List.copyOf(wordIds);
        return new MagicContext(
                player,
                player.level(),
                player.position(),
                sourceId,
                mainInkId,
                inkIds,
                words,
                state.level(),
                snapshot);
    }

    /**
     * <b>纯决策</b>：校验 + 结算 + 只作用于 {@link MagicState} 的扣费（扣池 / 入热）。
     *
     * <p>不触碰 {@code Player} —— 与玩家相关的那部分付账（扣理智值、绿源掉血）由
     * {@link #cast} 在本方法返回成功后补做。这样本方法可在无世界、无实体时逐分支验证。
     *
     * <p><b>为什么冷却不在这里起算</b>：§21.1 规则 2 定「冷却起算点 = 本次施法不再需要玩家参与的那一刻」
     * —— 一次性型是<b>前摇结束</b>时，而不是提交施法的瞬间（否则前摇会白吃一段冷却）。
     * 故起算交给 {@code MagicCastManager}，本方法只负责"冷却中不许施法"这道入口闸门。
     *
     * @param availableSan 当前可用理智值；{@link #UNLIMITED_SAN} 表示不设门槛（理智系统缺席）
     * @param busy         是否已有进行中的施法（单施法槽，§21.1 规则 1）
     */
    public CastOutcome decide(MagicState state, long gameTime, List<String> wordIds, String mainInkId,
            int overlayInks, float channelSeconds, int volleyShots, float availableSan, boolean busy) {
        return decide(state, gameTime, wordIds, mainInkId, overlayInks, channelSeconds, volleyShots,
                availableSan, busy, null, null);
    }

    /**
     * 纯决策的<b>完整形态</b>：载体路径经 {@code sourceOverride}（源由咒核决定）与
     * {@code adjustments}（词条调整 + 额外池先扣）进入；调试路径两参传 {@code null}，
     * 行为与接入前逐分支一致。
     */
    public CastOutcome decide(MagicState state, long gameTime, List<String> wordIds, String mainInkId,
            int overlayInks, float channelSeconds, int volleyShots, float availableSan, boolean busy,
            MagicSource sourceOverride, CastAdjustments adjustments) {

        if (state == null) {
            return CastOutcome.rejected(CastResult.SERVICE_UNAVAILABLE);
        }
        // 未知主墨属于调用方参数错误，按"句子不成立"处理（不猜默认墨，否则会静默换效果）
        if (MagicInkTable.get(mainInkId) == null) {
            return CastOutcome.rejected(CastResult.NOT_EXISTS);
        }

        // ① 句法
        var sourceForPlan = sourceOverride != null ? sourceOverride : state.source();
        var wordInks = adjustments != null ? adjustments.wordInks()
                : java.util.Collections.nCopies(wordIds.size(), mainInkId);
        var compiled = com.example.akaishi.forbidden.api.magic.SpellPlans.compile(
                new com.example.akaishi.forbidden.api.magic.SpellInput(wordIds, wordInks, mainInkId,
                        sourceForPlan.id(), channelSeconds, volleyShots));
        if (!compiled.ok()) {
            var diagnostic = compiled.diagnostics().stream()
                    .filter(d -> d.severity() == com.example.akaishi.forbidden.api.magic.CompileDiagnostic.Severity.ERROR)
                    .findFirst().orElseThrow();
            return CastOutcome.rejected(diagnostic.code().contains("conflict") ? CastResult.CONFLICT : CastResult.NOT_EXISTS,
                    diagnostic.code());
        }
        var plan = com.example.akaishi.forbidden.magic.formula.SpellPlanAdapter.adjusted(compiled.plan(), adjustments);
        var planWords = wordIds.stream().filter(id -> !id.isEmpty()).map(MagicWord::byId).toList();
        MagicSource source = sourceForPlan;
        List<String> inkIds = adjustments != null ? adjustments.involvedInks() : involvedInks(mainInkId);

        // ③ 解锁：句内每个词都必须已解锁
        for (MagicWord word : planWords) {
            if (!state.hasWord(word.id())) {
                return CastOutcome.rejected(CastResult.NOT_UNLOCKED);
            }
        }

        // ④ 词数上限（按等级）
        if (planWords.size() > MagicValues.wordLimitForLevel(state.level())) {
            return CastOutcome.rejected(CastResult.WORD_LIMIT_EXCEEDED);
        }

        // ⑤ 单施法槽 / 冷却 / 过载（§21.1 规则 1：三者并列，任一成立即拒绝）
        // 先判单槽：它比冷却更"近因"，玩家看到 BUSY 比看到 COOLDOWN 更符合直觉
        if (busy) {
            return CastOutcome.rejected(CastResult.BUSY);
        }
        if (state.onCooldown(gameTime)) {
            return CastOutcome.rejected(CastResult.COOLDOWN);
        }
        if (state.overloaded(gameTime)) {
            return CastOutcome.rejected(CastResult.OVERLOADED);
        }

        // ⑥ 独占墨（星墨）只认灰源（§3.1：星墨在非灰源下"无法生效"）
        // 判据取墨定义自身的 exclusive()，而不是硬编码 id —— 否则"表里写着独占、代码里另写一份"，
        // 改动任一处都会漂移
        if (hasExclusiveInk(inkIds) && source != MagicSource.SAN) {
            return CastOutcome.rejected(CastResult.STAR_SOURCE_INVALID);
        }

        // ⑦ 公式结算（此时才需要真算数）
        float cost = plan.cost().total();
        float sanityCost = plan.cost().sanityEquivalent();

        // ⑧ 支付门槛（§21.6：不允许透支）
        // **混合扣费**：一次性型预扣全额；持续型（X3 引导 / X7 连发）只预扣**首段**，
        // 其余段由状态机在"该段生效时"逐段结算（扣不出即终止）。故门槛也只查首段 ——
        // 否则会出现"首段付得起、却被整段总价挡在门外"的假拒绝。
        int segments = plan.schedule().segments();
        float upfront = cost / segments;
        // **额外池先扣**（定案 3.3）：载体路径把首段能从额外池付掉的部分先记走，
        // 主池/理智只对剩余部分设门槛与扣款
        float extraPaid = 0f;
        if (adjustments != null) {
            extraPaid = Math.max(0f, Math.min(adjustments.extraAvailable(source), upfront));
            // 仅预检：实际额外池提交由 MagicPayment 完成。
        }
        float mainPay = upfront - extraPaid;
        int poolIndex = MagicStates.poolIndexOf(source);
        if (poolIndex >= 0 && state.pool(poolIndex) < mainPay) {
            return CastOutcome.rejected(CastResult.POOL_INSUFFICIENT);
        }
        // **只有灰源查理智门槛** —— 绿 / 黑 / 白源付的是池（绿源另付血），
        // 若对它们也查 SAN，等于凭空多一道与所选源无关的门槛，把非灰源玩家挡在门外
        if (source == MagicSource.SAN && availableSan < (sanityCost / segments - extraPaid / 100f)) {
            return CastOutcome.rejected(CastResult.POOL_INSUFFICIENT);
        }

        // 通过 —— 扣池（首段中主池应付的部分）/ 入热（与玩家无关的那部分付账）
        // **冷却不在此起算**：§21.1 规则 2 要的是"不再需要玩家的那一刻"（前摇结束时），
        // 由 MagicCastManager 在投送完成 / 持续段结束时起算
        // decide 保持无副作用；支付和入热在完整编译后提交。

        return CastOutcome.success(com.example.akaishi.forbidden.magic.formula.SpellPlanAdapter.snapshot(plan));
    }

    /** 首段应付的池量（§21.6：一次性 = 全额；持续型 = 总价 ÷ 计价段数） */
    private static int segmentsOf(float pricingCoefficient) {
        return pricingCoefficient > 1f ? Math.max(1, Math.round(pricingCoefficient)) : 1;
    }

    /** 完整编译后原子提交首段；会话拒绝时凭据全额返还。 */
    private static CastOutcome beginPaid(Player player, MagicState state, MagicContext context, CastOutcome outcome) {
        var plan = context.snapshot().plan();
        if (plan != null && (com.example.akaishi.forbidden.api.magic.exec.MagicExecRegistry.delivery(plan.delivery().deliveryId()) == null
                || com.example.akaishi.forbidden.api.magic.exec.MagicExecRegistry.target(
                        com.example.akaishi.forbidden.api.magic.exec.MagicShape.valueOf(plan.target().shape())) == null
                || plan.operations().stream().anyMatch(op -> com.example.akaishi.forbidden.api.magic.exec.MagicExecRegistry.effect(op.effectId()) == null)))
            return CastOutcome.rejected(CastResult.NOT_EXISTS, "missing_executor");
        var receipt = com.example.akaishi.forbidden.magic.cast.MagicPayment.pay(player,
                MagicSource.byIdOrDefault(context.sourceId()), outcome.snapshot().segmentCost());
        if (receipt == null) return CastOutcome.rejected(CastResult.POOL_INSUFFICIENT);
        if (!MagicCastManager.begin(player, context, receipt)) {
            com.example.akaishi.forbidden.magic.cast.MagicPayment.refund(player, receipt, 1f);
            return CastOutcome.rejected(CastResult.BUSY);
        }
        state.addHeat(entryHeat(outcome.snapshot().wordCount()));
        return outcome;
    }

    // ==================== 内部工具 ====================

    /** 施法入热：{@code 20 + 词数 × 4}（§24.1 v13） */
    private static float entryHeat(int wordCount) {
        return 20f + wordCount * 4f;
    }

    /**
     * 本句涉及的墨集合（主墨 + 逐词覆盖墨）。
     *
     * <p><b>待细化</b>：逐词覆盖墨（§3.2，Lv3+）需要"槽内法术视图"（{@code InlaidSpell}）才能拿到；
     * 当前只报主墨，故墨协同与墨轴判定暂时只对主墨生效。
     */
    private static List<String> involvedInks(String mainInkId) {
        List<String> inks = new ArrayList<>(1);
        inks.add(mainInkId);
        return inks;
    }

    /** 句内是否含<b>独占</b>墨（星墨）；判据来自墨表定义，不硬编码 id */
    private static boolean hasExclusiveInk(List<String> inkIds) {
        for (String id : inkIds) {
            MagicInkDef def = MagicInkTable.get(id);
            if (def != null && def.exclusive()) {
                return true;
            }
        }
        return false;
    }

    /** 读当前 SAN；理智系统未安装 / 异常时返回 {@code null}（表示"无门槛"） */
    private static Float querySanity(Player player) {
        try {
            return com.example.akaishi.forbidden.api.sanity.SanityServices.get().getSan(player);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static long gameTime(Player player) {
        return player.level() == null ? 0L : player.level().getGameTime();
    }

}
