package com.example.akaishi.forbidden.forge.brewing;

import com.example.akaishi.forbidden.brewing.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder("forbidden_hex")
@PrefixGameTestTemplate(false)
public final class BrewingPotionInputGameTests {
    private BrewingPotionInputGameTests() { }

    private static BrewingPotionData potion(BrewingEffectType type, BrewingPotionData.Delivery delivery) {
        return new BrewingPotionData(type.cost(), 1, 1, delivery, false, false,
                List.of(), List.of(), List.of(new BrewingEffectEntry(type)));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    @GameTest(template = "brewing_empty", timeoutTicks = 40)
    public static void drinkUsesExecutorAndReturnsBottle(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        player.moveTo(helper.absolutePos(new BlockPos(2, 2, 2)), 0, 0);
        ItemStack stack = new ItemStack(BrewingItems.POTION.get());
        potion(BrewingEffectType.GLOWING, BrewingPotionData.Delivery.DRINK).write(stack);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        check(stack.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getResult().consumesAction(), "Drink must start use");
        check(player.isUsingItem(), "Drink must enter vanilla use lifecycle");
        ItemStack result = stack.finishUsingItem(helper.getLevel(), player);
        check(result.is(Items.GLASS_BOTTLE) && stack.isEmpty(), "Drink must consume exactly one bottle and return glass");
        check(player.hasEffect(MobEffects.GLOWING), "Drink must execute the custom effect");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty", timeoutTicks = 40)
    public static void drunkMultipliesRealPlayerExhaustion(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        player.getFoodData().setExhaustion(0);
        player.causeFoodExhaustion(1);
        float normal = player.getFoodData().getExhaustionLevel();
        check(normal > 0, "Baseline player exhaustion must be active");
        player.getFoodData().setExhaustion(0);
        BrewingPotionData drunk = potion(BrewingEffectType.DRUNK, BrewingPotionData.Delivery.DRINK);
        BrewingPotionExecutor.applyEntity(drunk, player, player, player, 1, true);
        player.causeFoodExhaustion(1);
        check(Math.abs(player.getFoodData().getExhaustionLevel() - normal * 1.5F) < 0.0001F, "Drunk must multiply actual exhaustion by 1.5");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty", timeoutTicks = 40)
    public static void coatingOnlyTriggersForMeleeAndImmunityBlocksPoison(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        Sheep sheep = helper.spawn(EntityType.SHEEP, new BlockPos(2, 2, 2));
        sheep.setNoAi(true);
        BrewingPotionData coating = potion(BrewingEffectType.POISON_COATING, BrewingPotionData.Delivery.DRINK);
        BrewingPotionExecutor.applyEntity(coating, player, player, player, 1, true);
        sheep.hurt(helper.getLevel().damageSources().indirectMagic(player, player), 1);
        check(!sheep.hasEffect(MobEffects.POISON), "Magic damage cannot trigger poison coating");
        sheep.invulnerableTime = 0;
        sheep.hurt(helper.getLevel().damageSources().playerAttack(player), 1);
        check(sheep.hasEffect(MobEffects.POISON), "Successful player melee must trigger coating");
        BrewingPotionExecutor.applyEntity(potion(BrewingEffectType.ANTIDOTE, BrewingPotionData.Delivery.DRINK), sheep, sheep, sheep, 1, true);
        check(!sheep.hasEffect(MobEffects.POISON), "Antidote must immediately remove poison");
        sheep.invulnerableTime = 0;
        sheep.hurt(helper.getLevel().damageSources().playerAttack(player), 1);
        check(!sheep.hasEffect(MobEffects.POISON), "Antidote must prevent poison reapplication");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty", timeoutTicks = 40)
    public static void dyePayloadAndBleachUseActualDyeIds(GameTestHelper helper) {
        Sheep sheep = helper.spawn(EntityType.SHEEP, new BlockPos(2, 2, 2));
        BrewingPotionData dye = new BrewingPotionData(1, 1, 1, BrewingPotionData.Delivery.SPLASH, false, false,
                List.of(), List.of(), List.of(new BrewingEffectEntry(BrewingEffectType.DYE, DyeColor.RED.getId())));
        BrewingPotionExecutor.applyEntity(dye, sheep, null, null, 1, true);
        check(sheep.getColor() == DyeColor.RED, "Dye payload must preserve dye ID rather than treat it as RGB");
        check(sheep.getEffect(BrewingStatusEffects.get(BrewingEffectType.DYE)).getAmplifier() == DyeColor.RED.getId(), "Temporary color must synchronize using status payload");
        BrewingPotionExecutor.applyEntity(potion(BrewingEffectType.BLEACH, BrewingPotionData.Delivery.SPLASH), sheep, null, null, 1, true);
        check(sheep.getColor() == DyeColor.WHITE && !BrewingStatusEffects.has(sheep, BrewingEffectType.DYE), "Bleach must reset sheep and remove temporary tint");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty", timeoutTicks = 50)
    public static void thrownPotionUsesItemAndRealCollision(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        BlockPos origin = helper.absolutePos(new BlockPos(5, 3, 3));
        player.moveTo(origin.getX() + .5, origin.getY(), origin.getZ() + .5, 0, 0);
        for (int y = 3; y <= 6; y++) for (int x = 3; x <= 7; x++) helper.setBlock(new BlockPos(x, y, 7), net.minecraft.world.level.block.Blocks.STONE);
        Sheep target = helper.spawn(EntityType.SHEEP, new BlockPos(6, 3, 6));
        target.setNoAi(true); target.setNoGravity(true);
        ItemStack bottle = new ItemStack(BrewingItems.POTION.get());
        potion(BrewingEffectType.GLOWING, BrewingPotionData.Delivery.SPLASH).write(bottle);
        player.setItemInHand(InteractionHand.MAIN_HAND, bottle);
        check(bottle.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getResult().consumesAction(), "Throw must use actual potion item");
        check(bottle.isEmpty(), "Throw must consume exactly one potion");
        helper.runAfterDelay(20, () -> {
            check(target.hasEffect(MobEffects.GLOWING), "Real projectile collision must apply custom potion effect");
            check(helper.getLevel().getEntitiesOfClass(BrewingPotionProjectile.class, target.getBoundingBox().inflate(10)).isEmpty(), "Projectile must shatter after collision");
            helper.succeed();
        });
    }

    @GameTest(template = "brewing_empty")
    public static void waterWalkingUsesVanillaFluidCollisionAndAllowsSneaking(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        BlockPos local = new BlockPos(5, 3, 5);
        helper.setBlock(local, net.minecraft.world.level.block.Blocks.WATER);
        BlockPos pos = helper.absolutePos(local);
        player.moveTo(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5, 0, 0);
        var state = helper.getLevel().getBlockState(pos);
        check(state.getCollisionShape(helper.getLevel(), pos, net.minecraft.world.phys.shapes.CollisionContext.of(player)).isEmpty(), "Water must have no collision without effect");
        BrewingPotionExecutor.applyEntity(potion(BrewingEffectType.WATER_WALKING, BrewingPotionData.Delivery.DRINK), player, player, player, 1, true);
        check(!state.getCollisionShape(helper.getLevel(), pos, net.minecraft.world.phys.shapes.CollisionContext.of(player)).isEmpty(), "Water walking must create vanilla fluid collision");
        player.setShiftKeyDown(true);
        check(state.getCollisionShape(helper.getLevel(), pos, net.minecraft.world.phys.shapes.CollisionContext.of(player)).isEmpty(), "Sneaking must allow diving through water");
        helper.succeed();
    }
}
