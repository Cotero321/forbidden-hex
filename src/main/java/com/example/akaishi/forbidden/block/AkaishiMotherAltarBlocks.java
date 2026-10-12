package com.example.akaishi.forbidden.block;
import com.example.akaishi.block.AkaishiBlockRegistrar;

import com.example.akaishi.AkaishiMod;
import dev.architectury.registry.registries.Registrar;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * 母神祭坛方块族注册表。
 * <p>
 * 从 ModBlocks 拆分出的域注册类：母神祭坛核心 + 母神祭坛石（结构件）。
 * 字段由 {@link #register()} 在 {@link AkaishiMod#init()} 阶段填充，消费方须在
 * register() 之后访问，否则会触发 NPE。
 */
public final class AkaishiMotherAltarBlocks {

    /** 母神祭坛：生命线终局多方块祭坛核心（黑山羊之母，NBT 献祭识别 + 悬浮供奉） */
    public static RegistrySupplier<Block> CHISHI_MOTHER_ALTAR = null;
    /** 母神祭坛石：祭坛结构件（5×5 底座 + 四角柱，铺设成型后母神驻留） */
    public static RegistrySupplier<Block> CHISHI_ALTAR_STONE = null;
    public static RegistrySupplier<Block> CHISHI_ESSENCE_BLOCK;
    /** 红纹哭泣黑曜石：祭坛成型时替换结构内哭泣黑曜石，紫色纹路转为红色流动纹路 */
    public static RegistrySupplier<Block> CRYING_OBSIDIAN_RED = null;
    /** 巨坛封印：成型时补入中心 2×2 正上方（y+1）的隐形选靶壳，转发主座界面（技术方块，无对应物品） */
    public static RegistrySupplier<Block> CHISHI_ALTAR_SEAL = null;

    private AkaishiMotherAltarBlocks() {
    }

    /** 注册全部母神祭坛方块（由 AkaishiMod.init 调用） */
    public static void register() {
        Registrar<Block> registrar = RegistrarManager.get(AkaishiMod.MOD_ID).get(Registries.BLOCK);
        CHISHI_MOTHER_ALTAR = AkaishiBlockRegistrar.registerMachineBlock(registrar, "forbidden_hex_mother_altar", AkaishiMotherAltarBlock::new);
        CHISHI_ALTAR_STONE = AkaishiBlockRegistrar.registerMachineBlock(registrar, "forbidden_hex_altar_stone",
                () -> new Block(BlockBehaviour.Properties.copy(Blocks.OBSIDIAN)));
        CHISHI_ESSENCE_BLOCK = AkaishiBlockRegistrar.registerMachineBlock(registrar, "forbidden_hex_essence_block",
                () -> new Block(BlockBehaviour.Properties.of().strength(3.0F, 6.0F).requiresCorrectToolForDrops()));
        // 沿用哭泣黑曜石属性（硬度/抗爆/发光），仅替换贴图与纹路动画
        CRYING_OBSIDIAN_RED = AkaishiBlockRegistrar.registerMachineBlock(registrar, "forbidden_hex_crying_obsidian_red",
                () -> new Block(BlockBehaviour.Properties.copy(Blocks.CRYING_OBSIDIAN)));
        // 封印无对应物品：随结构成型补入、还原移除，玩家无法自行获取
        CHISHI_ALTAR_SEAL = AkaishiBlockRegistrar.registerBlockOnly(registrar, "forbidden_hex_altar_seal",
                AkaishiAltarSealBlock::new);
    }
}
