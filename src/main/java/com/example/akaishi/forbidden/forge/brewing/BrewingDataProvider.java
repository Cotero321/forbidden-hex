package com.example.akaishi.forbidden.forge.brewing;

import com.example.akaishi.forbidden.brewing.BrewingPlants;
import com.google.common.hash.Hashing;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class BrewingDataProvider implements DataProvider {
    private static final String NS = "forbidden_hex:";
    private static final String PREFIX = "forbidden_hex_";
    private static final List<String> MATERIALS = List.of("mandrake_root", "henbane", "belladonna", "datura", "aconite", "foxglove",
            "hellroot", "colchicum", "yew", "ergot", "parasitic_vine", "ember_moss", "glint_weed");
    private static final String[] HERB_FUMES = {"shriek", "madness", "charm", "hallucination", "frost_poison", "heartbeat",
            null, null, null, "curse", null, "heat", "bright"};
    private static final String[] TREE_FUMES = {"white_thorn", "red_vein", "bloodwood", "underpass", "water_mirror"};
    private final Path root;
    private final List<CompletableFuture<?>> writes = new ArrayList<>();
    private CachedOutput output;

    public BrewingDataProvider(PackOutput output) { root = output.getOutputFolder(); }

    @Override public CompletableFuture<?> run(CachedOutput cached) {
        output = cached;
        writes.clear();
        try {
            recipes();
            plants();
            for (String name : List.of("wood_ash", "grass_ash", "fume", "brewed_potion", "transmutation_dust",
                    "refined_herb_paste", "rune_stone", "charged_rune_stone")) item(name);
            for (String material : MATERIALS) item(material);
            template();
        } catch (IOException error) { return CompletableFuture.failedFuture(error); }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override public String getName() { return "Forbidden Hex brewing resources"; }

    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    private static JsonArray array(List<String> values) {
        JsonArray result = new JsonArray();
        values.forEach(result::add);
        return result;
    }
    private void save(String path, JsonElement value) { writes.add(DataProvider.saveStable(output, value, root.resolve(path))); }
    private void bytes(String path, byte[] content) throws IOException {
        output.writeIfNeeded(root.resolve(path), content, Hashing.sha1().hashBytes(content));
    }
    private static String id(String shortName) { return NS + PREFIX + shortName; }
    private static String blockTexture(String name) { return NS + "block/" + PREFIX + name; }
    private static String model(String name) { return NS + "block/" + PREFIX + name; }
    private void blockModel(String name, JsonObject value) { save("assets/forbidden_hex/models/block/" + PREFIX + name + ".json", value); }
    private void itemModel(String name, JsonObject value) { save("assets/forbidden_hex/models/item/" + PREFIX + name + ".json", value); }
    private void states(String name, JsonObject value) { save("assets/forbidden_hex/blockstates/" + PREFIX + name + ".json", value); }
    private void tag(String namespace, String type, String name, List<String> values) {
        JsonObject value = json("{\"replace\":false}");
        value.add("values", array(values));
        save("data/" + namespace + "/tags/" + type + "/" + name + ".json", value);
    }

    private void recipes() {
        String[] vanilla = {"oak_sapling", "spruce_sapling", "birch_sapling", "jungle_sapling", "acacia_sapling",
                "dark_oak_sapling", "mangrove_propagule", "cherry_sapling"};
        String[] fumes = {"earth", "winter", "dawn", "vitality", "sun", "gloom", "swamp", "dream"};
        for (int i = 0; i < vanilla.length; i++) oven("minecraft:" + vanilla[i], "wood_ash", fumes[i]);
        for (int i = 0; i < BrewingPlants.TREES.size(); i++) oven(id(BrewingPlants.TREES.get(i) + "_sapling"), "wood_ash", TREE_FUMES[i]);
        for (int i = 0; i < MATERIALS.size(); i++) if (HERB_FUMES[i] != null) oven(id(MATERIALS.get(i)), "grass_ash", HERB_FUMES[i]);
        JsonArray dust = new JsonArray();
        for (String name : List.of("redstone", "glowstone_dust", "gunpowder", "sugar")) dust.add(step("item", "minecraft:" + name));
        crucible("transmutation_dust", dust, List.of(id("transmutation_dust")));
        JsonArray trees = new JsonArray();
        trees.add(step("tag", "minecraft:saplings"));
        conversionTail(trees);
        crucible("tree_conversion", trees, BrewingPlants.TREES.stream().map(name -> id(name + "_sapling")).toList());
        JsonArray herbs = new JsonArray();
        herbs.add(step("tag", NS + "brewing_plants"));
        conversionTail(herbs);
        crucible("plant_conversion", herbs, BrewingPlants.HERBS.stream().map(name -> id(name + "_seeds")).toList());
    }
    private JsonObject step(String key, String value) { return json("{\"ingredient\":{\"" + key + "\":\"" + value + "\"}}"); }
    private void conversionTail(JsonArray steps) {
        steps.add(step("item", "minecraft:egg"));
        steps.add(step("item", id("fume")));
        steps.add(step("item", id("transmutation_dust")));
    }
    private void crucible(String name, JsonArray steps, List<String> results) {
        JsonObject recipe = json("{\"type\":\"forbidden_hex:crucible\",\"kind\":\"transformation\",\"heat\":\"fire\"}");
        recipe.add("steps", steps);
        JsonArray pool = new JsonArray();
        for (String item : results) pool.add(json("{\"item\":\"" + item + "\",\"count\":1}"));
        recipe.add("results", pool);
        save("data/forbidden_hex/recipes/brewing/" + name + ".json", recipe);
    }
    private void oven(String input, String ash, String fume) {
        JsonObject recipe = json("{\"type\":\"forbidden_hex:witch_oven\",\"ingredient\":{\"item\":\"" + input
                + "\"},\"result\":{\"item\":\"" + id(ash) + "\"},\"fume\":{\"item\":\"" + id("fume")
                + "\",\"nbt\":\"{forbidden_hex_fume:'forbidden_hex:" + fume + "_breath'}\"},\"cooking_time\":200,\"fume_chance\":0.3}");
        save("data/forbidden_hex/recipes/brewing/oven_" + input.substring(input.indexOf(':') + 1) + ".json", recipe);
    }

    private void plants() throws IOException {
        List<String> cropBlocks = new ArrayList<>(), plantItems = new ArrayList<>(), seeds = new ArrayList<>();
        for (int i = 0; i < BrewingPlants.HERBS.size(); i++) {
            String name = BrewingPlants.HERBS.get(i);
            String crop = name + "_crop";
            cropBlocks.add(id(crop));
            plantItems.add(id(MATERIALS.get(i)));
            seeds.add(id(name + "_seeds"));
            item(name + "_seeds");
            JsonObject variants = new JsonObject();
            for (int age = 0; age <= 7; age++) {
                int stage = age / 2;
                variants.add("age=" + age, json("{\"model\":\"" + model(crop + "_" + stage) + "\"}"));
            }
            for (int stage = 0; stage < 4; stage++) {
                String stageName = crop + "_" + stage;
                blockModel(stageName, cross(stageName, stageName + "_uv"));
            }
            JsonObject state = new JsonObject();
            state.add("variants", variants);
            states(crop, state);
            cropLoot(crop, MATERIALS.get(i), name + "_seeds");
        }
        plantItems.addAll(seeds);
        plantItems.addAll(List.of("#minecraft:flowers", "minecraft:grass", "minecraft:fern", "minecraft:dead_bush",
                "minecraft:vine", "minecraft:lily_pad", "minecraft:kelp", "minecraft:seagrass", "minecraft:cactus",
                "minecraft:sugar_cane", "minecraft:bamboo", "minecraft:moss_block", "minecraft:moss_carpet",
                "minecraft:wheat", "minecraft:wheat_seeds", "minecraft:carrot", "minecraft:potato", "minecraft:beetroot",
                "minecraft:beetroot_seeds", "minecraft:melon_seeds", "minecraft:pumpkin_seeds", "minecraft:nether_wart",
                "minecraft:torchflower_seeds", "minecraft:pitcher_pod", "minecraft:brown_mushroom", "minecraft:red_mushroom"));
        tag("forbidden_hex", "items", "brewing_plants", plantItems);
        tag("forge", "items", "seeds", seeds);
        tag("minecraft", "blocks", "crops", cropBlocks);
        List<String> saplings = new ArrayList<>(), logs = new ArrayList<>(), leaves = new ArrayList<>();
        for (String tree : BrewingPlants.TREES) {
            String sapling = tree + "_sapling", log = tree + "_log", leaf = tree + "_leaves";
            saplings.add(id(sapling)); logs.add(id(log)); leaves.add(id(leaf));
            blockModel(sapling, cross(sapling, sapling + "_uv"));
            states(sapling, json("{\"variants\":{\"\":{\"model\":\"" + model(sapling) + "\"}}}"));
            itemModel(sapling, json("{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"" + blockTexture(sapling) + "\"}}"));
            dropSelf(sapling);
            JsonObject logModel = cube(log, log + "_top", log);
            JsonObject logTextures = logModel.getAsJsonObject("textures");
            logTextures.addProperty("trim", blockTexture(log + "_uv"));
            logModel.addProperty("render_type", "minecraft:cutout");
            logModel.getAsJsonArray("elements").add(cuboid(-0.01, -0.01, -0.01, 16.01, 16.01, 16.01, "trim", "trim", "trim", -1));
            blockModel(log, logModel);
            states(log, json("{\"variants\":{\"axis=y\":{\"model\":\"" + model(log) + "\"},\"axis=x\":{\"model\":\""
                    + model(log) + "\",\"x\":90,\"y\":90},\"axis=z\":{\"model\":\"" + model(log) + "\",\"x\":90}}}"));
            itemModel(log, json("{\"parent\":\"" + model(log) + "\"}"));
            dropSelf(log);
            JsonObject leafModel = cube(leaf, leaf, leaf);
            leafModel.addProperty("render_type", "minecraft:cutout_mipped");
            leafModel.getAsJsonObject("textures").addProperty("trim", blockTexture(leaf + "_uv"));
            leafModel.getAsJsonArray("elements").add(cuboid(-0.01, -0.01, -0.01, 16.01, 16.01, 16.01, "trim", "trim", "trim", -1));
            blockModel(leaf, leafModel);
            states(leaf, json("{\"variants\":{\"\":{\"model\":\"" + model(leaf) + "\"}}}"));
            itemModel(leaf, json("{\"parent\":\"" + model(leaf) + "\"}"));
            leavesLoot(leaf, sapling);
            configuredTree(tree, log, leaf);
        }
        for (String type : List.of("items", "blocks")) {
            tag("minecraft", type, "saplings", saplings);
            tag("minecraft", type, "logs", logs);
            tag("minecraft", type, "logs_that_burn", logs);
            tag("minecraft", type, "leaves", leaves);
        }
        tag("minecraft", "blocks", "mineable/axe", logs);
        tag("minecraft", "blocks", "mineable/hoe", leaves);
        tag("minecraft", "blocks", "needs_stone_tool", List.of(id("witch_oven"), id("crucible")));
    }
    private void configuredTree(String tree, String log, String leaf) {
        save("data/forbidden_hex/worldgen/configured_feature/" + PREFIX + tree + "_tree.json", json("""
                {"type":"minecraft:tree","config":{
                 "trunk_provider":{"type":"minecraft:simple_state_provider","state":{"Name":"%s","Properties":{"axis":"y"}}},
                 "trunk_placer":{"type":"minecraft:straight_trunk_placer","base_height":4,"height_rand_a":2,"height_rand_b":1},
                 "foliage_provider":{"type":"minecraft:simple_state_provider","state":{"Name":"%s","Properties":{"distance":"7","persistent":"false","waterlogged":"false"}}},
                 "foliage_placer":{"type":"minecraft:blob_foliage_placer","radius":2,"offset":0,"height":3},
                 "dirt_provider":{"type":"minecraft:simple_state_provider","state":{"Name":"minecraft:dirt"}},
                 "minimum_size":{"type":"minecraft:two_layers_feature_size","limit":1,"lower_size":0,"upper_size":1},
                 "decorators":[],"ignore_vines":true,"force_dirt":false}}
                """.formatted(id(log), id(leaf))));
    }

    private JsonObject cube(String side, String top, String front) {
        JsonObject result = json("{\"parent\":\"minecraft:block/block\"}");
        result.add("textures", json("{\"side\":\"" + blockTexture(side) + "\",\"top\":\"" + blockTexture(top)
                + "\",\"front\":\"" + blockTexture(front) + "\",\"particle\":\"" + blockTexture(side) + "\"}"));
        JsonArray elements = new JsonArray();
        elements.add(cuboid(0, 0, 0, 16, 16, 16, "side", "top", "front", -1));
        result.add("elements", elements);
        return result;
    }
    private JsonObject cuboid(double x, double y, double z, double xx, double yy, double zz, String side, String top, String front, int tint) {
        JsonObject element = json("{\"from\":[" + x + "," + y + "," + z + "],\"to\":[" + xx + "," + yy + "," + zz + "]}");
        JsonObject faces = new JsonObject();
        for (String face : List.of("north", "south", "east", "west", "up", "down")) {
            String texture = face.equals("up") ? top : face.equals("north") ? front : side;
            JsonObject data = json("{\"uv\":[0,0,16,16],\"texture\":\"#" + texture + "\"}");
            if (tint >= 0) data.addProperty("tintindex", tint);
            faces.add(face, data);
        }
        element.add("faces", faces);
        return element;
    }
    private JsonObject cross(String texture, String overlay) {
        JsonObject model = json("{\"parent\":\"minecraft:block/block\",\"ambientocclusion\":false,\"render_type\":\"minecraft:cutout\"}");
        model.add("textures", json("{\"cross\":\"" + blockTexture(texture) + "\",\"uv\":\"" + blockTexture(overlay) + "\",\"particle\":\"" + blockTexture(texture) + "\"}"));
        JsonArray elements = new JsonArray();
        for (String layer : List.of("cross", "uv")) for (int angle : List.of(-45, 45)) {
            double depth = layer.equals("uv") ? 8.01 : 8;
            elements.add(json("{\"from\":[0.8,0," + depth + "],\"to\":[15.2,16," + depth + "],\"rotation\":{\"origin\":[8,8,8],\"axis\":\"y\",\"angle\":" + angle
                    + ",\"rescale\":true},\"shade\":false,\"faces\":{\"north\":{\"uv\":[0,0,16,16],\"texture\":\"#" + layer
                    + "\"},\"south\":{\"uv\":[0,0,16,16],\"texture\":\"#" + layer + "\"}}}"));
        }
        model.add("elements", elements);
        return model;
    }
    private void dropSelf(String name) {
        save("data/forbidden_hex/loot_tables/blocks/" + PREFIX + name + ".json", json("{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\""
                + id(name) + "\"}],\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"}]}]}"));
    }
    private void cropLoot(String crop, String material, String seed) {
        save("data/forbidden_hex/loot_tables/blocks/" + PREFIX + crop + ".json", json("""
                {"type":"minecraft:block","pools":[
                 {"rolls":1,"entries":[{"type":"minecraft:item","name":"%s"}],"conditions":[{"condition":"minecraft:survives_explosion"}]},
                 {"rolls":1,"entries":[{"type":"minecraft:item","name":"%s"}],"conditions":[{"condition":"minecraft:block_state_property","block":"%s","properties":{"age":"7"}},{"condition":"minecraft:survives_explosion"}]}]}
                """.formatted(id(seed), id(material), id(crop))));
    }
    private void leavesLoot(String leaves, String sapling) {
        save("data/forbidden_hex/loot_tables/blocks/" + PREFIX + leaves + ".json", json("""
                {"type":"minecraft:block","pools":[{"rolls":1,"entries":[{"type":"minecraft:alternatives","children":[
                  {"type":"minecraft:item","name":"%s","conditions":[{"condition":"minecraft:any_of","terms":[
                    {"condition":"minecraft:match_tool","predicate":{"items":["minecraft:shears"]}},
                    {"condition":"minecraft:match_tool","predicate":{"enchantments":[{"enchantment":"minecraft:silk_touch","levels":{"min":1}}]}}]}]},
                  {"type":"minecraft:item","name":"%s","conditions":[{"condition":"minecraft:table_bonus","enchantment":"minecraft:fortune","chances":[0.05,0.0625,0.0833,0.1]}]}]}],"conditions":[{"condition":"minecraft:survives_explosion"}]}]}
                """.formatted(id(leaves), id(sapling))));
    }

    private void item(String name) {
        if (!List.of("wood_ash", "grass_ash", "fume", "brewed_potion").contains(name)) {
            itemModel(name, json("{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"" + NS + "item/" + PREFIX + name + "\"}}"));
        }
    }
    private void template() throws IOException {
        CompoundTag template = new CompoundTag();
        template.putInt("DataVersion", net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        ListTag size = new ListTag();
        for (int axis : List.of(16, 14, 16)) size.add(IntTag.valueOf(axis));
        template.put("size", size);
        ListTag palette = new ListTag();
        CompoundTag air = new CompoundTag(); air.putString("Name", "minecraft:air"); palette.add(air);
        template.put("palette", palette); template.put("blocks", new ListTag()); template.put("entities", new ListTag());
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        NbtIo.writeCompressed(template, stream);
        bytes("data/forbidden_hex/structures/brewing_empty.nbt", stream.toByteArray());
    }
}
