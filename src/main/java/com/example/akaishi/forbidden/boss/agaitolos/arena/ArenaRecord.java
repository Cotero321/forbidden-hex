package com.example.akaishi.forbidden.boss.agaitolos.arena;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一座下界牢狱场地的记录：<b>状态机 + 地形快照 + 施工游标</b>（落盘的最小单元）。
 * <p>
 * <b>快照为什么长这样</b>（设计 §4.2.2 的"palette + 索引"）：整座场地有
 * {@code π×50²×41 ≈ 32 万} 个坐标、下界地形里非空气格可占三到七成。逐格存"坐标 + 方块"既大又慢，
 * 故这里只存两样东西：
 * <ol>
 *   <li>{@link #palette} —— 去重后的方块状态表（NBT，形如 {@code {Name:"minecraft:netherrack",Properties:{...}}}）；</li>
 *   <li>{@link #offsets} + {@link #paletteIndex} —— 每个 <b>非空气格</b> 的"枚举下标"与它的调色板下标。</li>
 * </ol>
 * 枚举下标由 {@link ArenaGeometry#offset(int, int)} 唯一决定，且施工时按枚举序<b>递增</b>写入，
 * 故还原时可以只用一个前进游标 {@link #entryCursor} 线性对上，无需查表也无需存坐标。
 * <p>
 * <b>为什么落盘用的是方块状态 NBT 而不是"方块状态 id"</b>：id 只在一次注册表加载内稳定，
 * 换过一次模组列表就会整体错位，而错位还原等于<b>把 A 方块写进 B 格</b>（设计 §4.1 认定的最坏故障）。
 * NBT 带方块名，最坏情况也只是解析不出而退化为空气。
 */
public final class ArenaRecord {

    /** 场地状态机：施工 → 战斗中 → 还原 */
    public enum Stage {
        /** 清空 + 记录快照 + 铺纹饰（分批，见 {@link NetherPrisonArena#BLOCKS_PER_TICK}） */
        BUILD,
        /** 场地已成型，战斗进行中 */
        ACTIVE,
        /** 逐格还原回快照原样（分批） */
        RESTORE
    }

    private static final String TAG_DIM = "Dim";
    private static final String TAG_CENTER = "Center";
    private static final String TAG_BOSS = "Boss";
    private static final String TAG_STAGE = "Stage";
    private static final String TAG_COLUMN = "Column";
    private static final String TAG_DY = "Dy";
    private static final String TAG_ENTRY_CURSOR = "EntryCursor";
    private static final String TAG_ENTRY_COUNT = "EntryCount";
    private static final String TAG_PALETTE = "Palette";
    private static final String TAG_OFFSETS = "Offsets";
    private static final String TAG_INDICES = "Indices";

    /** 所属维度：场地记录统一存主世界数据盘，靠它分派到真正要 tick 的维度（解析失败为 null，此时任何维度都不匹配，不会误施工） */
    @Nullable
    private final ResourceLocation dimension;
    /** 场地中心（召唤点） */
    private final BlockPos center;
    /** 发起召唤的 BOSS（用于判定"战斗是否还在进行"） */
    private final UUID bossId;

    private Stage stage = Stage.BUILD;
    /** 施工/还原共用的列游标：0 ~ {@link ArenaGeometry#COLUMN_COUNT} */
    private int columnCursor;
    /** 列内 y 偏移游标：{@code -HEIGHT_BELOW} ~ {@code HEIGHT_ABOVE}（列处理完即复位） */
    private int dyCursor = -ArenaGeometry.HEIGHT_BELOW;
    /** 还原期的快照条目游标（施工期的条目追加位置恒等于 {@link #entryCount}） */
    private int entryCursor;

    /** 去重后的方块状态表（NBT 形态，跨重启可直接用） */
    private final List<CompoundTag> palette = new ArrayList<>();
    /** 每个非空气格的枚举下标（严格递增） */
    private int[] offsets = new int[256];
    /** 每个非空气格在 {@link #palette} 中的下标 */
    private int[] paletteIndex = new int[256];
    /** 已记录的非空气格数 */
    private int entryCount;

    // ---- 运行期缓存（不落盘，按需重建）----
    /** 状态 → 调色板下标（采集期去重） */
    private transient Map<CompoundTag, Integer> tagIndex;
    /** 状态 → NBT（避免逐格重复序列化同一个 BlockState） */
    private transient Map<BlockState, CompoundTag> stateTags;
    /** 调色板解析后的 BlockState 表（还原期惰性解析一次） */
    private transient BlockState[] resolvedPalette;

    public ArenaRecord(ResourceLocation dimension, BlockPos center, UUID bossId) {
        this.dimension = dimension;
        this.center = center.immutable();
        this.bossId = bossId;
    }

    /** 从存档 NBT 还原（含半途中断的施工：游标与快照同步落盘，故可续做） */
    public ArenaRecord(CompoundTag tag) {
        this.dimension = ResourceLocation.tryParse(tag.getString(TAG_DIM));
        int[] c = tag.getIntArray(TAG_CENTER);
        this.center = c.length == 3 ? new BlockPos(c[0], c[1], c[2]) : BlockPos.ZERO;
        this.bossId = tag.hasUUID(TAG_BOSS) ? tag.getUUID(TAG_BOSS) : new UUID(0L, 0L);
        this.stage = stageByOrdinal(tag.getInt(TAG_STAGE));
        this.columnCursor = Math.max(0, Math.min(ArenaGeometry.COLUMN_COUNT, tag.getInt(TAG_COLUMN)));
        this.dyCursor = Math.max(-ArenaGeometry.HEIGHT_BELOW,
                Math.min(ArenaGeometry.HEIGHT_ABOVE, tag.getInt(TAG_DY)));
        this.entryCursor = Math.max(0, tag.getInt(TAG_ENTRY_CURSOR));
        ListTag list = tag.getList(TAG_PALETTE, 10);
        for (int i = 0; i < list.size(); i++) {
            palette.add(list.getCompound(i));
        }
        int[] off = tag.getIntArray(TAG_OFFSETS);
        int[] idx = tag.getIntArray(TAG_INDICES);
        int count = Math.max(0, tag.getInt(TAG_ENTRY_COUNT));
        count = Math.min(count, Math.min(off.length, idx.length));
        this.offsets = Arrays.copyOf(off, Math.max(256, count));
        this.paletteIndex = Arrays.copyOf(idx, Math.max(256, count));
        this.entryCount = count;
    }

    private static Stage stageByOrdinal(int ordinal) {
        Stage[] values = Stage.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : Stage.BUILD;
    }

    /** 本记录是否属于该维度 */
    public boolean matches(ResourceLocation location) {
        return dimension != null && dimension.equals(location);
    }

    public BlockPos center() {
        return center;
    }

    public UUID bossId() {
        return bossId;
    }

    public Stage stage() {
        return stage;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    /** 转入还原：游标从头开始（施工游标作废，还原要整场扫一遍） */
    public void requestRestore() {
        if (stage == Stage.RESTORE) {
            return;
        }
        stage = Stage.RESTORE;
        columnCursor = 0;
        dyCursor = -ArenaGeometry.HEIGHT_BELOW;
        entryCursor = 0;
    }

    public int columnCursor() {
        return columnCursor;
    }

    /**
     * 列内 y 游标（{-HEIGHT_BELOW} 起、{@code HEIGHT_ABOVE} 止）。
     * <p><b>为什么必须落到"列内格子"这一级</b>：预算耗尽时施工/还原会停在列中间，
     * 若只记列游标、下一 tick 从列顶重来，还原就会把"上一 tick 已经写回的原方块"再判一次 ——
     * 而它恰好等于纹饰方块时（下界灵魂沙非常常见）会被误判成"我们铺的纹饰"而清成空气，
     * 造成<b>不可逆的地形损失</b>。故游标必须细到格子，保证每格只判一次。
     */
    public int dyCursor() {
        return dyCursor;
    }

    public void setDyCursor(int dyCursor) {
        this.dyCursor = dyCursor;
    }

    /** 列处理完毕：列游标前进、列内 y 游标复位 */
    public void advanceColumn() {
        columnCursor++;
        dyCursor = -ArenaGeometry.HEIGHT_BELOW;
    }

    public boolean buildFinished() {
        return columnCursor >= ArenaGeometry.COLUMN_COUNT;
    }

    /** 施工是否已推进过（决定"要不要落盘"） */
    public int entryCount() {
        return entryCount;
    }

    // ---------------------------------------------------------------- 采集（施工期）

    /**
     * 记录一格原方块（<b>必须在清空之前调用</b>）。
     *
     * @param offset 该格在枚举序中的下标（{@link ArenaGeometry#offset(int, int)}），必须严格递增
     */
    public void append(int offset, BlockState state) {
        if (state.isAir()) {
            return;
        }
        Map<CompoundTag, Integer> index = tagIndex();
        CompoundTag tag = stateTags().computeIfAbsent(state, NbtUtils::writeBlockState);
        Integer paletteIdx = index.get(tag);
        if (paletteIdx == null) {
            paletteIdx = palette.size();
            palette.add(tag);
            index.put(tag, paletteIdx);
        }
        if (entryCount == offsets.length) {
            int cap = Math.max(256, offsets.length * 2);
            offsets = Arrays.copyOf(offsets, cap);
            paletteIndex = Arrays.copyOf(paletteIndex, cap);
        }
        offsets[entryCount] = offset;
        paletteIndex[entryCount] = paletteIdx;
        entryCount++;
    }

    private Map<CompoundTag, Integer> tagIndex() {
        if (tagIndex == null) {
            // 读档续做时重建：调色板里已有状态必须先入表，否则同一状态会被重复追加、还原时下标失真
            Map<CompoundTag, Integer> rebuilt = new ConcurrentHashMap<>();
            for (int i = 0; i < palette.size(); i++) {
                rebuilt.putIfAbsent(palette.get(i), i);
            }
            tagIndex = rebuilt;
        }
        return tagIndex;
    }

    private Map<BlockState, CompoundTag> stateTags() {
        if (stateTags == null) {
            stateTags = new ConcurrentHashMap<>();
        }
        return stateTags;
    }

    // ---------------------------------------------------------------- 还原（消费快照）

    /**
     * 尝试用快照还原"枚举下标为 {@code offset} 的这一格"。
     * <p>快照条目按枚举序递增存放，故只需看当前游标那一条：对上了就返回它的方块状态并前进游标，
     * 对不上说明这一格原本是空气（不推进游标）。
     *
     * @return 原本的方块状态；{@code null} = 原本是空气
     */
    public BlockState consume(int offset, HolderGetter<Block> lookup) {
        if (entryCursor >= entryCount || offsets[entryCursor] != offset) {
            return null;
        }
        int paletteIdx = paletteIndex[entryCursor];
        entryCursor++;
        BlockState[] table = resolve(lookup);
        return paletteIdx >= 0 && paletteIdx < table.length
                ? table[paletteIdx] : Blocks.AIR.defaultBlockState();
    }

    /** 还原是否已扫完（扫完即可注销本记录，场地恢复原样） */
    public boolean restoreFinished() {
        return columnCursor >= ArenaGeometry.COLUMN_COUNT;
    }

    /** 调色板惰性解析：读档后的第一次还原才需要注册表，故不放在构造里 */
    private BlockState[] resolve(HolderGetter<Block> lookup) {
        if (resolvedPalette == null) {
            BlockState[] table = new BlockState[palette.size()];
            for (int i = 0; i < table.length; i++) {
                try {
                    table[i] = NbtUtils.readBlockState(lookup, palette.get(i));
                } catch (RuntimeException ex) {
                    // 单个状态解析失败不能让整场还原崩掉：退化为空气，其余格继续
                    table[i] = Blocks.AIR.defaultBlockState();
                }
            }
            resolvedPalette = table;
        }
        return resolvedPalette;
    }

    // ---------------------------------------------------------------- 落盘

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_DIM, dimension == null ? "" : dimension.toString());
        tag.putIntArray(TAG_CENTER, new int[]{center.getX(), center.getY(), center.getZ()});
        tag.putUUID(TAG_BOSS, bossId);
        tag.putInt(TAG_STAGE, stage.ordinal());
        tag.putInt(TAG_COLUMN, columnCursor);
        tag.putInt(TAG_DY, dyCursor);
        tag.putInt(TAG_ENTRY_CURSOR, entryCursor);
        tag.putInt(TAG_ENTRY_COUNT, entryCount);
        ListTag list = new ListTag();
        for (CompoundTag state : palette) {
            list.add(state);
        }
        tag.put(TAG_PALETTE, list);
        // 只写有效区段：数组容量按 2 倍增长，整存会带上未用尾巴
        tag.putIntArray(TAG_OFFSETS, Arrays.copyOf(offsets, entryCount));
        tag.putIntArray(TAG_INDICES, Arrays.copyOf(paletteIndex, entryCount));
        return tag;
    }
}
