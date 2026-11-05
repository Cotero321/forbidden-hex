package com.example.akaishi.forbidden.transmutation;

/** Immutable timing and energy rules for the life furnace. */
public final class LifeFurnaceRules {
    public static final int STRUCTURE_WIDTH = 4;
    public static final int STRUCTURE_DEPTH = 4;
    public static final int STRUCTURE_HEIGHT = 6;
    public static final int DAMAGE_INTERVAL_TICKS = 10;
    public static final float DAMAGE_PER_INTERVAL = 1.0F;
    public static final int ENERGY_PER_DAMAGE = 5;
    public static final int HEALTH_PER_BIOMASS = 5;
    /** NBT key on biomass stacks identifying the entity type that supplied the biomass. */
    public static final String BIOMASS_ENTITY_TYPE_TAG = "EntityType";

    private LifeFurnaceRules() {
    }

    public static boolean shouldDamage(long gameTime) {
        return gameTime >= 0L && gameTime % DAMAGE_INTERVAL_TICKS == 0L;
    }

    public static boolean acceptsEntity(boolean isFamiliar, boolean isBoss) {
        return !isFamiliar && !isBoss;
    }
}
