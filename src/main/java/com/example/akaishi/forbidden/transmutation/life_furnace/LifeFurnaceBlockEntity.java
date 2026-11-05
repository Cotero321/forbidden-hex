package com.example.akaishi.forbidden.transmutation.life_furnace;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosEntity;
import com.example.akaishi.forbidden.familiar.entity.FamiliarEntity;
import com.example.akaishi.forbidden.transmutation.BiomassLedger;
import com.example.akaishi.forbidden.transmutation.LifeFurnaceRules;
import com.example.akaishi.forbidden.altar.SourceEnergyNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative 4x4x6 entity processor with one downward automation output slot. */
public final class LifeFurnaceBlockEntity extends BlockEntity implements WorldlyContainer {
    public static final int OUTPUT_SLOT = 0;
    private static final int STATUS_IDLE = 0;
    private static final int STATUS_WAITING_ENTITY = 1;
    private static final int STATUS_WAITING_ENERGY = 2;
    private static final int STATUS_PROCESSING = 3;
    private static final int STATUS_OUTPUT_BLOCKED = 4;
    private final NonNullList<net.minecraft.world.item.ItemStack> items = NonNullList.withSize(1, net.minecraft.world.item.ItemStack.EMPTY);
    /** Damage progress is tracked per entity so biomass keeps its source entity NBT. */
    private final Map<UUID, BiomassLedger> ledgers = new HashMap<>();
    private final Map<UUID, String> ledgerTypes = new HashMap<>();
    private final Map<String, Integer> pendingBiomass = new LinkedHashMap<>();
    private int status = STATUS_IDLE;

    public LifeFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(LifeFurnaceContent.BLOCK_ENTITY.get(), pos, state);
    }

    public int status() { return status; }
    public int pendingBiomass() { return pendingBiomass.values().stream().mapToInt(Integer::intValue).sum(); }
    public float damageRemainder() {
        double total = ledgers.values().stream().mapToDouble(BiomassLedger::damageRemainder).sum();
        return (float) Math.min(Float.MAX_VALUE, total);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, LifeFurnaceBlockEntity furnace) {
        if (!(level instanceof ServerLevel server) || !LifeFurnaceRules.shouldDamage(server.getGameTime())) return;
        furnace.flushPendingOutput();
        List<LivingEntity> targets = furnace.targets(server);
        if (targets.isEmpty()) {
            furnace.setStatus(STATUS_WAITING_ENTITY);
            return;
        }
        LivingEntity target = targets.get(0);
        String entityType = furnace.entityTypeId(target);
        if (!furnace.outputCanAccept(1, entityType)) {
            furnace.setStatus(STATUS_OUTPUT_BLOCKED);
            return;
        }
        long sourceHalfUnits = LifeFurnaceRules.ENERGY_PER_DAMAGE * 2L;
        if (!SourceEnergyNetwork.request(server, pos, sourceHalfUnits, true)) {
            furnace.setStatus(STATUS_WAITING_ENERGY);
            return;
        }
        if (!SourceEnergyNetwork.request(server, pos, sourceHalfUnits, false)) {
            furnace.setStatus(STATUS_WAITING_ENERGY);
            return;
        }
        float before = target.getHealth();
        if (!target.hurt(furnace.trueDamage(server), LifeFurnaceRules.DAMAGE_PER_INTERVAL)) {
            furnace.setStatus(STATUS_IDLE);
            return;
        }
        float dealt = Math.max(0.0F, before - target.getHealth());
        if (dealt <= 0.0F) dealt = LifeFurnaceRules.DAMAGE_PER_INTERVAL;
        BiomassLedger ledger = furnace.ledgers.computeIfAbsent(target.getUUID(), ignored -> new BiomassLedger());
        furnace.ledgerTypes.putIfAbsent(target.getUUID(), entityType);
        int biomass = ledger.recordDamage(dealt);
        if (biomass > 0) furnace.pendingBiomass.merge(entityType, biomass, Integer::sum);
        if (!target.isAlive()) {
            furnace.ledgers.remove(target.getUUID());
            furnace.ledgerTypes.remove(target.getUUID());
        }
        furnace.flushPendingOutput();
        furnace.setStatus(STATUS_PROCESSING);
        furnace.setChanged();
    }

    private List<LivingEntity> targets(ServerLevel server) {
        AABB volume = new AABB(worldPosition, worldPosition.offset(
                LifeFurnaceRules.STRUCTURE_WIDTH, LifeFurnaceRules.STRUCTURE_HEIGHT, LifeFurnaceRules.STRUCTURE_DEPTH));
        return server.getEntitiesOfClass(LivingEntity.class, volume, entity ->
                entity.isAlive() && !(entity instanceof Player) && !isExcluded(entity));
    }

    private boolean isExcluded(LivingEntity entity) {
        // Familiar entities are intentionally valid templates and must never be consumed.
        // Agaitolos is the project's custom Boss; the other two are vanilla bosses.
        return entity instanceof FamiliarEntity || entity instanceof EnderDragon || entity instanceof WitherBoss
                || entity instanceof AgaitolosEntity;
    }

    private DamageSource trueDamage(ServerLevel server) {
        var key = net.minecraft.resources.ResourceKey.create(Registries.DAMAGE_TYPE,
                new net.minecraft.resources.ResourceLocation(AkaishiMod.MOD_ID, "true_damage"));
        var holder = server.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key);
        return new DamageSource(holder);
    }

    private boolean outputCanAccept(int amount, String entityType) {
        ItemStack output = items.get(OUTPUT_SLOT);
        if (output.isEmpty()) return true;
        ItemStack sample = biomassStack(entityType, 1);
        return ItemStack.isSameItemSameTags(output, sample)
                && output.getCount() + amount <= Math.min(64, output.getMaxStackSize());
    }

    private void flushPendingOutput() {
        if (pendingBiomass.isEmpty()) return;
        ItemStack output = items.get(OUTPUT_SLOT);
        String outputType = biomassEntityType(output);
        for (var entry : List.copyOf(pendingBiomass.entrySet())) {
            String entityType = entry.getKey();
            int amount = entry.getValue();
            if (amount <= 0) { pendingBiomass.remove(entityType); continue; }
            if (!output.isEmpty() && (!output.is(LifeFurnaceContent.BIOMASS.get()) || !entityType.equals(outputType))) break;
            if (output.isEmpty()) {
                int count = Math.min(amount, 64);
                output = biomassStack(entityType, count);
                items.set(OUTPUT_SLOT, output);
                amount -= count;
            } else {
                int count = Math.min(amount, Math.min(64, output.getMaxStackSize()) - output.getCount());
                if (count <= 0) break;
                output.grow(count);
                amount -= count;
            }
            if (amount == 0) pendingBiomass.remove(entityType);
            else pendingBiomass.put(entityType, amount);
            setChanged();
            if (!output.isEmpty() && output.getCount() >= Math.min(64, output.getMaxStackSize())) break;
        }
    }

    private ItemStack biomassStack(String entityType, int count) {
        ItemStack stack = new ItemStack(LifeFurnaceContent.BIOMASS.get(), count);
        stack.getOrCreateTag().putString(LifeFurnaceRules.BIOMASS_ENTITY_TYPE_TAG, entityType);
        return stack;
    }

    private String biomassEntityType(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().getString(LifeFurnaceRules.BIOMASS_ENTITY_TYPE_TAG) : "";
    }

    private String entityTypeId(LivingEntity entity) {
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return key == null ? entity.getClass().getName() : key.toString();
    }

    private void setStatus(int next) {
        if (status == next) return;
        status = next;
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            boolean active = next == STATUS_PROCESSING;
            if (state.getValue(LifeFurnaceBlock.WORKING) != active || state.getValue(LifeFurnaceBlock.STATUS) != next) {
                level.setBlock(worldPosition, state.setValue(LifeFurnaceBlock.WORKING, active)
                        .setValue(LifeFurnaceBlock.STATUS, Math.min(6, next)), 3);
            }
        }
    }

    @Override public int getContainerSize() { return 1; }
    @Override public boolean isEmpty() { return items.get(OUTPUT_SLOT).isEmpty(); }
    @Override public net.minecraft.world.item.ItemStack getItem(int slot) { return slot == OUTPUT_SLOT ? items.get(OUTPUT_SLOT) : net.minecraft.world.item.ItemStack.EMPTY; }
    @Override public net.minecraft.world.item.ItemStack removeItem(int slot, int amount) { return slot == OUTPUT_SLOT ? ContainerHelper.removeItem(items, slot, amount) : net.minecraft.world.item.ItemStack.EMPTY; }
    @Override public net.minecraft.world.item.ItemStack removeItemNoUpdate(int slot) { return slot == OUTPUT_SLOT ? ContainerHelper.takeItem(items, slot) : net.minecraft.world.item.ItemStack.EMPTY; }
    @Override public void setItem(int slot, net.minecraft.world.item.ItemStack stack) { if (slot == OUTPUT_SLOT) { items.set(slot, stack); setChanged(); } }
    @Override public void clearContent() { items.clear(); setChanged(); }
    @Override public boolean stillValid(Player player) { return level != null && level.getBlockEntity(worldPosition) == this && player.distanceToSqr(worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5) <= 64; }
    @Override public boolean canPlaceItem(int slot, net.minecraft.world.item.ItemStack stack) { return false; }
    @Override public int[] getSlotsForFace(Direction face) { return face == Direction.DOWN ? new int[]{OUTPUT_SLOT} : new int[0]; }
    @Override public boolean canPlaceItemThroughFace(int slot, net.minecraft.world.item.ItemStack stack, @Nullable Direction face) { return false; }
    @Override public boolean canTakeItemThroughFace(int slot, net.minecraft.world.item.ItemStack stack, Direction face) { return slot == OUTPUT_SLOT && face == Direction.DOWN; }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        tag.putInt("Status", status);
        ListTag pending = new ListTag();
        for (var entry : pendingBiomass.entrySet()) {
            if (entry.getValue() <= 0) continue;
            CompoundTag value = new CompoundTag();
            value.putString("EntityType", entry.getKey());
            value.putInt("Count", entry.getValue());
            pending.add(value);
        }
        tag.put("PendingBiomass", pending);
        ListTag active = new ListTag();
        for (var entry : ledgers.entrySet()) {
            CompoundTag value = new CompoundTag();
            value.putUUID("Entity", entry.getKey());
            value.putString("EntityType", ledgerTypes.getOrDefault(entry.getKey(), ""));
            entry.getValue().save(value);
            active.add(value);
        }
        tag.put("ActiveLedgers", active);
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        items.clear();
        ContainerHelper.loadAllItems(tag, items);
        status = Math.max(0, Math.min(6, tag.getInt("Status")));
        pendingBiomass.clear();
        if (tag.contains("PendingBiomass", Tag.TAG_LIST)) {
            ListTag pending = tag.getList("PendingBiomass", CompoundTag.TAG_COMPOUND);
            for (int i = 0; i < pending.size(); i++) {
                CompoundTag value = pending.getCompound(i);
                String type = value.getString("EntityType");
                int count = value.getInt("Count");
                if (!type.isBlank() && count > 0) pendingBiomass.put(type, count);
            }
        }
        ledgers.clear();
        ledgerTypes.clear();
        if (tag.contains("ActiveLedgers", Tag.TAG_LIST)) {
            ListTag active = tag.getList("ActiveLedgers", CompoundTag.TAG_COMPOUND);
            for (int i = 0; i < active.size(); i++) {
                CompoundTag value = active.getCompound(i);
                if (!value.hasUUID("Entity")) continue;
                UUID entity = value.getUUID("Entity");
                BiomassLedger ledger = new BiomassLedger();
                ledger.load(value);
                ledgers.put(entity, ledger);
                ledgerTypes.put(entity, value.getString("EntityType"));
            }
        }
    }
}
