package com.example.akaishi.forbidden.api.magic;

import java.util.List;

/** 仅编译已支持词；报价不扣费，实际多通道支付仍由 MagicPayment 负责。 */
public record CostBreakdown(List<WordCost> words, float baseCost, float scaleMultiplier,
                            float deliveryMultiplier, float pricingCoefficient, float total,
                            float sanityEquivalent, float segmentCost) {
    public CostBreakdown { words = List.copyOf(words); }
    public record WordCost(int slot, String wordId, String inkId, float cost) {}
}
