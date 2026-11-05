package com.example.akaishi.forbidden.forge.altar;

import com.google.common.hash.Hashing;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** 固定资源集合；图像由确定性像素绘制生成，物品图标均为16×16。 */
public final class SourceAltarDataProvider implements DataProvider {
    private static final List<String> BLOCKS = List.of("source_altar", "offering_pedestal", "candelabrum", "large_candelabrum", "sacred_candelabrum");
    private static final List<String> ITEMS = List.of("creeper_heart", "charged_creeper_heart", "abyssal_heart", "witch_core", "chalice",
            "filled_chalice", "immortality_chalice", "philosophers_stone", "ritual_dagger");
    private static final Color OBSIDIAN = new Color(0x293236);
    private static final Color COPPER = new Color(0xb58a54);
    private static final Color GREEN = new Color(0x65d6b0);
    private final Path root;
    private final List<CompletableFuture<?>> writes = new ArrayList<>();
    private CachedOutput output;
    public SourceAltarDataProvider(PackOutput output) { root = output.getOutputFolder(); }
    @Override public String getName() { return "Forbidden Hex source altar resources"; }
    @Override public CompletableFuture<?> run(CachedOutput output) {
        this.output = output;
        writes.clear();
        try {
            altar();
            pedestal();
            for (int i = 2; i < BLOCKS.size(); i++) candelabrum(BLOCKS.get(i), i - 2);
            for (String name : BLOCKS) { item(name); loot(name); icon(name); }
            for (String name : ITEMS) { item(name); icon(name); }
            JsonArray values = new JsonArray();
            BLOCKS.forEach(name -> values.add(id(name)));
            JsonObject tag = json("{\"replace\":false}"); tag.add("values", values);
            save("data/forbidden_hex/tags/blocks/source_altar_blocks.json", tag);
            gui();
            template();
        } catch (IOException error) { return CompletableFuture.failedFuture(error); }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }
    private static String id(String name) { return "forbidden_hex:forbidden_hex_" + name; }
    private static String texture(String name) { return "forbidden_hex:block/forbidden_hex_" + name; }
    private static String model(String name) { return "forbidden_hex:block/forbidden_hex_" + name; }
    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    private void save(String path, JsonElement value) { writes.add(DataProvider.saveStable(output, value, root.resolve(path))); }
    private void bytes(String path, byte[] data) throws IOException { output.writeIfNeeded(root.resolve(path), data, Hashing.sha1().hashBytes(data)); }
    private void png(String path, BufferedImage image) throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", stream);
        bytes("assets/forbidden_hex/textures/" + path + ".png", stream.toByteArray());
    }
    private void states(String name, JsonObject value) { save("assets/forbidden_hex/blockstates/forbidden_hex_" + name + ".json", value); }
    private void model(String name, JsonObject value) { save("assets/forbidden_hex/models/block/forbidden_hex_" + name + ".json", value); }
    private void item(String name) {
        save("assets/forbidden_hex/models/item/forbidden_hex_" + name + ".json",
                json("{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"forbidden_hex:item/forbidden_hex_" + name + "\"}}"));
    }
    private void loot(String name) {
        save("data/forbidden_hex/loot_tables/blocks/forbidden_hex_" + name + ".json", json("""
                {"type":"minecraft:block","pools":[{"rolls":1,"entries":[{"type":"minecraft:item","name":"%s"}],
                "conditions":[{"condition":"minecraft:survives_explosion"}]}]}
                """.formatted(id(name))));
    }
    private static JsonArray vector(double... values) {
        JsonArray array = new JsonArray(); for (double value : values) array.add(value); return array;
    }
    private static JsonObject element(double x1, double y1, double z1, double x2, double y2, double z2, String tex) {
        JsonObject element = new JsonObject();
        element.add("from", vector(x1, y1, z1)); element.add("to", vector(x2, y2, z2));
        JsonObject faces = new JsonObject();
        for (String face : List.of("north", "south", "east", "west", "up", "down")) {
            JsonObject value = new JsonObject(); value.addProperty("texture", "#" + tex);
            value.add("uv", vector(0, 0, 16, 16)); faces.add(face, value);
        }
        element.add("faces", faces);
        return element;
    }
    private static JsonObject blockModel(String tex, JsonArray elements) {
        JsonObject model = json("{\"parent\":\"minecraft:block/block\",\"textures\":{}}");
        model.getAsJsonObject("textures").addProperty("all", texture(tex));
        model.getAsJsonObject("textures").addProperty("particle", texture(tex));
        model.add("elements", elements);
        return model;
    }
    private void altar() throws IOException {
        JsonArray elements = new JsonArray();
        JsonObject body = element(0, 0, 0, 16, 12, 16, "all"); elements.add(body);
        JsonObject base = blockModel("source_altar_side", elements);
        base.getAsJsonObject("textures").addProperty("top", texture("source_altar_top"));
        base.getAsJsonObject("textures").addProperty("bottom", texture("source_altar_bottom"));
        body.getAsJsonObject("faces").getAsJsonObject("up").addProperty("texture", "#top");
        body.getAsJsonObject("faces").getAsJsonObject("down").addProperty("texture", "#bottom");
        model("source_altar", base);
        JsonObject variants = new JsonObject();
        for (int rotation = 0; rotation < 4; rotation++) {
            String facing = List.of("north", "east", "south", "west").get(rotation);
            for (boolean formed : new boolean[]{false, true}) for (int part = 0; part < 6; part++) {
                JsonObject variant = new JsonObject();
                variant.addProperty("model", model(formed ? "source_altar_imported_formed_" + part : "source_altar"));
                variant.addProperty("y", rotation * 90);
                variants.add("facing=" + facing + ",formed=" + formed + ",part=" + part, variant);
            }
        }
        JsonObject state = new JsonObject(); state.add("variants", variants); states("source_altar", state);
        png("block/forbidden_hex_source_altar_side", stone(16, 16, true));
        png("block/forbidden_hex_source_altar_bottom", stone(16, 16, false));
        BufferedImage top = stone(16, 16, false);
        Graphics2D g = top.createGraphics(); g.setColor(COPPER); g.drawRect(1, 1, 13, 13); g.dispose();
        png("block/forbidden_hex_source_altar_top", top);
        BufferedImage active = stone(48, 32, false);
        g = active.createGraphics(); g.setColor(COPPER); g.drawRect(1, 1, 45, 29);
        g.setColor(new Color(0x317562)); g.drawOval(6, 3, 35, 25); g.drawOval(8, 5, 31, 21);
        g.setColor(GREEN); g.drawOval(13, 6, 21, 19); g.drawLine(23, 6, 33, 21); g.drawLine(33, 21, 14, 21); g.drawLine(14, 21, 23, 6);
        for (int x = 5; x < 45; x += 6) { g.drawLine(x, 3, x, 5); g.drawLine(x, 26, x + 2, 28); }
        g.fillRect(22, 14, 3, 3); g.dispose(); png("block/forbidden_hex_source_altar_active_top", active);
    }
    private void pedestal() throws IOException {
        JsonArray elements = new JsonArray();
        elements.add(element(2, 0, 2, 14, 3, 14, "all"));
        elements.add(element(5, 3, 5, 11, 13, 11, "all"));
        elements.add(element(1, 13, 1, 15, 16, 15, "all"));
        model("offering_pedestal", blockModel("offering_pedestal", elements));
        simpleState("offering_pedestal");
        png("block/forbidden_hex_offering_pedestal", stone(16, 16, true));
    }
    private void simpleState(String name) { states(name, json("{\"variants\":{\"\":{\"model\":\"" + model(name) + "\"}}}")); }
    private void candelabrum(String name, int size) throws IOException {
        JsonArray elements = new JsonArray();
        elements.add(element(3, 0, 3, 13, 2, 13, "all"));
        elements.add(element(7, 2, 7, 9, size == 0 ? 8 : 12, 9, "all"));
        elements.add(element(size == 0 ? 3 : 1, size == 0 ? 6 : 8, 7, size == 0 ? 13 : 15, size == 0 ? 8 : 10, 9, "all"));
        for (int x : size == 0 ? new int[]{4, 8, 12} : new int[]{2, 5, 8, 11, 14}) {
            int y = size == 0 ? 8 : 10;
            elements.add(element(x - 1, y, 7, x + 1, y + 3, 9, "wax"));
            elements.add(element(x - .5, y + 3, 7.5, x + .5, y + 4, 8.5, "flame"));
        }
        JsonObject value = blockModel(name, elements);
        value.getAsJsonObject("textures").addProperty("wax", texture(name));
        value.getAsJsonObject("textures").addProperty("flame", texture(name));
        for (JsonElement entry : elements) {
            JsonObject faces = entry.getAsJsonObject().getAsJsonObject("faces");
            for (String face : faces.keySet()) {
                JsonObject data = faces.getAsJsonObject(face);
                if (data.get("texture").getAsString().equals("#wax")) data.add("uv", vector(0, 0, 4, 4));
                else if (data.get("texture").getAsString().equals("#flame")) data.add("uv", vector(4, 0, 8, 4));
                else data.add("uv", vector(0, 4, 16, 16));
            }
        }
        model(name, value); simpleState(name);
        BufferedImage image = stone(16, 16, true); Graphics2D g = image.createGraphics();
        g.setColor(new Color(0xf1e9d6)); g.fillRect(0, 0, 4, 4);
        g.setColor(size == 2 ? GREEN : new Color(0xf6c56c)); g.fillRect(4, 0, 4, 4);
        g.dispose(); png("block/forbidden_hex_" + name, image);
    }
    private static BufferedImage stone(int width, int height, boolean trim) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int noise = Math.floorMod(x * 13 + y * 17 + x * y * 7, 12);
            image.setRGB(x, y, new Color(25 + noise, 30 + noise, 34 + noise).getRGB());
        }
        if (trim) {
            Graphics2D g = image.createGraphics(); g.setColor(COPPER); g.fillRect(0, 1, width, 2); g.fillRect(0, height - 3, width, 1);
            g.setColor(GREEN); for (int x = 3; x < width; x += 7) { g.fillRect(x, 6, 1, 4); g.fillRect(x, 9, 3, 1); } g.dispose();
        }
        return image;
    }
    private void icon(String name) throws IOException {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        if (name.contains("heart") || name.equals("witch_core")) {
            Color tint = name.equals("creeper_heart") ? new Color(0x70b452) : name.equals("charged_creeper_heart") ? new Color(0x73dcdf)
                    : name.equals("abyssal_heart") ? new Color(0x6066c8) : new Color(0xc37bd4);
            g.setColor(new Color(0x18282b)); g.fillRect(2, 3, 12, 7); g.fillRect(4, 9, 8, 3); g.fillRect(6, 11, 4, 3);
            g.setColor(tint.darker()); g.fillRect(3, 3, 4, 6); g.fillRect(8, 3, 4, 6); g.fillRect(5, 8, 6, 3); g.fillRect(7, 10, 2, 3);
            g.setColor(tint); g.fillRect(3, 3, 3, 3); g.fillRect(9, 3, 3, 3); g.fillRect(4, 6, 7, 3);
            g.setColor(tint.brighter()); g.fillRect(4, 3, 2, 1); g.fillRect(9, 4, 1, 3);
            g.setColor(new Color(0x25383c)); g.drawLine(7, 4, 6, 7); g.drawLine(6, 7, 9, 10);
        } else if (name.contains("chalice")) {
            g.setColor(new Color(0x473c31)); g.fillRect(3, 2, 10, 5); g.fillRect(5, 7, 6, 3); g.fillRect(7, 9, 2, 4); g.fillRect(4, 13, 8, 2);
            g.setColor(COPPER); g.fillRect(4, 3, 8, 3); g.fillRect(5, 6, 6, 2); g.fillRect(7, 8, 2, 5); g.fillRect(5, 13, 6, 1);
            g.setColor(new Color(0xf1d494)); g.drawLine(3, 2, 12, 2); g.drawLine(5, 3, 5, 6);
            g.setColor(name.equals("chalice") ? OBSIDIAN : name.equals("filled_chalice") ? new Color(0xa54e67) : GREEN); g.fillRect(5, 3, 6, 1);
        } else if (name.equals("ritual_dagger")) {
            g.setColor(new Color(0x24343b)); g.drawLine(4, 12, 13, 3); g.drawLine(5, 13, 14, 4);
            g.setColor(new Color(0xc8e8dc)); g.drawLine(6, 10, 13, 3); g.setColor(GREEN); g.drawLine(7, 10, 13, 4);
            g.setColor(COPPER); g.drawLine(4, 9, 8, 13); g.drawLine(3, 12, 5, 14); g.setColor(new Color(0x654c3d)); g.drawLine(2, 14, 5, 11);
        } else if (name.equals("philosophers_stone")) {
            g.setColor(new Color(0x54253c)); g.fillPolygon(new int[]{8, 14, 11, 4, 1}, new int[]{1, 5, 13, 14, 6}, 5);
            g.setColor(new Color(0xc85773)); g.fillPolygon(new int[]{8, 12, 10, 5, 3}, new int[]{3, 6, 11, 12, 6}, 5);
            g.setColor(new Color(0xf3b698)); g.drawLine(7, 4, 10, 5); g.setColor(COPPER); g.drawRect(6, 6, 3, 3);
        } else if (name.contains("candelabrum")) {
            int count = name.equals("candelabrum") ? 3 : 5;
            g.setColor(COPPER); g.fillRect(7, 7, 2, 7); g.fillRect(4, 14, 8, 1); g.fillRect(2, 8, 12, 1);
            for (int i = 0; i < count; i++) {
                int x = count == 3 ? 3 + i * 4 : 2 + i * 3;
                g.setColor(new Color(0xf1e9d6)); g.fillRect(x, 4, 1, 4);
                g.setColor(name.equals("sacred_candelabrum") ? GREEN : new Color(0xffd582)); g.fillRect(x, 2, 1, 2);
            }
        } else if (name.equals("offering_pedestal")) {
            g.setColor(new Color(0x141e24)); g.fillRect(4, 2, 8, 3); g.fillRect(6, 5, 4, 7); g.fillRect(3, 12, 10, 3);
            g.setColor(OBSIDIAN.brighter()); g.fillRect(7, 5, 2, 7); g.setColor(COPPER); g.fillRect(3, 2, 10, 1); g.fillRect(3, 12, 10, 1);
            g.setColor(GREEN); g.fillRect(7, 6, 1, 4);
        } else {
            g.setColor(new Color(0x121c22)); g.fillRect(1, 5, 14, 9); g.setColor(OBSIDIAN.brighter()); g.fillRect(2, 6, 12, 6);
            g.setColor(COPPER); g.drawRect(1, 4, 13, 2); g.fillRect(2, 13, 12, 1); g.setColor(GREEN); g.drawLine(4, 4, 11, 4); g.fillRect(7, 9, 2, 2);
        }
        g.dispose(); png("item/forbidden_hex_" + name, image);
    }
    private void gui() throws IOException {
        BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0x171d20)); g.fillRect(0, 0, 236, 194);
        g.setColor(new Color(0x4c554c)); g.drawRect(0, 0, 235, 193); g.setColor(COPPER); g.drawRect(3, 3, 229, 187);
        g.setColor(new Color(0x293236)); g.fillRect(10, 8, 216, 17); g.fillRect(11, 60, 214, 54); g.fillRect(11, 117, 214, 42);
        g.setColor(new Color(0x39534b)); g.drawLine(18, 57, 217, 57); g.drawLine(18, 156, 217, 156);
        g.setColor(GREEN); for (int x : new int[]{6, 229}) for (int y : new int[]{6, 187}) g.fillRect(x, y, 2, 2);
        g.dispose(); png("gui/source_altar", image);
    }
    private void template() throws IOException {
        CompoundTag tag = new CompoundTag(); tag.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        ListTag size = new ListTag(); for (int axis : List.of(24, 16, 24)) size.add(IntTag.valueOf(axis)); tag.put("size", size);
        ListTag palette = new ListTag(); CompoundTag air = new CompoundTag(); air.putString("Name", "minecraft:air"); palette.add(air);
        tag.put("palette", palette); tag.put("blocks", new ListTag()); tag.put("entities", new ListTag());
        ByteArrayOutputStream stream = new ByteArrayOutputStream(); NbtIo.writeCompressed(tag, stream);
        bytes("data/forbidden_hex/structures/source_altar_empty.nbt", stream.toByteArray());
    }
}
