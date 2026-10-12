package com.example.akaishi.forbidden.magic.judge;

import com.example.akaishi.forbidden.magic.table.MagicSynergyTable;
import com.example.akaishi.forbidden.magic.table.MagicWord;

import java.util.List;

/**
 * 判定结果（设计记忆 §7「判定与处罚」+ §6.2 / §24.2 的协同结算）。
 *
 * <p>它同时承担两件事，因为两者本就是同一次判定的产物：
 * <ol>
 *   <li>{@link #verdict 成立与否}（冲突 / 不存在）；</li>
 *   <li><b>本次施法实际生效的协同清单</b> —— 上层公式只消费这个清单，不需要自己再判一遍。</li>
 * </ol>
 * 若让公式层自行判断协同，就会与判定层各写一套规则，迟早漂移。
 *
 * @param verdict       判定结论
 * @param conflicts     命中的冲突清单（{@code CONFLICT} 时非空，用于告诉玩家"错在哪"）
 * @param wordSynergies 生效的词协同（已按 §24.2 的段限位与总数上限裁剪）
 * @param inkSynergies  生效的墨协同（已按 §6.2 / §24.3 的上限裁剪）
 * @param detail        人类可读说明（空串表示无）
 */
public record MagicValidation(
        Verdict verdict,
        List<Conflict> conflicts,
        List<MagicSynergyTable.WordSynergy> wordSynergies,
        List<MagicSynergyTable.InkSynergy> inkSynergies,
        String detail) {

    /** 判定结论 —— 与 §7 的两类判定及 §11 的 `CastResult` 语义对齐 */
    public enum Verdict {
        /** 成立，可结算 */
        OK,
        /** 句子本身不成立（空句 / 未知词 / 段溢出 / 无核心）—— §7「不存在」 */
        NOT_EXISTS,
        /** 语义自相矛盾（对立轴 / 同词重复 / 星墨违例）—— §7「冲突」 */
        CONFLICT
    }

    /** 一条冲突 */
    public record Conflict(Kind kind, String label, String detail) {
    }

    /** 冲突种类 */
    public enum Kind {
        /** 语义对立轴命中（§4.5 的 10 条词轴） */
        WORD_AXIS,
        /** 同一词重复出现（§7） */
        WORD_DUPLICATE,
        /** 墨对立轴命中（§6.1 的生机 / 律法 / 明暗） */
        INK_AXIS,
        /** 星墨独占被破坏（§3.1：星墨不可与其他墨同句） */
        STAR_EXCLUSIVE
    }

    /** 成立且无协同 */
    public static MagicValidation ok() {
        return new MagicValidation(Verdict.OK, List.of(), List.of(), List.of(), "");
    }

    /** 成立，携带生效的协同 */
    public static MagicValidation ok(List<MagicSynergyTable.WordSynergy> wordSynergies,
            List<MagicSynergyTable.InkSynergy> inkSynergies) {
        return new MagicValidation(Verdict.OK, List.of(), List.copyOf(wordSynergies),
                List.copyOf(inkSynergies), "");
    }

    /** 句子不成立 */
    public static MagicValidation notExists(ParsedSpell.Error error) {
        return new MagicValidation(Verdict.NOT_EXISTS, List.of(), List.of(), List.of(), describe(error));
    }

    /** 冲突 */
    public static MagicValidation conflict(List<Conflict> conflicts) {
        String detail = conflicts.isEmpty() ? ""
                : conflicts.get(0).label() + "：" + conflicts.get(0).detail();
        return new MagicValidation(Verdict.CONFLICT, List.copyOf(conflicts), List.of(), List.of(), detail);
    }

    /** 判定是否成立（{@code isOk} 而非 {@code ok}：后者已被静态工厂占用，Java 不按返回类型重载） */
    public boolean isOk() {
        return verdict == Verdict.OK;
    }

    /** 把解析错误翻成玩家可读的说明 */
    private static String describe(ParsedSpell.Error error) {
        return switch (error) {
            case NONE -> "";
            case EMPTY -> "空句";
            case TOO_MANY_WORDS -> "词数超过上限";
            case SEGMENT_OVERFLOW -> "某段超过 2 词";
            case UNKNOWN_WORD -> "含未知词";
            case NO_CORE -> "整句无核心词（参数修改器与 TOLL 类词不可单独成句）";
        };
    }

    /** 便捷：词轴冲突 */
    public static Conflict wordAxis(String axisName, MagicWord a, MagicWord b) {
        return new Conflict(Kind.WORD_AXIS, axisName, a.label() + " ↔ " + b.label());
    }

    /** 便捷：同词重复 */
    public static Conflict duplicate(MagicWord word) {
        return new Conflict(Kind.WORD_DUPLICATE, "同词重复", word.label() + " 出现多次");
    }

    /** 便捷：墨轴冲突 */
    public static Conflict inkAxis(String axisName, String inkA, String inkB) {
        return new Conflict(Kind.INK_AXIS, axisName, inkA + " ↔ " + inkB);
    }

    /** 便捷：星墨违例 */
    public static Conflict starExclusive(String otherInk) {
        return new Conflict(Kind.STAR_EXCLUSIVE, "星墨独占", "星墨不可与「" + otherInk + "」同句");
    }
}
