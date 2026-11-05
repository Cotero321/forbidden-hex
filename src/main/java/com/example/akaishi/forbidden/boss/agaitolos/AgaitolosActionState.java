package com.example.akaishi.forbidden.boss.agaitolos;

import com.example.akaishi.forbidden.boss.agaitolos.skill.AgaitolosBlinkSkill;
import com.example.akaishi.forbidden.boss.agaitolos.skill.AgaitolosKickSkill;
import com.example.akaishi.forbidden.boss.agaitolos.skill.AgaitolosMeleeSkill;
import com.example.akaishi.forbidden.boss.agaitolos.skill.AgaitolosSkullSkill;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

/** 服务端起手、唯一命中帧与收势锁；不持久化跨读档的待结算攻击。 */
final class AgaitolosActionState {
    enum Strike {
        SLASH("boss_slash", 12, 20), CAST("boss_summon_throw", 20, 30),
        KICK("boss_rapid_kick", 10, 26), BLINK("boss_blink_strike", 16, 34),
        DIVE("boss_dive_sweep", 10, 22);
        final String clip;
        final int impact;
        final int duration;
        Strike(String clip, int impact, int duration) {
            this.clip = clip;
            this.impact = impact;
            this.duration = duration;
        }
    }

    private final AgaitolosEntity boss;
    private Strike strike;
    private UUID target;
    private long began;
    private boolean resolved;
    private double rate = 1.0;

    AgaitolosActionState(AgaitolosEntity boss) {
        this.boss = boss;
    }

    boolean busy() {
        return strike != null;
    }

    boolean start(Strike next, LivingEntity victim) {
        if (boss.level().isClientSide() || boss.isPerformingAction() || victim == null || !victim.isAlive()
                || victim.level() != boss.level() || !canAttack()) return false;
        strike = next;
        target = victim.getUUID();
        began = boss.level().getGameTime();
        resolved = false;
        rate = next == Strike.SLASH ? next.duration / (double) AgaitolosPace.scaledCooldown(boss,
                AgaitolosMeleeSkill.MELEE_INTERVAL_TICKS) : 1.0;
        boss.startVisualAction(next.clip, next.duration);
        boss.setVisualActionRate(rate);
        return true;
    }

    void beginDiveSweep() {
        strike = Strike.DIVE;
        rate = 1.0;
        target = null;
        began = boss.level().getGameTime();
        resolved = false;
        boss.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        boss.startVisualActionAt("boss_dive_sweep", 30, 8, false);
    }

    void tick() {
        if (strike == null) return;
        if (!canAttack()) {
            cancel();
            return;
        }
        double age = (boss.level().getGameTime() - began) * rate;
        if (age < 0) {
            cancel();
            return;
        }
        LivingEntity victim = boss.getTarget();
        boolean valid = victim != null && victim.isAlive() && victim.level() == boss.level()
                && victim.getUUID().equals(target);
        if (!resolved && age >= strike.impact) {
            resolved = true;
            if (strike == Strike.DIVE) {
                boolean swept = com.example.akaishi.forbidden.boss.agaitolos.skill.AgaitolosDiveSweepSkill.perform(boss);
                AgaitolosActionFx.diveSweepImpact(boss, swept);
                if (swept) boss.diveSweepCooldownTicks = AgaitolosPace.scaledCooldown(boss,
                        com.example.akaishi.forbidden.boss.agaitolos.skill.AgaitolosDiveSweepSkill.SWEEP_COOLDOWN_TICKS);
            } else if (valid) resolve(victim);
        }
        if (age >= strike.duration) cancel();
    }

    void cancel() {
        strike = null;
        target = null;
        resolved = false;
    }

    private boolean canAttack() {
        return boss.isAlive() && !boss.isRespawning() && !boss.isIntroPlaying()
                && !boss.isGuarding() && !boss.isCharging() && !boss.isDiving()
                && !boss.phaseThreeState().isBusy();
    }

    private void resolve(LivingEntity victim) {
        double distance = boss.distanceTo(victim);
        switch (strike) {
            case SLASH -> {
                if (boss.distanceToSqr(victim) <= boss.getMeleeAttackRangeSqr(victim)
                        && boss.hasLineOfSight(victim) && AgaitolosMeleeSkill.perform(boss, victim)) {
                    AgaitolosActionFx.meleeSlash(boss);
                }
            }
            case CAST -> {
                if (distance <= AgaitolosSkullSkill.SKULL_ATTACK_RADIUS
                        && boss.hasLineOfSight(victim)) AgaitolosSkullSkill.fire(boss, victim);
            }
            case KICK -> {
                if (boss.getPhase().combatOrdinal() >= AgaitolosPhase.PHASE_2.combatOrdinal()
                        && AgaitolosKickSkill.isWithinKickRange(boss, victim)
                        && AgaitolosKickSkill.perform(boss, victim)) {
                    AgaitolosActionFx.kickImpact(boss, victim);
                }
            }
            case BLINK -> {
                if (boss.getPhase().combatOrdinal() >= AgaitolosPhase.PHASE_2.combatOrdinal()
                        && boss.isGrounded() && AgaitolosBlinkSkill.canBlink(boss, victim)) {
                    net.minecraft.world.phys.Vec3 departure = boss.position();
                    boolean success = AgaitolosBlinkSkill.perform(boss, victim);
                    AgaitolosActionFx.blinkTransition(boss, departure, success);
                    if (!success) {
                        boss.blinkCooldownTicks = AgaitolosBlinkSkill.BLINK_FAILED_RETRY_TICKS;
                    }
                }
            }
        }
    }
}
