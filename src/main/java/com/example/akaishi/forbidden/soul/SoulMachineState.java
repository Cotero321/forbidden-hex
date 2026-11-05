package com.example.akaishi.forbidden.soul;

/** Shared server-side state vocabulary for soul replication, cultivation and infusion. */
public enum SoulMachineState {
    IDLE,
    READY,
    PROCESSING,
    WAITING_ENERGY,
    OUTPUT_BLOCKED,
    COMPLETE,
    INVALID_INPUT,
    PAUSED;

    public boolean isTerminal() {
        return this == COMPLETE || this == OUTPUT_BLOCKED || this == INVALID_INPUT;
    }
}
