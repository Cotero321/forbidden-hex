package com.example.akaishi.forbidden.boss.chimera;

public enum ChimeraHead {
    WOLF(0), DRAGON(1), BIRD(2);
    private final int index;
    ChimeraHead(int index) { this.index = index; }
    public int index() { return index; }
    public int bit() { return 1 << index; }
}