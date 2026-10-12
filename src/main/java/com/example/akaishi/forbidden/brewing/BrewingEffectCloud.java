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

    public BrewingEffectCloud(EntityType<? extends BrewingEffectCloud> type, Level level) { super(type, level); }

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
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("HideBrewingParticles", hideParticles());
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setHideParticles(tag.getBoolean("HideBrewingParticles"));
    }
}
