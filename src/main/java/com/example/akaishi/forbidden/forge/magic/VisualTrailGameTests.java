package com.example.akaishi.forbidden.forge.magic;

import com.example.akaishi.forbidden.forge.magic.visual.VisualTrail;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("forbidden_hex")
@PrefixGameTestTemplate(false)
public final class VisualTrailGameTests {
    private VisualTrailGameTests() { }

    @GameTest(template = "brewing_empty")
    public static void boundedCurvedHistory(GameTestHelper helper) {
        VisualTrail trail = new VisualTrail();
        for (int tick = 0; tick < 100; tick++) {
            trail.add(new Vec3(tick * 0.1d, Math.sin(tick * 0.2d), 0d), tick, 10);
        }
        var points = trail.samples();
        helper.assertTrue(points.size() == 11, "Trail grew beyond its lifetime budget");
        helper.assertTrue(points.get(0).tick() == 89 && points.get(10).tick() == 99,
                "Trail did not retain the most recent positions");
        helper.assertTrue(points.get(10).position().equals(new Vec3(9.9d, Math.sin(19.8d), 0d)),
                "Trail lost the curved path head");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void teleportAndInvalidPositions(GameTestHelper helper) {
        VisualTrail trail = new VisualTrail();
        trail.add(Vec3.ZERO, 0, 10);
        trail.add(new Vec3(1, 0, 0), 1, 10);
        trail.add(new Vec3(100, 0, 0), 2, 10);
        helper.assertTrue(trail.samples().size() == 1,
                "Teleport left a ribbon spanning unloaded space");
        trail.add(new Vec3(Double.NaN, 0, 0), 3, 10);
        helper.assertTrue(trail.isEmpty(), "Non-finite position reached the renderer");
        trail.add(new Vec3(Double.POSITIVE_INFINITY, 0, 0), 4, 10);
        helper.assertTrue(trail.isEmpty(), "Infinite position reached the renderer");
        trail.add(new Vec3(2, 0, 0), 5, 10);
        helper.assertTrue(trail.samples().size() == 1, "Valid trail did not recover after invalid data");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void fadeAndTimeReset(GameTestHelper helper) {
        VisualTrail trail = new VisualTrail();
        trail.add(Vec3.ZERO, 100, 10);
        trail.add(new Vec3(1, 0, 0), 101, 10);
        trail.add(new Vec3(1, 0, 0), 102, 10);
        helper.assertTrue(trail.samples().size() == 2, "Stationary projectile accumulated redundant segments");
        trail.prune(112, 10);
        helper.assertTrue(trail.isEmpty(), "Discarded projectile trail did not expire");
        trail.add(Vec3.ZERO, 200, 10);
        trail.add(new Vec3(1, 0, 0), 201, 10);
        trail.add(new Vec3(2, 0, 0), 20, 10);
        helper.assertTrue(trail.samples().size() == 1 && trail.samples().get(0).tick() == 20,
                "World time reset retained stale samples");
        helper.succeed();
    }
}
