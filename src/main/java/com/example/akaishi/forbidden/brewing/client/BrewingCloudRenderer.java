package com.example.akaishi.forbidden.brewing.client;

import com.example.akaishi.forbidden.brewing.BrewingEffectCloud;
import com.example.akaishi.forbidden.forge.client.ForbiddenVisualConfig;
import com.example.akaishi.forbidden.forge.magic.visual.SpellVfx;
import com.example.akaishi.forbidden.forge.magic.visual.SpellVisualPainter;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public final class BrewingCloudRenderer extends EntityRenderer<BrewingEffectCloud> {
    public BrewingCloudRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(BrewingEffectCloud entity, float yaw, float partialTick,
            PoseStack pose, MultiBufferSource buffers, int light) {
        if (!ForbiddenVisualConfig.BREWING_EFFECTS.get() || entity.hideParticles() || entity.isWaiting()) return;
        double range = ForbiddenVisualConfig.EFFECT_DISTANCE.get();
        if (entity.distanceToSqr(entityRenderDispatcher.camera.getPosition()) > range * range) return;
        float radius = entity.getRadius();
        if (radius < 0.1f) return;
        float time = (entity.tickCount + partialTick) / 20f;
        float fade = Math.min(1f, radius / 0.65f);
        int rgb = entity.getColor();
        int wisps = Minecraft.getInstance().options.particles().get() == ParticleStatus.MINIMAL ? 3 : 7;
        for (int i = 0; i < wisps; i++) {
            double angle = time * (i % 2 == 0 ? 0.22d : -0.18d) + Math.PI * 2d * i / wisps;
            double orbit = radius * (0.2d + 0.5d * ((i % 3) / 2d));
            pose.pushPose();
            pose.translate(Math.cos(angle) * orbit, 0.08d + 0.05d * Math.sin(time + i), Math.sin(angle) * orbit);
            SpellVisualPainter.flatQuad(buffers, pose, SpellVfx.BREWING_MIST,
                    entityRenderDispatcher.cameraOrientation(), Math.max(0.15f, radius * 0.38f),
                    (0.1f + 0.035f * (float) Math.sin(time * 1.7f + i)) * fade, rgb);
            pose.popPose();
        }
        pose.pushPose();
        pose.translate(0d, 0.025d, 0d);
        pose.mulPose(Axis.XP.rotationDegrees(-90f));
        pose.mulPose(Axis.ZP.rotationDegrees(time * 6f));
        SpellVisualPainter.flatQuad(buffers, pose, SpellVfx.BREWING_RIPPLE, null, radius, 0.12f * fade, rgb);
        pose.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(BrewingEffectCloud entity) {
        return SpellVfx.BREWING_MIST;
    }
}
