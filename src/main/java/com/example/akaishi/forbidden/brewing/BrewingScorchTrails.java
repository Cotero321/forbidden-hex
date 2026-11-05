package com.example.akaishi.forbidden.brewing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class BrewingScorchTrails {
    private static final int MAX_PATCHES = 4096;
    private static final Map<ServerLevel, LinkedHashMap<BlockPos, Patch>> TRAILS = new WeakHashMap<>();
    private record Patch(UUID owner, long expires) { }

    private BrewingScorchTrails() { }

    public static void emit(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || !entity.onGround() || entity.isInWaterOrBubble()) return;
        BlockPos pos = entity.blockPosition();
        if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) return;
        LinkedHashMap<BlockPos, Patch> patches = TRAILS.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
        if (patches.size() >= MAX_PATCHES && !patches.containsKey(pos)) return;
        patches.put(pos.immutable(), new Patch(entity.getUUID(), level.getGameTime() + 60));
    }

    public static void tick(ServerLevel level) {
        var patches = TRAILS.get(level);
        if (patches == null) return;
        long now = level.getGameTime();
        int particles = 0;
        Iterator<Map.Entry<BlockPos, Patch>> iterator = patches.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            BlockPos pos = entry.getKey();
            Patch patch = entry.getValue();
            if (patch.expires <= now || !level.hasChunkAt(pos)) { iterator.remove(); continue; }
            if (now % 10 != 0) continue;
            if (particles++ < 32) level.sendParticles(ParticleTypes.SMALL_FLAME, pos.getX() + .5, pos.getY() + .08,
                    pos.getZ() + .5, 1, .25, .02, .25, 0);
            Entity owner = level.getEntity(patch.owner);
            if (!(owner instanceof LivingEntity living) || !living.isAlive()) continue;
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(pos).inflate(.1, .2, .1), candidate -> candidate.isAlive() && candidate != owner)) {
                if (living.isAlliedTo(target) || target.fireImmune()) continue;
                if (owner instanceof Player player && target instanceof Player other && !player.canHarmPlayer(other)) continue;
                target.setSecondsOnFire(2);
            }
        }
        if (patches.isEmpty()) TRAILS.remove(level);
    }

    public static void clear() { TRAILS.clear(); }
}
