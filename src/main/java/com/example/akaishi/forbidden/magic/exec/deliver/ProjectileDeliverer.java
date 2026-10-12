package com.example.akaishi.forbidden.magic.exec.deliver;

import com.example.akaishi.forbidden.api.magic.exec.IDeliverer;
import com.example.akaishi.forbidden.api.magic.exec.MagicContext;
import com.example.akaishi.forbidden.api.magic.exec.MagicDeliveryAction;
import com.example.akaishi.forbidden.magic.projectile.SpellProjectile;
import com.example.akaishi.forbidden.magic.projectile.SpellProjectiles;
import com.example.akaishi.forbidden.magic.table.MagicIds;
import com.example.akaishi.forbidden.magic.visual.SpellVisual;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * X2 投射（设计记忆 §19.3）—— <b>抛出一枚会飞的弹体，命中才结算</b>。
 *
 * <p>手感词是"有期待、可被躲"：与 X1 直射的根本差别不在数值，在于
 * <b>从按下到命中之间有一段可以被躲开、也可以被打掉的时间</b>。
 * 因此本投送器<b>不在原地结算</b>，而是把闭包用 {@link SpellProjectiles} 挂到弹体上，
 * 等它撞上东西（或被打落）再说。
 *
 * <p><b>为什么把"结算"交给弹体回调而不是自己等</b>：飞行是实体的职责
 * （碰撞、区块加载、客户端插值都是原版在管），投送器自己起一个计时器去猜落点，
 * 就会与实体的真实位置漂移 —— 玩家看到弹体撞墙了、法术却在半空结算。
 */
public final class ProjectileDeliverer implements IDeliverer {

    @Override
    public String deliveryId() {
        return MagicIds.Delivery.PROJECTILE;
    }

    @Override
    public boolean deliver(MagicContext context, MagicDeliveryAction action) {
        if (!(context.level() instanceof ServerLevel level)) {
            return false;
        }
        Vec3 from = ImmediateDeliverer.originOf(context);
        // 无施法者（自主法阵）时没有"看向哪"，沿 +Z 平推 —— 那是无主施法的唯一合理默认
        Vec3 direction = context.caster() == null
                ? new Vec3(0d, 0d, 1d)
                : context.caster().getLookAngle();
        SpellProjectile bolt = SpellProjectile.create(level, context.caster(), from, direction,
                SpellVisual.from(context));
        bolt.setMaxRange(from, context.snapshot().range());
        if (!level.addFreshEntity(bolt)) return false;
        // 先入世界再登记：万一生成就失败（区块未加载等），登记项会被 TTL 兜底清掉，
        // 而不是留下一条永远等不到弹体的记录
        SpellProjectiles.bind(bolt, level, action);
        return true;
    }
}
