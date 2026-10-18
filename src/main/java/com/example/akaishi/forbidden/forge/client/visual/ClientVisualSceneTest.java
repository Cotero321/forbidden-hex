package com.example.akaishi.forbidden.forge.client.visual;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.forge.magic.visual.SpellVisualEventHandler;
import com.example.akaishi.forbidden.magic.table.MagicIds;
import com.example.akaishi.forbidden.magic.table.MagicSource;
import com.example.akaishi.forbidden.magic.visual.SpellVisual;
import com.example.akaishi.forbidden.magic.visual.SpellVisualEvent;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.Screenshot;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Explicit isolated-world sampling; synthetic events only exercise client presentation. */
@Mod.EventBusSubscriber(modid = AkaishiMod.MOD_ID, value = Dist.CLIENT)
public final class ClientVisualSceneTest {
    private static final int WARMUP_FRAMES = 120;
    private static final int SAMPLE_FRAMES = 180;
    private static final int[] COUNTS = {0, 1, 8, 32};
    private static final String[] DELIVERIES = {MagicIds.Delivery.INSTANT, MagicIds.Delivery.CHANNEL,
            MagicIds.Delivery.PROJECTILE, MagicIds.Delivery.ATTACH, MagicIds.Delivery.ECHO, MagicIds.Delivery.VOLLEY};
    private static final String[] INKS = {MagicIds.Ink.CHI, MagicIds.Ink.YUAN, MagicIds.Ink.MAN, MagicIds.Ink.XING};
    private static final int[] COLORS = {0xF6A24D, 0x7664CA, 0x75BE78, 0xA3DCEE};
    private static final List<Double> frameTimes = new ArrayList<>();
    private static final JsonArray results = new JsonArray();
    private static final JsonArray screenshots = new JsonArray();
    private static boolean started;
    private static boolean finished;
    private static int phase;
    private static int frames;
    private static int pendingScreenshots;
    private static long lastFrame;
    private static long lastEmission = Long.MIN_VALUE;
    private static long drainUntilTick;
    private static Vec3 eye = Vec3.ZERO;
    private static Vec3 forward = Vec3.ZERO;
    private static Vec3 side = Vec3.ZERO;
    private static ParticleStatus previousParticles;
    private static int previousFrameLimit;
    private static boolean previousVsync;
    private static Path output;

    private ClientVisualSceneTest() { }

    public static boolean enabled() {
        return Boolean.getBoolean("forbidden_hex.visualSceneTest");
    }

    public static boolean finished() {
        return finished;
    }

    public static void start() throws IOException {
        if (started) return;
        Minecraft minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null || minecraft.level == null) return;
        var shaderState = ClientShaderCompatibility.state();
        boolean expectedShader = Boolean.getBoolean("forbidden_hex.shaderCompatTest");
        if (expectedShader ? shaderState != com.example.akaishi.forbidden.visual.ShaderCompatibility.State.ACTIVE
                : shaderState != com.example.akaishi.forbidden.visual.ShaderCompatibility.State.ABSENT
                && shaderState != com.example.akaishi.forbidden.visual.ShaderCompatibility.State.INACTIVE) {
            throw new IllegalStateException("Scene shader state disagrees with requested mode: " + shaderState);
        }
        Path modeOutput = minecraft.gameDirectory.toPath().resolve("../visual-review/world-scenes").normalize()
                .resolve(expectedShader ? "shader" : "vanilla");
        Files.createDirectories(modeOutput);
        output = Files.createTempDirectory(modeOutput, "run-");
        eye = player.getEyePosition();
        forward = player.getLookAngle().normalize();
        side = forward.cross(new Vec3(0, 1, 0)).normalize();
        if (side.lengthSqr() < 0.5d) side = new Vec3(1, 0, 0);
        previousParticles = java.util.Objects.requireNonNull(minecraft.options.particles().get());
        previousFrameLimit = minecraft.options.framerateLimit().get();
        previousVsync = minecraft.options.enableVsync().get();
        minecraft.options.enableVsync().set(false);
        minecraft.options.framerateLimit().set(120);
        started = true;
        beginPhase(minecraft);
    }

    private static void beginPhase(Minecraft minecraft) {
        frames = 0;
        lastFrame = 0;
        lastEmission = Long.MIN_VALUE;
        drainUntilTick = java.util.Objects.requireNonNull(minecraft.level).getGameTime() + 60;
        frameTimes.clear();
        minecraft.options.particles().set(phase < COUNTS.length ? ParticleStatus.ALL : ParticleStatus.MINIMAL);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!enabled() || !started || finished || event.phase != TickEvent.Phase.END || phase >= COUNTS.length * 2) return;
        Minecraft minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        if (level == null || minecraft.isPaused() || minecraft.screen != null || minecraft.getOverlay() != null) return;
        long now = level.getGameTime();
        if (now < drainUntilTick || now == lastEmission || (lastEmission != Long.MIN_VALUE && now - lastEmission < 6)) return;
        lastEmission = now;
        int count = COUNTS[phase % COUNTS.length];
        double distance = phase < COUNTS.length ? 8d : 52d;
        for (int index = 0; index < count; index++) {
            double x = count == 1 ? 0d : ((index % 8) - 3.5d) * 0.5d;
            double y = count <= 8 ? 0d : ((index / 8) - 1.5d) * 0.55d;
            Vec3 to = eye.add(forward.scale(distance)).add(side.scale(x)).add(0, y, 0);
            MagicSource source = MagicSource.values()[index % MagicSource.values().length];
            SpellVisual visual = new SpellVisual(MagicIds.Effect.DAMAGE, DELIVERIES[index % DELIVERIES.length],
                    source.id(), INKS[index % INKS.length], COLORS[index % COLORS.length], source.rgb(),
                    3f, 0.6f, 1f, 0, List.of(), "");
            SpellVisualEventHandler.INSTANCE.accept(new SpellVisualEvent(visual,
                    eye.add(side.scale(-2d)).add(forward.scale(2d)), to, -1, 6));
        }
    }

    @SubscribeEvent
    public static void render(TickEvent.RenderTickEvent event) {
        if (!enabled() || !started || finished || event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        if (level == null || minecraft.isPaused() || minecraft.screen != null || minecraft.getOverlay() != null
                || level.getGameTime() < drainUntilTick) {
            lastFrame = 0;
            return;
        }
        if (phase >= COUNTS.length * 2) {
            if (pendingScreenshots == 0) finish(minecraft);
            return;
        }
        long now = System.nanoTime();
        if (lastFrame != 0 && frames >= WARMUP_FRAMES) frameTimes.add((now - lastFrame) / 1_000_000d);
        lastFrame = now;
        frames++;
        int count = COUNTS[phase % COUNTS.length];
        if ((count == 1 || count == 32) && frames == WARMUP_FRAMES + SAMPLE_FRAMES / 2) {
            String name = (phase < COUNTS.length ? "near-all-" : "far-minimal-") + count + ".png";
            screenshots.add("screenshots/" + name);
            pendingScreenshots++;
            Screenshot.grab(output.toFile(), name, minecraft.getMainRenderTarget(), message ->
                    minecraft.execute(() -> {
                        pendingScreenshots--;
                        if (!(message.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents contents)
                                || !"screenshot.success".equals(contents.getKey())) {
                            throw new IllegalStateException("Scene screenshot failed: " + message.getString());
                        }
                        LogUtils.getLogger().info("VISUAL_SCENE_SCREENSHOT: {}", message.getString());
                    }));
        }
        if (frameTimes.size() >= SAMPLE_FRAMES) {
            JsonObject result = new JsonObject();
            result.addProperty("concurrent_active_events", count);
            result.addProperty("distance", phase < COUNTS.length ? 8 : 52);
            result.addProperty("particles", phase < COUNTS.length ? "ALL" : "MINIMAL");
            result.addProperty("samples", frameTimes.size());
            List<Double> sorted = frameTimes.stream().sorted().toList();
            result.addProperty("p50_ms", sorted.get(sorted.size() / 2));
            result.addProperty("p95_ms", sorted.get((int) Math.ceil(sorted.size() * 0.95d) - 1));
            result.addProperty("max_ms", sorted.get(sorted.size() - 1));
            results.add(result);
            phase++;
            beginPhase(minecraft);
        }
    }

    private static void finish(Minecraft minecraft) {
        try {
            JsonObject report = new JsonObject();
            report.addProperty("scope", "synthetic client spell carriers, particles and impacts; no gameplay execution");
            report.addProperty("shader_state", ClientShaderCompatibility.state().name());
            report.addProperty("renderer", org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER));
            report.addProperty("resolution", minecraft.getWindow().getWidth() + "x" + minecraft.getWindow().getHeight());
            report.addProperty("render_distance", minecraft.options.renderDistance().get());
            report.addProperty("frame_limit", 120);
            report.addProperty("warmup_frames_per_case", WARMUP_FRAMES);
            report.addProperty("baseline", "zero-effect scene in this run; no pre-redesign frame-time baseline exists");
            report.addProperty("acceptance", "measurements only; not a pre-redesign 15-percent regression verdict or full gameplay acceptance");
            report.add("cases", results);
            report.add("screenshots", screenshots);
            Files.writeString(output.resolve("frame-times.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report)
                    + System.lineSeparator(), StandardCharsets.UTF_8);
            for (var screenshot : screenshots) {
                if (!Files.isRegularFile(output.resolve(screenshot.getAsString()))) {
                    throw new IOException("Scene screenshot was not written: " + screenshot.getAsString());
                }
            }
            LogUtils.getLogger().info("VISUAL_SCENE_TEST PASSED: 8 cases and 4 screenshots written to {}", output);
            finished = true;
        } catch (IOException exception) {
            throw new IllegalStateException("Visual scene output failed", exception);
        } finally {
            minecraft.options.particles().set(java.util.Objects.requireNonNull(previousParticles));
            minecraft.options.framerateLimit().set(previousFrameLimit);
            minecraft.options.enableVsync().set(previousVsync);
        }
    }
}
