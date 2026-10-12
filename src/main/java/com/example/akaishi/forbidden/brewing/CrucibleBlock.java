package com.example.akaishi.forbidden.brewing;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class CrucibleBlock extends BaseEntityBlock {
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 3);
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 4, 16),
            Block.box(0, 4, 0, 2, 16, 16), Block.box(14, 4, 0, 16, 16, 16),
            Block.box(2, 4, 0, 14, 16, 2), Block.box(2, 4, 14, 14, 16, 16));

    public CrucibleBlock() {
        super(Properties.of().mapColor(MapColor.METAL).strength(3.5F).sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops());
        registerDefaultState(stateDefinition.any().setValue(LEVEL, 0));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(LEVEL); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return BrewingBlocks.CRUCIBLE_ENTITY.get().create(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, BrewingBlocks.CRUCIBLE_ENTITY.get(), CrucibleBlockEntity::tick);
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CrucibleBlockEntity pot)) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (held.is(Items.WATER_BUCKET)) {
            if (pot.fillWater(CrucibleBlockEntity.WATER_CAPACITY, false) == CrucibleBlockEntity.WATER_CAPACITY) {
                pot.fillWater(CrucibleBlockEntity.WATER_CAPACITY, true);
                if (!player.getAbilities().instabuild) player.setItemInHand(hand, new ItemStack(Items.BUCKET));
            } else player.displayClientMessage(pot.status(), true);
        } else if (held.is(Items.BUCKET)) {
            if (pot.drainWater(CrucibleBlockEntity.WATER_CAPACITY, false) == CrucibleBlockEntity.WATER_CAPACITY) {
                pot.drainWater(CrucibleBlockEntity.WATER_CAPACITY, true);
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                    ItemStack waterBucket = new ItemStack(Items.WATER_BUCKET);
                    if (held.isEmpty()) player.setItemInHand(hand, waterBucket);
                    else if (!player.getInventory().add(waterBucket)) player.drop(waterBucket, false);
                }
            } else player.displayClientMessage(pot.status(), true);
        } else {
            if (held.isEmpty() && !player.isShiftKeyDown()) pot.commitRecipe(player);
            player.displayClientMessage(pot.status().copy().append(" · ").append(pot.recipeStatus()), true);
            if (player.isShiftKeyDown()) {
                var steps = pot.steps();
                for (int i = 0; i < steps.size(); i++) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal((i + 1) + ". ").append(steps.get(i).getHoverName()));
                }
            }
        }
        return InteractionResult.CONSUME;
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof CrucibleBlockEntity pot) {
            for (ItemStack output : pot.takeOutputs()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), output);
        }
        super.onRemove(state, level, pos, replacement, moving);
    }
}
