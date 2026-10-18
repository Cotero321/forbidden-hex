package com.example.akaishi.forbidden.forge.client.visual;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.forge.client.ForbiddenVisualConfig;
import com.example.akaishi.forbidden.forge.magic.visual.SpellRenderTypes;
import com.example.akaishi.forbidden.mixin.PostChainAccess;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Explicit development check: runClient -PvisualSmokeTest, without opening or modifying a world. */
@Mod.EventBusSubscriber(modid = AkaishiMod.MOD_ID, value = Dist.CLIENT)
public final class ClientVisualSmokeTest {
    private static int stage;
    private static int ticks;
    private static CompletableFuture<Void> reload;
    private static boolean reloadVerified;
    private static boolean customProgramsVerified;

    private ClientVisualSmokeTest() { }

    static boolean readyForScene() {
        return !Boolean.getBoolean("forbidden_hex.visualSmokeTest") || reloadVerified;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("forbidden_hex.visualSmokeTest") || event.phase != TickEvent.Phase.END || stage == 2) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        boolean shaderCompatTest = Boolean.getBoolean("forbidden_hex.shaderCompatTest");
        if (++ticks > 2400) {
            throw new IllegalStateException("Visual smoke test timed out waiting for resource reload");
        }
        boolean worldScene = shaderCompatTest || ClientVisualSceneTest.enabled()
                || Boolean.getBoolean("forbidden_hex.circleSceneTest") || Boolean.getBoolean("forbidden_hex.sanitySceneTest");
        if (minecraft.getOverlay() != null || (worldScene && minecraft.level == null)
                || (shaderCompatTest && ClientShaderCompatibility.state() != com.example.akaishi.forbidden.visual.ShaderCompatibility.State.ACTIVE)) {
            return;
        }
        try {
            if (stage == 0) {
                customProgramsVerified = verify(minecraft);
                stage = 1;
                reload = minecraft.reloadResourcePacks();
            } else if (reload.isDone()) {
                reload.join();
                if (!reloadVerified) {
                    require(verify(minecraft) == customProgramsVerified, "Smoke validation changed branch across resource reload");
                    reloadVerified = true;
                    LogUtils.getLogger().info("VISUAL_SMOKE_TEST PASSED: {}; budget and distance bounds; resource reload; shader_state={}",
                            customProgramsVerified ? "custom core shader, material batches, forced vanilla fallback and post programs"
                                    : "vanilla fallback material and shader-binding assertions (custom programs skipped)",
                            ClientShaderCompatibility.state());
                }
                if (Boolean.getBoolean("forbidden_hex.circleSceneTest") || Boolean.getBoolean("forbidden_hex.sanitySceneTest")) {
                    stage = 2;
                } else if (!ClientVisualSceneTest.enabled() || ClientVisualSceneTest.finished()) {
                    stage = 2;
                    minecraft.stop();
                } else {
                    ClientVisualSceneTest.start();
                }
            }
        } catch (IOException | RuntimeException exception) {
            LogUtils.getLogger().error("VISUAL_SMOKE_TEST FAILED", exception);
            throw new IllegalStateException("Visual smoke test failed", exception);
        }
    }

    private static boolean verify(Minecraft minecraft) throws IOException {
        boolean shaderCompatTest = Boolean.getBoolean("forbidden_hex.shaderCompatTest");
        if (!shaderCompatTest) {
            require(ForbiddenVisualConfig.ENERGY_SHADERS.get(), "Custom smoke requires visuals.energyShaders=true");
            require(!ForbiddenVisualConfig.FORCE_VANILLA_RENDERING.get(),
                    "Custom smoke requires visuals.forceVanillaRendering=false");
            require(ClientShaderCompatibility.allowsCustomPrograms(), "Custom smoke requires confirmed inactive or absent shader packs");
        }
        if (shaderCompatTest || ClientVisualSceneTest.enabled() || Boolean.getBoolean("forbidden_hex.circleSceneTest")
                || Boolean.getBoolean("forbidden_hex.sanitySceneTest")) {
            require(minecraft.level != null, "World visual validation requires the isolated test world");
            if (shaderCompatTest) {
                require(ClientShaderCompatibility.state() == com.example.akaishi.forbidden.visual.ShaderCompatibility.State.ACTIVE,
                        "Shader compatibility smoke test did not reach ACTIVE state");
            }
        } else {
            require(minecraft.level == null, "Smoke validation must not run inside an existing world");
        }
        require(ClientVisualBudget.contractCheck(), "Visual budget failed exhaustion, tick, config or world reset checks");
        verifyDetailBounds(minecraft);
        ResourceLocation texture = new ResourceLocation("minecraft", "textures/misc/white.png");
        if (!ClientShaderCompatibility.allowsCustomPrograms()) {
            require(!ClientVisualShaders.available(ClientVisualShaders.ENERGY), "Shader-pack fallback kept the local core shader available");
            require(!SpellRenderTypes.flowEnabled(), "Shader-pack fallback retained custom UV flow");
            require(VisualRenderTypes.material(VisualMaterials.FLOW, texture) == RenderType.entityTranslucentEmissive(texture),
                    "Shader-pack fallback did not return the vanilla emissive render type");
            require(ClientVisualShaders.bind(VisualMaterials.get(VisualMaterials.FLOW))
                    == GameRenderer.getRendertypeEntityTranslucentEmissiveShader(),
                    "Shader-pack fallback bound a local shader");
            return false;
        }
        require(ClientVisualShaders.available(ClientVisualShaders.ENERGY), "Energy shader did not load");
        ShaderInstance shader = ClientVisualShaders.bind(VisualMaterials.get(VisualMaterials.FLOW));
        Uniform flowSpeed = shader.getUniform("FlowSpeed");
        checkScalar(flowSpeed, 0.85f * ForbiddenVisualConfig.FLOW_SPEED.get().floatValue());
        checkScalar(shader.getUniform("PulseStrength"), 0.44f);
        checkScalar(shader.getUniform("EdgeSoftness"), 0.4f);
        require(SpellRenderTypes.flowEnabled() == (Math.abs(java.util.Objects.requireNonNull(flowSpeed).getFloatBuffer().get(0)) > 0.0001f),
                "Trail UV selection disagrees with the material flow state");
        ClientVisualShaders.bind(VisualMaterials.get(VisualMaterials.GLOW));
        checkScalar(shader.getUniform("FlowSpeed"), 0f);
        checkScalar(shader.getUniform("PulseStrength"), 0f);
        checkScalar(shader.getUniform("EdgeSoftness"), 0f);
        checkScalar(shader.getUniform("GlowStrength"), ForbiddenVisualConfig.GLOW_STRENGTH.get().floatValue());
        require(VisualRenderTypes.material(VisualMaterials.FLOW, texture) == VisualRenderTypes.material(VisualMaterials.FLOW, texture),
                "Identical material/texture did not reuse its render type");
        boolean previousFallback = ForbiddenVisualConfig.FORCE_VANILLA_RENDERING.get();
        try {
            ForbiddenVisualConfig.FORCE_VANILLA_RENDERING.set(true);
            require(!ClientShaderCompatibility.allowsCustomPrograms(), "Forced compatibility mode allowed custom programs");
            require(!ClientVisualShaders.available(ClientVisualShaders.ENERGY), "Forced fallback kept the core shader available");
            require(!SpellRenderTypes.flowEnabled(), "Fallback retained custom UV flow");
            require(VisualRenderTypes.material(VisualMaterials.FLOW, texture) == RenderType.entityTranslucentEmissive(texture),
                    "Fallback did not return the vanilla emissive render type");
            require(ClientVisualShaders.bind(VisualMaterials.get(VisualMaterials.FLOW))
                    == GameRenderer.getRendertypeEntityTranslucentEmissiveShader(), "Cached material bound a custom shader during fallback");
            ForbiddenVisualConfig.FORCE_VANILLA_RENDERING.set(false);
            require(ClientVisualShaders.available(ClientVisualShaders.ENERGY), "Core shader did not recover after fallback");
            require(VisualRenderTypes.material(VisualMaterials.FLOW, texture) != RenderType.entityTranslucentEmissive(texture),
                    "Custom material did not recover after fallback");
        } finally {
            ForbiddenVisualConfig.FORCE_VANILLA_RENDERING.set(previousFallback);
        }
        TextureTarget target = new TextureTarget(32, 32, true, Minecraft.ON_OSX);
        try {
            for (String name : List.of("sanity_vision", "unnameable")) {
                ResourceLocation path = new ResourceLocation(AkaishiMod.MOD_ID, "shaders/post/" + name + ".json");
                try (PostChain chain = new PostChain(minecraft.getTextureManager(), minecraft.getResourceManager(), target, path)) {
                    chain.resize(32, 32);
                    for (float brightness : new float[]{0.015f, 0.06f}) {
                        for (float time : new float[]{0f, 1f, 3f, 7f}) {
                            target.setClearColor(brightness, brightness, brightness, 1f);
                            target.clear(Minecraft.ON_OSX);
                            for (PostPass pass : ((PostChainAccess) chain).forbiddenHex$getPasses()) {
                                ClientPostEffects.scalar(pass.getEffect(), "AkaishiTime", time);
                                ClientPostEffects.scalar(pass.getEffect(), "AkaishiGray", 1f);
                                ClientPostEffects.scalar(pass.getEffect(), "AkaishiBlood", 0.7f);
                                ClientPostEffects.scalar(pass.getEffect(), "AkaishiMono", 1f);
                                ClientPostEffects.scalar(pass.getEffect(), "AkaishiIntensity", 1f);
                            }
                            chain.process(0f);
                            target.bindWrite(false);
                            var pixel = org.lwjgl.BufferUtils.createByteBuffer(4);
                            GL11.glReadPixels(16, 16, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
                            for (int channel = 0; channel < 3; channel++) {
                                require(Byte.toUnsignedInt(pixel.get(channel)) > 0,
                                        "Post effect crushed dark particle coverage: " + name + " at " + time);
                            }
                            require(GL11.glGetError() == GL11.GL_NO_ERROR, "OpenGL error while processing " + name);
                        }
                    }
                }
            }
        } finally {
            target.destroyBuffers();
            minecraft.getMainRenderTarget().bindWrite(true);
        }
        return true;
    }

    private static void verifyDetailBounds(Minecraft minecraft) {
        var particles = minecraft.options.particles();
        net.minecraft.client.ParticleStatus previousParticles = java.util.Objects.requireNonNull(particles.get());
        int previousDistance = ForbiddenVisualConfig.EFFECT_DISTANCE.get();
        int previousDetail = ForbiddenVisualConfig.SPELL_DETAIL_DISTANCE.get();
        try {
            ForbiddenVisualConfig.EFFECT_DISTANCE.set(64);
            ForbiddenVisualConfig.SPELL_DETAIL_DISTANCE.set(48);
            particles.set(net.minecraft.client.ParticleStatus.ALL);
            require(ClientVisualBudget.detailScale(0d) == 1f, "Near spell lost full detail");
            require(ClientVisualBudget.detailScale(49d * 49d) == 0.72f, "Distant spell retained full detail");
            require(ClientVisualBudget.detailScale(64d * 64d) == 0.5f, "Boundary spell lost its silhouette");
            require(ClientVisualBudget.detailScale(65d * 65d) == 0f, "Out-of-range spell remained visible");
            for (double invalid : new double[] {-1d, Double.NaN, Double.POSITIVE_INFINITY}) {
                require(ClientVisualBudget.detailScale(invalid) == 0f, "Invalid spell distance was accepted");
            }
            particles.set(net.minecraft.client.ParticleStatus.DECREASED);
            require(ClientVisualBudget.detailScale(0d) == 0.72f, "Reduced particles retained full geometry detail");
            particles.set(net.minecraft.client.ParticleStatus.MINIMAL);
            require(ClientVisualBudget.detailScale(0d) == 0.5f, "Minimal particles retained decoration layers");
            particles.set(net.minecraft.client.ParticleStatus.ALL);
            ForbiddenVisualConfig.EFFECT_DISTANCE.set(16);
            require(ClientVisualBudget.detailScale(17d * 17d) == 0f, "Runtime effect distance was ignored");
        } finally {
            particles.set(previousParticles);
            ForbiddenVisualConfig.EFFECT_DISTANCE.set(previousDistance);
            ForbiddenVisualConfig.SPELL_DETAIL_DISTANCE.set(previousDetail);
        }
    }

    private static void checkScalar(Uniform uniform, float expected) {
        require(uniform != null && Math.abs(uniform.getFloatBuffer().get(0) - expected) < 0.0001f,
                "Material uniform was missing or leaked from another batch");
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new IllegalStateException(message);
        }
    }
}
