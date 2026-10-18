package com.example.akaishi.forbidden.brewing;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public record BrewingModifier(BrewingIngredient ingredient, Kind kind, int value,
                              BrewingPotionData.Delivery delivery, List<Integer> amplifiers) {
    public enum Kind { CAPACITY, STRENGTH, DURATION, DELIVERY, HIDE_PARTICLES, PRESSURE_PLATES }

    public BrewingModifier {
        if (ingredient == null || kind == null || delivery == null || amplifiers == null) throw new IllegalArgumentException("Invalid brewing modifier");
        amplifiers = List.copyOf(amplifiers);
        if (kind == Kind.CAPACITY && (value < 1 || value > 4)
                || kind == Kind.STRENGTH && (value < 2 || value > BrewingPotionData.MAX_STRENGTH)
                || kind == Kind.DURATION && (value < 2 || value > BrewingPotionData.MAX_DURATION)
                || amplifiers.size() > BrewingPotionData.MAX_EFFECTS
                || amplifiers.stream().anyMatch(amplifier -> amplifier < 0 || amplifier > BrewingPotionData.MAX_AMPLIFIER)) {
            throw new IllegalArgumentException("Invalid brewing modifier value");
        }
        if (kind != Kind.STRENGTH && !amplifiers.isEmpty()) throw new IllegalArgumentException("Amplifiers require a strength modifier");
    }

    public BrewingPotionData apply(BrewingPotionData base) {
        int capacity = base.capacity(), strength = base.strength(), duration = base.duration();
        var delivery = base.delivery();
        boolean hide = base.hideParticles(), pressure = base.triggerPressurePlates();
        List<MobEffectInstance> effects = base.effects();
        switch (kind) {
            case CAPACITY -> {
                if (capacity + value > BrewingPotionData.MAX_CAPACITY) throw new IllegalArgumentException("Capacity exceeds the supported budget");
                capacity += value;
            }
            case STRENGTH -> {
                if (value <= strength || !amplifiers.isEmpty() && amplifiers.size() != effects.size()) {
                    throw new IllegalArgumentException("Strength must increase and match the effect list");
                }
                strength = value;
                List<MobEffectInstance> modified = new ArrayList<>();
                for (int i = 0; i < effects.size(); i++) {
                    MobEffectInstance effect = effects.get(i);
                    modified.add(new MobEffectInstance(effect.getEffect(), effect.getDuration(),
                            amplifiers.isEmpty() ? value - 1 : amplifiers.get(i),
                            effect.isAmbient(), effect.isVisible(), effect.showIcon()));
                }
                effects = modified;
            }
            case DURATION -> {
                int previousMultiplier = Math.max(1, duration);
                if (value <= previousMultiplier) throw new IllegalArgumentException("Duration must increase");
                duration = value;
                effects = effects.stream().map(effect -> new MobEffectInstance(effect.getEffect(),
                        effect.getEffect().isInstantenous() ? effect.getDuration()
                                : Math.toIntExact(Math.multiplyExact((long) effect.getDuration(), value) / previousMultiplier),
                        effect.getAmplifier(), effect.isAmbient(), effect.isVisible(), effect.showIcon())).toList();
            }
            case DELIVERY -> {
                if (this.delivery == delivery || this.delivery == BrewingPotionData.Delivery.SPLASH
                        && delivery == BrewingPotionData.Delivery.LINGERING) {
                    throw new IllegalArgumentException("Delivery must change without downgrading lingering");
                }
                delivery = this.delivery;
            }
            case HIDE_PARTICLES -> hide = true;
            case PRESSURE_PLATES -> pressure = true;
        }
        return new BrewingPotionData(capacity, strength, duration, delivery, hide, pressure, effects, base.effectCosts());
    }

    public static BrewingModifier fromJson(JsonObject json) {
        try {
            Kind kind = Kind.valueOf(GsonHelper.getAsString(json, "kind").toUpperCase(Locale.ROOT));
            int value = switch (kind) {
                case CAPACITY -> GsonHelper.getAsInt(json, "capacity");
                case STRENGTH -> GsonHelper.getAsInt(json, "strength");
                case DURATION -> GsonHelper.getAsInt(json, "multiplier");
                default -> 0;
            };
            var delivery = kind == Kind.DELIVERY
                    ? BrewingPotionData.Delivery.valueOf(GsonHelper.getAsString(json, "delivery").toUpperCase(Locale.ROOT))
                    : BrewingPotionData.Delivery.DRINK;
            List<Integer> amplifiers = new ArrayList<>();
            if (kind == Kind.STRENGTH && json.has("amplifiers")) {
                for (var entry : GsonHelper.getAsJsonArray(json, "amplifiers")) amplifiers.add(entry.getAsInt());
            }
            return new BrewingModifier(BrewingIngredient.fromJson(json), kind, value, delivery, amplifiers);
        } catch (IllegalArgumentException exception) {
            throw new JsonSyntaxException("Invalid brewing modifier", exception);
        }
    }

    public static BrewingModifier fromNetwork(FriendlyByteBuf buffer) {
        BrewingIngredient ingredient = BrewingIngredient.fromNetwork(buffer);
        Kind kind = buffer.readEnum(Kind.class);
        int value = buffer.readVarInt();
        var delivery = buffer.readEnum(BrewingPotionData.Delivery.class);
        int size = buffer.readVarInt();
        if (size < 0 || size > BrewingPotionData.MAX_EFFECTS) throw new IllegalArgumentException("Invalid amplifier list");
        List<Integer> amplifiers = new ArrayList<>();
        for (int i = 0; i < size; i++) amplifiers.add(buffer.readVarInt());
        return new BrewingModifier(ingredient, kind, value, delivery, amplifiers);
    }

    public void toNetwork(FriendlyByteBuf buffer) {
        ingredient.toNetwork(buffer);
        buffer.writeEnum(kind);
        buffer.writeVarInt(value);
        buffer.writeEnum(delivery);
        buffer.writeVarInt(amplifiers.size());
        for (int amplifier : amplifiers) buffer.writeVarInt(amplifier);
    }
}
