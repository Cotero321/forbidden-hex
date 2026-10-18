package com.example.akaishi.forbidden.forge.magic.particle;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.forge.client.visual.ClientVisualBudget;
import com.example.akaishi.forbidden.magic.circle.SpellCircleEntity;
import com.example.akaishi.forbidden.magic.particle.AkaishiMagicParticles;
import com.example.akaishi.forbidden.magic.table.MagicSource;
import com.example.akaishi.forbidden.magic.visual.SpellCircleAnimation;
import com.example.akaishi.forbidden.magic.visual.SpellCircleLayers;
import com.example.akaishi.forbidden.magic.visual.SpellVisual;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Decorative emissions use the spell particle library and its shared per-tick quota. */
@Mod.EventBusSubscriber(modid = AkaishiMod.MOD_ID, value = Dist.CLIENT)
public final class SpellCircleParticles {
    private SpellCircleParticles() { }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.isPaused() || level.getGameTime() % 2L != 0L) return;
        for (var entity : level.entitiesForRendering()) {
            if (!(entity instanceof SpellCircleEntity circle) || circle.isRemoved()
                    || circle.visualState() == SpellCircleEntity.VisualState.FADING
                    || circle.fadeAlpha() < 0.5f || circle.introProgress(0f) < 0.2f) continue;
            float detail = ClientVisualBudget.detailScale(level, circle.position());
            if (detail <= 0.5f) continue;
            emit(level, circle, detail);
        }
    }

    private static void emit(ClientLevel level, SpellCircleEntity circle, float detail) {
        SpellVisual visual = circle.visual();
        SpellCircleLayers layers = circle.layers();
        boolean ground = !circle.followsCaster();
        float radius = ground ? circle.effectRadius() : Math.max(0.7f, visual.baseSize() * 0.82f);
        float time = circle.animationTime(0f);
        float progress = circle.introProgress(0f);
        int count = detail > 0.72f ? 3 : 1;
        for (int particle = 0; particle < count; particle++) {
            int slot = Math.floorMod(circle.tickCount / 2 + particle * 3, SpellCircleLayers.SLOT_COUNT);
            int index = slot / 2;
            var layer = layers.layer(index);
            var data = layer.slot(slot % 2);
            if (!data.occupied() || SpellCircleAnimation.layerProgress(index, progress) < 0.5f) continue;
            Vec3 local = SpellCircleAnimation.point(index, time, ground, radius, progress,
                    90f + slot % 2 * 180f, 0.81f);
            Vec3 position = world(circle, local, ground).add(0d, ground ? 0.06d : 0d, 0d);
            Vec3 radial = world(circle, new Vec3(local.x, local.y, 0d), ground)
                    .subtract(circle.position()).normalize();
            Vec3 tangent = world(circle, new Vec3(-local.y, local.x, 0d), ground)
                    .subtract(circle.position()).normalize();
            Vec3 velocity = switch (visual.form().kind()) {
                case VORTEX, MIST -> tangent.scale(0.035d).add(radial.scale(-0.02d));
                case SPIKE, SHARD -> radial.scale(0.045d).add(0d, ground ? 0.025d : 0.008d, 0d);
                case RIPPLE -> radial.scale(visual.form().inverted() ? -0.026d : 0.026d);
                case SHELL, CHAIN -> new Vec3(0d, 0.014d, 0d);
                default -> radial.scale(0.012d).add(0d, 0.018d, 0d);
            };
            String inkId = layer.inkIds().get(slot % 2);
            if (inkId.isEmpty()) inkId = visual.inkId();
            if (!AkaishiMagicParticles.allowLocal(level, position.x, position.y, position.z)) return;
            level.addParticle(AkaishiMagicParticles.options(MagicSource.byIdOrDefault(visual.sourceId()),
                            inkId, visual.carrier(), data.inkRgb()),
                    position.x, position.y, position.z, velocity.x, velocity.y, velocity.z);
            if (particle == 0 && circle.tickCount % 6 == 0) {
                AkaishiMagicParticles.photon(level, data.inkRgb(), position.x, position.y, position.z,
                        velocity.x * 0.5d, velocity.y * 0.5d, velocity.z * 0.5d, 0.45f, 0.65f);
            }
        }
    }

    private static Vec3 world(SpellCircleEntity circle, Vec3 local, boolean ground) {
        if (ground) return circle.position().add(local.x, local.z, -local.y);
        double yaw = Math.toRadians(-circle.yaw(0f));
        return circle.position().add(local.x * Math.cos(yaw) + local.z * Math.sin(yaw), local.y,
                -local.x * Math.sin(yaw) + local.z * Math.cos(yaw));
    }
}
