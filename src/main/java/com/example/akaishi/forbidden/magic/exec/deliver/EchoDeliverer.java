package com.example.akaishi.forbidden.magic.exec.deliver;

import com.example.akaishi.forbidden.api.magic.exec.IDeliverer;
import com.example.akaishi.forbidden.api.magic.exec.MagicContext;
import com.example.akaishi.forbidden.api.magic.exec.MagicDeliveryAction;
import com.example.akaishi.forbidden.magic.exec.MagicScheduler;
import com.example.akaishi.forbidden.magic.table.MagicIds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * X6 回响（设计记忆 §19.3）—— <b>延迟 1~3s 才复现</b>。
 *
 * <p>它是唯一一个"投送本身带时间差"的 X：起手时什么都不发生，几秒后原地爆发一次。
 * 手感词是"悬念、赌"（§19.3），所以它<b>必须真的延迟</b> ——
 * 若实现成立即触发，回响就退化成了直射，这一整列 X 的语义就没了。
 *
 * <p><b>为什么用 {@link MagicScheduler} 而不是扩施法状态机</b>：
 * 那时会话早已结束、状态机也已不再持有它（§21.1 的终态只有 IDLE + 冷却）。
 * "未来的某一刻要发生什么"是独立的一件事，塞进状态机反而会让状态机的退出条件变模糊。
 *
 * <p><b>延迟取 2s</b>：§19.3 给的是 1~3s 区间，首轮取中值。
 * 它没有挂进公式（§21.2 的 X6 修正全是中性，因为复现收益已含在 TEMPORAL 的 E 基准里），
 * 故这里是一个常量而非派生值 —— 待调项（§13.3）。
 */
public final class EchoDeliverer implements IDeliverer {

    /** 回响延迟（秒）—— §19.3 的 1~3s 中值，待调 */
    public static final float ECHO_DELAY_SECONDS = 2f;

    @Override
    public String deliveryId() {
        return MagicIds.Delivery.ECHO;
    }

    @Override
    public boolean deliver(MagicContext context, MagicDeliveryAction action) {
        if (!context.usable() || !(context.level() instanceof ServerLevel level)) {
            return false;
        }
        // 复现点 = 起手瞬间的位置快照：回响复现的是"刚才那一发"，不该跟着施法者跑
        Vec3 at = ImmediateDeliverer.originOf(context);
        MagicScheduler.schedule(level, Math.round(ECHO_DELAY_SECONDS * 20f), () -> action.fire(at));
        return true;
    }
}
