package com.example.akaishi.forbidden.familiar.entity;

import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;

/** Conservative first-pass animation controller; action clips are added per template. */
public final class FamiliarAnimations {
    public static final String IDLE = "animation.familiar.idle";

    private FamiliarAnimations() {
    }

    public static void registerControllers(FamiliarEntity entity, AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(entity, "main", 5,
                state -> state.setAndContinue(RawAnimation.begin().thenLoop(IDLE))));
    }
}
