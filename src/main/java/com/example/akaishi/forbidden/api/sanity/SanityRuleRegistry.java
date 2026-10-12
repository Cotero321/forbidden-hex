package com.example.akaishi.forbidden.api.sanity;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 环境规则注册表，主模组与附属模组共用。
 *
 * <p>线程安全（ConcurrentHashMap），注册时机不敏感：结算层在每次结算时<b>惰性读表</b>，
 * 不缓存快照，因此附属在任意时刻注册/注销都能被下一次结算看到。
 */
public final class SanityRuleRegistry {

    private static final ConcurrentMap<ResourceLocation, ISanityRule> RULES = new ConcurrentHashMap<>();

    private SanityRuleRegistry() {
    }

    /** 注册规则；同 id 已存在时抛异常（要替换请用 {@link #override}） */
    public static void register(ISanityRule rule) {
        validate(rule);
        if (RULES.putIfAbsent(idOf(rule), rule) != null) {
            throw new IllegalArgumentException("Sanity rule already registered: " + rule.id());
        }
    }

    /** 覆盖注册：同 id 直接替换（预留 id 抢占 / 开发期热替换用） */
    public static void override(ISanityRule rule) {
        validate(rule);
        RULES.put(idOf(rule), rule);
    }

    /** 按 id 查询，不存在返回 null */
    public static ISanityRule get(ResourceLocation id) {
        return id == null ? null : RULES.get(id);
    }

    /** 按 id 字符串查询（非法 id 返回 null，不抛异常） */
    public static ISanityRule get(String id) {
        ResourceLocation resourceLocation = ResourceLocation.tryParse(id);
        return resourceLocation == null ? null : get(resourceLocation);
    }

    /** 注销规则，返回被移除的实例；不存在返回 null */
    public static ISanityRule unregister(ResourceLocation id) {
        return id == null ? null : RULES.remove(id);
    }

    /**
     * 全部已注册规则。
     *
     * <p>只读快照，但<b>顺序不稳定</b>（哈希表序）；结算层必须按 {@link ISanityRule#priority()} 显式排序，
     * 不要依赖返回顺序。
     */
    public static Collection<ISanityRule> getAll() {
        return List.copyOf(RULES.values());
    }

    private static ResourceLocation idOf(ISanityRule rule) {
        return rule == null ? null : rule.id();
    }

    private static void validate(ISanityRule rule) {
        if (rule == null || rule.id() == null) {
            throw new IllegalArgumentException("Sanity rule ID must be a valid namespaced resource location");
        }
        if (rule.periodTicks() <= 0) {
            throw new IllegalArgumentException("Sanity rule periodTicks must be > 0: " + rule.id());
        }
        if (!Float.isFinite(rule.amountPerPeriod())) {
            throw new IllegalArgumentException("Sanity rule amountPerPeriod must be finite: " + rule.id());
        }
        if (rule.capPerExposure() < 0) {
            throw new IllegalArgumentException("Sanity rule capPerExposure must be >= 0: " + rule.id());
        }
    }
}
