package com.example.akaishi.block;

import com.example.akaishi.AkaishiMod;
import dev.architectury.registry.registries.Registrar;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

/**
 * 方块注册公共工具：注册一个「方块 + 同名 BlockItem」的组合。
 * <p>
 * 各机器族域注册类（如 AkaishiWirelessBlocks / AkaishiReactorBlocks / AkaishiFusionBlocks）
 * 统一经由 {@link #registerMachineBlock} 注册，避免每个域类各自复制一套 BlockItem 注册样板。
 * 所有 register 调用都发生在 {@link AkaishiMod#init()} 的注册表冻结前。
 */
public final class AkaishiBlockRegistrar {

    private AkaishiBlockRegistrar() {
    }

    /** 注册方块及其同名 BlockItem，返回方块的延迟引用 */
    public static RegistrySupplier<Block> registerMachineBlock(Registrar<Block> registrar, String id,
                                                               Supplier<Block> factory) {
        return registerMachineBlock(registrar, id, factory, BlockItem::new);
    }

    /**
     * 注册方块及其同名 BlockItem，<b>自定义物品类型</b>（如需要悬浮文本的物品）。
     * <p>
     * 与上面同 id 同语义，只是把 BlockItem 的构造交给调用方；默认重载仍走原版 {@link BlockItem}，
     * 因此既有域类无需改动。
     */
    public static RegistrySupplier<Block> registerMachineBlock(Registrar<Block> registrar, String id,
                                                               Supplier<Block> factory,
                                                               java.util.function.BiFunction<Block, Item.Properties, Item> itemFactory) {
        RegistrySupplier<Block> block = registrar.register(new ResourceLocation(AkaishiMod.MOD_ID, id), factory);
        RegistrarManager.get(AkaishiMod.MOD_ID).get(Registries.ITEM)
                .register(new ResourceLocation(AkaishiMod.MOD_ID, id),
                        () -> itemFactory.apply(block.get(), new Item.Properties()));
        return block;
    }

    /**
     * 仅注册方块本体，不注册 BlockItem。
     * <p>用于随结构生成的「技术方块」（如巨坛封印）：玩家无法自行获取，进入创造栏与创造栏物品反而会留下孤儿物品。
     */
    public static RegistrySupplier<Block> registerBlockOnly(Registrar<Block> registrar, String id,
                                                            Supplier<Block> factory) {
        return registrar.register(new ResourceLocation(AkaishiMod.MOD_ID, id), factory);
    }
}
