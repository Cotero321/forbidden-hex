package com.example.akaishi.forbidden.mixin;

import com.example.akaishi.forbidden.brewing.BrewingEffectType;
import com.example.akaishi.forbidden.brewing.BrewingStatusEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AreaEffectCloud.class)
public abstract class BrewingCloudImmunityMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;isAffectedByPotions()Z"))
    private boolean forbiddenHex$canReceiveCloud(LivingEntity target) {
        return target.isAffectedByPotions() && !BrewingStatusEffects.has(target, BrewingEffectType.LINGERING_IMMUNITY);
    }
}
