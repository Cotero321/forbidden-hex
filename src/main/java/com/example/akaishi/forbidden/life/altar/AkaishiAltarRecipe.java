package com.example.akaishi.forbidden.life.altar;

import com.example.akaishi.config.ModConfig;
import com.example.akaishi.forbidden.multiblock.AkaishiGoatAltarTiersStructure;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

public record AkaishiAltarRecipe(Predicate<ItemStack> hostOffering,
                                 Supplier<Item> hostItem,
                                 List<Requirement> outer,
                                 Supplier<Item> output,
                                 boolean advanced,
                                 @Nullable String ritualName) {
    public static final int OUTER_SLOTS = 8;

    public record Requirement(Predicate<ItemStack> test, int count, Supplier<Item> item) {
        public static Requirement of(int count, Predicate<ItemStack> test, Supplier<Item> item) {
            return new Requirement(test, count, item);
        }
    }

    public static List<AkaishiAltarRecipe> all() {
        return List.of();
    }

    public int outerCount() {
        return outer.stream().mapToInt(Requirement::count).sum();
    }

    public int requiredTier() {
        return advanced ? ModConfig.altarNewRecipeTierRequired : AkaishiGoatAltarTiersStructure.FORMED_TIER;
    }

    public long progressMax() {
        return advanced ? ModConfig.altarNewRecipeProgressMax : ModConfig.altarLegacyProgressMax;
    }
}
