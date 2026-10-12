package com.example.akaishi.forbidden.menu;

import com.example.akaishi.forbidden.codex.CodexNode;
import com.example.akaishi.forbidden.codex.CodexNodeState;
import com.example.akaishi.forbidden.codex.CodexTable;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 秘典「研究画布」：神秘时代（Thaumcraft）魔导手册式的一张<b>徽记图版</b>——
 * 节点是贴在羊皮纸上的徽记（见 {@link AkaishiCodexEmblem}），前置关系是直角墨线
 * （见 {@link AkaishiCodexRender#link}）；<b>按住左键拖动即可平移整张图</b>，
 * 画布<b>不受窗口边界限制</b>（内容拖到哪都行，只要还留一角在窗口里，见 {@link #MIN_VISIBLE}）。
 *
 * <p><b>它只做三件事</b>：① 把数据表坐标换算成画布坐标（含 {@link #SPACING_SCALE} 放大）；
 * ② 维护"每族各自的平移量 / 是否已自动居中"；③ 命中与拖动。
 * <b>它不再画任何像素</b>：徽记、连线、名字全部走渲染框架（{@link AkaishiCodexRender} /
 * {@link AkaishiCodexEmblem}），故以后加内容不必再进这个文件。
 *
 * <p><b>状态语言（照 TC 的四档，画法各不相同）</b>：已读＝亮、可研究＝闪、隐藏研究＝偏黄、
 * 被挡＝暗，禁忌另叠一圈紫雾。四档的判定与画法都在 {@link AkaishiCodexEmblem}，
 * 本类只把"这个节点现在该是哪一档"从服务端快照里取出来传进去。
 *
 * <p><b>作用域（族 = 一张独立图版）</b>：{@code scope == null} = 全部族（保留能力，当前界面不再用），
 * {@code scope == 族 id} = 只画本族。作用域由界面按书签选择设定，一变就重算包围盒并<b>恢复该族上次的平移</b>
 * （{@link #pans} / {@link #centeredScopes}）；于是"换书签 = 换一张图版，各图版的视角互不干扰"。
 *
 * <p><b>族里为什么不画跨族前置线</b>：知识网络里有<b>跨族前置</b>（{@code cold_sympathy} 属"疼与血"族，
 * 却要"静与暗"族的 {@code quiet_thought} 当前置）。每张图版只装本族节点，那条线的另一端在别的图版上，
 * 画一条通向画布外的线只会让人以为数据坏了；它改由条目页的<b>条件清单</b>用文字给出（且可点击跳转）。
 *
 * <p><b>图标零贴图资产</b>：节点图标直接用 {@code node.icon()} 的物品栈渲染，进界面时求值一次即缓存
 * （见 {@link #buildIcons}），渲染期只读。
 */
final class AkaishiCodexCanvasView {

    // ===== 画布内几何（待调手感值） =====

    /** 徽记边长：贴图按 32×32 出稿，故取 32（`1:1` 采样最清晰） */
    private static final int NODE_BOX = 32;
    /** 数据表坐标 → 画布坐标的放大系数（列距 88→158、行距 44→79；面板放大后图版不至于挤在中间） */
    private static final float SPACING_SCALE = 1.8f;
    /** 徽记名放在徽记下 4px，行距 79 足够不压到下一行 */
    private static final int NODE_LABEL_GAP = 4;
    private static final int NODE_LABEL_H = 9;
    /** 徽记名最大绘制宽度（列距 158，留出与邻列的间隙） */
    private static final int NODE_LABEL_MAX = 140;
    /** 图版顶部留白（徽记上方不至于贴住纸面页眉） */
    private static final int PLATE_TOP_PAD = 14;
    /** 拖动时内容至少留在画布里的像素（保证拖远了还能抓回来） */
    private static final int MIN_VISIBLE = 56;
    /** 位移小于该像素视为"点击"而非拖动 */
    private static final int DRAG_THRESHOLD = 3;

    // ===== 状态 =====

    /** 图版视口：书体尺寸随内容变，故由界面每帧同步（见 {@link #setViewport}），这里不能是 final */
    private AkaishiCodexRender.Area vp = new AkaishiCodexRender.Area(0, 0, 0, 0);
    /** 视口是否已同步过（首帧必须同步一次，否则平移量是按 0 视口算的） */
    private boolean viewportSet;
    private final List<CodexNode> nodes;
    /** 每节点图标缓存：进界面建一次，渲染期只读（键 = 节点 id 字符串） */
    private final Map<String, ItemStack> icons = new HashMap<>();
    /** 每族各自的平移量（键 = 族 id；换书签来回不会丢视角） */
    private final Map<String, int[]> pans = new HashMap<>();
    /** 已经"自动居中到当前进度"过的族（每族只做一次，之后不抢玩家的手） */
    private final Set<String> centeredScopes = new HashSet<>();

    /** 内容包围盒（本地坐标左上角恒为 (0,0)；只含作用域内的节点） */
    private int contentW;
    private int contentH;
    private int contentMinX;
    private int contentMinY;
    /** 平移量（内容左上角在画布内的偏移；只平移不缩放） */
    private int panX;
    private int panY;
    /** 作用域：null = 全部族；非 null = 只画该族（一张图版 = 一个书签） */
    @Nullable
    private String scope;
    /** 作用域是否已按界面要求同步过（首帧必须同步一次，之后只在变化时重算） */
    private boolean scopeSet;
    /** 拖动中 / 本次拖动累计位移（用于区分"点击"与"拖动"） */
    private boolean dragging;
    private double dragMoved;
    private double dragLastX;
    private double dragLastY;

    AkaishiCodexCanvasView(List<CodexNode> nodes) {
        this.nodes = List.copyOf(nodes);
        buildIcons();
        recompute(null);
        resetPan();
    }

    // ===== 生命周期 =====

    /**
     * 同步图版视口（= 书体减去页眉与留白后的那块）。
     *
     * <p>书体尺寸按内容变（见 {@code AkaishiCodexRender#chartBook}），所以视口每帧由界面算好传进来；
     * 只有真的变了才重算平移量：还没自动居中过就重新居中，已居中过则把已有的平移量重新钳进新视口。
     */
    void setViewport(AkaishiCodexRender.Area area) {
        if (viewportSet && vp.equals(area)) {
            return;
        }
        vp = area;
        if (!centered()) {
            resetPan();
        } else {
            panX = clampPan(panX, vp.w(), contentW);
            panY = clampPan(panY, vp.h(), contentH);
        }
        viewportSet = true;
    }

    /** 本作用域的内容包围盒宽（界面据此算画布态书体宽） */
    int contentW() {
        return contentW;
    }

    /** 本作用域的内容包围盒高（界面据此算画布态书体高） */
    int contentH() {
        return contentH;
    }

    /** 图标求值一次即缓存：物品走 Supplier 延迟求值（规避注册顺序），单个取不到只让该节点空着，不连累其它 */
    private void buildIcons() {
        icons.clear();
        for (CodexNode node : nodes) {
            ItemStack stack = ItemStack.EMPTY;
            try {
                ItemStack supplied = node.icon() == null ? null : node.icon().get();
                if (supplied != null) {
                    stack = supplied.copy();
                }
            } catch (RuntimeException ignored) {
                // 某项注册表尚未就绪：该节点画成空白徽记（名字仍在），不把整个界面拖崩
            }
            icons.put(node.id().toString(), stack);
        }
    }

    /** 某节点的图标（书签复用同一份缓存；未知 id / 未就绪返回空栈） */
    ItemStack iconOf(@Nullable String nodeId) {
        ItemStack stack = nodeId == null ? null : icons.get(nodeId);
        return stack == null ? ItemStack.EMPTY : stack;
    }

    /** 某族第一个节点的 id（书签取图标用；本族没有节点返回 null） */
    @Nullable
    static String firstNodeId(List<CodexNode> all, String family) {
        for (CodexNode node : all) {
            if (family.equals(node.family())) {
                return node.id().toString();
            }
        }
        return null;
    }

    /**
     * 切换作用域（null = 全部族，非 null = 只画该族的一张图版）。
     *
     * <p>每帧调用都安全：只有作用域真的变了才重算包围盒并<b>恢复该族上次的平移</b>
     * （界面不必自己记旧值）。需要 {@link Font} 是因为包围盒要把"徽记名"的宽度也算进去
     * （否则长名字会被裁在徽记边上）。
     */
    void setScope(Font font, @Nullable String family) {
        if (scopeSet && Objects.equals(scope, family)) {
            return;
        }
        scope = family;
        scopeSet = true;
        recompute(font);
        int[] saved = pans.get(scopeKey());
        if (saved == null) {
            resetPan();
        } else {
            panX = clampPan(saved[0], vp.w(), contentW);
            panY = clampPan(saved[1], vp.h(), contentH);
        }
    }

    /** 内容包围盒：横向含徽记与徽记名，纵向含徽记名（下）与图版留白（上） */
    private void recompute(@Nullable Font font) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int labelW = 0;
        for (CodexNode node : nodes) {
            if (!inScope(node)) {
                continue;
            }
            minX = Math.min(minX, scaledX(node));
            minY = Math.min(minY, scaledY(node));
            maxX = Math.max(maxX, scaledX(node));
            maxY = Math.max(maxY, scaledY(node));
            if (font != null) {
                labelW = Math.max(labelW, Math.min(font.width(Component.translatable(node.nameKey()).getString()),
                        NODE_LABEL_MAX));
            }
        }
        if (minX == Integer.MAX_VALUE) {
            contentMinX = 0;
            contentMinY = 0;
            contentW = 0;
            contentH = 0;
            return;
        }
        // 徽记名居中画在徽记下方，可能向两侧溢出，故左右各让出半个溢出量
        int padX = Math.max(0, (Math.max(labelW, NODE_BOX) - NODE_BOX) / 2);
        contentMinX = minX - padX;
        contentMinY = minY;
        contentW = (maxX - minX) + NODE_BOX + 2 * padX;
        contentH = (maxY - minY) + NODE_BOX + NODE_LABEL_GAP + NODE_LABEL_H + PLATE_TOP_PAD;
    }

    /** 回到几何中心（首次进入某族时；随后由首个快照触发 {@link #centerOnProgress}） */
    private void resetPan() {
        panX = (vp.w() - contentW) / 2;
        panY = (vp.h() - contentH) / 2;
        savePan();
    }

    /** 本作用域是否已按"当前进度节点"自动居中过 */
    boolean centered() {
        return centeredScopes.contains(scopeKey());
    }

    boolean isDragging() {
        return dragging;
    }

    /** 自动居中：优先首个"可研究"（只看作用域内的节点），其次最后一个"已学"，都没有则几何中心 */
    void centerOnProgress(List<AkaishiCodexSync.NodeView> views) {
        CodexNode target = null;
        CodexNode lastLearned = null;
        for (CodexNode node : nodes) {
            if (!inScope(node)) {
                continue;
            }
            CodexNodeState state = stateOf(node, views);
            if (state == CodexNodeState.AVAILABLE && target == null) {
                target = node;
            }
            if (state == CodexNodeState.LEARNED) {
                lastLearned = node;
            }
        }
        if (target == null) {
            target = lastLearned;
        }
        if (target == null) {
            panX = (vp.w() - contentW) / 2;
            panY = (vp.h() - contentH) / 2;
        } else {
            panX = vp.w() / 2 - (localX(target) + NODE_BOX / 2);
            panY = vp.h() / 2 - (localY(target) + NODE_BOX / 2);
        }
        panX = clampPan(panX, vp.w(), contentW);
        panY = clampPan(panY, vp.h(), contentH);
        savePan();
        centeredScopes.add(scopeKey());
    }

    // ===== 绘制（全部委托渲染框架） =====

    /** 画整张图版（直角墨线 → 徽记）；内容裁在视口内（坐标一律实际 GUI 坐标，整数） */
    void render(GuiGraphics gui, Font font, List<AkaishiCodexSync.NodeView> views,
                double mouseX, double mouseY) {
        CodexNode hovered = hovered(mouseX, mouseY);
        AkaishiCodexRender.scissor(gui, vp);
        // 1) 墨线（画在徽记下层：先线后徽记，线天然落在徽记之下，不会切断图案）
        //    挡路的连线要比别的高亮，故分两趟：先全部常态/暗线，再补高亮线 + 箭头
        for (CodexNode node : nodes) {
            drawLinks(gui, node, views, false);
        }
        for (CodexNode node : nodes) {
            if (inScope(node) && blockingPrereq(node, views) != null) {
                drawLinks(gui, node, views, true);
            }
        }
        // 2) 徽记
        for (CodexNode node : nodes) {
            if (inScope(node)) {
                drawNode(gui, font, node, node == hovered, views);
            }
        }
        AkaishiCodexRender.unscissor(gui);
    }

    /**
     * 画 {@code node} 的前置连线。
     *
     * @param highlightOnly false = 只画常态/暗线（跳过挡路的那条）；true = 只画挡路那条（粗线 + 箭头）
     */
    private void drawLinks(GuiGraphics gui, CodexNode node, List<AkaishiCodexSync.NodeView> views,
                           boolean highlightOnly) {
        if (!inScope(node)) {
            return;
        }
        CodexNodeState state = stateOf(node, views);
        CodexNode blocker = state == CodexNodeState.LOCKED ? blockingPrereq(node, views) : null;
        int inked = AkaishiCodexRender.inked(node.family());
        for (ResourceLocation prereqId : node.requiredNodes()) {
            CodexNode prereq = CodexTable.get(prereqId);
            // 跨族前置的另一端不在这张图版上，不画（见类注释：改由条目页的条件清单给出）
            if (prereq == null || !inScope(prereq)) {
                continue;
            }
            boolean blocking = blocker != null && prereq.id().equals(blocker.id());
            if (blocking != highlightOnly) {
                continue;
            }
            int color = blocking
                    ? AkaishiCodexBookArt.semi(AkaishiCodexRender.familyColor(node.family()),
                            AkaishiCodexRender.LINK_ALPHA_HIGHLIGHT)
                    : AkaishiCodexBookArt.semi(inked, blocker != null
                            ? AkaishiCodexRender.LINK_ALPHA_DIM : AkaishiCodexRender.LINK_ALPHA_NORMAL);
            // 箭头指向"挡路的前置"：把 (x1,y1) 传成前置，框架会把箭头画在它那一端
            AkaishiCodexRender.link(gui,
                    centerX(prereq), centerY(prereq), centerX(node), centerY(node), color,
                    blocking ? AkaishiCodexRender.LINK_W_THICK : AkaishiCodexRender.LINK_W_THIN, blocking);
        }
    }

    /** 一个徽记 + 徽记名（画法与状态叠加都在 {@link AkaishiCodexEmblem}） */
    private void drawNode(GuiGraphics gui, Font font, CodexNode node, boolean hovered,
                          List<AkaishiCodexSync.NodeView> views) {
        AkaishiCodexRender.Area area = new AkaishiCodexRender.Area(
                nodeX(node), nodeY(node), NODE_BOX, NODE_BOX);
        AkaishiCodexSync.NodeView view = viewOf(node, views);
        AkaishiCodexEmblem.Mark mark =
                AkaishiCodexEmblem.mark(stateOf(node, views), view);
        AkaishiCodexEmblem.emblem(gui, area, node, mark, icons.get(node.id().toString()),
                AkaishiCodexRender.pulse(), hovered);
        String name = font.plainSubstrByWidth(
                Component.translatable(node.nameKey()).getString(), NODE_LABEL_MAX);
        AkaishiCodexRender.label(gui, font, name, area.cx(), area.bottom() + NODE_LABEL_GAP,
                node.family(), mark, node.forbidden());
    }

    /**
     * 挡住 {@code node} 的那个前置：第一个"还没读完"的前置节点（本族与否都算）。
     * <p>被挡的徽记据此<b>用连线指出挡在研究路上的那个</b>（TC 的做法），
     * 让玩家一眼知道该去读谁，而不是只看到一团暗；界面也用它写悬停提示的"挡路者：X"。
     * 跨族前置的连线画不出来（另一端不在这张图版上），但名字仍在提示里给出。
     */
    @Nullable
    static CodexNode blockingPrereq(CodexNode node, List<AkaishiCodexSync.NodeView> views) {
        if (stateOf(node, views) != CodexNodeState.LOCKED) {
            return null;
        }
        for (ResourceLocation prereqId : node.requiredNodes()) {
            CodexNode prereq = CodexTable.get(prereqId);
            if (prereq != null && stateOf(prereq, views) != CodexNodeState.LEARNED) {
                return prereq;
            }
        }
        return null;
    }

    // ===== 交互（鼠标就是实际 GUI 坐标，无需任何换算） =====

    /** 是否落在图版视口内 */
    boolean isIn(double mouseX, double mouseY) {
        return AkaishiCodexRender.in(mouseX, mouseY, vp);
    }

    void beginDrag(double mouseX, double mouseY) {
        dragging = true;
        dragMoved = 0D;
        dragLastX = mouseX;
        dragLastY = mouseY;
    }

    void dragTo(double mouseX, double mouseY) {
        if (!dragging) {
            return;
        }
        dragMoved += Math.abs(mouseX - dragLastX) + Math.abs(mouseY - dragLastY);
        panX = clampPan(panX + (int) Math.round(mouseX - dragLastX), vp.w(), contentW);
        panY = clampPan(panY + (int) Math.round(mouseY - dragLastY), vp.h(), contentH);
        dragLastX = mouseX;
        dragLastY = mouseY;
        savePan();
    }

    /**
     * 松开左键：位移没过阈值 = 点击 —— 点在徽记上返回该节点；点空白则重新居中到当前进度（拖丢了的兜底）。
     *
     * @return 被点中的节点；未点中任何节点（含本次是拖动）返回 null
     */
    @Nullable
    CodexNode release(double mouseX, double mouseY, List<AkaishiCodexSync.NodeView> views) {
        if (!dragging) {
            return null;
        }
        dragging = false;
        if (dragMoved >= DRAG_THRESHOLD) {
            return null;
        }
        CodexNode node = hovered(mouseX, mouseY);
        if (node == null) {
            centerOnProgress(views);
        }
        return node;
    }

    /** 鼠标下的徽记（命中区比徽记稍大 2px，便于点中小图标）；不在图版内或不在作用域内返回 null */
    @Nullable
    CodexNode hovered(double mouseX, double mouseY) {
        if (!isIn(mouseX, mouseY)) {
            return null;
        }
        int rx = (int) Math.floor(mouseX);
        int ry = (int) Math.floor(mouseY);
        // 倒序：后画的在上，命中取其先
        for (int i = nodes.size() - 1; i >= 0; i--) {
            CodexNode node = nodes.get(i);
            if (!inScope(node)) {
                continue;
            }
            int bx = nodeX(node);
            int by = nodeY(node);
            if (rx >= bx - 2 && rx < bx + NODE_BOX + 2 && ry >= by - 2 && ry < by + NODE_BOX + 2) {
                return node;
            }
        }
        return null;
    }

    // ===== 坐标与小工具 =====

    /** 是否在当前作用域内（null = 全部族都在） */
    private boolean inScope(CodexNode node) {
        return scope == null || scope.equals(node.family());
    }

    /** 当前作用域的键（null 作用域归一成空串，免得 Map 里出现 null 键） */
    private String scopeKey() {
        return scope == null ? "" : scope;
    }

    /** 记下本族当前视角（切走再切回时恢复；也是"每族各自独立"的唯一落点） */
    private void savePan() {
        pans.put(scopeKey(), new int[]{panX, panY});
    }

    /** 数据表 X → 画布 X（含间距放大；表里的坐标是数据，不因界面放大而改） */
    private static int scaledX(CodexNode node) {
        return Math.round(node.x() * SPACING_SCALE);
    }

    private static int scaledY(CodexNode node) {
        return Math.round(node.y() * SPACING_SCALE);
    }

    private int localX(CodexNode node) {
        return scaledX(node) - contentMinX;
    }

    private int localY(CodexNode node) {
        return scaledY(node) - contentMinY + PLATE_TOP_PAD;
    }

    /** 徽记左上角（设计坐标） */
    private int nodeX(CodexNode node) {
        return vp.x() + panX + localX(node);
    }

    private int nodeY(CodexNode node) {
        return vp.y() + panY + localY(node);
    }

    /** 徽记中心（连线的两端） */
    private int centerX(CodexNode node) {
        return nodeX(node) + NODE_BOX / 2;
    }

    private int centerY(CodexNode node) {
        return nodeY(node) + NODE_BOX / 2;
    }

    /** 平移量钳制：任何方向上内容都至少留 {@link #MIN_VISIBLE} px 在图版里（拖不丢） */
    private static int clampPan(int pan, int viewport, int content) {
        int lo = MIN_VISIBLE - content;
        int hi = viewport - MIN_VISIBLE;
        return Math.max(lo, Math.min(hi, pan));
    }

    /** 某节点的服务端快照（未到时返回 null ⇒ 最保守画法） */
    @Nullable
    private static AkaishiCodexSync.NodeView viewOf(CodexNode node, List<AkaishiCodexSync.NodeView> views) {
        String id = node.id().toString();
        for (AkaishiCodexSync.NodeView view : views) {
            if (view.id().equals(id)) {
                return view;
            }
        }
        return null;
    }

    private static CodexNodeState stateOf(CodexNode node, List<AkaishiCodexSync.NodeView> views) {
        AkaishiCodexSync.NodeView view = viewOf(node, views);
        // 快照未到时按最保守的 LOCKED 画（不把没资格的节点画亮）
        return view == null ? CodexNodeState.LOCKED : CodexNodeState.byId(view.state());
    }
}
