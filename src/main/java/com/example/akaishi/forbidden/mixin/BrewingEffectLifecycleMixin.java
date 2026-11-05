package com.example.akaishi.forbidden.mixin;

import com.example.akaishi.forbidden.brewing.BrewingFumePharmacology;
import com.example.akaishi.forbidden.boss.chimera.ChimeraSkillDirector;
import com.example.akaishi.forbidden.boss.chimera.ChimeraEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class BrewingEffectLifecycleMixin {
    @Shadow protected float lastHurt;

    @Inject(method = "tick", at = @At("TAIL"))
    private void forbiddenHex$chimeraBleedingTick(CallbackInfo callback) {
        ChimeraSkillDirector.tickBleeding((LivingEntity) (Object) this);
    }

    @Inject(method = "hurt", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/entity/LivingEntity;lastHurt:F", opcode = 181))
    private void forbiddenHex$captureChimeraBleedGate(DamageSource source, float amount, CallbackInfoReturnable<Boolean> callback) {
        ChimeraSkillDirector.captureBleedGate((LivingEntity) (Object) this, source, lastHurt);
    }

    @Inject(method = "hurt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V"))
    private void forbiddenHex$restoreChimeraBleedGate(DamageSource source, float amount, CallbackInfoReturnable<Boolean> callback) {
        Float previous = ChimeraSkillDirector.restoreBleedGate((LivingEntity) (Object) this, source);
        if (previous != null) lastHurt = previous;
    }

    @Inject(method = "onEffectRemoved", at = @At("TAIL"))
    private void forbiddenHex$effectRemoved(MobEffectInstance effect, CallbackInfo callback) {
        BrewingFumePharmacology.removed((LivingEntity) (Object) this,
                BrewingFumePharmacology.typeOf(effect.getEffect()));
    }

    @Redirect(method = "actuallyHurt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;setHealth(F)V"))
    private void forbiddenHex$settleGreedDamage(LivingEntity victim, float health, DamageSource source, float amount) {
        float previousHealth = victim.getHealth();
        victim.setHealth(health);
        ChimeraEntity.settleGreedHealthDamage(victim, source, previousHealth);
    }
}
