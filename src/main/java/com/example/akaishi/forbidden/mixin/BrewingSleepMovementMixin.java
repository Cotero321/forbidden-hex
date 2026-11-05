package com.example.akaishi.forbidden.mixin;

import com.example.akaishi.forbidden.brewing.BrewingFumePharmacology;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class BrewingSleepMovementMixin {
    @ModifyVariable(method = "travel", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Vec3 forbiddenHex$sleepMovement(Vec3 movement) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (entity.level() instanceof ServerLevel && entity instanceof Mob && BrewingFumePharmacology.asleep(entity)) {
            Vec3 motion = entity.getDeltaMovement();
            entity.setDeltaMovement(0, Math.min(0, motion.y), 0);
            return Vec3.ZERO;
        }
        return movement;
    }
}
