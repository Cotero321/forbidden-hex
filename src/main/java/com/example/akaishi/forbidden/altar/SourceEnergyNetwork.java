package com.example.akaishi.forbidden.altar;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 仅服务端线程调用；索引只存已加载主控，供能始终重新校验结构和余额。 */
public final class SourceEnergyNetwork {
    private static final Map<ServerLevel, State> LEVELS = new HashMap<>();
    private SourceEnergyNetwork() { }
    private static final class State {
        private final Set<BlockPos> positions = new LinkedHashSet<>();
        private final Map<Long, Set<BlockPos>> watchers = new HashMap<>();
        private int next;
    }
    private static void checkThread(ServerLevel level) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Source energy must be accessed on the server thread");
    }
    public static void add(ServerLevel level, BlockPos pos) {
        checkThread(level);
        State state = LEVELS.computeIfAbsent(level, ignored -> new State());
        BlockPos immutable = pos.immutable();
        if (!state.positions.add(immutable)) return;
        for (int x = (pos.getX() - SourceAltarRules.RADIUS) >> 4; x <= (pos.getX() + SourceAltarRules.RADIUS) >> 4; x++) {
            for (int z = (pos.getZ() - SourceAltarRules.RADIUS) >> 4; z <= (pos.getZ() + SourceAltarRules.RADIUS) >> 4; z++) {
                state.watchers.computeIfAbsent(ChunkPos.asLong(x, z), ignored -> new LinkedHashSet<>()).add(immutable);
            }
        }
    }
    public static void remove(ServerLevel level, BlockPos pos) {
        checkThread(level);
        State state = LEVELS.get(level);
        if (state == null || !state.positions.remove(pos)) return;
        state.watchers.values().forEach(values -> values.remove(pos));
        state.watchers.values().removeIf(Set::isEmpty);
    }
    public static void unload(ServerLevel level) { LEVELS.remove(level); }
    public static void clear() { LEVELS.clear(); }
    public static void invalidateNear(ServerLevel level, BlockPos changed) { blockChanged(level, changed); }
    public static void blockChanged(ServerLevel level, BlockPos changed) {
        if (!level.getServer().isSameThread()) {
            BlockPos immutable = changed.immutable();
            level.getServer().execute(() -> blockChanged(level, immutable));
            return;
        }
        State state = LEVELS.get(level);
        if (state == null) return;
        Set<BlockPos> sources = state.watchers.get(ChunkPos.asLong(changed.getX() >> 4, changed.getZ() >> 4));
        if (sources == null) return;
        for (BlockPos pos : sources) {
            if (inRange(pos, changed, SourceAltarRules.EXTENDED_HEIGHT)) {
                SourceAltarBlockEntity altar = loadedAltar(level, pos);
                if (altar != null) altar.environmentChanged(changed);
            }
        }
    }
    public static void chunkChanged(ServerLevel level, int x, int z, boolean loaded) {
        if (!level.getServer().isSameThread()) {
            level.getServer().execute(() -> chunkChanged(level, x, z, loaded));
            return;
        }
        State state = LEVELS.get(level);
        if (state == null) return;
        Set<BlockPos> sources = state.watchers.get(ChunkPos.asLong(x, z));
        if (sources == null) return;
        for (BlockPos pos : sources) {
            SourceAltarBlockEntity altar = loadedAltar(level, pos);
            if (altar != null) altar.chunkChanged(x, z, loaded);
        }
    }
    private static SourceAltarBlockEntity loadedAltar(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk != null && chunk.getBlockEntity(pos) instanceof SourceAltarBlockEntity altar && altar.isMaster() ? altar : null;
    }
    public static void blockChanged(ServerLevel level, BlockPos pos, BlockState before, BlockState after) {
        if (!level.getServer().isSameThread()) {
            BlockPos immutable = pos.immutable();
            level.getServer().execute(() -> blockChanged(level, immutable, before, after));
            return;
        }
        State state = LEVELS.get(level);
        if (state != null && state.watchers.containsKey(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4))
                && relevantChange(before, after)) blockChanged(level, pos);
    }
    public static boolean relevantChange(BlockState before, BlockState after) {
        if (before.getBlock() == after.getBlock() && java.util.Objects.equals(SourceAltarRules.entry(before), SourceAltarRules.entry(after))) {
            return after.is(Blocks.RESPAWN_ANCHOR)
                    && (before.getValue(RespawnAnchorBlock.CHARGE) > 0) != (after.getValue(RespawnAnchorBlock.CHARGE) > 0);
        }
        return relevant(before) || relevant(after);
    }
    private static boolean relevant(BlockState state) {
        String id = SourceAltarRules.blockId(state.getBlock());
        return SourceAltarRules.entry(state) != null || SourceAltarRules.headMultiplier(id) > 1 || SourceAltarRules.lightMultiplier(id) > 1
                || id.equals("minecraft:beacon") || id.equals("minecraft:respawn_anchor") || id.equals("minecraft:dragon_egg")
                || SourceAltarRules.normalizeHead(id).equals("minecraft:dragon_head") || state.getBlock() instanceof OfferingPedestalBlock;
    }
    public static int tick(ServerLevel level) {
        checkThread(level);
        State state = LEVELS.get(level);
        if (state == null || state.positions.isEmpty()) return 0;
        for (BlockPos pos : List.copyOf(state.positions)) {
            if (loadedAltar(level, pos) == null) remove(level, pos);
        }
        List<BlockPos> positions = new ArrayList<>(state.positions);
        if (positions.isEmpty()) return 0;
        int budget = SourceAltarRules.SCAN_BUDGET;
        int start = Math.floorMod(state.next++, positions.size());
        for (int i = 0; i < positions.size() && budget > 0; i++) {
            BlockPos pos = positions.get((start + i) % positions.size());
            if (level.getBlockEntity(pos) instanceof SourceAltarBlockEntity altar) {
                int share = Math.max(1, budget / (positions.size() - i));
                budget -= altar.scanStep(share);
            }
        }
        return SourceAltarRules.SCAN_BUDGET - budget;
    }
    public static boolean inRange(BlockPos source, BlockPos target, int height) {
        return Math.abs((long) source.getY() - target.getY()) <= height
                && horizontalSquared(source, target) <= (long) SourceAltarRules.RADIUS * SourceAltarRules.RADIUS;
    }
    private static long horizontalSquared(BlockPos a, BlockPos b) {
        long dx = (long) a.getX() - b.getX();
        long dz = (long) a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    /** halfUnits 为半点源能；simulate只读，不跨多个祭坛凑付。 */
    public static boolean request(ServerLevel level, BlockPos consumer, long halfUnits, boolean simulate) {
        checkThread(level);
        if (halfUnits <= 0 || halfUnits > SourceAltarRules.MAX_UNITS || !level.hasChunkAt(consumer)) return false;
        State state = LEVELS.get(level);
        if (state == null) return false;
        List<BlockPos> candidates = state.positions.stream()
                .filter(pos -> inRange(pos, consumer, SourceAltarRules.EXTENDED_HEIGHT))
                .sorted(Comparator.comparingDouble((BlockPos pos) -> pos.distSqr(consumer)).thenComparing(SourceAltarStructure.POSITION_ORDER)).toList();
        for (BlockPos pos : candidates) {
            if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof SourceAltarBlockEntity altar)
                    || !altar.isOperational() || !inRange(pos, consumer, altar.snapshot().height())) continue;
            if (altar.consume(halfUnits, simulate)) return true;
        }
        return false;
    }
}
