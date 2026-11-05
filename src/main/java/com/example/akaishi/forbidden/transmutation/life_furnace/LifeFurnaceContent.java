package com.example.akaishi.forbidden.transmutation.life_furnace;

import com.example.akaishi.AkaishiMod;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Registration entry point for the life furnace controller and biomass output. */
public final class LifeFurnaceContent {
    public static RegistrySupplier<Block> BLOCK;
    public static RegistrySupplier<Item> BLOCK_ITEM;
    public static RegistrySupplier<Item> BIOMASS;
    public static RegistrySupplier<BlockEntityType<LifeFurnaceBlockEntity>> BLOCK_ENTITY;

    private LifeFurnaceContent() {
    }

    public static void register() {
        var manager = RegistrarManager.get(AkaishiMod.MOD_ID);
        BLOCK = manager.get(Registries.BLOCK).register(id("life_furnace"), LifeFurnaceBlock::new);
        BLOCK_ITEM = manager.get(Registries.ITEM).register(id("life_furnace"),
                () -> new BlockItem(BLOCK.get(), new Item.Properties()));
        BIOMASS = manager.get(Registries.ITEM).register(id("biomass"),
                () -> new BiomassItem(new Item.Properties().stacksTo(64)));
        BLOCK_ENTITY = manager.get(Registries.BLOCK_ENTITY_TYPE).register(id("life_furnace"),
                () -> BlockEntityType.Builder.of(LifeFurnaceBlockEntity::new, BLOCK.get()).build(null));
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(AkaishiMod.MOD_ID, path);
    }
}
