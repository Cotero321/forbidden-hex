package com.example.akaishi.forbidden.transmutation.machine;

import java.util.Arrays;
import java.util.Optional;

/**
 * Immutable machine energy contract. The configured values are the user-facing source-energy
 * values; the existing SourceEnergyNetwork consumes half-units and conversion is kept at the
 * integration boundary instead of being hidden in this data class.
 */
public record MachineEnergyCost(int displayUnits, int processingTicks) {
    public static final MachineEnergyCost LOW = new MachineEnergyCost(500, 20);
    public static final MachineEnergyCost MEDIUM = new MachineEnergyCost(1000, 40);
    public static final MachineEnergyCost HIGH = new MachineEnergyCost(1500, 60);

    public MachineEnergyCost {
        if (displayUnits <= 0) throw new IllegalArgumentException("displayUnits must be positive");
        if (processingTicks <= 0) throw new IllegalArgumentException("processingTicks must be positive");
    }

    public static Optional<MachineEnergyCost> byDisplayUnits(int units) {
        return Arrays.stream(new MachineEnergyCost[]{LOW, MEDIUM, HIGH})
                .filter(cost -> cost.displayUnits == units).findFirst();
    }

    /** Convert a display unit value only where the SourceEnergyNetwork contract is explicit. */
    public long toSourceHalfUnits(long displayToHalfUnitFactor) {
        if (displayToHalfUnitFactor <= 0) throw new IllegalArgumentException("factor must be positive");
        return Math.multiplyExact(displayUnits, displayToHalfUnitFactor);
    }
}
