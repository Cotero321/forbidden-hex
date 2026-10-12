package com.example.akaishi.forbidden.multiblock;

import com.example.akaishi.forbidden.block.AkaishiMotherAltarBlock;
import com.example.akaishi.forbidden.block.AkaishiMotherAltarBlocks;
import com.example.akaishi.forbidden.block.entity.AkaishiMotherAltarBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 黑山羊三级祭坛"成型/还原"：结构成型（等级 ≥ 1）时把中心 2×2 四座母祭坛合并为一座巨坛。
 * <p>成型动作：四座各自切换 {@link AkaishiMotherAltarBlock#FORMED}/{@link AkaishiMotherAltarBlock#CORNER}
 * 渲染四分之一的巨坛模型；外圈 8 座点亮红光（{@link AkaishiMotherAltarBlock#RED}）；
 * <p>结构内哭泣黑曜石替换为红纹版（紫色纹路 → 红色流动纹路）；四座旧供品逐座弹出归还。
 * <p>还原动作：等级归零时按象限反推原点，全量回退（红纹 → 哭泣黑曜石、红光熄灭、四座解散）。
 * <p>红纹维护：成型后每轮扫描持续校准——新填入的普通哭泣黑曜石自动转红；等级下降时，超出当前等级
 * 的那一层红纹（3 级层 y=-60）自动还原，1 级层（y=-58）随成型常驻。
 * <p>幂等性：成型跃迁（合并/弹出）只在"未成型 → 成型"瞬间执行一次；红纹校准与还原按当前方块状态判定，
 * 重复执行不产生副作用，且可由任意一座尚存的中心祭坛驱动。
 */
public final class AkaishiAltarFormation {

    /** 满级：底层（y=-60）红纹哭泣黑曜石只在此等级生效，降级须还原 */
    private static final int FULL_TIER = 3;

    private AkaishiAltarFormation() {
    }

    /**
     * 结构等级刷新入口（由中心 2×2 四座的方块实体周期调用）。
     *
     * @param anchor 触发扫描的祭坛坐标（须为结构中心 2×2 之一）
     * @param tier   该座识别到的结构等级
     */
    public static void refresh(Level level, BlockPos anchor, int tier) {
        BlockState anchorState = level.getBlockState(anchor);
        boolean formed = anchorState.hasProperty(AkaishiMotherAltarBlock.FORMED)
                && anchorState.getValue(AkaishiMotherAltarBlock.FORMED);

        if (tier >= AkaishiGoatAltarTiersStructure.FORMED_TIER) {
            // 成型：仅主座（西北象限 corner=0）驱动，避免四座重复执行
            BlockPos origin = AkaishiGoatAltarTiersStructure.findOrigin(level, anchor);
            if (origin == null || !AkaishiGoatAltarTiersStructure.primaryAnchor(origin).equals(anchor)) {
                return;
            }
            if (!formed) {
                // 一次性跃迁：仅"未成型 → 成型"瞬间执行（含非幂等的旧供品弹出）
                form(level, origin);
            }
            // 持续校准红纹：补齐新填入的普通哭泣黑曜石，并按当前等级还原超龄那一层
            reconcileCryingObsidian(level, origin, tier);
            // 持续补齐正上方封印：旧存档的成型态（当时尚无封印）也由此幂等修复
            ensureSeals(level, origin);
            return;
        }
        // 还原：此时结构已不完整，无法由四座定位原点，改由自身象限反推
        if (formed) {
            unform(level, originOfFormed(anchorState, anchor));
        }
    }

    /** 成型（一次性跃迁）：合并巨坛 + 外圈红光 + 弹出归还旧供品；红纹由 {@link #reconcileCryingObsidian} 持续维护 */
    private static void form(Level level, BlockPos origin) {
        List<BlockPos> centers = AkaishiGoatAltarTiersStructure.collectCenterAltars(origin);
        // 1. 中心四座合并：各自成型并标记巨坛象限
        for (int corner = 0; corner < centers.size(); corner++) {
            BlockPos pos = centers.get(corner);
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof AkaishiMotherAltarBlock) {
                level.setBlock(pos, state
                        .setValue(AkaishiMotherAltarBlock.FORMED, true)
                        .setValue(AkaishiMotherAltarBlock.CORNER, corner), Block.UPDATE_ALL);
            }
        }
        // 2. 外圈 8 座：紫光 → 红光
        for (BlockPos pos : AkaishiGoatAltarTiersStructure.collectOuterAltars(origin)) {
            setRed(level, pos, true);
        }
        // 3. 逐座弹出归还旧供品，避免合并后"凭空消失"
        for (BlockPos pos : centers) {
            if (level.getBlockEntity(pos) instanceof AkaishiMotherAltarBlockEntity altar) {
                altar.ejectOffering();
            }
        }
    }

    /**
     * 持续校准结构内红纹哭泣黑曜石（幂等，每轮扫描可安全重放）。
     * <p>1 级层（y=-58）成型后常驻红纹；3 级层（y=-60）仅等级 ≥ {@link #FULL_TIER} 时红纹，降级即还原为普通哭泣黑曜石。
     * <p>由此同时解决两个问题：结构内新填入的普通哭泣黑曜石会自动转红；破坏导致等级下降时，超出当前等级那一层的红纹会自行还原。
     */
    private static void reconcileCryingObsidian(Level level, BlockPos origin, int tier) {
        applyCryingState(level, AkaishiGoatAltarTiersStructure.collectTier1CryingObsidian(origin), true);
        applyCryingState(level, AkaishiGoatAltarTiersStructure.collectTier3CryingObsidian(origin), tier >= FULL_TIER);
    }

    /** 成组切换哭泣黑曜石外观：red 为真则"普通 → 红纹"，为假则"红纹 → 普通"；已是目标形态时跳过 */
    private static void applyCryingState(Level level, List<BlockPos> positions, boolean red) {
        for (BlockPos pos : positions) {
            BlockState state = level.getBlockState(pos);
            if (red && state.is(Blocks.CRYING_OBSIDIAN)) {
                level.setBlock(pos, AkaishiMotherAltarBlocks.CRYING_OBSIDIAN_RED.get()
                        .defaultBlockState(), Block.UPDATE_ALL);
            } else if (!red && state.is(AkaishiMotherAltarBlocks.CRYING_OBSIDIAN_RED.get())) {
                level.setBlock(pos, Blocks.CRYING_OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    /** 还原：四座解散 + 红光熄灭 + 红纹换回哭泣黑曜石 + 移除正上方封印 */
    private static void unform(Level level, BlockPos origin) {
        removeSeals(level, origin);
        for (BlockPos pos : AkaishiGoatAltarTiersStructure.collectCenterAltars(origin)) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof AkaishiMotherAltarBlock && state.getValue(AkaishiMotherAltarBlock.FORMED)) {
                level.setBlock(pos, state
                        .setValue(AkaishiMotherAltarBlock.FORMED, false)
                        .setValue(AkaishiMotherAltarBlock.CORNER, 0), Block.UPDATE_ALL);
            }
        }
        for (BlockPos pos : AkaishiGoatAltarTiersStructure.collectOuterAltars(origin)) {
            setRed(level, pos, false);
        }
        applyCryingState(level, AkaishiGoatAltarTiersStructure.collectTier1CryingObsidian(origin), false);
        applyCryingState(level, AkaishiGoatAltarTiersStructure.collectTier3CryingObsidian(origin), false);
    }

    /** 外圈子祭坛红光开关（仅视觉，属性不存在则跳过） */
    private static void setRed(Level level, BlockPos pos, boolean red) {
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(AkaishiMotherAltarBlock.RED) && state.getValue(AkaishiMotherAltarBlock.RED) != red) {
            level.setBlock(pos, state.setValue(AkaishiMotherAltarBlock.RED, red), Block.UPDATE_ALL);
        }
    }

    /**
     * 补齐中心 2×2 正上方的封印方块（幂等，可每轮重放）。
     * <p>巨坛模型高 29/16 ≈ 1.81 格，而方块选靶是逐体素取形的：瞄准模型上半区时，射线落在"正上方那一格"
     * 的空气体素上，空气无形状，既没有选中轮廓也不会触发 {@code use()}。故成型后须在四座正上方各补一座
     * {@link com.example.akaishi.forbidden.block.AkaishiAltarSealBlock}（隐形实心、转发主座界面）。
     * <p>放在每轮扫描而非仅成型跃迁执行，还兼顾两种补漏：旧存档的成型态、封印被破坏后的自愈。
     */
    private static void ensureSeals(Level level, BlockPos origin) {
        Block seal = AkaishiMotherAltarBlocks.CHISHI_ALTAR_SEAL.get();
        for (BlockPos pos : AkaishiGoatAltarTiersStructure.collectCenterAltars(origin)) {
            BlockPos sealPos = pos.above();
            if (!level.getBlockState(sealPos).is(seal)) {
                level.setBlock(sealPos, seal.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    /** 移除中心 2×2 正上方的封印，还原顶部空位（仅认本模组的封印方块，不误伤玩家自建） */
    private static void removeSeals(Level level, BlockPos origin) {
        Block seal = AkaishiMotherAltarBlocks.CHISHI_ALTAR_SEAL.get();
        for (BlockPos pos : AkaishiGoatAltarTiersStructure.collectCenterAltars(origin)) {
            BlockPos sealPos = pos.above();
            if (level.getBlockState(sealPos).is(seal)) {
                level.setBlock(sealPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    /**
     * 由已成型祭坛的象限属性反推结构原点。
     * <p>象限顺序与 {@link AkaishiGoatAltarTiersStructure#collectCenterAltars} 一致：
     * 0=(7,7) 西北 1=(8,7) 东北 2=(7,8) 西南 3=(8,8) 东南。
     */
    private static BlockPos originOfFormed(BlockState state, BlockPos anchor) {
        int corner = state.getValue(AkaishiMotherAltarBlock.CORNER);
        int lx = (corner == 1 || corner == 3) ? 8 : 7;
        int lz = corner >= 2 ? 8 : 7;
        return anchor.offset(-lx, 0, -lz);
    }
}
