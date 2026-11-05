package com.example.akaishi.forbidden.forge.altar;

import com.example.akaishi.forbidden.altar.SourceAltarBlockEntity;
import com.example.akaishi.forbidden.altar.SourceAltarContent;
import com.example.akaishi.forbidden.altar.SourceAltarRules;
import com.example.akaishi.forbidden.altar.SourceAltarStructure;
import com.example.akaishi.forbidden.altar.SourceEnergyNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder("forbidden_hex")
@PrefixGameTestTemplate(false)
public final class SourceAltarListenerGameTests {
    private static final BlockPos ORIGIN = new BlockPos(3, 3, 3);
    private static final BlockPos MARKER = new BlockPos(12, 6, 10);
    private SourceAltarListenerGameTests() { }

    @GameTest(template = "source_altar_empty")
    public static void removalRetainsOverflowAndNextHighest(GameTestHelper helper) {
        var values = new SourceAltarRules.Accumulator();
        for (int i = 0; i < 51; i++) values.environment(Blocks.GRASS_BLOCK.defaultBlockState());
        values.environment(Blocks.GRASS_BLOCK.defaultBlockState(), -1);
        helper.assertTrue(values.finish(List.of()).capacityUnits() == 300, "Removing overflow reduced capped contribution");
        values.environment(Blocks.GRASS_BLOCK.defaultBlockState(), -1);
        helper.assertTrue(values.finish(List.of()).capacityUnits() == 298, "Raw count did not fall below quota");
        values.environment(Blocks.PLAYER_HEAD.defaultBlockState());
        values.environment(Blocks.SKELETON_SKULL.defaultBlockState());
        values.environment(Blocks.END_ROD.defaultBlockState());
        values.environment(Blocks.LANTERN.defaultBlockState());
        values.environment(Blocks.PLAYER_HEAD.defaultBlockState(), -1);
        values.environment(Blocks.END_ROD.defaultBlockState(), -1);
        var result = values.finish(List.of());
        helper.assertTrue(result.headMultiplier() == 2 && result.lightMultiplier() == 2,
                "Removing maximum did not fall back to remaining head and light");
        helper.assertTrue(!SourceEnergyNetwork.relevantChange(Blocks.WHEAT.defaultBlockState(),
                Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7)), "Age-only changes should not enqueue contribution work");
        helper.succeed();
    }
    @GameTest(template = "source_altar_empty")
    public static void blockWritesUpdateOnlyOnePosition(GameTestHelper helper) {
        helper.setBlock(MARKER, Blocks.AIR);
        var altar = form(helper);
        completeScan(helper, altar, 8192);
        long before = altar.snapshot().capacityUnits();
        long increment = 3L * altar.snapshot().headMultiplier() * altar.snapshot().implementHalves();
        helper.setBlock(MARKER, Blocks.CRYING_OBSIDIAN);
        helper.assertTrue(!altar.isOperational(), "Actual setBlock did not notify the listener");
        helper.assertTrue(altar.scanStep(1) == 1 && altar.isOperational(), "Single change triggered more than one-position work");
        helper.assertTrue(altar.snapshot().capacityUnits() == before + increment, "Placed block was not included incrementally");
        helper.setBlock(MARKER, Blocks.AIR);
        helper.setBlock(MARKER, Blocks.CRYING_OBSIDIAN);
        helper.setBlock(MARKER, Blocks.AIR);
        helper.assertTrue(altar.scanStep(1) == 1 && altar.isOperational(), "Repeated writes were not coalesced by position");
        helper.assertTrue(altar.snapshot().capacityUnits() == before, "Final removed state left stale contribution");
        helper.succeed();
    }
    @GameTest(template = "source_altar_empty")
    public static void changesDuringInitializationAreNotLost(GameTestHelper helper) {
        helper.setBlock(MARKER, Blocks.AIR);
        var altar = form(helper);
        helper.assertTrue(altar.scanStep(1) == 1 && !altar.isOperational(), "Initialization did not remain budgeted");
        helper.setBlock(MARKER, Blocks.CRYING_OBSIDIAN);
        completeScan(helper, altar, 257);
        long withMarker = altar.snapshot().capacityUnits();
        long increment = 3L * altar.snapshot().headMultiplier() * altar.snapshot().implementHalves();
        helper.setBlock(MARKER, Blocks.AIR);
        helper.assertTrue(altar.scanStep(1) == 1 && altar.isOperational(), "Post-initialization removal was not incremental");
        helper.assertTrue(altar.snapshot().capacityUnits() == withMarker - increment, "Change during initialization was lost or double-counted");
        helper.succeed();
    }
    @GameTest(template = "source_altar_empty")
    public static void daggerUsesCachedOuterHeight(GameTestHelper helper) {
        BlockPos outer = ORIGIN.offset(8, 11, 8);
        helper.setBlock(outer, Blocks.AIR);
        var altar = form(helper);
        completeScan(helper, altar, 8192);
        long before = altar.snapshot().capacityUnits();
        helper.setBlock(outer, Blocks.CRYING_OBSIDIAN);
        helper.assertTrue(altar.scanStep(1) == 1 && altar.snapshot().capacityUnits() == before,
                "Outer height contributed without dagger");
        helper.assertTrue(altar.putDisplay(new ItemStack(SourceAltarContent.item("ritual_dagger"))), "Could not display dagger");
        helper.assertTrue(altar.scanStep(1) == 0 && altar.isOperational() && altar.snapshot().height() == 14,
                "Dagger addition restarted a world scan");
        long extended = altar.snapshot().capacityUnits();
        long increment = 3L * altar.snapshot().headMultiplier() * altar.snapshot().implementHalves();
        helper.assertTrue(extended >= before + increment, "Pre-cached outer position was not available");
        altar.takeDisplay();
        helper.assertTrue(altar.scanStep(1) == 0 && altar.isOperational() && altar.snapshot().height() == 10
                && altar.snapshot().capacityUnits() == before, "Dagger removal failed to return to inner cached values");
        helper.setBlock(outer, Blocks.AIR);
        helper.succeed();
    }
    @GameTest(template = "source_altar_empty")
    public static void chunkVisibilityRemovesAndRebuildsOnlyItsCache(GameTestHelper helper) {
        helper.setBlock(MARKER, Blocks.CRYING_OBSIDIAN);
        var altar = form(helper);
        completeScan(helper, altar, 8192);
        long before = altar.snapshot().capacityUnits();
        long increment = 3L * altar.snapshot().headMultiplier() * altar.snapshot().implementHalves();
        BlockPos marker = helper.absolutePos(MARKER);
        int x = marker.getX() >> 4;
        int z = marker.getZ() >> 4;
        SourceEnergyNetwork.chunkChanged(helper.getLevel(), x, z, false);
        helper.assertTrue(!altar.isOperational(), "Unloaded chunk left stale energy available");
        completeScan(helper, altar, 257);
        helper.assertTrue(altar.snapshot().capacityUnits() <= before - increment, "Chunk contribution was not removed");
        helper.setBlock(MARKER, Blocks.AIR);
        helper.assertTrue(altar.scanStep(1) == 1 && altar.isOperational(), "Hidden chunk event exceeded single-position budget");
        SourceEnergyNetwork.chunkChanged(helper.getLevel(), x, z, true);
        completeScan(helper, altar, 257);
        long afterReload = altar.snapshot().capacityUnits();
        helper.setBlock(MARKER, Blocks.CRYING_OBSIDIAN);
        helper.assertTrue(altar.scanStep(1) == 1 && altar.isOperational(), "Reloaded chunk did not resume incremental listening");
        helper.assertTrue(altar.snapshot().capacityUnits() == before && afterReload == before - increment,
                "Chunk reload retained stale data or duplicated contributions");
        helper.setBlock(MARKER, Blocks.AIR);
        helper.succeed();
    }
    @GameTest(template = "source_altar_empty", batch = "source_altar_listener_idle", timeoutTicks = 400)
    public static void idleNeverRestartsPeriodicScan(GameTestHelper helper) {
        SourceAltarBlockEntity[] source = new SourceAltarBlockEntity[1];
        helper.startSequence().thenIdle(80).thenExecute(() -> {
            source[0] = form(helper);
            completeScan(helper, source[0], 8192);
        }).thenExecuteFor(220, () -> helper.assertTrue(source[0].scanStep(8192) == 0 && source[0].isOperational(),
                "Idle altar restarted periodic scan")).thenSucceed();
    }
    private static SourceAltarBlockEntity form(GameTestHelper helper) {
        for (int i = 0; i < 6; i++) helper.setBlock(ORIGIN.offset(i % 3, 0, i / 3), SourceAltarContent.SOURCE_ALTAR.get());
        BlockPos pos = helper.absolutePos(ORIGIN);
        SourceAltarStructure.refresh(helper.getLevel(), pos);
        var altar = (SourceAltarBlockEntity) helper.getLevel().getBlockEntity(pos);
        helper.assertTrue(altar != null && altar.isMaster(), "Altar did not form");
        return altar;
    }
    private static void completeScan(GameTestHelper helper, SourceAltarBlockEntity altar, int budget) {
        for (int i = 0; i < 8192 && !altar.isOperational(); i++) {
            int used = altar.scanStep(budget);
            helper.assertTrue(used >= 0 && used <= budget, "Listener work exceeded shared position budget");
        }
        helper.assertTrue(altar.isOperational(), "Listener cache did not finish within bounded work");
    }
}
