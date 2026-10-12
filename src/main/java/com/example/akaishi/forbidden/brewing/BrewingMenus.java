package com.example.akaishi.forbidden.brewing;

import com.example.akaishi.AkaishiMod;
import dev.architectury.registry.menu.MenuRegistry;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;

public final class BrewingMenus {
    public static RegistrySupplier<MenuType<WitchOvenMenu>> OVEN;

    private BrewingMenus() { }

    public static void register() {
        MenuType<WitchOvenMenu> type = MenuRegistry.ofExtended((id, inventory, buffer) -> {
            var blockEntity = inventory.player.level().getBlockEntity(buffer.readBlockPos());
            return blockEntity instanceof WitchOvenBlockEntity oven
                    ? new WitchOvenMenu(id, inventory, oven, oven.data()) : WitchOvenMenu.empty(id, inventory);
        });
        OVEN = RegistrarManager.get(AkaishiMod.MOD_ID).get(Registries.MENU)
                .register(BrewingItems.id("witch_oven"), () -> type);
    }
}
