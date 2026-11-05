package com.example.akaishi.forbidden.altar;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class SourceCandelabrumBlock extends Block {
    private final VoxelShape shape;
    public SourceCandelabrumBlock(String kind) {
        super(Properties.of().mapColor(MapColor.COLOR_BROWN).strength(2.0F).sound(SoundType.METAL)
                .requiresCorrectToolForDrops().noOcclusion().lightLevel(state -> kind.equals("sacred_candelabrum") ? 15 : 12));
        boolean large = !kind.equals("candelabrum");
        shape = Shapes.or(Block.box(3, 0, 3, 13, 2, 13), Block.box(7, 2, 7, 9, large ? 14 : 10, 9),
                Block.box(large ? 1 : 3, large ? 8 : 6, 6, large ? 15 : 13, large ? 16 : 12, 10));
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return shape; }
}
