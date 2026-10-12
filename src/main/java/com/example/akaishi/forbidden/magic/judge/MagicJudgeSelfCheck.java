package com.example.akaishi.forbidden.magic.judge;

import com.example.akaishi.forbidden.magic.table.MagicIds;
import com.example.akaishi.forbidden.magic.table.MagicSynergyTable;
import com.example.akaishi.forbidden.magic.table.MagicWord;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>判定链自检</b>：在真实 Parser / Validator 上验证 §5 / §1 / §7 / §24.2 的规则。
 *
 * <p>沿用 §28.2 缺陷 5 的教训 —— <b>不写"另算一遍"的简化版</b>，全部直接调用真实实现。
 *
 * <p>校验项：
 * <ol>
 *   <li><b>四段归位</b>：乱序输入也能正确落段，段内保持原相对顺序；</li>
 *   <li><b>核心词选举</b>：按 动作 → 结构 → 作用点 → 性质 的优先级，且跳过不可作核心的词；</li>
 *   <li><b>不存在</b>：空句 / 超 8 词 / 段溢出 / 未知词 / 无核心；</li>
 *   <li><b>冲突</b>：跨段对立轴能被抓到（§4.5 有 5 条轴是跨段的）+ 同词重复 + 墨轴 + 星墨独占；</li>
 *   <li><b>协同限位</b>：4 词阶才解锁、每段至多 1 对、总数至多 2、8 词放宽墨协同到 3。</li>
 * </ol>
 */
public final class MagicJudgeSelfCheck {

    private static final List<String> NO_INK = List.of();

    private MagicJudgeSelfCheck() {
    }

    public static void main(String[] args) {
        var failures = inspect();
        if (!failures.isEmpty()) throw new IllegalStateException(String.join("\n", failures));
        System.out.println("MagicJudgeSelfCheck: PASS");
    }

    /** 跑完全部判定链校验（空列表 = 全部通过） */
    public static List<String> inspect() {
        List<String> out = new ArrayList<>();
        checkSegmentPlacement(out);
        checkCoreElection(out);
        checkNotExists(out);
        checkConflicts(out);
        checkSynergyLimits(out);
        return out;
    }

    /** ① 四段归位：输入乱序，仍应各归其段，且段内保序 */
    private static void checkSegmentPlacement(List<String> out) {
        // 输入顺序刻意打乱：钉(A) 尖(D) 丝(B) 界(C)
        ParsedSpell spell = MagicParser.parse(List.of(
                MagicWord.JIAN, MagicWord.DING_NAIL, MagicWord.JIE, MagicWord.SI));
        if (!spell.ok()) {
            out.add("归位：乱序输入被判不成立，error=" + spell.error());
            return;
        }
        if (!spell.nature().equals(List.of(MagicWord.JIAN))) {
            out.add("归位：性质段应为 [尖]，实为 " + labels(spell.nature()));
        }
        if (!spell.action().equals(List.of(MagicWord.DING_NAIL))) {
            out.add("归位：动作段应为 [钉]，实为 " + labels(spell.action()));
        }
        if (!spell.point().equals(List.of(MagicWord.SI))) {
            out.add("归位：作用点段应为 [丝]，实为 " + labels(spell.point()));
        }
        if (!spell.structure().equals(List.of(MagicWord.JIE))) {
            out.add("归位：结构段应为 [界]，实为 " + labels(spell.structure()));
        }
        if (spell.wordCount() != 4) {
            out.add("归位：词数应为 4，实为 " + spell.wordCount());
        }
    }

    /** ② 核心词选举：动作段优先于结构段，且跳过参数修改器 */
    private static void checkCoreElection(List<String> out) {
        // 动作段有词 ⇒ 核心应是动作段首词（而非结构段）
        ParsedSpell a = MagicParser.parse(List.of(MagicWord.JIE, MagicWord.YIN));
        if (a.core() != MagicWord.YIN) {
            out.add("核心选举：动作段有词时应取它，实取 " + name(a.core()));
        }

        // 动作段为空 ⇒ 落到结构段
        ParsedSpell b = MagicParser.parse(List.of(MagicWord.JIE));
        if (b.core() != MagicWord.JIE) {
            out.add("核心选举：动作段为空时应落到结构段，实取 " + name(b.core()));
        }

        // 性质段只有参数修改器（叉）⇒ 不可作核心 ⇒ 顺延到动作段
        ParsedSpell c = MagicParser.parse(List.of(MagicWord.CHA, MagicWord.YIN));
        if (c.core() != MagicWord.YIN) {
            out.add("核心选举：应跳过参数修改器「叉」，实取 " + name(c.core()));
        }

        // 整句只有参数修改器 ⇒ 无核心
        ParsedSpell d = MagicParser.parse(List.of(MagicWord.CHA));
        if (d.error() != ParsedSpell.Error.NO_CORE) {
            out.add("核心选举：只有「叉」时应为 NO_CORE，实为 " + d.error());
        }

        // ★v20：「源」已从 TOLL 改为 BOOST/INSTANT，不再是 TOLL 类词，
        // 原"TOLL 词不可单独作核心"测试用例不再适用（TOLL 词已全部迁出）。
        // TOLL 的 E 表条目保留但无词引用它；如需测试 TOLL 不作核心，可在 E 表中
        // 临时造词，此处从略。
    }

    /** ③ 不存在：五类解析失败都要被识别 */
    private static void checkNotExists(List<String> out) {
        expectError(out, "空句", MagicParser.parse(List.of()), ParsedSpell.Error.EMPTY);
        expectError(out, "未知词", MagicParser.parseIds(List.of("no_such_word")),
                ParsedSpell.Error.UNKNOWN_WORD);

        List<MagicWord> nine = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            nine.add(MagicWord.YIN);
        }
        expectError(out, "超 8 词", MagicParser.parse(nine), ParsedSpell.Error.TOO_MANY_WORDS);

        // 动作段 3 词（性质段无词，但动作段溢出一词）⇒ 段溢出
        expectError(out, "段溢出",
                MagicParser.parse(List.of(MagicWord.YIN, MagicWord.LIU, MagicWord.BENG)),
                ParsedSpell.Error.SEGMENT_OVERFLOW);

        // 不成立时判定应为 NOT_EXISTS
        MagicValidation v = MagicValidator.validate(
                MagicParser.parseIds(List.of("no_such_word")), NO_INK);
        if (v.verdict() != MagicValidation.Verdict.NOT_EXISTS) {
            out.add("不存在：未知词的判定应为 NOT_EXISTS，实为 " + v.verdict());
        }
    }

    /** ④ 冲突：跨段对立轴（§4.5 有 5 条跨段轴）、同词重复、墨轴、星墨独占 */
    private static void checkConflicts(List<String> out) {
        // 断(A) ↔ 丝(B) 是跨段轴 —— 只比同段的实现会漏掉它
        expectConflict(out, "跨段词轴 断↔丝",
                List.of(MagicWord.DUAN, MagicWord.SI), NO_INK);

        // 裂(A) ↔ 界(C) 跨段
        expectConflict(out, "跨段词轴 裂↔界",
                List.of(MagicWord.LIE, MagicWord.JIE), NO_INK);

        // 同词重复
        expectConflict(out, "同词重复",
                List.of(MagicWord.YIN, MagicWord.YIN), NO_INK);

        // 墨对立轴：蔓 ↔ 蚀（生机）
        expectConflict(out, "墨轴 蔓↔蚀",
                List.of(MagicWord.YIN),
                List.of(MagicIds.Ink.MAN, MagicIds.Ink.SHI));

        // 星墨独占
        expectConflict(out, "星墨独占",
                List.of(MagicWord.YIN),
                List.of(MagicIds.Ink.XING, MagicIds.Ink.CHI));

        // 正常句不应被误判为冲突
        MagicValidation clean = MagicValidator.validate(
                MagicParser.parse(List.of(MagicWord.JIE, MagicWord.YIN)), List.of(MagicIds.Ink.CHI));
        if (!clean.isOk()) {
            out.add("冲突：正常句被误判为 " + clean.verdict() + "（" + clean.detail() + "）");
        }
    }

    /** ⑤ 协同限位：解锁词数、段内、总数、8 词放宽墨协同 */
    private static void checkSynergyLimits(List<String> out) {
        // 3 词阶：词协同未解锁
        MagicValidation three = MagicValidator.validate(
                MagicParser.parse(List.of(MagicWord.LIU, MagicWord.DUAN, MagicWord.YIN)), NO_INK);
        if (!three.wordSynergies().isEmpty()) {
            out.add("协同限位：3 词阶不该有词协同，实有 " + three.wordSynergies().size() + " 条");
        }

        // 4 词阶 + 动作段含「流+断」⇒ 应解锁「断续」
        // 注意：四段容量各 2，故 4 词只能是 2+1+1（或 1+2+1 等），不能同段塞 3 个
        MagicValidation four = MagicValidator.validate(
                MagicParser.parse(List.of(MagicWord.JIAN, MagicWord.LIU, MagicWord.DUAN, MagicWord.HUAN)),
                NO_INK);
        if (four.wordSynergies().isEmpty()) {
            out.add("协同限位：4 词阶的「流+断」应解锁「断续」，实为 0 条（verdict=" + four.verdict() + "）");
        }

        // 墨协同上限：非满句至多 1 组（§6.2）。
        // 只放「炽+飓(风火)」与「壤+蔓(地生)」两组 —— 若同时给出 蔓+蚀 / 序+混 / 耀+黯
        // 会先判墨轴冲突，就测不到协同上限了。
        MagicValidation mid = MagicValidator.validate(
                MagicParser.parse(List.of(MagicWord.YIN, MagicWord.JIE)),
                List.of(MagicIds.Ink.CHI, MagicIds.Ink.JU, MagicIds.Ink.RANG, MagicIds.Ink.MAN));
        if (mid.inkSynergies().size() > MagicSynergyTable.INK_SYNERGY_CAP) {
            out.add("协同限位：非满句的墨协同应为至多 " + MagicSynergyTable.INK_SYNERGY_CAP
                    + " 组，实为 " + mid.inkSynergies().size());
        }

        // 8 词满句：四段各 2 词（性质 尖/峰 · 动作 引/流 · 作用点 丝/根 · 结构 界/环）
        List<MagicWord> full = List.of(MagicWord.JIAN, MagicWord.FENG_PEAK,
                MagicWord.YIN, MagicWord.LIU, MagicWord.SI, MagicWord.GEN,
                MagicWord.JIE, MagicWord.HUAN);
        MagicValidation eight = MagicValidator.validate(MagicParser.parse(full),
                List.of(MagicIds.Ink.CHI, MagicIds.Ink.JU, MagicIds.Ink.RANG, MagicIds.Ink.MAN));
        if (eight.verdict() == MagicValidation.Verdict.OK
                && eight.inkSynergies().size() > MagicSynergyTable.INK_SYNERGY_CAP_ECHO) {
            out.add("协同限位：8 词满句的墨协同上限应为 " + MagicSynergyTable.INK_SYNERGY_CAP_ECHO
                    + "，实为 " + eight.inkSynergies().size());
        }

        // 词协同总数至多 2（§24.2 规则 4）：构造两段各一对协同
        // 动作段 流+断（断续）、作用点段 隙+针（刺穴）
        MagicValidation capped = MagicValidator.validate(MagicParser.parse(List.of(
                MagicWord.LIU, MagicWord.DUAN, MagicWord.XI, MagicWord.ZHEN)), NO_INK);
        if (capped.wordSynergies().size() > MagicSynergyTable.WORD_SYNERGY_PER_CAST_CAP) {
            out.add("协同限位：词协同总数应为至多 " + MagicSynergyTable.WORD_SYNERGY_PER_CAST_CAP
                    + "，实为 " + capped.wordSynergies().size());
        }
    }

    // ==================== 断言小工具 ====================

    private static void expectError(List<String> out, String what, ParsedSpell spell,
            ParsedSpell.Error expected) {
        if (spell.error() != expected) {
            out.add("不存在：" + what + " 应为 " + expected + "，实为 " + spell.error());
        }
    }

    private static void expectConflict(List<String> out, String what, List<MagicWord> words,
            List<String> inks) {
        MagicValidation v = MagicValidator.validate(MagicParser.parse(words), inks);
        if (v.verdict() != MagicValidation.Verdict.CONFLICT) {
            out.add("冲突：" + what + " 应判 CONFLICT，实为 " + v.verdict());
        }
    }

    private static String labels(List<MagicWord> words) {
        List<String> out = new ArrayList<>(words.size());
        for (MagicWord w : words) {
            out.add(w.label());
        }
        return out.toString();
    }

    private static String name(MagicWord word) {
        return word == null ? "null" : word.label();
    }
}
