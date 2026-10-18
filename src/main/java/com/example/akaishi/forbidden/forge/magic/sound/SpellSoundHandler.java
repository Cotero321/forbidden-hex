package com.example.akaishi.forbidden.forge.magic.sound;

import com.example.akaishi.forbidden.forge.client.ForbiddenVisualConfig;
import com.example.akaishi.forbidden.magic.circle.SpellCircleEntity;
import com.example.akaishi.forbidden.magic.projectile.SpellProjectile;
import com.example.akaishi.forbidden.magic.table.MagicIds;
import com.example.akaishi.forbidden.magic.visual.LinkVisualEvent;
import com.example.akaishi.forbidden.magic.visual.SpellVisual;
import com.example.akaishi.forbidden.magic.visual.SpellVisualEvent;
import com.example.akaishi.forbidden.sound.AkaishiForbiddenSounds;
import com.example.akaishi.forbidden.sound.SpellSoundCatalog;
import com.example.akaishi.forbidden.sound.SpellSoundEvent;
import com.example.akaishi.forbidden.sound.SpellSoundEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** All sound mutations execute on the client thread; budgets include complete one-shot tails. */
public final class SpellSoundHandler {
    public static final SpellSoundHandler INSTANCE = new SpellSoundHandler();
    private record Voice(SoundInstance sound, long expires) { }
    private record RetiringLoop(SpellLoopSound sound, long expires) { }
    private final Map<UUID, SpellLoopSound> loops = new LinkedHashMap<>();
    private final List<RetiringLoop> retiring = new ArrayList<>();
    private final List<Voice> voices = new ArrayList<>();
    private final Map<String, Long> recent = new HashMap<>();
    private final Set<UUID> knownEntities = new HashSet<>();
    private ClientLevel world;
    private long budgetTick = Long.MIN_VALUE;
    private int perTick;

    private SpellSoundHandler() { }
    public void register() { SpellSoundEvents.registerClient(this::accept); }

    private void syncWorld(ClientLevel level) {
        if (world == level) return;
        clear();
        world = level;
    }

    public void clear() {
        var manager = Minecraft.getInstance().getSoundManager();
        for (SpellLoopSound loop : loops.values()) { loop.halt(); manager.stop(loop); }
        for (RetiringLoop tail : retiring) { tail.sound().halt(); manager.stop(tail.sound()); }
        for (Voice voice : voices) manager.stop(voice.sound());
        loops.clear(); retiring.clear(); voices.clear(); recent.clear(); knownEntities.clear();
    }

    public void accept(SpellSoundEvent event) {
        ClientLevel level = Minecraft.getInstance().level;
        syncWorld(level);
        if (level == null || !level.dimension().location().equals(event.dimension())) return;
        SpellVisual visual = event.visual();
        switch (event.phase()) {
            case CHARGE, CHANNEL, VOLLEY -> {
                if (!enabled() || !near(event.position())) return;
                SpellLoopSound loop = loops.get(event.instance());
                boolean charge = event.phase() == SpellSoundEvent.Phase.CHARGE;
                if (loop != null && (loop.isStopped() || loop.charging() != charge)) {
                    stopLoop(event.instance());
                    loop = null;
                }
                if (loop == null && loops.size() < maxLoops()
                        && totalVoices() < ForbiddenVisualConfig.SPELL_SOUND_VOICES.get()) {
                    loop = new SpellLoopSound(level, visual, null, event);
                    loops.put(event.instance(), loop);
                    Minecraft.getInstance().getSoundManager().play(loop);
                }
                if (loop != null) loop.refresh(event);
            }
            case RELEASE, SEGMENT -> {
                if (event.phase() == SpellSoundEvent.Phase.RELEASE) stopLoop(event.instance());
                if (!admit("release:" + event.instance(), 1L)) return;
                release(visual, event.position(), event.phase() == SpellSoundEvent.Phase.SEGMENT ? 0.68f : 1f);
            }
            case END, INTERRUPT -> {
                stopLoop(event.instance());
                if (admit("end:" + event.instance(), 100L)) {
                    play(event.phase() == SpellSoundEvent.Phase.END ? "spell.end" : "spell.interrupt",
                            event.position(), 0.25f, pitch(visual));
                }
            }
            case IMPACT, SHATTER -> {
                stopLoop(event.instance());
                if (!admit("terminal:" + event.instance(), 100L)) return;
                if (event.phase() == SpellSoundEvent.Phase.SHATTER) play("spell.shatter", event.position(), 0.42f, 1f);
                else impact(visual, event.position(), "collision");
            }
        }
    }

    private void release(SpellVisual visual, Vec3 position, float gain) {
        String source = SpellSoundCatalog.known("sources", visual.sourceId(), "san");
        String carrier = SpellSoundCatalog.known("carriers", visual.deliveryId(), "instant");
        play("spell.source." + source + ".release", position, 0.34f * gain, pitch(visual));
        play("spell.carrier." + carrier, position, 0.4f * gain, pitch(visual));
        accent(visual, position, 0.25f * gain);
    }

    public void visual(SpellVisualEvent event) {
        syncWorld(Minecraft.getInstance().level);
        if (world == null || MagicIds.Delivery.PLACE.equals(event.visual().deliveryId())) return;
        impact(event.visual(), event.to(), MagicIds.Delivery.PROJECTILE.equals(event.visual().deliveryId())
                ? "collision" : "effect");
    }

    public void link(LinkVisualEvent event) {
        syncWorld(Minecraft.getInstance().level);
        if (world != null) impact(event.visual(), event.to(), "link");
    }

    private void impact(SpellVisual visual, Vec3 position, String kind) {
        String cell = kind + ":" + visual.effectId() + ":" + (int) Math.floor(position.x / 3d)
                + ":" + (int) Math.floor(position.y / 3d) + ":" + (int) Math.floor(position.z / 3d);
        if (!admit(cell, MagicIds.Delivery.ATTACH.equals(visual.deliveryId()) ? 12L : 4L)) return;
        String effect = SpellSoundCatalog.known("effects", visual.effectId(), "damage");
        float power = Float.isFinite(visual.power()) ? Mth.clamp(visual.power(), 0f, 20f) : 0f;
        play("spell.effect." + effect, position, 0.43f + power * 0.008f, pitch(visual));
        accent(visual, position, 0.18f);
    }

    private void accent(SpellVisual visual, Vec3 position, float volume) {
        String main = SpellSoundCatalog.known("inks", visual.inkId(), "xu");
        play("spell.ink." + main, position, volume, 1f);
        for (String ink : visual.slotInks()) {
            if (!ink.isEmpty() && !ink.equals(main) && SpellSoundCatalog.ids("inks").contains(ink)) {
                play("spell.ink." + ink, position, volume * 0.38f, 1.12f);
                break;
            }
        }
    }

    private static float pitch(SpellVisual visual) {
        return Mth.clamp(0.98f + (visual.wordCount() - 2) * 0.012f, 0.92f, 1.08f);
    }

    private boolean admit(String key, long interval) {
        if (world == null || !enabled()) return false;
        long now = world.getGameTime();
        Long previous = recent.get(key);
        if (previous != null && now - previous < interval) return false;
        if (recent.size() >= 512) recent.entrySet().removeIf(entry -> now - entry.getValue() > 40L);
        if (recent.size() >= 512) return false;
        recent.put(key, now);
        return true;
    }

    private void play(String name, Vec3 position, float volume, float pitch) {
        if (world == null || !enabled() || !near(position)) return;
        long now = world.getGameTime();
        if (budgetTick != now) { budgetTick = now; perTick = 0; }
        if (perTick >= 10 || totalVoices() >= ForbiddenVisualConfig.SPELL_SOUND_VOICES.get()) return;
        perTick++;
        var sound = new SimpleSoundInstance(AkaishiForbiddenSounds.spell(name), SoundSource.PLAYERS,
                volume * ForbiddenVisualConfig.SPELL_SOUND_VOLUME.get().floatValue(), pitch,
                RandomSource.create(world.random.nextLong()), position.x, position.y, position.z);
        voices.add(new Voice(sound, now + 35L));
        Minecraft.getInstance().getSoundManager().play(sound);
    }

    private boolean near(Vec3 position) {
        Entity listener = Minecraft.getInstance().getCameraEntity();
        return listener != null && listener.position().distanceToSqr(position) <= 28d * 28d;
    }
    private static boolean enabled() { return ForbiddenVisualConfig.SPELL_SOUNDS.get() && ForbiddenVisualConfig.SPELL_SOUND_VOLUME.get() > 0d; }
    private static int maxLoops() { return Math.max(1, ForbiddenVisualConfig.SPELL_SOUND_VOICES.get() / 3); }

    private void stopLoop(UUID id) {
        SpellLoopSound loop = loops.remove(id);
        if (loop == null) return;
        if (loop.isStopped()) Minecraft.getInstance().getSoundManager().stop(loop);
        else {
            loop.fade();
            retiring.add(new RetiringLoop(loop, world.getGameTime() + SpellLoopSound.FADE_TICKS + 2L));
        }
    }

    @SubscribeEvent
    public void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); world = null; }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ClientLevel level = Minecraft.getInstance().level;
        syncWorld(level);
        if (world == null || Minecraft.getInstance().isPaused()) return;
        if (!enabled()) { clear(); return; }
        long now = world.getGameTime();
        voices.removeIf(voice -> {
            if (now < voice.expires()) return false;
            Minecraft.getInstance().getSoundManager().stop(voice.sound());
            return true;
        });
        recent.entrySet().removeIf(entry -> now - entry.getValue() > 120L);
        var manager = Minecraft.getInstance().getSoundManager();
        Iterator<Map.Entry<UUID, SpellLoopSound>> iterator = loops.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var loop = entry.getValue();
            if (loop.isStopped() || loop.fading() && !manager.isActive(loop)) {
                loop.halt(); manager.stop(loop); iterator.remove();
            } else if (!loop.fading() && now - loop.created() > 6L && !manager.isActive(loop)) {
                manager.stop(loop); manager.play(loop);
            }
        }
        retiring.removeIf(tail -> {
            if (!tail.sound().isStopped() && now < tail.expires()) return false;
            tail.sound().halt();
            manager.stop(tail.sound());
            return true;
        });
        Set<UUID> loaded = new HashSet<>();
        for (Entity entity : world.entitiesForRendering()) {
            SpellVisual visual;
            boolean field;
            if (entity instanceof SpellCircleEntity circle && !circle.followsCaster()
                    && circle.visualState() != SpellCircleEntity.VisualState.FADING) {
                visual = circle.visual(); field = true;
            } else if (entity instanceof SpellProjectile projectile && !projectile.isRemoved()) {
                visual = projectile.visual(); field = false;
            } else continue;
            if (entity.isRemoved() || visual.effectId().isEmpty() || !near(entity.position())) continue;
            loaded.add(entity.getUUID());
            boolean first = knownEntities.add(entity.getUUID());
            if (!loops.containsKey(entity.getUUID()) && loops.size() < maxLoops()
                    && totalVoices() < ForbiddenVisualConfig.SPELL_SOUND_VOICES.get()) {
                SpellLoopSound loop = new SpellLoopSound(world, visual, entity, null);
                loops.put(entity.getUUID(), loop);
                manager.play(loop);
                if (first && entity.tickCount < 12 && field) release(visual, entity.position(), 0.7f);
            }
            if (field && entity.tickCount % 20 == 0 && admit("pulse:" + entity.getUUID(), 18L)) {
                play("spell.pulse", entity.position(), 0.13f, pitch(visual));
                play("spell.effect." + SpellSoundCatalog.known("effects", visual.effectId(), "damage"),
                        entity.position(), 0.16f, 0.84f);
            }
        }
        knownEntities.retainAll(loaded);
    }

    public boolean hasPlayingLoop() {
        var manager = Minecraft.getInstance().getSoundManager();
        return loops.values().stream().anyMatch(loop -> !loop.isStopped() && manager.isActive(loop));
    }
    public int activeLoops() { return loops.size(); }
    public int retiringLoops() { return retiring.size(); }
    public int activeVoices() { return voices.size(); }
    public int totalVoices() { return voices.size() + loops.size() + retiring.size(); }
}
