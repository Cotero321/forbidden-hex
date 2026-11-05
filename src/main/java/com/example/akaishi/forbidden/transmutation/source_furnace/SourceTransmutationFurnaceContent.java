package com.example.akaishi.forbidden.transmutation.source_furnace;

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

/** Registration entry point for the source-energy transmutation furnace. */
public final class SourceTransmutationFurnaceContent {
    public static RegistrySupplier<Block> BLOCK;
    public static RegistrySupplier<Item> ITEM;
    public static RegistrySupplier<BlockEntityType<SourceTransmutationFurnaceBlockEntity>> BLOCK_ENTITY;
    public static RegistrySupplier<MenuType<SourceTransmutationFurnaceMenu>> MENU;

    private SourceTransmutationFurnaceContent() { }

    @SuppressWarnings("unchecked")
    public static void register() {
        var manager = RegistrarManager.get(AkaishiMod.MOD_ID);
        BLOCK = manager.get(Registries.BLOCK).register(id("source_transmutation_furnace"), SourceTransmutationFurnaceBlock::new);
        ITEM = manager.get(Registries.ITEM).register(id("source_transmutation_furnace"),
                () -> new BlockItem(BLOCK.get(), new Item.Properties()));
        BLOCK_ENTITY = manager.get(Registries.BLOCK_ENTITY_TYPE).register(id("source_transmutation_furnace"),
                () -> BlockEntityType.Builder.of(SourceTransmutationFurnaceBlockEntity::new, BLOCK.get()).build(null));
        MenuType<SourceTransmutationFurnaceMenu> menuType = MenuRegistry.ofExtended((syncId, inventory, buffer) ->
                new SourceTransmutationFurnaceMenu(syncId, inventory, buffer.readBlockPos()));
        MENU = (RegistrySupplier<MenuType<SourceTransmutationFurnaceMenu>>) (Object) manager.get(Registries.MENU)
                .register(id("source_transmutation_furnace"), () -> menuType);
    }

    private static ResourceLocation id(String path) { return new ResourceLocation(AkaishiMod.MOD_ID, path); }
}
