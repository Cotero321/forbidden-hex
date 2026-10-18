package com.example.akaishi.forbidden.forge.client.visual;

import com.example.akaishi.forbidden.forge.client.ForbiddenVisualConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

/** Client-only budgets for decorative particles and distance-based visual detail. */
public final class ClientVisualBudget {
    private static final Quota SPELL = new Quota();
    private static final Quota BREWING = new Quota();

    private ClientVisualBudget() { }

    public static void beginTick(ClientLevel level) {
        if (level == null) clear();
        else {
            SPELL.advance(level, level.getGameTime());
            BREWING.advance(level, level.getGameTime());
        }
    }

    public static boolean reserveSpell(ClientLevel level, int amount) {
        return level != null && level == Minecraft.getInstance().level && !Minecraft.getInstance().isPaused()
                && SPELL.take(level, level.getGameTime(), spellLimit(), amount);
    }

    public static boolean reserveSpell(ClientLevel level, Vec3 position, int amount) {
        return level != null && level == Minecraft.getInstance().level
                && detailScale(level, position) > 0f && reserveSpell(level, amount);
    }

    public static boolean reserveBrewing(ClientLevel level, int amount) {
        return level != null && level == Minecraft.getInstance().level && !Minecraft.getInstance().isPaused()
                && BREWING.take(level, level.getGameTime(),
                        scaledLimit(ForbiddenVisualConfig.PARTICLE_BUDGET.get()), amount);
    }

    public static boolean reserveLocalSpell(net.minecraft.world.level.Level level, double x, double y, double z) {
        return level instanceof ClientLevel client && client == Minecraft.getInstance().level
                && detailScale(client, new Vec3(x, y, z)) > 0f && reserveSpell(client, 1);
    }

    private static final class Quota {
        private Object world;
        private long tick = Long.MIN_VALUE;
        private int used;

        void advance(Object nextWorld, long nextTick) {
            if (world != nextWorld || tick != nextTick) {
                world = nextWorld;
                tick = nextTick;
                used = 0;
            }
        }

        boolean take(Object nextWorld, long nextTick, int limit, int amount) {
            advance(nextWorld, nextTick);
            if (amount < 0 || amount > Math.max(0, limit) - used) return false;
            used += amount;
            return true;
        }

        void clear() {
            world = null;
            tick = Long.MIN_VALUE;
            used = 0;
        }
    }

    /**
     * Returns a multiplier for decorative geometry. The main silhouette remains visible at long range;
     * extra rings, glyphs and filaments are reduced first.
     */
    public static float detailScale(ClientLevel level, Vec3 position) {
        if (level == null || level != Minecraft.getInstance().level || position == null) return 0f;
        return detailScale(position.distanceToSqr(cameraPosition()));
    }

    public static float detailScale(double distanceSquared) {
        if (!Double.isFinite(distanceSquared) || distanceSquared < 0d) return 0f;
        double max = Math.max(16d, ForbiddenVisualConfig.EFFECT_DISTANCE.get());
        if (distanceSquared > max * max) return 0f;
        double detail = Math.max(16d, Math.min(max, ForbiddenVisualConfig.SPELL_DETAIL_DISTANCE.get()));
        ParticleStatus setting = Minecraft.getInstance().options.particles().get();
        if (setting == ParticleStatus.MINIMAL) return 0.5f;
        double far = max * 0.82d;
        if (distanceSquared > far * far) return 0.5f;
        if (distanceSquared > detail * detail || setting == ParticleStatus.DECREASED) return 0.72f;
        return 1f;
    }

    /** Clears counters on logout, dimension change, or client shutdown. */
    public static void clear() {
        SPELL.clear();
        BREWING.clear();
    }

    public static boolean contractCheck() {
        Quota quota = new Quota();
        Object first = new Object();
        Object second = new Object();
        boolean valid = !quota.take(first, 5, 0, 1)
                && quota.take(first, 5, 3, 3) && !quota.take(first, 5, 3, 1)
                && !quota.take(first, 5, 2, 1) && quota.take(first, 5, 4, 1)
                && quota.take(first, 6, 3, 3) && !quota.take(first, 6, 3, 1)
                && quota.take(second, 6, 3, 3) && !quota.take(second, 6, 3, 1)
                && !quota.take(second, 6, 3, -1);
        quota.clear();
        if (!valid || !quota.take(second, 6, 3, 3)) return false;
        for (int limit : new int[] {0, 1, 48, 64, 192}) {
            for (int concurrency : new int[] {1, 8, 32}) {
                quota.clear();
                int emitted = 0;
                for (int effect = 0; effect < concurrency; effect++) {
                    for (int particle = 0; particle < 64; particle++) {
                        if (quota.take(first, 10, limit, 1)) emitted++;
                    }
                }
                if (emitted != Math.min(limit, concurrency * 64)) return false;
                quota.advance(first, 10);
                if (quota.take(first, 10, limit, 1) != (emitted < limit)) return false;
                if (quota.take(first, 11, limit, limit + 1)) return false;
                if (!quota.take(first, 11, limit, limit)) return false;
            }
        }
        return true;
    }

    private static int spellLimit() {
        return scaledLimit(ForbiddenVisualConfig.SPELL_PARTICLE_BUDGET.get());
    }

    private static int scaledLimit(int limit) {
        return switch (Minecraft.getInstance().options.particles().get()) {
            case MINIMAL -> Math.max(0, limit / 4);
            case DECREASED -> Math.max(0, limit / 2);
            default -> Math.max(0, limit);
        };
    }

    private static Vec3 cameraPosition() {
        return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
    }
}
