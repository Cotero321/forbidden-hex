package com.example.akaishi.forbidden.brewing;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public enum BrewingHeat {
    NONE, FIRE, SOUL_FIRE;

    public static BrewingHeat below(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos.below());
        if (state.is(Blocks.SOUL_FIRE)) return SOUL_FIRE;
        if (state.is(Blocks.FIRE)) return FIRE;
        return NONE;
    }
}
