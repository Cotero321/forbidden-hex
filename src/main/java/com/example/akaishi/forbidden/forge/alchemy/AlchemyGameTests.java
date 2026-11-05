package com.example.akaishi.forbidden.forge.alchemy;

import com.example.akaishi.forbidden.alchemy.*;
import com.example.akaishi.forbidden.altar.*;
import com.example.akaishi.forbidden.brewing.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder("forbidden_hex")
@PrefixGameTestTemplate(false)
public final class AlchemyGameTests {
    private static final BlockPos MACHINE = new BlockPos(7, 3, 7);
    private AlchemyGameTests() { }
    private static void check(boolean value, String reason) { if (!value) throw new IllegalStateException(reason); }
    private static AlchemyRecipe recipe(GameTestHelper helper, String name) {
        return (AlchemyRecipe) helper.getLevel().getRecipeManager().byKey(new net.minecraft.resources.ResourceLocation("forbidden_hex", "alchemy/" + name)).orElseThrow();
    }
    private static AlchemyMachineBlockEntity machine(GameTestHelper helper, AlchemyContent.Kind kind) {
        helper.setBlock(MACHINE, AlchemyContent.BLOCKS.get(kind).get());
        return (AlchemyMachineBlockEntity) helper.getBlockEntity(MACHINE);
    }
    private static void tick(GameTestHelper helper, AlchemyMachineBlockEntity machine, int count) {
        for (int i = 0; i < count; i++) AlchemyMachineBlockEntity.tick(helper.getLevel(), machine.getBlockPos(), machine.getBlockState(), machine);
    }
    static SourceAltarBlockEntity altar(GameTestHelper helper) {
        BlockPos origin = new BlockPos(2, 3, 2);
        for (int i = 0; i < 6; i++) helper.setBlock(origin.offset(i % 3, 0, i / 3), SourceAltarContent.SOURCE_ALTAR.get());
        BlockPos absolute = helper.absolutePos(origin); SourceAltarStructure.refresh(helper.getLevel(), absolute);
        SourceAltarBlockEntity altar = (SourceAltarBlockEntity) helper.getLevel().getBlockEntity(absolute);
        CompoundTag saved = altar.saveWithoutMetadata(); saved.putLong("SourceEnergyHalfUnits", 100); altar.load(saved);
        for (int i = 0; i < 8192 && !altar.isOperational(); i++) altar.scanStep(8192);
        check(altar.isOperational(), "Source altar did not finish scan"); SourceEnergyNetwork.add(helper.getLevel(), absolute);
        return altar;
    }
    private static BrewingPotionData potion(BrewingEffectType type) {
        return new BrewingPotionData(6, 1, 1, BrewingPotionData.Delivery.SPLASH, false, false, List.of(), List.of(), List.of(new BrewingEffectEntry(type)));
    }
    @GameTest(template = "source_altar_empty")
    public static void equivalentCountsTimesAndCompanions(GameTestHelper helper) {
        check(recipe(helper, "iron_raw_fine").duration(1, false) == 23, "Fine tick rounding incorrect");
        check(recipe(helper, "iron_raw_coarse").duration(1, false) == 12, "Coarse tick rounding incorrect");
        check(recipe(helper, "iron_raw_coarse").duration(1, true) == 6, "Powered speed is not double");
        check(recipe(helper, "iron_raw_block_fine").equivalents() == 9, "Raw block multiplier incorrect");
        check(recipe(helper, "redstone_ore_coarse").equivalents() == 4 && recipe(helper, "lapis_ore_fine").equivalents() == 6, "Fixed ore medians incorrect");
        String[] companions = {"sulfur", "saltpeter", "lead", "green_vitriol", "blue_vitriol", "silver", "hematite", "pyrite", "saltpeter"};
        for (int i = 0; i < AlchemyMaterials.METALS.size(); i++) {
            String mineral = AlchemyMaterials.METALS.get(i);
            var raw = recipe(helper, mineral + "_raw_fine"); var crude = recipe(helper, mineral + "_crude_fine");
            check(raw.duration(4, false) == crude.duration(4, false), "Raw and crude times differ");
            check(raw.outputs().get(3).stack().is(AlchemyMaterials.item("tiny_" + companions[i] + "_dust")), "Wrong mineral companion");
            var maximum = raw.products(1, RandomSource.create(1), true);
            check(maximum.get(0).getCount() == 2 && maximum.get(1).getCount() == 1 && maximum.get(2).getCount() == 1 && maximum.get(3).getCount() == 2, "Fine outputs incomplete");
        }
        helper.succeed();
    }
    @GameTest(template = "source_altar_empty")
    public static void poweredBatchBlocksReloadsAndPaysOnlyWorkingTicks(GameTestHelper helper) {
        var altar = altar(helper); var machine = machine(helper, AlchemyContent.Kind.SOURCE_CRUSHER);
        machine.setMode(1); machine.setItem(0, new ItemStack(Items.RAW_IRON, 64));
        long before = altar.energyUnits(); tick(helper, machine, 1);
        check(machine.hasJob() && machine.getItem(0).getCount() == 32 && machine.duration() == 49, "Large stack was not bounded to 32 safe inputs");
        check(altar.energyUnits() == before - 1 && machine.credit() == 9, "First half-point prepayment wrong");
        check(!machine.setMode(0), "Mode changed during active batch");
        CompoundTag saved = machine.saveWithoutMetadata(); CompoundTag products = saved.getCompound("AlchemyJob").getCompound("Products").copy();
        machine.setItem(6, new ItemStack(Items.STONE, 64)); tick(helper, machine, 20);
        check(machine.progress() == 1 && machine.credit() == 9 && altar.energyUnits() == before - 1, "Blocked output spent energy or advanced");
        CompoundTag blocked = machine.saveWithoutMetadata(); machine.load(blocked);
        check(machine.saveWithoutMetadata().getCompound("AlchemyJob").getCompound("Products").equals(products), "Reload rerolled byproducts");
        machine.setItem(6, ItemStack.EMPTY); tick(helper, machine, 48);
        check(!machine.hasJob() && machine.getItem(3).is(AlchemyMaterials.item("fine_iron_dust")) && machine.getItem(3).getCount() == 64, "Batch did not finish atomically");
        check(altar.energyUnits() == before - 5 && machine.credit() == 1, "Active tick accounting incorrect");
        long paid = altar.energyUnits(); tick(helper, machine, 10);
        check(altar.energyUnits() == paid && machine.credit() == 1, "Idle or blocked machine spent credit"); helper.succeed();
    }
    @GameTest(template = "source_altar_empty")
    public static void manualLeasePausesResumesAndStopsOnMenuClose(GameTestHelper helper) {
        var machine = machine(helper, AlchemyContent.Kind.MANUAL_CRUSHER);
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "alchemy-grinder"));
        player.setPos(Vec3.atCenterOf(machine.getBlockPos()));
        helper.getLevel().players().add(player);
        var menu = machine.createMenu(1, player.getInventory(), player); player.containerMenu = menu;
        try {
            machine.setItem(0, new ItemStack(Items.RAW_IRON));
            check(menu.clickMenuButton(player, 1), "Valid held input rejected"); tick(helper, machine, 3);
            check(machine.progress() == 3 && machine.hasJob(), "Held manual input did not advance");
            check(menu.clickMenuButton(player, 2), "Release input rejected"); tick(helper, machine, 10);
            check(machine.progress() == 3 && !menu.clickMenuButton(player, 0), "Released work advanced or mode unlocked");
            check(menu.clickMenuButton(player, 1), "Resume input rejected"); tick(helper, machine, 2);
            check(machine.progress() == 5, "Resume restarted progress");
            menu.removed(player); player.containerMenu = player.inventoryMenu; tick(helper, machine, 10);
            check(machine.progress() == 5 && !menu.clickMenuButton(player, 1), "Closed menu retained a held lease");
            player.containerMenu = menu; menu.clickMenuButton(player, 1); tick(helper, machine, 7);
            check(!machine.hasJob() && machine.getItem(3).is(AlchemyMaterials.item("crude_iron_dust")), "Manual batch did not finish after resume");
        } finally {
            menu.removed(player); player.containerMenu = player.inventoryMenu; helper.getLevel().players().remove(player);
        }
        helper.succeed();
    }
    @GameTest(template = "source_altar_empty")
    public static void dismantledMachineKeepsJobWithoutDuplicatingInventory(GameTestHelper helper) {
        altar(helper); var machine = machine(helper, AlchemyContent.Kind.SOURCE_CRUSHER);
        machine.setItem(0, new ItemStack(Items.RAW_IRON, 2)); tick(helper, machine, 1);
        CompoundTag saved = machine.saveWithoutMetadata();
        var drops = net.minecraft.world.level.block.Block.getDrops(machine.getBlockState(), helper.getLevel(), machine.getBlockPos(), machine);
        check(drops.size() == 1 && drops.get(0).hasTag(), "Machine drop lacks persistent job");
        CompoundTag stored = drops.get(0).getTag().getCompound("BlockEntityTag");
        check(stored.getCompound("AlchemyJob").equals(saved.getCompound("AlchemyJob")) && stored.getInt("Credit") == machine.credit()
                && !stored.contains("Items"), "Device copied slots or lost paid job");
        machine.clearContent(); helper.setBlock(MACHINE, Blocks.AIR);
        var replacement = machine(helper, AlchemyContent.Kind.SOURCE_CRUSHER); replacement.load(stored);
        tick(helper, replacement, replacement.duration() - replacement.progress());
        check(!replacement.hasJob() && replacement.getItem(3).getCount() == 2 && replacement.getItem(0).isEmpty(), "Replaced device lost or duplicated output"); helper.succeed();
    }
    @GameTest(template = "source_altar_empty")
    public static void missingEnergyAndManualHoldDoNotConsumeInput(GameTestHelper helper) {
        BlockPos isolated = MACHINE.above(64);
        helper.setBlock(isolated, AlchemyContent.BLOCKS.get(AlchemyContent.Kind.SOURCE_CRUSHER).get());
        var powered = (AlchemyMachineBlockEntity) helper.getBlockEntity(isolated);
        powered.setItem(0, new ItemStack(Items.RAW_IRON)); tick(helper, powered, 1);
        check(!powered.hasJob() && powered.getItem(0).getCount() == 1 && powered.status() == AlchemyMachineBlockEntity.ENERGY, "Missing energy consumed input");
        powered.clearContent(); helper.setBlock(isolated, Blocks.AIR);
        var manual = machine(helper, AlchemyContent.Kind.MANUAL_CRUSHER);
        manual.setItem(0, new ItemStack(Items.RAW_IRON)); tick(helper, manual, 10);
        check(manual.progress() == 0 && !manual.hasJob() && manual.getItem(0).getCount() == 1 && manual.status() == AlchemyMachineBlockEntity.HOLD, "Manual crusher ran without valid held input");
        check(manual.setMode(1), "Idle mode did not change"); helper.succeed();
    }
    @GameTest(template = "source_altar_empty")
    public static void ordinaryDistillationRequiresHeatWaterAndTwoBottles(GameTestHelper helper) {
        var machine = machine(helper, AlchemyContent.Kind.DISTILLER);
        machine.setItem(0, new ItemStack(AlchemyMaterials.item("fine_cinnabar_dust")));
        machine.setItem(2, new ItemStack(Items.GLASS_BOTTLE));
        tick(helper, machine, 1); check(machine.status() == AlchemyMachineBlockEntity.GLASS && !machine.hasJob(), "Two-liquid distillation accepted one bottle");
        machine.setItem(2, new ItemStack(Items.GLASS_BOTTLE, 2)); tick(helper, machine, 1);
        check(machine.status() == AlchemyMachineBlockEntity.HEAT, "Distillation ignored heat");
        helper.setBlock(MACHINE.below(), Blocks.MAGMA_BLOCK); tick(helper, machine, 1);
        check(machine.status() == AlchemyMachineBlockEntity.WATER && !machine.getItem(0).isEmpty(), "Dry distillation consumed inputs");
        check(machine.fillWater(1000, true) == 1000 && machine.water() == 0 && machine.fillWater(1000, false) == 1000, "Water fill simulation mutated tank");
        tick(helper, machine, 100); check(machine.water() == 750 && machine.progress() == 100, "Water not debited once at start");
        helper.setBlock(MACHINE.below(), Blocks.AIR); tick(helper, machine, 10); check(machine.progress() == 100, "Cold tower kept working");
        CompoundTag saved = machine.saveWithoutMetadata(); machine.load(saved);
        helper.setBlock(MACHINE.below(), Blocks.MAGMA_BLOCK); tick(helper, machine, 100);
        check(!machine.hasJob() && machine.water() == 750 && machine.getItem(3).is(AlchemyMaterials.item("mercury"))
                && machine.getItem(4).is(AlchemyMaterials.item("sulfurous_acid")) && machine.getItem(2).isEmpty(), "Two-bottle atomic output incorrect"); helper.succeed();
    }
    @GameTest(template = "source_altar_empty")
    public static void sourceTowerReplacesWaterHeatAndSoulFire(GameTestHelper helper) {
        var altar = altar(helper); var machine = machine(helper, AlchemyContent.Kind.SOURCE_DISTILLER);
        machine.setItem(0, BrewingFumeItem.create(BrewingFumes.HEAT_BREATH)); machine.setItem(1, new ItemStack(AlchemyMaterials.item("nitric_acid")));
        long before = altar.energyUnits(); tick(helper, machine, 200);
        check(!machine.hasJob() && machine.water() == 0 && BrewingFumes.HELLFIRE_BREATH.equals(BrewingFumeItem.kind(machine.getItem(3)))
                && machine.getItem(4).is(Items.GLASS_BOTTLE), "Source tower failed without heat/water or lost reagent bottle");
        check(altar.energyUnits() == before - 40, "Source tower did not spend two source per second");
        helper.succeed();
    }
    @GameTest(template = "source_altar_empty")
    public static void normalFumeRefiningNeedsSoulHeatNotWater(GameTestHelper helper) {
        var machine = machine(helper, AlchemyContent.Kind.DISTILLER);
        machine.setItem(0, BrewingFumeItem.create(BrewingFumes.SUN_BREATH)); machine.setItem(1, new ItemStack(AlchemyMaterials.item("fine_sulfur_dust")));
        helper.setBlock(MACHINE.below(), Blocks.MAGMA_BLOCK); tick(helper, machine, 1);
        check(machine.status() == AlchemyMachineBlockEntity.HEAT && !machine.hasJob(), "Ordinary heat refined fume");
        helper.setBlock(MACHINE.below(2), Blocks.SOUL_SOIL);
        helper.setBlock(MACHINE.below(), Blocks.SOUL_FIRE); tick(helper, machine, 200);
        check(!machine.hasJob() && machine.water() == 0 && BrewingFumes.DEFLAGRATION_BREATH.equals(BrewingFumeItem.kind(machine.getItem(3))), "Soul-fire refining wrong"); helper.succeed();
    }
    @GameTest(template = "source_altar_empty")
    public static void recipesRoundtripAndCompleteScope(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager().getAllRecipesFor(AlchemyContent.RECIPES.get());
        check(recipes.stream().filter(value -> value.process().equals("distilling")).count() == 9, "Missing distillation reactions");
        check(recipes.stream().filter(value -> value.process().equals("refining")).count() == 13, "Missing fume refinements");
        for (var original : recipes) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                AlchemyContent.SERIALIZER.get().toNetwork(buffer, original);
                var restored = AlchemyContent.SERIALIZER.get().fromNetwork(original.id(), buffer);
                check(original.process().equals(restored.process()) && original.equivalents() == restored.equivalents()
                        && original.outputs().size() == restored.outputs().size()
                        && ItemStack.isSameItemSameTags(original.outputs().get(0).stack(), restored.outputs().get(0).stack()), "Recipe sync lost content");
            } finally { buffer.release(); }
        }
        check(recipes.stream().noneMatch(value -> value.process().equals("crushing") && value.input().test(new ItemStack(AlchemyMaterials.item("crude_lead_dust")))), "Companion residue opened unintended refining loop");
        helper.succeed();
    }
    @GameTest(template = "source_altar_empty")
    public static void refinedFumesAreTierSixAndNotRandomOvenDrops(GameTestHelper helper) {
        check(BrewingFumes.REFINED.size() == 13 && BrewingFumes.NON_EVIL.size() == 22, "Fume lists changed unexpectedly");
        for (var kind : BrewingFumes.REFINED) {
            var type = BrewingFumes.effect(kind); check(type != null && type.cost() == 6 && BrewingFumes.isAllowed(kind) && BrewingFumes.isNonEvil(kind), "Refined fume is not tier six");
            var full = BrewingPotionComposer.evaluate(List.of(new ItemStack(Items.NETHER_WART), new ItemStack(Items.NETHER_STAR), BrewingFumeItem.create(kind)));
            check(full.complete() && full.potion().usedCapacity() == 6, "Refined fume cannot compose");
            check(!BrewingPotionComposer.evaluate(List.of(new ItemStack(Items.NETHER_WART), BrewingFumeItem.create(kind))).valid(), "Low-capacity brew accepted tier-six fume");
            check(BrewingPotionData.read(potion(type).save()).orElseThrow().usedCapacity() == 6, "Potion save lost tier cost");
        }
        var random = RandomSource.create(1); for (int i = 0; i < 2000; i++) check(!BrewingFumes.REFINED.contains(BrewingFumes.randomNonEvil(random)), "Refined fume entered oven random pool");
        var lime = WitchOvenRecipe.find(new net.minecraft.world.SimpleContainer(new ItemStack(AlchemyMaterials.item("limestone_dust"))), helper.getLevel()).orElseThrow();
        check(lime.result().is(AlchemyMaterials.item("quicklime")) && new WitchOvenProfile(4, 1, 1).chance(lime) == 0, "Upgraded oven invented a lime fume"); helper.succeed();
    }
    @GameTest(template = "source_altar_empty")
    public static void rustChangesArmorAndPurgeKeepsNegativeEffects(GameTestHelper helper) {
        Sheep sheep = helper.spawn(EntityType.SHEEP, new BlockPos(7, 3, 7)); sheep.setNoAi(true);
        sheep.getAttribute(Attributes.ARMOR).setBaseValue(12); sheep.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        double before = sheep.getAttributeValue(Attributes.ARMOR);
        BrewingPotionExecutor.applyEntity(potion(BrewingEffectType.RUST), sheep, sheep, sheep, 1, true);
        check(Math.abs(sheep.getAttributeValue(Attributes.ARMOR) - before * .5) < .001 && sheep.getItemBySlot(EquipmentSlot.HEAD).getDamageValue() == 0, "Rust damaged durability or wrong armor amount");
        sheep.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200)); sheep.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
        BrewingPotionExecutor.applyEntity(potion(BrewingEffectType.PURGE_BENEFITS), sheep, sheep, sheep, 1, true);
        check(!sheep.hasEffect(MobEffects.MOVEMENT_SPEED) && sheep.hasEffect(MobEffects.POISON) && BrewingStatusEffects.has(sheep, BrewingEffectType.RUST), "Purge removed negative effects");
        sheep.removeEffect(BrewingStatusEffects.get(BrewingEffectType.RUST)); check(Math.abs(sheep.getAttributeValue(Attributes.ARMOR) - before) < .001, "Armor failed to restore"); helper.succeed();
    }
    @GameTest(template = "source_altar_empty")
    public static void corrosionWhitelistAndInstantMaturity(GameTestHelper helper) {
        BlockPos local = new BlockPos(8, 3, 8); Vec3 origin = Vec3.atCenterOf(helper.absolutePos(local));
        helper.setBlock(local, Blocks.DIRT); helper.setBlock(local.east(), Blocks.CHEST); helper.setBlock(local.west(), Blocks.IRON_ORE); helper.setBlock(local.north(), Blocks.STONE);
        BrewingPotionExecutor.applyWorld(potion(BrewingEffectType.CORROSION), helper.getLevel(), origin, null);
        helper.assertBlockPresent(Blocks.AIR, local); helper.assertBlockPresent(Blocks.CHEST, local.east());
        helper.assertBlockPresent(Blocks.IRON_ORE, local.west()); helper.assertBlockPresent(Blocks.STONE, local.north());
        helper.setBlock(local.below(), Blocks.FARMLAND); helper.setBlock(local, Blocks.WHEAT);
        BrewingPotionExecutor.applyWorld(potion(BrewingEffectType.FLOURISHING), helper.getLevel(), origin, null);
        check(helper.getBlockState(local).getValue(CropBlock.AGE) == 7, "Flourishing did not mature wheat"); helper.succeed();
    }
    @GameTest(template = "source_altar_empty", timeoutTicks = 80)
    public static void deflagrationThunderSonicAndDeliriumExecute(GameTestHelper helper) {
        BlockPos local = new BlockPos(8, 3, 8); Vec3 origin = Vec3.atCenterOf(helper.absolutePos(local));
        helper.setBlock(local, Blocks.STONE);
        Sheep sheep = helper.spawn(EntityType.SHEEP, local.east()); sheep.setNoAi(true); sheep.setNoGravity(true);
        BrewingPotionExecutor.applyWorld(potion(BrewingEffectType.DEFLAGRATION), helper.getLevel(), origin, null);
        helper.assertBlockPresent(Blocks.STONE, local);
        check(sheep.getHealth() < sheep.getMaxHealth(), "Deflagration did not damage entities");
        sheep.discard(); helper.setBlock(local, Blocks.AIR);
        sheep = helper.spawn(EntityType.SHEEP, local.east()); sheep.setNoAi(true); sheep.setNoGravity(true);
        float health = sheep.getHealth();
        helper.getLevel().setWeatherParameters(6000, 0, false, false);
        BrewingPotionExecutor.applyWorld(potion(BrewingEffectType.THUNDER), helper.getLevel(), origin, null);
        check(Math.abs(sheep.getHealth() - health + 6) < .001, "Clear-weather thunder damage incorrect");
        BrewingPotionExecutor.applyWorld(potion(BrewingEffectType.SONIC_BURST), helper.getLevel(), origin, null);
        check(sheep.getDeltaMovement().x > 1 && sheep.getDeltaMovement().y > .3, "Sonic burst did not knock outward");
        sheep.discard();
        // Keep the AI fixture inside the 24 x 16 x 24 template so repeated runs clear it.
        BlockPos aiLocal = local.above(7);
        String fixtureTag = "forbidden_hex_gametest_delirium_fixture";
        var fixtureBounds = new net.minecraft.world.phys.AABB(helper.absolutePos(aiLocal.west(5)),
                helper.absolutePos(aiLocal.east(3).above(3).south()));
        Runnable cleanup = () -> helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, fixtureBounds,
                entity -> entity.getPersistentData().getBoolean(fixtureTag)).forEach(net.minecraft.world.entity.Entity::discard);
        cleanup.run();
        var monster = helper.spawn(EntityType.ZOMBIE, aiLocal); monster.setNoAi(true); monster.setNoGravity(true); monster.setInvulnerable(true);
        Sheep close = helper.spawn(EntityType.SHEEP, aiLocal.east(2)); close.setNoAi(true); close.setNoGravity(true);
        Sheep far = helper.spawn(EntityType.SHEEP, aiLocal.west(5)); far.setNoAi(true); far.setNoGravity(true);
        for (var entity : List.of(monster, close, far)) entity.getPersistentData().putBoolean(fixtureTag, true);
        monster.setTarget(far);
        BrewingPotionExecutor.applyEntity(potion(BrewingEffectType.DELIRIUM), monster, monster, monster, 1, true);
        helper.runAfterDelay(15, () -> {
            try {
                check(monster.getTarget() == close, "Delirium did not choose closest creature; active="
                        + BrewingStatusEffects.has(monster, BrewingEffectType.DELIRIUM) + ", visible=" + monster.hasLineOfSight(close)
                        + ", target=" + monster.getTarget() + ", close=" + close + ", distances=" + monster.distanceToSqr(close) + "/" + monster.distanceToSqr(far));
                monster.removeEffect(BrewingStatusEffects.get(BrewingEffectType.DELIRIUM)); AlchemyPotionEffects.tick(monster);
                check(monster.getTarget() == null, "Delirium left forced target after removal"); helper.succeed();
            } finally { cleanup.run(); }
        });
    }
    @GameTest(template = "source_altar_empty")
    public static void temporarySnowAndGravitySurviveSave(GameTestHelper helper) {
        BlockPos local = new BlockPos(8, 3, 8), absolute = helper.absolutePos(local); helper.setBlock(local.below(), Blocks.STONE);
        BrewingPotionExecutor.applyWorld(potion(BrewingEffectType.EXTREME_COLD), helper.getLevel(), Vec3.atBottomCenterOf(absolute), null);
        helper.assertBlockPresent(Blocks.POWDER_SNOW, local);
        var live = BrewingWorldRestoration.get(helper.getLevel()); CompoundTag saved = live.save(new CompoundTag());
        var list = saved.getList("Entries", Tag.TAG_COMPOUND); for (int i = 0; i < list.size(); i++) list.getCompound(i).putLong("Expires", helper.getLevel().getGameTime() - 1);
        live.forget(absolute); BrewingWorldRestoration.load(saved).tick(helper.getLevel()); helper.assertBlockPresent(Blocks.AIR, local);
        BrewingGravityFields.get(helper.getLevel()).add(helper.getLevel(), Vec3.atBottomCenterOf(absolute), null, 1, 200);
        var fields = BrewingGravityFields.load(BrewingGravityFields.get(helper.getLevel()).save(new CompoundTag()));
        Sheep sheep = helper.spawn(EntityType.SHEEP, local); sheep.setNoAi(true); sheep.setNoGravity(true);
        fields.tick(helper.getLevel());
        helper.runAfterDelay(6, () -> { fields.tick(helper.getLevel()); check(BrewingStatusEffects.has(sheep, BrewingEffectType.GRAVITY_FIELD), "Reloaded gravity field failed"); helper.succeed(); });
    }
    @GameTest(template = "source_altar_empty", timeoutTicks = 80)
    public static void underwaterHellfireAndMovementOnlyBleeding(GameTestHelper helper) {
        Sheep burning = helper.spawn(EntityType.SHEEP, new BlockPos(6, 3, 6)); burning.setNoAi(true); burning.setNoGravity(true);
        helper.setBlock(new BlockPos(6, 3, 6), Blocks.WATER);
        Sheep still = helper.spawn(EntityType.SHEEP, new BlockPos(10, 3, 10)); still.setNoAi(true); still.setNoGravity(true);
        Sheep moving = helper.spawn(EntityType.SHEEP, new BlockPos(14, 3, 14)); moving.setNoAi(true); moving.setNoGravity(true);
        BrewingPotionExecutor.applyEntity(potion(BrewingEffectType.HELLFIRE), burning, burning, burning, 1, true);
        BrewingPotionExecutor.applyEntity(potion(BrewingEffectType.PIERCING), still, still, still, 1, true);
        BrewingPotionExecutor.applyEntity(potion(BrewingEffectType.PIERCING), moving, moving, moving, 1, true);
        float burningHealth = burning.getHealth(), stillHealth = still.getHealth(), movingHealth = moving.getHealth();
        helper.runAfterDelay(10, () -> moving.setPos(moving.getX() + .5, moving.getY(), moving.getZ()));
        helper.runAfterDelay(25, () -> {
            check(burning.isInWater() && burning.getHealth() < burningHealth, "Water extinguished hellfire damage");
            check(still.getHealth() == stillHealth && moving.getHealth() < movingHealth, "Bleeding did not distinguish movement"); helper.succeed();
        });
    }
}
