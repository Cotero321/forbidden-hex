package com.example.akaishi.forbidden.magic.table;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <b>E 表</b>：效果语义的静态表（20 条 · 设计记忆 §19.5 + §23.3）。
 *
 * <p>这是魔法侧所有表的<b>依赖根之一</b>：词表引它的 id，公式读它的基准，
 * {@code E × X} 合法性校验（§21.4）也以它的 id 集为域。
 *
 * <p><b>为什么用 {@link LinkedHashMap} 且插入序固定</b>：调试命令与自检器的输出要可 diff，
 * 顺序一旦随运行变化就没法比对。{@link Collections#unmodifiableMap} 包一层，
 * 任何运行时改动直接抛异常 —— 静态表就该是不可变的。
 *
 * <p><b>数值来源</b>：19 条沿用 §8.2 的基准（{@code BREAK} 三拆共用原 18 号的基准，
 * 不新增基准行 —— §20.4）；{@code WARD} 为 v10 新增，**无历史原语对应**，
 * 其基准按"与 {@code BOOST} 的治疗等量"取 6 HP，**属首轮提案，待 P4 实算标定**。
 */
public final class MagicEffectTable {

    private static final Map<String, MagicEffectDef> BY_ID;

    static {
        Map<String, MagicEffectDef> m = new LinkedHashMap<>();

        // ---- 伤害与破防（合并/三拆自 §8.2 的 1~4、18 号）----
        // ★v20 基准由 4f 上调为 6f（3 颗心）：原 4 HP 偏低（钻石剑 7 HP），法术作为主战手段应具威慑力。
        //   多词条共享改为百分比加成后，修饰词不再直接叠 baseP，而是按 POWER_BONUS_PERCENT 加成核心威力，
        //   故 baseP 的提升不会因多词叠加而失控。
        m.put(MagicIds.Effect.DAMAGE, new MagicEffectDef(
                MagicIds.Effect.DAMAGE, "造成伤害", 100, 6f, MagicEffectDef.Unit.HP,
                "6 HP 伤害", 0f, 0f, MagicEffectDef.Trend.LINEAR,
                "合并原 1/2/3/4（直射/引导/附着/连锁）；交付差异一律由 X 承担；★v20 基准 4→6",
                MagicEffectDef.Category.ATTACK));
        m.put(MagicIds.Effect.BREAK_UNLOCK, new MagicEffectDef(
                MagicIds.Effect.BREAK_UNLOCK, "解除封印 / 开启", 110, 1f, MagicEffectDef.Unit.COUNT,
                "解 1 重封锁", 0f, 0f, MagicEffectDef.Trend.LINEAR,
                "拆自原 18「启」；层数 = ⌊P⌋",
                MagicEffectDef.Category.DEFENSE));
        m.put(MagicIds.Effect.BREAK_TERMINATE, new MagicEffectDef(
                MagicIds.Effect.BREAK_TERMINATE, "终止持续效果与连接", 110, 1f, MagicEffectDef.Unit.COUNT,
                "终止 1 个持续效果", 0f, 0f, MagicEffectDef.Trend.LINEAR,
                "拆自原 18「断」；层数 = ⌊P⌋",
                MagicEffectDef.Category.NEUTRAL));
        m.put(MagicIds.Effect.BREAK_SHIELD, new MagicEffectDef(
                MagicIds.Effect.BREAK_SHIELD, "破开防御 / 结界 / 方块", 110, 6f, MagicEffectDef.Unit.COUNT,
                "对物 6 HP / 破 1 层防护", 0f, 0f, MagicEffectDef.Trend.LINEAR,
                "拆自原 18「裂」；破防层数 = ⌊P⌋",
                MagicEffectDef.Category.ATTACK));

        // ---- 位移与转移（5、6 号）----
        m.put(MagicIds.Effect.MOVE, new MagicEffectDef(
                MagicIds.Effect.MOVE, "推 / 拉目标位置", 80, 6f, MagicEffectDef.Unit.CELLS,
                "位移 6 格", 0f, 0f, MagicEffectDef.Trend.LINEAR,
                "高血量目标需 P ≥ 1.3",
                MagicEffectDef.Category.NEUTRAL));
        m.put(MagicIds.Effect.TELEPORT, new MagicEffectDef(
                MagicIds.Effect.TELEPORT, "自身 / 目标瞬时转移", 120, 8f, MagicEffectDef.Unit.CELLS,
                "传送 8 格", 0f, 0f, MagicEffectDef.Trend.LINEAR, "",
                MagicEffectDef.Category.NEUTRAL));

        // ---- 控制（7、8、10 号）----
        //
        // BIND 的 D 基准由 3s 上调为 5s（v19）：§30 接入「角色权重」后，非核心词只按 0.25 出力，
        // 控制时长被压得比接入前更低（2 词 4.03s、7 词因「锢缚」协同合成一条只剩 3.00s）。
        // 5s 是第一轮补偿值 —— 使单核心禁锢与「钉+蛇」组合都落在可感知的强控区间。
        m.put(MagicIds.Effect.BIND, new MagicEffectDef(
                MagicIds.Effect.BIND, "禁锢（禁止移动 / 束缚）", 90, 5f, MagicEffectDef.Unit.SECONDS,
                "挣脱基础 5s", 5f, 0f, MagicEffectDef.Trend.LINEAR,
                "Boss（>100 HP）需 P ≥ 1.3；★v19 基准由 3s 上调（待实测）",
                MagicEffectDef.Category.ATTACK));
        m.put(MagicIds.Effect.WEAKEN, new MagicEffectDef(
                MagicIds.Effect.WEAKEN, "削弱属性 / 放大弱点", 90, 20f, MagicEffectDef.Unit.PERCENT,
                "−20% 属性", 5f, 0f, MagicEffectDef.Trend.LINEAR,
                "下限 −60%",
                MagicEffectDef.Category.ATTACK));
        m.put(MagicIds.Effect.MIND, new MagicEffectDef(
                MagicIds.Effect.MIND, "心智（疯狂 / 认知偏离）", 110, 1f, MagicEffectDef.Unit.TIER,
                "低档（视野扭曲 + 随机转向）", 4f, 0f, MagicEffectDef.Trend.TIERED,
                "P < 1.3 低档 / P ≥ 1.3 高档（失控）",
                MagicEffectDef.Category.ATTACK));

        // ---- 增益与防护（9 号 + v10 新增 WARD）----
        m.put(MagicIds.Effect.BOOST, new MagicEffectDef(
                MagicIds.Effect.BOOST, "增幅 / 治疗", 100, 20f, MagicEffectDef.Unit.PERCENT,
                "+20% 或治疗 6 HP", 5f, 0f, MagicEffectDef.Trend.LINEAR,
                "上限 +60%",
                MagicEffectDef.Category.DEFENSE));
        m.put(MagicIds.Effect.WARD, new MagicEffectDef(
                MagicIds.Effect.WARD, "护盾 / 吸收伤害", 100, 6f, MagicEffectDef.Unit.HP,
                "吸收 6 HP", 5f, 0f, MagicEffectDef.Trend.LINEAR,
                "v10 新增（无历史原语）；基准按与 BOOST 治疗等量取 6 HP ★待标定",
                MagicEffectDef.Category.DEFENSE));

        // ---- 感知与隐匿（11、12 号）----
        m.put(MagicIds.Effect.SENSE, new MagicEffectDef(
                MagicIds.Effect.SENSE, "感知 / 暴露", 60, 1f, MagicEffectDef.Unit.TIER,
                "方位标记 10s", 10f, 8f, MagicEffectDef.Trend.TIERED,
                "P 0.8 方位 / 1.0 轮廓 / 1.2+ 详情",
                MagicEffectDef.Category.NEUTRAL));
        m.put(MagicIds.Effect.CONCEAL, new MagicEffectDef(
                MagicIds.Effect.CONCEAL, "隐匿", 70, 1f, MagicEffectDef.Unit.FACTOR,
                "被察觉距离 = 16 ÷ P", 4f, 0f, MagicEffectDef.Trend.LINEAR,
                "攻击 / 施法会短暂暴露",
                MagicEffectDef.Category.DEFENSE));

        // ---- 召唤与场地（13、14、15、16 号）----
        m.put(MagicIds.Effect.SUMMON, new MagicEffectDef(
                MagicIds.Effect.SUMMON, "召唤实体", 140, 1f, MagicEffectDef.Unit.TIER,
                "T1 投影（基准 50%）", 10f, 0f, MagicEffectDef.Trend.TIERED,
                "P 0.8 弱影 / 1.0 T1 / 1.3 T2",
                MagicEffectDef.Category.NEUTRAL));
        // ★v20 基准由 1f 上调为 2f：原 1 HP/秒在 6s 持续内仅 6 HP，几乎无威慑力。
        //   此外，持续伤害（FIELD）与传导伤害（LINK）现附加「主要目标伤害（DAMAGE baseP）的
        //   PERCENT_OF_PRIMARY × 持续秒数」作为额外伤害，使场地/链能从核心伤害中获益。
        m.put(MagicIds.Effect.FIELD, new MagicEffectDef(
                MagicIds.Effect.FIELD, "区域持续作用", 120, 2f, MagicEffectDef.Unit.HP_PER_SEC,
                "2 HP/秒 + 主伤害的 10%/秒", 6f, 4f, MagicEffectDef.Trend.LINEAR,
                "附加：每秒额外造成核心 DAMAGE 基准的 10%（见 ExecSupport）；★v20 基准 1→2",
                MagicEffectDef.Category.NEUTRAL));
        m.put(MagicIds.Effect.GATHER, new MagicEffectDef(
                MagicIds.Effect.GATHER, "聚拢 / 吸引", 90, 0.1f, MagicEffectDef.Unit.NONE,
                "吸力 0.1 格/tick", 3f, 4f, MagicEffectDef.Trend.LINEAR,
                "高血量目标需 P ≥ 1.3",
                MagicEffectDef.Category.NEUTRAL));
        m.put(MagicIds.Effect.LINK, new MagicEffectDef(
                MagicIds.Effect.LINK, "连接 / 传导", 100, 2f, MagicEffectDef.Unit.HP_PER_SEC,
                "传导 2 HP/秒 + 主伤害的 10%/跳", 5f, 8f, MagicEffectDef.Trend.LINEAR,
                "附加：每跳额外造成核心 DAMAGE 基准的 10%（见 ExecSupport）；★v20 基准 1→2",
                MagicEffectDef.Category.ATTACK));

        // ---- 时序 / 承载 / 代价（17、19、20 号）----
        m.put(MagicIds.Effect.TEMPORAL, new MagicEffectDef(
                MagicIds.Effect.TEMPORAL, "时序（延迟 / 回响）", 110, 100f, MagicEffectDef.Unit.PERCENT,
                "复现 100%", 2f, 0f, MagicEffectDef.Trend.LINEAR,
                "双模式：延迟（D 基准 2s）/ 余效（3s）",
                MagicEffectDef.Category.NEUTRAL));
        m.put(MagicIds.Effect.VESSEL, new MagicEffectDef(
                MagicIds.Effect.VESSEL, "承载", 80, 100f, MagicEffectDef.Unit.POOL,
                "承载 100 池量", 0f, 0f, MagicEffectDef.Trend.LINEAR, "",
                MagicEffectDef.Category.NEUTRAL));
        m.put(MagicIds.Effect.TOLL, new MagicEffectDef(
                MagicIds.Effect.TOLL, "代价（自伤换增幅）", 40, 1f, MagicEffectDef.Unit.PERCENT,
                "自伤 2 HP → 本法术 +30%", 0f, 0f, MagicEffectDef.Trend.LINEAR,
                "参数修改器，不占交付；自伤与增幅均 ×P",
                MagicEffectDef.Category.NEUTRAL));

        BY_ID = Collections.unmodifiableMap(m);
    }

    private MagicEffectTable() {
    }

    /** 全表（插入序固定，供自检 / 调试遍历） */
    public static Map<String, MagicEffectDef> all() {
        return BY_ID;
    }

    /** 按 id 取定义；未知 id 返回 {@code null}（调用方决定回退口径） */
    public static MagicEffectDef get(String id) {
        return BY_ID.get(id);
    }

    /** 表内条目数（自检断言用） */
    public static int size() {
        return BY_ID.size();
    }
}
