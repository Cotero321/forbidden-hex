package com.example.akaishi.forbidden.brewing;

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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class WitchOvenBlockEntity extends BlockEntity implements WorldlyContainer, ExtendedMenuProvider {
    public static final int INPUT = 0, FUEL = 1, RESULT = 2, FUME = 3, SLOTS = 4;
    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private int burnTime, burnTotal, progress, cookingTime, activeBatch;
    private WitchOvenProfile profile = WitchOvenProfile.BASIC;
    private String activeId = "";
    private CompoundTag activeSignature = new CompoundTag();
    private ItemStack activeInput = ItemStack.EMPTY;
    private WitchOvenRecipe activeRecipe;
    private int syncedQueues = 1, syncedChance = 3000;
    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            if (index == 8) return level != null && level.isClientSide ? syncedQueues : profile.queues();
            if (index == 9) return level != null && level.isClientSide ? syncedChance
                    : (int) Math.round((activeRecipe == null ? Math.min(profile.fumeChanceCap(), 0.3 + profile.fumeBonus()) : profile.chance(activeRecipe)) * 10000);
            int value = switch (index / 2) { case 0 -> burnTime; case 1 -> burnTotal; case 2 -> progress; case 3 -> cookingTime; default -> 0; };
            return (value >>> ((index % 2) * 16)) & 0xFFFF;
        }
        @Override public void set(int index, int value) {
            if (index == 8) { syncedQueues = value; return; }
            if (index == 9) { syncedChance = value; return; }
            int original = switch (index / 2) { case 0 -> burnTime; case 1 -> burnTotal; case 2 -> progress; case 3 -> cookingTime; default -> 0; };
            int updated = index % 2 == 0 ? (original & 0xFFFF0000) | (value & 0xFFFF)
                    : (original & 0xFFFF) | ((value & 0xFFFF) << 16);
            switch (index / 2) { case 0 -> burnTime = updated; case 1 -> burnTotal = updated; case 2 -> progress = updated; case 3 -> cookingTime = updated; }
        }
        @Override public int getCount() { return 10; }
    };

    public WitchOvenBlockEntity(BlockPos pos, BlockState state) {
        super(BrewingBlocks.OVEN_ENTITY.get(), pos, state);
    }

    public ContainerData data() { return data; }
    public WitchOvenProfile profile() { return profile; }
    public void setProfile(WitchOvenProfile profile) {
        if (profile == null || profile.equals(this.profile) || isRemoved() || !(level instanceof net.minecraft.server.level.ServerLevel server)
                || !server.getServer().isSameThread() || level.getBlockEntity(worldPosition) != this) return;
        this.profile = profile;
        progress = 0;
        activeBatch = 0;
        setChanged();
    }

    private static java.util.function.ToIntFunction<ItemStack> fuelProvider =
            stack -> AbstractFurnaceBlockEntity.getFuel().getOrDefault(stack.getItem(), 0);
    private static java.util.function.Function<ItemStack, ItemStack> fuelRemainder = stack ->
            stack.getItem().getCraftingRemainingItem() == null ? ItemStack.EMPTY : new ItemStack(stack.getItem().getCraftingRemainingItem());
    public static void setFuelProvider(java.util.function.ToIntFunction<ItemStack> provider,
                                       java.util.function.Function<ItemStack, ItemStack> remainder) {
        fuelProvider = java.util.Objects.requireNonNull(provider);
        fuelRemainder = java.util.Objects.requireNonNull(remainder);
    }
    public static int fuelTime(ItemStack stack) { return Math.max(0, fuelProvider.applyAsInt(stack)); }

    public static void tick(Level level, BlockPos pos, BlockState state, WitchOvenBlockEntity oven) {
        if (level.isClientSide) return;
        boolean wasBurning = oven.burnTime > 0;
        boolean changed = false;
        if (wasBurning) { oven.burnTime--; changed = true; }
        WitchOvenRecipe recipe = level.getRecipeManager().getRecipeFor(BrewingRecipes.OVEN.get(), oven, level).orElse(null);
        if (recipe == null) {
            changed |= oven.progress != 0 || oven.cookingTime != 0 || !oven.activeId.isEmpty();
            oven.progress = 0;
            oven.activeBatch = 0;
            oven.cookingTime = 0;
            oven.activeId = "";
            oven.activeSignature = new CompoundTag();
            oven.activeInput = ItemStack.EMPTY;
            oven.activeRecipe = null;
        } else {
            CompoundTag signature = recipe.signature();
            if (!recipe.id().toString().equals(oven.activeId) || !ItemStack.isSameItemSameTags(oven.activeInput, oven.items.get(INPUT))
                    || !signature.equals(oven.activeSignature)) {
                oven.progress = 0;
                oven.activeBatch = 0;
                oven.activeId = recipe.id().toString();
                oven.activeSignature = signature;
                oven.activeInput = oven.items.get(INPUT).copyWithCount(1);
                changed = true;
            }
            oven.activeRecipe = recipe;
            oven.cookingTime = recipe.cookingTicks();
            if (oven.progress >= oven.cookingTime) {
                oven.progress = 0;
                changed = true;
            }
            double chance = oven.profile.chance(recipe);
            if (oven.items.get(INPUT).getCount() < oven.activeBatch || oven.activeBatch > oven.profile.queues()) {
                oven.progress = 0;
                oven.activeBatch = 0;
                changed = true;
            }
            if (oven.activeBatch == 0) {
                int batch = Math.min(oven.profile.queues(), oven.items.get(INPUT).getCount());
                batch = Math.min(batch, room(oven.items.get(RESULT), recipe.result()) / recipe.result().getCount());
                if (chance > 0) batch = Math.min(batch, room(oven.items.get(FUME), recipe.fume()) / recipe.fume().getCount());
                oven.activeBatch = batch;
                changed |= batch > 0;
            }
            boolean ready = oven.activeBatch > 0
                    && room(oven.items.get(RESULT), recipe.result()) >= recipe.result().getCount() * oven.activeBatch
                    && (chance == 0 || room(oven.items.get(FUME), recipe.fume()) >= recipe.fume().getCount() * oven.activeBatch);
            if (ready && oven.burnTime == 0 && !oven.items.get(FUEL).isEmpty()) {
                int fuel = fuelTime(oven.items.get(FUEL));
                if (fuel > 0) {
                    oven.burnTime = fuel;
                    oven.burnTotal = fuel;
                    ItemStack remainder = fuelRemainder.apply(oven.items.get(FUEL).copyWithCount(1));
                    oven.items.get(FUEL).shrink(1);
                    if (oven.items.get(FUEL).isEmpty()) oven.items.set(FUEL, remainder);
                    else if (!remainder.isEmpty()) net.minecraft.world.Containers.dropItemStack(level,
                            pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, remainder);
                    changed = true;
                }
            }
            if (ready && oven.burnTime > 0) {
                oven.progress++;
                changed = true;
                if (oven.progress >= oven.cookingTime) {
                    for (int i = 0; i < oven.activeBatch; i++) {
                        boolean hasFume = level.random.nextDouble() < chance;
                        add(oven.items, RESULT, recipe.result());
                        if (hasFume) add(oven.items, FUME, recipe.fume());
                    }
                    oven.items.get(INPUT).shrink(oven.activeBatch);
                    oven.progress = 0;
                    oven.activeBatch = 0;
                }
            }
        }
        boolean burning = oven.burnTime > 0;
        if (burning != wasBurning) level.setBlock(pos, state.setValue(WitchOvenBlock.LIT, burning), 3);
        if (changed) oven.setChanged();
    }

    private static int room(ItemStack existing, ItemStack result) {
        if (existing.isEmpty()) return Math.min(result.getMaxStackSize(), 64);
        return ItemStack.isSameItemSameTags(existing, result) ? Math.max(0, Math.min(existing.getMaxStackSize(), 64) - existing.getCount()) : 0;
    }
    private static void add(NonNullList<ItemStack> inventory, int slot, ItemStack stack) {
        if (inventory.get(slot).isEmpty()) inventory.set(slot, stack.copy());
        else inventory.get(slot).grow(stack.getCount());
    }

    @Override public Component getDisplayName() { return Component.translatable("block.forbidden_hex.forbidden_hex_witch_oven"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new WitchOvenMenu(id, inventory, this, data); }
    @Override public void saveExtraData(FriendlyByteBuf buffer) { buffer.writeBlockPos(worldPosition); }
    @Override public int getContainerSize() { return SLOTS; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return slot >= 0 && slot < SLOTS ? items.get(slot) : ItemStack.EMPTY; }
    @Override public ItemStack removeItem(int slot, int amount) {
        ItemStack stack = ContainerHelper.removeItem(items, slot, amount);
        if (!stack.isEmpty()) setChanged();
        return stack;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }
    @Override public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SLOTS) return;
        items.set(slot, stack);
        if (!stack.isEmpty()) stack.setCount(Math.min(stack.getCount(), Math.min(getMaxStackSize(), stack.getMaxStackSize())));
        setChanged();
    }
    @Override public void clearContent() { items.clear(); setChanged(); }
    @Override public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64;
    }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == FUEL) return fuelTime(stack) > 0;
        return slot == INPUT && level != null && level.getRecipeManager().getAllRecipesFor(BrewingRecipes.OVEN.get())
                .stream().anyMatch(recipe -> recipe.input().test(stack));
    }
    @Override public int[] getSlotsForFace(Direction face) { return face == Direction.DOWN ? new int[]{RESULT, FUME, FUEL} : face == Direction.UP ? new int[]{INPUT} : new int[]{FUEL}; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction face) { return canPlaceItem(slot, stack); }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction face) {
        return slot == RESULT || slot == FUME || slot == FUEL && fuelTime(stack) == 0;
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        tag.putInt("Burn", burnTime);
        tag.putInt("BurnTotal", burnTotal);
        tag.putInt("Progress", progress);
        tag.putInt("CookingTime", cookingTime);
        tag.putString("Recipe", activeId);
        tag.put("ActiveInput", activeInput.save(new CompoundTag()));
        tag.put("RecipeSignature", activeSignature);
        tag.putInt("ActiveBatch", activeBatch);
        CompoundTag upgrade = new CompoundTag();
        upgrade.putInt("Queues", profile.queues());
        upgrade.putDouble("FumeBonus", profile.fumeBonus());
        upgrade.putDouble("ChanceCap", profile.fumeChanceCap());
        tag.put("Upgrade", upgrade);
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        items.clear();
        ContainerHelper.loadAllItems(tag, items);
        for (int i = 0; i < SLOTS; i++) {
            ItemStack stack = items.get(i);
            if (!stack.isEmpty()) stack.setCount(Math.min(stack.getCount(), Math.min(stack.getMaxStackSize(), 64)));
        }
        burnTime = Math.max(0, tag.getInt("Burn"));
        burnTotal = Math.max(burnTime, tag.getInt("BurnTotal"));
        cookingTime = Math.max(0, Math.min(72000, tag.getInt("CookingTime")));
        progress = Math.max(0, Math.min(cookingTime, tag.getInt("Progress")));
        activeId = tag.getString("Recipe");
        activeInput = ItemStack.of(tag.getCompound("ActiveInput"));
        activeSignature = tag.getCompound("RecipeSignature").copy();
        profile = WitchOvenProfile.BASIC;
        boolean validProfile = !tag.contains("Upgrade");
        if (tag.contains("Upgrade", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            CompoundTag upgrade = tag.getCompound("Upgrade");
            if (upgrade.contains("Queues", net.minecraft.nbt.Tag.TAG_INT)
                    && upgrade.contains("FumeBonus", net.minecraft.nbt.Tag.TAG_DOUBLE)
                    && upgrade.contains("ChanceCap", net.minecraft.nbt.Tag.TAG_DOUBLE)) {
                try {
                    profile = new WitchOvenProfile(upgrade.getInt("Queues"), upgrade.getDouble("FumeBonus"), upgrade.getDouble("ChanceCap"));
                    validProfile = true;
                } catch (IllegalArgumentException exception) {
                    profile = WitchOvenProfile.BASIC;
                }
            }
        }
        activeBatch = tag.getInt("ActiveBatch");
        if (!validProfile || !tag.contains("ActiveBatch", net.minecraft.nbt.Tag.TAG_INT)
                || activeBatch < 1 || activeBatch > profile.queues() || activeBatch > items.get(INPUT).getCount()
                || activeId.isEmpty() || activeInput.isEmpty() || activeSignature.isEmpty()
                || !ItemStack.isSameItemSameTags(activeInput, items.get(INPUT))) {
            progress = 0;
            cookingTime = 0;
            activeBatch = 0;
            activeId = "";
            activeInput = ItemStack.EMPTY;
            activeSignature = new CompoundTag();
        }
        activeRecipe = null;
    }
}
