package com.example.akaishi.forbidden.magic.cast;

/**
 * {@link MagicCarrierHook} 的静态注入口（与本体 {@code PoweredArmorQuery.install} 同一口径）。
 *
 * <p>载体模块在初始化时装入实现；魔法侧所有调用点经 {@code get()} 判空使用。
 * volatile 保证跨线程可见（钩子只在服务端主线程调用，但安装发生在初始化线程）。
 */
public final class MagicPayHooks {

    private static volatile MagicCarrierHook hook;

    private MagicPayHooks() {
    }

    /** 载体模块启动时装入（重复安装以最后一次为准） */
    public static void install(MagicCarrierHook value) {
        hook = value;
    }

    /** 当前钩子；载体模块未装时为 {@code null}（调用点判空回原版语义） */
    public static MagicCarrierHook get() {
        return hook;
    }
}
