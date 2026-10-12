package com.example.akaishi.forbidden.magic.cast;

import com.example.akaishi.forbidden.magic.table.MagicSource;

/** 支付拆分只做计算，供真实提交与无世界回归共同消费。 */
public record PaymentPlan(float extra, float main, float sanity, float health) {
    public static PaymentPlan calculate(MagicSource source, float cost, float availableExtra) {
        if (source == null || !Float.isFinite(cost) || cost < 0f || !Float.isFinite(availableExtra))
            throw new IllegalArgumentException("Invalid payment input");
        float extra = Math.min(Math.max(0f, availableExtra), cost);
        float main = cost - extra;
        return new PaymentPlan(extra, main, source == MagicSource.SAN ? main / 100f : 0f,
                source == MagicSource.LIFE ? main / 200f : 0f);
    }

    public static float refundableHealth(float current, float maximum, float paid, float ratio,
            boolean alive, boolean removed) {
        if (!alive || removed) return current;
        return Math.min(maximum, current + paid * Math.max(0f, Math.min(1f, ratio)));
    }
}
