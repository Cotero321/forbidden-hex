package com.example.akaishi.forbidden.familiar.workbench;

import com.example.akaishi.forbidden.familiar.machine.FamiliarWorkbenchSlotLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.MenuType;

/** Server-authoritative menu layout for the familiar workbench. */
public final class FamiliarWorkbenchMenu extends AbstractContainerMenu {
    private final Container inventory;
    private final ContainerData data;
    private final Player menuPlayer;

    public FamiliarWorkbenchMenu(int id, Inventory playerInventory, BlockPos pos) {
        this(id, playerInventory, playerInventory.player.level().getBlockEntity(pos) instanceof FamiliarWorkbenchBlockEntity workbench
                ? workbench : null,
                new net.minecraft.world.inventory.SimpleContainerData(3));
    }

    public FamiliarWorkbenchMenu(int id, Inventory playerInventory, FamiliarWorkbenchBlockEntity workbench, ContainerData data) {
        super(FamiliarWorkbenchContent.MENU.get(), id);
        this.inventory = workbench == null ? new net.minecraft.world.SimpleContainer(FamiliarWorkbenchSlotLayout.MACHINE_SLOT_COUNT) : workbench;
        this.data = data;
        this.menuPlayer = playerInventory.player;
        if (workbench != null) workbench.setOperator(playerInventory.player);
        addSlot(new InputSlot(inventory, FamiliarWorkbenchSlotLayout.SOUL_SLOT, 26, 25));
        addSlot(new InputSlot(inventory, FamiliarWorkbenchSlotLayout.TEMPLATE_SLOT, 62, 25));
        for (int i = 0; i < FamiliarWorkbenchSlotLayout.MATERIAL_SLOT_COUNT; i++) {
            addSlot(new InputSlot(inventory, FamiliarWorkbenchSlotLayout.FIRST_MATERIAL_SLOT + i, 26 + i * 18, 61));
        }
        addSlot(new Slot(inventory, FamiliarWorkbenchSlotLayout.RESULT_SLOT, 134, 43) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return canOperate(player); }
            @Override public int getMaxStackSize() { return 1; }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(playerInventory, 9 + row * 9 + col, 8 + col * 18, 96 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(playerInventory, col, 8 + col * 18, 154));
        addDataSlots(data);
    }

    @Override public boolean stillValid(Player player) { return inventory.stillValid(player); }
    public boolean isFor(FamiliarWorkbenchBlockEntity workbench) { return inventory == workbench; }
    private boolean canOperate(Player player) {
        return !(inventory instanceof FamiliarWorkbenchBlockEntity workbench) || workbench.canOperate(player);
    }
    public int progress() { return data.get(0); }
    public int duration() { return data.get(1); }
    public int status() { return data.get(2); }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        if (!canOperate(player)) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < FamiliarWorkbenchSlotLayout.MACHINE_SLOT_COUNT) {
            if (!moveItemStackTo(stack, FamiliarWorkbenchSlotLayout.MACHINE_SLOT_COUNT, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, FamiliarWorkbenchSlotLayout.RESULT_SLOT, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }

    private final class InputSlot extends Slot {
        private InputSlot(Container container, int index, int x, int y) { super(container, index, x, y); }
        @Override public boolean mayPlace(ItemStack stack) {
            return canOperate(menuPlayer) && !stack.isEmpty() && !stack.is(Items.AIR) && container.canPlaceItem(index, stack);
        }
        @Override public boolean mayPickup(Player player) {
            return canOperate(player) && (!(container instanceof FamiliarWorkbenchBlockEntity workbench)
                    || workbench.status() != FamiliarWorkbenchBlockEntity.STATUS_PROCESSING);
        }
        @Override public int getMaxStackSize() { return 1; }
    }
}
