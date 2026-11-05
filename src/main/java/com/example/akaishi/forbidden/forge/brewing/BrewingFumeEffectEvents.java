package com.example.akaishi.forbidden.forge.brewing;

import com.example.akaishi.forbidden.brewing.BrewingEffectType;
import com.example.akaishi.forbidden.brewing.BrewingFumePharmacology;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "forbidden_hex")
public final class BrewingFumeEffectEvents {
    private BrewingFumeEffectEvents() { }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void applicable(MobEffectEvent.Applicable event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel)) return;
        var effect = event.getEffectInstance().getEffect();
        if (BrewingFumePharmacology.rejectsVanilla(entity, effect)
                || !BrewingFumePharmacology.canApply(entity, BrewingFumePharmacology.typeOf(effect))) {
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public static void added(MobEffectEvent.Added event) {
        if (event.getEntity().level() instanceof ServerLevel) {
            BrewingFumePharmacology.added(event.getEntity(), event.getEffectInstance(), event.getOldEffectInstance());
        }
    }

    @SubscribeEvent
    public static void tick(LivingEvent.LivingTickEvent event) {
        BrewingFumePharmacology.tick(event.getEntity());
        com.example.akaishi.forbidden.brewing.AlchemyPotionEffects.tick(event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void heal(LivingHealEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel) || event.getAmount() <= 0) return;
        event.setAmount(event.getAmount() * BrewingFumePharmacology.healingMultiplier(event.getEntity()));
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void hurt(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel) || event.getAmount() <= 0) return;
        float amount = event.getAmount() * BrewingFumePharmacology.damageMultiplier(entity, event.getSource());
        if (BrewingFumePharmacology.directMelee(event.getSource())) {
            LivingEntity attacker = (LivingEntity) event.getSource().getEntity();
            int strength = BrewingFumePharmacology.strength(attacker, BrewingEffectType.BERSERK);
            if (strength > 0) amount *= 1.1F + .1F * strength;
        }
        event.setAmount(amount);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void damage(LivingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel) || event.getAmount() <= 0) return;
        event.setAmount(BrewingFumePharmacology.finalDamage(entity, event.getAmount()));
        if (event.getAmount() <= 0) return;
        BrewingFumePharmacology.wakeFromDamage(entity);
        if (event.getSource().getEntity() instanceof LivingEntity attacker && attacker != entity) {
            BrewingFumePharmacology.revealFromAttack(attacker);
        }
        if (BrewingFumePharmacology.directMelee(event.getSource())) {
            BrewingFumePharmacology.dealtMelee((LivingEntity) event.getSource().getEntity(), entity, event.getAmount());
        }
    }

    @SubscribeEvent
    public static void target(LivingChangeTargetEvent event) {
        if (event.getEntity().level() instanceof ServerLevel && event.getEntity() instanceof Mob mob
                && BrewingFumePharmacology.blocksTarget(mob, event.getNewTarget())) event.setCanceled(true);
    }
}
