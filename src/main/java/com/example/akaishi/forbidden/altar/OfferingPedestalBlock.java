package com.example.akaishi.forbidden.altar;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class OfferingPedestalBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = Shapes.or(Block.box(2, 0, 2, 14, 3, 14),
            Block.box(5, 3, 5, 11, 13, 11), Block.box(1, 13, 1, 15, 16, 15));
    public OfferingPedestalBlock() {
        super(Properties.of().mapColor(MapColor.COLOR_BLACK).strength(3.5F).sound(SoundType.DEEPSLATE).noOcclusion().requiresCorrectToolForDrops());
    }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new OfferingPedestalBlockEntity(pos, state); }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof OfferingPedestalBlockEntity pedestal)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!level.mayInteract(player, pos) || !player.mayBuild() || player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) > 64)
            return InteractionResult.FAIL;
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty()) {
            ItemStack removed = pedestal.takeDisplay();
            if (!removed.isEmpty() && !player.getInventory().add(removed)) player.drop(removed, false);
        } else if (pedestal.putDisplay(held) && !player.getAbilities().instabuild) held.shrink(1);
        return InteractionResult.CONSUME;
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof OfferingPedestalBlockEntity pedestal) {
            ItemStack removed = pedestal.takeDisplay();
            if (!removed.isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), removed);
        }
        super.onRemove(state, level, pos, replacement, moving);
    }
}
