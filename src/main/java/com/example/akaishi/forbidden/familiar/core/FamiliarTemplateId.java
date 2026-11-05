package com.example.akaishi.forbidden.familiar.core;

import java.util.Locale;

/** Stable data ids for the six familiar templates. */
public enum FamiliarTemplateId {
    NONE("none"),
    WOLF("wolf"),
    CAT("cat"),
    BAT("bat"),
    FROG("frog"),
    PARROT("parrot"),
    DRAGON("dragon");

    private final String id;

    FamiliarTemplateId(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "familiar.forbidden_hex.template." + id;
    }

    public boolean isConfigured() {
        return this != NONE;
    }

    public static FamiliarTemplateId fromId(String raw) {
        if (raw == null || raw.isBlank()) return NONE;
        String normalized = raw.toLowerCase(Locale.ROOT);
        for (FamiliarTemplateId value : values()) {
            if (value.id.equals(normalized)) return value;
        }
        return NONE;
    }
}
