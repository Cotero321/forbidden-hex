package com.example.akaishi.forbidden.forge.alchemy;

import com.example.akaishi.forbidden.brewing.AlchemyPotionEffects;
import com.example.akaishi.forbidden.brewing.BrewingWorldEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityStruckByLightningEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "forbidden_hex")
public final class AlchemyCombatEvents {
    private AlchemyCombatEvents() { }
    @SubscribeEvent public static void explosion(ExplosionEvent.Detonate event) {
        event.getAffectedEntities().removeIf(entity -> entity instanceof LivingEntity living && AlchemyPotionEffects.protectsFromExplosion(living));
    }
    @SubscribeEvent public static void lightning(EntityStruckByLightningEvent event) {
        if (!event.getLightning().getPersistentData().getBoolean("forbidden_hex_alchemy_lightning")) return;
        if (event.getEntity() instanceof LivingEntity living) {
            var owner = event.getLightning().getCause();
            if (owner == null && living instanceof Player || BrewingWorldEffects.protectedPlayer(owner, living)) event.setCanceled(true);
        }
    }
}
