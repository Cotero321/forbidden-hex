package com.example.akaishi.forbidden.boss.agaitolos;

import com.example.akaishi.forbidden.boss.agaitolos.entity.AgaitolosHexOrb;
import com.example.akaishi.forbidden.entity.AkaishiForbiddenEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("forbidden_hex")
@PrefixGameTestTemplate(false)
public final class AgaitolosActionGameTests {
    private AgaitolosActionGameTests() { }

    private record Rig(AgaitolosEntity boss, Pig victim) { }

    private static Rig rig(GameTestHelper helper, boolean perched) {
        var level = helper.getLevel();
        AgaitolosEntity boss = AkaishiForbiddenEntities.AGAITOLOS.get().create(level);
        Pig victim = EntityType.PIG.create(level);
        helper.assertTrue(boss != null && victim != null, "Missing test entity");
        Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 10, 1)));
        boss.setPos(origin);
        boss.initialRespawnDone = true;
        boss.setPhase(AgaitolosPhase.PHASE_3);
        boss.perched = perched;
        boss.setPerchState(perched ? AgaitolosEntity.PERCH_STATE_PERCHED : AgaitolosEntity.PERCH_STATE_AIRBORNE);
        victim.setPos(origin.add(0, 0, 1));
        victim.setNoAi(true);
        victim.setNoGravity(true);
        level.addFreshEntity(victim);
        boss.setTarget(victim);
        return new Rig(boss, victim);
    }

    @GameTest(template = "brewing_empty")
    public static void interruptedDiveReleasesVisualLock(GameTestHelper helper) {
        Rig rig = rig(helper, false);
        AgaitolosEntity boss = rig.boss();
        boss.diveTicks = 10;
        AgaitolosAnimations.playDiveSweep(boss);
        boss.setRespawning(true);
        AgaitolosDiveMachine.tickDiveState(boss);
        boss.setRespawning(false);
        helper.assertTrue(!boss.isDiving() && !boss.isPerformingAction(), "Interrupted held dive permanently locked actions");
        helper.assertTrue(boss.actions().start(AgaitolosActionState.Strike.SLASH, rig.victim()), "Boss cannot attack after dive interruption");
        rig.victim().discard();
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void grabIgnoresOldHurtAndCancelsOnNewDamage(GameTestHelper helper) {
        Rig rig = rig(helper, true);
        AgaitolosEntity boss = rig.boss();
        boss.hurtTime = 10;
        helper.assertTrue(boss.phaseThreeState().startGrabSweep(boss, rig.victim()), "Grab did not start");
        boss.phaseThreeState().tick(boss);
        helper.assertTrue(boss.phaseThreeState().isHoldingTarget(), "Pre-existing hurtTime incorrectly interrupted new grab");
        ++boss.appliedHurtSequence;
        boss.phaseThreeState().tick(boss);
        helper.assertTrue(!boss.phaseThreeState().isHoldingTarget() && !boss.isPerformingAction(), "New damage failed to release grab and visual lock");
        rig.victim().discard();
        helper.succeed();
    }

    @GameTest(template = "brewing_empty", timeoutTicks = 100)
    public static void phaseInterruptedChargeReleasesVisualLock(GameTestHelper helper) {
        Rig rig = rig(helper, false);
        AgaitolosEntity boss = rig.boss();
        boss.chargeTicks = 240;
        boss.setCharging(true);
        boss.setRespawning(true);
        AgaitolosGuardCharge.tickChargeState(boss);
        helper.assertTrue(!boss.isCharging(), "Phase transition left charging active");
        helper.runAfterDelay(80, () -> {
            boss.setRespawning(false);
            helper.assertTrue(!boss.isPerformingAction(), "Old charge clip locked boss after respawn");
            helper.assertTrue(boss.actions().start(AgaitolosActionState.Strike.SLASH, rig.victim()),
                    "Boss cannot attack after interrupted charge");
            rig.victim().discard();
            helper.succeed();
        });
    }

    @GameTest(template = "brewing_empty")
    public static void lostGrabTargetReleasesVisualLock(GameTestHelper helper) {
        Rig rig = rig(helper, true);
        helper.assertTrue(rig.boss().phaseThreeState().startGrabSweep(rig.boss(), rig.victim()), "Grab did not start");
        rig.victim().discard();
        rig.boss().phaseThreeState().tick(rig.boss());
        helper.assertTrue(!rig.boss().phaseThreeState().isBusy() && !rig.boss().isPerformingAction(), "Lost target left an active stomp animation");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void phaseThreeCannotOverwriteOtherAction(GameTestHelper helper) {
        Rig rig = rig(helper, false);
        helper.assertTrue(rig.boss().actions().start(AgaitolosActionState.Strike.SLASH, rig.victim()), "Slash did not start");
        helper.assertTrue(!rig.boss().phaseThreeState().startTripleThrow(rig.boss(), rig.victim()), "Triple throw overwrote slash");
        helper.assertTrue(!rig.boss().phaseThreeState().startCalamity(rig.boss(), rig.victim()), "Calamity overwrote slash");
        helper.assertTrue(!rig.boss().phaseThreeState().startBombard(rig.boss(), rig.victim()), "Barrage overwrote slash");
        helper.assertTrue(rig.boss().getVisualAction().equals("boss_slash"), "Slash clip replaced");
        rig.victim().discard();
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void sameEntityReloadDropsEveryPhaseThreeAction(GameTestHelper helper) {
        for (int move = 0; move < 5; move++) {
            Rig rig = rig(helper, move == 2 || move == 3);
            AgaitolosEntity boss = rig.boss();
            AgaitolosPhaseThreeState state = boss.phaseThreeState();
            boolean started = switch (move) {
                case 0 -> state.startTripleThrow(boss, rig.victim());
                case 1 -> state.startBombard(boss, rig.victim());
                case 2 -> state.startGrabSweep(boss, rig.victim());
                case 3 -> state.startGrabSmash(boss, rig.victim());
                default -> state.startCalamity(boss, rig.victim());
            };
            helper.assertTrue(started, "Phase three action failed to start: " + move);
            CompoundTag saved = new CompoundTag();
            boss.addAdditionalSaveData(saved);
            boss.readAdditionalSaveData(saved);
            helper.assertTrue(!state.isBusy() && !boss.isBombarding() && !boss.isPerformingAction(), "Same-entity reload retained phase three action: " + move);
            CompoundTag after = new CompoundTag();
            state.save(after);
            for (String key : after.getAllKeys()) helper.assertTrue(after.getInt(key) == saved.getInt(key), "Reload changed cooldown: " + key);
            rig.victim().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "brewing_empty", timeoutTicks = 60)
    public static void tripleReleaseMatchesVisualClock(GameTestHelper helper) {
        Rig rig = rig(helper, false);
        helper.assertTrue(rig.boss().phaseThreeState().startTripleThrow(rig.boss(), rig.victim()), "Triple throw did not start");
        for (int tick = 1; tick <= 40; tick++) {
            final int age = tick;
            helper.runAfterDelay(tick, () -> {
                rig.boss().phaseThreeState().tick(rig.boss());
                helper.assertTrue(Math.abs(rig.boss().getVisualActionAge(0) - age) < 0.00001, "Triple visual clock mismatch");
                long newborn = helper.getLevel().getEntitiesOfClass(AgaitolosHexOrb.class,
                        rig.boss().getBoundingBox().inflate(32), orb -> orb.getOwner() == rig.boss() && orb.tickCount == 0).size();
                boolean release = age == 6 || age == 12 || age == 18;
                helper.assertTrue(newborn == (release ? 1 : 0), "Triple release differs from visual beat at " + age + ": " + newborn);
                if (age == 40) {
                    helper.assertTrue(!rig.boss().phaseThreeState().isBusy(), "Triple throw did not recover");
                    rig.victim().discard();
                    helper.succeed();
                }
            });
        }
    }

    @GameTest(template = "brewing_empty", timeoutTicks = 40)
    public static void kickDoesNotHitEscapedTarget(GameTestHelper helper) {
        Rig rig = rig(helper, false);
        helper.assertTrue(rig.boss().actions().start(AgaitolosActionState.Strike.KICK, rig.victim()), "Kick did not start");
        float health = rig.victim().getHealth();
        rig.victim().setPos(rig.boss().position().add(20, 0, 0));
        helper.runAfterDelay(10, () -> {
            rig.boss().actions().tick();
            helper.assertTrue(rig.victim().getHealth() == health, "Kick hit a target outside its existing range");
            rig.victim().discard();
            helper.succeed();
        });
    }
}
