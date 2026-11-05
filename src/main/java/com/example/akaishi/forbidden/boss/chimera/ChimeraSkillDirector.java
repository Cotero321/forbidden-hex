package com.example.akaishi.forbidden.boss.chimera;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import com.example.akaishi.forbidden.entity.AkaishiForbiddenEntities;

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.function.BooleanSupplier;

public final class ChimeraSkillDirector {
    private static final int HEAD_DISABLED_TICKS = 100;
    private static final int WINDUP_TICKS = 20;
    private static final int COOLDOWN_TICKS = 40;
    private static final int CHANNEL_TICKS = 40;
    private static final int PULSE_INTERVAL_TICKS = 10;
    private static final double BREATH_RANGE = 8D;
    private static final double BREATH_COS_HALF_ANGLE = Math.cos(Math.PI / 4D);
    private static final String BLEEDING_KEY = "forbidden_hex_chimera_bleeding";
    private final int[] cooldowns = new int[3];
    private final int[] supportCooldowns = new int[3];
    private final int[] disabledTicks = new int[3];
    private final int[] actionTicks = new int[3];
    private final int[] actionRevisions = new int[3];
    private final Action[] actions = {Action.NONE, Action.NONE, Action.NONE};
    private final UUID[] targets = new UUID[3];
    private boolean titleInitialized;
    private static final ThreadLocal<ShieldProbe> SHIELD_PROBE = new ThreadLocal<>();

    public enum Action {
        NONE(null, false), WOLF_BITE(ChimeraHead.WOLF, true), DRAGON_BEAM(ChimeraHead.DRAGON, false),
        BIRD_SONIC(ChimeraHead.BIRD, false), WOLF_ORB(ChimeraHead.WOLF, false), BIRD_ORB(ChimeraHead.BIRD, false),
        DRAGON_BITE(ChimeraHead.DRAGON, true), BIRD_PECK(ChimeraHead.BIRD, true), DRAGON_BREATH(ChimeraHead.DRAGON, false),
        WOLF_CLAW(ChimeraHead.WOLF, true), WOLF_EMPOWER(ChimeraHead.WOLF, false),
        DRAGON_SHIELD(ChimeraHead.DRAGON, false), BIRD_CHANT(ChimeraHead.BIRD, false);

        private final ChimeraHead head;
        private final boolean melee;

        Action(ChimeraHead head, boolean melee) { this.head = head; this.melee = melee; }
        public static Action fromId(int id) { return id >= 0 && id < values().length ? values()[id] : NONE; }
        boolean support() { return this == WOLF_EMPOWER || this == DRAGON_SHIELD || this == BIRD_CHANT; }
        int cooldown() { return this == BIRD_CHANT ? 400 : support() ? 300 : COOLDOWN_TICKS; }
    }

    private static final class ShieldProbe {
        private final LivingEntity victim;
        private final DamageSource source;
        private final List<BooleanSupplier> blockEvents = new ArrayList<>();

        private ShieldProbe(LivingEntity victim, DamageSource source) { this.victim = victim; this.source = source; }
    }

    private record HitOutcome(boolean hit, boolean blocked) { }

    static void applyBleeding(ChimeraEntity chimera, Player player) {
        CompoundTag state = player.getPersistentData().getCompound(BLEEDING_KEY);
        if (state.getInt("Remaining") <= 0 || state.getInt("NextPulse") <= 0) state.putInt("NextPulse", 5);
        state.putInt("Remaining", 20);
        state.putUUID("Owner", chimera.getUUID());
        player.getPersistentData().put(BLEEDING_KEY, state);
    }

    public static void tickBleeding(LivingEntity victim) {
        if (!(victim instanceof Player player) || !(player.level() instanceof ServerLevel level)) return;
        if (!eligible(player)) {
            player.getPersistentData().remove(BLEEDING_KEY);
            return;
        }
        CompoundTag state = player.getPersistentData().getCompound(BLEEDING_KEY);
        int remaining = Math.max(0, Math.min(20, state.getInt("Remaining")));
        if (remaining == 0) return;
        int nextPulse = Math.max(1, Math.min(5, state.getInt("NextPulse"))) - 1;
        state.putInt("Remaining", --remaining);
        state.putInt("NextPulse", nextPulse == 0 ? 5 : nextPulse);
        if (nextPulse == 0) {
            Entity owner = state.hasUUID("Owner") ? level.getEntity(state.getUUID("Owner")) : null;
            LivingEntity attacker = owner instanceof ChimeraEntity chimera ? chimera : null;
            player.hurt(new BleedSource(player, attacker), 2F);
        }
        if (remaining == 0 && state.getInt("Remaining") == 0 && player.getPersistentData().get(BLEEDING_KEY) == state) {
            player.getPersistentData().remove(BLEEDING_KEY);
        }
    }

    public static void captureBleedGate(LivingEntity victim, DamageSource source, float lastHurt) {
        if (source instanceof BleedSource bleed && bleed.victim == victim && !bleed.captured) {
            bleed.captured = true;
            bleed.previousDamage = lastHurt;
            bleed.previousInvulnerability = victim.invulnerableTime;
        }
    }

    public static Float restoreBleedGate(LivingEntity victim, DamageSource source) {
        if (!(source instanceof BleedSource bleed) || bleed.victim != victim || !bleed.captured || bleed.restored) return null;
        bleed.restored = true;
        victim.invulnerableTime = bleed.previousInvulnerability;
        return bleed.previousDamage;
    }

    private static final class BleedSource extends DamageSource {
        private final LivingEntity victim;
        private boolean captured;
        private boolean restored;
        private float previousDamage;
        private int previousInvulnerability;

        private BleedSource(Player victim, LivingEntity owner) {
            super(victim.damageSources().mobAttack(owner).typeHolder(), owner, owner);
            this.victim = victim;
        }

        @Override public boolean is(TagKey<DamageType> tag) {
            return tag == DamageTypeTags.BYPASSES_COOLDOWN || tag == DamageTypeTags.BYPASSES_SHIELD || super.is(tag);
        }

        @Override public boolean scalesWithDifficulty() { return false; }
    }

    public static void observeShieldBlock(LivingEntity victim, DamageSource source, BooleanSupplier fullyBlocked) {
        ShieldProbe probe = SHIELD_PROBE.get();
        if (probe != null && probe.victim == victim && probe.source == source) probe.blockEvents.add(fullyBlocked);
    }

    private static HitOutcome hitWithShieldProbe(LivingEntity victim, DamageSource source, float amount) {
        ShieldProbe previous = SHIELD_PROBE.get();
        ShieldProbe probe = new ShieldProbe(victim, source);
        SHIELD_PROBE.set(probe);
        try {
            boolean hit = victim.hurt(source, amount);
            return new HitOutcome(hit, !probe.blockEvents.isEmpty() && probe.blockEvents.stream().allMatch(BooleanSupplier::getAsBoolean));
        } finally {
            if (previous == null) SHIELD_PROBE.remove();
            else SHIELD_PROBE.set(previous);
        }
    }

    public void tick(ChimeraEntity chimera) {
        if (!(chimera.level() instanceof ServerLevel level) || !chimera.isAlive() || chimera.isNoAi()) return;
        if (!titleInitialized) onTitleChanged(chimera);
        for (ChimeraHead head : ChimeraHead.values()) {
            int index = head.index();
            if (disabledTicks[index] > 0) disabledTicks[index]--;
            chimera.setHeadDisabled(head, disabledTicks[index] > 0);
            if (cooldowns[index] > 0) cooldowns[index]--;
            if (supportCooldowns[index] > 0) supportCooldowns[index]--;
            if (disabledTicks[index] > 0) continue;
            if (actionTicks[index] > 0) {
                tickAction(chimera, level, head);
                continue;
            }
            LivingEntity target = chimera.getTarget();
            if (!(target instanceof Player player) || !eligible(player)) continue;
            Action action = switch (head) {
                case WOLF -> !chimera.isFlyingMode() && chimera.distanceToSqr(player) <= 16D && chimera.getRandom().nextBoolean()
                        ? Action.WOLF_BITE : Action.WOLF_ORB;
                case DRAGON -> !chimera.isFlyingMode() && chimera.distanceToSqr(player) <= 16D && chimera.getRandom().nextBoolean()
                        ? Action.DRAGON_BITE : chimera.headPosition(head).distanceToSqr(player.getBoundingBox().getCenter()) <= BREATH_RANGE * BREATH_RANGE
                        && chimera.getRandom().nextBoolean() ? Action.DRAGON_BREATH : Action.DRAGON_BEAM;
                case BIRD -> !chimera.isFlyingMode() && chimera.distanceToSqr(player) <= 16D && chimera.getRandom().nextBoolean()
                        ? Action.BIRD_PECK : chimera.distanceToSqr(player) <= 25D && chimera.getRandom().nextBoolean()
                        ? Action.BIRD_SONIC : Action.BIRD_ORB;
            };
            if (supportCooldowns[index] == 0 && chimera.getRandom().nextInt(4) == 0) {
                action = switch (head) {
                    case WOLF -> Action.WOLF_EMPOWER;
                    case DRAGON -> Action.DRAGON_SHIELD;
                    case BIRD -> Action.BIRD_CHANT;
                };
            } else if (head == ChimeraHead.WOLF && !chimera.isFlyingMode()
                    && chimera.distanceToSqr(player) <= 16D && chimera.getRandom().nextBoolean()) action = Action.WOLF_CLAW;
            startAction(chimera, head, action, player);
        }
    }

    boolean startAction(ChimeraEntity chimera, ChimeraHead head, Action action, Player target) {
        if (!(chimera.level() instanceof ServerLevel level) || !chimera.isAlive() || chimera.isNoAi()) return false;
        if (!titleInitialized) onTitleChanged(chimera);
        int index = head.index();
        if (action == Action.NONE || action.head != head || disabledTicks[index] > 0 || actionTicks[index] > 0
                || (action.support() ? supportCooldowns[index] > 0 : cooldowns[index] > 0)) return false;
        if (!eligible(target) || target.level() != chimera.level() || chimera.distanceToSqr(target) > 576D) return false;
        if (action == Action.DRAGON_BREATH) {
            Vec3 origin = chimera.headPosition(head);
            Vec3 destination = target.getBoundingBox().getCenter();
            if (origin.distanceToSqr(destination) > BREATH_RANGE * BREATH_RANGE
                    || level.clip(new ClipContext(origin, destination, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, chimera)).getType() != HitResult.Type.MISS) return false;
        } else if (!action.support() && !chimera.hasLineOfSight(target)) return false;
        if (action.melee && (chimera.isFlyingMode() || chimera.distanceToSqr(target) > 16D)) return false;
        if (action == Action.BIRD_SONIC && chimera.distanceToSqr(target) > 25D) return false;
        targets[index] = target.getUUID();
        actions[index] = action;
        actionRevisions[index]++;
        actionTicks[index] = action == Action.DRAGON_BEAM || action == Action.DRAGON_BREATH ? WINDUP_TICKS + CHANNEL_TICKS
                : action == Action.BIRD_PECK ? WINDUP_TICKS / 2 : action == Action.DRAGON_SHIELD ? 30
                : action == Action.BIRD_CHANT ? 40 : WINDUP_TICKS;
        chimera.setHeadAction(head, action);
        return true;
    }

    private void tickAction(ChimeraEntity chimera, ServerLevel level, ChimeraHead head) {
        int index = head.index();
        Action action = actions[index];
        int revision = actionRevisions[index];
        LivingEntity victim = targets[index] == null ? null : level.getEntity(targets[index]) instanceof LivingEntity living ? living : null;
        if (!(victim instanceof Player target) || !eligible(target) || action == Action.NONE
                || (action.melee && chimera.isFlyingMode()) || chimera.distanceToSqr(target) > 4096D) {
            finish(chimera, head);
            return;
        }
        int remaining = --actionTicks[index];
        if (action == Action.DRAGON_BEAM || action == Action.DRAGON_BREATH) {
            if (remaining >= CHANNEL_TICKS) {
                telegraph(chimera, level, head);
            } else if ((CHANNEL_TICKS - 1 - remaining) % PULSE_INTERVAL_TICKS == 0) {
                if (action == Action.DRAGON_BEAM) beam(chimera, level, target);
                else breath(chimera, level, target, revision);
            }
        } else if (remaining > 0) {
            telegraph(chimera, level, head);
        } else if (action == Action.WOLF_CLAW) {
            claw(chimera, level, target, revision);
        } else if (action.melee) {
            melee(chimera, head, target);
        } else if (action == Action.WOLF_EMPOWER) {
            chimera.applyWolfEmpowerment();
        } else if (action == Action.DRAGON_SHIELD) {
            chimera.applyDragonShield();
        } else if (action == Action.BIRD_CHANT) {
            chimera.applyBirdChant();
        } else if (action == Action.BIRD_SONIC) {
            sonic(chimera);
        } else if ((action == Action.WOLF_ORB || action == Action.BIRD_ORB)
                && chimera.hasLineOfSight(target) && chimera.distanceToSqr(target) <= 576D) {
            MagicOrb orb = MagicOrb.create(chimera, target, head);
            level.addFreshEntity(orb);
        }
        if (remaining == 0 && actionRevisions[index] == revision) finish(chimera, head);
    }

    private static void telegraph(ChimeraEntity chimera, ServerLevel level, ChimeraHead head) {
        if (chimera.tickCount % 4 != 0) return;
        Vec3 origin = chimera.headPosition(head);
        level.sendParticles(ParticleTypes.ENCHANT, origin.x, origin.y, origin.z, 3, 0.3D, 0.2D, 0.3D, 0.02D);
    }

    private void melee(ChimeraEntity chimera, ChimeraHead head, Player target) {
        if (chimera.isFlyingMode() || chimera.distanceToSqr(target) > 16D || !chimera.hasLineOfSight(target)) return;
        DamageSource source = chimera.damageSources().mobAttack(chimera);
        HitOutcome outcome = hitWithShieldProbe(target, source, baseDamage(chimera) * chimera.meleeDamageMultiplier());
        if (outcome.blocked()) {
            disable(chimera, head);
        } else if (outcome.hit()) {
            onMeleeHit(chimera, target);
            if (head == ChimeraHead.DRAGON) {
                target.knockback(0.75D, chimera.getX() - target.getX(), chimera.getZ() - target.getZ());
            }
        }
    }

    private void claw(ChimeraEntity chimera, ServerLevel level, Player target, int revision) {
        Vec3 aim = target.position().subtract(chimera.position()).multiply(1D, 0D, 1D);
        if (aim.lengthSqr() < 1.0E-12D) return;
        Vec3 direction = aim.normalize();
        int index = ChimeraHead.WOLF.index();
        for (Player player : level.getEntitiesOfClass(Player.class, chimera.getBoundingBox().inflate(4D), ChimeraSkillDirector::eligible)) {
            if (!chimera.isAlive() || chimera.isNoAi() || chimera.isFlyingMode() || !eligible(target)
                    || target.level() != level || actions[index] != Action.WOLF_CLAW
                    || actionRevisions[index] != revision || disabledTicks[index] > 0) return;
            Vec3 offset = player.position().subtract(chimera.position()).multiply(1D, 0D, 1D);
            if (chimera.distanceToSqr(player) > 16D || offset.lengthSqr() < 1.0E-12D
                    || direction.dot(offset.normalize()) < 0.5D || !chimera.hasLineOfSight(player)) continue;
            float healthBefore = player.getHealth();
            float absorptionBefore = player.getAbsorptionAmount();
            HitOutcome outcome = hitWithShieldProbe(player, chimera.damageSources().mobAttack(chimera),
                    baseDamage(chimera) * (2F / 3F) * chimera.meleeDamageMultiplier());
            if (outcome.hit() && !outcome.blocked()
                    && (player.getHealth() < healthBefore || player.getAbsorptionAmount() < absorptionBefore)) {
                applyBleeding(chimera, player);
                onMeleeHit(chimera, player);
            }
        }
    }

    private static void beam(ChimeraEntity chimera, ServerLevel level, Player target) {
        Vec3 origin = chimera.headPosition(ChimeraHead.DRAGON);
        Vec3 direction = target.getBoundingBox().getCenter().subtract(origin).normalize();
        Vec3 end = level.clip(new ClipContext(origin, origin.add(direction.scale(24D)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, chimera)).getLocation();
        AABB corridor = new AABB(origin, end).inflate(0.35D);
        for (Player player : level.getEntitiesOfClass(Player.class, corridor, ChimeraSkillDirector::eligible)) {
            if (!chimera.isAlive() || chimera.level() != level) return;
            if (player.getBoundingBox().inflate(0.35D).clip(origin, end).isPresent()) {
                player.hurt(chimera.damageSources().indirectMagic(chimera, chimera), magicDamage(chimera));
            }
        }
        if (!chimera.isAlive() || chimera.level() != level) return;
        double length = origin.distanceTo(end);
        for (double step = 0D; step < length; step += 0.5D) {
            Vec3 point = origin.add(direction.scale(step));
            level.sendParticles(ParticleTypes.DRAGON_BREATH, point.x, point.y, point.z, 1, 0D, 0D, 0D, 0D);
        }
    }

    private void breath(ChimeraEntity chimera, ServerLevel level, Player target, int revision) {
        Vec3 origin = chimera.headPosition(ChimeraHead.DRAGON);
        Vec3 aim = target.getBoundingBox().getCenter().subtract(origin);
        if (!Double.isFinite(aim.lengthSqr()) || aim.lengthSqr() < 1.0E-12D) return;
        Vec3 direction = aim.normalize();
        for (Player player : level.getEntitiesOfClass(Player.class, new AABB(origin, origin).inflate(BREATH_RANGE), ChimeraSkillDirector::eligible)) {
            if (!breathActive(chimera, target, revision)) return;
            Vec3 destination = player.getBoundingBox().getCenter();
            Vec3 offset = destination.subtract(origin);
            double distanceSquared = offset.lengthSqr();
            if (!Double.isFinite(distanceSquared) || distanceSquared < 1.0E-12D || distanceSquared > BREATH_RANGE * BREATH_RANGE
                    || direction.dot(offset.normalize()) < BREATH_COS_HALF_ANGLE) continue;
            if (level.clip(new ClipContext(origin, destination, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, chimera)).getType() != HitResult.Type.MISS) continue;
            player.hurt(chimera.damageSources().indirectMagic(chimera, chimera), magicDamage(chimera));
        }
        if (!breathActive(chimera, target, revision)) return;
        for (int ray = -2; ray <= 2; ray++) {
            Vec3 spread = direction.yRot(ray * ((float) Math.PI / 8F));
            Vec3 end = level.clip(new ClipContext(origin, origin.add(spread.scale(BREATH_RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, chimera)).getLocation();
            for (double distance = 0D; distance < origin.distanceTo(end); distance += 0.75D) {
                Vec3 point = origin.add(spread.scale(distance));
                level.sendParticles(ParticleTypes.DRAGON_BREATH, point.x, point.y, point.z, 1, 0D, 0D, 0D, 0D);
            }
        }
    }

    private boolean breathActive(ChimeraEntity chimera, Player target, int revision) {
        int index = ChimeraHead.DRAGON.index();
        return chimera.isAlive() && !chimera.isNoAi() && eligible(target) && target.level() == chimera.level()
                && actions[index] == Action.DRAGON_BREATH && actionRevisions[index] == revision && disabledTicks[index] == 0;
    }

    private static void sonic(ChimeraEntity chimera) {
        for (Player player : chimera.level().getEntitiesOfClass(Player.class, chimera.getBoundingBox().inflate(5D), ChimeraSkillDirector::eligible)) {
            if (!chimera.isAlive()) return;
            if (chimera.distanceToSqr(player) > 25D || !chimera.hasLineOfSight(player)) continue;
            boolean hit = player.hurt(chimera.damageSources().mobAttack(chimera), magicDamage(chimera));
            if (hit) player.knockback(0.75D, chimera.getX() - player.getX(), chimera.getZ() - player.getZ());
        }
    }

    public void onTitleChanged(ChimeraEntity chimera) {
        titleInitialized = true;
        for (ChimeraHead head : ChimeraHead.values()) {
            if (actions[head.index()].support() || actions[head.index()] == Action.WOLF_CLAW) finish(chimera, head);
            disabledTicks[head.index()] = 0;
            actionTicks[head.index()] = 0;
            actionRevisions[head.index()]++;
            actions[head.index()] = Action.NONE;
            chimera.setHeadAction(head, Action.NONE);
            targets[head.index()] = null;
            chimera.setHeadDisabled(head, false);
        }
        if (chimera.getTitle() == ChimeraTitle.HUMILITY) {
            for (MobEffectInstance effect : List.copyOf(chimera.getActiveEffects())) {
                if (!effect.getEffect().isBeneficial()) chimera.removeEffect(effect.getEffect());
            }
        } else if (chimera.getTitle() == ChimeraTitle.ENVY && chimera.getTarget() instanceof Player player && eligible(player)) {
            for (MobEffectInstance effect : List.copyOf(player.getActiveEffects())) {
                if (!effect.getEffect().isBeneficial()) continue;
                if (player.removeEffect(effect.getEffect())) chimera.addEffect(new MobEffectInstance(effect));
                break;
            }
        } else if (chimera.getTitle() == ChimeraTitle.LUST) {
            for (Player player : chimera.level().getEntitiesOfClass(Player.class, chimera.getBoundingBox().inflate(30D), ChimeraSkillDirector::eligible)) {
                if (chimera.distanceToSqr(player) > 900D) continue;
                player.addEffect(new MobEffectInstance(chimera.getRandom().nextBoolean() ? MobEffects.CONFUSION : MobEffects.MOVEMENT_SLOWDOWN, 200));
            }
        }
    }

    private static float baseDamage(ChimeraEntity chimera) {
        return (float) chimera.getAttributeValue(Attributes.ATTACK_DAMAGE) * chimera.outgoingDamageMultiplier();
    }

    private static float magicDamage(ChimeraEntity chimera) {
        return baseDamage(chimera) * chimera.magicDamageMultiplier();
    }

    private static void onMeleeHit(ChimeraEntity chimera, Player player) {
        if (chimera.getTitle() == ChimeraTitle.WRATH) {
            ChimeraPoolDrainService.drainCurrentPools(player, ChimeraPoolDrainService.WRATH_CURRENT_POOL_FRACTION);
        }
    }

    private static boolean eligible(Player player) {
        return player.isAlive() && !player.isSpectator() && !player.isCreative();
    }

    private void finish(ChimeraEntity chimera, ChimeraHead head) {
        int index = head.index();
        Action completed = actions[index];
        actionRevisions[index]++;
        actionTicks[index] = 0;
        actions[index] = Action.NONE;
        chimera.setHeadAction(head, Action.NONE);
        targets[index] = null;
        int resolvedCooldown = chimera.getTitle() == ChimeraTitle.DILIGENCE ? completed.cooldown() / 2 : completed.cooldown();
        if (chimera.getTitle() == ChimeraTitle.WRATH && completed != Action.NONE && !completed.support()) resolvedCooldown /= 2;
        if (completed.support()) supportCooldowns[index] = resolvedCooldown;
        else cooldowns[index] = resolvedCooldown;
    }

    public void disable(ChimeraEntity chimera, ChimeraHead head) {
        int index = head.index();
        if (actions[index].support() || actions[index] == Action.WOLF_CLAW) finish(chimera, head);
        actionRevisions[index]++;
        disabledTicks[index] = HEAD_DISABLED_TICKS;
        actionTicks[index] = 0;
        actions[index] = Action.NONE;
        chimera.setHeadAction(head, Action.NONE);
        targets[index] = null;
        chimera.setHeadDisabled(head, true);
    }

    public void save(CompoundTag tag) {
        tag.putIntArray("HeadCooldowns", cooldowns);
        tag.putIntArray("SupportCooldowns", supportCooldowns);
        tag.putIntArray("DisabledHeadTicks", disabledTicks);
        tag.putIntArray("PendingHeadActions", java.util.Arrays.stream(actions).mapToInt(Action::ordinal).toArray());
    }

    public void load(ChimeraEntity chimera, CompoundTag tag) {
        int[] savedCooldowns = tag.getIntArray("HeadCooldowns");
        int[] savedSupport = tag.getIntArray("SupportCooldowns");
        int[] savedDisabled = tag.getIntArray("DisabledHeadTicks");
        int[] savedPending = tag.getIntArray("PendingHeadActions");
        for (ChimeraHead head : ChimeraHead.values()) {
            int index = head.index();
            cooldowns[index] = index < savedCooldowns.length ? Math.max(0, Math.min(400, savedCooldowns[index])) : 0;
            supportCooldowns[index] = index < savedSupport.length ? Math.max(0, Math.min(400, savedSupport[index])) : 0;
            Action pending = index < savedPending.length ? Action.fromId(savedPending[index]) : Action.NONE;
            if (pending.head == head && (pending.support() || pending == Action.WOLF_CLAW)) {
                int interruptedCooldown = pending.cooldown();
                if (chimera.getTitle() == ChimeraTitle.DILIGENCE) interruptedCooldown /= 2;
                if (chimera.getTitle() == ChimeraTitle.WRATH && !pending.support()) interruptedCooldown /= 2;
                if (pending.support()) supportCooldowns[index] = Math.max(supportCooldowns[index], interruptedCooldown);
                else cooldowns[index] = Math.max(cooldowns[index], interruptedCooldown);
            }
            disabledTicks[index] = index < savedDisabled.length ? Math.max(0, Math.min(HEAD_DISABLED_TICKS, savedDisabled[index])) : 0;
            actionTicks[index] = 0;
            actionRevisions[index]++;
            actions[index] = Action.NONE;
            targets[index] = null;
            chimera.setHeadAction(head, Action.NONE);
            chimera.setHeadDisabled(head, disabledTicks[index] > 0);
        }
        titleInitialized = true;
    }

    public static final class MagicOrb extends ThrowableItemProjectile {
        static final int LIFETIME_TICKS = 200;
        private static final double SPEED = 0.6D;
        private static final double TURN_STRENGTH = 0.08D;
        private static final double BLAST_RADIUS = 3D;
        private static final EntityDataAccessor<Boolean> EXPLOSIVE = SynchedEntityData.defineId(MagicOrb.class, EntityDataSerializers.BOOLEAN);
        private static final EntityDataAccessor<Boolean> REFLECTED = SynchedEntityData.defineId(MagicOrb.class, EntityDataSerializers.BOOLEAN);
        private UUID chimeraId;
        private UUID targetId;
        private int age;
        private float damage = 15F;

        public MagicOrb(EntityType<? extends MagicOrb> type, Level level) { super(type, level); setNoGravity(true); }

        static MagicOrb create(ChimeraEntity chimera, Player target, ChimeraHead head) {
            MagicOrb orb = new MagicOrb(AkaishiForbiddenEntities.CHIMERA_ORB.get(), chimera.level());
            orb.setOwner(chimera);
            orb.chimeraId = chimera.getUUID();
            orb.targetId = target.getUUID();
            orb.damage = magicDamage(chimera);
            orb.entityData.set(EXPLOSIVE, head == ChimeraHead.BIRD);
            orb.setPos(chimera.headPosition(head));
            orb.setDeltaMovement(target.getBoundingBox().getCenter().subtract(orb.position()).normalize().scale(SPEED));
            return orb;
        }

        @Override protected void defineSynchedData() {
            super.defineSynchedData();
            entityData.define(EXPLOSIVE, false);
            entityData.define(REFLECTED, false);
        }

        @Override protected Item getDefaultItem() { return isExplosive() ? Items.FIRE_CHARGE : Items.ENDER_PEARL; }
        public boolean isExplosive() { return entityData.get(EXPLOSIVE); }
        public boolean isReflected() { return entityData.get(REFLECTED); }
        public boolean isOwnedAttackOf(ChimeraEntity chimera) {
            return !isReflected() && getOwner() == chimera && chimera.getUUID().equals(chimeraId);
        }
        @Override public boolean isPickable() { return !isRemoved() && !isExplosive() && !isReflected(); }
        @Override public float getPickRadius() { return 0.25F; }

        @Override public void tick() {
            if (isRemoved()) return;
            if (level() instanceof ServerLevel server) {
                Entity original = chimeraId == null ? null : server.getEntity(chimeraId);
                if (!(original instanceof ChimeraEntity chimera) || !chimera.isAlive() || ++age > LIFETIME_TICKS) {
                    discard();
                    return;
                }
                if (isReflected()) {
                    if (!(getOwner() instanceof Player player) || !eligible(player)) { discard(); return; }
                } else {
                    Entity target = targetId == null ? null : server.getEntity(targetId);
                    if (!(target instanceof Player player) || !eligible(player) || chimera.distanceToSqr(player) > 4096D) { discard(); return; }
                    Vec3 current = getDeltaMovement().normalize();
                    Vec3 desired = player.getBoundingBox().getCenter().subtract(position()).normalize();
                    if (!isExplosive()) current = current.add(desired.subtract(current).scale(TURN_STRENGTH)).normalize();
                    setDeltaMovement(current.scale(SPEED));
                }
            }
            Vec3 movement = getDeltaMovement();
            if (!Double.isFinite(movement.lengthSqr()) || movement.lengthSqr() < 1.0E-12D) {
                if (!level().isClientSide) discard();
                return;
            }
            setDeltaMovement(movement.normalize().scale(SPEED));
            super.tick();
            if (level().isClientSide && !isRemoved()) level().addParticle(isExplosive() ? ParticleTypes.FLAME : ParticleTypes.WITCH,
                    getX(), getY(), getZ(), 0D, 0D, 0D);
        }

        @Override protected boolean canHitEntity(Entity candidate) {
            if (!super.canHitEntity(candidate)) return false;
            if (candidate instanceof ChimeraEntity chimera) return isReflected() && chimera.getUUID().equals(chimeraId);
            if (candidate instanceof ChimeraHeadHitbox head) return isReflected() && chimeraId != null && chimeraId.equals(head.ownerId());
            return !isReflected() && candidate instanceof Player player && eligible(player);
        }

        @Override public boolean hurt(DamageSource source, float amount) {
            if (level().isClientSide || isRemoved() || isExplosive() || isReflected() || !Float.isFinite(amount) || amount <= 0F) return false;
            if (!source.is(DamageTypes.PLAYER_ATTACK) || !(source.getDirectEntity() instanceof Player player)
                    || source.getEntity() != player || !eligible(player) || player.distanceToSqr(this) > 36D || !player.hasLineOfSight(this)) return false;
            return reflect(player, false);
        }

        private boolean reflect(Player player, boolean shield) {
            if (!(level() instanceof ServerLevel server) || isReflected() || isRemoved()) return false;
            Entity original = chimeraId == null ? null : server.getEntity(chimeraId);
            if (!(original instanceof ChimeraEntity chimera) || !chimera.isAlive()) return false;
            Vec3 direction = shield ? chimera.getBoundingBox().getCenter().subtract(position()).normalize() : player.getLookAngle();
            if (direction.lengthSqr() < 0.001D) return false;
            setOwner(player);
            entityData.set(REFLECTED, true);
            targetId = null;
            setDeltaMovement(direction.scale(SPEED));
            hasImpulse = true;
            hurtMarked = true;
            return true;
        }

        @Override protected void onHitEntity(EntityHitResult hit) {
            if (!(level() instanceof ServerLevel) || isRemoved()) return;
            Entity victim = hit.getEntity();
            if (victim instanceof Player player && !isReflected() && eligible(player)) {
                HitOutcome outcome = hitWithShieldProbe(player, damageSources().mobProjectile(this, getOwner() instanceof LivingEntity living ? living : null), damage);
                if (isExplosive() && outcome.blocked() && reflect(player, true)) return;
                burst(hit.getLocation(), player);
            } else if (isReflected() && canHitEntity(victim)) {
                victim.hurt(damageSources().mobProjectile(this, getOwner() instanceof LivingEntity living ? living : null), damage);
                burst(hit.getLocation(), null);
            }
            discard();
        }

        @Override protected void onHitBlock(BlockHitResult hit) {
            if (!(level() instanceof ServerLevel) || isRemoved()) return;
            burst(hit.getLocation().subtract(getDeltaMovement().normalize().scale(0.05D)), null);
            discard();
        }

        private void burst(Vec3 origin, Player directHit) {
            if (!(level() instanceof ServerLevel server)) return;
            if (!(getOwner() instanceof LivingEntity owner) || !owner.isAlive()) return;
            server.sendParticles(isExplosive() ? ParticleTypes.EXPLOSION : ParticleTypes.INSTANT_EFFECT,
                    origin.x, origin.y, origin.z, 1, 0D, 0D, 0D, 0D);
            if (!isExplosive() || isReflected()) return;
            for (Player player : server.getEntitiesOfClass(Player.class, new AABB(origin, origin).inflate(BLAST_RADIUS), ChimeraSkillDirector::eligible)) {
                if (!owner.isAlive()) return;
                if (player == directHit || player.getBoundingBox().getCenter().distanceToSqr(origin) > BLAST_RADIUS * BLAST_RADIUS) continue;
                Vec3 destination = player.getBoundingBox().getCenter();
                if (server.clip(new ClipContext(origin, destination, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS) continue;
                player.hurt(damageSources().mobProjectile(this, getOwner() instanceof LivingEntity living ? living : null), damage);
            }
        }

        @Override public void addAdditionalSaveData(CompoundTag tag) {
            super.addAdditionalSaveData(tag);
            if (chimeraId != null) tag.putUUID("Chimera", chimeraId);
            if (targetId != null) tag.putUUID("Target", targetId);
            tag.putInt("Age", age);
            tag.putFloat("Damage", damage);
            tag.putBoolean("Explosive", isExplosive());
            tag.putBoolean("Reflected", isReflected());
        }

        @Override public void readAdditionalSaveData(CompoundTag tag) {
            super.readAdditionalSaveData(tag);
            chimeraId = tag.hasUUID("Chimera") ? tag.getUUID("Chimera") : null;
            targetId = tag.hasUUID("Target") ? tag.getUUID("Target") : null;
            age = Math.max(0, Math.min(LIFETIME_TICKS, tag.getInt("Age")));
            float savedDamage = tag.getFloat("Damage");
            damage = Float.isFinite(savedDamage) && savedDamage > 0F ? Math.min(1000F, savedDamage) : 15F;
            entityData.set(EXPLOSIVE, tag.getBoolean("Explosive"));
            entityData.set(REFLECTED, tag.getBoolean("Reflected"));
            setNoGravity(true);
        }
    }
}
