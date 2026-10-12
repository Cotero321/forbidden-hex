package com.example.akaishi.forbidden.sanity;

/**
 * 客户端<b>深海视野锁定</b>的强度源（仅客户端加载路径使用）。
 *
 * <p><b>为什么强度在客户端自己算</b>：这是纯表现量。判定所需的三个事实
 * （是否主世界 / 是否海洋群系 / 是否 Y&lt;0）客户端本地世界数据里全都有，
 * 服务端为此多发一个包只是白付带宽 —— 与 {@link ClientSanityVision} 同一取舍。
 * 服务端侧的对应机制是环境规则 {@code forbidden_hex:deep_sea} / {@code forbidden_hex:deep_sea_bright}（P2 已落地），
 * 两者判据同源（主世界 + 海洋 + Y&lt;0），因此"扣理智"与"看不清"总是同时发生。
 *
 * <p><b>平滑</b>：目标强度由客户端 tick 每 20t 重算一次，雾渲染每帧按固定比例向目标趋近，
 * 于是"进出深海"是淡入淡出而不是硬切；离开深海后强度自然衰减回 0。
 * 字段为 volatile：渲染线程只读，主线程写。
 */
public final class ClientDeepSeaLock {

    /** 每帧向目标趋近的比例（越大过渡越快；待调手感值） */
    private static final float LERP = 0.06f;
    /** 低于此强度视为不生效（避免为不可见的量做雾距/雾色运算） */
    public static final float MIN_VISIBLE = 0.01f;

    private static volatile float target;
    private static volatile float current;

    private ClientDeepSeaLock() {
    }

    /** 主线程设置目标强度（0~1），自动钳制 */
    public static void setTarget(float value) {
        target = Math.max(0f, Math.min(1f, value));
    }

    /** 渲染线程调用：推进平滑过渡并返回当前强度（0~1） */
    public static float current() {
        float diff = target - current;
        if (diff * diff < 1e-6f) {
            current = target;
        } else {
            current += diff * LERP;
        }
        return current;
    }
}
