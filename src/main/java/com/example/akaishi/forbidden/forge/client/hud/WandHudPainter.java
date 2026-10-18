package com.example.akaishi.forbidden.forge.client.hud;

import com.example.akaishi.forbidden.magic.ClientMagicData;
import com.example.akaishi.forbidden.magic.cast.MagicCastState;
import com.example.akaishi.forbidden.magic.table.MagicSource;
import com.example.akaishi.forbidden.sanity.ClientSanityData;
import com.example.akaishi.forbidden.wand.WandItem;
import com.example.akaishi.forbidden.wand.WandModifiers;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** 法杖准星 HUD：独立归一的双轨读数与逐像素环带。 */
public final class WandHudPainter {

    // 以整数像素直接绘制原方案约 50% 的尺寸，避免 GUI 缩放矩阵造成半像素模糊。
    public static final int RING_RADIUS = 6;
    public static final int RING_THICKNESS = 1;
    public static final int RING_DX = 9;
    public static final int RING_DY = -3;
    public static final int BAR_WIDTH = 24;
    public static final int BAR_HEIGHT = 3;
    public static final int BAR_DY = 5;
    private static final int EXTRA_HEIGHT = 2;
    private static final int TRACK_GAP = 1;
    private static final int CELL_WIDTH = 3;
    public static final int HALO_RADIUS_FROM = 5;
    public static final int HALO_RADIUS_TO = 11;
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

    /** 就绪不画空圈；冷却只显示环形进度，避免文字压在准星与视线中心。 */
    public static void drawCooldownRing(GuiGraphics g, int cx, int cy, int radius,
            float remainFraction, boolean overloaded, float breath) {
        float remain = Mth.clamp(remainFraction, 0f, 1f);
        if (!overloaded && remain <= 0f) {
            return;
        }
        drawRingBand(g, cx, cy, radius, 0f, overloaded ? 1f : remain,
                overloaded ? COLOR_OVERLOAD : withAlpha(COLOR_CD, breath), RING_THICKNESS,
                overloaded ? COLOR_OVERLOAD : COLOR_RING_TRACK);
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

    public static void drawCastIndicator(GuiGraphics g, int cx, int cy, int rgb,
            MagicCastState state, float age, float progress, float transition, float segmentPhase) {
        int color = withAlpha(brighten(rgb, 0.25f), 0.9f);
        switch (state) {
            case CHARGING -> {
                drawRingBand(g, cx, cy, RING_RADIUS, 0f, Math.max(0.04f, progress), color, 1, COLOR_RING_TRACK);
                g.fill(cx - 1, cy - 1, cx + 1, cy + 1, withAlpha(rgb, 0.35f + 0.6f * progress));
            }
            case CHANNELING -> {
                float turn = age / 32f;
                ringArc(g, cx, cy, RING_RADIUS, turn, 0.30f, color, 1);
                ringArc(g, cx, cy, RING_RADIUS, turn + 0.5f, 0.30f, color, 1);
            }
            case VOLLEYING -> {
                int segment = (int) (segmentPhase * 4f);
                for (int i = 0; i < 4; i++) {
                    ringArc(g, cx, cy, RING_RADIUS, i * 0.25f, 0.16f,
                            withAlpha(rgb, i == segment ? 1f : 0.32f), 1);
                }
                g.fill(cx - 1, cy - 1, cx + 1, cy + 1, withAlpha(rgb, 1f - segmentPhase));
            }
            default -> { }
        }
        if (state == MagicCastState.CHANNELING || state == MagicCastState.VOLLEYING) {
            ringArc(g, cx, cy, RING_RADIUS, 0f, 1f, withAlpha(0xFFFFFF, transition * 0.55f), 1);
        }
    }

    public static void drawUnavailable(GuiGraphics g, int cx, int cy, boolean empty, boolean denied) {
        int color = denied ? COLOR_OVERLOAD : empty ? 0xB3707782 : COLOR_CD;
        if (empty) {
            g.fill(cx - 3, cy - 1, cx + 3, cy, color);
            g.fill(cx - 3, cy + 1, cx + 3, cy + 2, color);
        } else {
            for (int i = -2; i <= 2; i++) {
                g.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, color);
                g.fill(cx + i, cy - i, cx + i + 1, cy - i + 1, color);
            }
        }
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
        return com.example.akaishi.forbidden.visual.VisualColor.brighten(rgb, f);
    }

    public static int withAlpha(int rgb, float alpha) {
        return com.example.akaishi.forbidden.visual.VisualColor.withAlpha(rgb, alpha);
    }
}
