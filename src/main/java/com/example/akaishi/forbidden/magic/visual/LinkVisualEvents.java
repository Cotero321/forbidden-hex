package com.example.akaishi.forbidden.magic.visual;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** LINK 专用伤害脉冲通道，端点仅取真实链成员。 */
public final class LinkVisualEvents {
    public static final ResourceLocation CHANNEL = new ResourceLocation("forbidden_hex", "link_visual");
    private LinkVisualEvents() { }

    public static void registerClient(java.util.function.Consumer<LinkVisualEvent> receiver) {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, CHANNEL, (buf, context) -> {
            LinkVisualEvent event = LinkVisualEvent.read(buf);
            context.queue(() -> receiver.accept(event));
        });
    }

    public static void emit(ServerLevel level, SpellVisual visual, Entity previous, Entity victim) {
        if (visual == null) return;
        Vec3 to = center(victim);
        LinkVisualEvent event = new LinkVisualEvent(visual, previous == null ? -1 : previous.getId(),
                victim.getId(), previous == null ? to : center(previous), to);
        for (var player : level.players()) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            event.write(buf);
            NetworkManager.sendToPlayer(player, CHANNEL, buf);
        }
    }

    private static Vec3 center(Entity entity) {
        return entity.position().add(0, entity.getBbHeight() * 0.5d, 0);
    }
}
