package com.example.akaishi.forbidden.magic.exec.resolve;

import com.example.akaishi.forbidden.api.magic.exec.ITargetResolver;
import com.example.akaishi.forbidden.api.magic.exec.MagicContext;
import com.example.akaishi.forbidden.api.magic.exec.MagicShape;
import com.example.akaishi.forbidden.api.magic.exec.MagicTargetQuery;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 形状 {@link MagicShape#SPHERE} 与 {@link MagicShape#AREA} —— <b>原点球内全部</b>。
 *
 * <p><b>为什么共用一个实现类</b>：两者的圈人算法<b>一模一样</b>（以 {@code query.origin()} 为心、
 * 半径内的全部合法实体）。真正的差别是<b>原点从哪来</b> ——
 * 球形效果的原点是施法者，布场效果的原点是落点；而落点由投送器通过
 * {@code MagicDeliveryAction.fire(at)} 交给管线，管线再把它写进 {@code query.origin()}。
 * 因此这个差别在<b>上游</b>就已经消化掉了，再造两个等价类只会制造"改了一处漏了另一处"的机会。
 *
 * <p><b>施法者算作目标</b>：以己为中心的爆发本就该波及自己（§36.1 细则 1：
 * 基础效果对命中者一律生效）。是否真吃伤害由执行器按 E 的语义决定。
 *
 * <p>上限一律夹到 §36.1 细则 3 的硬上限（区域 / 连锁类 = 8），不随「叉」无限增长。
 */
public final class SphereResolver implements ITargetResolver {

    private final MagicShape shape;

    /**
     * @param shape 本实例负责的形状（{@code SPHERE} 或 {@code AREA}）——
     *              只作为注册表的键与日志标签，不影响算法
     */
    public SphereResolver(MagicShape shape) {
        this.shape = shape;
    }

    @Override
    public MagicShape shape() {
        return shape;
    }

    @Override
    public List<Entity> resolve(MagicContext context, MagicTargetQuery query) {
        if (context.level() == null) {
            return List.of();
        }
        Vec3 center = query.origin() == null ? context.origin() : query.origin();
        double radius = Math.max(0d, context.snapshot().radius());
        List<Entity> found = MagicResolvers.around(context.level(), center, radius,
                context.caster(), query, true);
        return MagicResolvers.capped(found, MagicTargetQuery.DEFAULT_LIMIT);
    }
}
