package com.example.akaishi.forbidden.boss.agaitolos.arena;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * 下界牢狱场地的几何与列枚举口径（<b>纯函数</b>：不读世界、不写世界、无状态）。
 * <p>
 * <b>为什么几何要单独成类且唯一</b>：快照只存"非空气格"的压缩数组（见 {@link ArenaRecord}），
 * 靠的是<b>施工与还原两遍扫描顺序完全一致</b>——还原时用"枚举下标"去对快照条目，
 * 对上了就把原方块写回、对不上就当作原来的空气。因此枚举顺序是唯一真源，
 * 任何一处各写一份顺序都会造成"还原错位"（把 A 格的方块写回 B 格），
 * 故本类给出 {@link #COLUMN_COUNT} / {@link #dx(int)} / {@link #dz(int)} / {@link #offset(int, int)}
 * 四个出口，施工与还原都只调它们。
 * <p>
 * 坐标系：{@code dx = x - center.x}、{@code dz = z - center.z}、{@code dy = y - center.y}；
 * 列 = (dx,dz) 的圆盘（{@code dx²+dz² ≤ RADIUS²}），列内自 {@code -HEIGHT_BELOW} 到 {@code +HEIGHT_ABOVE} 逐格。
 */
public final class ArenaGeometry {

    /** 场地半径（格）：规格 §0「牢狱为半径 50m 的哭泣黑曜石掺杂下界合金块和灵魂沙的纹饰」。待调手感值 */
    public static final int RADIUS = 50;

    /** 清空/快照的垂直向下范围（格）：设计 §6.2 拍板「垂直只做中心 ±20 层」。待调手感值 */
    public static final int HEIGHT_BELOW = 20;

    /** 清空/快照的垂直向上范围（格）：同上。待调手感值 */
    public static final int HEIGHT_ABOVE = 20;

    /**
     * 地板面相对场地中心的 y 偏移：{@code -2}。
     * <p>取 -2 而不是 0：BOSS 在召唤点悬停，地板必须落在它脚下；
     * 取 -2 让"召唤点 → 地板"留出 2 格高度，与常态低空悬停高度同量级，避免铺完地板把 BOSS 卡进地里。待调手感值
     */
    public static final int FLOOR_OFFSET = -2;

    /** 外墙高度（格，自地板面起算）：把"牢狱"围起来，顶上留空。待调手感值 */
    public static final int WALL_HEIGHT = 20;

    /** 单列格数（含地板层与外墙层） */
    public static final int COLUMN_LENGTH = HEIGHT_BELOW + HEIGHT_ABOVE + 1;

    /** 列枚举边长：{@code 2R+1}（含圆外列，圆外列在施工时以 0 开销跳过） */
    public static final int COLUMN_SPAN = RADIUS * 2 + 1;

    /** 列枚举总数（含圆外列） */
    public static final int COLUMN_COUNT = COLUMN_SPAN * COLUMN_SPAN;

    private ArenaGeometry() {
    }

    /**
     * 体积上限（格）：{@code 列数 × 列高}。
     * <p>召唤前用它做"体积试算"（设计 §4.2.1 的第一层防护）：非空气格数只会小于等于它，
     * 故它天然是快照条目数的硬上限；超过 {@link NetherPrisonArena#MAX_SNAPSHOT_ENTRIES} 就拒绝召唤，
     * 而不是"硬干到内存/IO 爆掉"。
     */
    public static int volume() {
        return COLUMN_COUNT * COLUMN_LENGTH;
    }

    /** 列下标 → 列内 x 偏移（枚举序：先 x 后 z，均可 O(1) 反查，游标可跨 tick/重启保存） */
    public static int dx(int column) {
        return column / COLUMN_SPAN - RADIUS;
    }

    /** 列下标 → 列内 z 偏移 */
    public static int dz(int column) {
        return column % COLUMN_SPAN - RADIUS;
    }

    /** 该列是否落在圆盘内（圆外列不清空、不铺纹饰） */
    public static boolean inDisc(int dx, int dz) {
        return dx * dx + dz * dz <= RADIUS * RADIUS;
    }

    /** 该列是否是外墙所在的最外圈（外墙只铺在最外圈，厚度 ≈ 1 格） */
    public static boolean isWallRing(int dx, int dz) {
        int inner = RADIUS - 1;
        int r2 = dx * dx + dz * dz;
        return r2 <= RADIUS * RADIUS && r2 > inner * inner;
    }

    /** 快照枚举下标：{@code 列下标 × 列高 + 列内 y 序}（与施工/还原的遍历顺序严格一致） */
    public static int offset(int column, int dy) {
        return column * COLUMN_LENGTH + (dy + HEIGHT_BELOW);
    }

    /** 场地中心 → 该列的方块坐标（给定列内 y 偏移） */
    public static BlockPos at(BlockPos center, int column, int dy) {
        return new BlockPos(center.getX() + dx(column), center.getY() + dy, center.getZ() + dz(column));
    }

    /** 地板面所在的 y（场地中心 y + {@link #FLOOR_OFFSET}） */
    public static int floorY(BlockPos center) {
        return center.getY() + FLOOR_OFFSET;
    }

    /** 场地包围盒（含外墙外沿一格），用于"场地内全体"的实体查询 */
    public static AABB box(BlockPos center) {
        return new AABB(
                center.getX() - RADIUS, center.getY() - HEIGHT_BELOW, center.getZ() - RADIUS,
                center.getX() + RADIUS + 1.0D, center.getY() + HEIGHT_ABOVE + 1.0D, center.getZ() + RADIUS + 1.0D);
    }

    /** 某坐标是否落在场地体积内（圆盘 + 垂直带），供"不可破坏"判定使用 */
    public static boolean contains(BlockPos center, BlockPos pos) {
        int dx = pos.getX() - center.getX();
        int dz = pos.getZ() - center.getZ();
        if (!inDisc(dx, dz)) {
            return false;
        }
        int dy = pos.getY() - center.getY();
        return dy >= -HEIGHT_BELOW && dy <= HEIGHT_ABOVE;
    }
}
