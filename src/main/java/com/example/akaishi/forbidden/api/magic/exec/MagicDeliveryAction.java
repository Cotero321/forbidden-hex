package com.example.akaishi.forbidden.api.magic.exec;

import net.minecraft.world.phys.Vec3;

/**
 * "<b>此刻，在这里，触发一次本段效果</b>"的回调句柄。
 *
 * <p><b>为什么投送器不直接调执行器</b>：{@link IDeliverer} 决定的是"<b>什么时候、在哪儿</b>触发"，
 * 而"触发时打谁、打多狠"是编排器（目标解析 + 执行器）的事。若让投送器自己遍历目标、自己挑执行器，
 * 它就得同时知道 G 与 E —— 那正是 §19.1 要拆开的那团焊死物。
 * 回调把这条边界钉死：<b>投送器只说"现在，这里"，编排器负责"打谁"</b>。
 *
 * <p>同一时间点可以调用多次（连发即多次 {@code fire}），也可以由投送器推迟到未来某一刻再调
 * （回响 / 投射落地）。{@link IPersistenceHandler} 的二次负载同样复用它。
 */
@FunctionalInterface
public interface MagicDeliveryAction {

    /**
     * 以 {@code at} 为原点触发一次效果（编排器内部完成目标解析 + 逐目标执行）。
     *
     * @param at 本次触发的原点（直射 = 施法原点；投射 = 弹体当前位置；布场 = 落点）
     */
    void fire(Vec3 at);

    default void fire(net.minecraft.world.entity.Entity hit, Vec3 at, Vec3 direction) {
        fire(at);
    }
}
