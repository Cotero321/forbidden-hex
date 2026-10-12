package com.example.akaishi.forbidden.menu;

public final class CoreLayout {
    public static final int CENTER_X = 190;
    public static final int CENTER_Y = 76;
    public static final int SLOT_RADIUS = 33;
    public static final int CORE_DISPLAY_RADIUS = 34;
    /** 槽位命中半宽：16×16 命中框。 */
    public static final int SLOT_SIZE = 8;
    public static final int CENTER_HIT_RADIUS = 24;
    private static final int[] OPEN_SLOTS = {1, 2, 3, 5, 7, 8};

    private CoreLayout() {}

    public static int slotX(int slot) {
        return CENTER_X + (int) Math.round(Math.cos(slot * Math.PI / 4 + Math.PI / 8) * SLOT_RADIUS);
    }

    public static int slotY(int slot) {
        return CENTER_Y + (int) Math.round(Math.sin(slot * Math.PI / 4 + Math.PI / 8) * SLOT_RADIUS);
    }

    public static boolean hitSlot(double x, double y, int slot) {
        if (slot < 0 || slot >= 8) return false;
        return Math.abs(x - slotX(slot)) <= SLOT_SIZE && Math.abs(y - slotY(slot)) <= SLOT_SIZE;
    }

    public static boolean hitCenter(double x, double y) {
        return Math.hypot(x - CENTER_X, y - CENTER_Y) <= CENTER_HIT_RADIUS;
    }

    public static int openSlotCount(int level) {
        int safeLevel = Math.max(0, Math.min(level, OPEN_SLOTS.length));
        return safeLevel == 0 ? 0 : OPEN_SLOTS[safeLevel - 1];
    }

    public static boolean isSlotOpen(int slot, int level) {
        return slot >= 0 && slot < openSlotCount(level);
    }
}
