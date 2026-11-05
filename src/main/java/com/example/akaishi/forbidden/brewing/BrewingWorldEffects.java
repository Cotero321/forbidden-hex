package com.example.akaishi.forbidden.brewing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.level.BlockEvent;

public final class BrewingWorldEffects {
    private BrewingWorldEffects() { }
    public static void register() { }

    public static void apply(BrewingEffectType type, ServerLevel level, Vec3 origin, Entity owner,
                             int strength, int durationMultiplier, int color) {
        apply(type, level, origin, owner, strength, durationMultiplier, color, false);
    }

    public static void apply(BrewingEffectType type, ServerLevel level, Vec3 origin, Entity owner,
                             int strength, int durationMultiplier, int color, boolean currentRules) {
        if (!level.getServer().isSameThread() || !Double.isFinite(origin.x) || !Double.isFinite(origin.y) || !Double.isFinite(origin.z)) return;
        int s = Math.max(1, Math.min(5, strength));
        if (AlchemyPotionEffects.world(type, level, origin, owner, s, durationMultiplier, color, currentRules)) return;
        if (type == BrewingEffectType.DISPEL) {
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(origin, origin).inflate(3))) {
                if (entity.distanceToSqr(origin) > 9 || protectedPlayer(owner, entity)) continue;
                entity.removeAllEffects();
            }
            for (AreaEffectCloud cloud : level.getEntitiesOfClass(AreaEffectCloud.class, new AABB(origin, origin).inflate(3))) {
                if (cloud.distanceToSqr(origin) <= 9) cloud.discard();
            }
            return;
        }
        int radius = 2 + (s - 1) / 2;
        int limit = switch (type) {
            case CRUSH, CONDENSE, EROSION, OVERGROWTH -> 4 + 4 * s;
            case WITHER_PLANTS -> 8 + 8 * s;
            case ABSORB_WATER -> 16 + 16 * s;
            case WEB -> 2;
            default -> Integer.MAX_VALUE;
        };
        if (type == BrewingEffectType.WEB) {
            BlockPos feet = BlockPos.containing(origin);
            for (int i = 0; i < 2; i++) {
                BlockPos pos = feet.above(i);
                if (!available(level, pos) || !level.getBlockState(pos).canBeReplaced() || !level.getFluidState(pos).isEmpty()) continue;
                temporary(level, pos, Blocks.COBWEB.defaultBlockState(), owner,
                        level.getGameTime() + type.ticks(s, durationMultiplier, currentRules), null);
            }
            return;
        }
        if (type != BrewingEffectType.EXTINGUISH && type != BrewingEffectType.OVERGROWTH && type != BrewingEffectType.DYE
                && type != BrewingEffectType.BLEACH && type != BrewingEffectType.CRUSH && type != BrewingEffectType.CONDENSE
                && type != BrewingEffectType.ABSORB_WATER && type != BrewingEffectType.EROSION && type != BrewingEffectType.WITHER_PLANTS) return;
        BlockPos center = BlockPos.containing(origin);
        int changed = 0;
        for (BlockPos cursor : BlockPos.withinManhattan(center, radius, radius, radius)) {
            if (changed >= limit) break;
            BlockPos pos = cursor.immutable();
            if (pos.distSqr(center) > radius * radius || !available(level, pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.hasBlockEntity()) continue;
            BlockState replacement = switch (type) {
                case EXTINGUISH -> state.is(BlockTags.FIRE) ? Blocks.AIR.defaultBlockState() : null;
                case CRUSH -> state.is(Blocks.COBBLESTONE) ? Blocks.GRAVEL.defaultBlockState()
                        : state.is(Blocks.GRAVEL) ? Blocks.SAND.defaultBlockState() : null;
                case CONDENSE -> state.is(Blocks.SAND) ? Blocks.GRAVEL.defaultBlockState()
                        : state.is(Blocks.GRAVEL) ? Blocks.COBBLESTONE.defaultBlockState() : null;
                case DYE, BLEACH -> state.is(BlockTags.WOOL) ? wool(type == BrewingEffectType.BLEACH ? 0 : color) : null;
                case ABSORB_WATER -> state.getFluidState().is(FluidTags.WATER)
                        ? state.hasProperty(BlockStateProperties.WATERLOGGED) ? state.setValue(BlockStateProperties.WATERLOGGED, false)
                        : Blocks.AIR.defaultBlockState() : null;
                case EROSION -> !state.isAir() && state.getDestroySpeed(level, pos) >= 0
                        && !state.is(BlockTags.NEEDS_IRON_TOOL) && !state.is(BlockTags.NEEDS_DIAMOND_TOOL)
                        && state.getFluidState().isEmpty() ? Blocks.AIR.defaultBlockState() : null;
                case WITHER_PLANTS -> state.is(BlockTags.SAPLINGS) ? Blocks.DEAD_BUSH.defaultBlockState()
                        : plant(state) ? Blocks.AIR.defaultBlockState() : null;
                case OVERGROWTH -> vine(level, pos, state);
                default -> null;
            };
            if (replacement != null && replacement != state && change(level, pos, replacement, owner)) changed++;
        }
        if (type == BrewingEffectType.EXTINGUISH) {
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(origin, origin).inflate(radius))) {
                if (entity.distanceToSqr(origin) <= radius * radius) entity.clearFire();
            }
        }
    }

    public static boolean protectedPlayer(Entity owner, LivingEntity target) {
        return owner instanceof Player player && target instanceof Player other && player != other && !player.canHarmPlayer(other);
    }

    private static boolean plant(BlockState state) {
        return state.getBlock() instanceof CropBlock || state.getBlock() instanceof StemBlock
                || state.getBlock() instanceof AttachedStemBlock || state.getBlock() instanceof FlowerBlock
                || state.getBlock() instanceof TallGrassBlock || state.getBlock() instanceof DoublePlantBlock
                || state.is(Blocks.VINE) || state.is(Blocks.SUGAR_CANE) || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(BlockTags.LEAVES);
    }

    private static BlockState wool(int color) {
        if (color < 0 || color > 15) return null;
        return BuiltInRegistries.BLOCK.get(new net.minecraft.resources.ResourceLocation("minecraft",
                DyeColor.byId(color).getName() + "_wool")).defaultBlockState();
    }

    private static BlockState vine(ServerLevel level, BlockPos pos, BlockState old) {
        if (!old.isAir() || !level.getFluidState(pos).isEmpty()) return null;
        BlockState state = Blocks.VINE.defaultBlockState();
        boolean supported = false;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = pos.relative(side);
            if (available(level, neighbor) && level.getBlockState(neighbor).isFaceSturdy(level, neighbor, side.getOpposite())) {
                state = state.setValue(VineBlock.getPropertyForFace(side), true);
                supported = true;
            }
        }
        return supported && state.canSurvive(level, pos) ? state : null;
    }

    public static boolean available(ServerLevel level, BlockPos pos) {
        return !level.isOutsideBuildHeight(pos) && level.hasChunkAt(pos) && level.getWorldBorder().isWithinBounds(pos);
    }

    public static boolean mayChange(ServerLevel level, BlockPos pos, Entity owner) {
        if (!available(level, pos) || level.getBlockState(pos).hasBlockEntity()) return false;
        if (owner instanceof Player player) return !player.isSpectator() && player.mayBuild()
                && level.mayInteract(player, pos) && player.mayUseItemAt(pos, Direction.UP, ItemStack.EMPTY);
        return ForgeEventFactory.getMobGriefingEvent(level, owner);
    }

    static boolean change(ServerLevel level, BlockPos pos, BlockState replacement, Entity owner) {
        if (!mayChange(level, pos, owner)) return false;
        BlockState old = level.getBlockState(pos);
        if (!old.isAir() && owner instanceof Player player
                && MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, old, player))) return false;
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
        if (!level.setBlock(pos, replacement, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE)) return false;
        if (ForgeEventFactory.onBlockPlace(owner, snapshot, Direction.UP)) {
            snapshot.restore(true, false);
            return false;
        }
        BrewingWorldRestoration.get(level).forget(pos);
        level.updateNeighborsAt(pos, replacement.getBlock());
        return true;
    }

    static void temporary(ServerLevel level, BlockPos pos, BlockState replacement, Entity owner, long expires, LivingEntity aura) {
        var recovery = BrewingWorldRestoration.get(level);
        if (recovery.isTracked(pos)) {
            recovery.refresh(pos, replacement, expires, aura == null ? null : aura.getUUID());
            return;
        }
        if (!recovery.hasRoom()) return;
        BlockState original = level.getBlockState(pos);
        if (change(level, pos, replacement, owner)) recovery.remember(pos, original, replacement, expires,
                aura == null ? null : aura.getUUID());
    }

    public static void tickAura(LivingEntity entity, int strength) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        int radius = strength >= 3 ? 3 : 2;
        BlockPos center = entity.blockPosition();
        long expires = level.getGameTime() + 10;
        int changed = 0;
        for (BlockPos cursor : BlockPos.withinManhattan(center, radius, radius, radius)) {
            if (changed >= 96) break;
            BlockPos pos = cursor.immutable();
            if (pos.distSqr(center) > radius * radius || !available(level, pos)) continue;
            var recovery = BrewingWorldRestoration.get(level);
            if (recovery.isLavaAt(pos) && level.getBlockState(pos).isAir()) {
                recovery.refresh(pos, Blocks.AIR.defaultBlockState(), expires, entity.getUUID());
                continue;
            }
            if (recovery.isTracked(pos)) recovery.forget(pos);
            BlockState state = level.getBlockState(pos);
            if (state.getFluidState().is(FluidTags.LAVA)) {
                temporary(level, pos, Blocks.AIR.defaultBlockState(), entity, expires, entity);
                changed++;
            }
        }
    }

    public static void snow(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || !entity.onGround()) return;
        BlockPos pos = entity.blockPosition();
        BlockState snow = Blocks.SNOW.defaultBlockState();
        if (available(level, pos) && level.getBlockState(pos).isAir() && snow.canSurvive(level, pos)) change(level, pos, snow, entity);
    }

    public static void grow(LivingEntity entity, int strength) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        int remaining = Math.max(1, Math.min(5, strength));
        BlockPos center = entity.blockPosition();
        for (BlockPos cursor : BlockPos.withinManhattan(center, 3, 1, 3)) {
            BlockPos pos = cursor.immutable();
            if (pos.distSqr(center) > 9 || !mayChange(level, pos, entity)) continue;
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof BonemealableBlock plant) || !plant.isValidBonemealTarget(level, pos, state, false)) continue;
            Player player = entity instanceof Player p ? p : net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level);
            if (BoneMealItem.applyBonemeal(new ItemStack(Items.BONE_MEAL), level, pos, player) && --remaining == 0) break;
        }
    }
}
