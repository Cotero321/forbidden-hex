package com.example.akaishi.forbidden.forge.brewing;

import com.example.akaishi.forbidden.brewing.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@GameTestHolder("forbidden_hex")
@PrefixGameTestTemplate(false)
public final class CrucibleMenuGameTests {
    private static final BlockPos POT = new BlockPos(3, 3, 3);
    private CrucibleMenuGameTests() { }
    private static ItemStack item(Item item) { return new ItemStack(item); }
    private static CrucibleBlockEntity pot(GameTestHelper helper) {
        helper.setBlock(POT.below(2), Blocks.NETHERRACK);
        helper.setBlock(POT.below(), Blocks.FIRE);
        helper.setBlock(POT, BrewingBlocks.CRUCIBLE.get());
        return (CrucibleBlockEntity) helper.getBlockEntity(POT);
    }
    private static void tick(GameTestHelper helper, CrucibleBlockEntity pot, int count) {
        for (int i = 0; i < count; i++) CrucibleBlockEntity.tick(helper.getLevel(), pot.getBlockPos(), pot.getBlockState(), pot);
    }

    private static void randomCraftingBatch(GameTestHelper helper, String path, Item ingredient, int poolSize) {
        var recipe = helper.getLevel().getRecipeManager().getAllRecipesFor(BrewingRecipes.CRUCIBLE.get()).stream()
                .filter(candidate -> candidate.id().getPath().equals("brewing/" + path)).findFirst().orElseThrow();
        helper.assertTrue(recipe.resultPool().size() == poolSize && recipe.heat() == BrewingHeat.FIRE,
                "Random conversion pool or heat changed");
        var pot = pot(helper);
        var inventory = pot.inventory();
        helper.assertTrue(pot.slotsEnabled() && inventory.setMode(1), "New machine could not select crafting mode");
        List<ItemStack> inputs = List.of(item(ingredient), item(Items.EGG), BrewingFumeItem.create(BrewingFumes.EARTH_BREATH),
                item(BrewingItems.TRANSMUTATION_DUST.get()));
        var evaluation = recipe.evaluateMaterials(inputs);
        helper.assertTrue(evaluation.valid() && evaluation.baseSteps() == recipe.steps().size(), "Random conversion fixture did not satisfy its recipe");
        for (int i = 0; i < inputs.size(); i++) inventory.setItem(CrucibleInventory.CRAFT_START + i, inputs.get(i).copy());
        helper.assertTrue(inventory.materials().size() == inputs.size(), "Random conversion fixture input was rejected");
        inventory.setItem(CrucibleInventory.BOTTLE, new ItemStack(Items.GLASS_BOTTLE, 3));
        CompoundTag idle = inventory.save();
        var random = net.minecraft.util.RandomSource.create(2);
        helper.getLevel().random.setSeed(2);
        for (int i = 0; i < 8; i++) {
            var preview = inventory.plan(i % 2 == 0);
            helper.assertTrue(preview.status() == CrucibleInventory.READY && preview.outputs().size() == 1
                    && ItemStack.isSameItemSameTags(preview.outputs().get(0), recipe.resultPool().get(0))
                    && preview.bottles() == 0 && preview.returns().isEmpty(), "Random preview was incomplete or rerolled");
            helper.assertTrue(inventory.status() == CrucibleInventory.WATER && !inventory.start(null)
                    && inventory.save().equals(idle), "Dry submission changed or consumed a random batch");
        }
        helper.assertTrue(helper.getLevel().random.nextLong() == random.nextLong(), "Preview or failed submission consumed randomness");
        pot.fillWater(1000, true);
        helper.setBlock(POT.below(), Blocks.AIR);
        random.setSeed(2);
        helper.getLevel().random.setSeed(2);
        helper.assertTrue(inventory.status() == CrucibleInventory.HEAT && !inventory.start(null)
                && inventory.save().equals(idle) && pot.water() == 1000, "Unheated submission changed the random batch");
        helper.assertTrue(helper.getLevel().random.nextLong() == random.nextLong(), "Unheated submission rolled a result");
        helper.setBlock(POT.below(), Blocks.FIRE);
        random.setSeed(2);
        helper.getLevel().random.setSeed(2);
        ItemStack expected = recipe.randomResult(random);
        helper.assertTrue(!ItemStack.isSameItemSameTags(expected, recipe.resultPool().get(0)), "Test seed did not distinguish preview from chosen output");
        helper.assertTrue(inventory.start(null) && !inventory.start(null), "Random batch did not start exactly once");
        for (int i = 0; i < 8; i++) {
            var preview = inventory.plan(i % 2 == 0);
            helper.assertTrue(preview.status() == CrucibleInventory.READY && preview.outputs().size() == 1
                    && ItemStack.isSameItemSameTags(preview.outputs().get(0), expected), "Accepted batch rerolled during preview");
        }
        helper.assertTrue(helper.getLevel().random.nextLong() == random.nextLong(), "Accepted batch did not roll exactly once");
        CompoundTag chosen = inventory.save().getCompound("RandomResult").copy();
        helper.assertTrue(!chosen.isEmpty() && ItemStack.isSameItemSameTags(ItemStack.of(chosen), expected)
                && inventory.materials().size() == inputs.size() && pot.water() == 1000, "Chosen result was not saved before consumption");
        tick(helper, pot, 40);
        pot.load(pot.saveWithoutMetadata());
        helper.assertTrue(!pot.corrupt() && inventory.active() && inventory.progress() == 40
                && inventory.save().getCompound("RandomResult").equals(chosen), "Reload lost the chosen random output");
        helper.setBlock(POT.below(), Blocks.AIR);
        tick(helper, pot, 1);
        helper.assertTrue(inventory.active() && inventory.progress() == 0
                && inventory.save().getCompound("RandomResult").equals(chosen), "Heat reset rerolled the accepted batch");
        helper.setBlock(POT.below(), Blocks.FIRE);
        tick(helper, pot, 99);
        pot.load(pot.saveWithoutMetadata());
        helper.assertTrue(!pot.corrupt() && inventory.active() && inventory.progress() == 99 && !pot.hasOutputs()
                && inventory.save().getCompound("RandomResult").equals(chosen), "Random output appeared early or changed on reload");
        tick(helper, pot, 1);
        pot.load(pot.saveWithoutMetadata());
        var outputs = pot.takeOutputs();
        helper.assertTrue(!pot.corrupt() && outputs.size() == 1 && outputs.get(0).getCount() == expected.getCount()
                && ItemStack.isSameItemSameTags(outputs.get(0), expected)
                && recipe.resultPool().stream().anyMatch(candidate -> ItemStack.isSameItemSameTags(candidate, outputs.get(0)))
                && inventory.materials().isEmpty() && inventory.getItem(CrucibleInventory.BOTTLE).getCount() == 3
                && !inventory.active() && !inventory.pendingReturns() && pot.water() == 0
                && !inventory.save().contains("RandomResult"), "Random batch lost or duplicated output, inputs, water or bottles");
        pot.load(pot.saveWithoutMetadata());
        tick(helper, pot, 2);
        helper.assertTrue(!pot.hasOutputs() && !inventory.start(null) && inventory.materials().isEmpty()
                && inventory.getItem(CrucibleInventory.BOTTLE).getCount() == 3, "Settled random batch could be repeated");
    }

    @GameTest(template = "brewing_empty")
    public static void treeConversionRollsOnceAndSurvivesReload(GameTestHelper helper) {
        randomCraftingBatch(helper, "tree_conversion", Items.OAK_SAPLING, 5);
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void plantConversionRollsOnceAndSurvivesReload(GameTestHelper helper) {
        randomCraftingBatch(helper, "plant_conversion", Items.WHEAT_SEEDS, 13);
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void materialMaximaAndDeliveryUniqueness(GameTestHelper helper) {
        List<ItemStack> materials = new ArrayList<>(List.of(item(Items.GLOW_BERRIES), item(Items.REDSTONE_BLOCK),
                item(Items.GLOWSTONE), item(Items.END_CRYSTAL), item(Items.REDSTONE), item(Items.NETHER_WART)));
        var first = BrewingPotionComposer.evaluateMaterials(materials);
        Collections.reverse(materials);
        var reversed = BrewingPotionComposer.evaluateMaterials(materials);
        helper.assertTrue(first.complete() && reversed.complete() && first.potion().equals(reversed.potion()), "Modifier order changed the potion");
        helper.assertTrue(first.potion().strength() == 3 && first.potion().duration() == 3 && first.potion().capacity() == 2,
                "Strength or duration did not take the maximum");
        materials.add(item(Items.GUNPOWDER));
        helper.assertTrue(BrewingPotionComposer.evaluateMaterials(materials).complete(), "One delivery form rejected");
        materials.add(item(Items.DRAGON_BREATH));
        helper.assertTrue(!BrewingPotionComposer.evaluateMaterials(materials).valid(), "Two forms were accepted");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void sixEffectsAndCapacityAreSeparate(GameTestHelper helper) {
        List<ItemStack> materials = new ArrayList<>();
        for (int i = 0; i < 4; i++) materials.add(item(Items.NETHER_STAR));
        for (Item ingredient : List.of(Items.GLOW_BERRIES, Items.SNOWBALL, Items.COD, Items.FLINT, Items.SUGAR, Items.BLAZE_POWDER)) {
            materials.add(item(ingredient));
        }
        var six = BrewingPotionComposer.evaluateMaterials(materials);
        helper.assertTrue(six.complete() && six.potion().customEffects().size() == 6 && six.potion().capacity() == 16,
                "Six effects or repeated capacity materials rejected");
        materials.add(item(Items.FEATHER));
        helper.assertTrue(!BrewingPotionComposer.evaluateMaterials(materials).valid(), "Seventh effect was silently accepted");
        var incomplete = BrewingPotionComposer.evaluateMaterials(List.of(item(Items.GLOW_BERRIES)));
        helper.assertTrue(incomplete.valid() && !incomplete.complete(), "Valid effect cannot be inserted before a capacity ingredient");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void strengthTimingAndLegacyNbt(GameTestHelper helper) {
        helper.assertTrue(BrewingPotionData.strengthenedTicks(1200, 1) == 1200, "Strength I was shortened");
        helper.assertTrue(BrewingPotionData.strengthenedTicks(1200, 2) == 900, "Strength II was not 45 seconds");
        helper.assertTrue(BrewingPotionData.strengthenedTicks(1200, 3) == 675, "Strength III was not 33.75 seconds");
        helper.assertTrue(BrewingPotionData.strengthenedTicks(100, 5) == 200, "Minimum ten seconds was not enforced");
        var current = BrewingPotionComposer.evaluateMaterials(List.of(item(Items.NETHER_WART), item(Items.GLOW_BERRIES), item(Items.GLOWSTONE))).potion();
        helper.assertTrue(current != null && current.rulesVersion() == 3 && BrewingPotionData.read(current.save()).orElseThrow().equals(current),
                "New potion NBT failed round trip");
        List<MobEffectInstance> seven = new ArrayList<>();
        for (int i = 0; i < 7; i++) seven.add(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 1200));
        var legacy = new BrewingPotionData(7, 1, 1, BrewingPotionData.Delivery.DRINK, false, false, seven);
        helper.assertTrue(BrewingPotionData.read(legacy.save()).orElseThrow().effects().size() == 7,
                "Legacy potion with more than six effects was truncated");
        CompoundTag invalid = legacy.save();
        invalid.putInt("version", 3);
        helper.assertTrue(BrewingPotionData.read(invalid).isEmpty(), "New version allowed a seventh effect");
        var oldCustom = new BrewingPotionData(2, 2, 1, BrewingPotionData.Delivery.DRINK, false, false,
                List.of(), List.of(), List.of(new BrewingEffectEntry(BrewingEffectType.GLOWING)));
        helper.assertTrue(BrewingPotionData.read(oldCustom.save()).orElseThrow().ticks(BrewingEffectType.GLOWING)
                == BrewingEffectType.GLOWING.ticks(2, 1), "Legacy custom effect duration changed");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void previewCommitReloadAndExactlyFour(GameTestHelper helper) {
        CrucibleBlockEntity pot = pot(helper);
        pot.fillWater(1000, true);
        CrucibleInventory inventory = pot.inventory();
        inventory.setItem(0, item(Items.GLOW_BERRIES));
        inventory.setItem(1, item(Items.NETHER_WART));
        helper.assertTrue(inventory.plan(true).outputs().size() == 4 && !pot.hasOutputs() && !inventory.active(),
                "Preview either failed or created real output");
        helper.assertTrue(!inventory.start(null) && !inventory.getItem(0).isEmpty() && pot.water() == 1000,
                "Missing bottles consumed inputs");
        inventory.setItem(CrucibleInventory.BOTTLE, new ItemStack(Items.GLASS_BOTTLE, 4));
        helper.assertTrue(inventory.start(null) && !inventory.start(null), "Submission missing or duplicated");
        helper.assertTrue(inventory.removeItem(0, 1).isEmpty() && !inventory.setMode(1) && pot.drainWater(1000, true) == 0,
                "Active batch allowed inputs, mode or water to change");
        tick(helper, pot, 40);
        pot.load(pot.saveWithoutMetadata());
        helper.assertTrue(!pot.corrupt() && inventory.active() && inventory.progress() == 40, "Valid active slot batch did not reload");
        tick(helper, pot, 59);
        helper.assertTrue(!pot.hasOutputs(), "Output appeared before 100 ticks");
        tick(helper, pot, 1);
        var outputs = pot.takeOutputs();
        helper.assertTrue(outputs.size() == 4 && outputs.stream().allMatch(stack -> ItemStack.isSameItemSameTags(stack, outputs.get(0))),
                "Batch did not produce four identical bottles");
        helper.assertTrue(inventory.getItem(0).isEmpty() && inventory.getItem(1).isEmpty()
                && inventory.getItem(CrucibleInventory.BOTTLE).isEmpty() && pot.water() == 0 && !inventory.active(),
                "Inputs, water or bottles were not consumed exactly once");
        tick(helper, pot, 2);
        helper.assertTrue(!pot.hasOutputs(), "Reload or later tick duplicated output");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void heatResetModeAndRecovery(GameTestHelper helper) {
        CrucibleBlockEntity pot = pot(helper);
        var inventory = pot.inventory();
        pot.fillWater(1000, true);
        inventory.setItem(0, item(Items.NETHER_WART));
        inventory.setItem(1, item(Items.GLOW_BERRIES));
        inventory.setItem(CrucibleInventory.BOTTLE, new ItemStack(Items.GLASS_BOTTLE, 4));
        helper.assertTrue(!inventory.setMode(1) && !pot.accept(item(BrewingItems.WOOD_ASH.get())), "Mode switch or ash cleared slotted inputs");
        helper.assertTrue(inventory.start(null), "Could not start");
        tick(helper, pot, 25);
        helper.setBlock(POT.below(), Blocks.AIR);
        tick(helper, pot, 1);
        helper.assertTrue(inventory.progress() == 0 && inventory.active() && pot.water() == 1000, "Lost heat failed to reset safely");
        helper.setBlock(POT.below(), Blocks.FIRE);
        tick(helper, pot, 99);
        helper.assertTrue(!pot.hasOutputs(), "Resumed batch finished early");
        tick(helper, pot, 1);
        pot.takeOutputs();
        helper.assertTrue(inventory.setMode(1) && inventory.mode() == 1, "Empty finished machine could not switch modes");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void previewCannotBeTaken(GameTestHelper helper) {
        CrucibleBlockEntity pot = pot(helper);
        pot.inventory().setItem(0, item(Items.NETHER_WART));
        pot.inventory().setItem(1, item(Items.GLOW_BERRIES));
        var player = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(helper.getLevel());
        player.setPos(pot.getBlockPos().getX() + 0.5, pot.getBlockPos().getY() + 1, pot.getBlockPos().getZ() + 0.5);
        CrucibleMenu menu = new CrucibleMenu(91, player.getInventory(), pot);
        menu.broadcastChanges();
        for (int index = CrucibleMenu.PREVIEW_START; index < CrucibleMenu.PLAYER_START; index++) {
            helper.assertTrue(menu.getSlot(index).hasItem() && !menu.getSlot(index).mayPickup(player), "Preview missing or extractable");
            helper.assertTrue(menu.quickMoveStack(player, index).isEmpty(), "Shift extracted a preview");
            for (ClickType type : List.of(ClickType.PICKUP, ClickType.QUICK_MOVE, ClickType.SWAP, ClickType.CLONE, ClickType.THROW, ClickType.PICKUP_ALL)) {
                menu.clicked(index, 0, type, player);
                helper.assertTrue(menu.getCarried().isEmpty() && menu.getSlot(index).hasItem(), "Click transferred a preview");
            }
        }
        helper.assertTrue(menu.slots.size() == CrucibleMenu.PLAYER_START + 36, "Slot indices changed");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void ingredientBottleIsReusedOnce(GameTestHelper helper) {
        CrucibleBlockEntity pot = pot(helper);
        pot.fillWater(1000, true);
        var inventory = pot.inventory();
        inventory.setItem(0, item(Items.NETHER_WART));
        inventory.setItem(1, net.minecraft.world.item.alchemy.PotionUtils.setPotion(item(Items.POTION), net.minecraft.world.item.alchemy.Potions.WATER));
        inventory.setItem(CrucibleInventory.BOTTLE, new ItemStack(Items.GLASS_BOTTLE, 3));
        helper.assertTrue(inventory.plan(true).bottles() == 3 && inventory.start(null), "Ingredient bottle was not credited");
        tick(helper, pot, 100);
        helper.assertTrue(pot.takeOutputs().size() == 4 && !inventory.pendingReturns()
                && inventory.getItem(CrucibleInventory.BOTTLE).isEmpty(), "Ingredient bottle was returned and reused twice");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void fourWaterBottlesAndOverflow(GameTestHelper helper) {
        CrucibleBlockEntity pot = pot(helper);
        var player = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(helper.getLevel());
        player.getAbilities().instabuild = false;
        BlockPos pos = pot.getBlockPos();
        var hit = new net.minecraft.world.phys.BlockHitResult(pos.getCenter(), net.minecraft.core.Direction.UP, pos, false);
        for (int i = 1; i <= 4; i++) {
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    net.minecraft.world.item.alchemy.PotionUtils.setPotion(item(Items.POTION), net.minecraft.world.item.alchemy.Potions.WATER));
            pot.getBlockState().getBlock().use(pot.getBlockState(), helper.getLevel(), pos, player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
            helper.assertTrue(pot.water() == i * 250 && player.getMainHandItem().is(Items.GLASS_BOTTLE), "Water bottle did not add exactly 250 mB and return a bottle");
        }
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                net.minecraft.world.item.alchemy.PotionUtils.setPotion(item(Items.POTION), net.minecraft.world.item.alchemy.Potions.WATER));
        pot.getBlockState().getBlock().use(pot.getBlockState(), helper.getLevel(), pos, player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(pot.water() == 1000 && player.getMainHandItem().is(Items.POTION), "Full pot consumed overflow water");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void quickMoveAndDragRespectSingleInputs(GameTestHelper helper) {
        CrucibleBlockEntity pot = pot(helper);
        var player = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(helper.getLevel());
        player.getInventory().clearContent();
        player.getInventory().setItem(9, new ItemStack(Items.NETHER_WART, 5));
        var menu = new CrucibleMenu(93, player.getInventory(), pot);
        menu.clicked(CrucibleMenu.PLAYER_START, 0, ClickType.QUICK_MOVE, player);
        for (int i = 0; i < CrucibleInventory.BOTTLE; i++) helper.assertTrue(menu.getSlot(i).getItem().getCount() <= 1, "Material slot stacked more than one");
        helper.assertTrue(pot.inventory().materials().size() == 5 && player.getInventory().getItem(9).isEmpty(), "Quick move lost or duplicated materials");
        menu.setCarried(new ItemStack(Items.GLASS_BOTTLE, 4));
        menu.clicked(-999, 0, ClickType.QUICK_CRAFT, player);
        menu.clicked(CrucibleMenu.PREVIEW_START, 1, ClickType.QUICK_CRAFT, player);
        menu.clicked(CrucibleInventory.CRAFT_START, 1, ClickType.QUICK_CRAFT, player);
        menu.clicked(CrucibleInventory.BOTTLE, 1, ClickType.QUICK_CRAFT, player);
        menu.clicked(-999, 2, ClickType.QUICK_CRAFT, player);
        helper.assertTrue(menu.getSlot(CrucibleInventory.BOTTLE).getItem().getCount() == 4
                && menu.getSlot(CrucibleInventory.CRAFT_START).getItem().isEmpty(), "Drag reached preview or hidden crafting slots");
        player.getInventory().clearContent();
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void invalidLoadIsQuarantinedAndBottleNbtRejected(GameTestHelper helper) {
        CrucibleBlockEntity pot = pot(helper);
        var inventory = pot.inventory();
        inventory.setItem(0, item(Items.NETHER_WART));
        ItemStack oversized = item(Items.GLASS_BOTTLE);
        oversized.getOrCreateTag().putString("large", "x".repeat(65536));
        helper.assertTrue(!inventory.canPlaceItem(CrucibleInventory.BOTTLE, oversized), "Oversized bottle was accepted for saving");
        CompoundTag saved = pot.saveWithoutMetadata();
        saved.getCompound("SlotInventory").putString("Mode", "invalid");
        pot.load(saved);
        helper.assertTrue(pot.corrupt(), "Wrong field type silently defaulted");
        CompoundTag quarantined = pot.saveWithoutMetadata().getCompound("QuarantinedBatch");
        helper.assertTrue(quarantined.getCompound("SlotInventory").getString("Mode").equals("invalid")
                && quarantined.getCompound("SlotInventory").getList("Items", 10).size() == 1, "Corrupt input NBT was lost");
        pot.load(pot.saveWithoutMetadata());
        helper.assertTrue(pot.corrupt() && pot.preservesBatchOnBreak() && pot.takeContents().isEmpty(), "Reload unlocked or extracted corrupt data");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void legacyBottledInputsStayWithMachine(GameTestHelper helper) {
        CrucibleBlockEntity pot = pot(helper);
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("Water", 1000);
        legacy.putBoolean("Locked", true);
        ListTag steps = new ListTag();
        steps.add(net.minecraft.world.item.alchemy.PotionUtils.setPotion(item(Items.POTION), net.minecraft.world.item.alchemy.Potions.WATER).save(new CompoundTag()));
        legacy.put("Steps", steps);
        pot.load(legacy);
        helper.assertTrue(!pot.corrupt() && pot.preservesBatchOnBreak() && !pot.canRecoverLegacy() && pot.takeContents().isEmpty(),
                "Legacy ingredient duplicated its already-returned bottle");
        ItemStack machine = new ItemStack(BrewingBlocks.CRUCIBLE.get());
        pot.saveToItem(machine);
        pot.load(machine.getTagElement("BlockEntityTag"));
        helper.assertTrue(pot.steps().size() == 1 && !pot.corrupt() && !pot.slotsEnabled(), "Machine item did not preserve legacy input");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void menuRejectsStaleAndDistantSubmissions(GameTestHelper helper) {
        CrucibleBlockEntity pot = pot(helper);
        pot.fillWater(1000, true);
        pot.inventory().setItem(0, item(Items.NETHER_WART));
        pot.inventory().setItem(1, item(Items.GLOW_BERRIES));
        pot.inventory().setItem(CrucibleInventory.BOTTLE, new ItemStack(Items.GLASS_BOTTLE, 4));
        var player = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(helper.getLevel());
        var previous = player.containerMenu;
        var first = new CrucibleMenu(94, player.getInventory(), pot);
        var second = new CrucibleMenu(95, player.getInventory(), pot);
        player.containerMenu = first;
        player.setPos(pot.getBlockPos().getX() + 20, pot.getBlockPos().getY(), pot.getBlockPos().getZ());
        helper.assertTrue(!first.clickMenuButton(player, 2), "Distant submission was accepted");
        player.setPos(pot.getBlockPos().getX(), pot.getBlockPos().getY(), pot.getBlockPos().getZ());
        helper.assertTrue(!second.clickMenuButton(player, 2) && !first.clickMenuButton(player, 99), "Stale menu or unknown action was accepted");
        helper.assertTrue(first.clickMenuButton(player, 2) && !first.clickMenuButton(player, 2) && !first.clickMenuButton(player, 1),
                "Repeat submission or mode switch bypassed active batch");
        player.containerMenu = previous;
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void transmutationDustUsesAnyFumeAndSeeds(GameTestHelper helper) {
        var recipe = helper.getLevel().getRecipeManager().getAllRecipesFor(BrewingRecipes.CRUCIBLE.get()).stream()
                .filter(candidate -> candidate.id().getPath().equals("brewing/transmutation_dust")).findFirst().orElseThrow();
        for (var kind : BrewingFumes.NON_EVIL) {
            helper.assertTrue(recipe.evaluateMaterials(List.of(item(Items.BONE_MEAL), item(Items.WHEAT_SEEDS), item(Items.EGG),
                    BrewingFumeItem.create(kind))).baseSteps() == 4, "An allowed fume could not make dust");
        }
        ItemStack evil = BrewingFumeItem.create(BrewingFumes.EVIL_BREATH);
        var evaluation = recipe.evaluateMaterials(List.of(item(Items.BONE_MEAL), item(Items.WHEAT_SEEDS), item(Items.EGG), evil));
        helper.assertTrue(evaluation.valid() && evaluation.baseSteps() == 4 && evaluation.result().getCount() == 4, "Any-fume dust rejected evil fume or produced the wrong count");
        helper.assertTrue(!recipe.evaluateMaterials(List.of(item(Items.BONE_MEAL), item(Items.DIRT), item(Items.EGG), evil)).valid(), "Ordinary block was treated as a seed");
        CrucibleBlockEntity pot = pot(helper);
        var inventory = pot.inventory();
        helper.assertTrue(inventory.setMode(1), "Could not select crafting mode");
        inventory.setItem(CrucibleInventory.CRAFT_START, item(Items.BONE_MEAL));
        inventory.setItem(CrucibleInventory.CRAFT_START + 1, item(Items.WHEAT_SEEDS));
        inventory.setItem(CrucibleInventory.CRAFT_START + 2, item(Items.EGG));
        inventory.setItem(CrucibleInventory.CRAFT_START + 3, evil);
        pot.fillWater(1000, true);
        helper.assertTrue(inventory.plan(true).bottles() == 0 && inventory.start(null), "Crafting dust incorrectly required empty bottles");
        tick(helper, pot, 100);
        var outputs = pot.takeOutputs();
        helper.assertTrue(outputs.size() == 1 && outputs.get(0).is(BrewingItems.TRANSMUTATION_DUST.get())
                && outputs.get(0).getCount() == 4 && inventory.isEmpty() && pot.water() == 0, "Dust transaction lost inputs or produced the wrong amount");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void breakingActiveMachineReturnsInputsOnce(GameTestHelper helper) {
        CrucibleBlockEntity pot = pot(helper);
        pot.fillWater(1000, true);
        pot.inventory().setItem(0, item(Items.NETHER_WART));
        pot.inventory().setItem(1, item(Items.GLOW_BERRIES));
        pot.inventory().setItem(CrucibleInventory.BOTTLE, new ItemStack(Items.GLASS_BOTTLE, 4));
        helper.assertTrue(pot.inventory().start(null), "Could not start break test batch");
        tick(helper, pot, 40);
        BlockPos pos = pot.getBlockPos();
        helper.assertTrue(helper.getLevel().destroyBlock(pos, true), "Could not break machine");
        var drops = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(pos).inflate(1));
        for (Item type : List.of(Items.NETHER_WART, Items.GLOW_BERRIES, Items.GLASS_BOTTLE)) {
            int expected = type == Items.GLASS_BOTTLE ? 4 : 1;
            helper.assertTrue(drops.stream().filter(entity -> entity.getItem().is(type)).mapToInt(entity -> entity.getItem().getCount()).sum() == expected,
                    "Machine break lost or duplicated " + type);
        }
        helper.assertTrue(drops.stream().noneMatch(entity -> entity.getItem().is(BrewingItems.POTION.get()))
                && !helper.getLevel().destroyBlock(pos, true), "Break completed or duplicated an unfinished batch");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void slakedLimeRecipeUsesFourBottlesExactlyOnce(GameTestHelper helper) {
        CrucibleBlockEntity pot = pot(helper);
        var inventory = pot.inventory();
        var lime = com.example.akaishi.forbidden.alchemy.AlchemyMaterials.item("quicklime");
        var bottle = BrewingItems.SLAKED_LIME_BOTTLE.get();
        var id = new net.minecraft.resources.ResourceLocation("forbidden_hex", "brewing/slaked_lime_bottle");
        var recipe = helper.getLevel().getRecipeManager().getAllRecipesFor(BrewingRecipes.CRUCIBLE.get()).stream()
                .filter(candidate -> candidate.id().equals(id)).findFirst().orElseThrow();
        helper.assertTrue(recipe.steps().size() == 4 && recipe.outputCount() == 4 && recipe.slottedOnly()
                && recipe.bottles().equals(new CrucibleRecipe.BottlePolicy(4, 0)) && recipe.heat() == BrewingHeat.FIRE,
                "R05 loaded with a different batch contract");
        helper.assertTrue(inventory.setMode(1), "Crafting mode could not be selected");
        for (int i = 0; i < 4; i++) inventory.setItem(CrucibleInventory.CRAFT_START + i, item(lime));
        inventory.setItem(CrucibleInventory.BOTTLE, new ItemStack(Items.GLASS_BOTTLE, 3));
        var preview = inventory.plan(true);
        helper.assertTrue(preview.outputs().size() == 1 && preview.outputs().get(0).is(bottle)
                && preview.outputs().get(0).getCount() == 4 && preview.bottles() == 4 && preview.returns().isEmpty(),
                "R05 preview had the wrong bottles or output count");
        helper.assertTrue(!inventory.start(null) && inventory.materials().size() == 4, "Dry R05 accepted or consumed ingredients");
        pot.fillWater(1000, true);
        helper.assertTrue(!inventory.start(null) && pot.water() == 1000 && inventory.materials().size() == 4
                && inventory.getItem(CrucibleInventory.BOTTLE).getCount() == 3, "Missing fourth bottle partially consumed R05");
        inventory.setItem(CrucibleInventory.BOTTLE, new ItemStack(Items.GLASS_BOTTLE, 5));
        helper.assertTrue(inventory.start(null) && !inventory.start(null), "R05 was not submitted exactly once");
        tick(helper, pot, 60);
        helper.setBlock(POT.below(), Blocks.AIR);
        tick(helper, pot, 1);
        helper.assertTrue(inventory.progress() == 0 && inventory.active() && pot.water() == 1000, "R05 heat loss did not reset safely");
        helper.setBlock(POT.below(), Blocks.FIRE);
        tick(helper, pot, 99);
        pot.load(pot.saveWithoutMetadata());
        helper.assertTrue(!pot.corrupt() && inventory.active() && inventory.progress() == 99 && !pot.hasOutputs(), "R05 reload changed pending work");
        tick(helper, pot, 1);
        pot.load(pot.saveWithoutMetadata());
        helper.assertTrue(!pot.corrupt() && !inventory.active() && pot.hasOutputs() && !inventory.pendingReturns(), "R05 result reloaded incorrectly");
        var outputs = pot.takeOutputs();
        helper.assertTrue(outputs.size() == 1 && outputs.get(0).is(bottle) && outputs.get(0).getCount() == 4
                && inventory.materials().isEmpty() && pot.water() == 0 && inventory.getItem(CrucibleInventory.BOTTLE).getCount() == 1,
                "R05 lost or duplicated ingredients, water, bottles or outputs");
        helper.assertTrue(item(bottle).getCraftingRemainingItem().is(Items.GLASS_BOTTLE)
                && item(bottle).getMaxStackSize() == 16, "Slaked lime bottle has a different container contract");
        tick(helper, pot, 2);
        helper.assertTrue(!pot.hasOutputs() && !inventory.start(null), "R05 duplicated after collection");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void slakedLimeContractDoesNotEnterLegacyBatches(GameTestHelper helper) {
        var pot = pot(helper);
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("BrewingVersion", 1);
        legacy.putInt("Water", 1000);
        legacy.putBoolean("Locked", true);
        ListTag steps = new ListTag();
        steps.add(item(com.example.akaishi.forbidden.alchemy.AlchemyMaterials.item("quicklime")).save(new CompoundTag()));
        legacy.put("Steps", steps);
        pot.load(legacy);
        helper.assertTrue(!pot.corrupt() && !pot.slotsEnabled()
                && !pot.accept(item(com.example.akaishi.forbidden.alchemy.AlchemyMaterials.item("quicklime")))
                && pot.steps().size() == 1 && pot.canRecoverLegacy(), "R05 bypassed bottles through legacy input");
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "lime_recovery"));
        player.setPos(pot.getBlockPos().getX() + 0.5, pot.getBlockPos().getY() + 1, pot.getBlockPos().getZ() + 0.5);
        helper.assertTrue(pot.recoverLegacy(player) && pot.slotsEnabled() && pot.steps().isEmpty() && pot.water() == 1000
                && player.getInventory().countItem(com.example.akaishi.forbidden.alchemy.AlchemyMaterials.item("quicklime")) == 1
                && !pot.recoverLegacy(player), "Legacy quicklime could not recover exactly once");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void confirmedBottleLedgerConservesContainers(GameTestHelper helper) {
        int[][] ledger = {{0, 2, 0, 2, 0}, {1, 2, 0, 1, 0}, {3, 2, 1, 0, 1},
                {2, 2, 1, 1, 1}, {2, 2, 2, 2, 2}, {0, 4, 0, 4, 0}, {1, 1, 0, 0, 0}};
        for (int[] row : ledger) {
            List<ItemStack> inputs = new ArrayList<>();
            for (int i = 0; i < row[0]; i++) inputs.add(item(Items.DRAGON_BREATH));
            inputs.add(BrewingFumeItem.create(BrewingFumes.EVIL_BREATH));
            var settlement = new CrucibleRecipe.BottlePolicy(row[1], row[2]).settle(inputs);
            int returned = settlement.returns().stream().filter(stack -> stack.is(Items.GLASS_BOTTLE)).mapToInt(ItemStack::getCount).sum();
            helper.assertTrue(settlement.bottles() == row[3] && returned == row[4]
                    && row[0] + settlement.bottles() == row[1] + returned, "Confirmed bottle ledger lost or created containers");
            helper.assertTrue(inputs.stream().allMatch(stack -> stack.getCount() == 1), "Preview consumed an input");
        }
        var surplus = new CrucibleRecipe.BottlePolicy(2, 0).settle(List.of(item(Items.DRAGON_BREATH),
                item(Items.DRAGON_BREATH), item(Items.DRAGON_BREATH), item(Items.MILK_BUCKET)));
        helper.assertTrue(surplus.bottles() == 0 && surplus.returns().stream().filter(stack -> stack.is(Items.GLASS_BOTTLE))
                .mapToInt(ItemStack::getCount).sum() == 1 && surplus.returns().stream().anyMatch(stack -> stack.is(Items.BUCKET)),
                "Surplus bottle or non-bottle remainder disappeared");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void splitInkOutputAndBatchNetworkRoundTrip(GameTestHelper helper) {
        var json = com.google.gson.JsonParser.parseString("""
                {"kind":"transformation","steps":[{"ingredient":{"item":"minecraft:dragon_breath"}}],
                 "result":{"item":"forbidden_hex:forbidden_hex_ink_ember","nbt":"{sample:1b}"},
                 "output_count":2,"bottles":{"output":2,"returns":1}}
                """).getAsJsonObject();
        var serializer = new CrucibleRecipe.Serializer();
        var id = new net.minecraft.resources.ResourceLocation("forbidden_hex", "test/split_ink");
        var recipe = serializer.fromJson(id, json);
        var outputs = recipe.outputs(recipe.result());
        helper.assertTrue(recipe.slottedOnly() && outputs.size() == 2 && outputs.stream().allMatch(stack -> stack.getCount() == 1
                && stack.getMaxStackSize() == 1 && stack.hasTag() && stack.getTag().getBoolean("sample")), "Durable ink output was overstacked");
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            serializer.toNetwork(buffer, recipe);
            var decoded = serializer.fromNetwork(id, buffer);
            helper.assertTrue(buffer.readableBytes() == 0 && decoded.outputCount() == 2 && decoded.bottles().equals(recipe.bottles())
                    && ItemStack.isSameItemSameTags(decoded.result(), recipe.result())
                    && ItemStack.isSameItemSameTags(decoded.resultPool().get(0), recipe.resultPool().get(0))
                    && !decoded.result().getTag().contains("forbidden_hex_crucible_batch")
                    && !decoded.result().getTag().contains("forbidden_hex_crucible_results"),
                    "Batch network contract drifted or leaked metadata into the result");
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void invalidBottleAndOutputContractsAreRejected(GameTestHelper helper) {
        var serializer = new CrucibleRecipe.Serializer();
        var id = new net.minecraft.resources.ResourceLocation("forbidden_hex", "test/invalid_batch");
        String base = "{\"kind\":\"transformation\",\"steps\":[{\"ingredient\":{\"item\":\"minecraft:egg\"}}],\"result\":{\"item\":\"forbidden_hex:forbidden_hex_ink_ember\"},";
        for (String contract : List.of("\"output_count\":0", "\"output_count\":5", "\"output_count\":2,\"bottles\":{\"output\":3}",
                "\"bottles\":{\"output\":-1}", "\"bottles\":{\"returns\":65}", "\"bottles\":{\"output\":2147483647,\"returns\":2147483647}")) {
            boolean rejected = false;
            try { serializer.fromJson(id, com.google.gson.JsonParser.parseString(base + contract + "}").getAsJsonObject()); }
            catch (com.google.gson.JsonSyntaxException expected) { rejected = true; }
            helper.assertTrue(rejected, "Invalid batch contract accepted: " + contract);
        }
        var reserved = com.google.gson.JsonParser.parseString(base + "\"output_count\":2}").getAsJsonObject();
        reserved.getAsJsonObject("result").addProperty("nbt", "{forbidden_hex_crucible_batch:{custom:1}}");
        boolean rejected = false;
        try { serializer.fromJson(id, reserved); }
        catch (com.google.gson.JsonSyntaxException expected) { rejected = true; }
        helper.assertTrue(rejected, "Reserved protocol tag was silently accepted as result data");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void outputsAndRemaindersSurviveReloadExactlyOnce(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        var previousRecipes = new ArrayList<>(manager.getRecipes());
        var recipes = new ArrayList<net.minecraft.world.item.crafting.Recipe<?>>(previousRecipes);
        var json = com.google.gson.JsonParser.parseString("""
                {"kind":"transformation","steps":[{"ingredient":{"item":"minecraft:milk_bucket"}},
                 {"ingredient":{"item":"minecraft:dragon_breath"}}],
                 "result":{"item":"forbidden_hex:forbidden_hex_ink_ember"},"output_count":2,
                 "bottles":{"output":2,"returns":2}}
                """).getAsJsonObject();
        recipes.add(new CrucibleRecipe.Serializer().fromJson(new net.minecraft.resources.ResourceLocation("forbidden_hex", "test/transaction"), json));
        manager.replaceRecipes(recipes);
        try {
            CrucibleBlockEntity pot = pot(helper);
            var inventory = pot.inventory();
            helper.assertTrue(inventory.setMode(1), "Test crafting mode was unavailable");
            pot.fillWater(1000, true);
            inventory.setItem(CrucibleInventory.CRAFT_START, item(Items.MILK_BUCKET));
            inventory.setItem(CrucibleInventory.CRAFT_START + 1, item(Items.DRAGON_BREATH));
            inventory.setItem(CrucibleInventory.BOTTLE, new ItemStack(Items.GLASS_BOTTLE, 2));
            helper.assertTrue(inventory.plan(true).bottles() == 3 && !inventory.start(null)
                    && inventory.materials().size() == 2 && pot.water() == 1000, "Missing bottles partially consumed a crafting batch");
            inventory.setItem(CrucibleInventory.BOTTLE, new ItemStack(Items.GLASS_BOTTLE, 3));
            helper.assertTrue(inventory.start(null) && !inventory.start(null), "Crafting batch did not start exactly once");
            tick(helper, pot, 99);
            pot.load(pot.saveWithoutMetadata());
            helper.assertTrue(!pot.corrupt() && inventory.active() && inventory.progress() == 99, "Pre-completion reload changed the batch");
            tick(helper, pot, 1);
            helper.assertTrue(pot.hasOutputs() && inventory.pendingReturns(), "Finished output and remainder were not both pending");
            pot.load(pot.saveWithoutMetadata());
            helper.assertTrue(!pot.corrupt() && !inventory.active() && pot.hasOutputs() && inventory.pendingReturns(),
                    "Completed batch failed to reload with output and remainder");
            var contents = pot.takeContents();
            var ink = com.example.akaishi.forbidden.item.AkaishiMagicItems.ink("chi").get();
            helper.assertTrue(contents.stream().filter(stack -> stack.is(ink)).count() == 2
                    && contents.stream().filter(stack -> stack.is(ink)).allMatch(stack -> stack.getCount() == 1)
                    && contents.stream().filter(stack -> stack.is(Items.BUCKET)).mapToInt(ItemStack::getCount).sum() == 1
                    && contents.stream().filter(stack -> stack.is(Items.GLASS_BOTTLE)).mapToInt(ItemStack::getCount).sum() == 2
                    && contents.size() == 4 && pot.water() == 0, "Recovery lost or duplicated products or remainder");
            pot.load(pot.saveWithoutMetadata());
            tick(helper, pot, 2);
            helper.assertTrue(pot.takeContents().isEmpty() && !inventory.active(), "Reload or later tick duplicated a settled batch");
        } finally { manager.replaceRecipes(previousRecipes); }
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void legacyLargeInputCanBeRecovered(GameTestHelper helper) {
        CrucibleBlockEntity pot = pot(helper);
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("BrewingVersion", 1);
        legacy.putInt("Water", 1000);
        legacy.putBoolean("Locked", true);
        ListTag steps = new ListTag();
        for (int i = 0; i < 30; i++) steps.add(item(Items.NETHER_WART).save(new CompoundTag()));
        legacy.put("Steps", steps);
        pot.load(legacy);
        helper.assertTrue(!pot.corrupt() && !pot.slotsEnabled() && pot.steps().size() == 30, "Legacy inputs were truncated to 25");
        var contents = pot.takeContents();
        helper.assertTrue(contents.size() == 30, "Breaking the machine lost legacy inputs");
        helper.succeed();
    }
}
