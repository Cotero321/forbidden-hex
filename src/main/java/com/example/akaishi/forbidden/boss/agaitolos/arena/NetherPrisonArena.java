package com.example.akaishi.forbidden.boss.agaitolos.arena;

import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosEntity;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

/**
 * 下界牢狱场地（规格 §0「召唤之后…场地变更为下界牢狱」）的<b>唯一对外门面</b>：
 * 铺场 / 还原 / 状态查询 / 每 tick 分批施工 / 复活后凋零，全部从这里进。
 * <p>
 * <b>状态机</b>（每个 {@link ArenaRecord} 各自持有）：
 * <pre>
 *   召唤 → BUILD ──(列游标走满)──→ ACTIVE ──(BOSS 不在 / 死亡 / 被移除 / 重启后未重建)──→ RESTORE ──(扫完)──→ 记录注销
 *                                  ↑                                                              │
 *                                   └──────── 玩家全退/区块卸载也归到这一条（设计 §4.4）──────────────┘
 * </pre>
 * 三条落点都<b>只改状态、不做一次性大循环</b>：真正改方块的活全在 {@link #serverTick(ServerLevel)} 里
 * 按 {@link #BLOCKS_PER_TICK} 分批做，所以「崩服 / 关服 / 卡顿」都不会把某一次施工拆成半截不可续的状态
 * （游标与快照同一次落盘，见 {@link ArenaSnapshotStore#persistNow}）。
 * <p>
 * <b>不可破坏</b>：场地方块仍是原版方块（哭泣黑曜石/下界合金块/灵魂沙），"不可破坏"由平台事件层拦住
 * （{@link #isProtected} 是它的唯一判据）：玩家破坏与爆炸波及都被拒。
 * <p>
 * <b>维度不限</b>：{@link #begin} 对任何维度都照常铺场（含 {@code /summon} 旁路 —— 方便在超平坦等测试世界验证）。
 * 规格 §0 首行「仅限地狱召唤」由<b>召唤仪式</b>把守（{@code AgaitolosSummonRitual} 对非下界直接拒绝并回执），
 * 与"铺不铺场"是两回事，两条互不干扰。
 * <p>
 * <b>不破坏地形</b>：BOSS 的凋零头弹体本就不调 {@code explode}（见 {@code AgaitolosWitherSkull}），
 * 故场地不会被自己的招式炸穿；其余爆炸源由 {@link #isProtected} 一并拦下。
 */
public final class NetherPrisonArena {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 每 tick 允许改动的方块格数<b>上限</b>（只统计"真正发生改动的格子"）：设计 §6.2 拍板 512 格/tick。
     * <p><b>计费口径</b>：空气格、以及"处理后既不必写方块、也不必入快照"的格子<b>不消耗预算</b>
     * （判据见 {@link #buildStep}）⇒ 超平坦等"几乎全是空气"的测试世界一两个 tick 内扫完；
     * 正常地形（非空气格三到七成）才受这道节流约束，清空阶段约 600~800 tick（30~40s）。
     * <p>与 {@link #TIME_BUDGET_MILLIS} 二者<b>取先到者</b>。这个节拍是"卡服"与"施工太慢"之间的旋钮，<b>待调手感值</b>。
     */
    public static final int BLOCKS_PER_TICK = 512;

    /**
     * 单 tick 施工/还原的<b>时间预算上限</b>（毫秒）：本 tick 累计耗时超过它，立刻保存游标停手、留给下一 tick。
     * <p><b>为什么除了格数还要有时间闸</b>：格数闸对"读了发现不用改"的格子不计费，但<b>读</b>本身仍有成本
     * （区块查询 + 纹饰哈希）；极端情况（超大空场、服务端卡顿）下光"扫描"也可能吃掉整个 tick。
     * 时间闸保证任何一次 {@link #serverTick} 都不会长时间独占主线程。与 {@link #BLOCKS_PER_TICK} 二者取先到者。待调手感值
     */
    public static final long TIME_BUDGET_MILLIS = 8L;

    /**
     * 快照条目硬上限（配合 {@link ArenaGeometry#volume()} 做召唤前体积试算，设计 §4.2.1）：
     * 超限就<b>拒绝召唤并回执</b>，不硬干。半径 50 / 高度 ±20 时体积约 32.2 万，留有安全余量。待调手感值
     */
    public static final int MAX_SNAPSHOT_ENTRIES = 400_000;

    /**
     * 落盘节拍（tick）：施工/还原期间每隔这么多 tick 把场地表写盘一次，
     * 把"已改世界但记录未落盘"的崩溃窗口压到 5s 内（理由见 {@link ArenaSnapshotStore#persistNow}）。待调手感值
     */
    public static final int PERSIST_INTERVAL_TICKS = 100;

    /** 复活后施加的凋零时长（tick）：规格 §0「凋零 III 持续 5s」⇒ 100 tick */
    public static final int WITHER_DURATION_TICKS = 100;

    /** 复活后施加的凋零等级：III 级 ⇒ 放大器 2（规格 §0） */
    public static final int WITHER_AMPLIFIER = 2;

    private NetherPrisonArena() {
    }

    // ---------------------------------------------------------------- 对外入口（BOSS 侧三个钩子）

    /**
     * 召唤那一 tick 开始铺场（首次召唤分支调用，见 {@code AgaitolosEntity#aiStep}）。
     * <p>此时立刻建记录并<b>先落盘再改方块</b>：这样"世界被改过"这件事在磁盘上一定有对应记录，
     * 不会出现"地形清空了却没有还原凭据"的最坏故障。
     * <p><b>维度不限</b>：任何维度都照常铺场（用户拍板 —— 超平坦等测试世界也要能验证场地）。
     * 「仅限地狱召唤」是<b>召唤仪式</b>的规格（{@code AgaitolosSummonRitual} 非下界直接拒绝并回执），
     * 与这里的"铺不铺场"是两条互不干扰的闸：原先此处也拦 {@code /summon} 旁路，本轮已放开。
     */
    public static void begin(AgaitolosEntity boss) {
        if (!(boss.level() instanceof ServerLevel level)) {
            return;
        }
        if (ArenaGeometry.volume() > MAX_SNAPSHOT_ENTRIES) {
            LOGGER.warn("[akaishi] 牢狱体积 {} 超过快照上限 {}，本次不铺场", ArenaGeometry.volume(), MAX_SNAPSHOT_ENTRIES);
            return;
        }
        ArenaSnapshotStore store = ArenaSnapshotStore.get(level);
        ResourceLocation dimension = level.dimension().location();
        if (store.findFor(dimension) != null) {
            // 同维度只允许一座：两座重叠会让快照互相覆盖，宁可这次不铺（BOSS 照常存在，只是没有场地）
            LOGGER.warn("[akaishi] {} 已有下界牢狱，跳过本次铺场", dimension);
            return;
        }
        ArenaRecord record = new ArenaRecord(dimension, boss.blockPosition(), boss.getUUID());
        store.add(record);
        ArenaSnapshotStore.persistNow(level);
    }

    /**
     * 战斗结束（BOSS 死亡 / 被移除）时请求还原。
     * <p>只置状态、不动方块：逐格还原交给 {@link #serverTick}。幂等（同一 BOSS 多次调用只生效一次）。
     */
    public static void end(LivingEntity boss) {
        if (!(boss.level() instanceof ServerLevel level)) {
            return;
        }
        ArenaRecord record = ArenaSnapshotStore.get(level).findForBoss(boss.getUUID());
        if (record != null) {
            record.requestRestore();
        }
    }

    /**
     * 复活阶段结束时的收尾（规格 §0「复活后对下界牢狱内的所有生物施加凋零 III 持续 5s」）：
     * 对场地内<b>全体</b>生物（含玩家）施加凋零 III 5s，<b>只排除 BOSS 自己</b>
     * （它另有"不受负面效果"的规格，{@code canBeAffected} 也会自行挡下，这里显式跳过以免出现无意义的调用）。
     * <p>半径判定用场地包围盒（{@link ArenaGeometry#box}），与"牢狱内"同义；
     * 若该 BOSS 没有场地记录（本维度已有一座牢狱、{@link #begin} 跳过了本次铺场），
     * 退化为"以 BOSS 为中心的同尺寸区域"，保证规格表现不因场地缺失而整体消失。
     */
    public static void applyRespawnWither(LivingEntity boss) {
        if (!(boss.level() instanceof ServerLevel level)) {
            return;
        }
        ArenaRecord record = ArenaSnapshotStore.get(level).findForBoss(boss.getUUID());
        BlockPos center = record != null ? record.center() : boss.blockPosition();
        AABB box = ArenaGeometry.box(center);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (entity == boss) {
                continue;
            }
            entity.addEffect(new MobEffectInstance(MobEffects.WITHER,
                    WITHER_DURATION_TICKS, WITHER_AMPLIFIER, false, true, true));
        }
    }

    /**
     * 该维度是否已有一座<b>尚未结束</b>的牢狱（记录处于 {@link ArenaRecord.Stage#BUILD} /
     * {@link ArenaRecord.Stage#ACTIVE} / {@link ArenaRecord.Stage#RESTORE} 任一状态）。
     * <p>这是用户拍板「只允许一座牢狱出现、牢狱消失之前 BOSS 无法再次被召唤」的唯一判据（召唤仪式调用点 ④ 的第二道闸）。
     * 记录在 {@link #begin} 建场那一刻写入，还原扫完由 {@link #restoreStep} 注销
     * ⇒「存在记录」即「牢狱尚未结束」；<b>注销后本方法立刻返回 {@code false}，可以再次召唤</b>。
     * <p>判据读 {@link ArenaSnapshotStore}（{@code SavedData}）：它随服务端启动从磁盘加载，
     * 且记录在"改第一格方块之前"必已落盘 ⇒ <b>重启后这道闸依然有效</b>，
     * 不会出现"重启绕过限制、在旧牢狱之上再压一座"。
     */
    public static boolean hasUnfinishedArena(ServerLevel level) {
        ArenaRecord record = ArenaSnapshotStore.get(level).findFor(level.dimension().location());
        if (record == null) {
            return false;
        }
        ArenaRecord.Stage stage = record.stage();
        return stage == ArenaRecord.Stage.BUILD
                || stage == ArenaRecord.Stage.ACTIVE
                || stage == ArenaRecord.Stage.RESTORE;
    }

    /**
     * 该坐标是否属于<b>在场</b>的下界牢狱：平台事件层据此拒绝破坏与爆炸。
     * <p>判定只看记录状态与几何，<b>不读方块、不加载区块</b>（破坏事件是高频入口，必须廉价）。
     */
    public static boolean isProtected(ServerLevel level, BlockPos pos) {
        ArenaRecord record = ArenaSnapshotStore.get(level).findFor(level.dimension().location());
        return record != null && ArenaGeometry.contains(record.center(), pos);
    }

    // ---------------------------------------------------------------- 每 tick 驱动

    /** 服务端每维度每 tick 调用（由 {@code AkaishiMod.init} 的 {@code SERVER_LEVEL_POST} 注册） */
    public static void serverTick(ServerLevel level) {
        ArenaSnapshotStore store = ArenaSnapshotStore.get(level);
        ArenaRecord record = store.findFor(level.dimension().location());
        if (record == null) {
            return;
        }
        switch (record.stage()) {
            case BUILD -> buildStep(level, store, record);
            case ACTIVE -> activeGuard(level, record);
            case RESTORE -> restoreStep(level, store, record);
        }
        // 施工/还原期按节拍落盘；ACTIVE 期状态基本不变，不额外写盘
        if (record.stage() != ArenaRecord.Stage.ACTIVE && level.getGameTime() % PERSIST_INTERVAL_TICKS == 0) {
            store.setDirty();
            ArenaSnapshotStore.persistNow(level);
        }
    }

    /**
     * ACTIVE 期的看门狗：BOSS 一旦"不在场"就把场地转入还原。
     * <p>这一条同时覆盖设计 §4.4 列出的多情形：BOSS 死亡/被 {@code /kill}/被移除（实体已消失）、
     * 玩家全退导致区块卸载（实体被卸载，{@code getEntity} 查不到）、服务端重启（实体若未重建则查不到）。
     * 于是"世界永远停在牢狱状态"这件事在结构上不可达。
     */
    private static void activeGuard(ServerLevel level, ArenaRecord record) {
        Entity boss = level.getEntity(record.bossId());
        if (boss instanceof AgaitolosEntity agaitolos && agaitolos.isAlive() && !agaitolos.isRemoved()) {
            return;
        }
        record.requestRestore();
    }

    /**
     * 施工一步：按列推进、列内自下而上，<b>同一格先读原状入快照、再清空、最后铺纹饰</b>。
     * <p>三步同格同轮完成，是为了让"读到的原状"与"写进去的新状"永远对应同一格：
     * 纹饰只在清空该格之后再铺，故绝不会覆盖任何未入快照的原方块。
     * <p><b>预算口径（只计"真正发生改动"的格子）</b>：本格的最终目标态 =
     * 「纹饰（{@code decoration != null}）或空气」。
     * <ul>
     *   <li><b>空气 + 目标仍是空气</b> ⇒ 不清、不铺、也不入快照（快照本就不存空气），<b>0 计费</b>直接跳过
     *       —— 这是超平坦测试世界能"几乎瞬间扫完"的原因；</li>
     *   <li>其余情形（要清一格 / 要铺一格 / 要入快照一笔）都<b>计 1 格预算</b>，取先到者停下。
     *       注意"当前已等于纹饰方块"这类格子<b>仍计费、且必须入快照</b>：还原靠"当前状态 == 纹饰"判定
     *       '这格是我们铺的'，若因不计费而漏记，还原时会被误判成纹饰而清成空气 ⇒ <b>不可逆地形损失</b>。</li>
     * </ul>
     * <p><b>为什么"空气不计费"不会漏记快照</b>：快照只存非空气格（{@link ArenaRecord#append} 首行即
     * {@code state.isAir()} 早退），还原端 {@link ArenaRecord#consume} 用"枚举下标对不上"来表达"原本是空气"
     * ⇒ 空气格<b>本来就没有条目可漏</b>；非空气格则一律计费且照样 {@code append}，一条不落。
     * <p>两种预算（格数 {@link #BLOCKS_PER_TICK} 或时间 {@link #TIME_BUDGET_MILLIS}）先到者都会把列内 y 游标
     * 停在当前格（见 {@link ArenaRecord#dyCursor}），下一 tick 从这一格继续，任何一格都只被读/写一次。
     */
    private static void buildStep(ServerLevel level, ArenaSnapshotStore store, ArenaRecord record) {
        BlockPos center = record.center();
        int budget = BLOCKS_PER_TICK;
        long deadline = System.nanoTime() + TIME_BUDGET_MILLIS * 1_000_000L;
        while (budget > 0 && !record.buildFinished()) {
            int column = record.columnCursor();
            int dx = ArenaGeometry.dx(column);
            int dz = ArenaGeometry.dz(column);
            if (!ArenaGeometry.inDisc(dx, dz)) {
                record.advanceColumn(); // 圆外列：不清不铺，0 开销跳过
                continue;
            }
            int dy = record.dyCursor();
            for (; dy <= ArenaGeometry.HEIGHT_ABOVE; dy++) {
                if (budget <= 0 || System.nanoTime() >= deadline) {
                    record.setDyCursor(dy); // 两种预算先到者：把格内游标停在当前格
                    store.setDirty();
                    return;
                }
                BlockPos pos = ArenaGeometry.at(center, column, dy);
                BlockState current = level.getBlockState(pos);
                BlockState decoration = ArenaBlockPalette.decorationAt(center, pos);
                boolean needsClear = !current.isAir();
                boolean needsPlace = decoration != null && !decoration.equals(current);
                if (!needsClear && !needsPlace) {
                    continue; // 空气 + 目标仍是空气：真正无改动，0 计费跳过
                }
                budget--;
                if (needsClear) {
                    record.append(ArenaGeometry.offset(column, dy), current);
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                if (decoration != null) {
                    level.setBlock(pos, decoration, Block.UPDATE_CLIENTS);
                }
            }
            record.advanceColumn();
        }
        if (!record.buildFinished()) {
            return; // 预算用尽：下一 tick 继续
        }
        record.setStage(ArenaRecord.Stage.ACTIVE);
        store.setDirty();
        ArenaSnapshotStore.persistNow(level);
    }

    /**
     * 还原一步：按同一套列枚举把快照写回。
     * <p>三分类（判据只用"当前状态"与"纯函数纹饰"两个已知量）：
     * <ol>
     *   <li><b>还是我们铺的</b>（空气，或恰好等于 {@link ArenaBlockPalette#decorationAt}）⇒ 写回快照原方块，原本是空气就写空气；</li>
     *   <li><b>被玩家/其他来源动过</b>（非空气且不等于纹饰）⇒ <b>保留</b>，跳过该格
     *       （设计 §4.4：宁可少还原，不可覆盖玩家建造）；</li>
     *   <li>快照里没有这一格（原本就是空气）⇒ 目标即空气，仅当当前不是空气时才写。</li>
     * </ol>
     * 由此"施工只做了一半就被还原"也安全：没扫到的列还是原方块（属于第 2 类，直接保留）。
     * <p>每格只判一次（列内 y 游标跨 tick 保存），故"写回去的原方块恰好等于纹饰方块"这种巧合
     * 不会被下一次重扫误判成纹饰而清空。
     * <p>预算口径与 {@link #buildStep} 一致：<b>只有真正落笔写方块的那一格才计费</b>
     * （保留玩家方块、目标态与当前态相同 ⇒ 0 计费），同样与 {@link #TIME_BUDGET_MILLIS} 取先到者。
     */
    private static void restoreStep(ServerLevel level, ArenaSnapshotStore store, ArenaRecord record) {
        BlockPos center = record.center();
        HolderGetter<Block> lookup = level.registryAccess().lookupOrThrow(Registries.BLOCK);
        int budget = BLOCKS_PER_TICK;
        long deadline = System.nanoTime() + TIME_BUDGET_MILLIS * 1_000_000L;
        while (budget > 0 && !record.restoreFinished()) {
            int column = record.columnCursor();
            int dx = ArenaGeometry.dx(column);
            int dz = ArenaGeometry.dz(column);
            if (!ArenaGeometry.inDisc(dx, dz)) {
                record.advanceColumn();
                continue;
            }
            int dy = record.dyCursor();
            for (; dy <= ArenaGeometry.HEIGHT_ABOVE; dy++) {
                if (budget <= 0 || System.nanoTime() >= deadline) {
                    record.setDyCursor(dy);
                    store.setDirty();
                    return;
                }
                BlockPos pos = ArenaGeometry.at(center, column, dy);
                // 先消费快照游标再判"要不要写"：否则被保留下来的格子会让游标卡住，后面全部错位
                BlockState original = record.consume(ArenaGeometry.offset(column, dy), lookup);
                BlockState current = level.getBlockState(pos);
                BlockState decoration = ArenaBlockPalette.decorationAt(center, pos);
                if (!current.isAir() && !current.equals(decoration)) {
                    continue; // 玩家动过：保留，0 计费
                }
                BlockState target = original != null ? original : Blocks.AIR.defaultBlockState();
                if (!current.equals(target)) {
                    budget--; // 真正写盘才计费
                    level.setBlock(pos, target, Block.UPDATE_CLIENTS);
                }
            }
            record.advanceColumn();
        }
        if (!record.restoreFinished()) {
            return; // 预算用尽：下一 tick 继续（还原重复处理同一格是幂等的）
        }
        store.remove(record);
        ArenaSnapshotStore.persistNow(level);
    }
}
