package com.example.akaishi.forbidden.forge.boss.agaitolos;

import com.eliotlash.mclib.math.IValue;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.keyframe.BoneAnimation;
import software.bernie.geckolib.core.keyframe.Keyframe;
import software.bernie.geckolib.core.keyframe.KeyframeStack;

import java.util.List;

/** 显式客户端烟测：检查 GeckoLib 实际烘焙结果，而非仅验证原始 JSON。 */
public final class AgaitolosAnimationCheck {
    private static final List<String> CLIPS = List.of("boss_idle", "boss_move", "boss_slash",
            "boss_summon_throw", "boss_dive_sweep", "boss_block", "boss_hurt", "boss_curse_reverse",
            "boss_curse_break", "boss_calamity", "boss_triple_throw", "boss_blink_strike",
            "boss_stomp_cleave", "boss_rapid_kick", "boss_saturation_barrage", "boss_execution_cleave");

    private AgaitolosAnimationCheck() { }

    public static void verify() {
        AgaitolosModel model = new AgaitolosModel();
        for (String clip : CLIPS) {
            Animation animation = model.getAnimation(null, clip);
            require(animation != null, "Missing baked Queen animation: " + clip);
            for (BoneAnimation bone : animation.boneAnimations()) {
                String label = clip + "/" + bone.boneName();
                checkStack(bone.rotationKeyFrames(), label + "/rotation");
                checkStack(bone.positionKeyFrames(), label + "/position");
                checkStack(bone.scaleKeyFrames(), label + "/scale");
            }
        }
        Animation slash = model.getAnimation(null, "boss_slash");
        BoneAnimation arm = java.util.Arrays.stream(slash.boneAnimations())
                .filter(bone -> bone.boneName().equals("RightArm")).findFirst().orElseThrow();
        List<Keyframe<IValue>> frames = arm.rotationKeyFrames().zKeyframes();
        require(Math.abs(sample(frames, 6) - sample(frames, 0)) > Math.toRadians(30),
                "Baked slash arm remained at its initial pose during windup");
        require(Math.abs(sample(frames, 12) - sample(frames, 0)) > Math.toRadians(30),
                "Baked slash arm remained at its initial pose during impact");
        require(AgaitolosRenderer.bladeWindow("boss_slash", 12), "Slash motion misses its blade window");
        checkBodyContrast(model, "boss_slash", 10, 12, 20);
        checkBodyContrast(model, "boss_execution_cleave", 9, 12, 32);
        checkBodyContrast(model, "boss_summon_throw", 18, 20, 9);
    }

    private static void checkBodyContrast(AgaitolosModel model, String clip, double windup, double impact, double degrees) {
        BoneAnimation body = java.util.Arrays.stream(model.getAnimation(null, clip).boneAnimations())
                .filter(bone -> bone.boneName().equals("AllBody")).findFirst().orElseThrow();
        List<Keyframe<IValue>> frames = body.rotationKeyFrames().xKeyframes();
        require(Math.abs(sample(frames, impact) - sample(frames, windup)) >= Math.toRadians(degrees),
                "Baked representative action lost its readable windup/impact contrast: " + clip);
        require(Math.abs(sample(frames, 0)) < 0.00001, "Representative action changed its entry pose: " + clip);
    }

    private static void checkStack(KeyframeStack<Keyframe<IValue>> stack, String label) {
        for (List<Keyframe<IValue>> axis : List.of(stack.xKeyframes(), stack.yKeyframes(), stack.zKeyframes())) {
            for (Keyframe<IValue> frame : axis) {
                require(Double.isFinite(frame.length()) && frame.length() >= 0,
                        "Negative or invalid baked keyframe duration: " + label + " = " + frame.length());
            }
        }
    }

    private static double sample(List<Keyframe<IValue>> frames, double tick) {
        double elapsed = 0;
        for (Keyframe<IValue> frame : frames) {
            elapsed += frame.length();
            if (elapsed > tick) {
                double progress = (tick - elapsed + frame.length()) / frame.length();
                return frame.startValue().get() + (frame.endValue().get() - frame.startValue().get()) * progress;
            }
        }
        return frames.get(frames.size() - 1).endValue().get();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
