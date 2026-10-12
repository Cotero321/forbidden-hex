package com.example.akaishi.forbidden.brewing.client;

import com.example.akaishi.forbidden.brewing.WitchOvenMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class WitchOvenScreen extends AbstractContainerScreen<WitchOvenMenu> {
    public WitchOvenScreen(WitchOvenMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 176;
        inventoryLabelY = 82;
    }
    @Override protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        gui.fill(x, y, x + imageWidth, y + imageHeight, 0xFF362F3E);
        gui.fill(x + 4, y + 4, x + imageWidth - 4, y + imageHeight - 4, 0xFFDAD4C6);
        for (var slot : menu.slots) {
            gui.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, 0xFF625865);
            gui.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, 0xFFB2A8B7);
        }
        gui.fill(x + 62, y + 34, x + 95, y + 41, 0xFF625865);
        if (menu.total() > 0) gui.fill(x + 63, y + 35,
                x + 63 + Math.min(31, 31 * menu.progress() / menu.total()), y + 40, 0xFF976E38);
        if (menu.burnTotal() > 0) gui.fill(x + 57, y + 69 - (int) Math.min(14, 14L * menu.burn() / menu.burnTotal()), x + 62, y + 69, 0xFFE39738);
        if (isHovering(62, 34, 33, 7, mouseX, mouseY)) gui.renderTooltip(font,
                Component.translatable("gui.forbidden_hex.brewing.profile", menu.queues(), menu.fumeChancePercent()), mouseX, mouseY);
        gui.drawString(font, Component.translatable("gui.forbidden_hex.brewing.result"), x + 108, y + 19, 0xFF493C52, false);
        gui.drawString(font, Component.translatable("gui.forbidden_hex.brewing.fume"), x + 138, y + 19, 0xFF493C52, false);
    }
    @Override public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        renderBackground(gui);
        super.render(gui, mouseX, mouseY, partialTick);
        renderTooltip(gui, mouseX, mouseY);
    }
}
