package com.example.akaishi.forbidden.familiar;

import com.example.akaishi.forbidden.familiar.entity.FamiliarEntity;
import com.example.akaishi.forbidden.soul.SoulAffinity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Server-side soul personality hooks.  The service deliberately owns soul effects separately from
 * template AI so a soul crystal can change personality without changing the entity renderer.
 */
public final class FamiliarSoulAbilityService {
    private FamiliarSoulAbilityService() {
    }

    public static void tick(FamiliarEntity familiar) {
        if (familiar.level().isClientSide || familiar.ownerId() == null) return;
        Player owner = familiar.level().getPlayerByUUID(familiar.ownerId());
        if (owner == null || !owner.isAlive()) return;
        SoulAffinity affinity = affinity(familiar);
        if (affinity == null || familiar.alignment() == 0) return;
        int tier = tier(familiar);

        switch (affinity) {
            case SLOTH -> {
                if (familiar.tickCount % 200 == 0)
                    owner.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false));
            }
            case LUST -> applyAura(familiar, tier);
            case HUMILITY -> {
                if (familiar.distanceToSqr(owner) <= 3.0D * 3.0D && familiar.tickCount % 40 == 0)
                    owner.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 60, Math.min(3, tier / 2), false, false));
            }
            case CHARITY -> {
                if (owner.getHealth() <= owner.getMaxHealth() * 0.30F && familiar.distanceToSqr(owner) <= 4.0D * 4.0D)
                    owner.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 30, Math.min(3, tier / 2), false, false));
            }
            case CHASTITY -> {
                if (familiar.tickCount % Math.max(40, 220 - tier * 20) == 0) removeOneNegativeEffect(owner);
            }
            case KINDNESS -> {
                if (familiar.tickCount % 20 == 0) healNearestAlly(familiar, owner, tier);
            }
            case DILIGENCE -> {
                if (familiar.tickCount % 60 == 0)
                    owner.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 70, Math.min(2, tier / 3), false, false));
            }
            default -> {
                // Other souls primarily alter target selection and attack values below.
            }
        }
    }

    public static double attackDamageMultiplier(FamiliarEntity familiar) {
        SoulAffinity affinity = affinity(familiar);
        if (affinity == null) return 1.0D;
        int tier = tier(familiar);
        return switch (affinity) {
            case PRIDE -> 1.0D + 0.02D * tier;
            case WRATH -> 1.0D + 0.05D * Math.min(10,
                    (int) Math.floor((familiar.getMaxHealth() - familiar.getHealth())
                            / Math.max(1.0D, familiar.getMaxHealth() * 0.10D)));
            case GLUTTONY -> 1.0D + 0.01D * tier;
            default -> 1.0D;
        };
    }

    public static void onSuccessfulAttack(FamiliarEntity familiar, LivingEntity target, float damage) {
        SoulAffinity affinity = affinity(familiar);
        if (affinity == null) return;
        int tier = tier(familiar);
        Player owner = familiar.ownerId() == null ? null : familiar.level().getPlayerByUUID(familiar.ownerId());
        switch (affinity) {
            case ENVY -> {
                if (owner != null && target.getActiveEffects().stream().anyMatch(effect -> effect.getEffect().isBeneficial())) {
                    var beneficial = target.getActiveEffects().stream()
                            .filter(effect -> effect.getEffect().isBeneficial()).findFirst().orElse(null);
                    if (beneficial != null) {
                        target.removeEffect(beneficial.getEffect());
                        owner.addEffect(new MobEffectInstance(beneficial.getEffect(),
                                Math.max(20, beneficial.getDuration() / 2), Math.min(beneficial.getAmplifier(), tier - 1), false, false));
                    }
                }
            }
            case GLUTTONY -> familiar.heal(damage * (0.02F * tier));
            case LUST -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, Math.min(2, tier / 3), false, true));
            case DILIGENCE -> {
                if (owner != null) owner.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60 + tier * 10, 0, false, false));
            }
            default -> {
            }
        }
    }

    private static void applyAura(FamiliarEntity familiar, int tier) {
        if (familiar.tickCount % 20 != 0) return;
        familiar.level().getEntitiesOfClass(LivingEntity.class, familiar.getBoundingBox().inflate(5.0D), entity ->
                        entity != familiar && entity.isAlive() && !(entity instanceof Player))
                .forEach(entity -> entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, Math.min(1, tier / 4), false, true)));
    }

    private static void healNearestAlly(FamiliarEntity familiar, Player owner, int tier) {
        List<LivingEntity> allies = familiar.level().getEntitiesOfClass(LivingEntity.class,
                familiar.getBoundingBox().inflate(Math.min(10.0D, 3.0D + tier)),
                entity -> entity.isAlive() && entity != familiar && (entity == owner || entity.isAlliedTo(owner))
                        && entity.getHealth() < entity.getMaxHealth());
        allies.stream().min(Comparator.comparingDouble(familiar::distanceToSqr)).ifPresent(target ->
                target.heal(0.5F + 0.25F * tier));
    }

    private static void removeOneNegativeEffect(Player owner) {
        for (MobEffectInstance effect : List.copyOf(owner.getActiveEffects())) {
            if (!effect.getEffect().isBeneficial()) {
                owner.removeEffect(effect.getEffect());
                break;
            }
        }
    }

    private static SoulAffinity affinity(FamiliarEntity familiar) {
        return SoulAffinity.byId(familiar.affinity().toLowerCase(Locale.ROOT)).orElse(null);
    }

    private static int tier(FamiliarEntity familiar) {
        return Math.max(1, Math.min(7, Math.abs(familiar.alignment())));
    }
}
