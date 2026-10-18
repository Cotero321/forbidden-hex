package com.example.akaishi.forbidden.forge.client.visual;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.effect.AkaishiForbiddenEffects;
import com.example.akaishi.forbidden.forge.client.ForbiddenVisualConfig;
import com.example.akaishi.forbidden.forge.magic.visual.SpellVisualEventHandler;
import com.example.akaishi.forbidden.magic.visual.SpellVisual;
import com.example.akaishi.forbidden.magic.particle.AkaishiMagicParticles;
import com.example.akaishi.forbidden.magic.table.MagicSource;
import com.example.akaishi.forbidden.magic.visual.SpellCarrierForm;
import com.example.akaishi.forbidden.magic.visual.SpellVisualEvent;
import com.example.akaishi.forbidden.sanity.ClientSanityData;
import com.example.akaishi.forbidden.sanity.ClientSanityVision;
import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Opt-in isolated-world presentation regression; client snapshots do not exercise gameplay settlement. */
@Mod.EventBusSubscriber(modid = AkaishiMod.MOD_ID, value = Dist.CLIENT)
public final class ClientSanityVisualSceneTest {
    private static int waiting;
    private static int ticks;
    private static int pending;
    private static boolean previousFallback;
    private static boolean previousPosts;
    private static CameraType previousCamera;
    private static boolean previousGui;
    private static CompletableFuture<Void> reload;
    private static Path output;
    private static String captureFailure;

    private ClientSanityVisualSceneTest() { }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void seed(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("forbidden_hex.sanitySceneTest") || event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.getOverlay() != null
                || !ClientVisualSmokeTest.readyForScene()) return;
        int next = ticks + 1;
        snapshot(next < 81 || next >= 701 ? 100f : next < 161 ? 39f : next < 241 || next >= 561 ? 19f : 0f);
        if (next >= 321 && next < 561) {
            minecraft.player.addEffect(new MobEffectInstance(AkaishiForbiddenEffects.UNNAMEABLE.get(), 20,
                    next >= 401 ? 1 : 0, false, false, false));
        } else minecraft.player.removeEffect(AkaishiForbiddenEffects.UNNAMEABLE.get());
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("forbidden_hex.sanitySceneTest") || event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (captureFailure != null) throw new IllegalStateException(captureFailure);
        if (++waiting > 2400) throw new IllegalStateException("Sanity scene timed out");
        if (minecraft.level == null || minecraft.player == null || minecraft.getSingleplayerServer() == null
                || minecraft.getOverlay() != null || !ClientVisualSmokeTest.readyForScene()) return;
        if (ticks == 0) {
            previousFallback = ForbiddenVisualConfig.FORCE_VANILLA_RENDERING.get();
            previousPosts = ForbiddenVisualConfig.POST_EFFECTS.get();
            previousCamera = minecraft.options.getCameraType();
            previousGui = minecraft.options.hideGui;
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
            minecraft.options.hideGui = false;
            ForbiddenVisualConfig.POST_EFFECTS.set(true);
            output = minecraft.gameDirectory.toPath().resolve("../visual-review/sanity-upgrade")
                    .normalize().resolve(Boolean.getBoolean("forbidden_hex.shaderCompatTest") ? "shader" : "vanilla");
            try { java.nio.file.Files.createDirectories(output); }
            catch (java.io.IOException exception) { throw new IllegalStateException("Cannot create sanity scene output", exception); }
        }
        minecraft.setScreen(null);
        ticks++;
        if (ticks == 481) ForbiddenVisualConfig.FORCE_VANILLA_RENDERING.set(true);
        if (ticks == 561) {
            ForbiddenVisualConfig.FORCE_VANILLA_RENDERING.set(previousFallback);
            reload = minecraft.reloadResourcePacks();
        }
        if (ticks % 4 == 0 && ticks < 701) emit(minecraft);
        if (ticks == 70) capture(minecraft, "sanity_01_normal.png");
        if (ticks == 150) { checkPost(minecraft, "sanity_vision"); capture(minecraft, "sanity_02_low39.png"); }
        if (ticks == 230) { checkPost(minecraft, "sanity_vision"); capture(minecraft, "sanity_03_low19.png"); }
        if (ticks == 310) { checkPost(minecraft, "sanity_vision"); capture(minecraft, "sanity_04_zero.png"); }
        if (ticks == 390) { checkPost(minecraft, "unnameable"); capture(minecraft, "sanity_05_unnameable1.png"); }
        if (ticks == 470) { checkPost(minecraft, "unnameable"); capture(minecraft, "sanity_06_unnameable2.png"); }
        if (ticks == 550) { checkPost(minecraft, null); capture(minecraft, "sanity_07_forced_fallback.png"); }
        if (ticks == 660) {
            require(reload != null && reload.isDone(), "Sanity resource reload did not complete");
            reload.join();
            checkPost(minecraft, "sanity_vision");
            capture(minecraft, "sanity_08_reload_recovery.png");
        }
        if (ticks == 670) ForbiddenVisualConfig.POST_EFFECTS.set(false);
        if (ticks == 695) {
            checkPost(minecraft, null);
            require(!ClientPostEffects.sanityPostActive(), "Disabled post incorrectly reported active");
            capture(minecraft, "sanity_09_posts_disabled.png");
        }
        if (ticks == 701) ForbiddenVisualConfig.POST_EFFECTS.set(true);
        if (ticks >= 800 && pending == 0) {
            require(!ClientSanityVision.active(), "Normal sanity kept presentation intensity");
            require(minecraft.gameRenderer.currentEffect() == null, "Recovered player retained local post chain");
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "OpenGL error in sanity scene");
            ForbiddenVisualConfig.FORCE_VANILLA_RENDERING.set(previousFallback);
            ForbiddenVisualConfig.POST_EFFECTS.set(previousPosts);
            minecraft.options.setCameraType(previousCamera);
            minecraft.options.hideGui = previousGui;
            LogUtils.getLogger().info("SANITY_SCENE_TEST PASSED: normal/39/19/0, unnameable I/II, particles, fallback, resource reload, recovery; shader_state={}; synthetic_client_snapshots=true; multiplayer=false; output={}",
                    ClientShaderCompatibility.state(), output);
            minecraft.stop();
        }
    }

    private static void snapshot(float sanity) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeFloat(sanity);
            buffer.writeFloat(100f);
            buffer.writeFloat(20f);
            buffer.writeFloat(0f);
            buffer.writeFloat(0f);
            buffer.writeVarInt(0);
            ClientSanityData.apply(buffer);
        } finally { buffer.release(); }
    }

    private static void emit(Minecraft minecraft) {
        Vec3 eye = minecraft.player.getEyePosition();
        Vec3 forward = minecraft.player.getLookAngle().normalize();
        Vec3 right = forward.cross(new Vec3(0d, 1d, 0d)).normalize();
        if (right.lengthSqr() < 0.5d) right = new Vec3(1d, 0d, 0d);
        for (int i = 0; i < 3; i++) {
            Vec3 position = eye.add(forward.scale(4.5d)).add(right.scale((i - 1) * 1.4d));
            SpellVisual visual = SpellVisual.assemble(i == 0 ? "tou" : i == 1 ? "pian" : "hu",
                    "instant", i == 0 ? "chi" : i == 1 ? "yuan" : "yao", "san", 2f, 0.35f, 1f, List.of("tou", "hu"));
            SpellVisualEventHandler.INSTANCE.accept(new SpellVisualEvent(visual,
                    position.subtract(forward.scale(0.5d)), position, -1, 10));
            minecraft.level.addParticle(ParticleTypes.SMOKE, position.x, position.y + 0.7d, position.z, 0d, 0.01d, 0d);
            minecraft.level.addParticle(ParticleTypes.ENCHANT, position.x, position.y - 0.6d, position.z, 0d, 0.02d, 0d);
            if (i < 2) AkaishiMagicParticles.trail(minecraft.level, MagicSource.DARK, "an",
                    i == 0 ? SpellCarrierForm.BEAM : SpellCarrierForm.FIELD, 0x5A2070,
                    position.x, position.y, position.z, 3, 0.05d);
        }
    }

    private static void checkPost(Minecraft minecraft, String expected) {
        var chain = minecraft.gameRenderer.currentEffect();
        if (expected == null || !ClientShaderCompatibility.allowsCustomPrograms()) {
            require(chain == null, "Compatibility fallback retained a local post chain");
        } else {
            require(chain != null && chain.getName().equals("forbidden_hex:shaders/post/" + expected + ".json"),
                    "Incorrect post chain for " + expected + ": actual=" + (chain == null ? "none" : chain.getName())
                            + ", sanity=" + ClientSanityData.snapshot() + ", gray=" + ClientSanityVision.gray()
                            + ", fallback=" + ForbiddenVisualConfig.FORCE_VANILLA_RENDERING.get()
                            + ", posts=" + ForbiddenVisualConfig.POST_EFFECTS.get());
        }
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "OpenGL error while displaying " + expected);
    }

    private static void capture(Minecraft minecraft, String name) {
        pending++;
        Screenshot.grab(output.toFile(), name, minecraft.getMainRenderTarget(), message -> minecraft.execute(() -> {
            pending--;
            if (!(message.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents contents)
                    || !"screenshot.success".equals(contents.getKey())) {
                captureFailure = "Sanity screenshot failed: " + message.getString();
                return;
            }
            LogUtils.getLogger().info("SANITY_SCENE_SCREENSHOT: {}", name);
        }));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
