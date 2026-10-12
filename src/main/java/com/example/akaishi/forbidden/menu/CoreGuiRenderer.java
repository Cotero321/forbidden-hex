package com.example.akaishi.forbidden.menu;

import com.example.akaishi.forbidden.item.MagicCoreItem;
import com.example.akaishi.forbidden.magic.table.MagicGlyphs;
import com.example.akaishi.forbidden.magic.table.MagicInkTable;
import com.example.akaishi.forbidden.magic.table.MagicSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 铭文镶嵌台的中央核心绘制。
 *
 * <p>用真实像素圆盘 / 圆环（逐行扫描填充）画出核心外壳与内腔，
 * 而不是稀疏点阵或十字方块；中心始终是“核心展示位”，八等角槽嵌在外圈上。
 * 中心、槽位坐标唯一来自 {@link CoreLayout}。
 */
public final class CoreGuiRenderer {
    private CoreGuiRenderer() {}

    /** 外壳 / 内腔用色 */
    private static final int CAVITY = 0xFF0E0C12;
    private static final int RIM_DARK = 0xFF1A1620;
    private static final int RIM_BRASS = 0xFFC0A278;
    private static final int METAL = 0xFF322B36;

    /**
     * 画一颗核心的圆环 + 八等角镶嵌位。
     *
     * @param cx,cy     环心（= 核心物品槽的中心）
     * @param radius    槽位轨道半径（中央区 33）
     * @param core      要显示的核心物品；不是核心时只画空壳
     * @param activeSlot 当前编辑槽（-1 表示不强调）
     * @param level      开放词位依据的等级（<b>玩家等级</b>，不是待刻核的等级）
     * @param draftWords 八个槽位的草稿词 id（空串=无草稿）；每个槽位显示自己的草稿，切槽不丢
     * @param draftInks  八个槽位各自冻结的墨（与 draftWords 同下标）；某一格为空则用默认笔色
     */
    public static void render(GuiGraphics g, int cx, int cy, int radius, ItemStack core,
                              int activeSlot, int level, String[] draftWords, String[] draftInks) {
        float k = radius / 33f;
        boolean hasCore = core.getItem() instanceof MagicCoreItem;
        MagicSource src = hasCore ? MagicCoreItem.source(core) : null;
        int source = src == null ? 0x6E6672 : src.rgb();
        int sourceColor = 0xFF000000 | source;

        // —— 核心外壳：中心留空给「核心物品槽」，环体围绕它 ——
        disc(g, cx, cy, scaled(26, k), CAVITY);
        ring(g, cx, cy, scaled(30, k), scaled(26, k), RIM_DARK);
        ring(g, cx, cy, scaled(26, k), scaled(23, k), RIM_BRASS);
        ring(g, cx, cy, scaled(23, k), scaled(20, k), METAL);
        ring(g, cx, cy, scaled(20, k), scaled(17, k), sourceColor);
        disc(g, cx, cy, scaled(15, k), CAVITY);

        // —— 源结构（决定核心的“体质”）——
        if (hasCore) drawSource(g, cx, cy, source, src.id());

        // —— 八等角镶嵌位：仅显示已镶嵌词，不可放置物品 ——
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4 + Math.PI / 8;
            int sx = cx + (int) Math.round(Math.cos(angle) * radius);
            int sy = cy + (int) Math.round(Math.sin(angle) * radius);
            boolean open = CoreLayout.isSlotOpen(i, level);
            boolean active = i == activeSlot;
            int edge = active ? 0xFFFFD36A : open ? sourceColor : 0xFF393138;
            slotBox(g, sx, sy, edge);
            if (!open) {
                seal(g, sx, sy);
                continue;
            }
            String word = hasCore ? MagicCoreItem.word(core, i) : "";
            String ink = hasCore ? MagicCoreItem.ink(core, i) : "";
            // 草稿优先：放核时草稿已从核回填，之后草稿即唯一真源，
            // 玩家一改就立刻可见，不会再被核里的旧 NBT 压住（"看着不刷新"）
            if (draftWords != null && i < draftWords.length
                    && draftWords[i] != null && !draftWords[i].isEmpty()) {
                word = draftWords[i];
                ink = draftInks != null && i < draftInks.length && draftInks[i] != null ? draftInks[i] : "";
            }
            drawWordMark(g, sx, sy, word, ink);
        }
    }

    private static int scaled(int base, float k) { return Math.max(2, Math.round(base * k)); }

    /** 绘制词的符文（默认可写态）。 */
    public static void drawWordMark(GuiGraphics g, int x, int y, String word, String ink) {
        drawWordMark(g, x, y, word, ink, true);
    }

    /**
     * 绘制词的符文；{@code enabled=false} 时画成暗色（该词结构上不允许写入）。
     *
     * <p>符文唯一来源是 {@link MagicGlyphs}（词表符号本身），所以词表网格、核心圆环
     * 永远是同一个字形。以后任何界面要画符文，直接调 {@link #drawRune} 即可，
     * 不要再自己拼笔画。
     */
    public static void drawWordMark(GuiGraphics g, int x, int y, String word, String ink, boolean enabled) {
        var def = MagicInkTable.get(ink);
        int base = def == null ? 0xC0A278 : def.rgb();
        drawRune(g, x, y, word, enabled ? (base | 0xFF000000) : 0xFF4E4650);
    }

    /** 符文绘制（可复用）：把词表符号以 {@code x,y} 为中心画出；未知词不画。 */
    public static void drawRune(GuiGraphics g, int x, int y, String wordId, int argb) {
        Component rune = MagicGlyphs.component(wordId);
        if (rune.getString().isEmpty()) return;
        Font font = Minecraft.getInstance().font;
        g.drawString(font, rune, x - font.width(rune) / 2, y - 4, argb, false);
    }

    private static void drawSource(GuiGraphics g, int x, int y, int color, String source) {
        int c = color | 0xD0000000;
        if ("life".equals(source)) {
            stroke(g, x, y - 12, x, y + 11, c);
            for (int i = 0; i < 4; i++) {
                stroke(g, x, y - 7 + i * 5, x - 7 + i * 2, y - 10 + i * 5, c);
                g.fill(x - 9 + i * 2, y - 12 + i * 5, x - 6 + i * 2, y - 9 + i * 5, color | 0xFF000000);
            }
        } else if ("san".equals(source)) {
            for (int i = 0; i < 4; i++) {
                stroke(g, x - 10 + i * 7, y - 7 + (i & 1) * 9, x - 4 + i * 7, y + 2 - (i & 1) * 9, c);
            }
        } else if ("dark".equals(source)) {
            stroke(g, x - 9, y - 9, x + 7, y + 7, c);
            stroke(g, x + 8, y - 6, x - 7, y + 9, 0xFF111019);
            g.fill(x - 9, y - 3, x - 4, y + 1, 0xFF0D0C12);
            g.fill(x + 3, y + 4, x + 8, y + 8, 0xFF0D0C12);
        } else {
            for (int i = 0; i < 4; i++) {
                int a = i * 90 + 45;
                int dx = (int) Math.round(Math.cos(Math.toRadians(a)) * 9);
                int dy = (int) Math.round(Math.sin(Math.toRadians(a)) * 9);
                g.fill(x + dx - 3, y + dy - 3, x + dx + 3, y + dy + 3, 0xFFD9E6F0);
            }
        }
    }

    /** 实心圆盘（逐行扫描，保证硬边圆）。 */
    private static void disc(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.sqrt((double) r * r - (double) dy * dy);
            g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    /** 圆环（外半径 exclusive 内半径，逐行扫描左右两条带）。 */
    private static void ring(GuiGraphics g, int cx, int cy, int rOuter, int rInner, int color) {
        for (int dy = -rOuter; dy <= rOuter; dy++) {
            int ox = (int) Math.sqrt((double) rOuter * rOuter - (double) dy * dy);
            int ix = Math.abs(dy) < rInner
                    ? (int) Math.sqrt((double) rInner * rInner - (double) dy * dy) : 0;
            if (ox <= ix) continue;
            g.fill(cx - ox, cy + dy, cx - ix, cy + dy + 1, color);
            g.fill(cx + ix, cy + dy, cx + ox + 1, cy + dy + 1, color);
        }
    }

    private static void stroke(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= steps; i++) {
            int x = x0 + (x1 - x0) * i / Math.max(1, steps);
            int y = y0 + (y1 - y0) * i / Math.max(1, steps);
            g.fill(x, y, x + 2, y + 2, color);
        }
    }

    private static void seal(GuiGraphics g, int x, int y) {
        g.fill(x - 1, y - 5, x + 1, y + 5, 0xFF5A4040);
        g.fill(x - 5, y - 1, x + 5, y + 1, 0xFF5A4040);
    }

    private static void slotBox(GuiGraphics g, int x, int y, int edge) {
        g.fill(x - 8, y - 8, x + 8, y + 8, 0xFF171216);
        g.fill(x - 7, y - 7, x + 7, y + 7, edge);
        g.fill(x - 5, y - 5, x + 5, y + 5, 0xFF2A2020);
    }
}
