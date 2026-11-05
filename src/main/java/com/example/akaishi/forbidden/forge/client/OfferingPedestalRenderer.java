package com.example.akaishi.forbidden.forge.client;

import com.example.akaishi.forbidden.altar.OfferingPedestalBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;

public final class OfferingPedestalRenderer implements BlockEntityRenderer<OfferingPedestalBlockEntity> {
    public OfferingPedestalRenderer(BlockEntityRendererProvider.Context context) { }
    @Override public void render(OfferingPedestalBlockEntity pedestal, float partialTick, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        var stack = pedestal.display();
        if (stack.isEmpty() || pedestal.getLevel() == null) return;
        float time = pedestal.getLevel().getGameTime() + partialTick;
        poses.pushPose();
        poses.translate(.5, 1.25 + Math.sin(time * .04) * .035, .5);
        poses.mulPose(Axis.YP.rotationDegrees(time));
        poses.scale(.5f, .5f, .5f);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light, overlay,
                poses, buffers, pedestal.getLevel(), (int) pedestal.getBlockPos().asLong());
        poses.popPose();
    }
}
