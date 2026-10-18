package com.example.akaishi.forbidden.forge.magic.visual;

import com.example.akaishi.forbidden.forge.client.visual.ClientVisualShaders;
import com.example.akaishi.forbidden.forge.client.visual.VisualMaterials;
import com.example.akaishi.forbidden.forge.client.visual.VisualRenderTypes;
import com.example.akaishi.forbidden.visual.VisualMaterial;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Compatibility entry points for spell, projectile-trail and brewing renderers. */
public final class SpellRenderTypes {
    private SpellRenderTypes() { }

    public static RenderType glow(ResourceLocation texture) {
        return material(VisualMaterials.GLOW, texture);
    }

    public static RenderType flow(ResourceLocation texture) {
        return material(VisualMaterials.FLOW, texture);
    }

    public static RenderType material(ResourceLocation materialId, ResourceLocation texture) {
        return VisualRenderTypes.material(materialId, texture);
    }

    public static boolean flowEnabled() {
        VisualMaterial material = VisualMaterials.get(VisualMaterials.FLOW);
        return material.pipeline() == VisualMaterial.Pipeline.ENERGY_ADDITIVE
                && ClientVisualShaders.available(material.shader())
                && Math.abs(ClientVisualShaders.scalarValue(material, "FlowSpeed")) > 0.0001f;
    }
}
