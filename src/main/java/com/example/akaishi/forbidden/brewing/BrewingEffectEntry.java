package com.example.akaishi.forbidden.brewing;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Optional;

public record BrewingEffectEntry(BrewingEffectType type, int color) {
    public BrewingEffectEntry {
        if (type == null || color < -1 || color > 15 || type == BrewingEffectType.DYE && color < 0) {
            throw new IllegalArgumentException("Invalid custom brewing effect");
        }
    }

    public BrewingEffectEntry(BrewingEffectType type) { this(type, -1); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", type.id().toString());
        tag.putInt("color", color);
        return tag;
    }

    public static Optional<BrewingEffectEntry> read(CompoundTag tag) {
        if (!tag.contains("id", Tag.TAG_STRING) || !tag.contains("color", Tag.TAG_INT)) return Optional.empty();
        try {
            return BrewingEffectType.byId(tag.getString("id")).map(type -> new BrewingEffectEntry(type, tag.getInt("color")));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
