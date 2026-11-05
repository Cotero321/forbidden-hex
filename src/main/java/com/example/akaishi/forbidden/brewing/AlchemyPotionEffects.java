package com.example.akaishi.forbidden.brewing;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class AlchemyPotionEffects {
    private static final String BLEED_POSITION = "forbidden_hex_alchemy_bleed_position";
    private static final String BLEED_MOVING = "forbidden_hex_alchemy_bleed_moving";
    private static final String BLEED_READY = "forbidden_hex_alchemy_bleed_ready";
    private static final String HELLFIRE_READY = "forbidden_hex_alchemy_hellfire_ready";
    private static final String DELIRIUM_TARGET = "forbidden_hex_alchemy_delirium_target";
    private static final ThreadLocal<Entity> EXPLOSION_OWNER = new ThreadLocal<>();
    private AlchemyPotionEffects() { }
    public static boolean protectsFromExplosion(LivingEntity target) {
        Entity owner = EXPLOSION_OWNER.get();
        return owner != null && BrewingWorldEffects.protectedPlayer(owner, target);
    }
    public static boolean refined(BrewingEffectType type) {
        return switch (type) {
            case DEFLAGRATION, EXTREME_COLD, FLOURISHING, BEWILDERMENT, GRAVITY_FIELD, THUNDER,
                    RUST, PIERCING, HELLFIRE, CORROSION, PURGE_BENEFITS, SONIC_BURST, DELIRIUM -> true;
            default -> false;
        };
    }
    public static boolean world(BrewingEffectType type, ServerLevel level, Vec3 origin, Entity owner, int strength, int duration, int color) {
        return world(type, level, origin, owner, strength, duration, color, false);
    }
    public static boolean world(BrewingEffectType type, ServerLevel level, Vec3 origin, Entity owner,
                                int strength, int duration, int color, boolean currentRules) {
        if (!refined(type)) return false;
        int s = Math.max(1, Math.min(5, strength));
        switch (type) {
            case DEFLAGRATION -> {
                float power = 2 + (s - 1) * .25F;
                Entity previous = EXPLOSION_OWNER.get();
                if (owner == null) EXPLOSION_OWNER.remove(); else EXPLOSION_OWNER.set(owner);
                try {
                    level.explode(owner, null, null, origin.x, origin.y, origin.z, power, false, Level.ExplosionInteraction.NONE);
                } finally {
                    if (previous == null) EXPLOSION_OWNER.remove(); else EXPLOSION_OWNER.set(previous);
                }
            }
            case EXTREME_COLD -> {
                BlockPos center = BlockPos.containing(origin);
                long expires = level.getGameTime() + type.ticks(s, duration, currentRules);
                for (BlockPos cursor : BlockPos.betweenClosed(center.offset(-3, -1, -3), center.offset(3, 1, 3))) {
                    BlockPos pos = cursor.immutable();
                    if (pos.distSqr(center) > 9 || !BrewingWorldEffects.available(level, pos) || !BrewingWorldEffects.available(level, pos.below())) continue;
                    BlockState state = level.getBlockState(pos);
                    if (state.is(Blocks.POWDER_SNOW) && BrewingWorldRestoration.get(level).isTracked(pos)) {
                        BrewingWorldEffects.temporary(level, pos, state, owner, expires, null);
                    } else if (state.isAir() && level.getFluidState(pos).isEmpty()
                            && level.getBlockState(pos.below()).isSolidRender(level, pos.below())) {
                        BrewingWorldEffects.temporary(level, pos, Blocks.POWDER_SNOW.defaultBlockState(), owner, expires, null);
                    }
                }
                for (LivingEntity target : targets(level, origin, 3, owner)) BrewingStatusEffects.apply(type, target, owner, owner, s, type.ticks(s, duration, currentRules), color, false, true, 1);
            }
            case FLOURISHING -> flourish(level, BlockPos.containing(origin), owner);
            case GRAVITY_FIELD -> {
                BrewingGravityFields.get(level).add(level, origin, owner, s, type.ticks(s, duration, currentRules));
                for (LivingEntity target : targets(level, origin, 3, owner)) BrewingStatusEffects.apply(type, target, owner, owner, s, 10, color, false, true, 1);
            }
            case THUNDER -> {
                if (level.isThundering() && level.isRainingAt(BlockPos.containing(origin)) && level.canSeeSky(BlockPos.containing(origin))) {
                    LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                    if (bolt != null) {
                        bolt.moveTo(origin);
                        bolt.getPersistentData().putBoolean("forbidden_hex_alchemy_lightning", true);
                        if (owner instanceof ServerPlayer player) bolt.setCause(player);
                        level.addFreshEntity(bolt);
                    }
                } else for (LivingEntity target : targets(level, origin, 3, owner)) target.hurt(level.damageSources().indirectMagic(owner, owner), 6 + 2 * (s - 1));
            }
            case CORROSION -> {
                BlockPos center = BlockPos.containing(origin);
                for (BlockPos cursor : BlockPos.withinManhattan(center, 2, 2, 2)) {
                    BlockPos pos = cursor.immutable();
                    if (pos.distSqr(center) > 4 || !BrewingWorldEffects.available(level, pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    if (corrodible(state) && !state.hasBlockEntity() && state.getDestroySpeed(level, pos) >= 0
                            && state.getDestroySpeed(level, pos) <= .6F && state.getFluidState().isEmpty()) {
                        BrewingWorldEffects.change(level, pos, Blocks.AIR.defaultBlockState(), owner);
                    }
                }
            }
            case SONIC_BURST -> {
                for (LivingEntity target : targets(level, origin, 4, owner)) {
                    Vec3 away = target.position().subtract(origin).multiply(1, 0, 1);
                    if (away.lengthSqr() < .0001) away = owner == null ? new Vec3(1, 0, 0) : owner.getLookAngle().multiply(1, 0, 1);
                    away = away.normalize().scale(1.6 + .2 * (s - 1));
                    target.setDeltaMovement(target.getDeltaMovement().add(away.x, .45, away.z)); target.hurtMarked = true;
                }
            }
            default -> { }
        }
        return true;
    }
    private static List<LivingEntity> targets(ServerLevel level, Vec3 origin, double radius, Entity owner) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(origin, origin).inflate(radius),
                target -> target.isAlive() && !target.isSpectator() && target.isAffectedByPotions()
                        && target.distanceToSqr(origin) <= radius * radius && !BrewingWorldEffects.protectedPlayer(owner, target));
    }
    public static boolean corrodible(BlockState state) {
        return state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)
                || state.is(Blocks.GRAVEL) || state.is(Blocks.MUD) || state.is(Blocks.SOUL_SAND) || state.is(Blocks.SOUL_SOIL);
    }
    private static void flourish(ServerLevel level, BlockPos center, Entity owner) {
        for (BlockPos cursor : BlockPos.betweenClosed(center.offset(-4, -2, -4), center.offset(4, 2, 4))) {
            BlockPos pos = cursor.immutable();
            if (pos.distSqr(center) > 16 || !BrewingWorldEffects.mayChange(level, pos, owner)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof CropBlock crop) {
                BrewingWorldEffects.change(level, pos, crop.getStateForAge(crop.getMaxAge()), owner);
            } else if (state.is(Blocks.NETHER_WART)) {
                BrewingWorldEffects.change(level, pos, state.setValue(NetherWartBlock.AGE, 3), owner);
            } else if (state.is(Blocks.SWEET_BERRY_BUSH)) {
                BrewingWorldEffects.change(level, pos, state.setValue(SweetBerryBushBlock.AGE, 3), owner);
            } else {
                Player actor = owner instanceof Player player ? player : FakePlayerFactory.getMinecraft(level);
                for (int i = 0; i < 8; i++) {
                    state = level.getBlockState(pos);
                    if (!(state.getBlock() instanceof BonemealableBlock plant) || !plant.isValidBonemealTarget(level, pos, state, false)) break;
                    if (!BoneMealItem.applyBonemeal(new ItemStack(Items.BONE_MEAL), level, pos, actor)) break;
                }
            }
        }
    }
    public static void apply(BrewingEffectType type, LivingEntity target, Entity source, Entity owner, int strength, int ticks, boolean hidden, double intensity) {
        if (BrewingWorldEffects.protectedPlayer(owner, target) && type.harmful()) return;
        if (type == BrewingEffectType.PURGE_BENEFITS) {
            for (MobEffectInstance effect : new ArrayList<>(target.getActiveEffects()))
                if (effect.getEffect().getCategory() == MobEffectCategory.BENEFICIAL) target.removeEffect(effect.getEffect());
            return;
        }
        if (type.instantaneous()) return;
        var effect = BrewingStatusEffects.get(type);
        if (effect == null) return;
        if (type == BrewingEffectType.EXTREME_COLD || type == BrewingEffectType.GRAVITY_FIELD)
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 3, false, !hidden, true), owner);
        if (type == BrewingEffectType.CORROSION)
            target.addEffect(new MobEffectInstance(MobEffects.POISON, ticks, strength >= 4 ? 1 : 0, false, !hidden, true), owner);
        target.addEffect(new MobEffectInstance(effect, ticks, Math.max(0, strength - 1), false, !hidden, true), source);
    }
    public static void tick(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || !entity.isAlive()) return;
        CompoundTag data = entity.getPersistentData();
        long now = level.getGameTime();
        if (BrewingStatusEffects.has(entity, BrewingEffectType.PIERCING)) {
            Vec3 current = entity.position();
            CompoundTag old = data.getCompound(BLEED_POSITION);
            boolean moving = entity.isSprinting() || data.contains(BLEED_POSITION)
                    && Math.pow(current.x - old.getDouble("x"), 2) + Math.pow(current.z - old.getDouble("z"), 2) > .0001;
            if (moving) data.putBoolean(BLEED_MOVING, true);
            if (!data.contains(BLEED_READY) || data.getLong(BLEED_READY) > now + 20) data.putLong(BLEED_READY, now + 20);
            if (now >= data.getLong(BLEED_READY)) {
                if (data.getBoolean(BLEED_MOVING)) entity.hurt(level.damageSources().magic(), 2 + .5F * (BrewingStatusEffects.strength(entity, BrewingEffectType.PIERCING) - 1));
                data.putLong(BLEED_READY, now + 20); data.putBoolean(BLEED_MOVING, false);
            }
            CompoundTag position = new CompoundTag(); position.putDouble("x", current.x); position.putDouble("z", current.z); data.put(BLEED_POSITION, position);
        } else { data.remove(BLEED_POSITION); data.remove(BLEED_READY); data.remove(BLEED_MOVING); }
        if (BrewingStatusEffects.has(entity, BrewingEffectType.HELLFIRE)) {
            entity.setRemainingFireTicks(Math.max(40, entity.getRemainingFireTicks()));
            if (!data.contains(HELLFIRE_READY) || data.getLong(HELLFIRE_READY) > now + 20) data.putLong(HELLFIRE_READY, now + 20);
            if (now >= data.getLong(HELLFIRE_READY)) {
                entity.hurt(level.damageSources().onFire(), 2 + .5F * (BrewingStatusEffects.strength(entity, BrewingEffectType.HELLFIRE) - 1));
                data.putLong(HELLFIRE_READY, now + 20);
            }
        } else if (data.contains(HELLFIRE_READY)) { data.remove(HELLFIRE_READY); entity.clearFire(); }
        if (BrewingStatusEffects.has(entity, BrewingEffectType.GRAVITY_FIELD)) {
            entity.setJumping(false);
            Vec3 velocity = entity.getDeltaMovement();
            entity.setDeltaMovement(velocity.x, Math.min(0, velocity.y), velocity.z);
        }
        if (entity instanceof Monster monster) {
            if (BrewingStatusEffects.has(entity, BrewingEffectType.DELIRIUM) && !BrewingFumePharmacology.boss(entity)
                    && !BrewingFumePharmacology.asleep(entity) && !BrewingStatusEffects.has(entity, BrewingEffectType.SEDATION)) {
                if (now % 10 == 0) {
                    LivingEntity nearest = level.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(12),
                                    candidate -> candidate != entity && candidate.isAlive() && !candidate.isSpectator()
                                            && !(candidate instanceof Player player && (player.isCreative() || player.isSpectator()))
                                            && monster.hasLineOfSight(candidate))
                            .stream().min(Comparator.comparingDouble(entity::distanceToSqr)).orElse(null);
                    monster.setTarget(nearest);
                    if (nearest != null) data.putUUID(DELIRIUM_TARGET, nearest.getUUID()); else data.remove(DELIRIUM_TARGET);
                }
            } else if (data.hasUUID(DELIRIUM_TARGET)) {
                if (monster.getTarget() != null && monster.getTarget().getUUID().equals(data.getUUID(DELIRIUM_TARGET))) monster.setTarget(null);
                data.remove(DELIRIUM_TARGET);
            }
        }
    }
}
