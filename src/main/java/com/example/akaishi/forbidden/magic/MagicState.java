package com.example.akaishi.forbidden.magic;

import com.example.akaishi.forbidden.state.ForbiddenSubState;
import com.example.akaishi.forbidden.magic.table.MagicSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 玩家魔法状态（<b>内部数据层</b>）：等级 / 已解锁词 / 三池 / 当前源 / 过载热值。
 * 设计记忆 §15.2 / §16.2（段名 {@code magic}，**5 键**）。
 *
 * <p><b>为什么挂在躯体 capability 里</b>：与 {@code SanityState} 同一理由 ——
 * 本状态必须"跨存档、跨死亡、跨维度"。项目里已验证过这条链路（落盘 + 死亡即时快照 + 克隆恢复）
 * 的玩家持久化载体只有玩家的躯体 capability；另立 capability 只会多出一套死亡/克隆语义要维护。
 *
 * <p><b>怎么挂上去的（v20 的架构接缝）</b>：{@code BodySubStateFactory} 原先只有一个槽位，
 * 且已被理智状态占用 —— 若直接复用，后注册的会<b>覆盖</b>先注册的（理智状态静默丢失）。
 * 故本轮为工厂加了<b>具名槽位</b>通道：本类以段名 {@link #TAG_ROOT} 注册，
 * 本体按段名原样存取、互不挤占（见 {@code BodySubStateFactory} 的类注释）。
 *
 * <p><b>存档容错承诺</b>：已解锁词 id 一律以<b>字符串原样</b>读写，载入时<b>不查注册表、
 * 不校验合法性、不清理未知 id</b>（附属卸载或改 id 后存档不清档）；空/非法条目跳过、不抛异常。
 *
 * <p><b>落盘口径</b>：只在"值 ≠ 缺键默认"时写；空段整体不落盘。
 * 注意三池的默认是<b>按当前等级满值</b>（不是 0），故"池被抽干"（= 0）<b>仍会写</b>，
 * 不会与"缺键 = 满值"混淆 —— 与 {@code SanityState} 里 SAN 默认 100 而同为 0 也写是同一处理。
 */
public final class MagicState implements ForbiddenSubState {

    // ===== 口径常量（数值均为待调手感值）=====

    /** 等级下界 */
    public static final int LEVEL_MIN = 1;
    /** 等级上界（称号表 6 级：学徒 → 至秘） */
    public static final int LEVEL_MAX = 6;
    /** 等级缺失 / 非法时的默认值 */
    public static final int DEFAULT_LEVEL = LEVEL_MIN;

    /** Lv1 池容量（§2：池容量 = 1000 + 200 ×（等级 − 1）） */
    public static final float BASE_POOL_CAPACITY = 1000f;
    /** 每级增加的池容量 */
    public static final float POOL_CAPACITY_PER_LEVEL = 200f;

    /** 过载热值区间（§24.1） */
    public static final float HEAT_MIN = 0f;
    public static final float HEAT_MAX = 100f;

    /**
     * 热值比较的容差。
     *
     * <p>为什么不用 {@code !=} 精确比较：散热 / 加热将来可能不是整数（如按 tick 摊成 −0.5），
     * 浮点累积误差会让"值其实没变"也判定为变化 ⇒ 每 tick 打脏 ⇒ 同步包风暴。
     * 容差把这类抖动挡在源头。
     */
    private static final float HEAT_EPSILON = 1e-4f;

    /** 过载罚站时长（秒 · §24.1） */
    public static final int OVERLOAD_DURATION_SECONDS = 5;

    /** 已解锁词的数量防御上限（防坏档/恶意包写入超大表；§16.2） */
    public static final int WORDS_CAPACITY = 256;

    /** 缺键默认源（§16.2；与 {@link MagicSource#DEFAULT} 同源） */
    public static final String DEFAULT_SOURCE = MagicSource.DEFAULT.id();

    // ===== NBT 键（躯体 capability 下的嵌套段 + 段内键）=====

    /** 嵌套段名：与 {@code sanity} / {@code mechanical_integration} 同级 */
    public static final String TAG_ROOT = "magic";
    private static final String TAG_LEVEL = "level";
    private static final String TAG_WORDS = "words";
    private static final String TAG_POOL_LIFE = "pool_life";
    private static final String TAG_POOL_DARK = "pool_dark";
    private static final String TAG_POOL_WHITE = "pool_white";
    private static final String TAG_SOURCE = "source";
    private static final String TAG_HEAT = "heat";
    private static final String TAG_ID = "id";

    /** 同步时刻初值：取负数保证登录后第一 tick 就满足最小间隔（不落盘） */
    private static final long NO_SYNC_YET = -1000L;

    // ===== 状态字段 =====

    private int level = DEFAULT_LEVEL;

    /**
     * 已解锁词 id（<b>原样保留未知 id</b>）。
     *
     * <p>用 {@link LinkedHashSet} 而非 List：写入去重（同一词重复解锁是常态，不该在存档里堆重复项），
     * 同时保持插入序（落盘与手册目录可复现）。
     */
    private final Set<String> words = new LinkedHashSet<>();

    /**
     * 三池当前值（上限随等级变化，见 {@link #poolCapacity()}）。
     *
     * <p><b>初值 = Lv1 满值</b>，与"缺键默认 = 按当前等级满值"（§16.2）必须一致 ——
     * 若这里是 0，则"新建实例"与"读空档"会给出两种不同的状态：
     * 新玩家（还没落过盘）会看到空池、且默认态也会往存档里写三个 pool 键。
     */
    private float poolLife = BASE_POOL_CAPACITY;
    private float poolDark = BASE_POOL_CAPACITY;
    private float poolWhite = BASE_POOL_CAPACITY;

    /** 当前源 id（未知 id 在读取时回退默认，但<b>原样落盘</b>，附属装回后仍能接续） */
    private String source = DEFAULT_SOURCE;

    /*
     * 为什么必须落盘：热值上调后"重登清热"会变成一种刷法（放完大招就重登），
     * 故与等级/池同样持久化，只在死亡时归零。
     */
    private float heat = HEAT_MIN;

    /**
     * 过热<b>阈值</b>乘子（运行期，不落盘）：载体词条（寂杖端 +20%）按比例乘在 HEAT 上限上（定案 14）。
     * 由载体结算层每秒刷新；默认 1 = 原版 100 阈值。
     */
    private volatile float heatThresholdMul = 1f;

    /**
     * 过载罚站的解除时刻（{@code getGameTime()}；0 = 未处于过载）。
     *
     * <p><b>不落盘</b>：罚站最长 5 秒，属于瞬态；重登即解除在体感上等价于"那一小会儿没在施法"，
     * 而落盘它反而要处理"跨会话的时间基准"。{@link #clearTemporaries()} 会一并清掉 ——
     * 否则玩家死亡重生后还得站着"罚"完剩下的秒数（热值已归零，罚站却还在，属于自相矛盾）。
     */
    private long overloadUntil;

    /**
     * 冷却结束时刻（{@code getGameTime()}；0 = 不在冷却）。
     *
     * <p><b>不落盘</b>：冷却最长十几秒（8 词 ×3.94 约 15.8s），属瞬态；
     * 落盘还要处理"跨会话时间基准"，而重登即解除冷却在体感上等价于"下线那会儿冷却早过了"。
     * 与 {@link #overloadUntil} 同样在 {@link #clearTemporaries()} 中清掉。
     */
    private long cooldownUntil;

    // ===== 同步簿记（运行期，不落盘）=====

    /** 数值是否已变化待下发；初值 true ⇒ 登录后必推一次，客户端镜像不会长期停在默认值 */
    private boolean syncDirty = true;
    private long lastSyncTick = NO_SYNC_YET;

    // ------------------------------------------------------------------
    // 等级
    // ------------------------------------------------------------------

    public int level() {
        return level;
    }

    /** 写入等级（夹取到 [1, 6]）；变化时打脏 */
    public void setLevel(int value) {
        int clamped = Math.max(LEVEL_MIN, Math.min(LEVEL_MAX, value));
        if (this.level != clamped) {
            this.level = clamped;
            // 升级会抬高池上限：把三池一并夹进新上限（降级则不会超限，无需处理）
            pressPoolsIntoCapacity();
            markDirty();
        }
    }

    // ------------------------------------------------------------------
    // 已解锁词
    // ------------------------------------------------------------------

    /**
     * 解锁一个词（幂等）。
     *
     * @return true 仅当<b>真的新增</b>（据此打脏；重复解锁返回 false）
     */
    public boolean unlockWord(String wordId) {
        if (wordId == null || wordId.isEmpty() || words.size() >= WORDS_CAPACITY) {
            return false;
        }
        if (words.add(wordId)) {
            markDirty();
            return true;
        }
        return false;
    }

    /** 该词是否已解锁（未知 id 返回 false —— 词表是闭集，未知即未解锁） */
    public boolean hasWord(String wordId) {
        return wordId != null && words.contains(wordId);
    }

    /** 调试用：清空已解锁词（回到未解锁态）。 */
    public void clearWords() {
        if (!words.isEmpty()) {
            words.clear();
            markDirty();
        }
    }

    /** 已解锁词 id（只读视图，含未知 id） */
    public Set<String> words() {
        return Collections.unmodifiableSet(words);
    }

    public int wordCount() {
        return words.size();
    }

    // ------------------------------------------------------------------
    // 三池
    // ------------------------------------------------------------------

    /** 当前等级的池容量上限（§2：1000 + 200 ×（等级 − 1）⇒ Lv1 1000 / Lv6 2000） */
    public float poolCapacity() {
        return BASE_POOL_CAPACITY + POOL_CAPACITY_PER_LEVEL * (level - LEVEL_MIN);
    }

    public float poolLife() {
        return poolLife;
    }

    public float poolDark() {
        return poolDark;
    }

    public float poolWhite() {
        return poolWhite;
    }

    /**
     * 按索引写三池（0 = 生命 / 1 = 黑暗 / 2 = 白色），写入前夹取到 [0, 容量]。
     *
     * <p>用索引而不是三个 setter：三池在多处需要"按同一规则逐个处理"（结算、同步、读档），
     * 索引化后那些地方不必写三遍分支。
     */
    public void setPool(int index, float value) {
        float clamped = clampPool(value);
        switch (index) {
            case 0 -> {
                if (poolLife != clamped) {
                    poolLife = clamped;
                    markDirty();
                }
            }
            case 1 -> {
                if (poolDark != clamped) {
                    poolDark = clamped;
                    markDirty();
                }
            }
            case 2 -> {
                if (poolWhite != clamped) {
                    poolWhite = clamped;
                    markDirty();
                }
            }
            default -> throw new IllegalArgumentException("pool index must be 0..2, got " + index);
        }
    }

    /** 按索引读三池 */
    public float pool(int index) {
        return switch (index) {
            case 0 -> poolLife;
            case 1 -> poolDark;
            case 2 -> poolWhite;
            default -> throw new IllegalArgumentException("pool index must be 0..2, got " + index);
        };
    }

    /** 夹取：非有限值按 0；其余夹到 [0, 容量]（坏存档不会读出负池或超容） */
    public float clampPool(float value) {
        if (!Float.isFinite(value) || value < 0f) {
            return 0f;
        }
        return Math.min(poolCapacity(), value);
    }

    /** 把三池夹进当前容量（升级时容量变大不会截断，降级/改档时才会） */
    private void pressPoolsIntoCapacity() {
        poolLife = clampPool(poolLife);
        poolDark = clampPool(poolDark);
        poolWhite = clampPool(poolWhite);
    }

    // ------------------------------------------------------------------
    // 源
    // ------------------------------------------------------------------

    /** 当前源 id（原样，可能是未知 id） */
    public String sourceId() {
        return source;
    }

    /** 当前源（未知 id 回退默认 —— 载体接管后迁移用；容错口径见 §16.2） */
    public MagicSource source() {
        return MagicSource.byIdOrDefault(source);
    }

    /** 写入源 id（空值忽略；原样保留未知 id，不清档） */
    public void setSourceId(String id) {
        if (id == null || id.isEmpty() || id.equals(this.source)) {
            return;
        }
        this.source = id;
        markDirty();
    }

    // ------------------------------------------------------------------
    // 过载热值
    // ------------------------------------------------------------------

    /** 当前生效的过热阈值（= HEAT 上限 × 载体乘子；寂杖端 1.2 ⇒ 120） */
    public float heatLimit() {
        return HEAT_MAX * heatThresholdMul;
    }

    /** 载体结算层刷新阈值乘子（夹取 [0.1, 3]，与词条总系数同一夹取区间） */
    public void setHeatThresholdMul(float value) {
        float clamped = Float.isFinite(value) ? Math.max(0.1f, Math.min(3f, value)) : 1f;
        this.heatThresholdMul = clamped;
    }

    public float heat() {
        return heat;
    }

    /** 写入热值（夹取到 [0, 阈值]，非有限值按 0）；变化超过容差时打脏 */
    public void setHeat(float value) {
        float clamped = Float.isFinite(value) ? Math.max(HEAT_MIN, Math.min(heatLimit(), value)) : HEAT_MIN;
        if (Math.abs(this.heat - clamped) > HEAT_EPSILON) {
            this.heat = clamped;
            markDirty();
        }
    }

    /** 累加热值（自动夹取；入热为正、散热为负） */
    public void addHeat(float delta) {
        if (delta != 0f) {
            setHeat(heat + delta);
        }
    }

    /** 热值是否已达过载<b>阈值</b>（阈值随载体乘子浮动）—— 描述"现在该不该触发过载" */
    public boolean overheated() {
        return heat >= heatLimit();
    }

    /**
     * 当前是否<b>处于过载罚站</b>（§24.1）。施法入口据此拒绝并返回 {@code CastResult.OVERLOADED}。
     *
     * <p>与 {@link #overheated()} 的区别：后者是"热值满了"这一瞬时条件，
     * 本方法是"罚站还没结束"这一<b>持续状态</b> —— 罚站期间即使热值已降回 100 以下，仍不可施法。
     */
    public boolean overloaded(long gameTime) {
        return gameTime < overloadUntil;
    }

    /**
     * 进入过载罚站（§24.1）：把解除时刻设为 {@code gameTime + 时长}。
     *
     * <p>取"更晚者"而不是直接覆盖：连续两次触发时不应把已有的罚站<b>缩短</b>。
     */
    public void markOverloaded(long gameTime, int durationSeconds) {
        long until = gameTime + Math.max(1, durationSeconds) * 20L;
        if (until > overloadUntil) {
            overloadUntil = until;
            markDirty();
        }
    }

    /** 立即解除过载（热值降到 0 时由结算层调用；也用于调试） */
    public void clearOverload() {
        if (overloadUntil != 0L) {
            overloadUntil = 0L;
            markDirty();
        }
    }

    // ------------------------------------------------------------------
    // 冷却（§21.1：可与 IDLE 并存，仅为时间戳，不阻塞其他状态）
    // ------------------------------------------------------------------

    /** 当前是否在冷却中（§21.1；冷却起算点 = 本次施法不再需要玩家参与的那一刻） */
    public boolean onCooldown(long gameTime) {
        return gameTime < cooldownUntil;
    }

    /**
     * 开始冷却（取"更晚者"，避免并发施法把已有冷却缩短）。
     *
     * @param seconds 冷却时长；非正数视为清除
     */
    public void startCooldown(long gameTime, float seconds) {
        long until = seconds <= 0f ? 0L : gameTime + (long) Math.ceil(seconds * 20f);
        if (until > cooldownUntil) {
            cooldownUntil = until;
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

    /** 任一数值变化时打脏（同步层据此决定是否下发） */
    public void markDirty() {
        this.syncDirty = true;
    }

    public void markSynced(long gameTime) {
        this.syncDirty = false;
        this.lastSyncTick = gameTime;
    }

    // ------------------------------------------------------------------
    // IBodySubState
    // ------------------------------------------------------------------

    /**
     * 死亡克隆时的瞬态清理（§16.2）：
     * <b>热值归零</b>；等级 / 已解锁词 / 三池 / 源<b>一律保留</b>
     * （与 SAN "保留死亡瞬间值"口径一致；换维度不调本方法，故走过传送门不会回池）。
     */
    @Override
    public void clearTemporaries() {
        boolean dirty = false;
        if (heat != HEAT_MIN) {
            heat = HEAT_MIN;
            dirty = true;
        }
        // 罚站与热值同生共死：热值已归零却还在"罚"是自相矛盾的
        if (overloadUntil != 0L) {
            overloadUntil = 0L;
            dirty = true;
        }
        // 冷却同属瞬态：复活后不该还接着上一次的冷却
        if (cooldownUntil != 0L) {
            cooldownUntil = 0L;
            dirty = true;
        }
        if (dirty) {
            markDirty();
        }
    }

    /** 落盘：只在有值时写入（缺键 ⇔ 内置默认） */
    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (level != DEFAULT_LEVEL) {
            tag.putInt(TAG_LEVEL, level);
        }
        if (!words.isEmpty()) {
            ListTag list = new ListTag();
            for (String id : words) {
                if (id == null || id.isEmpty()) {
                    continue;
                }
                CompoundTag entry = new CompoundTag();
                entry.putString(TAG_ID, id);
                list.add(entry);
            }
            if (!list.isEmpty()) {
                tag.put(TAG_WORDS, list);
            }
        }
        // 三池的缺键默认 = 当前等级满值，故"0（被抽干）"与"满值"必须区分 —— 两者都写
        float capacity = poolCapacity();
        if (poolLife != capacity) {
            tag.putFloat(TAG_POOL_LIFE, poolLife);
        }
        if (poolDark != capacity) {
            tag.putFloat(TAG_POOL_DARK, poolDark);
        }
        if (poolWhite != capacity) {
            tag.putFloat(TAG_POOL_WHITE, poolWhite);
        }
        if (!DEFAULT_SOURCE.equals(source)) {
            tag.putString(TAG_SOURCE, source);
        }
        if (heat != HEAT_MIN) {
            tag.putFloat(TAG_HEAT, heat);
        }
        return tag;
    }

    /**
     * 读档：缺键取内置默认，未知/非法条目跳过。
     *
     * <p><b>顺序敏感</b>：必须先读 {@code level}，再读三池 —— 三池的默认值是"按当前等级满值"，
     * 若先设池再设等级，会把池夹在旧等级（Lv1 = 1000）的容量上，导致 Lv6 玩家读档后池被截断。
     */
    @Override
    public void load(CompoundTag tag) {
        if (tag == null) {
            return;
        }
        // ① 等级先落地（池容量依赖它）
        level = tag.contains(TAG_LEVEL)
                ? Math.max(LEVEL_MIN, Math.min(LEVEL_MAX, tag.getInt(TAG_LEVEL)))
                : DEFAULT_LEVEL;
        float capacity = poolCapacity();

        // ② 三池：缺键 = 满值；越界/非法一律夹取
        poolLife = tag.contains(TAG_POOL_LIFE) ? clampPool(tag.getFloat(TAG_POOL_LIFE)) : capacity;
        poolDark = tag.contains(TAG_POOL_DARK) ? clampPool(tag.getFloat(TAG_POOL_DARK)) : capacity;
        poolWhite = tag.contains(TAG_POOL_WHITE) ? clampPool(tag.getFloat(TAG_POOL_WHITE)) : capacity;

        // ③ 已解锁词：字符串原样进集合（不查注册表、不清未知 id）
        words.clear();
        ListTag list = tag.getList(TAG_WORDS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof CompoundTag entry)) {
                continue;
            }
            String id = entry.getString(TAG_ID);
            if (!id.isEmpty() && words.size() < WORDS_CAPACITY) {
                words.add(id);
            }
        }

        // ④ 源 / 热值：缺键回默认（源原样保留未知 id，读取时才回退）
        source = tag.contains(TAG_SOURCE) && !tag.getString(TAG_SOURCE).isEmpty()
                ? tag.getString(TAG_SOURCE) : DEFAULT_SOURCE;
        heat = tag.contains(TAG_HEAT)
                ? Math.max(HEAT_MIN, Math.min(HEAT_MAX, tag.getFloat(TAG_HEAT))) : HEAT_MIN;

        // 读档后必推一次（客户端可能刚上线，需要权威快照）
        markDirty();
    }
}
