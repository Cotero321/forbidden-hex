package com.example.akaishi.forbidden.magic.visual;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

/** 一次成功链伤害的短暂闪电脉冲；位置用于实体移除后的端点回退。 */
public record LinkVisualEvent(SpellVisual visual, int fromId, int toId, Vec3 from, Vec3 to) {
    public static final int LIFE_TICKS = 5;

    public void write(FriendlyByteBuf buf) {
        visual.write(buf);
        buf.writeInt(fromId);
        buf.writeInt(toId);
        buf.writeDouble(from.x);
        buf.writeDouble(from.y);
        buf.writeDouble(from.z);
        buf.writeDouble(to.x);
        buf.writeDouble(to.y);
        buf.writeDouble(to.z);
    }

    public static LinkVisualEvent read(FriendlyByteBuf buf) {
        SpellVisual visual = SpellVisual.read(buf);
        int fromId = buf.readInt();
        int toId = buf.readInt();
        Vec3 from = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        Vec3 to = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        return new LinkVisualEvent(visual, fromId, toId, from, to);
    }
}
