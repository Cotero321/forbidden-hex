package com.example.akaishi.forbidden.forge.brewing;

import com.example.akaishi.forbidden.brewing.*;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder("forbidden_hex")
@PrefixGameTestTemplate(false)
public final class BrewingGameTests {
    private static final BlockPos POT = new BlockPos(2, 2, 2);
    private static final List<Item> DUST_INPUTS = List.of(Items.REDSTONE, Items.GLOWSTONE_DUST, Items.GUNPOWDER, Items.SUGAR);

    private BrewingGameTests() { }

    @GameTest(template = "brewing_empty")
    public static void heatAndFullWater(GameTestHelper helper) {
        CrucibleBlockEntity pot = pot(helper);
        helper.assertTrue(pot.fillWater(999, true) == 999, "Partial water fill failed");
        helper.assertTrue(!pot.accept(new ItemStack(Items.REDSTONE)), "Incomplete water accepted an ingredient");
        helper.assertTrue(pot.fillWater(1, true) == 1, "Final water fill failed");
        helper.setBlock(POT.below(), Blocks.AIR);
        helper.assertTrue(!pot.accept(new ItemStack(Items.REDSTONE)), "Cold crucible accepted an ingredient");
        helper.setBlock(POT.below(), Blocks.FIRE);
        for (Item item : DUST_INPUTS) helper.assertTrue(pot.accept(new ItemStack(item)), "Dust ingredient rejected: " + item);
        helper.assertTrue(pot.commitRecipe() && !pot.hasOutputs(), "Submission produced an instant result");
        helper.assertTrue(!pot.commitRecipe(), "Double submission was accepted");
        tick(helper, pot, 99);
        helper.assertTrue(!pot.hasOutputs() && pot.heatingTicks() == 99, "Result appeared before 100 ticks");
        tick(helper, pot, 1);
        List<ItemStack> outputs = pot.takeOutputs();
        helper.assertTrue(outputs.size() == 1 && outputs.get(0).is(BrewingItems.TRANSMUTATION_DUST.get()), "Dust output missing");
        helper.assertTrue(pot.water() == 0 && pot.steps().isEmpty() && !pot.locked(), "Completed batch did not clear water and inputs");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void resumeAndInterruptHeating(GameTestHelper helper) {
        CrucibleBlockEntity pot = pot(helper);
        prepareDust(helper, pot);
        tick(helper, pot, 40);
        CompoundTag saved = pot.saveWithoutMetadata();
        pot.load(saved);
        helper.assertTrue(pot.brewing() && pot.heatingTicks() == 40 && !pot.corrupt(), "Valid heating state failed to load");
        helper.setBlock(POT.below(), Blocks.AIR);
        tick(helper, pot, 1);
        helper.assertTrue(pot.heatingTicks() == 0 && pot.brewing() && !pot.hasOutputs(), "Loss of fire did not reset continuous heating");
        helper.setBlock(POT.below(), Blocks.FIRE);
        tick(helper, pot, 99);
        helper.assertTrue(!pot.hasOutputs(), "Interrupted heating completed too early");
        tick(helper, pot, 1);
        helper.assertTrue(pot.hasOutputs(), "Restored fire did not finish the batch");
        pot.takeOutputs();
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("Water", 1000);
        pot.load(legacy);
        helper.assertTrue(!pot.corrupt() && !pot.brewing() && pot.water() == 1000, "Legacy crucible state was rejected");
        legacy.putInt("HeatingTicks", -1);
        pot.load(legacy);
        helper.assertTrue(pot.corrupt(), "Illegal progress was accepted");
        helper.assertTrue(pot.accept(new ItemStack(BrewingItems.WOOD_ASH.get())) && !pot.corrupt(), "Wood ash did not recover corrupt state");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void rollbackAndClear(GameTestHelper helper) {
        CrucibleBlockEntity pot = pot(helper);
        prepareDust(helper, pot);
        tick(helper, pot, 20);
        helper.assertTrue(pot.accept(new ItemStack(BrewingItems.GRASS_ASH.get())), "Grass ash rollback failed");
        helper.assertTrue(!pot.brewing() && pot.heatingTicks() == 0 && pot.steps().size() == 3 && pot.water() == 1000,
                "Rollback did not preserve water and previous inputs");
        helper.assertTrue(!pot.commitRecipe(), "Incomplete recipe submitted after rollback");
        helper.assertTrue(pot.accept(new ItemStack(Items.SUGAR)) && pot.commitRecipe(), "Corrected batch could not submit");
        helper.assertTrue(pot.accept(new ItemStack(BrewingItems.WOOD_ASH.get())), "Wood ash clear failed");
        helper.assertTrue(pot.water() == 0 && pot.steps().isEmpty() && !pot.brewing() && !pot.locked(), "Wood ash did not reset the batch");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void poolsAndRecipeSync(GameTestHelper helper) {
        List<CrucibleRecipe> crucibles = helper.getLevel().getRecipeManager().getAllRecipesFor(BrewingRecipes.CRUCIBLE.get());
        helper.assertTrue(crucibles.size() >= 3, "Low-tier crucible recipes did not load");
        BrewingIngredient fume = new BrewingIngredient(Ingredient.of(BrewingItems.FUME.get()), new CompoundTag());
        for (ResourceLocation kind : BrewingFumes.NON_EVIL) helper.assertTrue(fume.test(BrewingFumeItem.create(kind)), "Non-evil fume was rejected");
        helper.assertTrue(!fume.test(BrewingFumeItem.create(BrewingFumes.EVIL_BREATH)), "Random fume slot accepted evil breath");
        helper.assertTrue(!fume.test(new ItemStack(BrewingItems.FUME.get())), "Untyped fume was accepted");
        boolean trees = false, plants = false;
        for (CrucibleRecipe recipe : crucibles) {
            int size = recipe.resultPool().size();
            trees |= size == 5;
            plants |= size == 13;
            if (size == 5 || size == 13) {
                List<ItemStack> conversion = new ArrayList<>(List.of(new ItemStack(size == 5 ? Items.OAK_SAPLING : Items.WHEAT_SEEDS),
                        new ItemStack(Items.EGG), BrewingFumeItem.create(BrewingFumes.EARTH_BREATH),
                        new ItemStack(BrewingItems.TRANSMUTATION_DUST.get())));
                helper.assertTrue(recipe.complete(conversion, BrewingHeat.FIRE), "Vanilla plant conversion entry is unavailable");
                conversion.set(2, BrewingFumeItem.create(BrewingFumes.EVIL_BREATH));
                helper.assertTrue(!recipe.prefix(conversion), "Conversion consumed evil breath");
            }
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                new CrucibleRecipe.Serializer().toNetwork(buffer, recipe);
                CrucibleRecipe copy = new CrucibleRecipe.Serializer().fromNetwork(recipe.id(), buffer);
                helper.assertTrue(copy.steps().size() == recipe.steps().size() && copy.resultPool().size() == size
                        && ItemStack.isSameItemSameTags(copy.result(), recipe.result()) && buffer.readableBytes() == 0,
                        "Crucible recipe sync changed data");
            } finally { buffer.release(); }
            ItemStack chosen = recipe.randomResult(net.minecraft.util.RandomSource.create(42));
            helper.assertTrue(recipe.resultPool().stream().anyMatch(candidate -> ItemStack.isSameItemSameTags(chosen, candidate)),
                    "Random conversion escaped its result pool");
        }
        helper.assertTrue(trees && plants, "Five-tree or thirteen-seed pool missing");
        List<WitchOvenRecipe> ovens = helper.getLevel().getRecipeManager().getAllRecipesFor(BrewingRecipes.OVEN.get());
        helper.assertTrue(ovens.size() >= 22, "Document oven recipes did not load");
        for (WitchOvenRecipe recipe : ovens) {
            helper.assertTrue(Math.abs(recipe.fumeChance() - 0.3) < 0.00001, "Base oven chance differs from design");
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                new WitchOvenRecipe.Serializer().toNetwork(buffer, recipe);
                WitchOvenRecipe copy = new WitchOvenRecipe.Serializer().fromNetwork(recipe.id(), buffer);
                helper.assertTrue(copy.signature().equals(recipe.signature()) && buffer.readableBytes() == 0, "Oven recipe sync changed data");
            } finally { buffer.release(); }
        }
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void potionModifiersAndNbt(GameTestHelper helper) {
        BrewingPotionData data = new BrewingPotionData(2, 1, 1, BrewingPotionData.Delivery.DRINK, false, false,
                List.of(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100)), List.of(2));
        data = modifier(Items.REDSTONE).apply(data);
        data = modifier(Items.REDSTONE_BLOCK).apply(data);
        helper.assertTrue(data.duration() == 3 && data.effects().get(0).getDuration() == 300, "Duration upgrades multiplied instead of replacing the base multiplier");
        data = modifier(Items.GLOWSTONE).apply(data);
        data = modifier(Items.END_CRYSTAL).apply(data);
        helper.assertTrue(data.strength() == 3 && data.effects().get(0).getAmplifier() == 2 && data.usedCapacity() == 2,
                "Strength changed effect budget or failed to apply");
        data = modifier(BrewingItems.MANDRAKE_ROOT.get()).apply(data);
        helper.assertTrue(data.capacity() == 3, "Capacity material did not add its value");
        CompoundTag saved = data.save();
        helper.assertTrue(BrewingPotionData.read(saved).orElseThrow().usedCapacity() == 2, "Effect cost was lost in NBT");
        saved.remove("effect_costs");
        helper.assertTrue(BrewingPotionData.read(saved).isPresent(), "Legacy potion without costs was rejected");
        saved.putString("effect_costs", "invalid");
        helper.assertTrue(BrewingPotionData.read(saved).isEmpty(), "Wrong NBT cost type was accepted");
        boolean exceeded = false;
        try {
            new BrewingPotionData(1, 1, 1, BrewingPotionData.Delivery.DRINK, false, false,
                    List.of(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100)), List.of(2));
        } catch (IllegalArgumentException expected) { exceeded = true; }
        helper.assertTrue(exceeded, "Effect budget overflow was accepted");
        String json = """
                {"kind":"potion","heat":"fire","steps":[{"ingredient":{"item":"minecraft:nether_wart"}}],
                 "result":{"capacity":2,"strength":1,"duration":1,"effects":[{"effect":"minecraft:speed","duration":100,"cost":2}]}}
                """;
        CrucibleRecipe recipe = new CrucibleRecipe.Serializer().fromJson(new ResourceLocation("forbidden_hex", "test_potion"),
                JsonParser.parseString(json).getAsJsonObject());
        List<ItemStack> inputs = new ArrayList<>();
        for (Item item : List.of(Items.NETHER_WART, BrewingItems.MANDRAKE_ROOT.get(), BrewingItems.GLINT_WEED.get(),
                Items.REDSTONE, Items.REDSTONE_BLOCK, Items.GLOWSTONE, Items.END_CRYSTAL, Items.GUNPOWDER, Items.DRAGON_BREATH,
                Items.GOLD_NUGGET, Items.IRON_NUGGET)) inputs.add(new ItemStack(item));
        var evaluation = recipe.evaluate(inputs);
        helper.assertTrue(evaluation.valid() && evaluation.baseSteps() == 1, "Document modifiers did not apply through a potion recipe");
        BrewingPotionData finalData = BrewingPotionData.read(evaluation.result()).orElseThrow();
        helper.assertTrue(finalData.capacity() == 4 && finalData.duration() == 3 && finalData.strength() == 3
                && finalData.delivery() == BrewingPotionData.Delivery.LINGERING && finalData.hideParticles()
                && finalData.triggerPressurePlates(), "Final potion attributes are incorrect");
        inputs.add(new ItemStack(Items.REDSTONE));
        helper.assertTrue(!recipe.evaluate(inputs).valid(), "Duplicate or lower-tier duration material was accepted");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void ovenOutputAndSmelting(GameTestHelper helper) {
        BlockPos relative = new BlockPos(4, 2, 2);
        helper.setBlock(relative, BrewingBlocks.OVEN.get());
        WitchOvenBlockEntity oven = (WitchOvenBlockEntity) helper.getBlockEntity(relative);
        var oak = WitchOvenRecipe.find(new SimpleContainer(new ItemStack(Items.OAK_SAPLING)), helper.getLevel()).orElseThrow();
        helper.assertTrue(oak.result().is(BrewingItems.WOOD_ASH.get())
                && BrewingFumes.EARTH_BREATH.equals(BrewingFumeItem.kind(oak.fume())), "Oak oven mapping differs from design");
        var beef = WitchOvenRecipe.find(new SimpleContainer(new ItemStack(Items.BEEF)), helper.getLevel()).orElseThrow();
        helper.assertTrue(beef.result().is(Items.COOKED_BEEF) && BrewingFumes.EVIL_BREATH.equals(BrewingFumeItem.kind(beef.fume())),
                "Food smelting did not preserve its cooked result");
        var log = WitchOvenRecipe.find(new SimpleContainer(new ItemStack(Items.OAK_LOG)), helper.getLevel()).orElseThrow();
        helper.assertTrue(log.result().is(Items.CHARCOAL), "Log smelting did not preserve charcoal");
        for (String tree : BrewingPlants.TREES) {
            var customLog = WitchOvenRecipe.find(new SimpleContainer(new ItemStack(BrewingPlants.logs().get(tree).get())), helper.getLevel()).orElseThrow();
            helper.assertTrue(customLog.result().is(Items.CHARCOAL)
                    && BrewingFumes.EVIL_BREATH.equals(BrewingFumeItem.kind(customLog.fume())), "Custom wood did not inherit log smelting");
        }
        oven.setProfile(new WitchOvenProfile(2, 0.7, 1));
        oven.setItem(WitchOvenBlockEntity.INPUT, new ItemStack(Items.OAK_SAPLING, 2));
        oven.setItem(WitchOvenBlockEntity.FUEL, new ItemStack(Items.COAL));
        oven.setItem(WitchOvenBlockEntity.BOTTLE, new ItemStack(Items.GLASS_BOTTLE, 2));
        BlockPos absolute = helper.absolutePos(relative);
        for (int i = 0; i < oak.cookingTicks(); i++) WitchOvenBlockEntity.tick(helper.getLevel(), absolute, oven.getBlockState(), oven);
        helper.assertTrue(oven.getItem(WitchOvenBlockEntity.INPUT).isEmpty() && oven.getItem(WitchOvenBlockEntity.RESULT).getCount() == 2
                && oven.getItem(WitchOvenBlockEntity.FUME).getCount() == 2
                && oven.getItem(WitchOvenBlockEntity.BOTTLE).isEmpty(), "Oven batching did not consume one glass bottle per fume");

        BlockPos withoutBottleRelative = relative.east(2);
        helper.setBlock(withoutBottleRelative, BrewingBlocks.OVEN.get());
        WitchOvenBlockEntity withoutBottle = (WitchOvenBlockEntity) helper.getBlockEntity(withoutBottleRelative);
        withoutBottle.setProfile(new WitchOvenProfile(1, 0.7, 1));
        withoutBottle.setItem(WitchOvenBlockEntity.INPUT, new ItemStack(Items.OAK_SAPLING));
        withoutBottle.setItem(WitchOvenBlockEntity.FUEL, new ItemStack(Items.COAL));
        BlockPos withoutBottleAbsolute = helper.absolutePos(withoutBottleRelative);
        for (int i = 0; i < oak.cookingTicks(); i++) WitchOvenBlockEntity.tick(helper.getLevel(), withoutBottleAbsolute,
                withoutBottle.getBlockState(), withoutBottle);
        helper.assertTrue(withoutBottle.getItem(WitchOvenBlockEntity.INPUT).isEmpty()
                && withoutBottle.getItem(WitchOvenBlockEntity.RESULT).getCount() == 1
                && withoutBottle.getItem(WitchOvenBlockEntity.FUME).isEmpty(), "Oven produced fume without a glass bottle");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void projectilePressurePlates(GameTestHelper helper) {
        BlockPos relative = new BlockPos(4, 2, 4);
        helper.setBlock(relative.below(), Blocks.STONE);
        helper.setBlock(relative, Blocks.OAK_PRESSURE_PLATE);
        BlockPos absolute = helper.absolutePos(relative);
        for (boolean trigger : List.of(false, true)) {
            helper.setBlock(relative, Blocks.OAK_PRESSURE_PLATE);
            BrewingPotionData data = new BrewingPotionData(1, 1, 1, BrewingPotionData.Delivery.DIRECT, true, trigger,
                    List.of(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100)));
            ItemStack bottle = new ItemStack(BrewingItems.POTION.get());
            data.write(bottle);
            BrewingPotionProjectile projectile = BrewingEntities.POTION.get().create(helper.getLevel());
            helper.assertTrue(projectile != null, "Potion projectile factory failed");
            projectile.setItem(bottle);
            projectile.setNoGravity(true);
            projectile.setPos(absolute.getX() + 0.5, absolute.getY() + 0.4, absolute.getZ() + 0.5);
            projectile.setDeltaMovement(0, -0.5, 0);
            helper.assertTrue(helper.getLevel().addFreshEntity(projectile), "Projectile failed to spawn");
            projectile.tick();
            helper.assertTrue(helper.getLevel().getBlockState(absolute).getValue(net.minecraft.world.level.block.PressurePlateBlock.POWERED) == trigger,
                    "Iron nugget pressure plate flag did not control the pulse");
            projectile.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void cropsAndTreeGrowth(GameTestHelper helper) {
        BlockPos cropPos = new BlockPos(3, 2, 3);
        helper.setBlock(cropPos.below(), Blocks.FARMLAND);
        helper.setBlock(cropPos.east(), Blocks.GLOWSTONE);
        helper.runAfterDelay(10, () -> checkCropsAndTrees(helper, cropPos));
    }

    private static void checkCropsAndTrees(GameTestHelper helper, BlockPos cropPos) {
        for (String herb : BrewingPlants.HERBS) {
            var crop = (net.minecraft.world.level.block.CropBlock) BrewingPlants.crop(herb);
            helper.assertTrue(BrewingPlants.seed(herb) instanceof net.minecraft.world.item.ItemNameBlockItem,
                    "Seed cannot plant its crop: " + herb);
            helper.setBlock(cropPos, crop.getStateForAge(7));
            var loot = net.minecraft.world.level.block.Block.getDrops(helper.getBlockState(cropPos), helper.getLevel(),
                    helper.absolutePos(cropPos), null);
            helper.assertTrue(loot.stream().anyMatch(stack -> stack.is(BrewingPlants.seed(herb))) && loot.size() >= 2,
                    "Mature crop did not return a seed and ingredient: " + herb);
            helper.setBlock(cropPos, crop.getStateForAge(0));
            helper.assertTrue(crop.canSurvive(helper.getBlockState(cropPos), helper.getLevel(), helper.absolutePos(cropPos)),
                    "Crop cannot survive on farmland: " + herb);
        }
        BlockPos treePos = new BlockPos(8, 2, 8);
        for (String tree : BrewingPlants.TREES) {
            for (int x = 4; x <= 12; x++) for (int z = 4; z <= 12; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.DIRT);
                for (int y = 2; y < 13; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
            }
            var sapling = (net.minecraft.world.level.block.SaplingBlock) BrewingPlants.sapling(tree);
            var state = sapling.defaultBlockState().setValue(net.minecraft.world.level.block.SaplingBlock.STAGE, 1);
            helper.setBlock(treePos, state);
            sapling.advanceTree(helper.getLevel(), helper.absolutePos(treePos), state, net.minecraft.util.RandomSource.create(42));
            helper.assertTrue(helper.getBlockState(treePos).is(BrewingPlants.logs().get(tree).get()),
                    "Sapling could not grow its configured tree: " + tree);
        }
        helper.succeed();
    }

    private static CrucibleBlockEntity pot(GameTestHelper helper) {
        helper.setBlock(POT.below().below(), Blocks.NETHERRACK);
        helper.setBlock(POT.below(), Blocks.FIRE);
        helper.setBlock(POT, BrewingBlocks.CRUCIBLE.get());
        return (CrucibleBlockEntity) helper.getBlockEntity(POT);
    }

    private static void prepareDust(GameTestHelper helper, CrucibleBlockEntity pot) {
        helper.assertTrue(pot.fillWater(1000, true) == 1000, "Could not fill crucible");
        for (Item item : DUST_INPUTS) helper.assertTrue(pot.accept(new ItemStack(item)), "Could not add dust ingredient");
        helper.assertTrue(pot.commitRecipe(), "Could not submit dust recipe");
    }

    private static void tick(GameTestHelper helper, CrucibleBlockEntity pot, int ticks) {
        for (int i = 0; i < ticks; i++) CrucibleBlockEntity.tick(helper.getLevel(), pot.getBlockPos(), pot.getBlockState(), pot);
    }

    private static BrewingModifier modifier(Item item) {
        return BrewingModifiers.defaults().stream().filter(entry -> entry.ingredient().test(new ItemStack(item))).findFirst().orElseThrow();
    }
}
