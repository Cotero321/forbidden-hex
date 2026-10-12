package com.example.akaishi.forbidden.api.magic;

public record TargetPlan(String shape, int limit, boolean requireLineOfSight, boolean includeHidden) {}
