package com.example.akaishi.forbidden.magic.exec;

import com.example.akaishi.forbidden.api.magic.*;
import com.example.akaishi.forbidden.api.magic.exec.*;
import com.example.akaishi.forbidden.magic.formula.SpellPlanAdapter;
import com.example.akaishi.forbidden.magic.table.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

/** 计划逐操作投送，成功计数和通知共用一个结算入口。 */
public final class MagicPipeline {
    private static final Logger LOGGER = LoggerFactory.getLogger(MagicPipeline.class);
    private MagicPipeline() {}

    public record ExecutionResult(boolean accepted, AtomicInteger successful) {
        public int successfulTargets() { return successful.get(); }
    }

    private record Successes(AtomicInteger count, IntConsumer callback) {
        void add(int amount) {
            if (amount <= 0) return;
            int total = count.addAndGet(amount);
            try {
                callback.accept(total);
            } catch (RuntimeException e) {
                LOGGER.error("[AkaishiMagic] 成功回调异常", e);
            }
        }
    }

    private static MagicContext planned(MagicContext context) {
        if (context == null || context.snapshot() == null) return null;
        if (context.snapshot().plan() != null) return context;
        var compiled = SpellPlans.compile(new SpellInput(context.words(),
                java.util.Collections.nCopies(context.words().size(), context.mainInkId()),
                context.mainInkId(), context.sourceId(), 0f, 0));
        if (!compiled.ok()) return null;
        return new MagicContext(context.caster(), context.level(), context.origin(), context.sourceId(),
                context.mainInkId(), context.inks(), context.words(), context.casterLevel(),
                SpellPlanAdapter.snapshot(compiled.plan()), context.hitEntity(), context.impactDirection());
    }

    public static ExecutionResult castResult(MagicContext context) { return castResult(context, count -> {}); }

    public static ExecutionResult castResult(MagicContext context, IntConsumer onSuccess) {
        var successes = new Successes(new AtomicInteger(), onSuccess == null ? count -> {} : onSuccess);
        context = planned(context);
        if (context == null || context.snapshot().plan().operations().stream()
                .anyMatch(op -> MagicExecRegistry.effect(op.effectId()) == null))
            return new ExecutionResult(false, successes.count());
        var deliverer = MagicExecRegistry.delivery(context.snapshot().deliveryId());
        if (deliverer == null) return new ExecutionResult(false, successes.count());
        try {
            return new ExecutionResult(deliverer.deliver(context, actionFor(context, successes)), successes.count());
        } catch (RuntimeException e) {
            LOGGER.error("[AkaishiMagic] 投送异常", e);
            return new ExecutionResult(false, successes.count());
        }
    }

    public static boolean cast(MagicContext context) { return castResult(context).accepted(); }

    public static MagicDeliveryAction actionFor(MagicContext context) {
        return actionFor(planned(context), new Successes(new AtomicInteger(), count -> {}));
    }

    private static MagicDeliveryAction actionFor(MagicContext context, Successes successes) {
        return new MagicDeliveryAction() {
            @Override public void fire(Vec3 at) { applyToTargets(context, at, successes); }
            @Override public void fire(Entity hit, Vec3 at, Vec3 direction) {
                if (context != null) applyToTargets(context.withImpact(hit, at, direction), at, successes);
            }
        };
    }

    private static void applyToTargets(MagicContext context, Vec3 at, Successes successes) {
        if (context == null) return;
        var plan = context.snapshot().plan();
        for (var operation : plan.operations()) {
            var view = new MagicContext(context.caster(), context.level(), at, context.sourceId(),
                    context.mainInkId(), context.inks(), context.words(), context.casterLevel(),
                    SpellPlanAdapter.snapshot(plan, operation), context.hitEntity(), context.impactDirection());
            applyOperation(view, at, successes, operation.effectId());
        }
    }

    private static void applyOperation(MagicContext context, Vec3 at, Successes successes, String effectId) {
        var executor = MagicExecRegistry.effect(effectId);
        if (executor == null) return;
        MagicShape shape;
        try {
            shape = MagicShape.valueOf(context.snapshot().plan().target().shape());
        } catch (IllegalArgumentException | NullPointerException e) {
            LOGGER.warn("[AkaishiMagic] 无效目标形状，跳过本次效果");
            return;
        }
        if (MagicIds.Delivery.PLACE.equals(context.snapshot().deliveryId())) shape = MagicShape.AREA;
        var resolver = MagicExecRegistry.target(shape);
        if (resolver == null) return;
        var query = MagicTargeting.queryOf(context, allowPvpFor(context, effectId))
                .withOrigin(at).withRadius(context.snapshot().radius());
        var effect = MagicEffectTable.get(effectId);
        if (effect != null && effect.category() == MagicEffectDef.Category.DEFENSE) query = query.forAllies();
        else if (effect != null && (effect.category() == MagicEffectDef.Category.ATTACK
                || MagicIds.Effect.FIELD.equals(effectId))) query = query.forEnemies();
        List<Entity> targets;
        try { targets = resolver.resolve(context, query); }
        catch (RuntimeException e) { LOGGER.error("[AkaishiMagic] 目标解析异常", e); return; }
        if (targets == null || targets.isEmpty()) {
            if (executor.worksWithoutTarget()) runOne(context, executor, null, successes);
            return;
        }
        if (executor.handlesWholeTargetSet()) {
            try { successes.add(executor.applyAllResult(context, List.copyOf(targets))); }
            catch (RuntimeException e) { LOGGER.error("[AkaishiMagic] 批量执行异常", e); }
            return;
        }
        for (Entity target : targets) {
            if (target == null || !target.isAlive()) continue;
            boolean applied = runOne(context, executor, target, successes);
            boolean attached = applied && MagicIds.Delivery.ATTACH.equals(context.snapshot().deliveryId())
                    && (MagicIds.Effect.DAMAGE.equals(effectId) || MagicIds.Effect.FIELD.equals(effectId));
            boolean field = MagicIds.Effect.FIELD.equals(effectId)
                    && !MagicIds.Delivery.PLACE.equals(context.snapshot().deliveryId())
                    && !MagicIds.Delivery.CHANNEL.equals(context.snapshot().deliveryId())
                    && !MagicIds.Delivery.ATTACH.equals(context.snapshot().deliveryId());
            if (attached || field) repeatAttached(context, executor, target, successes,
                    MagicPulseTiming.attachedTicks(context.snapshot().durationSeconds()) - MagicPulseTiming.ATTACHED_INTERVAL);
        }
    }

    private static void repeatAttached(MagicContext context, IPrimitiveExecutor executor, Entity target,
            Successes successes, int remainingTicks) {
        if (remainingTicks <= 0 || !(context.level() instanceof ServerLevel level)) return;
        MagicScheduler.schedule(level, MagicPulseTiming.ATTACHED_INTERVAL, () -> {
            if (!target.isAlive() || target.isRemoved() || target.level() != level) return;
            var query = MagicTargeting.queryOf(context, allowPvpFor(context, executor.effectId()))
                    .ignoringLineOfSight().forEnemies();
            if (!com.example.akaishi.forbidden.magic.exec.resolve.MagicResolvers.valid(context.caster(), target, query, false)) return;
            MagicContext impact = context.withImpact(target, target.position(), context.impactDirection());
            runOne(impact, executor, target, successes);
            repeatAttached(impact, executor, target, successes,
                    remainingTicks - MagicPulseTiming.ATTACHED_INTERVAL);
        });
    }

    private static boolean runOne(MagicContext context, IPrimitiveExecutor executor, Entity target,
            Successes successes) {
        try {
            if (!executor.applyResult(context, target)) return false;
            successes.add(1);
            com.example.akaishi.forbidden.magic.visual.SpellVisualEvents.emit(context, target, context.origin());
            return true;
        } catch (RuntimeException e) {
            LOGGER.error("[AkaishiMagic] 执行异常（E={}）", executor.effectId(), e);
            return false;
        }
    }

    public static String effectIdOf(MagicContext context) {
        if (context == null || context.snapshot() == null) return null;
        if (context.snapshot().operation() != null) return context.snapshot().operation().effectId();
        var adapted = planned(context);
        return adapted != null && adapted.snapshot().plan().operations().size() == 1
                ? adapted.snapshot().plan().operations().get(0).effectId() : null;
    }

    private static boolean allowPvpFor(MagicContext context, String effectId) {
        var effect = MagicEffectTable.get(effectId);
        if (effect == null || (effect.category() != MagicEffectDef.Category.ATTACK
                && !MagicIds.Effect.FIELD.equals(effectId))) return true;
        return !(context.level() instanceof ServerLevel level) || level.getServer().isPvpAllowed();
    }
}
