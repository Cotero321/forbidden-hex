package com.example.akaishi.forbidden.menu;

import com.example.akaishi.forbidden.altar.SourceAltarRules;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

public final class SourceAltarScreen extends AbstractContainerScreen<SourceAltarMenu> {
    private static final ResourceLocation BACKGROUND = new ResourceLocation("forbidden_hex", "textures/gui/source_altar.png");
    private static final int TEXT = 0xfff1e9d6;
    private static final int MUTED = 0xffb5c5c1;
    private static final int GREEN = 0xff65d6b0;
    private double displayedRatio;

    public SourceAltarScreen(SourceAltarMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 236;
        imageHeight = 194;
    }
    private static Component text(String name, Object... args) { return Component.translatable("source_altar.forbidden_hex." + name, args); }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        graphics.fill(leftPos + 18, topPos + 40, leftPos + 218, topPos + 54, 0xff101719);
        long capacity = menu.units(4);
        double ratio = capacity > 0 ? Math.min(1d, (double) menu.units(0) / capacity) : 0;
        displayedRatio += (ratio - displayedRatio) * Math.min(1, .12 + partialTick * .08);
        int width = (int) (198 * displayedRatio);
        if (width > 0) graphics.fillGradient(leftPos + 19, topPos + 41, leftPos + 19 + width, topPos + 53, 0xff79e0bb, 0xff267866);
        for (int i = 0; i < 4; i++) {
            int x = leftPos + 73 + i * 22;
            int color = (menu.value(16) & 1 << i) != 0 ? 0xffe4b968 : (menu.value(15) & 1 << i) != 0 ? GREEN : 0xff52615d;
            graphics.fill(x, topPos + 135, x + 18, topPos + 153, color);
            graphics.fill(x + 1, topPos + 136, x + 17, topPos + 152, 0xff171d20);
        }
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 18, 12, TEXT, false);
        Component status = text(menu.value(20) != 0 ? "ready" : "scanning");
        graphics.drawString(font, status, 218 - font.width(status), 13, menu.value(20) != 0 ? GREEN : 0xffe4b968, false);
        Component energy = text("energy", SourceAltarRules.format(menu.units(0)), SourceAltarRules.format(menu.units(4)));
        graphics.drawString(font, energy, 218 - font.width(energy), 28, TEXT, false);
        graphics.drawString(font, text("capacity_multiplier", SourceAltarRules.format((long) menu.value(12) * menu.value(13))), 18, 67, TEXT, false);
        graphics.drawString(font, text("recovery", SourceAltarRules.format(menu.units(8))), 18, 84, GREEN, false);
        graphics.drawString(font, text("range", SourceAltarRules.RADIUS, menu.value(18)), 18, 101, MUTED, false);
        graphics.drawString(font, text("scan", menu.value(19)), 145, 101, MUTED, false);
        graphics.drawString(font, text("offerings"), 18, 119, TEXT, false);
        Component help = text("controls");
        graphics.drawWordWrap(font, help, 18, 164, 200, MUTED);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        int x = mouseX - leftPos;
        int y = mouseY - topPos;
        if (x >= 18 && x <= 218 && y >= 62 && y <= 79) {
            graphics.renderComponentTooltip(font, List.of(text("head").copy().append(" ×" + menu.value(12)),
                    text("implement").copy().append(" ×" + SourceAltarRules.format(menu.value(13)))), mouseX, mouseY);
        } else if (x >= 18 && x <= 218 && y >= 80 && y <= 97) {
            graphics.renderComponentTooltip(font, List.of(text("light").copy().append(" ×" + menu.value(14))), mouseX, mouseY);
        } else if (x >= 73 && x < 157 && y >= 135 && y < 154) {
            int i = (x - 73) / 22;
            Component state = text((menu.value(16) & 1 << i) != 0 ? "duplicate" : (menu.value(15) & 1 << i) != 0 ? "ready" : "not_selected");
            var stack = menu.getSlot(i).getItem();
            if (!stack.isEmpty()) graphics.renderComponentTooltip(font, List.of(stack.getHoverName(), state,
                    Component.literal("×" + SourceAltarRules.format(SourceAltarRules.offeringHalves(stack)))), mouseX, mouseY);
            else graphics.renderTooltip(font, state, mouseX, mouseY);
        }
    }
}
