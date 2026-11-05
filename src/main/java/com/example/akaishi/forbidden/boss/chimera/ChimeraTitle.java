package com.example.akaishi.forbidden.boss.chimera;

import java.util.concurrent.ThreadLocalRandom;

/** The fourteen title states used by Chimera's ten-second rotation. */
public enum ChimeraTitle {
    PRIDE(false), ENVY(false), WRATH(false), SLOTH(false), GREED(false), GLUTTONY(false), LUST(false),
    HUMILITY(true), CHARITY(true), DILIGENCE(true), TEMPERANCE(true), CHASTITY(true), PATIENCE(true), KINDNESS(true);
    private final boolean virtue;
    ChimeraTitle(boolean virtue) { this.virtue = virtue; }
    public boolean isVirtue() { return virtue; }
    public static ChimeraTitle random() { ChimeraTitle[] values = values(); return values[ThreadLocalRandom.current().nextInt(values.length)]; }
}