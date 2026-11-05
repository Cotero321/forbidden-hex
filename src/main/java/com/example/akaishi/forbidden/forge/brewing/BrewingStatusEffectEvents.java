package com.example.akaishi.forbidden.forge.brewing;

import com.example.akaishi.forbidden.brewing.BrewingEffectType;
import com.example.akaishi.forbidden.brewing.BrewingStatusEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import static com.example.akaishi.forbidden.brewing.BrewingEffectType.*;

@Mod.EventBusSubscriber(modid = "forbidden_hex")
public final class BrewingStatusEffectEvents {
    private static final ThreadLocal<Boolean> SECONDARY = ThreadLocal.withInitial(() -> false);
    private BrewingStatusEffectEvents() {}
    private static int s(LivingEntity e, BrewingEffectType type) { return BrewingStatusEffects.strength(e, type); }
    private static boolean has(LivingEntity e, BrewingEffectType type) { return BrewingStatusEffects.has(e, type); }
    private static boolean allowed(LivingEntity attacker, LivingEntity victim) {
        return attacker != victim && (!(attacker instanceof Player a) || !(victim instanceof Player b) || a.canHarmPlayer(b)) && !attacker.isAlliedTo(victim);
    }
    private static void secondary(Runnable action) {
        if (SECONDARY.get()) return;
        SECONDARY.set(true);
        try { action.run(); } finally { SECONDARY.remove(); }
    }
    @SubscribeEvent public static void applicable(MobEffectEvent.Applicable event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide) return;
        MobEffect effect = event.getEffectInstance().getEffect();
        if ((effect == MobEffects.POISON && has(e, ANTIDOTE))
                || (effect == BrewingStatusEffects.get(PLAGUE) && has(e, EPIDEMIC_GUARD))
                || (effect == BrewingStatusEffects.get(EXPLOSIVE) && has(e, INERT))) event.setResult(Event.Result.DENY);
    }
    @SubscribeEvent public static void teleport(EntityTeleportEvent event) {
        if (event.getEntity().level().isClientSide || !(event.getEntity() instanceof LivingEntity e) || !has(e, ANCHOR)) return;
        if (event instanceof EntityTeleportEvent.EnderPearl || event instanceof EntityTeleportEvent.ChorusFruit) event.setCanceled(true);
    }
    @SubscribeEvent public static void attack(LivingAttackEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide) return;
        if (has(e, LINGERING_IMMUNITY) && (event.getSource().getDirectEntity() instanceof AreaEffectCloud || event.getSource().is(net.minecraft.world.damagesource.DamageTypes.DRAGON_BREATH))) event.setCanceled(true);
    }
    @SubscribeEvent public static void hurt(LivingHurtEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide) return;
        float amount = event.getAmount();
        int plague = s(e, PLAGUE), overgrowth = s(e, OVERGROWTH);
        if (!event.getSource().is(com.example.akaishi.forbidden.magic.MagicDamageTypes.MAGIC_RESISTIBLE))
            amount *= 1f - BrewingStatusEffects.damageReduction(e, event.getSource());
        if (plague > 0) amount *= 1.10F + .05F * plague;
        if (overgrowth > 0 && event.getSource().is(DamageTypeTags.IS_FIRE)) amount *= 1.15F + .05F * overgrowth;
        event.setAmount(amount);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void damage(LivingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || SECONDARY.get() || event.getAmount() <= 0) return;
        Entity attackerEntity = event.getSource().getEntity();
        if (attackerEntity instanceof LivingEntity attacker && allowed(victim, attacker) && has(victim, REFLECTION))
            secondary(() -> attacker.hurt(victim.damageSources().thorns(victim), event.getAmount() * (.15F + .05F * s(victim, REFLECTION))));
        if (has(victim, EXPLOSIVE) && !has(victim, INERT)) {
            long now = victim.level().getGameTime();
            long last = victim.getPersistentData().getLong("forbidden_hex_brewing_explosion");
            if (now >= last && victim.getRandom().nextFloat() < .05F + .05F * s(victim, EXPLOSIVE)) {
                victim.getPersistentData().putLong("forbidden_hex_brewing_explosion", now + 40);
                secondary(() -> victim.level().explode(victim, victim.getX(), victim.getY(), victim.getZ(), .75F + .25F * s(victim, EXPLOSIVE), false, Level.ExplosionInteraction.NONE));
            }
        }
        if (!(attackerEntity instanceof LivingEntity attacker) || event.getSource().getDirectEntity() != attacker || !allowed(attacker, victim)) return;
        if (!(event.getSource().is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK) || event.getSource().is(net.minecraft.world.damagesource.DamageTypes.MOB_ATTACK))) return;
        int poison = s(attacker, POISON_COATING);
        if (poison > 0) victim.addEffect(new MobEffectInstance(MobEffects.POISON, (3 + poison) * 20, poison >= 4 ? 1 : 0), attacker);
        int knockback = s(attacker, KNOCKBACK);
        if (knockback > 0) victim.knockback(.45 + .15 * knockback, attacker.getX() - victim.getX(), attacker.getZ() - victim.getZ());
        int pull = s(attacker, PULL);
        if (pull > 0) {
            Vec3 delta = attacker.position().subtract(victim.position());
            double length = Math.min(Math.max(0, delta.length() - 1), .75 + .25 * pull);
            if (length > 0) victim.move(MoverType.SELF, delta.normalize().scale(length));
        }
    }
    @SubscribeEvent public static void fall(LivingFallEvent event) {
        int strength = s(event.getEntity(), FAST_FALLING);
        if (!event.getEntity().level().isClientSide && strength > 0) event.setDamageMultiplier(event.getDamageMultiplier() * (1.15F + .05F * strength));
    }
    @SubscribeEvent public static void jump(LivingEvent.LivingJumpEvent event) {
        LivingEntity e = event.getEntity();
        if (!e.level().isClientSide && has(e, HEAVY)) { Vec3 v = e.getDeltaMovement(); e.setDeltaMovement(v.x, Math.min(v.y, .15), v.z); e.hurtMarked = true; }
    }
    @SubscribeEvent public static void tick(LivingEvent.LivingTickEvent event) {
        LivingEntity e = event.getEntity();
        if (!(e.level() instanceof ServerLevel level) || !e.isAlive()) return;
        long now = level.getGameTime();
        if (has(e, ABSORPTION)) BrewingStatusEffects.trackAbsorption(e);
        if (!has(e, DYE)) e.getPersistentData().remove("forbidden_hex_brewing_color");
        if (has(e, ANTIDOTE)) e.removeEffect(MobEffects.POISON);
        if (has(e, EPIDEMIC_GUARD)) e.removeEffect(BrewingStatusEffects.get(PLAGUE));
        if (has(e, HEAVY) && e.getDeltaMovement().y > .1) { Vec3 v = e.getDeltaMovement(); e.setDeltaMovement(v.x, .1, v.z); e.hurtMarked = true; }
        if (has(e, FAST_FALLING) && !e.onGround() && e.getDeltaMovement().y < 0) e.setDeltaMovement(e.getDeltaMovement().add(0, -.06, 0));
        if (has(e, SINKING) && now % 2 == 0 && e.isEyeInFluid(FluidTags.WATER) && !e.hasEffect(MobEffects.WATER_BREATHING)) e.setAirSupply(e.getAirSupply() - 1);
        if (has(e, FROST)) {
            int strength = s(e, FROST);
            if (now % 40 == 0 && e.canFreeze() && !has(e, COLD_RESISTANCE)) secondary(() -> e.hurt(level.damageSources().freeze(), 1 + (strength - 1) / 2));
        }
        if (has(e, PLAGUE)) {
            MobEffectInstance original = e.getEffect(BrewingStatusEffects.get(PLAGUE));
            if (now % 40 == 0 && original != null) for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, e.getBoundingBox().inflate(3), target -> allowed(e, target))) {
                if (!has(other, PLAGUE) && !has(other, EPIDEMIC_GUARD) && e.distanceToSqr(other) <= 9)
                    other.addEffect(new MobEffectInstance(original.getEffect(), original.getDuration(), original.getAmplifier()), e);
            }
        }
        if (e instanceof Player player) {
            int nourish = s(e, NOURISH), famine = s(e, FAMINE);
            if (nourish > 0 && now % (70 - 10 * nourish) == 0) player.getFoodData().setFoodLevel(Math.min(20, player.getFoodData().getFoodLevel() + 1));
            if (famine > 0 && now % (70 - 10 * famine) == 0) {
                float saturation = player.getFoodData().getSaturationLevel();
                if (saturation > 0) player.getFoodData().setSaturation(Math.max(0, saturation - 1));
                else player.getFoodData().setFoodLevel(Math.max(0, player.getFoodData().getFoodLevel() - 1));
            }
        }
        if (has(e, THORNS) && now % 20 == 0) for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, e.getBoundingBox().inflate(1), target -> allowed(e, target)))
            secondary(() -> other.hurt(level.damageSources().thorns(e), .5F + .5F * s(e, THORNS)));
        if (now % 5 == 0) {
            if (has(e, SNOW_TRAIL)) com.example.akaishi.forbidden.brewing.BrewingWorldEffects.snow(e);
            if (has(e, LAVA_REPULSION)) com.example.akaishi.forbidden.brewing.BrewingWorldEffects.tickAura(e, s(e, LAVA_REPULSION));
            if (has(e, SCORCH_TRAIL)) com.example.akaishi.forbidden.brewing.BrewingScorchTrails.emit(e);
        }
        if (has(e, GROWTH) && now % 40 == 0) com.example.akaishi.forbidden.brewing.BrewingWorldEffects.grow(e, s(e, GROWTH));
    }
}
