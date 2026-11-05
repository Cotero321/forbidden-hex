package com.example.akaishi.forbidden.forge.boss.chimera;

import com.example.akaishi.forbidden.boss.chimera.ChimeraHeadHitbox;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;

public final class ChimeraHeadRenderer extends EntityRenderer<ChimeraHeadHitbox> {
    public ChimeraHeadRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public void render(ChimeraHeadHitbox entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) { }
    @Override public ResourceLocation getTextureLocation(ChimeraHeadHitbox entity) { return net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS; }
}
