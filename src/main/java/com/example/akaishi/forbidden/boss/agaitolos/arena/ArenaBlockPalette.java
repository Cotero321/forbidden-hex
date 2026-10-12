package com.example.akaishi.forbidden.boss.agaitolos.arena;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 下界牢狱的纹饰调色板（<b>纯函数</b>：同一个坐标永远得到同一个方块，不依赖随机数种子、不读世界）。
 * <p>
 * 全部使用<b>原版既有方块</b>（哭泣黑曜石 / 下界合金块 / 灵魂沙 / 灵魂火 —— 规格 §0 点名的四种），
 * 故本轮不新增任何方块、贴图、掉落表与标签，也不会出现"缺贴图"。
 * <p>
 * <b>为什么纹饰必须是纯函数</b>：还原时要区分"这一格是我们铺的纹饰（该恢复成原样或空气）"与
 * "这一格被玩家动过（按设计 §4.4 应当保留）"，判据就是"当前状态是否等于本函数对该坐标的返回值"——
 * 因此函数值必须跨 tick、跨重启稳定，不能引入 {@code RandomSource} 或哈希表迭代序。
 */
public final class ArenaBlockPalette {

    /** 中心平台（下界合金块）半径：给 BOSS 一个识别性的落脚区。待调手感值 */
    private static final int CORE_RADIUS = 8;

    /** 地板上「灵魂沙」斑块概率（千分比）。待调手感值 */
    private static final int SOUL_SAND_PERMILLE = 90;

    /** 地板上「下界合金块」散点概率（千分比）。待调手感值 */
    private static final int NETHERITE_SPEC_PERMILLE = 25;

    /** 灵魂沙之上「灵魂火」的点燃概率（千分比）：规格「灵魂沙上会有灵魂火（可熄灭）」，取克制值以减少光照更新。待调手感值 */
    private static final int SOUL_FIRE_PERMILLE = 250;

    /** 外墙下界合金横带的周期（格）：每隔这么多格换一条横带，构成"分层带"。待调手感值 */
    private static final int WALL_BAND_STEP = 6;

    /** 外墙非横带处的下界合金散点概率（千分比）。待调手感值 */
    private static final int WALL_NETHERITE_PERMILLE = 60;

    private ArenaBlockPalette() {
    }

    /**
     * 该坐标应当铺什么方块。
     *
     * @return 需要铺的方块状态；{@code null} = 该坐标不铺（保持清空后的空气）
     */
    @Nullable
    public static BlockState decorationAt(BlockPos center, BlockPos pos) {
        int dx = pos.getX() - center.getX();
        int dz = pos.getZ() - center.getZ();
        if (!ArenaGeometry.inDisc(dx, dz)) {
            return null;
        }
        // 相对地板面的层号：0 = 地板面、-1 = 地板基座、1..WALL_HEIGHT = 外墙/火苗
        int rel = pos.getY() - ArenaGeometry.floorY(center);

        // ① 外墙：只铺在最外圈列，自地板面上方 1 格起 WALL_HEIGHT 格（顶上留空，不做穹顶）
        if (rel >= 1 && rel <= ArenaGeometry.WALL_HEIGHT && ArenaGeometry.isWallRing(dx, dz)) {
            if (rel % WALL_BAND_STEP == 0 || permille(dx, rel, dz, 0) < WALL_NETHERITE_PERMILLE) {
                return Blocks.NETHERITE_BLOCK.defaultBlockState();
            }
            return Blocks.CRYING_OBSIDIAN.defaultBlockState();
        }

        // ② 地板面：中心下界合金平台 + 灵魂沙斑块 + 下界合金散点 + 哭泣黑曜石底
        if (rel == 0) {
            if (dx * dx + dz * dz <= CORE_RADIUS * CORE_RADIUS) {
                return Blocks.NETHERITE_BLOCK.defaultBlockState();
            }
            if (permille(dx, 0, dz, 1) < SOUL_SAND_PERMILLE) {
                return Blocks.SOUL_SAND.defaultBlockState();
            }
            if (permille(dx, 0, dz, 2) < NETHERITE_SPEC_PERMILLE) {
                return Blocks.NETHERITE_BLOCK.defaultBlockState();
            }
            return Blocks.CRYING_OBSIDIAN.defaultBlockState();
        }

        // ③ 地板基座：加厚一层哭泣黑曜石，避免"一格薄板"观感与地形爆破面
        if (rel == -1) {
            return Blocks.CRYING_OBSIDIAN.defaultBlockState();
        }

        // ④ 灵魂火：地板是灵魂沙、且本格命中点燃噪声（可按玩家浇水/踩灭，熄灭后不重铺，符合规格"可熄灭"）
        if (rel == 1 && permille(dx, 1, dz, 3) < SOUL_FIRE_PERMILLE
                && isSoulSandFloor(center, pos)) {
            return Blocks.SOUL_FIRE.defaultBlockState();
        }
        return null;
    }

    /** 本格正下方是否是灵魂沙地板（只认本函数铺出来的那一层，不认玩家放的） */
    private static boolean isSoulSandFloor(BlockPos center, BlockPos pos) {
        BlockState below = decorationAt(center, pos.below());
        return below != null && below.is(Blocks.SOUL_SAND);
    }

    /**
     * 位置哈希（千分比）：自写整数散列，跨进程/跨版本稳定。
     * <p>刻意不用 {@code level.random}：纹饰必须在重启后算出同一结果（见类注释）。
     */
    private static int permille(int x, int y, int z, int salt) {
        // 0x9E3779B1 = 黄金比例素数（写作十六进制是为了绕开 int 字面量上限）
        int h = x * 73856093 ^ y * 19349663 ^ z * 83492791 ^ salt * 0x9E3779B1;
        h ^= h >>> 13;
        h *= 1274126177;
        h ^= h >>> 16;
        return Math.floorMod(h, 1000);
    }
}
