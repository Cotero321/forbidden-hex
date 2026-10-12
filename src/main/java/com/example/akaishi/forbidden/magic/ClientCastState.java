package com.example.akaishi.forbidden.magic;

import com.example.akaishi.forbidden.magic.cast.MagicCastState;

/**
 * 客户端<b>施法状态只读镜像</b>：由 {@code MagicCastStateS2C} 填充，供 HUD 判断"是否在释放中"。
 *
 * <p><b>为什么单独一条即时包，而不是并进 {@link MagicSyncS2C}</b>：
 * 那条包是"数值变化才发 + 最小间隔 0.5s"的节流包，适合池量这类连续量；
 * 而"开始施法 / 结束施法"是<b>离散事件</b>，延迟半秒会让准星光环迟到 ——
 * 前摇本身往往只有 0.5~1.5 秒，迟到半秒等于画了个寂寞。故状态走独立的即时小包。
 *
 * <p>写入在客户端主线程（接收器已调度），读取在渲染线程，故字段 volatile。
 */
public final class ClientCastState {

    /** 未收到任何包时的默认态：空闲（与"没在施法"一致，不会闪一个假光环） */
    private static volatile MagicCastState state = MagicCastState.IDLE;

    private ClientCastState() {
    }

    /** 是否正在释放中（前摇 / 引导 / 连发三态统一为"释放中"） */
    public static boolean casting() {
        return state.isActive();
    }

    /** 细粒度状态（前摇与持续段可分别取用） */
    public static MagicCastState state() {
        return state;
    }

    /** 接收器落地（仅客户端主线程调用） */
    public static void set(int ordinal) {
        MagicCastState[] values = MagicCastState.values();
        state = ordinal >= 0 && ordinal < values.length ? values[ordinal] : MagicCastState.IDLE;
    }

    /**
     * 断线 / 退服时清零。
     *
     * <p>必须清：会话在服务端，客户端若停在"释放中"，重连后光环会一直转下去。
     * 正常结束路径由服务端发 IDLE 覆盖，这里只兜"连接没了、包不会再来的"那一档。
     */
    public static void clear() {
        state = MagicCastState.IDLE;
    }
}
