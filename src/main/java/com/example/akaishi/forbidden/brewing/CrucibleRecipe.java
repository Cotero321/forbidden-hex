package com.example.akaishi.forbidden.brewing;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

public record CrucibleRecipe(ResourceLocation id, List<BrewingIngredient> steps, BrewingHeat heat,
                             ItemStack result, boolean potion, List<BrewingModifier> modifiers) implements Recipe<Container> {
    public CrucibleRecipe {
        if (steps == null || steps.isEmpty() || steps.size() > CrucibleBlockEntity.MAX_STEPS || heat == BrewingHeat.NONE
                || result == null || result.isEmpty() || result.getCount() < 1 || result.getCount() > result.getMaxStackSize()
                || modifiers == null || modifiers.size() > 64) {
            throw new IllegalArgumentException("Invalid crucible recipe");
        }
        if (potion && (result.getCount() != 1 || !result.is(BrewingItems.POTION.get())
                || BrewingPotionData.read(result).isEmpty())) throw new IllegalArgumentException("Invalid crucible potion");
        if (!potion && !modifiers.isEmpty()) throw new IllegalArgumentException("Invalid crucible modifiers");
        if (potion) {
            BrewingPotionData base = BrewingPotionData.read(result).orElseThrow();
            for (BrewingModifier modifier : modifiers) modifier.apply(base);
        }
        steps = List.copyOf(steps);
        modifiers = List.copyOf(modifiers);
        result = result.copy();
    }

    public CrucibleRecipe(ResourceLocation id, List<BrewingIngredient> steps, BrewingHeat heat, ItemStack result, boolean potion) {
        this(id, steps, heat, result, potion, List.of());
    }

    @Override public ItemStack result() { return result.copy(); }
    public record Evaluation(boolean valid, int baseSteps, ItemStack result) { }

    public Evaluation evaluate(List<ItemStack> history) {
        int baseSteps = 0;
        EnumSet<BrewingModifier.Kind> applied = EnumSet.noneOf(BrewingModifier.Kind.class);
        BrewingPotionData properties = potion ? BrewingPotionData.read(result).orElseThrow() : null;
        for (ItemStack input : history) {
            if (baseSteps < steps.size() && steps.get(baseSteps).test(input)) {
                baseSteps++;
                continue;
            }
            List<BrewingModifier> choices = modifiers.stream().filter(modifier -> modifier.ingredient().test(input)).toList();
            if (choices.size() != 1 || !applied.add(choices.get(0).kind()) || properties == null) {
                return new Evaluation(false, baseSteps, ItemStack.EMPTY);
            }
            try {
                properties = choices.get(0).apply(properties);
            } catch (IllegalArgumentException | ArithmeticException exception) {
                return new Evaluation(false, baseSteps, ItemStack.EMPTY);
            }
        }
        ItemStack output = result();
        if (properties != null) properties.write(output);
        return new Evaluation(true, baseSteps, output);
    }

    public boolean acceptsIngredient(ItemStack input) {
        return steps.stream().anyMatch(ingredient -> ingredient.test(input))
                || modifiers.stream().anyMatch(modifier -> modifier.ingredient().test(input));
    }

    public boolean prefix(List<ItemStack> history) { return evaluate(history).valid(); }

    public boolean complete(List<ItemStack> history, BrewingHeat currentHeat) {
        Evaluation evaluation = evaluate(history);
        return currentHeat == heat && evaluation.valid() && evaluation.baseSteps() == steps.size();
    }
    @Override public boolean matches(Container container, Level level) {
        List<ItemStack> history = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) if (!container.getItem(i).isEmpty()) history.add(container.getItem(i));
        Evaluation evaluation = evaluate(history);
        return evaluation.valid() && evaluation.baseSteps() == steps.size();
    }
    @Override public ItemStack assemble(Container container, RegistryAccess access) {
        List<ItemStack> history = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) if (!container.getItem(i).isEmpty()) history.add(container.getItem(i));
        Evaluation evaluation = evaluate(history);
        return evaluation.valid() && evaluation.baseSteps() == steps.size() ? evaluation.result() : ItemStack.EMPTY;
    }
    @Override public boolean canCraftInDimensions(int width, int height) { return true; }
    @Override public ItemStack getResultItem(RegistryAccess access) { return result(); }
    @Override public ResourceLocation getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return BrewingRecipes.CRUCIBLE_SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return BrewingRecipes.CRUCIBLE.get(); }
    @Override public boolean isSpecial() { return true; }

    public static final class Serializer implements RecipeSerializer<CrucibleRecipe> {
        @Override public CrucibleRecipe fromJson(ResourceLocation id, JsonObject json) {
            List<BrewingIngredient> steps = new ArrayList<>();
            for (var entry : GsonHelper.getAsJsonArray(json, "steps")) {
                JsonObject step = entry.getAsJsonObject();
                BrewingIngredient ingredient = BrewingIngredient.fromJson(step);
                int count = GsonHelper.getAsInt(step, "count", 1);
                if (count < 1 || count > CrucibleBlockEntity.MAX_STEPS || steps.size() + count > CrucibleBlockEntity.MAX_STEPS) {
                    throw new JsonSyntaxException("Invalid crucible ingredient count");
                }
                for (int i = 0; i < count; i++) steps.add(ingredient);
            }
            String kind = GsonHelper.getAsString(json, "kind");
            if (!kind.equals("potion") && !kind.equals("transformation")) throw new JsonSyntaxException("Unknown crucible recipe kind");
            boolean potion = kind.equals("potion");
            ItemStack result;
            if (potion) {
                result = new ItemStack(BrewingItems.POTION.get());
                BrewingRecipeIO.potion(GsonHelper.getAsJsonObject(json, "result")).write(result);
            } else result = BrewingRecipeIO.stack(GsonHelper.getAsJsonObject(json, "result"));
            List<BrewingModifier> modifiers = new ArrayList<>();
            if (json.has("modifiers")) for (var entry : GsonHelper.getAsJsonArray(json, "modifiers")) {
                if (modifiers.size() >= 64) throw new JsonSyntaxException("Too many crucible modifiers");
                modifiers.add(BrewingModifier.fromJson(entry.getAsJsonObject()));
            }
            return new CrucibleRecipe(id, steps,
                    BrewingHeat.valueOf(GsonHelper.getAsString(json, "heat", "fire").toUpperCase(Locale.ROOT)), result, potion, modifiers);
        }
        @Override public CrucibleRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            int count = buffer.readVarInt();
            if (count < 1 || count > CrucibleBlockEntity.MAX_STEPS) throw new IllegalArgumentException("Invalid crucible step count");
            List<BrewingIngredient> steps = new ArrayList<>();
            for (int i = 0; i < count; i++) steps.add(BrewingIngredient.fromNetwork(buffer));
            BrewingHeat heat = buffer.readEnum(BrewingHeat.class);
            ItemStack result = buffer.readItem();
            boolean potion = buffer.readBoolean();
            int modifierCount = buffer.readVarInt();
            if (modifierCount < 0 || modifierCount > 64) throw new IllegalArgumentException("Invalid crucible modifier count");
            List<BrewingModifier> modifiers = new ArrayList<>();
            for (int i = 0; i < modifierCount; i++) modifiers.add(BrewingModifier.fromNetwork(buffer));
            return new CrucibleRecipe(id, steps, heat, result, potion, modifiers);
        }
        @Override public void toNetwork(FriendlyByteBuf buffer, CrucibleRecipe recipe) {
            buffer.writeVarInt(recipe.steps.size());
            for (BrewingIngredient step : recipe.steps) step.toNetwork(buffer);
            buffer.writeEnum(recipe.heat);
            buffer.writeItem(recipe.result);
            buffer.writeBoolean(recipe.potion);
            buffer.writeVarInt(recipe.modifiers.size());
            for (BrewingModifier modifier : recipe.modifiers) modifier.toNetwork(buffer);
        }
    }
}
