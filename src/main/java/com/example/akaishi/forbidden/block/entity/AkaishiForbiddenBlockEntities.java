package com.example.akaishi.forbidden.block.entity;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.block.AkaishiForbiddenBlocks;
import com.example.akaishi.forbidden.block.entity.AkaishiInscriptionTableBlockEntity;
import com.example.akaishi.forbidden.block.AkaishiMotherAltarBlocks;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Arrays;

/**
 * 禁忌模块·方块实体类型注册（P3d 随生命融合 / 母神祭坛从本体迁入，注册 id 与迁前逐字一致）。
 *
 * <p>侵入式强转收敛在 {@link #be} 一处（与本体 {@code ModBlockEntities} 同范式）；
 * 未安装本模块时这些方块实体根本不注册。
 */
public final class AkaishiForbiddenBlockEntities {

    public static RegistrySupplier<BlockEntityType<AkaishiInscriptionTableBlockEntity>> INSCRIPTION_TABLE;

    /** 母神祭坛方块实体类型（黑山羊之母：NBT 识别供奉 + 供奉物悬浮展示） */
    public static RegistrySupplier<BlockEntityType<AkaishiMotherAltarBlockEntity>> CHISHI_MOTHER_ALTAR;

    private AkaishiForbiddenBlockEntities() {
    }

    /** 由 {@code AkaishiForbiddenMod.init()} 调用（须先于对应方块注册） */
    public static void register() {
        INSCRIPTION_TABLE = be("forbidden_hex_inscription_table", AkaishiInscriptionTableBlockEntity::new,
                AkaishiForbiddenBlocks.INSCRIPTION_TABLE);
        CHISHI_MOTHER_ALTAR = be("forbidden_hex_mother_altar", AkaishiMotherAltarBlockEntity::new,
                AkaishiMotherAltarBlocks.CHISHI_MOTHER_ALTAR);
    }

    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity> RegistrySupplier<BlockEntityType<T>> be(
            String id, BlockEntityType.BlockEntitySupplier<T> factory, RegistrySupplier<Block>... blocks) {
        return (RegistrySupplier<BlockEntityType<T>>) (Object) RegistrarManager
                .get(AkaishiMod.MOD_ID).get(Registries.BLOCK_ENTITY_TYPE)
                .register(new ResourceLocation(AkaishiMod.MOD_ID, id),
                        () -> BlockEntityType.Builder.of(factory,
                                Arrays.stream(blocks).map(RegistrySupplier::get).toArray(Block[]::new)).build(null));
    }
}
