package com.example.akaishi.forbidden.api.sanity;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * SANC 恢复来源注册表。
 *
 * <p>结算层按 {@code getAll()} 逐条调用 {@link ISanityRestoreSource#matches}——一次使用动作可能命中多条来源
 * （例如某物品同时属于两个附属的"首用"体系），全部命中都会结算，不做互斥。
 * 需要互斥的附属请用 id 前缀 + {@link SanityCallbacks} 自行协调。
 */
public final class SanityRestoreRegistry {

    private static final ConcurrentMap<ResourceLocation, ISanityRestoreSource> SOURCES = new ConcurrentHashMap<>();

    private SanityRestoreRegistry() {
    }

    /** 注册来源；同 id 已存在时抛异常（要替换请用 {@link #override}） */
    public static void register(ISanityRestoreSource source) {
        validate(source);
        if (SOURCES.putIfAbsent(source.id(), source) != null) {
            throw new IllegalArgumentException("Sanity restore source already registered: " + source.id());
        }
    }

    /** 覆盖注册：同 id 直接替换 */
    public static void override(ISanityRestoreSource source) {
        validate(source);
        SOURCES.put(source.id(), source);
    }

    /** 按 id 查询，不存在返回 null */
    public static ISanityRestoreSource get(ResourceLocation id) {
        return id == null ? null : SOURCES.get(id);
    }

    /** 按 id 字符串查询（非法 id 返回 null，不抛异常） */
    public static ISanityRestoreSource get(String id) {
        ResourceLocation resourceLocation = ResourceLocation.tryParse(id);
        return resourceLocation == null ? null : get(resourceLocation);
    }

    /** 注销来源（不影响玩家存档中已记录的一次性 id） */
    public static ISanityRestoreSource unregister(ResourceLocation id) {
        return id == null ? null : SOURCES.remove(id);
    }

    /** 全部已注册来源（只读快照，顺序不稳定） */
    public static Collection<ISanityRestoreSource> getAll() {
        return List.copyOf(SOURCES.values());
    }

    private static void validate(ISanityRestoreSource source) {
        if (source == null || source.id() == null) {
            throw new IllegalArgumentException("Sanity restore source ID must be a valid namespaced resource location");
        }
        if (!Float.isFinite(source.sancAmount())) {
            throw new IllegalArgumentException("Sanity restore sancAmount must be finite: " + source.id());
        }
    }
}
