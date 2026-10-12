package com.example.akaishi.forbidden.magic.exec.resolve;

import com.example.akaishi.forbidden.api.magic.exec.ITargetResolver;
import com.example.akaishi.forbidden.api.magic.exec.MagicContext;
import com.example.akaishi.forbidden.api.magic.exec.MagicShape;
import com.example.akaishi.forbidden.api.magic.exec.MagicTargetQuery;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 形状 {@link MagicShape#CONE} —— <b>扇形（前方一片）</b>。
 *
 * <p>与 {@link SingleResolver} 的差别只有<b>半角</b>：单体是"瞄一个"（20°），
 * 锥形是"扫一片"（45°）。两者共用 {@link MagicResolvers#aimed} ——
 * 差别若只剩一个常量，就不该存在两份算法。
 *
 * <p><b>待调</b>：半角 45° 为首轮暂定值。真正要拉开手感差距应该让锥口宽度随 {@code radius} 变，
 * 但那需要 R 与角度建立关系，属数值待调（§13.3）。
 */
public final class ConeResolver implements ITargetResolver {

    /** 扇形半角的余弦（45°） */
    private static final double CONE_COS = 0.7071d;

    @Override
    public MagicShape shape() {
        return MagicShape.CONE;
    }

    @Override
    public List<Entity> resolve(MagicContext context, MagicTargetQuery query) {
        Entity caster = context.caster();
        Vec3 dir = MagicResolvers.lookOf(caster);
        if (caster == null || dir == null) {
            return List.of();
        }
        Vec3 eye = MagicResolvers.eyeOf(caster, context.origin());
        List<Entity> inCone = MagicResolvers.aimed(context.level(), eye, dir,
                MagicResolvers.rangeOf(context), CONE_COS, caster, query, false);
        return MagicResolvers.capped(inCone, query.effectiveLimit());
    }
}
