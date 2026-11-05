package com.example.akaishi.forbidden.altar;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/** 仅用于首次初始化或新加载区块；分区段推进，从不创建区块票据。 */
public final class SourceAltarScanner {
    private final ServerLevel level;
    private final SourceAltarEnvironmentCache cache;
    private final BlockPos center;
    private final int minChunkX;
    private final int maxChunkX;
    private final int minChunkZ;
    private final int maxChunkZ;
    private final int minSection;
    private final int maxSection;
    private int chunkX;
    private int chunkZ;
    private int sectionY;
    private int local;
    private long processed;
    private final long total;
    private boolean complete;

    SourceAltarScanner(ServerLevel level, BlockPos center, SourceAltarEnvironmentCache cache, ChunkPos onlyChunk) {
        this.level = level;
        this.cache = cache;
        this.center = center;
        minChunkX = onlyChunk == null ? (center.getX() - SourceAltarRules.RADIUS) >> 4 : onlyChunk.x;
        maxChunkX = onlyChunk == null ? (center.getX() + SourceAltarRules.RADIUS) >> 4 : onlyChunk.x;
        minChunkZ = onlyChunk == null ? (center.getZ() - SourceAltarRules.RADIUS) >> 4 : onlyChunk.z;
        maxChunkZ = onlyChunk == null ? (center.getZ() + SourceAltarRules.RADIUS) >> 4 : onlyChunk.z;
        minSection = Math.max(level.getMinBuildHeight(), center.getY() - SourceAltarRules.EXTENDED_HEIGHT) >> 4;
        maxSection = Math.min(level.getMaxBuildHeight() - 1, center.getY() + SourceAltarRules.EXTENDED_HEIGHT) >> 4;
        chunkX = minChunkX;
        chunkZ = minChunkZ;
        sectionY = minSection;
        total = (long) (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1) * (maxSection - minSection + 1) * 4096;
    }
    public boolean complete() { return complete; }
    public int progress() { return complete ? 100 : (int) Math.min(99, processed * 100 / Math.max(1, total)); }
    public int step(int budget) {
        int used = 0;
        int sectionsVisited = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        while (!complete && used < budget && sectionsVisited < 64) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
            LevelChunkSection section = chunk == null ? null : chunk.getSection(sectionY - level.getMinSection());
            if (section == null || section.hasOnlyAir()) {
                processed += 4096 - local;
                local = 4096;
                used++;
            } else {
                while (local < 4096 && used < budget) {
                    int x = (chunkX << 4) + (local & 15);
                    int z = (chunkZ << 4) + ((local >> 4) & 15);
                    int y = (sectionY << 4) + (local >> 8);
                    local++;
                    processed++;
                    used++;
                    cursor.set(x, y, z);
                    if (!SourceEnergyNetwork.inRange(center, cursor, SourceAltarRules.EXTENDED_HEIGHT)) continue;
                    BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                    cache.update(cursor, state, chunk);
                }
            }
            if (local == 4096) {
                local = 0;
                sectionsVisited++;
                if (++sectionY > maxSection) {
                    sectionY = minSection;
                    if (++chunkZ > maxChunkZ) {
                        chunkZ = minChunkZ;
                        if (++chunkX > maxChunkX) complete = true;
                    }
                }
            }
        }
        return used;
    }
}
