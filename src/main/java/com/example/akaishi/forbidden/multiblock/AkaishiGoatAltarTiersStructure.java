package com.example.akaishi.forbidden.multiblock;

import com.example.akaishi.forbidden.block.AkaishiMotherAltarBlock;
import com.example.akaishi.forbidden.block.AkaishiMotherAltarBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 黑山羊三级祭坛结构识别（16×16×4 分层矩阵，代码写死，非 .nbt）。
 * <p>层级自顶向下堆叠：1 级 = 顶层(y=-57) + 其下垫层(y=-58)；2 级 = 再下 y=-59；3 级 = 再下 y=-60。
 * <p>信标结构（y=-58 四角信标 + y=-59 四角 3×3 基座）仅归属 3 级：1/2 级跳过这些格，仅 3 级要求其真实存在。
 * <p>矩阵中的空气格不参与校验（只认实体方块位），故结构内空位放其它方块不影响识别。
 * <p>中心 2×2（lx/lz = 7/8）均为母祭坛，仅这四座参与扫描（外圈 8 座同类祭坛直接返回 0）；返回可识别到的最高连续等级。
 */
public final class AkaishiGoatAltarTiersStructure {

    // 方块编码，与存档扫描矩阵一一对应（0=空气，不参与校验）
    private static final int AIR = 0;
    private static final int ALTAR_STONE = 1;
    private static final int ESSENCE = 2;
    private static final int MOTHER_ALTAR = 3;
    private static final int CRYING_OBSIDIAN = 4;
    /** 信标基座方块：能激活信标即可（铁/金/钻石/绿宝石/下界合金块） */
    private static final int BEACON_BASE = 5;
    private static final int BONE = 6;
    private static final int BEACON = 7;

    /** 结构水平尺寸，lx/lz 取值 0..15 */
    private static final int SIZE = 16;
    /** 中心 2×2 锚点组在矩阵中的水平坐标，锚点可落在其中任意一格 */
    private static final int[] ANCHOR_AXIS = {7, 8};

    /** 成型阈值：等级 ≥ 1 即成型（中心 2×2 合并为巨坛）。成型判定、界面显示与仪式注能共用此门槛 */
    public static final int FORMED_TIER = 1;
    /** 主座（西北象限 corner=0）在矩阵中的水平坐标 */
    private static final int PRIMARY_LX = 7;
    private static final int PRIMARY_LZ = 7;

    /** 四个结构信标在矩阵中的水平坐标（垫层 lx/lz ∈ {2,13} 的四角），信标位于其上一层 dy=-1 */
    private static final int[] BEACON_LX = {2, 13, 2, 13};
    private static final int[] BEACON_LZ = {2, 2, 13, 13};

    // 各层矩阵：索引 [lz][lx]，lz 自北向南、lx 自西向东；dy 相对锚点所在 y=-57 层
    private static final int[][] LAYER_TOP = {
            {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 3, 0, 0, 3, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 3, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 3, 3, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 3, 3, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 3, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 3, 0, 0, 3, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}
    };

    private static final int[][] LAYER_MID = {
            {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 7, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 7, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 1, 6, 2, 2, 6, 1, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 1, 1, 2, 4, 4, 2, 1, 1, 0, 0, 0, 0},
            {0, 0, 0, 0, 6, 2, 2, 4, 4, 2, 2, 6, 0, 0, 0, 0},
            {0, 0, 0, 0, 2, 4, 4, 6, 6, 4, 4, 2, 0, 0, 0, 0},
            {0, 0, 0, 0, 2, 4, 4, 6, 6, 4, 4, 2, 0, 0, 0, 0},
            {0, 0, 0, 0, 6, 2, 2, 4, 4, 2, 2, 6, 0, 0, 0, 0},
            {0, 0, 0, 0, 1, 1, 2, 4, 4, 2, 1, 1, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 1, 6, 2, 2, 6, 1, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 7, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 7, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}
    };

    private static final int[][] LAYER_LOW = {
            {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 5, 5, 5, 0, 0, 0, 0, 0, 0, 0, 0, 5, 5, 5, 0},
            {0, 5, 5, 5, 0, 0, 0, 0, 0, 0, 0, 0, 5, 5, 5, 0},
            {0, 5, 5, 5, 0, 1, 6, 2, 2, 6, 1, 0, 5, 5, 5, 0},
            {0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0},
            {0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0},
            {0, 0, 0, 6, 0, 0, 0, 0, 0, 0, 0, 0, 6, 0, 0, 0},
            {0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0},
            {0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0},
            {0, 0, 0, 6, 0, 0, 0, 0, 0, 0, 0, 0, 6, 0, 0, 0},
            {0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0},
            {0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0},
            {0, 5, 5, 5, 0, 1, 6, 2, 2, 6, 1, 0, 5, 5, 5, 0},
            {0, 5, 5, 5, 0, 0, 0, 0, 0, 0, 0, 0, 5, 5, 5, 0},
            {0, 5, 5, 5, 0, 0, 0, 0, 0, 0, 0, 0, 5, 5, 5, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}
    };

    private static final int[][] LAYER_BOTTOM = {
            {4, 4, 4, 4, 4, 0, 0, 0, 0, 0, 0, 4, 4, 4, 4, 4},
            {4, 1, 1, 1, 4, 0, 0, 0, 0, 0, 0, 4, 1, 1, 1, 4},
            {4, 1, 1, 1, 4, 4, 4, 4, 4, 4, 4, 4, 1, 1, 1, 4},
            {4, 1, 1, 1, 4, 0, 0, 0, 0, 0, 0, 4, 1, 1, 1, 4},
            {4, 4, 4, 4, 0, 0, 0, 0, 0, 0, 0, 0, 4, 4, 4, 4},
            {0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 0, 0},
            {0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 0, 0},
            {0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 0, 0},
            {0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 0, 0},
            {0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 0, 0},
            {0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 0, 0},
            {4, 4, 4, 4, 0, 0, 0, 0, 0, 0, 0, 0, 4, 4, 4, 4},
            {4, 1, 1, 1, 4, 0, 0, 0, 0, 0, 0, 4, 1, 1, 1, 4},
            {4, 1, 1, 1, 4, 4, 4, 4, 4, 4, 4, 4, 1, 1, 1, 4},
            {4, 1, 1, 1, 4, 0, 0, 0, 0, 0, 0, 4, 1, 1, 1, 4},
            {4, 4, 4, 4, 4, 0, 0, 0, 0, 0, 0, 4, 4, 4, 4, 4}
    };

    private AkaishiGoatAltarTiersStructure() {
    }

    /**
     * 以锚点方块（中心 2×2 中任意一座）为基准扫描结构。
     * <p>非中心锚点（外圈 8 座同类母祭坛）直接返回 0，不进入完整矩阵校验。
     *
     * @return 可识别到的最高连续等级：0=未成型，1/2/3 对应三级祭坛
     */
    public static int scan(Level level, BlockPos anchor) {
        if (!isCenterAnchor(level, anchor)) {
            return 0;
        }
        int best = 0;
        for (int ox : ANCHOR_AXIS) {
            for (int oz : ANCHOR_AXIS) {
                BlockPos origin = anchor.offset(-ox, 0, -oz);
                int tier = evaluate(level, origin);
                if (tier > best) {
                    best = tier;
                }
            }
        }
        return best;
    }

    /** 锚点是否为结构中心 2×2 母祭坛之一（该组四格须相互紧邻成正方形） */
    public static boolean isCenterAnchor(Level level, BlockPos anchor) {
        if (!isMotherAltar(level, anchor)) {
            return false;
        }
        // 锚点作为 2×2 的任一角，四个方向组合中只要有一组四格全为母祭坛即可
        return quadAllAltar(level, anchor, -1, -1)
                || quadAllAltar(level, anchor, 1, -1)
                || quadAllAltar(level, anchor, -1, 1)
                || quadAllAltar(level, anchor, 1, 1);
    }

    /** 以锚点为一角，向 (dx,dz) 方向取 2×2，判断四格是否全为母祭坛 */
    private static boolean quadAllAltar(Level level, BlockPos anchor, int dx, int dz) {
        return isMotherAltar(level, anchor.offset(dx, 0, 0))
                && isMotherAltar(level, anchor.offset(0, 0, dz))
                && isMotherAltar(level, anchor.offset(dx, 0, dz));
    }

    private static boolean isMotherAltar(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(AkaishiMotherAltarBlocks.CHISHI_MOTHER_ALTAR.get());
    }

    /** 逐级向上校验，返回能连续命中的最高等级 */
    private static int evaluate(Level level, BlockPos origin) {
        // 1 级：顶层 y=-57 + 垫层 y=-58（信标结构格跳过）
        if (!matchLayer(level, origin, 0, LAYER_TOP, false)
                || !matchLayer(level, origin, -1, LAYER_MID, false)) {
            return 0;
        }
        // 2 级：再向下加 y=-59（信标基座仍跳过）
        if (!matchLayer(level, origin, -2, LAYER_LOW, false)) {
            return 1;
        }
        // 3 级：信标结构（y=-59 基座 + y=-58 信标）必须真实存在，再加 y=-60
        if (!matchLayer(level, origin, -2, LAYER_LOW, true)
                || !matchLayer(level, origin, -1, LAYER_MID, true)
                || !matchLayer(level, origin, -3, LAYER_BOTTOM, false)) {
            return 2;
        }
        return 3;
    }

    /**
     * 校验单层。
     *
     * @param requireBeaconStructure 为真时校验信标结构格（信标 + 基座）；为假时跳过，留给更高等级要求
     */
    private static boolean matchLayer(Level level, BlockPos origin, int dy, int[][] layer, boolean requireBeaconStructure) {
        for (int lz = 0; lz < SIZE; lz++) {
            int[] row = layer[lz];
            for (int lx = 0; lx < SIZE; lx++) {
                int expect = row[lx];
                // 空位不参与校验：只认矩阵中的实体方块位
                if (expect == AIR) {
                    continue;
                }
                if (!requireBeaconStructure && (expect == BEACON || expect == BEACON_BASE)) {
                    continue;
                }
                if (!matches(level.getBlockState(origin.offset(lx, dy, lz)), expect)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean matches(BlockState state, int expect) {
        return switch (expect) {
            case ALTAR_STONE -> state.is(AkaishiMotherAltarBlocks.CHISHI_ALTAR_STONE.get());
            case ESSENCE -> state.is(AkaishiMotherAltarBlocks.CHISHI_ESSENCE_BLOCK.get());
            case MOTHER_ALTAR -> state.is(AkaishiMotherAltarBlocks.CHISHI_MOTHER_ALTAR.get());
            case CRYING_OBSIDIAN -> state.is(Blocks.CRYING_OBSIDIAN)
                    // 成型后结构内的哭泣黑曜石已被替换为红纹版，须一并容忍，否则"成型→替换→失配→拆解"死循环
                    || state.is(AkaishiMotherAltarBlocks.CRYING_OBSIDIAN_RED.get());
            case BEACON_BASE -> isBeaconBase(state);
            case BONE -> state.is(Blocks.BONE_BLOCK);
            case BEACON -> state.is(Blocks.BEACON);
            default -> false;
        };
    }

    /** 信标基座方块：铁/金/钻石/绿宝石/下界合金块，均可支撑并激活信标 */
    private static boolean isBeaconBase(BlockState state) {
        return state.is(Blocks.IRON_BLOCK)
                || state.is(Blocks.GOLD_BLOCK)
                || state.is(Blocks.DIAMOND_BLOCK)
                || state.is(Blocks.EMERALD_BLOCK)
                || state.is(Blocks.NETHERITE_BLOCK);
    }

    // ------------------------------------------------------------------
    // 成型辅助：定位规范原点 / 枚举结构内关键方块（供 AkaishiAltarFormation 消费）
    // ------------------------------------------------------------------

    /**
     * 定位规范结构原点（矩阵 (0,0) 对应的世界坐标）。
     * <p>四座中心祭坛任一座均可作锚点；仅当某候选原点的中心 2×2 全为母祭坛时成立，
     * 故结果唯一，与候选遍历顺序无关。
     *
     * @return 规范原点；锚点不在结构中心 2×2 时返回 null
     */
    @Nullable
    public static BlockPos findOrigin(Level level, BlockPos anchor) {
        for (int ox : ANCHOR_AXIS) {
            for (int oz : ANCHOR_AXIS) {
                BlockPos origin = anchor.offset(-ox, 0, -oz);
                if (isCenterQuad(level, origin)) {
                    return origin;
                }
            }
        }
        return null;
    }

    /**
     * 定位锚点所属巨坛的主座（西北象限 corner=0）：四座中心祭坛中唯一承载合并界面与仪式进度的座。
     *
     * @return 主座坐标；锚点不在结构中心 2×2 时返回 null（如外圈 8 座子祭坛）
     */
    @Nullable
    public static BlockPos findPrimary(Level level, BlockPos anchor) {
        BlockPos origin = findOrigin(level, anchor);
        return origin == null ? null : primaryAnchor(origin);
    }

    /** 巨坛主座世界坐标：结构原点 + 矩阵 (7,7)，即西北象限 corner=0 */
    public static BlockPos primaryAnchor(BlockPos origin) {
        return origin.offset(PRIMARY_LX, 0, PRIMARY_LZ);
    }

    /**
     * 判断世界坐标处的信标是否属于「已成型」祭坛结构的四个结构信标之一（供静音判定消费）。
     * <p>信标固定位于垫层，由信标坐标可反推唯一候选原点；成型要求四信标真实存在，
     * 故主座处于成型态即等价于该信标归属成型结构，不存在误判。
     */
    public static boolean isFormedBeacon(Level level, BlockPos beaconPos) {
        for (int i = 0; i < BEACON_LX.length; i++) {
            BlockPos primary = primaryAnchor(beaconPos.offset(-BEACON_LX[i], 1, -BEACON_LZ[i]));
            BlockState state = loadedBlockState(level, primary);
            if (state != null
                    && state.is(AkaishiMotherAltarBlocks.CHISHI_MOTHER_ALTAR.get())
                    && state.getValue(AkaishiMotherAltarBlock.FORMED)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 只读「已加载区块」内方块状态，未加载返回 null（不触发同步加载）。
     * <p>该方法在信标音效中执行，而区块卸载时 {@code BeaconBlockEntity.setRemoved} 也会播放音效；
     * 若改用 {@link Level#getBlockState} 查询未加载区块，会同步加载区块并向距离管理器追加
     * {@code TicketType.UNKNOWN} 票据——关服收敛循环恰好只保留该票据，
     * 使 {@code ChunkMap.hasWork()} 恒为真、服务端永不收敛（表现为关闭游戏黑屏卡死）。
     */
    @Nullable
    private static BlockState loadedBlockState(Level level, BlockPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? null : chunk.getBlockState(pos);
    }

    /** origin 处结构中心 2×2 是否全为母祭坛 */
    private static boolean isCenterQuad(Level level, BlockPos origin) {
        for (int lz = 7; lz <= 8; lz++) {
            for (int lx = 7; lx <= 8; lx++) {
                if (!isMotherAltar(level, origin.offset(lx, 0, lz))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** 中心 2×2 四座母祭坛世界坐标，顺序即巨坛象限 corner：0=西北 1=东北 2=西南 3=东南 */
    public static List<BlockPos> collectCenterAltars(BlockPos origin) {
        List<BlockPos> list = new ArrayList<>(4);
        for (int lz = 7; lz <= 8; lz++) {
            for (int lx = 7; lx <= 8; lx++) {
                list.add(origin.offset(lx, 0, lz));
            }
        }
        return list;
    }

    /** 外圈 8 座子祭坛世界坐标（成型时点亮红光） */
    public static List<BlockPos> collectOuterAltars(BlockPos origin) {
        List<BlockPos> list = new ArrayList<>(8);
        for (int lz = 0; lz < SIZE; lz++) {
            for (int lx = 0; lx < SIZE; lx++) {
                if (LAYER_TOP[lz][lx] == MOTHER_ALTAR && !isCenterQuadCell(lx, lz)) {
                    list.add(origin.offset(lx, 0, lz));
                }
            }
        }
        return list;
    }

    private static boolean isCenterQuadCell(int lx, int lz) {
        return lx >= 7 && lx <= 8 && lz >= 7 && lz <= 8;
    }

    /**
     * 随 1 级生效的哭泣黑曜石格（y=-58 垫层，属 1 级校验范围）。
     * <p>只要结构 ≥ 1 级即保持红纹，故与"是否仍成型"同步。
     */
    public static List<BlockPos> collectTier1CryingObsidian(BlockPos origin) {
        List<BlockPos> list = new ArrayList<>(8);
        collectLayer(list, origin, -1, LAYER_MID);
        return list;
    }

    /**
     * 随 3 级生效的哭泣黑曜石格（y=-60 底层，仅 3 级校验范围）。
     * <p>等级回落到 3 级以下时这些格不再属于有效结构，须还原为普通哭泣黑曜石。
     */
    public static List<BlockPos> collectTier3CryingObsidian(BlockPos origin) {
        List<BlockPos> list = new ArrayList<>(8);
        collectLayer(list, origin, -3, LAYER_BOTTOM);
        return list;
    }

    private static void collectLayer(List<BlockPos> out, BlockPos origin, int dy, int[][] layer) {
        for (int lz = 0; lz < SIZE; lz++) {
            for (int lx = 0; lx < SIZE; lx++) {
                if (layer[lz][lx] == CRYING_OBSIDIAN) {
                    out.add(origin.offset(lx, dy, lz));
                }
            }
        }
    }
}
