package com.example.akaishi.forbidden.alchemy;

import dev.architectury.registry.menu.MenuRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
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
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class AlchemyMachineBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty WORKING = BlockStateProperties.LIT;
    private final AlchemyContent.Kind kind;
    private static final VoxelShape MORTAR = Shapes.or(box(1, 0, 1, 15, 4, 15), box(2, 4, 2, 14, 9, 14));
    private static final VoxelShape TOWER = Shapes.or(box(1, 0, 1, 15, 3, 15), box(3, 3, 3, 13, 13, 13), box(6, 13, 6, 10, 16, 10));
    public AlchemyMachineBlock(AlchemyContent.Kind kind) {
        super(Properties.of().mapColor(MapColor.STONE).strength(3.5F).sound(SoundType.STONE).requiresCorrectToolForDrops()
                .noOcclusion().lightLevel(state -> state.getValue(WORKING) ? 7 : 0));
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WORKING, false));
    }
    public AlchemyContent.Kind kind() { return kind; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, WORKING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return kind.crusher() ? MORTAR : TOWER; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new AlchemyMachineBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, AlchemyContent.MACHINE_ENTITY.get(), AlchemyMachineBlockEntity::tick);
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof AlchemyMachineBlockEntity machine)) return InteractionResult.PASS;
        ItemStack stack = player.getItemInHand(hand);
        boolean bucket = stack.is(Items.WATER_BUCKET);
        boolean bottle = stack.is(Items.POTION) && PotionUtils.getPotion(stack) == Potions.WATER;
        if (kind == AlchemyContent.Kind.DISTILLER && (bucket || bottle)) {
            int amount = bucket ? 1000 : 250;
            if (!level.isClientSide && machine.fillWater(amount, true) == amount) {
                machine.fillWater(amount, false);
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(bucket ? Items.BUCKET : Items.GLASS_BOTTLE)));
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.BOTTLE_EMPTY, net.minecraft.sounds.SoundSource.BLOCKS, 0.7F, 1F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) MenuRegistry.openExtendedMenu(serverPlayer, machine);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock()) && level.getBlockEntity(pos) instanceof AlchemyMachineBlockEntity machine) {
            Containers.dropContents(level, pos, machine);
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(state, level, pos, replacement, moving);
    }
}
