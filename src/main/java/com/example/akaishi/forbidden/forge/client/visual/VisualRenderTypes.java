package com.example.akaishi.forbidden.forge.client.visual;

import com.example.akaishi.forbidden.visual.VisualMaterial;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Material plus texture is the complete cache key; tint and opacity belong to vertices. */
public final class VisualRenderTypes extends RenderType {
    private record Key(VisualMaterial material, ResourceLocation texture) { }

    private static final Map<Key, RenderType> CACHE = new HashMap<>();
    private static final TransparencyStateShard ADDITIVE = new TransparencyStateShard("forbidden_hex_additive", () -> {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
    }, () -> {
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    });

    private VisualRenderTypes() {
        super("forbidden_hex_visual", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
                256, false, true, () -> { }, () -> { });
    }

    public static RenderType material(ResourceLocation materialId, ResourceLocation texture) {
        Objects.requireNonNull(texture, "texture");
        VisualMaterial material = VisualMaterials.get(materialId);
        if (material.pipeline() == VisualMaterial.Pipeline.EMISSIVE_ALPHA || !ClientVisualShaders.available(material.shader())) {
            return RenderType.entityTranslucentEmissive(texture);
        }
        return CACHE.computeIfAbsent(new Key(material, texture), key -> create(
                "forbidden_hex_visual_" + CACHE.size(), DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
                1024, false, true, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> ClientVisualShaders.bind(key.material())))
                        .setTextureState(new TextureStateShard(key.texture(), false, false))
                        .setTransparencyState(ADDITIVE)
                        .setCullState(NO_CULL)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(COLOR_WRITE)
                        .setOutputState(MAIN_TARGET)
                        .createCompositeState(false)));
    }

    static void clearCache() {
        CACHE.clear();
    }
}
