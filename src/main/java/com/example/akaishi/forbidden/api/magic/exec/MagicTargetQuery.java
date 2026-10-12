package com.example.akaishi.forbidden.api.magic.exec;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** 每次目标解析的参数；极性在截断目标数前过滤。 */
public record MagicTargetQuery(Entity caster, Vec3 origin, MagicShape shape, float radius, int limit,
        boolean requireLineOfSight, boolean includeHidden, boolean allowPvp, boolean alliesOnly, boolean enemiesOnly) {
    public static final int DEFAULT_LIMIT = 8;

    public MagicTargetQuery(Entity caster, Vec3 origin, MagicShape shape, float radius, int limit,
            boolean requireLineOfSight, boolean includeHidden, boolean allowPvp) {
        this(caster, origin, shape, radius, limit, requireLineOfSight, includeHidden, allowPvp, false, false);
    }
    public MagicTargetQuery(Entity caster, Vec3 origin, MagicShape shape, float radius, int limit,
            boolean requireLineOfSight, boolean includeHidden, boolean allowPvp, boolean alliesOnly) {
        this(caster, origin, shape, radius, limit, requireLineOfSight, includeHidden, allowPvp, alliesOnly, false);
    }
    public MagicTargetQuery forAllies() {
        return new MagicTargetQuery(caster, origin, shape, radius, limit, requireLineOfSight, includeHidden, allowPvp, true, false);
    }
    public MagicTargetQuery forEnemies() {
        return new MagicTargetQuery(caster, origin, shape, radius, limit, requireLineOfSight, includeHidden, allowPvp, false, true);
    }
    public static MagicTargetQuery of(Entity caster, Vec3 origin, MagicShape shape) {
        return new MagicTargetQuery(caster, origin, shape, 0f, DEFAULT_LIMIT, true, false, true);
    }
    public MagicTargetQuery withRadius(float value) {
        return new MagicTargetQuery(caster, origin, shape, Math.max(0f, value), limit,
                requireLineOfSight, includeHidden, allowPvp, alliesOnly, enemiesOnly);
    }
    public MagicTargetQuery withOrigin(Vec3 value) {
        return new MagicTargetQuery(caster, value, shape, radius, limit,
                requireLineOfSight, includeHidden, allowPvp, alliesOnly, enemiesOnly);
    }
    public MagicTargetQuery ignoringLineOfSight() {
        return new MagicTargetQuery(caster, origin, shape, radius, limit, false, includeHidden, allowPvp, alliesOnly, enemiesOnly);
    }
    public MagicTargetQuery includingHidden() {
        return new MagicTargetQuery(caster, origin, shape, radius, limit,
                requireLineOfSight, true, allowPvp, alliesOnly, enemiesOnly);
    }
    public int effectiveLimit() { return limit <= 0 ? DEFAULT_LIMIT : limit; }
}
