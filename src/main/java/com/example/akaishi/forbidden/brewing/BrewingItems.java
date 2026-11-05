package com.example.akaishi.forbidden.brewing;

import com.example.akaishi.AkaishiMod;
import dev.architectury.registry.registries.Registrar;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public final class BrewingItems {
    public static RegistrySupplier<Item> WOOD_ASH;
    public static RegistrySupplier<Item> GRASS_ASH;
    public static RegistrySupplier<Item> FUME;
    public static RegistrySupplier<Item> POTION;
    public static RegistrySupplier<Item> TRANSMUTATION_DUST;
    public static RegistrySupplier<Item> SLAKED_LIME_BOTTLE;
    public static RegistrySupplier<Item> REFINED_HERB_PASTE;
    public static RegistrySupplier<Item> RUNE_STONE;
    public static RegistrySupplier<Item> CHARGED_RUNE_STONE;

    public static RegistrySupplier<Item> MANDRAKE_ROOT;
    public static RegistrySupplier<Item> HENBANE;
    public static RegistrySupplier<Item> BELLADONNA;
    public static RegistrySupplier<Item> DATURA;
    public static RegistrySupplier<Item> ACONITE;
    public static RegistrySupplier<Item> FOXGLOVE;
    public static RegistrySupplier<Item> HELLROOT;
    public static RegistrySupplier<Item> COLCHICUM;
    public static RegistrySupplier<Item> YEW;
    public static RegistrySupplier<Item> ERGOT;
    public static RegistrySupplier<Item> PARASITIC_VINE;
    public static RegistrySupplier<Item> EMBER_MOSS;
    public static RegistrySupplier<Item> GLINT_WEED;

    public static RegistrySupplier<Item> MANDRAKE_SEEDS;
    public static RegistrySupplier<Item> HENBANE_SEEDS;
    public static RegistrySupplier<Item> BELLADONNA_SEEDS;
    public static RegistrySupplier<Item> DATURA_SEEDS;
    public static RegistrySupplier<Item> ACONITE_SEEDS;
    public static RegistrySupplier<Item> FOXGLOVE_SEEDS;
    public static RegistrySupplier<Item> HELLROOT_SEEDS;
    public static RegistrySupplier<Item> COLCHICUM_SEEDS;
    public static RegistrySupplier<Item> YEW_SEEDS;
    public static RegistrySupplier<Item> ERGOT_SEEDS;
    public static RegistrySupplier<Item> PARASITIC_VINE_SEEDS;
    public static RegistrySupplier<Item> EMBER_MOSS_SEEDS;
    public static RegistrySupplier<Item> GLINT_WEED_SEEDS;

    public static RegistrySupplier<Item> HAWTHORN_SAPLING;
    public static RegistrySupplier<Item> ROWAN_SAPLING;
    public static RegistrySupplier<Item> ALDER_SAPLING;
    public static RegistrySupplier<Item> ELDER_SAPLING;
    public static RegistrySupplier<Item> WILLOW_SAPLING;

    private BrewingItems() { }

    public static void register() {
        Registrar<Item> items = RegistrarManager.get(AkaishiMod.MOD_ID).get(Registries.ITEM);
        WOOD_ASH = items.register(id("wood_ash"), () -> new Item(new Item.Properties()));
        GRASS_ASH = items.register(id("grass_ash"), () -> new Item(new Item.Properties()));
        FUME = items.register(id("fume"), BrewingFumeItem::new);
        POTION = items.register(id("brewed_potion"), BrewingPotionItem::new);
        TRANSMUTATION_DUST = items.register(id("transmutation_dust"), () -> new Item(new Item.Properties()));
        SLAKED_LIME_BOTTLE = items.register(id("slaked_lime_bottle"), () -> new Item(new Item.Properties()
                .stacksTo(16).craftRemainder(net.minecraft.world.item.Items.GLASS_BOTTLE)));
        REFINED_HERB_PASTE = items.register(id("refined_herb_paste"), () -> new Item(new Item.Properties()));
        RUNE_STONE = items.register(id("rune_stone"), () -> new Item(new Item.Properties()));
        CHARGED_RUNE_STONE = items.register(id("charged_rune_stone"), () -> new Item(new Item.Properties()));

        MANDRAKE_ROOT = items.register(id("mandrake_root"), () -> new Item(new Item.Properties()));
        HENBANE = items.register(id("henbane"), () -> new Item(new Item.Properties()));
        BELLADONNA = items.register(id("belladonna"), () -> new Item(new Item.Properties()));
        DATURA = items.register(id("datura"), () -> new Item(new Item.Properties()));
        ACONITE = items.register(id("aconite"), () -> new Item(new Item.Properties()));
        FOXGLOVE = items.register(id("foxglove"), () -> new Item(new Item.Properties()));
        HELLROOT = items.register(id("hellroot"), () -> new Item(new Item.Properties()));
        COLCHICUM = items.register(id("colchicum"), () -> new Item(new Item.Properties()));
        YEW = items.register(id("yew"), () -> new Item(new Item.Properties()));
        ERGOT = items.register(id("ergot"), () -> new Item(new Item.Properties()));
        PARASITIC_VINE = items.register(id("parasitic_vine"), () -> new Item(new Item.Properties()));
        EMBER_MOSS = items.register(id("ember_moss"), () -> new Item(new Item.Properties()));
        GLINT_WEED = items.register(id("glint_weed"), () -> new Item(new Item.Properties()));

        MANDRAKE_SEEDS = seed(items, "mandrake");
        HENBANE_SEEDS = seed(items, "henbane");
        BELLADONNA_SEEDS = seed(items, "belladonna");
        DATURA_SEEDS = seed(items, "datura");
        ACONITE_SEEDS = seed(items, "aconite");
        FOXGLOVE_SEEDS = seed(items, "foxglove");
        HELLROOT_SEEDS = seed(items, "hellroot");
        COLCHICUM_SEEDS = seed(items, "colchicum");
        YEW_SEEDS = seed(items, "yew");
        ERGOT_SEEDS = seed(items, "ergot");
        PARASITIC_VINE_SEEDS = seed(items, "parasitic_vine");
        EMBER_MOSS_SEEDS = seed(items, "ember_moss");
        GLINT_WEED_SEEDS = seed(items, "glint_weed");

        HAWTHORN_SAPLING = sapling(items, "hawthorn");
        ROWAN_SAPLING = sapling(items, "rowan");
        ALDER_SAPLING = sapling(items, "alder");
        ELDER_SAPLING = sapling(items, "elder");
        WILLOW_SAPLING = sapling(items, "willow");
    }

    private static RegistrySupplier<Item> seed(Registrar<Item> items, String name) {
        return items.register(id(name + "_seeds"), () -> new net.minecraft.world.item.ItemNameBlockItem(
                BrewingPlants.crop(name), new Item.Properties()));
    }

    private static RegistrySupplier<Item> sapling(Registrar<Item> items, String name) {
        return items.register(id(name + "_sapling"), () -> new net.minecraft.world.item.BlockItem(
                BrewingPlants.sapling(name), new Item.Properties()));
    }

    public static void addCreativeItems(net.minecraft.world.item.CreativeModeTab.Output output) {
        for (ResourceLocation fume : BrewingFumes.NON_EVIL) output.accept(BrewingFumeItem.create(fume));
        output.accept(BrewingFumeItem.create(BrewingFumes.EVIL_BREATH));
        for (ResourceLocation fume : BrewingFumes.REFINED) output.accept(BrewingFumeItem.create(fume));
        for (RegistrySupplier<Item> item : java.util.List.of(TRANSMUTATION_DUST, SLAKED_LIME_BOTTLE, REFINED_HERB_PASTE, RUNE_STONE,
                CHARGED_RUNE_STONE, MANDRAKE_ROOT, HENBANE, BELLADONNA, DATURA, ACONITE, FOXGLOVE, HELLROOT,
                COLCHICUM, YEW, ERGOT, PARASITIC_VINE, EMBER_MOSS, GLINT_WEED,
                MANDRAKE_SEEDS, HENBANE_SEEDS, BELLADONNA_SEEDS, DATURA_SEEDS, ACONITE_SEEDS, FOXGLOVE_SEEDS,
                HELLROOT_SEEDS, COLCHICUM_SEEDS, YEW_SEEDS, ERGOT_SEEDS, PARASITIC_VINE_SEEDS, EMBER_MOSS_SEEDS,
                GLINT_WEED_SEEDS, HAWTHORN_SAPLING, ROWAN_SAPLING, ALDER_SAPLING, ELDER_SAPLING, WILLOW_SAPLING)) {
            output.accept(item.get());
        }
        for (String tree : BrewingPlants.TREES) {
            output.accept(BrewingPlants.logs().get(tree).get());
            output.accept(BrewingPlants.leaves().get(tree).get());
        }
    }

    public static ResourceLocation id(String name) {
        return new ResourceLocation(AkaishiMod.MOD_ID, "forbidden_hex_" + name);
    }
}
