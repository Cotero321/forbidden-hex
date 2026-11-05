package com.example.akaishi.forbidden.forge.brewing;

import com.example.akaishi.forbidden.brewing.BrewingEffectEntry;
import com.example.akaishi.forbidden.brewing.BrewingEffectType;
import com.example.akaishi.forbidden.brewing.BrewingPotionData;
import com.example.akaishi.forbidden.brewing.BrewingPotionExecutor;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder("forbidden_hex")
@PrefixGameTestTemplate(false)
public final class BrewingFumePerceptionGameTests {
    private BrewingFumePerceptionGameTests() { }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static Player observer(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        player.moveTo(helper.absolutePos(new BlockPos(2, 3, 4)), 0, 0);
        BrewingPotionData data = new BrewingPotionData(6, 1, 1, BrewingPotionData.Delivery.DRINK,
                false, false, List.of(), List.of(), List.of(new BrewingEffectEntry(BrewingEffectType.LUCIDITY)));
        BrewingPotionExecutor.applyEntity(data, player, player, player, 1, true);
        return player;
    }

    @GameTest(template = "brewing_empty", timeoutTicks = 40)
    public static void lucidityOnlyRevealsToTheAffectedObserver(GameTestHelper helper) {
        Player observer = observer(helper);
        Player other = helper.makeMockSurvivalPlayer();
        other.moveTo(observer.position());
        var sheep = helper.spawn(EntityType.SHEEP, new BlockPos(6, 3, 4));
        sheep.setNoAi(true);
        sheep.setNoGravity(true);
        sheep.setInvisible(true);
        check(!sheep.isInvisibleTo(observer), "Lucidity must reveal a nearby visible-line invisible entity");
        check(sheep.isInvisibleTo(other), "Lucidity cannot reveal the entity to unaffected observers");
        check(sheep.isInvisible(), "Perception must not change the entity's actual invisible state");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty", timeoutTicks = 40)
    public static void lucidityRespectsDistanceAndSolidWalls(GameTestHelper helper) {
        Player observer = observer(helper);
        var sheep = helper.spawn(EntityType.SHEEP, new BlockPos(6, 3, 4));
        sheep.setNoAi(true);
        sheep.setNoGravity(true);
        sheep.setInvisible(true);
        check(!sheep.isInvisibleTo(observer), "Baseline must have clear line of sight");
        for (int y = 2; y <= 7; y++) {
            for (int z = 2; z <= 6; z++) helper.setBlock(new BlockPos(4, y, z), Blocks.STONE);
        }
        check(sheep.isInvisibleTo(observer), "Lucidity cannot reveal through a solid wall");
        for (int y = 2; y <= 7; y++) {
            for (int z = 2; z <= 6; z++) helper.setBlock(new BlockPos(4, y, z), Blocks.AIR);
        }
        sheep.moveTo(helper.absolutePos(new BlockPos(9, 3, 4)), 0, 0);
        check(sheep.isInvisibleTo(observer), "Strength one lucidity cannot exceed its six-block range");
        helper.succeed();
    }
}
