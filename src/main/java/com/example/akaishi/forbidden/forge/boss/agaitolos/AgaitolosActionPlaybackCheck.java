package com.example.akaishi.forbidden.forge.boss.agaitolos;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosEntity;
import com.example.akaishi.forbidden.entity.AkaishiForbiddenEntities;
import com.example.akaishi.forbidden.forge.client.ForbiddenVisualConfig;
import com.example.akaishi.forbidden.forge.magic.visual.SpellRenderTypes;
import com.example.akaishi.forbidden.forge.magic.visual.SpellVfx;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.cache.object.GeoBone;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 显式隔离世界测试：真实 queen 控制器、骨骼矩阵和 Boss 渲染器；不向世界添加实体。 */
@Mod.EventBusSubscriber(modid = AkaishiMod.MOD_ID, value = Dist.CLIENT)
public final class AgaitolosActionPlaybackCheck {
    private static final List<String> CLIPS = List.of("boss_idle", "boss_move", "boss_slash",
            "boss_summon_throw", "boss_dive_sweep", "boss_block", "boss_hurt", "boss_curse_reverse",
            "boss_curse_break", "boss_calamity", "boss_triple_throw", "boss_blink_strike",
            "boss_stomp_cleave", "boss_rapid_kick", "boss_saturation_barrage", "boss_execution_cleave",
            "boss_slash", "boss_slash");
    private static final Map<String, float[]> firstPose = new HashMap<>();
    private static Fixture fixture;
    private static AgaitolosRenderer renderer;
    private static final Probe probe = new Probe();
    private static int clipIndex;
    private static int samples;
    private static double motion;
    private static double primaryMotion;
    private static double bladeArea;
    private static long waitStarted;
    private static boolean fallback;
    private static boolean finished;
    private static boolean originalFallback;

    private AgaitolosActionPlaybackCheck() { }

    @SubscribeEvent
    public static void render(TickEvent.RenderTickEvent event) {
        if (!Boolean.getBoolean("forbidden_hex.bossActionTest") || finished || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getOverlay() != null || mc.screen != null || mc.isPaused()) return;
        try {
            if (fixture == null) {
                require(mc.getSingleplayerServer() != null, "Playback check requires an isolated singleplayer world");
                require(mc.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString().equals("shader-compat-run"),
                        "Playback check must not run in the normal client directory");
                require(ForbiddenVisualConfig.PROJECTILE_TRAILS.get() && ForbiddenVisualConfig.MAX_TRAILS.get() > 0,
                        "Playback check requires Boss blade trails enabled");
                originalFallback = ForbiddenVisualConfig.FORCE_VANILLA_RENDERING.get();
                fixture = new Fixture(mc.level);
                ForbiddenVisualConfig.FORCE_VANILLA_RENDERING.set(false);
                require(SpellRenderTypes.flowEnabled(), "Playback custom pass requires the active custom blade-flow shader");
                fixture.setPos(mc.player.position().add(0, 0, 3));
                fixture.xo = fixture.getX();
                fixture.yo = fixture.getY();
                fixture.zo = fixture.getZ();
                renderer = (AgaitolosRenderer) mc.getEntityRenderDispatcher().getRenderer(fixture);
                waitStarted = mc.level.getGameTime();
            }
            if (mc.level.getGameTime() - waitStarted < 2) return;
            String clip = CLIPS.get(clipIndex);
            if (samples == 0) {
                fixture.clip = clip;
                fixture.start++;
                firstPose.clear();
                motion = 0;
                primaryMotion = 0;
                bladeArea = 0;
            }
            double duration = renderer.getGeoModel().getAnimation(fixture, clip).length();
            fixture.age = Math.min(duration - 0.001, duration * Math.max(0, samples - 3) / 96.0);
            ++fixture.tickCount;
            var manager = fixture.getAnimatableInstanceCache().getManagerForId(fixture.getId());
            var controller = manager.getAnimationControllers().get("queen");
            var previousAnimation = controller == null ? null : controller.getCurrentAnimation();
            if (samples == 50) renderer.getGeoModel().getAnimationProcessor().reloadAnimations = true;
            probe.reset();
            MultiBufferSource buffers = type -> {
                probe.collect = type == SpellRenderTypes.flow(SpellVfx.FILAMENT);
                return probe;
            };
            renderer.render(fixture, 0, 0, new PoseStack(), buffers, 0xF000F0);
            if (samples == 50) {
                require(controller != null && controller.getCurrentAnimation() != null
                                && controller.getCurrentAnimation() != previousAnimation,
                        "Resource reload retained the old animation queue: " + clip);
            }
            for (var bone : renderer.getGeoModel().getAnimationProcessor().getRegisteredBones()) {
                float[] pose = {bone.getRotX(), bone.getRotY(), bone.getRotZ(), bone.getPosX(), bone.getPosY(), bone.getPosZ()};
                if (samples < 3) continue;
                float[] initial = firstPose.computeIfAbsent(bone.getName(), ignored -> pose.clone());
                for (int i = 0; i < pose.length; i++) {
                    require(Float.isFinite(pose[i]), "Non-finite evaluated bone: " + clip + "/" + bone.getName());
                    double delta = Math.abs(pose[i] - initial[i]);
                    motion = Math.max(motion, delta);
                    if (List.of("Root", "UpBody", "UpperBody", "RightArm", "LeftArm", "RightForeArm",
                            "LeftForeArm", "RightLeg", "LeftLeg", "Head").contains(bone.getName())) {
                        primaryMotion = Math.max(primaryMotion, delta);
                    }
                }
            }
            GeoBone blade = renderer.getGeoModel().getBone("FH_RoyalBlade").orElseThrow();
            require(blade.getLocalSpaceMatrix().isFinite(), "Invalid tracked blade matrix: " + clip);
            bladeArea = Math.max(bladeArea, probe.area);
            if (++samples < 100) return;
            require(primaryMotion > 0.00001, "Actual queen controller never moved a primary bone: " + clip);
            if (List.of("boss_slash", "boss_dive_sweep", "boss_blink_strike", "boss_stomp_cleave", "boss_execution_cleave").contains(clip)) {
                require(bladeArea > 0.00000001, "Boss renderer emitted no non-degenerate blade ribbon: " + clip);
            }
            LogUtils.getLogger().info("BOSS_ACTION_PLAYBACK clip={} fallback={} motion={} blade_area={}",
                    clip, fallback, motion, bladeArea);
            samples = 0;
            if (++clipIndex < CLIPS.size()) return;
            if (!fallback) {
                fallback = true;
                ForbiddenVisualConfig.FORCE_VANILLA_RENDERING.set(true);
                require(!SpellRenderTypes.flowEnabled(), "Forced vanilla pass kept the custom blade-flow shader active");
                clipIndex = 0;
                return;
            }
            ForbiddenVisualConfig.FORCE_VANILLA_RENDERING.set(originalFallback);
            finished = true;
            LogUtils.getLogger().info("BOSS_ACTION_PLAYBACK_CHECK PASSED: 16 clips, repeated slash, actual controller/bones/blade geometry, custom and forced vanilla");
            mc.stop();
        } catch (RuntimeException exception) {
            if (fixture != null) ForbiddenVisualConfig.FORCE_VANILLA_RENDERING.set(originalFallback);
            finished = true;
            LogUtils.getLogger().error("BOSS_ACTION_PLAYBACK_CHECK FAILED", exception);
            throw exception;
        }
    }

    private static final class Fixture extends AgaitolosEntity {
        private String clip = "boss_idle";
        private double age;
        private long start;

        private Fixture(ClientLevel level) {
            super(AkaishiForbiddenEntities.AGAITOLOS.get(), level);
        }

        @Override public String getVisualAction() { return clip; }
        @Override public double getVisualActionAge(float partialTick) { return age; }
        @Override public long getVisualActionStart() { return start; }
    }

    private static final class Probe implements VertexConsumer {
        private final Vec3[] quad = new Vec3[4];
        private boolean collect;
        private Vec3 position = Vec3.ZERO;
        private int count;
        private double area;

        private void reset() { count = 0; area = 0; collect = false; }
        @Override public VertexConsumer vertex(double x, double y, double z) { position = new Vec3(x, y, z); return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer uv(float u, float v) { return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { return this; }
        @Override public VertexConsumer uv2(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { return this; }
        @Override public void defaultColor(int r, int g, int b, int a) { }
        @Override public void unsetDefaultColor() { }
        @Override public void endVertex() {
            if (!collect) return;
            require(Double.isFinite(position.lengthSqr()), "Non-finite Boss blade vertex");
            quad[count++ % 4] = position;
            if (count % 4 == 0) {
                area += quad[1].subtract(quad[0]).cross(quad[2].subtract(quad[0])).length();
                area += quad[2].subtract(quad[0]).cross(quad[3].subtract(quad[0])).length();
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
