package com.example.akaishi.forbidden.forge.familiar;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.familiar.core.FamiliarTemplateId;
import com.example.akaishi.forbidden.familiar.entity.FamiliarEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** Geo resource contract for template-specific familiar assets. */
public final class FamiliarModel extends GeoModel<FamiliarEntity> {
    @Override
    public ResourceLocation getModelResource(FamiliarEntity entity) {
        String template = entity.template() == FamiliarTemplateId.NONE ? "wolf" : entity.template().id();
        return id("geo/entity/familiar_" + template + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(FamiliarEntity entity) {
        String template = entity.template() == FamiliarTemplateId.NONE ? "wolf" : entity.template().id();
        String affinity = entity.affinity().isBlank() ? "base" : entity.affinity();
        return id("textures/entity/familiar/" + template + "_" + affinity + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(FamiliarEntity entity) {
        String template = entity.template() == FamiliarTemplateId.NONE ? "wolf" : entity.template().id();
        return id("animations/entity/familiar_" + template + ".animation.json");
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(AkaishiMod.MOD_ID, path);
    }
}
