package com.example.akaishi.forbidden.boss.chimera;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import java.util.UUID;

public final class ChimeraHeadHitbox extends Entity {
    private static final EntityDataAccessor<Integer> HEAD = SynchedEntityData.defineId(ChimeraHeadHitbox.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ACTIVE = SynchedEntityData.defineId(ChimeraHeadHitbox.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> OWNER_ENTITY = SynchedEntityData.defineId(ChimeraHeadHitbox.class, EntityDataSerializers.INT);
    private UUID ownerId;
    public ChimeraHeadHitbox(EntityType<? extends ChimeraHeadHitbox> type, Level level) { super(type, level); noPhysics = true; }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return new ClientboundAddEntityPacket(this); }

    @Override public boolean isPushable() { return false; }

    @Override protected void defineSynchedData() { entityData.define(HEAD, 0); entityData.define(ACTIVE, true); entityData.define(OWNER_ENTITY, -1); }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { if (tag.hasUUID("Owner")) ownerId = tag.getUUID("Owner"); entityData.set(HEAD, tag.getInt("Head")); entityData.set(ACTIVE, tag.getBoolean("Active")); }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { if (ownerId != null) tag.putUUID("Owner", ownerId); tag.putInt("Head", headIndex()); tag.putBoolean("Active", active()); }
    @Override public void tick() {
        super.tick();
        Entity owner = level() instanceof ServerLevel serverLevel && ownerId != null
                ? serverLevel.getEntity(ownerId) : level().getEntity(entityData.get(OWNER_ENTITY));
        if (!(owner instanceof ChimeraEntity chimera) || !chimera.isAlive()) {
            if (!level().isClientSide && tickCount > 100) discard();
            return;
        }
        if (!level().isClientSide) entityData.set(OWNER_ENTITY, chimera.getId());
        setPos(chimera.headPosition(ChimeraHead.values()[headIndex()]));
        setBoundingBox(new AABB(position().subtract(0.65D, 0.55D, 0.65D), position().add(0.65D, 0.55D, 0.65D)));
        if (!level().isClientSide) entityData.set(ACTIVE, !chimera.isHeadDisabled(headIndex()));
    }
    @Override public boolean isPickable() { return true; }

    @Override public boolean hurt(DamageSource source, float amount) {
        if (!(level() instanceof ServerLevel serverLevel) || ownerId == null) return false;
        Entity owner = serverLevel.getEntity(ownerId);
        return owner instanceof ChimeraEntity chimera && chimera.hurtHead(ChimeraHead.values()[headIndex()], source, amount);
    }
    public void bind(ChimeraEntity owner, ChimeraHead head) { ownerId = owner.getUUID(); entityData.set(OWNER_ENTITY, owner.getId()); entityData.set(HEAD, head.index()); setPos(owner.headPosition(head)); }
    public int headIndex() { return Math.max(0, Math.min(2, entityData.get(HEAD))); }
    public boolean active() { return entityData.get(ACTIVE); }
    public UUID ownerId() { return ownerId; }
}
