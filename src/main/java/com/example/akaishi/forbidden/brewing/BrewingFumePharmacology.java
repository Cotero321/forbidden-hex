package com.example.akaishi.forbidden.brewing;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static com.example.akaishi.forbidden.brewing.BrewingEffectType.*;

public final class BrewingFumePharmacology {
    private static final String PREFIX = "forbidden_hex_fume_";
    private static final String SLEEP_START = PREFIX + "sleep_start";
    private static final String SLEEP_IMMUNITY = PREFIX + "sleep_immunity";
    private static final String SEDATION_IMMUNITY = PREFIX + "sedation_immunity";
    private static final String CONVULSION_IMMUNITY = PREFIX + "convulsion_immunity";
    private static final String REVEALED_UNTIL = PREFIX + "revealed_until";
    private static final String LIFESTEAL_READY = PREFIX + "lifesteal_ready";
    private static final String PREVIOUS_POSITION = PREFIX + "position";
    private static final ThreadLocal<LivingEntity> CHILLING_DAMAGE = new ThreadLocal<>();

    private BrewingFumePharmacology() { }

    public static int strength(LivingEntity entity, BrewingEffectType type) {
        return BrewingStatusEffects.strength(entity, type);
    }

    public static boolean has(LivingEntity entity, BrewingEffectType type) {
        return BrewingStatusEffects.has(entity, type);
    }

    public static BrewingEffectType typeOf(MobEffect effect) {
        for (BrewingEffectType type : BrewingEffectType.values()) {
            if (!type.instantaneous() && BrewingStatusEffects.get(type) == effect) return type;
        }
        return null;
    }

    public static boolean boss(LivingEntity entity) {
        return entity.getType().is(Tags.EntityTypes.BOSSES) || entity.getType() == EntityType.WITHER
                || entity.getType() == EntityType.ENDER_DRAGON
                || BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals("forbidden_hex:agaitolos");
    }

    public static int prepareApplication(BrewingEffectType type, LivingEntity target, int strength, int ticks) {
        if (!canApply(target, type)) return 0;
        return capDuration(target, type, ticks);
    }

    public static boolean canApply(LivingEntity target, BrewingEffectType type) {
        if (type == null) return true;
        if ((type == DRUNK || type == SLUMBER) && has(target, SOBRIETY)) return false;
        if ((type == BLINDNESS || type == HALLUCINATION) && has(target, LUCIDITY)) return false;
        if (type == COLD_SUSCEPTIBILITY && has(target, COLD_RESISTANCE)) return false;
        if (type == SLUMBER || type == SEDATION || type == CONVULSIONS) {
            if (boss(target) || type == SEDATION && target instanceof Player) return false;
            if (has(target, type) || cooling(target, immunityKey(type), 100)) return false;
        }
        return true;
    }

    public static boolean rejectsVanilla(LivingEntity target, MobEffect effect) {
        return has(target, LUCIDITY) && (effect == MobEffects.BLINDNESS || effect == MobEffects.DARKNESS);
    }

    public static MobEffectInstance adjustIncoming(LivingEntity target, MobEffectInstance incoming) {
        if (!(target.level() instanceof ServerLevel) || incoming == null) return incoming;
        BrewingEffectType type = typeOf(incoming.getEffect());
        double factor = 1;
        if (!incoming.getEffect().isInstantenous() && incoming.getEffect().getCategory() == MobEffectCategory.HARMFUL
                && type != DRUG_RESISTANCE && type != DRUG_SENSITIVITY) {
            int resistant = strength(target, DRUG_RESISTANCE);
            int sensitive = strength(target, DRUG_SENSITIVITY);
            if (resistant > 0) factor = .9 - .1 * resistant;
            else if (sensitive > 0) factor = 1 + .25 * sensitive;
        }
        int original = incoming.getDuration();
        int duration = original < 0 ? original : (int) Math.max(1, Math.min(Integer.MAX_VALUE, Math.round(original * factor)));
        duration = capDuration(target, type, duration);
        if (duration == original) return incoming;
        CompoundTag saved = incoming.save(new CompoundTag());
        saved.putInt("Duration", duration);
        MobEffectInstance adjusted = MobEffectInstance.load(saved);
        return adjusted == null ? incoming : adjusted;
    }

    private static int capDuration(LivingEntity target, BrewingEffectType type, int duration) {
        int cap = type == SLUMBER ? target instanceof Player ? 60 : 200
                : type == SEDATION ? 300 : type == CONVULSIONS ? 400 : Integer.MAX_VALUE;
        if (cap == Integer.MAX_VALUE) return duration;
        return Math.max(1, duration < 0 ? cap : Math.min(cap, duration));
    }

    public static void added(LivingEntity target, MobEffectInstance incoming, MobEffectInstance previous) {
        BrewingEffectType type = typeOf(incoming.getEffect());
        if (type == null) return;
        switch (type) {
            case SOBRIETY -> { remove(target, DRUNK); remove(target, SLUMBER); }
            case LUCIDITY -> { target.removeEffect(MobEffects.BLINDNESS); target.removeEffect(MobEffects.DARKNESS); remove(target, HALLUCINATION); }
            case COLD_RESISTANCE -> { remove(target, COLD_SUSCEPTIBILITY); target.setTicksFrozen(0); }
            case COLD_SUSCEPTIBILITY -> remove(target, COLD_RESISTANCE);
            case DRUG_RESISTANCE -> remove(target, DRUG_SENSITIVITY);
            case DRUG_SENSITIVITY -> remove(target, DRUG_RESISTANCE);
            case STRONG_HEART -> remove(target, WITHERING);
            case WITHERING -> remove(target, STRONG_HEART);
            case SLUMBER -> {
                if (previous == null) target.getPersistentData().putLong(SLEEP_START, target.level().getGameTime());
            }
            default -> { }
        }
    }

    private static void remove(LivingEntity target, BrewingEffectType type) {
        target.removeEffect(BrewingStatusEffects.get(type));
    }

    private static String immunityKey(BrewingEffectType type) {
        return type == SLUMBER ? SLEEP_IMMUNITY : type == SEDATION ? SEDATION_IMMUNITY : CONVULSION_IMMUNITY;
    }

    private static boolean cooling(LivingEntity entity, String key, int maximum) {
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(key)) return false;
        long remaining = data.getLong(key) - entity.level().getGameTime();
        if (remaining > 0 && remaining <= maximum) return true;
        data.remove(key);
        return false;
    }

    public static void removed(LivingEntity entity, BrewingEffectType type) {
        if (!(entity.level() instanceof ServerLevel)) return;
        if (type == SLUMBER || type == SEDATION || type == CONVULSIONS) {
            entity.getPersistentData().putLong(immunityKey(type), entity.level().getGameTime() + 100);
            if (type == SLUMBER) entity.getPersistentData().remove(SLEEP_START);
        }
        if (type == STRONG_HEART || type == WITHERING) clampHealth(entity);
    }

    public static void clampHealth(LivingEntity entity) {
        if (entity.isAlive() && entity.getHealth() > entity.getMaxHealth()) {
            entity.setHealth(Math.max(1, entity.getMaxHealth()));
        }
    }

    public static float healingMultiplier(LivingEntity entity) {
        int mending = strength(entity, MENDING), badBlood = strength(entity, BAD_BLOOD);
        return (mending == 0 ? 1 : 1.1F + .1F * mending) * (badBlood == 0 ? 1 : .7F - .1F * badBlood);
    }

    public static float exhaustionMultiplier(LivingEntity entity) {
        int rush = strength(entity, BLOOD_RUSH);
        return rush == 0 ? 1 : 1.15F + .1F * rush;
    }

    public static boolean directMelee(net.minecraft.world.damagesource.DamageSource source) {
        return source.getEntity() instanceof LivingEntity && source.getDirectEntity() == source.getEntity()
                && (source.is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)
                || source.is(net.minecraft.world.damagesource.DamageTypes.MOB_ATTACK));
    }

    public static void revealFromAttack(LivingEntity attacker) {
        if (has(attacker, CONCEALMENT)) attacker.getPersistentData().putLong(REVEALED_UNTIL, attacker.level().getGameTime() + 100);
    }

    public static void dealtMelee(LivingEntity attacker, LivingEntity victim, float damage) {
        if (attacker == victim || attacker.isAlliedTo(victim) || damage <= 0
                || attacker instanceof Player a && victim instanceof Player b && !a.canHarmPlayer(b)) return;
        int steal = strength(attacker, LIFESTEAL);
        if (steal > 0 && !cooling(attacker, LIFESTEAL_READY, 10)) {
            float actual = Math.min(damage, victim.getHealth());
            if (actual > 0) {
                attacker.getPersistentData().putLong(LIFESTEAL_READY, attacker.level().getGameTime() + 10);
                attacker.heal(Math.min(.75F + .25F * steal, actual * (.075F + .025F * steal)));
            }
        }
        int boiling = strength(attacker, BOILING_BLOOD);
        if (boiling > 0) victim.setSecondsOnFire(1 + boiling);
    }

    public static float finalDamage(LivingEntity entity, float amount) {
        return CHILLING_DAMAGE.get() == entity ? Math.max(0, Math.min(amount, entity.getHealth() - 1)) : amount;
    }

    public static void wakeFromDamage(LivingEntity entity) {
        if (has(entity, SLUMBER)) remove(entity, SLUMBER);
        if (has(entity, SEDATION)) remove(entity, SEDATION);
    }

    public static boolean blocksTarget(Mob mob, LivingEntity target) {
        if (target == null || boss(mob)) return false;
        if (has(mob, SEDATION) || asleep(mob)) return true;
        if (!(mob instanceof Monster) || mob.getTarget() == target) return false;
        int concealment = strength(target, CONCEALMENT);
        if (concealment == 0 || cooling(target, REVEALED_UNTIL, 100)) return false;
        double follow = mob.getAttributeValue(Attributes.FOLLOW_RANGE);
        double range = follow * Math.max(.25, .7 - .08 * concealment);
        return mob.distanceToSqr(target) > range * range;
    }

    public static boolean asleep(LivingEntity entity) {
        if (!has(entity, SLUMBER)) return false;
        if (entity instanceof Player) return true;
        CompoundTag data = entity.getPersistentData();
        long now = entity.level().getGameTime();
        if (!data.contains(SLEEP_START) || now < data.getLong(SLEEP_START)) data.putLong(SLEEP_START, now);
        return now - data.getLong(SLEEP_START) >= 40;
    }

    public static void tick(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || !entity.isAlive()) return;
        long now = level.getGameTime();
        clampHealth(entity);
        cooling(entity, SLEEP_IMMUNITY, 100);
        cooling(entity, SEDATION_IMMUNITY, 100);
        cooling(entity, CONVULSION_IMMUNITY, 100);
        cooling(entity, REVEALED_UNTIL, 100);
        cooling(entity, LIFESTEAL_READY, 10);
        if (!has(entity, SLUMBER)) entity.getPersistentData().remove(SLEEP_START);
        if (has(entity, SOBRIETY)) { remove(entity, DRUNK); remove(entity, SLUMBER); }
        if (has(entity, LUCIDITY)) {
            entity.removeEffect(MobEffects.BLINDNESS);
            entity.removeEffect(MobEffects.DARKNESS);
            remove(entity, HALLUCINATION);
        }
        if (has(entity, COLD_RESISTANCE)) entity.setTicksFrozen(0);
        if (now % 5 == 0) updateFrostSpeed(entity);
        int cold = strength(entity, COLD_SUSCEPTIBILITY);
        if (cold > 0 && entity.getTicksFrozen() > 0 && entity.canFreeze()) {
            entity.setTicksFrozen(Math.min(entity.getTicksRequiredToFreeze() + 40,
                    entity.getTicksFrozen() + 1 + (cold - 1) / 2));
        }
        if (BrewingFumeMovement.restrictsMovement(entity)) {
            entity.setSprinting(false);
            entity.setJumping(false);
        }
        boolean asleep = asleep(entity);
        if (entity instanceof Mob mob) {
            if (asleep || has(entity, SEDATION)) {
                mob.setTarget(null);
                mob.getNavigation().stop();
                if (asleep) {
                    mob.setXxa(0); mob.setYya(0); mob.setZza(0);
                    Vec3 motion = mob.getDeltaMovement();
                    mob.setDeltaMovement(0, Math.min(0, motion.y), 0);
                    mob.setJumping(false);
                }
            }
            if (has(entity, HALLUCINATION) && !boss(entity) && now % 40 == 0) mob.setTarget(null);
        }
        int photo = strength(entity, PHOTOSENSITIVITY);
        if (photo > 0 && now % 40 == 0 && level.isDay() && level.dimensionType().hasSkyLight()
                && level.canSeeSky(entity.blockPosition()) && !level.isRainingAt(entity.blockPosition())
                && !entity.isInWaterOrRain()) {
            entity.hurt(level.damageSources().onFire(), .5F + .5F * photo);
        }
        int poison = strength(entity, CHILLING_POISON);
        if (poison > 0) {
            CompoundTag data = entity.getPersistentData();
            Vec3 position = entity.position();
            double previousX = data.getCompound(PREVIOUS_POSITION).getDouble("x");
            double previousZ = data.getCompound(PREVIOUS_POSITION).getDouble("z");
            boolean moving = entity.isSprinting() || data.contains(PREVIOUS_POSITION)
                    && Math.pow(position.x - previousX, 2) + Math.pow(position.z - previousZ, 2) > .0025;
            int interval = (90 - 10 * poison) / (moving ? 2 : 1);
            if (now % interval == 0 && entity.getHealth() > 1) {
                LivingEntity previous = CHILLING_DAMAGE.get();
                CHILLING_DAMAGE.set(entity);
                try {
                    entity.hurt(level.damageSources().magic(), Math.min(1, entity.getHealth() - 1));
                } finally {
                    if (previous == null) CHILLING_DAMAGE.remove(); else CHILLING_DAMAGE.set(previous);
                }
            }
            CompoundTag saved = new CompoundTag();
            saved.putDouble("x", position.x); saved.putDouble("z", position.z);
            data.put(PREVIOUS_POSITION, saved);
        } else entity.getPersistentData().remove(PREVIOUS_POSITION);
        int boiling = strength(entity, BOILING_BLOOD);
        if (boiling > 0 && now % 20 == 0 && entity instanceof Player player) player.causeFoodExhaustion(.05F + .05F * boiling);
    }

    private static void updateFrostSpeed(LivingEntity entity) {
        var attribute = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        int frost = strength(entity, FROST);
        if (attribute == null || frost == 0) return;
        UUID id = UUID.nameUUIDFromBytes(FROST.id().toString().getBytes(StandardCharsets.UTF_8));
        AttributeModifier modifier = attribute.getModifier(id);
        double amount = -(.1 + .05 * frost) * (has(entity, COLD_RESISTANCE) ? .5 : 1);
        if (modifier != null && Math.abs(modifier.getAmount() - amount) > .000001) {
            attribute.removeModifier(id);
            attribute.addTransientModifier(new AttributeModifier(id, modifier.getName(), amount, modifier.getOperation()));
        }
    }

    public static float damageMultiplier(LivingEntity target, net.minecraft.world.damagesource.DamageSource source) {
        int berserk = strength(target, BERSERK);
        float multiplier = berserk == 0 ? 1 : 1.1F + .05F * berserk;
        int cold = strength(target, COLD_SUSCEPTIBILITY);
        if (cold > 0 && source.is(DamageTypeTags.IS_FREEZING)) multiplier *= 1.1F + .1F * cold;
        return multiplier;
    }
}
