package com.example.akaishi.forbidden.forge.boss.agaitolos;

import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosEntity;
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
 * 阿盖托洛丝的<b>自发光图层</b>（emissive）：把阶段化的发光遮罩按同一套 UV 覆在本体之上，
 * 得到"眼睛 / 浮颅眼窝与牙列 / 镰刀刃口与魂宝石 / 胸前亮块与翼膜破洞边缘"等区域恒亮的效果。
 * <p>
 * <b>采用的 API 与依据</b>（均以本机依赖 GeckoLib 4.4.9 的官方源码逐行核对，非凭记忆）：
 * <ul>
 *   <li>图层基类 {@code GeoRenderLayer<T extends GeoAnimatable>}，注册入口
 *       {@code GeoEntityRenderer#addRenderLayer(GeoRenderLayer)}（4.4.9 源码第 111 行，返回渲染器本身）；
 *       图层每帧在<b>本体之后</b>由 {@code GeoRenderer#applyRenderLayers} 回调
 *       （{@code render(...)}，见 {@code GeoRenderer} 第 207~212 行）。</li>
 *   <li>重绘走 {@code GeoRenderer#reRender(...)}（{@code GeoRenderer} 第 158~166 行）：
 *       它会按当前骨架姿态重放一遍渲染，<b>而不是</b>再套一层独立变换 —— 这正是"同 UV 覆遮罩"
 *       能对得上的前提（遮罩与本体共用同一份 geo 与同一批骨骼姿态）。</li>
 *   <li>不用官方 {@code AutoGlowingGeoLayer}／{@code AutoGlowingTexture}：它们按
 *       {@code <本体贴图>_glowmask.png} 的<b>固定后缀</b>找遮罩（{@code AutoGlowingTexture.APPENDIX = "_glowmask"}），
 *       还会把本体的发光区乘掉后回写，与建模侧已定的 {@code agaitolos_stage<N>_glow.png} 命名不符；
 *       这里只借用它同一处做法 —— 以 {@code reRender} + <b>全亮光照值</b>重绘（其 {@code AutoGlowingGeoLayer} 第 41~43 行
 *       传的正是 {@code 15728640} = {@code 0xF00000} 与 {@code OverlayTexture.NO_OVERLAY}）。</li>
 * </ul>
 * <p>
 * <b>为什么用 {@code RenderType.entityTranslucentEmissive}</b>（1.20.1 原版 {@code RenderType} 第 225~231 行，
 * 与 {@code eyeball} 系的 {@code RenderType.eyes} 是两个不同取舍）：
 * <ol>
 *   <li><b>不被环境光压暗</b>：该类型只挂 {@code rendertype_entity_translucent_emissive} 着色器，
 *       <b>没有</b> {@code setLightmapState(LIGHTMAP)}（对照第 70 行的 {@code entityCutoutNoCull} 等有），
 *       亮度不随光照贴图变化 ⇒ 黑暗处照常发光，不依赖调用方传参；</li>
 *   <li><b>alpha 混合（SRC_ALPHA / ONE_MINUS_SRC_ALPHA）</b>：遮罩里 alpha = 0 的像素贡献恒为 0
 *       ⇒ "非发光区完全不可见"，且<b>不会</b>受遮罩 RGB 残留色影响（这一点比 {@code RenderType.eyes} 安全：
 *       eyes 走 {@code ADDITIVE_TRANSPARENCY}，alpha=0 但 RGB 非黑的像素仍会加色发光）；</li>
 *   <li><b>只写颜色、不写深度（COLOR_WRITE）</b>：发光层不会在深度缓冲里留下自己的面，
 *       不会遮挡或"吃掉"本体与身后物体；同时深度测试仍在（合成状态默认 LEQUAL），
 *       所以该图层只在本体可见处叠加，不会透视出模型外轮廓。</li>
 *   <li>顺带一处有意接受的行为：该类型带 {@code NO_CULL}（<b>双面</b>渲染，与 GeckoLib 自带的
 *       {@code AutoGlowingTexture} 那个<u>开背面剔除</u>的自建类型不同）。对刃口、翼膜这类薄片而言
 *       正反两面都亮才是想要的（从背后看镰刀也应有刃光），而封闭躯干处背面的发光面会被本体自身的
 *       前向面挡住（深度测试仍然生效）⇒ 不需要为它另写一个自建 RenderType 去剔背面。</li>
 * </ol>
 * <p>
 * <b>阶段跟随</b>：遮罩资源由 {@link AgaitolosModel#glowTexture(AgaitolosEntity)} 按与本体贴图
 * <b>同一个阶段下标</b>（{@code phaseIndex}）给出，故换阶段时本体与遮罩必然同步切换，
 * 不会出现"已是阶段三却还亮着阶段一的眼睛"。
 * <p>
 * 本类只允许出现在客户端加载路径上（由 {@code AgaitolosRenderer} 构造函数注册），
 * <b>不得</b>被 common 的服务端代码引用。
 */
public class AgaitolosGlowLayer extends GeoRenderLayer<AgaitolosEntity> {

    /**
     * 重绘时使用的打包光照值：{@code 0xF00000}（方块光 0 / 天空光 15）。
     * <p>取 GeckoLib 官方 {@code AutoGlowingGeoLayer} 的同一个字面量；由于本图层所用的
     * {@code entityTranslucentEmissive} 着色器本身不吃光照贴图，该值只是"不给外界留下可变项"，
     * 换成 {@code 0} 观感也不变（留着是为了与上游做法逐字对齐、便于日后比对）。
     */
    private static final int FULL_BRIGHT_LIGHT = 15728640;

    public AgaitolosGlowLayer(GeoEntityRenderer<AgaitolosEntity> renderer) {
        super(renderer);
    }

    /**
     * 本体渲染完成后追加的一次"发光重绘"。
     * <p>
     * 缓冲区取自 {@code bufferSource.getBuffer(emissiveType)}：{@code MultiBufferSource} 内部按
     * RenderType 分桶，直接取对应桶即可，无需自己管理 {@code VertexConsumer}。
     */
    @Override
    public void render(PoseStack poseStack, AgaitolosEntity animatable, BakedGeoModel bakedModel, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight,
                       int packedOverlay) {
        ResourceLocation glowTexture = AgaitolosModel.glowTexture(animatable);
        RenderType emissiveType = RenderType.entityTranslucentEmissive(glowTexture);
        getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, emissiveType,
                bufferSource.getBuffer(emissiveType), partialTick, FULL_BRIGHT_LIGHT, OverlayTexture.NO_OVERLAY,
                1.0F, 1.0F, 1.0F, 1.0F);
    }
}
