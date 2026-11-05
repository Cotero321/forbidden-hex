package com.example.akaishi.forbidden;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/** 物品分类与公共材料接口；分类标签不代替魔法类型、来源和 NBT 校验。 */
public final class ForbiddenItemTags {
    public static final TagKey<Item> HERBS = internal("herbs");
    public static final TagKey<Item> ORE_FRAGMENTS = internal("ore_fragments");
    public static final TagKey<Item> CINNABAR_FRAGMENTS = internal("ore_fragments/cinnabar");
    public static final TagKey<Item> SULFUR_FRAGMENTS = internal("ore_fragments/sulfur");
    public static final TagKey<Item> WAND_PARTS = internal("wand_parts");
    public static final TagKey<Item> WAND_TIPS = internal("wand_parts/tips");
    public static final TagKey<Item> WAND_HANDLES = internal("wand_parts/handles");
    public static final TagKey<Item> WAND_CORES = internal("wand_parts/cores");
    public static final TagKey<Item> WANDS = internal("wands");
    public static final TagKey<Item> INKS = internal("inks");
    public static final TagKey<Item> BOTTLED = internal("bottled");
    public static final TagKey<Item> MAGIC_CORES = internal("magic_cores");

    public static final TagKey<Item> SILVER_ORES = common("ores/silver");
    public static final TagKey<Item> RAW_SILVER = common("raw_materials/silver");
    public static final TagKey<Item> SILVER_INGOTS = common("ingots/silver");
    public static final TagKey<Item> SILVER_NUGGETS = common("nuggets/silver");
    public static final TagKey<Item> SILVER_BLOCKS = common("storage_blocks/silver");
    public static final TagKey<Item> RAW_SILVER_BLOCKS = common("storage_blocks/raw_silver");

    private ForbiddenItemTags() { }

    private static TagKey<Item> internal(String path) {
        return TagKey.create(Registries.ITEM, new ResourceLocation(AkaishiForbiddenMod.MOD_ID, path));
    }

    private static TagKey<Item> common(String path) {
        return TagKey.create(Registries.ITEM, new ResourceLocation("forge", path));
    }
}
