package com.example.akaishi.forbidden.block;
import com.example.akaishi.block.AkaishiMachineBlock;

import com.example.akaishi.forbidden.block.entity.AkaishiForbiddenBlockEntities;
import com.example.akaishi.forbidden.block.entity.AkaishiMotherAltarBlockEntity;
import com.example.akaishi.forbidden.multiblock.AkaishiGoatAltarTiersStructure;
import dev.architectury.registry.menu.MenuRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 母神祭坛：生命线终局多方块祭坛的核心，向"黑山羊之母"献上供品。
 * <p>未成型：持物右键 → 供奉（不设黑白名单，任何物品均可）；空手右键 → 取回；供奉物悬浮旋转。
 * <p>结构成型（≥1 级）：中心 2×2 四座转为巨坛象限（{@link #FORMED}/{@link #CORNER}），
 * 右键打开合并祭坛界面（等级展示 + 单物品供奉槽）；外圈 8 座点亮红光（{@link #RED}）。
 * <p>巨坛模型最高 29/16 ≈ 1.81 格，超出本体一格：射线是<b>逐体素</b>取形的，加高本座形状对
 * "正上方那一格"（空气体素）毫无作用，故正上方由 {@link AkaishiAltarSealBlock} 随成型补入
 * （隐形实心 + 转发主座界面），否则右键上半区既无轮廓框也打不开界面。
 * <p>材质参考黑曜石（硬度 50 / 抗爆 1200），象征不可亵渎的神龛。
 */
public class AkaishiMotherAltarBlock extends AkaishiMachineBlock {

    /** 成型：中心 2×2 四座合并为巨坛（各自渲染四分之一大模型，无缝拼合） */
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");
    /** 红光：外圈 8 座子祭坛在结构成型时由紫光转为红光（仅视觉） */
    public static final BooleanProperty RED = BooleanProperty.create("red");
    /** 巨坛象限：0=西北 1=东北 2=西南 3=东南，仅 formed=true 时有效 */
    public static final IntegerProperty CORNER = IntegerProperty.create("corner", 0, 3);

    public AkaishiMotherAltarBlock() {
        super(Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .strength(50.0F, 1200.0F)
                .sound(SoundType.STONE));
        registerDefaultState(defaultBlockState()
                .setValue(FORMED, false)
                .setValue(RED, false)
                .setValue(CORNER, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED, RED, CORNER);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return AkaishiForbiddenBlockEntities.CHISHI_MOTHER_ALTAR.get().create(pos, state);
    }

    /** 祭坛用普通方块模型渲染本体（BaseEntityBlock 默认 INVISIBLE 会导致本体透明） */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, AkaishiForbiddenBlockEntities.CHISHI_MOTHER_ALTAR.get(),
                AkaishiMotherAltarBlockEntity::serverTick);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        // 成型后的中心四座：右键打开合并祭坛界面（等级展示 + 单物品供奉槽），不再走单座供奉
        if (state.getValue(FORMED)) {
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                openAltarMenu(level, pos, serverPlayer);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS; // 放/取由服务端权威处理，客户端放行等待同步
        }
        if (!(level.getBlockEntity(pos) instanceof AkaishiMotherAltarBlockEntity altar)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (!altar.hasOffering()) {
            // 祭坛空置：持物 → 供奉（不设黑白名单，任何物品均可）；空手无操作
            if (held.isEmpty()) {
                return InteractionResult.CONSUME;
            }
            // 取 1 件悬浮供奉（创造模式不消耗实物）
            ItemStack offering = held.copyWithCount(1);
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
            altar.setOffering(offering);
            return InteractionResult.CONSUME;
        }
        // 已有供奉：空手 → 取回；持物无操作
        if (held.isEmpty()) {
            ItemStack back = altar.takeOffering();
            if (!player.getInventory().add(back)) {
                player.drop(back, false);
            }
        }
        return InteractionResult.CONSUME;
    }

    /**
     * 打开合并祭坛界面：以结构主座（西北象限 corner=0）的方块实体为宿主，
     * 四座界面共享同一供奉槽与等级数据，避免"四座各有一份供奉"的歧义。
     * <p>包内可见：正上方的 {@link AkaishiAltarSealBlock} 也由此转发。
     */
    static void openAltarMenu(Level level, BlockPos pos, ServerPlayer player) {
        BlockPos origin = AkaishiGoatAltarTiersStructure.findOrigin(level, pos);
        BlockPos host = origin == null ? pos : AkaishiGoatAltarTiersStructure.primaryAnchor(origin);
        if (level.getBlockEntity(host) instanceof AkaishiMotherAltarBlockEntity altar) {
            MenuRegistry.openExtendedMenu(player, altar);
        }
    }
}
