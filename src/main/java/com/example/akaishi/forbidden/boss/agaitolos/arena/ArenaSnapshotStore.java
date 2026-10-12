package com.example.akaishi.forbidden.boss.agaitolos.arena;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 下界牢狱场地的落盘载体（{@code SavedData}）：<b>服务器重启后仍能把场地还回去</b>。
 * <p>
 * <b>为什么存主世界数据盘</b>：与 {@code DecayZoneManager} 同一口径 —— 主世界维度在服务端生命周期内
 * 必然存在且必然被 tick，而场地记录自带 {@code Dimension} 字段，真正的施工/还原由"该维度 tick 时"
 * 分派执行（见 {@link NetherPrisonArena#serverTick}）。若存进下界自己的数据盘，就得多一条
 * "下界没被加载时也要找到它"的旁路，多一条旁路就多一处能失效的地方。
 * <p>
 * <b>重启自愈</b>：服务端启动后 <b>下界维度照常每 tick 被 tick</b>（与原版一致，与有没有玩家无关），
 * 于是 {@link NetherPrisonArena#serverTick} 会读到这份记录：施工中 ⇒ 续做，已成型但 BOSS 已不在 ⇒ 还原。
 * 世界因此在任何一次重启后都能收敛回原样，不存在"永久被改造"的残留。
 */
public final class ArenaSnapshotStore extends SavedData {

    /** 存档键名 */
    public static final String DATA_NAME = "akaishi_nether_prison";

    private static final String TAG_RECORDS = "Arenas";

    /** 全部场地记录（服务端主线程串行访问，用并发容器防跨线程误读） */
    private final List<ArenaRecord> records = new CopyOnWriteArrayList<>();

    public ArenaSnapshotStore() {
    }

    /** 1.20.1 SavedData 工厂：从存档 NBT 还原 */
    public ArenaSnapshotStore(CompoundTag tag) {
        this();
        load(tag);
    }

    /** 取主世界数据盘中的场地表（懒加载） */
    public static ArenaSnapshotStore get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(ArenaSnapshotStore::new, ArenaSnapshotStore::new, DATA_NAME);
    }

    /**
     * 立刻把场地表写盘（只在"即将开始改方块"与"周期节拍"两处调用）。
     * <p><b>为什么需要它</b>：{@code setDirty()} 只是打脏标记、等原版自动存盘（默认 5 分钟一次），
     * 而场地一旦开始清空就已在改写世界 —— 若这 5 分钟内崩服且记录还没落盘，那片地形就再也补不回来了
     * （本方案里唯一不可接受的故障）。故建记录后立即落一次盘，此后每
     * {@link NetherPrisonArena#PERSIST_INTERVAL_TICKS} tick 落一次，把暴露窗口压到这个节拍内。
     * <p>{@code SavedData#save(File)} 自带"非脏不写"，故周期调用不会产生无谓 IO。
     */
    public static void persistNow(ServerLevel level) {
        level.getServer().overworld().getDataStorage().save();
    }

    /** 该维度上已有的场地（同一维度同时只允许一座：两座重叠会让快照互相覆盖） */
    @Nullable
    public ArenaRecord findFor(ResourceLocation dimension) {
        for (ArenaRecord record : records) {
            if (record.matches(dimension)) {
                return record;
            }
        }
        return null;
    }

    /** 按 BOSS 反查场地（复活凋零、死亡还原都用它） */
    @Nullable
    public ArenaRecord findForBoss(java.util.UUID bossId) {
        for (ArenaRecord record : records) {
            if (record.bossId().equals(bossId)) {
                return record;
            }
        }
        return null;
    }

    public void add(ArenaRecord record) {
        records.add(record);
        setDirty();
    }

    public void remove(ArenaRecord record) {
        records.remove(record);
        setDirty();
    }

    // ===== SavedData 持久化（1.20.1：加载走构造工厂，保存实现抽象方法 save） =====

    private void load(CompoundTag tag) {
        records.clear();
        ListTag list = tag.getList(TAG_RECORDS, 10);
        for (int i = 0; i < list.size(); i++) {
            records.add(new ArenaRecord(list.getCompound(i)));
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (ArenaRecord record : records) {
            list.add(record.save());
        }
        tag.put(TAG_RECORDS, list);
        return tag;
    }
}
