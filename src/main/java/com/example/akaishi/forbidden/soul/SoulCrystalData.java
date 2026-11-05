package com.example.akaishi.forbidden.soul;

import net.minecraft.nbt.CompoundTag;

import java.util.Optional;

/** NBT contract for soul fragments, random crystals and neutralized crystals. */
public final class SoulCrystalData {
    public static final int DATA_VERSION = 1;
    public static final String ROOT = "ForbiddenSoul";
    public static final String ALIGNMENT = "alignment";
    public static final String AFFINITY = "affinity";
    public static final String NEUTRALIZED = "neutralized";
    public static final String RANDOMIZED = "randomized";
    public static final String VERSION = "version";

    private SoulCrystalData() {
    }

    public record Data(int alignment, SoulAffinity affinity, SoulScoreLedger scores,
                       boolean neutralized, boolean randomized) {
        public Data {
            alignment = clampAlignment(alignment);
            scores = scores == null ? new SoulScoreLedger() : scores;
        }

        public boolean hasAffinity() {
            return affinity != null;
        }
    }

    public static Data read(CompoundTag stackTag) {
        if (!stackTag.contains(ROOT, CompoundTag.TAG_COMPOUND)) {
            return new Data(0, null, new SoulScoreLedger(), false, false);
        }
        CompoundTag root = stackTag.getCompound(ROOT);
        SoulAffinity affinity = SoulAffinity.byId(root.getString(AFFINITY)).orElse(null);
        int alignment = root.contains(ALIGNMENT, CompoundTag.TAG_INT) ? root.getInt(ALIGNMENT) : 0;
        return new Data(alignment, affinity, SoulScoreLedger.load(root),
                root.getBoolean(NEUTRALIZED), root.getBoolean(RANDOMIZED));
    }

    public static void write(CompoundTag stackTag, Data data) {
        CompoundTag root = new CompoundTag();
        root.putInt(VERSION, DATA_VERSION);
        root.putInt(ALIGNMENT, clampAlignment(data.alignment()));
        if (data.affinity() != null) root.putString(AFFINITY, data.affinity().id());
        root.putBoolean(NEUTRALIZED, data.neutralized());
        root.putBoolean(RANDOMIZED, data.randomized());
        data.scores().save(root);
        stackTag.put(ROOT, root);
    }

    public static void clearToNeutral(CompoundTag stackTag) {
        write(stackTag, new Data(0, null, new SoulScoreLedger(), true, false));
    }

    public static boolean canRewrite(CompoundTag stackTag) {
        return read(stackTag).neutralized();
    }

    public static Optional<SoulAffinity> affinity(CompoundTag stackTag) {
        return Optional.ofNullable(read(stackTag).affinity());
    }

    public static int clampAlignment(int alignment) {
        return Math.max(-7, Math.min(7, alignment));
    }
}
