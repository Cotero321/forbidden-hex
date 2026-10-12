package com.example.akaishi.forbidden.menu;
import com.example.akaishi.menu.EnergyFormat;
import com.example.akaishi.menu.GuiWidgets;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 合并母神祭坛界面：全自绘（不依赖任何 GUI 贴图），与手术仓/机械三机共用 {@link GuiWidgets} 的原版灰面板风格。
 * <p>布局自上而下居中：标题 → 结构等级 → 供奉槽 → 供奉提示 → 仪式进度 → 玩家背包。
 * <p>进度条与能量数值仅在"祭品齐备（真正在合成）"时绘制：中心供奉槽为空或祭品不齐一律不显示。
 */
public class AkaishiMotherAltarScreen extends AbstractContainerScreen<AkaishiMotherAltarMenu> {

    /** 面板尺寸（与原版容器界面一致） */
    private static final int PANEL_W = 176;
    private static final int PANEL_H = 166;

    /** 等级文本基线（标题下方居中） */
    private static final int TIER_TEXT_Y = 18;
    /** 供奉槽提示文本基线（槽下方居中） */
    private static final int HINT_TEXT_Y = 48;
    /** 仪式进度条（居中，与供奉槽同列） */
    private static final int BAR_X = 54;
    private static final int BAR_Y = 60;
    private static final int BAR_W = 68;
    private static final int BAR_H = 8;
    /** 进度数值文本基线（进度条正下方居中） */
    private static final int PROGRESS_TEXT_Y = 72;
    /** 界面水平中心 */
    private static final int CENTER_X = PANEL_W / 2;
    /** 玩家背包 / 快捷栏 y（须与 {@link AkaishiMotherAltarMenu} 的槽位定义严格一致） */
    private static final int INV_Y = 84;
    private static final int HOTBAR_Y = 142;
    /** 仪式进度填充色（黑山羊体系识别色：紫） */
    private static final int PROGRESS_BAR_COLOR = 0xFF9C27B0;

    public AkaishiMotherAltarScreen(AkaishiMotherAltarMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = PANEL_W;
        this.imageHeight = PANEL_H;
    }

    @Override
    protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        GuiWidgets.panel(gui, x, y, PANEL_W, PANEL_H);
        // 供奉槽背景框（原先依赖 GUI 贴图预留槽位，贴图缺失导致不显示，改为直接自绘）
        GuiWidgets.slotBox(gui, x + AkaishiMotherAltarMenu.OFFERING_SLOT_X, y + AkaishiMotherAltarMenu.OFFERING_SLOT_Y);
        // 玩家背包 3×9 + 快捷栏：坐标取自 Menu 槽位定义，保证槽框与实际可交互槽位对齐
        GuiWidgets.playerInventory(gui, x, y, INV_Y, HOTBAR_Y);

        // 仅祭品齐备（真正在合成）时才展示所需能量：中心槽为空或祭品不齐一律不绘制
        if (menu.isRecipeReady()) {
            GuiWidgets.track(gui, x + BAR_X, y + BAR_Y, BAR_W, BAR_H);
            GuiWidgets.bar(gui, x + BAR_X, y + BAR_Y, BAR_W, BAR_H,
                    menu.getProgress(), menu.getProgressMax(), PROGRESS_BAR_COLOR);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics gui, int mouseX, int mouseY) {
        // 仅绘制标题，抑制原版"物品栏"标签避免与等级文本重叠
        gui.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFF3F3F3F, false);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gui);
        super.render(gui, mouseX, mouseY, partialTick);

        // 当前结构等级（居中）
        Component tierText = Component.translatable("gui.forbidden_hex.altar.tier", menu.getTier());
        gui.drawString(this.font, tierText, this.leftPos + CENTER_X - this.font.width(tierText) / 2,
                this.topPos + TIER_TEXT_Y, 0xFF8B2A2A, false);

        // 供奉槽提示（居中）
        Component hint = Component.translatable("gui.forbidden_hex.altar.slot");
        gui.drawString(this.font, hint, this.leftPos + CENTER_X - this.font.width(hint) / 2,
                this.topPos + HINT_TEXT_Y, 0xFF3F3F3F, false);

        // 仪式进度数值（仅齐备时显示，居中于进度条下方）
        if (menu.isRecipeReady()) {
            Component progressText = Component.translatable("gui.forbidden_hex.altar.progress.short")
                    .append(" " + EnergyFormat.format(menu.getProgress())
                            + " / " + EnergyFormat.format(menu.getProgressMax()));
            gui.drawString(this.font, progressText, this.leftPos + CENTER_X - this.font.width(progressText) / 2,
                    this.topPos + PROGRESS_TEXT_Y, 0xFF6A1B7A, false);
        }

        // 悬浮提示置于所有文本之上，最后绘制
        this.renderTooltip(gui, mouseX, mouseY);
    }

    @Override
    protected void renderTooltip(GuiGraphics gui, int mouseX, int mouseY) {
        // 原版仅负责"槽内有物品"时的物品名提示
        super.renderTooltip(gui, mouseX, mouseY);

        // 供奉槽为空时才提示槽位语义；已有物品时再叠加会与物品名重叠
        if (menu.slots.get(0).getItem().isEmpty()
                && isHovering(AkaishiMotherAltarMenu.OFFERING_SLOT_X, AkaishiMotherAltarMenu.OFFERING_SLOT_Y, 18, 18, mouseX, mouseY)) {
            gui.renderTooltip(this.font, Component.translatable("gui.forbidden_hex.altar.slot_tip"), mouseX, mouseY);
            return;
        }

        // 悬停进度条时显示完整数值（仅齐备时进度条存在）
        if (menu.isRecipeReady() && isHovering(BAR_X, BAR_Y, BAR_W, BAR_H, mouseX, mouseY)) {
            gui.renderTooltip(this.font,
                    Component.translatable("gui.forbidden_hex.altar.progress",
                            EnergyFormat.format(menu.getProgress()),
                            EnergyFormat.format(menu.getProgressMax())),
                    mouseX, mouseY);
        }
    }
}
