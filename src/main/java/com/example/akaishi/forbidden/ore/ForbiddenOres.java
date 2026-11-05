package com.example.akaishi.forbidden.ore;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.block.AkaishiBlockRegistrar;
import dev.architectury.registry.registries.Registrar;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.List;

public final class ForbiddenOres {
    public static RegistrySupplier<Block> CINNABAR_ORE;
    public static RegistrySupplier<Block> DEEPSLATE_CINNABAR_ORE;
    public static RegistrySupplier<Block> SULFUR_ORE;
    public static RegistrySupplier<Block> DEEPSLATE_SULFUR_ORE;
    public static RegistrySupplier<Block> SILVER_ORE;
    public static RegistrySupplier<Block> DEEPSLATE_SILVER_ORE;
    public static RegistrySupplier<Block> SILVER_BLOCK;
    public static RegistrySupplier<Block> RAW_SILVER_BLOCK;
    public static RegistrySupplier<Block> DEEPSLATE_MAGIC_STONE_ORE;
    public static RegistrySupplier<Block> MAGIC_STONE_BLOCK;
    public static RegistrySupplier<Item> MAGIC_STONE;
    public static RegistrySupplier<Item> CINNABAR_FRAGMENT;
    public static RegistrySupplier<Item> SULFUR_FRAGMENT;
    public static RegistrySupplier<Item> RAW_SILVER;
    public static RegistrySupplier<Item> SILVER_INGOT;
    public static RegistrySupplier<Item> SILVER_NUGGET;

    private ForbiddenOres() { }

    /** 注册矿石、储存块与材料，沿用现有 Architectury 注册生命周期。 */
    public static void register() {
        Registrar<Block> blocks = RegistrarManager.get(AkaishiMod.MOD_ID).get(Registries.BLOCK);
        CINNABAR_ORE = block(blocks, "cinnabar_ore", Blocks.GOLD_ORE);
        DEEPSLATE_CINNABAR_ORE = block(blocks, "deepslate_cinnabar_ore", Blocks.DEEPSLATE_GOLD_ORE);
        SULFUR_ORE = block(blocks, "sulfur_ore", Blocks.GOLD_ORE);
        DEEPSLATE_SULFUR_ORE = block(blocks, "deepslate_sulfur_ore", Blocks.DEEPSLATE_GOLD_ORE);
        SILVER_ORE = block(blocks, "silver_ore", Blocks.GOLD_ORE);
        DEEPSLATE_SILVER_ORE = block(blocks, "deepslate_silver_ore", Blocks.DEEPSLATE_GOLD_ORE);
        SILVER_BLOCK = block(blocks, "silver_block", Blocks.GOLD_BLOCK);
        RAW_SILVER_BLOCK = block(blocks, "raw_silver_block", Blocks.RAW_GOLD_BLOCK);
        DEEPSLATE_MAGIC_STONE_ORE = AkaishiBlockRegistrar.registerMachineBlock(blocks, "forbidden_hex_deepslate_magic_stone_ore",
                () -> new Block(BlockBehaviour.Properties.copy(Blocks.DEEPSLATE_DIAMOND_ORE)
                        .requiresCorrectToolForDrops().lightLevel(state -> 7)));
        MAGIC_STONE_BLOCK = AkaishiBlockRegistrar.registerMachineBlock(blocks, "forbidden_hex_magic_stone_block",
                () -> new Block(BlockBehaviour.Properties.copy(Blocks.DIAMOND_BLOCK)
                        .requiresCorrectToolForDrops().lightLevel(state -> 7)));
        Registrar<Item> items = RegistrarManager.get(AkaishiMod.MOD_ID).get(Registries.ITEM);
        MAGIC_STONE = material(items, "magic_stone");
        CINNABAR_FRAGMENT = material(items, "cinnabar_fragment");
        SULFUR_FRAGMENT = material(items, "sulfur_fragment");
        RAW_SILVER = material(items, "raw_silver");
        SILVER_INGOT = material(items, "silver_ingot");
        SILVER_NUGGET = material(items, "silver_nugget");
    }

    private static RegistrySupplier<Block> block(Registrar<Block> blocks, String name, Block base) {
        return AkaishiBlockRegistrar.registerMachineBlock(blocks, "forbidden_hex_" + name,
                () -> new Block(BlockBehaviour.Properties.copy(base).requiresCorrectToolForDrops()));
    }

    private static RegistrySupplier<Item> material(Registrar<Item> items, String name) {
        return items.register(new ResourceLocation(AkaishiMod.MOD_ID, "forbidden_hex_" + name),
                () -> new Item(new Item.Properties()));
    }

    public static void addCreativeItems(CreativeModeTab.Output output) {
        for (RegistrySupplier<Block> block : List.of(CINNABAR_ORE, DEEPSLATE_CINNABAR_ORE,
                SULFUR_ORE, DEEPSLATE_SULFUR_ORE, SILVER_ORE, DEEPSLATE_SILVER_ORE,
                SILVER_BLOCK, RAW_SILVER_BLOCK, DEEPSLATE_MAGIC_STONE_ORE, MAGIC_STONE_BLOCK)) output.accept(block.get());
        for (RegistrySupplier<Item> item : List.of(CINNABAR_FRAGMENT, SULFUR_FRAGMENT,
                RAW_SILVER, SILVER_INGOT, SILVER_NUGGET, MAGIC_STONE)) output.accept(item.get());
    }
}
