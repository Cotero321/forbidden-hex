package com.example.akaishi.forbidden.brewing;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record BrewingPotionData(int capacity, int strength, int duration, Delivery delivery,
                                boolean hideParticles, boolean triggerPressurePlates,
                                List<MobEffectInstance> effects, List<Integer> effectCosts) {
    public static final String TAG = "forbidden_hex_brewing";
    public static final int VOLUME = 250;
    public static final int MAX_EFFECTS = 64;
    public static final int MAX_AMPLIFIER = 127;
    public static final int MAX_CAPACITY = 64;
    public static final int MAX_STRENGTH = 5;
    public static final int MAX_DURATION = 6;

    public int usedCapacity() {
        return effectCosts.stream().mapToInt(Integer::intValue).sum();
    }

    public BrewingPotionData(int capacity, int strength, int duration, Delivery delivery,
                             boolean hideParticles, boolean triggerPressurePlates,
                             List<MobEffectInstance> effects) {
        this(capacity, strength, duration, delivery, hideParticles, triggerPressurePlates,
                effects, effects == null ? List.of() : java.util.Collections.nCopies(effects.size(), 1));
    }

    public enum Delivery { DRINK, SPLASH, LINGERING, DIRECT }

    public BrewingPotionData {
        if (capacity < 0 || capacity > MAX_CAPACITY || strength < 1 || strength > MAX_STRENGTH
                || duration < 0 || duration > MAX_DURATION || delivery == null
                || effects == null || effects.isEmpty() || effects.size() > MAX_EFFECTS) {
            throw new IllegalArgumentException("Invalid brewing potion properties");
        }
        List<MobEffectInstance> copies = new ArrayList<>();
        for (MobEffectInstance effect : effects) {
            if (effect == null || effect.getAmplifier() < 0 || effect.getAmplifier() > MAX_AMPLIFIER
                    || effect.getDuration() < 1) {
                throw new IllegalArgumentException("Invalid brewing potion effect");
            }
            copies.add(new MobEffectInstance(effect.getEffect(), effect.getDuration(), effect.getAmplifier(),
                    effect.isAmbient(), effect.isVisible(), effect.showIcon()));
        }
        effects = List.copyOf(copies);
        if (effectCosts == null || effectCosts.size() != effects.size()
                || effectCosts.stream().anyMatch(cost -> cost == null || cost < 1 || cost > MAX_CAPACITY)) {
            throw new IllegalArgumentException("Invalid brewing effect costs");
        }
        effectCosts = List.copyOf(effectCosts);
        if (effectCosts.stream().mapToInt(Integer::intValue).sum() > capacity) {
            throw new IllegalArgumentException("Brewing effects exceed capacity");
        }
    }

    @Override
    public List<MobEffectInstance> effects() {
        return effects.stream().map(MobEffectInstance::new).toList();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", 1);
        tag.putInt("volume", VOLUME);
        tag.putInt("capacity", capacity);
        tag.putInt("strength", strength);
        tag.putInt("duration", duration);
        tag.putString("delivery", delivery.name());
        tag.putBoolean("hide_particles", hideParticles);
        tag.putBoolean("pressure_plates", triggerPressurePlates);
        ListTag list = new ListTag();
        for (MobEffectInstance effect : effects) list.add(effect.save(new CompoundTag()));
        tag.put("effects", list);
        tag.putIntArray("effect_costs", effectCosts.stream().mapToInt(Integer::intValue).toArray());
        return tag;
    }

    public void write(ItemStack stack) {
        stack.getOrCreateTag().put(TAG, save());
    }

    public static Optional<BrewingPotionData> read(ItemStack stack) {
        if (!stack.hasTag() || !stack.getTag().contains(TAG, Tag.TAG_COMPOUND)) return Optional.empty();
        return read(stack.getTag().getCompound(TAG));
    }

    public static Optional<BrewingPotionData> read(CompoundTag tag) {
        if (tag.sizeInBytes() > 65536 || !tag.contains("version", Tag.TAG_INT) || tag.getInt("version") != 1
                || !tag.contains("volume", Tag.TAG_INT) || tag.getInt("volume") != VOLUME
                || !tag.contains("capacity", Tag.TAG_INT) || !tag.contains("strength", Tag.TAG_INT)
                || !tag.contains("duration", Tag.TAG_INT) || !tag.contains("delivery", Tag.TAG_STRING)) return Optional.empty();
        ListTag list = tag.getList("effects", Tag.TAG_COMPOUND);
        if (list.isEmpty() || list.size() > MAX_EFFECTS) return Optional.empty();
        List<MobEffectInstance> effects = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (entry.contains("HiddenEffect") || !entry.contains("Amplifier", Tag.TAG_BYTE)
                    || !entry.contains("Duration", Tag.TAG_INT) || entry.getByte("Amplifier") < 0
                    || entry.getInt("Duration") < 1) return Optional.empty();
            MobEffectInstance effect = MobEffectInstance.load(entry);
            if (effect == null) return Optional.empty();
            effects.add(effect);
        }
        if (tag.contains("effect_costs") && !tag.contains("effect_costs", Tag.TAG_INT_ARRAY)) return Optional.empty();
        List<Integer> costs = tag.contains("effect_costs", Tag.TAG_INT_ARRAY)
                ? java.util.Arrays.stream(tag.getIntArray("effect_costs")).boxed().toList()
                : java.util.Collections.nCopies(effects.size(), 1);
        int savedCapacity = tag.getInt("capacity");
        int savedDuration = tag.getInt("duration");
        if (!tag.contains("effect_costs")) {
            if (savedCapacity >= 0 && savedCapacity <= MAX_CAPACITY) savedCapacity = Math.max(savedCapacity, effects.size());
            if (savedDuration >= 0) savedDuration = Math.min(savedDuration, MAX_DURATION);
        }
        try {
            return Optional.of(new BrewingPotionData(savedCapacity, tag.getInt("strength"),
                    savedDuration, Delivery.valueOf(tag.getString("delivery")),
                    tag.getBoolean("hide_particles"), tag.getBoolean("pressure_plates"), effects, costs));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public void apply(LivingEntity target, double intensity) {
        if (target.level().isClientSide || !target.isAffectedByPotions() || !Double.isFinite(intensity) || intensity <= 0) return;
        intensity = Math.min(1, intensity);
        for (MobEffectInstance effect : effects) {
            if (effect.getEffect().isInstantenous()) {
                effect.getEffect().applyInstantenousEffect(null, null, target, effect.getAmplifier(), intensity);
            } else {
                int ticks = Math.max(1, (int) (effect.getDuration() * intensity));
                target.addEffect(new MobEffectInstance(effect.getEffect(), ticks, effect.getAmplifier(),
                        effect.isAmbient(), !hideParticles, !hideParticles));
            }
        }
    }
}
