package com.example.akaishi.forbidden.magic.exec;

import com.example.akaishi.forbidden.api.magic.exec.MagicContext;
import com.example.akaishi.forbidden.api.magic.exec.MagicShape;
import com.example.akaishi.forbidden.api.magic.exec.MagicTargetQuery;
import com.example.akaishi.forbidden.magic.table.MagicIds;
import com.example.akaishi.forbidden.magic.table.MagicWord;

import java.util.List;
import java.util.Map;

/**
 * 把"句子长什么样"翻成"<b>圈哪些人</b>"（设计记忆 §4.2 / §19.2 / §36.1）。
 *
 * <p>这段翻译单独成类，是因为它是<b>词层与执行层之间唯一的语义接缝</b>：
 * 词表怎么改都只动这里，{@code ITargetResolver} 的实现永远只看到
 * {@link MagicShape} + {@link MagicTargetQuery} 两个已决参数（OCP）。
 *
 * <p><b>形状 G 的判据</b>（§19.2：阶段①由<b>作用点段 B</b> 驱动）：
 * 句内有 B 组词则以它为准；没有 B 组词时退回"由 X 反推"（引导 = 射线、布场 = 区域、其余 = 单体）。
 * 不强行规定"必须写满四段"，因为 §4 并不要求句子写满。
 */
public final class MagicTargeting {

    /** 作用点段 B 的词 → 形状 G（§4.2 六词逐条对应） */
    private static final Map<String, MagicShape> BY_POINT_WORD = Map.of(
            MagicWord.XI.id(), MagicShape.SINGLE,        // 隙：开隙撕弱点 ⇒ 单体
            MagicWord.GEN.id(), MagicShape.CHAIN,        // 根：植根同化网络 ⇒ 链跳
            MagicWord.ZHEN.id(), MagicShape.SINGLE,      // 针：精确穿刺 ⇒ 单体
            MagicWord.JI_TRACE.id(), MagicShape.SPHERE,  // 迹：显迹暴露痕迹 ⇒ 球形扫出
            MagicWord.SI.id(), MagicShape.CHAIN,         // 丝：结丝连两方 ⇒ 链跳
            MagicWord.FENG_SEAM.id(), MagicShape.SINGLE  // 缝：撕缝留裂口 ⇒ 单体（持续由 C 段负责）
    );

    private MagicTargeting() {
    }

    /**
     * 推出本次的形状 G。
     *
     * @param words      句内全部词 id
     * @param deliveryId 交付方式 X id（无 B 组词时据它反推）
     */
    public static MagicShape shapeOf(List<String> words, String deliveryId) {
        if (words != null) {
            for (String id : words) {
                MagicShape shape = BY_POINT_WORD.get(id);
                if (shape != null) {
                    return shape;
                }
            }
        }
        return byDelivery(deliveryId);
    }

    /** 无 B 组词时由 X 反推形状（引导是束流 ⇒ 射线；布场是落点 ⇒ 区域；其余 ⇒ 单体） */
    private static MagicShape byDelivery(String deliveryId) {
        if (MagicIds.Delivery.CHANNEL.equals(deliveryId)) {
            return MagicShape.RAY;
        }
        if (MagicIds.Delivery.PLACE.equals(deliveryId)) {
            return MagicShape.AREA;
        }
        return MagicShape.SINGLE;
    }

    /**
     * 组装本次的目标查询参数（§36.1 的五条细则在这里落地为<b>参数</b>）。
     *
     * <p>豁免判据取自词表本身（{@code 门} / {@code 折} 豁免视线；{@code 迹} / {@code 标} 纳入隐匿），
     * 不硬编码字符串 —— 词改名只需改 {@link MagicWord}。
     *
     * @param allowPvp 服务端 PVP 开关是否对该效果生效（由编排器按 E 判定后传入，见 {@code MagicPipeline}）——
     *                 解析器不判断 E 语义，只照参数执行
     */
    public static MagicTargetQuery queryOf(MagicContext context, boolean allowPvp) {
        if (context.snapshot().plan() != null) {
            var target = context.snapshot().plan().target();
            return new MagicTargetQuery(context.caster(), context.origin(), MagicShape.valueOf(target.shape()),
                    context.snapshot().radius(), target.limit(), target.requireLineOfSight(), target.includeHidden(), allowPvp);
        }
        List<String> words = context.words();
        MagicShape shape = shapeOf(words, context.snapshot().deliveryId());
        int limit = limitFor(shape, count(words, MagicWord.CHA));

        MagicTargetQuery query = new MagicTargetQuery(
                context.caster(), context.origin(), shape,
                0f, limit, true, false, allowPvp);

        // 细则 4：默认要求视线；「门」（通道）与「折」（穿透）豁免
        if (contains(words, MagicWord.MEN) || contains(words, MagicWord.ZHE)) {
            query = query.ignoringLineOfSight();
        }
        // 细则 5：默认不可选隐匿者；「迹」（显迹）与「标」（标记）豁免
        if (contains(words, MagicWord.JI_TRACE) || contains(words, MagicWord.BIAO)) {
            query = query.includingHidden();
        }
        return query;
    }

    /**
     * 多目标上限（§36.1 细则 3）。
     *
     * <p><b>两类形状的规则不同，不能都用"硬上限"当默认</b>：
     * <ul>
     *   <li><b>单体系</b>（{@code SELF} / {@code SINGLE}）—— 默认就是 <b>1 个</b>，
     *       靠「叉」逐个递增（叉 = 目标数 +1，消耗 ×1.2 已由公式层计）；</li>
     *   <li><b>区域系</b>（射线 / 扇形 / 球形 / 连锁 / 布场）—— 天生覆盖多个，
     *       上限固定 {@link MagicTargetQuery#DEFAULT_LIMIT}（8）。</li>
     * </ul>
     * 若单体也拿 8 当默认，一次"点一下"的法术会打到正前方的 8 个人 —— 那是手感事故，不是数值问题。
     */
    private static int limitFor(MagicShape shape, int forks) {
        return switch (shape) {
            case SELF, SINGLE -> Math.min(MagicTargetQuery.DEFAULT_LIMIT, 1 + forks);
            case RAY, CONE, SPHERE, CHAIN, AREA -> MagicTargetQuery.DEFAULT_LIMIT;
        };
    }

    private static boolean contains(List<String> words, MagicWord word) {
        return words != null && words.contains(word.id());
    }

    private static int count(List<String> words, MagicWord word) {
        if (words == null) {
            return 0;
        }
        int n = 0;
        for (String id : words) {
            if (word.id().equals(id)) {
                n++;
            }
        }
        return n;
    }
}
