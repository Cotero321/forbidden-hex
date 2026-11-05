package com.example.akaishi.forbidden.forge.magic.visual;

import com.example.akaishi.forbidden.magic.visual.SpellInkMaterial;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** 法术暗体使用闭合表面与真实法线，亮纹单独绘制，不改变法阵的平面零件。 */
public final class SpellVolumeGeometry {
    private SpellVolumeGeometry() { }

    public static void crystal(VertexConsumer consumer, PoseStack pose, float radius, float height,
            float alpha, int rgb, int rim, boolean glow) {
        Vec3 top = new Vec3(0, height, 0);
        Vec3 bottom = new Vec3(0, -height, 0);
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI * 0.5d;
            double b = (i + 1) * Math.PI * 0.5d;
            Vec3 p = new Vec3(Math.cos(a) * radius, 0, Math.sin(a) * radius);
            Vec3 q = new Vec3(Math.cos(b) * radius, 0, Math.sin(b) * radius);
            if (glow) {
                if (i % 2 == 0) {
                    seam(consumer, pose, top, p, q, radius * 0.075f, alpha * 0.72f, rim);
                    seam(consumer, pose, bottom, q, p, radius * 0.055f, alpha * 0.45f, rgb);
                }
            } else {
                face(consumer, pose, top, q, p, p, rgb, alpha, false);
                face(consumer, pose, bottom, p, q, q, rgb, alpha, false);
            }
        }
    }

    private static void seam(VertexConsumer consumer, PoseStack pose, Vec3 from, Vec3 to, Vec3 inside,
            float width, float alpha, int rgb) {
        Vec3 normal = inside.subtract(from).cross(to.subtract(from)).normalize();
        double distance = inside.distanceTo(from.add(to).scale(0.5d));
        double inset = Math.min(0.18d, width / Math.max(distance, 1.0e-6d));
        Vec3 lift = normal.scale(width * 0.08d);
        face(consumer, pose, from.add(lift), from.lerp(inside, inset).add(lift),
                to.lerp(inside, inset).add(lift), to.add(lift), rgb, alpha, true);
    }

    public static void ellipsoid(VertexConsumer consumer, PoseStack pose, float radius, float height,
            float alpha, int rgb, int rim, int segments, int rows, float time, boolean glow) {
        if (glow) {
            for (int stripe = 0; stripe < 2; stripe++) {
                int segment = stripe * segments / 2;
                float u0 = (float) segment / segments;
                float u1 = (float) (segment + 1) / segments;
                for (int row = 0; row < rows; row++) {
                    float v0 = (float) row / rows;
                    float v1 = (float) (row + 1) / rows;
                    Vec3 a = sphere(u0, v0, radius, height, time);
                    Vec3 b = sphere(u1, v0, radius, height, time);
                    Vec3 c = sphere(u1, v1, radius, height, time);
                    Vec3 d = sphere(u0, v1, radius, height, time);
                    Vec3 lift = a.add(b).add(c).add(d).normalize().scale(radius * 0.002d);
                    Vec3 diagonal = a.lerp(c, 0.10d).add(lift);
                    float pulse = 0.52f + 0.11f * Mth.sin(time * 2.2f - v0 * Mth.TWO_PI + stripe);
                    if (row > 0) face(consumer, pose, a.add(lift), diagonal, a.lerp(b, 0.10d).add(lift),
                            a.lerp(b, 0.10d).add(lift), rim, alpha * pulse, true);
                    if (row < rows - 1) face(consumer, pose, a.add(lift), d.add(lift), d.lerp(c, 0.10d).add(lift), diagonal,
                            rim, alpha * pulse, true);
                }
            }
            return;
        }
        for (int row = 0; row < rows; row++) {
            for (int i = 0; i < segments; i++) {
                float u0 = (float) i / segments;
                float u1 = (float) (i + 1) / segments;
                float v0 = (float) row / rows;
                float v1 = (float) (row + 1) / rows;
                smoothVertex(consumer, pose, sphere(u0, v0, radius, height, time), radius, height, rgb, alpha);
                smoothVertex(consumer, pose, sphere(u0, v1, radius, height, time), radius, height, rgb, alpha);
                smoothVertex(consumer, pose, sphere(u1, v1, radius, height, time), radius, height, rgb, alpha);
                smoothVertex(consumer, pose, sphere(u1, v0, radius, height, time), radius, height, rgb, alpha);
            }
        }
    }

    private static Vec3 sphere(float u, float v, float radius, float height, float time) {
        double longitude = u * Math.PI * 2d;
        double latitude = (v - 0.5d) * Math.PI;
        double ridge = 1d + 0.035d * Math.sin(longitude * 3d + latitude * 2d - time * 2d);
        return new Vec3(Math.cos(latitude) * Math.cos(longitude) * radius * ridge,
                Math.sin(latitude) * height, Math.cos(latitude) * Math.sin(longitude) * radius * ridge);
    }

    private static void smoothVertex(VertexConsumer consumer, PoseStack pose, Vec3 position,
            float radius, float height, int rgb, float alpha) {
        Vec3 normal = new Vec3(position.x / (radius * radius), position.y / (height * height),
                position.z / (radius * radius)).normalize();
        vertex(consumer, pose, position, normal, rgb, alpha, false);
    }

    public static void tube(VertexConsumer consumer, PoseStack pose, Vec3 from, Vec3 to,
            Vec3 sideFrom, Vec3 sideTo, float widthFrom, float widthTo, float thickness,
            float alphaFrom, float alphaTo, int rgb, int rim, boolean glow, boolean capFrom, boolean capTo) {
        Vec3 axis = to.subtract(from).normalize();
        if (axis.lengthSqr() < 1.0e-8d || !Double.isFinite(axis.lengthSqr())) return;
        tube(consumer, pose, from, to, sideFrom, sideTo, axis.cross(sideFrom).normalize(),
                axis.cross(sideTo).normalize(), widthFrom, widthTo, thickness,
                alphaFrom, alphaTo, rgb, rim, glow, capFrom, capTo);
    }

    /** 连续曲线两侧共用采样点截面，不能按每段的方向重新计算接缝。 */
    public static void tube(VertexConsumer consumer, PoseStack pose, Vec3 from, Vec3 to,
            Vec3 sideFrom, Vec3 sideTo, Vec3 upFrom, Vec3 upTo, float widthFrom, float widthTo, float thickness,
            float alphaFrom, float alphaTo, int rgb, int rim, boolean glow, boolean capFrom, boolean capTo) {
        double length = from.distanceToSqr(to);
        if (!Double.isFinite(length) || length < 1.0e-8d || upFrom.lengthSqr() < 1.0e-8d
                || upTo.lengthSqr() < 1.0e-8d || widthFrom < 0f || widthTo < 0f
                || !Float.isFinite(widthFrom) || !Float.isFinite(widthTo)) return;
        if (glow) {
            Vec3 a = from.add(sideFrom.scale(widthFrom * 0.35d)).add(upFrom.scale(widthFrom * thickness * 1.015d));
            Vec3 b = a.add(sideFrom.scale(widthFrom * 0.10d));
            Vec3 d = to.add(sideTo.scale(widthTo * 0.35d)).add(upTo.scale(widthTo * thickness * 1.015d));
            Vec3 c = d.add(sideTo.scale(widthTo * 0.10d));
            Vec3 normal = faceNormal(a, d, c, b);
            vertex(consumer, pose, a, normal, rim, alphaFrom * 0.72f, true);
            vertex(consumer, pose, d, normal, rim, alphaTo * 0.72f, true);
            vertex(consumer, pose, c, normal, rim, alphaTo * 0.72f, true);
            vertex(consumer, pose, b, normal, rim, alphaFrom * 0.72f, true);
            return;
        }
        for (int i = 0; i < 4; i++) {
            Vec3 a = corner(from, sideFrom, upFrom, widthFrom, thickness, i);
            Vec3 b = corner(from, sideFrom, upFrom, widthFrom, thickness, (i + 1) % 4);
            Vec3 c = corner(to, sideTo, upTo, widthTo, thickness, (i + 1) % 4);
            Vec3 d = corner(to, sideTo, upTo, widthTo, thickness, i);
            Vec3 normal = faceNormal(a, b, c, d);
            vertex(consumer, pose, a, normal, rgb, alphaFrom, false);
            vertex(consumer, pose, b, normal, rgb, alphaFrom, false);
            vertex(consumer, pose, c, normal, rgb, alphaTo, false);
            vertex(consumer, pose, d, normal, rgb, alphaTo, false);
        }
        if (capFrom && widthFrom > 0f) face(consumer, pose, corner(from, sideFrom, upFrom, widthFrom, thickness, 3),
                corner(from, sideFrom, upFrom, widthFrom, thickness, 2),
                corner(from, sideFrom, upFrom, widthFrom, thickness, 1),
                corner(from, sideFrom, upFrom, widthFrom, thickness, 0), rgb, alphaFrom, false);
        if (capTo && widthTo > 0f) face(consumer, pose, corner(to, sideTo, upTo, widthTo, thickness, 0),
                corner(to, sideTo, upTo, widthTo, thickness, 1),
                corner(to, sideTo, upTo, widthTo, thickness, 2),
                corner(to, sideTo, upTo, widthTo, thickness, 3), rgb, alphaTo, false);
    }

    private static Vec3 corner(Vec3 center, Vec3 side, Vec3 up, float width, float thickness, int index) {
        return center.add(side.scale((index == 0 || index == 3 ? -1d : 1d) * width))
                .add(up.scale((index < 2 ? -1d : 1d) * width * thickness));
    }

    private static Vec3 faceNormal(Vec3 a, Vec3 b, Vec3 c, Vec3 d) {
        Vec3 normal = b.subtract(a).cross(c.subtract(a));
        if (normal.lengthSqr() < 1.0e-20d) normal = c.subtract(a).cross(d.subtract(a));
        return normal.lengthSqr() < 1.0e-20d ? new Vec3(0, 1, 0) : normal.scale(1d / Math.sqrt(normal.lengthSqr()));
    }

    private static void face(VertexConsumer consumer, PoseStack pose, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
            int rgb, float alpha, boolean glow) {
        Vec3 normal = faceNormal(a, b, c, d);
        vertex(consumer, pose, a, normal, rgb, alpha, glow);
        vertex(consumer, pose, b, normal, rgb, alpha, glow);
        vertex(consumer, pose, c, normal, rgb, alpha, glow);
        vertex(consumer, pose, d, normal, rgb, alpha, glow);
    }

    private static void vertex(VertexConsumer consumer, PoseStack pose, Vec3 position, Vec3 normal,
            int rgb, float alpha, boolean glow) {
        Vector3f transformed = new Vector3f((float) normal.x, (float) normal.y, (float) normal.z)
                .mul(pose.last().normal()).normalize();
        float diffuse = Math.max(0f, transformed.dot(-0.35f, 0.8f, 0.48f));
        float shade = glow ? 1f : 0.26f + diffuse * 0.56f;
        int color = glow ? rgb : SpellInkMaterial.mix(0x03060D, rgb, shade);
        consumer.vertex(pose.last().pose(), (float) position.x, (float) position.y, (float) position.z)
                .color(SpellVisualPainter.red(color), SpellVisualPainter.green(color), SpellVisualPainter.blue(color),
                        Mth.clamp(alpha * (glow ? 1f : 1.45f), 0f, glow ? 0.85f : 0.96f))
                .uv(0.5f, 0.5f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(transformed.x, transformed.y, transformed.z).endVertex();
    }
}
