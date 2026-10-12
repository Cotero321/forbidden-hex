package com.example.akaishi.forbidden.api.sanity;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 食补档位注册表（物品 id → 档位）。
 *
 * <p>一个物品只能有一条档位：同 id 重复注册抛异常，要替换请用 {@link #override}。
 * 注册时对 {@link ISanityFoodProfile#decayTiers()} 做<b>合法性校验</b>，
 * 因为非法档位（全 0、含 NaN）会让食补静默失效——那属于"看得到用不到"的坏数据，必须当场拒绝而不是运行时才发现。
 */
public final class SanityFoodRegistry {

    private static final ConcurrentMap<ResourceLocation, ISanityFoodProfile> PROFILES = new ConcurrentHashMap<>();

    private SanityFoodRegistry() {
    }

    /** 注册食补档位；同物品 id 已存在时抛异常 */
    public static void register(ISanityFoodProfile profile) {
        validate(profile);
        if (PROFILES.putIfAbsent(profile.itemId(), profile) != null) {
            throw new IllegalArgumentException("Sanity food profile already registered: " + profile.itemId());
        }
    }

    /** 覆盖注册：同物品 id 直接替换 */
    public static void override(ISanityFoodProfile profile) {
        validate(profile);
        PROFILES.put(profile.itemId(), profile);
    }

    /** 按物品 id 查询，不存在返回 null */
    public static ISanityFoodProfile get(ResourceLocation itemId) {
        return itemId == null ? null : PROFILES.get(itemId);
    }

    /** 按物品 id 字符串查询（非法 id 返回 null，不抛异常） */
    public static ISanityFoodProfile get(String itemId) {
        ResourceLocation resourceLocation = ResourceLocation.tryParse(itemId);
        return resourceLocation == null ? null : get(resourceLocation);
    }

    /** 注销档位，返回被移除的实例；不存在返回 null */
    public static ISanityFoodProfile unregister(ResourceLocation itemId) {
        return itemId == null ? null : PROFILES.remove(itemId);
    }

    /** 全部已注册档位（只读快照，顺序不稳定） */
    public static Collection<ISanityFoodProfile> getAll() {
        return List.copyOf(PROFILES.values());
    }

    private static void validate(ISanityFoodProfile profile) {
        if (profile == null || profile.itemId() == null) {
            throw new IllegalArgumentException("Sanity food itemId must be a valid namespaced resource location");
        }
        if (!Float.isFinite(profile.totalSan()) || !Float.isFinite(profile.instantSan())
                || !Float.isFinite(profile.protection())) {
            throw new IllegalArgumentException("Sanity food amounts must be finite: " + profile.itemId());
        }
        if (profile.windowTicks() <= 0 && profile.totalSan() != 0f) {
            // 有窗口总量却没有窗口长度 → 无法分摊，必然是静默失效的坏数据
            throw new IllegalArgumentException("Sanity food windowTicks must be > 0 when totalSan != 0: " + profile.itemId());
        }
        if (profile.windowTicks() < 0 || profile.refreshTicks() < 0) {
            throw new IllegalArgumentException("Sanity food ticks must be >= 0: " + profile.itemId());
        }
        float[] tiers = profile.decayTiers();
        if (tiers == null || tiers.length == 0) {
            throw new IllegalArgumentException("Sanity food decayTiers must not be empty: " + profile.itemId());
        }
        for (float tier : tiers) {
            if (!Float.isFinite(tier) || tier < 0f) {
                throw new IllegalArgumentException("Sanity food decayTiers must be finite and >= 0: " + profile.itemId());
            }
        }
        if (tiers[0] <= 0f) {
            // 第 1 档为 0 → 首次食用就无效果，等于注册了也没用
            throw new IllegalArgumentException("Sanity food decayTiers[0] must be > 0: " + profile.itemId());
        }
    }
}
