package com.example.akaishi.forbidden.forge.client.visual;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.effect.UnnameableClientAmbience;
import com.example.akaishi.forbidden.forge.client.ForbiddenVisualConfig;
import com.example.akaishi.forbidden.magic.visual.SpellFeedback;
import com.example.akaishi.forbidden.magic.visual.SpellVisualEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AkaishiMod.MOD_ID, value = Dist.CLIENT)
public final class ClientSpellFeedback {
    private static final SpellFeedback.Pulse PULSE = new SpellFeedback.Pulse();
    private static ClientLevel world;

    private ClientSpellFeedback() { }

    public static void trigger(SpellVisualEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        syncWorld(minecraft.level);
        if (!available(minecraft) || "ward".equals(event.visual().effectId())) return;
        double distance = minecraft.gameRenderer.getMainCamera().getPosition().distanceToSqr(event.to());
        PULSE.trigger(world.getGameTime(), SpellFeedback.strength(event.visual().power(), distance,
                event.from().distanceToSqr(minecraft.player.getEyePosition()) <= 4d));
    }

    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        syncWorld(minecraft.level);
        if (!available(minecraft)) {
            PULSE.clear();
            return;
        }
        float offset = PULSE.sample(world.getGameTime() + event.getPartialTick());
        event.setPitch(event.getPitch() + offset);
        event.setRoll(event.getRoll() + offset * 0.45f);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        syncWorld(minecraft.level);
        if (!available(minecraft)) PULSE.clear();
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        syncWorld(null);
    }

    public static void clear() {
        PULSE.clear();
    }

    private static boolean available(Minecraft minecraft) {
        return world != null && minecraft.player != null && minecraft.getCameraEntity() == minecraft.player
                && minecraft.screen == null && minecraft.getOverlay() == null && !minecraft.isPaused()
                && ForbiddenVisualConfig.SPELL_CAMERA_FEEDBACK.get() && UnnameableClientAmbience.level() <= 0.02f;
    }

    private static void syncWorld(ClientLevel next) {
        if (world == next) return;
        PULSE.clear();
        world = next;
    }
}
