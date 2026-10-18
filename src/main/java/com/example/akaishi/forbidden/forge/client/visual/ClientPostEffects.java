package com.example.akaishi.forbidden.forge.client.visual;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.effect.UnnameableClientAmbience;
import com.example.akaishi.forbidden.forge.client.ForbiddenVisualConfig;
import com.example.akaishi.forbidden.mixin.PostChainAccess;
import com.example.akaishi.forbidden.sanity.ClientSanityVision;
import com.example.akaishi.forbidden.visual.PostEffectPolicy;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.DoubleSupplier;

/** Owns at most one local post chain and never replaces a chain installed by another renderer. */
public final class ClientPostEffects {
    @FunctionalInterface
    public interface UniformWriter {
        void apply(EffectInstance program, float strength);
    }

    public record Definition(ResourceLocation chain, int priority, float threshold,
                             DoubleSupplier strength, Map<ResourceLocation, UniformWriter> programs) {
        public Definition {
            Objects.requireNonNull(chain, "chain");
            Objects.requireNonNull(strength, "strength");
            if (!Float.isFinite(threshold) || threshold < 0f || threshold >= 1f) {
                throw new IllegalArgumentException("Post-effect threshold must be within [0, 1)");
            }
            programs = Map.copyOf(programs);
        }
    }

    private static final ResourceLocation SANITY = new ResourceLocation(AkaishiMod.MOD_ID, "sanity_vision");
    private static final ResourceLocation UNNAMEABLE = new ResourceLocation(AkaishiMod.MOD_ID, "unnameable");
    public static final ClientPostEffects INSTANCE = new ClientPostEffects();

    public static boolean sanityPostActive() {
        return SANITY.equals(INSTANCE.ownedId) && INSTANCE.ownedChain != null
                && Minecraft.getInstance().gameRenderer.currentEffect() == INSTANCE.ownedChain;
    }

    private final Map<ResourceLocation, Definition> definitions = new LinkedHashMap<>();
    private final Map<ResourceLocation, Float> levels = new LinkedHashMap<>();
    private final Set<ResourceLocation> failed = new HashSet<>();
    private final List<PostEffectPolicy.Request> requests = new ArrayList<>();
    private PostChain ownedChain;
    private ResourceLocation ownedId;
    private ClientLevel lastLevel;
    private LocalPlayer lastPlayer;
    private volatile boolean reloadPending;

    private ClientPostEffects() {
        register(SANITY, new Definition(new ResourceLocation(AkaishiMod.MOD_ID, "shaders/post/sanity_vision.json"),
                10, 0.02f, () -> Math.max(ClientSanityVision.gray(), Math.max(ClientSanityVision.blood(), ClientSanityVision.mono())),
                Map.of(SANITY, (program, strength) -> {
                    float scale = ForbiddenVisualConfig.POST_STRENGTH.get().floatValue();
                    scalar(program, "AkaishiGray", ClientSanityVision.gray() * scale);
                    scalar(program, "AkaishiBlood", ClientSanityVision.blood() * scale);
                    scalar(program, "AkaishiMono", ClientSanityVision.mono() * scale);
                })));
        register(UNNAMEABLE, new Definition(new ResourceLocation(AkaishiMod.MOD_ID, "shaders/post/unnameable.json"),
                100, 0.02f, UnnameableClientAmbience::peek,
                Map.of(UNNAMEABLE, (program, strength) -> scalar(program, "AkaishiIntensity", strength))));
    }

    /** Call on the client thread during initialization; priorities are higher-first. */
    public void register(ResourceLocation id, Definition definition) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(definition, "definition");
        if (definitions.putIfAbsent(id, definition) != null) {
            throw new IllegalArgumentException("Duplicate post effect: " + id);
        }
    }

    public void onResourceReload() {
        reloadPending = true;
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != lastLevel || minecraft.player != lastPlayer) {
            release(minecraft);
            ClientSanityVision.reset();
            UnnameableClientAmbience.reset();
            lastLevel = minecraft.level;
            lastPlayer = minecraft.player;
        }
        if (reloadPending) {
            release(minecraft);
            failed.clear();
            reloadPending = false;
        }
        if (minecraft.level != null && minecraft.player != null && !minecraft.isPaused()) {
            ClientSanityVision.update();
            UnnameableClientAmbience.update();
        }
        PostChain current = minecraft.gameRenderer.currentEffect();
        if (current != ownedChain) {
            ownedChain = null;
            ownedId = null;
        }
        ResourceLocation wanted = requested(minecraft);
        switch (PostEffectPolicy.decide(current != null && current != ownedChain, ownedId, wanted)) {
            case LOAD -> load(minecraft, wanted);
            case RELEASE -> release(minecraft);
            case KEEP, YIELD -> { }
        }
    }

    @SubscribeEvent
    public void beforePostProcessing(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL && ownedChain != null
                && !ClientShaderCompatibility.allowsCustomPrograms()) {
            release(Minecraft.getInstance());
            levels.clear();
            requests.clear();
        }
    }

    private ResourceLocation requested(Minecraft minecraft) {
        levels.clear();
        requests.clear();
        if (minecraft.level == null || minecraft.player == null || !ForbiddenVisualConfig.POST_EFFECTS.get()
                || ForbiddenVisualConfig.POST_STRENGTH.get() <= 0d || !ClientShaderCompatibility.allowsCustomPrograms()) {
            return null;
        }
        definitions.forEach((id, definition) -> {
            if (failed.contains(id)) {
                return;
            }
            try {
                double raw = definition.strength().getAsDouble();
                float value = Double.isFinite(raw) ? (float) Math.max(0d, Math.min(1d, raw)) : 0f;
                value *= ForbiddenVisualConfig.POST_STRENGTH.get().floatValue();
                if (value > definition.threshold()) {
                    levels.put(id, value);
                    requests.add(new PostEffectPolicy.Request(id, definition.priority(), value));
                }
            } catch (RuntimeException exception) {
                fail(id, exception);
            }
        });
        return PostEffectPolicy.select(requests, failed);
    }

    private void load(Minecraft minecraft, ResourceLocation id) {
        release(minecraft);
        Definition definition = definitions.get(id);
        try {
            minecraft.gameRenderer.loadEffect(definition.chain());
            PostChain loaded = minecraft.gameRenderer.currentEffect();
            if (loaded != null && definition.chain().toString().equals(loaded.getName())) {
                ownedChain = loaded;
                ownedId = id;
            } else {
                fail(id, null);
            }
        } catch (RuntimeException exception) {
            PostChain partial = minecraft.gameRenderer.currentEffect();
            if (partial != null && definition.chain().toString().equals(partial.getName())) {
                ownedChain = partial;
                ownedId = id;
                release(minecraft);
            }
            fail(id, exception);
        }
    }

    private void fail(ResourceLocation id, RuntimeException exception) {
        if (failed.add(id)) {
            LogUtils.getLogger().warn("Post effect {} disabled until resource reload", id, exception);
        }
    }

    private void release(Minecraft minecraft) {
        if (ownedChain != null && minecraft.gameRenderer.currentEffect() == ownedChain) {
            minecraft.gameRenderer.shutdownEffect();
        }
        ownedChain = null;
        ownedId = null;
    }

    /** Called by PostPassMixin; only programs declared by our actually owned chain receive uniforms. */
    public void applyUniforms(PostPass pass) {
        if (ownedChain == null || failed.contains(ownedId)
                || Minecraft.getInstance().gameRenderer.currentEffect() != ownedChain
                || !((PostChainAccess) ownedChain).forbiddenHex$getPasses().contains(pass)) {
            return;
        }
        EffectInstance program = pass.getEffect();
        Definition definition = definitions.get(ownedId);
        for (Map.Entry<ResourceLocation, UniformWriter> entry : definition.programs().entrySet()) {
            if (entry.getKey().toString().equals(program.getName())) {
                try {
                    scalar(program, "AkaishiTime", ClientVisualClock.realSeconds());
                    entry.getValue().apply(program, levels.getOrDefault(ownedId, 0f));
                } catch (RuntimeException exception) {
                    fail(ownedId, exception);
                }
                return;
            }
        }
    }

    public static void scalar(EffectInstance program, String name, float value) {
        Uniform uniform = program.getUniform(name);
        if (uniform != null) {
            if (uniform.getCount() != 1 || uniform.getType() != Uniform.getTypeFromString("float")) {
                throw new IllegalArgumentException("Post-effect uniform must be a scalar float: " + name);
            }
            uniform.set(Float.isFinite(value) ? value : 0f);
        }
    }

    @SubscribeEvent
    public void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        release(Minecraft.getInstance());
        levels.clear();
        requests.clear();
        ClientSanityVision.reset();
        UnnameableClientAmbience.reset();
        lastLevel = null;
        lastPlayer = null;
    }
}
