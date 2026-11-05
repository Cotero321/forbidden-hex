package com.example.akaishi.forbidden.brewing;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class BrewingFumeMovement {
    private BrewingFumeMovement() { }

    public static boolean restrictsMovement(LivingEntity entity) {
        if (entity instanceof Player && BrewingStatusEffects.has(entity, BrewingEffectType.SLUMBER)) return true;
        MobEffectInstance convulsions = entity.getEffect(BrewingStatusEffects.get(BrewingEffectType.CONVULSIONS));
        if (convulsions == null) return false;
        int strength = Math.max(1, Math.min(5, convulsions.getAmplifier() + 1));
        return Math.floorMod(convulsions.getDuration(), 60) < 4 + 2 * strength;
    }
}
