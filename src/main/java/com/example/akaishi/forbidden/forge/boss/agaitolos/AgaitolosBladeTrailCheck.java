package com.example.akaishi.forbidden.forge.boss.agaitolos;

import com.example.akaishi.forbidden.forge.magic.visual.SpellGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Client-only history checks, invoked by the explicit visual smoke test. */
public final class AgaitolosBladeTrailCheck {
    private AgaitolosBladeTrailCheck() { }

    public static void verify() {
        List<Vec3> edge = List.of(Vec3.ZERO, new Vec3(1, 0, 0));
        AgaitolosBladeTrail trail = new AgaitolosBladeTrail();
        trail.observe("boss_slash", 0, 10, 10, edge, false);
        require(trail.expired(10), "Idle pose retained a blade trail slot");
        trail.observe("boss_slash", 0, 10, 10, edge, true);
        require(!trail.expired(14), "Blade trail expired before the four-tick boundary");
        require(trail.expired(14.001), "Blade trail did not release its slot after fading");
        trail.observe("boss_slash", 1, 11, 11, edge, false);
        require(trail.expired(11), "Restarted slash retained the previous action history");
        trail.observe("boss_slash", 1, 12, 12, edge, true);
        trail.observe("boss_execution_cleave", 1, 13, 13, edge, false);
        require(trail.expired(13), "Changed action retained slash history");
        trail.observe("boss_slash", 2, 14, 14, edge, true);
        trail.observe("boss_slash", 2, 15, 15, List.of(new Vec3(Double.NaN, 0, 0), Vec3.ZERO), true);
        require(trail.expired(15), "Invalid blade coordinates retained drawable history");
        trail.observe("boss_slash", 2, 16, 16, edge, true);
        trail.observe("boss_slash", 2, 17, Double.NaN, edge, true);
        require(trail.expired(17), "Invalid clock retained drawable history");
        trail.observe("boss_slash", 2, 18, 18, edge, true);
        trail.observe("boss_slash", 2, 19, 19, List.of(), true);
        require(trail.expired(19), "Missing blade samples retained history");
        trail.observe("boss_slash", 2, 20, 20, edge, true);
        trail.observe("boss_slash", 2, 21, 21, List.of(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO), false);
        require(trail.expired(21), "Changed edge topology retained incompatible history");
        trail.observe("boss_slash", 2, 22, 22, edge, true);
        trail.observe("boss_slash", 2, 21, 21, edge, false);
        require(trail.expired(21), "Clock rollback retained future samples");
        trail.observe("boss_slash", 2, 23, 23, edge, true);
        List<Vec3> distant = List.of(new Vec3(10, 0, 0), new Vec3(11, 0, 0));
        trail.observe("boss_slash", 2, 24, 24, distant, false);
        require(trail.expired(24), "Large position discontinuity retained history");
        require(AgaitolosRenderer.bladeWindow("boss_slash", 12), "Slash hit frame has no blade window");
        require(!AgaitolosRenderer.bladeWindow("boss_idle", 12), "Idle pose emits a blade trail");
        require(!AgaitolosRenderer.bladeWindow("boss_slash", 20), "Slash recovery emits a new blade trail");
        require(AgaitolosRenderer.kickWindow(10), "Kick impact frame has no readability window");
        require(!AgaitolosRenderer.kickWindow(20), "Kick recovery retained its foot readability effect");
        require(AgaitolosRenderer.blinkReadabilityWindow(3), "Blink departure lost its readability pulse");
        require(AgaitolosRenderer.blinkReadabilityWindow(17), "Blink arrival lost its readability pulse");
        require(!AgaitolosRenderer.blinkReadabilityWindow(10), "Blink gap emitted a continuous aura");
        Probe slash = paintCore("boss_slash", 0.25f);
        Probe heavy = paintCore("boss_execution_cleave", 0.25f);
        require(slash.vertices == 4 && heavy.vertices == 4, "Low-detail Boss blade lost its core");
        require(0.5 - heavy.minZ > (0.5 - slash.minZ) * 1.5, "Heavy blade lost its width contrast");
        Probe layered = paintCore("boss_execution_cleave", 1f);
        require(layered.vertices == 8 && layered.minZ < heavy.minZ, "Heavy blade lost its separate violet wake");
    }

    private static Probe paintCore(String clip, float detail) {
        AgaitolosBladeTrail trail = new AgaitolosBladeTrail();
        trail.observe(clip, 0, 10, 10, List.of(Vec3.ZERO, new Vec3(1, 0, 0)), true);
        trail.observe(clip, 0, 11, 11, List.of(new Vec3(0, 0, 0.5), new Vec3(1, 0, 0.5)), true);
        Probe core = new Probe(), flow = new Probe();
        trail.paint(new PoseStack(), type -> type == SpellGeometry.energyType() ? core : flow, Vec3.ZERO, 11, detail);
        return core;
    }

    private static final class Probe implements VertexConsumer {
        private int vertices;
        private double minZ = Double.POSITIVE_INFINITY;
        @Override public VertexConsumer vertex(double x, double y, double z) {
            require(Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z), "Invalid blade vertex");
            minZ = Math.min(minZ, z);
            return this;
        }
        @Override public VertexConsumer color(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer uv(float u, float v) { return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { return this; }
        @Override public VertexConsumer uv2(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { return this; }
        @Override public void endVertex() { vertices++; }
        @Override public void defaultColor(int r, int g, int b, int a) { }
        @Override public void unsetDefaultColor() { }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
