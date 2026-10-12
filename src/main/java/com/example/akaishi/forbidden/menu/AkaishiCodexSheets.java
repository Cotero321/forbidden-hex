package com.example.akaishi.forbidden.menu;
import com.example.akaishi.menu.GuiWidgets;

import com.example.akaishi.forbidden.menu.AkaishiCodexRender.Area;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.example.akaishi.forbidden.menu.AkaishiCodexLayout.LINE_H;
import static com.example.akaishi.forbidden.menu.AkaishiCodexLayout.LINE_TAIL_PAD;
import static com.example.akaishi.forbidden.menu.AkaishiCodexLayout.MIN_LINE_W;
import static com.example.akaishi.forbidden.menu.AkaishiCodexLayout.folioY;
import static com.example.akaishi.forbidden.menu.AkaishiCodexLayout.pageArea;
import static com.example.akaishi.forbidden.menu.AkaishiCodexLayout.textArea;
import static com.example.akaishi.forbidden.menu.AkaishiCodexRender.RULE_COLOR;
import static com.example.akaishi.forbidden.menu.AkaishiCodexRender.SCROLL_HANDLE;
import static com.example.akaishi.forbidden.menu.AkaishiCodexRender.SCROLL_TRACK;
import static com.example.akaishi.forbidden.menu.AkaishiCodexRender.familyColor;
import static com.example.akaishi.forbidden.menu.AkaishiCodexRender.pulse;

/**
 * 禁忌秘典 · <b>书页排版与页脚</b>渲染协作类（纯机械搬迁自 {@link AkaishiCodexRender}）。
 * <p>
 * 承接第②组"书页排版"与页脚控件 API：页眉、正文（含旁注 1:1 绘制）、正文滚动条、
 * 叶号、书页风格按钮、配方页（3×3 合成表），以及书体下方页脚控件带的几何
 * （返回 / 翻页 / 页码，随书体宽收窄，钳制在框线之内）与徽记下的节点名。
 * <p>
 * 外部引用一律走 {@code AkaishiCodexRender} 的同名门面入口；本类方法体与原实现逐位一致。
 */
final class AkaishiCodexSheets {

    // ===== 页脚控件（书体下方居中；自绘书页风格，故不依赖任何面板底） =====

    private static final int FOOTER_H = 14;
    /** 书体下缘到页脚控件的间隙 */
    private static final int FOOTER_GAP = 6;
    private static final int FOOTER_BACK_W = 64;
    private static final int FOOTER_NAV_W = 16;
    private static final int FOOTER_TEXT_W = 64;
    private static final int FOOTER_ITEM_GAP = 6;
    /** 书页风格按钮：深墨底 + 浅描边 + 浅字（默认态 / 置灰态） */
    private static final int BTN_BASE = 0xF02A2118;
    private static final int BTN_BASE_OFF = 0xB01C1812;
    private static final int BTN_EDGE = 0xFF6E5A3E;
    private static final int BTN_EDGE_OFF = 0xFF4A4034;
    private static final int BTN_TEXT = 0xFFEFE4C8;
    private static final int BTN_TEXT_OFF = 0xFF8C8272;

    // ===== 配方页（待调手感值） =====

    /** 槽位边长与槽距（照原版 18px 槽 + 2px 缝） */
    private static final int SLOT = 18;
    private static final int SLOT_GAP = 2;
    /** 产物槽与网格之间的箭头宽 */
    private static final int CRAFT_ARROW_W = 18;
    /** 网格整体在正文区里的顶部偏移 */
    private static final int CRAFT_TOP = 12;

    private AkaishiCodexSheets() {
    }

    // ===== 页脚控件几何（书体下方居中） =====

    /** 页脚基线（书体下缘外 {@link #FOOTER_GAP}） */
    private static int footerY(Area book) {
        return book.bottom() + FOOTER_GAP;
    }

    /**
     * 页脚各段的实际几何（{@code [返回键宽, 翻页键宽, 页码文本宽, 间隙]}）。
     *
     * <p><b>为什么要算而不是直接用常量</b>：书体宽会随屏宽被压窄（GUI 缩放档位越高越明显），
     * 而页脚若按常量横铺，窄屏下就会越出书体左右缘。这里按"书体可用宽"（= <b>框线内宽</b>，
     * 故页脚与纸面左右对齐）把各段<b>同比例取整收窄</b>，
     * 于是页脚在任何档位下都落在框线之内（这就是"宽度钳制"的落点）。
     */
    private static int[] footerMetrics(Area book) {
        int back = FOOTER_BACK_W;
        int nav = FOOTER_NAV_W;
        int text = FOOTER_TEXT_W;
        int gap = FOOTER_ITEM_GAP;
        int want = back + 3 * gap + 2 * nav + text;
        int room = book.w() - 2 * AkaishiCodexLayout.borderFor(book.w());
        if (room < want) {
            int r = Math.max(4, room);
            back = Math.max(1, back * r / want);
            nav = Math.max(1, nav * r / want);
            text = Math.max(1, text * r / want);
            gap = Math.max(1, gap * r / want);
        }
        return new int[]{back, nav, text, gap};
    }

    /** 页脚整行的左起点：按书体中心居中，并<b>钳制在框线左右缘之内</b>（窄屏也不越出内框线） */
    private static int footerLeft(Area book) {
        int[] m = footerMetrics(book);
        int pad = AkaishiCodexLayout.borderFor(book.w());
        int total = m[0] + 3 * m[3] + 2 * m[1] + m[2];
        int lo = book.x() + pad;
        int hi = Math.max(lo, book.right() - pad - total);
        return Math.max(lo, Math.min(book.cx() - total / 2, hi));
    }

    static Area footerBack(Area book) {
        return new Area(footerLeft(book), footerY(book), footerMetrics(book)[0], FOOTER_H);
    }

    static Area footerPrev(Area book) {
        int[] m = footerMetrics(book);
        return new Area(footerLeft(book) + m[0] + m[3], footerY(book), m[1], FOOTER_H);
    }

    /** 页码文本的绘制起点（不画底，直接落在压暗的背景上） */
    static int footerTextX(Area book) {
        int[] m = footerMetrics(book);
        return footerLeft(book) + m[0] + m[3] + m[1] + m[3];
    }

    /** 页码文本的实际可用宽（随书体宽收窄，故界面要按它截断与居中） */
    static int footerTextW(Area book) {
        return footerMetrics(book)[2];
    }

    static Area footerNext(Area book) {
        int[] m = footerMetrics(book);
        return new Area(footerTextX(book) + m[2] + m[3], footerY(book), m[1], FOOTER_H);
    }

    /** 页脚文字基线（与按钮内文字同一口径居中） */
    static int footerTextY(Area book) {
        return footerY(book) + (FOOTER_H - 8) / 2;
    }

    /** 书体之外的一行提示（画布态操作提示）的基线 */
    static int outsideHintY(Area book) {
        return footerTextY(book);
    }

    // ===== 书页排版 =====

    /**
     * 页眉（左页写册/篇名，右页写篇名 · 页类型）+ 页眉下的规律线。
     *
     * <p>横向从<b>版心左缘</b>起、按版心宽截断（故页眉既与正文左右对齐，也永不越过框线）；
     * 纵向落在纸面顶边下方的页眉带里（{@code HEAD_INSET} / {@code RULE_INSET}）。
     */
    static void pageHead(GuiGraphics gui, Font font, Area book, int side, Component head, int seed) {
        Area paper = pageArea(book, side);
        Area text = textArea(book, side);
        gui.drawString(font, font.plainSubstrByWidth(head.getString(), text.w()),
                text.x(), paper.y() + AkaishiCodexLayout.HEAD_INSET, AkaishiCodexPages.INK_DIM, false);
        AkaishiCodexBookArt.rule(gui, text.x(), paper.y() + AkaishiCodexLayout.RULE_INSET, text.w(), RULE_COLOR, seed);
    }

    /**
     * 正文：只画可见切片（段距占位行不画字；旁注贴页外侧：左页靠左、右页右对齐）。
     *
     * <p><b>本轮取消小字号</b>：旁注原是按 0.78 倍缩放画的，那同样会让字发虚；
     * 现在旁注与正文同一字号（1:1），靠墨色（{@link AkaishiCodexPages#INK_NOTE}）与贴边位置区分。
     *
     * @param side 0 = 左页 / 1 = 右页（决定旁注贴哪一边）
     */
    static void prose(GuiGraphics gui, Font font, Area book, int side, List<AkaishiCodexPages.Line> lines,
                      int scroll, int capacity) {
        Area text = textArea(book, side);
        int shown = Math.max(0, Math.min(lines.size() - scroll, capacity));
        for (int i = 0; i < shown; i++) {
            AkaishiCodexPages.Line line = lines.get(scroll + i);
            if (line.spacer()) {
                continue;
            }
            int y = text.y() + i * LINE_H;
            if (line.note()) {
                int x = side == 0
                        ? text.x() + AkaishiCodexPages.NOTE_OUTER_PAD
                        : text.right() - AkaishiCodexPages.NOTE_OUTER_PAD - font.width(line.text());
                gui.drawString(font, line.text(), x, y, line.color(), false);
            } else {
                gui.drawString(font, line.text(), text.x() + line.indent(), y, line.color(), false);
            }
        }
    }

    /** 正文溢出时的滚动条（只有它是"现代控件"，故压到最淡） */
    static void scrollBar(GuiGraphics gui, Area book, int side, int size, int capacity, int scroll) {
        if (size <= capacity) {
            return;
        }
        Area text = textArea(book, side);
        int trackH = capacity * LINE_H;
        int max = size - capacity;
        int handleH = Math.max(6, trackH * capacity / size);
        int handleY = text.y() + (trackH - handleH) * Math.max(0, Math.min(scroll, max)) / max;
        int barX = text.right() + AkaishiCodexLayout.SCROLL_GAP;
        gui.fill(barX, text.y(), barX + AkaishiCodexLayout.SCROLL_W, text.y() + trackH, SCROLL_TRACK);
        gui.fill(barX, handleY, barX + AkaishiCodexLayout.SCROLL_W, handleY + handleH, SCROLL_HANDLE);
    }

    /** 页外侧叶号：左页左下、右页右下；<b>从框线内的纸面外侧横向内缩 {@code FOLIO_INSET}</b>，永不压框线 */
    static void folio(GuiGraphics gui, Font font, Area book, int side, int leaf) {
        Area paper = pageArea(book, side);
        AkaishiCodexBookArt.folio(gui, font, leaf,
                side == 0 ? paper.x() + AkaishiCodexLayout.FOLIO_INSET : paper.right() - AkaishiCodexLayout.FOLIO_INSET,
                folioY(book), side != 0);
    }

    /**
     * 书页风格的按钮：深墨底 + 浅描边 + 浅字（默认态 / 置灰态）。
     *
     * <p><b>为什么自绘</b>：书体之外不再有面板底，页脚控件直接落在"压暗后的游戏世界"上，
     * 原版浅灰按钮会与背景糊在一起；深墨底 + 浅字在任何背景上都读得清，
     * 也与书页（深棕墨色体系）同族。落在纸面上时读作"墨块标签"，同样成立。
     */
    static void button(GuiGraphics gui, Font font, Area area, Component label, boolean enabled) {
        gui.fill(area.x(), area.y(), area.right(), area.bottom(), enabled ? BTN_BASE : BTN_BASE_OFF);
        AkaishiCodexBookArt.ring(gui, area.x(), area.y(), area.w(), area.h(),
                enabled ? BTN_EDGE : BTN_EDGE_OFF);
        String text = font.plainSubstrByWidth(label.getString(), Math.max(0, area.w() - 6));
        gui.drawString(font, text, area.x() + (area.w() - font.width(text)) / 2,
                area.y() + (area.h() - 8) / 2, enabled ? BTN_TEXT : BTN_TEXT_OFF, false);
    }

    /**
     * 配方页：有配方就画合成表（3×3 网格 + 箭头 + 产物槽），没有就画一句占位。
     *
     * <p>配方本体读<b>客户端本地</b>的 {@code RecipeManager}（配方是公共数据，两端同源，
     * 不需要服务端下发）。认不出的配方类型（如熔炉配方）不硬画，改画"找不到了"。
     */
    static void recipePage(GuiGraphics gui, Font font, Area book, int side, List<ResourceLocation> recipes) {
        Area text = textArea(book, side);
        // 占位/找不到两类说明行按"可用行宽"换行（同正文口径，不顶到版心边界）
        int wrapW = Math.max(MIN_LINE_W, text.w() - LINE_TAIL_PAD);
        if (recipes.isEmpty()) {
            wrapped(gui, font, Component.translatable("gui.forbidden_hex.codex.recipe.pending"),
                    text.x(), text.y() + LINE_H, wrapW, AkaishiCodexPages.INK_DIM);
            return;
        }
        ResourceLocation id = recipes.get(0);
        Recipe<?> recipe = findRecipe(id);
        if (!(recipe instanceof CraftingRecipe crafting)) {
            wrapped(gui, font, Component.translatable("gui.forbidden_hex.codex.recipe.missing", id.toString()),
                    text.x(), text.y() + LINE_H, wrapW, AkaishiCodexPages.INK_DIM);
            return;
        }
        drawCrafting(gui, font, text, crafting);
        if (recipes.size() > 1) {
            wrapped(gui, font, Component.translatable("gui.forbidden_hex.codex.recipe.more", recipes.size() - 1),
                    text.x(), text.bottom() - LINE_H, wrapW, AkaishiCodexPages.INK_DIM);
        }
    }

    /** 3×3 网格 + 箭头 + 产物槽（用原版槽位贴图，与项目其它 GUI 同一样式） */
    private static void drawCrafting(GuiGraphics gui, Font font, Area text, CraftingRecipe recipe) {
        int cell = SLOT + SLOT_GAP;
        int gridW = 3 * cell - SLOT_GAP;
        int totalW = gridW + CRAFT_ARROW_W + SLOT;
        int left = text.x() + Math.max(0, (text.w() - totalW) / 2);
        int top = text.y() + CRAFT_TOP;
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int sx = left + col * cell;
                int sy = top + row * cell;
                GuiWidgets.slotBox(gui, sx + 1, sy + 1);
                int index = row * 3 + col;
                if (index < ingredients.size()) {
                    ItemStack[] items = ingredients.get(index).getItems();
                    if (items.length > 0) {
                        gui.renderItem(items[0], sx + 1, sy + 1);
                    }
                }
            }
        }
        int arrowX = left + gridW + SLOT_GAP;
        GuiWidgets.progressArrow(gui, arrowX, top + cell, CRAFT_ARROW_W - SLOT_GAP, SLOT, 100f);
        int resultX = arrowX + CRAFT_ARROW_W;
        GuiWidgets.slotBox(gui, resultX + 1, top + cell + 1);
        var level = Minecraft.getInstance().level;
        if (level != null) {
            ItemStack result = recipe.getResultItem(level.registryAccess());
            if (!result.isEmpty()) {
                gui.renderItem(result, resultX + 1, top + cell + 1);
                gui.drawString(font,
                        font.plainSubstrByWidth(result.getHoverName().getString(), text.w()),
                        text.x(), top + 3 * cell + 6, AkaishiCodexPages.INK, false);
            }
        }
    }

    @Nullable
    private static Recipe<?> findRecipe(ResourceLocation id) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        return level.getRecipeManager().byKey(id).orElse(null);
    }

    /** 小段自动换行文字（占位文案、结果名之类的"说明行"） */
    private static void wrapped(GuiGraphics gui, Font font, Component text, int x, int y, int width, int color) {
        for (var line : font.split(text, Math.max(MIN_LINE_W, width))) {
            gui.drawString(font, line, x, y, color, false);
            y += LINE_H;
        }
    }

    // ===== 徽记的名字（徽记本体见 AkaishiCodexEmblem） =====

    /**
     * 徽记下的节点名（颜色按四档视觉状态）；"可研究"时名字下再补一笔族色线
     * （随圈注一起呼吸：圈注与名字呼应，像"这一条我圈了"）。
     */
    static void label(GuiGraphics gui, Font font, String text, int centerX, int y,
                      String family, AkaishiCodexEmblem.Mark mark, boolean forbidden) {
        int x = centerX - font.width(text) / 2;
        gui.drawString(font, text, x, y, AkaishiCodexEmblem.nameColor(mark, forbidden), false);
        if (mark == AkaishiCodexEmblem.Mark.AVAILABLE) {
            AkaishiCodexBookArt.rule(gui, x, y + 9, font.width(text),
                    AkaishiCodexBookArt.semi(familyColor(family), pulse() * 0x70 / 0xFF + 0x30),
                    AkaishiCodexBookArt.seed("label", family, text));
        }
    }
}
