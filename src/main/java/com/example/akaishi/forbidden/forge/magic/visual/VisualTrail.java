package com.example.akaishi.forbidden.forge.magic.visual;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class VisualTrail {
    public record Sample(Vec3 position, long tick) { }

    private final List<Sample> samples = new ArrayList<>();

    public void add(Vec3 position, long tick, int lifetime) {
        prune(tick, lifetime);
        if (!finite(position)) {
            samples.clear();
            return;
        }
        if (!samples.isEmpty()) {
            Sample last = samples.get(samples.size() - 1);
            if (position.distanceToSqr(last.position()) > 64d || tick < last.tick()) {
                samples.clear();
            } else if (tick == last.tick() || position.distanceToSqr(last.position()) < 1.0e-8d) {
                return;
            }
        }
        samples.add(new Sample(position, tick));
        while (samples.size() > Math.max(2, Math.min(24, lifetime)) + 1) samples.remove(0);
    }

    public void prune(long tick, int lifetime) {
        samples.removeIf(sample -> tick - sample.tick() > lifetime || tick < sample.tick());
    }

    public List<Sample> samples() {
        return List.copyOf(samples);
    }

    public boolean isEmpty() {
        return samples.isEmpty();
    }

    private static boolean finite(Vec3 point) {
        return Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }
}
