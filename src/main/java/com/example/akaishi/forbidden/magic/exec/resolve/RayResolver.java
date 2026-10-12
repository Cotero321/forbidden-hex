package com.example.akaishi.forbidden.magic.exec.resolve;

import com.example.akaishi.forbidden.api.magic.exec.ITargetResolver;
import com.example.akaishi.forbidden.api.magic.exec.MagicContext;
import com.example.akaishi.forbidden.api.magic.exec.MagicShape;
import com.example.akaishi.forbidden.api.magic.exec.MagicTargetQuery;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 形状 {@link MagicShape#RAY} —— <b>射线 / 束流</b>（引导态的默认形状）。
 *
 * <p>与 {@link SingleResolver} 的关键差别：<b>先算出这条线能走多远</b>。
 * 要求视线时用 {@code level.clip} 找到第一面墙，射线就在那里止住 ——
 * 否则"隔墙的怪也被引导烧到"，与 §36.1 细则 4 直接矛盾。
 *
 * <p>束宽取 {@code radius}（≥1 格），让贴着墙的目标也能被扫到，避免"擦边打不中"的手感问题。
 *
 * <p>被墙截断只在 {@code requireLineOfSight} 为真时生效：「门」（通道）与「折」（穿透）
 * 本就该穿过遮挡，此时射线一路走满射程。
 */
public final class RayResolver implements ITargetResolver {

    /** 束流的最小半宽（格）—— 纯数学直线会漏掉"擦边"目标 */
    private static final double MIN_BEAM_RADIUS = 1.0d;

    @Override
    public MagicShape shape() {
        return MagicShape.RAY;
    }

    @Override
    public List<Entity> resolve(MagicContext context, MagicTargetQuery query) {
        Level level = context.level();
        Entity caster = context.caster();
        Vec3 dir = MagicResolvers.lookOf(caster);
        if (level == null || caster == null || dir == null) {
            return List.of();
        }
        Vec3 eye = MagicResolvers.eyeOf(caster, context.origin());
        Vec3 dirNorm = dir.normalize();
        double range = MagicResolvers.rangeOf(context);

        // 要求视线时，射线在遇到第一面可碰撞方块处止住（穿透类豁免由 query 表达，故此处不判）
        if (query.requireLineOfSight()) {
            Vec3 end = eye.add(dirNorm.scale(range));
            BlockHitResult hit = level.clip(new ClipContext(
                    eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            if (hit.getType() == HitResult.Type.BLOCK) {
                range = Math.min(range, hit.getLocation().distanceTo(eye));
            }
        }

        // 沿射线取样，收集"到轴线的垂距 ≤ 束宽"的目标
        double beam = Math.max(MIN_BEAM_RADIUS, context.snapshot().radius());
        List<Entity> hits = new ArrayList<>();
        for (Entity target : MagicResolvers.around(level, eye, range + beam, caster, query, false)) {
            Vec3 to = target.getEyePosition().subtract(eye);
            double along = to.dot(dirNorm);
            if (along < 0d || along > range) {
                continue;
            }
            // 垂距 = |to - 投影|
            double offAxis = to.subtract(dirNorm.scale(along)).length();
            if (offAxis <= beam) {
                hits.add(target);
            }
        }
        return MagicResolvers.capped(hits, query.effectiveLimit());
    }
}
