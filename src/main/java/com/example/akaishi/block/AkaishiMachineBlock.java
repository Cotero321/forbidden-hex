package com.example.akaishi.block;

import net.minecraft.world.level.block.BaseEntityBlock;

public abstract class AkaishiMachineBlock extends BaseEntityBlock {
    protected AkaishiMachineBlock(Properties properties) {
        super(properties.strength(3.5F, 6.0F).requiresCorrectToolForDrops());
    }
}
