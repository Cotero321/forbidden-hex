package com.example.akaishi.forbidden.brewing;

import com.example.akaishi.AkaishiMod;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

public final class BrewingRecipes {
    public static RegistrySupplier<RecipeType<WitchOvenRecipe>> OVEN;
    public static RegistrySupplier<RecipeType<CrucibleRecipe>> CRUCIBLE;
    public static RegistrySupplier<RecipeSerializer<WitchOvenRecipe>> OVEN_SERIALIZER;
    public static RegistrySupplier<RecipeSerializer<CrucibleRecipe>> CRUCIBLE_SERIALIZER;

    private BrewingRecipes() { }

    public static void register() {
        OVEN = type("witch_oven");
        CRUCIBLE = type("crucible");
        var serializers = RegistrarManager.get(AkaishiMod.MOD_ID).get(Registries.RECIPE_SERIALIZER);
        OVEN_SERIALIZER = serializers.register(new ResourceLocation(AkaishiMod.MOD_ID, "witch_oven"), WitchOvenRecipe.Serializer::new);
        CRUCIBLE_SERIALIZER = serializers.register(new ResourceLocation(AkaishiMod.MOD_ID, "crucible"), CrucibleRecipe.Serializer::new);
    }

    private static <T extends Recipe<?>> RegistrySupplier<RecipeType<T>> type(String name) {
        ResourceLocation id = new ResourceLocation(AkaishiMod.MOD_ID, name);
        return RegistrarManager.get(AkaishiMod.MOD_ID).get(Registries.RECIPE_TYPE)
                .register(id, () -> new RecipeType<T>() {
                    @Override public String toString() { return id.toString(); }
                });
    }
}
