package com.example.akaishi.forbidden.brewing.client;

import com.example.akaishi.forbidden.brewing.CrucibleInventory;
import com.example.akaishi.forbidden.brewing.CrucibleMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public final class CrucibleScreen extends AbstractContainerScreen<CrucibleMenu> {
    private Button brewTab, craftTab, brew, recover;

    public CrucibleScreen(CrucibleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 230;
        imageHeight = 240;
        inventoryLabelX = 35;
        inventoryLabelY = 150;
        titleLabelX = 12;
    }

    @Override protected void init() {
        super.init();
        brewTab = addRenderableWidget(Button.builder(text("brew_mode"), button -> send(0))
                .bounds(leftPos + 12, topPos + 17, 88, 17).build());
        craftTab = addRenderableWidget(Button.builder(text("craft_mode"), button -> send(1))
                .bounds(leftPos + 104, topPos + 17, 88, 17).build());
        brew = addRenderableWidget(Button.builder(text("start"), button -> send(2))
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(text("delivery")))
                .bounds(leftPos + 122, topPos + 128, 96, 18).build());
        recover = addRenderableWidget(Button.builder(text("recover"), button -> send(3))
                .bounds(leftPos + 12, topPos + 128, 104, 18).build());
        updateButtons();
    }

    private static Component text(String key) { return Component.translatable("gui.forbidden_hex.crucible." + key); }
    private void send(int button) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }
    private void updateButtons() {
        brewTab.active = menu.mode() != 0 && menu.canSwitch();
        craftTab.active = menu.mode() != 1 && menu.canSwitch();
        brewTab.setMessage(text(menu.mode() == 0 ? "brew_selected" : "brew_mode"));
        craftTab.setMessage(text(menu.mode() == 1 ? "craft_selected" : "craft_mode"));
        brew.active = menu.status() == CrucibleInventory.READY || menu.status() == CrucibleInventory.LEGACY;
        recover.visible = menu.status() == CrucibleInventory.LEGACY;
        recover.active = recover.visible && menu.canRecover();
    }
    @Override protected void containerTick() { super.containerTick(); updateButtons(); }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (hoveredSlot != null && hoveredSlot.index >= CrucibleMenu.PREVIEW_START && hoveredSlot.index < CrucibleMenu.PLAYER_START) {
            if (!hoveredSlot.hasItem()) graphics.renderTooltip(font, text("preview_only"), mouseX, mouseY);
        } else if (hoveredSlot != null && hoveredSlot.index < CrucibleMenu.PREVIEW_START && !menu.getCarried().isEmpty()
                && menu.editable() && !hoveredSlot.mayPlace(menu.getCarried())) {
            graphics.renderTooltip(font, text("rejected"), mouseX, mouseY);
        } else if (mouseX >= leftPos + 122 && mouseX < leftPos + 220 && mouseY >= topPos + 107 && mouseY < topPos + 126) {
            graphics.renderTooltip(font, Component.translatable("gui.forbidden_hex.crucible.status." + menu.status()), mouseX, mouseY);
        }
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFF211F28);
        graphics.fill(x + 1, y + 1, x + imageWidth - 1, y + 3, 0xFF77717A);
        graphics.fill(x + 1, y + 3, x + imageWidth - 1, y + 5, 0xFF56505A);
        graphics.fill(x + 1, y + imageHeight - 3, x + imageWidth - 1, y + imageHeight - 1, 0xFF15141A);
        graphics.fill(x + 8, y + 148, x + imageWidth - 8, y + 149, 0xFF56505A);
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (!slot.isActive()) continue;
            int sx = x + slot.x, sy = y + slot.y;
            boolean preview = i >= CrucibleMenu.PREVIEW_START && i < CrucibleMenu.PLAYER_START;
            graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, preview ? 0xFF56505A : 0xFF131219);
            graphics.fill(sx, sy, sx + 16, sy + 16, preview ? 0xFF35313C : 0xFF39353F);
            if (!preview) graphics.fill(sx, sy + 16, sx + 17, sy + 17, 0xFF77717A);
        }
        graphics.drawString(font, Component.translatable("gui.forbidden_hex.crucible.water", menu.water()), x + 122, y + 44, 0xFFC5B796, false);
        graphics.drawString(font, text("preview"), x + 160, y + 51, 0xFFD9D0BD, false);
        graphics.drawString(font, text("bottles"), x + 115, y + 69, 0xFFC5B796, false);
        graphics.fill(x + 122, y + 102, x + 218, y + 105, 0xFF131219);
        graphics.fill(x + 122, y + 102, x + 122 + Math.min(96, menu.progress() * 96 / 100), y + 105, 0xFFB2ACA0);
        Component status = Component.translatable("gui.forbidden_hex.crucible.status." + menu.status());
        var lines = font.split(status, 98);
        for (int line = 0; line < Math.min(2, lines.size()); line++) {
            graphics.drawString(font, lines.get(line), x + 122, y + 108 + line * 9, 0xFFD9D0BD, false);
        }
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xFFE0D7C3, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFC5B796, false);
    }
}
