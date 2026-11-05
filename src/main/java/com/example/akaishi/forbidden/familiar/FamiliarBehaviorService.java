package com.example.akaishi.forbidden.familiar;

import com.example.akaishi.forbidden.familiar.core.FamiliarMode;
import com.example.akaishi.forbidden.familiar.entity.FamiliarEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;

/**
 * Small server-authoritative behavior layer shared by all familiar templates.
 * Template-specific attributes are applied by the entity; this service only handles
 * ownership, modes, hunger recovery and the common melee fallback.
 */
public final class FamiliarBehaviorService {
    private FamiliarBehaviorService() {
    }

    public static void tick(FamiliarEntity familiar) {
        if (familiar.level().isClientSide || !(familiar.level() instanceof ServerLevel)) return;
        if (!familiar.isAlive() || familiar.ownerId() == null) return;

        Player owner = familiar.level().getPlayerByUUID(familiar.ownerId());
        if (owner == null || !owner.isAlive()) return;

        updateNeeds(familiar);
        followOwner(familiar, owner);

        FamiliarMode mode = effectiveMode(familiar);
        if (familiar.hunger() <= 0 || familiar.mood() < 20 || mode == FamiliarMode.PASSIVE
                || mode == FamiliarMode.STAY || mode == FamiliarMode.FOLLOW) {
            familiar.setTarget(null);
            return;
        }
        // Target searches remain throttled, but attacks use their own declared tick interval.
        // Gating both with modulo 5 stretched 8/14/18 ticks into 40/70/90 ticks.
        if (familiar.tickCount % 5 == 0) familiar.setTarget(chooseTarget(familiar, owner));
        LivingEntity target = familiar.getTarget();
        if (target == null || !target.isAlive() || target.level() != familiar.level()
                || target == owner || target instanceof FamiliarEntity || target.isAlliedTo(owner)) return;
        if (familiar.distanceToSqr(target) <= 2.75D * 2.75D
                && familiar.tickCount % attackInterval(familiar) == 0) {
            float damage = (float) Math.max(0.0D,
                    familiar.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE));
            damage *= (float) familiar.soulAttackMultiplier();
            if (familiar.criticalChance() > 0.0D && familiar.getRandom().nextDouble() < familiar.criticalChance()) {
                damage *= 1.5F;
            }
            if (damage > 0.0F && target.hurt(familiar.damageSources().mobAttack(familiar), damage)) {
                familiar.setHunger(familiar.hunger() - hungerCost(familiar, 1));
                if (familiar.lifesteal() > 0.0D) familiar.heal(damage * (float) familiar.lifesteal());
                FamiliarSoulAbilityService.onSuccessfulAttack(familiar, target, damage);
                familiar.setMood(Math.min(100, familiar.mood() + 1));
            }
        } else if (effectiveMode(familiar) == FamiliarMode.AGGRESSIVE) {
            familiar.getNavigation().moveTo(target, movementSpeed(familiar));
        }
    }

    private static void updateNeeds(FamiliarEntity familiar) {
        if (familiar.tickCount % 200 == 0) {
            familiar.setHunger(familiar.hunger() - hungerCost(familiar, 1));
            if (familiar.hunger() <= 0) familiar.setMood(Math.max(0, familiar.mood() - 2));
            else if (familiar.mood() < 100) familiar.setMood(familiar.mood() + 1);
        }
        if (familiar.tickCount % 100 == 0 && familiar.hunger() > 0
                && familiar.getHealth() < familiar.getMaxHealth()) {
            familiar.heal((float) familiar.regenerationMultiplier());
            familiar.setHunger(familiar.hunger() - hungerCost(familiar, 1));
        }
    }

    private static int hungerCost(FamiliarEntity familiar, int base) {
        return Math.max(1, (int) Math.ceil(base * familiar.hungerConsumptionMultiplier()));
    }

    private static void followOwner(FamiliarEntity familiar, Player owner) {
        FamiliarMode mode = effectiveMode(familiar);
        if (mode == FamiliarMode.STAY || mode == FamiliarMode.AGGRESSIVE) return;
        double distance = familiar.distanceToSqr(owner);
        if (distance > 24.0D * 24.0D) {
            familiar.teleportTo(owner.getX(), owner.getY(), owner.getZ());
            familiar.getNavigation().stop();
            return;
        }
        if (distance > 3.0D * 3.0D) {
            familiar.getNavigation().moveTo(owner, movementSpeed(familiar));
        } else {
            familiar.getNavigation().stop();
        }
    }

    private static LivingEntity chooseTarget(FamiliarEntity familiar, Player owner) {
        FamiliarMode mode = effectiveMode(familiar);
        if (mode == FamiliarMode.PASSIVE || mode == FamiliarMode.STAY || mode == FamiliarMode.FOLLOW) return null;
        if (mode == FamiliarMode.GUARD && owner.getLastHurtByMob() != null) {
            LivingEntity attacker = owner.getLastHurtByMob();
            if (attacker.isAlive() && familiar.distanceToSqr(attacker) <= 16.0D * 16.0D) return attacker;
            return null;
        }
        AABB search = familiar.getBoundingBox().inflate(mode == FamiliarMode.AGGRESSIVE ? 12.0D : 8.0D);
        List<LivingEntity> candidates = familiar.level().getEntitiesOfClass(LivingEntity.class, search,
                entity -> entity.isAlive() && entity != owner && entity != familiar
                        && !(entity instanceof FamiliarEntity)
                        && !entity.isAlliedTo(owner));
        return candidates.stream().min(Comparator.comparingDouble(familiar::distanceToSqr)).orElse(null);
    }

    private static int attackInterval(FamiliarEntity familiar) {
        return switch (familiar.template()) {
            case CAT -> 8;
            case WOLF, FROG -> 14;
            case BAT -> 18;
            case DRAGON -> 30;
            case PARROT, NONE -> 30;
        };
    }

    private static double movementSpeed(FamiliarEntity familiar) {
        return Math.max(0.1D, familiar.getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED));
    }

    /** Soul personality supplies the default behavior; an explicit panel mode still overrides it. */
    private static FamiliarMode effectiveMode(FamiliarEntity familiar) {
        if (familiar.mode() != FamiliarMode.FOLLOW) return familiar.mode();
        return switch (familiar.affinity().toLowerCase(java.util.Locale.ROOT)) {
            case "wrath", "envy", "gluttony" -> FamiliarMode.AGGRESSIVE;
            case "pride" -> FamiliarMode.STAY;
            case "sloth", "lust", "temperance", "chastity" -> FamiliarMode.PASSIVE;
            default -> FamiliarMode.FOLLOW;
        };
    }
}
