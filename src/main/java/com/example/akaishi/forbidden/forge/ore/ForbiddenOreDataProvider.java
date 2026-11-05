package com.example.akaishi.forbidden.forge.ore;

import com.example.akaishi.forbidden.ForbiddenItemTags;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class ForbiddenOreDataProvider implements DataProvider {
    private static final String NS = "forbidden_hex";
    private static final String PREFIX = "forbidden_hex_";
    private static final List<String> ORES = List.of("cinnabar", "sulfur", "silver");
    private static final List<String> MATERIALS = List.of("cinnabar_fragment", "sulfur_fragment",
            "raw_silver", "silver_ingot", "silver_nugget");
    private final Path root;
    private final List<CompletableFuture<?>> writes = new ArrayList<>();
    private CachedOutput output;

    public ForbiddenOreDataProvider(PackOutput output) { root = output.getOutputFolder(); }

    @Override public CompletableFuture<?> run(CachedOutput cached) {
        output = cached;
        writes.clear();
        List<String> mineralBlocks = new ArrayList<>();
        for (String ore : ORES) {
            List<String> variants = List.of(id(ore + "_ore"), id("deepslate_" + ore + "_ore"));
            mineralBlocks.addAll(variants);
            for (String prefix : List.of("", "deepslate_")) {
                String name = prefix + ore + "_ore";
                block(name);
                oreLoot(name, ore.equals("silver") ? "raw_silver" : ore + "_fragment", !ore.equals("silver"));
            }
            for (String type : List.of("blocks", "items")) tag("forge", type, "ores/" + ore, variants);
        }
        List<String> oreTags = new ArrayList<>(ORES.stream().map(name -> "#forge:ores/" + name).toList());
        oreTags.add("#forge:ores/magic_stone");
        List<String> deepslateOres = new ArrayList<>(ORES.stream().map(name -> id("deepslate_" + name + "_ore")).toList());
        deepslateOres.add(id("deepslate_magic_stone_ore"));
        for (String type : List.of("blocks", "items")) {
            tag("forge", type, "ores", oreTags);
            tag("forge", type, "ores/magic_stone", List.of(id("deepslate_magic_stone_ore")));
            tag("forge", type, "ores_in_ground/stone", ORES.stream().map(name -> id(name + "_ore")).toList());
            tag("forge", type, "ores_in_ground/deepslate", deepslateOres);
            tag("forge", type, "ore_rates/singular", List.of("#forge:ores/silver", "#forge:ores/magic_stone"));
            tag("forge", type, "ore_rates/dense", List.of("#forge:ores/cinnabar", "#forge:ores/sulfur"));
            tag("forge", type, "storage_blocks/silver", List.of(id("silver_block")));
            tag("forge", type, "storage_blocks/raw_silver", List.of(id("raw_silver_block")));
            tag("forge", type, "storage_blocks/magic_stone", List.of(id("magic_stone_block")));
            tag("forge", type, "storage_blocks", List.of("#forge:storage_blocks/silver", "#forge:storage_blocks/raw_silver",
                    "#forge:storage_blocks/magic_stone"));
        }
        for (String name : List.of("silver_block", "raw_silver_block")) {
            block(name);
            dropSelf(name);
            mineralBlocks.add(id(name));
        }
        tag(NS, "blocks", "mineral_blocks", mineralBlocks);
        for (String name : MATERIALS) item(name);
        for (String[] entry : List.of(new String[]{"raw_materials", "raw_silver"},
                new String[]{"ingots", "silver_ingot"}, new String[]{"nuggets", "silver_nugget"})) {
            tag("forge", "items", entry[0] + "/silver", List.of(id(entry[1])));
            tag("forge", "items", entry[0], List.of("#forge:" + entry[0] + "/silver"));
        }
        block("deepslate_magic_stone_ore");
        oreLoot("deepslate_magic_stone_ore", "magic_stone", false);
        block("magic_stone_block");
        dropSelf("magic_stone_block");
        item("magic_stone");
        tag(NS, "blocks", "diamond_mineral_blocks", List.of(id("deepslate_magic_stone_ore"), id("magic_stone_block")));
        tag("minecraft", "blocks", "needs_diamond_tool", List.of("#forbidden_hex:diamond_mineral_blocks"));
        tag("forge", "items", "gems/magic_stone", List.of(id("magic_stone")));
        tag("forge", "items", "gems", List.of("#forge:gems/magic_stone"));
        compact("magic_stone", "magic_stone_block");
        worldgen("magic_stone", -64, -16, 1, 3, 0.0, true);
        recipes();
        worldgen("cinnabar", -32, 64, 2, 6, 0.0);
        worldgen("sulfur", 0, 96, 2, 6, 0.0);
        worldgen("silver", -64, 32, 3, 9, 0.5);
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override public String getName() { return "Forbidden Hex mineral resources"; }

    private static String id(String name) { return NS + ":" + PREFIX + name; }
    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
    private void save(String path, JsonElement value) {
        writes.add(DataProvider.saveStable(output, value, root.resolve(path + ".json")));
    }
    private void tag(String namespace, String type, String name, List<String> values) {
        JsonObject value = json("{\"replace\":false}");
        JsonArray entries = new JsonArray();
        values.forEach(entries::add);
        value.add("values", entries);
        save("data/" + namespace + "/tags/" + type + "/" + name, value);
    }

    private void block(String name) {
        String model = NS + ":block/" + PREFIX + name;
        save("assets/" + NS + "/models/block/" + PREFIX + name, json("""
                {"parent":"minecraft:block/cube_all","textures":{"all":"%s"}}
                """.formatted(model)));
        save("assets/" + NS + "/models/item/" + PREFIX + name, json("{\"parent\":\"" + model + "\"}"));
        save("assets/" + NS + "/blockstates/" + PREFIX + name, json("""
                {"variants":{"":{"model":"%s"}}}
                """.formatted(model)));
    }
    private void item(String name) {
        save("assets/" + NS + "/models/item/" + PREFIX + name, json("""
                {"parent":"minecraft:item/generated","textures":{"layer0":"%s:item/%s%s"}}
                """.formatted(NS, PREFIX, name)));
    }

    /** 时运只增幅材料分支，精准采集优先返回矿石本体。 */
    private void oreLoot(String ore, String material, boolean fragments) {
        JsonObject value = json("""
                {"type":"minecraft:block","pools":[{"rolls":1,"entries":[
                  {"type":"minecraft:alternatives","children":[
                    {"type":"minecraft:item","name":"%s","conditions":[
                      {"condition":"minecraft:match_tool","predicate":{"enchantments":[
                        {"enchantment":"minecraft:silk_touch","levels":{"min":1}}]}}]},
                    {"type":"minecraft:item","name":"%s","functions":[]}]}]}]}
                """.formatted(id(ore), id(material)));
        JsonArray functions = value.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries")
                .get(0).getAsJsonObject().getAsJsonArray("children").get(1).getAsJsonObject().getAsJsonArray("functions");
        if (fragments) functions.add(json("""
                {"function":"minecraft:set_count","count":{"type":"minecraft:uniform","min":2.0,"max":4.0}}
                """));
        functions.add(json("""
                {"function":"minecraft:apply_bonus","enchantment":"minecraft:fortune","formula":"minecraft:ore_drops"}
                """));
        value.add("functions", jsonArray("{\"function\":\"minecraft:explosion_decay\"}"));
        save("data/" + NS + "/loot_tables/blocks/" + PREFIX + ore, value);
    }
    private static JsonArray jsonArray(String value) {
        JsonArray result = new JsonArray();
        result.add(json(value));
        return result;
    }
    private void dropSelf(String name) {
        save("data/" + NS + "/loot_tables/blocks/" + PREFIX + name, json("""
                {"type":"minecraft:block","pools":[{"rolls":1,"entries":[{"type":"minecraft:item","name":"%s"}],
                  "conditions":[{"condition":"minecraft:survives_explosion"}]}]}
                """.formatted(id(name))));
    }

    private void recipes() {
        for (String source : List.of("raw_silver", "silver_ore")) {
            TagKey<Item> sourceTag = source.equals("raw_silver") ? ForbiddenItemTags.RAW_SILVER : ForbiddenItemTags.SILVER_ORES;
            JsonObject ingredient = json("{\"tag\":\"" + sourceTag.location() + "\"}");
            for (String type : List.of("smelting", "blasting")) {
                String name = "silver_ingot_from_" + type + "_" + source;
                recipe(name, json("""
                        {"type":"minecraft:%s","category":"misc","group":"silver_ingot","ingredient":%s,
                         "result":"%s","experience":1.0,"cookingtime":%d}
                        """.formatted(type, ingredient, id("silver_ingot"), type.equals("smelting") ? 200 : 100)), ingredient);
            }
        }
        compact("silver_nugget", "silver_ingot", ForbiddenItemTags.SILVER_NUGGETS, ForbiddenItemTags.SILVER_INGOTS);
        compact("silver_ingot", "silver_block", ForbiddenItemTags.SILVER_INGOTS, ForbiddenItemTags.SILVER_BLOCKS);
        compact("raw_silver", "raw_silver_block", ForbiddenItemTags.RAW_SILVER, ForbiddenItemTags.RAW_SILVER_BLOCKS);
    }
    private void compact(String small, String large) {
        compact(small, large, json("{\"item\":\"" + id(small) + "\"}"), json("{\"item\":\"" + id(large) + "\"}"));
    }

    private void compact(String small, String large, TagKey<Item> smallTag, TagKey<Item> largeTag) {
        compact(small, large, json("{\"tag\":\"" + smallTag.location() + "\"}"),
                json("{\"tag\":\"" + largeTag.location() + "\"}"));
    }

    private void compact(String small, String large, JsonObject smallIngredient, JsonObject largeIngredient) {
        recipe(large + "_from_" + small, json("""
                {"type":"minecraft:crafting_shaped","category":"misc","pattern":["###","###","###"],
                 "key":{"#":%s},"result":{"item":"%s","count":1}}
                """.formatted(smallIngredient, id(large))), smallIngredient);
        recipe(small + "_from_" + large, json("""
                {"type":"minecraft:crafting_shapeless","category":"misc","ingredients":[%s],
                 "result":{"item":"%s","count":9}}
                """.formatted(largeIngredient, id(small))), largeIngredient);
    }
    private void recipe(String name, JsonObject recipe, JsonObject ingredient) {
        String recipeId = id(name);
        JsonObject predicate = ingredient.has("tag") ? ingredient
                : json("{\"items\":[\"" + ingredient.get("item").getAsString() + "\"]}");
        save("data/" + NS + "/recipes/" + PREFIX + name, recipe);
        save("data/" + NS + "/advancements/recipes/misc/" + PREFIX + name, json("""
                {"parent":"minecraft:recipes/root","criteria":{
                  "has_material":{"trigger":"minecraft:inventory_changed","conditions":{"items":[%s]}},
                  "has_the_recipe":{"trigger":"minecraft:recipe_unlocked","conditions":{"recipe":"%s"}}},
                  "requirements":[["has_material","has_the_recipe"]],"rewards":{"recipes":["%s"]}}
                """.formatted(predicate, recipeId, recipeId)));
    }

    /** 仅向主世界群系追加矿脉，不回填旧区块、不额外修改任何群系的产量。 */
    private void worldgen(String ore, int min, int max, int count, int size, double discard) {
        worldgen(ore, min, max, count, size, discard, false);
    }

    private void worldgen(String ore, int min, int max, int count, int size, double discard, boolean deepslateOnly) {
        String name = ore + "_ore";
        JsonObject configured = json("""
                {"type":"minecraft:ore","config":{"size":%d,"discard_chance_on_air_exposure":%s,"targets":[
                  {"target":{"predicate_type":"minecraft:tag_match","tag":"minecraft:stone_ore_replaceables"},
                   "state":{"Name":"%s"}},
                  {"target":{"predicate_type":"minecraft:tag_match","tag":"minecraft:deepslate_ore_replaceables"},
                   "state":{"Name":"%s"}}]}}
                """.formatted(size, discard, id(name), id("deepslate_" + name)));
        if (deepslateOnly) configured.getAsJsonObject("config").getAsJsonArray("targets").remove(0);
        save("data/" + NS + "/worldgen/configured_feature/" + PREFIX + name, configured);
        save("data/" + NS + "/worldgen/placed_feature/" + PREFIX + name, json("""
                {"feature":"%s","placement":[{"type":"minecraft:count","count":%d},
                  {"type":"minecraft:in_square"},{"type":"minecraft:height_range","height":{"type":"minecraft:trapezoid",
                   "min_inclusive":{"absolute":%d},"max_inclusive":{"absolute":%d},"plateau":0}},
                  {"type":"minecraft:biome"}]}
                """.formatted(id(name), count, min, max)));
        save("data/" + NS + "/forge/biome_modifier/add_" + PREFIX + name, json("""
                {"type":"forge:add_features","biomes":"#minecraft:is_overworld","features":["%s"],"step":"underground_ores"}
                """.formatted(id(name))));
    }
}
