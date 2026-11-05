package com.example.akaishi.forbidden.altar;

import com.example.akaishi.forbidden.block.entity.AkaishiForbiddenBlockEntities;
import dev.architectury.registry.menu.MenuRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class SourceAltarBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 5);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 12, 16);

    public SourceAltarBlock() {
        super(Properties.of().mapColor(MapColor.COLOR_BLACK).strength(4.0F).sound(SoundType.DEEPSLATE)
                .requiresCorrectToolForDrops().noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FORMED, false).setValue(PART, 0));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, FORMED, PART); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SourceAltarBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, AkaishiForbiddenBlockEntities.SOURCE_ALTAR.get(), SourceAltarBlockEntity::tick);
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof SourceAltarBlockEntity altar)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!level.mayInteract(player, pos) || !player.mayBuild() || player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) > 64)
            return InteractionResult.FAIL;
        if (player.isShiftKeyDown()) {
            ItemStack held = player.getItemInHand(hand);
            if (held.isEmpty()) {
                ItemStack removed = altar.takeDisplay();
                if (!removed.isEmpty() && !player.getInventory().add(removed)) player.drop(removed, false);
            } else if (altar.putDisplay(held) && !player.getAbilities().instabuild) held.shrink(1);
        } else {
            SourceAltarStructure.refresh(level, pos);
            SourceAltarBlockEntity controller = altar.controller();
            if (controller != null && controller.isFormed() && player instanceof ServerPlayer serverPlayer)
                MenuRegistry.openExtendedMenu(serverPlayer, controller, buffer -> buffer.writeBlockPos(controller.getBlockPos()));
            else player.displayClientMessage(Component.translatable("source_altar.forbidden_hex.inactive"), true);
        }
        return InteractionResult.CONSUME;
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof SourceAltarBlockEntity altar) {
            ItemStack display = altar.takeDisplay();
            if (!display.isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), display);
            if (state.getValue(FORMED)) SourceAltarStructure.invalidate(level, SourceAltarStructure.layout(pos, state));
        }
        super.onRemove(state, level, pos, replacement, moving);
    }
}
