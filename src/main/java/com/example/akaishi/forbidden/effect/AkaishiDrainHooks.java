package com.example.akaishi.forbidden.effect;

import net.minecraft.world.entity.LivingEntity;

/**
 * 「仪式吸取」的 common↔forge 依赖倒置钩子（D257）。
 * <p>被吸死的生物须无掉落、无经验、不算击杀（D158），而掉落事件属平台侧 API，
 * common 不可见，故此处只声明抽象接口，由 forge 侧实现并注入。
 * <p>范式与 {@link ForbiddenSetHooks} 一致：volatile 中性默认实现 + 静态注入器，
 * 未注入时（如纯 common 环境）静默降级为「不做任何标记」，不抛异常。
 */
public final class AkaishiDrainHooks {

    /** 标记器：{@code drained=true} 打上"死于吸取"标记，{@code false} 撤销标记 */
    public interface DrainMarker {
        void mark(LivingEntity entity, boolean drained);
    }

    private static volatile DrainMarker drainMarker = (entity, drained) -> {
    };

    private AkaishiDrainHooks() {
    }

    /** 注入平台实现（由平台初始化调用一次） */
    public static void setDrainMarker(DrainMarker impl) {
        drainMarker = impl;
    }

    /** 致死前打标：平台侧据此在掉落/经验事件中豁免 */
    public static void markDrained(LivingEntity entity) {
        drainMarker.mark(entity, true);
    }

    /** 撤销标记（目标实际未死时的兜底，避免其日后正常死亡被误吞掉落） */
    public static void clearDrained(LivingEntity entity) {
        drainMarker.mark(entity, false);
    }
}
