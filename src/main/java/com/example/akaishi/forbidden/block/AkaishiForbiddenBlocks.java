package com.example.akaishi.forbidden.block;
import com.example.akaishi.block.AkaishiBlockRegistrar;

import com.example.akaishi.AkaishiMod;
import dev.architectury.registry.registries.Registrar;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;

/**
 * 禁忌模块·方块注册（P3d 随生命融合 / 母神祭坛从本体迁入，注册 id 与迁前逐字一致）。
 *
 * <p>本类只承载「不属于某一独立域类」的禁忌方块；母神祭坛体系仍由
 * {@link AkaishiMotherAltarBlocks} 自行注册（同本体拆分范式）。
 * 未安装本模块时这些方块根本不注册（注册命名空间仍为三模块共用的 {@code forbidden_hex:}）。
 */
public final class AkaishiForbiddenBlocks {

    public static RegistrySupplier<Block> INSCRIPTION_TABLE;

    private AkaishiForbiddenBlocks() {
    }

    /** 由 {@code AkaishiForbiddenMod.init()} 调用（须先于方块实体类型注册） */
    public static void register() {
        Registrar<Block> registrar = RegistrarManager.get(AkaishiMod.MOD_ID).get(Registries.BLOCK);
        INSCRIPTION_TABLE = AkaishiBlockRegistrar.registerMachineBlock(registrar,
                "forbidden_hex_inscription_table", AkaishiInscriptionTableBlock::new);
    }
}
