package com.example.akaishi.forbidden.transmutation.machine;

import java.util.Objects;

/** Common immutable processing metadata used by all six machine implementations. */
public record MachineProcessingContract(String machineId, MachineEnergyCost energyCost, int inputSlots,
                                       int outputSlots, boolean allowsAutomation) {
    public MachineProcessingContract {
        if (machineId == null || machineId.isBlank()) throw new IllegalArgumentException("machineId");
        Objects.requireNonNull(energyCost, "energyCost");
        if (inputSlots < 0 || outputSlots < 0) throw new IllegalArgumentException("slot count");
    }
}
