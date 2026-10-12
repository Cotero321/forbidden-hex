package com.example.akaishi.forbidden.magic.judge;

import com.example.akaishi.forbidden.magic.table.MagicWord;

import java.util.List;

/**
 * 四段归位后的语句（设计记忆 §5 语句文法）。
 *
 * <p><b>四段的书写顺序</b>是 性质 → 动作 → 作用点 → 结构；但本类型只承载<b>归位结果</b>，
 * 不记录"玩家当初写得是否严格成序"。理由：§7 的判定只有「冲突」与「不存在」两类，
 * 若强行要求语序就会多出第三类"格式错"，那是设计里没有的东西。
 * 因此 {@link MagicParser} 采取<b>按角色归位</b>（段内保持原相对顺序），
 * 语序只作为阅读习惯与法阵由内向外的显示顺序存在。
 *
 * @param nature    性质段（D 组）—— 怎么施
 * @param action    动作段（A 组）—— 做什么
 * @param point     作用点段（B 组）—— 借由什么
 * @param structure 结构段（C 组）—— 成什么形
 * @param core      核心词（§1：动作 → 结构 → 作用点 → 性质，取首个<b>可作核心</b>的词）
 * @param error     解析错误；{@code NONE} 表示句子成立
 */
public record ParsedSpell(
        List<MagicWord> nature,
        List<MagicWord> action,
        List<MagicWord> point,
        List<MagicWord> structure,
        MagicWord core,
        Error error) {

    /** 解析错误的类型。{@code NONE} 之外的每一项都归入 §7 的「<b>不存在</b>」。 */
    public enum Error {
        /** 句子成立 */
        NONE,
        /** 空句（0 词）—— §7「不存在」 */
        EMPTY,
        /** 词数超过上限 8（= 四段各 2 词）—— §5 */
        TOO_MANY_WORDS,
        /** 某段超过 2 词 —— §5 段容量 */
        SEGMENT_OVERFLOW,
        /** 含未知词 id（存档 / 网络传来的 id 解析不到）—— §7「不存在」 */
        UNKNOWN_WORD,
        /** 无核心词：整句只有参数修改器（叉 / 层）或 TOLL 类词（源 / 痕）—— §8.6 / §28.2 缺陷 4 */
        NO_CORE
    }

    /** 是否成立（可直接进入公式结算） */
    public boolean ok() {
        return error == Error.NONE && core != null;
    }

    /** 全部词（按四段顺序拼接），供遍历与冲突比对 */
    public List<MagicWord> allWords() {
        List<MagicWord> out = new java.util.ArrayList<>(
                nature.size() + action.size() + point.size() + structure.size());
        out.addAll(nature);
        out.addAll(action);
        out.addAll(point);
        out.addAll(structure);
        return out;
    }

    /** 词数 */
    public int wordCount() {
        return nature.size() + action.size() + point.size() + structure.size();
    }

    /** 该段的 2 词是否<b>同段</b>（词协同的成立前提，§24.2 规则 1） */
    public List<MagicWord> segmentOf(MagicWord.Role role) {
        return switch (role) {
            case NATURE -> nature;
            case ACTION -> action;
            case POINT -> point;
            case STRUCTURE -> structure;
        };
    }
}
