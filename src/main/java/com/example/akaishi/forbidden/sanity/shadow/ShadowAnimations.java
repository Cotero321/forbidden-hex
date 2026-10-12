package com.example.akaishi.forbidden.sanity.shadow;

import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

/**
 * 影怪的动画名常量与控制器注册（照 {@code AgaitolosAnimations} 的既有口径）。
 *
 * <p>clip 名必须与 {@code assets/forbidden_hex/animations/entity/shadow.animation.json} 逐字一致 ——
 * 写错只会在实机渲染时抛 GeckoLibException，编译期查不出来。骨骼只有 {@code root / body / head / arm_l / arm_r}
 * 五根，动作全部由 Java 侧状态驱动，动画本身不读实体状态。
 *
 * <p>两个控制器：
 * <ul>
 *   <li>{@link #CONTROLLER_MAIN} —— 常驻待机（loop 2s）；动作窗口内主动 STOP，把骨骼让给动作控制器
 *       （两个控制器同时驱动同一批骨骼会互相拉扯，这是 {@code AgaitolosAnimations} 已经踩过的坑）；</li>
 *   <li>{@link #CONTROLLER_ACTION} —— 触发式一次性动作（扑击 0.5s / 消散 0.4s），由服务端
 *       {@code triggerAnim} 派发、GeckoLib 自动同步给追踪客户端。</li>
 * </ul>
 */
public final class ShadowAnimations {

    /** 常驻待机（loop 2s） */
    public static final String IDLE = "animation.shadow.idle";
    /** 扑击（0.5s 单次） */
    public static final String ATTACK = "animation.shadow.attack";
    /** 消散（0.4s 单次）：既用于受击后的瞬移，也用于四次命中后的消散与超时自毁 */
    public static final String VANISH = "animation.shadow.vanish";

    /** 主控制器名：常驻待机 */
    public static final String CONTROLLER_MAIN = "main";
    /** 动作控制器名：触发式动作共用 */
    public static final String CONTROLLER_ACTION = "action";

    /** 待机过渡时长（tick）：让"消失后重新出现"的姿态切换不硬切 */
    public static final int TRANSITION_TICKS = 5;
    /** 动作过渡时长：动作本身只有 0.4~0.5s，过渡必须更短，否则首尾糊成一团 */
    public static final int ACTION_TRANSITION_TICKS = 2;

    /**
     * 触发键名。刻意与 clip 同名：{@code triggerAnim} 传错名字不会报错、只会静默不播，
     * 同名时"动画没出来"与"json 里没这条 clip"能一眼对上。
     */
    public static final String TRIGGER_ATTACK = "attack";
    public static final String TRIGGER_VANISH = "vanish";

    /** 扑击 clip 长度（tick）：0.5s，动作锁定窗口与它等长（改 clip 长度要同步改这里） */
    public static final int ATTACK_TICKS = 10;
    /** 消散 clip 长度（tick）：0.4s，同上 —— 瞬移落位与消散移除都卡在这个窗口末尾 */
    public static final int VANISH_TICKS = 8;

    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop(IDLE);
    // 两条动作 clip 在 json 里 loop 均为缺省（PLAY_ONCE）：thenPlay 播完自停，控制器随即交还给状态回调
    private static final RawAnimation ATTACK_ANIM = RawAnimation.begin().thenPlay(ATTACK);
    private static final RawAnimation VANISH_ANIM = RawAnimation.begin().thenPlay(VANISH);

    private ShadowAnimations() {
    }

    /** 注册动画控制器（由 {@link ShadowEntity#registerControllers} 转发） */
    public static void registerControllers(ShadowEntity entity, AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(entity, CONTROLLER_MAIN, TRANSITION_TICKS,
                state -> state.getAnimatable().isActing()
                        ? PlayState.STOP
                        : state.setAndContinue(IDLE_ANIM)));

        // 动作控制器：状态回调只在"没有触发动画在播"时才会走到这里，此时无事可做 ⇒ STOP 等下一次 triggerAnim。
        // 不能返回 CONTINUE，否则控制器永远停不下来，触发式动画结束后会卡在最后一帧。
        controllers.add(new AnimationController<>(entity, CONTROLLER_ACTION, ACTION_TRANSITION_TICKS,
                state -> PlayState.STOP)
                .triggerableAnim(TRIGGER_ATTACK, ATTACK_ANIM)
                .triggerableAnim(TRIGGER_VANISH, VANISH_ANIM));
    }
}
