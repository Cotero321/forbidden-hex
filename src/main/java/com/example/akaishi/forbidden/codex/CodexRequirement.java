package com.example.akaishi.forbidden.codex;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.function.Supplier;

/**
 * 秘典的「一条门槛要求」（<b>静态数据</b>，写在 {@link CodexTable} 里）——
 * 与 {@link CodexCondition} 的关系是"声明"与"求值结果"：
 * 本类只描述"要什么"，{@link CodexGates} 拿玩家状态求值后产出 {@link CodexCondition} 快照。
 *
 * <p><b>为什么与快照分成两个类</b>：静态表在类初始化期构建，此时物品注册域可能还没跑完
 * （同 {@code AkaishiAltarRecipe} 的老问题），故物品一律以 {@link Supplier} 持有、延迟求值；
 * 而快照只装"求值后的纯值"，要能安全穿网。混在一个类里就会被迫把 Supplier 也塞进网络结构。
 *
 * <p><b>六种要求与字段分工</b>（未使用的字段一律由静态工厂填中性值，禁止直接 new）：
 * <ul>
 *   <li>{@link CodexCondition#KIND_COG} —— 只用 {@link #cog()}；</li>
 *   <li>{@link CodexCondition#KIND_FIRST_SEEN} / {@link CodexCondition#KIND_PREREQ} —— 只用 {@link #id()}；</li>
 *   <li>{@link CodexCondition#KIND_ITEM} —— 用 {@link #item()} / {@link #count()} / {@link #consume()}；</li>
 *   <li>{@link CodexCondition#KIND_LOCATION} —— 用 {@link #id()}（群系 / 维度 / 结构的 id），
 *       群系<b>标签</b>形式另用 {@link #tag()}；</li>
 *   <li>{@link CodexCondition#KIND_DAMAGE} —— 单个伤害类型用 {@link #id()}，整族用 {@link #tag()}。</li>
 * </ul>
 *
 * <p><b>标签用通配 {@code TagKey<?>}</b>：群系标签与伤害类型标签是两种不同的泛型，而本记录的字段
 * 只有一个位置放它。求值端按 {@link #variant()} 断定实际泛型后再转换（转换点集中在 {@link CodexGates}
 * 的两处，附 {@code @SuppressWarnings} 说明），比为此拆两个记录类型省一层无收益的类型分支。
 *
 * @param kind    条件种类（沿用 {@link CodexCondition} 的 kind 常量，两端同一套编号）
 * @param variant 种类内部的分支（沿用 {@link CodexCondition} 的 variant 常量）
 * @param cog     认知门槛（仅 {@link CodexCondition#KIND_COG}）
 * @param item    目标物品的延迟求值引用（仅 {@link CodexCondition#KIND_ITEM}）
 * @param count   需要的个数（仅 {@link CodexCondition#KIND_ITEM}）
 * @param consume 阶段完成时是否消耗这些物品（仅 {@link CodexCondition#KIND_ITEM}）
 * @param id      目标 id（首见 / 前置节点 / 地点 / 单个伤害类型）
 * @param tag     目标标签（群系标签 / 伤害类型标签；非标签形式为 null）
 */
public record CodexRequirement(byte kind, byte variant, float cog, Supplier<Item> item, int count,
                               boolean consume, ResourceLocation id, TagKey<?> tag) {

    /** 认知门槛 */
    public static CodexRequirement cog(float min) {
        return new CodexRequirement(CodexCondition.KIND_COG, (byte) 0, min, null, 0, false, null, null);
    }

    /** 首见要求（复用理智系统的首见记档） */
    public static CodexRequirement firstSeen(ResourceLocation firstSeenId) {
        return new CodexRequirement(CodexCondition.KIND_FIRST_SEEN, (byte) 0, 0f,
                null, 0, false, firstSeenId, null);
    }

    /** 前置节点要求 */
    public static CodexRequirement prereq(ResourceLocation nodeId) {
        return new CodexRequirement(CodexCondition.KIND_PREREQ, (byte) 0, 0f,
                null, 0, false, nodeId, null);
    }

    /**
     * 物品要求。
     *
     * @param item    目标物品（延迟求值）
     * @param count   需要个数；&le;0 视为 1（"要一个"是唯一有意义的默认）
     * @param consume 阶段完成时是否消耗
     */
    public static CodexRequirement item(Supplier<Item> item, int count, boolean consume) {
        return new CodexRequirement(CodexCondition.KIND_ITEM,
                consume ? CodexCondition.ITEM_CONSUME : CodexCondition.ITEM_KEEP,
                0f, item, Math.max(1, count), consume, null, null);
    }

    /** 地点：群系 id */
    public static CodexRequirement biome(ResourceLocation biomeId) {
        return location(CodexCondition.LOC_BIOME, biomeId, null);
    }

    /** 地点：群系标签（tag 实例同时进 {@link #id()}，供界面取显示名） */
    public static CodexRequirement biomeTag(TagKey<Biome> biomeTag) {
        return location(CodexCondition.LOC_BIOME_TAG, biomeTag.location(), biomeTag);
    }

    /** 地点：维度 id */
    public static CodexRequirement dimension(ResourceLocation dimensionId) {
        return location(CodexCondition.LOC_DIMENSION, dimensionId, null);
    }

    /** 地点：结构 id */
    public static CodexRequirement structure(ResourceLocation structureId) {
        return location(CodexCondition.LOC_STRUCTURE, structureId, null);
    }

    /** 伤害：单个伤害类型 id */
    public static CodexRequirement damageType(ResourceLocation damageTypeId) {
        return new CodexRequirement(CodexCondition.KIND_DAMAGE, CodexCondition.DAMAGE_TYPE, 0f,
                null, 0, false, damageTypeId, null);
    }

    /** 伤害：伤害类型标签 */
    public static CodexRequirement damageTag(TagKey<DamageType> damageTag) {
        return new CodexRequirement(CodexCondition.KIND_DAMAGE, CodexCondition.DAMAGE_TAG, 0f,
                null, 0, false, damageTag.location(), damageTag);
    }

    /** 结构判定要的是"键"而不是裸 id：转换集中在这里，避免每个求值点各写一次 */
    public static ResourceKey<Structure> structureKey(ResourceLocation structureId) {
        return ResourceKey.create(Registries.STRUCTURE, structureId);
    }

    private static CodexRequirement location(byte variant, ResourceLocation targetId, TagKey<?> tag) {
        return new CodexRequirement(CodexCondition.KIND_LOCATION, variant, 0f,
                null, 0, false, targetId, tag);
    }
}
