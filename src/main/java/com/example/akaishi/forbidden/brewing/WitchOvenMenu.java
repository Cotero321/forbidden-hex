package com.example.akaishi.forbidden.brewing;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class WitchOvenMenu extends AbstractContainerMenu {
    private final Container oven;
    private final ContainerData data;

    public WitchOvenMenu(int id, Inventory playerInventory, Container oven, ContainerData data) {
        super(BrewingMenus.OVEN.get(), id);
        checkContainerSize(oven, WitchOvenBlockEntity.SLOTS);
        checkContainerDataCount(data, 10);
        this.oven = oven;
        this.data = data;
        addSlot(new Slot(oven, 0, 35, 29) {
            @Override public boolean mayPlace(ItemStack stack) {
                return playerInventory.player.level().getRecipeManager().getAllRecipesFor(BrewingRecipes.OVEN.get())
                        .stream().anyMatch(recipe -> recipe.input().test(stack));
            }
        });
        addSlot(new Slot(oven, 1, 35, 57) {
            @Override public boolean mayPlace(ItemStack stack) { return WitchOvenBlockEntity.fuelTime(stack) > 0; }
        });
        addSlot(new Slot(oven, 2, 111, 29) { @Override public boolean mayPlace(ItemStack stack) { return false; } });
        addSlot(new Slot(oven, 3, 137, 29) { @Override public boolean mayPlace(ItemStack stack) { return false; } });
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, 9 + row * 9 + column, 8 + column * 18, 94 + row * 18));
        }
        for (int column = 0; column < 9; column++) addSlot(new Slot(playerInventory, column, 8 + column * 18, 152));
        addDataSlots(data);
    }
    public static WitchOvenMenu empty(int id, Inventory inventory) { return new WitchOvenMenu(id, inventory, new SimpleContainer(4), new SimpleContainerData(10)); }
    private int time(int index) { return (data.get(index * 2) & 0xFFFF) | ((data.get(index * 2 + 1) & 0xFFFF) << 16); }
    public int burn() { return time(0); }
    public int burnTotal() { return time(1); }
    public int progress() { return time(2); }
    public int total() { return time(3); }
    public int queues() { return Math.max(1, data.get(8)); }
    public double fumeChancePercent() { return data.get(9) / 100.0; }
    @Override public boolean stillValid(Player player) { return oven.stillValid(player); }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < 4) {
            if (!moveItemStackTo(stack, 4, slots.size(), true)) return ItemStack.EMPTY;
        } else if (slots.get(0).mayPlace(stack)) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else if (slots.get(1).mayPlace(stack)) {
            if (!moveItemStackTo(stack, 1, 2, false)) return ItemStack.EMPTY;
        } else if (index < 31) {
            if (!moveItemStackTo(stack, 31, 40, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 4, 31, false)) return ItemStack.EMPTY;
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }
}
