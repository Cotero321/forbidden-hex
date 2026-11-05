package com.example.akaishi.forbidden.transmutation.source_furnace;

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

/** Two-slot menu for the source-energy transmutation furnace. */
public final class SourceTransmutationFurnaceMenu extends AbstractContainerMenu {
    private final Container inventory;
    private final ContainerData data;

    public SourceTransmutationFurnaceMenu(int id, Inventory playerInventory, BlockPos pos) {
        this(id, playerInventory, playerInventory.player.level().getBlockEntity(pos) instanceof SourceTransmutationFurnaceBlockEntity furnace
                ? furnace : null, new SimpleContainerData(4));
    }

    public SourceTransmutationFurnaceMenu(int id, Inventory playerInventory, SourceTransmutationFurnaceBlockEntity furnace, ContainerData data) {
        this(id, playerInventory, furnace == null ? new SimpleContainer(SourceTransmutationFurnaceBlockEntity.SLOTS) : furnace, data);
    }

    private SourceTransmutationFurnaceMenu(int id, Inventory playerInventory, Container inventory, ContainerData data) {
        super(SourceTransmutationFurnaceContent.MENU.get(), id);
        this.inventory = inventory;
        this.data = data;
        addSlot(new Slot(inventory, SourceTransmutationFurnaceBlockEntity.INPUT, 44, 35) {
            @Override public boolean mayPlace(ItemStack stack) { return inventory.canPlaceItem(SourceTransmutationFurnaceBlockEntity.INPUT, stack); }
            @Override public int getMaxStackSize() { return 64; }
        });
        addSlot(new Slot(inventory, SourceTransmutationFurnaceBlockEntity.OUTPUT, 116, 35) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, 9 + row * 9 + col, 8 + col * 18, 84 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        addDataSlots(data);
    }

    public int progress() { return data.get(0); }
    public int duration() { return data.get(1); }
    public int tier() { return data.get(2); }
    public int status() { return data.get(3); }
    @Override public boolean stillValid(Player player) { return inventory.stillValid(player); }

    @Override public boolean clickMenuButton(Player player, int id) {
        if (id != 0 || !(inventory instanceof SourceTransmutationFurnaceBlockEntity furnace)
                || !stillValid(player) || player.isSpectator()) return false;
        furnace.cycleTier();
        return true;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < SourceTransmutationFurnaceBlockEntity.SLOTS) {
            if (!moveItemStackTo(stack, SourceTransmutationFurnaceBlockEntity.SLOTS, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, SourceTransmutationFurnaceBlockEntity.INPUT, SourceTransmutationFurnaceBlockEntity.INPUT + 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }
}
