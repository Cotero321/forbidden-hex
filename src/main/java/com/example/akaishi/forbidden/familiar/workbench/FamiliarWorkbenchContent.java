package com.example.akaishi.forbidden.familiar.workbench;

import com.example.akaishi.AkaishiMod;
import dev.architectury.registry.menu.MenuRegistry;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Registration entry point; the main mod initializer must call register() after common registries are ready. */
public final class FamiliarWorkbenchContent {
    public static RegistrySupplier<Block> BLOCK;
    public static RegistrySupplier<Item> ITEM;
    public static RegistrySupplier<BlockEntityType<FamiliarWorkbenchBlockEntity>> BLOCK_ENTITY;
    public static RegistrySupplier<MenuType<FamiliarWorkbenchMenu>> MENU;

    private FamiliarWorkbenchContent() { }

    @SuppressWarnings("unchecked")
    public static void register() {
        var blocks = RegistrarManager.get(AkaishiMod.MOD_ID).get(Registries.BLOCK);
        var items = RegistrarManager.get(AkaishiMod.MOD_ID).get(Registries.ITEM);
        BLOCK = blocks.register(new ResourceLocation(AkaishiMod.MOD_ID, "familiar_workbench"), FamiliarWorkbenchBlock::new);
        ITEM = items.register(new ResourceLocation(AkaishiMod.MOD_ID, "familiar_workbench"),
                () -> new BlockItem(BLOCK.get(), new Item.Properties()));
        BLOCK_ENTITY = RegistrarManager.get(AkaishiMod.MOD_ID).get(Registries.BLOCK_ENTITY_TYPE).register(
                new ResourceLocation(AkaishiMod.MOD_ID, "familiar_workbench"),
                () -> BlockEntityType.Builder.of(FamiliarWorkbenchBlockEntity::new, BLOCK.get()).build(null));
        MenuType<FamiliarWorkbenchMenu> menuType = MenuRegistry.ofExtended((id, inv, buf) ->
                new FamiliarWorkbenchMenu(id, inv, buf.readBlockPos()));
        MENU = (RegistrySupplier<MenuType<FamiliarWorkbenchMenu>>) (Object) RegistrarManager.get(AkaishiMod.MOD_ID)
                .get(Registries.MENU).register(new ResourceLocation(AkaishiMod.MOD_ID, "familiar_workbench"), () -> menuType);
    }
}
