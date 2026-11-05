package com.example.akaishi.forbidden.brewing;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public final class CrucibleInventory implements Container {
    public static final int BREW_SLOTS = 25, CRAFT_START = 25, CRAFT_SLOTS = 9, BOTTLE = 34, SIZE = 35;
    public static final int READY = 0, INCOMPLETE = 1, WATER = 2, HEAT = 3, BOTTLES = 4,
            BUSY = 5, OUTPUTS = 6, LEGACY = 7, INVALID = 8, AMBIGUOUS = 9;
    private final CrucibleBlockEntity owner;
    private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private final List<ItemStack> returns = new ArrayList<>();
    private int mode;
    private int progress;
    private boolean active;
    private String activeRecipe = "";
    private ItemStack activeRandomResult = ItemStack.EMPTY;
    private Plan cached;
    private long refreshAt;

    public record Plan(String recipe, BrewingHeat heat, List<ItemStack> outputs, List<ItemStack> returns,
                       int bottles, int status) { }

    public CrucibleInventory(CrucibleBlockEntity owner) { this.owner = owner; }
    public int mode() { return mode; }
    public int progress() { return progress; }
    public boolean active() { return active; }
    public boolean pendingReturns() { return !returns.isEmpty(); }
    public boolean editable() { return owner.slotsEnabled() && !owner.corrupt() && !active && !owner.hasOutputs() && returns.isEmpty(); }
    public boolean materialSlot(int index) { return mode == 0 ? index >= 0 && index < BREW_SLOTS : index >= CRAFT_START && index < BOTTLE; }

    public boolean setMode(int mode) {
        if (!owner.canMutate() || mode < 0 || mode > 1 || !editable()) return false;
        for (int i = 0; i < BOTTLE; i++) if (!items.get(i).isEmpty()) return false;
        this.mode = mode;
        setChanged();
        return true;
    }

    public List<ItemStack> materials() {
        List<ItemStack> result = new ArrayList<>();
        for (int i = 0; i < BOTTLE; i++) if (materialSlot(i) && !items.get(i).isEmpty()) result.add(items.get(i).copy());
        return result;
    }

    @Override public boolean canPlaceItem(int index, ItemStack stack) {
        if (!editable() || stack.isEmpty() || index < 0 || index >= SIZE
                || stack.save(new CompoundTag()).sizeInBytes() > 65520) return false;
        if (index == BOTTLE) return stack.is(Items.GLASS_BOTTLE);
        if (!materialSlot(index)) return false;
        List<ItemStack> candidate = new ArrayList<>();
        for (int i = 0; i < BOTTLE; i++) {
            if (!materialSlot(i)) continue;
            ItemStack value = i == index ? stack.copyWithCount(1) : items.get(i);
            if (!value.isEmpty()) candidate.add(value);
        }
        if (mode == 0 && BrewingPotionComposer.evaluateMaterials(candidate).valid()) return true;
        if (owner.getLevel() == null) return false;
        return owner.getLevel().getRecipeManager().getAllRecipesFor(BrewingRecipes.CRUCIBLE.get()).stream()
                .anyMatch(recipe -> recipe.potion() == (mode == 0) && recipe.evaluateMaterials(candidate).valid());
    }

    public Plan plan(boolean force) {
        if (owner.getLevel() == null || !owner.slotsEnabled() || owner.corrupt()) return empty(LEGACY);
        long time = owner.getLevel().getGameTime();
        if (!force && cached != null && time < refreshAt) return cached;
        List<ItemStack> materials = materials();
        List<ItemStack> outputs = new ArrayList<>();
        String recipeId = "";
        BrewingHeat heat = BrewingHeat.FIRE;
        CrucibleRecipe.BottlePolicy bottles = mode == 0 ? CrucibleRecipe.BottlePolicy.POTION : CrucibleRecipe.BottlePolicy.NONE;
        var recipes = owner.getLevel().getRecipeManager().getAllRecipesFor(BrewingRecipes.CRUCIBLE.get());
        var matched = recipes.stream().filter(recipe -> recipe.potion() == (mode == 0)).filter(recipe -> {
            var evaluation = recipe.evaluateMaterials(materials);
            return evaluation.valid() && evaluation.baseSteps() == recipe.steps().size();
        }).toList();
        if (matched.size() > 1) return cache(empty(AMBIGUOUS), time);
        if (matched.size() == 1) {
            CrucibleRecipe recipe = matched.get(0);
            recipeId = recipe.id().toString();
            heat = recipe.heat();
            ItemStack result = recipe.evaluateMaterials(materials).result();
            List<ItemStack> pool = recipe.resultPool();
            if (pool.size() > 1) {
                result = pool.get(0);
                if (active && recipeId.equals(activeRecipe)) {
                    if (pool.stream().noneMatch(candidate -> candidate.getCount() == activeRandomResult.getCount()
                            && ItemStack.isSameItemSameTags(candidate, activeRandomResult))) return cache(empty(INVALID), time);
                    result = activeRandomResult.copy();
                }
                // A preview uses the first candidate; only an accepted submission rolls the pool.
                outputs.add(result);
            } else {
                if (active && !activeRandomResult.isEmpty()) return cache(empty(INVALID), time);
                outputs.addAll(recipe.outputs(result));
            }
            if (mode == 1) bottles = recipe.bottles();
        } else if (mode == 0) {
            if (active && !activeRandomResult.isEmpty()) return cache(empty(INVALID), time);
            var evaluation = BrewingPotionComposer.evaluateMaterials(materials);
            if (!evaluation.valid()) return cache(empty(INVALID), time);
            if (!evaluation.complete()) return cache(empty(INCOMPLETE), time);
            recipeId = BrewingPotionComposer.RECIPE_ID;
            for (int i = 0; i < 4; i++) outputs.add(BrewingPotionComposer.result(evaluation));
        } else return cache(empty(INCOMPLETE), time);
        var containers = bottles.settle(materials);
        return cache(new Plan(recipeId, heat, List.copyOf(outputs), containers.returns(), containers.bottles(), READY), time);
    }

    private Plan cache(Plan plan, long time) { cached = plan; refreshAt = time + 20; return plan; }
    private static Plan empty(int status) { return new Plan("", BrewingHeat.NONE, List.of(), List.of(), 0, status); }
    public int status() {
        if (!owner.slotsEnabled() || owner.corrupt()) return LEGACY;
        if (active) return BUSY;
        if (owner.hasOutputs() || pendingReturns()) return OUTPUTS;
        Plan plan = plan(false);
        if (plan.status != READY) return plan.status;
        if (owner.water() != CrucibleBlockEntity.WATER_CAPACITY) return WATER;
        if (BrewingHeat.below(owner.getLevel(), owner.getBlockPos()) != plan.heat) return HEAT;
        if (!items.get(BOTTLE).isEmpty() && !items.get(BOTTLE).is(Items.GLASS_BOTTLE)
                || items.get(BOTTLE).getCount() < plan.bottles) return BOTTLES;
        return READY;
    }

    public boolean start(Player player) {
        if (!owner.canMutate() || player != null && !stillValid(player)) return false;
        plan(true);
        if (status() != READY) return false;
        var recipe = owner.getLevel().getRecipeManager().byKey(ResourceLocation.tryParse(cached.recipe))
                .filter(CrucibleRecipe.class::isInstance).map(CrucibleRecipe.class::cast).orElse(null);
        activeRandomResult = recipe != null && recipe.resultPool().size() > 1
                ? recipe.randomResult(owner.getLevel().random) : ItemStack.EMPTY;
        activeRecipe = cached.recipe;
        active = true;
        progress = 0;
        setChanged();
        return true;
    }

    public void tick() {
        if (!owner.canMutate() || owner.corrupt() || !owner.slotsEnabled()) return;
        if (pendingReturns()) { dropReturn(); return; }
        if (!active) return;
        Plan plan = plan(false);
        if (plan.status != READY || !plan.recipe.equals(activeRecipe)) { cancel(); return; }
        if (BrewingHeat.below(owner.getLevel(), owner.getBlockPos()) != plan.heat) {
            if (progress != 0) { progress = 0; setChanged(); }
            return;
        }
        if (++progress < CrucibleBlockEntity.HEATING_TICKS) {
            owner.setChanged();
            return;
        }
        plan = plan(true);
        if (plan.status != READY || !plan.recipe.equals(activeRecipe)
                || owner.water() != CrucibleBlockEntity.WATER_CAPACITY || owner.hasOutputs()
                || items.get(BOTTLE).getCount() < plan.bottles
                || !owner.finishSlottedBatch(plan.outputs)) { cancel(); return; }
        for (int i = 0; i < BOTTLE; i++) if (materialSlot(i)) items.set(i, ItemStack.EMPTY);
        items.get(BOTTLE).shrink(plan.bottles);
        for (ItemStack container : plan.returns) returns.add(container.copy());
        cancel();
    }

    private void cancel() { active = false; progress = 0; activeRecipe = ""; activeRandomResult = ItemStack.EMPTY; setChanged(); }
    private void dropReturn() {
        var pos = owner.getBlockPos();
        ItemEntity entity = new ItemEntity(owner.getLevel(), pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, returns.get(0).copy());
        entity.setDefaultPickUpDelay();
        entity.getPersistentData().putLong("forbidden_hex_crucible_source", pos.asLong());
        if (owner.getLevel().addFreshEntity(entity)) { returns.remove(0); setChanged(); }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", 1);
        tag.putInt("Mode", mode);
        tag.putBoolean("Active", active);
        tag.putInt("Progress", progress);
        tag.putString("Recipe", activeRecipe);
        if (!activeRandomResult.isEmpty()) tag.put("RandomResult", activeRandomResult.save(new CompoundTag()));
        ContainerHelper.saveAllItems(tag, items);
        ListTag list = new ListTag();
        for (ItemStack stack : returns) list.add(stack.save(new CompoundTag()));
        tag.put("Returns", list);
        return tag;
    }

    public boolean load(CompoundTag tag) {
        items.clear();
        returns.clear();
        activeRandomResult = ItemStack.EMPTY;
        cached = null;
        refreshAt = 0;
        mode = tag.getInt("Mode");
        active = tag.getBoolean("Active");
        progress = tag.getInt("Progress");
        activeRecipe = tag.getString("Recipe");
        if (!tag.contains("Version", Tag.TAG_INT) || tag.getInt("Version") != 1
                || tag.contains("Mode") && !tag.contains("Mode", Tag.TAG_INT)
                || tag.contains("Active") && !tag.contains("Active", Tag.TAG_BYTE)
                || tag.contains("Progress") && !tag.contains("Progress", Tag.TAG_INT)
                || tag.contains("Recipe") && !tag.contains("Recipe", Tag.TAG_STRING)
                || mode < 0 || mode > 1 || progress < 0 || progress >= CrucibleBlockEntity.HEATING_TICKS
                || activeRecipe.length() > 256 || active && ResourceLocation.tryParse(activeRecipe) == null
                || !active && (progress != 0 || !activeRecipe.isEmpty())
                || !(tag.get("Items") instanceof ListTag list) || list.size() > SIZE
                || !list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) return false;
        if (tag.contains("RandomResult")) {
            if (!active || !tag.contains("RandomResult", Tag.TAG_COMPOUND)) return false;
            CompoundTag result = tag.getCompound("RandomResult");
            activeRandomResult = ItemStack.of(result);
            if (activeRandomResult.isEmpty() || activeRandomResult.getCount() > activeRandomResult.getMaxStackSize()
                    || result.sizeInBytes() > 65536) return false;
        }
        boolean[] seen = new boolean[SIZE];
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int slot = entry.getByte("Slot") & 255;
            ItemStack stack = ItemStack.of(entry);
            if (!entry.contains("Slot", Tag.TAG_BYTE) || slot >= SIZE || seen[slot] || stack.isEmpty() || entry.sizeInBytes() > 65536
                    || stack.getCount() > (slot == BOTTLE ? stack.getMaxStackSize() : 1)
                    || slot == BOTTLE && !stack.is(Items.GLASS_BOTTLE)
                    || slot != BOTTLE && !materialSlot(slot)) return false;
            seen[slot] = true;
            items.set(slot, stack);
        }
        if (!(tag.get("Returns") instanceof ListTag containers) || containers.size() > BREW_SLOTS
                || !containers.isEmpty() && containers.getElementType() != Tag.TAG_COMPOUND) return false;
        for (int i = 0; i < containers.size(); i++) {
            ItemStack stack = ItemStack.of(containers.getCompound(i));
            if (stack.isEmpty() || stack.getCount() > stack.getMaxStackSize() || containers.getCompound(i).sizeInBytes() > 65536) return false;
            returns.add(stack);
        }
        cached = null;
        return !active || !materials().isEmpty() && returns.isEmpty();
    }

    public List<ItemStack> takeAll() {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack stack : items) if (!stack.isEmpty()) result.add(stack.copy());
        for (ItemStack stack : returns) result.add(stack.copy());
        items.clear();
        returns.clear();
        cancel();
        return result;
    }

    @Override public int getContainerSize() { return SIZE; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int index) { return index >= 0 && index < SIZE ? items.get(index) : ItemStack.EMPTY; }
    @Override public ItemStack removeItem(int index, int count) {
        if (index < 0 || index >= SIZE || !editable() || !owner.canMutate()) return ItemStack.EMPTY;
        ItemStack removed = ContainerHelper.removeItem(items, index, count);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }
    @Override public ItemStack removeItemNoUpdate(int index) { return removeItem(index, Integer.MAX_VALUE); }
    @Override public void setItem(int index, ItemStack stack) {
        if (index < 0 || index >= SIZE) return;
        boolean client = owner.getLevel() != null && owner.getLevel().isClientSide;
        if (!client && (!owner.canMutate() || !editable() || !stack.isEmpty() && (!canPlaceItem(index, stack)
                || stack.getCount() > (index == BOTTLE ? stack.getMaxStackSize() : 1)))) return;
        items.set(index, stack);
        setChanged();
    }
    @Override public void setChanged() { cached = null; owner.changed(); }
    @Override public boolean stillValid(Player player) {
        return owner.getLevel() != null && player.level() == owner.getLevel() && player.isAlive()
                && owner.getLevel().getBlockEntity(owner.getBlockPos()) == owner
                && player.distanceToSqr(owner.getBlockPos().getCenter()) <= 64;
    }
    @Override public void clearContent() { if (owner.canMutate() && editable()) { items.clear(); setChanged(); } }
}
