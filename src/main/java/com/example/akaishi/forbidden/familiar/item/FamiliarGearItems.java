package com.example.akaishi.forbidden.familiar.item;

import com.example.akaishi.AkaishiMod;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/** Dedicated registry holder. The common mod initializer calls {@link #register()} during integration. */
public final class FamiliarGearItems {
    public static RegistrySupplier<Item> EQUIPMENT;
    public static RegistrySupplier<Item> ACCESSORY;

    private FamiliarGearItems() {
    }

    public static void register() {
        var items = RegistrarManager.get(AkaishiMod.MOD_ID).get(Registries.ITEM);
        EQUIPMENT = items.register(new ResourceLocation(AkaishiMod.MOD_ID, "familiar_equipment"),
                () -> new FamiliarBoundGearItem(new Item.Properties(), FamiliarBoundGearItem.SlotType.EQUIPMENT));
        ACCESSORY = items.register(new ResourceLocation(AkaishiMod.MOD_ID, "familiar_accessory"),
                () -> new FamiliarBoundGearItem(new Item.Properties(), FamiliarBoundGearItem.SlotType.ACCESSORY));
    }
}
