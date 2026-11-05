package com.example.akaishi.forbidden.brewing.client;

import com.example.akaishi.forbidden.brewing.BrewingEffectType;
import com.example.akaishi.forbidden.brewing.BrewingStatusEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import com.example.akaishi.forbidden.forge.client.visual.ClientVisualBudget;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "forbidden_hex", value = Dist.CLIENT)
public final class BrewingFumeClientEffects {
    private static int used;

    private BrewingFumeClientEffects() { }

    public static int hallucinationStrength() {
        var player = Minecraft.getInstance().player;
        if (player == null || BrewingStatusEffects.has(player, BrewingEffectType.LUCIDITY)) return 0;
        return BrewingStatusEffects.strength(player, BrewingEffectType.HALLUCINATION);
    }

    public static boolean shouldRenderDouble(LivingEntity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        int strength = hallucinationStrength();
        if (strength == 0 || entity.isInvisible() || entity.isSpectator() || minecraft.player == null
                || entity == minecraft.player || entity.distanceToSqr(minecraft.player) > 24 * 24
                || !minecraft.player.hasLineOfSight(entity)) return false;
        float detail = ClientVisualBudget.detailScale(minecraft.level, entity.position());
        return detail > 0 && used++ < Math.max(1, (int) (12 * detail));
    }

    public static Vec3 doubleOffset(LivingEntity entity, float partialTick) {
        int strength = hallucinationStrength();
        double time = (entity.tickCount + partialTick) * .055 + entity.getId() * 1.7;
        double distance = .12 + .035 * strength;
        return new Vec3(Math.sin(time) * distance, .025 * Math.sin(time * .7), Math.cos(time) * distance);
    }

    @SubscribeEvent
    public static void frame(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY
                || event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) used = 0;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        int strength = hallucinationStrength();
        if (minecraft.level == null || minecraft.player == null || minecraft.isPaused() || strength == 0) {
            used = 0;
            return;
        }
        if (minecraft.player.tickCount % (140 - 12 * strength) != 0) return;
        double angle = minecraft.level.random.nextDouble() * Math.PI * 2;
        Vec3 origin = minecraft.player.position().add(Math.cos(angle) * 4, 1, Math.sin(angle) * 4);
        minecraft.level.playLocalSound(origin.x, origin.y, origin.z, SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.AMBIENT, .1F, .65F + minecraft.level.random.nextFloat() * .4F, false);
    }
}
