package com.example.akaishi.forbidden.magic.formula;

import com.example.akaishi.forbidden.magic.table.MagicEffectDef;
import com.example.akaishi.forbidden.magic.table.MagicEffectTable;
import com.example.akaishi.forbidden.magic.table.MagicSource;
import com.example.akaishi.forbidden.magic.table.MagicWord;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>公式级自检</b>：在<b>真实公式</b>上验证不变量，而不是在简化模型上。
 *
 * <p><b>为什么单独有这么一个类</b>：P4 首轮用的是 {@code N × 威力规模 × 阶位系数} 这个简化指数，
 * 它既不含同源衰减、也不含"轴稀释"，于是<b>给出了假通过</b> —— 而真公式里 8 词满句的威力
 * 反而低于 5 词。这是很有代表性的一课：
 * <b>自检只验简化模型，等于没验。</b>所以这里一律<b>直接调 {@link MagicFormula}</b>。
 *
 * <p>校验项：
 * <ol>
 *   <li><b>轴不被稀释</b>：某轴只由 1 个词贡献时，加多少无关词都不得改变该轴输出；</li>
 *   <li><b>单位隔离</b>：非生命值类 E（秒 / 格 / 档 / 百分比）不得进入威力轴；</li>
 *   <li><b>异源加词不亏</b>：逐步加入不同 E 的词，总输出不得下降。</li>
 * </ol>
 */
public final class MagicFormulaSelfCheck {

    /** 用于"稀释测试"的基线词：钉（BIND，唯一贡献持续轴 3s） */
    private static final MagicWord ANCHOR = MagicWord.DING_NAIL;

    /**
     * 稀释测试的填充词：必须<b>不贡献持续轴</b>（各自 E 的 duration = 0），
     * 且两两无对立轴、无重复、每段不超过 2 个（四段容量）。
     *
     * <p>为什么不用同一个词重复填充：那会命中 §7 的「<b>同词重复</b>」冲突 ——
     * 判定链接入公式后，非法句会直接抛错。这正是"接入判定"带来的额外约束。
     *
     * <p>为什么填充词这么少（只有 6 个）：性质段（D）里 duration = 0 的只有「尖」一个
     * （偏 / 角 / 峰 / 斜 / 尾 / 顶 各自带 2~5s 持续），故本测试最多到 <b>7 词</b>。
     */
    private static final List<MagicWord> DILUTION_FILLERS = List.of(
            MagicWord.DUAN, MagicWord.ZHEN, MagicWord.FENG_SEAM, MagicWord.MEN,
            MagicWord.DIAN, MagicWord.JIAN);

    private static void checkPrimaryDelivery(List<String> out) {
        List<String> full = List.of("jian", "tou", "zhen", "xi", "beng", "cha", "dian", "bo");
        for (int count : new int[]{2, 3, 5, 8}) {
            var spell = com.example.akaishi.forbidden.magic.judge.MagicParser.parseOperationIds(full.subList(0, count), 1);
            var validation = com.example.akaishi.forbidden.magic.judge.MagicValidator.validate(spell, List.of("chi"));
            if (!spell.ok() || !validation.isOk()) {
                out.add("主词回归非法 N=" + count);
                continue;
            }
            var result = MagicFormula.calculate(spell, validation, "chi", MagicSource.LIFE, 0, 0, 0);
            if (!MagicWord.TOU.delivery().equals(result.delivery()) || result.power() <= 0
                    || !MagicWord.TOU.id().equals(result.coreWordId())) out.add("主词交付被抢 N=" + count);
        }
        if (com.example.akaishi.forbidden.magic.judge.MagicParser.parseOperationIds(List.of("tou", "cha"), 1).ok())
            out.add("纯修饰被接受为主词");
    }

    public static void main(String[] args) {
        List<String> failures = inspect();
        if (!failures.isEmpty()) throw new IllegalStateException(String.join("\n", failures));
        System.out.println("MagicFormulaSelfCheck: PASS");
    }

    private static void checkModifierConsumption(List<String> out) {
        for (MagicWord word : MagicWord.values()) {
            String kind = com.example.akaishi.forbidden.magic.judge.MagicModifierSupport.kind(word, MagicWord.TOU);
            if (kind.isEmpty()) out.add("修饰未分类 " + word.id());
        }
        // ★v20：原 6 个 unsupported 词已全部更换 E/X 并迁入 B 组，
        // "未消费修饰被拒绝"测试用例不再适用（它们现在是合法修饰词）
        for (MagicWord word : List.of(MagicWord.SI, MagicWord.GEN, MagicWord.JI_TRACE, MagicWord.JI_BASE, MagicWord.HUAN)) {
            var spell = com.example.akaishi.forbidden.magic.judge.MagicParser.parseOperationIds(List.of("tou", word.id()), 0);
            if (!com.example.akaishi.forbidden.magic.judge.MagicValidator.validate(spell, List.of("chi")).isOk())
                out.add("合法拓扑或数值结构被拒绝 " + word.id());
        }
        var spell = com.example.akaishi.forbidden.magic.judge.MagicParser.parseOperationIds(List.of("tou", "cha"), 0);
        var check = com.example.akaishi.forbidden.magic.judge.MagicValidator.validate(spell, List.of("chi"));
        var result = MagicFormula.calculate(spell, check, "chi", MagicSource.LIFE, 0, 0, 0);
        var base = MagicFormula.calculate(List.of(MagicWord.TOU), "chi", MagicSource.LIFE, 0, 0, 0);
        if (!Float.isFinite(result.cost()) || result.cost() <= base.cost()) out.add("叉没有消费溢价");
    }

    private MagicFormulaSelfCheck() {
    }

    /**
     * 跑完全部公式级校验。
     *
     * @return 违例消息清单；**空列表 = 全部通过**
     */
    public static List<String> inspect() {
        List<String> out = new ArrayList<>();
        checkNoAxisDilution(out);
        checkUnitIsolation(out);
        checkMixedGrowth(out);
        checkDoubleCore(out);
        checkSynergyNotWorse(out);
        checkPrimaryDelivery(out);
        checkModifierConsumption(out);
        return out;
    }

    /**
     * 轴稀释：锚词提供 3s 持续，其余位置填<b>不贡献持续轴</b>的词。
     * 修复前 N=8 会掉到 2.11s（被 tierMultiplier(8) 打折），这是 bug 而非"压制"。
     */
    private static void checkNoAxisDilution(List<String> out) {
        float baseline = 0f;
        int max = DILUTION_FILLERS.size() + 1;
        for (int n = 1; n <= max; n++) {
            List<MagicWord> words = new ArrayList<>();
            words.add(ANCHOR);
            words.addAll(DILUTION_FILLERS.subList(0, n - 1));
            MagicFormulaResult r = MagicFormula.calculate(words, "chi", MagicSource.LIFE, 0, 0f, 0);
            float duration = r.durationSeconds();
            if (n == 1) {
                baseline = duration;
            } else if (Math.abs(duration - baseline) > 1e-3f) {
                out.add("轴稀释：持续轴只有 1 个贡献词，N=" + n + " 时输出 " + duration
                        + "s，应恒为 " + baseline + "s");
            }
        }
    }

    /**
     * 单位隔离：威力轴只该收生命值类效果。
     * 逐词单独成句，检查"非 HP 类 E"的威力应为 0 —— 否则就是拿秒/格/档当血量加。
     */
    private static void checkUnitIsolation(List<String> out) {
        for (MagicWord word : MagicWord.values()) {
            if (word.isModifier()) {
                continue;
            }
            // TOLL 类词（源 / 痕）无 X ⇒ 不能单独成句，跳过（其单独成句应作「不存在」处理，
            // 该判定属 P5 判定链；公式层已对此抛明确异常）
            if (!com.example.akaishi.forbidden.magic.judge.MagicParser.canBeCore(word)) {
                continue;
            }
            MagicEffectDef effect = MagicEffectTable.get(word.effect());
            if (effect == null) {
                continue;
            }
            boolean hpLike = effect.unit() == MagicEffectDef.Unit.HP
                    || effect.unit() == MagicEffectDef.Unit.HP_PER_SEC;
            float power = MagicFormula.calculate(List.of(word), "chi", MagicSource.LIFE, 0, 0f, 0)
                    .power();
            if (!hpLike && power != 0f) {
                out.add("单位混用：「" + word.id() + "」的 E=" + effect.id()
                        + " 单位是 " + effect.unit() + "，不该产生威力 " + power);
            }
            if (hpLike && power <= 0f) {
                out.add("单位隔离：生命值类「" + word.id() + "」（" + effect.id()
                        + "）的威力为 " + power + "，应大于 0");
            }
        }
    }

    /**
     * 异源加词不亏：逐步加入<b>不同 E</b> 的词，总输出（各轴之和）不得下降。
     * 同源堆叠下降是 §26.3 的<b>设计意图</b>，故此处只用异源序列来验。
     */
    private static void checkMixedGrowth(List<String> out) {
        // 8 个<b>互不重复、两两无对立轴、每段恰 2 词</b>的词；E 尽量分散。
        // 动作段 [引, 钉] / 作用点段 [隙, 丝] / 结构段 [门, 环] / 性质段 [角, 峰]
        List<MagicWord> mixed = List.of(
                MagicWord.YIN, MagicWord.DING_NAIL, MagicWord.XI, MagicWord.SI,
                MagicWord.MEN, MagicWord.HUAN, MagicWord.JIAO_ANGLE, MagicWord.FENG_PEAK);

        float prev = -1f;
        for (int n = 1; n <= mixed.size(); n++) {
            MagicFormulaResult r = MagicFormula.calculate(new ArrayList<>(mixed.subList(0, n)),
                    "chi", MagicSource.LIFE, 0, 0f, 0);
            float total = r.power() + r.durationSeconds() + r.radius();
            if (n > 1 && total < prev - 1e-3f) {
                out.add("异源加词变弱：N=" + n + " 总输出 " + total + " < N=" + (n - 1) + " 的 " + prev);
            }
            prev = total;
        }
    }

    /**
     * 双核心（§23.4）：核心段 2 词在 6 词阶起，第二词应由「修饰 ×0.25」升为「第二核心 ×0.50」。
     *
     * <p>两组句子的核心段都是 `引 + 投`（同为 DAMAGE），其余填充词刻意选<b>非生命值类</b>的 E
     * （钉 / 门 / 涡 / 角），使威力轴的贡献词数恒为 2 ⇒ 两组的规模修正与阶位系数相同，
     * 差异只可能来自角色权重本身。
     */
    private static void checkDoubleCore(List<String> out) {
        // 5 词：动作段 [引, 投]（核心段 2 词，但 5 词阶未解锁双核）+ 隙(作用点) + 门(结构) + 角(性质)
        float five = MagicFormula.calculate(List.of(MagicWord.YIN, MagicWord.TOU,
                MagicWord.XI, MagicWord.MEN, MagicWord.JIAO_ANGLE),
                "chi", MagicSource.LIFE, 0, 0f, 0).power();

        // 6 词：同上 + 涡（结构段第二个词，非 HP 类）
        float six = MagicFormula.calculate(List.of(MagicWord.YIN, MagicWord.TOU,
                MagicWord.XI, MagicWord.MEN, MagicWord.WO, MagicWord.JIAO_ANGLE),
                "chi", MagicSource.LIFE, 0, 0f, 0).power();

        if (Math.abs(six - five) > 0.001f) {
            out.add("非主词被升为第二核心：5词=" + five + "，6词=" + six);
        }
        float single = MagicFormula.calculate(List.of(MagicWord.TOU), "chi", MagicSource.LIFE, 0, 0f, 0).power();
        float ward = MagicFormula.calculate(List.of(MagicWord.TOU, MagicWord.ZHAO_COVER), "chi", MagicSource.LIFE, 0, 0f, 0).power();
        if (Math.abs(single - ward) > 0.001f) out.add("护盾HP混入攻击输出");
    }

    /**
     * 词协同（§24.2 规则 6 + §25.2 的 powerMul）：协同段"合成一条"后，
     * 整句输出**不应低于**同词数的非协同写法 —— 否则 4~5 词阶解锁的协同就是负收益。
     */
    private static void checkSynergyNotWorse(List<String> out) {
        // 有协同：动作段「流 + 断」= 断续（powerMul 1.5）
        float withSynergy = MagicFormula.calculate(List.of(MagicWord.JIAN, MagicWord.LIU,
                MagicWord.DUAN, MagicWord.HUAN), "chi", MagicSource.LIFE, 0, 0f, 0).power();

        // 无协同：动作段换成「流 + 引」（非协同词对）
        float without = MagicFormula.calculate(List.of(MagicWord.JIAN, MagicWord.LIU,
                MagicWord.YIN, MagicWord.HUAN), "chi", MagicSource.LIFE, 0, 0f, 0).power();

        if (withSynergy < without - 1e-3f) {
            out.add("词协同为负收益：「流+断」威力 " + withSynergy + " 低于非协同写法的 " + without);
        }
    }
}
