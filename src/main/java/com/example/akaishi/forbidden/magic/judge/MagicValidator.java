package com.example.akaishi.forbidden.magic.judge;

import com.example.akaishi.forbidden.magic.table.MagicIds;
import com.example.akaishi.forbidden.magic.table.MagicInkTable;
import com.example.akaishi.forbidden.magic.table.MagicScalingTable;
import com.example.akaishi.forbidden.magic.table.MagicSynergyTable;
import com.example.akaishi.forbidden.magic.table.MagicWord;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * <b>语句判定</b>：冲突与协同（设计记忆 §7 / §4.5 / §6.1 / §6.2 / §24.2）。
 *
 * <p>纯函数，与 {@link MagicParser} 同层。判定分三种结论：
 * <ul>
 *   <li>{@code NOT_EXISTS} —— 由 Parser 的解析错误直传（空句 / 未知词 / 段溢出 / 无核心）；</li>
 *   <li>{@code CONFLICT} —— 对立词轴 / 同词重复 / 墨对立轴 / 星墨违例；</li>
 *   <li>{@code OK} —— 附上本次实际生效的协同清单，供公式层直接消费。</li>
 * </ul>
 *
 * <p><b>词轴必须全句两两比对，不能只比同段</b>：§4.5 的 10 条轴里有 5 条是跨段的
 * （断↔丝 A↔B、裂↔界 A↔C、折↔基 A↔C、峰↔谷 D↔C、角↔旗 D↔A）。
 * 只比同段会漏掉一半冲突 —— 这与<b>词协同必须同段</b>（§24.2 规则 1）正好相反，
 * 两条规则方向不同，实现时极易写反。
 */
public final class MagicValidator {

    /** 8 词满句（质变 A）放宽后的墨协同上限（§24.3） */
    private static final int INK_SYNERGY_CAP_FULL = MagicSynergyTable.INK_SYNERGY_CAP_ECHO;

    private MagicValidator() {
    }

    /**
     * 判定一句话。
     *
     * @param spell    解析结果
     * @param inkIds   本句涉及的墨（含主墨）；{@code null} 或空表示未指定墨
     *                 （镶嵌期只提示不阻止，故墨为空不算"不存在"）
     */
    public static MagicValidation validate(ParsedSpell spell, Collection<String> inkIds) {
        if (spell == null || !spell.ok()) {
            return MagicValidation.notExists(spell == null ? ParsedSpell.Error.EMPTY : spell.error());
        }

        List<MagicValidation.Conflict> conflicts = new ArrayList<>();
        collectWordConflicts(spell, conflicts);
        collectInkConflicts(inkIds, conflicts);
        if (!conflicts.isEmpty()) {
            return MagicValidation.conflict(conflicts);
        }

        String unsupported = MagicModifierSupport.rejection(spell);
        if (!unsupported.isEmpty())
            return new MagicValidation(MagicValidation.Verdict.NOT_EXISTS, List.of(), List.of(), List.of(), unsupported);
        return MagicValidation.ok(resolveWordSynergies(spell), resolveInkSynergies(spell, inkIds));
    }

    /** 效果组合入口：仅复用句法、冲突和协同，不选择主词或套用旧修饰降级。 */
    public static MagicValidation validateComposition(List<MagicWord> words, Collection<String> inks) {
        if (words.isEmpty()) return MagicValidation.notExists(ParsedSpell.Error.EMPTY);
        if (words.size() > MagicScalingTable.MAX_WORDS)
            return MagicValidation.notExists(ParsedSpell.Error.TOO_MANY_WORDS);
        var segments = new java.util.EnumMap<MagicWord.Role, List<MagicWord>>(MagicWord.Role.class);
        for (MagicWord.Role role : MagicWord.Role.values()) segments.put(role, new ArrayList<>());
        for (MagicWord word : words) {
            if (word == null) return MagicValidation.notExists(ParsedSpell.Error.UNKNOWN_WORD);
            segments.get(word.role()).add(word);
        }
        for (List<MagicWord> segment : segments.values()) {
            if (segment.size() > 2) return MagicValidation.notExists(ParsedSpell.Error.SEGMENT_OVERFLOW);
        }
        ParsedSpell spell = new ParsedSpell(segments.get(MagicWord.Role.NATURE),
                segments.get(MagicWord.Role.ACTION), segments.get(MagicWord.Role.POINT),
                segments.get(MagicWord.Role.STRUCTURE), null, ParsedSpell.Error.NONE);
        List<MagicValidation.Conflict> conflicts = new ArrayList<>();
        collectWordConflicts(spell, conflicts);
        collectInkConflicts(inks, conflicts);
        return conflicts.isEmpty() ? MagicValidation.ok(resolveWordSynergies(spell), resolveInkSynergies(spell, inks))
                : MagicValidation.conflict(conflicts);
    }

    // ==================== 冲突 ====================

    /** 词侧冲突：全句两两比对对立轴 + 同词重复 */
    private static void collectWordConflicts(ParsedSpell spell, List<MagicValidation.Conflict> out) {
        List<MagicWord> words = spell.allWords();

        // 同词重复（§7）：同一词出现 ≥2 次即冲突
        Set<MagicWord> seen = new HashSet<>();
        for (MagicWord word : words) {
            if (!seen.add(word)) {
                out.add(MagicValidation.duplicate(word));
            }
        }

        // 对立轴：i < j 两两比对，全句范围（跨段也算）
        for (int i = 0; i < words.size(); i++) {
            for (int j = i + 1; j < words.size(); j++) {
                MagicSynergyTable.Axis axis = MagicSynergyTable.axisOf(words.get(i), words.get(j));
                if (axis != null) {
                    out.add(MagicValidation.wordAxis(axis.name(), axis.a(), axis.b()));
                }
            }
        }
    }

    /** 墨侧冲突：对立轴 + 星墨独占 */
    private static void collectInkConflicts(Collection<String> inkIds, List<MagicValidation.Conflict> out) {
        if (inkIds == null || inkIds.isEmpty()) {
            return;
        }
        Set<String> present = new HashSet<>(inkIds);

        for (MagicSynergyTable.InkAxis axis : MagicSynergyTable.INK_AXES) {
            if (present.contains(axis.inkA()) && present.contains(axis.inkB())) {
                out.add(MagicValidation.inkAxis(axis.name(), inkLabel(axis.inkA()), inkLabel(axis.inkB())));
            }
        }

        // 星墨独占（§3.1）：星句的 K 恒为 0，故只要同句还有第二种墨即违例
        if (present.contains(MagicIds.Ink.XING)) {
            for (String ink : present) {
                if (!MagicIds.Ink.XING.equals(ink)) {
                    out.add(MagicValidation.starExclusive(inkLabel(ink)));
                    break;
                }
            }
        }
    }

    // ==================== 协同 ====================

    /**
     * 词协同（§24.2 六条规则）：
     * ① 仅同段 2 词；② 4~5 词阶起解锁；③ 对立轴词对不触发（此处已因"无冲突"而天然排除）；
     * ④ 每段至多 1 对、每次施法至多 2 对；⑤ 与墨协同互不挤占。
     *
     * <p>规则 ⑥（触发后不再"首词为主其余降修饰"）由公式层消费生效清单来体现。
     */
    private static List<MagicSynergyTable.WordSynergy> resolveWordSynergies(ParsedSpell spell) {
        if (spell.wordCount() < MagicSynergyTable.WORD_SYNERGY_MIN_WORDS) {
            return List.of();
        }
        List<MagicSynergyTable.WordSynergy> out = new ArrayList<>(MagicSynergyTable.WORD_SYNERGY_PER_CAST_CAP);
        for (MagicWord.Role role : MagicWord.Role.values()) {
            if (out.size() >= MagicSynergyTable.WORD_SYNERGY_PER_CAST_CAP) {
                break;
            }
            List<MagicWord> segment = spell.segmentOf(role);
            if (segment.size() < 2) {
                continue;
            }
            // 每段至多 1 对：该段只有 2 个词可选，取首对即可
            MagicSynergyTable.WordSynergy synergy = MagicSynergyTable.wordSynergyOf(segment.get(0), segment.get(1));
            if (synergy != null) {
                out.add(synergy);
            }
        }
        return out;
    }

    /**
     * 墨协同（§6.2）：至多 1 组；<b>8 词满句</b>时按质变 A 放宽到 3 组（§24.3）。
     *
     * <p><b>待细化（P5）</b>：§6.2 还要求"参与的两墨中至少一个覆盖在动作段的词上"，
     * 这需要逐词覆盖墨的映射（§3.2，Lv3+），属镶嵌 / 法术视图层，此处先按"墨集合"结算。
     */
    private static List<MagicSynergyTable.InkSynergy> resolveInkSynergies(ParsedSpell spell,
            Collection<String> inkIds) {
        if (inkIds == null || inkIds.size() < 2) {
            return List.of();
        }
        Set<String> present = new HashSet<>(inkIds);
        int cap = spell.wordCount() >= MagicScalingTable.MAX_WORDS ? INK_SYNERGY_CAP_FULL
                : MagicSynergyTable.INK_SYNERGY_CAP;
        List<MagicSynergyTable.InkSynergy> out = new ArrayList<>(cap);
        for (MagicSynergyTable.InkSynergy synergy : MagicSynergyTable.INK_SYNERGIES) {
            if (out.size() >= cap) {
                break;
            }
            if (present.contains(synergy.inkA()) && present.contains(synergy.inkB())) {
                out.add(synergy);
            }
        }
        return out;
    }

    // ==================== 小工具 ====================

    private static String inkLabel(String inkId) {
        var def = MagicInkTable.get(inkId);
        return def == null ? inkId : def.label();
    }
}
