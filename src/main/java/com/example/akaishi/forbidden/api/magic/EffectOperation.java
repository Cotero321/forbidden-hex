package com.example.akaishi.forbidden.api.magic;

import java.util.List;
import java.util.Set;

/** 每种效果独立携带单位和目标极性，不把护盾或百分比加进伤害。 */
public record EffectOperation(String effectId, String unit, String category, List<Integer> slots,
                              float power, float magnitude, float durationSeconds, float radius,
                              Set<String> traits) {
    public EffectOperation { slots = List.copyOf(slots); traits = Set.copyOf(traits); }
}
