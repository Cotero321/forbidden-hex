package com.example.akaishi.forbidden.magic.visual;

import com.example.akaishi.forbidden.magic.table.MagicDeliveryTable;
import com.example.akaishi.forbidden.magic.table.MagicEffectTable;
import com.example.akaishi.forbidden.magic.table.MagicIds;
import com.example.akaishi.forbidden.magic.table.MagicSource;
import com.example.akaishi.forbidden.magic.table.MagicWord;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 法术视觉的<b>规格表</b>（设计记忆 §7「先图后码」/ §19.5 / §19.3 / §2）。
 *
 * <p><b>为什么是四张表而不是一套画法</b>：法术的长相由四个正交维度决定 ——
 * <ol>
 *   <li>{@link Form}：<b>效果形态</b>由 E 决定（20 条，逐条不同）；</li>
 *   <li>{@link Carrier}：<b>载体形态</b>由 X 决定（7 条）；</li>
 *   <li>{@link Source}：<b>能量体质</b>由源决定（4 条）；</li>
 *   <li><b>识别色</b>由墨决定（11 条，直接读墨表的 {@code rgb()}，不在这里再抄一份）。</li>
 * </ol>
 * 四者<b>相乘</b>之后，"全部惯用一个模型"在结构上就不可能发生 ——
 * 换一个源、换一个墨、换一种交付方式，外观都会变。
 * 再加上每个词自带的 {@code glyph()}（51 个各不相同的符号）刻在术式上，
 * <b>每一句法术</b>都是独一份的。
 *
 * <p><b>与数值表的关系</b>：这里只描述"长什么样"，一个字都不参与结算。
 * 数值的真源仍是 §8.2 的 E 表 / §21.2 的 X 表 / §3.1 的墨表 —— 本表是它们的<b>视觉对偶</b>。
 */
public final class SpellVisualSpecs {

    /**
     * 一条 E 的视觉规格。
     *
     * @param kind     几何原型（画什么零件）
     * @param layers   层数（同心环数 / 圈数）
     * @param spin     自转速度（度/tick；负 = 反转，用来区分"封印 / 开启"这类对立）
     * @param pulse    脉动频率（Hz；0 = 不跳）
     * @param expand   张缩速度（+ 外扩 / − 内收 / 0 静止，格/秒）
     * @param detail   强调件数量（尖刺数 / 碎片数 / 链节数 / 旋臂数）
     * @param inverted 内外翻转（同一原型的两种极性，如「断敌增益」与「涤己负面」）
     */
    public record Form(SpellGeometryKind kind, int layers, float spin, float pulse, float expand,
            int detail, boolean inverted) {
    }

    /**
     * 一条 X 的载体规格。
     *
     * @param form      载体形态
     * @param sizeMul   尺寸倍率（阵大、印记小）
     * @param sustained 是否持续型（引导：束常亮；布场：场常存）
     * @param staggered 是否分段错开（连发：多枚沿时间轴拉开）
     */
    public record Carrier(SpellCarrierForm form, float sizeMul, boolean sustained, boolean staggered) {
    }

    /**
     * 一个源的<b>能量体质</b>。识别色不在这里 —— 它读 {@link MagicSource#rgb()}（单一真源）。
     *
     * @param alpha    透明度乘子（灰源最"虚"、生命源最"实"）
     * @param pulseMul 脉动倍率（生命源跳得快、暗源缓慢涌动）
     * @param sizeMul  体积乘子
     */
    public record Source(float alpha, float pulseMul, float sizeMul) {
    }

    /**
     * 一个<b>修饰词</b>对几何的加成（只列 D 组 10 词，它们不携带 E/X、纯改形态）。
     *
     * @param extraLayers 附加层数
     * @param extraDetail 附加强调件
     * @param sizeMul     尺寸乘子
     * @param alphaMul    透明度乘子（「角」隐蔽 ⇒ 变淡）
     * @param spinMul     自转乘子（负 = 反向）
     */
    public record Modifier(int extraLayers, int extraDetail, float sizeMul, float alphaMul, float spinMul) {
    }

    // ==================== E → 形态（20 行，逐条不同） ====================

    private static final Map<String, Form> FORMS;

    // ==================== X → 载体（7 行） ====================

    private static final Map<String, Carrier> CARRIERS;

    // ==================== 源 → 体质（4 行） ====================

    private static final Map<String, Source> SOURCES;

    // ==================== 修饰词 → 加成（D 组 10 词） ====================

    private static final Map<String, Modifier> MODIFIERS;

    static {
        Map<String, Form> f = new LinkedHashMap<>();
        // —— 伤害与破防：四者同属"破坏"，但形态意象完全不同 ——
        // 伤害＝尖锐放射（刺出去）
        f.put(MagicIds.Effect.DAMAGE, new Form(SpellGeometryKind.SPIKE, 1, 6f, 0f, 0f, 8, false));
        // 解锁＝环向外张开
        f.put(MagicIds.Effect.BREAK_UNLOCK, new Form(SpellGeometryKind.RING, 2, 25f, 0f, 0.6f, 0, false));
        // 终止＝横向一刀（弧），急收
        f.put(MagicIds.Effect.BREAK_TERMINATE, new Form(SpellGeometryKind.ARC, 1, 0f, 0f, -0.4f, 2, false));
        // 破防＝碎成片飞散
        f.put(MagicIds.Effect.BREAK_SHIELD, new Form(SpellGeometryKind.SHARD, 1, 12f, 0f, 1.2f, 12, false));

        // —— 位移：推＝柱状推进；传送＝环急收（消失）；聚拢＝内向涡 ——
        f.put(MagicIds.Effect.MOVE, new Form(SpellGeometryKind.PILLAR, 1, 0f, 0f, 0.8f, 0, false));
        f.put(MagicIds.Effect.TELEPORT, new Form(SpellGeometryKind.RIPPLE, 2, 0f, 0f, -0.9f, 0, true));
        f.put(MagicIds.Effect.GATHER, new Form(SpellGeometryKind.VORTEX, 4, 22f, 0.5f, -1.0f, 4, true));

        // —— 控制：禁锢＝链节；削弱＝倒转符环（向内、压下去）——
        f.put(MagicIds.Effect.BIND, new Form(SpellGeometryKind.CHAIN, 1, 8f, 0f, 0f, 6, false));
        f.put(MagicIds.Effect.WEAKEN, new Form(SpellGeometryKind.SIGIL, 1, 5f, 0.4f, 0f, 4, true));

        // —— 增益与防护：增幅＝上升光柱；护盾＝缓转壳体 ——
        f.put(MagicIds.Effect.BOOST, new Form(SpellGeometryKind.PILLAR, 1, 4f, 1.2f, 0.5f, 0, false));
        f.put(MagicIds.Effect.WARD, new Form(SpellGeometryKind.SHELL, 1, -6f, 0.4f, 0f, 6, false));

        // —— 心智与感知：心智＝倒转涡（扭曲）；感知＝外扩涟漪；隐蔽＝低透雾 ——
        f.put(MagicIds.Effect.MIND, new Form(SpellGeometryKind.VORTEX, 3, -18f, 0.6f, 0f, 3, true));
        f.put(MagicIds.Effect.SENSE, new Form(SpellGeometryKind.RIPPLE, 3, 0f, 1.5f, 1.0f, 0, false));
        f.put(MagicIds.Effect.CONCEAL, new Form(SpellGeometryKind.MIST, 2, 3f, 0.3f, 0f, 0, false));

        // —— 造物与连接：召唤＝正向符环；场域＝多层缓转环；连接＝电弧；时序＝延迟涟漪 ——
        f.put(MagicIds.Effect.SUMMON, new Form(SpellGeometryKind.SIGIL, 1, 14f, 0.8f, 0.4f, 6, false));
        f.put(MagicIds.Effect.FIELD, new Form(SpellGeometryKind.RING, 3, 10f, 0.5f, 0f, 0, false));
        f.put(MagicIds.Effect.LINK, new Form(SpellGeometryKind.ARC, 1, 0f, 0f, 0f, 5, false));
        f.put(MagicIds.Effect.TEMPORAL, new Form(SpellGeometryKind.RIPPLE, 2, 6f, 0.2f, 0.3f, 0, true));
        f.put(MagicIds.Effect.VESSEL, new Form(SpellGeometryKind.SHELL, 1, 2f, 0f, 0f, 4, false));

        // 代价＝内向碎解（暗红内裂）
        f.put(MagicIds.Effect.TOLL, new Form(SpellGeometryKind.SHARD, 2, -10f, 0f, -1.4f, 8, true));
        FORMS = Collections.unmodifiableMap(f);

        Map<String, Carrier> c = new LinkedHashMap<>();
        c.put(MagicIds.Delivery.INSTANT, new Carrier(SpellCarrierForm.BEAM, 1.00f, false, false));
        c.put(MagicIds.Delivery.PROJECTILE, new Carrier(SpellCarrierForm.ORB, 1.00f, false, false));
        // 引导的束比直射更粗更长（它要"按住"，视觉上得撑得住）
        c.put(MagicIds.Delivery.CHANNEL, new Carrier(SpellCarrierForm.BEAM, 1.15f, true, false));
        // 附着是挂在目标身上的小印记
        c.put(MagicIds.Delivery.ATTACH, new Carrier(SpellCarrierForm.MARK, 0.80f, false, false));
        // 布场最大（铺在地上要覆盖半径）
        c.put(MagicIds.Delivery.PLACE, new Carrier(SpellCarrierForm.FIELD, 1.30f, true, false));
        c.put(MagicIds.Delivery.ECHO, new Carrier(SpellCarrierForm.AFTERIMAGE, 0.90f, false, false));
        // 连发单枚最细（多枚错开才是它的样子）
        c.put(MagicIds.Delivery.VOLLEY, new Carrier(SpellCarrierForm.SALVO, 0.70f, false, true));
        CARRIERS = Collections.unmodifiableMap(c);

        Map<String, Source> s = new LinkedHashMap<>();
        // 灰：理智/星尘 —— 最"虚"的一档
        s.put(MagicSource.SAN.id(), new Source(0.75f, 1.0f, 1.00f));
        // 绿：生命 —— 最实、跳得最快
        s.put(MagicSource.LIFE.id(), new Source(1.00f, 1.6f, 1.05f));
        // 暗：虚空 —— 体积最大、涌动最慢
        s.put(MagicSource.DARK.id(), new Source(0.90f, 0.6f, 1.15f));
        // 白：神圣 —— 略虚但脉动明快
        s.put(MagicSource.WHITE.id(), new Source(0.85f, 1.2f, 1.00f));
        SOURCES = Collections.unmodifiableMap(s);

        Map<String, Modifier> m = new LinkedHashMap<>();
        m.put(MagicWord.JIAN.id(), new Modifier(0, 2, 0.90f, 1.00f, 1.00f));        // 尖：加锐
        m.put(MagicWord.PIAN.id(), new Modifier(0, 0, 1.00f, 0.90f, 1.80f));        // 偏：转得快、发虚
        m.put(MagicWord.CHA.id(), new Modifier(0, 1, 1.00f, 1.00f, 1.00f));         // 叉：多一簇（实例数另计）
        m.put(MagicWord.CENG.id(), new Modifier(1, 0, 1.00f, 1.00f, 1.00f));        // 层：多一层
        m.put(MagicWord.JIAO_ANGLE.id(), new Modifier(0, 0, 0.85f, 0.55f, 1.00f));  // 角：隐蔽 ⇒ 又小又淡
        m.put(MagicWord.FENG_PEAK.id(), new Modifier(0, 1, 1.20f, 1.00f, 1.10f));   // 峰：更大更亮
        m.put(MagicWord.XIE.id(), new Modifier(0, 0, 1.00f, 0.95f, -1.20f));        // 斜：反转
        m.put(MagicWord.WEI.id(), new Modifier(1, 0, 1.10f, 0.90f, 0.80f));         // 尾：留尾 ⇒ 多一层、慢下来
        m.put(MagicWord.DING_CAP.id(), new Modifier(-1, 0, 0.85f, 1.00f, 1.00f));   // 顶：封顶 ⇒ 少一层、收紧
        m.put(MagicWord.HEN.id(), new Modifier(0, 1, 1.05f, 0.80f, 1.00f));         // 痕：留痕 ⇒ 发暗
        MODIFIERS = Collections.unmodifiableMap(m);
    }

    private SpellVisualSpecs() {
    }

    /** 该 E 的形态规格；未知 id 回退 {@code DAMAGE} 的规格（绝不返回 null） */
    public static Form form(String effectId) {
        Form form = FORMS.get(effectId);
        return form != null ? form : FORMS.get(MagicIds.Effect.DAMAGE);
    }

    /** 该 X 的载体规格；未知 id 回退直射 */
    public static Carrier carrier(String deliveryId) {
        Carrier carrier = CARRIERS.get(deliveryId);
        return carrier != null ? carrier : CARRIERS.get(MagicIds.Delivery.INSTANT);
    }

    /** 该源的体质；未知 id 回退灰源 */
    public static Source source(String sourceId) {
        Source source = SOURCES.get(sourceId);
        return source != null ? source : SOURCES.get(MagicSource.SAN.id());
    }

    /** 该修饰词的加成；不是修饰词返回 {@code null}（调用方直接跳过） */
    public static Modifier modifier(String wordId) {
        return MODIFIERS.get(wordId);
    }

    // ==================== 自检 ====================

    /** E 表里是否每条都有视觉规格（自检用） */
    public static Map<String, Form> allForms() {
        return FORMS;
    }

    /** X 表里是否每条都有载体规格（自检用） */
    public static Map<String, Carrier> allCarriers() {
        return CARRIERS;
    }

    /** 覆盖度核对：E 表 / X 表里的每一条都必须有视觉规格 */
    public static java.util.List<String> inspect() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String id : MagicEffectTable.all().keySet()) {
            if (!FORMS.containsKey(id)) {
                out.add("E 缺视觉规格：" + id);
            }
        }
        for (String id : MagicDeliveryTable.all().keySet()) {
            if (!CARRIERS.containsKey(id)) {
                out.add("X 缺载体规格：" + id);
            }
        }
        for (MagicSource source : MagicSource.values()) {
            if (!SOURCES.containsKey(source.id())) {
                out.add("源缺能量体质：" + source.id());
            }
        }
        out.addAll(inspectDiversity());
        return out;
    }

    /** 至少要用到这么多种几何原型，否则就是"形态趋同" */
    private static final int MIN_DISTINCT_KINDS = 8;

    /**
     * <b>多元性不变量</b>：20 条 E 的形态必须<b>两两不同</b>，且覆盖足够多的几何原型。
     *
     * <p><b>为什么把它做成可执行断言而不是写在文档里</b>：本轮的硬要求就是
     * "不能全部惯用一个模型"。而"趋同"这件事不会报错、不会崩、性能也没有异常 ——
     * 它只会让所有法术看起来一模一样，等发现时已经写了几十条。
     * 把它变成启动即红的自检，是唯一能长期守住这条要求的办法。
     */
    private static java.util.List<String> inspectDiversity() {
        java.util.List<String> out = new java.util.ArrayList<>();

        java.util.Map<Form, String> seen = new java.util.HashMap<>();
        for (java.util.Map.Entry<String, Form> entry : FORMS.entrySet()) {
            String previous = seen.put(entry.getValue(), entry.getKey());
            if (previous != null) {
                out.add("形态趋同：E「" + previous + "」与「" + entry.getKey() + "」的视觉规格完全相同");
            }
        }

        java.util.Set<SpellGeometryKind> kinds = new java.util.HashSet<>();
        for (Form form : FORMS.values()) {
            kinds.add(form.kind());
        }
        if (kinds.size() < MIN_DISTINCT_KINDS) {
            out.add("形态趋同：只用到 " + kinds.size() + " 种几何原型（要求 ≥ " + MIN_DISTINCT_KINDS + "）");
        }
        // 载体侧不设"两两不同"：X1 直射与 X3 引导**本就该**同属"束"
        // （差异在持续/粗细则由 Carrier 的其余字段与状态机承担），强行要求不同反而是错的
        return out;
    }
}
