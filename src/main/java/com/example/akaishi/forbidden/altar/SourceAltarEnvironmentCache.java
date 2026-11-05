package com.example.akaishi.forbidden.altar;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;

/** 只缓存有贡献的位置；未封顶计数支持删除，外圈高度使匕首切换无需重新扫描。 */
final class SourceAltarEnvironmentCache {
    private record Sample(BlockState state, boolean beacon, boolean anchor, boolean pedestal) { }
    private final ServerLevel level;
    private final BlockPos center;
    private final Map<Long, Map<BlockPos, Sample>> chunks = new HashMap<>();
    private final Set<Long> hiddenChunks = new HashSet<>();
    private final SourceAltarRules.Accumulator inner = new SourceAltarRules.Accumulator();
    private final SourceAltarRules.Accumulator outer = new SourceAltarRules.Accumulator();
    private final NavigableSet<BlockPos> pedestals;

    SourceAltarEnvironmentCache(ServerLevel level, BlockPos center) {
        this.level = level;
        this.center = center.immutable();
        pedestals = new TreeSet<>(Comparator.comparingDouble((BlockPos pos) -> pos.distSqr(center))
                .thenComparing(SourceAltarStructure.POSITION_ORDER));
    }
    void update(BlockPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        update(pos, chunk == null ? Blocks.AIR.defaultBlockState() : chunk.getBlockState(pos), chunk);
    }
    void update(BlockPos pos, BlockState state, LevelChunk chunk) {
        if (!SourceEnergyNetwork.inRange(center, pos, SourceAltarRules.EXTENDED_HEIGHT)) return;
        long key = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        if (hiddenChunks.contains(key)) return;
        Map<BlockPos, Sample> values = chunks.get(key);
        Sample before = values == null ? null : values.get(pos);
        Sample after = sample(pos, state, chunk);
        if (java.util.Objects.equals(before, after)) return;
        if (before != null && values != null) {
            adjust(pos, before, -1);
            values.remove(pos);
            if (values.isEmpty()) chunks.remove(key);
        }
        if (after != null) {
            BlockPos immutable = pos.immutable();
            chunks.computeIfAbsent(key, ignored -> new HashMap<>()).put(immutable, after);
            adjust(immutable, after, 1);
        }
    }
    private Sample sample(BlockPos pos, BlockState state, LevelChunk chunk) {
        String id = SourceAltarRules.blockId(state.getBlock());
        boolean beacon = state.is(Blocks.BEACON) && chunk != null && chunk.getBlockEntity(pos) instanceof BeaconBlockEntity entity
                && !entity.getBeamSections().isEmpty();
        boolean anchor = state.is(Blocks.RESPAWN_ANCHOR) && state.getValue(RespawnAnchorBlock.CHARGE) > 0;
        boolean pedestal = state.getBlock() instanceof OfferingPedestalBlock
                && SourceEnergyNetwork.inRange(center, pos, SourceAltarRules.PEDESTAL_HEIGHT)
                && horizontalSquared(pos) <= (long) SourceAltarRules.PEDESTAL_RADIUS * SourceAltarRules.PEDESTAL_RADIUS;
        if (SourceAltarRules.entry(state) == null && SourceAltarRules.headMultiplier(id) == 1 && SourceAltarRules.lightMultiplier(id) == 1
                && !id.equals("minecraft:dragon_egg") && !SourceAltarRules.normalizeHead(id).equals("minecraft:dragon_head")
                && !beacon && !anchor && !pedestal) return null;
        return new Sample(state, beacon, anchor, pedestal);
    }
    private long horizontalSquared(BlockPos pos) {
        long dx = (long) pos.getX() - center.getX();
        long dz = (long) pos.getZ() - center.getZ();
        return dx * dx + dz * dz;
    }
    private void adjust(BlockPos pos, Sample sample, int delta) {
        SourceAltarRules.Accumulator values = Math.abs(pos.getY() - center.getY()) <= SourceAltarRules.BASE_HEIGHT ? inner : outer;
        values.environment(sample.state(), delta);
        if (sample.beacon()) values.activeBeacon(delta);
        if (sample.anchor()) values.chargedAnchor(delta);
        if (sample.pedestal()) {
            if (delta > 0) pedestals.add(pos); else pedestals.remove(pos);
        }
    }
    void visible(long key, boolean visible) {
        if (visible) hiddenChunks.remove(key); else hiddenChunks.add(key);
    }
    boolean hasChunk(long key) { return chunks.containsKey(key); }
    int removeChunk(long key, int budget) {
        Map<BlockPos, Sample> values = chunks.get(key);
        if (values == null) return 0;
        int used = 0;
        Iterator<Map.Entry<BlockPos, Sample>> iterator = values.entrySet().iterator();
        while (iterator.hasNext() && used < budget) {
            Map.Entry<BlockPos, Sample> value = iterator.next();
            adjust(value.getKey(), value.getValue(), -1);
            iterator.remove();
            used++;
        }
        if (values.isEmpty()) chunks.remove(key);
        return used;
    }
    List<ItemStack> offerings() {
        List<ItemStack> result = new ArrayList<>();
        for (BlockPos pos : pedestals) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null || !(chunk.getBlockEntity(pos) instanceof OfferingPedestalBlockEntity pedestal)) continue;
            result.add(pedestal.display());
            if (result.size() == 4) break;
        }
        return List.copyOf(result);
    }
    SourceAltarRules.Snapshot snapshot(SourceAltarBlockEntity altar, List<ItemStack> offerings) {
        SourceAltarRules.Accumulator values = inner.copy();
        if (altar.requestedHeight() == SourceAltarRules.EXTENDED_HEIGHT) values.mergeEnvironment(outer);
        SourceAltarStructure.Layout layout = SourceAltarStructure.layout(center, altar.getBlockState());
        for (int i = 0; i < 6; i++) {
            BlockPos member = layout.member(i);
            LevelChunk chunk = level.getChunkSource().getChunkNow(member.getX() >> 4, member.getZ() >> 4);
            if (chunk != null && chunk.getBlockEntity(member) instanceof SourceAltarBlockEntity entity) values.display(entity.display());
        }
        return values.finish(offerings);
    }
}
