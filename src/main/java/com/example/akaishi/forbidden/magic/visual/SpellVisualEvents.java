package com.example.akaishi.forbidden.magic.visual;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import com.example.akaishi.forbidden.api.magic.exec.MagicContext;
import com.example.akaishi.forbidden.magic.table.MagicIds;

import java.util.function.Consumer;

public final class SpellVisualEvents {
    public static final ResourceLocation CHANNEL = new ResourceLocation("forbidden_hex", "spell_visual");
    private SpellVisualEvents() { }

    public static void registerClient(Consumer<SpellVisualEvent> receiver) {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, CHANNEL, (buf, context) -> {
            SpellVisualEvent event = SpellVisualEvent.read(buf);
            context.queue(() -> receiver.accept(event));
        });
    }

    public static void emit(MagicContext context, Entity target, Vec3 at) {
        if (!(context.level() instanceof ServerLevel level)) return;
        String delivery = context.snapshot().deliveryId();
        // 只跳过"布场"：法阵由世界实体承载，其**整个生命周期**（含落点表现）都由实体渲染器负责。
        // 而投射物（X2）的**飞行体**虽由实体渲染器画，**命中那一下**却没人管 ——
        // 故 X2 照常派发，由表现层只出"到达时的收束与冲击"（见 SpellVisualPainter#paintCarrier 的 ORB 分支）。
        if (MagicIds.Delivery.PLACE.equals(delivery)) return;
        Vec3 to = target == null ? at : target.position().add(0, target.getBbHeight() * 0.5, 0);
        Vec3 from = context.caster() == null ? at : context.caster().getEyePosition();
        int life = MagicIds.Delivery.ATTACH.equals(delivery) ? Math.min(100, Math.round(context.snapshot().durationSeconds() * 20)) : 8;
        SpellVisual visual = SpellVisual.from(context);
        if (visual == null) return;
        SpellVisualEvent event = new SpellVisualEvent(visual, from, to,
                target == null ? -1 : target.getId(), Math.max(1, life));
        for (var player : level.players()) {
            if (player.position().distanceToSqr(to) > 96d * 96d
                    && player.position().distanceToSqr(from) > 96d * 96d) continue;
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            event.write(buf);
            NetworkManager.sendToPlayer(player, CHANNEL, buf);
        }
    }
}
