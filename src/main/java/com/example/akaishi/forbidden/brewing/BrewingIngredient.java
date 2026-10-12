package com.example.akaishi.forbidden.brewing;

import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

public record BrewingIngredient(Ingredient ingredient, CompoundTag requiredTag) {
    public BrewingIngredient {
        if (ingredient == null || requiredTag == null) throw new IllegalArgumentException("Missing brewing ingredient");
        requiredTag = requiredTag.copy();
    }

    @Override public CompoundTag requiredTag() { return requiredTag.copy(); }

    public boolean test(ItemStack stack) {
        return ingredient.test(stack) && (requiredTag.isEmpty()
                || stack.hasTag() && NbtUtils.compareNbt(requiredTag, stack.getTag(), true));
    }

    public static BrewingIngredient fromJson(JsonObject json) {
        return new BrewingIngredient(Ingredient.fromJson(json.get("ingredient")), BrewingRecipeIO.tag(json, "nbt"));
    }

    public static BrewingIngredient fromNetwork(FriendlyByteBuf buffer) {
        Ingredient ingredient = Ingredient.fromNetwork(buffer);
        CompoundTag tag = buffer.readNbt();
        return new BrewingIngredient(ingredient, tag == null ? new CompoundTag() : tag);
    }

    public void toNetwork(FriendlyByteBuf buffer) {
        ingredient.toNetwork(buffer);
        buffer.writeNbt(requiredTag);
    }
}
