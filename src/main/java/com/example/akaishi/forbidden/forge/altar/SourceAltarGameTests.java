package com.example.akaishi.forbidden.forge.altar;

import com.example.akaishi.forbidden.altar.OfferingPedestalBlockEntity;
import com.example.akaishi.forbidden.altar.SourceAltarBlock;
import com.example.akaishi.forbidden.altar.SourceAltarBlockEntity;
import com.example.akaishi.forbidden.altar.SourceAltarContent;
import com.example.akaishi.forbidden.altar.SourceAltarRules;
import com.example.akaishi.forbidden.altar.SourceAltarStructure;
import com.example.akaishi.forbidden.altar.SourceEnergyNetwork;
import com.example.akaishi.forbidden.brewing.BrewingPlants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder("forbidden_hex")
@PrefixGameTestTemplate(false)
public final class SourceAltarGameTests {
    private static final BlockPos ORIGIN = new BlockPos(3, 3, 3);
    private SourceAltarGameTests() { }
    private static ItemStack item(String name) { return new ItemStack(SourceAltarContent.item(name)); }

    @GameTest(template = "source_altar_empty")
    public static void independentQuotasAndVariants(GameTestHelper helper) {
        var values = new SourceAltarRules.Accumulator();
        for (int i = 0; i < 51; i++) {
            values.environment(Blocks.GRASS_BLOCK.defaultBlockState());
            values.environment(Blocks.OAK_LOG.defaultBlockState());
            values.environment(Blocks.OAK_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.X));
        }
        values.environment(Blocks.STRIPPED_OAK_LOG.defaultBlockState());
        helper.assertTrue(values.finish(List.of()).capacityUnits() == 500, "Per-species quota or log state normalization failed");
        var custom = new SourceAltarRules.Accumulator();
        for (int i = 0; i < 21; i++) custom.environment(BrewingPlants.logs().get("hawthorn").get().defaultBlockState());
        helper.assertTrue(custom.finish(List.of()).capacityUnits() == 320, "Custom tree was double-counted or exceeded twenty");
        var crop = new SourceAltarRules.Accumulator();
        for (int i = 0; i < 26; i++) crop.environment(Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, i % 8));
        helper.assertTrue(crop.finish(List.of()).capacityUnits() == 350, "Crop ages opened independent quotas");
        helper.succeed();
    }
    @GameTest(template = "source_altar_empty")
    public static void capacityAndExemptBonuses(GameTestHelper helper) {
        var values = new SourceAltarRules.Accumulator();
        for (int i = 0; i < 50; i++) { values.environment(Blocks.GRASS_BLOCK.defaultBlockState()); values.environment(Blocks.OAK_LOG.defaultBlockState()); }
        values.display(new ItemStack(Items.WITHER_SKELETON_SKULL));
        values.display(new ItemStack(Items.SKELETON_SKULL));
        values.display(item("chalice"));
        values.display(item("creeper_heart"));
        values.activeBeacon(); values.chargedAnchor();
        helper.assertTrue(values.finish(List.of()).capacityUnits() == 4700, "Expected capacity 2350 including exempt additions");
        var half = new SourceAltarRules.Accumulator();
        half.environment(Blocks.GRASS_BLOCK.defaultBlockState()); half.display(item("chalice"));
        helper.assertTrue(half.finish(List.of()).capacityUnits() == 303, "Capacity 151.5 was truncated");
        var capped = new SourceAltarRules.Accumulator();
        capped.display(new ItemStack(SourceAltarContent.item("creeper_heart"), 64));
        for (int i = 0; i < 8; i++) { capped.activeBeacon(); capped.chargedAnchor(); }
        helper.assertTrue(capped.finish(List.of()).capacityUnits() == 6200, "Exempt caps did not apply independently");
        helper.succeed();
    }
    @GameTest(template = "source_altar_empty")
    public static void recoveryAndDuplicateOfferings(GameTestHelper helper) {
        var values = new SourceAltarRules.Accumulator();
        values.environment(Blocks.LANTERN.defaultBlockState()); values.environment(Blocks.SOUL_LANTERN.defaultBlockState());
        var result = values.finish(List.of(new ItemStack(Items.HEART_OF_THE_SEA), new ItemStack(Items.NETHER_STAR),
                new ItemStack(Items.NETHER_STAR), item("abyssal_heart")));
        helper.assertTrue(result.recoveryUnits() == 54 && result.duplicateMask() == 4 && result.offeringMask() == 11,
                "Lights must take highest; unique offerings multiply and duplicate does not stack");
        helper.assertTrue(new SourceAltarRules.Accumulator().finish(List.of(new ItemStack(Items.HEART_OF_THE_SEA))).recoveryUnits() == 3,
                "Recovery 1.5 lost precision");
        helper.succeed();
    }
    @GameTest(template = "source_altar_empty")
    public static void structureDisplayAndRestore(GameTestHelper helper) {
        var altar = form(helper, ORIGIN);
        BlockPos center = helper.absolutePos(ORIGIN);
        for (int i = 0; i < 6; i++) {
            var member = (SourceAltarBlockEntity) helper.getLevel().getBlockEntity(center.offset(i % 3, 0, i / 3));
            helper.assertTrue(member.controller() == altar && (i == 0) == member.isMaster(), "Multiple controllers formed");
        }
        helper.assertTrue(altar.putDisplay(item("creeper_heart")) && altar.putDisplay(item("creeper_heart")), "Display stacking failed");
        helper.assertTrue(!altar.putDisplay(item("chalice")), "Mixed display stack accepted");
        CompoundTag saved = altar.saveWithoutMetadata();
        altar.load(saved);
        helper.assertTrue(altar.display().getCount() == 2, "Display inventory did not persist");
        ItemStack taken = altar.takeDisplay();
        helper.assertTrue(taken.getCount() == 2 && altar.takeDisplay().isEmpty(), "Display duplicated on retrieval");
        helper.setBlock(ORIGIN.offset(2, 0, 1), Blocks.AIR);
        helper.assertTrue(!altar.isOperational() && !altar.isFormed(), "Broken structure stayed active");
        helper.setBlock(ORIGIN.offset(2, 0, 1), SourceAltarContent.SOURCE_ALTAR.get());
        SourceAltarStructure.refresh(helper.getLevel(), center);
        helper.assertTrue(altar.isMaster(), "Structure did not reform with stable controller");
        helper.succeed();
    }
    @GameTest(template = "source_altar_empty")
    public static void pedestalValidationAndNbt(GameTestHelper helper) {
        BlockPos pos = new BlockPos(4, 3, 4);
        helper.setBlock(pos, SourceAltarContent.OFFERING_PEDESTAL.get());
        var pedestal = (OfferingPedestalBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        helper.assertTrue(!pedestal.putDisplay(new ItemStack(Items.DIRT)), "Invalid pedestal offering accepted");
        helper.assertTrue(pedestal.putDisplay(new ItemStack(Items.NETHER_STAR, 12)), "Valid offering rejected");
        helper.assertTrue(pedestal.display().getCount() == 1 && !pedestal.putDisplay(item("abyssal_heart")), "Pedestal holds more than one");
        CompoundTag saved = pedestal.saveWithoutMetadata(); pedestal.load(saved);
        helper.assertTrue(pedestal.takeDisplay().is(Items.NETHER_STAR) && pedestal.takeDisplay().isEmpty(), "Pedestal lost or duplicated saved offering");
        var altar = form(helper, new BlockPos(10, 3, 10));
        CompoundTag negative = new CompoundTag(); negative.putLong("SourceEnergyHalfUnits", -5); negative.putInt("RecoveryTicks", -100);
        altar.load(negative);
        helper.assertTrue(altar.energyUnits() == 0, "Negative saved source energy accepted");
        CompoundTag huge = new CompoundTag(); huge.putLong("SourceEnergyHalfUnits", Long.MAX_VALUE);
        altar.load(huge);
        helper.assertTrue(altar.energyUnits() == SourceAltarRules.MAX_UNITS && !altar.isOperational(), "Unverified saved energy was truncated or exposed");
        helper.succeed();
    }
    @GameTest(template = "source_altar_empty", timeoutTicks = 200)
    public static void scanBudgetAndAtomicSupply(GameTestHelper helper) {
        var altar = form(helper, ORIGIN);
        CompoundTag saved = new CompoundTag(); saved.putLong("SourceEnergyHalfUnits", 80); altar.load(saved);
        helper.assertTrue(!altar.consume(1, true) && altar.energyUnits() == 80, "Unscanned saved energy became usable");
        completeScan(helper, altar, 257);
        helper.assertTrue(altar.energyUnits() == 80, "First valid scan lost saved energy below capacity");
        helper.assertTrue(altar.consume(3, true) && altar.energyUnits() == 80, "Simulation deducted energy");
        helper.assertTrue(!altar.consume(81, false) && altar.energyUnits() == 80, "Insufficient request partially deducted");
        helper.assertTrue(altar.consume(79, false) && altar.energyUnits() == 1 && !altar.consume(2, false), "Continuous requests overdrew energy");
        SourceEnergyNetwork.add(helper.getLevel(), altar.getBlockPos());
        helper.assertTrue(SourceEnergyNetwork.request(helper.getLevel(), altar.getBlockPos(), 1, true) && altar.energyUnits() == 1, "Network simulation failed");
        helper.assertTrue(!SourceEnergyNetwork.request(helper.getLevel(), altar.getBlockPos(), 0, false), "Zero request accepted");
        BlockPos source = altar.getBlockPos();
        helper.assertTrue(SourceEnergyNetwork.inRange(source, source.offset(64, 10, 0), 10)
                && !SourceEnergyNetwork.inRange(source, source.offset(64, 0, 1), 10)
                && !SourceEnergyNetwork.inRange(source, source.offset(0, 11, 0), 10)
                && SourceEnergyNetwork.inRange(source, source.offset(0, 14, 0), 14), "Wireless range boundary failed");
        helper.setBlock(ORIGIN.offset(1, 0, 0), Blocks.AIR);
        helper.assertTrue(!altar.consume(1, false) && altar.energyUnits() == 1, "Broken structure supplied energy");
        helper.succeed();
    }
    @GameTest(template = "source_altar_empty", timeoutTicks = 200)
    public static void recoveryTimerPersists(GameTestHelper helper) {
        var altar = form(helper, ORIGIN);
        completeScan(helper, altar, 8192);
        long rate = altar.snapshot().recoveryUnits();
        for (int i = 0; i < 9; i++) SourceAltarBlockEntity.tick(helper.getLevel(), altar.getBlockPos(), altar.getBlockState(), altar);
        helper.assertTrue(altar.energyUnits() == 0, "Recovery arrived before twenty ticks");
        CompoundTag saved = altar.saveWithoutMetadata(); altar.load(saved); completeScan(helper, altar, 8192);
        for (int i = 0; i < 11; i++) SourceAltarBlockEntity.tick(helper.getLevel(), altar.getBlockPos(), altar.getBlockState(), altar);
        helper.assertTrue(altar.energyUnits() == Math.min(rate, altar.snapshot().capacityUnits()), "Partial recovery timer failed to survive reload");
        helper.succeed();
    }
    private static SourceAltarBlockEntity form(GameTestHelper helper, BlockPos origin) {
        for (int i = 0; i < 6; i++) helper.setBlock(origin.offset(i % 3, 0, i / 3), SourceAltarContent.SOURCE_ALTAR.get());
        BlockPos absolute = helper.absolutePos(origin);
        SourceAltarStructure.refresh(helper.getLevel(), absolute);
        var entity = (SourceAltarBlockEntity) helper.getLevel().getBlockEntity(absolute);
        helper.assertTrue(entity != null && entity.isMaster(), "3x2 altar did not form");
        return entity;
    }
    private static void completeScan(GameTestHelper helper, SourceAltarBlockEntity altar, int budget) {
        for (int i = 0; i < 8192 && !altar.isOperational(); i++) {
            int used = altar.scanStep(budget);
            helper.assertTrue(used >= 0 && used <= budget, "Scan exceeded position budget");
        }
        helper.assertTrue(altar.isOperational(), "Loaded scan did not finish within bounded steps");
    }
}
