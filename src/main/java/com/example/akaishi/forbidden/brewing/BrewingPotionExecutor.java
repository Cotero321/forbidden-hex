package com.example.akaishi.forbidden.brewing;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class BrewingPotionExecutor {
    private BrewingPotionExecutor() { }

    public static void applyWorld(BrewingPotionData data, ServerLevel level, Vec3 origin, Entity owner) {
        if (!level.getServer().isSameThread()) return;
        for (BrewingEffectEntry entry : data.customEffects()) {
            BrewingWorldEffects.apply(entry.type(), level, origin, owner, data.strength(), data.duration(), entry.color(), data.rulesVersion() >= BrewingPotionData.CURRENT_RULES);
        }
    }

    public static void applyEntity(BrewingPotionData data, LivingEntity target, Entity source, Entity owner,
                                   double intensity, boolean firstApplication) {
        if (!(target.level() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || !target.isAlive() || !target.isAffectedByPotions() || !Double.isFinite(intensity) || intensity <= 0) return;
        var immunity = BrewingStatusEffects.get(BrewingEffectType.LINGERING_IMMUNITY);
        if (source instanceof AreaEffectCloud && immunity != null && target.hasEffect(immunity)) return;
        boolean protectPlayer = owner instanceof Player player && target instanceof Player other
                && player != other && !player.canHarmPlayer(other);
        intensity = Math.min(1, intensity);
        for (int phase = 0; phase < 2; phase++) {
            boolean instantaneous = phase == 1;
            for (MobEffectInstance effect : data.effects()) {
                if (effect.getEffect().isInstantenous() != instantaneous) continue;
                if (protectPlayer && effect.getEffect().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL) continue;
                if (instantaneous) {
                    if (firstApplication) effect.getEffect().applyInstantenousEffect(source, owner, target, effect.getAmplifier(), intensity);
                } else {
                    int ticks = Math.max(1, (int) (effect.getDuration() * intensity));
                    target.addEffect(new MobEffectInstance(effect.getEffect(), ticks, effect.getAmplifier(),
                            effect.isAmbient(), !data.hideParticles(), !data.hideParticles()), source);
                }
            }
            for (BrewingEffectEntry entry : data.customEffects()) {
                if (entry.type().instantaneous() != instantaneous) continue;
                if (protectPlayer && entry.type().harmful()) continue;
                if (instantaneous && !firstApplication) continue;
                if (entry.type() == BrewingEffectType.GRAVITY_FIELD && (source instanceof BrewingPotionProjectile || source instanceof BrewingEffectCloud)) continue;
                if (entry.type() == BrewingEffectType.EXTREME_COLD && source instanceof BrewingPotionProjectile) continue;
                if (entry.type() == BrewingEffectType.WEB && firstApplication) {
                    BrewingWorldEffects.apply(entry.type(), level, target.position(), owner, data.strength(), data.duration(), entry.color(), data.rulesVersion() >= BrewingPotionData.CURRENT_RULES);
                }
                int ticks = Math.max(1, (int) (data.ticks(entry.type()) * intensity));
                BrewingStatusEffects.apply(entry.type(), target, source, owner, data.strength(), ticks,
                        entry.color(), data.hideParticles(), firstApplication, intensity);
            }
        }
    }
}
