package com.example.akaishi.forbidden.api.magic.exec;

/**
 * 阶段 ② 投射 / 投送（设计记忆 §19.2 / §19.3 / §21.1）。
 *
 * <p><b>X 是"手感"的真正来源</b>：同一 E 配不同 X，玩法就是两回事 ——
 * 直射只要点一下，引导要你按住并承担被打断的风险（§19.3 关键判据）。
 * 因此本接口的实现是**七种截然不同的时序**，而非七组系数。
 *
 * <p><b>职责边界</b>：实现只决定"<b>何时、何地</b>触发效果"（可立即、可延迟、可多段），
 * 通过调用 {@link MagicDeliveryAction#fire} 交还给编排器。实现<b>不得</b>自行遍历目标或施加效果。
 *
 * <p><b>与状态机的关系</b>：前摇（{@code CHARGING}）由施法状态机负责，
 * 状态机在前摇结束时才调用 {@link #deliver}。因此本接口拿到的时刻 = "第一段该生效了"，
 * 无需再自己数前摇。X3 引导与 X7 连发的**逐 tick 推进**由状态机驱动（见 {@code IPersistenceHandler}
 * 之外的连发/引导计时），{@code deliver} 只负责"发起"。
 */
public interface IDeliverer {

    /** 本投送器负责的交付方式 X id（注册表按它索引） */
    String deliveryId();

    /**
     * 发起投送。
     *
     * @param context 施法上下文
     * @param action  触发句柄：决定"何时何地生效"时调用它
     * @return {@code true} = 已受理；{@code false} = 无法投送（如无路径 / 无合法落点），
     *         由状态机按"自然终止（不返还）"处理
     */
    boolean deliver(MagicContext context, MagicDeliveryAction action);
}
