package com.example.akaishi.forbidden.forge.magic.sound;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.api.magic.SpellInput;
import com.example.akaishi.forbidden.api.magic.SpellPlans;
import com.example.akaishi.forbidden.api.magic.exec.MagicContext;
import com.example.akaishi.forbidden.entity.AkaishiForbiddenEntities;
import com.example.akaishi.forbidden.forge.client.ForbiddenVisualConfig;
import com.example.akaishi.forbidden.magic.cast.MagicCastSession;
import com.example.akaishi.forbidden.magic.circle.SpellCircleEntity;
import com.example.akaishi.forbidden.magic.formula.SpellPlanAdapter;
import com.example.akaishi.forbidden.magic.visual.SpellVisual;
import com.example.akaishi.forbidden.sound.AkaishiForbiddenSounds;
import com.example.akaishi.forbidden.sound.SpellSoundCatalog;
import com.example.akaishi.forbidden.sound.SpellSoundEvent;
import com.example.akaishi.forbidden.sound.SpellSoundEvents;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Isolated runtime validation; the phase sender is server-side, not a complete gameplay cast. */
@Mod.EventBusSubscriber(modid = AkaishiMod.MOD_ID, value = Dist.CLIENT)
public final class ClientSpellAudioTest {
    private static int elapsed;
    private static int waiting;
    private static boolean prepared;
    private static volatile MagicCastSession session;
    private static UUID owner;
    private static CompletableFuture<Void> reload;
    private static SpellCircleEntity field;
    private static boolean verifiedStart;
    private static volatile boolean actualCastStarted;
    private static volatile boolean actualCastDelivered;
    private static int previousBudget;
    private static boolean budgetChanged;

    private ClientSpellAudioTest() { }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("forbidden_hex.audioSceneTest") || event.phase != TickEvent.Phase.END) return;
        try {
            runScene();
        } catch (RuntimeException | Error failure) {
            restoreBudget();
            throw failure;
        }
    }

    @SubscribeEvent
    public static void logout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        restoreBudget();
    }

    private static void restoreBudget() {
        if (!budgetChanged) return;
        ForbiddenVisualConfig.SPELL_SOUND_VOICES.set(previousBudget);
        budgetChanged = false;
    }

    private static void runScene() {
        Minecraft minecraft = Minecraft.getInstance();
        if (++waiting > 1800) throw new IllegalStateException("Audio scene timed out");
        if (minecraft.level == null || minecraft.player == null || minecraft.getOverlay() != null
                || minecraft.getSingleplayerServer() == null) return;
        if (!prepared) {
            prepared = true;
            verifyResources(minecraft);
            verifyChargeMix(minecraft);
            owner = minecraft.player.getUUID();
            prepare(minecraft);
            return;
        }
        if (session == null) return;
        elapsed++;
        var handler = SpellSoundHandler.INSTANCE;
        if (elapsed == 20) {
            require(handler.activeLoops() == 2, "Server charge did not create source and ink layers");
            require(handler.hasPlayingLoop(), "OpenAL never activated the charge sound");
            verifiedStart = true;
            send(minecraft, SpellSoundEvent.Phase.CHANNEL);
        }
        if (elapsed == 35) send(minecraft, SpellSoundEvent.Phase.SEGMENT);
        if (elapsed == 50) send(minecraft, SpellSoundEvent.Phase.INTERRUPT);
        if (elapsed == 58) {
            require(handler.activeLoops() == 0, "Interrupted cast retained a loop");
            send(minecraft, SpellSoundEvent.Phase.CHARGE);
        }
        if (elapsed == 78) send(minecraft, SpellSoundEvent.Phase.RELEASE);
        if (elapsed == 80) send(minecraft, SpellSoundEvent.Phase.END);
        if (elapsed == 88) {
            require(handler.activeLoops() == 0, "Normal end retained a loop");
            field = new SpellCircleEntity(AkaishiForbiddenEntities.SPELL_CIRCLE.get(), minecraft.level);
            field.arm(SpellVisual.assemble("hu", "place", "yao", "white", 2f, 2f, 5f, List.of("hu")), point -> {}, 400);
            field.setPos(minecraft.player.position().add(1d, 0d, 0d));
            minecraft.level.putNonPlayerEntity(-199302, field);
        }
        if (elapsed == 103) {
            require(handler.activeLoops() >= 1, "Placed field did not start loop");
            require(handler.hasPlayingLoop(), "OpenAL never activated field loop");
            field.discard();
        }
        if (elapsed == 114) {
            require(handler.activeLoops() == 0, "Removed field retained loop");
            send(minecraft, SpellSoundEvent.Phase.CHARGE);
        }
        if (elapsed == 126) reload = minecraft.reloadResourcePacks();
        if (elapsed >= 140 && elapsed < 168 && reload != null && reload.isDone()) {
            reload.join();
            reload = null;
            verifyResources(minecraft);
            send(minecraft, SpellSoundEvent.Phase.CHARGE);
        }
        if (elapsed == 168) {
            require(reload == null, "Audio resource reload not complete");
            require(handler.activeLoops() > 0 && handler.hasPlayingLoop(), "Loop did not resume after resource reload");
            handler.clear();
            require(handler.activeLoops() == 0 && handler.activeVoices() == 0, "Audio teardown retained voices");
            beginActualCast(minecraft);
        }
        if (elapsed == 172) {
            require(actualCastStarted && handler.activeLoops() > 0, "Cast manager did not start charge audio");
        }
        if (elapsed == 225) {
            require(actualCastDelivered, "Cast manager never delivered spell");
            require(handler.activeLoops() == 0, "Completed cast retained audio loop");
            handler.clear();
            previousBudget = ForbiddenVisualConfig.SPELL_SOUND_VOICES.get();
            budgetChanged = true;
            ForbiddenVisualConfig.SPELL_SOUND_VOICES.set(12);
            verifyFade(minecraft);
            localPhase(minecraft, new UUID(9L, 1L), SpellSoundEvent.Phase.CHARGE);
        }
        if (elapsed == 235) {
            require(handler.hasPlayingLoop(), "Transition probe never activated");
            localPhase(minecraft, new UUID(9L, 1L), SpellSoundEvent.Phase.CHANNEL);
            require(handler.activeLoops() == 1 && handler.retiringLoops() == 2,
                    "Phase switch did not retire both source and ink layers");
        }
        if (elapsed == 245) {
            require(handler.retiringLoops() == 0 && handler.hasPlayingLoop(), "Phase tail failed to retire");
            localPhase(minecraft, new UUID(9L, 1L), SpellSoundEvent.Phase.INTERRUPT);
            require(handler.activeLoops() == 0 && handler.retiringLoops() == 1,
                    "Interrupt hard-stopped instead of retiring loop");
        }
        if (elapsed == 255) {
            require(handler.totalVoices() <= 1 && handler.retiringLoops() == 0, "Interrupt tail leaked");
            handler.clear();
            for (int index = 0; index < 4; index++) localPhase(minecraft, new UUID(14L, index), SpellSoundEvent.Phase.CHANNEL);
            localPhase(minecraft, new UUID(14L, 0L), SpellSoundEvent.Phase.RELEASE);
            localPhase(minecraft, new UUID(14L, 1L), SpellSoundEvent.Phase.RELEASE);
            require(handler.totalVoices() == 10, "Atomic admission probe has wrong voice count");
            localPhase(minecraft, new UUID(14L, 2L), SpellSoundEvent.Phase.END);
            require(handler.totalVoices() == 11, "Atomic admission probe needs exactly one free voice");
            localPhase(minecraft, new UUID(14L, 5L), SpellSoundEvent.Phase.CHARGE);
            require(handler.totalVoices() == 11 && handler.activeLoops() == 1,
                    "Charge partially admitted a source or ink layer");
            handler.clear();
            for (int index = 0; index < 8; index++) {
                UUID id = new UUID(10L, index);
                localPhase(minecraft, id, SpellSoundEvent.Phase.CHARGE);
                localPhase(minecraft, id, SpellSoundEvent.Phase.RELEASE);
                require(handler.totalVoices() <= 12, "Retiring loops exceeded total voice budget");
            }
            require(handler.retiringLoops() > 0, "Budget probe failed to retain outgoing tails");
            for (int index = 0; index < 8; index++) localPhase(minecraft, new UUID(11L, index), SpellSoundEvent.Phase.CHARGE);
            require(handler.totalVoices() <= 12, "Incoming loops ignored outgoing tail budget");
            reload = minecraft.reloadResourcePacks();
        }
        if (elapsed >= 275 && reload != null && reload.isDone()) {
            reload.join();
            reload = null;
            require(handler.retiringLoops() == 0, "Resource reload retained outgoing tails");
            handler.clear();
            require(handler.totalVoices() == 0, "Teardown retained outgoing tails");
            restoreBudget();
            LogUtils.getLogger().info("SPELL_AUDIO_TEST PASSED: events={}, source-ink combinations=41, shared charge envelopes, server-to-client phases, OpenAL playback, interrupt/end, field removal, resource reload, teardown, dual-layer zero-volume fade, crossfade, outgoing-tail budget=12, atomic-layer admission, reload-during-fade; cast_manager=true; full_input_unlock_payment=false; multiplayer=false; start={}", SpellSoundCatalog.events().size(), verifiedStart);
            minecraft.stop();
        }
    }

    private static void verifyChargeMix(Minecraft minecraft) {
        long now = minecraft.level.getGameTime();
        int combinations = 0;
        for (String source : SpellSoundCatalog.ids("sources")) {
            require(SpellLoopSound.chargeGain(source, 1f, false) > SpellLoopSound.chargeGain(source, 0f, false),
                    "Charge failed to gather energy: " + source);
            for (String ink : SpellSoundCatalog.ids("inks")) {
                if (ink.equals("xing") && !source.equals("san")) continue;
                combinations++;
                var visual = SpellVisual.assemble("hu", "instant", ink, source, 1f, 1f, 1f, List.of("hu"));
                for (float progress : List.of(0f, 0.35f, 0.8f, 1f)) {
                    long start = now - Math.round(progress * 100f);
                    var event = new SpellSoundEvent(minecraft.level.dimension().location(), new UUID(13L, 1L),
                            minecraft.player.getId(), SpellSoundEvent.Phase.CHARGE, minecraft.player.position(),
                            start, start + 100L, visual);
                    for (boolean inkLayer : List.of(false, true)) {
                        var loop = new SpellLoopSound(minecraft.level, visual, null, event, inkLayer);
                        String expected = inkLayer ? "spell.ink." + ink + ".charge" : "spell.source." + source + ".charge";
                        require(loop.getLocation().getPath().equals(expected), "Wrong charge layer: " + expected);
                        require(loop.resolve(minecraft.getSoundManager()) != null && loop.getSound() != SoundManager.EMPTY_SOUND,
                                "Charge layer cannot resolve: " + expected);
                        for (int tick = 0; tick < 8; tick++) loop.tick();
                        float gain = SpellLoopSound.chargeGain(source, progress, inkLayer);
                        float volume = gain * ForbiddenVisualConfig.SPELL_SOUND_VOLUME.get().floatValue();
                        require(Float.isFinite(gain) && gain > 0f && gain < 0.25f, "Unbounded charge envelope");
                        require(Math.abs(loop.getVolume() - volume) < 0.00001f, "Layer lost shared source envelope");
                        require(loop.getPitch() == 1f, "Effect pitch overrode source or ink identity");
                        loop.halt();
                    }
                }
            }
        }
        require(combinations == 41, "Legal source-ink coverage changed");
        for (String source : List.of("san", "life", "dark")) {
            require(Math.abs(SpellLoopSound.chargeGain(source, 0.35f, false)
                    - SpellLoopSound.chargeGain("white", 0.35f, false)) > 0.0001f, "Source shapes collapsed");
        }
    }

    private static void verifyFade(Minecraft minecraft) {
        var visual = SpellVisual.assemble("hu", "instant", "xu", "san", 1f, 1f, 1f, List.of("hu"));
        long now = minecraft.level.getGameTime();
        var event = new SpellSoundEvent(minecraft.level.dimension().location(), new UUID(12L, 1L),
                minecraft.player.getId(), SpellSoundEvent.Phase.CHARGE, minecraft.player.position(), now, now + 100L, visual);
        for (boolean inkLayer : List.of(false, true)) {
            var loop = new SpellLoopSound(minecraft.level, visual, null, event, inkLayer);
            require(loop.resolve(minecraft.getSoundManager()) != null && loop.getSound() != SoundManager.EMPTY_SOUND,
                    "Fade probe sound could not resolve");
            for (int tick = 0; tick < 8; tick++) loop.tick();
            float previous = loop.getVolume();
            float pitch = loop.getPitch();
            loop.fade();
            for (int tick = 0; tick < SpellLoopSound.FADE_TICKS; tick++) {
                loop.tick();
                require(loop.getVolume() <= previous, "Outgoing loop volume increased during fade");
                require(loop.getPitch() == pitch, "Outgoing loop changed pitch");
                if (tick < SpellLoopSound.FADE_TICKS - 1) require(!loop.isStopped(), "Loop stopped before fade completed");
                previous = loop.getVolume();
            }
            require(loop.isStopped() && loop.getVolume() == 0f, "Fade did not end at zero volume");
        }
    }

    private static void localPhase(Minecraft minecraft, UUID id, SpellSoundEvent.Phase phase) {
        long now = minecraft.level.getGameTime();
        var visual = SpellVisual.assemble("hu", "instant", "xu", "san", 1f, 1f, 1f, List.of("hu"));
        SpellSoundHandler.INSTANCE.accept(new SpellSoundEvent(minecraft.level.dimension().location(), id,
                minecraft.player.getId(), phase, minecraft.player.position(), now, now + 100L, visual));
    }

    private static void beginActualCast(Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(owner);
            require(player != null, "Missing cast test owner");
            var compiled = SpellPlans.compile(new SpellInput(List.of("tou", "hu"), List.of("chi", "yao"), "chi", "san", 0f, 0));
            require(compiled.ok(), "Cast test formula rejected");
            MagicContext context = new MagicContext(player, player.serverLevel(), player.position(), "san", "chi",
                    List.of("chi", "yao"), List.of("tou", "hu"), 6, SpellPlanAdapter.snapshot(compiled.plan()));
            require(com.example.akaishi.forbidden.magic.cast.MagicCastManager.begin(player, context, null),
                    "Cast manager rejected audio test session");
            com.example.akaishi.forbidden.magic.cast.MagicCastManager.onFirstDelivered(player,
                    () -> actualCastDelivered = true);
            actualCastStarted = com.example.akaishi.forbidden.magic.cast.MagicCastManager.stateOf(player)
                    == com.example.akaishi.forbidden.magic.cast.MagicCastState.CHARGING;
        });
    }

    private static void verifyResources(Minecraft minecraft) {
        require(SpellSoundCatalog.inspect().isEmpty(), SpellSoundCatalog.inspect().toString());
        for (String id : SpellSoundCatalog.events()) {
            var sound = new SimpleSoundInstance(AkaishiForbiddenSounds.spell(id), SoundSource.PLAYERS, 0.1f, 1f,
                    RandomSource.create(42L), 0d, 0d, 0d);
            require(sound.resolve(minecraft.getSoundManager()) != null && sound.getSound() != SoundManager.EMPTY_SOUND,
                    "Sound cannot resolve: " + id);
        }
    }

    private static void prepare(Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(owner);
            if (player == null) throw new IllegalStateException("Missing audio test owner");
            var compiled = SpellPlans.compile(new SpellInput(List.of("liu", "hu"), List.of("chi", "yao"), "chi", "san", 0f, 0));
            require(compiled.ok(), "Audio test formula rejected");
            MagicContext context = new MagicContext(player, player.serverLevel(), player.position(), "san", "chi",
                    List.of("chi", "yao"), List.of("liu", "hu"), 10, SpellPlanAdapter.snapshot(compiled.plan()));
            MagicCastSession value = new MagicCastSession(context, player.level().dimension(),
                    player.level().getGameTime() + 400L, 6, 0f, 20, List.of(), player.getHealth());
            session = value;
            SpellSoundEvents.session(player, value, SpellSoundEvent.Phase.CHARGE);
        });
    }

    private static void send(Minecraft minecraft, SpellSoundEvent.Phase phase) {
        var server = minecraft.getSingleplayerServer();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(owner);
            if (player != null) SpellSoundEvents.session(player, session, phase);
        });
    }

    private static void require(boolean condition, String failure) {
        if (!condition) throw new IllegalStateException(failure);
    }
}
