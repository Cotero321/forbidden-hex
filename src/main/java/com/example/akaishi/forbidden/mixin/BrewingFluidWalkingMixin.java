package com.example.akaishi.forbidden.mixin;

import com.example.akaishi.forbidden.brewing.BrewingEffectType;
import com.example.akaishi.forbidden.brewing.BrewingStatusEffects;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class BrewingFluidWalkingMixin {
    @Inject(method = "canStandOnFluid", at = @At("HEAD"), cancellable = true)
    private void forbiddenHex$walkOnWater(FluidState fluid, CallbackInfoReturnable<Boolean> result) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (fluid.is(FluidTags.WATER) && !entity.isShiftKeyDown()
                && BrewingStatusEffects.has(entity, BrewingEffectType.WATER_WALKING)) result.setReturnValue(true);
    }
}
