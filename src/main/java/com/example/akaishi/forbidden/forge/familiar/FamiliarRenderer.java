package com.example.akaishi.forbidden.forge.familiar;

import com.example.akaishi.forbidden.familiar.entity.FamiliarEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** GeckoLib renderer with template and affinity selected by synchronized entity state. */
public final class FamiliarRenderer extends GeoEntityRenderer<FamiliarEntity> {
    public FamiliarRenderer(EntityRendererProvider.Context context) {
        super(context, new FamiliarModel());
        this.shadowRadius = 0.35F;
    }
}
