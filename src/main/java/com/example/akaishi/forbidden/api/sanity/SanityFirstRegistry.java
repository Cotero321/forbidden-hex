package com.example.akaishi.forbidden.api.sanity;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 首见条目注册表。
 *
 * <p><b>注销的存档语义（重要）</b>：{@link #unregister} 只影响"本次运行还能不能触发"，
 * <b>不会</b>、也不允许影响玩家存档里已记录的 id。已触发过的玩家即使条目被注销，
 * 存档中的 id 仍须原样保留（见 {@link ISanityFirstEncounter} 的存档容错承诺）——
 * 否则会出现"卸载→装载"循环刷首见奖励。
 */
public final class SanityFirstRegistry {

    private static final ConcurrentMap<ResourceLocation, ISanityFirstEncounter> ENCOUNTERS = new ConcurrentHashMap<>();

    private SanityFirstRegistry() {
    }

    /** 注册首见条目；同 id 已存在时抛异常（要替换请用 {@link #override}） */
    public static void register(ISanityFirstEncounter encounter) {
        validate(encounter);
        if (ENCOUNTERS.putIfAbsent(encounter.id(), encounter) != null) {
            throw new IllegalArgumentException("Sanity first encounter already registered: " + encounter.id());
        }
    }

    /** 覆盖注册：同 id 直接替换 */
    public static void override(ISanityFirstEncounter encounter) {
        validate(encounter);
        ENCOUNTERS.put(encounter.id(), encounter);
    }

    /** 按 id 查询，不存在返回 null */
    public static ISanityFirstEncounter get(ResourceLocation id) {
        return id == null ? null : ENCOUNTERS.get(id);
    }

    /** 按 id 字符串查询（非法 id 返回 null，不抛异常） */
    public static ISanityFirstEncounter get(String id) {
        ResourceLocation resourceLocation = ResourceLocation.tryParse(id);
        return resourceLocation == null ? null : get(resourceLocation);
    }

    /** 注销条目（不影响玩家存档中已记录的 id） */
    public static ISanityFirstEncounter unregister(ResourceLocation id) {
        return id == null ? null : ENCOUNTERS.remove(id);
    }

    /** 全部已注册条目（只读快照，顺序不稳定） */
    public static Collection<ISanityFirstEncounter> getAll() {
        return List.copyOf(ENCOUNTERS.values());
    }

    private static void validate(ISanityFirstEncounter encounter) {
        if (encounter == null || encounter.id() == null) {
            throw new IllegalArgumentException("Sanity first encounter ID must be a valid namespaced resource location");
        }
        if (encounter.langKey() == null || encounter.langKey().isEmpty()) {
            throw new IllegalArgumentException("Sanity first encounter langKey must not be empty: " + encounter.id());
        }
        if (!Float.isFinite(encounter.sancDelta()) || !Float.isFinite(encounter.cogDelta())) {
            throw new IllegalArgumentException("Sanity first encounter deltas must be finite: " + encounter.id());
        }
    }
}
