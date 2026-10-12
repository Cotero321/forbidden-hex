package com.example.akaishi.forbidden.api.magic;

import java.util.List;
import java.util.Set;

public record SpellPlan(SpellInput input, List<EffectOperation> operations, TargetPlan target,
                        DeliveryPlan delivery, SchedulePlan schedule, CostBreakdown cost,
                        Set<String> features) {
    public SpellPlan { operations = List.copyOf(operations); features = Set.copyOf(features); }
}
