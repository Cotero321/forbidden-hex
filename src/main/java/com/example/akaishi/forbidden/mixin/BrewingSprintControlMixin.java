package com.example.akaishi.forbidden.mixin;

import com.example.akaishi.forbidden.brewing.BrewingFumeMovement;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class BrewingSprintControlMixin {
    @Inject(method = "setSprinting", at = @At("HEAD"), cancellable = true)
    private void forbiddenHex$restrictSprint(boolean sprinting, CallbackInfo callback) {
        if (sprinting && BrewingFumeMovement.restrictsMovement((LivingEntity) (Object) this)) callback.cancel();
    }
}
