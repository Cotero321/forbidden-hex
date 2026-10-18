package com.example.akaishi.forbidden.forge.client;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ForbiddenVisualConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENERGY_SHADERS;
    public static final ForgeConfigSpec.BooleanValue FORCE_VANILLA_RENDERING;
    public static final ForgeConfigSpec.BooleanValue PROJECTILE_TRAILS;
    public static final ForgeConfigSpec.BooleanValue BREWING_EFFECTS;
    public static final ForgeConfigSpec.IntValue TRAIL_TICKS;
    public static final ForgeConfigSpec.IntValue MAX_TRAILS;
    public static final ForgeConfigSpec.IntValue MAX_BURSTS;
    public static final ForgeConfigSpec.IntValue EFFECT_DISTANCE;
    public static final ForgeConfigSpec.IntValue PARTICLE_BUDGET;
    public static final ForgeConfigSpec.IntValue SPELL_PARTICLE_BUDGET;
    public static final ForgeConfigSpec.IntValue SPELL_DETAIL_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue GLOW_STRENGTH;
    public static final ForgeConfigSpec.DoubleValue FLOW_SPEED;
    public static final ForgeConfigSpec.BooleanValue POST_EFFECTS;
    public static final ForgeConfigSpec.DoubleValue POST_STRENGTH;
    public static final ForgeConfigSpec.BooleanValue SPELL_SOUNDS;
    public static final ForgeConfigSpec.DoubleValue SPELL_SOUND_VOLUME;
    public static final ForgeConfigSpec.IntValue SPELL_SOUND_VOICES;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("visuals");
        FORCE_VANILLA_RENDERING = builder.comment("Force vanilla visual render types and disable local post effects for unsupported shader loaders.",
                        "Active Oculus/Iris shader packs always use this fallback automatically.")
                .define("forceVanillaRendering", false);
        ENERGY_SHADERS = builder.comment("Use local energy shaders when no shader pack is active; otherwise use vanilla emissive rendering.")
                .define("energyShaders", true);
        PROJECTILE_TRAILS = builder.comment("Draw fading ribbons along actual projectile positions.")
                .define("projectileTrails", true);
        BREWING_EFFECTS = builder.comment("Draw brewing bottle auras, impact ripples and lingering mist.")
                .define("brewingEffects", true);
        TRAIL_TICKS = builder.defineInRange("trailTicks", 10, 2, 24);
        MAX_TRAILS = builder.defineInRange("maxTrails", 64, 0, 128);
        MAX_BURSTS = builder.defineInRange("maxBursts", 32, 0, 128);
        EFFECT_DISTANCE = builder.defineInRange("effectDistance", 64, 16, 128);
        PARTICLE_BUDGET = builder.comment("Maximum extra brewing particles per client tick; vanilla particle settings also apply.")
                .defineInRange("particleBudget", 48, 0, 128);
        SPELL_PARTICLE_BUDGET = builder.comment("Maximum extra spell decoration particles per client tick; the server spell result is unchanged.")
                .defineInRange("spellParticleBudget", 64, 0, 192);
        SPELL_DETAIL_DISTANCE = builder.comment("Distance at which optional spell rings and glyph details begin to reduce.")
                .defineInRange("spellDetailDistance", 48, 16, 128);
        GLOW_STRENGTH = builder.defineInRange("glowStrength", 1.15d, 0.25d, 2d);
        FLOW_SPEED = builder.comment("Multiplier applied to material flow speeds; zero freezes UV movement.")
                .defineInRange("flowSpeed", 1d, 0d, 2d);
        POST_EFFECTS = builder.comment("Enable local sanity and unnameable post effects; external post chains always take priority.")
                .define("postEffects", true);
        POST_STRENGTH = builder.comment("Post-effect intensity multiplier; zero disables local post effects.")
                .defineInRange("postStrength", 1d, 0d, 1d);
        builder.pop();
        builder.push("audio");
        SPELL_SOUNDS = builder.define("spellSounds", true);
        SPELL_SOUND_VOLUME = builder.comment("Spell effects also respect the vanilla Players sound category.")
                .defineInRange("spellVolume", 0.8d, 0d, 1d);
        SPELL_SOUND_VOICES = builder.comment("Maximum simultaneous spell sound layers, including loops and tails.")
                .defineInRange("maxSpellVoices", 32, 4, 64);
        builder.pop();
        SPEC = builder.build();
    }

    private ForbiddenVisualConfig() { }
}
