package com.example.akaishi.forbidden.brewing.client;

import com.example.akaishi.forbidden.brewing.BrewingPotionData;
import com.example.akaishi.forbidden.brewing.BrewingPotionProjectile;
import com.example.akaishi.forbidden.forge.client.ForbiddenVisualConfig;
import com.example.akaishi.forbidden.forge.magic.visual.SpellVfx;
import com.example.akaishi.forbidden.forge.magic.visual.SpellVisualPainter;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

public final class BrewingPotionRenderer extends ThrownItemRenderer<BrewingPotionProjectile> {
    public BrewingPotionRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(BrewingPotionProjectile entity, float yaw, float partialTick,
            PoseStack pose, MultiBufferSource buffers, int light) {
        super.render(entity, yaw, partialTick, pose, buffers, light);
        if (!ForbiddenVisualConfig.BREWING_EFFECTS.get() || entity.tickCount < 2) return;
        BrewingPotionData.read(entity.getItem()).filter(data -> !data.hideParticles()).ifPresent(data -> {
            int rgb = data.color();
            float breath = 0.8f + 0.2f * (float) Math.sin((entity.tickCount + partialTick) * 0.35f);
            SpellVisualPainter.flatQuad(buffers, pose, SpellVfx.HALO, entityRenderDispatcher.cameraOrientation(),
                    0.2f, 0.16f * breath, rgb);
        });
    }
}
