package com.example.akaishi.forbidden.forge.brewing;

import com.example.akaishi.forbidden.brewing.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder("forbidden_hex")
@PrefixGameTestTemplate(false)
public final class BrewingPotionGameTests {
    private BrewingPotionGameTests() { }
    private static void check(boolean value, String reason) { if (!value) throw new IllegalStateException(reason); }
    private static ItemStack item(net.minecraft.world.item.Item item) { return new ItemStack(item); }
    private static BrewingPotionData data(BrewingEffectType type) {
        return new BrewingPotionData(type.cost(), 1, 1, BrewingPotionData.Delivery.DRINK, false, false,
                List.of(), List.of(), List.of(new BrewingEffectEntry(type)));
    }

    @GameTest(template = "brewing_empty", timeoutTicks = 40)
    public static void crucibleCustomBatchHeatingRollbackAndReload(GameTestHelper helper) {
        BlockPos local = new BlockPos(4, 3, 4);
        helper.setBlock(local.below(2), Blocks.NETHERRACK);
        helper.setBlock(local.below(), Blocks.FIRE);
        helper.setBlock(local, BrewingBlocks.CRUCIBLE.get());
        CrucibleBlockEntity pot = (CrucibleBlockEntity) helper.getBlockEntity(local);
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("BrewingVersion", 1);
        legacy.putBoolean("SlotsEnabled", false);
        pot.load(legacy);
        check(pot.fillWater(1000, true) == 1000, "Custom batch must use 1000mL");
        check(pot.accept(item(Items.NETHER_WART)), "Capacity material must begin custom batch");
        check(!pot.commitRecipe(), "Capacity-only prefix cannot finish a potion");
        check(pot.accept(item(Items.GLOW_BERRIES)) && pot.accept(item(Items.FERMENTED_SPIDER_EYE)), "Custom effect and conversion must be accepted");
        check(pot.accept(item(BrewingItems.GRASS_ASH.get())), "Grass ash must undo conversion");
        check(BrewingPotionComposer.evaluate(pot.steps()).potion().customEffects().get(0).type() == BrewingEffectType.GLOWING, "Undo must replay original effect");
        check(pot.accept(item(Items.FERMENTED_SPIDER_EYE)), "Reapplied conversion after undo must work");
        check(!pot.accept(item(Items.STONE)), "Over-budget input must be rejected");
        check(pot.accept(item(Items.REDSTONE)) && pot.accept(item(Items.GLOWSTONE)), "Time and strength remain independent");
        check(pot.commitRecipe(), "Complete custom batch must commit");
        BlockPos pos = helper.absolutePos(local);
        for (int i = 0; i < 49; i++) CrucibleBlockEntity.tick(helper.getLevel(), pos, pot.getBlockState(), pot);
        CompoundTag saved = pot.saveWithoutMetadata();
        pot.load(saved);
        check(pot.heatingTicks() == 49, "Heating progress must survive reload");
        for (int i = 49; i < 99; i++) CrucibleBlockEntity.tick(helper.getLevel(), pos, pot.getBlockState(), pot);
        check(!pot.hasOutputs(), "Custom batch cannot finish before 100 ticks");
        CrucibleBlockEntity.tick(helper.getLevel(), pos, pot.getBlockState(), pot);
        List<ItemStack> bottles = pot.takeOutputs();
        check(bottles.size() == 4, "Custom batch must produce four bottles");
        for (ItemStack bottle : bottles) {
            BrewingPotionData potion = BrewingPotionData.read(bottle).orElseThrow();
            check(potion.usedCapacity() == 2 && potion.capacity() == 2 && potion.strength() == 2 && potion.duration() == 2,
                    "Capacity, strength and duration must remain distinct");
            check(potion.customEffects().get(0).type() == BrewingEffectType.BLINDNESS
                    && bottle.getTag().getCompound(BrewingPotionData.TAG).getInt("volume") == 250, "Potion contents or volume incorrect");
        }
        check(pot.water() == 0 && pot.steps().isEmpty(), "Finished batch must clear water and history");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void conversionCostLastEffectAndModifierPreservation(GameTestHelper helper) {
        List<ItemStack> small = List.of(item(BrewingItems.MANDRAKE_ROOT.get()), item(Items.GLOW_BERRIES), item(Items.FERMENTED_SPIDER_EYE));
        check(!BrewingPotionComposer.evaluate(small).valid(), "Conversion must recheck increased cost");
        List<ItemStack> steps = new ArrayList<>(List.of(item(Items.NETHER_WART), item(Items.GLOW_BERRIES), item(Items.REDSTONE), item(Items.GLOWSTONE), item(Items.FERMENTED_SPIDER_EYE)));
        var result = BrewingPotionComposer.evaluate(steps);
        check(result.complete() && result.potion().strength() == 2 && result.potion().duration() == 2
                && result.potion().customEffects().get(0).type() == BrewingEffectType.BLINDNESS, "Conversion must preserve previously applied modifiers");
        steps.add(item(Items.FERMENTED_SPIDER_EYE));
        check(!BrewingPotionComposer.evaluate(steps).valid(), "A slot can only convert once");
        check(!BrewingPotionComposer.evaluate(List.of(item(Items.NETHER_STAR), item(Items.GLOW_BERRIES), item(Items.VINE), item(Items.FERMENTED_SPIDER_EYE))).valid(), "Converter cannot search past last unconvertible effect");
        check(!BrewingPotionComposer.evaluate(List.of(item(Items.NETHER_STAR), item(Items.BONE_MEAL), item(Items.DEAD_BUSH))).valid(), "Opposing effects must be rejected");
        check(!BrewingPotionComposer.evaluate(List.of(item(Items.NETHER_STAR), item(Items.SCUTE))).valid(), "Removed water-guard recipe cannot be accepted");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void customPotionPayloadAndLegacyValidation(GameTestHelper helper) {
        BrewingPotionData custom = data(BrewingEffectType.CRUSH);
        CompoundTag tag = custom.save();
        check(tag.getInt("version") == 2 && BrewingPotionData.read(tag).orElseThrow().customEffects().equals(custom.customEffects()), "Pure world potion must roundtrip v2");
        tag.getList("custom_effects", Tag.TAG_COMPOUND).getCompound(0).putString("id", "forbidden_hex:missing_effect");
        check(BrewingPotionData.read(tag).isEmpty(), "Unknown custom effect must be rejected");
        tag = custom.save(); tag.remove("effect_costs");
        check(BrewingPotionData.read(tag).isEmpty(), "v2 cannot use legacy repair path");
        tag = custom.save(); tag.putInt("capacity", 0);
        check(BrewingPotionData.read(tag).isEmpty(), "NBT cannot bypass effect capacity");
        BrewingPotionData old = new BrewingPotionData(1, 1, 1, BrewingPotionData.Delivery.DRINK, false, false,
                List.of(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100)));
        CompoundTag legacy = old.save(); legacy.remove("effect_costs");
        check(legacy.getInt("version") == 1 && BrewingPotionData.read(legacy).isPresent(), "Existing v1 potion without effect costs must remain readable");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void environmentRunsWithoutNearbyLivingTargets(GameTestHelper helper) {
        BlockPos local = new BlockPos(5, 3, 5);
        helper.setBlock(local, Blocks.COBBLESTONE);
        Vec3 origin = Vec3.atCenterOf(helper.absolutePos(local));
        BrewingPotionExecutor.applyWorld(data(BrewingEffectType.CRUSH), helper.getLevel(), origin, null);
        helper.assertBlockPresent(Blocks.GRAVEL, local);
        BrewingPotionExecutor.applyWorld(data(BrewingEffectType.CONDENSE), helper.getLevel(), origin, null);
        helper.assertBlockPresent(Blocks.COBBLESTONE, local);
        helper.setBlock(local, Blocks.GOLD_ORE);
        helper.setBlock(local.east(), Blocks.CHEST);
        helper.setBlock(local.west(), Blocks.STONE);
        BrewingPotionExecutor.applyWorld(data(BrewingEffectType.EROSION), helper.getLevel(), origin, null);
        helper.assertBlockPresent(Blocks.GOLD_ORE, local);
        helper.assertBlockPresent(Blocks.CHEST, local.east());
        helper.assertBlockPresent(Blocks.AIR, local.west());
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void temporaryWebRestoresAfterSavedDataReload(GameTestHelper helper) {
        BlockPos local = new BlockPos(5, 3, 5);
        BlockPos pos = helper.absolutePos(local);
        BrewingWorldEffects.apply(BrewingEffectType.WEB, helper.getLevel(), Vec3.atBottomCenterOf(pos), null, 1, 1, -1);
        helper.assertBlockPresent(Blocks.COBWEB, local);
        BrewingWorldRestoration live = BrewingWorldRestoration.get(helper.getLevel());
        CompoundTag saved = live.save(new CompoundTag());
        var entries = saved.getList("Entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) entries.getCompound(i).putLong("Expires", helper.getLevel().getGameTime() - 1);
        live.forget(pos); live.forget(pos.above());
        BrewingWorldRestoration restored = BrewingWorldRestoration.load(saved);
        restored.tick(helper.getLevel());
        helper.assertBlockPresent(Blocks.AIR, local);
        helper.assertBlockPresent(Blocks.AIR, local.above());
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void cloudFirstApplicationPersistsAcrossEntityReload(GameTestHelper helper) {
        Sheep sheep = helper.spawn(EntityType.SHEEP, new BlockPos(5, 3, 5));
        sheep.setNoAi(true); sheep.setNoGravity(true); sheep.setHealth(5);
        BrewingEffectCloud cloud = BrewingEntities.CLOUD.get().create(helper.getLevel());
        cloud.setPos(sheep.getX(), sheep.getY(), sheep.getZ()); cloud.setRadius(3); cloud.setRadiusOnUse(0); cloud.setWaitTime(0); cloud.setDuration(200);
        cloud.setBrewingPotion(data(BrewingEffectType.TWILIGHT));
        for (int i = 0; i < 5; i++) cloud.tick();
        check(sheep.getHealth() == 8, "Twilight must heal exactly three on first cloud exposure");
        sheep.setHealth(5);
        for (int i = 0; i < 20; i++) cloud.tick();
        check(sheep.getHealth() == 5, "Cloud reapplication cannot heal again");
        CompoundTag saved = cloud.saveWithoutId(new CompoundTag());
        BrewingEffectCloud reloaded = BrewingEntities.CLOUD.get().create(helper.getLevel());
        reloaded.load(saved);
        for (int i = 0; i < 20; i++) reloaded.tick();
        check(sheep.getHealth() == 5, "Reload cannot reset cloud exposure history");
        cloud.discard(); reloaded.discard();
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void lingeringImmunityBlocksVanillaCloudAndDragonBreath(GameTestHelper helper) {
        Sheep immune = helper.spawn(EntityType.SHEEP, new BlockPos(5, 3, 5));
        Sheep normal = helper.spawn(EntityType.SHEEP, new BlockPos(6, 3, 5));
        immune.setNoAi(true); normal.setNoAi(true); immune.setNoGravity(true); normal.setNoGravity(true);
        BrewingPotionExecutor.applyEntity(data(BrewingEffectType.LINGERING_IMMUNITY), immune, immune, immune, 1, true);
        AreaEffectCloud cloud = new AreaEffectCloud(helper.getLevel(), immune.getX(), immune.getY(), immune.getZ());
        cloud.setRadius(3); cloud.setRadiusOnUse(0); cloud.setWaitTime(0); cloud.setDuration(100);
        cloud.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
        for (int i = 0; i < 5; i++) cloud.tick();
        check(!immune.hasEffect(MobEffects.POISON) && normal.hasEffect(MobEffects.POISON), "Cloud immunity must block vanilla cloud while normal target is affected");
        float health = immune.getHealth();
        immune.hurt(helper.getLevel().damageSources().dragonBreath(), 2);
        check(immune.getHealth() == health, "Lingering immunity must block dragon breath damage");
        cloud.discard();
        helper.succeed();
    }
}
