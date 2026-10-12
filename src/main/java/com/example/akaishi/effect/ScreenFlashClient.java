package com.example.akaishi.effect;

import net.minecraft.Util;

/**
 * 屏幕泛红的客户端状态（仅客户端持有，D100/D208）。
 * <p>
 * 服务端只在关键瞬间推一次「开始」信号，本类负责把这一次信号铺成
 * 「淡入 → 保持 → 淡出」的强度包络，渲染层每帧读 {@link #level()} 即可，
 * 无需再关心剩余时长；包络走完自动归零，不残留常亮。
 * <p>
 * 重复触发（如四件饰品几乎同时跑满）取较强的一次，避免叠成超长红屏（D123 收敛）。
 */
public final class ScreenFlashClient {

    /** 淡入 / 淡出各占包络的比例，其余为全亮保持段 */
    private static final float FADE_RATIO = 0.25f;

    private static volatile long startAt;
    private static volatile long endAt;
    private static volatile float peak;

    private ScreenFlashClient() {
    }

    /** 触发一次泛红：{@code ticks} 为持续时长，{@code intensity} 为峰值强度（0~1） */
    public static synchronized void trigger(int ticks, float intensity) {
        long now = Util.getMillis();
        float clamped = Math.max(0f, Math.min(1f, intensity));
        if (clamped <= 0f) {
            return;
        }
        // 已有表现尚未结束时，只在更强时覆盖，避免连续触发把红屏拉长
        if (now < endAt && peak >= clamped) {
            return;
        }
        startAt = now;
        endAt = now + Math.max(1, ticks) * 50L;
        peak = clamped;
    }

    /** 渲染线程读取当前强度（0 = 无表现，调用方据此跳过绘制） */
    public static float level() {
        long now = Util.getMillis();
        long end = endAt;
        if (now >= end) {
            return 0f;
        }
        float total = Math.max(1f, end - startAt);
        float t = (now - startAt) / total;
        float envelope;
        if (t < FADE_RATIO) {
            envelope = t / FADE_RATIO;
        } else if (t > 1f - FADE_RATIO) {
            envelope = (1f - t) / FADE_RATIO;
        } else {
            envelope = 1f;
        }
        return Math.max(0f, Math.min(1f, envelope)) * peak;
    }
}
