package com.example.akaishi.forbidden.menu;

import com.example.akaishi.forbidden.codex.CodexNode;
import com.example.akaishi.forbidden.codex.CodexNodeState;
import com.example.akaishi.forbidden.codex.CodexTier;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

/**
 * 秘典的「徽记」：画布上一个节点的完整外观（外框 + 内部图案 + 状态叠加 + 名字）。
 *
 * <p><b>为什么把徽记从画布里单独拆出来</b>：画布管"摆在哪、怎么平移、谁被点中"，
 * 徽记管"一个节点长什么样"。以后新增内容（新层级、新族、新的解锁状态）只改这一处，
 * 画布不用再碰像素代码 —— 这正是用户要的"以后添加内容直接用那个渲染就行"。
 *
 * <p><b>外框形状 = 知识层级</b>（{@link CodexTier}，与 {@code AkaishiCodexCanvasView} 的旧口径一致）：
 * 简单＝直角纸片、疯狂＝切角纸片、禁忌＝带刺纸片；<b>配色 = 族色</b>（族色原色只用在框线，纸面走墨化）。
 *
 * <p><b>内部图案优先读贴图</b>：{@code assets/forbidden_hex/textures/gui/codex/emblem/<节点路径>.png}
 * （32×32、透明底，<b>由建模侧并行产出</b>）。读不到就回落到"该节点现有物品图标 + 程序化描边"
 * —— 两条路都成立，故本轮不依赖任何新资产也不会画成缺图马赛克（见
 * {@link AkaishiCodexRender#texturePresent}）。
 *
 * <p><b>四档视觉状态</b>（三态之上多一档"隐藏研究"）：
 * <ul>
 *   <li>{@link Mark#LEARNED} 已读 = <b>亮</b>：外圈淡光 + 实心亮框；</li>
 *   <li>{@link Mark#AVAILABLE} 可研究 = <b>闪</b>：手绘圈按 {@link #PULSE_PERIOD_MS} 呼吸；</li>
 *   <li>{@link Mark#HIDDEN} 隐藏研究 = <b>偏黄</b>：暖黄罩 + 黄框（不呼吸，与"闪"区分；
 *       判定见 {@link #mark}）；</li>
 *   <li>{@link Mark#LOCKED} 被挡 = <b>暗</b>：整块压暗罩（前置未读完，见 {@code CodexNodeState}）。</li>
 * </ul>
 * 禁忌（{@code node.forbidden()}）与四档正交，叠一圈紫雾。
 */
final class AkaishiCodexEmblem {

    /** 族识别色（待调手感值；色相环上相隔约 120°，缩到小尺寸也不互相认错） */
    static final int COLOR_HUSH = 0xFF3E8E9E;
    static final int COLOR_GAZE = 0xFF7E56A8;
    static final int COLOR_BLOOD = 0xFFA8434E;

    /** 可研究亮圈的呼吸周期（毫秒；待调手感值） */
    static final long PULSE_PERIOD_MS = 1400L;

    // ===== 徽记几何与笔触（待调手感值） =====

    /** 贴图/图标相对徽记外框的内缩（留出"框"的边，读成徽章而不是纯贴图） */
    private static final int INSET = 4;
    /** 投影偏移 */
    private static final int SHADOW_OFFSET = 2;
    private static final int SCRAP_SHADOW = 0x3A1A1208;
    /** 纸片底（比羊皮纸略亮略黄，才有"另贴一张纸"的层次） */
    private static final int SCRAP_PAPER = 0xFFEDE0BE;
    private static final int SCRAP_EDGE = 0xFFB09C72;
    /** 层级形状：切角边长、尖刺长 */
    private static final int CHAMFER = 6;
    private static final int SPIKE = 4;

    // ===== 状态叠加配色（待调手感值） =====

    /** 被挡（沉睡）的暗罩（暖灰，把图标压暗降饱和） */
    private static final int LOCK_VEIL = 0xA8463F35;
    /** 隐藏研究的暖黄罩与黄框（TC 语汇的 Hidden research；不用红是因为红在纸面上等于"报错"） */
    private static final int HIDDEN_VEIL = 0x46B08A20;
    private static final int HIDDEN_RING = 0xE8E0C05A;
    /** 禁忌紫雾（外深内浅由 {@code AkaishiCodexBookArt.fume} 内部处理） */
    private static final int FORBIDDEN_FUME = 0xC46B3E9C;
    /** 已读亮框（实心亮色环 + 外圈一圈淡光，读作"亮"） */
    private static final int LEARNED_RING = 0xF0FFF3D6;
    /** 悬停提亮 */
    private static final int HOVER_VEIL = 0x28FFFFFF;
    /** 内部图案回落到物品图标时的程序化描边（族色，半透明） */
    private static final int OUTLINE_ALPHA = 0x88;
    /** 徽记名颜色：已读绿 / 可研究深墨 / 隐藏黄棕 / 被挡淡墨；禁忌一律偏红紫 */
    private static final int NAME_LEARNED = 0xFF2E6B3A;
    private static final int NAME_AVAILABLE = 0xFF3A2E22;
    private static final int NAME_HIDDEN = 0xFF8A6A16;
    private static final int NAME_LOCKED = 0xFF7A6A56;
    private static final int NAME_FORBIDDEN = 0xFF8A3C57;

    private AkaishiCodexEmblem() {
    }

    /**
     * 徽记的视觉状态：{@link CodexNodeState} 的三态 + 一档"隐藏研究"。
     *
     * <p><b>为什么不改 {@link CodexNodeState}</b>：那是机制层（前置判定）的真源，
     * 硬约束要求本轮不动它。"隐藏研究"纯粹是<b>观感分档</b>（条件太多、一条都没凑齐，
     * 还看不出这一页是什么），故只存在于渲染层。
     */
    enum Mark {
        /** 已读完 */
        LEARNED,
        /** 可以研究（前置已读完，条件也差不多齐了） */
        AVAILABLE,
        /** 隐藏研究：前置已读完，但这一阶的目标条数 ≥2 且一条都还没满足 ⇒ 偏黄 */
        HIDDEN,
        /** 被挡：前置节点还没读完 ⇒ 压暗 */
        LOCKED
    }

    /**
     * 判定一档视觉状态。
     *
     * <p><b>口径（本轮定）</b>：
     * <ol>
     *   <li>已学 → {@link Mark#LEARNED}；</li>
     *   <li>前置未读完（{@link CodexNodeState#LOCKED}）→ {@link Mark#LOCKED}：被挡优先，
     *       与"条件太多"是两回事，暗色与偏黄因此天然分开；</li>
     *   <li>前置已读完，且快照里<b>目标条数 ≥ {@link #HIDDEN_MIN_CONDITIONS} 且一条都没满足</b>
     *       → {@link Mark#HIDDEN}（隐藏研究）；</li>
     *   <li>其余 → {@link Mark#AVAILABLE}。</li>
     * </ol>
     * 快照未到（view == null）时按最保守的 {@link Mark#LOCKED}（不把没资格的节点画亮）。
     */
    static Mark mark(CodexNodeState state, @Nullable AkaishiCodexSync.NodeView view) {
        if (state == CodexNodeState.LEARNED) {
            return Mark.LEARNED;
        }
        if (state == CodexNodeState.LOCKED || view == null) {
            return Mark.LOCKED;
        }
        return hidden(view) ? Mark.HIDDEN : Mark.AVAILABLE;
    }

    /** 隐藏研究的判据：条数够多、却一条都没成全（读取端只读快照，不做任何机制判断） */
    private static boolean hidden(AkaishiCodexSync.NodeView view) {
        if (view.conditions().size() < HIDDEN_MIN_CONDITIONS) {
            return false;
        }
        for (var condition : view.conditions()) {
            if (condition.satisfied()) {
                return false;
            }
        }
        return true;
    }

    /** 触发"隐藏研究"的最小条数（待调手感值） */
    private static final int HIDDEN_MIN_CONDITIONS = 2;

    /**
     * 画一个徽记：禁忌紫雾 → 投影 → 层级纸片（撕边）→ 内部图案（贴图优先 / 图标回落）
     * → 悬停提亮 → 状态叠加（暗罩抬到物品层之上）。
     *
     * @param area    徽记外框（正方形）
     * @param node    节点（提供层级、族、禁忌标记与贴图路径）
     * @param mark    四档视觉状态
     * @param icon    节点图标（画布缓存的物品栈；贴图存在时不使用）
     * @param pulse   可研究呼吸透明度（{@link AkaishiCodexRender#pulse()}）
     */
    static void emblem(GuiGraphics gui, AkaishiCodexRender.Area area, CodexNode node, Mark mark,
                       ItemStack icon, int pulse, boolean hovered) {
        int accent = AkaishiCodexRender.familyColor(node.family());
        int seed = AkaishiCodexBookArt.seed("emblem", node.id().toString());
        int cx = area.cx();
        int cy = area.cy();
        // 1) 禁忌紫雾：在纸片之下、纸片之外，不遮图案
        if (node.forbidden()) {
            AkaishiCodexBookArt.fume(gui, cx, cy, area.w() / 2 + 3, FORBIDDEN_FUME, seed + 1);
        }
        // 2) 投影：贴上去的徽记有厚度
        gui.fill(area.x() + SHADOW_OFFSET, area.y() + SHADOW_OFFSET,
                area.right() + SHADOW_OFFSET, area.bottom() + SHADOW_OFFSET, SCRAP_SHADOW);
        // 3) 外框：形状按层级、撕边让它像手贴上去的
        plate(gui, area, node.tier(), SCRAP_PAPER);
        tornEdge(gui, area, seed);
        // 4) 内部图案：贴图优先（32×32 原尺寸贴，1:1 不糊）；没有贴图才回落"描边 + 物品图标"
        ResourceLocation tex = texture(node);
        if (AkaishiCodexRender.texturePresent(tex)) {
            gui.blit(tex, area.x(), area.y(), area.w(), area.h(),
                    0f, 0f, AkaishiCodexRender.TEX_EMBLEM_SIZE, AkaishiCodexRender.TEX_EMBLEM_SIZE,
                    AkaishiCodexRender.TEX_EMBLEM_SIZE, AkaishiCodexRender.TEX_EMBLEM_SIZE);
        } else {
            AkaishiCodexRender.Area inner = new AkaishiCodexRender.Area(
                    area.x() + INSET, area.y() + INSET, area.w() - 2 * INSET, area.h() - 2 * INSET);
            AkaishiCodexBookArt.ring(gui, inner.x(), inner.y(), inner.w(), inner.h(),
                    AkaishiCodexBookArt.semi(accent, OUTLINE_ALPHA));
            if (icon != null && !icon.isEmpty()) {
                // 物品图标按 16×16 原尺寸居中画：<b>不做 pose 缩放</b>，
                // 上一版是 24/16 = 1.5 倍缩放，非整数倍率同样会让图标发糊
                gui.renderItem(icon, area.cx() - 8, area.cy() - 8);
            }
        }
        if (hovered) {
            gui.fill(area.x(), area.y(), area.right(), area.bottom(), HOVER_VEIL);
        }
        // 5) 状态叠加
        overlay(gui, area, cx, cy, accent, mark, pulse, seed);
    }

    /** 状态叠加：暗罩必须抬到物品层之上（{@code renderItem} 会把 z 抬到物品层，留底层等于白画） */
    private static void overlay(GuiGraphics gui, AkaishiCodexRender.Area area, int cx, int cy,
                                int accent, Mark mark, int pulse, int seed) {
        switch (mark) {
            case LOCKED -> {
                gui.pose().pushPose();
                gui.pose().translate(0f, 0f, 300f);
                gui.fill(area.x(), area.y(), area.right(), area.bottom(), LOCK_VEIL);
                gui.pose().popPose();
            }
            case HIDDEN -> {
                gui.pose().pushPose();
                gui.pose().translate(0f, 0f, 300f);
                gui.fill(area.x(), area.y(), area.right(), area.bottom(), HIDDEN_VEIL);
                gui.pose().popPose();
                AkaishiCodexBookArt.ring(gui, area.x() - 2, area.y() - 2, area.w() + 4, area.h() + 4,
                        HIDDEN_RING);
            }
            case AVAILABLE -> AkaishiCodexBookArt.sketchRing(gui, cx, cy,
                    area.w() / 2 + 3, area.h() / 2 + 3, 26,
                    AkaishiCodexBookArt.semi(accent, pulse), seed + 3);
            default -> {
                AkaishiCodexBookArt.ring(gui, area.x() - 3, area.y() - 3, area.w() + 6, area.h() + 6,
                        AkaishiCodexBookArt.semi(accent, 0x50));
                AkaishiCodexBookArt.ring(gui, area.x() - 2, area.y() - 2, area.w() + 4, area.h() + 4,
                        LEARNED_RING);
            }
        }
    }

    /** 徽记贴图路径：{@code forbidden_hex:textures/gui/codex/emblem/<节点路径>.png} */
    static ResourceLocation texture(CodexNode node) {
        return new ResourceLocation(node.id().getNamespace(),
                AkaishiCodexRender.TEX_EMBLEM_DIR + node.id().getPath() + ".png");
    }

    /** 徽记名颜色（四档 + 禁忌） */
    static int nameColor(Mark mark, boolean forbidden) {
        if (forbidden) {
            return NAME_FORBIDDEN;
        }
        return switch (mark) {
            case LEARNED -> NAME_LEARNED;
            case AVAILABLE -> NAME_AVAILABLE;
            case HIDDEN -> NAME_HIDDEN;
            default -> NAME_LOCKED;
        };
    }

    /** 状态文案键（隐藏研究是与"可研究"并列的一档，故单独一条键） */
    static String stateKey(Mark mark) {
        return switch (mark) {
            case LEARNED -> "gui.forbidden_hex.codex.state.learned";
            case AVAILABLE -> "gui.forbidden_hex.codex.state.available";
            case HIDDEN -> "gui.forbidden_hex.codex.state.hidden";
            default -> "gui.forbidden_hex.codex.state.locked";
        };
    }

    // ===== 笔触：层级形状 / 撕边（原本在画布里，本轮收进徽记） =====

    /** 外框形状 = 知识层级：简单＝直角、疯狂＝切角、禁忌＝带刺 */
    private static void plate(GuiGraphics gui, AkaishiCodexRender.Area area, CodexTier tier, int color) {
        int x = area.x();
        int y = area.y();
        int size = area.w();
        if (tier == CodexTier.MAD) {
            int cut = Math.min(CHAMFER, size / 3);
            gui.fill(x + cut, y, x + size - cut, y + size, color);
            for (int i = 1; i < cut; i++) {
                gui.fill(x + cut - i, y + i, x + cut, y + size - i, color);
                gui.fill(x + size - cut, y + i, x + size - cut + i, y + size - i, color);
            }
            return;
        }
        gui.fill(x, y, x + size, y + size, color);
        if (tier == CodexTier.FORBIDDEN) {
            int mid = size / 2;
            spike(gui, x + mid, y, 0, -1, color);
            spike(gui, x + mid, y + size, 0, 1, color);
            spike(gui, x, y + mid, -1, 0, color);
            spike(gui, x + size, y + mid, 1, 0, color);
        }
    }

    /** 一根尖刺（从外框边缘向外的小三角；{@code (dx, dy)} 为朝外方向） */
    private static void spike(GuiGraphics gui, int x, int y, int dx, int dy, int color) {
        for (int i = 0; i < SPIKE; i++) {
            int half = SPIKE - i;
            int px = x + dx * i;
            int py = y + dy * i;
            if (dx != 0) {
                gui.fill(px, py - half, px + 1, py + half + 1, color);
            } else {
                gui.fill(px - half, py, px + half + 1, py + 1, color);
            }
        }
    }

    /** 撕边：四条边按散列逐像素错位 0~1px，用暗边色勾出毛边 */
    private static void tornEdge(GuiGraphics gui, AkaishiCodexRender.Area area, int seed) {
        int x = area.x();
        int y = area.y();
        int size = area.w();
        for (int i = 0; i < size; i++) {
            int top = (int) Math.floorMod(AkaishiCodexBookArt.noise(seed, 0x10 + i), 2L);
            gui.fill(x + i, y + top, x + i + 1, y + top + 1, SCRAP_EDGE);
            int bottom = 1 + (int) Math.floorMod(AkaishiCodexBookArt.noise(seed, 0x40 + i), 2L);
            gui.fill(x + i, y + size - bottom, x + i + 1, y + size - bottom + 1, SCRAP_EDGE);
            int left = (int) Math.floorMod(AkaishiCodexBookArt.noise(seed, 0x70 + i), 2L);
            gui.fill(x + left, y + i, x + left + 1, y + i + 1, SCRAP_EDGE);
            int right = 1 + (int) Math.floorMod(AkaishiCodexBookArt.noise(seed, 0xA0 + i), 2L);
            gui.fill(x + size - right, y + i, x + size - right + 1, y + i + 1, SCRAP_EDGE);
        }
    }
}
