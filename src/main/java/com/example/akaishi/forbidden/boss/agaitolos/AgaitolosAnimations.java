package com.example.akaishi.forbidden.boss.agaitolos;

import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.Map;

/** 一套骨架只由一个控制器写入；动画时钟取同步的服务端动作起点。 */
public final class AgaitolosAnimations {
    public static final String IDLE_FLIGHT = "boss_idle";
    public static final String IDLE_GROUND = IDLE_FLIGHT;
    public static final String LAND = IDLE_FLIGHT;
    public static final String TAKEOFF = IDLE_FLIGHT;
    public static final String ATTACK_SLASH = "boss_slash";
    public static final String CAST_SKULL = "boss_summon_throw";
    public static final String DIVE_SWEEP = "boss_dive_sweep";
    public static final String CHARGE = "boss_curse_reverse";
    public static final String GUARD = "boss_block";
    public static final String HURT = "boss_hurt";
    public static final String DEATH = IDLE_FLIGHT;
    public static final String INTRO = IDLE_FLIGHT;
    public static final String TRIPLE_THROW = "boss_triple_throw";
    public static final String BOMBARD = "boss_saturation_barrage";
    public static final String GRAB_SWEEP = "boss_stomp_cleave";
    public static final String GRAB_SMASH = "boss_execution_cleave";
    public static final String CALAMITY_CAST = "boss_calamity";
    private static final Map<String, Integer> LENGTHS = Map.ofEntries(
            Map.entry(IDLE_FLIGHT, 80), Map.entry("boss_move", 40), Map.entry(ATTACK_SLASH, 20),
            Map.entry(CAST_SKULL, 30), Map.entry(DIVE_SWEEP, 30), Map.entry(CHARGE, 240),
            Map.entry(GUARD, 24), Map.entry(HURT, 12), Map.entry("boss_curse_break", 26),
            Map.entry(TRIPLE_THROW, 40), Map.entry(BOMBARD, 40), Map.entry(GRAB_SWEEP, 22),
            Map.entry(GRAB_SMASH, 28), Map.entry(CALAMITY_CAST, 44),
            Map.entry("boss_blink_strike", 34), Map.entry("boss_rapid_kick", 26));

    private AgaitolosAnimations() { }

    public static void registerControllers(AgaitolosEntity boss, AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new ServerClockController(boss));
    }

    private static boolean loop(String clip) {
        return clip.equals(IDLE_FLIGHT) || clip.equals("boss_move") || clip.equals(BOMBARD);
    }

    private static final class ServerClockController extends AnimationController<AgaitolosEntity> {
        private final AgaitolosEntity boss;
        private String clip = "";
        private long started = Long.MIN_VALUE;
        private float partialTick;
        private boolean resetClock;

        private ServerClockController(AgaitolosEntity boss) {
            super(boss, "queen", 0, state -> PlayState.CONTINUE);
            this.boss = boss;
        }

        @Override
        protected PlayState handleAnimationState(software.bernie.geckolib.core.animation.AnimationState<AgaitolosEntity> state) {
            partialTick = state.getPartialTick();
            String next = boss.getVisualAction();
            if (!LENGTHS.containsKey(next)) next = IDLE_FLIGHT;
            long start = boss.getVisualActionStart();
            if (!next.equals(clip) || !loop(next) && started != start) {
                clip = next;
                started = start;
                resetClock = true;
                forceAnimationReset();
            }
            setAnimation(loop(clip) ? RawAnimation.begin().thenLoop(clip) : RawAnimation.begin().thenPlayAndHold(clip));
            return PlayState.CONTINUE;
        }

        @Override
        protected double adjustTick(double tick) {
            super.adjustTick(tick);
            if (resetClock) {
                resetClock = false;
                return 0;
            }
            if (getAnimationState() == State.TRANSITIONING) return 0;
            double age = loop(clip) && !clip.equals(BOMBARD)
                    ? boss.level().getGameTime() + partialTick : boss.getVisualActionAge(partialTick);
            int length = LENGTHS.getOrDefault(clip, 80);
            return loop(clip) ? age % length : Math.min(age, length - 0.001);
        }
    }

    public static void playAttack(AgaitolosEntity boss) { boss.startVisualAction(ATTACK_SLASH, 38); }
    public static void playCast(AgaitolosEntity boss) { boss.startVisualAction(CAST_SKULL, 30); }
    public static void playDiveSweep(AgaitolosEntity boss) { boss.startVisualActionAt(DIVE_SWEEP, 30, 0, true); }
    public static void playTripleThrow(AgaitolosEntity boss) { boss.startVisualAction(TRIPLE_THROW, 40); }
    public static void playGrabSweep(AgaitolosEntity boss) { boss.startVisualAction(GRAB_SWEEP, 22); }
    public static void playGrabSmash(AgaitolosEntity boss) { boss.startVisualAction(GRAB_SMASH, 28); }
    public static void playCalamityCast(AgaitolosEntity boss) { boss.startVisualAction(CALAMITY_CAST, 44); }
    public static void playHurt(AgaitolosEntity boss) {
        if (!boss.isPerformingAction() && !boss.isGuarding() && !boss.isCharging()
                && !boss.isDiving() && !boss.phaseThreeState().isBusy()
                && (boss.getVisualAction().equals(IDLE_FLIGHT) || boss.getVisualAction().equals("boss_move"))) {
            boss.startVisualAction(HURT, 12);
        }
    }
}
