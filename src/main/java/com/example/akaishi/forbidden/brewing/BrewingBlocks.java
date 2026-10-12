package com.example.akaishi.forbidden.brewing;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.block.AkaishiBlockRegistrar;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class BrewingBlocks {
    public static RegistrySupplier<Block> OVEN;
    public static RegistrySupplier<Block> CRUCIBLE;
    public static RegistrySupplier<BlockEntityType<WitchOvenBlockEntity>> OVEN_ENTITY;
    public static RegistrySupplier<BlockEntityType<CrucibleBlockEntity>> CRUCIBLE_ENTITY;

    private BrewingBlocks() { }

    public static void register(BlockEntityType.BlockEntitySupplier<CrucibleBlockEntity> crucibleFactory) {
        var blocks = RegistrarManager.get(AkaishiMod.MOD_ID).get(Registries.BLOCK);
        OVEN = AkaishiBlockRegistrar.registerMachineBlock(blocks, "forbidden_hex_witch_oven", WitchOvenBlock::new);
        CRUCIBLE = AkaishiBlockRegistrar.registerMachineBlock(blocks, "forbidden_hex_crucible", CrucibleBlock::new);
        var entities = RegistrarManager.get(AkaishiMod.MOD_ID).get(Registries.BLOCK_ENTITY_TYPE);
        OVEN_ENTITY = entities.register(BrewingItems.id("witch_oven"), () -> BlockEntityType.Builder
                .of(WitchOvenBlockEntity::new, OVEN.get()).build(null));
        CRUCIBLE_ENTITY = entities.register(BrewingItems.id("crucible"), () -> BlockEntityType.Builder
                .of(crucibleFactory, CRUCIBLE.get()).build(null));
    }
}
