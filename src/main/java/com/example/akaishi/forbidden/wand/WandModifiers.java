package com.example.akaishi.forbidden.wand;

import com.example.akaishi.forbidden.magic.cast.CastAdjustments;
import com.example.akaishi.forbidden.magic.table.MagicEffectDef;
import com.example.akaishi.forbidden.magic.table.MagicSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * <b>§10 数值合成引擎</b>：三段词条 → 一次施法的实际调整量。
 *
 * <p><b>合成口径（法杖记忆 §10.2，已定案）</b>：
 * <ol>
 *   <li>端 × 柄 × 核各自产生 {@code (1+m)} 因子，<b>相乘</b>；</li>
 *   <li>绝对项（渊池 +500）最后相加，不乘；</li>
 *   <li>总系数夹取 {@code [0.10, 3.00]}。</li>
 * </ol>
 *
 * <p><b>作用域（§10.3）</b>：墨偏向按<b>词</b>加权（该墨符文能耗 85% —— 以词数占比近似，
 * 公式层尚无逐词价格挂点）；源偏向 / 专精核按<b>源</b>；崩解/壁垒按<b>攻防类别</b>；
 * 第三发连击（30s 窗口）只作用于崩解的冷却与壁垒的防御威力。
 *
 * <p><b>生命周期</b>：一次施法一份（{@link #resolve}），实现 {@link CastAdjustments}
 * 直接喂给施法链；额外池记账字段由施法链回写。
 */
public final class WandModifiers implements CastAdjustments {

    /** 普通乘区夹取区间（定案 §10.2-3） */
    private static final float MUL_MIN = 0.10f;
    private static final float MUL_MAX = 3.00f;
    /** 效果威力的安全区间，避免多段词条叠乘失控。 */
    private static final float POWER_MIN = 0.75f;
    private static final float POWER_MAX = 1.35f;
    /** 源偏向杖端对非专精效果的平滑削弱。 */
    private static final float OFF_SCOPE_POWER = 0.96f;
    /** 三段基础效能：柄最平滑，杖端略高，杖核承担主强度。 */
    private static final float TIP_BASE_EFFICACY = 1.04f;
    private static final float HANDLE_BASE_EFFICACY = 1.03f;
    private static final float CORE_BASE_EFFICACY = 1.00f;
    private static final Set<String> MIND_EFFECTS = Set.of("mind", "sense", "conceal");
    private static final Set<String> HEAL_EFFECTS = Set.of("damage", "field", "boost", "ward");
    private static final Set<String> DEBUFF_UTILITY_EFFECTS = Set.of("field", "gather");
    private static final Set<String> DEFENSE_UTILITY_EFFECTS = Set.of("teleport", "move", "sense", "break_terminate");

    /** 额外池基础容量 = 主池上限 × 10%（定案 §0-9） */
    public static final float EXTRA_POOL_BASE_RATIO = 0.10f;
    /** 额外池基准恢复量（每 5 秒 10 点，定案 §0-7） */
    public static final float EXTRA_REGEN_AMOUNT = 10f;
    /** 额外池基准恢复间隔（秒） */
    public static final float EXTRA_REGEN_INTERVAL = 5f;

    private final List<WandPartDef> parts;
    private final MagicSource source;
    private final List<String> wordInks;
    private final List<String> involvedInks;
    private final float extraAvailable;
    private final boolean thirdCast;
    private float extraPaid;

    private WandModifiers(List<WandPartDef> parts, MagicSource source, List<String> wordInks,
            List<String> involvedInks, float extraAvailable, boolean thirdCast) {
        this.parts = parts;
        this.source = source;
        this.wordInks = wordInks;
        this.involvedInks = involvedInks;
        this.extraAvailable = extraAvailable;
        this.thirdCast = thirdCast;
    }

    // ==================== 组装 ====================

    /**
     * 从一根法杖解析出一次施法的调整量。
     *
     * @param wand           法杖物品栈（读三段 id）
     * @param source         本次施法源（咒核决定）
     * @param wordInks       逐词墨（词序）
     * @param extraAvailable 该源额外池现值
     * @param comboCount     连击计数（本次是第几发；≥3 视为"第三发连击"）
     */
    public static WandModifiers resolve(ItemStack wand, MagicSource source,
            List<String> wordInks, float extraAvailable, int comboCount) {
        List<WandPartDef> parts = new ArrayList<>(3);
        WandPartDef tip = WandTipTable.get(WandItem.tipId(wand));
        WandPartDef handle = WandHandleTable.get(WandItem.handleId(wand));
        WandPartDef core = WandCoreTable.get(WandItem.coreId(wand));
        if (tip != null) parts.add(tip);
        if (handle != null) parts.add(handle);
        if (core != null) parts.add(core);
        List<String> involved = new ArrayList<>();
        for (String ink : wordInks) {
            if (!ink.isEmpty() && !involved.contains(ink)) involved.add(ink);
        }
        return new WandModifiers(parts, source, List.copyOf(wordInks), involved, extraAvailable, comboCount >= 3);
    }

    // ==================== 原语 ====================

    /** 全局词条乘积（含夹取） */
    private float product(WandMod mod) {
        float value = 1f;
        for (WandPartDef part : parts) {
            value *= part.mod(mod);
        }
        return clamp(value);
    }

    /** 带作用域过滤的乘积：{@code scope} 空的段全源生效，否则仅命中源生效 */
    private float productWhere(WandMod mod, String sourceId) {
        float value = 1f;
        for (WandPartDef part : parts) {
            boolean applies = part.sourceId().isEmpty()
                    || (sourceId != null && part.sourceId().equals(sourceId));
            if (applies) value *= part.mod(mod);
        }
        return clamp(value);
    }

    /** 绝对值词条求和（不乘不夹） */
    private float sum(WandMod mod) {
        float value = 0f;
        for (WandPartDef part : parts) {
            value += part.mod(mod);
        }
        return value;
    }

    private static float clamp(float value) {
        return Math.max(MUL_MIN, Math.min(MUL_MAX, value));
    }

    // ==================== CastAdjustments ====================

    @Override
    public MagicSource source() {
        return source;
    }

    @Override
    public List<String> wordInks() {
        return wordInks;
    }

    @Override
    public List<String> involvedInks() {
        return involvedInks;
    }

    @Override
    public float costMul(MagicSource source, int wordCount) {
        float global = product(WandMod.COST) * productWhere(WandMod.SOURCE_COST, source.id());
        // 逐词墨折扣按词数占比加权（§2.2：只有该墨的符文吃 85%）
        if (wordCount <= 0 || wordInks.isEmpty()) {
            return global;
        }
        float weighted = 0f;
        for (String ink : wordInks) {
            float inkMul = 1f;
            for (WandPartDef part : parts) {
                if (!part.inkId().isEmpty() && part.inkId().equals(ink)) {
                    inkMul *= part.mod(WandMod.INK_COST);
                }
            }
            weighted += clamp(inkMul);
        }
        return clamp(global * (weighted / wordCount));
    }

    @Override
    public float cooldownMul(MagicSource source, MagicEffectDef.Category category, boolean thirdCast) {
        float value = product(WandMod.COOLDOWN) * productWhere(WandMod.SOURCE_COOLDOWN, source.id());
        value *= switch (category) {
            case ATTACK -> product(WandMod.ATTACK_COOLDOWN);
            case DEFENSE -> product(WandMod.DEFENSE_COOLDOWN);
            case NEUTRAL -> 1f;
        };
        if (thirdCast) value *= product(WandMod.THIRD_COOLDOWN);
        return clamp(value);
    }

    @Override
    public float castTimeMul() {
        return product(WandMod.CAST_TIME);
    }

    @Override
    public float rangeMul() {
        return product(WandMod.RANGE);
    }

    @Override
    public float radiusMul() {
        return product(WandMod.RADIUS);
    }

    @Override
    public float powerMul(String effectId, MagicEffectDef.Category category, boolean thirdCast) {
        float value = efficacyMul(effectId, category, thirdCast);
        for (WandPartDef part : parts) {
            float power = part.mod(WandMod.POWER);
            if (power == WandMod.POWER.defaultValue() || !sourceMatches(part)) continue;
            if (part.powerScope().isEmpty() || scopeHits(part.powerScope(), effectId, category)) {
                value *= power;
            } else if (part.kind() == WandPartDef.Kind.TIP) {
                value *= OFF_SCOPE_POWER;
            }
        }
        return Math.max(POWER_MIN, Math.min(POWER_MAX, value));
    }

    private float efficacyMul(String effectId, MagicEffectDef.Category category, boolean thirdCast) {
        float value = 1f;
        for (WandPartDef part : parts) {
            if (!sourceMatches(part)) continue;
            float efficacy = part.mod(WandMod.EFFICACY);
            if (efficacy == WandMod.EFFICACY.defaultValue()) {
                efficacy = switch (part.kind()) {
                    case TIP -> TIP_BASE_EFFICACY;
                    case HANDLE -> HANDLE_BASE_EFFICACY;
                    case CORE -> CORE_BASE_EFFICACY;
                };
            }
            value *= efficacy;
        }
        if (thirdCast && category == MagicEffectDef.Category.DEFENSE) {
            value *= product(WandMod.THIRD_POWER);
        }
        return Math.max(POWER_MIN, Math.min(POWER_MAX, value));
    }

    private boolean sourceMatches(WandPartDef part) {
        return part.sourceId().isEmpty() || (source != null && part.sourceId().equals(source.id()));
    }

    /** 威力作用域 → 效果 id / 类别的命中判定。 */
    private static boolean scopeHits(String scope, String effectId, MagicEffectDef.Category category) {
        if (scope == null || scope.isEmpty() || effectId == null) return false;
        return switch (scope) {
            case "mind" -> MIND_EFFECTS.contains(effectId);
            case "heal" -> HEAL_EFFECTS.contains(effectId);
            case "debuff" -> category == MagicEffectDef.Category.ATTACK
                    || DEBUFF_UTILITY_EFFECTS.contains(effectId);
            case "defense" -> category == MagicEffectDef.Category.DEFENSE
                    || DEFENSE_UTILITY_EFFECTS.contains(effectId);
            default -> false;
        };
    }

    @Override
    public float extraAvailable(MagicSource source) {
        return extraAvailable;
    }

    @Override
    public void recordExtraPaid(float amount) {
        this.extraPaid = amount;
    }

    @Override
    public float extraPaid() {
        return extraPaid;
    }

    @Override
    public boolean thirdCast() {
        return thirdCast;
    }

    // ==================== 引擎扩展（结算节拍 / 镶嵌页用）====================

    /** 额外池容量（该源）= 主池上限 × 10% × Π(1+修正) ＋ 绝对项（§10.4） */
    public static float extraPoolCapacity(List<WandPartDef> parts, float mainPoolCapacity, MagicSource source) {
        float mul = 1f;
        float flat = 0f;
        String sourceId = source.id();
        for (WandPartDef part : parts) {
            boolean applies = part.sourceId().isEmpty() || part.sourceId().equals(sourceId);
            if (!applies) continue;
            mul *= part.mod(WandMod.EXTRA_POOL);
            flat += part.mod(WandMod.EXTRA_POOL_FLAT);
        }
        return mainPoolCapacity * EXTRA_POOL_BASE_RATIO * clamp(mul) + flat;
    }

    /** 额外池恢复量（该源）= 基准 10 点 × Π(1+恢复效率)，作用于单次恢复（§10.4） */
    public static float extraRegenAmount(List<WandPartDef> parts, MagicSource source) {
        float mul = 1f;
        String sourceId = source.id();
        for (WandPartDef part : parts) {
            boolean applies = part.sourceId().isEmpty() || part.sourceId().equals(sourceId);
            if (applies) mul *= part.mod(WandMod.REGEN_EFF);
        }
        return EXTRA_REGEN_AMOUNT * clamp(mul);
    }

    /** 额外池恢复间隔（秒；词条取更小者 —— 潮汐核 3s） */
    public static float extraRegenIntervalSeconds(List<WandPartDef> parts) {
        float interval = EXTRA_REGEN_INTERVAL;
        for (WandPartDef part : parts) {
            float v = part.mod(WandMod.EXTRA_REGEN_INTERVAL);
            if (v > 0f) interval = Math.min(interval, v);
        }
        return interval;
    }

    /** 实例便利：本杖的额外池恢复间隔（秒） */
    public float extraRegenInterval() {
        return extraRegenIntervalSeconds(parts);
    }

    /** 主池自然恢复乘子（永动等词条；不含睡眠倍率） */
    public float mainRegenMul() {
        return product(WandMod.MAIN_REGEN);
    }

    /** 主池满时溢流改道额外池（蓄杖柄） */
    public boolean overflowToExtra() {
        for (WandPartDef part : parts) {
            if (part.has(WandFlag.OVERFLOW_TO_EXTRA)) return true;
        }
        return false;
    }

    /** 额外池耗尽时主池恢复翻倍（永动核） */
    public boolean mainRegenDoubleWhenExtraEmpty() {
        for (WandPartDef part : parts) {
            if (part.has(WandFlag.MAIN_REGEN_DOUBLE_WHEN_EXTRA_EMPTY)) return true;
        }
        return false;
    }

    /** 施法后返还比例（循杖柄 0.20；0 = 无） */
    public float postCastRefund() {
        return Math.max(0f, sum(WandMod.POST_CAST_REFUND));
    }

    /** 自然散热乘子（寂杖端） */
    public float heatDissipationMul() {
        return product(WandMod.HEAT_DISSIPATION);
    }

    /** 过热阈值乘子（寂杖端，比例乘在阈值上 —— 定案 14） */
    public float heatThresholdMul() {
        return product(WandMod.HEAT_THRESHOLD);
    }

    /** 三段定义（结算节拍 / 界面复用） */
    public List<WandPartDef> parts() {
        return parts;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "WandModifiers{parts=%d, source=%s, extraPaid=%.1f}",
                parts.size(), source.id(), extraPaid);
    }
}
