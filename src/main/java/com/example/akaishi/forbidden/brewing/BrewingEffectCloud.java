package com.example.akaishi.forbidden.brewing;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public class BrewingEffectCloud extends AreaEffectCloud {
    private static final EntityDataAccessor<Boolean> HIDE_PARTICLES =
            SynchedEntityData.defineId(BrewingEffectCloud.class, EntityDataSerializers.BOOLEAN);

    private BrewingPotionData brewingPotion;
    private final java.util.Set<java.util.UUID> exposed = new java.util.HashSet<>();
    private final java.util.Map<java.util.UUID, Integer> nextApplication = new java.util.HashMap<>();

    public BrewingEffectCloud(EntityType<? extends BrewingEffectCloud> type, Level level) { super(type, level); }

    public void setBrewingPotion(BrewingPotionData potion) {
        brewingPotion = potion;
        setFixedColor(potion.color());
    }

    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(HIDE_PARTICLES, false);
    }
    public void setHideParticles(boolean hide) { entityData.set(HIDE_PARTICLES, hide); }
    public boolean hideParticles() { return entityData.get(HIDE_PARTICLES); }
    @Override public void tick() {
        if (level().isClientSide && hideParticles()) {
            tickCount++;
            return;
        }
        super.tick();
        if (level().isClientSide || isRemoved() || brewingPotion == null || isWaiting() || tickCount % 5 != 0) return;
        float radius = getRadius();
        for (net.minecraft.world.entity.LivingEntity target : level().getEntitiesOfClass(
                net.minecraft.world.entity.LivingEntity.class, getBoundingBox())) {
            if (!target.isAlive() || !target.isAffectedByPotions()) continue;
            java.util.UUID id = target.getUUID();
            if (nextApplication.getOrDefault(id, 0) > tickCount) continue;
            double dx = target.getX() - getX(), dz = target.getZ() - getZ();
            if (dx * dx + dz * dz > radius * radius) continue;
            var immunity = BrewingStatusEffects.get(BrewingEffectType.LINGERING_IMMUNITY);
            if (immunity != null && target.hasEffect(immunity)) continue;
            if (exposed.size() >= 1024 && !exposed.contains(id)) continue;
            boolean first = exposed.add(id);
            nextApplication.put(id, tickCount + 20);
            BrewingPotionExecutor.applyEntity(brewingPotion, target, this, getOwner(), 1, first);
            if (first) {
                radius += getRadiusOnUse();
                if (radius < 0.5F) { discard(); return; }
                setRadius(radius);
            }
        }
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("HideBrewingParticles", hideParticles());
        if (brewingPotion != null) tag.put("BrewingPotion", brewingPotion.save());
        net.minecraft.nbt.ListTag entries = new net.minecraft.nbt.ListTag();
        for (java.util.UUID id : exposed) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", id);
            entry.putInt("Next", nextApplication.getOrDefault(id, tickCount));
            entries.add(entry);
        }
        tag.put("BrewingExposed", entries);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setHideParticles(tag.getBoolean("HideBrewingParticles"));
        exposed.clear();
        nextApplication.clear();
        brewingPotion = null;
        if (tag.contains("BrewingPotion")) {
            var potion = BrewingPotionData.read(tag.getCompound("BrewingPotion"));
            if (potion.isEmpty()) { discard(); return; }
            setBrewingPotion(potion.get());
        }
        var entries = tag.getList("BrewingExposed", net.minecraft.nbt.Tag.TAG_COMPOUND);
        if (entries.size() > 1024) { discard(); return; }
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (!entry.hasUUID("Id")) { discard(); return; }
            java.util.UUID id = entry.getUUID("Id");
            exposed.add(id);
            nextApplication.put(id, Math.max(tickCount, Math.min(tickCount + 20, entry.getInt("Next"))));
        }
    }
}
