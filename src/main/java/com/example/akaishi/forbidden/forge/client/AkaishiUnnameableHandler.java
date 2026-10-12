package com.example.akaishi.forbidden.forge.client;

import com.example.akaishi.forbidden.effect.UnnameableClientAmbience;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 「不可名状」视野扭曲（仅客户端）：相机滚转倾斜、yaw/pitch 低频抖动与 FOV 脉动，
 * 随效果强度线性放大，营造"视线开始失衡"的眩晕感。强度由
 * {@link UnnameableClientAmbience} 平滑提供，效果消退后视野自动还原。
 */
public final class AkaishiUnnameableHandler {

    public static final AkaishiUnnameableHandler INSTANCE = new AkaishiUnnameableHandler();

    /** 满强度时的相机滚转幅度（度） */
    private static final float MAX_ROLL = 5.0f;
    /** 满强度时的 yaw/pitch 抖动幅度（度） */
    private static final float MAX_DRIFT = 1.8f;
    /** 满强度时的 FOV 脉动幅度（度） */
    private static final float MAX_FOV_SWING = 6.0f;

    private AkaishiUnnameableHandler() {
    }

    @SubscribeEvent
    public void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float level = UnnameableClientAmbience.level();
        if (level <= 0.02f) {
            return;
        }
        float t = elapsedSeconds();
        // roll 为主通道：双频叠加产生不规则倾斜，比单一正弦更"不安定"
        float roll = (Mth.sin(t * 1.10f) + 0.5f * Mth.sin(t * 2.70f)) * MAX_ROLL * level;
        event.setRoll(event.getRoll() + roll);
        // yaw/pitch 用不同频率抖动，避免抖动方向固定成可预测的规律
        event.setYaw(event.getYaw() + Mth.sin(t * 1.90f) * MAX_DRIFT * level);
        event.setPitch(event.getPitch() + Mth.cos(t * 2.30f) * MAX_DRIFT * level);
    }

    @SubscribeEvent
    public void onComputeFov(ViewportEvent.ComputeFov event) {
        // 只改常规 FOV，望远镜/旁观等特殊视角保持原值，避免破坏其手感
        if (!event.usedConfiguredFov()) {
            return;
        }
        float level = UnnameableClientAmbience.level();
        if (level <= 0.02f) {
            return;
        }
        float t = elapsedSeconds();
        double swing = (Mth.sin(t * 1.50f) + 0.4f * Mth.sin(t * 3.30f)) * MAX_FOV_SWING * level;
        event.setFOV(event.getFOV() + swing);
    }

    /** 以秒为单位的连续时间：与帧率解耦，保证不同帧率下抖动节奏一致 */
    private static float elapsedSeconds() {
        return (float) (System.nanoTime() / 1.0E9D);
    }
}
