package com.example.akaishi.forbidden.brewing;

import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import java.util.EnumMap;

/** Server-authoritative effects; durations and amplifiers use vanilla persistence/synchronization. */
public final class BrewingStatusEffects {
    private static final EnumMap<BrewingEffectType, RegistrySupplier<MobEffect>> EFFECTS = new EnumMap<>(BrewingEffectType.class);
    private BrewingStatusEffects() {}
    public static void register() {
        if (!EFFECTS.isEmpty()) return;
        var registry = RegistrarManager.get("forbidden_hex").get(Registries.MOB_EFFECT);
        for (BrewingEffectType type : BrewingEffectType.values()) {
            if (vanilla(type) == null && !type.instantaneous())
                EFFECTS.put(type, registry.register(type.id(), () -> new Status(type)));
        }
    }
    public static MobEffect get(BrewingEffectType type) {
        MobEffect vanilla = vanilla(type);
        return vanilla != null ? vanilla : EFFECTS.containsKey(type) ? EFFECTS.get(type).get() : null;
    }
    public static boolean has(LivingEntity entity, BrewingEffectType type) {
        MobEffect effect = get(type);
        return effect != null && entity.hasEffect(effect);
    }
    public static int strength(LivingEntity entity, BrewingEffectType type) {
        MobEffect effect = get(type);
        MobEffectInstance instance = effect == null ? null : entity.getEffect(effect);
        return instance == null ? 0 : Math.max(1, Math.min(5, instance.getAmplifier() + 1));
    }
    public static float damageReduction(LivingEntity entity, net.minecraft.world.damagesource.DamageSource source) {
        int drunk = strength(entity, BrewingEffectType.DRUNK);
        int twilight = strength(entity, BrewingEffectType.TWILIGHT);
        float reduction = Math.max(drunk == 0 ? 0f : .10f + .05f * drunk,
                twilight == 0 ? 0f : .05f + .05f * twilight);
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION) && has(entity, BrewingEffectType.INERT))
            reduction = Math.max(reduction, .15f + .05f * strength(entity, BrewingEffectType.INERT));
        return reduction;
    }

    public static void apply(BrewingEffectType type, LivingEntity target, Entity source, Entity owner,
                             int strength, int ticks, int color, boolean hideParticles, boolean firstApplication, double intensity) {
        if (!(target.level() instanceof ServerLevel level) || !target.isAlive()) return;
        float scale = (float) (Double.isFinite(intensity) ? Math.max(0, Math.min(1, intensity)) : 0);
        if (scale <= 0) return;
        int s = Math.max(1, Math.min(5, strength));
        ticks = BrewingFumePharmacology.prepareApplication(type, target, s, ticks);
        if (ticks <= 0) return;
        if (AlchemyPotionEffects.refined(type)) {
            AlchemyPotionEffects.apply(type, target, source, owner, s, ticks, hideParticles, scale);
            return;
        }
        int rank = (s - 1) / 2;
        switch (type) {
            case EXTINGUISH -> target.clearFire();
            case IGNITE -> target.setSecondsOnFire(2 + s);
            case SMITE -> { if (target.getMobType() == MobType.UNDEAD) target.hurt(level.damageSources().indirectMagic(source, owner), (6 + 2 * s) * scale); }
            case HEALING, HARMING -> {
                boolean heal = (type == BrewingEffectType.HEALING) != (target.getMobType() == MobType.UNDEAD);
                float amount = (type == BrewingEffectType.HEALING ? 2 + 2 * s : 4 + 2 * s) * scale;
                if (heal) target.heal(amount);
                else target.hurt(level.damageSources().indirectMagic(source, owner), amount);
            }
            case LAUNCH -> { target.setDeltaMovement(target.getDeltaMovement().x, Math.sqrt(0.16 * (2 + s)), target.getDeltaMovement().z); target.hurtMarked = true; }
            case SHIELD_BREAK -> target.setAbsorptionAmount(Math.max(0, target.getAbsorptionAmount() - (2 + 2 * s) * scale));
            case EROSION -> {
                for (EquipmentSlot slot : EquipmentSlot.values()) if (slot.getType() == EquipmentSlot.Type.ARMOR)
                    target.getItemBySlot(slot).hurtAndBreak(2 + 2 * s, target, e -> e.broadcastBreakEvent(slot));
            }
            case RANDOM_TELEPORT -> {
                if (has(target, BrewingEffectType.ANCHOR)) return;
                for (int i = 0; i < 16; i++) {
                    double angle = target.getRandom().nextDouble() * Math.PI * 2;
                    double radius = (6 + 2 * s) * (0.5 + target.getRandom().nextDouble() * 0.5);
                    double x = target.getX() + Math.cos(angle) * radius, z = target.getZ() + Math.sin(angle) * radius;
                    BlockPos pos = BlockPos.containing(x, target.getY(), z);
                    if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) continue;
                    while (pos.getY() > level.getMinBuildHeight() + 1 && level.getBlockState(pos.below()).isAir()) pos = pos.below();
                    BlockState floor = level.getBlockState(pos.below());
                    if (!floor.isSolid() || !floor.getFluidState().isEmpty() || floor.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                            || floor.is(net.minecraft.world.level.block.Blocks.CAMPFIRE) || !level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) continue;
                    if (target.randomTeleport(x, pos.getY(), z, true)) { target.fallDistance = 0; break; }
                }
            }
            case BLEACH -> {
                target.getPersistentData().remove("forbidden_hex_brewing_color");
                target.removeEffect(get(BrewingEffectType.DYE));
                if (target instanceof Sheep sheep) sheep.setColor(DyeColor.WHITE);
                if (target instanceof Wolf wolf && wolf.isTame()) wolf.setCollarColor(DyeColor.WHITE);
                if (target instanceof Cat cat && cat.isTame()) cat.setCollarColor(DyeColor.WHITE);
            }
            default -> {
                if (type == BrewingEffectType.ANTIDOTE) target.removeEffect(MobEffects.POISON);
                if (type == BrewingEffectType.EPIDEMIC_GUARD) target.removeEffect(get(BrewingEffectType.PLAGUE));
                if (type == BrewingEffectType.INERT) target.removeEffect(get(BrewingEffectType.EXPLOSIVE));
                if (type == BrewingEffectType.TWILIGHT) {
                    if (firstApplication) target.heal(3);
                    add(target, MobEffects.DAMAGE_BOOST, ticks, rank, hideParticles, owner);
                }
                if (type == BrewingEffectType.DYE) {
                    if (color < 0 || color > 15) return;
                    DyeColor dye = DyeColor.byId(color);
                    if (target instanceof Sheep sheep) sheep.setColor(dye);
                    if (target instanceof Wolf wolf && wolf.isTame()) wolf.setCollarColor(dye);
                    if (target instanceof Cat cat && cat.isTame()) cat.setCollarColor(dye);
                    target.removeEffect(get(type));
                    add(target, get(type), ticks, color, hideParticles, owner);
                    return;
                }
                if (type == BrewingEffectType.ABSORPTION) {
                    add(target, get(type), ticks, s - 1, hideParticles, owner);
                    trackAbsorption(target);
                    if (firstApplication) {
                        float before = target.getAbsorptionAmount();
                        float after = Math.max(before, 2 + 2 * s);
                        target.setAbsorptionAmount(after);
                        target.getPersistentData().putFloat("forbidden_hex_brewing_shield_remaining", target.getPersistentData().getFloat("forbidden_hex_brewing_shield_remaining") + after - before);
                        target.getPersistentData().putFloat("forbidden_hex_brewing_shield_observed", after);
                    }
                    return;
                }
                if (type == BrewingEffectType.TURTLE) {
                    add(target, MobEffects.DAMAGE_RESISTANCE, ticks, s >= 4 ? 3 : 2, hideParticles, owner);
                    add(target, MobEffects.MOVEMENT_SLOWDOWN, ticks, 3, hideParticles, owner);
                }
                int amplifier = switch (type) {
                    case SPEED, STRENGTH, SLOWNESS, WEAKNESS, LEVITATION -> rank;
                    case POISON, REGENERATION -> s >= 4 ? 1 : 0;
                    case LUCK, UNLUCK, JUMP_BOOST -> s - 1;
                    default -> s - 1;
                };
                int duration = type == BrewingEffectType.LEVITATION ? Math.min(400, ticks) : type == BrewingEffectType.BLINDNESS ? Math.min(600, ticks) : ticks;
                add(target, get(type), duration, amplifier, hideParticles, owner);
            }
        }
    }
    private static void add(LivingEntity target, MobEffect effect, int ticks, int amplifier, boolean hidden, Entity owner) {
        if (effect != null) target.addEffect(new MobEffectInstance(effect, Math.max(1, ticks), amplifier, false, !hidden, true), owner);
    }
    private static MobEffect vanilla(BrewingEffectType type) {
        return switch (type) {
            case GLOWING -> MobEffects.GLOWING; case LEVITATION -> MobEffects.LEVITATION;
            case LUCK -> MobEffects.LUCK; case UNLUCK -> MobEffects.UNLUCK; case JUMP_BOOST -> MobEffects.JUMP;
            case SLOW_FALLING -> MobEffects.SLOW_FALLING; case SPEED -> MobEffects.MOVEMENT_SPEED;
            case SLOWNESS -> MobEffects.MOVEMENT_SLOWDOWN; case STRENGTH -> MobEffects.DAMAGE_BOOST;
            case WEAKNESS -> MobEffects.WEAKNESS; case POISON -> MobEffects.POISON;
            case REGENERATION -> MobEffects.REGENERATION; case FIRE_RESISTANCE -> MobEffects.FIRE_RESISTANCE;
            case WATER_BREATHING -> MobEffects.WATER_BREATHING; case NIGHT_VISION -> MobEffects.NIGHT_VISION;
            case INVISIBILITY -> MobEffects.INVISIBILITY; case BLINDNESS -> MobEffects.BLINDNESS;
            default -> null;
        };
    }
    public static void trackAbsorption(LivingEntity entity) {
        var data = entity.getPersistentData();
        float current = entity.getAbsorptionAmount();
        float spent = Math.max(0, data.getFloat("forbidden_hex_brewing_shield_observed") - current);
        data.putFloat("forbidden_hex_brewing_shield_remaining", Math.min(current, Math.max(0, data.getFloat("forbidden_hex_brewing_shield_remaining") - spent)));
        data.putFloat("forbidden_hex_brewing_shield_observed", current);
    }
    private static MobEffectCategory category(BrewingEffectType type) {
        return switch (type) {
            case OVERGROWTH, FROST, PLAGUE, EXPLOSIVE, SINKING, FAMINE, HEAVY, FAST_FALLING, ANCHOR, WEB,
                    PHOTOSENSITIVITY, VISCOSITY, SLUMBER, WITHERING, CONVULSIONS, BERSERK, SEDATION,
                    HALLUCINATION, CHILLING_POISON, BAD_BLOOD, COLD_SUSCEPTIBILITY, DRUG_SENSITIVITY,
                    EXTREME_COLD, BEWILDERMENT, GRAVITY_FIELD, RUST, PIERCING, HELLFIRE, CORROSION, DELIRIUM -> MobEffectCategory.HARMFUL;
            case DYE, SNOW_TRAIL, SCORCH_TRAIL -> MobEffectCategory.NEUTRAL;
            default -> MobEffectCategory.BENEFICIAL;
        };
    }
    private static final class Status extends MobEffect {
        private final BrewingEffectType type;
        Status(BrewingEffectType type) {
            super(category(type), type.color());
            this.type = type;
            if (type == BrewingEffectType.SWIM_SPEED || type == BrewingEffectType.SINKING)
                addAttributeModifier(net.minecraftforge.common.ForgeMod.SWIM_SPEED.get(), java.util.UUID.nameUUIDFromBytes(type.id().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(), 0, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL);
            if (type == BrewingEffectType.FROST || type == BrewingEffectType.PLAGUE)
                addAttributeModifier(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, java.util.UUID.nameUUIDFromBytes(type.id().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(), 0, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL);
            var multiply = net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL;
            var addition = net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION;
            switch (type) {
                case TENACITY -> attribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE, "tenacity", addition);
                case HARDENING -> {
                    attribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR, "armor", addition);
                    attribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS, "toughness", addition);
                    attribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, "speed", multiply);
                }
                case BLOOD_RUSH -> attribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED, "attack_speed", multiply);
                case STRONG_HEART, WITHERING -> attribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, "health", multiply);
                case VISCOSITY -> attribute(net.minecraftforge.common.ForgeMod.SWIM_SPEED.get(), "swim", multiply);
                case CHILLING_POISON, SLUMBER -> attribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, "speed", multiply);
                case RUST, CORROSION -> attribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR, "armor", multiply);
                default -> { }
            }
        }
        private void attribute(net.minecraft.world.entity.ai.attributes.Attribute attribute, String name,
                               net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation operation) {
            addAttributeModifier(attribute, java.util.UUID.nameUUIDFromBytes((type.id() + "/" + name)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(), 0, operation);
        }
        private boolean modifier(net.minecraft.world.entity.ai.attributes.AttributeModifier modifier, String name) {
            return modifier.getId().equals(java.util.UUID.nameUUIDFromBytes((type.id() + "/" + name)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
        @Override public double getAttributeModifierValue(int amplifier, net.minecraft.world.entity.ai.attributes.AttributeModifier modifier) {
            int s = Math.max(1, Math.min(5, amplifier + 1));
            return switch (type) {
                case SWIM_SPEED -> .1 + .1 * s;
                case SINKING -> -(.15 + .05 * s);
                case FROST -> -(.10 + .05 * s);
                case PLAGUE -> -(.05 + .05 * s);
                case TWILIGHT -> 3 * (1 + (s - 1) / 2);
                case TENACITY -> .05 + .15 * s;
                case HARDENING -> modifier(modifier, "armor") ? 1 + s
                        : modifier(modifier, "toughness") ? 1 + (s - 1) / 2 : -(.04 + .01 * s);
                case BLOOD_RUSH -> .05 + .05 * s;
                case STRONG_HEART -> .05 + .05 * s;
                case WITHERING -> -(.05 + .05 * s);
                case VISCOSITY -> -(.15 + .05 * s);
                case CHILLING_POISON -> -(.1 + .05 * s);
                case SLUMBER -> -.9;
                case RUST -> -Math.min(.8, .5 + .05 * (s - 1));
                case CORROSION -> -Math.min(.5, .25 + .05 * (s - 1));
                default -> super.getAttributeModifierValue(amplifier, modifier);
            };
        }
        @Override public void addAttributeModifiers(LivingEntity entity, net.minecraft.world.entity.ai.attributes.AttributeMap attributes, int amplifier) {
            super.addAttributeModifiers(entity, attributes, amplifier);
            if (type == BrewingEffectType.STRONG_HEART || type == BrewingEffectType.WITHERING) BrewingFumePharmacology.clampHealth(entity);
        }
        @Override public void removeAttributeModifiers(LivingEntity entity, net.minecraft.world.entity.ai.attributes.AttributeMap attributes, int amplifier) {
            super.removeAttributeModifiers(entity, attributes, amplifier);
            if (type == BrewingEffectType.ABSORPTION) {
                trackAbsorption(entity);
                entity.setAbsorptionAmount(Math.max(0, entity.getAbsorptionAmount() - entity.getPersistentData().getFloat("forbidden_hex_brewing_shield_remaining")));
                entity.getPersistentData().remove("forbidden_hex_brewing_shield_remaining");
                entity.getPersistentData().remove("forbidden_hex_brewing_shield_observed");
            }
        }
    }
}
