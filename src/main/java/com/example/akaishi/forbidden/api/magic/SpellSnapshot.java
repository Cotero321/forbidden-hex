package com.example.akaishi.forbidden.api.magic;

/** 旧消费者的数值桥接；计划保留全部操作，coreWordId 仅供旧视觉读取。 */
public record SpellSnapshot(int wordCount, String coreWordId, String deliveryId, String sourceId,
                            float cost, float sanityCost, float cooldownSeconds, float castTimeSeconds,
                            float range, float power, float durationSeconds, float radius, float magnitude,
                            int wordSynergyCount, int inkSynergyCount, float pricingCoefficient, SpellPlan plan,
                            EffectOperation operation) {
    public SpellSnapshot(int wordCount, String coreWordId, String deliveryId, String sourceId,
                         float cost, float sanityCost, float cooldownSeconds, float castTimeSeconds,
                         float range, float power, float durationSeconds, float radius, float magnitude,
                         int wordSynergyCount, int inkSynergyCount, float pricingCoefficient) {
        this(wordCount, coreWordId, deliveryId, sourceId, cost, sanityCost, cooldownSeconds, castTimeSeconds,
                range, power, durationSeconds, radius, magnitude, wordSynergyCount, inkSynergyCount, pricingCoefficient, null, null);
    }
    public int segments() {
        return plan != null ? plan.schedule().segments() : Math.max(1, Math.round(pricingCoefficient));
    }
    public float segmentCost() { return plan != null ? plan.cost().segmentCost() : cost / segments(); }
}
