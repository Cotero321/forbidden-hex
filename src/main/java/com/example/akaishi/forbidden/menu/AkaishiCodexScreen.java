package com.example.akaishi.forbidden.menu;

import com.example.akaishi.forbidden.codex.CodexFamily;
import com.example.akaishi.forbidden.codex.CodexNode;
import com.example.akaishi.forbidden.codex.CodexNodeState;
import com.example.akaishi.forbidden.codex.CodexPage;
import com.example.akaishi.forbidden.codex.CodexTable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 禁忌秘典界面：<b>神秘时代（Thaumcraft）魔导手册式</b>的两层结构 ——
 * <b>左侧窄条书签 + 研究画布</b> ↔ <b>点徽记展开的条目书页</b>。
 *
 * <p><b>版面（本轮重做）</b>：
 * <ul>
 *   <li><b>书体之外只有一层全屏轻压暗</b>（{@link #DIM}）：<b>不画灰面板、不画任何白框</b>，
 *       书直接浮在游戏世界之前；</li>
 *   <li><b>书签</b>：18×40 的窄条，<b>右缘贴住书体左缘</b>，选中那枚向左突出 4px；
 *       条上<b>只有一枚徽记</b>（16×16 居中，缺图回落物品图标），族名与族色走悬停提示；</li>
 *   <li><b>画布</b>：一张图版纸，纸上画本族徽记与族内前置连线；</li>
 *   <li><b>条目页</b>：摊开的双页书（页眉 / 正文 / 旁注 / 页外侧叶号 / 页内按钮）；
 *       返回、翻页与页码改画在<b>书体下缘外居中的书页风格按钮</b>上（原先画在灰面板上，面板没了就看不见）；</li>
 *   <li><b>书体尺寸按内容定</b>：宽由版心公式推出、高由当前内容的行数推出，
 *       上限 720×400、下限 320×160，同时不越出屏幕（见 {@link AkaishiCodexRender#entryBook}）。</li>
 * </ul>
 *
 * <p><b>坐标口径（本轮的根本修正）</b>：<b>一律直接用实际 GUI 坐标</b>（整数），
 * 不再有"设计坐标 + 等比缩放"那一层 —— 上一版把 720×400 缩到 {@code Screen#width/height}
 * （已被 GUI 缩放除过，GUI 缩放 4 时只有 480×270）会得到 0.58 这类<b>非整数比例</b>，
 * 字号被非整数重采样，观感就是"文字飘忽发虚"。现在字号/行高恒为 1:1，命中判定也不再需要坐标换算。
 *
 * <p><b>本类只管三件事</b>：① <b>版式</b>（按当前内容算书体矩形，见 {@link #book}）；
 * ② <b>页码与导航</b>（页码只算本节点，翻页不跨篇）；③ <b>事件分发与命中</b>。
 * <b>它不画像素</b>：书、徽记、连线、书页排版、配方表全部走 {@link AkaishiCodexRender} 与
 * {@link AkaishiCodexEmblem}；"这篇现在能读几页、每页写什么"走 {@link AkaishiCodexPages}。
 *
 * <p><b>导航与 ESC</b>：画布 ↔ 条目页两层。
 * <ul>
 *   <li>点书签 = 直接换当前画布（在条目页里点也回到该族画布）；</li>
 *   <li>点画布上的徽记 = 展开该节点的条目页（跨族跳转会顺手切族）；</li>
 *   <li>{@code ESC}：条目页 → 画布 →（再按一次交回原版）= 关闭界面；</li>
 *   <li>{@code ←/→} 只在条目页里翻摊，且<b>只在本节点内</b>（到头就停住）；滚轮只滚鼠标所在那一页。
 *       画布态的 {@code ←/→} 与滚轮<b>无反应</b>（图版不是书页，没有"上一页"）。</li>
 * </ul>
 *
 * <p><b>数据来源分成两半</b>：静态信息（名称 / 描述 / 族 / 坐标 / 图标 / 页序 / 奖励 / 线索）
 * 读本地 {@link CodexTable}（两端同一份二进制）；权威进度（三态 / 阶段 / 已学 / 仪式次数 / 条件是否满足）
 * 读 {@link AkaishiCodexMenu#nodes()} 的服务端快照。界面自身<b>不做任何条件判断</b>，
 * 按钮可用性一律取快照 {@code canAct}。
 *
 * <p>配色 / 尺寸 / 间距 / 种子一律提为常量并标"待调手感值"。
 */
public class AkaishiCodexScreen extends AbstractContainerScreen<AkaishiCodexMenu> {

    // ===== 书体之外（待调手感值） =====

    /**
     * 全屏轻压暗：书体之外唯一的一层底（alpha 0x70 待调手感值）。
     *
     * <p>不用原版的灰渐变背景、也不用 {@code GuiWidgets.panel} 的浅灰面板 + 白框 ——
     * 那两样正是用户截图里"周边白框"的来源，且会把书挤成"面板里的一小块"。
     */
    private static final int DIM = 0x70000000;

    /** 标题基线（书体上方那条标题带里；压暗背景上必须用浅色墨） */
    private static final int TITLE_Y = 8;

    /** 正文区右侧滚动条的让位宽（与框架 {@code textArea} 口径一致：滚动条 4 + 间隙 2） */
    private static final int SCROLL_ROOM = 6;

    /** 族顺序（= 书签顺序） */
    private static final String[] FAMILY_ORDER = {
            CodexFamily.HUSH, CodexFamily.GAZE, CodexFamily.BLOOD};

    // ===== 状态 =====

    /** 当前在哪一层（画布 / 条目页） */
    private enum Level {
        /** 族画布（主区，可拖动） */
        CHART,
        /** 节点条目书页（点徽记展开） */
        ENTRY
    }

    /** 画布层（图标缓存 / 每族平移 / 命中与拖动） */
    private final AkaishiCodexCanvasView canvas;
    /** 当前层 */
    private Level level = Level.CHART;
    /** 当前画布所属的族（书签选中项；界面任何时候都属于某一族） */
    private String family = FAMILY_ORDER[0];
    /** 当前打开的节点；null = 停在画布 */
    @Nullable
    private ResourceLocation openId;
    /** 节点内的摊下标（1 基；<b>只在本节点内</b>，见类注释的页码口径） */
    private int spread = 1;
    /** 当前节点共几摊（每帧渲染时刷新，页脚与翻页都读它） */
    private int spreadTotal = 1;
    /** 左右两页各自的正文滚动行（滚哪页滚哪页，互不牵连） */
    private final int[] pageScroll = new int[2];
    /** 本帧的书体矩形（每帧按当前内容算一次；绘制与命中共用同一份，故"所见即所点"） */
    private AkaishiCodexRender.Area book = new AkaishiCodexRender.Area(0, 0, 0, 0);
    /** 行数缓存的键与值（键 = 节点 + 快照 + 版心宽；换节点/换快照/改窗口只重算一次） */
    private String rowsKey = "";
    private int rowsCache = 1;

    public AkaishiCodexScreen(AkaishiCodexMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = AkaishiCodexMenu.PANEL_W;
        this.imageHeight = AkaishiCodexMenu.PANEL_H;
        this.canvas = new AkaishiCodexCanvasView(CodexTable.all());
    }

    /**
     * 初始化：本界面不再有"面板尺寸"，书体尺寸每帧按内容算（见 {@link #render}），
     * 故这里只保留原版的初始化。
     */
    @Override
    protected void init() {
        super.init();
    }

    /**
     * <b>有意留空</b>：原版容器界面的灰渐变/泥地背景正是用户要清掉的那层"白框底"，
     * 书体之外只保留 {@link #render} 里那一层全屏轻压暗。
     */
    @Override
    public void renderBackground(GuiGraphics gui) {
        // 有意留空（见方法注释）
    }

    /**
     * 本界面不画槽位、也不用原版那套"背包区"背景：整幅书与所有控件都在 {@link #render} 里一次画完
     * （坐标一律实际 GUI 坐标）。故这里留空。
     */
    @Override
    protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        // 有意留空（见方法注释）
    }

    // ===== 主渲染 =====

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        // 静态表里没有这个节点（理论上不会发生）：退回画布，不留一张空白书页
        if (openId != null && CodexTable.get(openId) == null) {
            backToChart();
        }
        CodexNode node = openNode();
        // 书体之外只有这一层轻压暗：让书在游戏世界之前可读，但没有任何面板/描边
        gui.fill(0, 0, this.width, this.height, DIM);
        // 作用域 = 当前族（每帧幂等调用：只有换族才重算包围盒并恢复该族上次的平移）
        canvas.setScope(this.font, family);
        if (node != null) {
            // 进度快照可能让本节点少一摊（理论上只增不减，这里只做防越界）
            spreadTotal = Math.max(1, spreadsOf(node));
            spread = Math.max(1, Math.min(spread, spreadTotal));
        }
        this.book = layout(node);
        if (level == Level.CHART) {
            canvas.setViewport(AkaishiCodexRender.chartArea(book));
            // 打开界面时服务端快照还没到（首帧 broadcastChanges 才推），此时无从知道"谁能研究"：
            // 等首个快照落地再居中一次，这是唯一能对准"当前进度节点"的时机
            if (!canvas.centered() && !menu.nodes().isEmpty()) {
                canvas.centerOnProgress(menu.nodes());
            }
        }
        AkaishiCodexRender.book(gui, book, bookSeed(), level == Level.ENTRY, family);
        if (level == Level.CHART) {
            renderChart(gui, mouseX, mouseY);
        } else if (node != null) {
            renderSpread(gui, node);
        }
        renderTabs(gui, mouseX, mouseY);
        renderTitle(gui);
        renderFooter(gui, node);
        // 悬停提示最后画（原版提示自带深底，落在书页上也清晰；且不会被书的贴图盖住）
        renderTooltips(gui, mouseX, mouseY, node);
    }

    /**
     * 书体尺寸（本轮核心口径）：
     * <ul>
     *   <li><b>条目页</b>：宽 = 版心公式推出的固定宽（窄屏才收窄）；高 = 按<b>本节点所有可见页的最大
     *       需求行数</b>算（条件页再加 {@link AkaishiCodexRender#ACTION_ROWS} 给动作按钮让位）。
     *       行数与"翻到第几摊"无关，故<b>同一节点内翻页书体高度不跳</b>，切节点/切族才会变。</li>
     *   <li><b>画布态</b>：宽高都按"该族节点包围盒 + 图版留白"算。</li>
     * </ul>
     * 两者都受 [下限 320×160, 上限 720×400] 与"屏幕可用区"双重约束；
     * 屏幕装不下时<b>不缩放</b>（缩放会糊字），改为压低书体高、由页内滚动消化。
     */
    private AkaishiCodexRender.Area layout(@Nullable CodexNode node) {
        if (level == Level.CHART) {
            return AkaishiCodexRender.chartBook(this.width, this.height,
                    canvas.contentW(), canvas.contentH());
        }
        int bookW = AkaishiCodexRender.entryBookWidth(this.width);
        int textW = AkaishiCodexRender.pageTextWidth(bookW);
        return AkaishiCodexRender.entryBook(this.width, this.height, bookW, rowsOf(node, textW));
    }

    /**
     * 本节点<b>所有可见页</b>的最大需求行数（含条件页给动作按钮让位的那两行）。
     *
     * <p>带缓存：键 = 节点 id + 决定"可见页集合与每页行数"的快照字段 + 版心宽。
     * 因此同一节点内翻页时这份值恒定（书体高度稳定），快照推进或窗口变化时才重算一次。
     */
    private int rowsOf(@Nullable CodexNode node, int textW) {
        if (node == null) {
            return 1;
        }
        AkaishiCodexSync.NodeView snapshot = snapshot(node.id());
        String key = rowsSignature(node, snapshot) + "@" + textW;
        if (key.equals(rowsKey)) {
            return rowsCache;
        }
        int max = 1;
        for (CodexPage page : AkaishiCodexPages.visible(node, snapshot)) {
            int rows = AkaishiCodexPages.lines(node, page, snapshot, this.font, textW).size();
            if (page.type() == CodexPage.Type.CONDITIONS) {
                rows += AkaishiCodexRender.ACTION_ROWS;
            }
            max = Math.max(max, rows);
        }
        rowsKey = key;
        rowsCache = max;
        return max;
    }

    /** 行数缓存的签名（快照一变就换键；快照未到按最保守口径，与内容层一致） */
    private static String rowsSignature(CodexNode node, @Nullable AkaishiCodexSync.NodeView view) {
        if (view == null) {
            return node.id() + "|-";
        }
        return node.id() + "|" + view.state() + "," + view.stage() + "," + view.learned()
                + "," + view.conditions().size();
    }

    /** 书底种子：画布态只由族决定（换族换纸、同族恒定）；条目页由"这一摊是谁"决定 */
    private int bookSeed() {
        return level == Level.ENTRY
                ? AkaishiCodexBookArt.seed(Level.ENTRY, family, openId, spread)
                : AkaishiCodexBookArt.seed("chart", family);
    }

    /** 一页的种子（页眉规律线等；左右页不同、同页每帧相同） */
    private int pageSeed(int side) {
        return bookSeed() + side * 0x51;
    }

    /** 标题：画在书体上方的标题带里（浅色墨），本界面没有背包区故不画"物品栏"标签 */
    private void renderTitle(GuiGraphics gui) {
        gui.drawString(this.font, this.title, (this.width - this.font.width(this.title)) / 2, TITLE_Y,
                AkaishiCodexRender.OUTSIDE_INK, false);
    }

    // ===== 画布（主区） =====

    /** 画布：纸面页眉（族名 + 本族进度）→ 族内徽记与连线 */
    private void renderChart(GuiGraphics gui, int mouseX, int mouseY) {
        String stat = truncate(Component.translatable("gui.forbidden_hex.codex.volume.stat",
                learnedInFamily(family), sizeOfFamily(family)).getString(),
                AkaishiCodexRender.chartArea(book).w() / 2);
        AkaishiCodexRender.sheetHead(gui, this.font, book,
                Component.translatable(CodexFamily.nameKey(family)), Component.literal(stat),
                AkaishiCodexBookArt.seed("chart", family, "rule"));
        canvas.render(gui, this.font, menu.nodes(), mouseX, mouseY);
    }

    // ===== 书签式族栏（窄条贴边；点一下直接换画布） =====

    /** 鼠标下的书签下标；不在任何书签上返回 -1（命中区与画出来的位置共用 {@code tabVisual}，含"翻出"的那一段） */
    private int tabAt(double mouseX, double mouseY) {
        for (int i = 0; i < FAMILY_ORDER.length; i++) {
            AkaishiCodexRender.Area area = AkaishiCodexRender.tabArea(book, i);
            boolean selected = Objects.equals(family, FAMILY_ORDER[i]);
            if (AkaishiCodexRender.in(mouseX, mouseY, AkaishiCodexRender.tabVisual(area, selected))) {
                return i;
            }
        }
        return -1;
    }

    private void renderTabs(GuiGraphics gui, int mouseX, int mouseY) {
        for (int i = 0; i < FAMILY_ORDER.length; i++) {
            String id = FAMILY_ORDER[i];
            AkaishiCodexRender.Area area = AkaishiCodexRender.tabArea(book, i);
            boolean selected = Objects.equals(family, id);
            boolean hover = AkaishiCodexRender.in(mouseX, mouseY,
                    AkaishiCodexRender.tabVisual(area, selected));
            CodexNode first = firstNodeOf(id);
            // 书签只有徽记，族名（与族色）由 renderTabTooltip 在悬停时给出
            AkaishiCodexRender.tab(gui, area, id, first,
                    canvas.iconOf(first == null ? null : first.id().toString()), selected, hover);
        }
    }

    /** 本族第一个节点（书签的徽记/图标代表；本族没有节点返回 null） */
    @Nullable
    private static CodexNode firstNodeOf(String target) {
        for (CodexNode node : CodexTable.all()) {
            if (Objects.equals(target, node.family())) {
                return node;
            }
        }
        return null;
    }

    /** 书签悬停：族名（族色）→ 题词（小字）→ 本族进度 → 操作提示 */
    private void renderTabTooltip(GuiGraphics gui, int mouseX, int mouseY, int index) {
        String id = FAMILY_ORDER[index];
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(CodexFamily.nameKey(id))
                .withStyle(AkaishiCodexRender.styleOf(AkaishiCodexRender.familyColor(id))));
        String motto = Component.translatableWithFallback(CodexFamily.mottoKey(id), "").getString();
        if (!motto.isEmpty()) {
            lines.add(Component.literal(motto).withStyle(AkaishiCodexRender.styleOf(0xFF9A94A8)));
        }
        lines.add(Component.translatable("gui.forbidden_hex.codex.volume.stat",
                learnedInFamily(id), sizeOfFamily(id)));
        lines.add(Component.translatable("gui.forbidden_hex.codex.tab.tip",
                Component.translatable(CodexFamily.nameKey(id))));
        gui.renderComponentTooltip(this.font, lines, mouseX, mouseY);
    }

    // ===== 条目页（双页） =====

    /** 当前摊的两页（页序 = 阅读序：第 1 摊的左页是这篇的第 1 页）；页码只算本节点 */
    private void renderSpread(GuiGraphics gui, CodexNode node) {
        List<CodexPage> pages = AkaishiCodexPages.visible(node, snapshot(node.id()));
        int base = (spread - 1) * 2;
        for (int side = 0; side < 2; side++) {
            int index = base + side;
            if (index >= 0 && index < pages.size()) {
                drawContentPage(gui, node, pages.get(index), side);
            }
        }
        // 页外侧叶号：左叶 = 2n−1、右叶 = 2n（都只在本节点内数，故跟页脚的 n/m 对得上）
        AkaishiCodexRender.folio(gui, this.font, book, 0, 2 * spread - 1);
        AkaishiCodexRender.folio(gui, this.font, book, 1, 2 * spread);
    }

    /**
     * 画一页：页眉 → 正文（或配方表）→ 溢出时的滚动条 → 条件页底部的页内动作按钮。
     *
     * @param side 0 = 左页 / 1 = 右页（决定滚动量取哪一个、旁注贴哪一边）
     */
    private void drawContentPage(GuiGraphics gui, CodexNode node, CodexPage page, int side) {
        AkaishiCodexSync.NodeView snapshot = snapshot(node.id());
        boolean speculate = AkaishiCodexPages.speculation(page, node, snapshot);
        Component head = side == 0
                ? Component.translatable(CodexFamily.nameKey(node.family()))
                : Component.translatable(node.nameKey()).copy()
                        .append(Component.literal(" · ")).append(pageHead(page, node, speculate));
        AkaishiCodexRender.pageHead(gui, this.font, book, side, head, pageSeed(side));
        if (page.type() == CodexPage.Type.RECIPES) {
            // 配方页不是文字页：合成表或占位由框架直接画
            AkaishiCodexRender.recipePage(gui, this.font, book, side, node.recipes());
            return;
        }
        int width = AkaishiCodexRender.lineWidth(book, side);
        List<AkaishiCodexPages.Line> lines = AkaishiCodexPages.lines(node, page, snapshot, this.font, width);
        int capacity = AkaishiCodexRender.capacity(book, side, page.type() == CodexPage.Type.CONDITIONS);
        int scroll = clampScroll(pageScroll[side], lines.size(), capacity);
        AkaishiCodexRender.prose(gui, this.font, book, side, lines, scroll, capacity);
        AkaishiCodexRender.scrollBar(gui, book, side, lines.size(), capacity, scroll);
        // 页内动作按钮：画在条件页底部（TC 手册把 Complete 按钮放在条目页里，而不是外框上）
        if (page.type() == CodexPage.Type.CONDITIONS) {
            AkaishiCodexRender.button(gui, this.font, AkaishiCodexRender.actionArea(book, side),
                    actionLabel(node, snapshot), snapshot != null && snapshot.canAct());
        }
    }

    /** 右页页眉：正文页给阶段（思索页再缀"思索"），自动页给页类型（左页恒为册名，两页互为呼应） */
    private Component pageHead(CodexPage page, CodexNode node, boolean speculate) {
        if (page.stagedText()) {
            Component stage = Component.translatable("gui.forbidden_hex.codex.stage",
                    page.stageIndex() + 1, node.stageCount());
            return speculate
                    ? stage.copy().append(Component.literal(" · "))
                            .append(Component.translatable("gui.forbidden_hex.codex.page.think"))
                    : stage;
        }
        return switch (page.type()) {
            case CONDITIONS -> Component.translatable("gui.forbidden_hex.codex.page.conditions");
            case REWARDS -> Component.translatable("gui.forbidden_hex.codex.reward.header");
            case RECIPES -> Component.translatable("gui.forbidden_hex.codex.page.recipes");
            default -> Component.translatable("gui.forbidden_hex.codex.page.relations");
        };
    }

    // ===== 页脚（书体下缘外居中；画布态只有一行提示） =====

    /**
     * 页脚：画布态 = 书体下方居中的一行操作提示；条目页 = 返回 + 翻页 + 页码。
     *
     * <p><b>为什么移到书体下方</b>：页脚原先画在灰面板上，面板一撤就会落在游戏世界背景上；
     * 书体下方始终有预算好的控件带（书体垂直定位已扣掉 {@code BOTTOM_RESERVE}），
     * 且控件自绘为"深墨底 + 浅描边 + 浅字"，与压暗背景对比足够，也与书页墨色同族。
     */
    private void renderFooter(GuiGraphics gui, @Nullable CodexNode node) {
        if (level == Level.CHART) {
            String hint = truncate(Component.translatable("gui.forbidden_hex.codex.canvas.hint").getString(),
                    book.w());
            gui.drawString(this.font, hint, book.cx() - this.font.width(hint) / 2,
                    AkaishiCodexRender.outsideHintY(book), AkaishiCodexRender.OUTSIDE_INK_DIM, false);
            return;
        }
        int total = node == null ? 1 : Math.max(1, spreadTotal);
        AkaishiCodexRender.button(gui, this.font, AkaishiCodexRender.footerBack(book),
                Component.translatable("gui.forbidden_hex.codex.view.back"), true);
        // 翻页只在本节点内：两端一律置灰（不提供"滑进下一篇"的按钮，跨节点只能点可跳转的前置行）
        AkaishiCodexRender.button(gui, this.font, AkaishiCodexRender.footerPrev(book),
                Component.literal("<"), spread > 1);
        AkaishiCodexRender.button(gui, this.font, AkaishiCodexRender.footerNext(book),
                Component.literal(">"), spread < total);
        String label = truncate(Component.translatable("gui.forbidden_hex.codex.page.footer",
                spread, total).getString(), AkaishiCodexRender.footerTextW(book));
        gui.drawString(this.font, label,
                AkaishiCodexRender.footerTextX(book)
                        + (AkaishiCodexRender.footerTextW(book) - this.font.width(label)) / 2,
                AkaishiCodexRender.footerTextY(book), AkaishiCodexRender.OUTSIDE_INK, false);
    }

    /** 动作按钮文案（与服务端 {@code canAct} 同源：亮着就能按，按了服务端仍会重算一次） */
    private Component actionLabel(CodexNode node, @Nullable AkaishiCodexSync.NodeView snapshot) {
        if (!isLearned(snapshot)) {
            int stage = snapshot == null ? 0 : snapshot.stage();
            return Component.translatable("gui.forbidden_hex.codex.action.study",
                    Math.min(stage + 1, node.stageCount()), node.stageCount());
        }
        if (!node.repeatable()) {
            return Component.translatable("gui.forbidden_hex.codex.action.learned");
        }
        int maxUses = ritualMaxUses(node);
        return snapshot != null && snapshot.uses() >= maxUses
                ? Component.translatable("gui.forbidden_hex.codex.action.exhausted")
                : Component.translatable("gui.forbidden_hex.codex.action.ritual", snapshot == null ? 0 : snapshot.uses(), maxUses);
    }

    private static int ritualMaxUses(CodexNode node) {
        return node.reward() == null ? 0 : node.reward().ritualMaxUses();
    }

    // ===== 悬停提示 =====

    private void renderTooltips(GuiGraphics gui, int mouseX, int mouseY, @Nullable CodexNode node) {
        int tab = tabAt(mouseX, mouseY);
        if (tab >= 0) {
            renderTabTooltip(gui, mouseX, mouseY, tab);
            return;
        }
        if (handleFooterTooltip(gui, mouseX, mouseY)) {
            return;
        }
        if (level == Level.CHART) {
            CodexNode hovered = canvas.hovered(mouseX, mouseY);
            if (hovered != null) {
                AkaishiCodexSync.NodeView snapshot = snapshot(hovered.id());
                AkaishiCodexRender.tooltip(gui, this.font, mouseX, mouseY, hovered, snapshot,
                        AkaishiCodexEmblem.mark(state(hovered), snapshot),
                        AkaishiCodexCanvasView.blockingPrereq(hovered, menu.nodes()));
            }
            return;
        }
        if (node == null) {
            return;
        }
        if (isInActionButton(mouseX, mouseY, node)) {
            AkaishiCodexRender.tip(gui, this.font, mouseX, mouseY, actionLabel(node, snapshot(node.id())));
            return;
        }
        AkaishiCodexPages.Line line = lineAt(mouseX, mouseY, node);
        if (line == null || line.jump() == null) {
            return;
        }
        CodexNode target = CodexTable.get(line.jump());
        if (target != null) {
            AkaishiCodexRender.tip(gui, this.font, mouseX, mouseY,
                    Component.translatable("gui.forbidden_hex.codex.page.jump_tip",
                            Component.translatable(target.nameKey())));
        }
    }

    /** 页脚按钮的悬停提示（画布态没有页脚按钮） */
    private boolean handleFooterTooltip(GuiGraphics gui, double mouseX, double mouseY) {
        if (level == Level.CHART) {
            return false;
        }
        if (AkaishiCodexRender.in(mouseX, mouseY, AkaishiCodexRender.footerBack(book))) {
            AkaishiCodexRender.tip(gui, this.font, (int) mouseX, (int) mouseY,
                    Component.translatable("gui.forbidden_hex.codex.view.back"));
            return true;
        }
        if (AkaishiCodexRender.in(mouseX, mouseY, AkaishiCodexRender.footerPrev(book))) {
            AkaishiCodexRender.tip(gui, this.font, (int) mouseX, (int) mouseY,
                    Component.translatable("gui.forbidden_hex.codex.page.prev"));
            return true;
        }
        if (AkaishiCodexRender.in(mouseX, mouseY, AkaishiCodexRender.footerNext(book))) {
            AkaishiCodexRender.tip(gui, this.font, (int) mouseX, (int) mouseY,
                    Component.translatable("gui.forbidden_hex.codex.page.next"));
            return true;
        }
        return false;
    }

    // ===== 交互 =====

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        // 书签在最上层：点它 = 换画布（条目页里点书签也回到该族画布）
        int tab = tabAt(mouseX, mouseY);
        if (tab >= 0) {
            selectFamily(FAMILY_ORDER[tab]);
            return true;
        }
        if (level == Level.CHART) {
            if (canvas.isIn(mouseX, mouseY)) {
                canvas.beginDrag(mouseX, mouseY);
                return true;
            }
        } else {
            if (handleFooterClick(mouseX, mouseY)) {
                return true;
            }
            CodexNode node = openNode();
            if (node != null && handlePageClick(mouseX, mouseY, node)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 页脚命中（条目页 = 返回画布 / 上一摊 / 下一摊） */
    private boolean handleFooterClick(double mouseX, double mouseY) {
        if (level == Level.CHART) {
            return false;
        }
        if (AkaishiCodexRender.in(mouseX, mouseY, AkaishiCodexRender.footerBack(book))) {
            backToChart();
            return true;
        }
        if (AkaishiCodexRender.in(mouseX, mouseY, AkaishiCodexRender.footerPrev(book))) {
            turnSpread(-1);
            return true;
        }
        if (AkaishiCodexRender.in(mouseX, mouseY, AkaishiCodexRender.footerNext(book))) {
            turnSpread(1);
            return true;
        }
        return false;
    }

    /** 条目页页内容点击：页内动作按钮 / 可跳转行（页脚不在这里，见 {@link #handleFooterClick}） */
    private boolean handlePageClick(double mouseX, double mouseY, CodexNode node) {
        if (isInActionButton(mouseX, mouseY, node)) {
            AkaishiCodexSync.NodeView snapshot = snapshot(node.id());
            // 置灰的按钮不吞点击也不发包：命中区与视觉状态保持一致（服务端仍会再校验一次）
            if (snapshot != null && snapshot.canAct()) {
                AkaishiCodexSync.sendStudy(menu.containerId, node.id().toString());
            }
            return true;
        }
        AkaishiCodexPages.Line line = lineAt(mouseX, mouseY, node);
        if (line != null && line.jump() != null && !line.jump().equals(node.id())) {
            CodexNode target = CodexTable.get(line.jump());
            if (target != null) {
                // 唯一的跨节点显式路径（前置 / 后继 / 未满足的前置条件行）
                openNode(target);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && canvas.isDragging()) {
            canvas.dragTo(mouseX, mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0 || !canvas.isDragging()) {
            return super.mouseReleased(mouseX, mouseY, button);
        }
        // 松开 = 命中徽记就展开这一篇；没命中则画布自己重新居中（拖丢了的兜底）
        CodexNode node = canvas.release(mouseX, mouseY, menu.nodes());
        if (node != null) {
            openNode(node);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        CodexNode node = openNode();
        if (level == Level.ENTRY && node != null && delta != 0.0D) {
            int side = pageSideAt(mouseX, mouseY);
            int index = (spread - 1) * 2 + side;
            List<CodexPage> pages = side < 0 ? List.of() : AkaishiCodexPages.visible(node, snapshot(node.id()));
            if (index >= 0 && index < pages.size()) {
                CodexPage page = pages.get(index);
                List<AkaishiCodexPages.Line> lines = AkaishiCodexPages.lines(node, page, snapshot(node.id()),
                        this.font, AkaishiCodexRender.lineWidth(book, side));
                int capacity = AkaishiCodexRender.capacity(book, side, page.type() == CodexPage.Type.CONDITIONS);
                int max = Math.max(0, lines.size() - capacity);
                if (max > 0) {
                    pageScroll[side] = Math.max(0, Math.min(max, pageScroll[side] - (int) Math.signum(delta)));
                    return true;
                }
            }
        }
        // 画布态无反应：图版不是书页，没有可滚的内容（也不抢原版的滚轮）
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // ESC 逐层退回：条目页 →（再按一次交回原版）= 关闭界面。画布是主区，没有"上一页"
        if (keyCode == InputConstants.KEY_ESCAPE && level == Level.ENTRY) {
            backToChart();
            return true;
        }
        if (level == Level.ENTRY && (keyCode == InputConstants.KEY_RIGHT || keyCode == InputConstants.KEY_LEFT)) {
            turnSpread(keyCode == InputConstants.KEY_RIGHT ? 1 : -1);
            return true;
        }
        // 画布态的 ←/→ 不吞键（留给原版），也不翻任何东西
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ===== 导航（画布 ↔ 条目页） =====

    @Nullable
    private CodexNode openNode() {
        return openId == null ? null : CodexTable.get(openId);
    }

    /** 展开某节点的条目页：<b>顺带切到它所属的族</b>（跨族跳转都走这里） */
    private void openNode(CodexNode node) {
        level = Level.ENTRY;
        family = node.family();
        openId = node.id();
        spread = 1;
        spreadTotal = Math.max(1, spreadsOf(node));
        clearScroll();
    }

    /** 点书签 / 切族：<b>直接换当前画布</b>（在条目页里点也回到该族画布） */
    private void selectFamily(String target) {
        family = target;
        level = Level.CHART;
        openId = null;
        spread = 1;
        clearScroll();
    }

    /** 返回画布（条目页 → 画布；画布态无操作） */
    private void backToChart() {
        if (level == Level.CHART) {
            return;
        }
        level = Level.CHART;
        openId = null;
        spread = 1;
        clearScroll();
    }

    /**
     * 翻一摊：<b>页码序列只在本节点内</b>（一摊两叶），走到本节点两端就停住。
     *
     * <p>跨节点只保留"条件页里点可跳转的前置行"这一条显式入口（不滑进同族的下一篇）。
     */
    private void turnSpread(int delta) {
        if (level != Level.ENTRY) {
            return;
        }
        CodexNode node = openNode();
        if (node == null) {
            backToChart();
            return;
        }
        int target = spread + delta;
        if (target < 1 || target > Math.max(1, spreadTotal)) {
            return;
        }
        spread = target;
        clearScroll();
    }

    // ===== 本族进度（画布页眉与书签提示共用） =====

    private int sizeOfFamily(String target) {
        int total = 0;
        for (CodexNode node : CodexTable.all()) {
            if (Objects.equals(target, node.family())) {
                total++;
            }
        }
        return total;
    }

    private int learnedInFamily(String target) {
        int learned = 0;
        for (CodexNode node : CodexTable.all()) {
            if (Objects.equals(target, node.family()) && state(node) == CodexNodeState.LEARNED) {
                learned++;
            }
        }
        return learned;
    }

    // ===== 页码（只算本节点；一摊 = 两叶） =====

    /** 本节点占的摊数（每 2 页 1 摊；条件页与配方页恒可见，故至少 1 摊） */
    private int spreadsOf(CodexNode node) {
        return (AkaishiCodexPages.visible(node, snapshot(node.id())).size() + 1) / 2;
    }

    // ===== 页内容命中 =====

    /** 鼠标落在哪一页的正文区（0 = 左页，1 = 右页，-1 = 不在正文区） */
    private int pageSideAt(double mouseX, double mouseY) {
        for (int side = 0; side < 2; side++) {
            AkaishiCodexRender.Area text = AkaishiCodexRender.textArea(book, side);
            if (AkaishiCodexRender.in(mouseX, mouseY,
                    new AkaishiCodexRender.Area(text.x(), text.y(), text.w() + SCROLL_ROOM, text.h()))) {
                return side;
            }
        }
        return -1;
    }

    /** 鼠标下的正文行（含该页的滚动偏移）；不在任何一页的可见正文区返回 null */
    @Nullable
    private AkaishiCodexPages.Line lineAt(double mouseX, double mouseY, CodexNode node) {
        int side = pageSideAt(mouseX, mouseY);
        if (side < 0) {
            return null;
        }
        List<CodexPage> pages = AkaishiCodexPages.visible(node, snapshot(node.id()));
        int index = (spread - 1) * 2 + side;
        if (index < 0 || index >= pages.size()) {
            return null;
        }
        CodexPage page = pages.get(index);
        List<AkaishiCodexPages.Line> lines = AkaishiCodexPages.lines(node, page, snapshot(node.id()),
                this.font, AkaishiCodexRender.lineWidth(book, side));
        int capacity = AkaishiCodexRender.capacity(book, side, page.type() == CodexPage.Type.CONDITIONS);
        int scroll = clampScroll(pageScroll[side], lines.size(), capacity);
        AkaishiCodexRender.Area text = AkaishiCodexRender.textArea(book, side);
        int row = ((int) Math.floor(mouseY) - text.y()) / AkaishiCodexRender.LINE_H + scroll;
        // 只认当前页真正画出来的那些行（条件页底部让给了动作按钮，那几行不算）
        if (row < 0 || row >= lines.size() || row - scroll >= capacity) {
            return null;
        }
        AkaishiCodexPages.Line line = lines.get(row);
        // 段距占位不是内容行：点它既不跳转也不给提示
        return line.spacer() ? null : line;
    }

    private static int clampScroll(int scroll, int size, int capacity) {
        return Math.max(0, Math.min(scroll, Math.max(0, size - capacity)));
    }

    // ===== 页内动作按钮的几何与命中 =====

    /** 本摊里条件页在哪一侧（0 = 左，1 = 右，-1 = 本摊没有条件页）——页内按钮就画在它底部 */
    private int actionPageSide(CodexNode node) {
        List<CodexPage> pages = AkaishiCodexPages.visible(node, snapshot(node.id()));
        int base = (spread - 1) * 2;
        for (int side = 0; side < 2; side++) {
            int index = base + side;
            if (index >= 0 && index < pages.size() && pages.get(index).type() == CodexPage.Type.CONDITIONS) {
                return side;
            }
        }
        return -1;
    }

    /** 鼠标是否落在页内动作按钮上（几何来自框架，故绘制与命中永远一致） */
    private boolean isInActionButton(double mouseX, double mouseY, CodexNode node) {
        int side = actionPageSide(node);
        if (side < 0) {
            return false;
        }
        return AkaishiCodexRender.in(mouseX, mouseY, AkaishiCodexRender.actionArea(book, side));
    }

    private void clearScroll() {
        pageScroll[0] = 0;
        pageScroll[1] = 0;
    }

    // ===== 小工具 =====

    /** 服务端进度镜像；还没收到快照时为 null（界面据此按"未学、不可点"保守显示） */
    @Nullable
    private AkaishiCodexSync.NodeView snapshot(ResourceLocation nodeId) {
        String id = nodeId.toString();
        for (AkaishiCodexSync.NodeView node : menu.nodes()) {
            if (node.id().equals(id)) {
                return node;
            }
        }
        return null;
    }

    /** 节点三态：快照未到时按最保守的 LOCKED（不把没资格的节点画亮） */
    private CodexNodeState state(CodexNode node) {
        AkaishiCodexSync.NodeView snapshot = snapshot(node.id());
        return snapshot == null ? CodexNodeState.LOCKED : CodexNodeState.byId(snapshot.state());
    }

    private static boolean isLearned(@Nullable AkaishiCodexSync.NodeView snapshot) {
        return snapshot != null && snapshot.learned();
    }

    /** 截到指定像素宽（宁可截断也绝不让两组字重叠/越出书体） */
    private String truncate(String text, int width) {
        return this.font.plainSubstrByWidth(text, Math.max(0, width));
    }
}
