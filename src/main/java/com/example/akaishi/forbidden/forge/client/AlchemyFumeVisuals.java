package com.example.akaishi.forbidden.forge.client;

import com.example.akaishi.forbidden.brewing.BrewingEffectType;
import com.example.akaishi.forbidden.brewing.BrewingStatusEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "forbidden_hex", value = Dist.CLIENT)
public final class AlchemyFumeVisuals {
    private AlchemyFumeVisuals() { }
    private static float strength() {
        var player = Minecraft.getInstance().player;
        if (player == null) return 0;
        float amount = 0;
        for (var type : new BrewingEffectType[]{BrewingEffectType.BEWILDERMENT, BrewingEffectType.DELIRIUM}) {
            MobEffectInstance effect = player.getEffect(BrewingStatusEffects.get(type));
            if (effect != null) amount = Math.max(amount, Math.min(1, effect.getDuration() / 20F) * (.7F + .075F * Math.min(4, effect.getAmplifier())));
        }
        return amount;
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles event) {
        float intensity = strength();
        if (intensity <= 0) return;
        double time = Minecraft.getInstance().player.tickCount + event.getPartialTick();
        event.setRoll(event.getRoll() + (float) (Math.sin(time * .11) * 4 * intensity));
        event.setPitch(event.getPitch() + (float) (Math.cos(time * .17) * 1.2 * intensity));
        event.setYaw(event.getYaw() + (float) (Math.sin(time * .13) * 1.2 * intensity));
    }
    @SubscribeEvent public static void fov(ViewportEvent.ComputeFov event) {
        if (!event.usedConfiguredFov()) return;
        float intensity = strength();
        if (intensity <= 0) return;
        double time = Minecraft.getInstance().player.tickCount + event.getPartialTick();
        event.setFOV(event.getFOV() + Math.sin(time * .15) * 4 * intensity);
    }
}
