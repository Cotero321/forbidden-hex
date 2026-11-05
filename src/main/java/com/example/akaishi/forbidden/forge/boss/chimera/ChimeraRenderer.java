package com.example.akaishi.forbidden.forge.boss.chimera;

import com.example.akaishi.forbidden.boss.chimera.ChimeraEntity;
import com.example.akaishi.forbidden.boss.chimera.ChimeraHead;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class ChimeraRenderer extends GeoEntityRenderer<ChimeraEntity> {
    public ChimeraRenderer(EntityRendererProvider.Context context) { super(context, new ChimeraModel()); this.shadowRadius = 1.3F; }

    @Override
    public void render(ChimeraEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        var resources = Minecraft.getInstance().getResourceManager();
        if (resources.getResource(getGeoModel().getModelResource(entity)).isPresent()
                && resources.getResource(getGeoModel().getTextureResource(entity)).isPresent()
                && resources.getResource(getGeoModel().getAnimationResource(entity)).isPresent()) {
            super.render(entity, yaw, partialTick, poseStack, buffers, light);
            return;
        }
        var vertices = buffers.getBuffer(RenderType.lines());
        AABB body = entity.getBoundingBox().move(entity.position().scale(-1D));
        LevelRenderer.renderLineBox(poseStack, vertices, body, 1F, 0.65F, 0.15F, 1F);
        for (ChimeraHead head : ChimeraHead.values()) {
            Vec3 origin = entity.headPosition(head).subtract(entity.position());
            AABB bounds = new AABB(origin.subtract(0.65D, 0.55D, 0.65D), origin.add(0.65D, 0.55D, 0.65D));
            LevelRenderer.renderLineBox(poseStack, vertices, bounds, 1F, entity.isHeadDisabled(head) ? 1F : 0.2F, 0.2F, 1F);
        }
        if (entity.distanceToSqr(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()) < 4096D) {
            renderNameTag(entity, Component.literal("Chimera [model pending]"), poseStack, buffers, light);
        }
    }
}
