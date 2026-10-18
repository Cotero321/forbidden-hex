package com.example.akaishi.forbidden.forge.client.visual;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.visual.VisualMaterial;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Resource-pack overrides live at assets/<namespace>/visual/materials/<id>.json. */
public final class VisualMaterials extends SimpleJsonResourceReloadListener {
    public static final ResourceLocation EMISSIVE = new ResourceLocation(AkaishiMod.MOD_ID, "emissive");
    public static final ResourceLocation GLOW = new ResourceLocation(AkaishiMod.MOD_ID, "glow");
    public static final ResourceLocation FLOW = new ResourceLocation(AkaishiMod.MOD_ID, "flow");
    public static final VisualMaterials INSTANCE = new VisualMaterials();
    private static final Map<ResourceLocation, VisualMaterial> DEFAULTS = new LinkedHashMap<>();
    private static volatile Map<ResourceLocation, VisualMaterial> active;
    private static boolean registrationClosed;

    static {
        register(EMISSIVE, new VisualMaterial(VisualMaterial.Pipeline.EMISSIVE_ALPHA, ClientVisualShaders.ENERGY, Map.of()));
        register(GLOW, new VisualMaterial(VisualMaterial.Pipeline.ENERGY_ADDITIVE, ClientVisualShaders.ENERGY, Map.of()));
        register(FLOW, new VisualMaterial(VisualMaterial.Pipeline.ENERGY_ADDITIVE, ClientVisualShaders.ENERGY,
                Map.of("FlowSpeed", 0.85f, "PulseStrength", 0.44f, "EdgeSoftness", 0.4f)));
    }

    private VisualMaterials() {
        super(new Gson(), "visual/materials");
    }

    /** Register defaults during client initialization; resources may override the same ID. */
    public static void register(ResourceLocation id, VisualMaterial material) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(material, "material");
        if (registrationClosed) {
            throw new IllegalStateException("Visual materials must be registered before the first resource reload");
        }
        if (material.pipeline() == VisualMaterial.Pipeline.ENERGY_ADDITIVE) {
            ClientVisualShaders.validate(material);
        }
        if (DEFAULTS.putIfAbsent(id, material) != null) {
            throw new IllegalArgumentException("Duplicate visual material: " + id);
        }
        active = Map.copyOf(DEFAULTS);
        VisualRenderTypes.clearCache();
    }

    public static VisualMaterial get(ResourceLocation id) {
        return active.getOrDefault(id, DEFAULTS.get(EMISSIVE));
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager manager, ProfilerFiller profiler) {
        registrationClosed = true;
        Map<ResourceLocation, VisualMaterial> next = new LinkedHashMap<>(DEFAULTS);
        resources.forEach((id, json) -> {
            try {
                VisualMaterial material = VisualMaterial.parse(json.getAsJsonObject());
                if (material.pipeline() == VisualMaterial.Pipeline.ENERGY_ADDITIVE) {
                    ClientVisualShaders.validate(material);
                }
                next.put(id, material);
            } catch (RuntimeException exception) {
                LogUtils.getLogger().warn("Ignoring invalid visual material {}; retaining its default", id, exception);
            }
        });
        active = Map.copyOf(next);
        VisualRenderTypes.clearCache();
        LogUtils.getLogger().info("Loaded {} visual materials", active.size());
    }
}
