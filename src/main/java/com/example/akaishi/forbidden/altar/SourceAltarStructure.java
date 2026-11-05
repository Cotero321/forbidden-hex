package com.example.akaishi.forbidden.altar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SourceAltarStructure {
    public static final Comparator<BlockPos> POSITION_ORDER = Comparator.comparingInt((BlockPos pos) -> pos.getX())
            .thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ);
    private SourceAltarStructure() { }

    public record Layout(BlockPos origin, Direction facing) {
        public BlockPos member(int part) {
            return origin.relative(facing.getClockWise(), part % 3).relative(facing.getOpposite(), part / 3);
        }
    }
    public static Layout layout(BlockPos pos, BlockState state) {
        Direction facing = state.getValue(SourceAltarBlock.FACING);
        int part = state.getValue(SourceAltarBlock.PART);
        return new Layout(pos.relative(facing.getClockWise(), -(part % 3)).relative(facing.getOpposite(), -(part / 3)), facing);
    }
    public static boolean complete(Level level, Layout layout) {
        for (int i = 0; i < 6; i++) {
            BlockPos pos = layout.member(i);
            if (!level.hasChunkAt(pos)) return false;
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof SourceAltarBlock) || !state.getValue(SourceAltarBlock.FORMED)
                    || state.getValue(SourceAltarBlock.PART) != i || state.getValue(SourceAltarBlock.FACING) != layout.facing()) return false;
        }
        return true;
    }
    public static void invalidate(Level level, BlockPos pos) {
        if (level.isClientSide || !level.hasChunkAt(pos)) return;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SourceAltarBlock)) return;
        invalidate(level, layout(pos, state));
    }
    public static void invalidate(Level level, Layout layout) {
        if (level.isClientSide) return;
        for (int i = 0; i < 6; i++) {
            BlockPos member = layout.member(i);
            if (!level.hasChunkAt(member)) continue;
            BlockState current = level.getBlockState(member);
            if (current.getBlock() instanceof SourceAltarBlock && layout(member, current).equals(layout)) {
                level.setBlock(member, current.setValue(SourceAltarBlock.FORMED, false), 3);
                if (level.getBlockEntity(member) instanceof SourceAltarBlockEntity altar) altar.structureChanged();
            }
        }
    }
    public static void refresh(Level level, BlockPos pos) {
        if (level.isClientSide || !level.hasChunkAt(pos)) return;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SourceAltarBlock)) return;
        if (state.getValue(SourceAltarBlock.FORMED)) {
            if (complete(level, layout(pos, state))) return;
            invalidate(level, pos);
        }
        List<Layout> candidates = new ArrayList<>();
        for (Direction facing : new Direction[]{Direction.NORTH, Direction.EAST}) {
            for (int part = 0; part < 6; part++) {
                BlockPos origin = pos.relative(facing.getClockWise(), -(part % 3)).relative(facing.getOpposite(), -(part / 3));
                Layout candidate = new Layout(origin.immutable(), facing);
                boolean fits = true;
                for (int i = 0; i < 6; i++) {
                    BlockPos member = candidate.member(i);
                    if (!level.hasChunkAt(member)) { fits = false; break; }
                    BlockState current = level.getBlockState(member);
                    if (!(current.getBlock() instanceof SourceAltarBlock) || current.getValue(SourceAltarBlock.FORMED)) { fits = false; break; }
                }
                if (fits) candidates.add(candidate);
            }
        }
        candidates.sort(Comparator.comparing(Layout::origin, POSITION_ORDER).thenComparingInt(value -> value.facing().get2DDataValue()));
        if (candidates.isEmpty()) return;
        Layout chosen = candidates.get(0);
        for (int i = 0; i < 6; i++) {
            BlockPos member = chosen.member(i);
            level.setBlock(member, level.getBlockState(member).setValue(SourceAltarBlock.FORMED, true)
                    .setValue(SourceAltarBlock.FACING, chosen.facing()).setValue(SourceAltarBlock.PART, i), 3);
            if (level.getBlockEntity(member) instanceof SourceAltarBlockEntity altar) altar.structureChanged();
        }
        if (level instanceof ServerLevel server) SourceEnergyNetwork.add(server, chosen.origin());
    }
}
