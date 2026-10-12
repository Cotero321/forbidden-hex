package com.example.akaishi.forbidden.brewing;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

import java.util.Optional;

public class BrewingPotionProjectile extends ThrowableItemProjectile {
    public BrewingPotionProjectile(EntityType<? extends BrewingPotionProjectile> type, Level level) {
        super(type, level);
    }

    @Override protected Item getDefaultItem() { return BrewingItems.POTION.get(); }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return BrewingPotionData.read(getItem()).map(data -> !data.triggerPressurePlates()).orElse(true);
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (level().isClientSide) return;
        Optional<BrewingPotionData> properties = BrewingPotionData.read(getItem());
        if (properties.isPresent() && properties.get().delivery() != BrewingPotionData.Delivery.DRINK) {
            BrewingPotionData data = properties.get();
            if (data.delivery() == BrewingPotionData.Delivery.LINGERING) {
                BrewingEffectCloud cloud = BrewingEntities.CLOUD.get().create(level());
                if (cloud == null) { discard(); return; }
                cloud.setPos(getX(), getY(), getZ());
                cloud.setHideParticles(data.hideParticles());
                if (getOwner() instanceof LivingEntity owner) cloud.setOwner(owner);
                cloud.setRadius(3);
                cloud.setRadiusOnUse(-0.5F);
                cloud.setWaitTime(10);
                cloud.setDuration(600);
                cloud.setRadiusPerTick(-cloud.getRadius() / cloud.getDuration());
                for (var effect : data.effects()) {
                    cloud.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect.getEffect(),
                            effect.getDuration(), effect.getAmplifier(), effect.isAmbient(), !data.hideParticles()));
                }
                level().addFreshEntity(cloud);
            } else {
                Entity direct = hit instanceof net.minecraft.world.phys.EntityHitResult entityHit ? entityHit.getEntity() : null;
                for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(4, 2, 4))) {
                    double distance = distanceToSqr(target);
                    if (target == direct || distance < 16) data.apply(target, target == direct ? 1 : 1 - Math.sqrt(distance) / 4);
                }
            }
        }
        discard();
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > 1200) discard();
    }
}
