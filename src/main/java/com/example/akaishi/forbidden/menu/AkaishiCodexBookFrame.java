package com.example.akaishi.forbidden.menu;

import com.example.akaishi.forbidden.codex.CodexNode;
import com.example.akaishi.forbidden.menu.AkaishiCodexLayout.Split;
import com.example.akaishi.forbidden.menu.AkaishiCodexRender.Area;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import static com.example.akaishi.forbidden.menu.AkaishiCodexLayout.RIBBON_H;
import static com.example.akaishi.forbidden.menu.AkaishiCodexLayout.RIBBON_INSET;
import static com.example.akaishi.forbidden.menu.AkaishiCodexLayout.RIBBON_W;
import static com.example.akaishi.forbidden.menu.AkaishiCodexLayout.SHEET_HEAD_RULE_Y;
import static com.example.akaishi.forbidden.menu.AkaishiCodexLayout.SHEET_HEAD_TEXT_Y;
import static com.example.akaishi.forbidden.menu.AkaishiCodexLayout.SHEET_HEAD_H;
import static com.example.akaishi.forbidden.menu.AkaishiCodexLayout.chartArea;
import static com.example.akaishi.forbidden.menu.AkaishiCodexLayout.frameArea;
import static com.example.akaishi.forbidden.menu.AkaishiCodexLayout.pageArea;
import static com.example.akaishi.forbidden.menu.AkaishiCodexLayout.split;
import static com.example.akaishi.forbidden.menu.AkaishiCodexLayout.tabVisual;
import static com.example.akaishi.forbidden.menu.AkaishiCodexRender.TEX_EMBLEM_SIZE;
import static com.example.akaishi.forbidden.menu.AkaishiCodexRender.RULE_COLOR;
import static com.example.akaishi.forbidden.menu.AkaishiCodexRender.familyColor;
import static com.example.akaishi.forbidden.menu.AkaishiCodexRender.texturePresent;

/**
 * 禁忌秘典 · <b>书框与书签</b>渲染协作类（纯机械搬迁自 {@link AkaishiCodexRender}）。
 * <p>
 * 承接第②组"书底"API：皮革封边 + 纸面 + 内框线 + 书脊折痕的书底绘制（贴图优先、缺图回落）、
 * 书底贴图的<b>横向 5 段 × 纵向 3 段分段拉伸</b>、画布态纸面页眉、以及书签式分类标签的绘制。
 * <p>
 * <b>贴图优先、缺图回落</b>：书底优先读 {@code textures/gui/codex/book_frame.png}，读不到一律走
 * {@link AkaishiCodexBookArt} 的程序化笔触，<b>绝不 blit 不存在的贴图</b>（那会被画成缺图马赛克）。
 * 外部引用一律走 {@code AkaishiCodexRender} 的同名门面入口；本类方法体与原实现逐位一致。
 */
final class AkaishiCodexBookFrame {

    // ===== 贴图（建模侧并行产出；本轮只读，不新增资产） =====

    /** 整幅书底贴图（皮革封边 + 纸面 + 内框线；<b>分段拉伸</b>覆盖书体，见 {@link #blitBookTexture}） */
    private static final ResourceLocation TEX_BOOK_FRAME =
            new ResourceLocation("forbidden_hex", "textures/gui/codex/book_frame.png");
    /** 书底贴图原尺寸（<b>实测</b> 512×288 RGBA；切片边界即按这张图的像素坐标给出） */
    private static final int TEX_BOOK_W = 512;
    private static final int TEX_BOOK_H = 288;
    /**
     * <b>切片边界（对 512×288 原图逐行/逐列实测得出，非估算）</b>。
     *
     * <p><b>复现口径</b>：解码 PNG 后取中行 y=144 与中列 x=256 的亮度剖面 ——
     * 皮革段亮度 ≈ 20~70、纸面 ≈ 230、框线是纸面上的一根暗线（≈172）、书脊是纸面上的对称暗带；
     * 逐像素找"亮度突变处"即得下列边界（脚本已按临时文件纪律删除，需要时按此口径重写）。
     * <ul>
     *   <li><b>皮革封边</b>：四边各 20px（x=0..19 / x=492..511 / y=0..19 / y=268..287）；</li>
     *   <li><b>内框线</b>：x=42..469、y=42..245（<b>1px</b> 的线；框线外沿距四边 42px，
     *       即 (512−428)/2 = 42、(288−204)/2 = 42 —— 恰好居中，故左右/上下天然对称）；</li>
     *   <li><b>书脊折痕</b>：x=250..261（最暗在 255/256 ＝图宽正中，两侧渐亮），纵向贯穿纸面。</li>
     * </ul>
     * <p><b>为什么固定段取 {@link #SLICE_EDGE} = 43 而不是 42</b>：42 是框线的<b>外沿</b>，
     * 要连那 1px 线一起"固定不拉伸"，固定段必须吃到 43px，线才会整根落在固定段里
     * （⇒ 屏幕上框线距边恒为 {@code borderFor(书体宽)}，可精确计算）。
     * 校验：43 + 207 + 12 + 207 + 43 = 512；43 + 202 + 43 = 288。
     */
    static final int SLICE_EDGE = 43;
    private static final int SLICE_SPINE_L = 250;
    private static final int SLICE_SPINE_R = 262;
    /** 书脊折痕带在源图里的宽（固定段：不拉伸，屏幕上按同一边框系数缩放；
     *  书体五段切分（{@code AkaishiCodexLayout#split}）按它反算书脊缝宽） */
    static final int SLICE_SPINE_W = SLICE_SPINE_R - SLICE_SPINE_L;

    /** 内框线的补画色（半透明墨色；待调手感值）。
     *
     * <p>贴图里那根框线只有 <b>1px</b>，而固定段是 43px 源像素 → 21px 屏幕像素的<b>下采样</b>，
     * 按最近邻采样它<b>可能整根被跳过</b>（实测口径：书体宽 356 时固定段 43→21，
     * 最后一个目标像素取到源像素 41 而非线所在的 42）。故在"框线应在的位置"
     * （= 离书体边缘 {@code borderFor(书体宽)} 处）补画一根同位置的线：
     * 采样到了就重叠（同色系、只是略深），没采样到就补上 ⇒ <b>框线永远可见、位置永远精确</b>。
     */
    private static final int FRAME_LINE = 0x553A2E22;

    /** 书签内的徽记边长（16×16 居中；缺图回落的物品图标同样是 1:1 的 16×16，不缩放） */
    private static final int TAB_ICON = 16;
    private static final int TAB_EDGE = 0x803A2E22;
    private static final int TAB_EDGE_ON = 0xFF3A2E22;
    /** 未选中标签的压暗系数（保色相，只是"没翻开"） */
    private static final float TAB_DIM_MIX = 0.55f;

    private AkaishiCodexBookFrame() {
    }

    /**
     * 书底：皮革封边 + 纸面 + 内框线 + 书脊折痕 + 书签带。
     *
     * <p><b>贴图存在时走 {@link #blitBookTexture}</b>（横向 5 段 / 纵向 3 段的分段拉伸，
     * 边框与书脊宽度固定、只拉伸两页纸面）；<b>缺图回落</b>到
     * {@link AkaishiCodexBookArt#leather} + {@link AkaishiCodexBookArt#pagePaper}（纸纹/污渍/折角）
     * + {@link AkaishiCodexBookArt#fold}（摊开时）—— 两条路的纸面都是
     * {@code pageArea}（<b>框线之内</b>），故版心口径一致，缺图也不会让文字跑到纸外。
     *
     * @param open   true = 摊开的双页书（条目页）；false = 单张图版纸（族画布）
     * @param family 当前族（决定书签带颜色；null = 中性暗红）
     */
    static void book(GuiGraphics gui, Area book, int seed, boolean open, @Nullable String family) {
        if (texturePresent(TEX_BOOK_FRAME)) {
            blitBookTexture(gui, book);
        } else {
            AkaishiCodexBookArt.leather(gui, book.x(), book.y(), book.right(), book.bottom());
            if (open) {
                Area left = pageArea(book, 0);
                Area right = pageArea(book, 1);
                AkaishiCodexBookArt.pagePaper(gui, left.x(), left.y(), left.w(), left.h(), seed);
                AkaishiCodexBookArt.pagePaper(gui, right.x(), right.y(), right.w(), right.h(), seed + 1);
                AkaishiCodexBookArt.fold(gui, left.right() + (right.x() - left.right()) / 2,
                        left.y(), left.bottom());
            } else {
                Area sheet = frameArea(book);
                AkaishiCodexBookArt.pagePaper(gui, sheet.x(), sheet.y(), sheet.w(), sheet.h(), seed);
            }
        }
        // 内框线：补画一根（位置 = 离书体边缘 borderFor(书体宽) 处，与贴图那根同位置；见 FRAME_LINE）
        Area frame = frameArea(book);
        AkaishiCodexBookArt.ring(gui, frame.x() - 1, frame.y() - 1, frame.w() + 2, frame.h() + 2, FRAME_LINE);
        // 书签带：族色（不随贴图回落而消失，它是"这一册"的标记）；挂在右页纸面外侧、框线之内
        Area rightPaper = pageArea(book, 1);
        AkaishiCodexBookArt.ribbon(gui, rightPaper.right() - RIBBON_W - RIBBON_INSET, rightPaper.y(),
                RIBBON_W, RIBBON_H, family == null ? 0xFF8C3B34 : familyColor(family));
    }

    /**
     * <b>书底贴图的分段拉伸</b>：横向 5 段 × 纵向 3 段（不是 9 宫格，因为书脊在正中）。
     *
     * <p><b>为什么分段</b>：源图是"跨页整幅"，整幅拉伸会把内框线的位置拉成
     * {@code 书体宽 × 42/512}（≈8.2%）与 {@code 书体高 × 42/288}（≈14.6%），
     * 与代码里的固定页边距对不上，左页文字就会跑到框线外（用户实测的那条 bug）。
     * 分段后：<b>左右边框、上下边框、书脊全部按同一系数缩放且不拉伸</b>（宽度固定），
     * 只有<b>两页纸面中心</b>各自被拉伸，于是框线在屏幕上恒在
     * {@code 书体边缘 + borderFor(书体宽)}，可精确算出。
     *
     * <p>源图分段（实测，见 {@link #SLICE_EDGE}）：
     * 横 {@code [0,43) [43,250) [250,262) [262,469) [469,512)}；
     * 纵 {@code [0,43) [43,245) [245,288)}。目标段与源段一一对应，且各段之和严格等于书体宽/高
     * （整数边界，故不会出现 1px 的接缝或重叠）。
     */
    private static void blitBookTexture(GuiGraphics gui, Area book) {
        Split s = split(book);
        int[] dx = {book.x(), book.x() + s.border(), book.x() + s.border() + s.paperW(),
                book.x() + s.border() + s.paperW() + s.spineW(), book.right() - s.border()};
        int[] dw = {s.border(), s.paperW(), s.spineW(), s.paperW(), s.border()};
        int[] sx = {0, SLICE_EDGE, SLICE_SPINE_L, SLICE_SPINE_R, TEX_BOOK_W - SLICE_EDGE};
        int[] sw = {SLICE_EDGE, SLICE_SPINE_L - SLICE_EDGE, SLICE_SPINE_W,
                (TEX_BOOK_W - SLICE_EDGE) - SLICE_SPINE_R, SLICE_EDGE};
        int[] dy = {book.y(), book.y() + s.border(), book.bottom() - s.border()};
        int[] dh = {s.border(), book.h() - 2 * s.border(), s.border()};
        int[] sy = {0, SLICE_EDGE, TEX_BOOK_H - SLICE_EDGE};
        int[] sh = {SLICE_EDGE, TEX_BOOK_H - 2 * SLICE_EDGE, SLICE_EDGE};
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 5; col++) {
                gui.blit(TEX_BOOK_FRAME, dx[col], dy[row], dw[col], dh[row],
                        sx[col], sy[row], sw[col], sh[row], TEX_BOOK_W, TEX_BOOK_H);
            }
        }
    }

    /** 画布态的纸面页眉：左 = 族名，右 = 本族进度，其下一条手绘规律线（都落在框线之内） */
    static void sheetHead(GuiGraphics gui, Font font, Area book, Component left, Component right, int seed) {
        Area sheet = chartArea(book);
        int headY = sheet.y() - SHEET_HEAD_H;
        String l = font.plainSubstrByWidth(left.getString(), sheet.w() / 2);
        String r = font.plainSubstrByWidth(right.getString(), sheet.w() / 2);
        gui.drawString(font, l, sheet.x(), headY + SHEET_HEAD_TEXT_Y, AkaishiCodexPages.INK, false);
        gui.drawString(font, r, sheet.right() - font.width(r), headY + SHEET_HEAD_TEXT_Y,
                AkaishiCodexPages.INK_DIM, false);
        AkaishiCodexBookArt.rule(gui, sheet.x(), headY + SHEET_HEAD_RULE_Y, sheet.w(), RULE_COLOR, seed);
    }

    // ===== 书签式分类标签 =====

    /**
     * 一个书签式分类标签：<b>窄条贴边</b>（右缘贴住书体左缘），选中那枚向左突出
     * {@code TAB_LIFT}px（"翻出来"的感觉），未选中则压暗收回（保色相）。
     *
     * <p><b>只放一枚徽记</b>（用户拍板）：{@link #TAB_ICON}×{@link #TAB_ICON} 居中，
     * 优先用该族的徽记贴图（族内第一个节点的
     * {@code textures/gui/codex/emblem/<节点路径>.png}，32→16 为整数倍下采样，不糊）；
     * 缺图回落该节点的物品图标（1:1，不缩放）。<b>族名不再画在书签上</b>——
     * 书签只有 18 宽，字挤在里面既看不清也压图案；族名（与族色）改由悬停提示给出
     * （见 {@code AkaishiCodexScreen#renderTabTooltip}）。
     */
    static void tab(GuiGraphics gui, Area area, String family, @Nullable CodexNode node,
                    ItemStack icon, boolean selected, boolean hovered) {
        int accent = familyColor(family);
        Area tab = tabVisual(area, selected);
        int body = selected ? accent : AkaishiCodexBookArt.ink(accent, TAB_DIM_MIX);
        gui.fill(tab.x(), tab.y(), tab.right(), tab.bottom(), body);
        gui.fill(tab.x(), tab.y(), tab.right(), tab.y() + 1, 0x40000000);
        gui.fill(tab.x(), tab.bottom() - 1, tab.right(), tab.bottom(), 0x70000000);
        gui.fill(tab.x(), tab.y(), tab.x() + 1, tab.bottom(), 0x40000000);
        if (hovered) {
            gui.fill(tab.x() + 1, tab.y() + 1, tab.right() - 1, tab.bottom() - 1, 0x22FFFFFF);
        }
        AkaishiCodexBookArt.ring(gui, tab.x(), tab.y(), tab.w(), tab.h(), selected ? TAB_EDGE_ON : TAB_EDGE);
        drawTabIcon(gui, tab, icon, node);
    }

    /** 书签唯一的图案：族徽记贴图优先（16 居中），缺图回落该节点物品图标（1:1，不缩放） */
    private static void drawTabIcon(GuiGraphics gui, Area tab, ItemStack icon, @Nullable CodexNode node) {
        int x = tab.x() + (tab.w() - TAB_ICON) / 2;
        int y = tab.y() + (tab.h() - TAB_ICON) / 2;
        if (node != null) {
            ResourceLocation tex = AkaishiCodexEmblem.texture(node);
            if (texturePresent(tex)) {
                gui.blit(tex, x, y, TAB_ICON, TAB_ICON,
                        0f, 0f, TEX_EMBLEM_SIZE, TEX_EMBLEM_SIZE, TEX_EMBLEM_SIZE, TEX_EMBLEM_SIZE);
                return;
            }
        }
        if (icon != null && !icon.isEmpty()) {
            gui.renderItem(icon, x, y);
        }
    }
}
