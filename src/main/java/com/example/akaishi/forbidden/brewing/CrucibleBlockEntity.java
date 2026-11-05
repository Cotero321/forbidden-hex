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

public class CrucibleBlockEntity extends BlockEntity implements dev.architectury.registry.menu.ExtendedMenuProvider {
    public static final int WATER_CAPACITY = 1000;
    public static final int HEATING_TICKS = 100;
    public static final int MAX_STEPS = 128;
    private int water;
    private int heatingTicks;
    private boolean locked;
    private boolean brewing;
    private boolean corrupt;
    private String brewingRecipeId = "";
    private final List<ItemStack> steps = new ArrayList<>();
    private final List<ItemStack> outputs = new ArrayList<>();
    private final CrucibleInventory inventory = new CrucibleInventory(this);
    private boolean slotsEnabled = true;
    private CompoundTag quarantinedBatch;

    public CrucibleInventory inventory() { return inventory; }
    public boolean slotsEnabled() { return slotsEnabled; }
    @Override public Component getDisplayName() { return Component.translatable("container.forbidden_hex.crucible"); }
    @Override public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory playerInventory, Player player) {
        return new CrucibleMenu(id, playerInventory, this);
    }
    @Override public void saveExtraData(net.minecraft.network.FriendlyByteBuf buffer) { buffer.writeBlockPos(worldPosition); }

    public CrucibleBlockEntity(BlockPos pos, BlockState state) {
        super(BrewingBlocks.CRUCIBLE_ENTITY.get(), pos, state);
    }

    public int water() { return water; }
    public int heatingTicks() { return heatingTicks; }
    public boolean brewing() { return brewing; }
    public boolean locked() { return locked; }
    public List<ItemStack> steps() { return steps.stream().map(ItemStack::copy).toList(); }
    public boolean hasOutputs() { return !outputs.isEmpty(); }
    public boolean corrupt() { return corrupt; }

    boolean canMutate() {
        return !isRemoved() && level instanceof net.minecraft.server.level.ServerLevel server
                && server.getServer().isSameThread() && level.getBlockEntity(worldPosition) == this;
    }

    public int fillWater(int amount, boolean execute) {
        if (!canMutate() || corrupt || locked || inventory.active() || inventory.pendingReturns() || hasOutputs() || amount <= 0) return 0;
        int accepted = Math.min(amount, WATER_CAPACITY - water);
        if (accepted > 0 && execute) { water += accepted; changed(); }
        return accepted;
    }
    public int drainWater(int amount, boolean execute) {
        if (!canMutate() || corrupt || locked || inventory.active() || inventory.pendingReturns() || hasOutputs() || amount <= 0) return 0;
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
        if (pot.slotsEnabled) { pot.inventory.tick(); return; }
        if (pot.brewing) pot.advanceBrewing();
        if (!pot.locked && !pot.brewing && pot.steps.isEmpty()) {
            pot.slotsEnabled = true;
            pot.changed();
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
                ItemStack accepted = entity.getItem().copyWithCount(1);
                ItemStack container = accepted.is(net.minecraft.world.item.Items.POTION)
                        ? new ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE) : accepted.getCraftingRemainingItem();
                if (!container.isEmpty()) {
                    ItemEntity returned = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, container);
                    returned.setDefaultPickUpDelay();
                    returned.getPersistentData().putLong("forbidden_hex_crucible_source", pos.asLong());
                    level.addFreshEntity(returned);
                }
                ItemStack remaining = entity.getItem().copy();
                remaining.shrink(1);
                if (remaining.isEmpty()) entity.discard(); else entity.setItem(remaining);
                break;
            }
        }
    }

    public boolean accept(ItemStack input) {
        if (!canMutate() || slotsEnabled || input.isEmpty()) return false;
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
            cancelBrewing();
            changed();
            return true;
        }
        if (brewing || water != WATER_CAPACITY || steps.size() >= MAX_STEPS || BrewingHeat.below(level, worldPosition) == BrewingHeat.NONE) return false;
        List<ItemStack> candidate = new ArrayList<>(steps);
        candidate.add(input);
        boolean known = legacyRecipes(candidate).stream()
                .anyMatch(recipe -> recipe.prefix(candidate));
        if (!known && !BrewingPotionComposer.evaluate(candidate).valid()) return false;
        locked = true;
        steps.add(input.copyWithCount(1));
        changed();
        return true;
    }

    private List<CrucibleRecipe> legacyRecipes(List<ItemStack> history) {
        List<CrucibleRecipe> recipes = new ArrayList<>(level.getRecipeManager().getAllRecipesFor(BrewingRecipes.CRUCIBLE.get()));
        if (slotsEnabled) return recipes;
        var id = new net.minecraft.resources.ResourceLocation("forbidden_hex", "brewing/transmutation_dust");
        recipes.removeIf(recipe -> recipe.id().equals(id) || recipe.slottedOnly());
        if (history.isEmpty() || !history.get(0).is(net.minecraft.world.item.Items.REDSTONE)) return recipes;
        var ingredients = List.of(net.minecraft.world.item.Items.REDSTONE, net.minecraft.world.item.Items.GLOWSTONE_DUST,
                net.minecraft.world.item.Items.GUNPOWDER, net.minecraft.world.item.Items.SUGAR).stream()
                .map(item -> new BrewingIngredient(net.minecraft.world.item.crafting.Ingredient.of(item), new CompoundTag())).toList();
        CrucibleRecipe legacy = new CrucibleRecipe(id, ingredients, BrewingHeat.FIRE, new ItemStack(BrewingItems.TRANSMUTATION_DUST.get()), false);
        if (legacy.prefix(history)) recipes.add(legacy);
        return recipes;
    }

    public boolean commitRecipe(Player player) {
        if (player == null || player.level() != level || !player.isAlive()
                || player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) > 64) return false;
        return commitRecipe();
    }

    public boolean commitRecipe() {
        if (slotsEnabled) return inventory.start(null);
        if (!canMutate() || corrupt || brewing || !locked || water != WATER_CAPACITY || hasOutputs()) return false;
        BrewingHeat heat = BrewingHeat.below(level, worldPosition);
        List<CrucibleRecipe> matched = legacyRecipes(steps).stream()
                .filter(recipe -> recipe.complete(steps, heat)).toList();
        if (matched.size() > 1) return false;
        if (matched.isEmpty()) {
            boolean fixedPrefix = legacyRecipes(steps).stream()
                    .anyMatch(recipe -> recipe.prefix(steps));
            if (fixedPrefix || heat != BrewingHeat.FIRE || !BrewingPotionComposer.evaluate(steps).complete()) return false;
            brewingRecipeId = BrewingPotionComposer.RECIPE_ID;
        } else brewingRecipeId = matched.get(0).id().toString();
        brewing = true;
        heatingTicks = 0;
        changed();
        return true;
    }

    private void advanceBrewing() {
        if (BrewingPotionComposer.RECIPE_ID.equals(brewingRecipeId)) {
            var evaluation = BrewingPotionComposer.evaluate(steps);
            if (!evaluation.complete()) { cancelBrewing(); changed(); return; }
            if (BrewingHeat.below(level, worldPosition) != BrewingHeat.FIRE) {
                if (heatingTicks != 0) { heatingTicks = 0; changed(); }
                return;
            }
            heatingTicks++;
            setChanged();
            if (heatingTicks < HEATING_TICKS) {
                if (heatingTicks % 20 == 0) changed();
                return;
            }
            ItemStack result = BrewingPotionComposer.result(evaluation);
            for (int i = 0; i < WATER_CAPACITY / BrewingPotionData.VOLUME; i++) outputs.add(result.copy());
            resetBatch();
            changed();
            return;
        }
        var recipes = legacyRecipes(steps);
        List<CrucibleRecipe> matched = recipes.stream().filter(recipe -> recipe.id().toString().equals(brewingRecipeId)
                && recipe.prefix(steps) && recipe.evaluate(steps).baseSteps() == recipe.steps().size()).toList();
        if (matched.size() != 1) {
            cancelBrewing();
            changed();
            return;
        }
        CrucibleRecipe recipe = matched.get(0);
        if (BrewingHeat.below(level, worldPosition) != recipe.heat()) {
            if (heatingTicks != 0) { heatingTicks = 0; changed(); }
            return;
        }
        heatingTicks++;
        setChanged();
        if (heatingTicks < HEATING_TICKS) {
            if (heatingTicks % 20 == 0) changed();
            return;
        }
        ItemStack result = recipe.potion() ? recipe.evaluate(steps).result() : recipe.randomResult(level.random);
        if (result.isEmpty() || result.getCount() > result.getMaxStackSize()
                || recipe.potion() && BrewingPotionData.read(result).isEmpty()) {
            cancelBrewing();
            changed();
            return;
        }
        if (recipe.potion()) {
            for (int i = 0; i < WATER_CAPACITY / BrewingPotionData.VOLUME; i++) outputs.add(result.copyWithCount(1));
        } else outputs.add(result);
        resetBatch();
        changed();
    }

    private void cancelBrewing() {
        brewing = false;
        heatingTicks = 0;
        brewingRecipeId = "";
    }

    private void resetBatch() {
        water = 0;
        locked = false;
        corrupt = false;
        quarantinedBatch = null;
        steps.clear();
        cancelBrewing();
    }

    private boolean validateLoadedState() {
        if (water < 0 || water > WATER_CAPACITY || steps.size() > MAX_STEPS || outputs.size() > 4) return false;
        for (ItemStack stack : steps) if (stack.isEmpty() || stack.getCount() != 1 || stack.hasTag() && stack.getTag().sizeInBytes() > 65536) return false;
        for (ItemStack stack : outputs) if (stack.isEmpty() || stack.getCount() < 1 || stack.getCount() > stack.getMaxStackSize()
                || stack.hasTag() && stack.getTag().sizeInBytes() > 65536
                || stack.is(BrewingItems.POTION.get()) && (stack.getCount() != 1 || BrewingPotionData.read(stack).isEmpty())) return false;
        return (!locked || water == WATER_CAPACITY)
                && (outputs.isEmpty() || water == 0 && !locked && steps.isEmpty())
                && heatingTicks >= 0 && heatingTicks < HEATING_TICKS
                && (brewing ? locked && !steps.isEmpty() && outputs.isEmpty()
                    && net.minecraft.resources.ResourceLocation.tryParse(brewingRecipeId) != null
                    : heatingTicks == 0 && brewingRecipeId.isEmpty());
    }

    public List<ItemStack> takeOutputs() {
        if (!canMutate() || corrupt) return List.of();
        List<ItemStack> result = outputs.stream().map(ItemStack::copy).toList();
        outputs.clear();
        setChanged();
        return result;
    }

    boolean finishSlottedBatch(List<ItemStack> results) {
        if (!canMutate() || !slotsEnabled || corrupt || hasOutputs() || water != WATER_CAPACITY
                || results.isEmpty() || results.size() > 4) return false;
        for (ItemStack result : results) {
            if (result.isEmpty() || result.getCount() > result.getMaxStackSize()) return false;
        }
        for (ItemStack result : results) outputs.add(result.copy());
        water = 0;
        return true;
    }

    public boolean preservesBatchOnBreak() {
        return corrupt || !slotsEnabled && steps.stream().anyMatch(stack -> stack.is(net.minecraft.world.item.Items.POTION)
                || !stack.getCraftingRemainingItem().isEmpty());
    }

    public boolean canRecoverLegacy() {
        return !slotsEnabled && !brewing && !hasOutputs() && !preservesBatchOnBreak();
    }

    public List<ItemStack> takeContents() {
        if (!canMutate() || preservesBatchOnBreak()) return List.of();
        List<ItemStack> result = new ArrayList<>(takeOutputs());
        result.addAll(steps.stream().map(ItemStack::copy).toList());
        result.addAll(inventory.takeAll());
        resetBatch();
        return result;
    }

    public boolean recoverLegacy(Player player) {
        if (!canMutate() || player == null || !inventory.stillValid(player) || !canRecoverLegacy()) return false;
        for (ItemStack stack : steps) {
            ItemStack remainder = stack.copy();
            player.getInventory().add(remainder);
            if (!remainder.isEmpty()) player.drop(remainder, false);
        }
        steps.clear();
        locked = false;
        slotsEnabled = true;
        changed();
        return true;
    }

    public Component status() {
        return Component.translatable("message.forbidden_hex.brewing.status", water, WATER_CAPACITY,
                steps.size(), Component.translatable(locked ? "message.forbidden_hex.brewing.locked" : "message.forbidden_hex.brewing.unlocked"));
    }

    public Component recipeStatus() {
        if (corrupt) return Component.translatable("message.forbidden_hex.brewing.corrupt");
        if (hasOutputs()) return Component.translatable("message.forbidden_hex.brewing.outputs");
        if (level == null) return Component.translatable("message.forbidden_hex.brewing.waiting");
        var recipes = legacyRecipes(steps);
        if (steps.isEmpty() || water != WATER_CAPACITY) return Component.translatable("message.forbidden_hex.brewing.waiting");
        BrewingHeat heat = BrewingHeat.below(level, worldPosition);
        boolean custom = BrewingPotionComposer.RECIPE_ID.equals(brewingRecipeId)
                || recipes.stream().noneMatch(recipe -> recipe.prefix(steps));
        if (custom) {
            var evaluation = BrewingPotionComposer.evaluate(steps);
            if (!evaluation.valid()) return Component.translatable("message.forbidden_hex.brewing.recover");
            if (heat == BrewingHeat.NONE) return Component.translatable("message.forbidden_hex.brewing.no_heat");
            if (heat != BrewingHeat.FIRE) return Component.translatable("message.forbidden_hex.brewing.wrong_heat");
            if (brewing) return Component.translatable("message.forbidden_hex.brewing.heating", heatingTicks, HEATING_TICKS);
            return Component.translatable(evaluation.complete() ? "message.forbidden_hex.brewing.ready" : "message.forbidden_hex.brewing.incomplete");
        }
        if (brewing) {
            if (heat == BrewingHeat.NONE) return Component.translatable("message.forbidden_hex.brewing.no_heat");
            if (recipes.stream().noneMatch(recipe -> recipe.id().toString().equals(brewingRecipeId) && recipe.heat() == heat)) {
                return Component.translatable("message.forbidden_hex.brewing.wrong_heat");
            }
            return Component.translatable("message.forbidden_hex.brewing.heating", heatingTicks, HEATING_TICKS);
        }
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
            for (CrucibleRecipe recipe : legacyRecipes(steps)) {
                if (recipe.potion() && recipe.prefix(steps) && !steps.isEmpty()) {
                    var data = BrewingPotionData.read(recipe.result());
                    if (data.isPresent()) return data.get().color();
                }
            }
        }
        var custom = BrewingPotionComposer.evaluate(steps);
        if (custom.complete()) return custom.potion().color();
        if (!steps.isEmpty()) {
            ItemStack last = steps.get(steps.size() - 1);
            if (last.is(BrewingItems.FUME.get())) return BrewingFumes.color(BrewingFumeItem.kind(last));
            int hash = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(last.getItem()).toString().hashCode();
            return 0x404040 | (hash & 0xBFBFBF);
        }
        return 0x3F76E4;
    }

    void changed() {
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
        tag.putBoolean("Brewing", brewing);
        tag.putInt("HeatingTicks", heatingTicks);
        tag.putString("BrewingRecipe", brewingRecipeId);
        tag.put("Steps", saveStacks(steps));
        tag.put("Outputs", saveStacks(outputs));
        tag.putBoolean("SlotsEnabled", slotsEnabled);
        tag.put("SlotInventory", inventory.save());
        if (quarantinedBatch != null) tag.put("QuarantinedBatch", quarantinedBatch.copy());
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
        quarantinedBatch = tag.contains("QuarantinedBatch", Tag.TAG_COMPOUND) ? tag.getCompound("QuarantinedBatch").copy() : null;
        corrupt = tag.contains("QuarantinedBatch") || tag.getBoolean("Corrupt")
                || tag.contains("BrewingVersion") && (!tag.contains("BrewingVersion", Tag.TAG_INT) || tag.getInt("BrewingVersion") != 1)
                || tag.contains("Water") && !tag.contains("Water", Tag.TAG_INT)
                || tag.contains("Locked") && !tag.contains("Locked", Tag.TAG_BYTE)
                || tag.contains("Corrupt") && !tag.contains("Corrupt", Tag.TAG_BYTE)
                || tag.contains("Brewing") && !tag.contains("Brewing", Tag.TAG_BYTE)
                || tag.contains("HeatingTicks") && !tag.contains("HeatingTicks", Tag.TAG_INT)
                || tag.contains("BrewingRecipe") && !tag.contains("BrewingRecipe", Tag.TAG_STRING);
        brewing = tag.getBoolean("Brewing");
        heatingTicks = tag.getInt("HeatingTicks");
        brewingRecipeId = tag.getString("BrewingRecipe");
        corrupt |= brewingRecipeId.length() > 256;
        water = tag.contains("Water", Tag.TAG_INT) ? tag.getInt("Water") : 0;
        corrupt |= !loadStacks(tag, "Steps", steps, MAX_STEPS);
        corrupt |= !loadStacks(tag, "Outputs", outputs, 4);
        locked = tag.getBoolean("Locked") || !steps.isEmpty();
        corrupt |= !validateLoadedState();
        slotsEnabled = tag.getBoolean("SlotsEnabled");
        if (tag.contains("SlotInventory")) {
            corrupt |= !tag.contains("SlotInventory", Tag.TAG_COMPOUND) || !inventory.load(tag.getCompound("SlotInventory"));
        } else inventory.load(newInventoryTag());
        corrupt |= tag.contains("SlotsEnabled") && !tag.contains("SlotsEnabled", Tag.TAG_BYTE)
                || slotsEnabled && (!tag.contains("SlotInventory", Tag.TAG_COMPOUND) || !steps.isEmpty() || brewing || locked)
                || !slotsEnabled && (!inventory.isEmpty() || inventory.active() || inventory.pendingReturns())
                || inventory.active() && (water != WATER_CAPACITY || hasOutputs() || inventory.pendingReturns())
                || inventory.pendingReturns() && water != 0;
        if (corrupt) {
            if (quarantinedBatch == null) {
                quarantinedBatch = tag.copy();
                quarantinedBatch.remove("QuarantinedBatch");
            }
            water = Math.max(0, Math.min(WATER_CAPACITY, water));
            steps.clear();
            outputs.clear();
            locked = true;
            cancelBrewing();
        }
    }
    private static CompoundTag newInventoryTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", 1);
        tag.put("Items", new ListTag());
        tag.put("Returns", new ListTag());
        return tag;
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
    @Override public CompoundTag getUpdateTag() {
        CompoundTag tag = saveWithoutMetadata();
        tag.remove("QuarantinedBatch");
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
