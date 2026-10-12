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
                                List<MobEffectInstance> effects) {
    public static final String TAG = "forbidden_hex_brewing";
    public static final int VOLUME = 250;
    public static final int MAX_EFFECTS = 64;
    public static final int MAX_AMPLIFIER = 127;

    public enum Delivery { DRINK, SPLASH, LINGERING, DIRECT }

    public BrewingPotionData {
        if (capacity < 0 || capacity > 64 || strength < 1 || strength > 5 || duration < 0
                || delivery == null || effects == null || effects.isEmpty() || effects.size() > MAX_EFFECTS) {
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
        try {
            return Optional.of(new BrewingPotionData(tag.getInt("capacity"), tag.getInt("strength"),
                    tag.getInt("duration"), Delivery.valueOf(tag.getString("delivery")),
                    tag.getBoolean("hide_particles"), tag.getBoolean("pressure_plates"), effects));
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
