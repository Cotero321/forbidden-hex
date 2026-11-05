package com.example.akaishi.forbidden.forge.boss.agaitolos;

import com.example.akaishi.forbidden.boss.agaitolos.entity.AgaitolosHexOrb;
import com.example.akaishi.forbidden.forge.client.visual.ClientVisualBudget;
import com.example.akaishi.forbidden.forge.magic.visual.SpellVfx;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public final class AgaitolosHexOrbRenderer extends EntityRenderer<AgaitolosHexOrb> {
    public AgaitolosHexOrbRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(AgaitolosHexOrb orb, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        float detail = ClientVisualBudget.detailScale(orb.distanceToSqr(entityRenderDispatcher.camera.getPosition()));
        if (detail <= 0f) return;
        AgaitolosEnergyVisuals.orb(pose, buffers, entityRenderDispatcher.cameraOrientation(),
                0.27f, (orb.tickCount + partialTick) / 20f, 1f, detail);
        super.render(orb, yaw, partialTick, pose, buffers, packedLight);
    }

    @Override
    public boolean shouldRender(AgaitolosHexOrb orb, Frustum frustum, double x, double y, double z) {
        return ClientVisualBudget.detailScale(orb.distanceToSqr(x, y, z)) > 0f
                && frustum.isVisible(orb.getBoundingBox().inflate(0.6));
    }

    @Override
    public ResourceLocation getTextureLocation(AgaitolosHexOrb orb) {
        return SpellVfx.COMET;
    }
}
