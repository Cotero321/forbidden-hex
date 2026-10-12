package com.example.akaishi.forbidden.forge.client;

import com.example.akaishi.forbidden.block.AkaishiMotherAltarBlock;
import com.example.akaishi.forbidden.block.entity.AkaishiMotherAltarBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 母神祭坛渲染器：把供奉物渲染于祭坛顶面之上——缓慢自转 + 轻微起伏，
 * 营造"献祭之物悬于母神之前"的氛围。客户端专用，渲染数据来自 BE 同步的 NBT。
 * <p>未成型：方块顶台面 0.875 格，悬浮点取方块中心；
 * 成型：中心 2×2 四座按 {@link AkaishiMotherAltarBlock#CORNER} 拼接为一座巨坛，
 * 每座只渲染四分之一，台面升至 1.8125 格，悬浮点须平移到四座交界处的巨坛台面中心，
 * 否则供奉物会沉在单座方块内部。
 */
public class MotherAltarRenderer implements BlockEntityRenderer<AkaishiMotherAltarBlockEntity> {

    /** 未成型：方块顶台面（0.875）上方悬浮高度 */
    private static final double PLAIN_Y = 1.12D;
    /** 成型：巨坛红毯台面（1.8125）上方悬浮高度 */
    private static final double FORMED_Y = 1.95D;

    public MotherAltarRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(AkaishiMotherAltarBlockEntity altar, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        ItemStack offering = altar.getOffering();
        if (offering.isEmpty()) {
            return;
        }
        BlockState state = altar.getBlockState();
        boolean formed = state.hasProperty(AkaishiMotherAltarBlock.FORMED)
                && state.getValue(AkaishiMotherAltarBlock.FORMED);
        // 悬浮点默认位于本方块中心；成型时按象限平移到四座交界处（巨坛台面中心）
        double x = 0.5D;
        double z = 0.5D;
        double y = PLAIN_Y;
        if (formed) {
            int corner = state.getValue(AkaishiMotherAltarBlock.CORNER);
            x = (corner == 0 || corner == 2) ? 1.0D : 0.0D;
            z = corner >= 2 ? 0.0D : 1.0D;
            y = FORMED_Y;
        }
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        long time = altar.getLevel() != null ? altar.getLevel().getGameTime() : 0L;
        float t = time + partialTick;
        // 缓慢自转
        poseStack.mulPose(Axis.YP.rotationDegrees((t * 1.4F) % 360.0F));
        // 轻微起伏
        poseStack.translate(0.0D, Math.sin(t * 0.05D) * 0.06D, 0.0D);
        poseStack.scale(0.6F, 0.6F, 0.6F);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            poseStack.popPose();
            return;
        }
        BlockPos lightPos = altar.getBlockPos().offset((int) x, 0, (int) z);
        int light = altar.getLevel() != null
                ? LevelRenderer.getLightColor(altar.getLevel(), lightPos.above())
                : packedLight;
        mc.getItemRenderer().renderStatic(offering, ItemDisplayContext.FIXED, light,
                packedOverlay, poseStack, bufferSource, mc.level, 0);
        poseStack.popPose();
    }
}
