package com.example.akaishi.forbidden.forge.familiar;

import com.example.akaishi.forbidden.familiar.entity.FamiliarEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * Temporary safe renderer used until the first Blockbench/Geo template is promoted.
 * The entity remains fully server-side functional; this renderer prevents a missing
 * client renderer crash and deliberately renders no placeholder geometry.
 */
public final class FamiliarPlaceholderRenderer extends EntityRenderer<FamiliarEntity> {
    public FamiliarPlaceholderRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.35F;
    }

    @Override
    public ResourceLocation getTextureLocation(FamiliarEntity entity) {
        return new ResourceLocation("minecraft", "textures/misc/white.png");
    }
}
