package com.example.akaishi.menu;

import com.example.akaishi.AkaishiMod;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.datafixers.util.Pair;
import org.joml.Matrix4f;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * 界面自绘控件工具：统一槽位框/按钮的绘制样式，供各 Screen 复用。
 * 仅客户端渲染代码调用，颜色与既有自绘界面（手术仓/药剂台）保持一致。
 */
public final class GuiWidgets {

    /** 原版容器贴图：其 (7,83) 处即标准 18×18 槽位图案（暗边 373737 / 体色 8B8B8B / 亮边 FFFFFF，全不透明） */
    private static final ResourceLocation VANILLA_SLOT_TEXTURE =
            new ResourceLocation("minecraft", "textures/gui/container/inventory.png");
    private static final int VANILLA_SLOT_U = 7;
    private static final int VANILLA_SLOT_V = 83;

    private static final int COLOR_SLOT = 0xFF8B8B8B;
    private static final int COLOR_SLOT_DARK = 0xFF373737;
    private static final int COLOR_SLOT_LIGHT = 0xFFFFFFFF;
    private static final int COLOR_PANEL = 0xFFC6C6C6;
    /** 输入框底槽：中深灰（与滚动条把柄同调），白字落上去对比足；不是纯黑，不会在浅色面板上跳出来 */
    private static final int COLOR_INPUT = 0xFF6E6E6E;
    private static final int COLOR_PROGRESS = 0xFFFFD030;
    /**
     * 「空图标」：槽位在原版渲染那一趟被伪装成空槽后（见各 Menu 的 {@code vanillaRenderPass}），
     * 原版会走 {@code getNoItemIcon} 分支画这张图 —— 全透明 ⇒ 屏幕上什么都不画。
     * <p>
     * 所以物品图标与数量全部由界面自绘（见各 Screen 的 {@code drawGrid}）。
     * <b>注意</b>：1.20.1 的 {@code AbstractContainerScreen#renderSlot} 只在槽里没有物品时才走该分支，
     * 槽有物品时原版一定会 {@code renderItem}，光靠它是挡不住的。
     */
    public static final Pair<ResourceLocation, ResourceLocation> BLANK_SLOT_ICON =
            Pair.of(InventoryMenu.BLOCK_ATLAS, new ResourceLocation(AkaishiMod.MOD_ID, "item/blank"));

    /**
     * 槽位数量标签：显示按类聚合后的<b>真实总量</b>（可远超单堆上限 64，按 K/M 缩写）。
     * <p>
     * 取自 AE2 1.20.1 {@code StackSizeRenderer} 的<b>默认档</b>：{@code scaleFactor = 0.5f}、
     * {@code offset = -1}，z 抬 200 后用 {@code MultiBufferSource.immediate} + {@code endBatch} 立即出图。
     * 调用前调用方必须已 {@code gui.flush()}：原版 GUI 与即时缓冲共用 {@code Tesselator} 的同一个
     * {@code BufferBuilder}，不先刷会丢掉界面待画几何。
     * <p>
     * 与 AE2 的两点差异都是实测踩出来的：
     * <ul>
     *   <li><b>必须关深度测试</b>：AE2 的 z=200 能压住物品，是因为它的数量文字与物品在同一 pose/深度
     *       上下文里画；本项目这趟在槽位渲染之外，z 不在同一空间，开着深度测试会被物品整片剔掉；</li>
     *   <li><b>四向薄描边</b>代替原版阴影：0.5 档字形只有 3~4 逻辑像素高，原版阴影只偏半个逻辑像素，
     *       压在浅色贴图（铁块 / 创造元件）与槽位白描边上白字会直接"消失"。
     *       描边偏移 ±1（缩放空间单位 = 0.5 逻辑像素 = 半个笔画宽）：只吃半个笔画、留出白芯，
     *       不会像早期 ±2（整条笔画宽）那样把字糊成黑点。</li>
     * </ul>
     */
    public static void amountLabel(GuiGraphics gui, Font font, int slotX, int slotY, String text) {
        final float scale = 0.5f;
        final float inverse = 1.0f / scale;
        final int offset = -1;
        int x = (int) ((slotX + offset + 16.0f - font.width(text) * scale) * inverse);
        int y = (int) ((slotY + offset + 16.0f - 7.0f * scale) * inverse);
        gui.pose().pushPose();
        gui.pose().translate(0.0f, 0.0f, 200.0f);
        gui.pose().scale(scale, scale, scale);
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        MultiBufferSource.BufferSource buffer =
                MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
        Matrix4f matrix = gui.pose().last().pose();
        for (int[] d : LABEL_OUTLINE) {
            font.drawInBatch(text, x + d[0], y + d[1], 0xFF000000, false, matrix, buffer,
                    Font.DisplayMode.NORMAL, 0, 0xF000F0);
        }
        font.drawInBatch(text, x, y, 0xFFFFFF, false, matrix, buffer,
                Font.DisplayMode.NORMAL, 0, 0xF000F0);
        buffer.endBatch();
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        gui.pose().popPose();
    }

    private GuiWidgets() {
    }

    /** 数量标签薄描边偏移（缩放空间单位，1 单位 ≈ 一个笔画宽）：上、下、左、右 */
    private static final int[][] LABEL_OUTLINE = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};

    /** 绘制不透明原版风格背景面板（灰底 + 四周内凹边框），解决无贴图界面的透明背景问题 */
    public static void panel(GuiGraphics gui, int x, int y, int w, int h) {
        gui.fill(x, y, x + w, y + h, COLOR_PANEL);
        gui.fill(x, y, x + w, y + 1, COLOR_SLOT_DARK);
        gui.fill(x, y, x + 1, y + h, COLOR_SLOT_DARK);
        gui.fill(x, y + h - 1, x + w, y + h, COLOR_SLOT_LIGHT);
        gui.fill(x + w - 1, y, x + w, y + h, COLOR_SLOT_LIGHT);
    }

    /** 绘制玩家背包 3×9 + 快捷栏 1×9 槽位框（机械三机布局：背包起点 y=104，快捷栏 y=168） */
    public static void playerInventory(GuiGraphics gui, int x, int y) {
        playerInventory(gui, x, y, 104, 168);
    }

    /**
     * 绘制玩家背包 3×9 + 快捷栏 1×9 槽位框，坐标由调用方按其 Menu 的槽位定义传入，
     * 保证槽框与实际可交互槽位严格对齐（各界面背包/快捷栏 y 并不一致）。
     *
     * @param invTop 背包首行 y（相对 GUI 原点）
     * @param hotbar 快捷栏 y（相对 GUI 原点）
     */
    public static void playerInventory(GuiGraphics gui, int x, int y, int invTop, int hotbar) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slotBox(gui, x + 8 + col * 18, y + invTop + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            slotBox(gui, x + 8 + col * 18, y + hotbar);
        }
    }

    /**
     * 绘制 18×18 原版槽位框。传入坐标为 Menu 槽位的 {@code (slot.x, slot.y)}（即 16×16 物品区左上角），
     * 原版槽框固定在 {@code (slot.x-1, slot.y-1)} —— 直接引用原版贴图图案，保证像素级与原版一致。
     */
    public static void slotBox(GuiGraphics gui, int x, int y) {
        gui.blit(VANILLA_SLOT_TEXTURE, x - 1, y - 1, VANILLA_SLOT_U, VANILLA_SLOT_V, 18, 18);
    }

    /** 绘制输入框底槽（浅色内凹）：与面板同族配色，供文字为深色的输入框使用 */
    public static void inputWell(GuiGraphics gui, int x, int y, int w, int h) {
        gui.fill(x, y, x + w, y + h, COLOR_INPUT);
        gui.fill(x, y, x + w, y + 1, COLOR_SLOT_DARK);
        gui.fill(x, y, x + 1, y + h, COLOR_SLOT_DARK);
        gui.fill(x, y + h - 1, x + w, y + h, COLOR_SLOT_LIGHT);
        gui.fill(x + w - 1, y, x + w, y + h, COLOR_SLOT_LIGHT);
    }

    /** 绘制原版风格轨道框（能量/液体/进度条底槽，内凹样式） */
    public static void track(GuiGraphics gui, int x, int y, int w, int h) {
        gui.fill(x, y, x + w, y + h, COLOR_SLOT);
        gui.fill(x, y, x + w, y + 1, COLOR_SLOT_DARK);
        gui.fill(x, y, x + 1, y + h, COLOR_SLOT_DARK);
        gui.fill(x, y + h - 1, x + w, y + h, COLOR_SLOT_LIGHT);
        gui.fill(x + w - 1, y, x + w, y + h, COLOR_SLOT_LIGHT);
    }

    /**
     * 在轨道内绘制填充进度条：自动把 value 钳制到 [0,max]，按轨道宽 w 等比例填充。
     * 末段不足 1px 时不绘制，保证与既有自绘进度条视觉一致。
     */
    public static void bar(GuiGraphics gui, int x, int y, int w, int h, long value, long max, int color) {
        long clamped = Math.max(0, Math.min(value, max));
        long cap = Math.max(1, max);
        int barWidth = (int) (w * clamped / cap);
        if (barWidth > 0) {
            gui.fill(x, y, x + barWidth, y + h, color);
        }
    }

    /**
     * 绘制"箭头即进度"的右箭头：矩形箭头体 + 三角尖头，内部按 pct(0~100) 从左侧填充黄色进度，
     * 替代独立进度条；箭头方向指向输出槽，兼具方向与进度语义。坐标一律做越界钳制。
     */
    public static void progressArrow(GuiGraphics gui, int x, int y, int w, int h, float pct) {
        if (w < 3 || h < 3) {
            return;
        }
        // 尖头占 40% 宽，收窄区间足够长才能看出箭头形状
        int head = Math.max(2, w * 2 / 5);
        int bodyW = w - head;
        int mid = h / 2;
        float clamped = Math.max(0, Math.min(pct, 100));
        // 进度覆盖整支箭头（含尖头），左 -> 右填充
        int fill = (int) (w * clamped / 100f);

        // 逐列计算上下边界：箭头体为满高，尖头对称收窄至中线
        for (int i = 0; i < w; i++) {
            int top;
            int bottom;
            if (i < bodyW) {
                top = 0;
                bottom = h - 1;
            } else {
                int k = i - bodyW;                       // 0 .. head-1
                int half = Math.round((h / 2f) * (head - k) / (float) head) - 1;
                if (half < 0) {
                    half = 0;
                }
                top = mid - half;
                bottom = mid + half;
            }
            for (int j = top; j <= bottom; j++) {
                int c;
                if (j == top || j == bottom) {
                    c = COLOR_SLOT_DARK;                 // 上下描边勾出箭头轮廓
                } else if (i < fill) {
                    c = COLOR_PROGRESS;                  // 已完成：黄色
                } else {
                    c = COLOR_SLOT;                      // 未完成：浅灰
                }
                gui.fill(x + i, y + j, x + i + 1, y + j + 1, c);
            }
        }
    }

    /** 绘制 30×12 原版风格小按钮（内凹灰体），供频道切换等操作按钮复用 */
    public static void button(GuiGraphics gui, int x, int y) {
        button(gui, x, y, 30, 12);
    }

    /** 绘制指定尺寸的原版风格按钮（内凹灰体），供页面切换/操作按钮复用 */
    public static void button(GuiGraphics gui, int x, int y, int w, int h) {
        gui.fill(x, y, x + w, y + h, COLOR_SLOT);
        gui.fill(x, y, x + w, y + 1, COLOR_SLOT_DARK);
        gui.fill(x, y, x + 1, y + h, COLOR_SLOT_DARK);
        gui.fill(x, y + h - 1, x + w, y + h, COLOR_SLOT_LIGHT);
        gui.fill(x + w - 1, y, x + w, y + h, COLOR_SLOT_LIGHT);
    }

    /**
     * 绘制"下拉式选择"框体：灰底内凹框 + 右侧下箭头(chevron)，供模板厂选择器官/部件。
     */
    public static void dropdown(GuiGraphics gui, int x, int y, int w, int h) {
        gui.fill(x, y, x + w, y + h, COLOR_SLOT);
        gui.fill(x, y, x + w, y + 1, COLOR_SLOT_DARK);
        gui.fill(x, y, x + 1, y + h, COLOR_SLOT_DARK);
        gui.fill(x, y + h - 1, x + w, y + h, COLOR_SLOT_LIGHT);
        gui.fill(x + w - 1, y, x + w, y + h, COLOR_SLOT_LIGHT);
        // 右侧下箭头 chevron
        int cx = x + w - 9;
        int cy = y + 3;
        int color = 0xFF3F3F3F;
        for (int i = 0; i < 4; i++) {
            gui.fill(cx + i, cy + i, cx + i + 1, cy + i + 3, color);
        }
    }

    /**
     * 绘制带居中文本的按钮；enabled=false 时压暗整体并淡化文字（用于条件不满足时的置灰）。
     * 供机械三机"制作"按钮等操作按钮复用。
     */
    public static void buttonText(GuiGraphics gui, Font font, int x, int y, int w, int h,
                                  Component text, boolean enabled) {
        button(gui, x, y, w, h);
        if (!enabled) {
            // 禁用态：近不透明黑罩压暗按钮体（0x88000000 压暗不足，会被误认为仍可点），文字同步调暗
            gui.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xD8000000);
        }
        int tw = font.width(text);
        gui.drawString(font, text, x + (w - tw) / 2, y + (h - 8) / 2,
                enabled ? 0xFF202020 : 0xFF909090, false);
    }
}
