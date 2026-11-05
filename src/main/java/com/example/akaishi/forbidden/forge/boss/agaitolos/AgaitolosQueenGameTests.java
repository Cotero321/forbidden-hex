package com.example.akaishi.forbidden.forge.boss.agaitolos;

import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosCastAnchors;
import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosEntity;
import com.example.akaishi.forbidden.boss.agaitolos.entity.AgaitolosHexOrb;
import com.example.akaishi.forbidden.entity.AkaishiForbiddenEntities;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@GameTestHolder("forbidden_hex")
@PrefixGameTestTemplate(false)
public final class AgaitolosQueenGameTests {
    private AgaitolosQueenGameTests() { }

    @GameTest(template = "brewing_empty")
    public static void queenAssets(GameTestHelper helper) throws Exception {
        JsonObject geometry = json("geo/entity/agaitolos_queen.geo.json")
                .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        Set<String> bones = new HashSet<>();
        for (var element : geometry.getAsJsonArray("bones")) {
            JsonObject bone = element.getAsJsonObject();
            helper.assertTrue(bones.add(bone.get("name").getAsString()), "Duplicate bone");
            if (bone.has("cubes")) for (var cube : bone.getAsJsonArray("cubes")) {
                for (var size : cube.getAsJsonObject().getAsJsonArray("size")) {
                    helper.assertTrue(size.getAsDouble() >= 0 && Double.isFinite(size.getAsDouble()), "Invalid cube size");
                }
            }
        }
        helper.assertTrue(bones.size() == 117, "Queen skeleton changed");
        for (var element : geometry.getAsJsonArray("bones")) {
            JsonObject bone = element.getAsJsonObject();
            helper.assertTrue(!bone.has("parent") || bones.contains(bone.get("parent").getAsString()), "Missing parent");
        }
        Map<String, Double> lengths = Map.ofEntries(
                Map.entry("boss_idle", 4d), Map.entry("boss_move", 2d), Map.entry("boss_slash", 1d),
                Map.entry("boss_summon_throw", 1.5d), Map.entry("boss_dive_sweep", 1.5d),
                Map.entry("boss_block", 1.2d), Map.entry("boss_hurt", 0.6d),
                Map.entry("boss_curse_reverse", 12d), Map.entry("boss_curse_break", 1.3d),
                Map.entry("boss_calamity", 2.2d), Map.entry("boss_triple_throw", 2d),
                Map.entry("boss_blink_strike", 1.7d), Map.entry("boss_stomp_cleave", 1.1d),
                Map.entry("boss_rapid_kick", 1.3d), Map.entry("boss_saturation_barrage", 2d),
                Map.entry("boss_execution_cleave", 1.4d));
        JsonObject clips = json("animations/entity/agaitolos_queen.animation.json").getAsJsonObject("animations");
        helper.assertTrue(clips.keySet().equals(lengths.keySet()), "Missing or unregistered queen clip");
        for (var entry : clips.entrySet()) {
            JsonObject clip = entry.getValue().getAsJsonObject();
            double length = clip.get("animation_length").getAsDouble();
            helper.assertTrue(Math.abs(length - lengths.get(entry.getKey())) < 1.0e-9, "Wrong clip duration: " + entry.getKey());
            boolean loop = Set.of("boss_idle", "boss_move", "boss_saturation_barrage").contains(entry.getKey());
            helper.assertTrue((clip.has("loop") && clip.get("loop").getAsBoolean()) == loop, "Wrong loop mode");
            for (var bone : clip.getAsJsonObject("bones").entrySet()) {
                helper.assertTrue(bones.contains(bone.getKey()), "Animated bone missing: " + bone.getKey());
                for (var channel : bone.getValue().getAsJsonObject().entrySet()) {
                    double previousTime = -1;
                    for (var key : channel.getValue().getAsJsonObject().entrySet()) {
                        double time = Double.parseDouble(key.getKey());
                        helper.assertTrue(Double.isFinite(time) && time >= 0 && time <= length + 1.0e-9, "Key outside clip");
                        helper.assertTrue(time > previousTime, "Unsorted GeckoLib keyframes: " + entry.getKey()
                                + "/" + bone.getKey() + "/" + channel.getKey() + ": " + previousTime + " -> " + time);
                        previousTime = time;
                        for (var value : key.getValue().getAsJsonArray()) {
                            helper.assertTrue(Double.isFinite(value.getAsDouble()), "Non-finite key value");
                        }
                    }
                }
            }
        }
        try (InputStream texture = resource("textures/entity/agaitolos_queen.png")) {
            var image = javax.imageio.ImageIO.read(texture);
            helper.assertTrue(image != null && image.getWidth() == 128 && image.getHeight() == 128, "Queen texture dimensions");
        }
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void orbRegistrationAndAnchors(GameTestHelper helper) {
        var level = helper.getLevel();
        AgaitolosHexOrb orb = AkaishiForbiddenEntities.AGAITOLOS_HEX_ORB.get().create(level);
        helper.assertTrue(orb != null && orb.getType() != AkaishiForbiddenEntities.AGAITOLOS_WITHER_SKULL.get(), "Orb is not independent");
        helper.assertTrue(orb.isPickable(), "Orb lost melee deflection");
        AgaitolosEntity boss = AkaishiForbiddenEntities.AGAITOLOS.get().create(level);
        helper.assertTrue(boss != null, "Boss registration missing");
        boss.setPos(16, 80, 16);
        Vec3 previous = null;
        for (int yaw = 0; yaw < 360; yaw += 90) {
            boss.yBodyRot = yaw;
            Vec3 point = AgaitolosCastAnchors.origin(boss).subtract(boss.position());
            helper.assertTrue(Double.isFinite(point.lengthSqr()) && point.length() < 3, "Invalid cast anchor");
            if (previous != null) helper.assertTrue(Math.abs(point.length() - previous.length()) < 1.0e-6, "Yaw scaled anchor");
            previous = point;
        }
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void attackWindupAndLateTrackingClock(GameTestHelper helper) {
        var level = helper.getLevel();
        AgaitolosEntity boss = AkaishiForbiddenEntities.AGAITOLOS.get().create(level);
        var victim = net.minecraft.world.entity.EntityType.PIG.create(level);
        helper.assertTrue(boss != null && victim != null, "Entities not registered");
        Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1)));
        boss.setPos(origin);
        victim.setPos(origin.add(0, 0, 1));
        boss.setTarget(victim);
        float before = victim.getHealth();
        helper.assertTrue(boss.doHurtTarget(victim), "Attack did not start");
        helper.assertTrue(victim.getHealth() == before, "Windup dealt instant damage");
        helper.assertTrue(!boss.doHurtTarget(victim), "Second attack bypassed action lock");
        helper.assertTrue(boss.getVisualAction().equals("boss_slash"), "Wrong slash clip");
        helper.assertTrue(Math.abs(boss.getVisualActionAge(0.5f) - 0.5) < 1.0e-6, "Clock origin mismatch");
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(Math.abs(boss.getVisualActionAge(0f) - 5) < 1.0e-6, "Action clock did not follow server time");
            helper.assertTrue(victim.getHealth() == before, "Untracked entity unexpectedly dealt damage");
            helper.succeed();
        });
    }

    private static JsonObject json(String path) throws Exception {
        try (var reader = new InputStreamReader(resource(path), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static InputStream resource(String path) {
        InputStream stream = AgaitolosQueenGameTests.class.getResourceAsStream("/assets/forbidden_hex/" + path);
        if (stream == null) throw new IllegalStateException("Missing queen resource " + path);
        return stream;
    }
}
