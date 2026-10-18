package com.example.akaishi.forbidden.forge.client.visual;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.entity.AkaishiForbiddenEntities;
import com.example.akaishi.forbidden.magic.cast.MagicCastState;
import com.example.akaishi.forbidden.magic.circle.SpellCircleEntity;
import com.example.akaishi.forbidden.magic.table.MagicIds;
import com.example.akaishi.forbidden.magic.visual.SpellVisual;
import com.mojang.logging.LogUtils;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.List;

/** Opt-in render regression scene using server-built entity data in the isolated test world. */
@Mod.EventBusSubscriber(modid = AkaishiMod.MOD_ID, value = Dist.CLIENT)
public final class ClientSpellCircleSceneTest {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CHARGE_ID = -890001;
    private static final int PLACE_ID = -890002;
    private static final List<String> EFFECTS = List.of(MagicIds.Effect.DAMAGE, MagicIds.Effect.BREAK_UNLOCK,
            MagicIds.Effect.BREAK_TERMINATE, MagicIds.Effect.BREAK_SHIELD, MagicIds.Effect.MOVE,
            MagicIds.Effect.TELEPORT, MagicIds.Effect.GATHER, MagicIds.Effect.BIND, MagicIds.Effect.WEAKEN,
            MagicIds.Effect.BOOST, MagicIds.Effect.WARD, MagicIds.Effect.MIND, MagicIds.Effect.SENSE,
            MagicIds.Effect.CONCEAL, MagicIds.Effect.SUMMON, MagicIds.Effect.FIELD, MagicIds.Effect.LINK,
            MagicIds.Effect.TEMPORAL, MagicIds.Effect.VESSEL, MagicIds.Effect.TOLL);
    private static int ticks;
    private static boolean requested;
    private static boolean ready;
    private static boolean previousFallback;
    private static SpellCircleEntity source;
    private static SpellCircleEntity charge;
    private static SpellCircleEntity placed;
    private static java.nio.file.Path output;
    private static int pending;
    private static int captures;
    private static String captureFailure;

    private ClientSpellCircleSceneTest() {
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Boolean.getBoolean("forbidden_hex.circleSceneTest")) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (captureFailure != null) throw new IllegalStateException(captureFailure);
        if (minecraft.level == null || minecraft.player == null || minecraft.getSingleplayerServer() == null
                || minecraft.getOverlay() != null || !ClientVisualSmokeTest.readyForScene()) return;
        minecraft.setScreen(null);
        if (!requested) {
            requested = true;
            previousFallback = com.example.akaishi.forbidden.forge.client.ForbiddenVisualConfig.FORCE_VANILLA_RENDERING.get();
            output = minecraft.gameDirectory.toPath().resolve("../visual-review/circle-upgrade")
                    .normalize().resolve(Boolean.getBoolean("forbidden_hex.shaderCompatTest") ? "shader" : "vanilla");
            try { java.nio.file.Files.createDirectories(output); }
            catch (java.io.IOException exception) { throw new IllegalStateException("Cannot create circle output", exception); }
            prepare(minecraft);
            return;
        }
        if (!ready) return;
        ticks++;
        if (ticks == 8) capture(minecraft, "circle_01_expand.png");
        if (ticks == 48) capture(minecraft, "circle_02_charged.png");
        if (ticks == 52) transition(minecraft, MagicCastState.CHANNELING);
        if (ticks == 68) capture(minecraft, "circle_03_channeling.png");
        if (ticks == 72) transition(minecraft, MagicCastState.VOLLEYING);
        if (ticks == 88) capture(minecraft, "circle_04_volleying.png");
        if (ticks == 92) transition(minecraft, MagicCastState.IDLE);
        if (ticks == 97) capture(minecraft, "circle_05_fading.png");
        if (ticks == 106) theme(minecraft, "hu", "yao", "white");
        if (ticks == 132) capture(minecraft, "circle_06_ward.png");
        if (ticks == 138) theme(minecraft, "ji_draw", "yuan", "dark");
        if (ticks == 164) capture(minecraft, "circle_07_vortex.png");
        if (ticks == 170) theme(minecraft, "tou", "chi", "life");
        if (ticks == 196) capture(minecraft, "circle_08_damage.png");
        if (ticks == 202) theme(minecraft, "qi_flag", "chi", "san");
        if (ticks == 226) {
            com.example.akaishi.forbidden.forge.client.ForbiddenVisualConfig.FORCE_VANILLA_RENDERING.set(true);
        }
        if (ticks == 234) capture(minecraft, "circle_09_vanilla_fallback.png");
        if (ticks == 242) com.example.akaishi.forbidden.forge.client.ForbiddenVisualConfig.FORCE_VANILLA_RENDERING.set(previousFallback);
        if (ticks == 246) theme(minecraft, "pian", "yuan", "dark");
        if (ticks == 272) capture(minecraft, "circle_10_mind.png");
        if (ticks == 278) theme(minecraft, "yuan", "man", "life");
        if (ticks == 304) capture(minecraft, "circle_11_boost.png");
        if (ticks == 310) theme(minecraft, "duan", "xu", "san");
        if (ticks == 336) capture(minecraft, "circle_12_terminate.png");
        if (ticks == 342) theme(minecraft, "jiao_cross", "xing", "white");
        if (ticks == 368) capture(minecraft, "circle_13_link.png");
        if (ticks == 374) theme(minecraft, "bei", "rang", "life");
        if (ticks == 400) capture(minecraft, "circle_14_field.png");
        if (ticks == 406) theme(minecraft, "qian", "ju", "san");
        if (ticks == 432) capture(minecraft, "circle_15_move.png");
        if (ticks == 438) theme(minecraft, "jiao_angle", "an", "dark");
        if (ticks == 464) capture(minecraft, "circle_16_conceal.png");
        if (ticks == 470) theme(minecraft, "hu", "xu", "san");
        if (ticks == 496) capture(minecraft, "circle_17_same_spell_san.png");
        if (ticks == 502) theme(minecraft, "hu", "xu", "life");
        if (ticks == 528) capture(minecraft, "circle_18_same_spell_life.png");
        if (ticks == 534) theme(minecraft, "hu", "xu", "dark");
        if (ticks == 560) capture(minecraft, "circle_19_same_spell_dark.png");
        if (ticks == 566) theme(minecraft, "hu", "xu", "white");
        if (ticks == 592) capture(minecraft, "circle_20_same_spell_white.png");
        if (ticks >= 610 && ticks < 610 + EFFECTS.size() * 32) {
            int index = (ticks - 610) / 32;
            int offset = (ticks - 610) % 32;
            if (offset == 0) theme(minecraft, "hu", "xu", "san", EFFECTS.get(index));
            if (offset == 26) capture(minecraft, "circle_effect_" + EFFECTS.get(index) + ".png");
        }
        if (ticks >= 610 + EFFECTS.size() * 32 && pending == 0) {
            if (captures != 40) throw new IllegalStateException("Expected 40 circle screenshots, saved " + captures);
            com.example.akaishi.forbidden.forge.client.ForbiddenVisualConfig.FORCE_VANILLA_RENDERING.set(previousFallback);
            charge.discard();
            placed.discard();
            LOGGER.info("[CircleScene] PASS render snapshots; saved={}; shader_state={}; scope=server-built snapshots, no multiplayer session",
                    captures, ClientShaderCompatibility.state());
            LOGGER.info("[CircleScene] Screenshots: {}", output.resolve("screenshots").toAbsolutePath());
            minecraft.stop();
        }
    }

    private static void prepare(Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer();
        if (server == null) return;
        var playerId = minecraft.player.getUUID();
        server.execute(() -> {
            ServerPlayer owner = server.getPlayerList().getPlayer(playerId);
            if (owner == null) {
                LOGGER.error("[CircleScene] FAIL missing server player");
                minecraft.execute(minecraft::stop);
                return;
            }
            List<String> slots = List.of("qi_flag", "", "zhen", "liu", "", "", "hu", "biao");
            List<String> inks = List.of("chi", "", "chi", "yuan", "", "", "yao", "chi");
            SpellVisual visual = SpellVisual.assemble("qi_flag", "place", "chi", "san",
                    1.8f, 1.8f, 12f, slots.stream().filter(word -> !word.isEmpty()).toList(), slots, inks);
            source = new SpellCircleEntity(AkaishiForbiddenEntities.SPELL_CIRCLE.get(), owner.serverLevel());
            source.armFollowing(visual, owner, owner.level().getGameTime() + 40L);
            SpellCircleEntity placeSource = new SpellCircleEntity(AkaishiForbiddenEntities.SPELL_CIRCLE.get(), owner.serverLevel());
            placeSource.arm(visual, point -> {}, 240);
            var chargeData = source.getEntityData().getNonDefaultValues();
            var placeData = placeSource.getEntityData().getNonDefaultValues();
            minecraft.execute(() -> {
                if (minecraft.level == null || minecraft.player == null) return;
                minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                charge = new SpellCircleEntity(AkaishiForbiddenEntities.SPELL_CIRCLE.get(), minecraft.level);
                assign(charge, chargeData);
                double angle = Math.toRadians(minecraft.player.getYRot());
                Vec3 back = new Vec3(Math.sin(angle), 0d, -Math.cos(angle));
                Vec3 right = new Vec3(Math.cos(angle), 0d, Math.sin(angle));
                Vec3 center = minecraft.player.position().add(back.scale(0.72d)).add(0d, 1.12d, 0d);
                charge.setPos(center.x, center.y, center.z);
                charge.setYRot(minecraft.player.getYRot());
                charge.yRotO = charge.getYRot();
                minecraft.level.putNonPlayerEntity(CHARGE_ID, charge);
                placed = new SpellCircleEntity(AkaishiForbiddenEntities.SPELL_CIRCLE.get(), minecraft.level);
                assign(placed, placeData);
                Vec3 target = minecraft.player.position().add(right.scale(2.4d));
                placed.setPos(target.x, target.y + 0.03d, target.z);
                minecraft.level.putNonPlayerEntity(PLACE_ID, placed);
                ready = true;
            });
        });
    }

    private static void transition(Minecraft minecraft, MagicCastState state) {
        var server = minecraft.getSingleplayerServer();
        if (server == null || source == null) return;
        server.execute(() -> {
            source.setVisualState(state);
            var data = source.getEntityData().getNonDefaultValues();
            minecraft.execute(() -> assign(charge, data));
        });
    }

    private static void theme(Minecraft minecraft, String word, String ink, String energy) {
        theme(minecraft, word, ink, energy, null);
    }

    private static void theme(Minecraft minecraft, String word, String ink, String energy, String effect) {
        var server = minecraft.getSingleplayerServer();
        if (server == null || minecraft.player == null) return;
        var ownerId = minecraft.player.getUUID();
        server.execute(() -> {
            ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
            if (owner == null) return;
            List<String> slots = List.of(word, "", "zhen", "liu", "", "", "hu", "biao");
            List<String> inks = List.of(ink, "", ink, "yuan", "", "", "yao", ink);
            SpellVisual visual = SpellVisual.assemble(word, "place", ink, energy, 1.8f, 1.8f, 12f,
                    slots.stream().filter(value -> !value.isEmpty()).toList(), slots, inks);
            if (effect != null) visual = new SpellVisual(effect, visual.deliveryId(), visual.sourceId(), visual.inkId(),
                    visual.inkRgb(), visual.sourceRgb(), visual.power(), visual.radius(), visual.duration(),
                    visual.wordCount(), visual.wordIds(), visual.glyphs(), visual.slotWords(), visual.slotInks());
            source.armFollowing(visual, owner, owner.level().getGameTime() + 12L);
            SpellCircleEntity placeSource = new SpellCircleEntity(AkaishiForbiddenEntities.SPELL_CIRCLE.get(), owner.serverLevel());
            placeSource.arm(visual, point -> {}, 240);
            var chargeData = source.getEntityData().getNonDefaultValues();
            var placeData = placeSource.getEntityData().getNonDefaultValues();
            minecraft.execute(() -> {
                assign(charge, chargeData);
                assign(placed, placeData);
            });
        });
    }

    private static void assign(SpellCircleEntity entity, List<SynchedEntityData.DataValue<?>> data) {
        if (entity != null && data != null) entity.getEntityData().assignValues(data);
    }

    private static void capture(Minecraft minecraft, String name) {
        pending++;
        Screenshot.grab(output.toFile(), name, minecraft.getMainRenderTarget(), message -> minecraft.execute(() -> {
            pending--;
            if (!(message.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents contents)
                    || !"screenshot.success".equals(contents.getKey())) {
                captureFailure = "Circle screenshot failed: " + message.getString();
                return;
            }
            captures++;
            LOGGER.info("[CircleScene] Saved {}", name);
        }));
    }
}
