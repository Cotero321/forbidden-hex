package com.example.akaishi.forbidden.sanity;

import com.example.akaishi.forbidden.ForbiddenIds;
import com.example.akaishi.forbidden.state.ForbiddenSubState;
import com.example.akaishi.forbidden.api.sanity.SanityValues;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 玩家理智状态（<b>内部数据层</b>）：五层数值 + 首见/首用标记 + 各机制运行状态。
 *
 * <p><b>为什么挂在躯体 capability 里</b>：本状态必须"跨存档、跨死亡、跨维度"。
 * 项目里已验证过这条链路（落盘 + 死亡即时快照 + 克隆恢复）的玩家持久化载体只有
 * {@code IPlayerBodyState}（见 forge 侧 {@code PlayerBodyCapability}），
 * 另立 capability 只为存几个 float 属于重复建设，且会多出第二套死亡/克隆语义要维护。
 *
 * <p><b>存档容错承诺（对齐 api.sanity 的对外承诺）</b>：
 * <ul>
 *   <li>首见/首用 id 与"按 id 存的机制状态"一律以<b>字符串原样</b>读写，
 *       载入时<b>不查注册表、不校验合法性、不清理未知 id</b>——附属卸载或改 id 后存档不清档，
 *       装回来还能接续（实现位置见 {@link #load(CompoundTag)}）；</li>
 *   <li>非法/空 id 的条目直接跳过（不抛异常、不中断登录）。</li>
 * </ul>
 *
 * <p>{@code damage_seen}（挨过的伤害类型 id）与首见同级、同一套容错口径，
 * 供禁忌秘典的 DAMAGE 条件查询（记录钩子在平台侧，本类只负责存与查）。
 *
 * <p><b>落盘口径</b>：沿用项目"只在有值时写入"的范式——键缺失 ⇔ 该值等于内置默认
 * （SAN/SANC 缺键 = 100，其余缺键 = 0），故 SAN 为 0 时<b>仍会写</b>（0 ≠ 默认 100），不会与"满值"混淆。
 * 禁忌秘典进度存在同级的 {@code codex} 子段里（已学节点 / 各节点阶段 / 可重复节点已用次数 / 已产出解锁键），
 * 四个子表都按字符串原样存 id，空段整体不落盘；节点被移除或改名后存档不清档。
 * 同步脏标记属于运行期簿记，<b>不落盘</b>。
 */
public final class SanityState implements ForbiddenSubState {

    // ===== 默认值与口径常量（均为待调手感值）=====

    /** SAN 初值 = 满值 */
    public static final float DEFAULT_SAN = 100f;
    /** SANC 初值（硬上限的上界） */
    public static final float DEFAULT_SANC = 100f;
    /** COG 初值 */
    public static final float DEFAULT_COG = 0f;
    /**
     * COG 合法上界：系数表的最高档是"≥200"，此处留出余量到 500，
     * 便于后续加档（更高档位只需扩表，不必再动夹取上界；否则"表已加档、值却被旧上界截住"会让新档永远取不到）。
     */
    public static final float COG_UPPER_BOUND = 500f;
    /** 暗处机制：「重见光明」后的 15min 冷却（tick） */
    public static final int DARK_LIGHT_COOLDOWN_TICKS = 15 * 60 * 20;
    /**
     * 「尚无记录的游戏日」哨兵：睡眠追踪的两个游标用它表示"还没写过"。
     *
     * <p>为什么不用 0：0 是<b>合法的游戏日</b>（世界第 1 天），用它当"未初始化"会让
     * "刚出生第 1 天就登录"与"从未记录"撞车，从而漏掉一次初始化。
     */
    public static final long NO_DAY_YET = -1L;

    // ===== NBT 键（躯体 capability 下的嵌套段 + 段内键）=====

    /** 嵌套段名：与 {@code mechanical_integration} 同级 */
    public static final String TAG_ROOT = "sanity";
    private static final String TAG_SAN = "san";
    private static final String TAG_SANC = "sanc";
    private static final String TAG_COG = "cog";
    private static final String TAG_PROTECTION = "protection";
    private static final String TAG_TEMP_CUT = "temp_cut";
    private static final String TAG_FIRST_SEEN = "first_seen";
    /** 挨过的伤害类型（秘典的"曾被某种伤害命中过"条件读它；与首见同级、同一套原样保留口径） */
    private static final String TAG_DAMAGE_SEEN = "damage_seen";
    private static final String TAG_DARK_EXPOSURE = "dark_exposure";
    private static final String TAG_DARK_CYCLE_ENDED = "dark_cycle_ended";
    private static final String TAG_DARK_COOLDOWN_UNTIL = "dark_cooldown_until";
    private static final String TAG_RULE_STATES = "rule_states";
    private static final String TAG_FOOD_STATES = "food_states";
    private static final String TAG_ITEM_USES = "item_uses";
    private static final String TAG_LOW_SAN_PSYCHIC = "psychic_low_san";
    /** 击杀恢复的每日节流：当日已结算到的游戏日 / 当日已发放的击杀恢复 SAN */
    private static final String TAG_KILL_REWARD_DAY = "kill_reward_day";
    private static final String TAG_KILL_REWARD_SAN = "kill_reward_san";
    /** 睡眠追踪（P6）：上次睡过整夜的游戏日 / 已结算惩罚到的游戏日 / 自然恢复进度累积器 */
    private static final String TAG_LAST_SLEEP_DAY = "last_sleep_day";
    private static final String TAG_PUNISHED_DAY = "punished_day";
    private static final String TAG_NAT_REGEN_PROGRESS = "nat_regen_progress";
    /** 禁忌秘典进度段（嵌套子段，与五层数值同级存放；只在有进度时写入） */
    public static final String TAG_CODEX = "codex";
    private static final String TAG_CODEX_LEARNED = "learned";
    private static final String TAG_CODEX_STAGES = "stages";
    private static final String TAG_CODEX_USES = "uses";
    private static final String TAG_CODEX_UNLOCKS = "unlocks";
    private static final String TAG_STAGE = "stage";
    private static final String TAG_USES = "uses";
    /** 通用条目字段：id / 累计值 / 节流计数 / 食补窗口字段 */
    private static final String TAG_ID = "id";
    private static final String TAG_ACCUMULATED = "accumulated";
    private static final String TAG_THROTTLE = "throttle";
    private static final String TAG_WINDOW_SAN = "window_san";
    private static final String TAG_WINDOW_TICKS = "window_ticks";
    private static final String TAG_WINDOW_PROTECTION = "window_protection";
    private static final String TAG_CHAIN = "chain";
    private static final String TAG_LAST_EAT = "last_eat";
    private static final String TAG_USE_TICK = "use_tick";

    /** 同步时刻初值：取负数保证登录后第一 tick 就满足最小间隔（不落盘） */
    private static final long NO_SYNC_YET = -1000L;

    // ===== 五层数值 =====

    private float san = DEFAULT_SAN;
    private float sanc = DEFAULT_SANC;
    private float cog = DEFAULT_COG;
    private float protection;
    private float tempCut;

    // ===== 首见 / 首用标记（原样保留未知 id）=====

    private final Set<String> firstSeen = new LinkedHashSet<>();

    /**
     * 挨过的伤害类型 id（<b>原样保留未知 id</b>，与首见同一套容错口径）。
     *
     * <p>记录的是"伤害类型 id"（如 {@code minecraft:wither}），不是"伤害事件"：
     * 同一类型挨一百次与挨一次等价，故用集合而非计数器（也天然幂等，重复记不会打脏）。
     */
    private final Set<String> damageSeen = new LinkedHashSet<>();

    /**
     * 记档节流用的运行期游标（<b>不落盘</b>）：同一 tick 内同一种伤害类型只记一次。
     *
     * <p>为什么还需要它：被同一来源连续多段命中时（如火焰每 tick 一跳），
     * 集合本身虽已幂等，但每次事件都要分配一次字符串；游标把这次分配也省掉。
     * 只挡"同 tick 同类型"，不同伤害类型在同 tick 各记一次，不丢信息。
     */
    private long lastDamageSeenTick = Long.MIN_VALUE;
    private String lastDamageSeenId;

    // ===== 暗处机制状态（内容在后续段落，本轮只落数据）=====

    /** 本轮累计值（0 = 无累计） */
    private float darkExposure;
    /** 当前"暴露周期"是否已结束 */
    private boolean darkCycleEnded;
    /** 「重见光明后 15min 冷却」的结束时刻（0 = 无冷却） */
    private long darkLightCooldownUntil;

    // ===== 按 id 存的机制运行时（附属扩展自动获得一份）=====

    /** 环境规则运行时：规则 id 字符串 → 单次暴露累计 + 节流计时 */
    private final Map<String, RuleRuntime> ruleStates = new LinkedHashMap<>();
    /** 食补窗口：物品 id 字符串 → 窗口剩余量与连续食用计数 */
    private final Map<String, FoodRuntime> foodStates = new LinkedHashMap<>();
    /**
     * 物品使用冷却：物品 id 字符串 → 上次使用时刻（{@code getGameTime()}）。
     *
     * <p><b>为什么记在玩家身上而不是物品 NBT</b>：冷却的语义是"这个玩家刚补过理智"，
     * 记在物品上会被"多瓶轮换"绕过（每瓶各自计时）。冷却时长不在本类（它属于内容层），
     * 本类只负责"谁、什么时候用过"，是否仍在冷却由读取方（{@code SanityTonicService}）判定。
     *
     * <p>条目由读取方在冷却结束后清除（见 {@code SanityTonicService#cooldownRemainingTicks}），
     * 故不会随时间累积成垃圾数据。
     */
    private final Map<String, Long> itemUseTicks = new LinkedHashMap<>();

    // ===== 阈值惩罚（P3）=====

    /**
     * 上限削减账本（逐档独立记账、可叠加、可单独取消/到期）。
     *
     * <p><b>为什么挂在状态里而不是另立 capability</b>：它必须与 {@link #tempCut} 同生共死
     * （同一份落盘段、同一套死亡清临时值语义），拆开就会出现"削减还在、账本没了"的孤儿数据。
     */
    private final SanityCutLedger cutLedger = new SanityCutLedger();

    /**
     * 「低理智精神化」标记（0% 档）：受到的伤害按精神伤害结算。
     *
     * <p><b>与 BOSS 的精神改写完全解耦</b>：BOSS 那条走躯体状态顶层的 {@code psychic_until}
     * （语义="这个玩家的伤害类型被永久替换"，由 {@code AgaitolosPsychic} 独占），
     * 本字段只在 {@code sanity} 段内、语义是"低理智导致挨打按精神伤害算"，随档位自动翻转，
     * 两者互不读写。它是<b>可推导状态的缓存</b>（权威源仍是 SAN/SANC），
     * 由 P3 结算每秒刷新，读端（伤害事件）因此不必重算百分比。
     */
    private boolean lowSanPsychic;

    /**
     * 击杀恢复的「每日上限」节流（P3 修补）：击杀回 SAN 与账本减免共享同一份额度，
     * 防止"养牲畜/刷怪场"把击杀回理智刷成无限资源。
     *
     * <p>口径与睡眠剥夺一致：<b>游戏日 = 主世界 {@code dayTime / 24000}</b>（见
     * {@code SanityKillReward#apply}）。额度按游戏日重置，落盘 ⇒ 重登/重启不刷新，也无法靠重登绕过。
     * <p>死亡<b>不</b>重置本额度（否则"自杀重置换额度"成为新的刷法）。
     */
    private long killRewardDay = NO_DAY_YET;
    /** {@link #killRewardDay} 当天已发放的击杀恢复 SAN 合计 */
    private float killRewardSanToday;

    // ===== 睡眠追踪与自然恢复（P6）=====

    /** 上次"睡过整夜"的游戏日（{@link #NO_DAY_YET} = 尚无记录） */
    private long lastSleepDay = NO_DAY_YET;
    /** 睡眠剥夺惩罚已结算到的游戏日（{@link #NO_DAY_YET} = 尚无记录） */
    private long punishedDay = NO_DAY_YET;
    /**
     * 自然恢复的<b>进度累积器</b>（0 ≤ x &lt; 1，满 1.0 即 +1 SAN 并减去 1.0）。
     *
     * <p>用累积器而不是"按周期计数"：+10% 这类效率倍率乘在<b>速度</b>上，若用整数周期表达
     * 会被取整抹平（5 分钟 × 1.1 仍写 5 分钟），效率形同虚设。累积器把"1 点 SAN 需要多少 tick"
     * 变成连续量，效率提升直接体现在"更早攒满"。
     */
    private double naturalRegenProgress;

    // ===== 禁忌秘典进度（本轮新增；与首见记档同一套"字符串原样保留"口径）=====

    /** 已学完的节点 id */
    private final Set<String> codexLearned = new LinkedHashSet<>();
    /** 节点 id → 已完成的阶段数（0 = 刚翻开，还没推进一步） */
    private final Map<String, Integer> codexStages = new LinkedHashMap<>();
    /** 节点 id → 可重复节点的已用次数 */
    private final Map<String, Integer> codexUses = new LinkedHashMap<>();
    /** 已产出的"解锁键"（供消费点查询；未知键同样原样保留） */
    private final Set<String> codexUnlocks = new LinkedHashSet<>();

    // ===== 同步簿记（运行期，不落盘）=====

    /** 数值是否已变化待下发；初值 true ⇒ 登录后必推一次，客户端镜像不会长期停在默认值 */
    private boolean syncDirty = true;
    private long lastSyncTick = NO_SYNC_YET;

    // ------------------------------------------------------------------
    // 五层数值读写（夹取规则只在 SanityServiceImpl，本类只存）
    // ------------------------------------------------------------------

    public float san() {
        return san;
    }

    public float sanc() {
        return sanc;
    }

    public float cog() {
        return cog;
    }

    public float protection() {
        return protection;
    }

    public float tempCut() {
        return tempCut;
    }

    /**
     * SAN 硬上限 = max(0, SANC − tempCut)。
     *
     * <p><b>委托给 {@link SanityValues#hardLimit(float, float)}</b>：这是"有效上限"换算的唯一实现，
     * 服务端夹取（{@code SanityServiceImpl#pressSanIntoLimit}）与客户端 HUD 共用同一份，
     * 避免两边各写一份导致口径漂移。行为与内联写法逐位一致。
     */
    public float effectiveMax() {
        return SanityValues.hardLimit(sanc, tempCut);
    }

    /** SAN 按硬上限夹取后的值（写路径统一入口） */
    public float clampedSan(float value) {
        return Math.max(0f, Math.min(effectiveMax(), value));
    }

    void setSan(float value) {
        this.san = value;
    }

    void setSanc(float value) {
        this.sanc = value;
    }

    void setCog(float value) {
        this.cog = value;
    }

    void setProtection(float value) {
        this.protection = value;
    }

    void setTempCut(float value) {
        this.tempCut = value;
    }

    /**
     * 死亡策略的"清除临时值"：临时保护与临时上限削减归零。
     *
     * <p>由平台侧在玩家克隆（死亡重生）时调用——SAN / SANC / COG 与首见标记<b>不在此清除</b>
     * （SAN 保留死亡瞬间值、上限与认知继承）。
     */
    public void clearTemporaries() {
        this.protection = 0f;
        this.tempCut = 0f;
        // 账本与 tempCut 同生共死：只清 tempCut 会留下"下次结算又把削减写回来"的幽灵
        this.cutLedger.clear();
        this.lowSanPsychic = false;
    }

    // ------------------------------------------------------------------
    // 首见 / 首用标记
    // ------------------------------------------------------------------

    /** 记一次首见/首用；返回 false 表示该 id 早已记录过（不重复结算） */
    public boolean markFirstSeen(String id) {
        return id != null && !id.isEmpty() && firstSeen.add(id);
    }

    public boolean hasFirstSeen(String id) {
        return id != null && firstSeen.contains(id);
    }

    // ------------------------------------------------------------------
    // 挨过的伤害类型（秘典的 DAMAGE 条件读它）
    // ------------------------------------------------------------------

    /**
     * 记一次"挨过的伤害类型"。
     *
     * @param id       伤害类型 id（如 {@code minecraft:wither}）；空 id 直接忽略
     * @param gameTime 本次事件的游戏刻（用于同 tick 节流）
     * @return true 仅当<b>真的新增</b>了一条记档（据此打脏；重复/被节流一律 false）
     */
    public boolean markDamageSeen(String id, long gameTime) {
        if (id == null || id.isEmpty()) {
            return false;
        }
        // 节流：同一 tick 内同一种类型只落一次（集合本身也幂等，这里省掉重复的字符串分配）
        if (gameTime == lastDamageSeenTick && id.equals(lastDamageSeenId)) {
            return false;
        }
        lastDamageSeenTick = gameTime;
        lastDamageSeenId = id;
        if (damageSeen.add(id)) {
            markDirty();
            return true;
        }
        return false;
    }

    /** 是否挨过某种伤害类型（秘典 DAMAGE 条件的"单个伤害类型"形式） */
    public boolean hasDamageSeen(String id) {
        return id != null && damageSeen.contains(id);
    }

    /** 已记录的伤害类型 id（只读视图，含未知 id；秘典"伤害类型标签"形式遍历它反查） */
    public Set<String> damageSeen() {
        return Collections.unmodifiableSet(damageSeen);
    }

    // ------------------------------------------------------------------
    // 暗处机制状态
    // ------------------------------------------------------------------

    public float darkExposure() {
        return darkExposure;
    }

    public void setDarkExposure(float value) {
        this.darkExposure = Math.max(0f, value);
    }

    public boolean darkCycleEnded() {
        return darkCycleEnded;
    }

    public void setDarkCycleEnded(boolean value) {
        this.darkCycleEnded = value;
    }

    public long darkLightCooldownUntil() {
        return darkLightCooldownUntil;
    }

    public void setDarkLightCooldownUntil(long gameTime) {
        this.darkLightCooldownUntil = Math.max(0L, gameTime);
    }

    // ------------------------------------------------------------------
    // 规则 / 食补运行时
    // ------------------------------------------------------------------

    /** 取（必要时新建）某规则的运行时——按 id 字符串存，未知 id 同样可持有 */
    public RuleRuntime ruleRuntime(String ruleId) {
        return ruleStates.computeIfAbsent(ruleId, key -> new RuleRuntime());
    }

    /** 取（必要时新建）某物品的食补窗口运行时 */
    public FoodRuntime foodRuntime(String itemId) {
        return foodStates.computeIfAbsent(itemId, key -> new FoodRuntime());
    }

    /** 全部规则运行时（只读视图） */
    public Map<String, RuleRuntime> ruleStates() {
        return Collections.unmodifiableMap(ruleStates);
    }

    /** 全部食补窗口运行时（只读视图） */
    public Map<String, FoodRuntime> foodStates() {
        return Collections.unmodifiableMap(foodStates);
    }

    /** 某物品上次被使用的时刻（0 = 从未使用 / 已清除） */
    public long itemUseTick(String itemId) {
        Long tick = itemId == null ? null : itemUseTicks.get(itemId);
        return tick == null ? 0L : tick;
    }

    /**
     * 记录某物品的使用时刻（{@code gameTime}）。
     *
     * @param tick &lt;= 0 表示清除该条目（冷却已结束的条目由读取方顺手删除，避免存档留垃圾）
     */
    public void setItemUseTick(String itemId, long tick) {
        if (itemId == null || itemId.isEmpty()) {
            return;
        }
        if (tick <= 0L) {
            itemUseTicks.remove(itemId);
        } else {
            itemUseTicks.put(itemId, tick);
        }
    }

    // ------------------------------------------------------------------
    // 阈值惩罚（P3）
    // ------------------------------------------------------------------

    /** 上限削减账本（唯一持有者；内部数据层可读写，外部请走 {@code SanityCutLedger} 的公开方法） */
    public SanityCutLedger cutLedger() {
        return cutLedger;
    }

    /** 是否处于「低理智精神化」（0% 档，挨打按精神伤害结算） */
    public boolean lowSanPsychic() {
        return lowSanPsychic;
    }

    /** 刷新「低理智精神化」标记（值未变时不动脏标记，避免无意义同步） */
    public void setLowSanPsychic(boolean value) {
        if (this.lowSanPsychic != value) {
            this.lowSanPsychic = value;
            markDirty();
        }
    }

    /** 击杀恢复额度已结算到的游戏日（{@link #NO_DAY_YET} = 尚未记录） */
    public long killRewardDay() {
        return killRewardDay;
    }

    public void setKillRewardDay(long day) {
        this.killRewardDay = Math.max(NO_DAY_YET, day);
    }

    /** 该游戏日已发放的击杀恢复 SAN 合计 */
    public float killRewardSanToday() {
        return killRewardSanToday;
    }

    /** 写入当日已发放量（负数/非法值按 0 处理） */
    public void setKillRewardSanToday(float value) {
        this.killRewardSanToday = Math.max(0f, Float.isFinite(value) ? value : 0f);
    }

    // ------------------------------------------------------------------
    // 睡眠追踪与自然恢复（P6）
    // ------------------------------------------------------------------

    public long lastSleepDay() {
        return lastSleepDay;
    }

    public void setLastSleepDay(long day) {
        this.lastSleepDay = Math.max(NO_DAY_YET, day);
    }

    public long punishedDay() {
        return punishedDay;
    }

    public void setPunishedDay(long day) {
        this.punishedDay = Math.max(NO_DAY_YET, day);
    }

    /** 自然恢复进度累积器（0 ≤ x &lt; 1） */
    public double naturalRegenProgress() {
        return naturalRegenProgress;
    }

    /** 写入进度累积器；非有限值按 0 处理，并按 {@code [0, 1)} 夹取（坏存档不会一次吐出多点 SAN） */
    public void setNaturalRegenProgress(double progress) {
        if (!Double.isFinite(progress) || progress < 0.0) {
            this.naturalRegenProgress = 0.0;
            return;
        }
        this.naturalRegenProgress = Math.min(progress, 1.0 - 1e-9);
    }

    // ------------------------------------------------------------------
    // 禁忌秘典进度
    // ------------------------------------------------------------------

    /** 该节点是否已学完 */
    public boolean hasCodexNode(String nodeId) {
        return nodeId != null && codexLearned.contains(nodeId);
    }

    /** 记该节点已学完（幂等；只在真的变化时打脏） */
    public void markCodexNodeLearned(String nodeId) {
        if (nodeId != null && !nodeId.isEmpty() && codexLearned.add(nodeId)) {
            markDirty();
        }
    }

    /** 该节点已完成的阶段数（缺键 = 0） */
    public int codexStage(String nodeId) {
        Integer stage = nodeId == null ? null : codexStages.get(nodeId);
        return stage == null ? 0 : stage;
    }

    /** 写入已完成的阶段数；非正数时删条目（0 = 无信息量，不落盘） */
    public void setCodexStage(String nodeId, int stage) {
        if (nodeId == null || nodeId.isEmpty()) {
            return;
        }
        if (stage <= 0) {
            codexStages.remove(nodeId);
        } else {
            codexStages.put(nodeId, stage);
        }
        markDirty();
    }

    /** 该可重复节点的已用次数（缺键 = 0） */
    public int codexUses(String nodeId) {
        Integer uses = nodeId == null ? null : codexUses.get(nodeId);
        return uses == null ? 0 : uses;
    }

    /** 写入已用次数；非正数时删条目（0 = 无信息量，不落盘） */
    public void setCodexUses(String nodeId, int uses) {
        if (nodeId == null || nodeId.isEmpty()) {
            return;
        }
        if (uses <= 0) {
            codexUses.remove(nodeId);
        } else {
            codexUses.put(nodeId, uses);
        }
        markDirty();
    }

    /** 该解锁键是否已产出 */
    public boolean hasCodexUnlock(String key) {
        return key != null && codexUnlocks.contains(key);
    }

    /** 产出一枚解锁键（幂等） */
    public void markCodexUnlock(String key) {
        if (key != null && !key.isEmpty() && codexUnlocks.add(key)) {
            markDirty();
        }
    }

    // ------------------------------------------------------------------
    // 同步簿记
    // ------------------------------------------------------------------

    public boolean syncDirty() {
        return syncDirty;
    }

    public long lastSyncTick() {
        return lastSyncTick;
    }

    /** 任一数值/机制状态变化时打脏（同步层据此决定是否下发） */
    public void markDirty() {
        this.syncDirty = true;
    }

    public void markSynced(long gameTime) {
        this.syncDirty = false;
        this.lastSyncTick = gameTime;
    }

    // ------------------------------------------------------------------
    // NBT
    // ------------------------------------------------------------------

    /** 落盘：只在有值时写入（缺键 ⇔ 内置默认） */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        // 五层数值：SAN/SANC 的默认是 100 而不是 0，故"0 也写"（否则 0 与"缺键=100"会撞车）
        if (san != DEFAULT_SAN) {
            tag.putFloat(TAG_SAN, san);
        }
        if (sanc != DEFAULT_SANC) {
            tag.putFloat(TAG_SANC, sanc);
        }
        if (cog != 0f) {
            tag.putFloat(TAG_COG, cog);
        }
        if (protection != 0f) {
            tag.putFloat(TAG_PROTECTION, protection);
        }
        if (tempCut != 0f) {
            tag.putFloat(TAG_TEMP_CUT, tempCut);
        }
        // 首见/首用：整体原样写回（未知 id 一并保留）
        if (!firstSeen.isEmpty()) {
            ListTag list = new ListTag();
            for (String id : firstSeen) {
                CompoundTag entry = new CompoundTag();
                entry.putString(TAG_ID, id);
                list.add(entry);
            }
            tag.put(TAG_FIRST_SEEN, list);
        }
        // 挨过的伤害类型：整表原样写回（未知 id 一并保留；空表不落盘，与首见同口径）
        ListTag damageList = writeIdList(damageSeen);
        if (!damageList.isEmpty()) {
            tag.put(TAG_DAMAGE_SEEN, damageList);
        }
        // 暗处机制：非默认才写
        if (darkExposure != 0f) {
            tag.putFloat(TAG_DARK_EXPOSURE, darkExposure);
        }
        if (darkCycleEnded) {
            tag.putBoolean(TAG_DARK_CYCLE_ENDED, true);
        }
        if (darkLightCooldownUntil > 0L) {
            tag.putLong(TAG_DARK_COOLDOWN_UNTIL, darkLightCooldownUntil);
        }
        // 规则运行时：只写非空闲项（累计与节流都归零的条目没有信息量，可安全丢弃）
        ListTag ruleList = new ListTag();
        for (Map.Entry<String, RuleRuntime> entry : ruleStates.entrySet()) {
            RuleRuntime runtime = entry.getValue();
            if (entry.getKey().isEmpty() || runtime.isIdle()) {
                continue;
            }
            CompoundTag item = new CompoundTag();
            item.putString(TAG_ID, entry.getKey());
            item.putFloat(TAG_ACCUMULATED, runtime.accumulated());
            item.putInt(TAG_THROTTLE, runtime.throttle());
            ruleList.add(item);
        }
        if (!ruleList.isEmpty()) {
            tag.put(TAG_RULE_STATES, ruleList);
        }
        // 食补窗口：同样只写非空闲项
        ListTag foodList = new ListTag();
        for (Map.Entry<String, FoodRuntime> entry : foodStates.entrySet()) {
            FoodRuntime runtime = entry.getValue();
            if (entry.getKey().isEmpty() || runtime.isIdle()) {
                continue;
            }
            CompoundTag item = new CompoundTag();
            item.putString(TAG_ID, entry.getKey());
            item.putFloat(TAG_WINDOW_SAN, runtime.windowSan());
            item.putInt(TAG_WINDOW_TICKS, runtime.windowTicks());
            item.putFloat(TAG_WINDOW_PROTECTION, runtime.windowProtection());
            item.putInt(TAG_CHAIN, runtime.chain());
            item.putLong(TAG_LAST_EAT, runtime.lastEatTick());
            foodList.add(item);
        }
        if (!foodList.isEmpty()) {
            tag.put(TAG_FOOD_STATES, foodList);
        }
        // 物品使用冷却：条目本身即"有值"（冷却结束的条目由读取方删除，故此处不筛空闲）
        ListTag useList = new ListTag();
        for (Map.Entry<String, Long> entry : itemUseTicks.entrySet()) {
            if (entry.getKey().isEmpty() || entry.getValue() == null || entry.getValue() <= 0L) {
                continue;
            }
            CompoundTag item = new CompoundTag();
            item.putString(TAG_ID, entry.getKey());
            item.putLong(TAG_USE_TICK, entry.getValue());
            useList.add(item);
        }
        if (!useList.isEmpty()) {
            tag.put(TAG_ITEM_USES, useList);
        }
        // 阈值削减账本：逐档条目（未知档位原样保留）+ 已发放位掩码
        ListTag cutList = cutLedger.save();
        if (!cutList.isEmpty()) {
            tag.put(SanityCutLedger.TAG_CUTS, cutList);
        }
        if (cutLedger.creditedMask() != 0) {
            tag.putInt(SanityCutLedger.TAG_CREDITED, cutLedger.creditedMask());
        }
        // "上次本账本写出的 tempCut"必须落盘：否则重登后 applied 归零，"总量=0 而 tempCut>0"的
        // 残留削减会被判成"不是我们写的"从而永不清零（幽灵削减，见 SanityCutLedger#TAG_APPLIED）
        if (cutLedger.applied() != 0f) {
            tag.putFloat(SanityCutLedger.TAG_APPLIED, cutLedger.applied());
        }
        if (lowSanPsychic) {
            tag.putBoolean(TAG_LOW_SAN_PSYCHIC, true);
        }
        // 击杀恢复的每日额度：哨兵（NO_DAY_YET）与 0.0 都属"无信息"，不写
        if (killRewardDay != NO_DAY_YET) {
            tag.putLong(TAG_KILL_REWARD_DAY, killRewardDay);
        }
        if (killRewardSanToday != 0f) {
            tag.putFloat(TAG_KILL_REWARD_SAN, killRewardSanToday);
        }
        // 睡眠追踪与自然恢复进度：哨兵（NO_DAY_YET）与 0.0 都属"无信息"，不写
        if (lastSleepDay != NO_DAY_YET) {
            tag.putLong(TAG_LAST_SLEEP_DAY, lastSleepDay);
        }
        if (punishedDay != NO_DAY_YET) {
            tag.putLong(TAG_PUNISHED_DAY, punishedDay);
        }
        if (naturalRegenProgress != 0.0) {
            tag.putDouble(TAG_NAT_REGEN_PROGRESS, naturalRegenProgress);
        }
        // 禁忌秘典进度：整段只在有内容时写入（空段不落盘，与其它段同口径）。
        // 四个子表都用"字符串原样"存 id —— 节点被移除/改名后存档不清档，装回可接续。
        CompoundTag codex = new CompoundTag();
        ListTag learned = writeIdList(codexLearned);
        if (!learned.isEmpty()) {
            codex.put(TAG_CODEX_LEARNED, learned);
        }
        ListTag stages = writeIdMap(codexStages, TAG_STAGE);
        if (!stages.isEmpty()) {
            codex.put(TAG_CODEX_STAGES, stages);
        }
        ListTag uses = writeIdMap(codexUses, TAG_USES);
        if (!uses.isEmpty()) {
            codex.put(TAG_CODEX_USES, uses);
        }
        ListTag unlocks = writeIdList(codexUnlocks);
        if (!unlocks.isEmpty()) {
            codex.put(TAG_CODEX_UNLOCKS, unlocks);
        }
        if (!codex.isEmpty()) {
            tag.put(TAG_CODEX, codex);
        }
        return tag;
    }

    /**
     * 读档：缺键取内置默认，未知/非法条目跳过。
     *
     * <p><b>存档容错的落点</b>：首见 id、规则 id、食补物品 id 一律按字符串原样进集合/映射，
     * 此处<b>不做任何注册表校验、不删除未知 id</b>——附属卸载/改 id 后重新装回可自然接续。
     */
    public void load(CompoundTag tag) {
        if (tag == null) {
            return;
        }
        san = tag.contains(TAG_SAN) ? Math.max(0f, tag.getFloat(TAG_SAN)) : DEFAULT_SAN;
        sanc = tag.contains(TAG_SANC) ? Math.max(0f, tag.getFloat(TAG_SANC)) : DEFAULT_SANC;
        cog = Math.max(0f, Math.min(COG_UPPER_BOUND, tag.getFloat(TAG_COG)));
        protection = Math.max(0f, tag.getFloat(TAG_PROTECTION));
        tempCut = Math.max(0f, tag.getFloat(TAG_TEMP_CUT));
        // 上限自洽：SAN 不得高于硬上限（旧存档被外部改过键时也要能自愈）
        san = clampedSan(san);

        firstSeen.clear();
        ListTag firstList = tag.getList(TAG_FIRST_SEEN, Tag.TAG_COMPOUND);
        for (int i = 0; i < firstList.size(); i++) {
            String id = ForbiddenIds.savedKey(firstList.getCompound(i).getString(TAG_ID));
            if (!id.isEmpty()) {
                firstSeen.add(id);
            }
        }

        // 挨过的伤害类型：缺键按"没挨过"处理（安全默认），未知 id 原样保留
        damageSeen.clear();
        readIdList(tag.getList(TAG_DAMAGE_SEEN, Tag.TAG_COMPOUND), damageSeen);

        darkExposure = Math.max(0f, tag.getFloat(TAG_DARK_EXPOSURE));
        darkCycleEnded = tag.getBoolean(TAG_DARK_CYCLE_ENDED);
        darkLightCooldownUntil = Math.max(0L, tag.getLong(TAG_DARK_COOLDOWN_UNTIL));

        ruleStates.clear();
        ListTag ruleList = tag.getList(TAG_RULE_STATES, Tag.TAG_COMPOUND);
        for (int i = 0; i < ruleList.size(); i++) {
            CompoundTag item = ruleList.getCompound(i);
            String id = ForbiddenIds.savedKey(item.getString(TAG_ID));
            if (id.isEmpty()) {
                continue;
            }
            RuleRuntime runtime = new RuleRuntime();
            runtime.addAccumulated(Math.max(0f, item.getFloat(TAG_ACCUMULATED)));
            runtime.addThrottle(Math.max(0, item.getInt(TAG_THROTTLE)));
            if (!runtime.isIdle()) {
                ruleStates.put(id, runtime);
            }
        }

        foodStates.clear();
        ListTag foodList = tag.getList(TAG_FOOD_STATES, Tag.TAG_COMPOUND);
        for (int i = 0; i < foodList.size(); i++) {
            CompoundTag item = foodList.getCompound(i);
            String id = ForbiddenIds.savedKey(item.getString(TAG_ID));
            if (id.isEmpty()) {
                continue;
            }
            FoodRuntime runtime = new FoodRuntime();
            runtime.setWindow(item.getFloat(TAG_WINDOW_SAN), item.getInt(TAG_WINDOW_TICKS),
                    item.getFloat(TAG_WINDOW_PROTECTION));
            runtime.setChain(item.getInt(TAG_CHAIN), item.getLong(TAG_LAST_EAT));
            if (!runtime.isIdle()) {
                foodStates.put(id, runtime);
            }
        }
        // 物品使用冷却：按字符串原样接续（同首见/食补的存档容错口径，不清未知 id）
        itemUseTicks.clear();
        ListTag useList = tag.getList(TAG_ITEM_USES, Tag.TAG_COMPOUND);
        for (int i = 0; i < useList.size(); i++) {
            CompoundTag item = useList.getCompound(i);
            String id = ForbiddenIds.savedKey(item.getString(TAG_ID));
            long tick = item.getLong(TAG_USE_TICK);
            if (!id.isEmpty() && tick > 0L) {
                itemUseTicks.put(id, tick);
            }
        }
        // 阈值削减账本：缺段/缺键按空账本处理（安全默认），未知档位条目原样保留。
        // applied 优先读落盘键；老存档缺该键时，若账本非空则以存档里的 tempCut 兜底
        // （否则旧档已存在的"账本空/条目过期但 tempCut 残留"会永远判不出归属，幽灵削减无法自愈）
        ListTag cutList = tag.getList(SanityCutLedger.TAG_CUTS, Tag.TAG_COMPOUND);
        float cutApplied = tag.contains(SanityCutLedger.TAG_APPLIED)
                ? tag.getFloat(SanityCutLedger.TAG_APPLIED)
                : (cutList.isEmpty() ? 0f : tempCut);
        cutLedger.load(cutList,
                tag.contains(SanityCutLedger.TAG_CREDITED) ? tag.getInt(SanityCutLedger.TAG_CREDITED) : 0,
                cutApplied);
        lowSanPsychic = tag.getBoolean(TAG_LOW_SAN_PSYCHIC);
        // 击杀恢复每日额度：缺键回哨兵/0（老存档升级后当天额度从满额起算，不凭空消耗）
        killRewardDay = tag.contains(TAG_KILL_REWARD_DAY)
                ? Math.max(NO_DAY_YET, tag.getLong(TAG_KILL_REWARD_DAY)) : NO_DAY_YET;
        killRewardSanToday = Math.max(0f, tag.getFloat(TAG_KILL_REWARD_SAN));
        // 睡眠追踪与自然恢复进度：缺键一律回哨兵/0（老存档升级后从"未初始化"开始，不凭空补罚）
        lastSleepDay = tag.contains(TAG_LAST_SLEEP_DAY) ? Math.max(NO_DAY_YET, tag.getLong(TAG_LAST_SLEEP_DAY)) : NO_DAY_YET;
        punishedDay = tag.contains(TAG_PUNISHED_DAY) ? Math.max(NO_DAY_YET, tag.getLong(TAG_PUNISHED_DAY)) : NO_DAY_YET;
        setNaturalRegenProgress(tag.contains(TAG_NAT_REGEN_PROGRESS) ? tag.getDouble(TAG_NAT_REGEN_PROGRESS) : 0.0);
        // 禁忌秘典进度：缺段/缺键一律按"还没读过"处理（安全默认），未知节点 id 原样保留
        codexLearned.clear();
        codexStages.clear();
        codexUses.clear();
        codexUnlocks.clear();
        CompoundTag codex = tag.contains(TAG_CODEX) ? tag.getCompound(TAG_CODEX) : null;
        if (codex != null) {
            readIdList(codex.getList(TAG_CODEX_LEARNED, Tag.TAG_COMPOUND), codexLearned);
            readIdMap(codex.getList(TAG_CODEX_STAGES, Tag.TAG_COMPOUND), TAG_STAGE, codexStages);
            readIdMap(codex.getList(TAG_CODEX_USES, Tag.TAG_COMPOUND), TAG_USES, codexUses);
            readIdList(codex.getList(TAG_CODEX_UNLOCKS, Tag.TAG_COMPOUND), codexUnlocks);
        }
        // 读档后必推一次（客户端可能刚上线，需要权威快照）
        markDirty();
    }

    /** id 集合 → NBT 列表（空/非法 id 跳过；未知 id 原样保留） */
    private static ListTag writeIdList(Set<String> ids) {
        ListTag list = new ListTag();
        for (String id : ids) {
            if (id == null || id.isEmpty()) {
                continue;
            }
            CompoundTag item = new CompoundTag();
            item.putString(TAG_ID, id);
            list.add(item);
        }
        return list;
    }

    /** id → 正整数 映射 → NBT 列表（非正数条目无信息量，不写） */
    private static ListTag writeIdMap(Map<String, Integer> map, String valueKey) {
        ListTag list = new ListTag();
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            Integer value = entry.getValue();
            if (entry.getKey() == null || entry.getKey().isEmpty() || value == null || value <= 0) {
                continue;
            }
            CompoundTag item = new CompoundTag();
            item.putString(TAG_ID, entry.getKey());
            item.putInt(valueKey, value);
            list.add(item);
        }
        return list;
    }

    /** NBT 列表 → id 集合（非法条目跳过，不抛异常） */
    private static void readIdList(ListTag list, Set<String> out) {
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof CompoundTag item)) {
                continue;
            }
            String id = ForbiddenIds.savedKey(item.getString(TAG_ID));
            if (!id.isEmpty()) {
                out.add(id);
            }
        }
    }

    /** NBT 列表 → id → 正数映射（缺键按 0，0 条目丢弃） */
    private static void readIdMap(ListTag list, String valueKey, Map<String, Integer> out) {
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof CompoundTag item)) {
                continue;
            }
            String id = ForbiddenIds.savedKey(item.getString(TAG_ID));
            int value = item.getInt(valueKey);
            if (!id.isEmpty() && value > 0) {
                out.put(id, value);
            }
        }
    }

    /** 规则运行时：单次暴露累计 + 节流计时（按规则 id 各自独立） */
    public static final class RuleRuntime {

        private float accumulated;
        private int throttle;

        /** 单次暴露已累计的<b>规则原始扣量</b>（与 capPerExposure 同单位） */
        public float accumulated() {
            return accumulated;
        }

        /** 节流计数（tick）：满 periodTicks 才消费一次 */
        public int throttle() {
            return throttle;
        }

        public void addAccumulated(float amount) {
            if (amount > 0f) {
                this.accumulated += amount;
            }
        }

        public void addThrottle(int ticks) {
            if (ticks > 0) {
                this.throttle += ticks;
            }
        }

        /** 消费一个周期：计数不足返回 false 且不改动 */
        public boolean consumeThrottle(int periodTicks) {
            if (periodTicks <= 0 || throttle < periodTicks) {
                return false;
            }
            throttle -= periodTicks;
            return true;
        }

        /**
         * 把节流计数压回不足一个周期：周期极小的规则被"单次结算最多补 N 次"限流后，
         * 余额必须丢弃，否则会随每次结算单调堆积（越跑越"欠"）。
         */
        public void clampThrottle(int periodTicks) {
            if (periodTicks > 0 && throttle >= periodTicks) {
                throttle %= periodTicks;
            }
        }

        /** 暴露中断（applies 变假）：累计与节流一起清零，下次重新进入可再扣满一轮 */
        public void resetExposure() {
            accumulated = 0f;
            throttle = 0;
        }

        boolean isIdle() {
            return accumulated == 0f && throttle == 0;
        }
    }

    /** 食补窗口运行时：窗口剩余总量 / 剩余 tick / 连续食用计数与上次食用时刻 */
    public static final class FoodRuntime {

        private float windowSan;
        private int windowTicks;
        private float windowProtection;
        private int chain;
        private long lastEatTick;

        public float windowSan() {
            return windowSan;
        }

        public int windowTicks() {
            return windowTicks;
        }

        public float windowProtection() {
            return windowProtection;
        }

        public int chain() {
            return chain;
        }

        public long lastEatTick() {
            return lastEatTick;
        }

        /** 写入窗口剩余量（食用时按档位刷新，逐 tick 递减由结算层负责） */
        public void setWindow(float remainingSan, int remainingTicks, float remainingProtection) {
            this.windowSan = remainingSan;
            this.windowTicks = Math.max(0, remainingTicks);
            this.windowProtection = remainingProtection;
        }

        /** 写入连续食用计数与上次食用时刻（超过 refreshTicks 由结算层重置为第 1 档） */
        public void setChain(int chain, long lastEatTick) {
            this.chain = Math.max(0, chain);
            this.lastEatTick = Math.max(0L, lastEatTick);
        }

        boolean isIdle() {
            return windowSan == 0f && windowTicks == 0 && windowProtection == 0f
                    && chain == 0 && lastEatTick == 0L;
        }
    }
}
