package com.example.akaishi.forbidden.menu;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Objects;

/**
 * 秘典的「笔触原语」：纸张质感（渐变/污渍/折角）、装订线、书签带、页码、小字旁注、手绘线、
 * 圈注与烟雾。
 *
 * <p><b>它在分层里的位置</b>：这是<b>最底下的一层像素笔触</b>，只知道"给我坐标与种子，我画一笔"；
 * <b>语义化绘制</b>（整幅书底、书签标签、徽记、直角连线、书页排版、配方表）在
 * {@link AkaishiCodexRender} 与 {@link AkaishiCodexEmblem} 里，由它们调这里。
 * 布局、状态、可见性一律不在这两层里 —— 那是屏幕、画布与内容层的事。
 *
 * <p><b>零贴图资产</b>：全部由 {@code GuiGraphics.fill} 的像素块拼出，不引入任何纹理/模型；
 * 贴图（书底/徽记）由 {@link AkaishiCodexRender} 负责"有就用、没有就回落"。
 *
 * <p><b>本层不做任何 pose 变换</b>：上一版的"小字旁注"靠 {@code pose().scale(0.78)} 实现，
 * 那会让字形被非整数重采样（观感发虚），本轮已删除 —— 文字一律 1:1。
 *
 * <p><b>确定性随机（关键）</b>：污渍、撕边、手绘抖动一律走 {@link #noise(int, int)} 的整数散列，
 * 种子由调用方按"页身份"（层级 + 族 + 节点 + 摊 + 左右页）算好传进来。
 * 因此<b>同一页每帧画出来完全一样</b>，不会随帧抖动，也不依赖 {@code Random} 的调用时序 ——
 * 这是"污渍像印在纸上"而不是"污渍在爬"的唯一做法。
 */
final class AkaishiCodexBookArt {

    // ===== 纸张与皮革（待调手感值） =====

    private static final int PAPER = 0xFFF2E7CE;
    private static final int COVER = 0xFF3B2B22;
    private static final int COVER_EDGE = 0xFF211710;
    private static final int COVER_LIGHT = 0xFF5C4636;

    /** 纸面渐变的暗色（RGB 部分，alpha 逐带叠加） */
    private static final int SHADE_RGB = 0x4A3A24;
    /** 渐变带数（逐像素渐变太贵，按带近似；待调手感值） */
    private static final int GRADIENT_BANDS = 12;
    private static final int GRADIENT_MAX_ALPHA = 0x12;

    /** 污渍（每页几处；alpha 很小，只求"旧纸"不求"脏"；待调手感值） */
    private static final int STAIN_COUNT = 4;
    private static final int STAIN_ALPHA = 0x14;
    private static final int STAIN_RGB = 0x6B5433;

    /** 折角（右下角翻起的小三角；待调手感值） */
    private static final int DOG_EAR = 9;
    private static final int DOG_EAR_FACE = 0xFFE3D5B4;
    private static final int DOG_EAR_EDGE = 0x70705C38;

    /** 装订线（书脊）：两道由内向外渐淡的阴影 + 一条中线 */
    private static final int FOLD_RGB = 0x201308;
    private static final int FOLD_LINE = 0x88341F10;
    private static final int FOLD_BANDS = 4;

    /** 书签带：主色由调用方给（当前族色），两侧压一条暗边 */
    private static final int RIBBON_EDGE = 0x66000000;
    /** 书签下端的燕尾缺口（待调手感值） */
    private static final int RIBBON_TAIL = 4;

    /** 手绘线的抖动幅度与断口密度（待调手感值） */
    private static final int SKETCH_JITTER = 1;
    private static final int SKETCH_BREAK_MASK = 7;

    private AkaishiCodexBookArt() {
    }

    // ===== 确定性随机 =====

    /**
     * 整数散列（splitmix 风格）：同 seed + 同序号恒得同值。
     * <p>只用于"纸面装饰"，不承载任何语义；跨 JVM 稳定（纯整数运算）。
     */
    static long noise(int seed, int index) {
        long h = seed * 0x9E3779B97F4A7C15L + (long) index * 0xBF58476D1CE4E5B9L;
        h ^= h >>> 30;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return h;
    }

    /**
     * 页身份 → 种子。调用方按"这一页是谁"给零件（层级 / 族 / 节点 / 摊 / 左右页），
     * 换页即换种子、同页恒定。{@code Objects.hash} 走 {@code String.hashCode}，跨 JVM 稳定。
     */
    static int seed(Object... parts) {
        return Objects.hash(parts);
    }

    // ===== 纸与皮革 =====

    /**
     * 一页纸：底色 → 自上而下渐暗的带 → 确定性污渍 → 右下折角。
     *
     * @param seed 本页的身份种子（见 {@link #seed(Object...)}）
     */
    static void pagePaper(GuiGraphics gui, int px, int py, int pw, int ph, int seed) {
        gui.fill(px, py, px + pw, py + ph, PAPER);
        for (int i = 0; i < GRADIENT_BANDS; i++) {
            int alpha = GRADIENT_MAX_ALPHA * i / GRADIENT_BANDS;
            int top = py + ph * i / GRADIENT_BANDS;
            int bottom = py + ph * (i + 1) / GRADIENT_BANDS;
            if (alpha > 0) {
                gui.fill(px, top, px + pw, bottom, (alpha << 24) | SHADE_RGB);
            }
        }
        for (int i = 0; i < STAIN_COUNT; i++) {
            stain(gui, px, py, pw, ph, seed, i);
        }
        dogEar(gui, px + pw, py + ph, DOG_EAR);
    }

    /** 一处污渍：三笔错位的小点（位置由散列决定，永不出纸面） */
    private static void stain(GuiGraphics gui, int px, int py, int pw, int ph, int seed, int index) {
        long r = noise(seed, 0x100 + index);
        int innerW = Math.max(1, pw - 12);
        int innerH = Math.max(1, ph - 12);
        int sx = px + 6 + (int) Math.floorMod(r, (long) innerW);
        int sy = py + 6 + (int) Math.floorMod(r >> 19, (long) innerH);
        int color = (STAIN_ALPHA << 24) | STAIN_RGB;
        int faint = ((STAIN_ALPHA * 2 / 3) << 24) | STAIN_RGB;
        gui.fill(sx, sy, sx + 3, sy + 2, color);
        gui.fill(sx - 2 + (int) ((r >> 43) & 1L), sy + 1, sx, sy + 3, faint);
        gui.fill(sx + 1, sy - 2, sx + 2, sy, faint);
    }

    /** 右下折角：从角往左上的三角折面 + 一条深色斜边（pagePaper 内部用） */
    private static void dogEar(GuiGraphics gui, int right, int bottom, int size) {
        for (int i = 0; i < size; i++) {
            int rowY = bottom - 1 - i;
            int rowX = right - size + i;
            gui.fill(rowX, rowY, right, rowY + 1, DOG_EAR_FACE);
            gui.fill(rowX, rowY, rowX + 1, rowY + 1, DOG_EAR_EDGE);
        }
    }

    /** 封面皮革：受光在上/左、背光在下/右的厚皮板 */
    static void leather(GuiGraphics gui, int left, int top, int right, int bottom) {
        gui.fill(left, top, right, bottom, COVER);
        gui.fill(left, top, right, top + 1, COVER_LIGHT);
        gui.fill(left, top, left + 1, bottom, COVER_LIGHT);
        gui.fill(left, bottom - 1, right, bottom, COVER_EDGE);
        gui.fill(right - 1, top, right, bottom, COVER_EDGE);
    }

    /** 书脊折痕：两道由内向外渐淡的阴影 + 一条中线（画在纸之上、内容之下） */
    static void fold(GuiGraphics gui, int cx, int top, int bottom) {
        for (int i = 0; i < FOLD_BANDS; i++) {
            int color = ((0x10 + (FOLD_BANDS - 1 - i) * 0x08) << 24) | FOLD_RGB;
            gui.fill(cx - 1 - i, top, cx - i, bottom, color);
            gui.fill(cx + i, top, cx + i + 1, bottom, color);
        }
        gui.fill(cx - 1, top, cx + 1, bottom, FOLD_LINE);
    }

    /** 书签带：竖条 + 燕尾缺口 + 右缘暗边（颜色由调用方给当前族色） */
    static void ribbon(GuiGraphics gui, int x, int y, int w, int h, int color) {
        if (w <= 1 || h <= RIBBON_TAIL + 2) {
            return;
        }
        gui.fill(x, y, x + w, y + h - RIBBON_TAIL, color);
        gui.fill(x, y + h - RIBBON_TAIL, x + w, y + h - RIBBON_TAIL + 2, color);
        gui.fill(x + 1, y + h - RIBBON_TAIL + 2, x + w - 1, y + h, color);
        gui.fill(x + w - 1, y, x + w, y + h - RIBBON_TAIL, RIBBON_EDGE);
    }

    /** 页码：印在页外侧（左页左下 / 右页右下）；{@code rightAligned} = 以给定 x 为右边界 */
    static void folio(GuiGraphics gui, Font font, int number, int x, int y, boolean rightAligned) {
        String text = Integer.toString(number);
        gui.drawString(font, text, rightAligned ? x - font.width(text) : x, y, 0xFF8A7A62, false);
    }

    /** 手绘等宽横线（逐像素抖动 0~1px，像用尺子比着却手抖画出来的规矩线） */
    static void rule(GuiGraphics gui, int x, int y, int w, int color, int seed) {
        for (int i = 0; i < w; i++) {
            int jitter = (int) Math.floorMod(noise(seed, 0x200 + i), 2L);
            gui.fill(x + i, y + jitter, x + i + 1, y + jitter + 1, color);
        }
    }

    /** 手绘矩形圈（朱笔圈注）：抖动的椭圆、留一处不闭合的缺口；{@code strokes} = 抬起笔的次数 */
    static void sketchRing(GuiGraphics gui, int cx, int cy, int halfW, int halfH, int steps, int color, int seed) {
        int lastX = Integer.MIN_VALUE;
        int lastY = Integer.MIN_VALUE;
        for (int i = 0; i <= steps; i++) {
            double angle = i * 2.0D * Math.PI / steps;
            long r = noise(seed, 0x300 + i);
            if ((r & 0x0FL) == 0L && i > 1 && i < steps - 1) {
                lastX = Integer.MIN_VALUE; // 抬笔：留一个断口，"手没画圆"
                continue;
            }
            int jitter = (int) Math.floorMod(r >> 8, (long) (SKETCH_JITTER + 1));
            int px = cx + (int) Math.round(Math.cos(angle) * (halfW + jitter));
            int py = cy + (int) Math.round(Math.sin(angle) * (halfH + jitter));
            drawSegment(gui, lastX, lastY, px, py, color);
            lastX = px;
            lastY = py;
        }
    }

    /** 烟雾弧的段数（外圈 / 内圈；待调手感值） */
    private static final int FUME_STEPS = 44;
    private static final int FUME_STEPS_INNER = 40;
    /** 半径起伏幅度与断口掩码（断口比手绘圈更密，才读成"烟"而不是"圈"；待调手感值） */
    private static final int FUME_WAVE = 3;
    private static final long FUME_BREAK_MASK = 0x7L;

    /**
     * 紫色烟雾描边（禁忌节点的状态记号）：两道半径起伏、断续更密的弧 —— 外深内浅。
     *
     * <p>与 {@link #sketchRing} 的区别：半径按散列起伏 ±1px、断口更多，像紫墨在纸上晕开的一圈烟，
     * 而不是刻意画圆的一笔。同样是确定性笔触（{@code seed} 由节点 id 派生），不会随帧抖动。
     */
    static void fume(GuiGraphics gui, int cx, int cy, int radius, int color, int seed) {
        wavyRing(gui, cx, cy, radius, color, seed, FUME_STEPS);
        wavyRing(gui, cx, cy, radius - 2, semi(color, 0x66), seed + 1, FUME_STEPS_INNER);
    }

    /** 半径起伏 + 断续的一圈（烟雾用；{@code radius} 为基准半径） */
    private static void wavyRing(GuiGraphics gui, int cx, int cy, int radius, int color, int seed, int steps) {
        int lastX = Integer.MIN_VALUE;
        int lastY = Integer.MIN_VALUE;
        for (int i = 0; i <= steps; i++) {
            double angle = i * 2.0D * Math.PI / steps;
            long r = noise(seed, 0x600 + i);
            if ((r & FUME_BREAK_MASK) == 0L && i > 2 && i < steps - 2) {
                lastX = Integer.MIN_VALUE; // 断口：烟是断续的
                continue;
            }
            int rad = radius + (int) Math.floorMod(r >> 7, (long) FUME_WAVE) - 1;
            int px = cx + (int) Math.round(Math.cos(angle) * rad);
            int py = cy + (int) Math.round(Math.sin(angle) * rad);
            drawSegment(gui, lastX, lastY, px, py, color);
            lastX = px;
            lastY = py;
        }
    }

    /** 用逐像素 fill 连两点（点很密时按最长边步进，不会漏点；{@code last < 0} = 抬笔起点） */
    private static void drawSegment(GuiGraphics gui, int x1, int y1, int x2, int y2, int color) {
        if (x1 == Integer.MIN_VALUE) {
            gui.fill(x2, y2, x2 + 1, y2 + 1, color);
            return;
        }
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        if (steps <= 0) {
            gui.fill(x2, y2, x2 + 1, y2 + 1, color);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            int px = x1 + (x2 - x1) * i / steps;
            int py = y1 + (y2 - y1) * i / steps;
            gui.fill(px, py, px + 1, py + 1, color);
        }
    }

    /** 1px 方框描边（四条 fill） */
    static void ring(GuiGraphics gui, int x, int y, int w, int h, int color) {
        gui.fill(x, y, x + w, y + 1, color);
        gui.fill(x, y + h - 1, x + w, y + h, color);
        gui.fill(x, y, x + 1, y + h, color);
        gui.fill(x + w - 1, y, x + w, y + h, color);
    }

    /** 取原色换透明度（印章、圈注一类的"半干墨"） */
    static int semi(int argb, int alpha) {
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    /** 族色 → 纸上墨色：压暗 RGB、保留色相（族色原色留给书签带与提示，纸面一律走本方法） */
    static int ink(int argb, float mix) {
        int r = (int) (((argb >> 16) & 0xFF) * mix);
        int g = (int) (((argb >> 8) & 0xFF) * mix);
        int b = (int) ((argb & 0xFF) * mix);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
