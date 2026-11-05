package com.example.akaishi.forbidden.forge.boss.agaitolos;

import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosEntity;
import com.example.akaishi.forbidden.forge.client.ForbiddenVisualConfig;
import com.example.akaishi.forbidden.forge.client.visual.ClientVisualBudget;
import com.example.akaishi.forbidden.forge.magic.visual.SpellVfx;
import com.example.akaishi.forbidden.forge.magic.visual.SpellVisualPainter;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

public class AgaitolosRenderer extends GeoEntityRenderer<AgaitolosEntity> {
    // 由最终模型 FH_RoyalBlade 的刃口方块 8..14 与尖端 7 计算，已包含块级旋转和 Bedrock X 镜像。
    private static final Vec3[] EDGE = {
            new Vec3(0.1135165, -0.0542102, 0), new Vec3(0.3527716, -0.0326990, 0),
            new Vec3(0.5810212, -0.0492930, 0), new Vec3(0.7955627, -0.0953868, 0),
            new Vec3(0.9846183, -0.1785221, 0), new Vec3(1.1246589, -0.2800560, 0),
            new Vec3(1.2179987, -0.3905706, 0), new Vec3(1.2578125, -0.4812500, 0)
    };
    private final Map<AgaitolosEntity, AgaitolosBladeTrail> trails = new WeakHashMap<>();
    private ClientLevel world;

    public AgaitolosRenderer(EntityRendererProvider.Context context) {
        super(context, new AgaitolosModel());
        addRenderLayer(new com.example.akaishi.forbidden.brewing.client.BrewingGeoTintLayer<>(this));
        shadowRadius = 0.65f;
    }

    @Override
    public void preRender(PoseStack pose, AgaitolosEntity boss, BakedGeoModel model,
                          MultiBufferSource buffers, VertexConsumer buffer, boolean reRender,
                          float partialTick, int light, int overlay, float r, float g, float b, float alpha) {
        getGeoModel().getBone("FH_RoyalBlade").ifPresent(bone -> bone.setTrackingMatrices(true));
        getGeoModel().getBone("LeftHandLocator").ifPresent(bone -> bone.setTrackingMatrices(true));
        getGeoModel().getBone("RightHandLocator").ifPresent(bone -> bone.setTrackingMatrices(true));
        getGeoModel().getBone("LeftFoot").ifPresent(bone -> bone.setTrackingMatrices(true));
        getGeoModel().getBone("RightFoot").ifPresent(bone -> bone.setTrackingMatrices(true));
        super.preRender(pose, boss, model, buffers, buffer, reRender, partialTick, light, overlay, r, g, b, alpha);
    }

    @Override
    public void renderFinal(PoseStack pose, AgaitolosEntity boss, BakedGeoModel model,
                            MultiBufferSource buffers, VertexConsumer buffer, float partialTick,
                            int light, int overlay, float r, float g, float b, float alpha) {
        super.renderFinal(pose, boss, model, buffers, buffer, partialTick, light, overlay, r, g, b, alpha);
        ClientLevel level = Minecraft.getInstance().level;
        if (world != level) {
            trails.clear();
            world = level;
        }
        if (level == null || boss.isInvisible() || boss.isDeadOrDying()) {
            trails.remove(boss);
            return;
        }
        Vec3 origin = boss.getPosition(partialTick);
        float detail = ClientVisualBudget.detailScale(origin.distanceToSqr(entityRenderDispatcher.camera.getPosition()));
        if (detail <= 0f) {
            trails.remove(boss);
            return;
        }
        String action = boss.getVisualAction();
        double age = boss.getVisualActionAge(partialTick);
        double now = level.getGameTime() + partialTick;
        if (ForbiddenVisualConfig.PROJECTILE_TRAILS.get() && ForbiddenVisualConfig.MAX_TRAILS.get() > 0) {
            trails.entrySet().removeIf(entry -> !entry.getKey().isAlive() || entry.getKey().isInvisible()
                    || entry.getValue().expired(now));
            getGeoModel().getBone("FH_RoyalBlade").ifPresent(bone -> {
                boolean active = bladeWindow(action, age);
                int limit = Math.min(16, ForbiddenVisualConfig.MAX_TRAILS.get());
                AgaitolosBladeTrail trail = trails.get(boss);
                if (trail == null) {
                    if (!active || trails.size() >= limit) return;
                    trail = new AgaitolosBladeTrail();
                    trails.put(boss, trail);
                }
                var edge = new ArrayList<Vec3>(EDGE.length);
                for (Vec3 point : EDGE) edge.add(worldPoint(bone, point, boss, partialTick));
                trail.observe(action, boss.getVisualActionStart(), age, now, edge, active);
                trail.paint(pose, buffers, origin, now, detail);
            });
        } else {
            trails.clear();
        }
        paintCasting(pose, buffers, boss, origin, partialTick, action, age, now, detail);
        paintActionReadability(pose, buffers, boss, origin, partialTick, action, age, now, detail);
    }

    /** 用低成本、低 detail 仍保留核心的局部信号强化快踢和瞬击。 */
    private void paintActionReadability(PoseStack pose, MultiBufferSource buffers, AgaitolosEntity boss,
                                        Vec3 origin, float partialTick, String action, double age,
                                        double now, float detail) {
        if (action.equals("boss_rapid_kick")) {
            GeoBone foot = getGeoModel().getBone("RightFoot").orElse(null);
            if (foot == null || !kickWindow(age)) return;
            Vec3 point = worldPoint(foot, Vec3.ZERO, boss, partialTick);
            float progress = (float) Math.max(0, Math.min(1, (age - 5) / 5.0));
            float fade = age > 12 ? (float) Math.max(0, 1 - (age - 12) / 4.0) : 1f;
            pose.pushPose();
            Vec3 local = point.subtract(origin);
            pose.translate(local.x, local.y + 0.08, local.z);
            AgaitolosEnergyVisuals.orb(pose, buffers, entityRenderDispatcher.cameraOrientation(),
                    0.16f + 0.16f * progress, (float) now / 14f, 0.78f * fade, detail);
            SpellVisualPainter.flatQuad(buffers, pose, SpellVfx.RING_PULSE,
                    entityRenderDispatcher.cameraOrientation(), 0.34f + 0.18f * progress,
                    0.58f * fade, AgaitolosEnergyVisuals.GOLD);
            pose.popPose();
            return;
        }
        if (action.equals("boss_blink_strike")) {
            if (!blinkReadabilityWindow(age)) return;
            float fade;
            if (age < 7) {
                fade = (float) Math.max(0, 1 - age / 7.0);
            } else if (age >= 14 && age <= 22) {
                fade = (float) Math.max(0, 1 - Math.abs(age - 17) / 5.0);
            } else {
                return;
            }
            pose.pushPose();
            pose.translate(0, 0.92f, 0);
            SpellVisualPainter.flatQuad(buffers, pose, SpellVfx.WISP,
                    entityRenderDispatcher.cameraOrientation(), 0.55f + 0.28f * fade,
                    0.42f * fade, AgaitolosEnergyVisuals.AURA);
            SpellVisualPainter.flatQuad(buffers, pose, SpellVfx.RING_PULSE,
                    entityRenderDispatcher.cameraOrientation(), 0.42f + 0.22f * fade,
                    0.56f * fade, AgaitolosEnergyVisuals.GOLD);
            pose.popPose();
        }
    }

    private void paintCasting(PoseStack pose, MultiBufferSource buffers, AgaitolosEntity boss, Vec3 origin,
                              float partialTick, String action, double age, double now, float detail) {
        float radius = 0f;
        float releaseGlow = 0f;
        boolean bothHands = false;
        switch (action) {
            case "boss_curse_reverse" -> {
                radius = 0.16f + 0.36f * (float) Math.min(1, age / 16.0);
                if (age > 216) radius *= (float) Math.max(0, (240 - age) / 24.0);
                bothHands = true;
            }
            case "boss_calamity" -> {
                radius = age < 10 ? 0.2f + (float) age * 0.04f
                        : 0.6f * (float) Math.max(0, 1 - (age - 10) / 7.0);
                bothHands = true;
            }
            case "boss_curse_break" -> {
                radius = age < 8 ? 0.18f + 0.05f * (float) age
                        : 0.58f * (float) Math.max(0, 1 - (age - 8) / 8.0);
                bothHands = true;
            }
            case "boss_summon_throw" -> {
                radius = age < 20 ? 0.13f + 0.25f * (float) Math.min(1, age / 16.0) : 0f;
                if (age >= 20 && age < 23) releaseGlow = (float) (1 - (age - 20) / 3);
            }
            case "boss_triple_throw" -> {
                double phase = age <= 6 ? age / 6 : age <= 12 ? (age - 6) / 6 : age <= 18 ? (age - 12) / 6 : 0;
                radius = phase > 0 ? 0.09f + (float) phase * 0.16f : 0;
            }
            case "boss_saturation_barrage" -> radius = 0.12f + 0.08f * (float) Math.sin(age * Math.PI / 20);
            default -> { }
        }
        GeoBone left = getGeoModel().getBone("LeftHandLocator").orElse(null);
        if (left == null) return;
        Vec3 point = worldPoint(left, Vec3.ZERO, boss, partialTick);
        if (bothHands) {
            GeoBone right = getGeoModel().getBone("RightHandLocator").orElse(null);
            if (right != null) point = point.add(worldPoint(right, Vec3.ZERO, boss, partialTick)).scale(0.5);
        }
        pose.pushPose();
        Vec3 local = point.subtract(origin);
        pose.translate(local.x, local.y + 0.08, local.z);
        if (action.equals("boss_block")) {
            SpellVisualPainter.flatQuad(buffers, pose, SpellVfx.RUNE, entityRenderDispatcher.cameraOrientation(),
                    0.38f, 0.28f, AgaitolosEnergyVisuals.GOLD);
        } else if (radius > 0.02f) {
            AgaitolosEnergyVisuals.orb(pose, buffers, entityRenderDispatcher.cameraOrientation(),
                    radius, (float) now / 20f, 0.85f, detail);
        }
        if (releaseGlow > 0f) {
            float alpha = releaseGlow * releaseGlow;
            SpellVisualPainter.flatQuad(buffers, pose, SpellVfx.RING_PULSE, entityRenderDispatcher.cameraOrientation(),
                    0.36f + 0.22f * (1 - releaseGlow), 0.42f * alpha, AgaitolosEnergyVisuals.GOLD);
            SpellVisualPainter.flatQuad(buffers, pose, SpellVfx.GLOW, entityRenderDispatcher.cameraOrientation(),
                    0.12f, 0.7f * alpha, AgaitolosEnergyVisuals.CORE);
        }
        pose.popPose();
    }

    private static Vec3 worldPoint(GeoBone bone, Vec3 local, AgaitolosEntity boss, float partialTick) {
        Vector3f point = bone.getLocalSpaceMatrix().transformPosition((float) local.x, (float) local.y,
                (float) local.z, new Vector3f());
        return boss.getPosition(partialTick).add(point.x, point.y, point.z);
    }

    static boolean bladeWindow(String action, double age) {
        return switch (action) {
            case "boss_slash" -> age >= 9.39 && age <= 14.14;
            case "boss_dive_sweep" -> age >= 14 && age <= 22;
            case "boss_blink_strike" -> age >= 14 && age <= 21;
            case "boss_stomp_cleave" -> age >= 8 && age <= 14;
            case "boss_execution_cleave" -> age >= 10 && age <= 18;
            default -> false;
        };
    }

    static boolean kickWindow(double age) {
        return age >= 5 && age <= 16;
    }

    static boolean blinkReadabilityWindow(double age) {
        return age < 7 || age >= 14 && age <= 22;
    }
}
