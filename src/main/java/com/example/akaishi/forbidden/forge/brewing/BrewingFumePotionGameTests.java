package com.example.akaishi.forbidden.forge.brewing;

import com.example.akaishi.forbidden.brewing.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static com.example.akaishi.forbidden.brewing.BrewingEffectType.*;

@GameTestHolder("forbidden_hex")
@PrefixGameTestTemplate(false)
public final class BrewingFumePotionGameTests {
    private BrewingFumePotionGameTests() { }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void close(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < .001, message + ": " + actual + " expected " + expected);
    }

    private static BrewingPotionData potion(int strength, BrewingEffectType... types) {
        List<BrewingEffectEntry> entries = java.util.Arrays.stream(types).map(BrewingEffectEntry::new).toList();
        return new BrewingPotionData(64, strength, 1, BrewingPotionData.Delivery.DRINK, false, false,
                List.of(), List.of(), entries);
    }

    private static void apply(net.minecraft.world.entity.LivingEntity target, int strength, BrewingEffectType... types) {
        BrewingPotionExecutor.applyEntity(potion(strength, types), target, target, target, 1, true);
    }

    private static CrucibleBlockEntity crucible(GameTestHelper helper) {
        helper.setBlock(new BlockPos(4, 1, 4), Blocks.NETHERRACK);
        helper.setBlock(new BlockPos(4, 2, 4), Blocks.FIRE);
        helper.setBlock(new BlockPos(4, 3, 4), Blocks.AIR);
        helper.setBlock(new BlockPos(4, 3, 4), BrewingBlocks.CRUCIBLE.get());
        CrucibleBlockEntity crucible = (CrucibleBlockEntity) helper.getBlockEntity(new BlockPos(4, 3, 4));
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("BrewingVersion", 1);
        legacy.putBoolean("SlotsEnabled", false);
        crucible.load(legacy);
        check(crucible.fillWater(1000, true) == 1000, "Fill crucible");
        return crucible;
    }

    private static List<ItemStack> complete(GameTestHelper helper, CrucibleBlockEntity crucible) {
        check(crucible.commitRecipe(), "Complete brew");
        for (int tick = 0; tick < 100; tick++) {
            CrucibleBlockEntity.tick(helper.getLevel(), crucible.getBlockPos(), crucible.getBlockState(), crucible);
        }
        return crucible.takeOutputs();
    }

    private static void budget(CrucibleBlockEntity crucible) {
        check(crucible.accept(new ItemStack(Items.NETHER_WART)), "Add two capacity");
        check(crucible.accept(new ItemStack(Items.NETHER_STAR)), "Establish six capacity with a distinct modifier");
    }

    @GameTest(template = "brewing_empty", timeoutTicks = 40)
    public static void allTwentyTwoFumesUseTheirDeclaredEffectBudget(GameTestHelper helper) {
        int index = 0;
        for (var kind : BrewingFumes.NON_EVIL) {
            CrucibleBlockEntity c = crucible(helper);
            budget(c);
            BrewingEffectType type = BrewingFumes.effect(kind);
            check(type != null && type.cost() == (index < 8 ? 3 : index < 13 ? 4 : 6), "Fume tier mapping");
            check(c.accept(BrewingFumeItem.create(kind)), "Accept fume " + kind);
            List<ItemStack> bottles = complete(helper, c);
            check(bottles.size() == 4, "Exactly four bottles from fume brew");
            for (ItemStack bottle : bottles) {
                BrewingPotionData data = BrewingPotionData.read(bottle).orElseThrow();
                check(data.customEffects().equals(List.of(new BrewingEffectEntry(type))), "Brew correct fume effect");
                check(data.capacity() == 6 && data.usedCapacity() == type.cost(), "Capacity remains separate from effect cost");
            }
            index++;
        }
        check(index == 22, "All twenty-two established fumes covered");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty", timeoutTicks = 40)
    public static void pendingEvilAndUnderBudgetFumesAreNotConsumed(GameTestHelper helper) {
        CrucibleBlockEntity c = crucible(helper);
        ItemStack evil = BrewingFumeItem.create(BrewingFumes.EVIL_BREATH);
        check(!c.accept(evil) && evil.getCount() == 1 && c.steps().isEmpty(), "Undesigned evil breath rejected without loss");
        check(!c.accept(BrewingFumeItem.create(BrewingFumes.HEARTBEAT_BREATH)), "Six cost fume cannot fit base two capacity");
        check(c.steps().isEmpty(), "Budget rejection does not mutate the pot");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty", timeoutTicks = 40)
    public static void fumeConversionsRecomputeCapacityAndUndoAcrossSave(GameTestHelper helper) {
        BrewingEffectType[] from = {COLD_RESISTANCE, DRUG_RESISTANCE, STRONG_HEART, LUCIDITY};
        BrewingEffectType[] to = {COLD_SUSCEPTIBILITY, DRUG_SENSITIVITY, WITHERING, HALLUCINATION};
        var kinds = List.of(BrewingFumes.WINTER_BREATH, BrewingFumes.WATER_MIRROR_BREATH,
                BrewingFumes.HEARTBEAT_BREATH, BrewingFumes.BRIGHT_BREATH);
        for (int i = 0; i < from.length; i++) {
            CrucibleBlockEntity c = crucible(helper);
            budget(c);
            check(c.accept(BrewingFumeItem.create(kinds.get(i))), "Add convertible fume");
            check(c.accept(new ItemStack(Items.GLOWSTONE)), "Set strength");
            check(c.accept(new ItemStack(Items.REDSTONE)), "Set duration");
            check(c.accept(new ItemStack(Items.FERMENTED_SPIDER_EYE)), "Convert latest fume after modifiers");
            check(!c.accept(new ItemStack(Items.FERMENTED_SPIDER_EYE)), "Fume converts only once");
            CompoundTag saved = c.saveWithoutMetadata();
            c.load(saved);
            BrewingPotionData data = BrewingPotionData.read(complete(helper, c).get(0)).orElseThrow();
            check(data.customEffects().get(0).type() == to[i] && data.strength() == 2 && data.duration() == 2,
                    "Converted fume and modifiers survive saving");
            c.load(saved);
            check(c.accept(new ItemStack(BrewingItems.GRASS_ASH.get())), "Grass ash removes conversion");
            BrewingPotionData undone = BrewingPotionData.read(complete(helper, c).get(0)).orElseThrow();
            check(undone.customEffects().get(0).type() == from[i], "Grass ash restores original fume");
        }
        CrucibleBlockEntity c = crucible(helper);
        check(c.accept(new ItemStack(Items.NETHER_STAR)), "Capacity four");
        check(c.accept(BrewingFumeItem.create(BrewingFumes.WHITE_THORN_BREATH)), "Hardening consumes four");
        check(!c.accept(BrewingFumeItem.create(BrewingFumes.EARTH_BREATH)), "Combined fume cost cannot exceed budget");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty", timeoutTicks = 40)
    public static void mendingPrecedesInstantHealingRegardlessOfIngredientOrder(GameTestHelper helper) {
        var first = helper.spawn(EntityType.COW, new BlockPos(3, 3, 3));
        var second = helper.spawn(EntityType.COW, new BlockPos(6, 3, 3));
        first.setHealth(1); second.setHealth(1);
        apply(first, 1, HEALING, MENDING);
        apply(second, 1, MENDING, HEALING);
        close(first.getHealth(), 5.8, "Healing benefits from mending in the same bottle");
        close(second.getHealth(), first.getHealth(), "Ingredient ordering cannot change healing");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty", timeoutTicks = 40)
    public static void strongHeartAndDepletionRestoreCapsWithoutFreeHealing(GameTestHelper helper) {
        var cow = helper.spawn(EntityType.COW, new BlockPos(4, 3, 4));
        cow.setHealth(5);
        apply(cow, 5, STRONG_HEART);
        close(cow.getMaxHealth(), 13, "Strong heart increases maximum health");
        close(cow.getHealth(), 5, "Strong heart does not grant free healing");
        cow.setHealth(13);
        cow.removeEffect(BrewingStatusEffects.get(STRONG_HEART));
        close(cow.getHealth(), 10, "Expiration clamps health to restored cap");
        apply(cow, 5, WITHERING);
        close(cow.getMaxHealth(), 7, "Depletion reduces health cap");
        close(cow.getHealth(), 7, "Depletion clamps without killing");
        cow.removeEffect(BrewingStatusEffects.get(WITHERING));
        close(cow.getMaxHealth(), 10, "Depletion restores original cap");
        close(cow.getHealth(), 7, "Restoring cap does not heal");
        apply(cow, 1, STRONG_HEART);
        apply(cow, 1, WITHERING);
        check(!BrewingStatusEffects.has(cow, STRONG_HEART) && BrewingStatusEffects.has(cow, WITHERING), "Health cap effects mutually replace");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty", timeoutTicks = 40)
    public static void drugResistanceAndSensitivityAffectOnlyNewDuration(GameTestHelper helper) {
        var cow = helper.spawn(EntityType.COW, new BlockPos(4, 3, 4));
        cow.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600));
        apply(cow, 1, DRUG_RESISTANCE);
        check(cow.getEffect(MobEffects.WEAKNESS).getDuration() == 600, "Existing effects unchanged");
        cow.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0, true, false, false));
        var poison = cow.getEffect(MobEffects.POISON);
        check(poison.getDuration() == 160 && poison.isAmbient() && !poison.isVisible() && !poison.showIcon(), "New harmful duration and flags");
        cow.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200));
        check(cow.getEffect(MobEffects.REGENERATION).getDuration() == 200, "Beneficial effects unchanged");
        apply(cow, 1, DRUG_SENSITIVITY);
        cow.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200));
        check(cow.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getDuration() == 250, "Sensitivity lengthens new duration");
        check(!BrewingStatusEffects.has(cow, DRUG_RESISTANCE), "Drug responses are mutually exclusive");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty", timeoutTicks = 40)
    public static void sobrietyAndLucidityPreventTheirSpecificAilments(GameTestHelper helper) {
        var cow = helper.spawn(EntityType.COW, new BlockPos(4, 3, 4));
        apply(cow, 1, DRUNK, SLUMBER);
        apply(cow, 1, SOBRIETY);
        check(!BrewingStatusEffects.has(cow, DRUNK) && !BrewingStatusEffects.has(cow, SLUMBER), "Sobriety clears drunkenness and sleep");
        apply(cow, 1, DRUNK, SLUMBER);
        check(!BrewingStatusEffects.has(cow, DRUNK) && !BrewingStatusEffects.has(cow, SLUMBER), "Sobriety prevents new exposure");
        cow.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 200));
        cow.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 200));
        apply(cow, 1, HALLUCINATION, LUCIDITY);
        check(!cow.hasEffect(MobEffects.BLINDNESS) && !cow.hasEffect(MobEffects.DARKNESS) && !BrewingStatusEffects.has(cow, HALLUCINATION), "Lucidity clears visual ailments");
        cow.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 200));
        apply(cow, 1, BLINDNESS, HALLUCINATION);
        check(!cow.hasEffect(MobEffects.DARKNESS) && !cow.hasEffect(MobEffects.BLINDNESS)
                && !BrewingStatusEffects.has(cow, HALLUCINATION), "Lucidity prevents visual ailments");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty", timeoutTicks = 40)
    public static void damageWakesAndRepeatedExposureCannotExtendSleep(GameTestHelper helper) {
        var cow = helper.spawn(EntityType.COW, new BlockPos(4, 3, 4));
        apply(cow, 1, SLUMBER);
        check(BrewingStatusEffects.has(cow, SLUMBER), "Sleep applies");
        int duration = cow.getEffect(BrewingStatusEffects.get(SLUMBER)).getDuration();
        apply(cow, 5, SLUMBER);
        check(cow.getEffect(BrewingStatusEffects.get(SLUMBER)).getDuration() == duration, "Reapplication cannot refresh control");
        cow.hurt(helper.getLevel().damageSources().generic(), 1);
        check(!BrewingStatusEffects.has(cow, SLUMBER), "Damage wakes sleeper");
        apply(cow, 1, SLUMBER);
        check(!BrewingStatusEffects.has(cow, SLUMBER), "Wake-up immunity blocks repeated exposure");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty", timeoutTicks = 40)
    public static void playerAndBossControlsRemainLimited(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        apply(player, 5, DRUG_SENSITIVITY);
        apply(player, 5, SLUMBER);
        check(player.getEffect(BrewingStatusEffects.get(SLUMBER)).getDuration() <= 60, "Player sleep capped after sensitivity");
        double sleepingSpeed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        player.setSprinting(true);
        check(!player.isSprinting(), "Sleep blocks actual sprint input");
        close(player.getAttributeValue(Attributes.MOVEMENT_SPEED), sleepingSpeed, "Blocked sprint cannot install a sprint speed modifier");
        player.removeEffect(BrewingStatusEffects.get(SLUMBER));
        player.setSprinting(true);
        check(player.isSprinting(), "Control removal restores sprint input");
        apply(player, 1, SEDATION);
        check(!BrewingStatusEffects.has(player, SEDATION), "Sedation does not control players");
        var wither = EntityType.WITHER.create(helper.getLevel());
        check(wither != null, "Create boss for immunity check");
        apply(wither, 5, SLUMBER, SEDATION, CONVULSIONS);
        check(!BrewingStatusEffects.has(wither, SLUMBER) && !BrewingStatusEffects.has(wither, SEDATION)
                && !BrewingStatusEffects.has(wither, CONVULSIONS), "Boss immune to movement controls");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty", timeoutTicks = 40)
    public static void badBloodRetainsLimitedHealingAndMendingCanCounteractIt(GameTestHelper helper) {
        var cow = helper.spawn(EntityType.COW, new BlockPos(4, 3, 4));
        cow.setHealth(1);
        apply(cow, 5, BAD_BLOOD);
        cow.heal(4);
        close(cow.getHealth(), 1.8, "Bad blood retains twenty percent of healing");
        apply(cow, 1, MENDING);
        cow.heal(4);
        close(cow.getHealth(), 2.76, "Mending and bad blood multiply independently");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty", timeoutTicks = 40)
    public static void lifestealUsesActualMeleeDamageAndCooldown(GameTestHelper helper) {
        var attacker = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 3, 3));
        var victim = helper.spawn(EntityType.COW, new BlockPos(6, 3, 3));
        attacker.setNoAi(true); victim.setNoAi(true);
        attacker.setHealth(5);
        apply(attacker, 5, LIFESTEAL);
        victim.hurt(helper.getLevel().damageSources().magic(), 2);
        close(attacker.getHealth(), 5, "Unowned magic cannot trigger lifesteal");
        victim.invulnerableTime = 0;
        victim.hurt(helper.getLevel().damageSources().mobAttack(attacker), 4);
        close(attacker.getHealth(), 5.8, "Only actual direct melee damage heals");
        victim.invulnerableTime = 0;
        victim.hurt(helper.getLevel().damageSources().mobAttack(attacker), 2);
        close(attacker.getHealth(), 5.8, "Lifesteal cooldown prevents repeated healing");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty", timeoutTicks = 80)
    public static void fumeCloudDoesNotRepeatedlyScaleOrRefreshControls(GameTestHelper helper) {
        var cow = helper.spawn(EntityType.COW, new BlockPos(4, 3, 4));
        cow.setNoAi(true); cow.setNoGravity(true);
        apply(cow, 1, DRUG_RESISTANCE);
        BrewingPotionData data = new BrewingPotionData(3, 1, 1, BrewingPotionData.Delivery.LINGERING,
                false, false, List.of(), List.of(), List.of(new BrewingEffectEntry(SLUMBER)));
        BrewingEffectCloud cloud = new BrewingEffectCloud(BrewingEntities.CLOUD.get(), helper.getLevel());
        cloud.setPos(cow.position());
        cloud.setWaitTime(0);
        cloud.setRadius(2);
        cloud.setRadiusOnUse(0);
        cloud.setDuration(100);
        cloud.setBrewingPotion(data);
        helper.getLevel().addFreshEntity(cloud);
        helper.runAtTickTime(12, () -> {
            check(BrewingStatusEffects.has(cow, SLUMBER), "Cloud applies sleep");
            int duration = cow.getEffect(BrewingStatusEffects.get(SLUMBER)).getDuration();
            check(duration > 110 && duration <= 128, "Drug resistance scales new cloud exposure once, not repeatedly");
            cow.hurt(helper.getLevel().damageSources().generic(), 1);
        });
        helper.runAtTickTime(30, () -> {
            check(!BrewingStatusEffects.has(cow, SLUMBER), "Cloud cannot immediately reapply sleep after waking");
            cloud.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "brewing_empty", timeoutTicks = 40)
    public static void fumeAttributesAndColdProtectionCleanUp(GameTestHelper helper) {
        var cow = helper.spawn(EntityType.COW, new BlockPos(4, 3, 4));
        double originalSpeed = cow.getAttributeValue(Attributes.MOVEMENT_SPEED);
        apply(cow, 5, TENACITY, HARDENING);
        close(cow.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), .8, "Tenacity fifth tier");
        close(cow.getAttributeValue(Attributes.ARMOR), 6, "Hardening armor");
        close(cow.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 3, "Hardening toughness");
        check(cow.getAttributeValue(Attributes.MOVEMENT_SPEED) < originalSpeed, "Hardening movement cost");
        cow.removeEffect(BrewingStatusEffects.get(HARDENING));
        close(cow.getAttributeValue(Attributes.MOVEMENT_SPEED), originalSpeed, "Movement modifier removed cleanly");
        cow.setTicksFrozen(100);
        apply(cow, 1, COLD_RESISTANCE);
        check(cow.getTicksFrozen() == 0, "Cold protection clears freezing");
        apply(cow, 1, COLD_SUSCEPTIBILITY);
        check(!BrewingStatusEffects.has(cow, COLD_SUSCEPTIBILITY), "Cold protection prevents susceptibility");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty", timeoutTicks = 40)
    public static void bloodRushExhaustionAndBoilingBloodMeleeUseRealInputs(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        player.getFoodData().setExhaustion(0);
        player.causeFoodExhaustion(1);
        float baseline = player.getFoodData().getExhaustionLevel();
        apply(player, 5, BLOOD_RUSH);
        player.getFoodData().setExhaustion(0);
        player.causeFoodExhaustion(1);
        close(player.getFoodData().getExhaustionLevel(), baseline * 1.65, "Blood rush increases activity exhaustion");
        apply(player, 1, BOILING_BLOOD);
        var cow = helper.spawn(EntityType.COW, new BlockPos(4, 3, 4));
        cow.hurt(helper.getLevel().damageSources().indirectMagic(player, player), 1);
        check(!cow.isOnFire(), "Magic does not trigger boiling blood");
        cow.invulnerableTime = 0;
        cow.hurt(helper.getLevel().damageSources().playerAttack(player), 1);
        check(cow.getRemainingFireTicks() >= 40, "Actual melee ignites with boiling blood");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty", timeoutTicks = 40)
    public static void concealmentOnlyBlocksInitialHostileDetection(GameTestHelper helper) {
        var zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 3, 4));
        var cow = helper.spawn(EntityType.COW, new BlockPos(9, 3, 4));
        zombie.setNoAi(true); cow.setNoAi(true);
        zombie.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(10);
        zombie.setTarget(cow);
        apply(cow, 5, CONCEALMENT);
        zombie.setTarget(cow);
        check(zombie.getTarget() == cow, "Existing pursuit is retained");
        zombie.setTarget(null);
        zombie.setTarget(cow);
        check(zombie.getTarget() == null, "Concealment blocks new distant detection");
        zombie.invulnerableTime = 0;
        zombie.hurt(helper.getLevel().damageSources().mobAttack(cow), 1);
        zombie.setTarget(cow);
        check(zombie.getTarget() == cow, "Attacking briefly breaks concealment");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty", timeoutTicks = 80)
    public static void sleepingMobStopsActualTravelAndReleasesWithoutNoAiChanges(GameTestHelper helper) {
        var cow = helper.spawn(EntityType.COW, new BlockPos(4, 3, 4));
        cow.setNoGravity(true);
        cow.setNoAi(true);
        apply(cow, 1, SLUMBER);
        helper.runAtTickTime(45, () -> {
            var before = cow.position();
            check(cow.isNoAi(), "Slumber cannot change a preexisting no-AI flag");
            cow.setNoAi(false);
            cow.setDeltaMovement(.2, 0, .2);
            cow.travel(new net.minecraft.world.phys.Vec3(1, 0, 1));
            close(cow.position().distanceToSqr(before), 0, "Sleeping mob cannot move by real travel input");
            cow.removeEffect(BrewingStatusEffects.get(SLUMBER));
            check(!cow.isNoAi(), "Removal preserves the active AI flag");
            cow.setDeltaMovement(.2, 0, .2);
            cow.travel(new net.minecraft.world.phys.Vec3(1, 0, 1));
            check(cow.position().distanceToSqr(before) > .001, "Travel resumes after waking");
            helper.succeed();
        });
    }

    @GameTest(template = "brewing_empty", timeoutTicks = 160)
    public static void expiredControlsGrantTemporaryImmunityThenBecomeUsableAgain(GameTestHelper helper) {
        var cow = helper.spawn(EntityType.COW, new BlockPos(4, 3, 4));
        cow.setNoAi(true); cow.setNoGravity(true);
        cow.addEffect(new MobEffectInstance(BrewingStatusEffects.get(SEDATION), 2));
        helper.runAtTickTime(5, () -> {
            check(!BrewingStatusEffects.has(cow, SEDATION), "Sedation naturally expires");
            apply(cow, 1, SEDATION);
            check(!BrewingStatusEffects.has(cow, SEDATION), "Natural expiration grants temporary control immunity");
        });
        helper.runAtTickTime(110, () -> {
            apply(cow, 1, SEDATION);
            check(BrewingStatusEffects.has(cow, SEDATION), "Expired immunity allows a new exposure");
            helper.succeed();
        });
    }

    @GameTest(template = "brewing_empty", timeoutTicks = 120)
    public static void chillingPoisonRemainsNonlethalWithBerserkVulnerability(GameTestHelper helper) {
        var cow = helper.spawn(EntityType.COW, new BlockPos(4, 3, 4));
        cow.setNoAi(true); cow.setNoGravity(true); cow.setHealth(1.25F);
        apply(cow, 5, CHILLING_POISON, BERSERK);
        helper.runAtTickTime(90, () -> {
            check(cow.isAlive() && cow.getHealth() >= 1, "Chilling poison cannot kill through vulnerability multipliers");
            check(cow.getHealth() < 1.25F, "Chilling poison must still damage a vulnerable target");
            helper.succeed();
        });
    }
}
