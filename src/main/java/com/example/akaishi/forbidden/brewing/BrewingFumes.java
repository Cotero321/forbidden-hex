package com.example.akaishi.forbidden.brewing;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BrewingFumes {
    public static final ResourceLocation EARTH_BREATH = id("earth_breath");
    public static final ResourceLocation WINTER_BREATH = id("winter_breath");
    public static final ResourceLocation DAWN_BREATH = id("dawn_breath");
    public static final ResourceLocation VITALITY_BREATH = id("vitality_breath");
    public static final ResourceLocation SUN_BREATH = id("sun_breath");
    public static final ResourceLocation GLOOM_BREATH = id("gloom_breath");
    public static final ResourceLocation SWAMP_BREATH = id("swamp_breath");
    public static final ResourceLocation DREAM_BREATH = id("dream_breath");
    public static final ResourceLocation WHITE_THORN_BREATH = id("white_thorn_breath");
    public static final ResourceLocation RED_VEIN_BREATH = id("red_vein_breath");
    public static final ResourceLocation BLOODWOOD_BREATH = id("bloodwood_breath");
    public static final ResourceLocation UNDERPASS_BREATH = id("underpass_breath");
    public static final ResourceLocation WATER_MIRROR_BREATH = id("water_mirror_breath");
    public static final ResourceLocation SHRIEK_BREATH = id("shriek_breath");
    public static final ResourceLocation MADNESS_BREATH = id("madness_breath");
    public static final ResourceLocation CHARM_BREATH = id("charm_breath");
    public static final ResourceLocation HALLUCINATION_BREATH = id("hallucination_breath");
    public static final ResourceLocation FROST_POISON_BREATH = id("frost_poison_breath");
    public static final ResourceLocation HEARTBEAT_BREATH = id("heartbeat_breath");
    public static final ResourceLocation CURSE_BREATH = id("curse_breath");
    public static final ResourceLocation HEAT_BREATH = id("heat_breath");
    public static final ResourceLocation BRIGHT_BREATH = id("bright_breath");
    public static final ResourceLocation EVIL_BREATH = id("evil_breath");
    public static final ResourceLocation DEFLAGRATION_BREATH = id("deflagration_breath");
    public static final ResourceLocation EXTREME_COLD_BREATH = id("extreme_cold_breath");
    public static final ResourceLocation FLOURISHING_BREATH = id("flourishing_breath");
    public static final ResourceLocation BEWILDERMENT_BREATH = id("bewilderment_breath");
    public static final ResourceLocation GRAVITY_BREATH = id("gravity_breath");
    public static final ResourceLocation THUNDER_BREATH = id("thunder_breath");
    public static final ResourceLocation RUST_BREATH = id("rust_breath");
    public static final ResourceLocation PIERCING_BREATH = id("piercing_breath");
    public static final ResourceLocation HELLFIRE_BREATH = id("hellfire_breath");
    public static final ResourceLocation CORROSION_BREATH = id("corrosion_breath");
    public static final ResourceLocation PURGE_BREATH = id("purge_breath");
    public static final ResourceLocation SONIC_BREATH = id("sonic_breath");
    public static final ResourceLocation DELIRIUM_BREATH = id("delirium_breath");
    public static final List<ResourceLocation> REFINED = List.of(DEFLAGRATION_BREATH, EXTREME_COLD_BREATH,
            FLOURISHING_BREATH, BEWILDERMENT_BREATH, GRAVITY_BREATH, THUNDER_BREATH, RUST_BREATH,
            PIERCING_BREATH, HELLFIRE_BREATH, CORROSION_BREATH, PURGE_BREATH, SONIC_BREATH, DELIRIUM_BREATH);

    public static final List<ResourceLocation> NON_EVIL = List.of(
            EARTH_BREATH, WINTER_BREATH, DAWN_BREATH, VITALITY_BREATH, SUN_BREATH, GLOOM_BREATH,
            SWAMP_BREATH, DREAM_BREATH, WHITE_THORN_BREATH, RED_VEIN_BREATH, BLOODWOOD_BREATH,
            UNDERPASS_BREATH, WATER_MIRROR_BREATH, SHRIEK_BREATH, MADNESS_BREATH, CHARM_BREATH,
            HALLUCINATION_BREATH, FROST_POISON_BREATH, HEARTBEAT_BREATH, CURSE_BREATH, HEAT_BREATH,
            BRIGHT_BREATH);
    private static final Set<ResourceLocation> ALLOWED = Set.copyOf(NON_EVIL);
    private static final Map<ResourceLocation, BrewingEffectType> EFFECTS = Map.ofEntries(
            Map.entry(EARTH_BREATH, BrewingEffectType.TENACITY),
            Map.entry(WINTER_BREATH, BrewingEffectType.COLD_RESISTANCE),
            Map.entry(DAWN_BREATH, BrewingEffectType.SOBRIETY),
            Map.entry(VITALITY_BREATH, BrewingEffectType.MENDING),
            Map.entry(SUN_BREATH, BrewingEffectType.PHOTOSENSITIVITY),
            Map.entry(GLOOM_BREATH, BrewingEffectType.CONCEALMENT),
            Map.entry(SWAMP_BREATH, BrewingEffectType.VISCOSITY),
            Map.entry(DREAM_BREATH, BrewingEffectType.SLUMBER),
            Map.entry(WHITE_THORN_BREATH, BrewingEffectType.HARDENING),
            Map.entry(RED_VEIN_BREATH, BrewingEffectType.BLOOD_RUSH),
            Map.entry(BLOODWOOD_BREATH, BrewingEffectType.LIFESTEAL),
            Map.entry(UNDERPASS_BREATH, BrewingEffectType.WITHERING),
            Map.entry(WATER_MIRROR_BREATH, BrewingEffectType.DRUG_RESISTANCE),
            Map.entry(SHRIEK_BREATH, BrewingEffectType.CONVULSIONS),
            Map.entry(MADNESS_BREATH, BrewingEffectType.BERSERK),
            Map.entry(CHARM_BREATH, BrewingEffectType.SEDATION),
            Map.entry(HALLUCINATION_BREATH, BrewingEffectType.HALLUCINATION),
            Map.entry(FROST_POISON_BREATH, BrewingEffectType.CHILLING_POISON),
            Map.entry(HEARTBEAT_BREATH, BrewingEffectType.STRONG_HEART),
            Map.entry(CURSE_BREATH, BrewingEffectType.BAD_BLOOD),
            Map.entry(HEAT_BREATH, BrewingEffectType.BOILING_BLOOD),
            Map.entry(BRIGHT_BREATH, BrewingEffectType.LUCIDITY),
            Map.entry(DEFLAGRATION_BREATH, BrewingEffectType.DEFLAGRATION),
            Map.entry(EXTREME_COLD_BREATH, BrewingEffectType.EXTREME_COLD),
            Map.entry(FLOURISHING_BREATH, BrewingEffectType.FLOURISHING),
            Map.entry(BEWILDERMENT_BREATH, BrewingEffectType.BEWILDERMENT),
            Map.entry(GRAVITY_BREATH, BrewingEffectType.GRAVITY_FIELD),
            Map.entry(THUNDER_BREATH, BrewingEffectType.THUNDER),
            Map.entry(RUST_BREATH, BrewingEffectType.RUST),
            Map.entry(PIERCING_BREATH, BrewingEffectType.PIERCING),
            Map.entry(HELLFIRE_BREATH, BrewingEffectType.HELLFIRE),
            Map.entry(CORROSION_BREATH, BrewingEffectType.CORROSION),
            Map.entry(PURGE_BREATH, BrewingEffectType.PURGE_BENEFITS),
            Map.entry(SONIC_BREATH, BrewingEffectType.SONIC_BURST),
            Map.entry(DELIRIUM_BREATH, BrewingEffectType.DELIRIUM));
    private static final int[] COLORS = {0xA08051, 0x9BD5EF, 0xF5BD7B, 0x7BBF68, 0xED973C, 0x665B9B,
            0x79936A, 0xE4AECF, 0xE1DBAC, 0xCF6868, 0xA03E3D, 0x697ABC, 0x79C4C6,
            0xA2A953, 0xB27BCE, 0xED8ABD, 0xA788DA, 0x6FA9BE, 0xD36466, 0x8C6099,
            0xF29B43, 0xE7DE8D};

    private BrewingFumes() { }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("forbidden_hex", path);
    }

    public static boolean isAllowed(ResourceLocation kind) {
        return kind != null && (EVIL_BREATH.equals(kind) || ALLOWED.contains(kind) || REFINED.contains(kind));
    }

    public static boolean isNonEvil(ResourceLocation kind) {
        return kind != null && (ALLOWED.contains(kind) || REFINED.contains(kind));
    }

    public static BrewingEffectType effect(ResourceLocation kind) {
        return kind == null ? null : EFFECTS.get(kind);
    }

    public static int color(ResourceLocation kind) {
        if (kind == null) return 0xB4C0C8;
        if (EVIL_BREATH.equals(kind)) return 0x4C364F;
        if (REFINED.contains(kind)) return EFFECTS.get(kind).color();
        int index = NON_EVIL.indexOf(kind);
        return index >= 0 ? COLORS[index] : 0xB4C0C8;
    }

    public static ResourceLocation randomNonEvil(RandomSource random) {
        return NON_EVIL.get(random.nextInt(NON_EVIL.size()));
    }
}
