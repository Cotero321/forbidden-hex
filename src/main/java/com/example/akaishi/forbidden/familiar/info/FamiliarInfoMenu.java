package com.example.akaishi.forbidden.familiar.info;

import com.example.akaishi.forbidden.familiar.core.FamiliarMode;
import com.example.akaishi.forbidden.familiar.entity.FamiliarEntity;
import com.example.akaishi.forbidden.familiar.FamiliarContent;
import com.example.akaishi.forbidden.familiar.item.FamiliarBoundGearItem;
import com.example.akaishi.AkaishiMod;
import dev.architectury.registry.menu.ExtendedMenuProvider;
import dev.architectury.registry.menu.MenuRegistry;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.UUID;

/** Server-authoritative familiar information and mode control menu. */
public final class FamiliarInfoMenu extends AbstractContainerMenu {
    public static RegistrySupplier<MenuType<FamiliarInfoMenu>> MENU;
    private final int familiarId;
    private final FamiliarEntity familiar;
    private final Container gearInventory;

    private FamiliarInfoMenu(int id, Inventory inventory, int familiarId) {
        super(MENU.get(), id);
        this.familiarId = familiarId;
        this.familiar = resolve(inventory.player.level(), familiarId);
        this.gearInventory = new GearInventory(inventory);
        ((GearInventory) gearInventory).initialize();
        addSlot(gearSlot(inventory.player, FamiliarBoundGearItem.SlotType.EQUIPMENT, 0, 64, 94));
        addSlot(gearSlot(inventory.player, FamiliarBoundGearItem.SlotType.ACCESSORY, 1, 118, 94));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, 9 + row * 9 + col, 8 + col * 18, 128 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 186));
    }

    public static void register() {
        MenuType<FamiliarInfoMenu> type = MenuRegistry.ofExtended((id, inv, buf) ->
                new FamiliarInfoMenu(id, inv, buf.readVarInt()));
        MENU = (RegistrySupplier<MenuType<FamiliarInfoMenu>>) (Object) RegistrarManager.get(AkaishiMod.MOD_ID)
                .get(Registries.MENU).register(new ResourceLocation(AkaishiMod.MOD_ID, "familiar_info"), () -> type);
    }

    public static void open(ServerPlayer player, FamiliarEntity familiar) {
        if (player == null || familiar == null || !familiar.isOwnedBy(player)) return;
        MenuRegistry.openExtendedMenu(player, new ExtendedMenuProvider() {
            @Override public Component getDisplayName() { return Component.translatable("gui.forbidden_hex.familiar_info.title"); }
            @Override public void saveExtraData(FriendlyByteBuf buffer) { buffer.writeVarInt(familiar.getId()); }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player ignored) {
                return new FamiliarInfoMenu(id, inventory, familiar.getId());
            }
        });
    }

    private static FamiliarEntity resolve(Level level, int id) {
        if (level == null || id < 0) return null;
        return level.getEntity(id) instanceof FamiliarEntity entity ? entity : null;
    }

    private Slot gearSlot(Player player, FamiliarBoundGearItem.SlotType expected, int slot,
                          int x, int y) {
        return new Slot(gearInventory, slot, x, y) {
            @Override public boolean mayPlace(ItemStack stack) {
                if (!stillValid(player) || familiar == null) return false;
                FamiliarBoundGearItem gear = FamiliarBoundGearItem.of(stack);
                if (gear == null || gear.slotType() != expected) return false;
                return player.level().isClientSide || gear.canBindTo(stack, familiar, player);
            }

            @Override public boolean mayPickup(Player candidate) {
                return stillValid(candidate) && super.mayPickup(candidate);
            }

            @Override public void set(ItemStack stack) {
                if (!player.level().isClientSide) {
                    if (!stack.isEmpty()) {
                        FamiliarBoundGearItem gear = FamiliarBoundGearItem.of(stack);
                        if (gear == null || gear.slotType() != expected || !gear.canBindTo(stack, familiar, player)) return;
                        gear.bindTo(stack, familiar, player);
                    }
                }
                super.set(stack);
            }
        };
    }

    private void syncGear(ServerPlayer player) {
        if (familiar == null || !familiar.isOwnedBy(player)) return;
        ItemStack equipment = gearInventory.getItem(0);
        ItemStack accessory = gearInventory.getItem(1);
        familiar.setGear(FamiliarBoundGearItem.SlotType.EQUIPMENT, equipment);
        familiar.setGear(FamiliarBoundGearItem.SlotType.ACCESSORY, accessory);
    }

    private final class GearInventory extends SimpleContainer {
        private final Inventory playerInventory;
        private boolean initializing;

        private GearInventory(Inventory playerInventory) {
            super(2);
            this.playerInventory = playerInventory;
        }

        private void initialize() {
            initializing = true;
            if (familiar != null) {
                super.setItem(0, familiar.equipment());
                super.setItem(1, familiar.accessory());
            }
            initializing = false;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            if (initializing || familiar == null || playerInventory.player.level().isClientSide
                    || !(playerInventory.player instanceof ServerPlayer serverPlayer)
                    || !familiar.isOwnedBy(serverPlayer)) return;
            syncGear(serverPlayer);
        }
    }

    public FamiliarEntity familiar() { return familiar; }
    public int familiarId() { return familiarId; }

    @Override public boolean stillValid(Player player) {
        return familiar != null && familiar.isAlive() && familiar.isOwnedBy(player)
                && player.distanceToSqr(familiar) <= 64.0D;
    }

    @Override public boolean clickMenuButton(Player player, int buttonId) {
        if (!stillValid(player) || familiar == null || buttonId < 0 || buttonId >= FamiliarMode.values().length) return false;
        familiar.setMode(FamiliarMode.values()[buttonId]);
        player.displayClientMessage(Component.translatable("familiar.forbidden_hex.mode_changed", familiar.mode().id()), true);
        return true;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem();
        ItemStack original = source.copy();
        if (index < 2) {
            if (!moveItemStackTo(source, 2, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            FamiliarBoundGearItem gear = FamiliarBoundGearItem.of(source);
            if (gear == null) return ItemStack.EMPTY;
            int destination = gear.slotType() == FamiliarBoundGearItem.SlotType.EQUIPMENT ? 0 : 1;
            Slot target = slots.get(destination);
            if (target.hasItem() || !target.mayPlace(source)) return ItemStack.EMPTY;
            ItemStack one = source.copyWithCount(1);
            target.set(one);
            source.shrink(1);
        }
        if (source.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }
}
