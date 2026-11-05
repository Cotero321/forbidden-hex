package com.example.akaishi.forbidden.mixin;

import com.example.akaishi.forbidden.brewing.BrewingEffectType;
import com.example.akaishi.forbidden.brewing.BrewingFumeMovement;
import com.example.akaishi.forbidden.brewing.BrewingStatusEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class BrewingJumpControlMixin {
    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
    private void forbiddenHex$restrictJump(CallbackInfo callback) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (BrewingFumeMovement.restrictsMovement(entity) || BrewingStatusEffects.has(entity, BrewingEffectType.GRAVITY_FIELD)) callback.cancel();
    }

    @Inject(method = "jumpFromGround", at = @At("TAIL"))
    private void forbiddenHex$viscousJump(CallbackInfo callback) {
        LivingEntity entity = (LivingEntity) (Object) this;
        int strength = BrewingStatusEffects.strength(entity, BrewingEffectType.VISCOSITY);
        if (strength > 0) {
            var motion = entity.getDeltaMovement();
            entity.setDeltaMovement(motion.x, motion.y * (0.8 - 0.1 * strength), motion.z);
        }
    }
}
