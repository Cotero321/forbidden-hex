package com.example.akaishi.forbidden.magic.table;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 魔法侧<b>静态表自检器</b>（纯校验核心，无任何 Minecraft 依赖 ⇒ 可单测、可两加载器共用）。
 *
 * <p>平台侧入口是 {@code AkaishiMagicRegistryChecker}（dev 环境 {@code -Dakaishi.gametest=1}
 * 于服务端启动后调用），它只负责传参与本表无关的日志汇总 —— 校验逻辑全部在这里。
 *
 * <p><b>为什么结果做成返回值而不是只打日志</b>：日志是副作用，测试断言不了它。
 * 这里 {@link #inspect()} 返回<b>违例消息清单</b>（纯函数、无副作用）；
 * 打印由平台侧的入口 {@code AkaishiMagicRegistryChecker} 负责（它把结果刷进日志）。
 * 于是同一套规则既能被自检断言，也能在 dev 启动时输出 —— 且本类不依赖任何日志实现。
 *
 * <p><b>为什么要把"能自动查的"全查一遍</b>：RULES §0 的三维铁律里，"注册 / 生效"靠代码结构保证，
 * 而"不矛盾"只能靠断言。设计记忆里明写过的约束（如"对立轴与词协同必须互斥"、
 * "加词不得降低总输出"）若只躺在文档里，迟早会被后续改动打破。
 */
public final class MagicTableValidator {

    // 表规模（故意写成常量而非"从表里取" —— 这样"少了一行"才会被查出来）
    private static final int EXPECT_EFFECTS = 20;
    private static final int EXPECT_DELIVERIES = 7;
    private static final int EXPECT_WORDS = 51;
    private static final int EXPECT_INKS = 11;
    private static final int EXPECT_SOURCES = 4;

    // 词分组计数（★v20：8 个 unsupported 词迁入 B 组后的新分布）
    // A 动作 19 / B 作用点 14 / C 结构 11 / D 性质 7；合计 51
    private static final int EXPECT_ROLE_ACTION = 19;
    private static final int EXPECT_ROLE_POINT = 14;
    private static final int EXPECT_ROLE_STRUCTURE = 11;
    private static final int EXPECT_ROLE_NATURE = 7;

    /** 词权重的合理区间（超出即多半是手滑多打/少打一位） */
    private static final float WEIGHT_MIN = 0.5f;
    private static final float WEIGHT_MAX = 1.6f;

    /** 词 id 允许的字符（小写字母、数字、下划线 —— 与命名空间 id 的路径段兼容） */
    private static final String ID_PATTERN = "[a-z][a-z0-9_]*";

    private MagicTableValidator() {
    }

    /**
     * 跑完全部校验（纯函数，无副作用、不打日志）。
     *
     * @return 违例消息清单；**空列表 = 全部通过**
     */
    public static List<String> inspect() {
        List<String> out = new ArrayList<>();
        checkWords(out);
        checkInks(out);
        checkSources(out);
        checkSynergies(out);
        checkScaling(out);
        return out;
    }

    // ==================== 规模 + 词 ====================

    /** 表规模核对（少一行 / 多一行都必须报出来，否则"表被改小了"是静默的）+ 词字段核对 */
    private static void checkWords(List<String> out) {
        size(out, "E 表", MagicEffectTable.size(), EXPECT_EFFECTS);
        size(out, "X 表", MagicDeliveryTable.size(), EXPECT_DELIVERIES);
        size(out, "词表", MagicWord.values().length, EXPECT_WORDS);
        size(out, "墨表", MagicInkTable.size(), EXPECT_INKS);
        size(out, "源表", MagicSource.values().length, EXPECT_SOURCES);

        Set<String> ids = new HashSet<>();
        Set<String> glyphs = new HashSet<>();
        Map<MagicWord.Role, Integer> byRole = new HashMap<>();

        for (MagicWord w : MagicWord.values()) {
            // id 合法且唯一
            if (w.id() == null || !w.id().matches(ID_PATTERN)) {
                out.add("词 id 非法：" + w.id() + "（须匹配 " + ID_PATTERN + "）");
            } else if (!ids.add(w.id())) {
                out.add("词 id 重复：" + w.id());
            }
            // 符号唯一（法阵靠符号读句，重复即歧义）
            if (!glyphs.add(w.glyph())) {
                out.add("符号重复：" + w.glyph() + "（" + w.id() + "）");
            }
            byRole.merge(w.role(), 1, Integer::sum);

            // 参数修改器不得携带交付方式
            if (w.isModifier()) {
                if (w.delivery() != null) {
                    out.add("参数修改器「" + w.id() + "」不应携带 X");
                }
                continue;
            }

            // (E, X) 交叉引用必须可解析 —— 拦"词指向了不存在的效果 / 交付"
            if (MagicEffectTable.get(w.effect()) == null) {
                out.add("未知 E：「" + w.id() + "」引用 " + w.effect());
            }
            boolean tollLike = MagicIds.Effect.TOLL.equals(w.effect());
            if (w.delivery() == null) {
                if (!tollLike) {
                    out.add("缺 X：「" + w.id() + "」的 E=" + w.effect() + " 非 TOLL 类，必须给出交付方式");
                }
            } else if (MagicDeliveryTable.get(w.delivery()) == null) {
                out.add("未知 X：「" + w.id() + "」引用 " + w.delivery());
            }

            weight(out, w, "wP", w.wP());
            weight(out, w, "wD", w.wD());
            weight(out, w, "wR", w.wR());
        }

        roleCount(out, byRole, MagicWord.Role.ACTION, EXPECT_ROLE_ACTION);
        roleCount(out, byRole, MagicWord.Role.POINT, EXPECT_ROLE_POINT);
        roleCount(out, byRole, MagicWord.Role.STRUCTURE, EXPECT_ROLE_STRUCTURE);
        roleCount(out, byRole, MagicWord.Role.NATURE, EXPECT_ROLE_NATURE);
    }

    private static void size(List<String> out, String name, int actual, int expected) {
        if (actual != expected) {
            out.add("表规模：「" + name + "」有 " + actual + " 条，应为 " + expected + " 条");
        }
    }

    private static void weight(List<String> out, MagicWord w, String axis, float value) {
        if (value < WEIGHT_MIN || value > WEIGHT_MAX) {
            out.add("权重越界：「" + w.id() + "」" + axis + " = " + value
                    + "（应落在 " + WEIGHT_MIN + " ~ " + WEIGHT_MAX + "）");
        }
    }

    private static void roleCount(List<String> out, Map<MagicWord.Role, Integer> byRole,
            MagicWord.Role role, int expected) {
        int actual = byRole.getOrDefault(role, 0);
        if (actual != expected) {
            out.add("词分组计数：" + role + " 有 " + actual + " 词，应为 " + expected);
        }
    }

    // ==================== 墨 / 源 ====================

    /** 墨：识别色唯一（法阵 / 投射物 / HUD 共用色，撞色即"两套体系分不开"）+ 四列为正 */
    private static void checkInks(List<String> out) {
        Set<Integer> colors = new HashSet<>();
        for (MagicInkDef ink : MagicInkTable.all().values()) {
            if (!colors.add(ink.rgb())) {
                out.add("墨色重复：" + ink.id() + " = #" + Integer.toHexString(ink.rgb()) + "，与已有墨撞色");
            }
            if (ink.costMul() <= 0f || ink.cooldown() < 0f || ink.castTime() < 0f || ink.range() <= 0) {
                out.add("墨数值非法：" + ink.id() + "（四列须为正）");
            }
        }
    }

    /** 源：识别色唯一 */
    private static void checkSources(List<String> out) {
        Set<Integer> colors = new HashSet<>();
        for (MagicSource s : MagicSource.values()) {
            if (!colors.add(s.rgb())) {
                out.add("源色重复：" + s.id() + " = #" + Integer.toHexString(s.rgb()));
            }
        }
    }

    // ==================== 协同 ====================

    /**
     * 协同表：墨协同引用的墨必须存在；词协同两词必须<b>同段</b>且与对立轴<b>互斥</b>。
     *
     * <p>"互斥"是本节最要紧的一条：一个词对若既是冲突又是协同，判定链会自相矛盾。
     */
    private static void checkSynergies(List<String> out) {
        for (MagicSynergyTable.InkSynergy s : MagicSynergyTable.INK_SYNERGIES) {
            if (MagicInkTable.get(s.inkA()) == null || MagicInkTable.get(s.inkB()) == null) {
                out.add("墨协同「" + s.name() + "」引用了未知墨：" + s.inkA() + " + " + s.inkB());
            }
        }

        for (Map.Entry<String, MagicSynergyTable.WordSynergy> e : MagicSynergyTable.wordSynergies().entrySet()) {
            String[] parts = e.getKey().split("\\+", 2);
            MagicWord a = MagicWord.byId(parts[0]);
            MagicWord b = MagicWord.byId(parts[1]);
            MagicSynergyTable.WordSynergy syn = e.getValue();

            if (a == null || b == null) {
                out.add("词协同「" + syn.name() + "」引用了未知词：" + e.getKey());
                continue;
            }
            if (a.role() != syn.role() || b.role() != syn.role()) {
                out.add("词协同「" + syn.name() + "」跨段：" + a.id() + "(" + a.role() + ") 与 "
                        + b.id() + "(" + b.role() + ") 不在同一段");
            }
            MagicSynergyTable.Axis axis = MagicSynergyTable.axisOf(a, b);
            if (axis != null) {
                out.add("冲突/协同重叠：「" + a.id() + " + " + b.id() + "」既是「" + axis.name()
                        + "」轴、又建了协同「" + syn.name() + "」");
            }
            if (syn.costMul() <= 0f) {
                out.add("词协同「" + syn.name() + "」消耗倍率非法：" + syn.costMul());
            }
            // 输出倍率必须 > 0；取 0 会让协同段"合成一条"后彻底不产出（比被并入的次词还不如）
            if (syn.powerMul() <= 0f) {
                out.add("词协同「" + syn.name() + "」输出倍率非法：" + syn.powerMul());
            }
        }

        for (MagicSynergyTable.Axis axis : MagicSynergyTable.AXES) {
            if (axis.a() == axis.b()) {
                out.add("对立轴「" + axis.name() + "」两端为同一词：" + axis.a().id());
            }
        }

        // 墨侧：对立轴与协同必须无交集 —— 同一对墨不能既"冲突"又"增幅"，否则判定链自相矛盾
        Set<Set<String>> inkAxes = new HashSet<>();
        for (MagicSynergyTable.InkAxis axis : MagicSynergyTable.INK_AXES) {
            if (axis.inkA().equals(axis.inkB())) {
                out.add("墨对立轴「" + axis.name() + "」两端为同一墨：" + axis.inkA());
            }
            inkAxes.add(Set.of(axis.inkA(), axis.inkB()));
        }
        for (MagicSynergyTable.InkSynergy synergy : MagicSynergyTable.INK_SYNERGIES) {
            Set<String> pair = Set.of(synergy.inkA(), synergy.inkB());
            if (inkAxes.contains(pair)) {
                out.add("墨冲突/协同重叠：「" + synergy.inkA() + " + " + synergy.inkB()
                        + "」既是墨对立轴、又建了协同「" + synergy.name() + "」");
            }
        }
    }

    // ==================== 规模 / 阶位 / 衰减 ====================

    /**
     * 规模与阶位系数：
     * <ol>
     *   <li>阶位强度系数单调<b>不增</b>，且落在 {@code (0, 1]}；</li>
     *   <li>同源衰减曲线非递增。</li>
     * </ol>
     *
     * <p><b>为什么这里不再验"加词不得降低总输出"</b>：那条不变量曾用
     * {@code N × 威力规模 × 阶位系数} 这个<b>简化指数</b>来验，结果给出<b>假通过</b> ——
     * 简化式既不含同源衰减、也不含"轴稀释"（长句里只有 1 个词贡献某轴时，
     * 系数却按总词数打折）。它已被真实公式级的 {@code MagicFormulaSelfCheck} 取代。
     * 教训：<b>自检若验的是模型的简化版，就等于没验。</b>
     */
    private static void checkScaling(List<String> out) {
        float prevTier = Float.MAX_VALUE;

        for (int n = 1; n <= MagicScalingTable.MAX_WORDS; n++) {
            float tier = MagicScalingTable.tierMultiplier(n);
            if (tier > 1.0f || tier <= 0f) {
                out.add("阶位强度系数越界：m(" + n + ") = " + tier);
            }
            if (tier > prevTier + 1e-6f) {
                out.add("阶位强度系数非单调不增：m(" + n + ") = " + tier + " > m(" + (n - 1) + ") = " + prevTier);
            }
            prevTier = tier;
        }

        float[] decay = MagicScalingTable.SAME_SOURCE_DECAY;
        for (int i = 1; i < decay.length; i++) {
            if (decay[i] > decay[i - 1]) {
                out.add("同源衰减曲线非递减：第 " + i + " 项 " + decay[i] + " > 第 " + (i - 1) + " 项 " + decay[i - 1]);
            }
        }
    }
}
