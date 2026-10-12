package com.example.akaishi.forbidden.api.magic.exec;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 执行层四阶段的<b>注册门面</b>（设计记忆 §11 / §19.2 / §36）。
 *
 * <p><b>为什么内置内容与附属走同一张表</b>：与理智系统的 {@code SanityBuiltinRules} 同一口径 ——
 * 内置的 20 个 E / 7 个 X 与附属注册的扩展项<b>同权</b>，不存在"官方走后门"的第二条路径。
 * 这样"能不能被覆盖 / 会不会冲突"只需在一处回答。
 *
 * <p><b>为什么不放在实现包</b>：{@link IPrimitiveExecutor} 等四接口在 api，
 * 若注册入口留在实现包，附属就只能"实现接口却注册不进去" —— 接口等于废纸。
 *
 * <p><b>线程安全</b>：注册可能来自不同线程（数据包 / 服务端启动 / 附属初始化），
 * 故一律 {@link ConcurrentHashMap}；查询在服务端 tick 内命中，无锁。
 *
 * <p><b>重复注册 = 后者覆盖前者</b>（便于附属覆写内置行为），但同名同类型时以最后一次为准。
 * 值为 {@code null} 的注册被忽略（避免"注册了个空"导致后续静默 NPE）。
 */
public final class MagicExecRegistry {

    /** E id → 执行器 */
    private static final Map<String, IPrimitiveExecutor> EFFECTS = new ConcurrentHashMap<>();
    /** X id → 投送器 */
    private static final Map<String, IDeliverer> DELIVERIES = new ConcurrentHashMap<>();
    /** 形状 G → 目标解析器 */
    private static final Map<MagicShape, ITargetResolver> SHAPES = new ConcurrentHashMap<>();
    /** 结构词 id → 持续处理器 */
    private static final Map<String, IPersistenceHandler> PERSISTENCE = new ConcurrentHashMap<>();

    private MagicExecRegistry() {
    }

    // ==================== 注册 ====================

    public static void register(IPrimitiveExecutor executor) {
        if (executor != null && executor.effectId() != null) {
            EFFECTS.put(executor.effectId(), executor);
        }
    }

    public static void register(IDeliverer deliverer) {
        if (deliverer != null && deliverer.deliveryId() != null) {
            DELIVERIES.put(deliverer.deliveryId(), deliverer);
        }
    }

    public static void register(ITargetResolver resolver) {
        if (resolver != null && resolver.shape() != null) {
            SHAPES.put(resolver.shape(), resolver);
        }
    }

    public static void register(IPersistenceHandler handler) {
        if (handler != null && handler.structureId() != null) {
            PERSISTENCE.put(handler.structureId(), handler);
        }
    }

    // ==================== 查询（无则返回 null，调用方必须容忍缺失） ====================

    public static IPrimitiveExecutor effect(String effectId) {
        return effectId == null ? null : EFFECTS.get(effectId);
    }

    public static IDeliverer delivery(String deliveryId) {
        return deliveryId == null ? null : DELIVERIES.get(deliveryId);
    }

    public static ITargetResolver target(MagicShape shape) {
        return shape == null ? null : SHAPES.get(shape);
    }

    public static IPersistenceHandler persistence(String structureId) {
        return structureId == null ? null : PERSISTENCE.get(structureId);
    }

    // ==================== 只读视图（自检 / 调试用） ====================

    public static Collection<String> effectIds() {
        return java.util.List.copyOf(EFFECTS.keySet());
    }

    public static Collection<String> deliveryIds() {
        return java.util.List.copyOf(DELIVERIES.keySet());
    }

    public static Collection<MagicShape> shapes() {
        return java.util.List.copyOf(SHAPES.keySet());
    }

    public static Collection<String> persistenceIds() {
        return java.util.List.copyOf(PERSISTENCE.keySet());
    }

    /** 清空全部注册（自检 / 重载用；正常流程不调） */
    public static void clearAll() {
        EFFECTS.clear();
        DELIVERIES.clear();
        SHAPES.clear();
        PERSISTENCE.clear();
    }
}
