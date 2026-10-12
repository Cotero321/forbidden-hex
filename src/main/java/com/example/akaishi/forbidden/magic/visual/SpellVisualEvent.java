package com.example.akaishi.forbidden.magic.visual;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

/** 仅携带本次实际生效的位置与术式，不下发静态表。 */
public record SpellVisualEvent(SpellVisual visual, Vec3 from, Vec3 to, int targetId, int lifeTicks) {
    public SpellVisualEvent {
        if (!finite(from) || !finite(to) || from.distanceToSqr(to) > 256d * 256d) {
            throw new IllegalArgumentException("Invalid spell endpoints");
        }
        lifeTicks = Math.max(1, Math.min(100, lifeTicks));
    }

    private static boolean finite(Vec3 position) {
        return Double.isFinite(position.x) && Double.isFinite(position.y) && Double.isFinite(position.z);
    }

    public void write(FriendlyByteBuf buf) {
        visual.write(buf);
        buf.writeDouble(from.x).writeDouble(from.y).writeDouble(from.z);
        buf.writeDouble(to.x).writeDouble(to.y).writeDouble(to.z);
        buf.writeInt(targetId);
        buf.writeVarInt(lifeTicks);
    }

    public static SpellVisualEvent read(FriendlyByteBuf buf) {
        SpellVisual visual = SpellVisual.read(buf);
        Vec3 from = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        Vec3 to = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        return new SpellVisualEvent(visual, from, to, buf.readInt(), buf.readVarInt());
    }
}
