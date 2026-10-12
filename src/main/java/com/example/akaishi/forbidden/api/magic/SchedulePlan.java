package com.example.akaishi.forbidden.api.magic;

public record SchedulePlan(int segments, float intervalSeconds, float channelSeconds,
                           int volleyShots, int followupHits, boolean interruptible) {}
