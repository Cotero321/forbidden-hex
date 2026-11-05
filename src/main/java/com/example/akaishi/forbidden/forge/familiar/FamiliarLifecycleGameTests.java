package com.example.akaishi.forbidden.forge.familiar;

import com.example.akaishi.forbidden.entity.AkaishiForbiddenEntities;
import com.example.akaishi.forbidden.familiar.FamiliarBehaviorService;
import com.example.akaishi.forbidden.familiar.FamiliarContent;
import com.example.akaishi.forbidden.familiar.FamiliarDeathStore;
import com.example.akaishi.forbidden.familiar.FamiliarRuntimeService;
import com.example.akaishi.forbidden.familiar.core.FamiliarData;
import com.example.akaishi.forbidden.familiar.core.FamiliarMode;
import com.example.akaishi.forbidden.familiar.core.FamiliarTemplateId;
import com.example.akaishi.forbidden.familiar.entity.FamiliarEntity;
import com.example.akaishi.forbidden.familiar.item.FamiliarContractItem;
import com.example.akaishi.forbidden.familiar.item.FamiliarMaterialItems;
import com.example.akaishi.forbidden.familiar.item.FamiliarTemplateItems;
import com.example.akaishi.forbidden.familiar.machine.FamiliarWorkbenchSlotLayout;
import com.example.akaishi.forbidden.familiar.workbench.FamiliarWorkbenchBlockEntity;
import com.example.akaishi.forbidden.familiar.workbench.FamiliarWorkbenchContent;
import com.example.akaishi.forbidden.familiar.workbench.FamiliarWorkbenchMenu;
import com.example.akaishi.forbidden.soul.SoulAffinity;
import com.example.akaishi.forbidden.soul.SoulContent;
import com.example.akaishi.forbidden.soul.SoulCrystalData;
import com.example.akaishi.forbidden.soul.SoulScoreLedger;
import com.example.akaishi.forbidden.transmutation.life_furnace.LifeFurnaceContent;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder("forbidden_hex")
@PrefixGameTestTemplate(false)
public final class FamiliarLifecycleGameTests {
    private static final BlockPos BENCH = new BlockPos(7, 3, 7);

    private FamiliarLifecycleGameTests() { }

    private static FamiliarEntity familiar(GameTestHelper helper, FamiliarData data) {
        FamiliarEntity familiar = AkaishiForbiddenEntities.FAMILIAR.get().create(helper.getLevel());
        helper.assertTrue(familiar != null, "Familiar entity factory returned null");
        familiar.setPos(Vec3.atCenterOf(helper.absolutePos(BENCH)));
        familiar.applyFamiliarData(data);
        return familiar;
    }

    private static FamiliarData data(UUID owner, FamiliarTemplateId template) {
        FamiliarData data = new FamiliarData();
        data.setOwner(owner);
        data.setTemplate(template);
        data.setHealth(12.0F);
        return data;
    }

    private static ServerPlayer player(GameTestHelper helper, UUID id) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(id, "familiar-test"));
        player.getInventory().clearContent();
        player.setPos(Vec3.atCenterOf(helper.absolutePos(BENCH)));
        helper.getLevel().players().add(player);
        return player;
    }

    private static void removePlayer(GameTestHelper helper, ServerPlayer player) {
        player.containerMenu.removed(player);
        player.containerMenu = player.inventoryMenu;
        player.getInventory().clearContent();
        helper.getLevel().players().remove(player);
    }

    private static ItemStack contract(FamiliarData data, UUID entityId) {
        FamiliarData active = data.copy();
        active.setActive(true);
        active.setActiveEntity(entityId);
        ItemStack contract = new ItemStack(FamiliarContent.FAMILIAR_CONTRACT.get());
        FamiliarContractItem.writeData(contract, active);
        return contract;
    }

    @GameTest(template = "source_altar_empty")
    public static void damagedAndHealedHealthSurvivesSaveAndRecall(GameTestHelper helper) {
        ServerPlayer owner = player(helper, UUID.randomUUID());
        FamiliarEntity original = familiar(helper, data(owner.getUUID(), FamiliarTemplateId.CAT));
        try {
            helper.assertTrue(original.hurt(original.damageSources().generic(), 3.0F), "Damage fixture was rejected");
            original.heal(1.0F);
            float current = original.getHealth();
            helper.assertTrue(current > 0.0F && current < 12.0F, "Damage/heal fixture did not change initial health");
            helper.assertTrue(original.toFamiliarData().health() == current, "Snapshot retained summon health after damage/healing");
            CompoundTag saved = original.saveWithoutId(new CompoundTag());
            FamiliarEntity restored = familiar(helper, data(owner.getUUID(), FamiliarTemplateId.CAT));
            restored.load(saved);
            helper.assertTrue(restored.getHealth() == current && restored.toFamiliarData().health() == current,
                    "Entity reload changed current health");
            ItemStack contract = contract(restored.toFamiliarData(), restored.getUUID());
            owner.getInventory().setItem(0, contract);
            owner.setShiftKeyDown(true);
            contract.getItem().interactLivingEntity(contract, owner, restored, InteractionHand.MAIN_HAND);
            FamiliarData recalled = FamiliarContractItem.readData(contract);
            helper.assertTrue(restored.isRemoved() && !recalled.active() && recalled.activeEntity() == null
                    && recalled.health() == current, "Recall lost the damaged/healed health snapshot");
            helper.succeed();
        } finally {
            owner.setShiftKeyDown(false);
            original.discard();
            removePlayer(helper, owner);
        }
    }

    @GameTest(template = "source_altar_empty")
    public static void legacyStaleHealthDoesNotOverrideVanillaHealth(GameTestHelper helper) {
        FamiliarEntity original = familiar(helper, data(UUID.randomUUID(), FamiliarTemplateId.CAT));
        original.setHealth(4.0F);
        CompoundTag saved = original.saveWithoutId(new CompoundTag());
        saved.getCompound("FamiliarData").putFloat("Health", 12.0F);
        saved.putFloat("SavedHealth", 12.0F);
        FamiliarEntity restored = familiar(helper, data(UUID.randomUUID(), FamiliarTemplateId.CAT));
        restored.load(saved);
        helper.assertTrue(restored.getHealth() == 4.0F && restored.toFamiliarData().health() == 4.0F,
                "Legacy summon snapshot overrode live vanilla Health");
        restored.heal(3.0F);
        helper.assertTrue(restored.toFamiliarData().health() == 7.0F, "Healing after reload was not snapshotted");
        original.discard();
        restored.discard();
        helper.succeed();
    }

    @GameTest(template = "source_altar_empty")
    public static void offlineDeathRecoversStoredContractAndChargesRevivalOnce(GameTestHelper helper) {
        UUID ownerId = UUID.randomUUID();
        FamiliarData initial = data(ownerId, FamiliarTemplateId.WOLF);
        initial.setLevel(3);
        initial.setExperience(123L);
        initial.setAlignment(-4);
        initial.setAffinity("wrath");
        initial.setMaterials(List.of("starfallen_iron", "hardened_leather"));
        initial.setMaterialBiomassCost(6);
        initial.setEquipment(new ItemStack(Items.IRON_SWORD));
        FamiliarEntity dead = familiar(helper, initial);
        dead.setExperience(456L);
        helper.setBlock(BENCH.below(), Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(BENCH.below());
        ItemStack stored = contract(initial, dead.getUUID());
        chest.setItem(0, stored);
        helper.assertTrue(helper.getLevel().getServer().getPlayerList().getPlayer(ownerId) == null,
                "Offline owner fixture unexpectedly logged in");
        dead.setHealth(0.0F);
        dead.die(dead.damageSources().generic());
        helper.assertTrue(FamiliarContractItem.readData(stored).active(), "Stored contract was unexpectedly in the owner's inventory");
        FamiliarDeathStore reloaded = FamiliarDeathStore.load(FamiliarDeathStore.get(helper.getLevel().getServer()).save(new CompoundTag()));
        FamiliarData old = FamiliarContractItem.readData(stored);
        FamiliarData recovered = reloaded.recovery(old);
        helper.assertTrue(recovered != null && recovered.deathPending() && !recovered.active() && recovered.health() == 0.0F,
                "Offline death was not durable across SavedData serialization");
        FamiliarData unrelated = old.copy();
        unrelated.setOwner(UUID.randomUUID());
        helper.assertTrue(reloaded.recovery(unrelated) == null, "Death recovery crossed owners");
        unrelated = old.copy();
        unrelated.setFamiliarId(UUID.randomUUID());
        helper.assertTrue(reloaded.recovery(unrelated) == null, "Death recovery crossed familiar identities");
        unrelated = old.copy();
        unrelated.setActiveEntity(UUID.randomUUID());
        helper.assertTrue(reloaded.recovery(unrelated) == null, "Old death overrode a newer active entity");

        ServerPlayer owner = player(helper, ownerId);
        try {
            ItemStack returned = chest.removeItem(0, 1);
            owner.setItemInHand(InteractionHand.MAIN_HAND, returned);
            returned.getItem().inventoryTick(returned, helper.getLevel(), owner, 0, true);
            FamiliarData pending = FamiliarContractItem.readData(returned);
            helper.assertTrue(pending.deathPending() && !pending.active() && pending.activeEntity() == null
                    && pending.level() == 3 && pending.experience() == 456L && pending.alignment() == -4
                    && pending.materials().equals(initial.materials()) && pending.materialBiomassCost() == 6
                    && pending.equipment().is(Items.IRON_SWORD), "Recovered contract lost identity, progress, gear or original material cost");
            CompoundTag once = returned.getTag().copy();
            returned.getItem().inventoryTick(returned, helper.getLevel(), owner, 0, true);
            helper.assertTrue(returned.getTag().equals(once), "Repeated reconciliation changed an already recovered contract");
            owner.getInventory().setItem(1, new ItemStack(LifeFurnaceContent.BIOMASS.get(), 6));
            var result = returned.getItem().use(helper.getLevel(), owner, InteractionHand.MAIN_HAND);
            FamiliarData revived = FamiliarContractItem.readData(returned);
            helper.assertTrue(result.getResult().consumesAction() && revived.active() && !revived.deathPending()
                    && revived.activeEntity() != null && !revived.activeEntity().equals(dead.getUUID())
                    && owner.getInventory().getItem(1).isEmpty(), "Revival did not charge exactly the original biomass cost");
            helper.assertTrue(!FamiliarRuntimeService.reconcileContract(returned, helper.getLevel()),
                    "Latest death replayed onto a new summon");
            returned.getItem().use(helper.getLevel(), owner, InteractionHand.MAIN_HAND);
            helper.assertTrue(owner.getInventory().getItem(1).isEmpty() && FamiliarContractItem.readData(returned).activeEntity().equals(revived.activeEntity()),
                    "Repeated use duplicated a summon or charged again");
            if (helper.getLevel().getEntity(revived.activeEntity()) instanceof FamiliarEntity entity) entity.discard();
            helper.succeed();
        } finally {
            dead.discard();
            removePlayer(helper, owner);
        }
    }

    private static FamiliarWorkbenchBlockEntity bench(GameTestHelper helper) {
        helper.setBlock(BENCH, FamiliarWorkbenchContent.BLOCK.get());
        return (FamiliarWorkbenchBlockEntity) helper.getBlockEntity(BENCH);
    }

    private static void tickBench(GameTestHelper helper, FamiliarWorkbenchBlockEntity bench, int ticks) {
        for (int i = 0; i < ticks; i++) FamiliarWorkbenchBlockEntity.tick(helper.getLevel(), bench.getBlockPos(), bench.getBlockState(), bench);
    }

    @GameTest(template = "source_altar_empty")
    public static void secondViewerCannotStealInProgressBatchAfterReload(GameTestHelper helper) {
        FamiliarWorkbenchBlockEntity bench = bench(helper);
        ServerPlayer first = player(helper, UUID.randomUUID());
        ServerPlayer second = player(helper, UUID.randomUUID());
        try {
            FamiliarData pending = data(first.getUUID(), FamiliarTemplateId.CAT);
            CompoundTag saved = bench.saveWithoutMetadata();
            saved.putUUID("Operator", first.getUUID());
            saved.put("PendingData", pending.save());
            saved.putInt("Progress", FamiliarWorkbenchBlockEntity.PROCESSING_TICKS - 2);
            saved.putInt("Duration", FamiliarWorkbenchBlockEntity.PROCESSING_TICKS);
            saved.putInt("Status", FamiliarWorkbenchBlockEntity.STATUS_PROCESSING);
            bench.load(saved);
            FamiliarWorkbenchMenu secondMenu = (FamiliarWorkbenchMenu) bench.createMenu(2, second.getInventory(), second);
            second.containerMenu = secondMenu;
            helper.assertTrue(!bench.canOperate(second), "Second viewer became the paid batch operator");
            bench.load(bench.saveWithoutMetadata());
            tickBench(helper, bench, 2);
            FamiliarData result = FamiliarContractItem.readData(bench.getItem(FamiliarWorkbenchSlotLayout.RESULT_SLOT));
            helper.assertTrue(result.isOwnedBy(first.getUUID()) && result.familiarId().equals(pending.familiarId()),
                    "Second viewer or reload changed the paid batch's owner/identity");
            helper.assertTrue(!secondMenu.getSlot(FamiliarWorkbenchSlotLayout.RESULT_SLOT).mayPickup(second)
                    && secondMenu.quickMoveStack(second, FamiliarWorkbenchSlotLayout.RESULT_SLOT).isEmpty(),
                    "Second viewer could extract the first player's result");
            helper.succeed();
        } finally {
            removePlayer(helper, first);
            removePlayer(helper, second);
        }
    }

    @GameTest(template = "source_altar_empty")
    public static void staleOrDifferentMenuCannotStartSpendingForOperator(GameTestHelper helper) {
        FamiliarWorkbenchBlockEntity bench = bench(helper);
        ServerPlayer first = player(helper, UUID.randomUUID());
        ServerPlayer second = player(helper, UUID.randomUUID());
        try {
            FamiliarWorkbenchMenu firstMenu = (FamiliarWorkbenchMenu) bench.createMenu(1, first.getInventory(), first);
            first.containerMenu = firstMenu;
            FamiliarWorkbenchMenu secondMenu = (FamiliarWorkbenchMenu) bench.createMenu(2, second.getInventory(), second);
            second.containerMenu = secondMenu;
            ItemStack soul = new ItemStack(SoulContent.SOUL_CRYSTAL.get());
            SoulCrystalData.write(soul.getOrCreateTag(), new SoulCrystalData.Data(0, SoulAffinity.WRATH, new SoulScoreLedger(), false, false));
            bench.setItem(FamiliarWorkbenchSlotLayout.SOUL_SLOT, soul);
            bench.setItem(FamiliarWorkbenchSlotLayout.TEMPLATE_SLOT, FamiliarTemplateItems.stack(FamiliarTemplateId.CAT));
            for (int slot : FamiliarWorkbenchSlotLayout.materialSlots()) bench.setItem(slot, FamiliarMaterialItems.stack("starfallen_iron"));
            first.getInventory().setItem(0, new ItemStack(LifeFurnaceContent.BIOMASS.get(), 15));
            second.getInventory().setItem(0, new ItemStack(LifeFurnaceContent.BIOMASS.get(), 15));
            helper.assertTrue(bench.canOperate(first) && !bench.canOperate(second)
                    && !secondMenu.getSlot(FamiliarWorkbenchSlotLayout.FIRST_MATERIAL_SLOT).mayPlace(FamiliarMaterialItems.stack("starfallen_iron")),
                    "Different viewer stole the current operator or gained input rights");
            first.containerMenu = first.inventoryMenu;
            tickBench(helper, bench, 1);
            helper.assertTrue(bench.status() == FamiliarWorkbenchBlockEntity.STATUS_INVALID_INPUT
                    && bench.progress() == 0 && !bench.getItem(FamiliarWorkbenchSlotLayout.SOUL_SLOT).isEmpty()
                    && first.getInventory().getItem(0).getCount() == 15 && second.getInventory().getItem(0).getCount() == 15,
                    "A closed/stale operator menu consumed materials or biomass");
            secondMenu.clicked(FamiliarWorkbenchSlotLayout.FIRST_MATERIAL_SLOT, 0, ClickType.PICKUP, second);
            helper.assertTrue(!bench.getItem(FamiliarWorkbenchSlotLayout.FIRST_MATERIAL_SLOT).isEmpty(),
                    "Different viewer removed the operator's input through normal pickup");
            // A deliberate new menu claims the idle workbench after the old operator leaves.
            second.containerMenu = bench.createMenu(3, second.getInventory(), second);
            helper.assertTrue(bench.canOperate(second) && !bench.canOperate(first), "Idle workbench could not be claimed by its new operator");
            helper.succeed();
        } finally {
            removePlayer(helper, first);
            removePlayer(helper, second);
        }
    }

    @GameTest(template = "source_altar_empty")
    public static void attackIntervalsAreIndependentOfTargetSearch(GameTestHelper helper) {
        ServerPlayer owner = player(helper, UUID.randomUUID());
        try {
            for (FamiliarTemplateId template : List.of(FamiliarTemplateId.CAT, FamiliarTemplateId.WOLF, FamiliarTemplateId.FROG, FamiliarTemplateId.BAT)) {
                FamiliarEntity familiar = familiar(helper, data(owner.getUUID(), template));
                familiar.setMode(FamiliarMode.AGGRESSIVE);
                CountingTarget target = new CountingTarget(helper.getLevel(), familiar);
                target.setPos(familiar.getX() + 1.0D, familiar.getY(), familiar.getZ());
                target.setNoAi(true);
                helper.assertTrue(helper.getLevel().addFreshEntity(target), "Could not add the attack interval target");
                try {
                    int interval = template == FamiliarTemplateId.CAT ? 8 : template == FamiliarTemplateId.BAT ? 18 : 14;
                    for (int tick = 1; tick <= 100; tick++) {
                        familiar.tickCount = tick;
                        FamiliarBehaviorService.tick(familiar);
                    }
                    List<Integer> expected = new ArrayList<>();
                    for (int tick = interval; tick <= 100; tick += interval) expected.add(tick);
                    helper.assertTrue(target.attacks.equals(expected), template + " attack ticks were " + target.attacks + ", expected " + expected);
                    familiar.setMode(FamiliarMode.PASSIVE);
                    familiar.tickCount = interval * 8;
                    FamiliarBehaviorService.tick(familiar);
                    helper.assertTrue(target.attacks.equals(expected), "Switching to passive retained an attacking target");
                } finally {
                    target.discard();
                    familiar.discard();
                }
            }
            helper.succeed();
        } finally {
            removePlayer(helper, owner);
        }
    }

    /** Record attack attempts without vanilla hurt-immunity hiding an eight-tick attack. */
    private static final class CountingTarget extends Cow {
        private final FamiliarEntity familiar;
        private final List<Integer> attacks = new ArrayList<>();

        private CountingTarget(ServerLevel level, FamiliarEntity familiar) {
            super(EntityType.COW, level);
            this.familiar = familiar;
        }

        @Override public boolean hurt(DamageSource source, float amount) {
            attacks.add(familiar.tickCount);
            return false;
        }
    }
}
