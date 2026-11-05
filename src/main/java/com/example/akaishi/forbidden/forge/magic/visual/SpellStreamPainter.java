package com.example.akaishi.forbidden.forge.magic.visual;

import com.example.akaishi.forbidden.magic.table.MagicSource;
import com.example.akaishi.forbidden.magic.visual.SpellInkMaterial;
import com.example.akaishi.forbidden.magic.visual.SpellVisual;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

/** 束流和连接共享源结构，端点始终使用实际施法位置。 */
public final class SpellStreamPainter {
    private SpellStreamPainter() { }

    public static void paint(PoseStack pose, MultiBufferSource buffers, SpellVisual visual,
            Vec3 line, float time, float alpha, float detail) {
        double length = line.length();
        if (!Double.isFinite(length) || length < 0.002d || !Float.isFinite(time)
                || !Float.isFinite(alpha) || detail <= 0f || alpha <= 0.01f) return;
        MagicSource source = MagicSource.byIdOrDefault(visual.sourceId());
        SpellInkMaterial material = SpellInkMaterial.get(visual.inkId());
        Vec3 axis = line.scale(1d / length);
        Vec3 side = axis.cross(Math.abs(axis.y) > 0.9d ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        Vec3 up = axis.cross(side).normalize();
        int steps = Math.max(4, Math.min(detail > 0.72f ? 28 : 16, (int) Math.ceil(length * 2d)));
        int strands = detail > 0.5f ? 2 : 1;
        float width = Math.min(0.09f, 0.026f + visual.baseSize() * 0.018f) * material.trailWidth();
        float clock = time % 3600f * material.flowSpeed();
        int rim = material.rimRgb(visual.inkRgb());
        for (int pass = 0; pass < 2; pass++) {
            boolean glow = pass == 1;
            var consumer = buffers.getBuffer(SpellRenderTypes.volume(glow));
            for (int strand = 0; strand < strands; strand++) {
                Vec3[] centers = new Vec3[steps + 1];
                Vec3[] sides = new Vec3[steps + 1];
                Vec3[] ups = new Vec3[steps + 1];
                float[] radii = new float[steps + 1];
                float[] opacities = new float[steps + 1];
                for (int i = 0; i <= steps; i++) {
                    float t = (float) i / steps;
                    float envelope = (float) Math.sin(t * Math.PI);
                    double phase = t * Math.PI * 5d - clock * 3d + strand * Math.PI;
                    double spread = switch (source) {
                        case SAN -> Math.sin(i * 13.17d + Math.floor(clock * 6f)) * 0.09d;
                        case LIFE -> 0.035d + 0.02d * Math.sin(clock * 3f - t * 12f);
                        case DARK -> 0.09d * (1d - t * 0.65d);
                        case WHITE -> 0.035d;
                    };
                    spread *= envelope * (0.7d + material.turbulence() * 3d);
                    Vec3 next = line.scale(t).add(side.scale(Math.cos(phase) * spread))
                            .add(up.scale((source == MagicSource.WHITE ? 0d : Math.sin(phase)) * spread));
                    Vec3 section = source == MagicSource.DARK
                            ? side.scale(Math.cos(phase)).add(up.scale(Math.sin(phase))) : side;
                    centers[i] = next;
                    sides[i] = section;
                    ups[i] = axis.cross(section).normalize();
                    radii[i] = width * (strand == 0 ? 1f : 0.42f) * (0.65f + envelope * 0.35f);
                    opacities[i] = alpha * 0.82f * (0.86f + 0.14f * (float) Math.sin(clock * 5f - t * 16f));
                }
                for (int i = 1; i <= steps; i++) {
                    if (source == MagicSource.SAN && i % 4 == 0) continue;
                    SpellVolumeGeometry.tube(consumer, pose, centers[i - 1], centers[i],
                            sides[i - 1], sides[i], ups[i - 1], ups[i], radii[i - 1], radii[i],
                            source == MagicSource.DARK ? 0.35f : 0.8f, opacities[i - 1], opacities[i],
                            visual.inkRgb(), rim, glow, i == 1 || source == MagicSource.SAN && (i - 1) % 4 == 0,
                            i == steps || source == MagicSource.SAN && (i + 1) % 4 == 0);
                }
            }
        }
        if (detail > 0.72f) {
            int nodes = Math.min(2, Math.max(1, (int) (length / 4d)));
            for (int i = 0; i < nodes; i++) {
                float t = (clock * 0.3f + (float) i / nodes) % 1f;
                pose.pushPose();
                pose.translate(line.x * t, line.y * t, line.z * t);
                SpellBodyPainter.paint(pose, buffers, source, visual.inkId(), visual.inkRgb(), time,
                        width * 2.5f, alpha * 0.55f * (float) Math.sin(t * Math.PI),
                        0.5f, SpellBodyPainter.Stage.SUSTAIN, 0f);
                pose.popPose();
            }
        }
    }
}
