package com.example.akaishi.forbidden.magic.table;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>冲突 - 协同表</b>：三张子表的静态定义（设计记忆 §4.5 / §6 / §17.1 / §24.2 / §25.2）。
 *
 * <p><b>三者的判定地位完全不同，不可混为一谈</b>：
 * <ul>
 *   <li>{@link #AXES 词对立轴} —— 命中即 <b>冲突</b>：施法失败 + 扣 SAN + 短时负面（§7）；</li>
 *   <li>{@link #INK_SYNERGIES 墨协同} —— 命中即 <b>增幅</b>；管"成立之后有多强"（§6.2）；</li>
 *   <li>{@link #WORD_SYNERGIES 词协同} —— 命中即 <b>合成第三义</b>：两词不再"主从降级"，
 *       而是合成一条新机制（§24.2 规则 6）。</li>
 * </ul>
 * <p>故"对立轴词对"与"词协同词对"<b>必须互斥</b>：一个词对不能既冲突又协同。
 * 自检器会断言两个集合无交集 —— 这条不写进代码，光靠人眼迟早会写出重叠。
 *
 * <p><b>为什么词协同用稀疏白名单</b>：与 {@code E × X} 合法性（§21.4）的"黑名单"思路正好相反 ——
 * 那里要的是"未命中非法规则即合法"（穷举合法对不可行），这里要的是<b>精选特殊组合</b>
 * （穷举全部同段词对＝355 对，既写不完也没意义）。筛选原则见 §24.2。
 */
public final class MagicSynergyTable {

    /** 墨协同的每次施法触发上限（§6.2「至多 1 组」；8 词质变时见 {@link #INK_SYNERGY_CAP_ECHO}） */
    public static final int INK_SYNERGY_CAP = 1;
    /** 8 词满句（质变 A）解除后的墨协同上限（§24.3 定案） */
    public static final int INK_SYNERGY_CAP_ECHO = 3;
    /** 词协同：每次施法至多触发对数（§24.2 规则 4） */
    public static final int WORD_SYNERGY_PER_CAST_CAP = 2;
    /** 词协同的解锁词数下界（§23.4 阶四：4~5 词） */
    public static final int WORD_SYNERGY_MIN_WORDS = 4;

    /** 一条语义对立轴：命中即冲突（§7） */
    public record Axis(String name, MagicWord a, MagicWord b, String meaning) {
    }

    /**
     * 一条<b>墨</b>对立轴：命中即冲突（§6.1）。
     *
     * <p>与 {@link Axis 词对立轴} 分开建类，因为一端是 {@code MagicWord}、另一端是墨 id，
     * 强行合并只会让调用方到处做类型判断。
     */
    public record InkAxis(String name, String inkA, String inkB, String meaning) {
    }

    /** 一组墨协同：命中即增幅（§6.2） */
    public record InkSynergy(String name, String inkA, String inkB, String effect) {
    }

    /**
     * 一条词协同：两词合成一条新机制（§24.2 / §25.2）。
     *
     * @param name     协同名
     * @param role     所属段（协同只在**同段**内成立，§24.2 规则 1）
     * @param effect   机制描述（含首轮数值，**待 P4 实算标定**）
     * @param costMul  本协同生效时的消耗倍率（与 §8.5 连乘）
     * @param powerMul <b>输出轴倍率</b> —— 协同把两词<b>合成一条</b>（§24.2 规则 6），
     *                 若不给倍率，这一条就只等于"单核心"，反而弱于 6~7 词阶的<b>双核</b>（1.00 + 0.50）；
     *                 故把 §25.2 里明写的"伤害 ×1.5 / 威力 ×1.3 / 威力 ×2"等落成字段。
     *                 机制类协同（无视护甲、转移能量等）无倍率可言，取 {@code 1.0}。
     */
    public record WordSynergy(String name, MagicWord.Role role, String effect, float costMul, float powerMul) {
    }

    /** 语义对立轴（**命中即冲突**）。v10 后为 10 条（原「启↔印」「折↔柱」随 印/柱 并入而收敛） */
    public static final List<Axis> AXES = List.of(
            new Axis("开合轴", MagicWord.QI_OPEN, MagicWord.DING_NAIL, "开 ↔ 锁 / 封"),
            new Axis("连接轴", MagicWord.DUAN, MagicWord.SI, "断 ↔ 连"),
            new Axis("界域轴", MagicWord.LIE, MagicWord.JIE, "破 ↔ 封"),
            new Axis("空间轴", MagicWord.ZHE, MagicWord.JI_BASE, "折 ↔ 锚"),
            new Axis("极限轴", MagicWord.DING_CAP, MagicWord.FENG_PEAK, "封顶 ↔ 登峰"),
            new Axis("高下轴", MagicWord.FENG_PEAK, MagicWord.GU, "登峰 ↔ 放逐"),
            new Axis("起伏轴", MagicWord.QIU, MagicWord.WA, "隆 ↔ 陷"),
            new Axis("聚散轴", MagicWord.DIAN, MagicWord.LIAN, "聚 ↔ 散"),
            new Axis("显隐轴", MagicWord.JIAO_ANGLE, MagicWord.QI_FLAG, "隐 ↔ 显"),
            new Axis("动定轴", MagicWord.QIAN, MagicWord.DING_NAIL, "动 ↔ 定"));

    /**
     * <b>墨</b>对立轴（**命中即冲突** · §6.1）。与 {@link #INK_SYNERGIES 墨协同} 互斥：
     * 同一对墨不可能既冲突又协同，自检器会断言两者无交集。
     */
    public static final List<InkAxis> INK_AXES = List.of(
            new InkAxis("生机轴", MagicIds.Ink.MAN, MagicIds.Ink.SHI, "生长 ↔ 衰败"),
            new InkAxis("律法轴", MagicIds.Ink.XU, MagicIds.Ink.HUN, "秩序 ↔ 混沌"),
            new InkAxis("明暗轴", MagicIds.Ink.YAO, MagicIds.Ink.AN, "光 ↔ 暗"));

    /** 墨协同（**命中即增幅** · §6.2）。参与的两墨中至少一个须覆盖在动作段的词上，主墨恒可参与 */
    public static final List<InkSynergy> INK_SYNERGIES = List.of(
            new InkSynergy("风火", MagicIds.Ink.CHI, MagicIds.Ink.JU, "威力 ↑"),
            new InkSynergy("地生", MagicIds.Ink.RANG, MagicIds.Ink.MAN, "持续 ↑ / 范围 ↑"),
            new InkSynergy("深蚀", MagicIds.Ink.YUAN, MagicIds.Ink.SHI, "破甲 ↑ / 持续伤害 ↑"),
            new InkSynergy("圣序", MagicIds.Ink.YAO, MagicIds.Ink.XU, "净化与稳定性 ↑、耗能 ↓"),
            new InkSynergy("堕乱", MagicIds.Ink.AN, MagicIds.Ink.HUN, "负面强度 ↑"));

    private static final Map<String, WordSynergy> WORD_SYNERGIES;
    private static final Map<String, Axis> AXIS_BY_PAIR;

    static {
        Map<String, WordSynergy> w = new LinkedHashMap<>();

        // ---- A 动作段（10 对）----
        w.put(pair(MagicWord.LIU, MagicWord.DUAN), new WordSynergy("断续", MagicWord.Role.ACTION,
                "束流周期性断（每 0.5s 断一次），但单次伤害 ×1.5 ⇒ 节奏从「稳」变「顿」", 1.3f, 1.5f));
        w.put(pair(MagicWord.LIU, MagicWord.BENG), new WordSynergy("弹幕", MagicWord.Role.ACTION,
                "引导期间每 0.5s 自动追加 1 发；单发伤害 = 引导单跳 ×0.6", 1.6f, 1.0f));
        w.put(pair(MagicWord.YIN, MagicWord.QU), new WordSynergy("追魂", MagicWord.Role.ACTION,
                "投射物自动追踪（转向 30°/tick）；命中后弱点「受击 +20% / 3s」", 1.2f, 1.0f));
        w.put(pair(MagicWord.YIN, MagicWord.LIE), new WordSynergy("贯穿", MagicWord.Role.ACTION,
                "无视防护；破防层数 = ⌊P⌋ + 1", 1.4f, 1.0f));
        w.put(pair(MagicWord.QI_OPEN, MagicWord.JING), new WordSynergy("涤荡", MagicWord.Role.ACTION,
                "解除封印的同时驱散目标全部负面，并以自身为圆心范围化（半径 3 格）", 1.3f, 1.0f));
        w.put(pair(MagicWord.DUAN, MagicWord.JI_DRAW), new WordSynergy("抽离", MagicWord.Role.ACTION,
                "终止目标持续效果的同时，每终止 1 个回收 30 池量给施法者", 1.2f, 1.0f));
        w.put(pair(MagicWord.QIAN, MagicWord.ZHE), new WordSynergy("拉门", MagicWord.Role.ACTION,
                "把目标拖至施法者刚刚折跃的落点（≤8 格）", 1.4f, 1.0f));
        w.put(pair(MagicWord.DING_NAIL, MagicWord.SHE), new WordSynergy("锢缚", MagicWord.Role.ACTION,
                "定身 + 缠绕叠合；挣脱耗时取两者 max（而非相加）", 1.3f, 1.0f));
        w.put(pair(MagicWord.JIAO_CROSS, MagicWord.DUAN), new WordSynergy("短路", MagicWord.Role.ACTION,
                "干涉的两股效果立即湮灭 ⇒ 转化为一次爆发伤害 6 HP × P", 1.5f, 1.0f));
        // 注：设计记忆 §25.2 原列「标 + 迹 = 溯源」，但 标 属 A 段、迹 属 B 段 ——
        // 跨段词对违 §24.2 规则 1（协同只在同段成立），故改用同段词对「标 + 旗」，
        // 保留原意（单体标记 + 区域标记 ⇒ 无法隐匿的群体追踪）。
        w.put(pair(MagicWord.BIAO, MagicWord.QI_FLAG), new WordSynergy("标网", MagicWord.Role.ACTION,
                "单体标记 + 区域标记合一 ⇒ 范围内全部目标被持续追踪且无法隐匿，15s", 1.2f, 1.0f));

        // ---- B 作用点段（3 对）----
        w.put(pair(MagicWord.XI, MagicWord.ZHEN), new WordSynergy("刺穴", MagicWord.Role.POINT,
                "先开隙再精确穿刺 ⇒ 伤害 ×1.4 且无视护甲；弱点「受击 +20%」", 1.4f, 1.4f));
        w.put(pair(MagicWord.SI, MagicWord.GEN), new WordSynergy("根网", MagicWord.Role.POINT,
                "连接两方并持续吸收两侧能量（每秒各 20 池量）", 1.3f, 1.0f));
        w.put(pair(MagicWord.FENG_SEAM, MagicWord.GEN), new WordSynergy("寄生", MagicWord.Role.POINT,
                "裂口 DOT 的 50% 转化为自身治疗", 1.3f, 1.0f));

        // ---- C 结构段（4 对）----
        w.put(pair(MagicWord.JI_BASE, MagicWord.JIE), new WordSynergy("城郭", MagicWord.Role.STRUCTURE,
                "结界内部额外 +20% 抗冲；结界持续 ×1.5", 1.4f, 1.5f));
        w.put(pair(MagicWord.WO, MagicWord.WA), new WordSynergy("深渊", MagicWord.Role.STRUCTURE,
                "吸引 + 困住；中心 1.5 HP/秒，半径 4 格", 1.5f, 1.0f));
        w.put(pair(MagicWord.DIAN, MagicWord.XUAN), new WordSynergy("钻心", MagicWord.Role.STRUCTURE,
                "单体聚焦；威力 ×1.3 且绕轴穿透（免一次防护）", 1.4f, 1.3f));
        w.put(pair(MagicWord.HUAN, MagicWord.JI_BASE), new WordSynergy("坛城", MagicWord.Role.STRUCTURE,
                "仪式圈锚定：圈内法术强度每停留 1s +5%，上限 +30%", 1.5f, 1.0f));

        // ---- D 性质段（3 对）----
        // ★v20：「层」迁入 B 组后不再与 D 段同段，原「锥叠」协同改为「尖+斜」：
        //   尖刺穿透 + 倾斜失衡 ⇒ 刺中弱点并偏转，伤害 ×1.5
        w.put(pair(MagicWord.JIAN, MagicWord.XIE), new WordSynergy("偏锋", MagicWord.Role.NATURE,
                "尖刺穿透 + 倾斜偏转 ⇒ 命中弱点并使其失衡，伤害 ×1.5 且护甲无效", 1.0f, 1.5f));
        // ★v20：「尾」迁入 B 组后不再与 D 段同段，原「潜影」协同改为「角+顶」：
        //   隐匿 + 封顶 ⇒ 潜伏封禁，威力 ×2 且必定命中
        w.put(pair(MagicWord.JIAO_ANGLE, MagicWord.DING_CAP), new WordSynergy("潜封", MagicWord.Role.NATURE,
                "隐匿结束瞬间本次法术威力 ×2 且必定命中", 1.4f, 2.0f));
        w.put(pair(MagicWord.PIAN, MagicWord.XIE), new WordSynergy("乱流", MagicWord.Role.NATURE,
                "目标命中与效果 −40%；且随机攻击邻近", 1.3f, 1.0f));

        WORD_SYNERGIES = Collections.unmodifiableMap(w);

        Map<String, Axis> a = new LinkedHashMap<>();
        for (Axis axis : AXES) {
            a.put(pair(axis.a(), axis.b()), axis);
        }
        AXIS_BY_PAIR = Collections.unmodifiableMap(a);
    }

    private MagicSynergyTable() {
    }

    /**
     * 词对的<b>无序</b>键（内部按 id 字典序归一）。
     * 归一的意义：{@code 引+裂} 与 {@code 裂+引} 必须是同一条，否则查表会漏。
     */
    private static String pair(MagicWord a, MagicWord b) {
        return a.id().compareTo(b.id()) <= 0 ? a.id() + "+" + b.id() : b.id() + "+" + a.id();
    }

    /** 两词是否命中语义对立轴（命中即冲突） */
    public static Axis axisOf(MagicWord a, MagicWord b) {
        return AXIS_BY_PAIR.get(pair(a, b));
    }

    /** 取词协同条目；无则返回 {@code null}（调用方回退到 §5「主从降级」或双核） */
    public static WordSynergy wordSynergyOf(MagicWord a, MagicWord b) {
        return WORD_SYNERGIES.get(pair(a, b));
    }

    /** 全部词协同（插入序 = 段序 A→B→C→D） */
    public static Map<String, WordSynergy> wordSynergies() {
        return WORD_SYNERGIES;
    }

    /** 取墨协同条目；无则返回 {@code null}（其余组合中性） */
    public static InkSynergy inkSynergyOf(String inkA, String inkB) {
        for (InkSynergy s : INK_SYNERGIES) {
            if ((s.inkA().equals(inkA) && s.inkB().equals(inkB))
                    || (s.inkA().equals(inkB) && s.inkB().equals(inkA))) {
                return s;
            }
        }
        return null;
    }
}
