package com.example.akaishi.forbidden.brewing;

import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.example.akaishi.forbidden.brewing.BrewingEffectType.*;

public final class BrewingPotionComposer {
    public static final String RECIPE_ID = "forbidden_hex:crucible_custom_potion";
    public record Evaluation(boolean valid, boolean complete, BrewingPotionData potion) { }
    private static final Evaluation INVALID = new Evaluation(false, false, null);
    private static final Map<BrewingEffectType, BrewingEffectType> CONVERSIONS = Map.ofEntries(
            Map.entry(GLOWING, BLINDNESS), Map.entry(SNOW_TRAIL, SCORCH_TRAIL), Map.entry(GROWTH, WITHER_PLANTS),
            Map.entry(DYE, BLEACH), Map.entry(EXTINGUISH, IGNITE), Map.entry(SWIM_SPEED, SINKING),
            Map.entry(CRUSH, CONDENSE), Map.entry(NOURISH, FAMINE), Map.entry(LEVITATION, HEAVY),
            Map.entry(SLOW_FALLING, FAST_FALLING), Map.entry(LUCK, UNLUCK), Map.entry(POISON_COATING, ANTIDOTE),
            Map.entry(KNOCKBACK, PULL), Map.entry(PLAGUE, EPIDEMIC_GUARD), Map.entry(ABSORPTION, SHIELD_BREAK),
            Map.entry(RANDOM_TELEPORT, ANCHOR), Map.entry(EXPLOSIVE, INERT), Map.entry(NIGHT_VISION, INVISIBILITY),
            Map.entry(SPEED, SLOWNESS), Map.entry(JUMP_BOOST, SLOWNESS), Map.entry(HEALING, HARMING), Map.entry(POISON, HARMING),
            Map.entry(COLD_RESISTANCE, COLD_SUSCEPTIBILITY), Map.entry(DRUG_RESISTANCE, DRUG_SENSITIVITY),
            Map.entry(STRONG_HEART, WITHERING), Map.entry(LUCIDITY, HALLUCINATION));
    private static final Map<Item, BrewingEffectType> MATERIALS = Map.ofEntries(
            Map.entry(Items.GLOW_BERRIES, GLOWING), Map.entry(Items.SNOWBALL, SNOW_TRAIL), Map.entry(Items.BONE_MEAL, GROWTH),
            Map.entry(Items.VINE, OVERGROWTH), Map.entry(Items.COD, SWIM_SPEED), Map.entry(Items.SALMON, SWIM_SPEED),
            Map.entry(Items.TROPICAL_FISH, SWIM_SPEED), Map.entry(Items.FLINT, CRUSH), Map.entry(Items.STONE, DISPEL),
            Map.entry(Items.COBBLESTONE, LAVA_REPULSION), Map.entry(Items.SPONGE, ABSORB_WATER), Map.entry(Items.PRISMARINE, LINGERING_IMMUNITY),
            Map.entry(Items.WHEAT, DRUNK), Map.entry(Items.CAKE, NOURISH), Map.entry(Items.SUGAR_CANE, LEVITATION),
            Map.entry(Items.LILY_PAD, WATER_WALKING), Map.entry(Items.RABBIT_FOOT, LUCK), Map.entry(Items.LEATHER, JUMP_BOOST),
            Map.entry(Items.FEATHER, SLOW_FALLING), Map.entry(Items.COBWEB, WEB), Map.entry(Items.CACTUS, THORNS),
            Map.entry(Items.POWDER_SNOW_BUCKET, FROST), Map.entry(Items.PUMPKIN, SMITE), Map.entry(Items.SWEET_BERRIES, REFLECTION),
            Map.entry(Items.DEAD_BUSH, WITHER_PLANTS), Map.entry(Items.POISONOUS_POTATO, POISON_COATING), Map.entry(Items.SLIME_BLOCK, LAUNCH),
            Map.entry(Items.MAGMA_BLOCK, EROSION), Map.entry(Items.STICK, KNOCKBACK), Map.entry(Items.ROTTEN_FLESH, PLAGUE),
            Map.entry(Items.ENCHANTED_GOLDEN_APPLE, TWILIGHT), Map.entry(Items.GOLDEN_APPLE, ABSORPTION), Map.entry(Items.ENDER_PEARL, RANDOM_TELEPORT),
            Map.entry(Items.CREEPER_HEAD, EXPLOSIVE), Map.entry(Items.SUGAR, SPEED), Map.entry(Items.BLAZE_POWDER, STRENGTH),
            Map.entry(Items.GLISTERING_MELON_SLICE, HEALING), Map.entry(Items.SPIDER_EYE, POISON), Map.entry(Items.GHAST_TEAR, REGENERATION),
            Map.entry(Items.MAGMA_CREAM, FIRE_RESISTANCE), Map.entry(Items.PUFFERFISH, WATER_BREATHING), Map.entry(Items.GOLDEN_CARROT, NIGHT_VISION),
            Map.entry(Items.TURTLE_HELMET, TURTLE));

    private BrewingPotionComposer() { }

    public static Evaluation evaluate(List<ItemStack> steps) {
        if (steps == null || steps.size() > CrucibleBlockEntity.MAX_STEPS) return INVALID;
        int capacity = 0, capacityTier = 0, strength = 1, duration = 1;
        boolean modifiers = false, hide = false, pressure = false;
        BrewingPotionData.Delivery delivery = BrewingPotionData.Delivery.DRINK;
        Set<Item> usedModifiers = new HashSet<>();
        List<BrewingEffectEntry> effects = new ArrayList<>();
        List<Boolean> converted = new ArrayList<>();
        for (ItemStack stack : steps) {
            if (stack == null || stack.isEmpty()) return INVALID;
            BrewingModifier modifier = BrewingModifiers.defaults().stream().filter(m -> m.ingredient().test(stack)).findFirst().orElse(null);
            if (modifier != null) {
                if (!usedModifiers.add(stack.getItem())) return INVALID;
                if (modifier.kind() == BrewingModifier.Kind.CAPACITY) {
                    if (!effects.isEmpty() || modifiers || modifier.value() < capacityTier) return INVALID;
                    capacityTier = modifier.value();
                    capacity += modifier.value();
                    if (capacity > BrewingPotionData.MAX_CAPACITY) return INVALID;
                    continue;
                }
                if (effects.isEmpty()) return INVALID;
                modifiers = true;
                switch (modifier.kind()) {
                    case STRENGTH -> { if (modifier.value() <= strength) return INVALID; strength = modifier.value(); }
                    case DURATION -> { if (modifier.value() <= duration) return INVALID; duration = modifier.value(); }
                    case DELIVERY -> {
                        if (modifier.delivery().ordinal() <= delivery.ordinal()) return INVALID;
                        delivery = modifier.delivery();
                    }
                    case HIDE_PARTICLES -> { if (hide) return INVALID; hide = true; }
                    case PRESSURE_PLATES -> { if (pressure) return INVALID; pressure = true; }
                    default -> { return INVALID; }
                }
                continue;
            }
            if (modifiers && !stack.is(Items.FERMENTED_SPIDER_EYE)) return INVALID;
            BrewingEffectType type;
            int color = -1;
            int replace = -1;
            boolean conversion = false;
            if (stack.is(Items.FERMENTED_SPIDER_EYE)) {
                if (effects.isEmpty()) type = WEAKNESS;
                else {
                    replace = effects.size() - 1;
                    if (converted.get(replace)) return INVALID;
                    type = CONVERSIONS.get(effects.get(replace).type());
                    if (type == null) return INVALID;
                    conversion = true;
                    color = effects.get(replace).color();
                }
            } else if (stack.is(BrewingItems.FUME.get())) {
                type = BrewingFumes.effect(BrewingFumeItem.kind(stack));
            } else if (stack.getItem() instanceof DyeItem dye) {
                type = DYE;
                color = dye.getDyeColor().getId();
            } else if (stack.is(Items.POTION) && PotionUtils.getPotion(stack) == Potions.WATER
                    && PotionUtils.getCustomEffects(stack).isEmpty()) type = EXTINGUISH;
            else type = MATERIALS.get(stack.getItem());
            if (type == null) return INVALID;
            int cost = type.cost();
            for (int i = 0; i < effects.size(); i++) {
                if (i == replace) continue;
                BrewingEffectType previous = effects.get(i).type();
                if (previous == type || incompatible(previous, type)) return INVALID;
                cost += previous.cost();
            }
            if (cost > capacity || effects.size() >= BrewingPotionData.MAX_EFFECTS && replace < 0) return INVALID;
            BrewingEffectEntry entry = new BrewingEffectEntry(type, color);
            if (replace >= 0) { effects.set(replace, entry); converted.set(replace, true); }
            else { effects.add(entry); converted.add(conversion); }
        }
        if (effects.isEmpty()) return new Evaluation(true, false, null);
        return new Evaluation(true, true, new BrewingPotionData(capacity, strength, duration, delivery, hide, pressure,
                List.of(), List.of(), effects));
    }

    public static Evaluation evaluateMaterials(List<ItemStack> materials) {
        if (materials == null || materials.size() > 25) return INVALID;
        int capacity = 0, strength = 1, duration = 1, deliveries = 0;
        boolean hide = false, pressure = false;
        BrewingPotionData.Delivery delivery = BrewingPotionData.Delivery.DRINK;
        List<ItemStack> effectMaterials = new ArrayList<>();
        for (ItemStack stack : materials) {
            if (stack == null || stack.isEmpty() || stack.getCount() != 1
                    || stack.hasTag() && stack.getTag().sizeInBytes() > 65536) return INVALID;
            BrewingModifier modifier = BrewingModifiers.defaults().stream()
                    .filter(candidate -> candidate.ingredient().test(stack)).findFirst().orElse(null);
            if (modifier == null) {
                effectMaterials.add(stack);
                continue;
            }
            switch (modifier.kind()) {
                case CAPACITY -> capacity += modifier.value();
                case STRENGTH -> strength = Math.max(strength, modifier.value());
                case DURATION -> duration = Math.max(duration, modifier.value());
                case DELIVERY -> {
                    if (++deliveries > 1) return INVALID;
                    delivery = modifier.delivery();
                }
                case HIDE_PARTICLES -> hide = true;
                case PRESSURE_PLATES -> pressure = true;
            }
        }
        if (capacity > BrewingPotionData.MAX_CAPACITY) return INVALID;
        List<BrewingEffectEntry> effects = new ArrayList<>();
        Set<Integer> converted = new HashSet<>();
        for (ItemStack stack : effectMaterials) {
            BrewingEffectType type;
            int color = -1, replace = -1;
            if (stack.is(Items.FERMENTED_SPIDER_EYE)) {
                if (effects.isEmpty()) type = WEAKNESS;
                else {
                    replace = effects.size() - 1;
                    if (!converted.add(replace)) return INVALID;
                    type = CONVERSIONS.get(effects.get(replace).type());
                    if (type == null) return INVALID;
                    color = effects.get(replace).color();
                }
            } else if (stack.is(BrewingItems.FUME.get())) {
                type = BrewingFumes.effect(BrewingFumeItem.kind(stack));
            } else if (stack.getItem() instanceof DyeItem dye) {
                type = DYE;
                color = dye.getDyeColor().getId();
            } else if (stack.is(Items.POTION) && PotionUtils.getPotion(stack) == Potions.WATER
                    && PotionUtils.getCustomEffects(stack).isEmpty()) type = EXTINGUISH;
            else type = MATERIALS.get(stack.getItem());
            if (type == null) return INVALID;
            for (int i = 0; i < effects.size(); i++) {
                if (i != replace && (effects.get(i).type() == type || incompatible(effects.get(i).type(), type))) return INVALID;
            }
            BrewingEffectEntry entry = new BrewingEffectEntry(type, color);
            if (replace >= 0) effects.set(replace, entry); else effects.add(entry);
            if (effects.size() > BrewingPotionData.MAX_BREWED_EFFECTS) return INVALID;
        }
        if (effects.isEmpty() || effects.stream().mapToInt(entry -> entry.type().cost()).sum() > capacity) {
            return new Evaluation(true, false, null);
        }
        return new Evaluation(true, true, new BrewingPotionData(capacity, strength, duration, delivery, hide, pressure,
                List.of(), List.of(), effects, BrewingPotionData.CURRENT_RULES));
    }

    private static boolean incompatible(BrewingEffectType first, BrewingEffectType second) {
        if (CONVERSIONS.get(first) == second || CONVERSIONS.get(second) == first) return true;
        return first == SOBRIETY && second == SLUMBER || first == SLUMBER && second == SOBRIETY;
    }

    public static ItemStack result(Evaluation evaluation) {
        if (!evaluation.valid() || !evaluation.complete() || evaluation.potion() == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(BrewingItems.POTION.get());
        evaluation.potion().write(stack);
        return stack;
    }
}
