package com.example.akaishi.forbidden.boss.chimera;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import com.example.akaishi.forbidden.entity.AkaishiForbiddenEntities;
import com.example.akaishi.forbidden.magic.MagicDamageTypes;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.ForgeEventFactory;
import com.example.akaishi.forbidden.boss.agaitolos.arena.NetherPrisonArena;
import com.example.akaishi.forbidden.brewing.BrewingWorldRestoration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.common.ForgeHooks;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

public class ChimeraEntity extends Monster implements GeoEntity {
    public static final int TITLE_INTERVAL_TICKS = 200;
    public static final float MAX_SINGLE_DAMAGE = 20F;
    public static final int DAMAGE_INTERVAL_TICKS = 4;
    public static final double ARENA_RADIUS = 30D;
    public static final double RETURN_RADIUS = 20D;
    private static final UUID WOLF_SPEED_MODIFIER = UUID.fromString("406c20df-51de-4fa6-aed0-023f046e9953");
    private static final UUID TITLE_ARMOR_MODIFIER = UUID.fromString("bd930bce-64cf-40b4-bf45-56e6e82326f4");
    private static final UUID TITLE_WALK_MODIFIER = UUID.fromString("14855659-19f4-4f74-8d1e-e8b052521b15");
    private static final UUID TITLE_FLIGHT_MODIFIER = UUID.fromString("1071ae33-2d48-4c61-a98c-78ca91006d1d");
    private static final EntityDataAccessor<Integer> TITLE = SynchedEntityData.defineId(ChimeraEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SECOND_PHASE = SynchedEntityData.defineId(ChimeraEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> FLYING = SynchedEntityData.defineId(ChimeraEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Byte> DISABLED_HEADS = SynchedEntityData.defineId(ChimeraEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> HEAD_ACTIONS = SynchedEntityData.defineId(ChimeraEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BODY_ACTION = SynchedEntityData.defineId(ChimeraEntity.class, EntityDataSerializers.INT);
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private long nextTitleTick;
    private long lastDamageTick = Long.MIN_VALUE;
    private DamageSource incomingSpellSource;
    private float incomingPsychicFraction;
    private Vec3 arenaCenter;
    private final Map<ChimeraHead, UUID> headHitboxes = new EnumMap<>(ChimeraHead.class);
    private final Set<UUID> participants = new HashSet<>();
    private final Map<UUID, Vec3> groundedPositions = new HashMap<>();
    private final ChimeraSkillDirector skillDirector = new ChimeraSkillDirector();
    private int wolfEmpowermentTicks;
    private int dragonShieldTicks;
    private float dragonShieldCapacity;
    private int birdChantTicks;
    private int kindnessRegenTicks;
    private int magicWardTicks;
    private int stationaryTicks;
    private final BodyController bodyController = new BodyController();

    public enum BodyAction { NONE, TAKEOFF, LANDING, TAIL, DIVE_ASCENT, DIVE }

    public ChimeraEntity(EntityType<? extends ChimeraEntity> type, Level level) { super(type, level); setPersistenceRequired(); }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 5000D).add(Attributes.ATTACK_DAMAGE, 15D)
                .add(Attributes.ARMOR, 8D).add(Attributes.KNOCKBACK_RESISTANCE, 1D).add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 64D).add(Attributes.FLYING_SPEED, 0.4D);
    }

    @Override public double getAttributeValue(Attribute attribute) {
        if (attribute != Attributes.MAX_HEALTH) return super.getAttributeValue(attribute);
        var instance = getAttribute(attribute);
        if (instance == null) return super.getAttributeValue(attribute);
        double base = instance.getBaseValue();
        for (AttributeModifier modifier : instance.getModifiers(AttributeModifier.Operation.ADDITION)) base += modifier.getAmount();
        double value = base;
        for (AttributeModifier modifier : instance.getModifiers(AttributeModifier.Operation.MULTIPLY_BASE)) value += base * modifier.getAmount();
        for (AttributeModifier modifier : instance.getModifiers(AttributeModifier.Operation.MULTIPLY_TOTAL)) value *= 1D + modifier.getAmount();
        if (getTitle() == ChimeraTitle.GLUTTONY) value += 500D;
        return Double.isFinite(value) ? Math.max(1D, Math.min(Float.MAX_VALUE, value)) : 1D;
    }

    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(TITLE, ChimeraTitle.PRIDE.ordinal());
        entityData.define(SECOND_PHASE, false);
        entityData.define(FLYING, false);
        entityData.define(DISABLED_HEADS, (byte) 0);
        entityData.define(HEAD_ACTIONS, 0);
        entityData.define(BODY_ACTION, 0);
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 48F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide || !isAlive()) return;
        if (arenaCenter == null) arenaCenter = position();
        ensureHeadHitboxes();
        tickSupportEffects();
        tickTitleEffects();
        if (!isAlive() || isNoAi()) { tickBodyActions(); return; }
        if (nextTitleTick == 0L) {
            setTitle(ChimeraTitle.random());
            nextTitleTick = level().getGameTime() + TITLE_INTERVAL_TICKS;
        }
        collectParticipants();
        tickBodyActions();
        if (!isAlive() || isNoAi()) return;
        skillDirector.tick(this);
        if (magicWardTicks > 0) magicWardTicks--;
        if (stationaryTicks > 0) { stationaryTicks--; setDeltaMovement(Vec3.ZERO); }
        if (!isNoAi() && !isFlyingMode() && getBodyAction() == BodyAction.NONE && getTarget() != null && tickCount % 10 == 0) {
            if (stationaryTicks > 0 || getTitle() == ChimeraTitle.PATIENCE) getNavigation().stop();
            else getNavigation().moveTo(getTarget(), 1D);
        }
        if (!isSecondPhase() && getHealth() <= getMaxHealth() * 0.5F) enterSecondPhase();
        else if (level().getGameTime() >= nextTitleTick) rotateTitle();
        collectParticipants();
        pullPlayersBack();
        if (isSecondPhase()) enforceGroundedPlayers();
    }

    private void ensureHeadHitboxes() {
        if (tickCount % 10 != 0 || AkaishiForbiddenEntities.CHIMERA_HEAD == null) return;
        for (ChimeraHead head : ChimeraHead.values()) {
            Entity entity = headHitboxes.get(head) == null ? null : ((ServerLevel) level()).getEntity(headHitboxes.get(head));
            if (entity instanceof ChimeraHeadHitbox) continue;
            ChimeraHeadHitbox existing = level().getEntitiesOfClass(ChimeraHeadHitbox.class, getBoundingBox().inflate(8D)).stream()
                    .filter(part -> getUUID().equals(part.ownerId()) && part.headIndex() == head.index()).findFirst().orElse(null);
            if (existing != null) {
                headHitboxes.put(head, existing.getUUID());
                continue;
            }
            ChimeraHeadHitbox hitbox = new ChimeraHeadHitbox(AkaishiForbiddenEntities.CHIMERA_HEAD.get(), level());
            hitbox.bind(this, head);
            level().addFreshEntity(hitbox);
            headHitboxes.put(head, hitbox.getUUID());
        }
    }

    private void rotateTitle() {
        setTitle(ChimeraTitle.random());
        nextTitleTick = level().getGameTime() + TITLE_INTERVAL_TICKS;
        skillDirector.onTitleChanged(this);
        roar();
    }

    private void enterSecondPhase() { entityData.set(SECOND_PHASE, true); rotateTitle(); }

    private void roar() {
        for (LivingEntity entity : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(5D), e -> e != this)) {
            Vec3 delta = entity.position().subtract(position());
            if (delta.lengthSqr() > 0.0001D) entity.push(delta.normalize().scale(1.1D).x,
                    isSecondPhase() && entity instanceof Player player && participants.contains(player.getUUID()) ? 0D : 0.45D,
                    delta.normalize().scale(1.1D).z);
        }
    }

    private static boolean eligible(Player player) {
        return player.isAlive() && !player.isSpectator() && !player.isCreative();
    }

    private void collectParticipants() {
        participants.removeIf(id -> {
            Player player = level().getPlayerByUUID(id);
            return player == null || !eligible(player);
        });
        groundedPositions.keySet().retainAll(participants);
        if (!(getTarget() instanceof Player target) || !eligible(target)) return;
        participants.add(target.getUUID());
        for (ServerPlayer player : ((ServerLevel) level()).players()) {
            if (eligible(player) && player.distanceToSqr(arenaCenter()) <= ARENA_RADIUS * ARENA_RADIUS) {
                participants.add(player.getUUID());
            }
        }
    }

    private void pullPlayersBack() {
        for (ServerPlayer player : ((ServerLevel) level()).players()) {
            if (!participants.contains(player.getUUID()) || !eligible(player)) continue;
            if (player.distanceToSqr(arenaCenter()) <= RETURN_RADIUS * RETURN_RADIUS) continue;
            Vec3 destination = safeArenaReturnPosition(player);
            if (destination != null) {
                player.teleportTo(destination.x, destination.y, destination.z);
                player.resetFallDistance();
                groundedPositions.put(player.getUUID(), destination);
            }
        }
    }

    Vec3 safeGroundPosition(Player player, Vec3 origin) {
        int[] offsets = {0, -1, 1, -2, 2};
        for (int depth = 0; depth <= 16; depth++) {
            for (int offsetX : offsets) {
                for (int offsetZ : offsets) {
                    BlockPos support = BlockPos.containing(origin).offset(offsetX, -depth, offsetZ);
                    if (!level().hasChunkAt(support)) continue;
                    var shape = level().getBlockState(support).getCollisionShape(level(), support);
                    if (shape.isEmpty()) continue;
                    Vec3 destination = new Vec3(origin.x + offsetX, support.getY() + shape.max(Direction.Axis.Y), origin.z + offsetZ);
                    AABB bounds = player.getBoundingBox().move(destination.subtract(player.position()));
                    if (level().getWorldBorder().isWithinBounds(bounds) && level().noCollision(player, bounds)
                            && level().getBlockCollisions(player, bounds.move(0D, -0.05D, 0D)).iterator().hasNext()
                            && !level().containsAnyLiquid(bounds)) return destination;
                }
            }
        }
        return null;
    }

    private Vec3 safeArenaReturnPosition(Player player) {
        Vec3 destination = safeGroundPosition(player, arenaCenter());
        if (destination != null) return destination;
        int[] offsets = {0, -1, 1, -2, 2};
        BlockPos center = BlockPos.containing(arenaCenter());
        for (int offsetX : offsets) {
            for (int offsetZ : offsets) {
                BlockPos column = center.offset(offsetX, 0, offsetZ);
                if (!level().hasChunkAt(column)) continue;
                int height = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
                if (height <= level().getMinBuildHeight()) continue;
                destination = safeGroundPosition(player, new Vec3(column.getX() + 0.5D, height, column.getZ() + 0.5D));
                if (destination != null) return destination;
            }
        }
        return null;
    }

    private void enforceGroundedPlayers() {
        for (ServerPlayer player : ((ServerLevel) level()).players()) {
            if (!participants.contains(player.getUUID()) || !eligible(player)) continue;
            if (player.distanceToSqr(arenaCenter()) > ARENA_RADIUS * ARENA_RADIUS) continue;
            if (player.getAbilities().flying) {
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }
            if (player.isFallFlying()) player.stopFallFlying();
            if (player.isPassenger()) player.stopRiding();
            if (player.onGround() && player.getDeltaMovement().y <= 0D) {
                groundedPositions.put(player.getUUID(), player.position());
                continue;
            }
            Vec3 destination = safeGroundPosition(player, player.position());
            if (destination == null) {
                Vec3 previous = groundedPositions.get(player.getUUID());
                if (previous != null) destination = safeGroundPosition(player, previous);
            }
            if (destination == null) destination = safeArenaReturnPosition(player);
            if (destination == null) continue;
            player.teleportTo(destination.x, destination.y, destination.z);
            player.setDeltaMovement(player.getDeltaMovement().x, 0D, player.getDeltaMovement().z);
            player.resetFallDistance();
            groundedPositions.put(player.getUUID(), destination);
        }
    }

    @Override public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || !Float.isFinite(amount) || amount <= 0F || !isAlive()) return false;
        if (source.is(DamageTypes.FALL) || source.is(DamageTypes.IN_WALL)) return false;
        if (hasDotImmunity() && source.is(DamageTypes.WITHER)) return false;
        long now = level().getGameTime();
        if (lastDamageTick != Long.MIN_VALUE && now - lastDamageTick < DAMAGE_INTERVAL_TICKS) return false;
        float reduced = Math.min(MAX_SINGLE_DAMAGE, amount * 0.7F * incomingDamageMultiplier(source));
        int previousInvulnerability = invulnerableTime;
        long previousDamageTick = lastDamageTick;
        float previousHealth = getHealth();
        float previousAbsorption = getAbsorptionAmount();
        float previousShield = dragonShieldCapacity;
        DamageSource previousSpellSource = incomingSpellSource;
        float previousPsychicFraction = incomingPsychicFraction;
        incomingSpellSource = source;
        incomingPsychicFraction = source instanceof MagicDamageTypes.SpellSource spell
                ? spell.psychicFraction() / incomingMagicDamageMultiplier(source) : 0F;
        lastDamageTick = now;
        invulnerableTime = 0;
        boolean accepted = false;
        try {
            accepted = super.hurt(source, reduced);
            if (accepted && source.getEntity() instanceof Player player && eligible(player)) participants.add(player.getUUID());
            if (accepted && getTitle() == ChimeraTitle.ENVY
                    && (getHealth() < previousHealth || getAbsorptionAmount() < previousAbsorption || dragonShieldCapacity < previousShield)
                    && source.getEntity() instanceof LivingEntity attacker && attacker != this && attacker.isAlive()) {
                attacker.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40));
            }
            return accepted;
        } finally {
            incomingSpellSource = previousSpellSource;
            incomingPsychicFraction = previousPsychicFraction;
            if (!accepted) {
                lastDamageTick = previousDamageTick;
                invulnerableTime = previousInvulnerability;
            }
        }
    }

    @Override public boolean doHurtTarget(Entity target) {
        if (!isAlive() || !(target instanceof LivingEntity living) || !hasLineOfSight(living)) return false;
        boolean hit = living.hurt(damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE)
                * outgoingDamageMultiplier() * meleeDamageMultiplier());
        if (hit && getTitle() == ChimeraTitle.WRATH && living instanceof Player player) {
            ChimeraPoolDrainService.drainCurrentPools(player, ChimeraPoolDrainService.WRATH_CURRENT_POOL_FRACTION);
        }
        return hit;
    }

    public static void settleGreedHealthDamage(LivingEntity victim, DamageSource source, float previousHealth) {
        if (victim.level().isClientSide || !(source.getEntity() instanceof ChimeraEntity owner) || owner == victim
                || !owner.isAlive() || owner.getTitle() != ChimeraTitle.GREED || owner.level() != victim.level()) return;
        if (source.getDirectEntity() instanceof ChimeraSkillDirector.MagicOrb orb && !orb.isOwnedAttackOf(owner)) return;
        float loss = previousHealth - victim.getHealth();
        if (!Float.isFinite(previousHealth) || !Float.isFinite(loss) || previousHealth <= 0F || loss <= 0F) return;
        owner.heal(Math.min(previousHealth, loss) * 0.20F);
    }

    public ChimeraTitle getTitle() { int value = entityData.get(TITLE); return value >= 0 && value < ChimeraTitle.values().length ? ChimeraTitle.values()[value] : ChimeraTitle.PRIDE; }
    public void setTitle(ChimeraTitle title) {
        entityData.set(TITLE, title.ordinal());
        kindnessRegenTicks = 0;
        refreshTitleAttributes();
        clearTitleImmuneEffects();
        if (!level().isClientSide && getHealth() > getMaxHealth()) setHealth(getMaxHealth());
        if (!level().isClientSide) bodyController.titleChanged();
    }

    @Override public boolean canBeAffected(MobEffectInstance effect) {
        if (isTitleImmuneEffect(effect.getEffect())) return false;
        return super.canBeAffected(effect);
    }

    private boolean hasDotImmunity() {
        return getTitle() == ChimeraTitle.SLOTH || getTitle() == ChimeraTitle.CHASTITY;
    }

    private boolean isTitleImmuneEffect(MobEffect effect) {
        if (hasDotImmunity() && (effect == MobEffects.POISON || effect == MobEffects.WITHER)) return true;
        if (getTitle() == ChimeraTitle.TEMPERANCE
                && (effect == MobEffects.MOVEMENT_SLOWDOWN || effect == MobEffects.WEAKNESS)) return true;
        return getTitle() == ChimeraTitle.CHASTITY && (effect == MobEffects.BLINDNESS || effect == MobEffects.CONFUSION);
    }

    private void clearTitleImmuneEffects() {
        if (level().isClientSide) return;
        for (MobEffect effect : new MobEffect[]{MobEffects.BLINDNESS, MobEffects.CONFUSION, MobEffects.POISON,
                MobEffects.WITHER, MobEffects.MOVEMENT_SLOWDOWN, MobEffects.WEAKNESS}) {
            if (isTitleImmuneEffect(effect)) {
                MobEffectInstance removed = removeEffectNoUpdate(effect);
                if (removed != null) onEffectRemoved(removed);
            }
        }
    }

    private void refreshTitleAttributes() {
        if (level().isClientSide) return;
        ChimeraTitle title = getTitle();
        updateTitleModifier(Attributes.ARMOR, TITLE_ARMOR_MODIFIER, title == ChimeraTitle.SLOTH ? 0.50D : 0D);
        updateTitleModifier(Attributes.MOVEMENT_SPEED, TITLE_WALK_MODIFIER,
                title == ChimeraTitle.LUST ? 0.40D : title == ChimeraTitle.CHARITY ? -0.30D : 0D);
        updateTitleModifier(Attributes.FLYING_SPEED, TITLE_FLIGHT_MODIFIER, title == ChimeraTitle.LUST ? 0.40D : 0D);
    }

    private void updateTitleModifier(Attribute attribute, UUID id, double amount) {
        var instance = getAttribute(attribute);
        if (instance == null) return;
        AttributeModifier existing = instance.getModifier(id);
        if (existing != null && amount != 0D && existing.getAmount() == amount
                && existing.getOperation() == AttributeModifier.Operation.MULTIPLY_TOTAL) return;
        instance.removeModifier(id);
        if (amount != 0D) instance.addTransientModifier(new AttributeModifier(id,
                "Chimera title", amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }
    public float outgoingDamageMultiplier() {
        float multiplier = isSecondPhase() ? 1.20F : 1F;
        return switch (getTitle()) {
            case PRIDE -> multiplier * 1.30F;
            case HUMILITY -> multiplier * 0.50F;
            default -> multiplier;
        };
    }

    private float incomingDamageMultiplier(DamageSource source) {
        float multiplier = 1F;
        if (getTitle() == ChimeraTitle.CHARITY) multiplier *= 0.70F;
        if (getTitle() == ChimeraTitle.PATIENCE) multiplier *= 0.60F;
        return multiplier * incomingMagicDamageMultiplier(source);
    }

    public float spellPsychicFraction(DamageSource source) {
        if (source == incomingSpellSource) return incomingPsychicFraction;
        return source instanceof MagicDamageTypes.SpellSource spell ? spell.psychicFraction() : 0F;
    }

    private float incomingMagicDamageMultiplier(DamageSource source) {
        if (source.is(MagicDamageTypes.MAGIC_RESISTIBLE) || source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)) {
            float psychicFraction = source instanceof MagicDamageTypes.SpellSource spell ? spell.psychicFraction() : 0F;
            float magicMultiplier = switch (getTitle()) {
                case PRIDE -> 1.15F;
                case TEMPERANCE -> 0.50F;
                default -> 1F;
            };
            return (1F - psychicFraction) * magicMultiplier + psychicFraction;
        }
        return 1F;
    }

    @Override protected void actuallyHurt(DamageSource source, float amount) {
        if (dragonShieldTicks <= 0 || dragonShieldCapacity <= 0F) {
            super.actuallyHurt(source, amount);
            return;
        }
        if (isInvulnerableTo(source)) return;
        float hurtAmount = ForgeHooks.onLivingHurt(this, source, amount);
        if (hurtAmount <= 0F) return;
        float mitigated = getDamageAfterMagicAbsorb(source, getDamageAfterArmorAbsorb(source, hurtAmount));
        float healthDamage = Math.max(mitigated - getAbsorptionAmount(), 0F);
        float absorbed = mitigated - healthDamage;
        setAbsorptionAmount(getAbsorptionAmount() - absorbed);
        if (absorbed > 0F && absorbed < 3.4028235E37F && source.getEntity() instanceof ServerPlayer player) {
            player.awardStat(Stats.DAMAGE_DEALT_ABSORBED, Math.round(absorbed * 10F));
        }
        healthDamage = ForgeHooks.onLivingDamage(this, source, healthDamage);
        if (healthDamage > 0F && dragonShieldTicks > 0 && dragonShieldCapacity > 0F) {
            float shieldAbsorbed = Math.min(dragonShieldCapacity, healthDamage);
            dragonShieldCapacity -= shieldAbsorbed;
            healthDamage -= shieldAbsorbed;
            if (dragonShieldCapacity <= 0F) dragonShieldTicks = 0;
        }
        if (healthDamage != 0F) {
            getCombatTracker().recordDamage(source, healthDamage);
            float previousHealth = getHealth();
            setHealth(getHealth() - healthDamage);
            settleGreedHealthDamage(this, source, previousHealth);
            setAbsorptionAmount(getAbsorptionAmount() - healthDamage);
            gameEvent(GameEvent.ENTITY_DAMAGE);
        }
    }

    void applyWolfEmpowerment() {
        if (level().isClientSide || !isAlive()) return;
        wolfEmpowermentTicks = 100;
        updateWolfSpeedModifiers();
    }

    void applyDragonShield() {
        if (level().isClientSide || !isAlive()) return;
        dragonShieldTicks = 80;
        dragonShieldCapacity = 100F;
    }

    void applyBirdChant() {
        if (level().isClientSide || !isAlive()) return;
        birdChantTicks = 100;
        heal(50F);
    }

    float meleeDamageMultiplier() { return wolfEmpowermentTicks > 0 ? 1.20F : 1F; }
    float magicDamageMultiplier() { return birdChantTicks > 0 ? 1.15F : 1F; }

    private void tickSupportEffects() {
        if (wolfEmpowermentTicks > 0 && --wolfEmpowermentTicks == 0) updateWolfSpeedModifiers();
        if (dragonShieldTicks > 0 && --dragonShieldTicks == 0) dragonShieldCapacity = 0F;
        if (birdChantTicks > 0) birdChantTicks--;
    }

    private void tickTitleEffects() {
        if (getTitle() != ChimeraTitle.KINDNESS) {
            kindnessRegenTicks = 0;
            return;
        }
        if (++kindnessRegenTicks >= 20) {
            kindnessRegenTicks = 0;
            heal(getMaxHealth() * 0.01F);
        }
    }

    private void updateWolfSpeedModifiers() {
        for (Attribute attribute : new Attribute[]{Attributes.MOVEMENT_SPEED, Attributes.FLYING_SPEED}) {
            var instance = getAttribute(attribute);
            if (instance == null) continue;
            instance.removeModifier(WOLF_SPEED_MODIFIER);
            if (wolfEmpowermentTicks > 0) instance.addTransientModifier(new AttributeModifier(WOLF_SPEED_MODIFIER,
                    "Chimera wolf empowerment", 0.20D, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    public void setShieldTicks(int ticks) {
        if (ticks <= 0 || level().isClientSide || !isAlive()) return;
        applyDragonShield();
        dragonShieldTicks = Math.min(ticks, 80);
    }
    public void setMagicWardTicks(int ticks) { magicWardTicks = Math.max(magicWardTicks, ticks); }
    public boolean isMagicWardActive() { return magicWardTicks > 0; }
    public void setStationaryTicks(int ticks) { stationaryTicks = Math.max(stationaryTicks, ticks); }

    public boolean isSecondPhase() { return entityData.get(SECOND_PHASE); }
    public boolean isFlyingMode() { return entityData.get(FLYING); }
    public void setFlyingMode(boolean flying) { entityData.set(FLYING, flying); }
    public BodyAction getBodyAction() {
        int value = entityData.get(BODY_ACTION);
        return value >= 0 && value < BodyAction.values().length ? BodyAction.values()[value] : BodyAction.NONE;
    }
    boolean beginTakeoff() { return bodyController.takeoff(); }
    boolean beginLanding(boolean center) { return bodyController.land(center); }
    boolean beginTailAttack(Player target) { return bodyController.tail(target); }
    boolean beginObstacleDive(Player target) { return bodyController.dive(target); }
    void tickBodyActions() {
        if (!level().isClientSide) {
            if (arenaCenter == null) arenaCenter = position();
            bodyController.tick();
        }
    }
    public boolean isHeadDisabled(int head) { return (entityData.get(DISABLED_HEADS) & (1 << head)) != 0; }
    public boolean isHeadDisabled(ChimeraHead head) { return isHeadDisabled(head.index()); }
    public ChimeraSkillDirector.Action getHeadAction(ChimeraHead head) {
        return ChimeraSkillDirector.Action.fromId((entityData.get(HEAD_ACTIONS) >> (head.index() * 4)) & 15);
    }
    public void setHeadAction(ChimeraHead head, ChimeraSkillDirector.Action action) {
        int shift = head.index() * 4;
        entityData.set(HEAD_ACTIONS, (entityData.get(HEAD_ACTIONS) & ~(15 << shift)) | (action.ordinal() << shift));
    }
    public void setHeadDisabled(ChimeraHead head, boolean disabled) { setHeadDisabled(head.index(), disabled); }
    public void setHeadDisabled(int head, boolean disabled) { byte value = entityData.get(DISABLED_HEADS); entityData.set(DISABLED_HEADS, disabled ? (byte)(value | (1 << head)) : (byte)(value & ~(1 << head))); }
    public Vec3 headPosition(ChimeraHead head) {
        Vec3 local = switch (head) {
            case WOLF -> new Vec3(-0.85D, 2.65D, 0.7D);
            case DRAGON -> new Vec3(0D, 2.9D, 0.95D);
            case BIRD -> new Vec3(0.85D, 2.65D, 0.7D);
        };
        return position().add(local.yRot(-getYRot() * ((float)Math.PI / 180F)));
    }

    public boolean hurtHead(ChimeraHead head, DamageSource source, float amount) {
        float routed = isHeadDisabled(head) ? amount * 2F : amount;
        return hurt(source, routed);
    }

    public Vec3 arenaCenter() { return arenaCenter == null ? position() : arenaCenter; }

    @Override public void die(DamageSource source) {
        if (level() instanceof ServerLevel serverLevel) {
            for (UUID id : headHitboxes.values()) { Entity entity = serverLevel.getEntity(id); if (entity != null) entity.discard(); }
        }
        headHitboxes.clear();
        super.die(source);
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("ChimeraTitle", getTitle().ordinal());
        tag.putBoolean("ChimeraSecondPhase", isSecondPhase());
        tag.putBoolean("ChimeraFlying", isFlyingMode());
        tag.putLong("ChimeraNextTitleTick", nextTitleTick);
        tag.putInt("ChimeraWolfEmpowermentTicks", wolfEmpowermentTicks);
        tag.putInt("ChimeraDragonShieldTicks", dragonShieldTicks);
        tag.putFloat("ChimeraDragonShieldCapacity", dragonShieldCapacity);
        tag.putInt("ChimeraBirdChantTicks", birdChantTicks);
        tag.putInt("ChimeraKindnessRegenTicks", kindnessRegenTicks);
        Vec3 center = arenaCenter();
        tag.putDouble("ChimeraCenterX", center.x);
        tag.putDouble("ChimeraCenterY", center.y);
        tag.putDouble("ChimeraCenterZ", center.z);
        ListTag savedParticipants = new ListTag();
        for (UUID participant : participants) savedParticipants.add(NbtUtils.createUUID(participant));
        tag.put("ChimeraParticipants", savedParticipants);
        skillDirector.save(tag);
        bodyController.save(tag);
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        int title = tag.getInt("ChimeraTitle");
        entityData.set(TITLE, title >= 0 && title < ChimeraTitle.values().length ? title : 0);
        super.readAdditionalSaveData(tag);
        entityData.set(SECOND_PHASE, tag.getBoolean("ChimeraSecondPhase"));
        entityData.set(FLYING, tag.getBoolean("ChimeraFlying"));
        nextTitleTick = tag.getLong("ChimeraNextTitleTick");
        wolfEmpowermentTicks = Math.max(0, Math.min(100, tag.getInt("ChimeraWolfEmpowermentTicks")));
        dragonShieldTicks = Math.max(0, Math.min(80, tag.getInt("ChimeraDragonShieldTicks")));
        float savedCapacity = tag.getFloat("ChimeraDragonShieldCapacity");
        dragonShieldCapacity = Float.isFinite(savedCapacity) && dragonShieldTicks > 0 ? Math.max(0F, Math.min(100F, savedCapacity)) : 0F;
        if (dragonShieldCapacity <= 0F) dragonShieldTicks = 0;
        birdChantTicks = Math.max(0, Math.min(100, tag.getInt("ChimeraBirdChantTicks")));
        int savedRegenTicks = tag.contains("ChimeraKindnessRegenTicks", Tag.TAG_INT) ? tag.getInt("ChimeraKindnessRegenTicks") : 0;
        kindnessRegenTicks = getTitle() == ChimeraTitle.KINDNESS && savedRegenTicks >= 0 && savedRegenTicks < 20 ? savedRegenTicks : 0;
        updateWolfSpeedModifiers();
        arenaCenter = null;
        refreshTitleAttributes();
        clearTitleImmuneEffects();
        if (tag.contains("ChimeraCenterX", Tag.TAG_ANY_NUMERIC) && tag.contains("ChimeraCenterY", Tag.TAG_ANY_NUMERIC)
                && tag.contains("ChimeraCenterZ", Tag.TAG_ANY_NUMERIC)) {
            Vec3 center = new Vec3(tag.getDouble("ChimeraCenterX"), tag.getDouble("ChimeraCenterY"), tag.getDouble("ChimeraCenterZ"));
            if (Double.isFinite(center.x) && Double.isFinite(center.y) && Double.isFinite(center.z)) arenaCenter = center;
        }
        participants.clear();
        groundedPositions.clear();
        ListTag savedParticipants = tag.getList("ChimeraParticipants", net.minecraft.nbt.Tag.TAG_INT_ARRAY);
        for (int index = 0; index < savedParticipants.size(); index++) {
            int[] encoded = savedParticipants.getIntArray(index);
            if (encoded.length == 4) participants.add(NbtUtils.loadUUID(savedParticipants.get(index)));
        }
        skillDirector.load(this, tag);
        bodyController.load(tag);
    }
    private final class BodyController {
        private int modeTicks;
        private int tailCooldown;
        private int landingCooldown;
        private int diveCooldown;
        private int remaining;
        private int travelTicks;
        private int revision;
        private int checkedBlocks;
        private int brokenBlocks;
        private boolean titleRequest;
        private boolean forcedTitle;
        private boolean ticking;
        private boolean patienceLanded;
        private int forceRetryTicks;
        private Vec3 destination;
        private Vec3 ascent;
        private Vec3 tailDirection;
        private Vec3 orbitPoint;
        private Vec3 diveStep;
        private Vec3 lastSafePosition;
        private UUID lockedTarget;
        private final Map<UUID, Integer> targetOrder = new HashMap<>();
        private final Map<UUID, Integer> obstructed = new HashMap<>();
        private final Set<BlockPos> checked = new HashSet<>();
        private final ArrayDeque<BlockPos> pendingBlocks = new ArrayDeque<>();
        private int order;

        private boolean available() {
            return level() instanceof ServerLevel && isAlive() && !isNoAi() && getBodyAction() == BodyAction.NONE;
        }

        private AABB bounds(Vec3 point) { return getBoundingBox().move(point.subtract(position())); }

        private boolean loaded(AABB box) {
            if (box.minY < level().getMinBuildHeight() || box.maxY >= level().getMaxBuildHeight()
                    || !level().getWorldBorder().isWithinBounds(box)) return false;
            for (int chunkX = net.minecraft.util.Mth.floor(box.minX) >> 4; chunkX <= net.minecraft.util.Mth.floor(box.maxX) >> 4; chunkX++) {
                for (int chunkZ = net.minecraft.util.Mth.floor(box.minZ) >> 4; chunkZ <= net.minecraft.util.Mth.floor(box.maxZ) >> 4; chunkZ++) {
                    if (!level().hasChunk(chunkX, chunkZ)) return false;
                }
            }
            return true;
        }

        private boolean clear(Vec3 point) {
            AABB box = bounds(point).deflate(0.001D);
            return loaded(box) && level().noCollision(ChimeraEntity.this, box) && !level().containsAnyLiquid(box);
        }

        private boolean supported(Vec3 point) {
            AABB box = bounds(point).deflate(0.001D);
            return clear(point) && level().getBlockCollisions(ChimeraEntity.this, box.move(0D, -0.06D, 0D)).iterator().hasNext();
        }

        private Vec3 ground(Vec3 origin) {
            int[] offsets = {0, -1, 1, -2, 2};
            for (int depth = 0; depth <= 16; depth++) {
                for (int offsetX : offsets) for (int offsetZ : offsets) {
                    BlockPos support = BlockPos.containing(origin).offset(offsetX, -depth, offsetZ);
                    if (!level().hasChunkAt(support) || level().isOutsideBuildHeight(support)) continue;
                    var shape = level().getBlockState(support).getCollisionShape(level(), support);
                    if (shape.isEmpty()) continue;
                    Vec3 point = new Vec3(origin.x + offsetX, support.getY() + shape.max(Direction.Axis.Y), origin.z + offsetZ);
                    if (point.distanceToSqr(arenaCenter()) <= ARENA_RADIUS * ARENA_RADIUS && supported(point)) return point;
                }
            }
            return null;
        }

        private Vec3 airPoint() {
            double angle = (modeTicks / 40D) + getId();
            for (int offset = 0; offset < 8; offset++) {
                double direction = angle + offset * Math.PI / 4D;
                Vec3 base = ground(arenaCenter().add(Math.cos(direction) * 8D, 6D, Math.sin(direction) * 8D));
                if (base == null) continue;
                for (int height : new int[]{8, 6, 10}) {
                    Vec3 point = base.add(0D, height, 0D);
                    if (clear(point)) return point;
                }
            }
            return null;
        }

        private List<Player> players() {
            List<Player> result = new ArrayList<>();
            for (Player player : level().getEntitiesOfClass(Player.class,
                    new AABB(arenaCenter(), arenaCenter()).inflate(ARENA_RADIUS), ChimeraEntity::eligible)) {
                if (participants.contains(player.getUUID()) && player.distanceToSqr(arenaCenter()) <= ARENA_RADIUS * ARENA_RADIUS) result.add(player);
            }
            if (getTarget() instanceof Player player && eligible(player) && player.level() == level()
                    && player.distanceToSqr(arenaCenter()) <= ARENA_RADIUS * ARENA_RADIUS && !result.contains(player)) result.add(player);
            java.util.Collections.shuffle(result, new java.util.Random(getRandom().nextLong()));
            result.sort(Comparator.comparingInt(player -> targetOrder.getOrDefault(player.getUUID(), 0)));
            return result;
        }

        private void start(BodyAction action, int windup, Vec3 point, LivingEntity target) {
            revision++;
            entityData.set(BODY_ACTION, action.ordinal());
            remaining = windup;
            travelTicks = 0;
            destination = point;
            lockedTarget = target == null ? null : target.getUUID();
            if (target != null) {
                if (order == Integer.MAX_VALUE) { order = 0; targetOrder.clear(); }
                targetOrder.put(target.getUUID(), ++order);
            }
            getNavigation().stop();
            setDeltaMovement(Vec3.ZERO);
            if (clear(position())) lastSafePosition = position();
        }

        private boolean takeoff() {
            if (!available()) return false;
            if (airPoint() == null) return false;
            Vec3 floor = ground(position());
            if (floor == null) return false;
            Vec3 point = null;
            for (int height : new int[]{8, 6, 10}) {
                Vec3 candidate = new Vec3(getX(), floor.y + height, getZ());
                AABB sweep = getBoundingBox().expandTowards(candidate.subtract(position())).deflate(0.001D);
                if (loaded(sweep) && clear(candidate) && level().noCollision(ChimeraEntity.this, sweep)
                        && !level().containsAnyLiquid(sweep)) { point = candidate; break; }
            }
            if (point == null) return false;
            start(BodyAction.TAKEOFF, 16, point, null);
            setFlyingMode(true);
            setNoGravity(true);
            return true;
        }

        private boolean land(boolean center) {
            if (!available()) return false;
            LivingEntity target = null;
            Vec3 point = center ? ground(arenaCenter().add(0D, 4D, 0D)) : null;
            if (!center) {
                for (Player player : players()) {
                    point = ground(player.position());
                    if (point != null) { target = player; break; }
                }
                if (target == null && players().isEmpty()) {
                    for (LivingEntity living : level().getEntitiesOfClass(LivingEntity.class,
                            new AABB(arenaCenter(), arenaCenter()).inflate(ARENA_RADIUS), living -> living != ChimeraEntity.this
                                    && !(living instanceof Player) && living.isAlive())) {
                        point = ground(living.position());
                        if (point != null) { target = living; break; }
                    }
                }
            }
            if (point == null && center) return false;
            if (point == null) point = ground(position());
            if (point == null) return false;
            start(BodyAction.LANDING, 20, point, target);
            setFlyingMode(true);
            setNoGravity(true);
            return true;
        }

        private boolean rear(Player player, Vec3 direction) {
            Vec3 offset = player.position().subtract(position()).multiply(1D, 0D, 1D);
            return eligible(player) && player.level() == level() && Math.abs(player.getY() - getY()) <= 4D
                    && offset.lengthSqr() > 0.0001D && offset.lengthSqr() <= 36D && direction.dot(offset.normalize()) >= 0.5D;
        }

        private boolean tail(Player target) {
            Vec3 direction = getLookAngle().multiply(-1D, 0D, -1D).normalize();
            if (!available() || isFlyingMode() || !supported(position()) || tailCooldown > 0
                    || target == null || !rear(target, direction) || !hasLineOfSight(target)) return false;
            tailDirection = direction;
            start(BodyAction.TAIL, 24, position(), target);
            return true;
        }

        private boolean dive(Player target) {
            if (!available() || diveCooldown > 0 || target == null || !eligible(target) || target.level() != level()
                    || target.distanceToSqr(arenaCenter()) > ARENA_RADIUS * ARENA_RADIUS) return false;
            Vec3 point = ground(target.position());
            if (point == null || !clear(point.add(0D, 10D, 0D))) return false;
            Vec3 rise = new Vec3(getX(), Math.max(getY(), point.y + 10D), getZ());
            if (!clear(rise)) return false;
            start(BodyAction.DIVE_ASCENT, 30, point, target);
            ascent = rise;
            checked.clear();
            pendingBlocks.clear();
            diveStep = null;
            checkedBlocks = 0;
            brokenBlocks = 0;
            setFlyingMode(true);
            setNoGravity(true);
            return true;
        }

        private void finish() {
            BodyAction previous = getBodyAction();
            if (previous == BodyAction.TAIL) tailCooldown = 100;
            if (previous == BodyAction.LANDING) landingCooldown = 100;
            if (previous == BodyAction.DIVE || previous == BodyAction.DIVE_ASCENT) diveCooldown = 200;
            revision++;
            entityData.set(BODY_ACTION, BodyAction.NONE.ordinal());
            lockedTarget = null;
            destination = null;
            pendingBlocks.clear();
            diveStep = null;
            if (previous != BodyAction.TAIL) modeTicks = 0;
            orbitPoint = null;
            setDeltaMovement(Vec3.ZERO);
            if (!clear(position())) {
                Vec3 fallback = lastSafePosition != null && clear(lastSafePosition) ? lastSafePosition : ground(position());
                if (fallback != null) setPos(fallback);
            }
            if (supported(position())) { setFlyingMode(false); setNoGravity(false); setOnGround(true); }
            else { setFlyingMode(true); setNoGravity(true); }
            if (getTitle() == ChimeraTitle.PATIENCE && !patienceLanded) { titleRequest = true; forceRetryTicks = 20; }
        }

        private void titleChanged() {
            boolean nextForced = getTitle() == ChimeraTitle.DILIGENCE || getTitle() == ChimeraTitle.SLOTH || getTitle() == ChimeraTitle.PATIENCE;
            if (nextForced || forcedTitle) {
                if (getBodyAction() != BodyAction.NONE) finish();
                modeTicks = 0;
                orbitPoint = null;
            }
            titleRequest = nextForced;
            forcedTitle = nextForced;
            patienceLanded = false;
            forceRetryTicks = 0;
        }

        private boolean visible(Vec3 origin, LivingEntity target) {
            return level().clip(new ClipContext(origin, target.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, ChimeraEntity.this)).getType() == HitResult.Type.MISS;
        }

        private void telegraph(ServerLevel server) {
            if (remaining % 5 != 0 || destination == null) return;
            BodyAction action = getBodyAction();
            Vec3 center = action == BodyAction.TAKEOFF ? position() : destination;
            double radius = action == BodyAction.TAKEOFF ? 5D : action == BodyAction.TAIL ? 6D : 3D;
            for (int index = 0; index < 12; index++) {
                double angle = action == BodyAction.TAIL ? Math.atan2(tailDirection.z, tailDirection.x) - Math.PI / 3D
                        + index * (2D * Math.PI / 3D) / 11D : index * Math.PI / 6D;
                Vec3 point = center.add(Math.cos(angle) * radius, 0.15D, Math.sin(angle) * radius);
                server.sendParticles(ParticleTypes.CLOUD, point.x, point.y, point.z, 1, 0D, 0D, 0D, 0D);
            }
        }

        private void fan() {
            for (LivingEntity living : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(5D),
                    living -> living != ChimeraEntity.this && living.isAlive())) {
                if (distanceToSqr(living) > 25D || !hasLineOfSight(living)) continue;
                Vec3 offset = living.position().subtract(position()).multiply(1D, 0D, 1D).normalize().scale(0.8D);
                boolean grounded = isSecondPhase() && living instanceof Player player
                        && (participants.contains(player.getUUID()) || getTarget() == player);
                living.push(offset.x, grounded ? 0D : 0.35D, offset.z);
                living.hurtMarked = true;
            }
        }

        private void impact(boolean tail) {
            int expectedRevision = revision;
            float factor = tail ? 0.8F : getBodyAction() == BodyAction.DIVE ? 1.4F : 1.2F;
            for (LivingEntity living : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(tail ? 6D : 3D),
                    living -> living != ChimeraEntity.this && living.isAlive())) {
                if (!isAlive() || isNoAi() || revision != expectedRevision) return;
                if (living instanceof Player player && !eligible(player)) continue;
                Vec3 offset = living.position().subtract(position()).multiply(1D, 0D, 1D);
                if (tail ? !(living instanceof Player player) || !rear(player, tailDirection)
                        : offset.lengthSqr() > 9D || Math.abs(living.getY() - getY()) > 3D) continue;
                if (!visible(position().add(0D, 1D, 0D), living)) continue;
                float before = living.getHealth() + living.getAbsorptionAmount();
                boolean hit = living.hurt(damageSources().mobAttack(ChimeraEntity.this),
                        (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * factor * outgoingDamageMultiplier() * meleeDamageMultiplier());
                if (!isAlive() || revision != expectedRevision) return;
                if (hit && living.getHealth() + living.getAbsorptionAmount() < before) {
                    if (getTitle() == ChimeraTitle.WRATH && living instanceof Player player) {
                        ChimeraPoolDrainService.drainCurrentPools(player, ChimeraPoolDrainService.WRATH_CURRENT_POOL_FRACTION);
                    }
                    if (tail) { Vec3 push = offset.normalize().scale(0.6D); living.push(push.x, 0D, push.z); living.hurtMarked = true; }
                }
            }
        }

        private boolean moveToward(Vec3 point, boolean breaking) {
            Vec3 origin = position();
            Level originalLevel = level();
            int expectedRevision = revision;
            if (++travelTicks > 200) { finish(); return false; }
            Vec3 delta = point.subtract(position());
            double speed = Math.min(0.6D, Math.max(0.1D, getAttributeValue(Attributes.FLYING_SPEED)));
            Vec3 step = position().add(delta.length() <= speed ? delta : delta.normalize().scale(speed));
            AABB sweep = getBoundingBox().expandTowards(step.subtract(position())).deflate(0.001D);
            if (!loaded(sweep)) { finish(); return false; }
            if (breaking) {
                if (!clearDiveStep(step, sweep)) return false;
            } else if (!level().noCollision(ChimeraEntity.this, sweep) || level().containsAnyLiquid(sweep)) {
                finish(); return false;
            }
            if (!isAlive() || isNoAi() || level() != originalLevel || revision != expectedRevision
                    || position().distanceToSqr(origin) > 0.0001D) {
                if (revision == expectedRevision) finish();
                return false;
            }
            setPos(step);
            lastSafePosition = step;
            setDeltaMovement(Vec3.ZERO);
            resetFallDistance();
            return step.distanceToSqr(point) < 0.0001D;
        }

        private boolean clearDiveStep(Vec3 step, AABB sweep) {
            int expectedRevision = revision;
            Vec3 origin = position();
            Level originalLevel = level();
            if (diveStep != null && diveStep.distanceToSqr(step) > 0.0001D) { finish(); return false; }
            if (diveStep == null && level().noCollision(ChimeraEntity.this, sweep) && !level().containsAnyLiquid(sweep)) return true;
            if (diveStep == null) {
                diveStep = step;
                BlockPos.betweenClosedStream(BlockPos.containing(sweep.minX, sweep.minY, sweep.minZ),
                        BlockPos.containing(sweep.maxX, sweep.maxY, sweep.maxZ)).map(BlockPos::immutable)
                        .filter(pos -> !checked.contains(pos)).forEach(pendingBlocks::addLast);
            }
            int inspected = 0;
            int destroyed = 0;
            while (!pendingBlocks.isEmpty() && inspected < 32 && destroyed < 8) {
                BlockPos pos = pendingBlocks.removeFirst();
                if (checkedBlocks >= 128 || !level().hasChunkAt(pos)) { finish(); return false; }
                checkedBlocks++;
                inspected++;
                checked.add(pos);
                BlockState state = level().getBlockState(pos);
                var shape = state.getCollisionShape(level(), pos, net.minecraft.world.phys.shapes.CollisionContext.of(ChimeraEntity.this));
                if (shape.isEmpty() || !shape.toAabbs().stream().anyMatch(box -> box.move(pos).intersects(sweep))) continue;
                if (brokenBlocks >= 64) { finish(); return false; }
                boolean removed = destroy(pos, state);
                if (revision != expectedRevision || !isAlive() || getBodyAction() != BodyAction.DIVE) return false;
                if (isNoAi() || level() != originalLevel || position().distanceToSqr(origin) > 0.0001D) { finish(); return false; }
                if (!removed) { finish(); return false; }
                brokenBlocks++;
                destroyed++;
                if (!isAlive() || getBodyAction() != BodyAction.DIVE) return false;
            }
            if (!pendingBlocks.isEmpty()) return false;
            diveStep = null;
            if (!clear(step) || !level().noCollision(ChimeraEntity.this, sweep)) { finish(); return false; }
            return true;
        }

        private boolean destroy(BlockPos pos, BlockState state) {
            ServerLevel server = (ServerLevel) level();
            int expectedRevision = revision;
            Vec3 origin = position();
            try {
                if (state.hasBlockEntity() || state.getDestroySpeed(server, pos) < 0F
                        || NetherPrisonArena.isProtected(server, pos)
                        || !state.canEntityDestroy(server, pos, ChimeraEntity.this)
                        || !ForgeEventFactory.getMobGriefingEvent(server, ChimeraEntity.this)
                        || !ForgeEventFactory.onEntityDestroyBlock(ChimeraEntity.this, pos, state)) return false;
                if (!isAlive() || isNoAi() || level() != server || position().distanceToSqr(origin) > 0.0001D
                        || revision != expectedRevision || !server.hasChunkAt(pos) || !server.getBlockState(pos).equals(state)
                        || server.getBlockState(pos).hasBlockEntity() || NetherPrisonArena.isProtected(server, pos)) return false;
                boolean removed = server.destroyBlock(pos, false, ChimeraEntity.this);
                if (removed) BrewingWorldRestoration.get(server).forget(pos);
                return removed;
            } catch (RuntimeException exception) {
                org.apache.logging.log4j.LogManager.getLogger().warn("Chimera dive stopped by block protection at {}", pos, exception);
                return false;
            }
        }

        private void tick() {
            if (ticking) return;
            ticking = true;
            try { advance(); } finally { ticking = false; }
        }

        private void advance() {
            if (!isAlive() || isNoAi()) { if (getBodyAction() != BodyAction.NONE) finish(); return; }
            if (tailCooldown > 0) tailCooldown--;
            if (landingCooldown > 0) landingCooldown--;
            if (diveCooldown > 0) diveCooldown--;
            if (forceRetryTicks > 0) forceRetryTicks--;
            if (getBodyAction() == BodyAction.NONE || getBodyAction() == BodyAction.TAIL) modeTicks = Math.min(100000, modeTicks + 1);
            List<Player> candidates = players();
            Set<UUID> active = new HashSet<>();
            for (Player player : candidates) {
                active.add(player.getUUID());
                if (hasLineOfSight(player)) obstructed.remove(player.getUUID());
                else obstructed.merge(player.getUUID(), 1, (previous, increment) -> Math.min(100000, previous + increment));
            }
            obstructed.keySet().retainAll(active);
            targetOrder.keySet().retainAll(active);
            if (titleRequest && getBodyAction() == BodyAction.NONE && forceRetryTicks == 0) {
                boolean accepted = switch (getTitle()) {
                    case DILIGENCE -> isFlyingMode() || takeoff();
                    case SLOTH -> !isFlyingMode() || land(false);
                    case PATIENCE -> patienceLanded || land(true);
                    default -> true;
                };
                titleRequest = !accepted;
                if (!accepted) forceRetryTicks = 20;
            }
            BodyAction action = getBodyAction();
            if (action != BodyAction.NONE) {
                getNavigation().stop();
                setDeltaMovement(Vec3.ZERO);
                if (lockedTarget != null && !(((ServerLevel) level()).getEntity(lockedTarget) instanceof LivingEntity target
                        && target.isAlive() && target.level() == level() && target.distanceToSqr(arenaCenter()) <= ARENA_RADIUS * ARENA_RADIUS
                        && (!(target instanceof Player player) || eligible(player)))) { finish(); return; }
                if (action == BodyAction.DIVE_ASCENT) {
                    if (moveToward(ascent, false)) {
                        Vec3 overhead = destination.add(0D, 10D, 0D);
                        if (ascent.distanceToSqr(overhead) < 0.0001D) {
                            entityData.set(BODY_ACTION, BodyAction.DIVE.ordinal());
                            travelTicks = 0;
                        } else { ascent = overhead; travelTicks = 0; }
                    }
                    return;
                }
                if (remaining > 0) {
                    telegraph((ServerLevel) level());
                    remaining--;
                    if (remaining > 0) return;
                    if (action == BodyAction.TAKEOFF) {
                        int token = revision;
                        fan();
                        if (!isAlive() || isNoAi() || revision != token) return;
                    }
                    if (action == BodyAction.TAIL) { int token = revision; impact(true); if (revision == token) finish(); return; }
                }
                if (moveToward(destination, action == BodyAction.DIVE)) {
                    if (action == BodyAction.TAKEOFF) { finish(); return; }
                    if (!supported(position())) { finish(); return; }
                    if (action == BodyAction.LANDING && getTitle() == ChimeraTitle.PATIENCE) patienceLanded = true;
                    int token = revision;
                    impact(false);
                    if (revision == token) finish();
                }
                return;
            }
            if (getTitle() == ChimeraTitle.PATIENCE) { getNavigation().stop(); setDeltaMovement(Vec3.ZERO); return; }
            if (getTitle() != ChimeraTitle.DILIGENCE && getTitle() != ChimeraTitle.SLOTH && diveCooldown == 0) {
                candidates.sort(Comparator.comparingInt((Player player) -> obstructed.getOrDefault(player.getUUID(), 0)).reversed()
                        .thenComparingInt(player -> targetOrder.getOrDefault(player.getUUID(), 0)));
                for (Player player : candidates) if (obstructed.getOrDefault(player.getUUID(), 0) >= 40 && dive(player)) return;
            }
            if (isFlyingMode()) {
                if (getTitle() != ChimeraTitle.DILIGENCE && (getTitle() == ChimeraTitle.SLOTH || modeTicks >= 160 && landingCooldown == 0)) {
                    if (land(false)) return;
                }
                if (orbitPoint == null || position().distanceToSqr(orbitPoint) < 0.1D || modeTicks % 40 == 0) orbitPoint = airPoint();
                if (orbitPoint != null) {
                    travelTicks = 0;
                    int token = revision;
                    moveToward(orbitPoint, false);
                    if (revision != token && isFlyingMode()) land(false);
                }
                else if (!land(false)) { setNoGravity(false); setFlyingMode(!supported(position())); }
            } else {
                if (getTitle() == ChimeraTitle.DILIGENCE || getTitle() != ChimeraTitle.SLOTH && modeTicks >= 240 && landingCooldown == 0 && !candidates.isEmpty()) {
                    if (takeoff()) return;
                }
                candidates.sort(Comparator.comparingInt((Player player) -> targetOrder.getOrDefault(player.getUUID(), 0)).thenComparingDouble(ChimeraEntity.this::distanceToSqr));
                for (Player player : candidates) if (tail(player)) return;
            }
        }

        private void save(CompoundTag tag) {
            tag.putInt("ChimeraBodyModeTicks", modeTicks);
            tag.putInt("ChimeraTailCooldown", getBodyAction() == BodyAction.TAIL ? 100 : tailCooldown);
            tag.putInt("ChimeraLandingCooldown", getBodyAction() == BodyAction.LANDING ? 100 : landingCooldown);
            tag.putInt("ChimeraDiveCooldown", getBodyAction() == BodyAction.DIVE || getBodyAction() == BodyAction.DIVE_ASCENT ? 200 : diveCooldown);
        }

        private void load(CompoundTag tag) {
            revision++;
            entityData.set(BODY_ACTION, BodyAction.NONE.ordinal());
            modeTicks = Math.max(0, Math.min(240, tag.getInt("ChimeraBodyModeTicks")));
            tailCooldown = Math.max(0, Math.min(100, tag.getInt("ChimeraTailCooldown")));
            landingCooldown = Math.max(0, Math.min(100, tag.getInt("ChimeraLandingCooldown")));
            diveCooldown = Math.max(0, Math.min(200, tag.getInt("ChimeraDiveCooldown")));
            destination = null;
            orbitPoint = null;
            lockedTarget = null;
            diveStep = null;
            lastSafePosition = null;
            pendingBlocks.clear();
            checked.clear();
            targetOrder.clear();
            obstructed.clear();
            titleRequest = true;
            patienceLanded = false;
            forceRetryTicks = 0;
            forcedTitle = getTitle() == ChimeraTitle.DILIGENCE || getTitle() == ChimeraTitle.SLOTH || getTitle() == ChimeraTitle.PATIENCE;
            setDeltaMovement(Vec3.ZERO);
            setNoGravity(isFlyingMode());
        }
    }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) { }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return animationCache; }
}
