package com.example.akaishi.forbidden.brewing;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class CrucibleBlockEntity extends BlockEntity {
    public static final int WATER_CAPACITY = 1000;
    public static final int MAX_STEPS = 128;
    private int water;
    private boolean locked;
    private boolean corrupt;
    private final List<ItemStack> steps = new ArrayList<>();
    private final List<ItemStack> outputs = new ArrayList<>();

    public CrucibleBlockEntity(BlockPos pos, BlockState state) {
        super(BrewingBlocks.CRUCIBLE_ENTITY.get(), pos, state);
    }

    public int water() { return water; }
    public boolean locked() { return locked; }
    public List<ItemStack> steps() { return steps.stream().map(ItemStack::copy).toList(); }
    public boolean hasOutputs() { return !outputs.isEmpty(); }
    public boolean corrupt() { return corrupt; }

    private boolean canMutate() {
        return !isRemoved() && level instanceof net.minecraft.server.level.ServerLevel server
                && server.getServer().isSameThread() && level.getBlockEntity(worldPosition) == this;
    }

    public int fillWater(int amount, boolean execute) {
        if (!canMutate() || corrupt || locked || hasOutputs() || amount <= 0) return 0;
        int accepted = Math.min(amount, WATER_CAPACITY - water);
        if (accepted > 0 && execute) { water += accepted; changed(); }
        return accepted;
    }
    public int drainWater(int amount, boolean execute) {
        if (!canMutate() || corrupt || locked || hasOutputs() || amount <= 0) return 0;
        int drained = Math.min(amount, water);
        if (drained > 0 && execute) { water -= drained; changed(); }
        return drained;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, CrucibleBlockEntity pot) {
        if (level.isClientSide || !pot.canMutate()) return;
        if (!pot.corrupt && pot.hasOutputs()) {
            ItemStack output = pot.outputs.get(0);
            ItemEntity entity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, output.copy());
            entity.setDefaultPickUpDelay();
            entity.getPersistentData().putLong("forbidden_hex_crucible_source", pos.asLong());
            entity.setDeltaMovement(0, 0.12, 0);
            if (level.addFreshEntity(entity)) { pot.outputs.remove(0); pot.changed(); }
            return;
        }
        AABB mouth = new AABB(pos.getX() + 0.2, pos.getY() + 0.25, pos.getZ() + 0.2,
                pos.getX() + 0.8, pos.getY() + 1.25, pos.getZ() + 0.8);
        List<ItemEntity> candidates = level.getEntitiesOfClass(ItemEntity.class, mouth,
                entity -> entity.isAlive() && !entity.isRemoved()
                        && entity.getX() >= mouth.minX && entity.getX() <= mouth.maxX
                        && entity.getZ() >= mouth.minZ && entity.getZ() <= mouth.maxZ
                        && !(entity.getPersistentData().contains("forbidden_hex_crucible_source", Tag.TAG_LONG)
                        && entity.getPersistentData().getLong("forbidden_hex_crucible_source") == pos.asLong()));
        candidates.sort(Comparator.comparingInt(ItemEntity::getAge).reversed().thenComparing(ItemEntity::getUUID));
        for (ItemEntity entity : candidates) {
            if (!entity.isAlive() || entity.isRemoved() || entity.getItem().isEmpty()) continue;
            if (pot.accept(entity.getItem())) {
                ItemStack remaining = entity.getItem().copy();
                remaining.shrink(1);
                if (remaining.isEmpty()) entity.discard(); else entity.setItem(remaining);
                break;
            }
        }
    }

    public boolean accept(ItemStack input) {
        if (!canMutate() || input.isEmpty()) return false;
        if (input.is(BrewingItems.WOOD_ASH.get())) {
            if (hasOutputs() && !corrupt || water == 0 && steps.isEmpty() && !locked && !corrupt) return false;
            outputs.clear();
            resetBatch();
            changed();
            return true;
        }
        if (corrupt || hasOutputs() || input.hasTag() && input.getTag().sizeInBytes() > 65536) return false;
        if (input.is(BrewingItems.GRASS_ASH.get())) {
            if (steps.isEmpty()) return false;
            steps.remove(steps.size() - 1);
            changed();
            return true;
        }
        if (water != WATER_CAPACITY || steps.size() >= MAX_STEPS || BrewingHeat.below(level, worldPosition) == BrewingHeat.NONE) return false;
        List<ItemStack> candidate = new ArrayList<>(steps);
        candidate.add(input);
        boolean known = level.getRecipeManager().getAllRecipesFor(BrewingRecipes.CRUCIBLE.get()).stream()
                .anyMatch(recipe -> recipe.prefix(candidate));
        if (!known) return false;
        locked = true;
        steps.add(input.copyWithCount(1));
        changed();
        return true;
    }

    public boolean commitRecipe(Player player) {
        if (player == null || player.level() != level || !player.isAlive()
                || player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) > 64) return false;
        return commitRecipe();
    }

    public boolean commitRecipe() {
        if (!canMutate() || corrupt || !locked || water != WATER_CAPACITY || hasOutputs()) return false;
        BrewingHeat heat = BrewingHeat.below(level, worldPosition);
        List<CrucibleRecipe> matched = level.getRecipeManager().getAllRecipesFor(BrewingRecipes.CRUCIBLE.get()).stream()
                .filter(recipe -> recipe.complete(steps, heat)).toList();
        if (matched.size() != 1) return false;
        CrucibleRecipe recipe = matched.get(0);
        ItemStack result = recipe.evaluate(steps).result();
        if (result.isEmpty() || result.getCount() > result.getMaxStackSize()) return false;
        if (recipe.potion()) {
            if (BrewingPotionData.read(result).isEmpty()) return false;
            for (int i = 0; i < 4; i++) outputs.add(result.copyWithCount(1));
        } else outputs.add(result);
        resetBatch();
        changed();
        return true;
    }

    private void resetBatch() { water = 0; locked = false; corrupt = false; steps.clear(); }

    private boolean validateLoadedState() {
        if (water < 0 || water > WATER_CAPACITY || steps.size() > MAX_STEPS || outputs.size() > 4) return false;
        for (ItemStack stack : steps) if (stack.isEmpty() || stack.getCount() != 1 || stack.hasTag() && stack.getTag().sizeInBytes() > 65536) return false;
        for (ItemStack stack : outputs) if (stack.isEmpty() || stack.getCount() < 1 || stack.getCount() > stack.getMaxStackSize()
                || stack.hasTag() && stack.getTag().sizeInBytes() > 65536
                || stack.is(BrewingItems.POTION.get()) && (stack.getCount() != 1 || BrewingPotionData.read(stack).isEmpty())) return false;
        return (!locked || water == WATER_CAPACITY)
                && (outputs.isEmpty() || water == 0 && !locked && steps.isEmpty());
    }

    public List<ItemStack> takeOutputs() {
        if (!canMutate() || corrupt) return List.of();
        List<ItemStack> result = outputs.stream().map(ItemStack::copy).toList();
        outputs.clear();
        setChanged();
        return result;
    }

    public Component status() {
        return Component.translatable("message.forbidden_hex.brewing.status", water, WATER_CAPACITY,
                steps.size(), Component.translatable(locked ? "message.forbidden_hex.brewing.locked" : "message.forbidden_hex.brewing.unlocked"));
    }

    public Component recipeStatus() {
        if (corrupt) return Component.translatable("message.forbidden_hex.brewing.corrupt");
        if (hasOutputs()) return Component.translatable("message.forbidden_hex.brewing.outputs");
        if (level == null) return Component.translatable("message.forbidden_hex.brewing.waiting");
        var recipes = level.getRecipeManager().getAllRecipesFor(BrewingRecipes.CRUCIBLE.get());
        if (recipes.isEmpty()) return Component.translatable("message.forbidden_hex.brewing.no_recipes");
        if (steps.isEmpty() || water != WATER_CAPACITY) return Component.translatable("message.forbidden_hex.brewing.waiting");
        BrewingHeat heat = BrewingHeat.below(level, worldPosition);
        long completed = recipes.stream().filter(recipe -> recipe.complete(steps, heat)).count();
        if (completed > 1) return Component.translatable("message.forbidden_hex.brewing.ambiguous");
        if (completed == 1) return Component.translatable("message.forbidden_hex.brewing.ready");
        if (recipes.stream().anyMatch(recipe -> recipe.prefix(steps))) {
            if (heat == BrewingHeat.NONE) return Component.translatable("message.forbidden_hex.brewing.no_heat");
            boolean wrongHeat = recipes.stream().anyMatch(recipe -> {
                var evaluation = recipe.evaluate(steps);
                return evaluation.valid() && evaluation.baseSteps() == recipe.steps().size();
            });
            return Component.translatable(wrongHeat ? "message.forbidden_hex.brewing.wrong_heat" : "message.forbidden_hex.brewing.incomplete");
        }
        return Component.translatable("message.forbidden_hex.brewing.recover");
    }

    public int color() {
        if (level != null) {
            for (CrucibleRecipe recipe : level.getRecipeManager().getAllRecipesFor(BrewingRecipes.CRUCIBLE.get())) {
                if (recipe.potion() && recipe.prefix(steps) && !steps.isEmpty()) {
                    var data = BrewingPotionData.read(recipe.result());
                    if (data.isPresent()) return data.get().effects().get(0).getEffect().getColor();
                }
            }
        }
        return 0x3F76E4;
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            int visible = water == 0 ? 0 : Math.max(1, (water * 3 + WATER_CAPACITY - 1) / WATER_CAPACITY);
            BlockState state = getBlockState();
            if (state.getValue(CrucibleBlock.LEVEL) != visible) {
                level.setBlock(worldPosition, state.setValue(CrucibleBlock.LEVEL, visible), 3);
            }
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("BrewingVersion", 1);
        tag.putInt("Water", water);
        tag.putBoolean("Locked", locked);
        tag.putBoolean("Corrupt", corrupt);
        tag.put("Steps", saveStacks(steps));
        tag.put("Outputs", saveStacks(outputs));
    }
    private static ListTag saveStacks(List<ItemStack> stacks) {
        ListTag result = new ListTag();
        for (ItemStack stack : stacks) result.add(stack.save(new CompoundTag()));
        return result;
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        steps.clear();
        outputs.clear();
        corrupt = tag.getBoolean("Corrupt")
                || tag.contains("BrewingVersion") && (!tag.contains("BrewingVersion", Tag.TAG_INT) || tag.getInt("BrewingVersion") != 1)
                || tag.contains("Water") && !tag.contains("Water", Tag.TAG_INT)
                || tag.contains("Locked") && !tag.contains("Locked", Tag.TAG_BYTE)
                || tag.contains("Corrupt") && !tag.contains("Corrupt", Tag.TAG_BYTE);
        water = tag.contains("Water", Tag.TAG_INT) ? tag.getInt("Water") : 0;
        corrupt |= !loadStacks(tag, "Steps", steps, MAX_STEPS);
        corrupt |= !loadStacks(tag, "Outputs", outputs, 4);
        locked = tag.getBoolean("Locked") || !steps.isEmpty();
        corrupt |= !validateLoadedState();
        if (corrupt) {
            water = Math.max(0, Math.min(WATER_CAPACITY, water));
            steps.clear();
            outputs.clear();
            locked = true;
        }
    }
    private static boolean loadStacks(CompoundTag tag, String key, List<ItemStack> stacks, int maximum) {
        if (!tag.contains(key)) return true;
        if (!(tag.get(key) instanceof ListTag list) || list.size() > maximum
                || !list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) return false;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (entry.sizeInBytes() > 65536) return false;
            ItemStack stack = ItemStack.of(entry);
            if (stack.isEmpty()) return false;
            stacks.add(stack);
        }
        return true;
    }
    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
