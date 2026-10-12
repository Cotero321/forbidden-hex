package com.example.akaishi.forbidden.menu;

import com.example.akaishi.forbidden.item.AkaishiMagicItems;
import com.example.akaishi.forbidden.item.MagicCoreItem;
import com.example.akaishi.forbidden.magic.judge.MagicParser;
import com.example.akaishi.forbidden.magic.judge.MagicValidation;
import com.example.akaishi.forbidden.magic.judge.MagicValidator;
import com.example.akaishi.forbidden.magic.judge.ParsedSpell;
import com.example.akaishi.forbidden.magic.table.MagicDeliveryDef;
import com.example.akaishi.forbidden.magic.table.MagicDeliveryTable;
import com.example.akaishi.forbidden.magic.table.MagicEffectDef;
import com.example.akaishi.forbidden.magic.table.MagicEffectTable;
import com.example.akaishi.forbidden.magic.table.MagicGlyphs;
import com.example.akaishi.forbidden.magic.table.MagicInkDef;
import com.example.akaishi.forbidden.magic.table.MagicInkTable;
import com.example.akaishi.forbidden.magic.table.MagicWord;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 铭文镶嵌台界面：六分区布局（上：墨栏 / 核心 / 选择；下：词编辑 / 物品栏 / 预览）。
 * 分区边界常量同时用于绘制与命中，保证所见即可点。
 */
public final class AkaishiInscriptionTableScreen extends AbstractContainerScreen<AkaishiInscriptionTableMenu> {
    private static final int W = AkaishiInscriptionTableMenu.IMAGE_WIDTH, H = AkaishiInscriptionTableMenu.IMAGE_HEIGHT;

    // 分区边界（本地坐标）
    private static final int TITLE_X0 = 7, TITLE_Y0 = 6, TITLE_X1 = 373, TITLE_Y1 = 30;
    private static final int UP_Y0 = 34, UP_Y1 = 118, LO_Y0 = 122, LO_Y1 = 225;
    private static final int INK_X0 = 7, INK_X1 = 102, CORE_X0 = 106, CORE_X1 = 274, SEL_X0 = 278, SEL_X1 = 373;
    private static final int WORD_X0 = 7, WORD_X1 = 102, INV_X0 = 106, INV_X1 = 274, PRV_X0 = 278, PRV_X1 = 373;

    // 墨栏行（16 高，容纳 16×16 图标）
    private static final int INK_ROW_X = 10, INK_ROW_Y = 46, INK_ROW_W = 88, INK_ROW_H = 16, INK_ROWS = 3;
    // 左下符文区：分类按钮 + 小格网格（3 行 4 列 = 12 格/页，底部留出翻页行）
    private static final int CAT_X = 10, CAT_Y = 126, CAT_W = 21, CAT_H = 12, CAT_GAP = 2;
    private static final int GRID_X = 10, GRID_Y = 150, GRID_COLS = 4, GRID_PITCH = 20, GRID_CELLS = 12;
    // 翻页控件（▲ / ▼ + 页码）：墨区放在标题行右侧（避开输入槽），词区放在网格下方
    private static final int ARROW_W = 11, ARROW_H = 11;
    private static final int INK_PG_X = 56, INK_PG_Y = 37;
    private static final int WORD_PG_X = 12, WORD_PG_Y = 208;
    private static final MagicWord.Role[] CATS = {MagicWord.Role.ACTION, MagicWord.Role.POINT,
            MagicWord.Role.STRUCTURE, MagicWord.Role.NATURE};
    // 右下：产物槽（= 预览区）与雕刻按钮
    private static final int OUT_X = AkaishiInscriptionTableMenu.PRODUCT_X, OUT_Y = AkaishiInscriptionTableMenu.PRODUCT_Y;
    private static final int ENGRAVE_X = PRV_X0 + 6, ENGRAVE_Y = 188, ENGRAVE_W = PRV_X1 - PRV_X0 - 12, ENGRAVE_H = 17;

    private int inkPage, wordPage, wordCategory;
    private String hoveredInk = "", hoveredWord = "";
    /** 悬停到不可写的符文时的原因（null = 无）。带判定层的可读说明，而不是笼统一句"冲突"。 */
    private Component hoveredReason;
    /** 每帧只解析一次，避免重复构建集合与重复能力查询。 */
    private List<String> inksView = List.of();
    private List<String> wordsView = List.of();
    /** 八个槽位的草稿词（每帧刷新）：每个槽位显示自己的草稿，切槽不会丢掉原先的。 */
    private final String[] draftsView = new String[8];
    /** 八个槽位各自冻结的墨（每帧刷新）；空则回落到当前选中的墨。 */
    private final String[] draftInksView = new String[8];
    /** 墨物品显示栈缓存，避免每帧 new ItemStack 触发模型查找。 */
    private static final java.util.Map<String, ItemStack> INK_STACKS = new java.util.HashMap<>();

    public AkaishiInscriptionTableScreen(AkaishiInscriptionTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = W;
        imageHeight = H;
        inventoryLabelY = 0;
    }

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        drawFrame(g, x, y);
        drawInputSlots(g, x, y);
        // 预览区只留产物槽本身（产物物品由真实槽位渲染），不再画圆环
        for (int i = 0; i < draftsView.length; i++) {
            draftsView[i] = menu.selectedWord(i);
            String frozen = menu.selectedWordInk(i);
            draftInksView[i] = frozen.isEmpty() ? menu.selectedInk() : frozen;   // 该位没记墨就用当前选中的
        }
        CoreGuiRenderer.render(g, x + CoreLayout.CENTER_X, y + CoreLayout.CENTER_Y, CoreLayout.SLOT_RADIUS,
                menu.inputCore(), menu.tableSlot(), menu.playerLevel(), draftsView, draftInksView);
        drawInventoryFrames(g, x, y);
    }

    /** 外框、标题栏与六个分区底板。 */
    private void drawFrame(GuiGraphics g, int x, int y) {
        frameBox(g, x, y, x + W, y + H, 0xFF211713, 0xFF171216, 0xFF725039);
        frameBox(g, x + TITLE_X0, y + TITLE_Y0, x + TITLE_X1, y + TITLE_Y1, 0xFF2B1B17, 0xFF171216, 0xFF4E3327);
        frameBox(g, x + INK_X0, y + UP_Y0, x + INK_X1, y + UP_Y1, 0xFF33211B, 0xFF1B1210, 0xFF8B613F);
        frameBox(g, x + CORE_X0, y + UP_Y0, x + CORE_X1, y + UP_Y1, 0xFF18151A, 0xFF100D12, 0xFF69503A);
        frameBox(g, x + SEL_X0, y + UP_Y0, x + SEL_X1, y + UP_Y1, 0xFF33211B, 0xFF1B1210, 0xFF8B613F);
        frameBox(g, x + WORD_X0, y + LO_Y0, x + WORD_X1, y + LO_Y1, 0xFF33211B, 0xFF1B1210, 0xFF7A5634);
        frameBox(g, x + INV_X0, y + LO_Y0, x + INV_X1, y + LO_Y1, 0xFF281A18, 0xFF150E0D, 0xFF60402E);
        frameBox(g, x + PRV_X0, y + LO_Y0, x + PRV_X1, y + LO_Y1, 0xFF2A1E1A, 0xFF150E0D, 0xFF7A5634);
    }

    /** 输入槽框：中央核心槽 + 左下毛笔槽 / 墨槽（主墨 + 3 覆墨）。 */
    private void drawInputSlots(GuiGraphics g, int x, int y) {
        // 中央：魔法核心输入槽
        slotFrame(g, x + AkaishiInscriptionTableMenu.CORE_X, y + AkaishiInscriptionTableMenu.CORE_Y, 0xFFFFD36A);
        // 左下：毛笔 + 4 墨槽（同类合并，耐久相加不封顶）
        slotFrame(g, x + AkaishiInscriptionTableMenu.BRUSH_X, y + AkaishiInscriptionTableMenu.BRUSH_Y, 0xFF8B613F);
        for (int i = 0; i < 4; i++) {
            slotFrame(g, x + AkaishiInscriptionTableMenu.INK_X + i * 18, y + AkaishiInscriptionTableMenu.INK_Y, 0xFF8B613F);
        }
    }

    /** 中下玩家槽框：跳过机器槽，从玩家槽开始。 */
    private void drawInventoryFrames(GuiGraphics g, int x, int y) {
        List<Slot> slots = menu.slots; int start = menu.machineSlotCount();
        for (int i = start; i < slots.size(); i++) { Slot s = slots.get(i); slotFrame(g, x + s.x, y + s.y, 0xFF6B5240); }
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        int mx = mouseX - leftPos, my = mouseY - topPos;
        g.drawString(font, title, TITLE_X0 + 5, TITLE_Y0 + 6, 0xFFF0D8A2, false);
        g.drawString(font, Component.translatable("gui.forbidden_hex.inscription.ink_library"), INK_X0 + 4, UP_Y0 + 4, 0xFFE7C99A, false);
        g.drawString(font, Component.translatable("gui.forbidden_hex.inscription.preview"), SEL_X0 + 6, UP_Y0 + 4, 0xFFE7C99A, false);
        g.drawString(font, Component.translatable("gui.forbidden_hex.inscription.inventory"), INV_X0 + 3, LO_Y0 + 4, 0xFFE7C99A, false);
        g.drawString(font, Component.translatable("gui.forbidden_hex.inscription.hotbar"), INV_X0 + 3, LO_Y1 - 11, 0xFFE7C99A, false);
        g.drawString(font, Component.translatable("gui.forbidden_hex.inscription.preview_panel"), PRV_X0 + 5, LO_Y0 + 3, 0xFFE7C99A, false);

        drawInkColumn(g, mx, my);
        drawWordEditor(g, mx, my);
        drawSelection(g);
        drawPreview(g);
    }

    /** 左上：已解锁墨列表（真实图标 + 名称 + 数量/耐久，选中金框，滚轮翻页）。 */
    private void drawInkColumn(GuiGraphics g, int mouseX, int mouseY) {
        List<String> inks = inksView;
        int pages = Math.max(1, (inks.size() + INK_ROWS - 1) / INK_ROWS);
        inkPage = Math.min(inkPage, pages - 1); hoveredInk = "";
        for (int i = 0; i < INK_ROWS; i++) {
            int index = inkPage * INK_ROWS + i; if (index >= inks.size()) break;
            String id = inks.get(index);
            int rx = INK_ROW_X, ry = INK_ROW_Y + i * INK_ROW_H;
            boolean selected = id.equals(menu.selectedInk());
            boolean hover = inside(mouseX, mouseY, rx, ry, INK_ROW_W, INK_ROW_H);
            g.fill(rx, ry, rx + INK_ROW_W, ry + INK_ROW_H, selected ? 0xFFFFD36A : hover ? 0xFFC1914D : 0xFF4A3026);
            var def = MagicInkTable.get(id);
            if (def != null) {
                ItemStack stack = inkStack(id);
                g.renderItem(stack, rx + 1, ry);
                g.renderItemDecorations(font, stack, rx + 1, ry);
                g.drawString(font, Component.literal(def.label()), rx + 20, ry + 2, selected ? 0xFF3A2A10 : 0xFFEAD5AE, false);
                // 汇总耐久：背包中该种墨的剩余耐久之和，点击后抽取到墨槽
                g.drawString(font, Component.literal("Σ " + menu.inkStock(id)), rx + 20, ry + 9,
                        selected ? 0xFF5A4020 : 0xFFA88F6C, false);
            }
            if (hover) hoveredInk = id;
        }
        drawPager(g, INK_PG_X, INK_PG_Y, inkPage, pages);
        if (inks.isEmpty()) g.drawString(font, Component.translatable("gui.forbidden_hex.inscription.empty_inks"), INK_X0 + 4, UP_Y0 + 16, 0xFFBFA98C, false);
    }

    /** 翻页控件：▲ / ▼ 两个可点击箭头 + 页码（滚轮同样可用）。 */
    private void drawPager(GuiGraphics g, int x, int y, int page, int pages) {
        arrow(g, x, y, true, 0xFFBFA98C);
        arrow(g, x + 15, y, false, 0xFFBFA98C);
        g.drawString(font, Component.literal((page + 1) + "/" + pages), x + 32, y + 2, 0xFFBFA98C, false);
    }

    /** 画一个小三角箭头。 */
    private static void arrow(GuiGraphics g, int x, int y, boolean up, int color) {
        if (up) {
            for (int i = 0; i < 4; i++) g.fill(x + 3 - i, y + i, x + 5 + i, y + i + 1, color);
        } else {
            for (int i = 0; i < 4; i++) g.fill(x + 3 - i, y + 3 - i, x + 5 + i, y + 4 - i, color);
        }
    }

    /** 左下：符文选择区 —— 四分类按钮 + 小格符文网格（悬浮显示释义）。 */
    private void drawWordEditor(GuiGraphics g, int mouseX, int mouseY) {
        hoveredWord = "";
        hoveredReason = null;
        // 四个分类按钮：动作 / 作用点 / 结构 / 性质
        for (int i = 0; i < 4; i++) {
            int bx = CAT_X + i * (CAT_W + CAT_GAP), by = CAT_Y;
            boolean sel = i == wordCategory;
            g.fill(bx, by, bx + CAT_W, by + CAT_H, sel ? 0xFFD2A65B : 0xFF4B3428);
            g.drawCenteredString(font, Component.translatable(catKey(i)), bx + CAT_W / 2, by + 2,
                    sel ? 0xFF3A2A10 : 0xFFEAD5AE);
        }
        String selected = menu.selectedWord(menu.tableSlot());
        MagicWord selectedDef = MagicWord.byId(selected);
        Component selectedLine = selectedDef == null ? Component.literal("—")
                : MagicGlyphs.leading(selectedDef.id(), Component.literal(selectedDef.label()));
        g.drawString(font, Component.translatable("gui.forbidden_hex.inscription.word", selectedLine),
                WORD_X0 + 4, CAT_Y + 16, 0xFFD8C3A0, false);

        // 只列该分类下的已解锁词
        List<String> all = wordsView;
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            MagicWord w = MagicWord.byId(all.get(i));
            if (w != null && w.role() == CATS[wordCategory]) idx.add(i);
        }
        int pages = Math.max(1, (idx.size() + GRID_CELLS - 1) / GRID_CELLS);
        wordPage = Math.min(wordPage, pages - 1);
        drawPager(g, WORD_PG_X, WORD_PG_Y, wordPage, pages);
        if (idx.isEmpty()) {
            g.drawString(font, Component.translatable("gui.forbidden_hex.inscription.empty_words"), GRID_X, GRID_Y + 8, 0xFFBFA98C, false);
            return;
        }
        hoveredReason = null;
        List<String> inlaid = draftedWords();
        int room = Math.max(1, menu.openSlotCount());
        for (int k = 0; k < GRID_CELLS; k++) {
            int pos = wordPage * GRID_CELLS + k;
            if (pos >= idx.size()) break;
            MagicWord w = MagicWord.byId(all.get(idx.get(pos)));
            if (w == null) continue;
            int cx = GRID_X + (k % GRID_COLS) * GRID_PITCH;
            int cy = GRID_Y + (k / GRID_COLS) * GRID_PITCH;
            Component reason = blockReason(w.id(), inlaid, room);
            boolean enabled = reason == null;
            boolean chosen = w.id().equals(selected);
            boolean hover = inside(mouseX, mouseY, cx, cy, 16, 16);
            int bg = !enabled ? 0xFF221B18
                    : chosen ? 0xFFD2A65B : hover ? 0xFF6B4A33 : 0xFF3A2A22;
            g.fill(cx, cy, cx + 16, cy + 16, bg);
            CoreGuiRenderer.drawWordMark(g, cx + 8, cy + 8, w.id(), menu.selectedInk(), enabled);
            if (hover && enabled) hoveredWord = w.id();
            if (hover && !enabled) hoveredReason = reason;
        }
    }

    /**
     * 草稿里已被占用的词（<b>不含</b>目标槽自己那份）。
     * 判定要基于"正在写的东西"而不是核里已有的内容，否则同一个词能占两个草稿槽。
     */
    private List<String> draftedWords() {
        List<String> out = new ArrayList<>();
        int active = menu.tableSlot();
        for (int i = 0; i < draftsView.length; i++) {
            if (i == active) continue;
            String w = draftsView[i];
            if (w != null && !w.isEmpty()) out.add(w);
        }
        return out;
    }

    /**
     * 为什么这个词现在写不进去（{@code null} = 可以写）。
     *
     * <p>判定与雕刻同源：都在"草稿 + 当前墨"上跑 {@link MagicParser} / {@link MagicValidator}。
     * 失败时把<b>判定层给出的可读原因</b>带出来（例如"某轴对立""星墨不可同句"），
     * 而不是笼统回一句"冲突"——否则玩家换墨换来换去也不知道到底卡在哪一条。
     */
    private Component blockReason(String wordId, List<String> inlaid, int room) {
        List<String> words = java.util.stream.IntStream.range(0, 8).mapToObj(menu::selectedWord).toList();
        List<String> inks = java.util.stream.IntStream.range(0, 8)
                .mapToObj(i -> menu.selectedWord(i).isEmpty() ? "" : menu.selectedWordInk(i).isEmpty()
                        ? menu.selectedInk() : menu.selectedWordInk(i)).toList();
        return InscriptionSelection.reason(words, inks, menu.tableSlot(), wordId, menu.selectedInk(),
                MagicCoreItem.source(menu.inputCore()).id(), menu.openSlotCount(),
                menu.unlockedWords(), menu.unlockedInks());
    }

    /** 分类按钮的 lang 键。 */
    private static String catKey(int i) {
        return switch (i) {
            case 1 -> "gui.forbidden_hex.inscription.cat_point";
            case 2 -> "gui.forbidden_hex.inscription.cat_structure";
            case 3 -> "gui.forbidden_hex.inscription.cat_nature";
            default -> "gui.forbidden_hex.inscription.cat_action";
        };
    }

    /** 右上「预览」：当前选择（术式数值由刻好的核心悬浮文本承担）。 */
    private void drawSelection(GuiGraphics g) {
        String word = menu.selectedWord(menu.tableSlot());
        MagicWord wordDef = MagicWord.byId(word);
        Component wordText = wordDef == null ? Component.literal("—")
                : MagicGlyphs.leading(wordDef.id(), Component.literal(wordDef.label()));
        String ink = menu.selectedInk().isEmpty() ? "—" : menu.selectedInk();
        int tx = SEL_X0 + 6, ty = UP_Y0 + 14;
        g.drawString(font, Component.translatable("gui.forbidden_hex.inscription.ink", ink), tx, ty, 0xFFE7C99A, false);
        g.drawString(font, Component.translatable("gui.forbidden_hex.inscription.word", wordText), tx, ty + 12, 0xFFE7C99A, false);
        g.drawString(font, Component.translatable("gui.forbidden_hex.inscription.slot", menu.tableSlot() + 1), tx, ty + 24, 0xFFE7C99A, false);
    }

    /** 右下：产物槽（预览区）= 刻好的核心；悬浮查看属性；下方是雕刻按钮。 */
    private void drawPreview(GuiGraphics g) {
        slotFrame(g, OUT_X, OUT_Y, 0xFFFFD36A);
        // 产物物品由真实槽位渲染，这里只补一句状态提示
        boolean hasProduct = menu.productCore().getItem() instanceof MagicCoreItem;
        boolean hasInput = menu.inputCore().getItem() instanceof MagicCoreItem;
        String hint = hasProduct ? "gui.forbidden_hex.inscription.product_ready"
                : hasInput ? "gui.forbidden_hex.inscription.product_pending"
                : "gui.forbidden_hex.inscription.product_empty";
        g.drawString(font, Component.translatable(hint), PRV_X0 + 5, OUT_Y + 24, 0xFFBFA98C, false);
        boolean enabled = hasInput && !hasProduct && MagicCoreItem.summarize(menu.draftCore()).result() != null;
        g.fill(ENGRAVE_X, ENGRAVE_Y, ENGRAVE_X + ENGRAVE_W, ENGRAVE_Y + ENGRAVE_H,
                enabled ? 0xFF54231F : 0xFF221B18);
        g.fill(ENGRAVE_X, ENGRAVE_Y, ENGRAVE_X + ENGRAVE_W, ENGRAVE_Y + 2, 0xFFC18B48);
        g.fill(ENGRAVE_X, ENGRAVE_Y + ENGRAVE_H - 2, ENGRAVE_X + ENGRAVE_W, ENGRAVE_Y + ENGRAVE_H, 0xFFC18B48);
        g.drawCenteredString(font, Component.translatable("gui.forbidden_hex.inscription.engrave"),
                ENGRAVE_X + ENGRAVE_W / 2, ENGRAVE_Y + 5, 0xFFF0D8A2);
    }

    /**
     * 墨的悬浮说明：属性 + 四个数值列 + 手感画像。
     *
     * <p>数值<b>直读墨表</b>（不在这里另写一份），所以手册 / 界面 / 结算永远同一套。
     */
    private MagicWord currentCore() {
        List<String> words = new ArrayList<>();
        for (String draft : draftsView) if (draft != null && !draft.isEmpty()) words.add(draft);
        return MagicParser.parseIds(words).core();
    }

    private List<FormattedCharSequence> wordTooltip(MagicWord word, MagicInkDef ink, MagicWord core) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(MagicGlyphs.leading(word.id(), Component.literal(word.label()).withStyle(ChatFormatting.WHITE))
                .getVisualOrderText());
        lines.add(Component.translatable("gui.forbidden_hex.inscription.word_role_" + word.role().name().toLowerCase(java.util.Locale.ROOT))
                .withStyle(ChatFormatting.GRAY).getVisualOrderText());
        if (core == null || core == word) {
            lines.add(Component.translatable("gui.forbidden_hex.inscription.word_core",
                    Component.translatable("item.forbidden_hex.magic_core.op." + word.effect()))
                    .withStyle(ChatFormatting.YELLOW).getVisualOrderText());
        } else {
            lines.add(Component.translatable("gui.forbidden_hex.inscription.word_modifier",
                    com.example.akaishi.forbidden.magic.judge.MagicModifierSupport.description(word, core))
                    .withStyle(ChatFormatting.YELLOW).getVisualOrderText());
        }
        lines.add(Component.translatable("gui.forbidden_hex.inscription.word_effect_delivery",
                effectLabel(word.effect()), deliveryLabel(word.delivery()))
                .withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
        if (ink != null) lines.add(Component.translatable("gui.forbidden_hex.inscription.ring_ink", ink.label())
                .withStyle(ChatFormatting.GRAY).getVisualOrderText());
        return lines;
    }

    private static List<FormattedCharSequence> inkTooltip(MagicInkDef d) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(Component.literal(d.label() + " · " + d.attribute())
                .withStyle(ChatFormatting.WHITE).getVisualOrderText());
        lines.add(Component.translatable("gui.forbidden_hex.inscription.ink_cost", num(d.costMul()))
                .withStyle(ChatFormatting.GOLD).getVisualOrderText());
        lines.add(Component.translatable("gui.forbidden_hex.inscription.ink_cooldown", num(d.cooldown()))
                .withStyle(ChatFormatting.YELLOW).getVisualOrderText());
        lines.add(Component.translatable("gui.forbidden_hex.inscription.ink_cast", num(d.castTime()))
                .withStyle(ChatFormatting.YELLOW).getVisualOrderText());
        lines.add(Component.translatable("gui.forbidden_hex.inscription.ink_range", String.valueOf(d.range()))
                .withStyle(ChatFormatting.AQUA).getVisualOrderText());
        if (d.exclusive()) {
            lines.add(Component.translatable("gui.forbidden_hex.inscription.ink_exclusive")
                    .withStyle(ChatFormatting.LIGHT_PURPLE).getVisualOrderText());
        }
        if (d.id().equals("xing")) lines.add(Component.translatable("item.forbidden_hex.magic_core.plan.star")
                .withStyle(ChatFormatting.LIGHT_PURPLE).getVisualOrderText());
        return lines;
    }

    /** 效果 / 交付的可读名（直读表，不另写一份）；未知 id 原样显示便于排错。 */
    private static String effectLabel(String id) {
        if (id == null || id.isEmpty()) return "—";
        MagicEffectDef def = MagicEffectTable.get(id);
        return def == null ? id : def.label();
    }

    private static String deliveryLabel(String id) {
        if (id == null || id.isEmpty()) return "—";
        MagicDeliveryDef def = MagicDeliveryTable.get(id);
        return def == null ? id : def.label();
    }

    /** 保留两位小数，固定 Locale，避免某些语言环境下小数点变成逗号。 */
    private static String num(float value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    /** 墨物品显示栈缓存（避免每帧 new ItemStack 触发模型查找）。 */
    private static ItemStack inkStack(String id) {
        return INK_STACKS.computeIfAbsent(id, k -> {
            var it = AkaishiMagicItems.ink(k);
            return it == null ? ItemStack.EMPTY : new ItemStack(it.get());
        });
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    private static void frameBox(GuiGraphics g, int x0, int y0, int x1, int y1, int fill, int border, int inner) {
        g.fill(x0, y0, x1, y1, fill);
        g.fill(x0, y0, x1, y0 + 1, border); g.fill(x0, y1 - 1, x1, y1, border);
        g.fill(x0, y0, x0 + 1, y1, border); g.fill(x1 - 1, y0, x1, y1, border);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, inner); g.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, inner);
        g.fill(x0 + 1, y0 + 1, x0 + 2, y1 - 1, inner); g.fill(x1 - 2, y0 + 1, x1 - 1, y1 - 1, inner);
    }

    private static void slotFrame(GuiGraphics g, int x, int y, int edge) { g.fill(x - 1, y - 1, x + 17, y + 17, 0xFF171216); g.fill(x, y, x + 16, y + 16, edge); g.fill(x + 2, y + 2, x + 14, y + 14, 0xFF2A2020); }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 每帧只解析一次已解锁墨 / 词，供本帧所有绘制与命中使用
        inksView = new ArrayList<>(menu.unlockedInks());
        wordsView = new ArrayList<>(menu.unlockedWords());
        renderBackground(g); hoveredInk = hoveredWord = ""; hoveredReason = null; super.render(g, mouseX, mouseY, partialTick);
        double lx = mouseX - leftPos, ly = mouseY - topPos;
        if (!hoveredInk.isEmpty()) {
            MagicInkDef d = MagicInkTable.get(hoveredInk);
            if (d != null) g.renderTooltip(font, inkTooltip(d), mouseX, mouseY);
        }
        else if (!hoveredWord.isEmpty()) {
            MagicWord w = MagicWord.byId(hoveredWord);
            if (w != null) {
                MagicInkDef wi = MagicInkTable.get(menu.selectedInk());
                g.renderTooltip(font, wordTooltip(w, wi, currentCore()), mouseX, mouseY);
            }
        }
        else if (hoveredReason != null) { g.renderTooltip(font, hoveredReason, mouseX, mouseY); }
        else if (inside(lx, ly, ENGRAVE_X, ENGRAVE_Y, ENGRAVE_W, ENGRAVE_H)
                || hasShiftDown() && inside(lx, ly, SEL_X0, UP_Y0, SEL_X1 - SEL_X0, UP_Y1 - UP_Y0)) {
            var summary = MagicCoreItem.summarize(menu.draftCore());
            List<Component> lines = new ArrayList<>();
            if (summary.result() == null) lines.add(summary.reason());
            else if (hasShiftDown())
                com.example.akaishi.forbidden.magic.formula.SpellPlanTooltips.append(lines, summary.result(), true, 0f);
            else {
                lines.add(Component.translatable("gui.forbidden_hex.inscription.preview_hint").withStyle(ChatFormatting.GRAY));
                for (var operation : summary.result().operations()) {
                    lines.add(Component.translatable("item.forbidden_hex.magic_core.plan.brief",
                            Component.translatable("item.forbidden_hex.magic_core.op." + operation.effectId()),
                            com.example.akaishi.forbidden.magic.formula.SpellPlanTooltips.briefValue(operation)));
                }
                lines.add(Component.translatable("item.forbidden_hex.magic_core.plan.cost",
                        num(summary.result().cost().total()), num(summary.result().cost().segmentCost())));
            }
            g.renderTooltip(font, lines.stream().map(Component::getVisualOrderText).toList(), mouseX, mouseY);
        }
        else if (!renderRingTooltip(g, lx, ly, mouseX, mouseY)) {
            // 其余区域（中央/预览面板的空白处等）不再显示任何悬浮文本，
            // 核心与产物的信息只在各自的物品槽上悬停时才出（原版槽位提示 = 物品自身 tooltip）
            renderTooltip(g, mouseX, mouseY);
        }
    }

    /**
     * 中央圆环八个镶嵌位：悬停显示该位的词（词名 / 核心义 / 修饰义 / 效果 / 交付 / 墨）。
     * 空位与封印位不显示任何东西。返回是否已绘制。
     */
    private boolean renderRingTooltip(GuiGraphics g, double lx, double ly, int mouseX, int mouseY) {
        for (int i = 0; i < 8; i++) {
            if (!CoreLayout.hitSlot(lx, ly, i)) continue;
            MagicWord def = MagicWord.byId(draftsView[i]);
            if (def == null) return true;      // 空位：拦下，不给面板空白处弹提示
            MagicInkDef inkDef = MagicInkTable.get(draftInksView[i]);
            g.renderTooltip(font, wordTooltip(def, inkDef, currentCore()), mouseX, mouseY);
            return true;
        }
        return false;
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        double lx = mx - leftPos, ly = my - topPos;
        for (int i = 0; i < 8; i++) if (CoreLayout.hitSlot(lx, ly, i)) { click(AkaishiInscriptionTableMenu.BUTTON_SLOT_BASE + i); return true; }
        List<String> inks = inksView;
        // 墨区翻页箭头
        int inkPages = Math.max(1, (inks.size() + INK_ROWS - 1) / INK_ROWS);
        if (inside(lx, ly, INK_PG_X, INK_PG_Y, ARROW_W, ARROW_H)) { inkPage = Math.max(0, inkPage - 1); return true; }
        if (inside(lx, ly, INK_PG_X + 15, INK_PG_Y, ARROW_W, ARROW_H)) { inkPage = Math.min(inkPages - 1, inkPage + 1); return true; }
        for (int i = 0; i < INK_ROWS; i++) { int index = inkPage * INK_ROWS + i;
            if (index < inks.size() && inside(lx, ly, INK_ROW_X, INK_ROW_Y + i * INK_ROW_H, INK_ROW_W, INK_ROW_H)) { click(AkaishiInscriptionTableMenu.BUTTON_INK_BASE + index); return true; } }
        // 分类按钮：纯客户端视图切换
        for (int i = 0; i < 4; i++) {
            if (inside(lx, ly, CAT_X + i * (CAT_W + CAT_GAP), CAT_Y, CAT_W, CAT_H)) { wordCategory = i; wordPage = 0; return true; }
        }
        // 符文小格：按分类过滤后取原始下标
        List<String> all = wordsView;
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) { MagicWord w = MagicWord.byId(all.get(i)); if (w != null && w.role() == CATS[wordCategory]) idx.add(i); }
        // 词区翻页箭头
        int wordPages = Math.max(1, (idx.size() + GRID_CELLS - 1) / GRID_CELLS);
        if (inside(lx, ly, WORD_PG_X, WORD_PG_Y, ARROW_W, ARROW_H)) { wordPage = Math.max(0, wordPage - 1); return true; }
        if (inside(lx, ly, WORD_PG_X + 15, WORD_PG_Y, ARROW_W, ARROW_H)) { wordPage = Math.min(wordPages - 1, wordPage + 1); return true; }
        List<String> inlaidNow = draftedWords();
        int roomNow = Math.max(1, menu.openSlotCount());
        for (int k = 0; k < GRID_CELLS; k++) {
            int pos = wordPage * GRID_CELLS + k; if (pos >= idx.size()) break;
            int cx = GRID_X + (k % GRID_COLS) * GRID_PITCH, cy = GRID_Y + (k / GRID_COLS) * GRID_PITCH;
            if (!inside(lx, ly, cx, cy, 16, 16)) continue;
            String wordId = all.get(idx.get(pos));
            if (blockReason(wordId, inlaidNow, roomNow) != null) return true;   // 结构/墨不允许：不可选取
            click(AkaishiInscriptionTableMenu.BUTTON_WORD_BASE + idx.get(pos)); return true;
        }
        if (inside(lx, ly, ENGRAVE_X, ENGRAVE_Y, ENGRAVE_W, ENGRAVE_H)) {
            if (menu.productCore().isEmpty() && MagicCoreItem.summarize(menu.draftCore()).result() != null)
                click(AkaishiInscriptionTableMenu.BUTTON_ENGRAVE);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private void click(int id) { minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id); }

    @Override public boolean mouseScrolled(double mx, double my, double delta) {
        double lx = mx - leftPos, ly = my - topPos;
        if (inside(lx, ly, INK_X0, UP_Y0, INK_X1 - INK_X0, UP_Y1 - UP_Y0)) { inkPage = page(inkPage, delta, inksView.size(), INK_ROWS); return true; }
        if (inside(lx, ly, WORD_X0, LO_Y0, WORD_X1 - WORD_X0, LO_Y1 - LO_Y0)) {
            int count = 0;
            for (String id : wordsView) {
                MagicWord w = MagicWord.byId(id);
                if (w != null && w.role() == CATS[wordCategory]) count++;
            }
            wordPage = page(wordPage, delta, count, GRID_CELLS); return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    private static int page(int current, double delta, int count, int rows) { int pages = Math.max(1, (count + rows - 1) / rows); return Math.max(0, Math.min(pages - 1, current + (delta < 0 ? 1 : -1))); }
}
