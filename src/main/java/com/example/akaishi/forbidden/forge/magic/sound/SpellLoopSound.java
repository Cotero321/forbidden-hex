package com.example.akaishi.forbidden.forge.magic.sound;

import com.example.akaishi.forbidden.forge.client.ForbiddenVisualConfig;
import com.example.akaishi.forbidden.magic.circle.SpellCircleEntity;
import com.example.akaishi.forbidden.magic.visual.SpellVisual;
import com.example.akaishi.forbidden.sound.AkaishiForbiddenSounds;
import com.example.akaishi.forbidden.sound.SpellSoundCatalog;
import com.example.akaishi.forbidden.sound.SpellSoundEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

final class SpellLoopSound extends AbstractTickableSoundInstance {
    static final int FADE_TICKS = 6;
    private final ClientLevel world;
    private final Entity entity;
    private SpellSoundEvent event;
    private long refreshed;
    private final long created;
    private int age;
    private int fading;
    private float fadeVolume;
    private final boolean charging;
    private final float basePitch;

    SpellLoopSound(ClientLevel world, SpellVisual visual, Entity entity, SpellSoundEvent event) {
        super(AkaishiForbiddenSounds.spell("spell.source."
                        + SpellSoundCatalog.known("sources", visual.sourceId(), "san")
                        + (event != null && event.phase() == SpellSoundEvent.Phase.CHARGE ? ".charge" : ".sustain")),
                SoundSource.PLAYERS, RandomSource.create(entity != null ? entity.getUUID().getLeastSignificantBits()
                        : event.instance().getLeastSignificantBits()));
        this.world = world;
        this.entity = entity;
        this.event = event;
        this.charging = event != null && event.phase() == SpellSoundEvent.Phase.CHARGE;
        this.created = world.getGameTime();
        this.refreshed = created;
        this.basePitch = Mth.clamp(0.86f + (float) Math.log(SpellSoundCatalog.frequency(visual.effectId()) / 180f) * 0.13f,
                0.72f, 1.3f);
        this.pitch = basePitch;
        this.looping = true;
        this.attenuation = Attenuation.LINEAR;
        this.volume = 0.01f;
        Vec3 at = entity != null ? entity.position() : event.position();
        x = at.x; y = at.y; z = at.z;
    }

    void refresh(SpellSoundEvent value) { event = value; refreshed = world.getGameTime(); }
    void fade() {
        if (fading == 0 && !isStopped()) { fading = FADE_TICKS; fadeVolume = volume; }
    }
    void halt() { volume = 0f; stop(); }
    boolean fading() { return fading > 0; }
    boolean charging() { return charging; }
    long created() { return created; }

    @Override
    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != world || minecraft.player == null) { stop(); return; }
        Entity anchor = entity != null ? entity : world.getEntity(event.anchor());
        boolean valid = entity != null ? !entity.isRemoved() && entity.level() == world
                : world.getGameTime() - refreshed < 60L;
        if (anchor instanceof SpellCircleEntity circle
                && circle.visualState() == SpellCircleEntity.VisualState.FADING) valid = false;
        if (!valid || !ForbiddenVisualConfig.SPELL_SOUNDS.get()
                || minecraft.player.distanceToSqr(x, y, z) > 32d * 32d) fade();
        if (anchor != null && !anchor.isRemoved()) {
            x = anchor.getX(); y = anchor.getY() + (entity == null ? 1d : 0.2d); z = anchor.getZ();
        }
        float progress = charging ? Mth.clamp((world.getGameTime() - event.started())
                / (float) Math.max(1L, event.phaseEnd() - event.started()), 0f, 1f) : 1f;
        float target = (entity instanceof SpellCircleEntity ? 0.14f : entity != null ? 0.11f
                : charging ? 0.12f + 0.13f * progress : 0.22f);
        float gain = Math.min(1f, ++age / 6f);
        if (fading > 0) {
            float remaining = --fading / (float) FADE_TICKS;
            volume = fadeVolume * remaining * remaining;
            if (fading == 0) stop();
        } else {
            volume = target * gain * ForbiddenVisualConfig.SPELL_SOUND_VOLUME.get().floatValue();
        }
        pitch = basePitch;
    }
}
