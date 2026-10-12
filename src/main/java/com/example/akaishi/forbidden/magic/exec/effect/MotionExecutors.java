package com.example.akaishi.forbidden.magic.exec.effect;

import com.example.akaishi.forbidden.api.magic.exec.IPrimitiveExecutor;
import com.example.akaishi.forbidden.api.magic.exec.MagicContext;
import com.example.akaishi.forbidden.api.magic.exec.MagicExecRegistry;
import com.example.akaishi.forbidden.magic.exec.ExecSupport;
import com.example.akaishi.forbidden.magic.table.MagicIds;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** 位移执行以速度改变或安全传送成功为准。 */
public final class MotionExecutors {
    private MotionExecutors() {}
    public static void register() {
        MagicExecRegistry.register(new Move());
        MagicExecRegistry.register(new Teleport());
        MagicExecRegistry.register(new Gather());
    }

    static final class Move implements IPrimitiveExecutor {
        @Override public String effectId() { return MagicIds.Effect.MOVE; }
        @Override public void apply(MagicContext context, Entity target) { applyResult(context, target); }
        @Override public boolean applyResult(MagicContext context, Entity target) {
            if (!(target instanceof LivingEntity living)
                    || !ExecSupport.meetsHighHealthGate(context, effectId(), living)) return false;
            Vec3 from = context.caster() == null ? context.origin() : context.caster().getEyePosition();
            Vec3 horizontal = new Vec3(target.getX() - from.x, 0d, target.getZ() - from.z);
            if (horizontal.lengthSqr() < 1.0e-4d) {
                if (context.caster() == null) return false;
                Vec3 look = context.caster().getLookAngle();
                horizontal = new Vec3(look.x, 0d, look.z);
            }
            Vec3 before = living.getDeltaMovement();
            double speed = Math.min(1.2d, Math.max(0.1d, context.snapshot().magnitude() * 0.12d));
            ExecSupport.shove(living, horizontal.add(0d, 0.35d, 0d), speed);
            return !before.equals(living.getDeltaMovement());
        }
    }

    static final class Teleport implements IPrimitiveExecutor {
        @Override public String effectId() { return MagicIds.Effect.TELEPORT; }
        @Override public void apply(MagicContext context, Entity target) { applyResult(context, target); }
        @Override public boolean applyResult(MagicContext context, Entity target) {
            if (!(target instanceof LivingEntity living)) return false;
            Vec3 direction = context.caster() == null ? new Vec3(0d, 0d, 1d)
                    : context.caster().getLookAngle().normalize();
            Vec3 before = living.position();
            Vec3 to = before.add(direction.scale(Math.max(1d, context.snapshot().magnitude())));
            boolean moved = living.randomTeleport(to.x, to.y, to.z, true);
            if (moved) living.resetFallDistance();
            return moved && !before.equals(living.position());
        }
    }

    static final class Gather implements IPrimitiveExecutor {
        @Override public String effectId() { return MagicIds.Effect.GATHER; }
        @Override public void apply(MagicContext context, Entity target) { applyResult(context, target); }
        @Override public boolean applyResult(MagicContext context, Entity target) {
            if (!(target instanceof LivingEntity living)
                    || !ExecSupport.meetsHighHealthGate(context, effectId(), living)) return false;
            Vec3 to = context.origin().subtract(living.position());
            if (to.lengthSqr() < 0.25d) return false;
            Vec3 before = living.getDeltaMovement();
            ExecSupport.shove(living, new Vec3(to.x, Math.min(to.y, 0d), to.z),
                    Math.max(0.02d, context.snapshot().magnitude()));
            return !before.equals(living.getDeltaMovement());
        }
    }
}
