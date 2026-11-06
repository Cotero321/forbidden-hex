package com.example.akaishi.forbidden.soul;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/** The fourteen stable soul personalities used by soul crystals and familiars. */
public enum SoulAffinity {
    PRIDE("pride", false),
    ENVY("envy", false),
    WRATH("wrath", false),
    SLOTH("sloth", false),
    GREED("greed", false),
    GLUTTONY("gluttony", false),
    LUST("lust", false),
    HUMILITY("humility", true),
    CHARITY("charity", true),
    DILIGENCE("diligence", true),
    TEMPERANCE("temperance", true),
    CHASTITY("chastity", true),
    PATIENCE("patience", true),
    KINDNESS("kindness", true);

    private final String id;
    private final boolean virtue;

    SoulAffinity(String id, boolean virtue) {
        this.id = id;
        this.virtue = virtue;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "soul.forbidden_hex.affinity." + id;
    }

    public boolean isVirtue() {
        return virtue;
    }

    public boolean isSin() {
        return !virtue;
    }

    public static Optional<SoulAffinity> byId(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        String normalized = value.toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(entry -> entry.id.equals(normalized)).findFirst();
    }

    /** A convenience fallback for callers that do not own a Minecraft RandomSource. */
    public static SoulAffinity random() {
        SoulAffinity[] all = values();
        return all[ThreadLocalRandom.current().nextInt(all.length)];
    }
}
