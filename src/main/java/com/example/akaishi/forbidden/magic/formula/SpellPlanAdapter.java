package com.example.akaishi.forbidden.magic.formula;

import com.example.akaishi.forbidden.api.magic.*;
import com.example.akaishi.forbidden.magic.cast.CastAdjustments;
import com.example.akaishi.forbidden.magic.table.MagicEffectDef;
import com.example.akaishi.forbidden.magic.table.MagicSource;
import java.util.List;

/** 载体调整逐操作结算，快照只作为旧消费者的参数视图。 */
public final class SpellPlanAdapter {
    private SpellPlanAdapter() {}
    public static SpellPlan adjusted(SpellPlan plan, CastAdjustments adjustments) {
        if (adjustments == null) return plan;
        MagicSource source = MagicSource.byIdOrDefault(plan.input().sourceId());
        int count = plan.cost().words().size();
        List<EffectOperation> operations = plan.operations().stream().map(op -> {
            float mul = adjustments.powerMul(op.effectId(), MagicEffectDef.Category.valueOf(op.category()), adjustments.thirdCast());
            float magnitude = "teleport".equals(op.effectId()) ? op.magnitude() : op.magnitude() * mul;
            return new EffectOperation(op.effectId(), op.unit(), op.category(), op.slots(), op.power() * mul,
                    magnitude, op.durationSeconds(), op.radius() * adjustments.radiusMul(), op.traits());
        }).toList();
        float cooldownMul = plan.operations().stream().map(op -> adjustments.cooldownMul(source,
                MagicEffectDef.Category.valueOf(op.category()), adjustments.thirdCast())).max(Float::compare).orElse(1f);
        var delivery = plan.delivery();
        var cost = plan.cost();
        float mul = adjustments.costMul(source, count);
        return new SpellPlan(plan.input(), operations, plan.target(), new DeliveryPlan(delivery.deliveryId(),
                delivery.range() * adjustments.rangeMul(), delivery.cooldownSeconds() * cooldownMul,
                delivery.castTimeSeconds() * adjustments.castTimeMul()), plan.schedule(),
                new CostBreakdown(cost.words(), cost.baseCost(), cost.scaleMultiplier() * mul,
                        cost.deliveryMultiplier(), cost.pricingCoefficient(), cost.total() * mul,
                        cost.sanityEquivalent() * mul, cost.segmentCost() * mul), plan.features());
    }
    public static SpellSnapshot snapshot(SpellPlan plan) {
        var first = plan.operations().get(0);
        float duration = plan.operations().stream().map(EffectOperation::durationSeconds).max(Float::compare).orElse(0f);
        float radius = plan.operations().stream().map(EffectOperation::radius).max(Float::compare).orElse(0f);
        return snapshot(plan, new EffectOperation(first.effectId(), first.unit(), first.category(), first.slots(),
                first.power(), first.magnitude(), duration, radius, first.traits()));
    }
    public static SpellSnapshot snapshot(SpellPlan plan, EffectOperation operation) {
        String visualWord = plan.input().words().get(operation.slots().get(0));
        var delivery = plan.delivery();
        var cost = plan.cost();
        return new SpellSnapshot(cost.words().size(), visualWord, delivery.deliveryId(), plan.input().sourceId(),
                cost.total(), cost.sanityEquivalent(), delivery.cooldownSeconds(), delivery.castTimeSeconds(),
                delivery.range(), operation.power(), operation.durationSeconds(), operation.radius(), operation.magnitude(),
                0, 0, cost.pricingCoefficient(), plan, operation);
    }
}
