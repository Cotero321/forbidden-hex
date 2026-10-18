package com.example.akaishi.forbidden.sound;

import com.example.akaishi.forbidden.magic.visual.SpellVisual;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.UUID;

/** New S2C protocol; existing cast and visual packets are unchanged. */
public record SpellSoundEvent(ResourceLocation dimension, UUID instance, int anchor, Phase phase,
        Vec3 position, long started, long phaseEnd, SpellVisual visual) {
    public enum Phase { CHARGE, CHANNEL, VOLLEY, RELEASE, SEGMENT, END, INTERRUPT, IMPACT, SHATTER }

    public SpellSoundEvent {
        Objects.requireNonNull(dimension);
        Objects.requireNonNull(instance);
        Objects.requireNonNull(phase);
        Objects.requireNonNull(visual);
        Objects.requireNonNull(position);
        if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)
                || Math.abs(position.x) > 3.1E7 || Math.abs(position.z) > 3.1E7
                || Math.abs(position.y) > 2E7) throw new IllegalArgumentException("Invalid sound position");
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(dimension);
        buffer.writeUUID(instance);
        buffer.writeInt(anchor);
        buffer.writeVarInt(phase.ordinal());
        buffer.writeDouble(position.x).writeDouble(position.y).writeDouble(position.z);
        buffer.writeLong(started).writeLong(phaseEnd);
        visual.write(buffer);
    }

    public static SpellSoundEvent read(FriendlyByteBuf buffer) {
        ResourceLocation dimension = buffer.readResourceLocation();
        UUID id = buffer.readUUID();
        int anchor = buffer.readInt();
        int phase = buffer.readVarInt();
        if (phase < 0 || phase >= Phase.values().length) throw new IllegalArgumentException("Invalid sound phase");
        Vec3 position = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        long start = buffer.readLong(), end = buffer.readLong();
        return new SpellSoundEvent(dimension, id, anchor, Phase.values()[phase], position, start, end,
                SpellVisual.read(buffer));
    }
}
