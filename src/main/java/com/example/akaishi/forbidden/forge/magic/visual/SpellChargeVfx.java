package com.example.akaishi.forbidden.forge.magic.visual;

import com.example.akaishi.forbidden.forge.client.visual.ClientWandVisuals;
import com.example.akaishi.forbidden.forge.client.visual.ClientVisualBudget;
import com.example.akaishi.forbidden.magic.cast.MagicCastState;
import com.example.akaishi.forbidden.magic.particle.AkaishiMagicParticles;
import com.example.akaishi.forbidden.magic.table.MagicSource;
import com.example.akaishi.forbidden.magic.visual.SpellCarrierForm;
import com.example.akaishi.forbidden.magic.visual.SpellCircleAnimation;
import com.example.akaishi.forbidden.magic.particle.MagicInkStyle;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.Objects;

/** Shared HUD timing drives charge convergence, channel rotation and volley pulses. */
public final class SpellChargeVfx {

    /** 起手表现的位置：视线前方多少格（够近才有"在手里成型"的感觉，又不能糊在脸上） */
    private static final float AIM_DISTANCE = 1.25f;
    private static final int MOTE_INTERVAL_TICKS = 2;
    private long lastMoteTick = Long.MIN_VALUE;
    private boolean wasActive;
    private int retreatTicks;
    private Vec3 lastAim = Vec3.ZERO;
    private int lastInk;
    private float lastRadius;

    public void clear() {
        lastMoteTick = Long.MIN_VALUE;
        wasActive = false;
        retreatTicks = 0;
        lastAim = Vec3.ZERO;
    }

    /** 绘制与服务端状态镜像同步的瞄准点表现。 */
    public void paint(PoseStack poseStack, MultiBufferSource buffers, ClientLevel level,
            Vec3 camera, float partialTick) {
        MagicCastState state = ClientWandVisuals.state();
        if (!state.isActive()) {
            if (retreatTicks > 0 && ClientVisualBudget.detailScale(level, lastAim) > 0f) {
                float remaining = Math.max(0f, (retreatTicks - partialTick) / 6f);
                Quaternionf rotation = Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
                poseStack.pushPose();
                poseStack.translate(lastAim.x - camera.x, lastAim.y - camera.y, lastAim.z - camera.z);
                SpellVisualPainter.flatQuad(buffers, poseStack, SpellVfx.RING, rotation,
                        lastRadius * (0.5f + 0.5f * remaining), remaining * remaining * 0.28f, lastInk);
                poseStack.popPose();
            }
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        MagicSource source = ClientWandVisuals.source();
        if (player == null || source == null || !ClientWandVisuals.valid()) return;
        int ink = ClientWandVisuals.inkRgb();
        int aura = source.rgb();

        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 look = player.getViewVector(partialTick);
        Vec3 aim = eye.add(look.scale(AIM_DISTANCE));
        float age = ClientWandVisuals.age(partialTick);
        float phase = ClientWandVisuals.segmentPhase(partialTick);
        float progress = ClientWandVisuals.chargeProgress(partialTick);
        Quaternionf billboard = Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
        float detail = ClientVisualBudget.detailScale(level, aim);
        if (detail <= 0f) return;

        poseStack.pushPose();
        poseStack.translate(aim.x - camera.x, aim.y - camera.y, aim.z - camera.z);
        float opened = state == MagicCastState.CHARGING
                ? SpellCircleAnimation.smooth(age / 5f) : 1f;
        float gathered = state == MagicCastState.CHARGING
                ? SpellCircleAnimation.smooth(progress) : 1f;
        float breath = 0.5f + 0.5f * (float) Math.sin(age * 0.12f);
        float volley = 0.5f + 0.5f * (float) Math.cos(phase * Math.PI * 2d);
        float transition = SpellCircleAnimation.smooth(ClientWandVisuals.transition(partialTick));
        float radius = state == MagicCastState.CHARGING ? 0.44f - gathered * 0.20f
                : state == MagicCastState.VOLLEYING ? 0.27f + (1f - volley) * 0.045f
                : 0.31f + breath * 0.025f - transition * 0.065f;
        float opacity = opened * (0.26f + 0.30f * gathered);
        SpellVisualPainter.flatQuad(buffers, poseStack, SpellVfx.RING,
                new Quaternionf(billboard).rotateZ(age * 0.008f), radius,
                opacity * 0.60f, ink);
        SpellVisualPainter.flatQuad(buffers, poseStack, SpellVfx.HALO, billboard,
                0.11f + 0.09f * gathered, opened * (0.18f + 0.20f * gathered), aura);
        poseStack.pushPose();
        poseStack.mulPose(Objects.requireNonNull(billboard));
        SpellSourceVfx.paint(poseStack, buffers, source, aura, ink, age / 20f,
                radius, opacity, detail);
        if (detail > 0.5f) {
            paintSeal(poseStack, buffers, age, gathered, radius, opacity, detail, ink);
        }
        if (detail > 0.72f) {
            SpellVisualPainter.paintWordRing(poseStack, buffers, ClientWandVisuals.slotWords(),
                    ClientWandVisuals.slotInks(), ink, radius * 0.88f, 0.065f,
                    -age * 0.36f, opacity * 0.85f, 0.12f + gathered * 0.88f);
        }
        poseStack.popPose();
        poseStack.popPose();
    }

    private static void paintSeal(PoseStack pose, MultiBufferSource buffers, float age, float gathered,
            float radius, float alpha, float detail, int ink) {
        var consumer = buffers.getBuffer(SpellGeometry.energyType());
        float r = SpellVisualPainter.red(ink), g = SpellVisualPainter.green(ink), b = SpellVisualPainter.blue(ink);
        float spin = age * (0.32f + Math.abs(MagicInkStyle.get(ClientWandVisuals.inkId()).swirl()) * 0.06f);
        for (int arc = 0; arc < 3; arc++) {
            float reveal = SpellCircleAnimation.smooth((gathered - arc * 0.08f) / 0.72f);
            SpellGeometry.ringBand(consumer, pose.last().pose(), radius * 1.07f, radius * 1.085f,
                    detail > 0.72f ? 24 : 16, arc * 120f + spin, 12f + 78f * reveal,
                    alpha * 0.20f, alpha * 0.65f, r, g, b);
        }
        int marks = detail > 0.72f ? 12 : 6;
        for (int i = 0; i < marks; i++) {
            float reveal = SpellCircleAnimation.smooth(gathered * 1.3f - i / (float) marks * 0.5f);
            var local = new org.joml.Matrix4f(pose.last().pose())
                    .rotateZ((float) Math.toRadians(i * 360f / marks - spin * 0.5f))
                    .translate(radius * 1.12f, 0f, 0.002f);
            SpellGeometry.texturedRibbon(consumer, local, 1f, 0f, 0f, 0f, 1f, 0f,
                    radius * (i % 3 == 0 ? 0.045f : 0.025f), radius * 0.008f,
                    alpha * reveal * 0.65f, r, g, b);
        }
    }

    public void tick(ClientLevel level) {
        long now = level.getGameTime();
        if (lastMoteTick == now) return;
        lastMoteTick = now;
        MagicCastState state = ClientWandVisuals.state();
        LocalPlayer player = Minecraft.getInstance().player;
        if (retreatTicks > 0) retreatTicks--;
        if (!state.isActive()) {
            if (wasActive && player != null && player.isAlive()) retreatTicks = 6;
            wasActive = false;
            return;
        }
        MagicSource source = ClientWandVisuals.source();
        if (player == null || source == null || !ClientWandVisuals.valid()) {
            clear();
            return;
        }
        wasActive = true;
        retreatTicks = 0;
        Vec3 aim = player.getEyePosition().add(player.getLookAngle().scale(AIM_DISTANCE));
        lastAim = aim;
        lastInk = ClientWandVisuals.inkRgb();
        lastRadius = state == MagicCastState.CHARGING
                ? 0.44f - SpellCircleAnimation.smooth(ClientWandVisuals.chargeProgress(0f)) * 0.20f : 0.29f;
        if ((int) ClientWandVisuals.age(0f) % MOTE_INTERVAL_TICKS != 0) return;
        int count = 1;
        for (int i = 0; i < count; i++) {
            spawnMotes(level, player, source, ClientWandVisuals.inkId(), ClientWandVisuals.inkRgb(),
                    aim.x, aim.y, aim.z);
        }
    }

    /** 从玩家眼睛周围一圈生成一颗微光，初速指向瞄准点（"在吸取"的方向性） */
    private static void spawnMotes(ClientLevel level, LocalPlayer player, MagicSource source, String inkId,
            int ink, double aimX, double aimY, double aimZ) {
        MagicInkStyle style = MagicInkStyle.get(inkId);
        double angle = ClientWandVisuals.age(0f) * 0.55d + (level.getGameTime() % 3L) * Math.PI * 2d / 3d;
        double radius = (0.42d + 0.14d * (1d - ClientWandVisuals.chargeProgress(0f)))
                * Math.min(1.2d, style.spreadMul());
        Vec3 look = player.getLookAngle();
        Vec3 side = look.cross(Math.abs(look.y) > 0.9d ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        Vec3 up = side.cross(look).normalize();
        Vec3 point = new Vec3(aimX, aimY, aimZ).add(side.scale(Math.cos(angle) * radius))
                .add(up.scale(Math.sin(angle) * radius)).subtract(look.scale(0.12d));
        AkaishiMagicParticles.converge(level, source, inkId, SpellCarrierForm.ORB, ink,
                point.x, point.y, point.z, aimX, aimY, aimZ, 0.065d + style.drag() * 0.02d);
    }
}
