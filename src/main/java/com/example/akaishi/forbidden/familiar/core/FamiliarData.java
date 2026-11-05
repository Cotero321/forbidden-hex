package com.example.akaishi.forbidden.familiar.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Persistent state shared by a released familiar and its contract item. */
public final class FamiliarData {
    public static final int DATA_VERSION = 1;
    public static final int MAX_ALIGNMENT = 7;
    public static final int MAX_HUNGER = 20;
    public static final int MAX_MOOD = 100;

    private UUID owner;
    private UUID familiarId = UUID.randomUUID();
    private FamiliarTemplateId template = FamiliarTemplateId.NONE;
    private int level;
    private long experience;
    private int alignment;
    private String affinity = "";
    private CompoundTag soulScores = new CompoundTag();
    private int hunger = MAX_HUNGER;
    private float saturation;
    private int mood = MAX_MOOD;
    private FamiliarMode mode = FamiliarMode.FOLLOW;
    private int sourceEnergy;
    private int sourceEnergyCapacity;
    private float health;
    private boolean active;
    private boolean deathPending;
    private UUID activeEntity;
    private List<String> materials = new ArrayList<>();
    private int materialBiomassCost;
    private ItemStack equipment = ItemStack.EMPTY;
    private ItemStack accessory = ItemStack.EMPTY;

    public FamiliarData() {
    }

    public FamiliarData copy() {
        FamiliarData copy = new FamiliarData();
        copy.owner = owner;
        copy.familiarId = familiarId;
        copy.template = template;
        copy.level = level;
        copy.experience = experience;
        copy.alignment = alignment;
        copy.affinity = affinity;
        copy.soulScores = soulScores.copy();
        copy.hunger = hunger;
        copy.saturation = saturation;
        copy.mood = mood;
        copy.mode = mode;
        copy.sourceEnergy = sourceEnergy;
        copy.sourceEnergyCapacity = sourceEnergyCapacity;
        copy.health = health;
        copy.active = active;
        copy.deathPending = deathPending;
        copy.activeEntity = activeEntity;
        copy.materials = new ArrayList<>(materials);
        copy.materialBiomassCost = materialBiomassCost;
        copy.equipment = equipment.copy();
        copy.accessory = accessory.copy();
        return copy;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("DataVersion", DATA_VERSION);
        if (owner != null) tag.putUUID("Owner", owner);
        if (familiarId != null) tag.putUUID("FamiliarId", familiarId);
        tag.putString("Template", template.id());
        tag.putInt("Level", level);
        tag.putLong("Experience", experience);
        tag.putInt("Alignment", alignment);
        if (!affinity.isBlank()) tag.putString("Affinity", affinity);
        if (!soulScores.isEmpty()) tag.put("SoulScores", soulScores.copy());
        tag.putInt("Hunger", hunger);
        tag.putFloat("Saturation", saturation);
        tag.putInt("Mood", mood);
        tag.putString("Mode", mode.id());
        tag.putInt("SourceEnergy", sourceEnergy);
        tag.putInt("SourceEnergyCapacity", sourceEnergyCapacity);
        tag.putFloat("Health", health);
        tag.putBoolean("Active", active);
        tag.putBoolean("DeathPending", deathPending);
        if (activeEntity != null) tag.putUUID("ActiveEntity", activeEntity);
        if (!materials.isEmpty()) tag.putString("Materials", String.join("\u0000", materials));
        tag.putInt("MaterialBiomassCost", materialBiomassCost);
        if (!equipment.isEmpty()) tag.put("Equipment", equipment.save(new CompoundTag()));
        if (!accessory.isEmpty()) tag.put("Accessory", accessory.save(new CompoundTag()));
        return tag;
    }

    public static FamiliarData load(CompoundTag tag) {
        FamiliarData data = new FamiliarData();
        if (tag == null) return data;
        if (tag.hasUUID("Owner")) data.owner = tag.getUUID("Owner");
        if (tag.hasUUID("FamiliarId")) data.familiarId = tag.getUUID("FamiliarId");
        data.template = FamiliarTemplateId.fromId(tag.getString("Template"));
        data.level = Math.max(0, tag.getInt("Level"));
        data.experience = Math.max(0L, tag.getLong("Experience"));
        data.alignment = clampAlignment(tag.getInt("Alignment"));
        data.affinity = tag.getString("Affinity");
        if (tag.contains("SoulScores", Tag.TAG_COMPOUND)) data.soulScores = tag.getCompound("SoulScores").copy();
        data.hunger = clamp(tag.getInt("Hunger"), 0, MAX_HUNGER);
        data.saturation = Math.max(0.0F, tag.getFloat("Saturation"));
        data.mood = clamp(tag.getInt("Mood"), 0, MAX_MOOD);
        data.mode = FamiliarMode.fromId(tag.getString("Mode"));
        data.sourceEnergy = Math.max(0, tag.getInt("SourceEnergy"));
        data.sourceEnergyCapacity = Math.max(0, tag.getInt("SourceEnergyCapacity"));
        data.health = Math.max(0.0F, tag.getFloat("Health"));
        data.active = tag.getBoolean("Active");
        data.deathPending = tag.getBoolean("DeathPending");
        if (tag.hasUUID("ActiveEntity")) data.activeEntity = tag.getUUID("ActiveEntity");
        if (tag.contains("Materials", Tag.TAG_STRING)) {
            String raw = tag.getString("Materials");
            if (!raw.isBlank()) {
                for (String material : raw.split("\\u0000", -1)) {
                    if (!material.isBlank()) data.materials.add(material);
                }
            }
        }
        data.materialBiomassCost = Math.max(0, tag.getInt("MaterialBiomassCost"));
        if (tag.contains("Equipment", Tag.TAG_COMPOUND)) {
            data.equipment = ItemStack.of(tag.getCompound("Equipment"));
        }
        if (tag.contains("Accessory", Tag.TAG_COMPOUND)) {
            data.accessory = ItemStack.of(tag.getCompound("Accessory"));
        }
        return data;
    }

    private static int clampAlignment(int value) {
        return clamp(value, -MAX_ALIGNMENT, MAX_ALIGNMENT);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public UUID owner() { return owner; }
    public void setOwner(UUID owner) { this.owner = owner; }
    public UUID familiarId() { return familiarId; }
    public void setFamiliarId(UUID familiarId) { this.familiarId = familiarId == null ? UUID.randomUUID() : familiarId; }
    public boolean isOwnedBy(UUID uuid) { return owner != null && owner.equals(uuid); }
    public FamiliarTemplateId template() { return template; }
    public void setTemplate(FamiliarTemplateId template) { this.template = template == null ? FamiliarTemplateId.NONE : template; }
    public int level() { return level; }
    public void setLevel(int level) { this.level = Math.max(0, level); }
    public long experience() { return experience; }
    public void setExperience(long experience) { this.experience = Math.max(0L, experience); }
    public int alignment() { return alignment; }
    public void setAlignment(int alignment) { this.alignment = clampAlignment(alignment); }
    public String affinity() { return affinity; }
    public void setAffinity(String affinity) { this.affinity = affinity == null ? "" : affinity; }
    public CompoundTag soulScores() { return soulScores.copy(); }
    public void setSoulScores(CompoundTag soulScores) { this.soulScores = soulScores == null ? new CompoundTag() : soulScores.copy(); }
    public int hunger() { return hunger; }
    public void setHunger(int hunger) { this.hunger = clamp(hunger, 0, MAX_HUNGER); }
    public float saturation() { return saturation; }
    public void setSaturation(float saturation) { this.saturation = Math.max(0.0F, saturation); }
    public int mood() { return mood; }
    public void setMood(int mood) { this.mood = clamp(mood, 0, MAX_MOOD); }
    public FamiliarMode mode() { return mode; }
    public void setMode(FamiliarMode mode) { this.mode = mode == null ? FamiliarMode.FOLLOW : mode; }
    public int sourceEnergy() { return sourceEnergy; }
    public void setSourceEnergy(int sourceEnergy) { this.sourceEnergy = Math.max(0, sourceEnergy); }
    public int sourceEnergyCapacity() { return sourceEnergyCapacity; }
    public void setSourceEnergyCapacity(int sourceEnergyCapacity) { this.sourceEnergyCapacity = Math.max(0, sourceEnergyCapacity); }
    public float health() { return health; }
    public void setHealth(float health) { this.health = Math.max(0.0F, health); }
    public boolean active() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public boolean deathPending() { return deathPending; }
    public void setDeathPending(boolean deathPending) { this.deathPending = deathPending; }
    public UUID activeEntity() { return activeEntity; }
    public void setActiveEntity(UUID activeEntity) { this.activeEntity = activeEntity; }
    public List<String> materials() { return Collections.unmodifiableList(materials); }
    public void setMaterials(List<String> materials) {
        this.materials = materials == null ? new ArrayList<>() : new ArrayList<>(materials);
    }
    public int materialBiomassCost() { return materialBiomassCost; }
    public void setMaterialBiomassCost(int materialBiomassCost) { this.materialBiomassCost = Math.max(0, materialBiomassCost); }
    public ItemStack equipment() { return equipment.copy(); }
    public void setEquipment(ItemStack equipment) { this.equipment = equipment == null ? ItemStack.EMPTY : equipment.copy(); }
    public ItemStack accessory() { return accessory.copy(); }
    public void setAccessory(ItemStack accessory) { this.accessory = accessory == null ? ItemStack.EMPTY : accessory.copy(); }
}
