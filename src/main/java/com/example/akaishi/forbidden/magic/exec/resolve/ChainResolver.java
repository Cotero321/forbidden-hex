package com.example.akaishi.forbidden.magic.exec.resolve;

import com.example.akaishi.forbidden.api.magic.exec.ITargetResolver;
import com.example.akaishi.forbidden.api.magic.exec.MagicContext;
import com.example.akaishi.forbidden.api.magic.exec.MagicShape;
import com.example.akaishi.forbidden.api.magic.exec.MagicTargetQuery;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 形状 {@link MagicShape#CHAIN} —— <b>链跳</b>（§4.2「根」「丝」）。
 *
 * <p>做法分两步，与 {@link SingleResolver} 共用"瞄第一个"：
 * <ol>
 *   <li><b>起点</b> = 视线窄锥里最近的那个（否则会从脚边的怪开始跳，手感诡异）；</li>
 *   <li><b>跳</b> = 从当前目标出发，在 {@code radius} 内找<b>尚未访问过</b>的最近者。</li>
 * </ol>
 *
 * <p><b>跳距用 radius 而非 range</b>：range 是"打得有多远"，radius 是"波及有多大"，
 * 链跳的每一跳显然是后者。跳数上限 = §36.1 细则 3 的硬上限（8）。
 *
 * <p><b>必须记已访问</b>：否则 A↔B 互相是最近邻时会无限来回跳 ——
 * 这是链跳类实现最经典的一个死循环来源。
 */
public final class ChainResolver implements ITargetResolver {

    /** 起点瞄准半角的余弦（20°，与单体一致） */
    private static final double START_AIM_COS = 0.94d;

    @Override
    public MagicShape shape() {
        return MagicShape.CHAIN;
    }

    @Override
    public List<Entity> resolve(MagicContext context, MagicTargetQuery query) {
        Entity caster = context.caster();
        Vec3 dir = MagicResolvers.lookOf(caster);
        if (context.level() == null || caster == null || dir == null) {
            return List.of();
        }
        Vec3 eye = MagicResolvers.eyeOf(caster, context.origin());
        boolean projectile = com.example.akaishi.forbidden.magic.table.MagicIds.Delivery.PROJECTILE
                .equals(context.snapshot().deliveryId());
        List<Entity> started = projectile
                ? (MagicResolvers.valid(caster, context.hitEntity(), query, true) ? List.of(context.hitEntity()) : List.of())
                : MagicResolvers.aimed(context.level(), eye, dir,
                        MagicResolvers.rangeOf(context), START_AIM_COS, caster, query, false);

        int limit = Math.max(1, Math.min(query.effectiveLimit(), MagicTargetQuery.DEFAULT_LIMIT));
        double jump = Math.max(1d, context.snapshot().radius());

        List<Entity> chain = new ArrayList<>(limit);
        Entity current = started.isEmpty() ? null : started.get(0);
        while (current != null && chain.size() < limit) {
            chain.add(current);
            current = nearestUnvisited(context, current.position(), jump, query, chain);
        }
        return chain;
    }

    /** 从 {@code from} 出发、半径 {@code jump} 内最近的未访问目标；没有则返回 null（链结束） */
    private static Entity nearestUnvisited(MagicContext context, Vec3 from, double jump,
            MagicTargetQuery query, List<Entity> visited) {
        List<Entity> around = MagicResolvers.around(context.level(), from, jump,
                context.caster(), query, true);
        for (Entity candidate : around) {
            if (!visited.contains(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
