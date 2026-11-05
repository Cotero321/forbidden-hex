package com.example.akaishi.forbidden.transmutation;

import java.util.Arrays;
import java.util.Optional;

/** Three source-energy processing tiers for the source-energy transmutation furnace. */
public enum TransmutationEnergyTiers {
    LOW(500, 20),
    MEDIUM(1000, 40),
    HIGH(1500, 60);

    private final int energy;
    private final int processingTicks;

    TransmutationEnergyTiers(int energy, int processingTicks) {
        this.energy = energy;
        this.processingTicks = processingTicks;
    }

    public int energy() {
        return energy;
    }

    public int processingTicks() {
        return processingTicks;
    }

    public static Optional<TransmutationEnergyTiers> byEnergy(int energy) {
        return Arrays.stream(values()).filter(tier -> tier.energy == energy).findFirst();
    }
}
