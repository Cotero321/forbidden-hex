package com.example.akaishi.forbidden.brewing;

import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Optional;

public enum BrewingEffectType {
    GLOWING(1, 60, 0xD8C75A),
    SNOW_TRAIL(1, 60, 0xDCEFF2),
    GROWTH(1, 30, 0x79BA59),
    OVERGROWTH(1, 30, 0x598B42),
    DYE(1, 60, 0xD18FC4),
    EXTINGUISH(1, 0, 0x579DD0),
    SWIM_SPEED(1, 60, 0x53BCD0),
    CRUSH(1, 0, 0xA2947D),
    DISPEL(1, 0, 0xA4B3BA),
    LAVA_REPULSION(2, 20, 0xE18140),
    ABSORB_WATER(2, 0, 0xD1C367),
    LINGERING_IMMUNITY(2, 60, 0x79BCAD),
    DRUNK(2, 60, 0xBB9147),
    NOURISH(2, 60, 0xE7BD87),
    LEVITATION(2, 5, 0xCEFFFF),
    WATER_WALKING(2, 60, 0x71AE86),
    LUCK(2, 60, 0x339900),
    JUMP_BOOST(2, 60, 0x786297),
    SLOW_FALLING(2, 60, 0xF3CFB9),
    WEB(2, 4, 0xD7D8D0),
    THORNS(2, 30, 0x4D844A),
    FROST(2, 10, 0x88C7E0),
    SMITE(2, 0, 0xD89742),
    REFLECTION(2, 30, 0xB45D78),
    WITHER_PLANTS(2, 0, 0x796347),
    POISON_COATING(3, 60, 0x7A9D43),
    LAUNCH(3, 0, 0x80BE6B),
    EROSION(3, 0, 0xC27948),
    KNOCKBACK(3, 60, 0xA78A63),
    PLAGUE(3, 20, 0x7E8650),
    TWILIGHT(3, 30, 0xA46C9C),
    ABSORPTION(3, 60, 0xEAC957),
    RANDOM_TELEPORT(3, 0, 0x72958B),
    EXPLOSIVE(3, 30, 0x7CA15D),
    SPEED(2, 60, 0x7CAFC6),
    SLOWNESS(2, 30, 0x5A6C81),
    STRENGTH(2, 60, 0x932423),
    HEALING(2, 0, 0xF82423),
    HARMING(2, 0, 0x430A09),
    POISON(2, 15, 0x4E9331),
    REGENERATION(2, 15, 0xCD5CAB),
    FIRE_RESISTANCE(2, 60, 0xE49A3A),
    WATER_BREATHING(2, 60, 0x2E5299),
    NIGHT_VISION(2, 60, 0x1F1FA1),
    INVISIBILITY(2, 60, 0x7F8392),
    WEAKNESS(2, 30, 0x484D48),
    TURTLE(2, 20, 0x6F785E),
    BLINDNESS(2, 10, 0x1F1F23),
    SCORCH_TRAIL(2, 30, 0xD86B37),
    BLEACH(1, 0, 0xE9E6DB),
    IGNITE(1, 0, 0xE78538),
    SINKING(2, 20, 0x315C7F),
    CONDENSE(1, 0, 0x817A71),
    FAMINE(2, 30, 0x8C744C),
    HEAVY(2, 15, 0x686577),
    FAST_FALLING(2, 10, 0x756B82),
    UNLUCK(2, 60, 0xC0A44D),
    ANTIDOTE(3, 30, 0x90C89A),
    EPIDEMIC_GUARD(3, 30, 0xB4BE7F),
    PULL(3, 30, 0x8776A3),
    SHIELD_BREAK(3, 0, 0xB4A16A),
    ANCHOR(3, 15, 0x686F8E),
    INERT(3, 30, 0x8AA0A0),
    TENACITY(3, 60, 0xA08051),
    COLD_RESISTANCE(3, 60, 0x9BD5EF),
    SOBRIETY(3, 60, 0xF5BD7B),
    MENDING(3, 60, 0x7BBF68),
    PHOTOSENSITIVITY(3, 30, 0xED973C),
    CONCEALMENT(3, 60, 0x665B9B),
    VISCOSITY(3, 30, 0x79936A),
    SLUMBER(3, 8, 0xE4AECF),
    HARDENING(4, 60, 0xE1DBAC),
    BLOOD_RUSH(4, 60, 0xCF6868),
    LIFESTEAL(4, 45, 0xA03E3D),
    WITHERING(4, 30, 0x697ABC),
    DRUG_RESISTANCE(4, 60, 0x79C4C6),
    CONVULSIONS(6, 12, 0xA2A953),
    BERSERK(6, 30, 0xB27BCE),
    SEDATION(6, 10, 0xED8ABD),
    HALLUCINATION(6, 20, 0xA788DA),
    CHILLING_POISON(6, 20, 0x6FA9BE),
    STRONG_HEART(6, 60, 0xD36466),
    BAD_BLOOD(6, 30, 0x8C6099),
    BOILING_BLOOD(6, 30, 0xF29B43),
    LUCIDITY(6, 60, 0xE7DE8D),
    COLD_SUSCEPTIBILITY(3, 30, 0x7396BC),
    DRUG_SENSITIVITY(4, 30, 0xBA83AA),
    DEFLAGRATION(6, 0, 0xEF913F),
    EXTREME_COLD(6, 10, 0xA6E5EF),
    FLOURISHING(6, 0, 0x86CF63),
    BEWILDERMENT(6, 10, 0x9571C6),
    GRAVITY_FIELD(6, 10, 0x615B82),
    THUNDER(6, 0, 0x83C9E3),
    RUST(6, 20, 0xBA6349),
    PIERCING(6, 10, 0xBD768B),
    HELLFIRE(6, 10, 0xF26C35),
    CORROSION(6, 10, 0x9EB568),
    PURGE_BENEFITS(6, 0, 0xC7AFE6),
    SONIC_BURST(6, 0, 0xBBDDDC),
    DELIRIUM(6, 10, 0xB89CCF);

    private final int cost;
    private final int seconds;
    private final int color;
    private final ResourceLocation id;

    BrewingEffectType(int cost, int seconds, int color) {
        this.cost = cost;
        this.seconds = seconds;
        this.color = color;
        this.id = new ResourceLocation("forbidden_hex", "brewing_" + name().toLowerCase(Locale.ROOT));
    }

    public int cost() { return cost; }
    public int color() { return color; }
    public ResourceLocation id() { return id; }
    public String translationKey() { return "effect.forbidden_hex." + id.getPath(); }
    public boolean instantaneous() { return seconds == 0; }
    public boolean harmful() {
        return switch (this) {
            case OVERGROWTH, DISPEL, LEVITATION, WEB, THORNS, FROST, SMITE, REFLECTION, POISON_COATING,
                    LAUNCH, EROSION, KNOCKBACK, PLAGUE, RANDOM_TELEPORT, EXPLOSIVE, SLOWNESS, HARMING,
                    POISON, WEAKNESS, BLINDNESS, SCORCH_TRAIL, IGNITE, SINKING, FAMINE, HEAVY,
                    FAST_FALLING, UNLUCK, PULL, SHIELD_BREAK, ANCHOR, PHOTOSENSITIVITY, VISCOSITY,
                    SLUMBER, WITHERING, CONVULSIONS, BERSERK, SEDATION, HALLUCINATION, CHILLING_POISON,
                    BAD_BLOOD, COLD_SUSCEPTIBILITY, DRUG_SENSITIVITY, DEFLAGRATION, EXTREME_COLD,
                    BEWILDERMENT, GRAVITY_FIELD, THUNDER, RUST, PIERCING, HELLFIRE, CORROSION,
                    PURGE_BENEFITS, SONIC_BURST, DELIRIUM -> true;
            default -> false;
        };
    }

    public int ticks(int strength, int multiplier) {
        int base = this == WEB ? (strength + 3) * 20 : Math.max(1, seconds * 20);
        if (instantaneous()) return 1;
        int cap = switch (this) {
            case LEVITATION, WEB -> 400;
            case BLINDNESS -> 600;
            case SLUMBER -> 200;
            case SEDATION -> 300;
            case CONVULSIONS -> 400;
            default -> Integer.MAX_VALUE;
        };
        return Math.min(cap, base * Math.max(1, Math.min(6, multiplier)));
    }

    public int ticks(int strength, int multiplier, boolean currentRules) {
        int legacyTicks = ticks(strength, multiplier);
        return !currentRules || instantaneous() ? legacyTicks : BrewingPotionData.strengthenedTicks(legacyTicks, strength);
    }

    public static Optional<BrewingEffectType> byId(String name) {
        ResourceLocation parsed = ResourceLocation.tryParse(name);
        if (parsed == null) return Optional.empty();
        for (BrewingEffectType type : values()) if (type.id.equals(parsed)) return Optional.of(type);
        return Optional.empty();
    }
}
