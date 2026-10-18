package com.example.akaishi.forbidden.brewing;

import com.example.akaishi.forbidden.item.ForbiddenMaterialItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.List;

public final class BrewingModifiers {
    private static List<BrewingModifier> defaults;

    private BrewingModifiers() { }

    public static List<BrewingModifier> defaults() {
        if (defaults == null) {
            List<BrewingModifier> entries = new ArrayList<>();
            add(entries, BrewingItems.MANDRAKE_ROOT.get(), BrewingModifier.Kind.CAPACITY, 1);
            add(entries, BrewingItems.GLINT_WEED.get(), BrewingModifier.Kind.CAPACITY, 1);
            add(entries, Items.NETHER_WART, BrewingModifier.Kind.CAPACITY, 2);
            add(entries, ForbiddenMaterialItems.goldenMelonSlice.get(), BrewingModifier.Kind.CAPACITY, 2);
            add(entries, BrewingItems.REFINED_HERB_PASTE.get(), BrewingModifier.Kind.CAPACITY, 2);
            add(entries, Items.NETHER_STAR, BrewingModifier.Kind.CAPACITY, 4);
            add(entries, Items.REDSTONE, BrewingModifier.Kind.DURATION, 2);
            add(entries, Items.REDSTONE_BLOCK, BrewingModifier.Kind.DURATION, 3);
            add(entries, Items.OBSIDIAN, BrewingModifier.Kind.DURATION, 4);
            add(entries, Items.GILDED_BLACKSTONE, BrewingModifier.Kind.DURATION, 5);
            add(entries, Items.CRYING_OBSIDIAN, BrewingModifier.Kind.DURATION, 6);
            add(entries, Items.GLOWSTONE, BrewingModifier.Kind.STRENGTH, 2);
            add(entries, Items.END_CRYSTAL, BrewingModifier.Kind.STRENGTH, 3);
            add(entries, BrewingItems.RUNE_STONE.get(), BrewingModifier.Kind.STRENGTH, 4);
            add(entries, BrewingItems.CHARGED_RUNE_STONE.get(), BrewingModifier.Kind.STRENGTH, 5);
            entries.add(delivery(Items.GUNPOWDER, BrewingPotionData.Delivery.SPLASH));
            entries.add(delivery(Items.DRAGON_BREATH, BrewingPotionData.Delivery.LINGERING));
            entries.add(delivery(BrewingItems.HENBANE.get(), BrewingPotionData.Delivery.DIRECT));
            add(entries, Items.GOLD_NUGGET, BrewingModifier.Kind.HIDE_PARTICLES, 0);
            add(entries, Items.IRON_NUGGET, BrewingModifier.Kind.PRESSURE_PLATES, 0);
            defaults = List.copyOf(entries);
        }
        return defaults;
    }

    private static void add(List<BrewingModifier> entries, Item item, BrewingModifier.Kind kind, int value) {
        entries.add(new BrewingModifier(ingredient(item), kind, value, BrewingPotionData.Delivery.DRINK, List.of()));
    }

    private static BrewingModifier delivery(Item item, BrewingPotionData.Delivery delivery) {
        return new BrewingModifier(ingredient(item), BrewingModifier.Kind.DELIVERY, 0, delivery, List.of());
    }

    private static BrewingIngredient ingredient(Item item) {
        return new BrewingIngredient(Ingredient.of(item), new CompoundTag());
    }
}
