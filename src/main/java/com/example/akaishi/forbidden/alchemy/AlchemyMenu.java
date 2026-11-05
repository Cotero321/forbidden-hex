package com.example.akaishi.forbidden.alchemy;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class AlchemyMenu extends AbstractContainerMenu {
    private final Container inventory;
    private final ContainerData data;
    private final AlchemyMachineBlockEntity machine;
    public AlchemyMenu(int id, Inventory playerInventory, BlockPos pos) {
        this(id, playerInventory, playerInventory.player.level().getBlockEntity(pos) instanceof AlchemyMachineBlockEntity entity ? entity : null);
    }
    private AlchemyMenu(int id, Inventory playerInventory, AlchemyMachineBlockEntity machine) {
        this(id, playerInventory, machine == null ? new SimpleContainer(7) : machine,
                machine == null || playerInventory.player.level().isClientSide ? clientData(machine) : machine.data());
    }
    private static ContainerData clientData(AlchemyMachineBlockEntity machine) {
        var data = new SimpleContainerData(9);
        if (machine != null) data.set(6, machine.kind().ordinal());
        return data;
    }
    public AlchemyMenu(int id, Inventory playerInventory, Container inventory, ContainerData data) {
        super(AlchemyContent.MENU.get(), id);
        checkContainerSize(inventory, 7); checkContainerDataCount(data, 9);
        this.inventory = inventory; this.data = data;
        machine = inventory instanceof AlchemyMachineBlockEntity entity ? entity : null;
        for (int slot = 0; slot < 7; slot++) {
            final int index = slot;
            int x = slot < 3 ? 18 + slot * 26 : 104 + (slot - 3) * 22;
            addSlot(new Slot(inventory, slot, x, 39) {
                @Override public boolean mayPlace(ItemStack stack) { return index < 3 && machine != null && machine.canPlaceItem(index, stack); }
                @Override public boolean isActive() { return index == 0 || index >= 3 || !kind().crusher(); }
            });
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(playerInventory, 9 + row * 9 + col, 24 + col * 18, 114 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(playerInventory, col, 24 + col * 18, 172));
        addDataSlots(data);
    }
    public AlchemyMachineBlockEntity machine() { return machine; }
    public AlchemyContent.Kind kind() { return AlchemyContent.Kind.values()[Math.max(0, Math.min(3, data.get(6)))]; }
    public int progress() { return (data.get(0) & 65535) | ((data.get(1) & 65535) << 16); }
    public int total() { return (data.get(2) & 65535) | ((data.get(3) & 65535) << 16); }
    public int water() { return data.get(4); }
    public int mode() { return data.get(5); }
    public int status() { return data.get(7); }
    public boolean hasJob() { return data.get(8) != 0; }
    @Override public boolean clickMenuButton(Player player, int action) {
        if (machine == null || player.containerMenu != this || !stillValid(player) || player.isSpectator()) return false;
        if (action == 0) return machine.setMode(1 - machine.mode());
        if (action == 1 || action == 2) return machine.manualInput(player, action == 1);
        return false;
    }
    @Override public void removed(Player player) {
        super.removed(player);
        if (machine != null && !player.level().isClientSide) machine.manualInput(player, false);
    }
    @Override public boolean stillValid(Player player) { return inventory.stillValid(player); }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        if (index < 7) {
            if (!moveItemStackTo(stack, 7, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            for (int i = 0; i < 3; i++) if (slots.get(i).mayPlace(stack)) {
                moved = moveItemStackTo(stack, i, i + 1, false);
                if (moved) break;
            }
            if (!moved && !(index < 34 ? moveItemStackTo(stack, 34, 43, false) : moveItemStackTo(stack, 7, 34, false))) return ItemStack.EMPTY;
        }
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }
}
