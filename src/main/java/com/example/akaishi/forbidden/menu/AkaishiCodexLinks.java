package com.example.akaishi.forbidden.menu;

import com.example.akaishi.forbidden.codex.CodexFamily;
import com.example.akaishi.forbidden.codex.CodexNode;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.example.akaishi.forbidden.menu.AkaishiCodexRender.TIP_DIM;
import static com.example.akaishi.forbidden.menu.AkaishiCodexRender.TIP_JUMP;
import static com.example.akaishi.forbidden.menu.AkaishiCodexRender.familyColor;
import static com.example.akaishi.forbidden.menu.AkaishiCodexRender.styleOf;

/**
 * 禁忌秘典 · <b>画布连线与悬停提示</b>渲染协作类（纯机械搬迁自 {@link AkaishiCodexRender}）。
 * <p>
 * 承接画布（族图）的直角折线连线（先横后竖、拐角圆化、箭头指向挡路的前置）、
 * 以及节点悬停提示（名称/族/层级/状态/描述/挡路者或线索）与单行提示。
 * <p>
 * 外部引用一律走 {@code AkaishiCodexRender} 的同名门面入口；本类方法体与原实现逐位一致。
 */
final class AkaishiCodexLinks {

    // ===== 连线（待调手感值） =====

    /** 拐角圆化半径 */
    private static final int LINK_CORNER = 4;
    /** 挡路连线（粗）与常态连线（细）的线宽（外部经 {@code AkaishiCodexRender.LINK_W_*} 门面引用） */
    static final int LINK_W_THICK = 2;
    static final int LINK_W_THIN = 1;
    /** 箭头长度与半宽 */
    private static final int ARROW_LEN = 9;
    private static final int ARROW_HALF = 4;
    /** 箭头尾巴离目标徽记中心的距离（≈ 徽记半径 + 3，故不遮图案；与画布的徽记尺寸对齐） */
    private static final int ARROW_GAP = 20;
    /** 连线三档透明度：挡路高亮 / 常态 / 被挡节点的其余入线（待调手感值；
     *  外部经 {@code AkaishiCodexRender.LINK_ALPHA_*} 门面引用） */
    static final int LINK_ALPHA_HIGHLIGHT = 0xE8;
    static final int LINK_ALPHA_NORMAL = 0xB0;
    static final int LINK_ALPHA_DIM = 0x38;

    private AkaishiCodexLinks() {
    }

    // ===== 连线（直角折线） =====

    /**
     * 一条前置连线：<b>只走横竖</b>（先横后竖），拐角圆化 {@link #LINK_CORNER} 像素。
     *
     * @param thickness 线宽（挡路的那根用 {@link #LINK_W_THICK}）
     * @param arrow     true = 在 {@code (x1,y1)} 这一端画箭头，<b>箭头指向 (x1,y1)</b>
     *                  （调用方把 (x1,y1) 传成"挡路的前置"，于是箭头就指着挡你的那一个）
     */
    static void link(GuiGraphics gui, int x1, int y1, int x2, int y2, int color,
                     int thickness, boolean arrow) {
        if (x1 == x2 || y1 == y2) {
            segment(gui, x1, y1, x2, y2, color, thickness);
            if (arrow) {
                arrowHead(gui, x1, y1, x2, y2, color);
            }
            return;
        }
        int signX = x2 > x1 ? 1 : -1;
        int signY = y2 > y1 ? 1 : -1;
        int r = Math.min(LINK_CORNER, Math.min(Math.abs(x2 - x1), Math.abs(y2 - y1)) / 2);
        if (r < 1) {
            segment(gui, x1, y1, x2, y1, color, thickness);
            segment(gui, x2, y1, x2, y2, color, thickness);
        } else {
            // 两条腿各缩掉圆角半径，中间补一段四分之一圆（凸角朝外）
            segment(gui, x1, y1, x2 - signX * r, y1, color, thickness);
            segment(gui, x2, y1 + signY * r, x2, y2, color, thickness);
            cornerArc(gui, x2 - signX * r, y1 + signY * r, r, signX, signY, color, thickness);
        }
        if (arrow) {
            // 第一段是"从 (x1,y1) 横向走到拐角"，故箭头方向由拐角指回 (x1,y1)
            arrowHead(gui, x1, y1, x2, y1, color);
        }
    }

    /** 圆角：以 (cx,cy) 为圆心画一段四分之一圆弧（两个端点分别落在两条腿上） */
    private static void cornerArc(GuiGraphics gui, int cx, int cy, int r,
                                  int signX, int signY, int color, int thickness) {
        int steps = Math.max(3, r * 3);
        for (int i = 0; i <= steps; i++) {
            double t = i * (Math.PI / 2) / steps;
            int px = cx + (int) Math.round(signX * r * Math.sin(t));
            int py = cy + (int) Math.round(-signY * r * Math.cos(t));
            dot(gui, px, py, color, thickness);
        }
    }

    /** 直线段（逐像素 fill；厚度按方形块画） */
    private static void segment(GuiGraphics gui, int x1, int y1, int x2, int y2, int color, int thickness) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        if (steps == 0) {
            dot(gui, x2, y2, color, thickness);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            dot(gui, x1 + (x2 - x1) * i / steps, y1 + (y2 - y1) * i / steps, color, thickness);
        }
    }

    private static void dot(GuiGraphics gui, int x, int y, int color, int thickness) {
        int t = Math.max(1, thickness);
        gui.fill(x, y, x + t, y + t, color);
    }

    /**
     * 箭头：尖端在 {@code (targetX,targetY)} 那一侧，尾巴落在连线上（距目标 {@link #ARROW_GAP} px，
     * 因此不会盖住目标的徽记图案）。
     */
    private static void arrowHead(GuiGraphics gui, int targetX, int targetY, int fromX, int fromY, int color) {
        double dx = targetX - fromX;
        double dy = targetY - fromY;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1.0D) {
            return;
        }
        double ux = dx / len;
        double uy = dy / len;
        double tailX = targetX - ux * ARROW_GAP;
        double tailY = targetY - uy * ARROW_GAP;
        double px = -uy;
        double py = ux;
        for (int i = 0; i <= ARROW_LEN; i++) {
            int half = ARROW_HALF * (ARROW_LEN - i) / ARROW_LEN;
            int bx = (int) Math.round(tailX + ux * i);
            int by = (int) Math.round(tailY + uy * i);
            for (int j = -half; j <= half; j++) {
                int qx = (int) Math.round(bx + px * j);
                int qy = (int) Math.round(by + py * j);
                gui.fill(qx, qy, qx + 1, qy + 1, color);
            }
        }
    }

    // ===== 悬停提示 =====

    /**
     * 节点悬停提示：名称（按状态上色）→ 族 · 层级[ · 禁忌] → 层级 → 状态 → 一句话描述 →
     * 挡路者（被挡时）或线索（可研究但条件未齐时）。
     */
    static void tooltip(GuiGraphics gui, Font font, int mouseX, int mouseY, CodexNode node,
                        @Nullable AkaishiCodexSync.NodeView view,
                        AkaishiCodexEmblem.Mark mark, @Nullable CodexNode blocker) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(node.nameKey())
                .withStyle(styleOf(AkaishiCodexEmblem.nameColor(mark, node.forbidden()))));
        MutableComponent meta = Component.translatable(CodexFamily.nameKey(node.family()))
                .withStyle(styleOf(familyColor(node.family())))
                .append(Component.literal(" · "))
                .append(Component.translatable(node.tier().nameKey()));
        if (node.forbidden()) {
            meta.append(Component.literal(" · ")).append(Component.translatable("gui.forbidden_hex.codex.tip.forbidden"));
        }
        lines.add(meta);
        lines.add(Component.translatable("gui.forbidden_hex.codex.detail.tier",
                Component.translatable(node.tier().nameKey())).withStyle(styleOf(TIP_DIM)));
        lines.add(Component.translatable(AkaishiCodexEmblem.stateKey(mark)));
        String desc = Component.translatableWithFallback(node.descKey(), "").getString();
        if (!desc.isEmpty()) {
            lines.add(Component.literal(desc).withStyle(styleOf(TIP_DIM)));
        }
        if (blocker != null) {
            lines.add(Component.translatable("gui.forbidden_hex.codex.tip.blocker",
                    Component.translatable(blocker.nameKey())).withStyle(styleOf(TIP_JUMP)));
        } else if (AkaishiCodexPages.needsHint(node, view)) {
            lines.add(Component.translatable("gui.forbidden_hex.codex.page.hint",
                    AkaishiCodexPages.hintText(node)).withStyle(styleOf(TIP_JUMP)));
        }
        gui.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    /** 一行提示（书签悬停 / 页脚按钮 / 可跳转行） */
    static void tip(GuiGraphics gui, Font font, int mouseX, int mouseY, Component text) {
        gui.renderTooltip(font, text, mouseX, mouseY);
    }
}
