package com.example.akaishi.forbidden.forge.magic;

import com.example.akaishi.forbidden.entity.AkaishiForbiddenEntities;
import com.example.akaishi.forbidden.magic.cast.MagicCastState;
import com.example.akaishi.forbidden.magic.circle.SpellCircleEntity;
import com.example.akaishi.forbidden.magic.visual.SpellVisual;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@GameTestHolder("forbidden_hex")
@PrefixGameTestTemplate(false)
public final class SpellCircleLifecycleGameTests {
    private SpellCircleLifecycleGameTests() {
    }

    @GameTest(template = "brewing_empty", timeoutTicks = 90)
    public static void circle_place_retains_pulse_schedule(GameTestHelper helper) {
        SpellCircleEntity circle = circle(helper);
        AtomicInteger pulses = new AtomicInteger();
        circle.arm(visual(), point -> pulses.incrementAndGet(), 60);
        for (int tick = 1; tick <= 62; tick++) {
            int elapsed = tick;
            helper.runAfterDelay(tick, () -> {
                circle.tick();
                if (elapsed == 1) helper.assertTrue(pulses.get() == 1, "PLACE first tick must fire once");
                if (elapsed == 20) helper.assertTrue(pulses.get() == 1, "PLACE fired before 20-tick interval");
                if (elapsed == 21) helper.assertTrue(pulses.get() == 2, "PLACE second pulse timing changed");
                if (elapsed == 41) helper.assertTrue(pulses.get() == 3, "PLACE third pulse timing changed");
                if (elapsed == 62) {
                    helper.assertTrue(circle.isRemoved(), "expired PLACE circle was not removed");
                    helper.assertTrue(pulses.get() == 3, "expired PLACE circle executed an extra effect");
                    helper.succeed();
                }
            });
        }
    }

    @GameTest(template = "brewing_empty", timeoutTicks = 30)
    public static void circle_sync_retains_sparse_slots_and_cache(GameTestHelper helper) {
        SpellCircleEntity source = circle(helper);
        source.armFollowing(visual(), owner(helper), helper.getLevel().getGameTime() + 40L);
        SpellCircleEntity observer = circle(helper);
        observer.visual();
        observer.layers();
        sync(helper, source, observer);
        helper.assertTrue(observer.followsCaster(), "observer lost following mode");
        helper.assertTrue(observer.visualState() == SpellCircleEntity.VisualState.CHARGING, "observer lost state");
        helper.assertTrue(observer.visual().equals(source.visual()), "observer lost visual snapshot fields");
        helper.assertTrue(observer.visual().duration() == 3f, "observer lost visual duration");
        helper.assertTrue(!observer.layers().layer(1).occupied() && !observer.layers().layer(2).occupied(),
                "observer collapsed empty middle layers or retained stale cache");
        helper.assertTrue(observer.layers().layer(3).wordIds().equals(List.of("", "hu")), "observer moved final slot");
        helper.assertTrue(observer.visual() == observer.visual() && observer.layers() == observer.layers(),
                "visual snapshot is parsed every frame");
        helper.runAfterDelay(4, () -> {
            float phase = source.animationTime(0f);
            source.setVisualState(MagicCastState.CHANNELING);
            sync(helper, source, observer);
            helper.assertTrue(observer.visualState() == SpellCircleEntity.VisualState.CHANNELING,
                    "observer lost channel transition");
            helper.assertTrue(Math.abs(observer.animationTime(0f) - phase) < 0.0001f,
                    "synchronized channel transition jumped phase");
            source.setVisualState(MagicCastState.VOLLEYING);
            sync(helper, source, observer);
            helper.assertTrue(observer.visualState() == SpellCircleEntity.VisualState.VOLLEYING,
                    "observer lost volley transition");
            source.fadeOut();
            sync(helper, source, observer);
            helper.assertTrue(observer.visualState() == SpellCircleEntity.VisualState.FADING,
                    "observer lost fade transition");
            helper.assertTrue(observer.fadeAlpha(0.5f) == source.fadeAlpha(0.5f)
                            && observer.chargeProgress(0.5f) == source.chargeProgress(0.5f),
                    "observer lost fade timing or frozen charge");
            helper.succeed();
        });
    }

    @GameTest(template = "brewing_empty", timeoutTicks = 40)
    public static void circle_orphan_fades_and_discards(GameTestHelper helper) {
        SpellCircleEntity circle = circle(helper);
        circle.armFollowing(visual(), owner(helper), helper.getLevel().getGameTime() + 40L);
        circle.tick();
        helper.assertTrue(circle.visualState() == SpellCircleEntity.VisualState.FADING, "orphan did not fade");
        float frozenCharge = circle.chargeProgress(0f);
        helper.runAfterDelay(5, () -> {
            circle.tick();
            helper.assertTrue(!circle.isRemoved(), "fade disappeared before its animation ended");
            helper.assertTrue(circle.fadeAlpha(0.5f) < circle.fadeAlpha(0f), "fade lacks partial tick interpolation");
            helper.assertTrue(circle.chargeProgress(0.9f) == frozenCharge, "fading circle kept charging");
        });
        helper.runAfterDelay(9, () -> {
            circle.tick();
            helper.assertTrue(!circle.isRemoved(), "orphan circle discarded before ten fade ticks");
        });
        helper.runAfterDelay(10, () -> {
            circle.tick();
            helper.assertTrue(circle.isRemoved(), "orphan circle did not discard after ten fade ticks");
            helper.succeed();
        });
    }

    @GameTest(template = "brewing_empty", timeoutTicks = 30)
    public static void circle_phase_continuity_and_fade_freeze(GameTestHelper helper) {
        SpellCircleEntity circle = circle(helper);
        circle.armFollowing(visual(), owner(helper), helper.getLevel().getGameTime() + 40L);
        helper.runAfterDelay(4, () -> {
            float phase = circle.animationTime(0f);
            circle.setVisualState(MagicCastState.CHANNELING);
            helper.assertTrue(Math.abs(circle.animationTime(0f) - phase) < 0.0001f, "channel transition jumped phase");
            circle.setVisualState(MagicCastState.VOLLEYING);
            helper.assertTrue(Math.abs(circle.animationTime(0f) - phase) < 0.0001f, "volley transition jumped phase");
            circle.fadeOut();
            float progress = circle.chargeProgress(0f);
            helper.runAfterDelay(5, () -> {
                helper.assertTrue(circle.fadeAlpha(0f) <= 0.5f, "circle did not fade at server time");
                helper.assertTrue(circle.chargeProgress(0f) == progress, "cancelled circle increased charge brightness");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "brewing_empty", timeoutTicks = 30)
    public static void circle_saved_following_never_restores_orphan(GameTestHelper helper) {
        SpellCircleEntity source = circle(helper);
        source.armFollowing(visual(), owner(helper), helper.getLevel().getGameTime() + 40L);
        var saved = source.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        SpellCircleEntity restored = circle(helper);
        restored.load(saved);
        helper.assertTrue(restored.visual().equals(source.visual()), "saved circle lost visual snapshot");
        helper.assertTrue(restored.visualState() == SpellCircleEntity.VisualState.FADING,
                "reloaded following circle escaped its non-persistent session");
        helper.assertTrue(restored.fadeAlpha(0f) == 0f, "reloaded orphan retained visible lifetime");
        restored.tick();
        helper.assertTrue(restored.isRemoved(), "reloaded following circle was not discarded");
        saved.remove("Duration");
        SpellCircleEntity legacy = circle(helper);
        legacy.load(saved);
        helper.assertTrue(legacy.visual().duration() == 0f, "legacy circle did not default missing duration");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void circle_layers_expand_in_order_above_ground(GameTestHelper helper) {
        for (int step = 0; step <= 20; step++) {
            float progress = step / 20f;
            float previous = 0f;
            for (int layer = 0; layer < 4; layer++) {
                float current = com.example.akaishi.forbidden.magic.visual.SpellCircleAnimation.layerProgress(layer, progress);
                helper.assertTrue(current >= previous && current >= 0f && current <= 1f,
                        "Circle did not unfold from inner to outer layers");
                previous = current;
            }
            for (float radius : new float[]{0.1f, 0.7f, 2f, 8f}) {
                for (int layer = 0; layer < 4; layer++) {
                    for (float time : new float[]{0f, 1.3f, 7.7f, 29f}) {
                        for (int angle = 0; angle < 360; angle += 15) {
                            var point = com.example.akaishi.forbidden.magic.visual.SpellCircleAnimation.point(
                                    layer, time, true, radius, progress, angle, 1.08f);
                            helper.assertTrue(Double.isFinite(point.lengthSqr()) && point.z >= 0.078d && point.z <= 0.642d,
                                    "Tilted PLACE layer crossed ground or expected height bounds");
                            helper.assertTrue(point.x * point.x + point.y * point.y <= radius * radius + 0.0001d,
                                    "PLACE layer exceeded horizontal effect radius");
                            float layerRadius = com.example.akaishi.forbidden.magic.visual.SpellCircleAnimation.radius(layer, radius, progress);
                            var matrix = new org.joml.Matrix4f().translate(0f, 0f,
                                    com.example.akaishi.forbidden.magic.visual.SpellCircleAnimation.depth(layer, time, true));
                            matrix.rotateX((float) Math.toRadians(
                                    com.example.akaishi.forbidden.magic.visual.SpellCircleAnimation.tiltX(layer, time, true, layerRadius)));
                            matrix.rotateY((float) Math.toRadians(
                                    com.example.akaishi.forbidden.magic.visual.SpellCircleAnimation.tiltY(layer, time, true, layerRadius)));
                            matrix.rotateZ((float) Math.toRadians(
                                    com.example.akaishi.forbidden.magic.visual.SpellCircleAnimation.spin(layer, time)));
                            double radians = Math.toRadians(angle);
                            var transformed = matrix.transformPosition(new org.joml.Vector3f(
                                    (float) Math.cos(radians) * layerRadius * 1.08f,
                                    (float) Math.sin(radians) * layerRadius * 1.08f, 0f));
                            helper.assertTrue(point.distanceToSqr(new net.minecraft.world.phys.Vec3(
                                            transformed.x, transformed.y, transformed.z)) < 0.000001d,
                                    "Connector anchor separated from rendered layer transform");
                        }
                    }
                }
            }
        }
        helper.succeed();
    }

    private static void sync(GameTestHelper helper, SpellCircleEntity source, SpellCircleEntity observer) {
        var values = source.getEntityData().getNonDefaultValues();
        helper.assertTrue(values != null, "armed circle has no synchronized data");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new ClientboundSetEntityDataPacket(source.getId(), values).write(buffer);
            ClientboundSetEntityDataPacket decoded = new ClientboundSetEntityDataPacket(buffer);
            helper.assertTrue(decoded.id() == source.getId() && !buffer.isReadable(),
                    "entity data packet lost identity or field boundaries");
            observer.getEntityData().assignValues(decoded.packedItems());
        } finally {
            buffer.release();
        }
    }

    private static SpellCircleEntity circle(GameTestHelper helper) {
        return new SpellCircleEntity(AkaishiForbiddenEntities.SPELL_CIRCLE.get(), helper.getLevel());
    }

    private static ServerPlayer owner(GameTestHelper helper) {
        return new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "CircleRegression"));
    }

    private static SpellVisual visual() {
        List<String> words = List.of("tou", "", "", "", "", "", "", "hu");
        List<String> inks = List.of("chi", "", "", "", "", "", "", "yao");
        return SpellVisual.assemble("tou", "place", "chi", "san", 2f, 2f, 3f,
                List.of("tou", "hu"), words, inks);
    }
}
