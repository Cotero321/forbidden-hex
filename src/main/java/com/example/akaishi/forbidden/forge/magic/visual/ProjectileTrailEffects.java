package com.example.akaishi.forbidden.forge.magic.visual;

import com.example.akaishi.forbidden.brewing.BrewingPotionData;
import com.example.akaishi.forbidden.brewing.BrewingPotionProjectile;
import com.example.akaishi.forbidden.forge.client.ForbiddenVisualConfig;
import com.example.akaishi.forbidden.magic.particle.SpellMoteOptions;
import com.example.akaishi.forbidden.magic.projectile.SpellProjectile;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.ParticleStatus;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ProjectileTrailEffects {
    public static final ProjectileTrailEffects INSTANCE = new ProjectileTrailEffects();
    private static final int BURST_TICKS = 16;

    private static final class Trail {
        private final Entity entity;
        private final VisualTrail path = new VisualTrail();
        private int coreRgb;
        private int auraRgb;
        private float width;
        private float alpha;
        private boolean brewing;

        private Trail(Entity entity) {
            this.entity = entity;
        }
    }

    private record Burst(Vec3 position, int rgb, long started) { }

    private final Map<UUID, Trail> trails = new LinkedHashMap<>();
    private final List<Burst> bursts = new ArrayList<>();
    private final MultiBufferSource.BufferSource buffers = MultiBufferSource.immediate(new BufferBuilder(8192));
    private ClientLevel world;

    private ProjectileTrailEffects() { }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        syncWorld(minecraft.level);
        if (world == null || minecraft.isPaused()) return;
        long now = world.getGameTime();
        int lifetime = trailLifetime();
        for (Trail trail : trails.values()) trail.path.prune(now, lifetime);
        trails.values().removeIf(trail -> trail.path.isEmpty()
                || !ForbiddenVisualConfig.PROJECTILE_TRAILS.get()
                || trail.brewing && !ForbiddenVisualConfig.BREWING_EFFECTS.get());
        bursts.removeIf(burst -> now - burst.started() >= BURST_TICKS);
        if (!ForbiddenVisualConfig.BREWING_EFFECTS.get()) bursts.clear();
        int limit = trailLimit();
        while (trails.size() > limit) trails.remove(trails.keySet().iterator().next());
        if (!ForbiddenVisualConfig.PROJECTILE_TRAILS.get() || limit == 0) return;
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        double range = ForbiddenVisualConfig.EFFECT_DISTANCE.get();
        for (Entity entity : world.entitiesForRendering()) {
            if (entity.isRemoved() || entity.position().distanceToSqr(camera) > range * range) continue;
            if (entity instanceof SpellProjectile projectile) {
                var visual = projectile.visual();
                if (visual == null || visual.effectId().isEmpty()) continue;
                observe(entity, visual.inkRgb(), visual.sourceRgb(),
                        Math.max(0.035f, Math.min(0.22f, visual.baseSize() * 0.13f)),
                        Math.min(0.85f, visual.brightness() * 0.65f), false, now, limit);
            } else if (entity instanceof BrewingPotionProjectile projectile && ForbiddenVisualConfig.BREWING_EFFECTS.get()) {
                BrewingPotionData.read(projectile.getItem()).filter(data -> !data.hideParticles()).ifPresent(data ->
                        observe(entity, data.effects().get(0).getEffect().getColor(), 0xCFEEE4,
                                0.035f, 0.45f, true, now, limit));
            }
        }
    }

    private void observe(Entity entity, int core, int aura, float width, float alpha,
            boolean brewing, long now, int limit) {
        Trail trail = trails.get(entity.getUUID());
        if (trail == null || trail.entity != entity) {
            if (trails.size() >= limit) return;
            trail = new Trail(entity);
            trails.put(entity.getUUID(), trail);
            trail.path.add(new Vec3(entity.xOld, entity.yOld, entity.zOld), now - 1, trailLifetime());
        }
        trail.coreRgb = core;
        trail.auraRgb = aura;
        trail.width = width;
        trail.alpha = alpha;
        trail.brewing = brewing;
        trail.path.add(entity.position(), now, trailLifetime());
    }

    public void shatter(BrewingPotionProjectile projectile) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(projectile.level() instanceof ClientLevel level) || level != minecraft.level
                || !ForbiddenVisualConfig.BREWING_EFFECTS.get()) return;
        syncWorld(level);
        BrewingPotionData.read(projectile.getItem()).filter(data -> !data.hideParticles()).ifPresent(data -> {
            Vec3 point = projectile.position();
            double range = ForbiddenVisualConfig.EFFECT_DISTANCE.get();
            if (point.distanceToSqr(minecraft.gameRenderer.getMainCamera().getPosition()) > range * range) return;
            int rgb = data.effects().get(0).getEffect().getColor();
            int limit = ForbiddenVisualConfig.MAX_BURSTS.get();
            if (limit > 0) {
                while (bursts.size() >= limit) bursts.remove(0);
                bursts.add(new Burst(point, rgb, level.getGameTime()));
            }
            int count = minecraft.options.particles().get() == ParticleStatus.MINIMAL ? 3 : 12;
            for (int i = 0; i < count && com.example.akaishi.forbidden.forge.client.visual.ClientVisualBudget.reserveBrewing(level, 1); i++) {
                double angle = level.random.nextDouble() * Math.PI * 2d;
                double speed = 0.035d + level.random.nextDouble() * 0.07d;
                level.addParticle(new SpellMoteOptions(1, rgb), point.x, point.y + 0.04d, point.z,
                        Math.cos(angle) * speed, 0.02d + level.random.nextDouble() * 0.04d, Math.sin(angle) * speed);
            }
        });
    }

    @SubscribeEvent
    public void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || world == null
                || world != Minecraft.getInstance().level) return;
        PoseStack pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        double now = world.getGameTime() + event.getPartialTick();
        if (ForbiddenVisualConfig.PROJECTILE_TRAILS.get()) {
            for (Trail trail : trails.values()) {
                if (!trail.brewing || ForbiddenVisualConfig.BREWING_EFFECTS.get()) {
                    paintTrail(pose, camera, trail, now, event.getPartialTick());
                }
            }
        }
        if (ForbiddenVisualConfig.BREWING_EFFECTS.get()) {
            double range = ForbiddenVisualConfig.EFFECT_DISTANCE.get();
            double rangeSquared = range * range;
            for (Burst burst : bursts) {
                float age = (float) (now - burst.started()) / BURST_TICKS;
                if (age < 0f || age >= 1f || burst.position().distanceToSqr(camera) > rangeSquared) continue;
                pose.pushPose();
                pose.translate(burst.position().x - camera.x, burst.position().y - camera.y + 0.035d,
                        burst.position().z - camera.z);
                SpellVisualPainter.flatQuad(buffers, pose, SpellVfx.BREWING_MIST, event.getCamera().rotation(),
                        0.2f + age * 0.65f, (1f - age) * (1f - age) * 0.35f, burst.rgb());
                pose.mulPose(Axis.XP.rotationDegrees(-90f));
                SpellVisualPainter.flatQuad(buffers, pose, SpellVfx.BREWING_RIPPLE, null,
                        0.15f + age * 1.7f, (1f - age) * (1f - age) * 0.6f, burst.rgb());
                pose.popPose();
            }
        }
        buffers.endBatch();
    }

    private void paintTrail(PoseStack pose, Vec3 camera, Trail trail, double now, float partialTick) {
        List<VisualTrail.Sample> history = trail.path.samples();
        if (history.size() < 2) return;
        List<Vec3> points = new ArrayList<>(history.size());
        List<Float> fades = new ArrayList<>(history.size());
        int lifetime = trailLifetime();
        Vec3 first = history.get(0).position();
        double range = ForbiddenVisualConfig.EFFECT_DISTANCE.get();
        boolean visible = false;
        for (VisualTrail.Sample sample : history) {
            double age = now - sample.tick();
            float fade = (float) Math.max(0d, 1d - age / lifetime);
            points.add(sample.position());
            fades.add(fade * fade);
            visible |= sample.position().distanceToSqr(camera) <= range * range;
        }
        if (!visible) return;
        if (!trail.entity.isRemoved() && world.getEntity(trail.entity.getId()) == trail.entity) {
            Vec3 head = trail.entity.getPosition(partialTick);
            int last = points.size() - 1;
            if (head.distanceToSqr(points.get(Math.max(0, last - 1))) < 64d) points.set(last, head);
        }
        List<Vec3> sides = new ArrayList<>(points.size());
        for (int i = 0; i < points.size(); i++) {
            Vec3 tangent = points.get(Math.min(points.size() - 1, i + 1))
                    .subtract(points.get(Math.max(0, i - 1))).normalize();
            Vec3 side = tangent.cross(camera.subtract(points.get(i))).normalize();
            if (side.lengthSqr() < 1.0e-8d) {
                side = tangent.cross(Math.abs(tangent.y) > 0.9d ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
            }
            if (!sides.isEmpty() && side.dot(sides.get(sides.size() - 1)) < 0d) side = side.scale(-1d);
            sides.add(side);
        }
        pose.pushPose();
        pose.translate(first.x - camera.x, first.y - camera.y, first.z - camera.z);
        Matrix4f matrix = pose.last().pose();
        VertexConsumer consumer = buffers.getBuffer(SpellRenderTypes.flow(SpellVfx.FILAMENT));
        for (int i = 1; i < points.size(); i++) {
            Vec3 from = points.get(i - 1);
            Vec3 to = points.get(i);
            float length = (float) from.distanceTo(to);
            if (length < 1.0e-4f) continue;
            float u0 = 0f;
            float u1 = 1f;
            if (SpellRenderTypes.flowEnabled()) {
                u0 = (float) ((history.get(i - 1).tick() % 80L) / 4d);
                u1 = u0 + (history.get(i).tick() - history.get(i - 1).tick()) / 4f;
            }
            strip(consumer, matrix, from.subtract(first), to.subtract(first), sides.get(i - 1), sides.get(i),
                    trail.width * 2.4f, fades.get(i - 1), fades.get(i), trail.alpha * 0.2f,
                    trail.auraRgb, u0, u1);
            strip(consumer, matrix, from.subtract(first), to.subtract(first), sides.get(i - 1), sides.get(i),
                    trail.width, fades.get(i - 1), fades.get(i), trail.alpha,
                    trail.coreRgb, u0, u1);
        }
        pose.popPose();
    }

    private static void strip(VertexConsumer consumer, Matrix4f matrix, Vec3 from, Vec3 to,
            Vec3 sideFrom, Vec3 sideTo, float width, float fadeFrom, float fadeTo,
            float alpha, int rgb, float u0, float u1) {
        Vec3 a = sideFrom.scale(width * Math.sqrt(fadeFrom));
        Vec3 b = sideTo.scale(width * Math.sqrt(fadeTo));
        point(consumer, matrix, from.subtract(a), u0, 0f, rgb, alpha * fadeFrom);
        point(consumer, matrix, from.add(a), u0, 1f, rgb, alpha * fadeFrom);
        point(consumer, matrix, to.add(b), u1, 1f, rgb, alpha * fadeTo);
        point(consumer, matrix, to.subtract(b), u1, 0f, rgb, alpha * fadeTo);
    }

    private static void point(VertexConsumer consumer, Matrix4f matrix, Vec3 point,
            float u, float v, int rgb, float alpha) {
        SpellGeometry.vertex(consumer, matrix, (float) point.x, (float) point.y, (float) point.z, u, v,
                SpellVisualPainter.red(rgb), SpellVisualPainter.green(rgb), SpellVisualPainter.blue(rgb), alpha);
    }

    private static int trailLifetime() {
        int ticks = ForbiddenVisualConfig.TRAIL_TICKS.get();
        return Minecraft.getInstance().options.particles().get() == ParticleStatus.MINIMAL ? Math.min(ticks, 4) : ticks;
    }

    private static int trailLimit() {
        int limit = ForbiddenVisualConfig.MAX_TRAILS.get();
        return Minecraft.getInstance().options.particles().get() == ParticleStatus.MINIMAL ? Math.min(limit, 16) : limit;
    }

    private void syncWorld(ClientLevel level) {
        if (world == level) return;
        trails.clear();
        bursts.clear();
        com.example.akaishi.forbidden.forge.client.visual.ClientVisualBudget.beginTick(level);
        world = level;
    }

    @SubscribeEvent
    public void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        syncWorld(null);
    }
}
