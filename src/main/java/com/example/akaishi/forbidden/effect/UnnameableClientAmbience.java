package com.example.akaishi.forbidden.effect;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * 「不可名状」客户端表现强度（仅客户端调用）。
 * <p>
 * 每客户端 tick 读取效果等级并换算为目标强度（I 级 0.6、II 级及以上拉满），
 * 再按指数衰减趋近目标，使画面表现随效果生效/消退平滑过渡，而非瞬间开关。
 */
public final class UnnameableClientAmbience {

    /** 每 tick 向目标趋近比例，近似原先 60 FPS 下的过渡速度。 */
    private static final float LERP = 0.27f;
    /** I 级时的基础强度，等级越高越强（每级 +0.4，上限 1） */
    private static final float BASE_LEVEL = 0.6f;

    private static float target;
    private static float current;

    private UnnameableClientAmbience() {
    }

    /** 由后处理调度器每 tick 更新一次，所有表现层只读取结果。 */
    public static void update() {
        target = resolveTarget();
        float diff = target - current;
        if (diff * diff < 1e-6f) {
            current = target;
        } else {
            current += diff * LERP;
        }
    }

    public static float level() {
        return current;
    }

    public static void reset() {
        target = 0f;
        current = 0f;
    }

    /** 只读取当前强度、不推进过渡：供同一帧内的其他表现层复用，避免重复推进 */
    public static float peek() {
        return current;
    }

    /** 读取本地玩家「不可名状」的等级并换算为 0~1 强度（无效果返回 0） */
    private static float resolveTarget() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return 0f;
        }
        MobEffectInstance instance = player.getEffect(AkaishiForbiddenEffects.UNNAMEABLE.get());
        if (instance == null) {
            return 0f;
        }
        return Math.min(1f, BASE_LEVEL + 0.4f * instance.getAmplifier());
    }
}
