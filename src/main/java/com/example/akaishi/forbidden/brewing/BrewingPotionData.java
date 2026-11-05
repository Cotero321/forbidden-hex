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
                                List<MobEffectInstance> effects, List<Integer> effectCosts,
                                List<BrewingEffectEntry> customEffects, int rulesVersion) {
    public static final String TAG = "forbidden_hex_brewing";
    public static final int VOLUME = 250;
    public static final int MAX_EFFECTS = 64;
    public static final int MAX_BREWED_EFFECTS = 6;
    public static final int CURRENT_RULES = 3;
    public static final int MAX_AMPLIFIER = 127;
    public static final int MAX_CAPACITY = 64;
    public static final int MAX_STRENGTH = 5;
    public static final int MAX_DURATION = 6;

    public int usedCapacity() {
        return effectCosts.stream().mapToInt(Integer::intValue).sum()
                + customEffects.stream().mapToInt(entry -> entry.type().cost()).sum();
    }

    public BrewingPotionData(int capacity, int strength, int duration, Delivery delivery,
                             boolean hideParticles, boolean triggerPressurePlates,
                             List<MobEffectInstance> effects, List<Integer> effectCosts) {
        this(capacity, strength, duration, delivery, hideParticles, triggerPressurePlates, effects, effectCosts, List.of());
    }

    public BrewingPotionData(int capacity, int strength, int duration, Delivery delivery,
                             boolean hideParticles, boolean triggerPressurePlates,
                             List<MobEffectInstance> effects) {
        this(capacity, strength, duration, delivery, hideParticles, triggerPressurePlates,
                effects, effects == null ? List.of() : java.util.Collections.nCopies(effects.size(), 1));
    }

    public BrewingPotionData(int capacity, int strength, int duration, Delivery delivery,
                             boolean hideParticles, boolean triggerPressurePlates,
                             List<MobEffectInstance> effects, List<Integer> effectCosts,
                             List<BrewingEffectEntry> customEffects) {
        this(capacity, strength, duration, delivery, hideParticles, triggerPressurePlates,
                effects, effectCosts, customEffects, customEffects.isEmpty() ? 1 : 2);
    }

    public enum Delivery { DRINK, SPLASH, LINGERING, DIRECT }

    public int ticks(BrewingEffectType type) {
        return type.ticks(strength, duration, rulesVersion >= CURRENT_RULES);
    }

    public static int strengthenedTicks(long ticks, int strength) {
        return (int) Math.max(200, Math.min(Integer.MAX_VALUE,
                Math.floor(ticks * Math.pow(0.75, Math.max(0, strength - 1)))));
    }

    public BrewingPotionData currentRules() {
        if (rulesVersion >= CURRENT_RULES) return this;
        if (effects.size() + customEffects.size() > MAX_BREWED_EFFECTS) {
            throw new IllegalArgumentException("New potions support at most six effects");
        }
        List<MobEffectInstance> adjusted = effects.stream().map(effect -> new MobEffectInstance(effect.getEffect(),
                effect.getEffect().isInstantenous() ? effect.getDuration() : strengthenedTicks(effect.getDuration(), strength),
                effect.getAmplifier(), effect.isAmbient(), effect.isVisible(), effect.showIcon())).toList();
        return new BrewingPotionData(capacity, strength, duration, delivery, hideParticles, triggerPressurePlates,
                adjusted, effectCosts, customEffects, CURRENT_RULES);
    }

    public BrewingPotionData {
        if (rulesVersion < 1 || rulesVersion > CURRENT_RULES
                || capacity < 0 || capacity > MAX_CAPACITY || strength < 1 || strength > MAX_STRENGTH
                || duration < 0 || duration > MAX_DURATION || delivery == null
                || effects == null || customEffects == null
                || effects.size() + customEffects.size() > (rulesVersion >= CURRENT_RULES ? MAX_BREWED_EFFECTS : MAX_EFFECTS)
                || effects.isEmpty() && customEffects.isEmpty()) {
            throw new IllegalArgumentException("Invalid brewing potion properties");
        }
        customEffects = List.copyOf(customEffects);
        if (customEffects.stream().map(BrewingEffectEntry::type).distinct().count() != customEffects.size()) {
            throw new IllegalArgumentException("Duplicate custom brewing effects");
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
        if (effectCosts.stream().mapToInt(Integer::intValue).sum()
                + customEffects.stream().mapToInt(entry -> entry.type().cost()).sum() > capacity) {
            throw new IllegalArgumentException("Brewing effects exceed capacity");
        }
    }

    @Override
    public List<MobEffectInstance> effects() {
        return effects.stream().map(MobEffectInstance::new).toList();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", rulesVersion);
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
        if (!customEffects.isEmpty()) {
            ListTag custom = new ListTag();
            for (BrewingEffectEntry entry : customEffects) custom.add(entry.save());
            tag.put("custom_effects", custom);
        }
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
        if (tag.sizeInBytes() > 65536 || !tag.contains("version", Tag.TAG_INT)
                || tag.getInt("version") < 1 || tag.getInt("version") > CURRENT_RULES
                || !tag.contains("volume", Tag.TAG_INT) || tag.getInt("volume") != VOLUME
                || !tag.contains("capacity", Tag.TAG_INT) || !tag.contains("strength", Tag.TAG_INT)
                || !tag.contains("duration", Tag.TAG_INT) || !tag.contains("delivery", Tag.TAG_STRING)) return Optional.empty();
        if (!(tag.get("effects") instanceof ListTag list) || !list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND
                || list.size() > MAX_EFFECTS) return Optional.empty();
        List<BrewingEffectEntry> custom = new ArrayList<>();
        if (tag.getInt("version") == 2 || tag.contains("custom_effects")) {
            if (tag.getInt("version") == 1 || !(tag.get("custom_effects") instanceof ListTag entries) || entries.isEmpty()
                    || entries.getElementType() != Tag.TAG_COMPOUND || entries.size() + list.size() > MAX_EFFECTS) return Optional.empty();
            for (int i = 0; i < entries.size(); i++) {
                var entry = BrewingEffectEntry.read(entries.getCompound(i));
                if (entry.isEmpty()) return Optional.empty();
                custom.add(entry.get());
            }
        } else if (list.isEmpty() || tag.contains("custom_effects")) return Optional.empty();
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
            if (tag.getInt("version") != 1) return Optional.empty();
            if (savedCapacity >= 0 && savedCapacity <= MAX_CAPACITY) savedCapacity = Math.max(savedCapacity, effects.size());
            if (savedDuration >= 0) savedDuration = Math.min(savedDuration, MAX_DURATION);
        }
        try {
            return Optional.of(new BrewingPotionData(savedCapacity, tag.getInt("strength"),
                    savedDuration, Delivery.valueOf(tag.getString("delivery")),
                    tag.getBoolean("hide_particles"), tag.getBoolean("pressure_plates"), effects, costs, custom, tag.getInt("version")));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public int color() {
        long red = 0, green = 0, blue = 0;
        int count = effects.size() + customEffects.size();
        for (MobEffectInstance effect : effects) {
            int rgb = effect.getEffect().getColor();
            red += rgb >> 16 & 255;
            green += rgb >> 8 & 255;
            blue += rgb & 255;
        }
        for (BrewingEffectEntry entry : customEffects) {
            int rgb = entry.type() == BrewingEffectType.DYE && entry.color() >= 0
                    ? net.minecraft.world.item.DyeColor.byId(entry.color()).getFireworkColor() : entry.type().color();
            red += rgb >> 16 & 255;
            green += rgb >> 8 & 255;
            blue += rgb & 255;
        }
        return (int) (red / count) << 16 | (int) (green / count) << 8 | (int) (blue / count);
    }

    public void apply(LivingEntity target, double intensity) {
        BrewingPotionExecutor.applyEntity(this, target, target, target, intensity, true);
    }
}
