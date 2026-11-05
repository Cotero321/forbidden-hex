package com.example.akaishi.forbidden.transmutation;

import net.minecraft.nbt.CompoundTag;

/** Accumulates real damage and converts complete five-health units into biomass. */
public final class BiomassLedger {
    private static final String DAMAGE_REMAINDER = "damage_remainder";
    private float damageRemainder;

    public int recordDamage(float realDamage) {
        if (!(realDamage > 0.0F)) return 0;
        damageRemainder += realDamage;
        int biomass = (int) (damageRemainder / LifeFurnaceRules.HEALTH_PER_BIOMASS);
        damageRemainder -= biomass * LifeFurnaceRules.HEALTH_PER_BIOMASS;
        return biomass;
    }

    public float damageRemainder() {
        return damageRemainder;
    }

    public void save(CompoundTag tag) {
        tag.putFloat(DAMAGE_REMAINDER, damageRemainder);
    }

    public void load(CompoundTag tag) {
        damageRemainder = Math.max(0.0F, tag.getFloat(DAMAGE_REMAINDER));
    }
}
