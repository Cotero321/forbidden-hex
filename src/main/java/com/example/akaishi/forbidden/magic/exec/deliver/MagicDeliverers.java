package com.example.akaishi.forbidden.magic.exec.deliver;

import com.example.akaishi.forbidden.api.magic.exec.MagicExecRegistry;
import com.example.akaishi.forbidden.magic.table.MagicIds;

/**
 * 投送器族的<b>注册入口</b>（设计记忆 §19.3 / §21.4）。
 *
 * <p><b>7 / 7 种 X 全部就位</b>：X1 直射 · X2 投射 · X3 引导 · X4 附着 · X5 布场 · X6 回响 · X7 连发。
 */
public final class MagicDeliverers {

    private MagicDeliverers() {
    }

    /** 注册全部内置投送器 */
    public static void register() {
        // 「立即生效一次」型：四个 X 共用一份动作，差异在状态机与 X 修正系数（见 ImmediateDeliverer 类注释）
        MagicExecRegistry.register(new ImmediateDeliverer(MagicIds.Delivery.INSTANT));
        MagicExecRegistry.register(new ImmediateDeliverer(MagicIds.Delivery.CHANNEL));
        MagicExecRegistry.register(new ImmediateDeliverer(MagicIds.Delivery.ATTACH));
        MagicExecRegistry.register(new ImmediateDeliverer(MagicIds.Delivery.VOLLEY));
        // 三个"不在原地立刻响"的 X：飞到才响 / 留在落点每秒响 / 等一会儿再响
        MagicExecRegistry.register(new ProjectileDeliverer());
        MagicExecRegistry.register(new PlacementDeliverer());
        MagicExecRegistry.register(new EchoDeliverer());
    }
}
