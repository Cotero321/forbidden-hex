package com.example.akaishi.forbidden.brewing.client;

import com.example.akaishi.forbidden.brewing.BrewingEffectType;
import com.example.akaishi.forbidden.brewing.BrewingStatusEffects;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "forbidden_hex", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BrewingTintLayers {
    private BrewingTintLayers() { }

    @SubscribeEvent
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (var type : BuiltInRegistries.ENTITY_TYPE) {
            if (event.getEntityRenderer(type) instanceof LivingEntityRenderer renderer) renderer.addLayer(new TintLayer(renderer));
        }
        for (String skin : event.getSkins()) {
            if (event.getPlayerSkin(skin) instanceof LivingEntityRenderer renderer) renderer.addLayer(new TintLayer(renderer));
        }
    }

    private static final class TintLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
        private TintLayer(RenderLayerParent<T, M> parent) { super(parent); }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            if (BrewingFumeClientEffects.shouldRenderDouble(entity)) {
                var offset = BrewingFumeClientEffects.doubleOffset(entity, partialTick);
                pose.pushPose();
                pose.translate(offset.x, offset.y, offset.z);
                getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(getTextureLocation(entity))),
                        light, LivingEntityRenderer.getOverlayCoords(entity, 0), .85F, .8F, 1F, .2F);
                pose.popPose();
            }
            var dye = BrewingStatusEffects.get(BrewingEffectType.DYE);
            var effect = dye == null ? null : entity.getEffect(dye);
            if (effect == null || entity.isInvisible()) return;
            float[] rgb = DyeColor.byId(effect.getAmplifier()).getTextureDiffuseColors();
            pose.pushPose();
            pose.scale(1.002F, 1.002F, 1.002F);
            getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(getTextureLocation(entity))),
                    light, LivingEntityRenderer.getOverlayCoords(entity, 0), rgb[0], rgb[1], rgb[2], 0.65F);
            pose.popPose();
        }
    }
}
