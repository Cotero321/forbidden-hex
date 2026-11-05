package com.example.akaishi.forbidden.brewing.client;

import com.example.akaishi.forbidden.brewing.BrewingEffectType;
import com.example.akaishi.forbidden.brewing.BrewingStatusEffects;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.DyeColor;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

public final class BrewingGeoTintLayer<T extends LivingEntity & GeoAnimatable> extends GeoRenderLayer<T> {
    public BrewingGeoTintLayer(GeoEntityRenderer<T> renderer) { super(renderer); }

    @Override
    public void render(PoseStack pose, T entity, BakedGeoModel model, RenderType renderType,
                       MultiBufferSource buffers, VertexConsumer buffer, float partialTick,
                       int light, int overlay) {
        if (BrewingFumeClientEffects.shouldRenderDouble(entity)) {
            var offset = BrewingFumeClientEffects.doubleOffset(entity, partialTick);
            RenderType doubled = RenderType.entityTranslucent(getTextureResource(entity));
            pose.pushPose();
            pose.translate(offset.x, offset.y, offset.z);
            getRenderer().reRender(model, pose, buffers, entity, doubled, buffers.getBuffer(doubled),
                    partialTick, light, overlay, .85F, .8F, 1F, .2F);
            pose.popPose();
        }
        var dye = BrewingStatusEffects.get(BrewingEffectType.DYE);
        var effect = dye == null ? null : entity.getEffect(dye);
        if (effect == null || entity.isInvisible()) return;
        float[] rgb = DyeColor.byId(effect.getAmplifier()).getTextureDiffuseColors();
        RenderType tint = RenderType.entityTranslucent(getTextureResource(entity));
        pose.pushPose();
        pose.scale(1.002F, 1.002F, 1.002F);
        getRenderer().reRender(model, pose, buffers, entity, tint, buffers.getBuffer(tint),
                partialTick, light, overlay, rgb[0], rgb[1], rgb[2], 0.65F);
        pose.popPose();
    }
}
