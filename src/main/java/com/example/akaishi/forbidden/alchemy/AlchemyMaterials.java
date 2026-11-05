package com.example.akaishi.forbidden.alchemy;

import com.example.akaishi.AkaishiMod;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AlchemyMaterials {
    public record Material(String name, String chinese, int color, String shape) { }
    public static final List<String> METALS = List.of("cinnabar", "sulfur", "silver", "iron", "copper", "gold", "redstone", "lapis", "quartz");
    public static final List<Material> MATERIALS;
    private static final Map<String, RegistrySupplier<Item>> ITEMS = new LinkedHashMap<>();
    static {
        List<Material> materials = new ArrayList<>();
        String[] chinese = {"朱砂", "硫磺", "银", "铁", "铜", "金", "红石", "青金石", "石英"};
        int[] colors = {0xCC483C, 0xE7CD43, 0xBFCCD6, 0xAF9C88, 0xCD8454, 0xE6BE48, 0xBF363A, 0x496DC7, 0xE7DED3};
        for (int i = 0; i < METALS.size(); i++) {
            materials.add(new Material("crude_" + METALS.get(i) + "_dust", "粗制" + chinese[i] + "粉", colors[i], "dust"));
            materials.add(new Material("fine_" + METALS.get(i) + "_dust", "精细" + chinese[i] + "粉", colors[i], "dust"));
            materials.add(new Material("tiny_" + METALS.get(i) + "_dust", "细小" + chinese[i] + "粉", colors[i], "tiny"));
        }
        String[] companions = {"lead", "green_vitriol", "blue_vitriol", "hematite", "pyrite", "saltpeter"};
        String[] names = {"铅", "绿矾", "胆矾", "赤铁矿", "黄铁矿", "硝石"};
        int[] tints = {0x88849E, 0x7EAE91, 0x489CCB, 0xAF5143, 0xC1A049, 0xDCD6BD};
        for (int i = 0; i < companions.length; i++) {
            materials.add(new Material("tiny_" + companions[i] + "_dust", "细小" + names[i] + "粉", tints[i], "tiny"));
            materials.add(new Material("crude_" + companions[i] + "_dust", "粗制" + names[i] + "粉", tints[i], "dust"));
        }
        materials.add(new Material("stone_fragment", "碎石块", 0x92938D, "grain"));
        materials.add(new Material("stone_dust", "石粉", 0xAAA99F, "dust"));
        materials.add(new Material("limestone_dust", "石灰粉", 0xE8DEB9, "dust"));
        materials.add(new Material("quicklime", "生石灰", 0xE9E1CE, "grain"));
        materials.add(new Material("iron_oxide", "氧化铁（铁红）", 0xBA513A, "grain"));
        materials.add(new Material("copper_oxide", "氧化铜（铜黑）", 0x454C51, "grain"));
        materials.add(new Material("lead_oxide", "氧化铅（密陀僧）", 0xD7A54E, "grain"));
        materials.add(new Material("pure_iron_dust", "纯铁粉", 0xD1C6B6, "dust"));
        materials.add(new Material("potash", "钾碱粉", 0xDBCDC0, "grain"));
        materials.add(new Material("moon_salt", "月华之盐", 0xC8C5F1, "grain"));
        String[] liquids = {"sulfuric_acid", "sulfurous_acid", "purified_water", "nitric_acid", "moon_spring", "mercury"};
        String[] liquidNames = {"硫酸", "亚硫酸", "纯化水", "硝酸", "月华之泉", "水银"};
        int[] liquidColors = {0xD9DE9B, 0xCEC591, 0x8BCDDD, 0xD9C976, 0xB6A4E9, 0x9FAFBF};
        for (int i = 0; i < liquids.length; i++) materials.add(new Material(liquids[i], liquidNames[i], liquidColors[i], "bottle"));
        MATERIALS = List.copyOf(materials);
    }
    private AlchemyMaterials() { }
    public static ResourceLocation id(String name) { return new ResourceLocation(AkaishiMod.MOD_ID, "forbidden_hex_" + name); }
    public static void register() {
        var registry = RegistrarManager.get(AkaishiMod.MOD_ID).get(Registries.ITEM);
        for (Material material : MATERIALS) ITEMS.put(material.name(), registry.register(id(material.name()),
                () -> material.shape().equals("bottle") ? new Item(new Item.Properties().stacksTo(16).craftRemainder(net.minecraft.world.item.Items.GLASS_BOTTLE))
                        : new Item(new Item.Properties())));
    }
    public static Item item(String name) { return ITEMS.get(name).get(); }
    public static boolean isLiquid(Item item) {
        return MATERIALS.stream().anyMatch(material -> material.shape().equals("bottle") && item(material.name()) == item);
    }
    public static void addCreativeItems(CreativeModeTab.Output output) { ITEMS.values().forEach(item -> output.accept(item.get())); }
}
