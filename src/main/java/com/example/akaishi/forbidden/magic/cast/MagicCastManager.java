package com.example.akaishi.forbidden.magic.cast;

import com.example.akaishi.forbidden.api.magic.SpellSnapshot;
import com.example.akaishi.forbidden.api.magic.exec.IPersistenceHandler;
import com.example.akaishi.forbidden.api.magic.exec.MagicContext;
import com.example.akaishi.forbidden.api.magic.exec.MagicExecRegistry;
import com.example.akaishi.forbidden.magic.MagicState;
import com.example.akaishi.forbidden.magic.MagicStates;
import com.example.akaishi.forbidden.magic.exec.MagicPipeline;
import com.example.akaishi.forbidden.magic.table.MagicDeliveryTable;
import com.example.akaishi.forbidden.magic.table.MagicIds;
import com.example.akaishi.forbidden.magic.table.MagicSource;
import com.example.akaishi.forbidden.magic.table.MagicWord;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 服务端施法状态机：支付凭据与成功投送账本分开记录。 */
public final class MagicCastManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(MagicCastManager.class);
    public static final float INTERRUPT_DAMAGE_THRESHOLD = 2f;
    public static final float REFUND_RATIO = 0.5f;
    public static final int MAX_CHANNEL_SECONDS = 6;
    public static final int MAX_VOLLEY_SHOTS = 10;
    public static final float CHANNEL_HEAT_PER_SECOND = 2f;
    private static final Map<UUID, MagicCastSession> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, Runnable> FIRST_DELIVERED = new ConcurrentHashMap<>();
    private MagicCastManager() {}

    public static void onFirstDelivered(Player player, Runnable callback) {
        if (isCasting(player)) FIRST_DELIVERED.put(player.getUUID(), callback);
    }

    public static void register() {
        TickEvent.SERVER_LEVEL_POST.register(MagicCastManager::serverTick);
        PlayerEvent.PLAYER_QUIT.register(player -> {
            MagicCastSession session = SESSIONS.get(player.getUUID());
            if (session != null) end(player, session, false, true, 0f);
        });
    }

    public static boolean isCasting(Player player) {
        return player != null && SESSIONS.containsKey(player.getUUID());
    }
    public static MagicCastState stateOf(Player player) {
        MagicCastSession session = player == null ? null : SESSIONS.get(player.getUUID());
        return session == null ? MagicCastState.IDLE : session.state();
    }
    public static boolean inContinuousSegment(Player player) {
        MagicCastState state = stateOf(player);
        return state == MagicCastState.CHANNELING || state == MagicCastState.VOLLEYING;
    }

    /** 松手：引导已生效时正常结束；前摇松手按未生效打断返还。 */
    public static void stop(Player player) {
        if (!(player instanceof ServerPlayer server)) return;
        MagicCastSession session = SESSIONS.get(player.getUUID());
        if (session == null || !MagicIds.Delivery.CHANNEL.equals(session.snapshot().deliveryId())) return;
        if (session.firstFired()) finish(server, session, true);
        else end(server, session, false, true, 0f);
    }

    /** 旧入口仅做兼容；新服务入口必须传入实付凭据。 */
    public static void begin(Player player, MagicContext context) {
        begin(player, context, null);
    }
    public static boolean begin(Player player, MagicContext context, PaymentReceipt firstPayment) {
        if (player == null || context == null || context.snapshot() == null || isCasting(player)) return false;
        SpellSnapshot snapshot = context.snapshot();
        long now = gameTime(player);
        MagicCastSession session = new MagicCastSession(context, player.level().dimension(),
                now + Math.max(1, Math.round(snapshot.castTimeSeconds() * 20f)), snapshot.segments(),
                snapshot.segmentCost(), snapshot.plan() == null ? segmentInterval(snapshot.deliveryId())
                        : Math.max(1, Math.round(snapshot.plan().schedule().intervalSeconds() * 20f)),
                persistenceOf(context), player.getHealth());
        session.setFirstPayment(firstPayment);
        SESSIONS.put(player.getUUID(), session);
        if (player instanceof ServerPlayer server) MagicCastStateS2C.send(server, MagicCastState.CHARGING);
        return true;
    }

    public static void serverTick(ServerLevel level) {
        if (level.getGameTime() % 20L == 0L) {
            SESSIONS.keySet().removeIf(id -> level.getServer().getPlayerList().getPlayer(id) == null);
            FIRST_DELIVERED.keySet().removeIf(id -> !SESSIONS.containsKey(id));
        }
        for (ServerPlayer player : level.players()) {
            try {
                tickOne(player, level);
            } catch (RuntimeException e) {
                LOGGER.error("[AkaishiMagic] 施法推进异常，玩家={}", player.getUUID(), e);
                MagicCastSession session = SESSIONS.get(player.getUUID());
                if (session != null) end(player, session, false, !session.firstFired(), 0f);
            }
        }
    }

    static void tickOne(ServerPlayer player, ServerLevel level) {
        MagicCastSession session = SESSIONS.get(player.getUUID());
        if (session == null) return;
        if (player.isDeadOrDying() || session.dimension() != level.dimension() || player.hasDisconnected()) {
            end(player, session, false, true, 0f);
            return;
        }
        float health = player.getHealth();
        boolean hit = session.lastHealth() - health >= INTERRUPT_DAMAGE_THRESHOLD;
        session.setLastHealth(health);
        if (hit) {
            end(player, session, false, true, 0f);
            return;
        }
        switch (session.state()) {
            case CHARGING -> tickCharging(player, session, level.getGameTime());
            case CHANNELING, VOLLEYING -> tickContinuous(player, session, level.getGameTime());
            default -> { }
        }
    }

    private static void tickCharging(ServerPlayer player, MagicCastSession session, long now) {
        if (now < session.chargeEndTick()) return;
        if (!MagicPipeline.cast(session.context())) {
            MagicPayment.refund(player, session.firstPayment(), 1f);
            finish(player, session, false);
            return;
        }
        session.markFirstFired();
        Runnable delivered = FIRST_DELIVERED.remove(player.getUUID());
        if (delivered != null) delivered.run();
        session.recordSuccessfulPayment(session.firstPayment());
        if (!session.continuous()) {
            finish(player, session, true);
            return;
        }
        session.setState(MagicIds.Delivery.CHANNEL.equals(session.snapshot().deliveryId())
                ? MagicCastState.CHANNELING : MagicCastState.VOLLEYING);
        session.setNextSegmentTick(now + session.segmentIntervalTicks());
        MagicCastStateS2C.send(player, session.state());
    }

    private static void tickContinuous(ServerPlayer player, MagicCastSession session, long now) {
        long elapsed = now - session.chargeEndTick();
        if ((session.state() == MagicCastState.CHANNELING && elapsed > (long) MAX_CHANNEL_SECONDS * 20L)
                || (session.state() == MagicCastState.VOLLEYING && session.segmentsPaid() >= MAX_VOLLEY_SHOTS)) {
            finish(player, session, true);
            return;
        }
        tickPersistence(session);
        if (now < session.nextSegmentTick()) return;
        PaymentReceipt first = session.firstPayment();
        PaymentReceipt payment = MagicPayment.pay(player, MagicSource.byIdOrDefault(session.context().sourceId()),
                session.segmentCost(), first == null ? null : first.account());
        if (payment == null) {
            finish(player, session, false);
            return;
        }
        // 只扣除支付自身的生命变化，外部受击仍由下一刻的落差识别。
        session.setLastHealth(session.lastHealth() - payment.healthPaid());
        session.markSegmentPaid();
        session.setNextSegmentTick(now + session.segmentIntervalTicks());
        if (!MagicPipeline.cast(session.context())) {
            MagicPayment.refund(player, payment, 1f);
            finish(player, session, false);
            return;
        }
        session.recordSuccessfulPayment(payment);
        if (!session.hasMoreSegments()) finish(player, session, true);
    }

    private static void tickPersistence(MagicCastSession session) {
        int elapsed = session.persistenceTicks();
        for (IPersistenceHandler handler : session.persistence()) {
            handler.tick(session.context(), elapsed, MagicPipeline.actionFor(session.context()));
        }
        session.advancePersistence();
    }

    private static void finish(ServerPlayer player, MagicCastSession session, boolean completed) {
        float cooldown = session.snapshot().cooldownSeconds();
        MagicState state = MagicStates.state(player);
        if (state != null) state.startCooldown(gameTime(player), cooldown);
        end(player, session, completed, false, cooldown);
    }

    private static void end(ServerPlayer player, MagicCastSession session, boolean completed,
            boolean refund, float cooldown) {
        if (!SESSIONS.remove(player.getUUID(), session)) return;
        FIRST_DELIVERED.remove(player.getUUID());
        try {
            if (refund && !session.firstFired()) MagicPayment.refund(player, session.firstPayment(), REFUND_RATIO);
        } catch (RuntimeException e) {
            LOGGER.error("[AkaishiMagic] 前摇返还失败，玩家={}", player.getUUID(), e);
        } finally {
            MagicCarrierHook hook = MagicPayHooks.get();
            if (hook != null) {
                try {
                    hook.onCastEnded(player, session.successfulPayments(), session.firstPayment(), completed, cooldown);
                } catch (RuntimeException e) {
                    LOGGER.error("[AkaishiMagic] 载体结算失败，玩家={}", player.getUUID(), e);
                }
            }
            if (!player.hasDisconnected()) MagicCastStateS2C.send(player, MagicCastState.IDLE);
        }
    }

    private static int segmentInterval(String deliveryId) {
        if (MagicIds.Delivery.CHANNEL.equals(deliveryId)) return 20;
        if (MagicIds.Delivery.VOLLEY.equals(deliveryId))
            return Math.max(1, Math.round(MagicDeliveryTable.VOLLEY_INTERVAL_SECONDS * 20f));
        return 1;
    }
    private static List<IPersistenceHandler> persistenceOf(MagicContext context) {
        List<IPersistenceHandler> handlers = new ArrayList<>();
        for (String id : context.words()) {
            MagicWord word = MagicWord.byId(id);
            if (word == null || word.role() != MagicWord.Role.STRUCTURE) continue;
            IPersistenceHandler handler = MagicExecRegistry.persistence(id);
            if (handler != null) handlers.add(handler);
        }
        return handlers;
    }
    private static long gameTime(Player player) { return player.level().getGameTime(); }
}
