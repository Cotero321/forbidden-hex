package com.example.akaishi.forbidden.forge.boss.agaitolos;

import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 阿盖托洛丝实体渲染器。
 * <p>
 * 现阶段在 GeckoLib 默认渲染之外只挂<b>一层自发光</b>（{@link AgaitolosGlowLayer}）：
 * 阶段化的光效是"这张皮本身该有的样子"，与骨骼动画同源，不需要另开一套粒子去补。
 * 缩放与阴影等其余表现仍留给后续渲染阶段（按 RULES §7 先图后码）。
 * <p>
 * 换模/换贴图不必改写这里：{@link AgaitolosModel} 已按阶段各自给出 geo / 贴图 / 动画，
 * 发光遮罩走同一套下标（见其 {@code glowTexture}），三者始终同档。
 */
public class AgaitolosRenderer extends GeoEntityRenderer<AgaitolosEntity> {

    public AgaitolosRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new AgaitolosModel());
        // 注册发光图层：图层容器由 GeoEntityRenderer 持有（4.4.9 源码第 50 / 111 行），
        // 本体渲染完成后由 GeoRenderer#applyRenderLayers 回调本图层（同文件第 207~212 行）。
        // 在这里而非外部注册：渲染器的附加图层属于渲染器自身的构成，外部注册会多出一份"谁先谁后"的隐式约定。
        addRenderLayer(new AgaitolosGlowLayer(this));
    }
}
