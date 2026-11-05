package com.example.akaishi.forbidden.mixin;

import com.example.akaishi.forbidden.brewing.BrewingEffectType;
import com.example.akaishi.forbidden.brewing.BrewingStatusEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class BrewingPerceptionMixin {
    @Inject(method = "isInvisibleTo", at = @At("HEAD"), cancellable = true)
    private void forbiddenHex$discernInvisible(Player observer, CallbackInfoReturnable<Boolean> result) {
        Entity entity = (Entity) (Object) this;
        if (observer == null || observer == entity || !entity.isInvisible()
                || !(entity instanceof LivingEntity) || entity.isSpectator()) return;
        int strength = BrewingStatusEffects.strength(observer, BrewingEffectType.LUCIDITY);
        int range = 4 + 2 * strength;
        if (strength > 0 && observer.distanceToSqr(entity) <= range * range && observer.hasLineOfSight(entity)) {
            result.setReturnValue(false);
        }
    }
}
