package com.example.akaishi.forbidden.transmutation.machine;

/** Server-side lifecycle states for the 4x4x6 life furnace. */
public enum LifeFurnaceProcessingState {
    IDLE(false),
    WAITING_ENTITY(false),
    WAITING_ENERGY(false),
    PROCESSING(true),
    OUTPUT_BLOCKED(false),
    COMPLETE(false),
    PAUSED(false);

    private final boolean damaging;

    LifeFurnaceProcessingState(boolean damaging) {
        this.damaging = damaging;
    }

    public boolean isDamaging() {
        return damaging;
    }
}
