package com.example.akaishi.forbidden.magic.exec;

import com.example.akaishi.forbidden.api.magic.*;
import com.example.akaishi.forbidden.api.magic.exec.*;
import com.example.akaishi.forbidden.magic.formula.SpellPlanAdapter;
import com.example.akaishi.forbidden.magic.cast.CastAdjustments;
import com.example.akaishi.forbidden.magic.table.MagicEffectDef;
import com.example.akaishi.forbidden.magic.table.MagicSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** 无世界管道回归：验证实际分派、独立参数及受理和成功计数，不模拟实体伤害。 */
public final class SpellPlanExecutionSelfCheck {
    private SpellPlanExecutionSelfCheck() {}
    public static List<String> inspect() {
        List<String> failures = new ArrayList<>();
        for (List<String> words : List.of(List.of("tou", "jian"), List.of("tou", "jian", "beng"),
                List.of("hu", "zhao_cover"), List.of("si", "lian"), List.of("liu", "beng"))) {
            var compiled = SpellPlans.compile(new SpellInput(words, java.util.Collections.nCopies(words.size(), "chi"),
                    "chi", "life", 0f, 0));
            if (!compiled.ok()) { failures.add("compile " + words); continue; }
            SpellPlan basePlan = compiled.plan();
            CastAdjustments adjustments = new TestAdjustments();
            SpellPlan plan = SpellPlanAdapter.adjusted(basePlan, adjustments);
            List<String> invoked = new ArrayList<>();
            MagicShape shape = MagicShape.valueOf(plan.target().shape());
            try {
                MagicExecRegistry.register(new ITargetResolver() {
                    public MagicShape shape() { return shape; }
                    public List<Entity> resolve(MagicContext context, MagicTargetQuery query) {
                        if (query.limit() != plan.target().limit()) failures.add("target limit " + words);
                        return List.of();
                    }
                });
                MagicExecRegistry.register(new IDeliverer() {
                    public String deliveryId() { return plan.delivery().deliveryId(); }
                    public boolean deliver(MagicContext context, MagicDeliveryAction action) {
                        action.fire(Vec3.ZERO);
                        return true;
                    }
                });
                for (EffectOperation operation : plan.operations()) {

                    MagicExecRegistry.register(new IPrimitiveExecutor() {
                        public String effectId() { return operation.effectId(); }
                        public boolean worksWithoutTarget() { return true; }
                        public void apply(MagicContext context, Entity target) {}
                        public boolean applyResult(MagicContext context, Entity target) {
                            invoked.add(effectId());
                            if (context.snapshot().power() != operation.power()
                                    || context.snapshot().magnitude() != operation.magnitude()
                                    || context.snapshot().durationSeconds() != operation.durationSeconds()
                                    || context.snapshot().radius() != operation.radius())
                                failures.add("operation values " + words + ":" + effectId());
                            return !(words.contains("si") && effectId().equals("damage"));
                        }
                    });
                }
                if (plan.operations().stream().noneMatch(op -> op.power() != basePlan.operations().stream()
                        .filter(base -> base.effectId().equals(op.effectId())).findFirst().orElseThrow().power()))
                    failures.add("power adjustment missing " + words);
                var context = new MagicContext(null, null, Vec3.ZERO, "life", "chi", List.of("chi"), words,
                        6, SpellPlanAdapter.snapshot(plan));
                List<Integer> notifications = new ArrayList<>();
                var result = MagicPipeline.castResult(context, notifications::add);
                if (!result.accepted() || !invoked.equals(plan.operations().stream().map(EffectOperation::effectId).toList()))
                    failures.add("dispatch order " + words + ":" + invoked);
                long expected = plan.operations().stream().filter(op -> !(words.contains("si") && op.effectId().equals("damage"))).count();
                if (result.successfulTargets() != expected || notifications.size() != expected
                        || (expected > 0 && notifications.get(notifications.size() - 1) != expected))
                    failures.add("effect success/callback duplicate " + words);
                if (context.snapshot().segmentCost() != plan.cost().segmentCost()) failures.add("payment bridge " + words);
                if (words.contains("beng") && plan.schedule().followupHits() != 1) failures.add("followup " + words);
                if (words.equals(List.of("liu", "beng"))
                        && !com.example.akaishi.forbidden.magic.table.MagicIds.Delivery.CHANNEL.equals(plan.delivery().deliveryId()))
                    failures.add("guided volley delivery " + words);
            } finally {
                // 只允许独立 main 运行，禁止服务端启动调用此探针。
                MagicExecRegistry.clearAll();
            }
        }
        return failures;
    }

    private static final class TestAdjustments implements CastAdjustments {
        @Override public MagicSource source() { return MagicSource.LIFE; }
        @Override public List<String> wordInks() { return List.of("chi"); }
        @Override public List<String> involvedInks() { return List.of("chi"); }
        @Override public float costMul(MagicSource source, int wordCount) { return 0.90f; }
        @Override public float cooldownMul(MagicSource source, MagicEffectDef.Category category, boolean thirdCast) { return 0.95f; }
        @Override public float castTimeMul() { return 0.90f; }
        @Override public float rangeMul() { return 1.10f; }
        @Override public float radiusMul() { return 1.15f; }
        @Override public float powerMul(String effectId, MagicEffectDef.Category category, boolean thirdCast) { return 1.12f; }
        @Override public float extraAvailable(MagicSource source) { return 0f; }
        @Override public void recordExtraPaid(float amount) {}
        @Override public float extraPaid() { return 0f; }
        @Override public boolean thirdCast() { return false; }
    }

    public static void main(String[] args) {
        var failures = inspect();
        if (!failures.isEmpty()) throw new IllegalStateException(String.join("\n", failures));
        System.out.println("SpellPlanExecutionSelfCheck: PASS");
    }
}
