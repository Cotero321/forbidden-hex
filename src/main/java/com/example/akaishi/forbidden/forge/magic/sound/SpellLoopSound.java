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
    private final boolean inkLayer;
    private final String sourceId;
    private final float basePitch;

    SpellLoopSound(ClientLevel world, SpellVisual visual, Entity entity, SpellSoundEvent event) {
        this(world, visual, entity, event, false);
    }

    SpellLoopSound(ClientLevel world, SpellVisual visual, Entity entity, SpellSoundEvent event, boolean inkLayer) {
        super(AkaishiForbiddenSounds.spell(inkLayer ? "spell.ink."
                        + SpellSoundCatalog.known("inks", visual.inkId(), "xu") + ".charge" : "spell.source."
                        + SpellSoundCatalog.known("sources", visual.sourceId(), "san")
                        + (event != null && event.phase() == SpellSoundEvent.Phase.CHARGE ? ".charge" : ".sustain")),
                SoundSource.PLAYERS, RandomSource.create(entity != null ? entity.getUUID().getLeastSignificantBits()
                        : event.instance().getLeastSignificantBits()));
        this.world = world;
        this.entity = entity;
        this.event = event;
        this.charging = event != null && event.phase() == SpellSoundEvent.Phase.CHARGE;
        this.inkLayer = inkLayer;
        this.sourceId = SpellSoundCatalog.known("sources", visual.sourceId(), "san");
        this.created = world.getGameTime();
        this.refreshed = created;
        this.basePitch = charging ? 1f : Mth.clamp(0.86f
                + (float) Math.log(SpellSoundCatalog.frequency(visual.effectId()) / 180f) * 0.13f, 0.72f, 1.3f);
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
                : charging ? chargeGain(sourceId, progress, inkLayer) : 0.22f);
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

    /** 源决定收束轮廓，两层共享包络，墨只改变音色与混合权重。 */
    static float chargeGain(String source, float progress, boolean inkLayer) {
        float p = Mth.clamp(progress, 0f, 1f);
        double phase = Math.PI * 2d * (1.5d * p + 1.5d * p * p);
        float shape = switch (source) {
            case "life" -> 0.76f + 0.24f * (float) Math.pow(0.5d + 0.5d * Math.cos(phase), 2d);
            case "dark" -> 0.70f + 0.30f * p * p;
            case "white" -> 0.94f + 0.06f * (float) Math.cos(phase);
            default -> 0.84f + 0.10f * (float) Math.cos(phase)
                    + 0.06f * (1f - p) * (float) Math.cos(phase * 1.5d + Math.PI / 2d);
        };
        float focus = p * p * (3f - 2f * p);
        return (0.09f + 0.15f * focus) * shape * (inkLayer ? 0.64f + 0.16f * focus : 0.72f);
    }
}
