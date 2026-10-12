package com.example.akaishi.forbidden.forge.sanity.shadow;

import com.example.akaishi.forbidden.sanity.shadow.ShadowEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 影怪实体渲染器：GeckoLib 几何动画 + 一层自发光（{@link ShadowGlowLayer}）。
 * <p>发光层在构造器内注册（与 {@code AgaitolosRenderer} 同一取舍：附加图层属于渲染器自身的构成，
 * 外部注册会多出一份"谁先谁后"的隐式约定）。
 */
public class ShadowRenderer extends GeoEntityRenderer<ShadowEntity> {

    public ShadowRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new ShadowModel());
        addRenderLayer(new ShadowGlowLayer(this));
    }
}
