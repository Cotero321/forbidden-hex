package com.example.akaishi.forbidden.brewing;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.List;
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

    public static final List<ResourceLocation> NON_EVIL = List.of(
            EARTH_BREATH, WINTER_BREATH, DAWN_BREATH, VITALITY_BREATH, SUN_BREATH, GLOOM_BREATH,
            SWAMP_BREATH, DREAM_BREATH, WHITE_THORN_BREATH, RED_VEIN_BREATH, BLOODWOOD_BREATH,
            UNDERPASS_BREATH, WATER_MIRROR_BREATH, SHRIEK_BREATH, MADNESS_BREATH, CHARM_BREATH,
            HALLUCINATION_BREATH, FROST_POISON_BREATH, HEARTBEAT_BREATH, CURSE_BREATH, HEAT_BREATH,
            BRIGHT_BREATH);
    private static final Set<ResourceLocation> ALLOWED = Set.copyOf(NON_EVIL);
    private static final int[] COLORS = {0xA08051, 0x9BD5EF, 0xF5BD7B, 0x7BBF68, 0xED973C, 0x665B9B,
            0x79936A, 0xE4AECF, 0xE1DBAC, 0xCF6868, 0xA03E3D, 0x697ABC, 0x79C4C6,
            0xA2A953, 0xB27BCE, 0xED8ABD, 0xA788DA, 0x6FA9BE, 0xD36466, 0x8C6099,
            0xF29B43, 0xE7DE8D};

    private BrewingFumes() { }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("forbidden_hex", path);
    }

    public static boolean isAllowed(ResourceLocation kind) {
        return kind != null && (EVIL_BREATH.equals(kind) || ALLOWED.contains(kind));
    }

    public static boolean isNonEvil(ResourceLocation kind) {
        return kind != null && ALLOWED.contains(kind);
    }

    public static int color(ResourceLocation kind) {
        if (kind == null) return 0xB4C0C8;
        if (EVIL_BREATH.equals(kind)) return 0x4C364F;
        int index = NON_EVIL.indexOf(kind);
        return index >= 0 ? COLORS[index] : 0xB4C0C8;
    }

    public static ResourceLocation randomNonEvil(RandomSource random) {
        return NON_EVIL.get(random.nextInt(NON_EVIL.size()));
    }
}
