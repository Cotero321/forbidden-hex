package com.example.akaishi.forbidden.brewing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class BrewingWorldRestoration extends SavedData {
    private static final String KEY = "forbidden_hex_brewing_restoration";
    private static final int MAX_ENTRIES = 8192;
    private final Map<BlockPos, Entry> entries = new LinkedHashMap<>();
    private record Entry(BlockState original, BlockState expected, long expires, UUID aura) { }

    public static BrewingWorldRestoration get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(BrewingWorldRestoration::load, BrewingWorldRestoration::new, KEY);
    }

    public boolean isTracked(BlockPos pos) { return entries.containsKey(pos); }
    public boolean isLavaAt(BlockPos pos) {
        Entry entry = entries.get(pos);
        return entry != null && entry.original.getFluidState().is(FluidTags.LAVA);
    }
    public boolean hasRoom() { return entries.size() < MAX_ENTRIES; }
    public int size() { return entries.size(); }

    public void remember(BlockPos pos, BlockState original, BlockState expected, long expires, UUID aura) {
        if (!hasRoom() && !isTracked(pos)) return;
        entries.put(pos.immutable(), new Entry(original, expected, expires, aura));
        setDirty();
    }

    public void refresh(BlockPos pos, BlockState expected, long expires, UUID aura) {
        Entry previous = entries.get(pos);
        if (previous == null || previous.expected != expected) return;
        entries.put(pos.immutable(), new Entry(previous.original, expected, Math.max(expires, previous.expires), aura));
        setDirty();
    }

    public void forget(BlockPos pos) {
        if (entries.remove(pos) != null) setDirty();
    }

    public void tick(ServerLevel level) {
        long now = level.getGameTime();
        int restored = 0;
        Iterator<Map.Entry<BlockPos, Entry>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext() && restored < 256) {
            var tracked = iterator.next();
            BlockPos pos = tracked.getKey();
            Entry entry = tracked.getValue();
            if (!BrewingWorldEffects.available(level, pos)) continue;
            boolean active = false;
            if (entry.aura != null) {
                Entity owner = level.getEntity(entry.aura);
                if (owner instanceof LivingEntity entity && BrewingStatusEffects.has(entity, BrewingEffectType.LAVA_REPULSION)) {
                    int radius = BrewingStatusEffects.strength(entity, BrewingEffectType.LAVA_REPULSION) >= 3 ? 3 : 2;
                    active = pos.distSqr(entity.blockPosition()) <= radius * radius;
                }
            }
            if (active || entry.aura == null && entry.expires > now) continue;
            iterator.remove();
            setDirty();
            if (level.getBlockState(pos) == entry.expected && !level.getBlockState(pos).hasBlockEntity()) {
                level.setBlock(pos, entry.original, Block.UPDATE_ALL);
                restored++;
            }
        }
    }

    public static BrewingWorldRestoration load(CompoundTag tag) {
        BrewingWorldRestoration result = new BrewingWorldRestoration();
        ListTag list = tag.getList("Entries", Tag.TAG_COMPOUND);
        if (list.size() > MAX_ENTRIES) return result;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.contains("Pos", Tag.TAG_LONG) || !entry.contains("Original", Tag.TAG_COMPOUND)
                    || !entry.contains("Expected", Tag.TAG_COMPOUND) || !entry.contains("Expires", Tag.TAG_LONG)) continue;
            try {
                BlockState original = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), entry.getCompound("Original"));
                BlockState expected = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), entry.getCompound("Expected"));
                if (original.hasBlockEntity() || expected.hasBlockEntity()) continue;
                result.entries.put(BlockPos.of(entry.getLong("Pos")), new Entry(original, expected,
                        entry.getLong("Expires"), entry.hasUUID("Aura") ? entry.getUUID("Aura") : null));
            } catch (IllegalArgumentException ignored) { }
        }
        return result;
    }

    @Override public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (var tracked : entries.entrySet()) {
            Entry entry = tracked.getValue();
            CompoundTag item = new CompoundTag();
            item.putLong("Pos", tracked.getKey().asLong());
            item.put("Original", NbtUtils.writeBlockState(entry.original));
            item.put("Expected", NbtUtils.writeBlockState(entry.expected));
            item.putLong("Expires", entry.expires);
            if (entry.aura != null) item.putUUID("Aura", entry.aura);
            list.add(item);
        }
        tag.put("Entries", list);
        return tag;
    }
}
