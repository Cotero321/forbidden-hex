package com.example.akaishi.forbidden.brewing;

public record WitchOvenProfile(int queues, double fumeBonus, double fumeChanceCap) {
    public static final int MAX_QUEUES = 64;
    public static final WitchOvenProfile BASIC = new WitchOvenProfile(1, 0, 1);

    public WitchOvenProfile {
        if (queues < 1 || queues > MAX_QUEUES || !Double.isFinite(fumeBonus) || fumeBonus < 0 || fumeBonus > 1
                || !Double.isFinite(fumeChanceCap) || fumeChanceCap < 0 || fumeChanceCap > 1) {
            throw new IllegalArgumentException("Invalid witch oven profile");
        }
    }

    public double chance(WitchOvenRecipe recipe) { return Math.min(fumeChanceCap, recipe.fumeChance() + fumeBonus); }
}
