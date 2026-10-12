package com.example.akaishi.forbidden.forge.client.hud;

import com.example.akaishi.forbidden.magic.ClientMagicData;
import com.example.akaishi.forbidden.magic.particle.AkaishiMagicParticles;
import com.example.akaishi.forbidden.magic.table.MagicSource;
import com.example.akaishi.forbidden.sanity.ClientSanityData;
import com.example.akaishi.forbidden.wand.WandItem;
import com.example.akaishi.forbidden.wand.WandModifiers;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** 法杖准星 HUD：独立归一的双轨读数与逐像素环带。 */
public final class WandHudPainter {

    public static final int RING_RADIUS = 11;
    public static final int RING_THICKNESS = 2;
    public static final int RING_DX = 18;
    public static final int RING_DY = -6;
    public static final int BAR_WIDTH = 48;
    public static final int BAR_HEIGHT = 5;
    public static final int BAR_DY = 9;
    private static final int EXTRA_HEIGHT = 3;
    private static final int TRACK_GAP = 2;
    private static final int CELL_WIDTH = 6;
    public static final int HALO_RADIUS_FROM = 9;
    public static final int HALO_RADIUS_TO = 22;
    public static final int HALO_PERIOD_TICKS = 34;

    private static final int COLOR_TRACK = 0xFF20242A;
    private static final int COLOR_TRACK_EXTRA = 0xFF14181E;
    private static final int COLOR_EXTRA_FRAME = 0xFF707782;
    private static final int COLOR_RING_TRACK = 0x8A20242A;
    private static final int COLOR_CD = 0xFFFFD24A;
    private static final int COLOR_OVERLOAD = 0xFFFF5A4A;
    private static final int HUD_SAN = 0xC3C8D2;
    private static final int HUD_LIFE = 0x4FD98D;
    private static final int HUD_DARK = 0x9C6BE8;
    private static final int HUD_WHITE = 0xFFF4D6;
    private static final float EXTRA_WHITEN = 0.55f;

    private WandHudPainter() {
    }

    public record PoolRead(float main, float mainCap, float extra, float extraCap, int rgb) {
        public float mainRatio() {
            return ratio(main, mainCap);
        }

        public float extraRatio() {
            return ratio(extra, extraCap);
        }

        private static float ratio(float value, float total) {
            return total <= 0f ? 0f : Mth.clamp(value / total, 0f, 1f);
        }
    }

    /** HUD 可读色保留 RGB，提交 GUI 实心填充时必须补齐 FF alpha。 */
    public static int hudColor(MagicSource source) {
        return switch (source) {
            case SAN -> HUD_SAN;
            case LIFE -> HUD_LIFE;
            case DARK -> HUD_DARK;
            case WHITE -> HUD_WHITE;
        };
    }

    public static ItemStack heldWand(Player player) {
        return WandItem.heldWand(player);
    }

    public static MagicSource socketedSource(ItemStack wand) {
        return WandItem.socketedSource(wand);
    }

    public static PoolRead read(ItemStack wand, MagicSource source) {
        ClientMagicData.Snapshot snap = ClientMagicData.snapshot();
        float base = snap.poolCapacity();
        float main;
        float mainCap;
        switch (source) {
            case SAN -> {
                var sanity = ClientSanityData.snapshot();
                main = sanity.san();
                mainCap = sanity.effectiveMax();
            }
            case DARK -> {
                main = snap.poolDark();
                mainCap = base;
            }
            case WHITE -> {
                main = snap.poolWhite();
                mainCap = base;
            }
            default -> {
                main = snap.poolLife();
                mainCap = base;
            }
        }
        float extra = WandItem.extra(wand, source);
        float extraCap = WandModifiers.extraPoolCapacity(WandItem.parts(wand), base, source);
        return new PoolRead(main, mainCap, extra, extraCap, hudColor(source));
    }

    /** 两轨各用自己的容量归一，灰源 SAN 与杖内魔力不混算。 */
    public static void drawPoolBar(GuiGraphics g, int x, int y, int width, int height, PoolRead pool) {
        if (width <= 0 || height <= 0) {
            return;
        }
        g.fill(x, y, x + width, y + height, COLOR_TRACK);
        int mainFill = Math.round(width * pool.mainRatio());
        if (mainFill > 0) {
            g.fill(x, y, x + mainFill, y + height, withAlpha(pool.rgb(), 1f));
        }
        if (pool.extraCap() <= 0f) {
            return;
        }
        // 外框上缘与主轨之间留两像素；下轨填充本身高三像素。
        int extraY = y + height + TRACK_GAP + 1;
        g.fill(x - 1, extraY - 1, x + width + 1, extraY + EXTRA_HEIGHT + 1, COLOR_EXTRA_FRAME);
        g.fill(x, extraY, x + width, extraY + EXTRA_HEIGHT, COLOR_TRACK_EXTRA);
        int extraFill = Math.round(width * pool.extraRatio());
        int extraColor = withAlpha(brighten(pool.rgb(), EXTRA_WHITEN), 1f);
        for (int cell = 0; cell < width; cell += CELL_WIDTH) {
            int end = cell + CELL_WIDTH >= width ? width : cell + CELL_WIDTH - 1;
            int filledEnd = Math.min(end, extraFill);
            if (filledEnd > cell) {
                g.fill(x + cell, extraY, x + filledEnd, extraY + EXTRA_HEIGHT, extraColor);
            }
        }
    }

    /** 就绪不画空圈；冷却的轨道与黄色进度在同一次扫描内择一绘制。 */
    public static void drawCooldownRing(GuiGraphics g, Font font, int cx, int cy, int radius,
            float remainFraction, String seconds, boolean overloaded, float breath) {
        float remain = Mth.clamp(remainFraction, 0f, 1f);
        if (!overloaded && remain <= 0f) {
            return;
        }
        drawRingBand(g, cx, cy, radius, 0f, overloaded ? 1f : remain,
                overloaded ? COLOR_OVERLOAD : withAlpha(COLOR_CD, breath), RING_THICKNESS,
                overloaded ? COLOR_OVERLOAD : COLOR_RING_TRACK);
        if (!overloaded && seconds != null && !seconds.isEmpty()) {
            g.drawString(font, seconds, cx - font.width(seconds) / 2, cy - 4, COLOR_CD, true);
        }
    }

    /** 不把仍在冷却的不足一秒显示为零。 */
    public static String secondsText(float seconds) {
        return Integer.toString(Math.max(0, (int) Math.ceil(seconds)));
    }

    public static void drawHalo(GuiGraphics g, int cx, int cy, int rgb, float phase, float alphaScale) {
        float clamped = Mth.clamp(phase, 0f, 1f);
        float fade = (1f - clamped) * (1f - clamped) * 0.62f * Math.max(0f, alphaScale);
        if (fade < 0.02f) {
            return;
        }
        int radius = Math.round(HALO_RADIUS_FROM + (HALO_RADIUS_TO - HALO_RADIUS_FROM) * clamped);
        ringArc(g, cx, cy, radius, 0f, 1f, withAlpha(rgb, fade), 1);
    }

    /** 出生节拍由 overlay 按玩家 tick 节流，位置与既有汇聚效果保持一致。 */
    public static void spawnConvergingMotes(LocalPlayer player, MagicSource source, String inkId, int rgb, int age) {
        Vec3 eye = player.getEyePosition();
        Vec3 dir = player.getLookAngle();
        double distance = 2.2d;
        double aimX = eye.x + dir.x * distance;
        double aimY = eye.y + dir.y * distance;
        double aimZ = eye.z + dir.z * distance;
        Vec3 up = Math.abs(dir.y) > 0.9d ? new Vec3(1d, 0d, 0d) : new Vec3(0d, 1d, 0d);
        Vec3 side = dir.cross(up).normalize();
        Vec3 vert = side.cross(dir).normalize();
        double angle = Math.toRadians(age % 12 * 30d);
        double radius = 0.26d + age % 5 * 0.03d;
        double ox = Math.cos(angle) * radius;
        double oy = Math.sin(angle) * radius;
        double x = aimX + side.x * ox + vert.x * oy;
        double y = aimY + side.y * ox + vert.y * oy;
        double z = aimZ + side.z * ox + vert.z * oy;
        AkaishiMagicParticles.converge(player.level(), source, inkId,
                com.example.akaishi.forbidden.magic.visual.SpellCarrierForm.ORB, rgb,
                x, y, z, aimX, aimY, aimZ, 0.055d);
    }

    public static void ringArc(GuiGraphics g, int cx, int cy, int radius,
            float startFraction, float spanFraction, int color, int thickness) {
        drawRingBand(g, cx, cy, radius, startFraction, spanFraction, color, thickness, 0);
    }

    private static void drawRingBand(GuiGraphics g, int cx, int cy, int radius,
            float startFraction, float spanFraction, int color, int thickness, int trackColor) {
        float span = Mth.clamp(spanFraction, 0f, 1f);
        if (span <= 0f || radius <= 0 || thickness <= 0) {
            return;
        }
        double outerSquared = (double) radius * radius;
        double inner = Math.max(0, radius - thickness);
        double innerSquared = inner * inner;
        for (int dy = -radius; dy < radius; dy++) {
            for (int dx = -radius; dx < radius; dx++) {
                double distanceSquared = (dx + 0.5d) * (dx + 0.5d) + (dy + 0.5d) * (dy + 0.5d);
                if (distanceSquared < innerSquared || distanceSquared >= outerSquared) {
                    continue;
                }
                double angle = Math.atan2(dx + 0.5d, -(dy + 0.5d)) / (Math.PI * 2d);
                double progress = angle - startFraction;
                progress -= Math.floor(progress);
                int pixelColor = progress < span ? color : trackColor;
                if ((pixelColor >>> 24) != 0) {
                    g.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, pixelColor);
                }
            }
        }
    }

    public static float cyclePhase(float timeSeconds, int periodTicks) {
        float periodSeconds = Math.max(0.05f, periodTicks / 20f);
        float t = timeSeconds / periodSeconds;
        return t - (float) Math.floor(t);
    }

    public static float breath(float timeSeconds) {
        return 0.84f + 0.16f * (float) Math.sin(timeSeconds * 6.98d);
    }

    public static int brighten(int rgb, float f) {
        float t = Mth.clamp(f, 0f, 1f);
        int r = (rgb >> 16 & 0xFF);
        int g = (rgb >> 8 & 0xFF);
        int b = (rgb & 0xFF);
        return Math.round(r + (255 - r) * t) << 16
                | Math.round(g + (255 - g) * t) << 8
                | Math.round(b + (255 - b) * t);
    }

    public static int withAlpha(int rgb, float alpha) {
        int a = Math.round(Mth.clamp(alpha, 0f, 1f) * 255f);
        return a << 24 | rgb & 0xFFFFFF;
    }
}
