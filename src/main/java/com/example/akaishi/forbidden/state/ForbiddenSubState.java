package com.example.akaishi.forbidden.state;

import net.minecraft.nbt.CompoundTag;

public interface ForbiddenSubState {
    CompoundTag save();

    void load(CompoundTag tag);

    void clearTemporaries();
}
