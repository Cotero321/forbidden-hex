package com.example.akaishi.forbidden.visual;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable material parameters; per-instance tint and opacity stay in vertex colors. */
public record VisualMaterial(Pipeline pipeline, ResourceLocation shader, Map<String, Float> uniforms) {
    private static final Set<String> RESERVED = Set.of(
            "ModelViewMat", "ProjMat", "ColorModulator", "FogStart", "FogEnd", "FogShape",
            "GameTime", "VisualTime", "ScreenSize", "TextureMat", "LineWidth");

    private static final Set<String> ENERGY_UNIFORMS = Set.of(
            "FlowSpeed", "GlowStrength", "PulseStrength", "PulseSpeed", "EdgeSoftness", "AlphaCutoff");

    public enum Pipeline {
        EMISSIVE_ALPHA,
        ENERGY_ADDITIVE
    }

    public VisualMaterial {
        Objects.requireNonNull(pipeline, "pipeline");
        Objects.requireNonNull(shader, "shader");
        Objects.requireNonNull(uniforms, "uniforms");
        if (uniforms.size() > 32) {
            throw new IllegalArgumentException("A material supports at most 32 scalar uniforms");
        }
        uniforms.forEach(VisualMaterial::validateUniform);
        uniforms = Map.copyOf(uniforms);
    }

    public static VisualMaterial parse(JsonObject json) {
        Pipeline pipeline = Pipeline.valueOf(string(json, "pipeline", "energy_additive").toUpperCase(Locale.ROOT));
        ResourceLocation shader = new ResourceLocation(string(json, "shader", "forbidden_hex:energy"));
        Map<String, Float> uniforms = new LinkedHashMap<>();
        if (json.has("uniforms")) {
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("uniforms").entrySet()) {
                JsonElement value = entry.getValue();
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                    throw new IllegalArgumentException("Uniform " + entry.getKey() + " must be numeric");
                }
                uniforms.put(entry.getKey(), value.getAsFloat());
            }
        }
        return new VisualMaterial(pipeline, shader, uniforms);
    }

    public static void validateUniform(String name, Float value) {
        if (name == null || !name.matches("[A-Za-z][A-Za-z0-9_]{0,63}") || RESERVED.contains(name)) {
            throw new IllegalArgumentException("Invalid or reserved material uniform: " + name);
        }
        if (value == null || !Float.isFinite(value) || Math.abs(value) > 64f) {
            throw new IllegalArgumentException("Uniform " + name + " must be finite and within [-64, 64]");
        }
        float min = switch (name) {
            case "FlowSpeed" -> -4f;
            default -> 0f;
        };
        float max = switch (name) {
            case "FlowSpeed", "GlowStrength" -> 4f;
            case "PulseStrength", "EdgeSoftness" -> 1f;
            case "PulseSpeed" -> 16f;
            case "AlphaCutoff" -> 0.5f;
            default -> 64f;
        };
        if (ENERGY_UNIFORMS.contains(name)
                && (value < min || value > max)) {
            throw new IllegalArgumentException("Uniform " + name + " must be within [" + min + ", " + max + "]");
        }
    }

    private static String string(JsonObject json, String name, String fallback) {
        if (!json.has(name)) {
            return fallback;
        }
        JsonElement element = json.get(name);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(name + " must be a string");
        }
        return element.getAsString();
    }
}
