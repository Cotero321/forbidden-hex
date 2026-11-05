package com.example.akaishi.forbidden.soul;

import net.minecraft.nbt.CompoundTag;

import java.util.EnumMap;
import java.util.Map;

/** Hidden affinity scores persisted with a soul crystal. */
public final class SoulScoreLedger {
    private static final String SCORES_TAG = "scores";
    private final EnumMap<SoulAffinity, Integer> scores = new EnumMap<>(SoulAffinity.class);

    public SoulScoreLedger() {
        for (SoulAffinity affinity : SoulAffinity.values()) scores.put(affinity, 0);
    }

    public SoulScoreLedger(Map<SoulAffinity, Integer> values) {
        this();
        values.forEach((affinity, value) -> scores.put(affinity, Math.max(0, value)));
    }

    public int score(SoulAffinity affinity) {
        return scores.getOrDefault(affinity, 0);
    }

    public void add(SoulAffinity affinity, int amount) {
        if (affinity == null || amount <= 0) return;
        scores.merge(affinity, amount, Integer::sum);
    }

    public void set(SoulAffinity affinity, int value) {
        if (affinity != null) scores.put(affinity, Math.max(0, value));
    }

    public Map<SoulAffinity, Integer> asMap() {
        return Map.copyOf(scores);
    }

    public void clear() {
        scores.replaceAll((affinity, ignored) -> 0);
    }

    public void save(CompoundTag parent) {
        CompoundTag tag = new CompoundTag();
        for (SoulAffinity affinity : SoulAffinity.values()) {
            int value = score(affinity);
            if (value > 0) tag.putInt(affinity.id(), value);
        }
        parent.put(SCORES_TAG, tag);
    }

    public static SoulScoreLedger load(CompoundTag parent) {
        SoulScoreLedger ledger = new SoulScoreLedger();
        if (!parent.contains(SCORES_TAG, CompoundTag.TAG_COMPOUND)) return ledger;
        CompoundTag tag = parent.getCompound(SCORES_TAG);
        for (SoulAffinity affinity : SoulAffinity.values()) {
            if (tag.contains(affinity.id(), CompoundTag.TAG_INT)) ledger.set(affinity, tag.getInt(affinity.id()));
        }
        return ledger;
    }

    public SoulAffinity highest(java.util.random.RandomGenerator random) {
        int highest = 0;
        for (int value : scores.values()) highest = Math.max(highest, value);
        if (highest <= 0) return null;
        final int maxScore = highest;
        SoulAffinity[] candidates = scores.entrySet().stream()
                .filter(entry -> entry.getValue() == maxScore)
                .map(Map.Entry::getKey)
                .toArray(SoulAffinity[]::new);
        return candidates[random.nextInt(candidates.length)];
    }
}
