package com.example.akaishi.forbidden.api.sanity;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 阈值钩子注册表。
 *
 * <p>钩子本身不携带阈值数值——档位（80/60/40/20/0）由核心统一持有，
 * 注册表只回答"跨档时该通知谁"，这样附属无法自定义档位（避免两套百分比体系打架），
 * 只能扩展"跨档后做什么"。
 */
public final class SanityThresholdRegistry {

    private static final ConcurrentMap<ResourceLocation, ISanityThresholdHook> HOOKS = new ConcurrentHashMap<>();

    private SanityThresholdRegistry() {
    }

    /** 注册钩子；同 id 已存在时抛异常（要替换请用 {@link #override}） */
    public static void register(ISanityThresholdHook hook) {
        validate(hook);
        if (HOOKS.putIfAbsent(hook.id(), hook) != null) {
            throw new IllegalArgumentException("Sanity threshold hook already registered: " + hook.id());
        }
    }

    /** 覆盖注册：同 id 直接替换 */
    public static void override(ISanityThresholdHook hook) {
        validate(hook);
        HOOKS.put(hook.id(), hook);
    }

    /** 按 id 查询，不存在返回 null */
    public static ISanityThresholdHook get(ResourceLocation id) {
        return id == null ? null : HOOKS.get(id);
    }

    /** 按 id 字符串查询（非法 id 返回 null，不抛异常） */
    public static ISanityThresholdHook get(String id) {
        ResourceLocation resourceLocation = ResourceLocation.tryParse(id);
        return resourceLocation == null ? null : get(resourceLocation);
    }

    /** 注销钩子，返回被移除的实例；不存在返回 null */
    public static ISanityThresholdHook unregister(ResourceLocation id) {
        return id == null ? null : HOOKS.remove(id);
    }

    /** 全部已注册钩子（只读快照，顺序不稳定；跨档通知顺序不保证） */
    public static Collection<ISanityThresholdHook> getAll() {
        return List.copyOf(HOOKS.values());
    }

    private static void validate(ISanityThresholdHook hook) {
        if (hook == null || hook.id() == null) {
            throw new IllegalArgumentException("Sanity threshold hook ID must be a valid namespaced resource location");
        }
    }
}
