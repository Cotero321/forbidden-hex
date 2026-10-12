package com.example.akaishi.forbidden.item;

import com.example.akaishi.AkaishiMod;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;

import java.util.function.Supplier;

public final class ForbiddenMaterialItems {
    public static final String GOLDEN_MELON_SLICE_ID = "forbidden_hex_golden_melon_slice";
    public static RegistrySupplier<Item> goldenMelonSlice;
    public static RegistrySupplier<Item> sculkLifeform;
    public static RegistrySupplier<Item> witherCharm;

    private ForbiddenMaterialItems() {
    }

    public static void register() {
        goldenMelonSlice = item(GOLDEN_MELON_SLICE_ID, () -> new Item(new Item.Properties()
                .food(new FoodProperties.Builder().nutrition(6).saturationMod(1.2f).alwaysEat().build())));
        sculkLifeform = item("forbidden_hex_sculk_lifeform", () -> new Item(new Item.Properties()));
        witherCharm = item("forbidden_hex_wither_charm", () -> new Item(new Item.Properties()));
    }

    private static RegistrySupplier<Item> item(String id, Supplier<Item> factory) {
        return RegistrarManager.get(AkaishiMod.MOD_ID).get(Registries.ITEM)
                .register(new ResourceLocation(AkaishiMod.MOD_ID, id), factory);
    }
}
