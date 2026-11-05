package com.example.akaishi.forbidden.familiar.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Material modifiers used by the familiar runtime.  The workbench stores stable material ids in
 * NBT; this table is the common-side interpretation of those ids.  Percentages are represented as
 * decimal fractions (0.15 = +15%).
 */
public final class FamiliarMaterialData {
    private FamiliarMaterialData() {
    }

    public record Modifiers(
            double attackMultiplier,
            double spellPowerMultiplier,
            double armorMultiplier,
            double attackSpeedMultiplier,
            double moveSpeedMultiplier,
            double maxHealthMultiplier,
            double sourceCapacityMultiplier,
            double hungerConsumptionMultiplier,
            double statusResistance,
            double dodgeChance,
            double knockbackResistance,
            double criticalChance,
            double lifesteal,
            double regenerationMultiplier,
            double spellPenetration,
            double magicDamageReduction
    ) {
        public static Modifiers none() {
            return new Modifiers(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        public Modifiers plus(Modifiers other) {
            return new Modifiers(
                    attackMultiplier + other.attackMultiplier,
                    spellPowerMultiplier + other.spellPowerMultiplier,
                    armorMultiplier + other.armorMultiplier,
                    attackSpeedMultiplier + other.attackSpeedMultiplier,
                    moveSpeedMultiplier + other.moveSpeedMultiplier,
                    maxHealthMultiplier + other.maxHealthMultiplier,
                    sourceCapacityMultiplier + other.sourceCapacityMultiplier,
                    hungerConsumptionMultiplier + other.hungerConsumptionMultiplier,
                    statusResistance + other.statusResistance,
                    dodgeChance + other.dodgeChance,
                    knockbackResistance + other.knockbackResistance,
                    criticalChance + other.criticalChance,
                    lifesteal + other.lifesteal,
                    regenerationMultiplier + other.regenerationMultiplier,
                    spellPenetration + other.spellPenetration,
                    magicDamageReduction + other.magicDamageReduction
            );
        }

        public double scale(double base) {
            return Math.max(0.0D, base * (1.0D + attackMultiplier));
        }
    }

    public static Modifiers forId(String rawId) {
        if (rawId == null || rawId.isBlank()) return Modifiers.none();
        return switch (rawId.toLowerCase(Locale.ROOT)) {
            case "starfallen_iron", "starfall_iron" -> new Modifiers(
                    0.15, 0, 0, 0, 0, -0.10, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            case "flawless_silver" -> new Modifiers(
                    0, 0.15, -0.10, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            case "void_obsidian" -> new Modifiers(
                    0, 0, 0.20, 0, -0.15, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            case "aurum", "gilded_gold" -> new Modifiers(
                    0, 0, 0, 0, 0, 0, 0.20, 0.15, 0, 0, 0, 0, 0, 0, 0, 0);
            case "alchemical_white_salt", "white_salt" -> new Modifiers(
                    0, 0, 0, 0, 0, -0.05, 0, 0, 0.20, 0, 0, 0, 0, 0, 0, 0);
            case "moonlight_brocade" -> new Modifiers(
                    0, 0, -0.15, 0.15, 0, 0, 0, 0, 0, 0.05, 0, 0, 0, 0, 0, 0);
            case "windwhisper_thread", "wind_silk" -> new Modifiers(
                    0, 0, 0, 0, 0.15, -0.10, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            case "hardened_leather" -> new Modifiers(
                    0, 0, 0, 0, -0.05, 0.15, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            case "abyssal_soft_hide", "abyssal_soft_leather" -> new Modifiers(
                    0, 0, 0, -0.10, 0, 0, 0, 0, 0, 0, 0.50, 0, 0, 0, 0, 0);
            case "soul_bone_block", "soul_bone" -> new Modifiers(
                    0, -0.10, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.15, 0, 0, 0, 0);
            case "carbonized_flesh" -> new Modifiers(
                    0.20, 0, 0, 0, -0.10, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            case "active_protoplasm" -> new Modifiers(
                    0, 0, -0.10, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.10, 0, 0, 0);
            case "resilient_vine_core" -> new Modifiers(
                    0, 0, 0, 0, -0.10, 0, 0, 0, 0, 0, 0, 0, 0, 0.20, 0, 0);
            case "dream_crystal" -> new Modifiers(
                    0, 0.15, 0, -0.10, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.15, 0);
            case "antimagic_crystal", "anti_magic_crystal" -> new Modifiers(
                    0, 0, -0.10, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.20);
            default -> Modifiers.none();
        };
    }

    public static Modifiers combined(List<String> materialIds) {
        Modifiers result = Modifiers.none();
        if (materialIds == null) return result;
        for (String materialId : materialIds) result = result.plus(forId(materialId));
        return result;
    }

    public static List<String> normalize(List<String> materialIds) {
        List<String> result = new ArrayList<>();
        if (materialIds == null) return result;
        for (String value : materialIds) {
            if (value != null && !value.isBlank()) result.add(value.toLowerCase(Locale.ROOT));
        }
        return result;
    }
}
