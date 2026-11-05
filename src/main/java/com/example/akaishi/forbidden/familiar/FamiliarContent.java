package com.example.akaishi.forbidden.familiar;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.familiar.item.FamiliarContractItem;
import com.example.akaishi.forbidden.familiar.item.FamiliarMaterialItems;
import com.example.akaishi.forbidden.familiar.item.FamiliarTemplateItems;
import com.example.akaishi.forbidden.familiar.workbench.FamiliarWorkbenchContent;
import com.example.akaishi.forbidden.familiar.info.FamiliarInfoMenu;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/** Registration entry point for the familiar contract and its future machine content. */
public final class FamiliarContent {
    public static RegistrySupplier<Item> FAMILIAR_CONTRACT;

    private FamiliarContent() {
    }

    public static void register() {
        FAMILIAR_CONTRACT = RegistrarManager.get(AkaishiMod.MOD_ID).get(Registries.ITEM)
                .register(new ResourceLocation(AkaishiMod.MOD_ID, "familiar_contract"),
                        () -> new FamiliarContractItem(new Item.Properties().stacksTo(1)));
        FamiliarTemplateItems.register();
        FamiliarMaterialItems.register();
        // Keep the workbench registration in the familiar content entry point so platform
        // initializers only need to invoke one familiar registration hook.
        FamiliarWorkbenchContent.register();
        FamiliarInfoMenu.register();
    }
}
