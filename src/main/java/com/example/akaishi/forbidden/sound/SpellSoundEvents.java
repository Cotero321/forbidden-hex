package com.example.akaishi.forbidden.sound;

import com.example.akaishi.forbidden.magic.cast.MagicCastSession;
import com.example.akaishi.forbidden.magic.cast.MagicCastState;
import com.example.akaishi.forbidden.magic.visual.SpellVisual;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class SpellSoundEvents {
    public static final ResourceLocation CHANNEL = new ResourceLocation("forbidden_hex", "spell_sound_phase");
    public static final double BROADCAST_DISTANCE = 32d;
    private record Audience(MinecraftServer server, ResourceKey<Level> dimension, Set<UUID> listeners) { }
    private static final Map<UUID, Audience> AUDIENCES = new HashMap<>();

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(SpellSoundEvents.class);
    private static long lastWarning;
    private SpellSoundEvents() { }

    public static void register() {
        dev.architectury.event.events.common.LifecycleEvent.SERVER_STOPPED.register(server ->
                AUDIENCES.entrySet().removeIf(entry -> entry.getValue().server() == server));
    }

    private static void safely(Runnable action) {
        try { action.run(); }
        catch (RuntimeException exception) {
            long now = System.nanoTime();
            if (now - lastWarning > 5_000_000_000L) {
                lastWarning = now;
                LOGGER.warn("Spell audio notification failed; gameplay continues", exception);
            }
        }
    }

    public static void registerClient(Consumer<SpellSoundEvent> receiver) {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, CHANNEL, (buffer, context) -> {
            SpellSoundEvent event = SpellSoundEvent.read(buffer);
            context.queue(() -> receiver.accept(event));
        });
    }

    public static void session(ServerPlayer player, MagicCastSession session, SpellSoundEvent.Phase phase) {
        safely(() -> emitSession(player, session, phase));
    }

    private static void emitSession(ServerPlayer player, MagicCastSession session, SpellSoundEvent.Phase phase) {
        SpellVisual visual = SpellVisual.from(session.context());
        if (visual == null) return;
        UUID id = session.soundInstance();
        Audience audience = AUDIENCES.computeIfAbsent(id,
                ignored -> new Audience(player.server, session.dimension(), new HashSet<>()));
        boolean ending = phase == SpellSoundEvent.Phase.END || phase == SpellSoundEvent.Phase.INTERRUPT;
        Vec3 position = player.position().add(0d, 1d, 0d);
        if (!player.level().dimension().equals(session.dimension())) position = session.context().origin();
        SpellSoundEvent event = new SpellSoundEvent(session.dimension().location(), id, player.getId(), phase,
                position, session.chargeEndTick() - Math.max(1, Math.round(session.snapshot().castTimeSeconds() * 20f)),
                session.chargeEndTick(), visual);
        ServerLevel level = player.server.getLevel(session.dimension());
        if (level != null) {
            for (ServerPlayer listener : level.players()) {
                if (listener.position().distanceToSqr(position) <= BROADCAST_DISTANCE * BROADCAST_DISTANCE) {
                    audience.listeners().add(listener.getUUID());
                    if (!ending) send(listener, event);
                }
            }
        }
        if (ending) {
            for (UUID listenerId : audience.listeners()) {
                ServerPlayer listener = audience.server().getPlayerList().getPlayer(listenerId);
                if (listener != null) send(listener, event);
            }
            AUDIENCES.remove(id);
        }
    }

    public static void refresh(ServerPlayer player, MagicCastSession session) {
        SpellSoundEvent.Phase phase = session.state() == MagicCastState.CHARGING ? SpellSoundEvent.Phase.CHARGE
                : session.state() == MagicCastState.CHANNELING ? SpellSoundEvent.Phase.CHANNEL : SpellSoundEvent.Phase.VOLLEY;
        session(player, session, phase);
    }

    public static void abandon(MagicCastSession session) {
        safely(() -> emitAbandon(session));
    }

    private static void emitAbandon(MagicCastSession session) {
        Audience audience = AUDIENCES.remove(session.soundInstance());
        SpellVisual visual = SpellVisual.from(session.context());
        if (audience == null || visual == null) return;
        SpellSoundEvent event = new SpellSoundEvent(audience.dimension().location(), session.soundInstance(), -1,
                SpellSoundEvent.Phase.INTERRUPT, session.context().origin(), 0L, 0L, visual);
        for (UUID id : audience.listeners()) {
            ServerPlayer listener = audience.server().getPlayerList().getPlayer(id);
            if (listener != null) send(listener, event);
        }
    }

    public static void entity(Entity entity, SpellVisual visual, SpellSoundEvent.Phase phase, Vec3 position) {
        safely(() -> emitEntity(entity, visual, phase, position));
    }

    private static void emitEntity(Entity entity, SpellVisual visual, SpellSoundEvent.Phase phase, Vec3 position) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        SpellSoundEvent event = new SpellSoundEvent(level.dimension().location(), entity.getUUID(), entity.getId(),
                phase, position, level.getGameTime(), level.getGameTime(), visual);
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(position) <= BROADCAST_DISTANCE * BROADCAST_DISTANCE) send(player, event);
        }
    }

    private static void send(ServerPlayer player, SpellSoundEvent event) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            event.write(buffer);
            NetworkManager.sendToPlayer(player, CHANNEL, buffer);
        } catch (RuntimeException exception) {
            if (buffer.refCnt() > 0) buffer.release();
            throw exception;
        }
    }
}
