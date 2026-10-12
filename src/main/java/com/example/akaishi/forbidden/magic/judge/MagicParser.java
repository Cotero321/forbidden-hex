package com.example.akaishi.forbidden.magic.judge;

import com.example.akaishi.forbidden.magic.table.MagicScalingTable;
import com.example.akaishi.forbidden.magic.table.MagicWord;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>语句解析</b>：把一串词按四段归位，并选出核心词（设计记忆 §5 + §1）。
 *
 * <p>纯函数、无状态、无 Minecraft 依赖 —— 因此可被 P3 服务层、客户端预览、调试命令与自检
 * 共用同一份实现（客户端若另写一套，必然与服务端判定漂移）。
 *
 * <p><b>两个入口</b>：{@link #parse(List)} 收枚举（编译期就保证词有效）；
 * {@link #parseIds(List)} 收字符串 id（存档与网络用 —— §11 规定"只同步词 id"）。
 * 后者遇到未知 id 返回 {@link ParsedSpell.Error#UNKNOWN_WORD} 而不是抛异常，
 * 因为那属于<b>用户数据</b>问题（老档 / 未知附属），不是程序错误。
 */
public final class MagicParser {

    /** 每段容量（§5：0~2） */
    public static final int SEGMENT_CAPACITY = 2;

    private MagicParser() {
    }

    /**
     * 按 id 解析（存档 / 网络入口）。
     *
     * @param wordIds 词 id 序列；{@code null} 或空 ⇒ 空句
     * @return 归位结果；非法时 {@link ParsedSpell#error()} 说明原因
     */
    public static ParsedSpell parseIds(List<String> wordIds) {
        if (wordIds == null || wordIds.isEmpty()) {
            return empty(ParsedSpell.Error.EMPTY);
        }
        List<MagicWord> words = new ArrayList<>(wordIds.size());
        for (String id : wordIds) {
            MagicWord word = MagicWord.byId(id);
            if (word == null) {
                return empty(ParsedSpell.Error.UNKNOWN_WORD);
            }
            words.add(word);
        }
        return parse(words);
    }

    /** 仅供旧公式逐操作自检指定消费者，不属于整句施法入口。 */
    public static ParsedSpell parseOperationIds(List<String> ids, int operationIndex) {
        ParsedSpell spell = parseIds(ids);
        if (!spell.ok()) return spell;
        MagicWord operation = operationIndex >= 0 && operationIndex < ids.size()
                ? MagicWord.byId(ids.get(operationIndex)) : null;
        if (operation == null || !canBeCore(operation)) return empty(ParsedSpell.Error.NO_CORE);
        return new ParsedSpell(spell.nature(), spell.action(), spell.point(), spell.structure(),
                operation, ParsedSpell.Error.NONE);
    }

    /**
     * 按枚举解析（内部 / 调试入口）。
     */
    public static ParsedSpell parse(List<MagicWord> words) {
        if (words == null || words.isEmpty()) {
            return empty(ParsedSpell.Error.EMPTY);
        }
        if (words.size() > MagicScalingTable.MAX_WORDS) {
            return empty(ParsedSpell.Error.TOO_MANY_WORDS);
        }

        List<MagicWord> nature = new ArrayList<>(SEGMENT_CAPACITY);
        List<MagicWord> action = new ArrayList<>(SEGMENT_CAPACITY);
        List<MagicWord> point = new ArrayList<>(SEGMENT_CAPACITY);
        List<MagicWord> structure = new ArrayList<>(SEGMENT_CAPACITY);

        for (MagicWord word : words) {
            List<MagicWord> bucket = switch (word.role()) {
                case NATURE -> nature;
                case ACTION -> action;
                case POINT -> point;
                case STRUCTURE -> structure;
            };
            if (bucket.size() >= SEGMENT_CAPACITY) {
                // 段溢出是「句法不成立」，与"词本身不存在"不同，但同归 §7「不存在」
                return empty(ParsedSpell.Error.SEGMENT_OVERFLOW);
            }
            bucket.add(word);
        }

        MagicWord core = pickCore(action, structure, point, nature);
        if (core == null) {
            // 整句只有参数修改器（叉）⇒ 无核心，句子不成立
            // ★v20：原 TOLL 词（源/痕）已迁入 B 组并更换 E/X，不再是 TOLL
            return new ParsedSpell(nature, action, point, structure, null, ParsedSpell.Error.NO_CORE);
        }
        return new ParsedSpell(nature, action, point, structure, core, ParsedSpell.Error.NONE);
    }

    /**
     * 核心词选举（§1）：<b>动作段首词 → 结构段首词 → 作用点段首词 → 性质段首词</b>，
     * 前一段为空才看后一段。
     *
     * <p><b>为什么还要过滤"可作核心"</b>：§1 的规则只按段取首词，但段里可能全是
     * {@code 叉}（参数修改器，无 E / X）。
     * ★v20：原 TOLL 类词（源/痕）已迁入 B 组并更换 E/X，不再需要 TOLL 过滤。
     * 这些词根本不携带 {@code (E, X)}，选出来也无法结算 ⇒ 顺延到下一个可作核心的词；
     * 全句都不可作核心时才判 {@link ParsedSpell.Error#NO_CORE}（§28.2 缺陷 4 的口径）。
     */
    private static MagicWord pickCore(List<MagicWord> action, List<MagicWord> structure,
            List<MagicWord> point, List<MagicWord> nature) {
        return firstExecutable(action, structure, point, nature);
    }

    @SafeVarargs
    private static MagicWord firstExecutable(List<MagicWord>... segments) {
        for (List<MagicWord> segment : segments) {
            for (MagicWord word : segment) {
                if (canBeCore(word)) {
                    return word;
                }
            }
        }
        return null;
    }

    /** 可作核心 = 携带 E 且携带 X（参数修改器与 {@code TOLL} 类词都不满足） */
    public static boolean canBeCore(MagicWord word) {
        return word != null && !word.isModifier() && word.delivery() != null && word.effect() != null
                && !java.util.Set.of("summon", "vessel", "temporal").contains(word.effect());
    }

    /** 构造一个"不成立"的结果（四段皆空、无核心） */
    private static ParsedSpell empty(ParsedSpell.Error error) {
        return new ParsedSpell(List.of(), List.of(), List.of(), List.of(), null, error);
    }
}
