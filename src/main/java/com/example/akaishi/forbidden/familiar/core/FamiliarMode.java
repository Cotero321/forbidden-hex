package com.example.akaishi.forbidden.familiar.core;

import java.util.Locale;

/** Server-authoritative behavior mode selected from the familiar panel. */
public enum FamiliarMode {
    FOLLOW("follow"),
    STAY("stay"),
    GUARD("guard"),
    AGGRESSIVE("aggressive"),
    PASSIVE("passive");

    private final String id;

    FamiliarMode(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "familiar.forbidden_hex.mode." + id;
    }

    public static FamiliarMode fromId(String raw) {
        if (raw == null || raw.isBlank()) return FOLLOW;
        String normalized = raw.toLowerCase(Locale.ROOT);
        for (FamiliarMode value : values()) {
            if (value.id.equals(normalized)) return value;
        }
        return FOLLOW;
    }
}
