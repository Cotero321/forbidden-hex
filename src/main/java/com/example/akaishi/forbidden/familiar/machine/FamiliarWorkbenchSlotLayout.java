package com.example.akaishi.forbidden.familiar.machine;

import java.util.Arrays;

/** Fixed input/output contract for the familiar workbench menu. */
public final class FamiliarWorkbenchSlotLayout {
    public static final int SOUL_SLOT = 0;
    public static final int TEMPLATE_SLOT = 1;
    public static final int FIRST_MATERIAL_SLOT = 2;
    public static final int MATERIAL_SLOT_COUNT = 5;
    public static final int LAST_MATERIAL_SLOT = FIRST_MATERIAL_SLOT + MATERIAL_SLOT_COUNT - 1;
    public static final int RESULT_SLOT = 7;
    public static final int INPUT_SLOT_COUNT = 7;
    public static final int MACHINE_SLOT_COUNT = 8;

    private FamiliarWorkbenchSlotLayout() {
    }

    public static boolean isMaterialSlot(int slot) {
        return slot >= FIRST_MATERIAL_SLOT && slot <= LAST_MATERIAL_SLOT;
    }

    public static boolean isInputSlot(int slot) {
        return slot >= SOUL_SLOT && slot <= LAST_MATERIAL_SLOT;
    }

    public static int[] materialSlots() {
        int[] slots = new int[MATERIAL_SLOT_COUNT];
        for (int i = 0; i < slots.length; i++) slots[i] = FIRST_MATERIAL_SLOT + i;
        return slots;
    }

    public static boolean isValidInputLayout() {
        return Arrays.equals(materialSlots(), new int[]{2, 3, 4, 5, 6});
    }
}
