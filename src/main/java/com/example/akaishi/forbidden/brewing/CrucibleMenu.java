package com.example.akaishi.forbidden.brewing;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class CrucibleMenu extends AbstractContainerMenu {
    public static final int PREVIEW_START = CrucibleInventory.SIZE, PLAYER_START = PREVIEW_START + 4;
    private final CrucibleBlockEntity owner;
    private final Container inventory;
    private final SimpleContainer previews = new SimpleContainer(4);
    private final ContainerData data;

    public CrucibleMenu(int id, Inventory playerInventory, CrucibleBlockEntity owner) {
        super(BrewingMenus.CRUCIBLE.get(), id);
        this.owner = owner;
        inventory = owner == null ? new SimpleContainer(CrucibleInventory.SIZE) : owner.inventory();
        data = owner == null || playerInventory.player.level().isClientSide ? new SimpleContainerData(6) : new ContainerData() {
            @Override public int get(int index) {
                return switch (index) {
                    case 0 -> owner.inventory().mode();
                    case 1 -> owner.water();
                    case 2 -> owner.inventory().active() ? owner.inventory().progress() : owner.heatingTicks();
                    case 3 -> owner.inventory().status();
                    case 4 -> owner.slotsEnabled() && !owner.corrupt() ? 1 : 0;
                    case 5 -> owner.canRecoverLegacy() ? 1 : 0;
                    default -> 0;
                };
            }
            @Override public void set(int index, int value) { }
            @Override public int getCount() { return 6; }
        };
        for (int row = 0; row < 5; row++) for (int column = 0; column < 5; column++) {
            addMaterialSlot(row * 5 + column, 12 + column * 18, 37 + row * 18, 0);
        }
        for (int row = 0; row < 3; row++) for (int column = 0; column < 3; column++) {
            addMaterialSlot(CrucibleInventory.CRAFT_START + row * 3 + column, 30 + column * 18, 62 + row * 18, 1);
        }
        addSlot(new Slot(inventory, CrucibleInventory.BOTTLE, 122, 80) {
            @Override public boolean mayPlace(ItemStack stack) {
                return editable() && stack.is(Items.GLASS_BOTTLE) && (owner == null || owner.inventory().canPlaceItem(CrucibleInventory.BOTTLE, stack));
            }
            @Override public boolean mayPickup(Player player) { return editable(); }
        });
        for (int row = 0; row < 2; row++) for (int column = 0; column < 2; column++) {
            addSlot(new Slot(previews, row * 2 + column, 162 + column * 18, 62 + row * 18) {
                @Override public boolean mayPlace(ItemStack stack) { return false; }
                @Override public boolean mayPickup(Player player) { return false; }
            });
        }
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, 9 + row * 9 + column, 35 + column * 18, 160 + row * 18));
        }
        for (int column = 0; column < 9; column++) addSlot(new Slot(playerInventory, column, 35 + column * 18, 218));
        addDataSlots(data);
        updatePreview();
    }

    private void addMaterialSlot(int index, int x, int y, int mode) {
        addSlot(new Slot(inventory, index, x, y) {
            @Override public int getMaxStackSize() { return 1; }
            @Override public boolean isActive() { return mode() == mode; }
            @Override public boolean mayPickup(Player player) { return isActive() && editable(); }
            @Override public boolean mayPlace(ItemStack stack) {
                return isActive() && editable() && (owner == null || owner.inventory().canPlaceItem(index, stack));
            }
        });
    }
    public static CrucibleMenu empty(int id, Inventory inventory) { return new CrucibleMenu(id, inventory, null); }
    public int mode() { return data.get(0); }
    public int water() { return data.get(1); }
    public int progress() { return data.get(2); }
    public int status() { return data.get(3); }
    public boolean canRecover() { return data.get(5) == 1; }
    public boolean editable() { return data.get(4) == 1 && status() != CrucibleInventory.BUSY && status() != CrucibleInventory.OUTPUTS; }
    public boolean canSwitch() {
        if (!editable()) return false;
        for (int i = 0; i < CrucibleInventory.BOTTLE; i++) if (!inventory.getItem(i).isEmpty()) return false;
        return true;
    }
    @Override public boolean stillValid(Player player) { return owner != null && owner.inventory().stillValid(player); }

    @Override public boolean clickMenuButton(Player player, int id) {
        if (owner == null || player.level().isClientSide || !stillValid(player) || player.containerMenu != this) return false;
        boolean accepted = switch (id) {
            case 0, 1 -> owner.inventory().setMode(id);
            case 2 -> owner.slotsEnabled() ? owner.inventory().start(player) : owner.commitRecipe(player);
            case 3 -> owner.recoverLegacy(player);
            default -> false;
        };
        if (accepted) broadcastChanges();
        return accepted;
    }

    private void updatePreview() {
        if (owner == null || owner.getLevel() == null || owner.getLevel().isClientSide) return;
        var outputs = owner.inventory().plan(false).outputs();
        for (int i = 0; i < 4; i++) {
            ItemStack desired = i < outputs.size() ? outputs.get(i) : ItemStack.EMPTY;
            if (!ItemStack.matches(previews.getItem(i), desired)) previews.setItem(i, desired.copy());
        }
    }
    @Override public void broadcastChanges() { updatePreview(); super.broadcastChanges(); }
    @Override public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != previews && slot.isActive() && (slot.container != inventory || editable())
                && super.canTakeItemForPickAll(stack, slot);
    }
    @Override public boolean canDragTo(Slot slot) {
        return slot.container != previews && slot.isActive() && (slot.container != inventory || editable()) && super.canDragTo(slot);
    }
    @Override public void clicked(int slotId, int button, ClickType type, Player player) {
        if (slotId >= PREVIEW_START && slotId < PLAYER_START) return;
        if (slotId >= 0 && slotId < CrucibleInventory.SIZE && (!slots.get(slotId).isActive() || !editable())) return;
        super.clicked(slotId, button, type, player);
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || index >= PREVIEW_START && index < PLAYER_START) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.isActive() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < CrucibleInventory.SIZE) {
            if (!moveItemStackTo(stack, PLAYER_START, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (!editable()) return ItemStack.EMPTY;
            if (stack.is(Items.GLASS_BOTTLE)) {
                if (!moveItemStackTo(stack, CrucibleInventory.BOTTLE, CrucibleInventory.BOTTLE + 1, false)) return ItemStack.EMPTY;
            } else {
                int start = mode() == 0 ? 0 : CrucibleInventory.CRAFT_START;
                int end = mode() == 0 ? CrucibleInventory.BREW_SLOTS : CrucibleInventory.BOTTLE;
                if (!moveItemStackTo(stack, start, end, false)) return ItemStack.EMPTY;
            }
        }
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }
}
