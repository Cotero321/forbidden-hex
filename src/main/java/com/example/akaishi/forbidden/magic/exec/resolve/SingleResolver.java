package com.example.akaishi.forbidden.magic.exec.resolve;

import com.example.akaishi.forbidden.api.magic.exec.ITargetResolver;
import com.example.akaishi.forbidden.api.magic.exec.MagicContext;
import com.example.akaishi.forbidden.api.magic.exec.MagicShape;
import com.example.akaishi.forbidden.api.magic.exec.MagicTargetQuery;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 形状 {@link MagicShape#SINGLE} —— <b>瞄准单点</b>。
 *
 * <p>做法：在射程内、以视线为轴的<b>窄锥</b>（20°）里按距离取前 N 个。
 * 为什么是锥而不是"最近的那个"：§36.1 细则 1 要求基础效果对命中者生效，
 * 而"一个都不在正前方时随手抓最近的怪"会让法术打到背后 —— 那比打不中更糟。
 *
 * <p><b>目标数为 N</b>：N 由 §36.1 细则 3 的「叉」递增给出（默认 1）。
 * 这也是"单体法术"与"多发单体"共用同一形状的原因 —— 差别只在 N。
 *
 * <p><b>待调</b>：半角 20° 为首轮暂定值。
 */
public final class SingleResolver implements ITargetResolver {

    /** 瞄准半角的余弦（20°） */
    private static final double AIM_COS = 0.94d;

    @Override
    public MagicShape shape() {
        return MagicShape.SINGLE;
    }

    @Override
    public List<Entity> resolve(MagicContext context, MagicTargetQuery query) {
        Entity caster = context.caster();
        if (context.hitEntity() != null || com.example.akaishi.forbidden.magic.table.MagicIds.Delivery.PROJECTILE
                .equals(context.snapshot().deliveryId())) {
            Entity hit = context.hitEntity();
            return MagicResolvers.valid(caster, hit, query, true) ? List.of(hit) : List.of();
        }
        Vec3 dir = MagicResolvers.lookOf(caster);
        // 无施法者就没有"看向哪"，瞄准类形状无法解析（不是"空目标"，而是无从判断）
        if (caster == null || dir == null) {
            return List.of();
        }
        Vec3 eye = MagicResolvers.eyeOf(caster, context.origin());
        List<Entity> aimed = MagicResolvers.aimed(context.level(), eye, dir,
                MagicResolvers.rangeOf(context), AIM_COS, caster, query, false);
        return MagicResolvers.capped(aimed, query.effectiveLimit());
    }
}
