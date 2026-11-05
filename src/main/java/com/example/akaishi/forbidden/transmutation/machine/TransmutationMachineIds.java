package com.example.akaishi.forbidden.transmutation.machine;

/** Stable ids for the six planned machines; registration is intentionally separate. */
public enum TransmutationMachineIds {
    LIFE_FURNACE("life_furnace"),
    SOUL_REPLICATION("soul_replication"),
    SOURCE_TRANSMUTATION_FURNACE("source_transmutation_furnace"),
    SOUL_CULTIVATION_CHAMBER("soul_cultivation_chamber"),
    SOUL_INFUSION_TABLE("soul_infusion_table"),
    FAMILIAR_WORKBENCH("familiar_workbench");

    private final String id;

    TransmutationMachineIds(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
