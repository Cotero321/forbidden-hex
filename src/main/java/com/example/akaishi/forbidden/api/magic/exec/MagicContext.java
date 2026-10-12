package com.example.akaishi.forbidden.api.magic.exec;

import com.example.akaishi.forbidden.api.magic.SpellSnapshot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 一次施法的<b>执行上下文</b>（不可变）。四阶段管道全程只读它。
 *
 * <p>它是 §20.9 说的"<b>先落 MagicContext 参数面，再定四接口</b>"里的那个参数面 ——
 * 四接口的方法签名都以此类型为轴，故此类型必须先定稿。
 *
 * <p><b>为什么把词只留 id 而不再解析</b>：解析与判定已在 P4 完成（{@code ParsedSpell} 属实现层，
 * 不进 api）。执行阶段只需要"这句话长什么样"来做措辞与豁免判断，
 * 因此这里只保留<b>扁平 id 序列</b>，不把实现类型泄漏给附属。
 *
 * @param caster   施法者（可为 {@code null}：自主法阵等无主施法）
 * @param level    施法所在世界<b>（可为 {@code null}，仅当连世界都没有时）</b> ——
 *                 解析器与执行器都要它；单靠 {@code caster} 取世界会在"无主施法"下断链，
 *                 故显式带上，而不是让每个实现各自去猜
 * @param origin   施法原点（前摇结束瞬间的施法者位置；布场 / 投射的起点）
 * @param sourceId 实际使用的源 id（§2）
 * @param mainInkId 主墨 id（§3.2）
 * @param inks     本句涉及的全部墨 id（主墨 + 逐词覆盖墨；§21.3 的 {@code K} 由它去重计数）
 * @param words    句内全部词 id（按四段顺序拼接，已由判定层确认成立）
 * @param casterLevel 施法者等级（1~6；影响阶位专属能力，§23.4）
 * @param snapshot 本次施法的数值快照（P4 结算结果）
 */
public record MagicContext(
        Entity caster,
        Level level,
        Vec3 origin,
        String sourceId,
        String mainInkId,
        List<String> inks,
        List<String> words,
        int casterLevel,
        SpellSnapshot snapshot,
        Entity hitEntity,
        Vec3 impactDirection) {

    public MagicContext(Entity caster, Level level, Vec3 origin, String sourceId, String mainInkId,
            List<String> inks, List<String> words, int casterLevel, SpellSnapshot snapshot) {
        this(caster, level, origin, sourceId, mainInkId, inks, words, casterLevel, snapshot, null, null);
    }

    public MagicContext withImpact(Entity hit, Vec3 at, Vec3 direction) {
        return new MagicContext(caster, level, at, sourceId, mainInkId, inks, words,
                casterLevel, snapshot, hit, direction);
    }

    /** 紧凑构造：列表一律不可变拷贝，防止执行期被外部改写 */
    public MagicContext {
        inks = inks == null ? List.of() : List.copyOf(inks);
        words = words == null ? List.of() : List.copyOf(words);
    }

    /** 词数（= {@link SpellSnapshot#wordCount()}，便捷） */
    public int wordCount() {
        return words.size();
    }

    /** 该 id 的词是否在本句内（执行器做措辞 / 豁免判断用） */
    public boolean hasWord(String wordId) {
        return wordId != null && words.contains(wordId);
    }

    /** 该 id 的墨是否在本句内 */
    public boolean hasInk(String inkId) {
        return inkId != null && inks.contains(inkId);
    }

    /** 是否具备可执行的最基本条件（世界存在） */
    public boolean usable() {
        return level != null && snapshot != null;
    }

    /** 换原点（投射物飞行中推进原点用） */
    public MagicContext withOrigin(Vec3 value) {
        return new MagicContext(caster, level, value, sourceId, mainInkId, inks, words,
                casterLevel, snapshot, hitEntity, impactDirection);
    }
}
