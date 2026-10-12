package com.example.akaishi.forbidden.menu;

import net.minecraft.client.gui.GuiGraphics;

import static com.example.akaishi.forbidden.menu.AkaishiCodexRender.Area;

/**
 * 禁忌秘典 · <b>版面几何唯一真源</b>（纯机械搬迁自 {@link AkaishiCodexRender}）。
 * <p>
 * 承接原渲染框架的第①组 API 与其常量：书体尺寸（按内容定）、双页书几何（由框线反算）、
 * 版心 / 画布视口 / 书签列 / 页内动作区 / 叶号基线，以及命中判定（{@link #in}）与
 * 裁剪开关（{@link #scissor}/{@link #unscissor}）。
 * <p>
 * 取消缩放的口径不变：所有几何直接用<b>实际 GUI 坐标</b>算、一律整数，
 * 字号与行高保持项目上限（{@link #LINE_H} = 10），本类不存在任何 {@code pose().scale(...)}。
 * 外部引用一律走 {@code AkaishiCodexRender} 的同名门面入口；本类方法体与原实现逐位一致。
 */
final class AkaishiCodexLayout {

    // ===== 书体尺寸（按内容定；上限 = 菜单里的设计基准；待调手感值） =====

    /** 书体尺寸上限（超上限一律截断并由页内滚动消化） */
    static final int MAX_BOOK_W = AkaishiCodexMenu.PANEL_W;
    static final int MAX_BOOK_H = AkaishiCodexMenu.PANEL_H;
    /** 书体尺寸下限（太短的一段文案也不该得到一本袖珍书） */
    private static final int MIN_BOOK_W = 320;
    private static final int MIN_BOOK_H = 160;
    /**
     * 摊开的双页书体宽（条目页：不随内容变，窄屏才被压窄）。
     *
     * <p>口径自洽：{@code borderFor(356) = round(356×0.06) = 21}；书脊 = {@code round(12×21/43) = 6}；
     * 框线内宽 = 356 − 42 = 314 ⇒ 两页纸各 (314−6)/2 = <b>154</b>；
     * 每页版心 = 154 − 10(外留白+滚动条) − 13(留白+装订余量) = <b>131</b>，可用行宽 121 —— 与上一版 124 同量级。
     */
    private static final int OPEN_BOOK_W = 356;
    /** 书体离屏幕左右边至少留的边距 */
    private static final int EDGE_MARGIN = 8;
    /** 顶部留给标题带的高度 / 底部留给页脚控件带的高度（书体垂直定位用） */
    private static final int TOP_RESERVE = 24;
    private static final int BOTTOM_RESERVE = 28;

    // ===== 双页书几何（全部由"框线"反算；待调手感值） =====

    /** 正文区右侧给滚动条让出的宽（含间隙；滚动条画在版心<b>之外</b>、仍在留白之内；
     *  包私有：正文滚动条绘制共用同一真源） */
    static final int SCROLL_W = 4;
    static final int SCROLL_GAP = 2;
    /**
     * 行尾留白：<b>可用行宽 = 版心宽 − 它</b>（"版心宽"与"可用行宽"分开定义）。
     *
     * <p>两件事都要它：① 正文不该顶到版心边界；② 中文"避头尾"允许把行首禁则标点留在上一行
     * （见 {@code AkaishiCodexPages} 的断行），那一行最多再宽出一个汉字（9px）——留白 ≥ 一个汉字宽，
     * 于是"吸标点"后的行也仍在正文区之内，绝不会挤到滚动条或纸缘上。
     */
    static final int LINE_TAIL_PAD = 10;
    /** 版心的最小可用宽（极端窄屏的兜底；再窄也按它算，行会被截断而不是越界；
     *  包私有：书页排版与配方页的换行宽口径共用同一真源） */
    static final int MIN_LINE_W = 24;
    /** 版心的最小可读宽（{@link #fitInside} 自检用；书体宽 ≥ {@link #MIN_BOOK_W} 时恒可达） */
    private static final int MIN_READABLE_W = 60;

    /**
     * <b>边框系数</b>（待调手感值）：框线在屏幕上距书体边缘的距离，按书体宽取比例再夹上下限。
     *
     * <p>贴图里框线距四边 42px（占宽 8.20%），照搬会让 356 宽的书留下 29px 边框（吃掉版心）；
     * 用户已拍板"边框厚度可按书体尺寸缩一缩（比例更匀）"，故取 6.0% 再夹 [12, 28]。
     * 该值<b>只由书体宽决定</b>（同一尺寸下恒定，也不与"按内容定高"循环依赖），
     * 版心/书体高全部由它反算 ⇒ <b>框线位置在屏幕上恰等于 {@code borderFor(书体宽)}</b>。
     */
    private static final double BORDER_RATIO = 0.06;
    private static final int BORDER_MIN = 12;
    private static final int BORDER_MAX = 28;

    /** 版心到框线内侧的留白（外侧；还要再让出滚动条宽，见 {@link #textArea}） */
    private static final int MARGIN = 4;
    /** 靠书脊一侧在 {@link #MARGIN} 之外<b>另加</b>的装订余量（两页各自独立算；待调手感值） */
    private static final int SPINE_GUTTER = 9;

    /** 页眉基线 / 页眉下规律线 / 正文顶（都相对"框线内"的纸面顶边；待调手感值；
     *  包私有：页眉绘制共用同一真源） */
    static final int HEAD_INSET = 6;
    static final int RULE_INSET = 18;
    private static final int HEAD_BAND = 24;
    /** 正文底到框线内下缘的距离（叶号就画在这一带里，故正文永不压叶号） */
    private static final int FOLIO_BAND = 13;
    /** 行高：<b>项目既有上限，永不缩放</b>（外部经 {@code AkaishiCodexRender.LINE_H} 门面引用） */
    static final int LINE_H = 10;
    /** 叶号距框线内下缘的距离，以及从纸面外侧向内缩进的量（待调手感值；包私有：叶号绘制共用） */
    private static final int FOLIO_BOTTOM = 11;
    static final int FOLIO_INSET = 10;
    /** 图版（画布态）四边留白与顶部页眉行高（页眉行高包私有：书框页眉绘制共用） */
    private static final int SHEET_PAD = 10;
    static final int SHEET_HEAD_H = 20;
    static final int SHEET_HEAD_TEXT_Y = 5;
    static final int SHEET_HEAD_RULE_Y = 17;
    /** 书签带：宽 / 高（颜色 = 当前族色），以及它距框线内侧的距离 */
    static final int RIBBON_W = 8;
    static final int RIBBON_H = 76;
    static final int RIBBON_INSET = 6;

    // ===== 书页内动作按钮（条件页底部；待调手感值） =====

    static final int ACTION_H = 14;
    private static final int ACTION_GAP = 6;
    /** 动作按钮在正文区里占掉的行数（算书体高度时给条件页让位，故不会被按钮压字；
     *  外部经 {@code AkaishiCodexRender.ACTION_ROWS} 门面引用） */
    static final int ACTION_ROWS = (ACTION_H + ACTION_GAP + LINE_H - 1) / LINE_H;

    // ===== 书签式分类标签（窄条贴边；用户已定规格） =====

    /** 书签宽 × 高（用户拍板 <b>18×40</b>；只放一枚徽记，族名改走悬停提示） */
    static final int TAB_W = 18;
    static final int TAB_H = 40;
    /** 书签垂直间距（用户拍板 3~4，取 4） */
    static final int TAB_GAP = 4;
    /** 选中那枚向左（书外）突出的距离（用户拍板 4；绘制与命中共用 {@link #tabVisual}） */
    private static final int TAB_LIFT = 4;
    /** 书签数（= 屏幕里的族数，用于把"最下面一枚书签"也算进垂直预算） */
    private static final int TAB_COUNT = 3;

    private AkaishiCodexLayout() {
    }

    /** 命中判定（鼠标是浮点，矩形是整数；一律整数比较，不做任何坐标换算） */
    static boolean in(double mouseX, double mouseY, Area area) {
        return mouseX >= area.x() && mouseX < area.right()
                && mouseY >= area.y() && mouseY < area.bottom();
    }

    /**
     * 按矩形开裁剪。{@code gui.enableScissor} 吃的是<b>屏幕坐标</b>，而本界面所有几何本来就是屏幕坐标
     * （取消视图变换的收益之一），故这里直接透传。
     */
    static void scissor(GuiGraphics gui, Area area) {
        gui.enableScissor(area.x(), area.y(), area.right(), area.bottom());
    }

    static void unscissor(GuiGraphics gui) {
        gui.disableScissor();
    }

    // ===== 书体尺寸（按内容定，最大 720×400） =====

    /**
     * <b>边框厚度</b>（= 框线在屏幕上距书体边缘的距离，见 {@link #BORDER_RATIO}）。
     *
     * <p>只吃书体宽 ⇒ 与"按内容定高"不构成循环；同一书体尺寸下恒定。
     * 版心、书体高、书脊缝宽全部由它反算，故框线位置永远可精确算出（就落在 {@code x + borderFor(w)}）。
     */
    static int borderFor(int bookW) {
        return clampSize((int) Math.round(bookW * BORDER_RATIO), BORDER_MIN, BORDER_MAX);
    }

    /** 书体的横向五段切分结果（边框 / 一页纸宽 / 书脊缝宽；包私有：书底贴图分段拉伸共用同一真源） */
    record Split(int border, int paperW, int spineW) {
    }

    /**
     * 把书体横切成五段：左边框 | 左页纸 | 书脊 | 右页纸 | 右边框。
     *
     * <p>书脊宽按<b>同一边框系数</b>缩放后取整（固定段，不拉伸），再做 0/1px 的奇偶修正，
     * 使两页纸宽<b>严格相等</b>（这是"左右两页必须对称"的落点）。
     */
    static Split split(Area book) {
        int b = borderFor(book.w());
        int inner = Math.max(2, book.w() - 2 * b);
        int spine = Math.max(2, Math.round(AkaishiCodexBookFrame.SLICE_SPINE_W * b / (float) AkaishiCodexBookFrame.SLICE_EDGE));
        if (((inner - spine) & 1) != 0) {
            spine++;
        }
        spine = Math.min(spine, Math.max(2, inner - 2));
        return new Split(b, Math.max(1, (inner - spine) / 2), spine);
    }

    /**
     * 一页正文需要 {@code rows} 行时，书体的高度：两侧边框 + 页眉带 + rows×行高 + 叶号带。
     *
     * <p>这是"书体高度按内容定"的唯一公式；调用方给的是<b>本节点所有可见页的最大需求行数</b>
     * （条件页还要加上 {@link #ACTION_ROWS} 给动作按钮让位），故同一节点翻页时高度恒定。
     * <b>两侧边框也要算进来</b>，否则页眉/叶号会压到框线或皮革上。
     */
    static int entryBookHeight(int bookW, int rows) {
        return 2 * borderFor(bookW) + HEAD_BAND + Math.max(1, rows) * LINE_H + FOLIO_BAND;
    }

    /** 条目页书体宽：<b>常量</b>（不随内容变）；窄屏时会被压到可用宽。
     *  口径自洽校验：{@code borderFor(356) = 21}、书脊 6、两页纸各 154 ⇒ 42 + 6 + 308 = 356。 */
    static int entryBookWidth(int screenW) {
        return clampSize(OPEN_BOOK_W, MIN_BOOK_W, maxBookWidth(screenW));
    }

    /**
     * 给定书体宽时，一页真正能画正文的<b>可用行宽</b>（版心再扣行尾留白 {@link #LINE_TAIL_PAD}）。
     *
     * <p><b>为什么要单独暴露它</b>：内容层要按这个宽做换行，而"书体高度按内容行数定"是反过来的
     * （书体高 ← 行数 ← 换行宽 ← 书体宽）。书体宽只由屏宽决定、与行数无关，
     * 于是调用顺序是"先算宽 → 再按这个宽算行数 → 再算高"，不存在循环。
     * 这里用一根探针书体调 {@link #textArea}，故与真正绘制时<b>同源</b>（不会两处口径漂移）。
     */
    static int pageTextWidth(int bookW) {
        Area probe = new Area(0, 0, bookW, MIN_BOOK_H);
        return Math.max(MIN_LINE_W, textArea(probe, 0).w() - LINE_TAIL_PAD);
    }

    /** 可用行宽（{@link #textArea} 再扣行尾留白）：内容层换行与绘制<b>共用这一口径</b> */
    static int lineWidth(Area book, int side) {
        return Math.max(MIN_LINE_W, textArea(book, side).w() - LINE_TAIL_PAD);
    }

    /** 条目页书体：宽由屏宽定，高按内容行数定 */
    static Area entryBook(int screenW, int screenH, int bookW, int rows) {
        int h = clampSize(entryBookHeight(bookW, rows), MIN_BOOK_H, maxBookHeight(screenH));
        return place(screenW, screenH, bookW, h);
    }

    /**
     * 画布态书体：宽高都按"该族节点包围盒 + 图版留白 + 两侧边框"定（受同一套上下限约束）。
     *
     * <p>边框宽度反过来依赖书体宽，故先按 {@link #BORDER_MIN} 估一版宽、再按真实边框定稿
     * （边框系数很小，估一次就够；定稿后 {@link #chartArea} 的视口宽恰好 = 包围盒宽）。
     */
    static Area chartBook(int screenW, int screenH, int contentW, int contentH) {
        int guess = clampSize(contentW + 2 * (SHEET_PAD + BORDER_MIN),
                MIN_BOOK_W, maxBookWidth(screenW));
        int w = clampSize(contentW + 2 * (SHEET_PAD + borderFor(guess)),
                MIN_BOOK_W, maxBookWidth(screenW));
        int h = clampSize(contentH + SHEET_HEAD_H + SHEET_PAD + 2 * borderFor(w),
                MIN_BOOK_H, maxBookHeight(screenH));
        return place(screenW, screenH, w, h);
    }

    /** 书体可用高（再扣掉标题带与页脚带）与上限取小 */
    private static int maxBookHeight(int screenH) {
        return Math.min(MAX_BOOK_H, screenH - TOP_RESERVE - BOTTOM_RESERVE);
    }

    /** 书体可用宽：书体是水平居中的，故左侧那条书签列要按"两侧都留"来预算（窄屏也不会把书签顶出屏幕） */
    private static int maxBookWidth(int screenW) {
        return Math.min(MAX_BOOK_W, screenW - 2 * (EDGE_MARGIN + TAB_W + TAB_LIFT));
    }

    /** 书签列总高（从书体顶边向下排满 {@link #TAB_COUNT} 枚） */
    private static int tabColumnHeight() {
        return TAB_COUNT * TAB_H + (TAB_COUNT - 1) * TAB_GAP;
    }

    /** 钳制到 [lo, hi]；窗口小到装不下下限时优先"装得下"（宁可少几行靠滚动，也不越出屏幕） */
    private static int clampSize(int want, int lo, int hi) {
        int low = Math.min(lo, Math.max(1, hi));
        return Math.max(low, Math.min(hi, want));
    }

    /**
     * 书的落位：水平恒居中；垂直"偏上居中"，并同时满足两条预算 ——
     * ① 书体本身要装进"标题带以下、页脚带以上"；② 左侧书签列（比书体高的短书）也要留在屏幕内
     * （书签从书体顶边向下排，书很短时若不额外上收，最下面那枚会掉出屏幕下沿）。
     */
    private static Area place(int screenW, int screenH, int w, int h) {
        int x = (screenW - w) / 2;
        int hi = Math.max(TOP_RESERVE, Math.min(
                screenH - BOTTOM_RESERVE - h,
                screenH - EDGE_MARGIN - tabColumnHeight()));
        int y = Math.max(TOP_RESERVE, Math.min((screenH - h) / 2, hi));
        return new Area(x, y, w, h);
    }

    // ===== 版面几何（屏幕/画布/内容层都从这里取，保证只有一份真源） =====

    /**
     * <b>框线以内的纸面</b>（= 书体四边各内缩 {@link #borderFor}）。
     *
     * <p>它是书页上一切文字/控件的<b>硬边界</b>：页眉、正文、叶号、动作按钮全部由它派生，
     * 故不可能越过贴图里那根内框线（左页曾出现的"越框"正是版心没吃这个边界导致的）。
     */
    static Area frameArea(Area book) {
        Split s = split(book);
        return new Area(book.x() + s.border(), book.y() + s.border(),
                book.w() - 2 * s.border(), book.h() - 2 * s.border());
    }

    /**
     * 一页纸面（side = 0 左页 / 1 右页）：<b>框线之内</b>、两页等宽、中间夹书脊缝。
     *
     * <p>两页宽严格相等（{@link #split} 的奇偶修正保证）；书脊<b>不参与拉伸</b>，
     * 故"跨页整幅"的源图在中间不会变形。
     */
    static Area pageArea(Area book, int side) {
        Split s = split(book);
        int first = book.x() + s.border();
        int x = side == 0 ? first : first + s.paperW() + s.spineW();
        return new Area(x, book.y() + s.border(), s.paperW(), book.h() - 2 * s.border());
    }

    /**
     * 一页的正文区（版心）：<b>由框线反算</b>，左右两页对称。
     *
     * <p>口径：外侧 = 纸面外缘 + {@code MARGIN}（再让出滚动条宽，滚动条画在版心之外）；
     * 靠书脊侧 = 纸面内缘 − {@code MARGIN} − {@code SPINE_GUTTER}（装订余量，两页各自独立算）。
     * 纵向 = 纸面顶 + {@code HEAD_BAND}（页眉带）到 纸面底 − {@code FOLIO_BAND}（叶号带）。
     * 结果一律经 {@link #fitInside} 自检钳进纸面，<b>永不越框</b>。
     */
    static Area textArea(Area book, int side) {
        Area paper = pageArea(book, side);
        int outer = MARGIN + SCROLL_W + SCROLL_GAP;
        int inner = MARGIN + SPINE_GUTTER;
        int left = side == 0 ? paper.x() + outer : paper.x() + inner;
        int right = side == 0 ? paper.right() - inner : paper.right() - outer;
        return fitInside(paper, left, paper.y() + HEAD_BAND, right - left,
                paper.h() - HEAD_BAND - FOLIO_BAND);
    }

    /**
     * <b>版心自检（永不越框的兜底）</b>：把算出的矩形硬钳进 {@code limit}（框线以内的纸面）之内。
     *
     * <p>宽高先收到纸面之内（宽不低于 {@link #MIN_READABLE_W}，高不低于一行），
     * 再把坐标拉回纸面 —— 于是即便以后有人调大 {@link #MARGIN}/换更窄的边框/换更小的字，
     * 也只会看到版心<b>变窄</b>，不会看到文字跑到框线外去。
     * 书体宽 ≥ {@link #MIN_BOOK_W}(320) 时纸面宽恒 ≥ 130 ⇒ 最小可读宽永远可达。
     */
    private static Area fitInside(Area limit, int x, int y, int w, int h) {
        int cw = Math.max(1, Math.min(w, limit.w()));
        if (limit.w() >= MIN_READABLE_W) {
            cw = Math.min(limit.w(), Math.max(MIN_READABLE_W, cw));
        }
        int ch = Math.max(LINE_H, Math.min(h, limit.h()));
        int cx = Math.max(limit.x(), Math.min(x, limit.right() - cw));
        int cy = Math.max(limit.y(), Math.min(y, limit.bottom() - ch));
        return new Area(cx, cy, cw, ch);
    }

    /** 画布（族图）的视口：<b>框线之内</b>再留白，顶部留出一行页眉 */
    static Area chartArea(Area book) {
        Area frame = frameArea(book);
        return new Area(frame.x() + SHEET_PAD, frame.y() + SHEET_HEAD_H,
                frame.w() - 2 * SHEET_PAD, frame.h() - SHEET_HEAD_H - SHEET_PAD);
    }

    /** 书签式分类标签的列：<b>右缘贴住书体左缘（不留缝）</b>，从书体顶边向下排 */
    static Area tabArea(Area book, int index) {
        return new Area(book.x() - TAB_W, book.y() + index * (TAB_H + TAB_GAP), TAB_W, TAB_H);
    }

    /**
     * 书签"画出来 / 可点中"的矩形：选中那枚向书外（左）凸出 {@link #TAB_LIFT}。
     *
     * <p><b>绘制与命中共用这一处</b>，故不会出现"看得到却点不到"（或反之）的错位。
     */
    static Area tabVisual(Area area, boolean selected) {
        return selected
                ? new Area(area.x() - TAB_LIFT, area.y(), area.w() + TAB_LIFT, area.h())
                : area;
    }

    /** 页外侧叶号的基线（相对<b>框线内</b>的纸面底边内缩，故永不压到框线/皮革） */
    static int folioY(Area book) {
        return frameArea(book).bottom() - FOLIO_BOTTOM;
    }

    /**
     * 页内动作按钮（画在条件页底部）。
     *
     * <p><b>宽度钳制（硬要求）</b>：无论窗口多窄、书体被压到多小、文案多短，按钮的左右缘
     * 一律钳在<b>该页纸面（框线内）之内</b>（纸面再内缩 {@link #MARGIN}，与正文同一口径）；
     * 高度也不超过正文区。故绝不会出现"已学完"横跨并越出书体左缘那种反例。
     */
    static Area actionArea(Area book, int side) {
        Area paper = pageArea(book, side);
        Area text = textArea(book, side);
        int w = Math.max(1, Math.min(text.w(), paper.w() - 2 * MARGIN));
        int h = Math.max(1, Math.min(ACTION_H, text.h()));
        int x = Math.max(paper.x() + MARGIN, Math.min(text.x(), paper.right() - MARGIN - w));
        return new Area(x, text.bottom() - h, w, h);
    }

    /** 一页能放几行：条件页底部让出一块给动作按钮，故少 {@link #ACTION_ROWS} 行 */
    static int capacity(Area book, int side, boolean reserveAction) {
        int h = textArea(book, side).h() - (reserveAction ? ACTION_H + ACTION_GAP : 0);
        return Math.max(1, h / LINE_H);
    }
}
