package com.example.akaishi.forbidden.forge.boss.agaitolos;

import com.example.akaishi.forbidden.forge.magic.visual.SpellGeometry;
import com.example.akaishi.forbidden.forge.magic.visual.SpellRenderTypes;
import com.example.akaishi.forbidden.forge.magic.visual.SpellVfx;
import com.example.akaishi.forbidden.forge.magic.visual.SpellVisualPainter;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/** 记录实际镰刃曲线而非身体朝向；瞬移、换招与时间倒退会断开轨迹。 */
final class AgaitolosBladeTrail {
    private record Frame(double time, List<Vec3> edge) { }
    private final List<Frame> frames = new ArrayList<>();
    private String action = "";
    private double lastAge = -1;
    private long actionStart = Long.MIN_VALUE;

    void observe(String nextAction, long nextStart, double age, double time, List<Vec3> edge, boolean active) {
        if (!Double.isFinite(age) || !Double.isFinite(time) || edge.size() < 2
                || edge.stream().anyMatch(point -> !Double.isFinite(point.lengthSqr()))) {
            frames.clear();
            return;
        }
        if (!nextAction.equals(action) || nextStart != actionStart || age < lastAge || !frames.isEmpty()
                && (time < frames.get(frames.size() - 1).time()
                || edge.size() != frames.get(frames.size() - 1).edge().size()
                || edge.get(0).distanceToSqr(frames.get(frames.size() - 1).edge().get(0)) > 16)) {
            frames.clear();
        }
        action = nextAction;
        actionStart = nextStart;
        lastAge = age;
        frames.removeIf(frame -> time - frame.time() > 4.0);
        if (!active) return;
        if (!frames.isEmpty() && time - frames.get(frames.size() - 1).time() < 0.12) return;
        frames.add(new Frame(time, List.copyOf(edge)));
        while (frames.size() > 24) frames.remove(0);
    }

    boolean expired(double now) {
        return frames.isEmpty() || !Double.isFinite(now)
                || now < frames.get(frames.size() - 1).time()
                || now - frames.get(frames.size() - 1).time() > 4.0;
    }

    void paint(PoseStack pose, MultiBufferSource buffers, Vec3 origin, double now, float detail) {
        if (frames.size() < 2) return;
        boolean heavy = action.equals("boss_execution_cleave");
        float strength = heavy ? 1f : 0.9f;
        Matrix4f matrix = pose.last().pose();
        VertexConsumer flow = buffers.getBuffer(SpellRenderTypes.flow(SpellVfx.FILAMENT));
        for (int i = 1; i < frames.size(); i++) {
            Frame before = frames.get(i - 1), after = frames.get(i);
            float life0 = fade(now - before.time()), life1 = fade(now - after.time());
            float a0 = life0 * strength * (0.18f + 0.56f * life0);
            float a1 = life1 * strength * (0.18f + 0.56f * life1);
            if (a0 <= 0f && a1 <= 0f) continue;
            for (int j = 1; j < after.edge().size(); j++) {
                float u0 = (j - 1f) / (after.edge().size() - 1f), u1 = j / (after.edge().size() - 1f);
                vertex(flow, matrix, before.edge().get(j - 1).subtract(origin), u0, life0, tint(life0), a0);
                vertex(flow, matrix, before.edge().get(j).subtract(origin), u1, life0, tint(life0), a0);
                vertex(flow, matrix, after.edge().get(j).subtract(origin), u1, life1, tint(life1), a1);
                vertex(flow, matrix, after.edge().get(j - 1).subtract(origin), u0, life1, tint(life1), a1);
            }
        }
        Frame head = frames.get(frames.size() - 1), before = frames.get(frames.size() - 2);
        float alpha = fade(now - head.time()) * strength;
        VertexConsumer core = buffers.getBuffer(SpellGeometry.energyType());
        for (int j = 1; j < head.edge().size(); j++) {
            Vec3 a = head.edge().get(j - 1), b = head.edge().get(j);
            double width = heavy ? 0.11 : 0.06;
            Vec3 directionA = before.edge().get(j - 1).subtract(a).normalize();
            Vec3 directionB = before.edge().get(j).subtract(b).normalize();
            Vec3 backA = directionA.scale(width);
            Vec3 backB = directionB.scale(width);
            if (heavy && detail > 0.5f) {
                vertex(core, matrix, a.add(backA).subtract(origin), 0, 0, AgaitolosEnergyVisuals.AURA, alpha * 0.24f);
                vertex(core, matrix, b.add(backB).subtract(origin), 1, 0, AgaitolosEnergyVisuals.AURA, alpha * 0.24f);
                vertex(core, matrix, b.add(directionB.scale(0.24)).subtract(origin), 1, 1, AgaitolosEnergyVisuals.AURA, 0f);
                vertex(core, matrix, a.add(directionA.scale(0.24)).subtract(origin), 0, 1, AgaitolosEnergyVisuals.AURA, 0f);
            }
            vertex(core, matrix, a.subtract(origin), 0, 0, AgaitolosEnergyVisuals.CORE, alpha * 0.85f);
            vertex(core, matrix, b.subtract(origin), 1, 0, AgaitolosEnergyVisuals.CORE, alpha * 0.85f);
            vertex(core, matrix, b.add(backB).subtract(origin), 1, 1, AgaitolosEnergyVisuals.GOLD, 0f);
            vertex(core, matrix, a.add(backA).subtract(origin), 0, 1, AgaitolosEnergyVisuals.GOLD, 0f);
        }
    }

    private static int tint(float life) {
        int rgb = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int from = AgaitolosEnergyVisuals.AURA >> shift & 255;
            int to = AgaitolosEnergyVisuals.GOLD >> shift & 255;
            rgb |= Math.round(from + (to - from) * life) << shift;
        }
        return rgb;
    }

    private static float fade(double age) {
        float value = (float) Math.max(0, Math.min(1, 1 - age / 4.0));
        return value * value;
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Vec3 point,
                               float u, float v, int rgb, float alpha) {
        SpellGeometry.vertex(consumer, matrix, (float) point.x, (float) point.y, (float) point.z, u, v,
                SpellVisualPainter.red(rgb), SpellVisualPainter.green(rgb), SpellVisualPainter.blue(rgb), alpha);
    }
}
