package com.example.akaishi.forbidden.magic.exec.resolve;

import com.example.akaishi.forbidden.api.magic.exec.ITargetResolver;
import com.example.akaishi.forbidden.api.magic.exec.MagicContext;
import com.example.akaishi.forbidden.api.magic.exec.MagicShape;
import com.example.akaishi.forbidden.api.magic.exec.MagicTargetQuery;
import net.minecraft.world.entity.Entity;

import java.util.List;

/**
 * 形状 {@link MagicShape#SELF} —— <b>目标就是施法者自己</b>。
 *
 * <p>两个用途：
 * <ol>
 *   <li>自身向效果（自增益 / 自传送 / 自隐匿）的常规目标；</li>
 *   <li>自义无实体目标的效果（对物类：开锁 / 破方块）的"<b>保证执行器被调用</b>"的目标 ——
 *       管线在目标集合为空时不会调用执行器，这类效果用 SELF 占位后再由执行器自行按视线取方块。</li>
 * </ol>
 *
 * <p><b>不做任何过滤</b>：§36.1 的五条细则全是"如何从世界里挑别人"，对"就是自己"没有意义。
 * 若施法者不存在（无主施法），返回空 —— 那类施法需要 P5-B 的实体施法者。
 */
public final class SelfResolver implements ITargetResolver {

    @Override
    public MagicShape shape() {
        return MagicShape.SELF;
    }

    @Override
    public List<Entity> resolve(MagicContext context, MagicTargetQuery query) {
        Entity caster = context.caster();
        if (caster == null || caster.isRemoved()) {
            return List.of();
        }
        return List.of(caster);
    }
}
