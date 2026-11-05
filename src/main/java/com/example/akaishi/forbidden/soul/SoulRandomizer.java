package com.example.akaishi.forbidden.soul;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;

/** Server-side random soul generation for data-less fragments and crystals. */
public final class SoulRandomizer {
    private SoulRandomizer() {
    }

    public static SoulCrystalData.Data randomData(RandomSource random) {
        int alignment = random.nextInt(15) - 7;
        SoulAffinity affinity = SoulAffinity.values()[random.nextInt(SoulAffinity.values().length)];
        SoulScoreLedger scores = new SoulScoreLedger();
        scores.set(affinity, Math.max(1, Math.abs(alignment)));
        return new SoulCrystalData.Data(alignment, affinity, scores, false, true);
    }

    public static void randomize(CompoundTag stackTag, RandomSource random) {
        SoulCrystalData.write(stackTag, randomData(random));
    }
}
