package com.example.akaishi.forbidden.alchemy;

import com.example.akaishi.forbidden.altar.SourceEnergyNetwork;
import dev.architectury.registry.menu.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.UUID;

public final class AlchemyMachineBlockEntity extends BlockEntity implements WorldlyContainer, ExtendedMenuProvider {
    public static final int INPUT = 0, REAGENT = 1, BOTTLES = 2, OUTPUT = 3, SLOTS = 7, WATER_CAPACITY = 4000;
    public static final int IDLE = 0, WORKING = 1, HOLD = 2, HEAT = 3, WATER = 4, GLASS = 5, BLOCKED = 6, ENERGY = 7, REAGENT_MISSING = 8;
    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private final NonNullList<ItemStack> products = NonNullList.withSize(4, ItemStack.EMPTY);
    private CompoundTag job = new CompoundTag();
    private int progress, duration, mode, water, credit, status;
    private UUID operator;
    private long leaseUntil;
    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> progress & 65535; case 1 -> progress >>> 16;
                case 2 -> duration & 65535; case 3 -> duration >>> 16;
                case 4 -> water; case 5 -> mode; case 6 -> kind().ordinal(); case 7 -> status;
                case 8 -> hasJob() ? 1 : 0; default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> progress = (progress & 0xFFFF0000) | (value & 65535);
                case 1 -> progress = (progress & 65535) | ((value & 65535) << 16);
                case 2 -> duration = (duration & 0xFFFF0000) | (value & 65535);
                case 3 -> duration = (duration & 65535) | ((value & 65535) << 16);
                case 4 -> water = value; case 5 -> mode = value; case 7 -> status = value;
            }
        }
        @Override public int getCount() { return 9; }
    };
    public AlchemyMachineBlockEntity(BlockPos pos, BlockState state) { super(AlchemyContent.MACHINE_ENTITY.get(), pos, state); }
    public AlchemyContent.Kind kind() { return ((AlchemyMachineBlock) getBlockState().getBlock()).kind(); }
    public ContainerData data() { return data; }
    public int water() { return water; }
    public int credit() { return credit; }
    public int progress() { return progress; }
    public int duration() { return duration; }
    public int status() { return status; }
    public int mode() { return mode; }
    public boolean hasJob() { return !job.isEmpty(); }
    public boolean setMode(int next) {
        if (next < 0 || next > 1 || hasJob() || !kind().crusher()) return false;
        if (mode != next) { mode = next; setChanged(); }
        return true;
    }
    public boolean manualInput(Player player, boolean held) {
        if (kind() != AlchemyContent.Kind.MANUAL_CRUSHER || !stillValid(player) || player.isSpectator() || level == null) return false;
        long now = level.getGameTime();
        if (operator != null && !operator.equals(player.getUUID()) && leaseUntil >= now) return false;
        if (held) { operator = player.getUUID(); leaseUntil = now + 6; }
        else { operator = null; leaseUntil = 0; }
        return true;
    }
    private boolean held() {
        if (level == null || operator == null || leaseUntil < level.getGameTime()) return false;
        Player player = level.getPlayerByUUID(operator);
        return player != null && player.isAlive() && !player.isSpectator() && stillValid(player)
                && player.containerMenu instanceof AlchemyMenu menu && menu.machine() == this;
    }
    public int fillWater(int amount, boolean simulate) {
        if (kind() != AlchemyContent.Kind.DISTILLER || amount <= 0) return 0;
        int accepted = Math.min(amount, WATER_CAPACITY - water);
        if (!simulate && accepted > 0) { water += accepted; setChanged(); }
        return accepted;
    }
    private boolean heat(boolean refining) {
        if (kind().powered()) return true;
        if (level == null || !level.hasChunkAt(worldPosition.below())) return false;
        BlockState below = level.getBlockState(worldPosition.below());
        if (refining) return below.is(Blocks.SOUL_FIRE) || below.is(Blocks.SOUL_CAMPFIRE) && below.getValue(CampfireBlock.LIT);
        return below.is(Blocks.FIRE) || below.is(Blocks.SOUL_FIRE) || below.is(Blocks.MAGMA_BLOCK)
                || (below.is(Blocks.CAMPFIRE) || below.is(Blocks.SOUL_CAMPFIRE)) && below.getValue(CampfireBlock.LIT);
    }
    private boolean outputFits(NonNullList<ItemStack> pending) {
        for (int i = 0; i < 4; i++) {
            ItemStack addition = pending.get(i), existing = items.get(OUTPUT + i);
            if (addition.isEmpty()) continue;
            int limit = Math.min(64, addition.getMaxStackSize());
            if (addition.getCount() > limit || !existing.isEmpty()
                    && (!ItemStack.isSameItemSameTags(existing, addition) || existing.getCount() + addition.getCount() > limit)) return false;
        }
        return true;
    }
    private boolean energy(ServerLevel server, boolean simulate) {
        if (!kind().powered() || credit > 0) return true;
        if (!SourceEnergyNetwork.request(server, worldPosition, 1, simulate)) return false;
        if (!simulate) credit = kind().crusher() ? 10 : 5;
        return true;
    }
    private boolean start(ServerLevel server) {
        AlchemyRecipe recipe = server.getRecipeManager().getAllRecipesFor(AlchemyContent.RECIPES.get()).stream()
                .filter(candidate -> candidate.forMachine(kind(), mode) && candidate.input().test(items.get(INPUT)))
                .sorted(Comparator.comparing(candidate -> candidate.id().toString())).findFirst().orElse(null);
        if (recipe == null) { status = IDLE; return false; }
        if (recipe.reagent() != null && !recipe.reagent().test(items.get(REAGENT))) { status = REAGENT_MISSING; return false; }
        if (recipe.bottles() > 0 && (!items.get(BOTTLES).is(Items.GLASS_BOTTLE) || items.get(BOTTLES).getCount() < recipe.bottles())) { status = GLASS; return false; }
        boolean refining = recipe.process().equals("refining");
        if (!kind().crusher() && !heat(refining)) { status = HEAT; return false; }
        if (!kind().crusher() && !kind().powered() && !refining && water < 250) { status = WATER; return false; }
        int batch = kind().crusher() ? Math.min(64, items.get(INPUT).getCount()) : 1;
        while (batch > 0 && !outputFits(recipe.products(batch, server.random, true))) batch--;
        if (batch == 0) { status = BLOCKED; return false; }
        if (!energy(server, true)) { status = ENERGY; return false; }
        if (kind() == AlchemyContent.Kind.MANUAL_CRUSHER && !held()) { status = HOLD; return false; }
        NonNullList<ItemStack> rolled = recipe.products(batch, server.random, false);
        job = new CompoundTag();
        job.putString("Recipe", recipe.id().toString());
        job.putBoolean("Refining", refining);
        job.put("Input", items.get(INPUT).split(batch).save(new CompoundTag()));
        if (recipe.reagent() != null) job.put("Reagent", items.get(REAGENT).split(1).save(new CompoundTag()));
        items.get(BOTTLES).shrink(recipe.bottles());
        job.putInt("Bottles", recipe.bottles());
        if (!kind().crusher() && !kind().powered() && !refining) water -= 250;
        for (int i = 0; i < 4; i++) products.set(i, rolled.get(i));
        progress = 0;
        duration = recipe.duration(batch, kind().powered());
        setChanged();
        return true;
    }
    public static void tick(Level level, BlockPos pos, BlockState state, AlchemyMachineBlockEntity machine) {
        if (!(level instanceof ServerLevel server)) return;
        int oldStatus = machine.status;
        if (machine.hasJob() || machine.start(server)) {
            if (!machine.outputFits(machine.products)) machine.status = BLOCKED;
            else if (!machine.kind().crusher() && !machine.heat(machine.job.getBoolean("Refining"))) machine.status = HEAT;
            else if (machine.kind() == AlchemyContent.Kind.MANUAL_CRUSHER && !machine.held()) machine.status = HOLD;
            else if (!machine.energy(server, false)) machine.status = ENERGY;
            else {
                machine.status = WORKING;
                if (machine.kind().powered()) machine.credit--;
                machine.progress++;
                if (machine.progress >= machine.duration) {
                    for (int i = 0; i < 4; i++) {
                        ItemStack addition = machine.products.get(i), existing = machine.items.get(OUTPUT + i);
                        if (addition.isEmpty()) continue;
                        if (existing.isEmpty()) machine.items.set(OUTPUT + i, addition.copy()); else existing.grow(addition.getCount());
                    }
                    machine.job = new CompoundTag();
                    machine.products.replaceAll(ignored -> ItemStack.EMPTY);
                    machine.progress = 0;
                    machine.duration = 0;
                }
                machine.setChanged();
            }
        }
        boolean active = machine.status == WORKING;
        if (state.getValue(AlchemyMachineBlock.WORKING) != active) level.setBlock(pos, state.setValue(AlchemyMachineBlock.WORKING, active), 3);
        if (machine.status != oldStatus) machine.setChanged();
    }
    @Override public Component getDisplayName() { return Component.translatable("block.forbidden_hex.forbidden_hex_" + kind().id()); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new AlchemyMenu(id, inventory, this, data); }
    @Override public void saveExtraData(FriendlyByteBuf buffer) { buffer.writeBlockPos(worldPosition); }
    @Override public int getContainerSize() { return SLOTS; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return slot >= 0 && slot < SLOTS ? items.get(slot) : ItemStack.EMPTY; }
    @Override public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) setChanged();
        return result;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }
    @Override public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SLOTS) return;
        if (!stack.isEmpty()) stack.setCount(Math.min(stack.getCount(), Math.min(64, stack.getMaxStackSize())));
        items.set(slot, stack); setChanged();
    }
    @Override public void clearContent() { items.clear(); setChanged(); }
    @Override public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64;
    }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot >= OUTPUT || stack.isEmpty() || level == null) return false;
        if (slot == BOTTLES) return !kind().crusher() && stack.is(Items.GLASS_BOTTLE);
        return level.getRecipeManager().getAllRecipesFor(AlchemyContent.RECIPES.get()).stream().anyMatch(recipe ->
                (kind().crusher() ? recipe.process().equals("crushing") : !recipe.process().equals("crushing"))
                        && (slot == INPUT ? recipe.input().test(stack) : !kind().crusher() && recipe.reagent() != null && recipe.reagent().test(stack)));
    }
    @Override public int[] getSlotsForFace(Direction face) { return face == Direction.DOWN ? new int[]{3, 4, 5, 6} : kind().crusher() ? new int[]{0} : new int[]{0, 1, 2}; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction face) { return canPlaceItem(slot, stack); }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction face) { return slot >= OUTPUT; }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        tag.putInt("Mode", mode); tag.putInt("Water", water); tag.putInt("Credit", credit);
        if (hasJob()) {
            CompoundTag saved = job.copy();
            saved.putInt("Progress", progress); saved.putInt("Duration", duration);
            CompoundTag pending = new CompoundTag(); ContainerHelper.saveAllItems(pending, products, true);
            saved.put("Products", pending); tag.put("AlchemyJob", saved);
        }
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        items.clear(); ContainerHelper.loadAllItems(tag, items);
        for (ItemStack stack : items) if (!stack.isEmpty()) stack.setCount(Math.min(stack.getCount(), Math.min(64, stack.getMaxStackSize())));
        mode = Math.max(0, Math.min(1, tag.getInt("Mode")));
        water = Math.max(0, Math.min(WATER_CAPACITY, tag.getInt("Water")));
        credit = Math.max(0, Math.min(kind().crusher() ? 9 : 4, tag.getInt("Credit")));
        operator = null; leaseUntil = 0;
        job = tag.getCompound("AlchemyJob").copy(); products.clear(); progress = duration = 0;
        if (!job.isEmpty()) {
            duration = Math.max(1, Math.min(72000, job.getInt("Duration")));
            progress = Math.max(0, Math.min(duration - 1, job.getInt("Progress")));
            if (job.contains("Products", Tag.TAG_COMPOUND)) ContainerHelper.loadAllItems(job.getCompound("Products"), products);
            for (ItemStack stack : products) if (!stack.isEmpty()) stack.setCount(Math.min(stack.getCount(), Math.min(64, stack.getMaxStackSize())));
        }
    }
}
