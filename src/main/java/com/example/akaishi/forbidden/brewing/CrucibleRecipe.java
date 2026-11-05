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
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

public record CrucibleRecipe(ResourceLocation id, List<BrewingIngredient> steps, BrewingHeat heat,
                             ItemStack result, boolean potion, List<BrewingModifier> modifiers,
                             List<ItemStack> resultPool, int outputCount, BottlePolicy bottles) implements Recipe<Container> {
    public CrucibleRecipe {
        if (id == null || steps == null || steps.isEmpty() || steps.size() > CrucibleBlockEntity.MAX_STEPS
                || heat == null || heat == BrewingHeat.NONE || result == null || result.isEmpty()
                || result.getCount() < 1 || result.getCount() > result.getMaxStackSize()
                || modifiers == null || modifiers.size() > 64 || resultPool == null || resultPool.isEmpty()
                || resultPool.size() > 64) {
            throw new IllegalArgumentException("Invalid crucible recipe");
        }
        for (ItemStack candidate : resultPool) {
            if (candidate == null || candidate.isEmpty() || candidate.getCount() < 1
                    || candidate.getCount() > candidate.getMaxStackSize()) {
                throw new IllegalArgumentException("Invalid crucible result pool");
            }
            if (candidate.hasTag() && candidate.getTag().contains("forbidden_hex_crucible_batch")) {
                throw new IllegalArgumentException("Reserved crucible batch metadata in result");
            }
        }
        if (result.hasTag() && result.getTag().contains("forbidden_hex_crucible_batch")) {
            throw new IllegalArgumentException("Reserved crucible batch metadata in result");
        }
        if (potion && (result.getCount() != 1 || resultPool.size() != 1
                || !result.is(BrewingItems.POTION.get()) || BrewingPotionData.read(result).isEmpty())) {
            throw new IllegalArgumentException("Invalid crucible potion");
        }
        if (!potion && !modifiers.isEmpty()) throw new IllegalArgumentException("Invalid crucible modifiers");
        if (bottles == null || outputCount < 1 || outputCount > result.getMaxStackSize() * 4
                || bottles.output() != 0 && bottles.output() != outputCount
                || (potion || resultPool.size() > 1) && (outputCount != result.getCount() || !bottles.equals(BottlePolicy.NONE))) {
            throw new IllegalArgumentException("Invalid crucible batch contract");
        }
        steps = List.copyOf(steps);
        modifiers = List.copyOf(modifiers);
        result = result.copy();
        resultPool = resultPool.stream().map(ItemStack::copy).toList();
        if (potion) {
            int effectCount = BrewingPotionData.read(result).orElseThrow().effects().size();
            for (BrewingModifier modifier : modifiers) {
                if (modifier.kind() == BrewingModifier.Kind.STRENGTH && !modifier.amplifiers().isEmpty()
                        && modifier.amplifiers().size() != effectCount) {
                    throw new IllegalArgumentException("Strength amplifiers must match the effect list");
                }
            }
        }
    }

    public CrucibleRecipe(ResourceLocation id, List<BrewingIngredient> steps, BrewingHeat heat,
                          ItemStack result, boolean potion, List<BrewingModifier> modifiers, List<ItemStack> resultPool) {
        this(id, steps, heat, result, potion, modifiers, resultPool, result.getCount(), BottlePolicy.NONE);
    }

    public CrucibleRecipe(ResourceLocation id, List<BrewingIngredient> steps, BrewingHeat heat,
                          ItemStack result, boolean potion, List<BrewingModifier> modifiers) {
        this(id, steps, heat, result, potion, modifiers, List.of(result));
    }

    public CrucibleRecipe(ResourceLocation id, List<BrewingIngredient> steps, BrewingHeat heat,
                          ItemStack result, boolean potion) {
        this(id, steps, heat, result, potion, List.of(), List.of(result));
    }

    @Override public ItemStack result() { return result.copy(); }
    @Override public List<ItemStack> resultPool() { return resultPool.stream().map(ItemStack::copy).toList(); }
    public ItemStack randomResult(RandomSource random) {
        return resultPool.get(resultPool.size() == 1 ? 0 : random.nextInt(resultPool.size())).copy();
    }
    public record Evaluation(boolean valid, int baseSteps, ItemStack result) { }

    public boolean slottedOnly() { return !bottles.equals(BottlePolicy.NONE) || outputCount != result.getCount(); }

    public List<ItemStack> outputs(ItemStack evaluated) {
        List<ItemStack> outputs = new ArrayList<>();
        if (potion) {
            for (int i = 0; i < 4; i++) outputs.add(evaluated.copy());
        } else {
            for (int remaining = outputCount; remaining > 0; remaining -= evaluated.getMaxStackSize()) {
                outputs.add(evaluated.copyWithCount(Math.min(remaining, evaluated.getMaxStackSize())));
            }
        }
        return List.copyOf(outputs);
    }

    public record ContainerPlan(int bottles, List<ItemStack> returns) { }

    public record BottlePolicy(int output, int minimumReturns) {
        public static final BottlePolicy NONE = new BottlePolicy(0, 0);
        public static final BottlePolicy POTION = new BottlePolicy(4, 0);

        public BottlePolicy {
            if (output < 0 || output > 64 || minimumReturns < 0 || minimumReturns > 64 || output + minimumReturns > 64) {
                throw new IllegalArgumentException("Invalid crucible bottle policy");
            }
        }

        public ContainerPlan settle(List<ItemStack> materials) {
            List<ItemStack> returns = new ArrayList<>();
            int needed = output;
            int returnedBottles = 0;
            for (ItemStack stack : materials) {
                ItemStack container = stack.is(net.minecraft.world.item.Items.POTION)
                        ? new ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE) : stack.getCraftingRemainingItem().copy();
                if (container.is(net.minecraft.world.item.Items.GLASS_BOTTLE)) {
                    int reused = Math.min(needed, container.getCount());
                    needed -= reused;
                    container.shrink(reused);
                    returnedBottles += container.getCount();
                }
                if (!container.isEmpty()) returns.add(container);
            }
            int supplementedReturns = Math.max(0, minimumReturns - returnedBottles);
            if (supplementedReturns > 0) returns.add(new ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE, supplementedReturns));
            return new ContainerPlan(needed + supplementedReturns, List.copyOf(returns));
        }
    }

    public Evaluation evaluate(List<ItemStack> history) {
        int baseSteps = 0;
        int capacityTier = 0;
        EnumSet<BrewingModifier.Kind> applied = EnumSet.noneOf(BrewingModifier.Kind.class);
        java.util.Set<BrewingModifier> used = new java.util.HashSet<>();
        List<BrewingModifier> available = new ArrayList<>(modifiers);
        if (potion) available.addAll(BrewingModifiers.defaults());
        BrewingPotionData properties = potion ? BrewingPotionData.read(result).orElseThrow() : null;
        for (ItemStack input : history) {
            if (baseSteps < steps.size() && steps.get(baseSteps).test(input)) {
                baseSteps++;
                continue;
            }
            List<BrewingModifier> choices = modifiers.stream().filter(modifier -> modifier.ingredient().test(input)).toList();
            if (choices.isEmpty()) choices = available.stream().filter(modifier -> modifier.ingredient().test(input)).toList();
            if (choices.size() != 1 || properties == null) return new Evaluation(false, baseSteps, ItemStack.EMPTY);
            BrewingModifier modifier = choices.get(0);
            if (modifier.kind() == BrewingModifier.Kind.CAPACITY) {
                if (modifier.value() < capacityTier) return new Evaluation(false, baseSteps, ItemStack.EMPTY);
                capacityTier = modifier.value();
            }
            boolean staircase = modifier.kind() == BrewingModifier.Kind.CAPACITY
                    || modifier.kind() == BrewingModifier.Kind.STRENGTH || modifier.kind() == BrewingModifier.Kind.DURATION
                    || modifier.kind() == BrewingModifier.Kind.DELIVERY;
            if (!used.add(modifier) || !staircase && !applied.add(modifier.kind())) {
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

    public Evaluation evaluateMaterials(List<ItemStack> materials) {
        if (materials == null || materials.size() > (potion ? 25 : 9)) return new Evaluation(false, 0, ItemStack.EMPTY);
        int[] assigned = new int[steps.size()];
        java.util.Arrays.fill(assigned, -1);
        List<ItemStack> extras = new ArrayList<>();
        int matched = 0;
        for (int input = 0; input < materials.size(); input++) {
            ItemStack stack = materials.get(input);
            if (stack == null || stack.isEmpty() || stack.getCount() != 1
                    || stack.hasTag() && stack.getTag().sizeInBytes() > 65536) return new Evaluation(false, matched, ItemStack.EMPTY);
            if (assignMaterial(input, materials, assigned, new boolean[steps.size()])) matched++;
            else extras.add(stack);
        }
        BrewingPotionData properties = potion ? BrewingPotionData.read(result).orElseThrow() : null;
        List<BrewingModifier> available = new ArrayList<>(modifiers);
        if (potion) available.addAll(BrewingModifiers.defaults());
        int deliveries = 0;
        for (ItemStack extra : extras) {
            List<BrewingModifier> choices = modifiers.stream().filter(modifier -> modifier.ingredient().test(extra)).toList();
            if (choices.isEmpty()) choices = available.stream().filter(modifier -> modifier.ingredient().test(extra)).toList();
            if (choices.size() != 1 || properties == null) return new Evaluation(false, matched, ItemStack.EMPTY);
            BrewingModifier modifier = choices.get(0);
            if (modifier.kind() == BrewingModifier.Kind.DELIVERY && ++deliveries > 1) return new Evaluation(false, matched, ItemStack.EMPTY);
            try {
                properties = modifier.applyMaximum(properties);
            } catch (IllegalArgumentException | ArithmeticException exception) {
                return new Evaluation(false, matched, ItemStack.EMPTY);
            }
        }
        ItemStack output = result();
        if (properties != null) {
            try {
                properties.currentRules().write(output);
            } catch (IllegalArgumentException exception) {
                return new Evaluation(false, matched, ItemStack.EMPTY);
            }
        }
        return new Evaluation(true, matched, output);
    }

    private boolean assignMaterial(int input, List<ItemStack> materials, int[] assigned, boolean[] visited) {
        for (int step = 0; step < steps.size(); step++) {
            if (visited[step] || !steps.get(step).test(materials.get(input))) continue;
            visited[step] = true;
            if (assigned[step] < 0 || assignMaterial(assigned[step], materials, assigned, visited)) {
                assigned[step] = input;
                return true;
            }
        }
        return false;
    }

    public boolean acceptsIngredient(ItemStack input) {
        return steps.stream().anyMatch(ingredient -> ingredient.test(input))
                || modifiers.stream().anyMatch(modifier -> modifier.ingredient().test(input))
                || potion && BrewingModifiers.defaults().stream().anyMatch(modifier -> modifier.ingredient().test(input));
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
            List<ItemStack> resultPool;
            if (potion) {
                result = new ItemStack(BrewingItems.POTION.get());
                BrewingRecipeIO.potion(GsonHelper.getAsJsonObject(json, "result")).write(result);
                resultPool = List.of(result);
            } else if (json.has("results")) {
                if (GsonHelper.getAsJsonArray(json, "results").size() > 64) throw new JsonSyntaxException("Too many crucible results");
                resultPool = new ArrayList<>();
                for (var entry : GsonHelper.getAsJsonArray(json, "results")) resultPool.add(BrewingRecipeIO.stack(entry.getAsJsonObject()));
                if (resultPool.isEmpty()) throw new JsonSyntaxException("Empty crucible result pool");
                result = resultPool.get(0);
            } else {
                result = BrewingRecipeIO.stack(GsonHelper.getAsJsonObject(json, "result"));
                resultPool = List.of(result);
            }
            List<BrewingModifier> modifiers = new ArrayList<>();
            if (json.has("modifiers")) for (var entry : GsonHelper.getAsJsonArray(json, "modifiers")) {
                if (modifiers.size() >= 64) throw new JsonSyntaxException("Too many crucible modifiers");
                modifiers.add(BrewingModifier.fromJson(entry.getAsJsonObject()));
            }
            try {
                JsonObject containers = GsonHelper.getAsJsonObject(json, "bottles", new JsonObject());
                BottlePolicy bottles = new BottlePolicy(GsonHelper.getAsInt(containers, "output", 0),
                        GsonHelper.getAsInt(containers, "returns", 0));
                return new CrucibleRecipe(id, steps,
                        BrewingHeat.valueOf(GsonHelper.getAsString(json, "heat", "fire").toUpperCase(Locale.ROOT)),
                        result, potion, modifiers, resultPool, GsonHelper.getAsInt(json, "output_count", result.getCount()), bottles);
            } catch (IllegalArgumentException exception) {
                throw new JsonSyntaxException("Invalid crucible recipe", exception);
            }
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
            int outputCount = result.getCount();
            BottlePolicy bottles = BottlePolicy.NONE;
            String batchKey = "forbidden_hex_crucible_batch";
            if (result.hasTag() && result.getTag().contains(batchKey)) {
                if (!(result.getTag().get(batchKey) instanceof net.minecraft.nbt.CompoundTag batch)
                        || !batch.contains("Version", net.minecraft.nbt.Tag.TAG_INT) || batch.getInt("Version") != 1
                        || !batch.contains("OutputCount", net.minecraft.nbt.Tag.TAG_INT)
                        || !batch.contains("OutputBottles", net.minecraft.nbt.Tag.TAG_INT)
                        || !batch.contains("ReturnedBottles", net.minecraft.nbt.Tag.TAG_INT)) {
                    throw new IllegalArgumentException("Invalid crucible batch contract");
                }
                outputCount = batch.getInt("OutputCount");
                bottles = new BottlePolicy(batch.getInt("OutputBottles"), batch.getInt("ReturnedBottles"));
                result.removeTagKey(batchKey);
            }
            List<ItemStack> resultPool = new ArrayList<>();
            String poolKey = "forbidden_hex_crucible_results";
            if (result.hasTag() && result.getTag().contains(poolKey)) {
                if (!(result.getTag().get(poolKey) instanceof net.minecraft.nbt.ListTag pool)
                        || pool.isEmpty() || pool.size() > 64 || pool.getElementType() != net.minecraft.nbt.Tag.TAG_COMPOUND) {
                    throw new IllegalArgumentException("Invalid crucible result pool");
                }
                for (int i = 0; i < pool.size(); i++) resultPool.add(ItemStack.of(pool.getCompound(i)));
                result.removeTagKey(poolKey);
            } else resultPool.add(result);
            return new CrucibleRecipe(id, steps, heat, result, potion, modifiers, resultPool, outputCount, bottles);
        }

        @Override public void toNetwork(FriendlyByteBuf buffer, CrucibleRecipe recipe) {
            buffer.writeVarInt(recipe.steps.size());
            for (BrewingIngredient step : recipe.steps) step.toNetwork(buffer);
            buffer.writeEnum(recipe.heat);
            ItemStack networkResult = recipe.result();
            if (recipe.slottedOnly()) {
                net.minecraft.nbt.CompoundTag batch = new net.minecraft.nbt.CompoundTag();
                batch.putInt("Version", 1);
                batch.putInt("OutputCount", recipe.outputCount);
                batch.putInt("OutputBottles", recipe.bottles.output());
                batch.putInt("ReturnedBottles", recipe.bottles.minimumReturns());
                networkResult.getOrCreateTag().put("forbidden_hex_crucible_batch", batch);
            }
            if (recipe.resultPool.size() > 1) {
                net.minecraft.nbt.ListTag pool = new net.minecraft.nbt.ListTag();
                for (ItemStack result : recipe.resultPool) pool.add(result.save(new net.minecraft.nbt.CompoundTag()));
                networkResult.getOrCreateTag().put("forbidden_hex_crucible_results", pool);
            }
            buffer.writeItem(networkResult);
            buffer.writeBoolean(recipe.potion);
            buffer.writeVarInt(recipe.modifiers.size());
            for (BrewingModifier modifier : recipe.modifiers) modifier.toNetwork(buffer);
        }
    }
}
