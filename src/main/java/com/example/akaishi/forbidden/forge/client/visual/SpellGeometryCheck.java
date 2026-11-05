package com.example.akaishi.forbidden.forge.client.visual;

import com.example.akaishi.forbidden.forge.magic.visual.SpellBodyPainter;
import com.example.akaishi.forbidden.forge.magic.visual.SpellImpactVfx;
import com.example.akaishi.forbidden.forge.magic.visual.SpellStreamPainter;
import com.example.akaishi.forbidden.forge.magic.visual.SpellRenderTypes;
import com.example.akaishi.forbidden.forge.magic.visual.SpellVolumeGeometry;
import com.example.akaishi.forbidden.magic.table.MagicInkTable;
import com.example.akaishi.forbidden.magic.table.MagicSource;
import com.example.akaishi.forbidden.magic.visual.SpellInkMaterial;
import com.example.akaishi.forbidden.magic.visual.SpellVisual;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

import java.nio.ByteOrder;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;

/** 显式客户端烟测的真实顶点合约；不把几何断言等同于观感验收。 */
public final class SpellGeometryCheck {
    private SpellGeometryCheck() { }

    public static void verify() {
        require(SpellInkMaterial.ALL.keySet().equals(MagicInkTable.all().keySet()), "Ink material coverage");
        Set<Long> silhouettes = new HashSet<>();
        for (MagicSource source : MagicSource.values()) {
            for (String ink : MagicInkTable.order()) {
                for (SpellBodyPainter.Stage stage : SpellBodyPainter.Stage.values()) {
                    int low = 0;
                    int high = 0;
                    for (float detail : new float[]{0.5f, 0.72f, 1f}) {
                        for (float time : new float[]{0f, 0.12f, 1.1f, 3601f}) {
                            Result result = capture((pose, buffers) -> SpellBodyPainter.paint(pose, buffers,
                                    source, ink, MagicInkTable.get(ink).rgb(), time, 1f, 0.75f, detail, stage, 0.5f));
                            require(result.count() > 0 && result.count() <= 1536, "Source geometry budget: " + source + "/" + ink);
                            require(result.depth() > 0.10f, "Source became a flat billboard");
                            require(result.normals() >= 4 && result.colors() >= 4, "Volume lost surface normals or shaded faces");
                            require(result.layers() == 3, "Volume lost depth-writing body or emissive trim");
                            if (time == 0f) {
                                if (detail == 0.5f) low = result.count();
                                if (detail == 1f) high = result.count();
                                if ("chi".equals(ink) && stage == SpellBodyPainter.Stage.FLIGHT && detail == 0.5f)
                                    silhouettes.add(result.hash());
                            }
                        }
                    }
                    require(high > low, "LOD did not remove decoration");
                }
            }
            SpellVisual visual = SpellVisual.assemble("gon", "xian", "yuan", source.id(), 8f, 1f, 1f, java.util.List.of("gon"));
            for (Vec3 line : new Vec3[]{Vec3.ZERO, new Vec3(0, 4, 0), new Vec3(3, 1, 4)}) {
                Result result = capture((pose, buffers) -> SpellStreamPainter.paint(pose, buffers, visual, line, 0.25f, 0.8f, 1f));
                require(result.count() <= 2304, "Stream geometry budget");
                require(line.lengthSqr() == 0d ? result.count() == 0 : result.count() > 0, "Stream zero-motion guard");
            }
        }
        require(silhouettes.size() == MagicSource.values().length, "Source silhouettes are identical");
        Result invalid = capture((pose, buffers) -> SpellBodyPainter.paint(pose, buffers, MagicSource.SAN,
                "chi", 0xFF6A2A, Float.NaN, 1f, 1f, 1f, SpellBodyPainter.Stage.FLIGHT, 0f));
        require(invalid.count() == 0, "Invalid animation time emitted geometry");
        Result tube = capture((pose, buffers) -> SpellVolumeGeometry.tube(buffers.getBuffer(SpellRenderTypes.volume(false)),
                pose, new Vec3(-1, 0, 0), new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 1, 0),
                0.1f, 0.1f, 0.5f, 0.8f, 0.8f, 0xFF6A2A, 0xFFBC64, false, true, true));
        require(tube.count() == 24 && tube.depth() > 0.09f && tube.normals() == 6,
                "Trail section lost a side wall, end cap or thickness");
        Result stationary = capture((pose, buffers) -> SpellVolumeGeometry.tube(buffers.getBuffer(SpellRenderTypes.volume(false)),
                pose, Vec3.ZERO, Vec3.ZERO, new Vec3(0, 1, 0), new Vec3(0, 1, 0),
                0.1f, 0.1f, 0.5f, 0.8f, 0.8f, 0xFF6A2A, 0xFFBC64, false, true, true));
        require(stationary.count() == 0, "Zero-length trail emitted geometry");
        verifySurfaceTrim();
        verifyTubeSeam();
        SpellVisual impact = SpellVisual.assemble("gon", "xian", "yuan", MagicSource.LIFE.id(),
                8f, 1f, 1f, java.util.List.of("gon"));
        float before = capture((pose, buffers) -> SpellImpactVfx.paint(pose, buffers, impact,
                0.0799f, new org.joml.Quaternionf(), 1f)).depth();
        float after = capture((pose, buffers) -> SpellImpactVfx.paint(pose, buffers, impact,
                0.0801f, new org.joml.Quaternionf(), 1f)).depth();
        require(Math.abs(after - before) < 0.01f, "Impact compression still jumps at 0.08 seconds");
    }

    private static void verifySurfaceTrim() {
        for (int segments : new int[]{6, 8}) {
            for (float time : new float[]{0f, 0.5f, 1.1f}) {
                var body = new java.util.ArrayList<Vec3>();
                var trim = new java.util.ArrayList<Vec3>();
                capture((pose, buffers) -> SpellVolumeGeometry.ellipsoid(buffers.getBuffer(SpellRenderTypes.volume(false)),
                        pose, 0.4f, 0.5f, 0.8f, 0xFF6A2A, 0xFFBC64, segments, 4, time, false), body);
                capture((pose, buffers) -> SpellVolumeGeometry.ellipsoid(buffers.getBuffer(SpellRenderTypes.volume(true)),
                        pose, 0.4f, 0.5f, 0.8f, 0xFF6A2A, 0xFFBC64, segments, 4, time, true), trim);
                for (var mesh : java.util.List.of(body, trim)) {
                    for (int i = 0; i < mesh.size(); i += 4) {
                        Vec3 a = mesh.get(i), b = mesh.get(i + 1), c = mesh.get(i + 2), d = mesh.get(i + 3);
                        Vec3 normal = b.subtract(a).cross(c.subtract(a)).add(c.subtract(a).cross(d.subtract(a)));
                        require(normal.dot(a.add(b).add(c).add(d)) > 0d,
                                "Ellipsoid winding points inward or contains a collapsed face");
                    }
                }
                for (Vec3 point : trim) {
                    boolean attached = false;
                    for (int i = 0; i < body.size(); i += 4) {
                        attached |= onTriangle(point, body.get(i), body.get(i + 1), body.get(i + 2))
                                || onTriangle(point, body.get(i), body.get(i + 2), body.get(i + 3));
                    }
                    require(attached, "Ellipsoid trim floats above or crosses outside the body mesh");
                }
            }
        }
    }

    private static boolean onTriangle(Vec3 point, Vec3 a, Vec3 b, Vec3 c) {
        Vec3 ab = b.subtract(a), ac = c.subtract(a), ap = point.subtract(a);
        double aa = ab.dot(ab), bb = ac.dot(ac), cross = ab.dot(ac);
        double determinant = aa * bb - cross * cross;
        if (determinant < 1.0e-16d) return false;
        double u = (ap.dot(ab) * bb - ap.dot(ac) * cross) / determinant;
        double v = (ap.dot(ac) * aa - ap.dot(ab) * cross) / determinant;
        return u >= -0.005d && v >= -0.005d && u + v <= 1.005d
                && Math.abs(ap.dot(ab.cross(ac).normalize())) < 0.0011d;
    }

    private static void verifyTubeSeam() {
        Vec3 joint = new Vec3(0, 0, 1);
        Vec3 side = new Vec3(1, 0, 0), up = new Vec3(0, 1, 0);
        var positions = new java.util.ArrayList<Vec3>();
        capture((pose, buffers) -> {
            var consumer = buffers.getBuffer(SpellRenderTypes.volume(false));
            SpellVolumeGeometry.tube(consumer, pose, Vec3.ZERO, joint, side, side, up, up,
                    0.1f, 0.07f, 0.5f, 0.8f, 0.6f, 0xFF6A2A, 0xFFBC64, false, false, false);
            SpellVolumeGeometry.tube(consumer, pose, joint, new Vec3(0.5d, 0.2d, 2), side, side, up, up,
                    0.07f, 0f, 0.5f, 0.6f, 0f, 0xFF6A2A, 0xFFBC64, false, false, false);
        }, positions);
        require(positions.size() == 32, "Connected tube changed its side-wall budget");
        for (int wall = 0; wall < 4; wall++) {
            require(positions.get(wall * 4 + 3).distanceToSqr(positions.get(16 + wall * 4)) < 1.0e-12d
                    && positions.get(wall * 4 + 2).distanceToSqr(positions.get(17 + wall * 4)) < 1.0e-12d,
                    "Connected tube has a cracked joint");
        }
    }

    private record Result(int count, float depth, long hash, int normals, int colors, int layers) { }

    private static Result capture(BiConsumer<PoseStack, MultiBufferSource> draw) {
        return capture(draw, null);
    }

    private static Result capture(BiConsumer<PoseStack, MultiBufferSource> draw, java.util.List<Vec3> positions) {
        BufferBuilder builder = new BufferBuilder(32768);
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
        PoseStack pose = new PoseStack();
        int[] layers = {0};
        draw.accept(pose, type -> {
            if (type == SpellRenderTypes.volume(false)) layers[0] |= 1;
            if (type == SpellRenderTypes.volume(true)) layers[0] |= 2;
            return builder;
        });
        require(pose.clear(), "Unbalanced pose stack");
        var rendered = builder.end();
        try {
            int count = rendered.drawState().vertexCount();
            require(count % 4 == 0, "Incomplete quad");
            var data = rendered.vertexBuffer().order(ByteOrder.nativeOrder());
            int stride = rendered.drawState().format().getVertexSize();
            int colorOffset = -1;
            int normalOffset = -1;
            var format = rendered.drawState().format();
            for (int index = 0; index < format.getElements().size(); index++) {
                var usage = format.getElements().get(index).getUsage();
                if (usage == com.mojang.blaze3d.vertex.VertexFormatElement.Usage.COLOR) colorOffset = format.getOffset(index);
                if (usage == com.mojang.blaze3d.vertex.VertexFormatElement.Usage.NORMAL) normalOffset = format.getOffset(index);
            }
            require(colorOffset >= 0 && normalOffset >= 0, "Volume vertex format lost color or normals");
            Set<Integer> normals = new HashSet<>();
            Set<Integer> colors = new HashSet<>();
            float minZ = Float.POSITIVE_INFINITY;
            float maxZ = Float.NEGATIVE_INFINITY;
            long hash = 1L;
            for (int i = 0; i < count; i++) {
                for (int axis = 0; axis < 3; axis++) {
                    float value = data.getFloat(i * stride + axis * 4);
                    require(Float.isFinite(value) && Math.abs(value) <= 6f, "Non-finite or unbounded geometry");
                    hash = hash * 31L + Float.floatToIntBits(value);
                    if (axis == 2) {
                        minZ = Math.min(minZ, value);
                        maxZ = Math.max(maxZ, value);
                    }
                }
                if (positions != null) positions.add(new Vec3(data.getFloat(i * stride),
                        data.getFloat(i * stride + 4), data.getFloat(i * stride + 8)));
                int color = i * stride + colorOffset;
                colors.add(Byte.toUnsignedInt(data.get(color)) << 16
                        | Byte.toUnsignedInt(data.get(color + 1)) << 8 | Byte.toUnsignedInt(data.get(color + 2)));
                int normal = i * stride + normalOffset;
                int nx = data.get(normal), ny = data.get(normal + 1), nz = data.get(normal + 2);
                require(nx * nx + ny * ny + nz * nz > 10000, "Volume contains a zero or invalid normal");
                normals.add((nx & 255) << 16 | (ny & 255) << 8 | nz & 255);
            }
            return new Result(count, count == 0 ? 0f : maxZ - minZ, hash, normals.size(), colors.size(), layers[0]);
        } finally {
            rendered.release();
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
