package com.example.akaishi.forbidden.forge.alchemy;

import com.example.akaishi.forbidden.alchemy.AlchemyContent;
import com.example.akaishi.forbidden.alchemy.AlchemyMaterials;
import com.google.common.hash.Hashing;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class AlchemyDataProvider implements DataProvider {
    private final Path root;
    private CachedOutput output;
    private final List<CompletableFuture<?>> writes = new ArrayList<>();
    private static final String ASSETS = "assets/forbidden_hex/", DATA = "data/forbidden_hex/";
    private static final String[] EFFECTS = {"deflagration", "extreme_cold", "flourishing", "bewilderment", "gravity_field", "thunder", "rust", "piercing", "hellfire", "corrosion", "purge_benefits", "sonic_burst", "delirium"};
    private static final String[] REFINED = {"deflagration", "extreme_cold", "flourishing", "bewilderment", "gravity", "thunder", "rust", "piercing", "hellfire", "corrosion", "purge", "sonic", "delirium"};
    private static final String[] FUME_NAMES = {"爆燃之息", "极寒之息", "繁盛之息", "迷惘之息", "沉沦之息", "雷鸣之息", "锈蚀之息", "穿刺之息", "地狱火之息", "腐蚀之息", "破魔之息", "破空之息", "谵妄之息"};
    public AlchemyDataProvider(PackOutput output) { root = output.getOutputFolder(); }
    @Override public String getName() { return "Forbidden Hex alchemy resources"; }
    @Override public CompletableFuture<?> run(CachedOutput cached) {
        output = cached; writes.clear();
        try {
            for (AlchemyMaterials.Material material : AlchemyMaterials.MATERIALS) {
                save(ASSETS + "models/item/forbidden_hex_" + material.name() + ".json", json("{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"forbidden_hex:item/forbidden_hex_" + material.name() + "\"}}"));
                if (!material.shape().equals("bottle") && !hasAuthoredItemTexture(material.name()))
                    png(ASSETS + "textures/item/forbidden_hex_" + material.name() + ".png", materialTexture(material));
            }
            for (AlchemyContent.Kind kind : AlchemyContent.Kind.values()) machine(kind);
            JsonObject machines = json("{\"replace\":false}");
            JsonArray machineIds = new JsonArray();
            for (var kind : AlchemyContent.Kind.values()) machineIds.add(id(kind.id()));
            machines.add("values", machineIds); save(DATA + "tags/blocks/alchemy_machines.json", machines);
            png(ASSETS + "textures/gui/alchemy_machine.png", gui());
            save(ASSETS + "models/item/forbidden_hex_refined_fume.json", json("{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"minecraft:item/glass_bottle\",\"layer1\":\"forbidden_hex:item/forbidden_hex_fume\",\"layer2\":\"forbidden_hex:item/forbidden_hex_refined_fume_mark\"}}"));
            for (String effect : EFFECTS) png(ASSETS + "textures/mob_effect/brewing_" + effect + ".png", effectIcon(effect));
            recipes(); language("zh_cn"); language("en_us");
        } catch (IOException exception) { return CompletableFuture.failedFuture(exception); }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }
    private static boolean hasAuthoredItemTexture(String name) {
        return name.equals("stone_fragment") || name.endsWith("_dust")
                && (name.startsWith("crude_") || name.startsWith("fine_") || name.startsWith("tiny_"));
    }
    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    private static String id(String name) { return "forbidden_hex:forbidden_hex_" + name; }
    private void save(String path, JsonElement value) { writes.add(DataProvider.saveStable(output, value, root.resolve(path))); }
    private void png(String path, BufferedImage image) throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream(); ImageIO.write(image, "png", stream);
        byte[] bytes = stream.toByteArray(); output.writeIfNeeded(root.resolve(path), bytes, Hashing.sha1().hashBytes(bytes));
    }
    private static JsonObject ingredient(String... items) {
        JsonObject value = new JsonObject();
        if (items.length == 1) value.add("ingredient", json("{\"item\":\"" + items[0] + "\"}"));
        else {
            JsonArray array = new JsonArray();
            for (String item : items) array.add(json("{\"item\":\"" + item + "\"}"));
            value.add("ingredient", array);
        }
        return value;
    }
    private static JsonObject product(String name, int min, int max, double chance) {
        JsonObject value = json("{\"item\":\"" + id(name) + "\",\"min\":" + min + ",\"max\":" + max + ",\"chance\":" + chance + "}");
        return value;
    }
    private void process(String name, String process, JsonObject input, JsonObject reagent, int mode, int equivalents, int bottles, JsonObject... outputs) {
        JsonObject value = input.deepCopy();
        value.addProperty("type", id("alchemy")); value.addProperty("process", process); value.addProperty("mode", mode);
        value.addProperty("equivalents", equivalents); value.addProperty("bottles", bottles);
        if (reagent != null) value.add("reagent", reagent);
        JsonArray results = new JsonArray(); for (JsonObject result : outputs) results.add(result);
        value.add("outputs", results); save(DATA + "recipes/alchemy/" + name + ".json", value);
    }
    private void mineral(String mineral, String companion, String name, int equivalents, String... inputs) {
        process(name + "_coarse", "crushing", ingredient(inputs), null, 0, equivalents, 0,
                product("crude_" + mineral + "_dust", 1, 1, 1), product("stone_fragment", 1, 1, 1), product("tiny_" + mineral + "_dust", 1, 1, 1));
        fine(mineral, companion, name, equivalents, inputs);
    }
    private void fine(String mineral, String companion, String name, int equivalents, String... inputs) {
        process(name + "_fine", "crushing", ingredient(inputs), null, 1, equivalents, 0,
                product("fine_" + mineral + "_dust", 2, 2, 1), product("stone_fragment", 1, 1, 1),
                product("stone_dust", 1, 1, .5), product("tiny_" + companion + "_dust", 1, 2, 1));
    }
    private void recipes() {
        String[] companions = {"sulfur", "saltpeter", "lead", "green_vitriol", "blue_vitriol", "silver", "hematite", "pyrite", "saltpeter"};
        for (int i = 0; i < AlchemyMaterials.METALS.size(); i++) {
            String mineral = AlchemyMaterials.METALS.get(i), companion = companions[i];
            if (i < 3) {
                mineral(mineral, companion, mineral + "_ore", 1, id(mineral + "_ore"), id("deepslate_" + mineral + "_ore"));
                mineral(mineral, companion, mineral + "_raw", 1, id(i == 2 ? "raw_silver" : mineral + "_fragment"));
                if (i == 2) mineral(mineral, companion, "silver_raw_block", 9, id("raw_silver_block"));
            } else if (i <= 5) {
                mineral(mineral, companion, mineral + "_ore", 1, "minecraft:" + mineral + "_ore", "minecraft:deepslate_" + mineral + "_ore");
                mineral(mineral, companion, mineral + "_raw", 1, "minecraft:raw_" + mineral);
                mineral(mineral, companion, mineral + "_raw_block", 9, "minecraft:raw_" + mineral + "_block");
            } else if (i < 8) {
                mineral(mineral, companion, mineral + "_ore", i == 6 ? 4 : 6, "minecraft:" + mineral + "_ore", "minecraft:deepslate_" + mineral + "_ore");
                mineral(mineral, companion, mineral + "_raw", 1, "minecraft:" + (i == 6 ? "redstone" : "lapis_lazuli"));
            } else {
                mineral(mineral, companion, "quartz_ore", 1, "minecraft:nether_quartz_ore");
                mineral(mineral, companion, "quartz_raw", 1, "minecraft:quartz");
            }
            fine(mineral, companion, mineral + "_crude", 1, id("crude_" + mineral + "_dust"));
        }
        for (int mode = 0; mode < 2; mode++) {
            process("fragment_" + mode, "crushing", ingredient(id("stone_fragment")), null, mode, 1, 0, product("stone_dust", 3, 3, 1));
            if (mode == 0) process("cobblestone_0", "crushing", ingredient("minecraft:cobblestone"), null, 0, 1, 0, product("stone_fragment", 3, 3, 1), product("stone_dust", 2, 2, 1));
            else process("cobblestone_1", "crushing", ingredient("minecraft:cobblestone"), null, 1, 1, 0, product("stone_dust", 3, 3, 1));
            process("limestone_" + mode, "crushing", ingredient("minecraft:calcite", "minecraft:dripstone_block"), null, mode, 1, 0, product("limestone_dust", 2, 2, 1), product("stone_fragment", 2, 2, 1));
        }
        combine("stone_fragment", "minecraft:cobblestone"); combine("stone_dust", id("stone_fragment"));
        for (var material : AlchemyMaterials.MATERIALS) if (material.name().startsWith("tiny_")) combine(material.name(), id(material.name().replace("tiny_", "crude_")));
        JsonObject smelting = json("{\"type\":\"minecraft:smelting\",\"ingredient\":{\"item\":\"" + id("limestone_dust") + "\"},\"result\":\"" + id("quicklime") + "\",\"experience\":0.1,\"cookingtime\":200}");
        save(DATA + "recipes/alchemy/quicklime.json", smelting);
        save(DATA + "recipes/alchemy/oven_quicklime.json", json("{\"type\":\"forbidden_hex:witch_oven\",\"ingredient\":{\"item\":\"" + id("limestone_dust") + "\"},\"result\":{\"item\":\"" + id("quicklime") + "\"},\"cooking_time\":200,\"fume_chance\":0}"));
        String[] powders = {"tiny_green_vitriol_dust", "tiny_blue_vitriol_dust", "tiny_pyrite_dust", "tiny_lead_dust", "tiny_hematite_dust", "tiny_saltpeter_dust", "fine_sulfur_dust", "fine_silver_dust", "fine_cinnabar_dust"};
        String[] liquids = {"sulfuric_acid", "sulfuric_acid", "sulfurous_acid", "sulfurous_acid", "purified_water", "nitric_acid", "sulfurous_acid", "moon_spring", "mercury"};
        String[] solids = {"iron_oxide", "copper_oxide", "iron_oxide", "lead_oxide", "pure_iron_dust", "potash", null, "moon_salt", "sulfurous_acid"};
        for (int i = 0; i < powders.length; i++) {
            JsonObject first = product(liquids[i], 1, 1, 1);
            if (solids[i] == null) process("distill_" + powders[i], "distilling", ingredient(id(powders[i])), null, 0, 1, 1, first);
            else process("distill_" + powders[i], "distilling", ingredient(id(powders[i])), null, 0, 1, i == 8 ? 2 : 1, first, product(solids[i], 1, 1, 1));
        }
        String[] inputs = {"sun", "winter", "vitality", "gloom", "underpass", "water_mirror", "bloodwood", "white_thorn", "heat", "frost_poison", "curse", "shriek", "madness"};
        String[] reagents = {"fine_sulfur_dust", "tiny_saltpeter_dust", "tiny_green_vitriol_dust", "tiny_pyrite_dust", "lead_oxide", "copper_oxide", "iron_oxide", "tiny_blue_vitriol_dust", "nitric_acid", "sulfurous_acid", "moon_spring", "purified_water", "mercury"};
        for (int i = 0; i < inputs.length; i++) {
            JsonObject input = ingredient(id("fume")); input.addProperty("nbt", "{forbidden_hex_fume:'forbidden_hex:" + inputs[i] + "_breath'}");
            JsonObject result = product("fume", 1, 1, 1); result.addProperty("nbt", "{forbidden_hex_fume:'forbidden_hex:" + REFINED[i] + "_breath'}");
            if (i < 8) process("refine_" + REFINED[i], "refining", input, ingredient(id(reagents[i])), 0, 1, 0, result);
            else {
                JsonObject bottle = json("{\"item\":\"minecraft:glass_bottle\",\"min\":1,\"max\":1}");
                process("refine_" + REFINED[i], "refining", input, ingredient(id(reagents[i])), 0, 1, 0, result, bottle);
            }
        }
        shaped("manual_crusher", List.of(" S ", " F ", "CCC"), "S", "minecraft:stick", "F", "minecraft:flint", "C", "minecraft:cobblestone");
        shaped("distiller", List.of("GCG", " C ", "CFC"), "G", "minecraft:glass", "C", "minecraft:copper_ingot", "F", "minecraft:furnace");
        for (String machine : List.of("crusher", "distiller")) {
            JsonArray ingredients = new JsonArray(); ingredients.add(json("{\"item\":\"" + id(machine.equals("crusher") ? "manual_crusher" : "distiller") + "\"}"));
            ingredients.add(json("{\"item\":\"" + id("magic_stone") + "\"}"));
            for (int i = 0; i < 4; i++) ingredients.add(json("{\"item\":\"" + id("silver_ingot") + "\"}"));
            JsonObject value = json("{\"type\":\"" + id("alchemy_upgrade") + "\",\"result\":{\"item\":\"" + id("source_" + machine) + "\"}}");
            value.add("ingredients", ingredients); save(DATA + "recipes/alchemy/source_" + machine + ".json", value);
        }
    }
    private void combine(String input, String result) {
        JsonObject value = json("{\"type\":\"minecraft:crafting_shapeless\",\"result\":{\"item\":\"" + result + "\"}}");
        JsonArray ingredients = new JsonArray(); for (int i = 0; i < 4; i++) ingredients.add(json("{\"item\":\"" + id(input) + "\"}"));
        value.add("ingredients", ingredients); save(DATA + "recipes/alchemy/combine_" + input + ".json", value);
    }
    private void shaped(String result, List<String> rows, String... keys) {
        JsonObject value = json("{\"type\":\"minecraft:crafting_shaped\",\"result\":{\"item\":\"" + id(result) + "\"}}");
        JsonArray pattern = new JsonArray(); rows.forEach(pattern::add); value.add("pattern", pattern);
        JsonObject key = new JsonObject(); for (int i = 0; i < keys.length; i += 2) key.add(keys[i], json("{\"item\":\"" + keys[i + 1] + "\"}"));
        value.add("key", key); save(DATA + "recipes/alchemy/" + result + ".json", value);
    }
    private void machine(AlchemyContent.Kind kind) throws IOException {
        String name = "forbidden_hex_" + kind.id(), texture = "forbidden_hex:block/" + name;
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            int base = kind.crusher() ? 0x89857D : 0xAC744E;
            int shift = ((x * 7 + y * 13) % 5 - 2) * 7;
            int color = shade(base, shift);
            if (y == 2 || y == 13 || x == 1 || x == 14) color = kind.powered() ? 0x9980BB : 0x514B49;
            if (kind.powered() && ((x == 7 || x == 8) && y > 4 && y < 12 || (y == 7 || y == 8) && x > 4 && x < 12)) color = 0xB4D6E3;
            image.setRGB(x, y, 0xFF000000 | color);
        }
        png(ASSETS + "textures/block/" + name + ".png", image);
        JsonArray elements = new JsonArray();
        if (kind.crusher()) {
            elements.add(element(1, 0, 1, 15, 3, 15)); elements.add(element(1, 3, 1, 4, 9, 15));
            elements.add(element(12, 3, 1, 15, 9, 15)); elements.add(element(4, 3, 1, 12, 9, 4)); elements.add(element(4, 3, 12, 12, 9, 15));
        } else {
            elements.add(element(1, 0, 1, 15, 3, 15)); elements.add(element(3, 3, 3, 13, 12, 13));
            elements.add(element(5, 12, 5, 11, 15, 11)); elements.add(element(7, 15, 7, 9, 16, 9));
            elements.add(element(13, 5, 6, 16, 7, 10));
        }
        JsonObject model = json("{\"parent\":\"minecraft:block/block\",\"textures\":{\"all\":\"" + texture + "\",\"particle\":\"" + texture + "\"}}");
        model.add("elements", elements); save(ASSETS + "models/block/" + name + ".json", model);
        save(ASSETS + "models/item/" + name + ".json", json("{\"parent\":\"forbidden_hex:block/" + name + "\"}"));
        JsonObject variants = new JsonObject(); String[] faces = {"north", "east", "south", "west"};
        for (int i = 0; i < 4; i++) for (boolean lit : List.of(false, true)) variants.add("facing=" + faces[i] + ",lit=" + lit, json("{\"model\":\"forbidden_hex:block/" + name + "\",\"y\":" + i * 90 + "}"));
        JsonObject states = new JsonObject(); states.add("variants", variants); save(ASSETS + "blockstates/" + name + ".json", states);
        JsonObject loot = json("{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"" + id(kind.id()) + "\"}],\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"}]}]}");
        JsonArray operations = new JsonArray();
        for (String key : List.of("AlchemyJob", "Mode", "Water", "Credit")) operations.add(json("{\"source\":\"" + key + "\",\"target\":\"BlockEntityTag." + key + "\",\"op\":\"replace\"}"));
        JsonObject copy = json("{\"function\":\"minecraft:copy_nbt\",\"source\":\"block_entity\"}"); copy.add("ops", operations);
        JsonArray functions = new JsonArray(); functions.add(copy);
        loot.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject().add("functions", functions);
        save(DATA + "loot_tables/blocks/" + name + ".json", loot);
    }
    private static JsonObject element(int x1, int y1, int z1, int x2, int y2, int z2) {
        JsonObject value = json("{\"from\":[" + x1 + "," + y1 + "," + z1 + "],\"to\":[" + x2 + "," + y2 + "," + z2 + "]}");
        JsonObject faces = new JsonObject();
        for (String face : List.of("down", "up", "north", "south", "east", "west")) faces.add(face, json("{\"uv\":[0,0,16,16],\"texture\":\"#all\"}"));
        value.add("faces", faces); return value;
    }
    private static int shade(int color, int shift) {
        return Math.max(0, Math.min(255, (color >> 16 & 255) + shift)) << 16
                | Math.max(0, Math.min(255, (color >> 8 & 255) + shift)) << 8 | Math.max(0, Math.min(255, (color & 255) + shift));
    }
    private static BufferedImage materialTexture(AlchemyMaterials.Material material) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        String shape = material.shape();
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            boolean inside;
            if (shape.equals("grain")) inside = (x - 8) * (x - 8) * 2 + (y - 9) * (y - 9) * 3 < 27;
            else {
                int width = shape.equals("tiny") ? 4 : 7, top = shape.equals("tiny") ? 8 : 5;
                inside = y >= top && y <= 13 && Math.abs(x - 7) <= Math.min(width, 1 + (y - top) * 2)
                        && ((x * 13 + y * 7) % 11 != 0 || y > 11);
            }
            if (!inside) continue;
            int color = shade(material.color(), ((x * 11 + y * 17) % 7 - 3) * 9 + (13 - y) * 2);
            image.setRGB(x, y, 0xFF000000 | color);
        }
        return image;
    }
    private static BufferedImage effectIcon(String name) {
        BufferedImage image = new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB);
        int color = com.example.akaishi.forbidden.brewing.BrewingEffectType.valueOf(name.toUpperCase(Locale.ROOT)).color();
        for (int y = 2; y < 16; y++) for (int x = 2; x < 16; x++) {
            int distance = Math.abs(x - 8) + Math.abs(y - 8);
            if (distance == 6 || x >= 7 && x <= 9 && y >= 5 && y <= 11 || y == 8 && x >= 5 && x <= 11)
                image.setRGB(x, y, 0xFF000000 | shade(color, (x + y) % 3 * 12));
        }
        return image;
    }
    private static BufferedImage gui() {
        BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics(); graphics.setColor(new Color(0xD8CFB7)); graphics.fillRect(0, 0, 210, 200);
        graphics.setColor(new Color(0x51444A)); graphics.drawRect(0, 0, 209, 199); graphics.drawRect(2, 2, 205, 195);
        graphics.setColor(new Color(0xB7AA8C)); graphics.fillRect(7, 100, 196, 2); graphics.dispose(); return image;
    }
    private void language(String locale) {
        JsonObject language = new JsonObject();
        boolean chinese = locale.equals("zh_cn");
        for (var material : AlchemyMaterials.MATERIALS) language.addProperty("item.forbidden_hex.forbidden_hex_" + material.name(), chinese ? material.chinese() : title(material.name()));
        for (var kind : AlchemyContent.Kind.values()) language.addProperty("block.forbidden_hex.forbidden_hex_" + kind.id(), chinese ? kind.chinese() : title(kind.id()));
        String[] descriptions = chinese ? new String[]{
                "6档：命中引发强度2的爆炸，不破坏地形。", "6档：半径3格生成10秒临时细雪，并施加缓慢IV。",
                "6档：瞬间催熟半径4格内的作物，促进其他可催生植物生长。", "6档：10秒内视野摇摆、透视脉动，不传送。",
                "6档：半径3格重力场持续10秒，禁跳并施加缓慢IV。", "6档：雷雨露天召唤真实雷电，其余天气造成6点电击伤害。",
                "6档：护甲值降低50%，持续20秒，不损耗装备耐久。", "6档：持续10秒，移动时每秒额外受到2点伤害。",
                "6档：持续灼烧10秒，进入水中仍继续受伤。", "6档：溶解半径2格内泥沙白名单方块；护甲降低25%并中毒10秒。",
                "6档：瞬间移除目标全部正面状态，保留负面与中性状态。", "6档：向外强力击退半径4格内的生物。",
                "6档：10秒内怪物攻击最近可见生物；玩家仅产生视觉混乱。"
        } : new String[]{
                "Tier 6: Impact causes a power-2 explosion without block damage.", "Tier 6: Temporary powder snow and Slowness IV within 3 blocks for 10s.",
                "Tier 6: Instantly matures crops within 4 blocks and grows other plants.", "Tier 6: Warps the view for 10s without teleporting.",
                "Tier 6: A 3-block gravity field blocks jumping and slows for 10s.", "Tier 6: Real lightning in exposed thunderstorms; otherwise 6 shock damage.",
                "Tier 6: Reduces armor by 50% for 20s without durability damage.", "Tier 6: Movement causes 2 extra damage per second for 10s.",
                "Tier 6: Burns for 10s, including underwater.", "Tier 6: Dissolves whitelisted dirt and sand within 2 blocks; -25% armor and poison for 10s.",
                "Tier 6: Removes only beneficial effects.", "Tier 6: Strong outward knockback within 4 blocks.",
                "Tier 6: Monsters attack the nearest visible creature for 10s; players receive visual confusion only."
        };
        for (int i = 0; i < REFINED.length; i++) {
            language.addProperty("fume.forbidden_hex." + REFINED[i] + "_breath", chinese ? FUME_NAMES[i] : title(REFINED[i]) + " Breath");
            language.addProperty("effect.forbidden_hex.brewing_" + EFFECTS[i], chinese ? FUME_NAMES[i].replace("之息", "") : title(EFFECTS[i]));
            language.addProperty("effect.forbidden_hex.brewing_" + EFFECTS[i] + ".description", descriptions[i]);
        }
        String[] keys = {"mode_0", "mode_1", "input", "outputs", "hold", "energy", "water", "fill", "progress"};
        String[] zh = {"粗制模式", "精细模式", "输入", "主产物 / 副产物", "按住研磨", "源能 %s点/秒", "水：%s / 4000 mB", "手持水桶或水瓶右键补水；蒸馏每次250mB", "进度 %s / %s tick"};
        String[] en = {"Coarse", "Fine", "Input", "Products", "Hold to grind", "Source: %s/s", "Water: %s / 4000 mB", "Use a water bucket or water bottle; 250mB per distillation", "Progress %s / %s ticks"};
        for (int i = 0; i < keys.length; i++) language.addProperty("gui.forbidden_hex.alchemy." + keys[i], chinese ? zh[i] : en[i]);
        String[] statuses = chinese ? new String[]{"等待材料", "加工中", "按住研磨继续", "缺少对应热源", "需要250mB水", "空玻璃瓶不足", "产物槽堵塞", "源能不足", "缺少对应炼金材料"}
                : new String[]{"Waiting for ingredients", "Working", "Hold to continue", "Requires heat", "Requires 250mB water", "Requires glass bottles", "Output blocked", "Insufficient source", "Requires reagent"};
        for (int i = 0; i < statuses.length; i++) language.addProperty("gui.forbidden_hex.alchemy.status_" + i, statuses[i]);
        save("assets/forbidden_hex_alchemy/lang/" + locale + ".json", language);
    }
    private static String title(String name) {
        StringBuilder value = new StringBuilder(); for (String word : name.split("_")) { if (!value.isEmpty()) value.append(' '); value.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1)); }
        return value.toString();
    }
}
