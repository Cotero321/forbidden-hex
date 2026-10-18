package com.example.akaishi.forbidden.forge.magic.visual;

import com.example.akaishi.forbidden.forge.client.visual.ClientWandVisuals;
import com.example.akaishi.forbidden.forge.client.visual.ClientVisualBudget;
import com.example.akaishi.forbidden.magic.cast.MagicCastState;
import com.example.akaishi.forbidden.magic.particle.AkaishiMagicParticles;
import com.example.akaishi.forbidden.magic.table.MagicSource;
import com.example.akaishi.forbidden.magic.visual.SpellCarrierForm;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Shared HUD timing drives charge convergence, channel rotation and volley pulses. */
public final class SpellChargeVfx {

    /** 起手表现的位置：视线前方多少格（够近才有"在手里成型"的感觉，又不能糊在脸上） */
    private static final float AIM_DISTANCE = 1.25f;
    private static final int MOTE_INTERVAL_TICKS = 2;
    private long lastMoteTick = Long.MIN_VALUE;

    public void clear() {
        lastMoteTick = Long.MIN_VALUE;
    }

    /** 绘制与服务端状态镜像同步的瞄准点表现。 */
    public void paint(PoseStack poseStack, MultiBufferSource buffers, ClientLevel level,
            Vec3 camera, float partialTick) {
        MagicCastState state = ClientWandVisuals.state();
        if (!state.isActive()) return;
        LocalPlayer player = Minecraft.getInstance().player;
        MagicSource source = ClientWandVisuals.source();
        if (player == null || source == null || !ClientWandVisuals.valid()) return;
        int ink = ClientWandVisuals.inkRgb();
        int aura = source.rgb();

        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 look = player.getLookAngle();
        Vec3 aim = eye.add(look.scale(AIM_DISTANCE));
        float age = ClientWandVisuals.age(partialTick);
        float phase = ClientWandVisuals.segmentPhase(partialTick);
        float progress = ClientWandVisuals.chargeProgress(partialTick);
        Quaternionf billboard = Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
        float detail = ClientVisualBudget.detailScale(level, aim);
        if (detail <= 0f) return;

        poseStack.pushPose();
        poseStack.translate(aim.x - camera.x, aim.y - camera.y, aim.z - camera.z);
        if (state == MagicCastState.CHARGING) {
            float tighten = 1f - progress;
            SpellVisualPainter.flatQuad(buffers, poseStack, SpellVfx.RING, billboard,
                    0.14f + 0.30f * tighten, 0.20f + 0.55f * progress, ink);
            SpellVisualPainter.flatQuad(buffers, poseStack, SpellVfx.HALO, billboard,
                    0.10f + 0.12f * progress, 0.20f + 0.30f * progress, aura);
        } else if (state == MagicCastState.CHANNELING) {
            float breath = 0.5f + 0.5f * (float) Math.sin(age * 0.20d);
            Quaternionf rotation = new Quaternionf(billboard).rotateZ(age * 0.06f);
            SpellVisualPainter.flatQuad(buffers, poseStack, SpellVfx.RING_PULSE, rotation,
                    0.26f + 0.05f * breath, 0.45f + 0.25f * breath, ink);
            SpellVisualPainter.flatQuad(buffers, poseStack, SpellVfx.HALO, billboard,
                    0.16f + 0.06f * breath, 0.35f + 0.20f * breath, aura);
        } else if (state == MagicCastState.VOLLEYING) {
            float pulse = 1f - phase;
            SpellVisualPainter.flatQuad(buffers, poseStack, SpellVfx.RING_PULSE, billboard,
                    0.20f + 0.16f * phase, 0.20f + 0.55f * pulse, ink);
            SpellVisualPainter.flatQuad(buffers, poseStack, SpellVfx.HALO, billboard,
                    0.10f + 0.10f * pulse, 0.18f + 0.48f * pulse, aura);
        }
        if (detail > 0.72f) {
            Quaternionf sigil = new Quaternionf(billboard).rotateZ(-age * 0.035f);
            SpellVisualPainter.flatQuad(buffers, poseStack, SpellVfx.SIGIL, sigil,
                    0.18f + progress * 0.12f, 0.12f + progress * 0.22f, ink);
        }
        poseStack.popPose();
    }

    public void tick(ClientLevel level) {
        long now = level.getGameTime();
        if (lastMoteTick == now) return;
        lastMoteTick = now;
        MagicCastState state = ClientWandVisuals.state();
        if (!state.isActive()) return;
        LocalPlayer player = Minecraft.getInstance().player;
        MagicSource source = ClientWandVisuals.source();
        if (player == null || source == null || !ClientWandVisuals.valid()
                || (int) ClientWandVisuals.age(0f) % MOTE_INTERVAL_TICKS != 0) return;
        Vec3 aim = player.getEyePosition().add(player.getLookAngle().scale(AIM_DISTANCE));
        int count = state == MagicCastState.CHARGING && ClientWandVisuals.chargeProgress(0f) > 0.65f ? 2 : 1;
        for (int i = 0; i < count; i++) {
            spawnMotes(level, player, source, ClientWandVisuals.inkId(), ClientWandVisuals.inkRgb(),
                    aim.x, aim.y, aim.z);
        }
    }

    /** 从玩家眼睛周围一圈生成一颗微光，初速指向瞄准点（"在吸取"的方向性） */
    private static void spawnMotes(ClientLevel level, LocalPlayer player, MagicSource source, String inkId,
            int ink, double aimX, double aimY, double aimZ) {
        double angle = level.random.nextDouble() * Math.PI * 2d;
        double radius = 0.70d + level.random.nextDouble() * 0.40d;
        Vec3 eye = player.getEyePosition();
        double x = eye.x + Math.cos(angle) * radius;
        double y = eye.y + (level.random.nextDouble() - 0.35d) * 0.80d;
        double z = eye.z + Math.sin(angle) * radius;
        // 起手尚未定载体，取最中性的「核心」形 —— 微光仍带着该墨的形状与个性
        AkaishiMagicParticles.converge(level, source, inkId, SpellCarrierForm.ORB, ink,
                x, y, z, aimX, aimY, aimZ, 0.085d);
    }
}
