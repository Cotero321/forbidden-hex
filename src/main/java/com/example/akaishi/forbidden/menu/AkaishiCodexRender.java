package com.example.akaishi.forbidden.menu;

import com.example.akaishi.forbidden.codex.CodexFamily;
import com.example.akaishi.forbidden.codex.CodexNode;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 禁忌秘典的<b>专属渲染框架（门面）</b>：秘典里的一切绘制都从这里出，屏幕/画布/内容层只负责
 * "数据准备 + 摆位置 + 命中判定"，<b>不再直接写零散 {@code fill}</b>。
 *
 * <p><b>为什么这一层要取消缩放</b>：上一版把"设计基准 720×400"整体
 * {@code translate + scale} 到实际屏幕，但 MC 的 {@code Screen#width/height} 是<b>已被 GUI 缩放除过</b>
 * 的逻辑空间（GUI 缩放 4 时只有 480×270），于是实际比例落到 0.58 这类<b>非整数</b>，
 * 字体被非整数重采样 —— 观感就是"文字飘忽、发虚"。因此<b>整套视图变换已删</b>：
 * <ul>
 *   <li>所有几何直接用<b>实际 GUI 坐标</b>算，<b>一律整数</b>（不出现 {@code Math.round(坐标 * scale)}）；</li>
 *   <li>字号与行高保持项目上限（字体 9px、{@link #LINE_H} = 10），<b>文字永远 1:1</b>；</li>
 *   <li>书体尺寸改为<b>按内容定</b>（见 {@link #entryBook}/{@link #chartBook}），
 *       上限 {@link AkaishiCodexMenu#PANEL_W}/{@link AkaishiCodexMenu#PANEL_H}（720×400）；
 *       屏幕装不下时<b>不缩放</b>，而是收窄书体高度、靠页内滚动消化多余内容。</li>
 * </ul>
 * 本框架里<b>不存在任何 {@code pose().scale(...)}</b>；仅有的 pose 操作是状态暗罩抬 z
 * （{@link AkaishiCodexEmblem} 里的 {@code translate(0,0,300)}），与像素密度无关。
 *
 * <p><b>实现按渲染职责拆分到同包包私有协作类（纯机械搬迁，本类保留全部门面入口）</b>：
 * <ul>
 *   <li>① 版面几何唯一真源（书体/版心/页签列/命中/裁剪）→ {@link AkaishiCodexLayout}；</li>
 *   <li>② 书框与书签（书底贴图分段拉伸 / 缺图回落 / 书签带与页签）→ {@link AkaishiCodexBookFrame}；</li>
 *   <li>③ 书页排版与页脚（页眉/正文/滚动条/叶号/按钮/配方页）→ {@link AkaishiCodexSheets}；</li>
 *   <li>④ 画布连线与悬停提示 → {@link AkaishiCodexLinks}；</li>
 *   <li>⑤ 小工具（族色 / 墨化 / 呼吸相位 / 贴图存在性缓存 / 样式）保留在本类。</li>
 * </ul>
 * 本类的全部 static 方法签名与 {@link Area} 均保持原样（外部调用点零改动），实现为一行委托；
 * 方法体与原实现逐位一致。
 *
 * <p><b>贴图优先、缺图回落</b>：书底优先读 {@code textures/gui/codex/book_frame.png}，
 * 徽记优先读 {@code textures/gui/codex/emblem/<节点路径>.png}；读不到一律走
 * {@link AkaishiCodexBookArt} 的程序化笔触，<b>绝不 blit 不存在的贴图</b>（那会被画成缺图马赛克）。
 *
 * <p><b>书底为分段拉伸</b>：贴图是"跨页整幅"（书脊在正中），分段规则见
 * {@code AkaishiCodexBookFrame#blitBookTexture}；边框厚度由书体宽反算（{@link #borderFor}），
 * 版心（{@link #textArea}）由它加内边距推出并自检钳制 ⇒ <b>永不越框</b>。
 *
 * <p>本框架只管"画"，不读存档、不做条件判定、不管页码 —— 那些是屏幕与内容层的事。
 * 全部尺寸/配色/周期都提为常量并标"待调手感值"。
 */
final class AkaishiCodexRender {

    // ===== 门面常量（真源随迁到协作类，此处别名保持外部引用点零改动） =====

    /** 行高：<b>项目既有上限，永不缩放</b>（真源 {@link AkaishiCodexLayout#LINE_H}） */
    static final int LINE_H = AkaishiCodexLayout.LINE_H;
    /** 动作按钮在正文区里占掉的行数（真源 {@link AkaishiCodexLayout#ACTION_ROWS}） */
    static final int ACTION_ROWS = AkaishiCodexLayout.ACTION_ROWS;
    /** 挡路连线（粗）与常态连线（细）的线宽（真源 {@link AkaishiCodexLinks#LINK_W_THICK}） */
    static final int LINK_W_THICK = AkaishiCodexLinks.LINK_W_THICK;
    static final int LINK_W_THIN = AkaishiCodexLinks.LINK_W_THIN;
    /** 连线三档透明度：挡路高亮 / 常态 / 被挡节点的其余入线（待调手感值；真源 {@link AkaishiCodexLinks}） */
    static final int LINK_ALPHA_HIGHLIGHT = AkaishiCodexLinks.LINK_ALPHA_HIGHLIGHT;
    static final int LINK_ALPHA_NORMAL = AkaishiCodexLinks.LINK_ALPHA_NORMAL;
    static final int LINK_ALPHA_DIM = AkaishiCodexLinks.LINK_ALPHA_DIM;

    /** 书体之外的一行文字（标题 / 画布提示）：压暗背景下必须用浅色，深墨会看不见 */
    static final int OUTSIDE_INK = 0xFFE8DCC2;
    static final int OUTSIDE_INK_DIM = 0xB0C8BCA2;

    /** 徽记贴图目录前缀与约定尺寸（32×32，透明底） */
    static final String TEX_EMBLEM_DIR = "textures/gui/codex/emblem/";
    static final int TEX_EMBLEM_SIZE = 32;

    // ===== 纸面笔触配色（待调手感值；真源在本类，协作类经静态导入共用） =====

    static final int RULE_COLOR = 0x337A6A56;
    static final int SCROLL_TRACK = 0x30A89A78;
    static final int SCROLL_HANDLE = 0xB08A7A5C;
    /** 可点击行（关联页 / 可跳转条件）的提示色（提示是深底，故用亮色；包私有：悬停协作类静态导入共用） */
    static final int TIP_JUMP = 0xFF9FD0FF;
    static final int TIP_DIM = 0xFF9A94A8;
    /** 纹章配色里的族色族（族色原色只给标签/亮框/提示；纸面连线一律经 {@link AkaishiCodexBookArt#ink} 压暗） */
    private static final int COLOR_FAMILY_UNKNOWN = 0xFF8A8A95;
    private static final float INK_MIX = 0.62f;

    /** 贴图存在性缓存（键 = 贴图 id；资源重载会换掉 ResourceManager 实例 ⇒ 整表失效重探） */
    private static final Map<ResourceLocation, Boolean> PRESENT = new ConcurrentHashMap<>();
    private static ResourceManager cachedManager;

    private AkaishiCodexRender() {
    }

    /** 屏幕坐标（= 实际 GUI 坐标）里的矩形；命中判定与绘制共用同一份几何 */
    record Area(int x, int y, int w, int h) {

        int right() {
            return x + w;
        }

        int bottom() {
            return y + h;
        }

        int cx() {
            return x + w / 2;
        }

        int cy() {
            return y + h / 2;
        }
    }

    // ===== 门面委托：① 版面几何 → {@link AkaishiCodexLayout} =====

    /** 命中判定（鼠标是浮点，矩形是整数；一律整数比较，不做任何坐标换算） */
    static boolean in(double mouseX, double mouseY, Area area) {
        return AkaishiCodexLayout.in(mouseX, mouseY, area);
    }

    /** 按矩形开裁剪（{@code enableScissor} 吃屏幕坐标，本界面几何本来就是屏幕坐标，直接透传） */
    static void scissor(GuiGraphics gui, Area area) {
        AkaishiCodexLayout.scissor(gui, area);
    }

    static void unscissor(GuiGraphics gui) {
        AkaishiCodexLayout.unscissor(gui);
    }

    /**
     * <b>边框厚度</b>（= 框线在屏幕上距书体边缘的距离）。
     * 只吃书体宽 ⇒ 与"按内容定高"不构成循环；版心、书体高、书脊缝宽全部由它反算。
     */
    static int borderFor(int bookW) {
        return AkaishiCodexLayout.borderFor(bookW);
    }

    /** 一页正文需要 {@code rows} 行时，书体的高度（"书体高度按内容定"的唯一公式） */
    static int entryBookHeight(int bookW, int rows) {
        return AkaishiCodexLayout.entryBookHeight(bookW, rows);
    }

    /** 条目页书体宽：<b>常量</b>（不随内容变）；窄屏时会被压到可用宽 */
    static int entryBookWidth(int screenW) {
        return AkaishiCodexLayout.entryBookWidth(screenW);
    }

    /** 给定书体宽时，一页真正能画正文的<b>可用行宽</b>（内容层换行与绘制共用这一口径的探针） */
    static int pageTextWidth(int bookW) {
        return AkaishiCodexLayout.pageTextWidth(bookW);
    }

    /** 可用行宽（版心再扣行尾留白）：内容层换行与绘制<b>共用这一口径</b> */
    static int lineWidth(Area book, int side) {
        return AkaishiCodexLayout.lineWidth(book, side);
    }

    /** 条目页书体：宽由屏宽定，高按内容行数定 */
    static Area entryBook(int screenW, int screenH, int bookW, int rows) {
        return AkaishiCodexLayout.entryBook(screenW, screenH, bookW, rows);
    }

    /** 画布态书体：宽高都按"该族节点包围盒 + 图版留白 + 两侧边框"定（受同一套上下限约束） */
    static Area chartBook(int screenW, int screenH, int contentW, int contentH) {
        return AkaishiCodexLayout.chartBook(screenW, screenH, contentW, contentH);
    }

    /** <b>框线以内的纸面</b>（= 书体四边各内缩 {@link #borderFor}）：书页一切文字/控件的硬边界 */
    static Area frameArea(Area book) {
        return AkaishiCodexLayout.frameArea(book);
    }

    /** 一页纸面（side = 0 左页 / 1 右页）：<b>框线之内</b>、两页等宽、中间夹书脊缝 */
    static Area pageArea(Area book, int side) {
        return AkaishiCodexLayout.pageArea(book, side);
    }

    /** 一页的正文区（版心）：<b>由框线反算</b>，左右两页对称，自检钳进纸面<b>永不越框</b> */
    static Area textArea(Area book, int side) {
        return AkaishiCodexLayout.textArea(book, side);
    }

    /** 画布（族图）的视口：<b>框线之内</b>再留白，顶部留出一行页眉 */
    static Area chartArea(Area book) {
        return AkaishiCodexLayout.chartArea(book);
    }

    /** 书签式分类标签的列：<b>右缘贴住书体左缘（不留缝）</b>，从书体顶边向下排 */
    static Area tabArea(Area book, int index) {
        return AkaishiCodexLayout.tabArea(book, index);
    }

    /** 书签"画出来 / 可点中"的矩形：选中那枚向书外（左）凸出（绘制与命中共用） */
    static Area tabVisual(Area area, boolean selected) {
        return AkaishiCodexLayout.tabVisual(area, selected);
    }

    /** 页外侧叶号的基线（相对<b>框线内</b>的纸面底边内缩，故永不压到框线/皮革） */
    static int folioY(Area book) {
        return AkaishiCodexLayout.folioY(book);
    }

    /** 页内动作按钮（画在条件页底部）：宽度钳在该页纸面之内、高度不超过正文区 */
    static Area actionArea(Area book, int side) {
        return AkaishiCodexLayout.actionArea(book, side);
    }

    /** 一页能放几行：条件页底部让出一块给动作按钮，故少 {@link #ACTION_ROWS} 行 */
    static int capacity(Area book, int side, boolean reserveAction) {
        return AkaishiCodexLayout.capacity(book, side, reserveAction);
    }

    // ===== 门面委托：② 书框与书签 → {@link AkaishiCodexBookFrame} =====

    /**
     * 书底：皮革封边 + 纸面 + 内框线 + 书脊折痕 + 书签带（贴图优先、缺图回落到程序化笔触）。
     *
     * @param open   true = 摊开的双页书（条目页）；false = 单张图版纸（族画布）
     * @param family 当前族（决定书签带颜色；null = 中性暗红）
     */
    static void book(GuiGraphics gui, Area book, int seed, boolean open, @Nullable String family) {
        AkaishiCodexBookFrame.book(gui, book, seed, open, family);
    }

    /** 画布态的纸面页眉：左 = 族名，右 = 本族进度，其下一条手绘规律线（都落在框线之内） */
    static void sheetHead(GuiGraphics gui, Font font, Area book, Component left, Component right, int seed) {
        AkaishiCodexBookFrame.sheetHead(gui, font, book, left, right, seed);
    }

    /** 书签式分类标签：窄条贴边、只放一枚徽记（贴图优先 / 物品图标回落），未选中压暗 */
    static void tab(GuiGraphics gui, Area area, String family, @Nullable CodexNode node,
                    ItemStack icon, boolean selected, boolean hovered) {
        AkaishiCodexBookFrame.tab(gui, area, family, node, icon, selected, hovered);
    }

    // ===== 门面委托：③ 书页排版与页脚 → {@link AkaishiCodexSheets} =====

    /** 页眉（左页写册/篇名，右页写篇名 · 页类型）+ 页眉下的规律线 */
    static void pageHead(GuiGraphics gui, Font font, Area book, int side, Component head, int seed) {
        AkaishiCodexSheets.pageHead(gui, font, book, side, head, seed);
    }

    /** 正文：只画可见切片（段距占位行不画字；旁注贴页外侧，与正文同一字号 1:1） */
    static void prose(GuiGraphics gui, Font font, Area book, int side, List<AkaishiCodexPages.Line> lines,
                      int scroll, int capacity) {
        AkaishiCodexSheets.prose(gui, font, book, side, lines, scroll, capacity);
    }

    /** 正文溢出时的滚动条（只有它是"现代控件"，故压到最淡） */
    static void scrollBar(GuiGraphics gui, Area book, int side, int size, int capacity, int scroll) {
        AkaishiCodexSheets.scrollBar(gui, book, side, size, capacity, scroll);
    }

    /** 页外侧叶号：左页左下、右页右下，永不压框线 */
    static void folio(GuiGraphics gui, Font font, Area book, int side, int leaf) {
        AkaishiCodexSheets.folio(gui, font, book, side, leaf);
    }

    /** 书页风格的按钮：深墨底 + 浅描边 + 浅字（默认态 / 置灰态） */
    static void button(GuiGraphics gui, Font font, Area area, Component label, boolean enabled) {
        AkaishiCodexSheets.button(gui, font, area, label, enabled);
    }

    /** 配方页：有配方就画合成表（3×3 网格 + 箭头 + 产物槽），没有就画一句占位 */
    static void recipePage(GuiGraphics gui, Font font, Area book, int side, List<ResourceLocation> recipes) {
        AkaishiCodexSheets.recipePage(gui, font, book, side, recipes);
    }

    /** 页脚：返回键矩形 */
    static Area footerBack(Area book) {
        return AkaishiCodexSheets.footerBack(book);
    }

    /** 页脚：上一页键矩形 */
    static Area footerPrev(Area book) {
        return AkaishiCodexSheets.footerPrev(book);
    }

    /** 页脚：页码文本的绘制起点（不画底，直接落在压暗的背景上） */
    static int footerTextX(Area book) {
        return AkaishiCodexSheets.footerTextX(book);
    }

    /** 页脚：页码文本的实际可用宽（随书体宽收窄，故界面要按它截断与居中） */
    static int footerTextW(Area book) {
        return AkaishiCodexSheets.footerTextW(book);
    }

    /** 页脚：下一页键矩形 */
    static Area footerNext(Area book) {
        return AkaishiCodexSheets.footerNext(book);
    }

    /** 页脚文字基线（与按钮内文字同一口径居中） */
    static int footerTextY(Area book) {
        return AkaishiCodexSheets.footerTextY(book);
    }

    /** 书体之外的一行提示（画布态操作提示）的基线 */
    static int outsideHintY(Area book) {
        return AkaishiCodexSheets.outsideHintY(book);
    }

    /** 徽记下的节点名（颜色按四档视觉状态）；"可研究"时名字下再补一笔族色呼吸线 */
    static void label(GuiGraphics gui, Font font, String text, int centerX, int y,
                      String family, AkaishiCodexEmblem.Mark mark, boolean forbidden) {
        AkaishiCodexSheets.label(gui, font, text, centerX, y, family, mark, forbidden);
    }

    // ===== 门面委托：④ 画布连线与悬停提示 → {@link AkaishiCodexLinks} =====

    /**
     * 一条前置连线：<b>只走横竖</b>（先横后竖），拐角圆化；箭头（可选）指向 {@code (x1,y1)} 端。
     */
    static void link(GuiGraphics gui, int x1, int y1, int x2, int y2, int color,
                     int thickness, boolean arrow) {
        AkaishiCodexLinks.link(gui, x1, y1, x2, y2, color, thickness, arrow);
    }

    /** 节点悬停提示：名称 → 族 · 层级[ · 禁忌] → 层级 → 状态 → 描述 → 挡路者或线索 */
    static void tooltip(GuiGraphics gui, Font font, int mouseX, int mouseY, CodexNode node,
                        @Nullable AkaishiCodexSync.NodeView view,
                        AkaishiCodexEmblem.Mark mark, @Nullable CodexNode blocker) {
        AkaishiCodexLinks.tooltip(gui, font, mouseX, mouseY, node, view, mark, blocker);
    }

    /** 一行提示（书签悬停 / 页脚按钮 / 可跳转行） */
    static void tip(GuiGraphics gui, Font font, int mouseX, int mouseY, Component text) {
        AkaishiCodexLinks.tip(gui, font, mouseX, mouseY, text);
    }

    // ===== 小工具（真源保留在本类；协作类经静态导入共用） =====

    /** 族 → 识别色（未知族回退兜底色，绝不返回 0 导致"看不见的线"） */
    static int familyColor(String family) {
        return switch (family) {
            case CodexFamily.HUSH -> AkaishiCodexEmblem.COLOR_HUSH;
            case CodexFamily.GAZE -> AkaishiCodexEmblem.COLOR_GAZE;
            case CodexFamily.BLOOD -> AkaishiCodexEmblem.COLOR_BLOOD;
            default -> COLOR_FAMILY_UNKNOWN;
        };
    }

    /** 纸面墨化：族色按 {@link #INK_MIX} 压暗（保色相；族色原色只留给标签与提示） */
    static int inked(String family) {
        return AkaishiCodexBookArt.ink(familyColor(family), INK_MIX);
    }

    /** 可研究徽记的呼吸相位（只改 alpha，不改色相；周期 {@link AkaishiCodexEmblem#PULSE_PERIOD_MS}） */
    static int pulse() {
        double phase = (System.currentTimeMillis() % AkaishiCodexEmblem.PULSE_PERIOD_MS)
                / (double) AkaishiCodexEmblem.PULSE_PERIOD_MS;
        return (int) (0x70 + 0x8F * (0.5D + 0.5D * Math.sin(phase * Math.PI * 2D)));
    }

    /**
     * 贴图是否存在（<b>缺图回落的前提</b>）：存在才 blit，否则一律走程序化笔触 ——
     * 直接 blit 不存在的贴图会被原版画成"缺图马赛克"，那是硬性禁止的。
     *
     * <p>结果缓存；资源重载会换掉 {@code ResourceManager} 实例，检测到换实例即整表重探
     * （于是开发期新丢进去的贴图不用重启也能生效）。
     */
    static boolean texturePresent(ResourceLocation id) {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft == null) {
            return false;
        }
        ResourceManager manager = minecraft.getResourceManager();
        if (manager != cachedManager) {
            PRESENT.clear();
            cachedManager = manager;
        }
        return PRESENT.computeIfAbsent(id, key -> manager.getResource(key).isPresent());
    }

    static Style styleOf(int argb) {
        return Style.EMPTY.withColor(TextColor.fromRgb(argb & 0xFFFFFF));
    }
}
