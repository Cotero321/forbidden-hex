package com.example.akaishi.forbidden.alchemy.client;

import com.example.akaishi.forbidden.alchemy.AlchemyMachineBlock;
import com.example.akaishi.forbidden.alchemy.AlchemyMachineBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.Blocks;

public final class AlchemyMachineRenderer implements BlockEntityRenderer<AlchemyMachineBlockEntity> {
    public AlchemyMachineRenderer(BlockEntityRendererProvider.Context context) { }
    @Override public void render(AlchemyMachineBlockEntity machine, float partialTick, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        if (!machine.kind().crusher() || machine.getLevel() == null) return;
        boolean moving = machine.getBlockState().getValue(AlchemyMachineBlock.WORKING);
        double phase = (machine.getLevel().getGameTime() + partialTick) * (machine.kind().powered() ? .9 : .45);
        poses.pushPose();
        poses.translate(.5, .39 + (moving ? .10 + .10 * Math.sin(phase) : .16), .5);
        poses.mulPose(Axis.ZP.rotationDegrees(moving ? (float) (Math.sin(phase) * 12) : 12));
        poses.scale(.15F, .50F, .15F);
        poses.translate(-.5, 0, -.5);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Blocks.POLISHED_ANDESITE.defaultBlockState(), poses, buffers, light, overlay);
        poses.popPose();
    }
}
