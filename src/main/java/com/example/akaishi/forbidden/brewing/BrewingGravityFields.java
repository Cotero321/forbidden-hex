package com.example.akaishi.forbidden.brewing;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class BrewingGravityFields extends SavedData {
    private record Field(Vec3 origin, UUID owner, boolean playerOwner, int strength, long expires) { }
    private final List<Field> fields = new ArrayList<>();
    public static BrewingGravityFields get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(BrewingGravityFields::load, BrewingGravityFields::new, "forbidden_hex_alchemy_gravity");
    }
    public void add(ServerLevel level, Vec3 origin, Entity owner, int strength, int ticks) {
        if (fields.size() >= 128) return;
        fields.add(new Field(origin, owner == null ? null : owner.getUUID(), owner instanceof Player,
                Math.max(1, Math.min(5, strength)), level.getGameTime() + ticks));
        setDirty();
    }
    public void tick(ServerLevel level) {
        long now = level.getGameTime();
        if (fields.removeIf(field -> field.expires <= now)) setDirty();
        if (now % 5 != 0) return;
        for (Field field : fields) {
            Entity owner = field.owner == null ? null : level.getEntity(field.owner);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(field.origin, field.origin).inflate(3))) {
                if (!target.isAlive() || target.isSpectator() || !target.isAffectedByPotions() || target.distanceToSqr(field.origin) > 9) continue;
                if (field.playerOwner && owner == null && target instanceof Player) continue;
                if (BrewingWorldEffects.protectedPlayer(owner, target)) continue;
                int ticks = (int) Math.min(10, field.expires - now);
                BrewingStatusEffects.apply(BrewingEffectType.GRAVITY_FIELD, target, owner, owner, field.strength, ticks,
                        BrewingEffectType.GRAVITY_FIELD.color(), false, false, 1);
            }
        }
    }
    public static BrewingGravityFields load(CompoundTag tag) {
        BrewingGravityFields result = new BrewingGravityFields();
        ListTag list = tag.getList("Fields", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(128, list.size()); i++) {
            CompoundTag field = list.getCompound(i);
            Vec3 origin = new Vec3(field.getDouble("X"), field.getDouble("Y"), field.getDouble("Z"));
            if (!Double.isFinite(origin.x) || !Double.isFinite(origin.y) || !Double.isFinite(origin.z)) continue;
            result.fields.add(new Field(origin, field.hasUUID("Owner") ? field.getUUID("Owner") : null, field.getBoolean("Player"),
                    Math.max(1, Math.min(5, field.getInt("Strength"))), field.getLong("Expires")));
        }
        return result;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Field field : fields) {
            CompoundTag entry = new CompoundTag(); entry.putDouble("X", field.origin.x); entry.putDouble("Y", field.origin.y); entry.putDouble("Z", field.origin.z);
            if (field.owner != null) entry.putUUID("Owner", field.owner);
            entry.putBoolean("Player", field.playerOwner); entry.putInt("Strength", field.strength); entry.putLong("Expires", field.expires); list.add(entry);
        }
        tag.put("Fields", list); return tag;
    }
}
