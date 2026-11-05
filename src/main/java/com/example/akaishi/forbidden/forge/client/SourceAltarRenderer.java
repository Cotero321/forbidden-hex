package com.example.akaishi.forbidden.forge.client;

import com.example.akaishi.forbidden.altar.SourceAltarBlock;
import com.example.akaishi.forbidden.altar.SourceAltarBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;

public final class SourceAltarRenderer implements BlockEntityRenderer<SourceAltarBlockEntity> {
    public SourceAltarRenderer(BlockEntityRendererProvider.Context context) { }
    @Override public void render(SourceAltarBlockEntity altar, float partialTick, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        var stack = altar.display();
        if (stack.isEmpty() || altar.getLevel() == null) return;
        poses.pushPose();
        poses.translate(.5, altar.getBlockState().getValue(SourceAltarBlock.FORMED) ? 1.8 : 1.02, .5);
        poses.mulPose(Axis.YP.rotationDegrees((altar.getLevel().getGameTime() + partialTick) * .8f));
        poses.scale(.45f, .45f, .45f);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light, overlay,
                poses, buffers, altar.getLevel(), (int) altar.getBlockPos().asLong());
        poses.popPose();
    }
}
