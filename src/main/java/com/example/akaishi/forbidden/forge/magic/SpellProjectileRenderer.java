package com.example.akaishi.forbidden.forge.magic;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.forge.magic.visual.SpellGeometry;
import com.example.akaishi.forbidden.forge.magic.visual.SpellVfx;
import com.example.akaishi.forbidden.forge.magic.visual.SpellVisualPainter;
import com.example.akaishi.forbidden.magic.particle.MagicInkStyle;
import com.example.akaishi.forbidden.magic.projectile.SpellProjectile;
import com.example.akaishi.forbidden.magic.visual.SpellVisual;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 法术弹体渲染器（设计记忆 §19.3 X2 / §7）。
 *
 * <p><b>为什么不再复用原版凋零头渲染器</b>：那是上一轮的临时占位。
 * 凋零头是一个<b>固定模型</b> —— 它无法表达"这一发是火墨的尖刺还是渊墨的涡旋"。
 * 现在弹体的外观同样走 {@link SpellVisualPainter} 的组装：
 * 形态由核心 E 决定、配色由源与墨决定、体积由威力决定。
 *
 * <p><b>为什么整枚自转</b>：弹体在飞，若形态静止会看成"贴图没跟上"。
 * 自转让正交三环的笼状结构产生明显的空间感，
 * 而且这个自转是<b>渲染层自己加的</b>，不占用实体的朝向（弹体朝向仍由飞行方向决定，
 * 只是我们不用它）。
 */
public class SpellProjectileRenderer extends EntityRenderer<SpellProjectile> {

    /** 自转角速度（度/秒）——快一点才有"高速旋转的能量球"的观感 */
    private static final float SPIN_PER_SECOND = 220f;

    /** 尾带长度（格）：约 1.5 倍初速，飞行中看得出"拖尾"又不至于糊成一团 */
    private static final float TRAIL_LENGTH = 1.8f;
    /** 环绕微光的颗数（绕弹体旋转的小光点） */
    private static final int ORBIT_MOTES = 3;

    /** 墨 id → 该墨的「核心」形态粒子贴图（按墨缓存，避免每帧 new ResourceLocation） */
    private final Map<String, ResourceLocation> orbTextures = new ConcurrentHashMap<>();

    public SpellProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    /**
     * 该墨的「核心」形态粒子贴图（= 尾迹粒子用的同一张）。
     *
     * <p><b>为什么用粒子图当彗头而不是另找一张 vfx</b>：头与尾同一套图才像"一个东西在飞"；
     * 混两种美术语言会读成"贴了两个互不相干的物件"。
     *
     * <p>贴图路径可直连：{@code entityTranslucentEmissive} 绑定的是<b>独立贴图</b>（按需加载），
     * 不需要进图集。
     */
    private ResourceLocation orbTexture(String inkId) {
        if (inkId == null || inkId.isEmpty()) {
            return SpellVfx.COMET; // 无墨：退回通用彗头
        }
        return orbTextures.computeIfAbsent(inkId, id -> new ResourceLocation(AkaishiMod.MOD_ID,
                "textures/particle/spell_mote_" + id + "_orb.png"));
    }

    /** 尾带的滚动速度（rad/s）：按墨的旋性换算 —— 零旋性也留一点底速，免得尾带像死物 */
    private static float rollSpeed(String inkId) {
        return 0.4f + MagicInkStyle.get(inkId).swirl() * 0.5f;
    }

    /** 环绕微光：绕弹体旋转的小光点（面朝相机）—— "这一发是活的" */
    private static void paintOrbitMotes(PoseStack poseStack, MultiBufferSource buffers, SpellVisual visual,
            Vec3 motion, org.joml.Quaternionf billboard, ResourceLocation head,
            float size, float bright, float time) {
        Vec3 dir = motion.normalize();
        Vec3 up = Math.abs(dir.y) > 0.9d ? new Vec3(1d, 0d, 0d) : new Vec3(0d, 1d, 0d);
        Vec3 side = dir.cross(up).normalize();
        Vec3 vert = side.cross(dir).normalize();
        float orbit = Math.max(0.10f, size * 0.60f);
        float moteSize = Math.max(0.06f, size * 0.28f);
        double spin = time * (3.0d + Math.abs(rollSpeed(visual.inkId())));
        for (int i = 0; i < ORBIT_MOTES; i++) {
            double angle = spin + i * (Math.PI * 2d / ORBIT_MOTES);
            double ox = Math.cos(angle) * orbit;
            double oy = Math.sin(angle) * orbit;
            poseStack.pushPose();
            poseStack.translate(side.x * ox + vert.x * oy, side.y * ox + vert.y * oy, side.z * ox + vert.z * oy);
            SpellVisualPainter.flatQuad(buffers, poseStack, head, billboard, moteSize,
                    bright * 0.80f, visual.inkRgb());
            poseStack.popPose();
        }
    }

    @Override
    public boolean shouldRender(SpellProjectile entity, Frustum frustum,
            double camX, double camY, double camZ) {
        float radius = Math.max(0.5f, entity.visual().baseSize());
        return frustum.isVisible(entity.getBoundingBox().inflate(radius));
    }

    @Override
    public void render(SpellProjectile entity, float entityYaw, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight) {
        SpellVisual visual = entity.visual();
        // 未武装（effectId 为空）说明这一发还没同步到视觉描述 —— 宁可不画，也不要画一团白球
        if (visual == null || visual.effectId().isEmpty()) {
            return;
        }
        float time = (entity.tickCount + partialTick) / 20f;
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(time * SPIN_PER_SECOND));
        SpellVisualPainter.paint(poseStack, buffers, visual, time);
        poseStack.popPose();

        // 彗头 + 尾带 + 环绕微光：光靠"自转的几何笼"读不出"这一发在飞"。
        // 三样的取图/取参都<b>跟着墨走</b> —— 于是"炽的一发"与"星的一发"不只是颜色不同：
        // 头形不同、尾带滚动快慢不同、环绕微光的形态也不同。
        float size = visual.baseSize();
        float bright = visual.brightness();
        String inkId = visual.inkId();
        org.joml.Quaternionf billboard = entityRenderDispatcher.cameraOrientation();
        ResourceLocation head = orbTexture(inkId);
        SpellVisualPainter.flatQuad(buffers, poseStack, SpellVfx.HALO, billboard,
                Math.max(0.30f, size * 1.25f), Math.min(0.60f, bright * 0.50f), visual.sourceRgb());
        SpellVisualPainter.flatQuad(buffers, poseStack, head, billboard,
                Math.max(0.18f, size * 0.78f), Math.min(1f, bright), visual.inkRgb());
        net.minecraft.world.phys.Vec3 motion = entity.getDeltaMovement();
        if (motion.lengthSqr() > 1.0e-6d) {
            // 尾带：按墨的"旋性"滚起来（混墨/蔓墨绞得明显，炽墨几乎不滚）
            float roll = time * rollSpeed(inkId);
            SpellVisualPainter.ribbon(buffers, poseStack, motion.scale(-TRAIL_LENGTH),
                    Math.max(0.05f, size * 0.16f), Math.min(0.85f, bright * 0.70f),
                    visual.inkRgb(), SpellVfx.STREAK, roll);
            paintOrbitMotes(poseStack, buffers, visual, motion, billboard, head, size, bright, time);
        }

        if (!visual.glyphs().isEmpty()) {
            // 词符环独立面向相机，避免跟随主体的 220 度自转而失去可读性。
            poseStack.pushPose();
            poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
            var consumer = buffers.getBuffer(SpellGeometry.energyType());
            float alpha = visual.brightness();
            int rgb = visual.inkRgb();
            SpellGeometry.runeRing(consumer, poseStack.last().pose(), visual.glyphs(),
                    Math.max(0.12f, visual.baseSize() * 0.55f),
                    Math.max(0.035f, visual.baseSize() * 0.10f), time * 18f, alpha * 0.9f,
                    SpellVisualPainter.red(rgb), SpellVisualPainter.green(rgb), SpellVisualPainter.blue(rgb));
            poseStack.popPose();
        }
    }

    @Override
    public ResourceLocation getTextureLocation(SpellProjectile entity) {
        return SpellGeometry.WHITE;
    }
}
