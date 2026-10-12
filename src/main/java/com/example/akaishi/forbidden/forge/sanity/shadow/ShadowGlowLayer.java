package com.example.akaishi.forbidden.forge.sanity.shadow;

import com.example.akaishi.forbidden.sanity.shadow.ShadowEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * 影怪的<b>自发光图层</b>：把 {@code shadow_glow.png}（只保留眼睛）按同一套 UV 覆在本体之上，
 * 得到"暗处的眼睛自己亮着"的效果。写法与 {@code AgaitolosGlowLayer} 逐行同构（本项目的既有口径）。
 *
 * <p><b>API 依据</b>（GeckoLib 4.4.9，与本项目既有的发光层同源，非印象）：
 * <ul>
 *   <li>图层基类 {@code GeoRenderLayer}，注册入口 {@code GeoEntityRenderer#addRenderLayer}
 *       （在 {@link ShadowRenderer} 的构造器里注册 —— 图层的先后顺序属于渲染器自身的构成）；</li>
 *   <li>重绘走 {@code GeoRenderer#reRender}：按当前骨架姿态重放一遍，而不是再套一层独立变换 ——
 *       这是"同 UV 覆遮罩"能对得上的前提（遮罩与本体共用同一份 geo 与同一批骨骼姿态）；</li>
 *   <li>不走官方 {@code AutoGlowingGeoLayer}：它按 {@code <本体贴图>_glowmask.png} 的固定后缀找遮罩，
 *       与建模侧已定的 {@code shadow_glow.png} 命名不符。</li>
 * </ul>
 *
 * <p><b>为什么用 {@code RenderType.entityTranslucentEmissive}</b>：该类型只挂
 * {@code rendertype_entity_translucent_emissive} 着色器、<b>没有</b>光照贴图状态 ⇒ 亮度不随环境光变化
 * （暗处照常发光，这正是"只让眼睛在暗处发亮"的落点），同时走 alpha 混合 ⇒ 遮罩里 alpha=0 的像素
 * 贡献恒为 0（非发光区完全不可见），并且只写颜色不写深度 ⇒ 不会在自己的深度上留下"吃掉本体"的面。
 *
 * <p>本类只允许出现在客户端加载路径上（由 {@link ShadowRenderer} 注册），<b>不得</b>被 common 引用。
 */
public class ShadowGlowLayer extends GeoRenderLayer<ShadowEntity> {

    /** 重绘用的打包光照值（方块光 0 / 天空光 15）；该着色器本身不吃光照贴图，此值只为与上游写法对齐 */
    private static final int FULL_BRIGHT_LIGHT = 15728640;

    public ShadowGlowLayer(GeoEntityRenderer<ShadowEntity> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, ShadowEntity animatable, BakedGeoModel bakedModel, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight,
                       int packedOverlay) {
        ResourceLocation glowTexture = ShadowModel.glowTexture();
        RenderType emissiveType = RenderType.entityTranslucentEmissive(glowTexture);
        getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, emissiveType,
                bufferSource.getBuffer(emissiveType), partialTick, FULL_BRIGHT_LIGHT, OverlayTexture.NO_OVERLAY,
                1.0F, 1.0F, 1.0F, 1.0F);
    }
}
