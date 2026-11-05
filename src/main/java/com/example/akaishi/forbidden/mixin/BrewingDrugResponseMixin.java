package com.example.akaishi.forbidden.mixin;

import com.example.akaishi.forbidden.brewing.BrewingFumePharmacology;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class BrewingDrugResponseMixin {
    @ModifyVariable(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private MobEffectInstance forbiddenHex$adjustIncomingDrug(MobEffectInstance incoming) {
        return BrewingFumePharmacology.adjustIncoming((LivingEntity) (Object) this, incoming);
    }
}
