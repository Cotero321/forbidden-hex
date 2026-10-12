package com.example.akaishi.forbidden.magic.exec.deliver;

import com.example.akaishi.forbidden.api.magic.exec.IDeliverer;
import com.example.akaishi.forbidden.api.magic.exec.MagicContext;
import com.example.akaishi.forbidden.api.magic.exec.MagicDeliveryAction;
import com.example.akaishi.forbidden.entity.AkaishiForbiddenEntities;
import com.example.akaishi.forbidden.magic.circle.SpellCircleEntity;
import com.example.akaishi.forbidden.magic.exec.ExecSupport;
import com.example.akaishi.forbidden.magic.table.MagicIds;
import com.example.akaishi.forbidden.magic.visual.SpellVisual;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.BlockHitResult;

/**
 * X5 布场（设计记忆 §19.3 / §36.2）—— <b>选一个落点，把法阵留在那儿</b>。
 *
 * <p>手感词是"布局、控位"：先落点、后发挥。与其它 X 的根本差别在于，
 * <b>效果的发生地不在施法者身上，而在世界里的某一点</b> ——
 * 所以它不是"立即触发一次"，而是交给一个能站得住的世界实体
 * （{@link SpellCircleEntity}）按秒重放。
 *
 * <p><b>必须真的指到一个落点</b>：§19.3 把 X5 的玩家动作写作"点一下（选点）"，
 * 而 §21.2 又给它的距离打了 ×0.85 的折扣（"必须走近"）——
 * 两句都指向"落点是玩家选出来的"。故对着天空放（射线打不到任何方块）时
 * <b>返回无法投送</b>，由状态机按自然终止处理，而不是凭空在玩家脚下铺场。
 */
public final class PlacementDeliverer implements IDeliverer {

    @Override
    public String deliveryId() {
        return MagicIds.Delivery.PLACE;
    }

    @Override
    public boolean deliver(MagicContext context, MagicDeliveryAction action) {
        if (!(context.level() instanceof ServerLevel level)) {
            return false;
        }
        BlockHitResult hit = ExecSupport.aimBlock(context, context.snapshot().range());
        if (hit == null) {
            return false;
        }
        SpellCircleEntity circle =
                new SpellCircleEntity(AkaishiForbiddenEntities.SPELL_CIRCLE.get(), level);
        // 落点取射线与方块的真实交点：地面命中时它正好是地面之上那一层
        circle.setPos(hit.getLocation().x, hit.getLocation().y, hit.getLocation().z);
        // 视觉描述随实体同步给客户端：客户端据此组装法阵的外观（形态/配色/符号）
        circle.arm(SpellVisual.from(context), action, ExecSupport.durationTicks(context));
        return level.addFreshEntity(circle);
    }
}
