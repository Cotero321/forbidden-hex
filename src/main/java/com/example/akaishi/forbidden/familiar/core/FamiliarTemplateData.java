package com.example.akaishi.forbidden.familiar.core;

import java.util.EnumMap;
import java.util.Map;

/**
 * Initial template table used by the common entity layer. Values mirror the confirmed design
 * sheet; the qualitative speed bands are centralized here so a future JSON data loader can replace
 * this table without changing entity code.
 */
public final class FamiliarTemplateData {
    private static final Map<FamiliarTemplateId, FamiliarStats> BASE = new EnumMap<>(FamiliarTemplateId.class);

    static {
        BASE.put(FamiliarTemplateId.WOLF, stats(2.0D, 0.10D, 2.0D, 3.0D, 0.30D, 100));
        BASE.put(FamiliarTemplateId.CAT, stats(1.0D, 0.05D, 1.0D, 5.0D, 0.35D, 50));
        BASE.put(FamiliarTemplateId.BAT, stats(1.0D, 0.20D, 0.0D, 2.0D, 0.40D, 200));
        BASE.put(FamiliarTemplateId.FROG, stats(2.0D, 0.10D, 3.0D, 1.0D, 0.25D, 100));
        BASE.put(FamiliarTemplateId.PARROT, stats(0.0D, 0.50D, 0.0D, 0.5D, 0.25D, 400));
        BASE.put(FamiliarTemplateId.DRAGON, stats(3.0D, 0.30D, 4.0D, 0.5D, 0.15D, 300));
    }

    private FamiliarTemplateData() {
    }

    private static FamiliarStats stats(double attack, double spellPower, double armor,
                                       double attackSpeed, double moveSpeed, int sourceEnergyCapacity) {
        return new FamiliarStats(20.0D, attack, spellPower, armor, attackSpeed, moveSpeed, sourceEnergyCapacity);
    }

    public static FamiliarStats baseStats(FamiliarTemplateId template) {
        return BASE.getOrDefault(template, new FamiliarStats(20.0D, 0.0D, 0.0D, 0.0D, 4.0D, 0.25D, 0));
    }

    public static FamiliarStats statsAtLevel(FamiliarTemplateId template, int level) {
        return baseStats(template).scaledForLevel(level);
    }
}
