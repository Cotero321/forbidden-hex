package com.example.akaishi.forbidden.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 巨坛封印方块：随母神祭坛成型补入中心 2×2 的正上方（y+1），仅作"上半区"的选靶与界面转发壳。
 * <p>为何需要它：巨坛模型高 29/16 ≈ 1.81 格，而方块选靶的射线是<b>逐体素</b>取形的——瞄准模型上半区时，
 * 射线落在"正上方那一格"的空气体素上，空气没有形状，既不产生选中轮廓，也不会触发 {@code use()}。
 * 把该格换成真实方块，上半区才可瞄准、可右键；加高下方祭坛自身的形状对此毫无作用。
 * <p>本体不绘制（{@link RenderShape#INVISIBLE}）、不遮挡邻面（否则巨坛探入本格的模型上半段会被剔除）、不挡光，
 * 但保留满立方选中与碰撞形状；右键转发至结构主座的合并祭坛界面。
 * <p>无对应物品：玩家无法自行获取，随结构成型补入、还原移除。仍需可破坏——万一结构被整体摧毁
 * （四座全毁后无人驱动还原），残留的隐形实心方块可由玩家自行清除。
 */
public class AkaishiAltarSealBlock extends Block {

    public AkaishiAltarSealBlock() {
        super(Properties.of()
                .mapColor(MapColor.NONE)
                .strength(2.0F)
                .sound(SoundType.STONE)
                .noOcclusion());
    }

    /** 本体交由下方巨坛模型呈现，本座不绘制 */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    /** 不参与遮挡：否则巨坛探入本格的模型上半段会被邻面剔除吞掉 */
    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    /** 不挡光，保持巨坛周围亮度与成型前一致 */
    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 0;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            // 封印恒位于中心祭坛正上方：下移一格还原锚点，再交由祭坛侧统一换算主座
            AkaishiMotherAltarBlock.openAltarMenu(level, pos.below(), serverPlayer);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
