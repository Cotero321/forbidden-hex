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

    private BrewingItems() { }

    public static void register() {
        Registrar<Item> items = RegistrarManager.get(AkaishiMod.MOD_ID).get(Registries.ITEM);
        WOOD_ASH = items.register(id("wood_ash"), () -> new Item(new Item.Properties()));
        GRASS_ASH = items.register(id("grass_ash"), () -> new Item(new Item.Properties()));
        FUME = items.register(id("fume"), BrewingFumeItem::new);
        POTION = items.register(id("brewed_potion"), BrewingPotionItem::new);
    }

    public static ResourceLocation id(String name) {
        return new ResourceLocation(AkaishiMod.MOD_ID, "forbidden_hex_" + name);
    }
}
