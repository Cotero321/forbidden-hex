package com.example.akaishi.forbidden.brewing;

import com.google.gson.JsonObject;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

public record WitchOvenRecipe(ResourceLocation id, BrewingIngredient input, ItemStack result,
                              ItemStack fume, int cookingTicks, double fumeChance) implements Recipe<Container> {
    public WitchOvenRecipe {
        if (id == null || input == null || result == null || result.isEmpty() || result.getCount() > result.getMaxStackSize()
                || fume == null || fume.isEmpty() || fume.getCount() > fume.getMaxStackSize() || cookingTicks < 1 || cookingTicks > 72000
                || !Double.isFinite(fumeChance) || fumeChance < 0 || fumeChance > 1) {
            throw new IllegalArgumentException("Invalid witch oven recipe");
        }
        result = result.copy();
        fume = fume.copy();
    }

    @Override public ItemStack result() { return result.copy(); }
    @Override public ItemStack fume() { return fume.copy(); }
    public net.minecraft.nbt.CompoundTag signature() {
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("Ingredient", input.ingredient().toJson().toString());
        tag.put("RequiredTag", input.requiredTag());
        tag.put("Result", result.save(new net.minecraft.nbt.CompoundTag()));
        tag.put("Fume", fume.save(new net.minecraft.nbt.CompoundTag()));
        tag.putInt("Time", cookingTicks);
        tag.putDouble("Chance", fumeChance);
        return tag;
    }
    @Override public boolean matches(Container container, Level level) { return input.test(container.getItem(0)); }
    @Override public ItemStack assemble(Container container, RegistryAccess access) { return result(); }
    @Override public boolean canCraftInDimensions(int width, int height) { return true; }
    @Override public ItemStack getResultItem(RegistryAccess access) { return result(); }
    @Override public ResourceLocation getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return BrewingRecipes.OVEN_SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return BrewingRecipes.OVEN.get(); }
    @Override public boolean isSpecial() { return true; }

    public static final class Serializer implements RecipeSerializer<WitchOvenRecipe> {
        @Override public WitchOvenRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new WitchOvenRecipe(id, BrewingIngredient.fromJson(json),
                    BrewingRecipeIO.stack(GsonHelper.getAsJsonObject(json, "result")),
                    BrewingRecipeIO.stack(GsonHelper.getAsJsonObject(json, "fume")),
                    GsonHelper.getAsInt(json, "cooking_time"), GsonHelper.getAsDouble(json, "fume_chance", 0.3));
        }
        @Override public WitchOvenRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new WitchOvenRecipe(id, BrewingIngredient.fromNetwork(buffer), buffer.readItem(),
                    buffer.readItem(), buffer.readVarInt(), buffer.readDouble());
        }
        @Override public void toNetwork(FriendlyByteBuf buffer, WitchOvenRecipe recipe) {
            recipe.input.toNetwork(buffer);
            buffer.writeItem(recipe.result);
            buffer.writeItem(recipe.fume);
            buffer.writeVarInt(recipe.cookingTicks);
            buffer.writeDouble(recipe.fumeChance);
        }
    }
}
