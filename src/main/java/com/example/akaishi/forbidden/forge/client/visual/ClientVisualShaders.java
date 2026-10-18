package com.example.akaishi.forbidden.forge.client.visual;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.forge.client.ForbiddenVisualConfig;
import com.example.akaishi.forbidden.visual.VisualMaterial;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Registers additive NEW_ENTITY programs and resets their scalar parameters at batch submission. */
@Mod.EventBusSubscriber(modid = AkaishiMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientVisualShaders {
    public static final ResourceLocation ENERGY = new ResourceLocation(AkaishiMod.MOD_ID, "energy");
    private static final Map<ResourceLocation, Map<String, Float>> DEFINITIONS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, ShaderInstance> LOADED = new LinkedHashMap<>();
    private static boolean registrationClosed;

    static {
        register(ENERGY, Map.of("FlowSpeed", 0f, "GlowStrength", 1f,
                "PulseStrength", 0f, "PulseSpeed", 1.5f, "EdgeSoftness", 0f, "AlphaCutoff", 0.003f));
    }

    private ClientVisualShaders() { }

    /** Call during client initialization, before the first shader reload. */
    public static void register(ResourceLocation id, Map<String, Float> defaults) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(defaults, "defaults");
        if (registrationClosed) {
            throw new IllegalStateException("Visual shaders must be registered before the first shader reload");
        }
        if (defaults.size() > 32) {
            throw new IllegalArgumentException("A visual shader supports at most 32 scalar defaults");
        }
        defaults.forEach(VisualMaterial::validateUniform);
        if (DEFINITIONS.putIfAbsent(id, Map.copyOf(defaults)) != null) {
            throw new IllegalArgumentException("Duplicate visual shader: " + id);
        }
    }

    public static boolean registered(ResourceLocation id) {
        return DEFINITIONS.containsKey(id);
    }

    public static void validate(VisualMaterial material) {
        Map<String, Float> defaults = DEFINITIONS.get(material.shader());
        if (defaults == null || !defaults.keySet().containsAll(material.uniforms().keySet())) {
            throw new IllegalArgumentException("Unknown shader or scalar uniform in material: " + material.shader());
        }
    }

    public static boolean available(ResourceLocation id) {
        return ForbiddenVisualConfig.ENERGY_SHADERS.get() && LOADED.containsKey(id)
                && ClientShaderCompatibility.allowsCustomPrograms();
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        registrationClosed = true;
        LOADED.clear();
        VisualRenderTypes.clearCache();
        for (ResourceLocation id : DEFINITIONS.keySet()) {
            try {
                ShaderInstance shader = new ShaderInstance(event.getResourceProvider(), id, DefaultVertexFormat.NEW_ENTITY);
                try {
                    for (String name : DEFINITIONS.get(id).keySet()) {
                        Uniform uniform = shader.getUniform(name);
                        if (uniform == null || uniform.getCount() != 1 || uniform.getType() != Uniform.getTypeFromString("float")) {
                            throw new IllegalArgumentException("Missing or non-scalar float uniform: " + name);
                        }
                    }
                    event.registerShader(shader, loaded -> {
                        LOADED.put(id, loaded);
                        LogUtils.getLogger().info("Loaded visual shader {}", id);
                    });
                } catch (RuntimeException exception) {
                    shader.close();
                    throw exception;
                }
            } catch (IOException | RuntimeException exception) {
                LogUtils.getLogger().warn("Visual shader {} unavailable; using vanilla emissive rendering", id, exception);
            }
        }
    }

    public static float scalarValue(VisualMaterial material, String name) {
        Map<String, Float> defaults = DEFINITIONS.getOrDefault(material.shader(), Map.of());
        float value = material.uniforms().getOrDefault(name, defaults.getOrDefault(name, 0f));
        if (name.equals("GlowStrength")) {
            value *= ForbiddenVisualConfig.GLOW_STRENGTH.get().floatValue();
        } else if (name.equals("FlowSpeed")) {
            value *= ForbiddenVisualConfig.FLOW_SPEED.get().floatValue();
        }
        return value;
    }

    static ShaderInstance bind(VisualMaterial material) {
        ShaderInstance shader = LOADED.get(material.shader());
        if (shader == null || !available(material.shader())) {
            return GameRenderer.getRendertypeEntityTranslucentEmissiveShader();
        }
        // Shared shaders must receive complete defaults for every batch, not while vertices are queued.
        for (String name : DEFINITIONS.get(material.shader()).keySet()) {
            Uniform uniform = shader.getUniform(name);
            if (uniform != null) {
                uniform.set(scalarValue(material, name));
            }
        }
        Uniform time = shader.getUniform("VisualTime");
        if (time != null) {
            time.set(ClientVisualClock.worldSeconds());
        }
        return shader;
    }
}
