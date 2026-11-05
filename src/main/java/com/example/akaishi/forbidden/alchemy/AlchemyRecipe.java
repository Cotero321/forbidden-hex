package com.example.akaishi.forbidden.alchemy;

import com.example.akaishi.forbidden.brewing.BrewingIngredient;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.crafting.ShapedRecipe;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public record AlchemyRecipe(ResourceLocation id, String process, int mode, BrewingIngredient input,
                            BrewingIngredient reagent, int equivalents, int bottles, List<Yield> outputs) implements Recipe<Container> {
    public record Yield(ItemStack stack, int min, int max, double chance) {
        public Yield {
            if (stack == null || stack.isEmpty() || min < 1 || max < min || max > 64
                    || !Double.isFinite(chance) || chance <= 0 || chance > 1) throw new IllegalArgumentException("Invalid alchemy yield");
            stack = stack.copyWithCount(1);
        }
        @Override public ItemStack stack() { return stack.copy(); }
    }
    public AlchemyRecipe {
        if (id == null || !List.of("crushing", "distilling", "refining").contains(process) || mode < 0 || mode > 1
                || input == null || equivalents < 1 || equivalents > 9 || bottles < 0 || bottles > 2
                || outputs == null || outputs.isEmpty() || outputs.size() > 4
                || process.equals("refining") && reagent == null) throw new IllegalArgumentException("Invalid alchemy recipe");
        outputs = List.copyOf(outputs);
    }
    public boolean forMachine(AlchemyContent.Kind kind, int selectedMode) {
        return kind.crusher() ? process.equals("crushing") && mode == selectedMode : !process.equals("crushing");
    }
    @Override public boolean matches(Container container, Level level) {
        return container.getContainerSize() >= 3 && input.test(container.getItem(0))
                && (reagent == null || reagent.test(container.getItem(1))) && container.getItem(2).getCount() >= bottles;
    }
    public int duration(int batch, boolean powered) {
        if (!process.equals("crushing")) return 200;
        int numerator = (mode == 0 ? 50 : 100) + (mode == 0 ? 6 : 12) * batch * equivalents;
        int denominator = powered ? 10 : 5;
        return (numerator + denominator - 1) / denominator;
    }
    public NonNullList<ItemStack> products(int batch, RandomSource random, boolean maximum) {
        NonNullList<ItemStack> result = NonNullList.withSize(4, ItemStack.EMPTY);
        for (int i = 0; i < outputs.size(); i++) {
            Yield yield = outputs.get(i);
            int count = 0;
            for (int j = 0; j < batch * equivalents; j++) {
                if (maximum) count += yield.max();
                else if (random.nextDouble() < yield.chance()) count += yield.min() + random.nextInt(yield.max() - yield.min() + 1);
            }
            if (count > 0) result.set(i, yield.stack().copyWithCount(count));
        }
        return result;
    }
    @Override public ItemStack assemble(Container container, RegistryAccess access) { return getResultItem(access); }
    @Override public ItemStack getResultItem(RegistryAccess access) { return outputs.get(0).stack(); }
    @Override public boolean canCraftInDimensions(int width, int height) { return true; }
    @Override public ResourceLocation getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return AlchemyContent.SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return AlchemyContent.RECIPES.get(); }
    @Override public boolean isSpecial() { return true; }

    public static final class Serializer implements RecipeSerializer<AlchemyRecipe> {
        @Override public AlchemyRecipe fromJson(ResourceLocation id, JsonObject json) {
            String process = GsonHelper.getAsString(json, "process");
            BrewingIngredient reagent = json.has("reagent") ? BrewingIngredient.fromJson(json.getAsJsonObject("reagent")) : null;
            JsonArray array = GsonHelper.getAsJsonArray(json, "outputs");
            List<Yield> outputs = new ArrayList<>();
            for (var entry : array) {
                JsonObject value = entry.getAsJsonObject();
                ItemStack stack = ShapedRecipe.itemStackFromJson(value);
                if (value.has("nbt")) {
                    try { stack.setTag(TagParser.parseTag(GsonHelper.getAsString(value, "nbt"))); }
                    catch (CommandSyntaxException exception) { throw new com.google.gson.JsonSyntaxException("Invalid alchemy output NBT", exception); }
                }
                outputs.add(new Yield(stack, GsonHelper.getAsInt(value, "min", 1),
                        GsonHelper.getAsInt(value, "max", GsonHelper.getAsInt(value, "min", 1)), GsonHelper.getAsDouble(value, "chance", 1)));
            }
            return new AlchemyRecipe(id, process, GsonHelper.getAsInt(json, "mode", 0), BrewingIngredient.fromJson(json),
                    reagent, GsonHelper.getAsInt(json, "equivalents", 1), GsonHelper.getAsInt(json, "bottles", 0), outputs);
        }
        @Override public AlchemyRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            String process = buffer.readUtf(32);
            int mode = buffer.readVarInt();
            BrewingIngredient input = BrewingIngredient.fromNetwork(buffer);
            BrewingIngredient reagent = buffer.readBoolean() ? BrewingIngredient.fromNetwork(buffer) : null;
            int equivalents = buffer.readVarInt(), bottles = buffer.readVarInt(), count = buffer.readVarInt();
            if (count < 1 || count > 4) throw new IllegalArgumentException("Invalid alchemy output count");
            List<Yield> outputs = new ArrayList<>();
            for (int i = 0; i < count; i++) outputs.add(new Yield(buffer.readItem(), buffer.readVarInt(), buffer.readVarInt(), buffer.readDouble()));
            return new AlchemyRecipe(id, process, mode, input, reagent, equivalents, bottles, outputs);
        }
        @Override public void toNetwork(FriendlyByteBuf buffer, AlchemyRecipe recipe) {
            buffer.writeUtf(recipe.process, 32);
            buffer.writeVarInt(recipe.mode);
            recipe.input.toNetwork(buffer);
            buffer.writeBoolean(recipe.reagent != null);
            if (recipe.reagent != null) recipe.reagent.toNetwork(buffer);
            buffer.writeVarInt(recipe.equivalents);
            buffer.writeVarInt(recipe.bottles);
            buffer.writeVarInt(recipe.outputs.size());
            for (Yield yield : recipe.outputs) {
                buffer.writeItem(yield.stack()); buffer.writeVarInt(yield.min()); buffer.writeVarInt(yield.max()); buffer.writeDouble(yield.chance());
            }
        }
    }
}
