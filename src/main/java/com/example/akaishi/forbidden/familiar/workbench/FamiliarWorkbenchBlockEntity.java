package com.example.akaishi.forbidden.familiar.workbench;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.altar.SourceEnergyNetwork;
import com.example.akaishi.forbidden.familiar.FamiliarContent;
import com.example.akaishi.forbidden.familiar.FamiliarRuntimeService;
import com.example.akaishi.forbidden.familiar.core.FamiliarData;
import com.example.akaishi.forbidden.familiar.core.FamiliarTemplateId;
import com.example.akaishi.forbidden.familiar.item.FamiliarContractItem;
import com.example.akaishi.forbidden.familiar.item.FamiliarMaterialItems;
import com.example.akaishi.forbidden.familiar.item.FamiliarTemplateItems;
import com.example.akaishi.forbidden.familiar.machine.FamiliarWorkbenchSlotLayout;
import com.example.akaishi.forbidden.soul.SoulContent;
import com.example.akaishi.forbidden.soul.SoulCrystalData;
import dev.architectury.registry.menu.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persistent seven-input/one-output familiar workbench. Input semantics use NBT data contracts so
 * future registered template/material items can be added without changing this machine.
 */
public final class FamiliarWorkbenchBlockEntity extends BlockEntity implements WorldlyContainer, ExtendedMenuProvider {
    public static final int STATUS_IDLE = 0;
    public static final int STATUS_PROCESSING = 1;
    public static final int STATUS_OUTPUT_BLOCKED = 2;
    public static final int STATUS_INVALID_INPUT = 3;
    public static final int STATUS_WAITING_ENERGY = 4;
    public static final int PROCESSING_TICKS = 60 * 20;
    /** The user-facing workbench cost is 4000 source energy; the network stores half-units. */
    public static final long SOURCE_ENERGY_HALF_UNITS = 4000L * 2L;
    private static final String TEMPLATE_TAG = "FamiliarTemplate";
    private static final String MATERIAL_TAG = "FamiliarMaterial";
    private static final String BIOMASS_COST_TAG = "BiomassCost";
    private static final String BIOMASS_ID = AkaishiMod.MOD_ID + ":biomass";

    private final NonNullList<ItemStack> items = NonNullList.withSize(FamiliarWorkbenchSlotLayout.MACHINE_SLOT_COUNT, ItemStack.EMPTY);
    private int progress;
    private int duration;
    private int status = STATUS_IDLE;
    @Nullable private UUID operator;
    @Nullable private FamiliarData pendingData;
    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> duration;
                case 2 -> status;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> progress = Math.max(0, value);
                case 1 -> duration = Math.max(0, value);
                case 2 -> status = Math.max(0, value);
                default -> { }
            }
        }
        @Override public int getCount() { return 3; }
    };

    public FamiliarWorkbenchBlockEntity(BlockPos pos, BlockState state) {
        super(FamiliarWorkbenchContent.BLOCK_ENTITY.get(), pos, state);
    }

    public ContainerData data() { return data; }
    public int progress() { return progress; }
    public int duration() { return duration; }
    public int status() { return status; }
    public void setOperator(Player player) {
        if (player == null || player.level().isClientSide || pendingData != null || progress != 0
                || status == STATUS_PROCESSING) return;
        Player previous = level == null || operator == null ? null : level.getPlayerByUUID(operator);
        if (previous != null && !previous.getUUID().equals(player.getUUID()) && hasOpenMenu(previous)) return;
        if (!player.getUUID().equals(operator)) {
            operator = player.getUUID();
            setChanged();
        }
    }

    public boolean canOperate(Player player) {
        return player.level().isClientSide || player.getUUID().equals(operator);
    }

    private boolean hasOpenMenu(Player player) {
        return player.containerMenu instanceof FamiliarWorkbenchMenu menu && menu.isFor(this) && stillValid(player);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, FamiliarWorkbenchBlockEntity workbench) {
        if (level.isClientSide) return;
        if (workbench.pendingData == null && workbench.status != STATUS_PROCESSING) workbench.tryStart((ServerLevel) level);
        if (workbench.pendingData != null || workbench.status == STATUS_PROCESSING) workbench.advance((ServerLevel) level);
        boolean working = workbench.status == STATUS_PROCESSING;
        if (state.getValue(FamiliarWorkbenchBlock.WORKING) != working) {
            level.setBlock(pos, state.setValue(FamiliarWorkbenchBlock.WORKING, working), 3);
        }
    }

    public boolean isInputSlot(int slot) {
        return FamiliarWorkbenchSlotLayout.isInputSlot(slot);
    }

    private void tryStart(ServerLevel level) {
        if (operator == null || pendingData != null || progress != 0) return;
        if (!getItem(FamiliarWorkbenchSlotLayout.RESULT_SLOT).isEmpty()) {
            status = STATUS_OUTPUT_BLOCKED;
            return;
        }
        Player owner = level.getPlayerByUUID(operator);
        if (owner == null || !owner.isAlive() || owner.isSpectator() || !hasOpenMenu(owner)) {
            status = STATUS_INVALID_INPUT;
            return;
        }
        if (FamiliarRuntimeService.hasConfiguredFamiliar(owner)) {
            status = STATUS_INVALID_INPUT;
            return;
        }
        Evaluation evaluation = evaluateInputs();
        if (evaluation == null) {
            status = STATUS_INVALID_INPUT;
            return;
        }
        if (!hasBiomass(owner, evaluation.biomassCost())) {
            status = STATUS_INVALID_INPUT;
            return;
        }
        if (!SourceEnergyNetwork.request(level, worldPosition, SOURCE_ENERGY_HALF_UNITS, true)) {
            status = STATUS_WAITING_ENERGY;
            return;
        }
        if (!SourceEnergyNetwork.request(level, worldPosition, SOURCE_ENERGY_HALF_UNITS, false)) {
            status = STATUS_WAITING_ENERGY;
            return;
        }
        consumeBiomass(owner, evaluation.biomassCost());
        for (int slot = 0; slot <= FamiliarWorkbenchSlotLayout.LAST_MATERIAL_SLOT; slot++) items.set(slot, ItemStack.EMPTY);
        pendingData = evaluation.data();
        pendingData.setOwner(owner.getUUID());
        progress = 0;
        duration = PROCESSING_TICKS;
        status = STATUS_PROCESSING;
        setChanged();
    }

    private void advance(ServerLevel level) {
        if (pendingData == null) {
            progress = 0;
            duration = 0;
            status = STATUS_INVALID_INPUT;
            return;
        }
        progress = Math.min(duration, progress + 1);
        if (progress < duration) {
            setChanged();
            return;
        }
        if (!getItem(FamiliarWorkbenchSlotLayout.RESULT_SLOT).isEmpty()) {
            status = STATUS_OUTPUT_BLOCKED;
            return;
        }
        ItemStack output = new ItemStack(FamiliarContent.FAMILIAR_CONTRACT.get());
        FamiliarData result = pendingData.copy();
        result.setActive(false);
        result.setActiveEntity(null);
        result.setDeathPending(false);
        FamiliarContractItem.writeData(output, result);
        items.set(FamiliarWorkbenchSlotLayout.RESULT_SLOT, output);
        pendingData = null;
        progress = 0;
        duration = 0;
        status = STATUS_IDLE;
        setChanged();
    }

    @Nullable
    private Evaluation evaluateInputs() {
        ItemStack soul = getItem(FamiliarWorkbenchSlotLayout.SOUL_SLOT);
        if (!soul.is(SoulContent.SOUL_CRYSTAL.get()) || soul.getCount() != 1) return null;
        SoulCrystalData.Data soulData = SoulCrystalData.read(soul.getOrCreateTag());
        if (!soulData.hasAffinity()) return null;
        ItemStack template = getItem(FamiliarWorkbenchSlotLayout.TEMPLATE_SLOT);
        FamiliarTemplateId templateId = templateId(template);
        if (templateId == FamiliarTemplateId.NONE || template.getCount() != 1) return null;
        List<String> materials = new ArrayList<>();
        int biomassCost = 0;
        for (int slot : FamiliarWorkbenchSlotLayout.materialSlots()) {
            ItemStack material = getItem(slot);
            if (!isMaterial(material) || material.getCount() != 1) return null;
            String id = FamiliarMaterialItems.materialId(material);
            materials.add(id);
            biomassCost += FamiliarMaterialItems.biomassCost(material);
        }
        FamiliarData data = new FamiliarData();
        data.setTemplate(templateId);
        data.setAlignment(soulData.alignment());
        data.setAffinity(soulData.affinity().id());
        CompoundTag soulScores = new CompoundTag();
        soulData.scores().save(soulScores);
        data.setSoulScores(soulScores);
        data.setHunger(FamiliarData.MAX_HUNGER);
        data.setMood(FamiliarData.MAX_MOOD);
        data.setMaterials(materials);
        data.setMaterialBiomassCost(biomassCost);
        return new Evaluation(data, biomassCost);
    }

    private static FamiliarTemplateId templateId(ItemStack stack) {
        return FamiliarTemplateItems.templateId(stack);
    }

    private static boolean isMaterial(ItemStack stack) {
        return FamiliarMaterialItems.isMaterial(stack);
    }

    private static ItemStack biomassStack(Player player) {
        Item biomass = BuiltInRegistries.ITEM.get(new ResourceLocation(BIOMASS_ID));
        if (biomass == Items.AIR) return ItemStack.EMPTY;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(biomass)) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static boolean hasBiomass(Player player, int amount) {
        int count = 0;
        Item biomass = BuiltInRegistries.ITEM.get(new ResourceLocation(BIOMASS_ID));
        if (biomass == Items.AIR) return false;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(biomass)) count += stack.getCount();
            if (count >= amount) return true;
        }
        return false;
    }

    private static void consumeBiomass(Player player, int amount) {
        Item biomass = BuiltInRegistries.ITEM.get(new ResourceLocation(BIOMASS_ID));
        if (biomass == Items.AIR) return;
        int remaining = amount;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(biomass)) continue;
            int used = Math.min(remaining, stack.getCount());
            stack.shrink(used);
            remaining -= used;
        }
    }

    private record Evaluation(FamiliarData data, int biomassCost) { }

    @Override public Component getDisplayName() { return Component.translatable("block.forbidden_hex.familiar_workbench"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new FamiliarWorkbenchMenu(id, inventory, this, data); }
    @Override public void saveExtraData(FriendlyByteBuf buffer) { buffer.writeBlockPos(worldPosition); }
    @Override public int getContainerSize() { return items.size(); }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY; }
    @Override public ItemStack removeItem(int slot, int amount) {
        if (status == STATUS_PROCESSING && isInputSlot(slot)) return ItemStack.EMPTY;
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) setChanged();
        return result;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }
    @Override public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= items.size()) return;
        if (slot == FamiliarWorkbenchSlotLayout.RESULT_SLOT) return;
        if (!isInputSlot(slot) || status == STATUS_PROCESSING || !canPlaceItem(slot, stack)) return;
        if (!stack.isEmpty()) stack = stack.copyWithCount(1);
        items.set(slot, stack);
        setChanged();
    }
    @Override public void clearContent() { items.clear(); setChanged(); }
    @Override public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0D;
    }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        if (!isInputSlot(slot) || stack.isEmpty() || status == STATUS_PROCESSING) return false;
        if (slot == FamiliarWorkbenchSlotLayout.SOUL_SLOT) return stack.is(SoulContent.SOUL_CRYSTAL.get()) && stack.getCount() == 1;
        if (slot == FamiliarWorkbenchSlotLayout.TEMPLATE_SLOT) return templateId(stack) != FamiliarTemplateId.NONE && stack.getCount() == 1;
        return isMaterial(stack) && stack.getCount() == 1;
    }
    @Override public int[] getSlotsForFace(Direction face) {
        return face == Direction.DOWN ? new int[]{FamiliarWorkbenchSlotLayout.RESULT_SLOT} : new int[]{0, 1, 2, 3, 4, 5, 6};
    }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction face) { return canPlaceItem(slot, stack); }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction face) { return slot == FamiliarWorkbenchSlotLayout.RESULT_SLOT; }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        tag.putInt("Progress", progress);
        tag.putInt("Duration", duration);
        tag.putInt("Status", status);
        if (operator != null) tag.putUUID("Operator", operator);
        if (pendingData != null) tag.put("PendingData", pendingData.save());
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        items.clear();
        ContainerHelper.loadAllItems(tag, items);
        progress = Math.max(0, tag.getInt("Progress"));
        duration = Math.max(0, tag.getInt("Duration"));
        status = Math.max(0, tag.getInt("Status"));
        operator = tag.hasUUID("Operator") ? tag.getUUID("Operator") : null;
        pendingData = tag.contains("PendingData", CompoundTag.TAG_COMPOUND)
                ? FamiliarData.load(tag.getCompound("PendingData")) : null;
        // Older in-progress batches kept their owner only in Operator.
        if (pendingData != null && pendingData.owner() == null) pendingData.setOwner(operator);
    }
}
