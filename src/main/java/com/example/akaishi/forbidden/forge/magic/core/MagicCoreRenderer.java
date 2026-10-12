package com.example.akaishi.forbidden.forge.magic.core;

import com.example.akaishi.forbidden.forge.magic.visual.SpellGeometry;
import com.example.akaishi.forbidden.item.MagicCoreItem;
import com.example.akaishi.forbidden.magic.table.MagicSource;
import com.example.akaishi.forbidden.magic.table.MagicWord;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.joml.Matrix4f;

/** 共用外壳与八刻槽；四种源分别绘制独立的结构，中心始终是透明镂空。 */
public final class MagicCoreRenderer extends BlockEntityWithoutLevelRenderer implements IClientItemExtensions {
    public static final MagicCoreRenderer INSTANCE = new MagicCoreRenderer();
    private static final int METAL = 0x716D79;
    private static final int SHADOW = 0x26242D;
    private static final int EDGE = 0xBBB4AF;

    private MagicCoreRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public BlockEntityWithoutLevelRenderer getCustomRenderer() {
        return this;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers,
                             int light, int overlay) {
        pose.pushPose();
        pose.translate(0.5f, 0.5f, 0.5f);
        if (context == ItemDisplayContext.GROUND) {
            pose.mulPose(Axis.XP.rotationDegrees(75));
            pose.scale(0.72f, 0.72f, 0.72f);
        } else if (context != ItemDisplayContext.GUI && context != ItemDisplayContext.FIXED) {
            pose.mulPose(Axis.XP.rotationDegrees(-12));
            pose.mulPose(Axis.YP.rotationDegrees(25));
            pose.scale(0.84f, 0.84f, 0.84f);
        }
        Matrix4f m = pose.last().pose();
        VertexConsumer out = buffers.getBuffer(SpellGeometry.energyType());
        MagicSource source = MagicCoreItem.source(stack);
        shell(out, m);
        sourceStructure(out, m, source);
        slots(out, pose, buffers, stack, light);
        levelMounts(out, m, MagicCoreItem.level(stack), source);
        pose.popPose();
    }

    private static void shell(VertexConsumer out, Matrix4f m) {
        band(out, m, 0.365f, 0.48f, 0, 360, -0.012f, SHADOW, 1);
        band(out, m, 0.453f, 0.478f, 0, 360, 0.024f, EDGE, 0.85f);
        band(out, m, 0.36f, 0.379f, 0, 360, 0.031f, METAL, 0.95f);
        for (int i = 0; i < 8; i++) {
            float a = (float) (i * Math.PI / 4);
            float x = (float) Math.cos(a) * 0.459f;
            float y = (float) Math.sin(a) * 0.459f;
            stroke(out, m, x - 0.012f, y, x + 0.012f, y, 0.006f, 0.04f, SHADOW, 0.8f);
        }
    }

    private static void sourceStructure(VertexConsumer out, Matrix4f m, MagicSource source) {
        int color = source.rgb();
        switch (source) {
            case SAN -> {
                for (int i = 0; i < 6; i++) {
                    float start = i * 60 + (i % 2 == 0 ? 4 : 10);
                    band(out, m, 0.225f + (i % 2) * 0.012f, 0.34f, start, 43, 0.025f, 0x65656B, 1);
                    band(out, m, 0.29f, 0.302f, start + 7, 28, 0.033f, color, 0.48f);
                    radial(out, m, start + 21, 0.23f, 0.34f, 0.009f, SHADOW, 1);
                }
            }
            case LIFE -> {
                for (int i = 0; i < 4; i++) {
                    float a = i * 90 + 25;
                    band(out, m, 0.262f, 0.286f, a, 53, 0.03f, 0x285D42, 1);
                    band(out, m, 0.272f, 0.279f, a + 3, 47, 0.038f, color, 0.8f);
                    radial(out, m, a + 21, 0.275f, 0.337f, 0.011f, color, 0.72f);
                    radial(out, m, a + 41, 0.275f, 0.33f, 0.008f, color, 0.6f);
                    float angle = (float) Math.toRadians(a + 21);
                    dot(out, m, (float) Math.cos(angle) * 0.337f, (float) Math.sin(angle) * 0.337f,
                            0.016f, 0.049f, 0xA5DCA7, 0.8f);
                }
            }
            case DARK -> {
                for (int i = 0; i < 5; i++) {
                    float a = i * 72 + 5;
                    band(out, m, 0.204f, 0.345f, a, 59, 0.021f, 0x11121B, 1);
                    band(out, m, 0.207f, 0.227f, a + 3, 53, 0.033f, 0x777689, 0.7f);
                    radial(out, m, a + 31, 0.23f, 0.33f, 0.009f, 0xB0AEB6, 0.7f);
                    radial(out, m, a + 37, 0.27f, 0.341f, 0.006f, 0x777689, 0.55f);
                }
            }
            case WHITE -> {
                for (int i = 0; i < 6; i++) {
                    float a = (float) Math.toRadians(i * 60 + 30);
                    float x = (float) Math.cos(a), y = (float) Math.sin(a);
                    float tx = -y, ty = x;
                    polygon(out, m, 0.026f, 0xCBD6E2, 0.95f,
                            x * 0.225f, y * 0.225f, x * 0.275f + tx * 0.055f, y * 0.275f + ty * 0.055f,
                            x * 0.347f, y * 0.347f, x * 0.275f - tx * 0.055f, y * 0.275f - ty * 0.055f);
                    stroke(out, m, x * 0.225f, y * 0.225f, x * 0.347f, y * 0.347f,
                            0.008f, 0.035f, 0xEDF4F6, 0.75f);
                }
            }
        }
    }

    private static void slots(VertexConsumer out, PoseStack pose, MultiBufferSource buffers,
                              ItemStack stack, int light) {
        Matrix4f m = pose.last().pose();
        for (int i = 0; i < MagicCoreItem.SLOTS; i++) {
            float a = (float) (i * Math.PI / 4 + Math.PI / 8);
            float x = (float) Math.cos(a) * 0.413f;
            float y = (float) Math.sin(a) * 0.413f;
            dot(out, m, x, y, 0.033f, 0.034f, SHADOW, 1);
            stroke(out, m, x - 0.025f, y - 0.025f, x + 0.025f, y + 0.025f,
                    0.004f, 0.036f, METAL, 0.62f);
            MagicWord word = MagicWord.byId(MagicCoreItem.word(stack, i));
            if (word != null) {
                CoreRuneGeometry.draw(pose, buffers, x, y, word, MagicCoreItem.ink(stack, i), light);
            }
        }
    }

    private static void levelMounts(VertexConsumer out, Matrix4f m, int level, MagicSource source) {
        // 孔径约 6/16 像素；等级圈仅画孔外挂点，零等级不出现数字或光环。
        band(out, m, 0.19f, 0.206f, 0, 360, 0.027f, SHADOW, 0.9f);
        for (int i = 0; i < 6; i++) {
            float a = (float) (i * Math.PI / 3);
            float x = (float) Math.cos(a) * 0.211f;
            float y = (float) Math.sin(a) * 0.211f;
            dot(out, m, x, y, 0.015f, 0.044f, METAL, 0.8f);
            if (i < Math.min(6, level)) dot(out, m, x, y, 0.008f, 0.047f, source.rgb(), 0.77f);
        }
    }

    static void band(VertexConsumer out, Matrix4f m, float inner, float outer, float start, float span,
                     float z, int color, float alpha) {
        int segments = Math.max(3, Math.round(span / 6));
        for (int i = 0; i < segments; i++) {
            double a = Math.toRadians(start + span * i / segments);
            double b = Math.toRadians(start + span * (i + 1) / segments);
            polygon(out, m, z, color, alpha,
                    (float) Math.cos(a) * inner, (float) Math.sin(a) * inner,
                    (float) Math.cos(a) * outer, (float) Math.sin(a) * outer,
                    (float) Math.cos(b) * outer, (float) Math.sin(b) * outer,
                    (float) Math.cos(b) * inner, (float) Math.sin(b) * inner);
        }
    }

    static void radial(VertexConsumer out, Matrix4f m, float degrees, float inner, float outer,
                       float width, int color, float alpha) {
        float a = (float) Math.toRadians(degrees);
        stroke(out, m, (float) Math.cos(a) * inner, (float) Math.sin(a) * inner,
                (float) Math.cos(a) * outer, (float) Math.sin(a) * outer, width, 0.043f, color, alpha);
    }

    static void dot(VertexConsumer out, Matrix4f m, float x, float y, float size, float z, int color, float alpha) {
        polygon(out, m, z, color, alpha, x - size, y - size, x + size, y - size,
                x + size, y + size, x - size, y + size);
    }

    static void stroke(VertexConsumer out, Matrix4f m, float x0, float y0, float x1, float y1,
                       float width, float z, int color, float alpha) {
        float dx = x1 - x0, dy = y1 - y0;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length == 0) return;
        float px = -dy / length * width / 2, py = dx / length * width / 2;
        polygon(out, m, z, color, alpha, x0 + px, y0 + py, x0 - px, y0 - py,
                x1 - px, y1 - py, x1 + px, y1 + py);
    }

    static void polygon(VertexConsumer out, Matrix4f m, float z, int color, float alpha,
                        float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3) {
        float r = (color >> 16 & 255) / 255f, g = (color >> 8 & 255) / 255f, b = (color & 255) / 255f;
        SpellGeometry.vertex(out, m, x0, y0, z, 0, 0, r, g, b, alpha);
        SpellGeometry.vertex(out, m, x1, y1, z, 1, 0, r, g, b, alpha);
        SpellGeometry.vertex(out, m, x2, y2, z, 1, 1, r, g, b, alpha);
        SpellGeometry.vertex(out, m, x3, y3, z, 0, 1, r, g, b, alpha);
    }
}
