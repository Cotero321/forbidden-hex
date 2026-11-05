package com.example.akaishi.forbidden.familiar.core;

/**
 * Numeric template data. Values are intentionally data-driven; this class only
 * applies the confirmed level-0 growth rule and does not invent template values.
 */
public record FamiliarStats(
        double maxHealth,
        double attack,
        double spellPower,
        double armor,
        double attackSpeed,
        double moveSpeed,
        int sourceEnergyCapacity
) {
    public FamiliarStats {
        if (!Double.isFinite(maxHealth) || maxHealth < 0) throw new IllegalArgumentException("maxHealth");
        if (!Double.isFinite(attack) || attack < 0) throw new IllegalArgumentException("attack");
        if (!Double.isFinite(spellPower) || spellPower < 0) throw new IllegalArgumentException("spellPower");
        if (!Double.isFinite(armor) || armor < 0) throw new IllegalArgumentException("armor");
        if (!Double.isFinite(attackSpeed) || attackSpeed < 0) throw new IllegalArgumentException("attackSpeed");
        if (!Double.isFinite(moveSpeed) || moveSpeed < 0) throw new IllegalArgumentException("moveSpeed");
        if (sourceEnergyCapacity < 0) throw new IllegalArgumentException("sourceEnergyCapacity");
    }

    /** Level 0 is the unmodified template baseline; material modifiers are applied separately. */
    public FamiliarStats scaledForLevel(int level) {
        int safeLevel = Math.max(0, level);
        double multiplier = 1.0D + (0.15D * safeLevel);
        return new FamiliarStats(
                maxHealth * multiplier,
                attack * multiplier,
                spellPower * multiplier,
                armor * multiplier,
                attackSpeed * multiplier,
                moveSpeed * multiplier,
                (int) Math.floor(sourceEnergyCapacity * multiplier)
        );
    }
}
