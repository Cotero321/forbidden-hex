package com.example.akaishi.forbidden.forge.familiar;

import com.example.akaishi.forbidden.familiar.workbench.FamiliarWorkbenchMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Lightweight client screen for the seven-slot familiar workbench. */
public final class FamiliarWorkbenchScreen extends AbstractContainerScreen<FamiliarWorkbenchMenu> {
    public FamiliarWorkbenchScreen(FamiliarWorkbenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 190;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFF211A24);
        graphics.fill(x + 8, y + 8, x + imageWidth - 8, y + 82, 0xFF352A38);
        for (int index = 0; index < menu.slots.size(); index++) {
            var slot = menu.slots.get(index);
            if (!slot.isActive() || index >= 8) continue;
            graphics.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, 0xFF0E0B10);
            graphics.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, 0xFF8C7A91);
        }
        int barWidth = 116;
        graphics.fill(x + 30, y + 84, x + 30 + barWidth, y + 90, 0xFF0E0B10);
        if (menu.duration() > 0) {
            int filled = Math.min(barWidth, barWidth * menu.progress() / menu.duration());
            graphics.fill(x + 31, y + 85, x + 31 + filled, y + 89, 0xFFD6A85E);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 6, 0xFFE8DCC8, false);
        graphics.drawString(font, Component.translatable("gui.forbidden_hex.familiar_workbench.status." + menu.status()),
                8, 92, 0xFFC9B8A0, false);
        graphics.drawString(font, Component.translatable("container.inventory"), inventoryLabelX, inventoryLabelY, 0xFFE8DCC8, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
