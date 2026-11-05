package com.example.akaishi.forbidden.forge.boss.chimera;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.boss.chimera.ChimeraEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class ChimeraModel extends GeoModel<ChimeraEntity> {
    @Override public ResourceLocation getModelResource(ChimeraEntity entity) { return id("geo/entity/chimera.geo.json"); }
    @Override public ResourceLocation getTextureResource(ChimeraEntity entity) { return id("textures/entity/chimera.png"); }
    @Override public ResourceLocation getAnimationResource(ChimeraEntity entity) { return id("animations/entity/chimera.animation.json"); }
    private static ResourceLocation id(String path) { return new ResourceLocation(AkaishiMod.MOD_ID, path); }
}