package com.example.akaishi.forbidden.forge.familiar;

import com.example.akaishi.forbidden.familiar.core.FamiliarMode;
import com.example.akaishi.forbidden.familiar.entity.FamiliarEntity;
import com.example.akaishi.forbidden.familiar.info.FamiliarInfoMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Compact information panel for a released familiar. */
public final class FamiliarInfoScreen extends AbstractContainerScreen<FamiliarInfoMenu> {
    public FamiliarInfoScreen(FamiliarInfoMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 260;
        imageHeight = 210;
        inventoryLabelY = imageHeight - 94;
    }

    @Override protected void init() {
        super.init();
        int x = leftPos + 180;
        int y = topPos + 32;
        FamiliarMode[] modes = FamiliarMode.values();
        for (int i = 0; i < modes.length; i++) {
            final int mode = i;
            addRenderableWidget(Button.builder(Component.translatable(modes[i].translationKey()), button ->
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, mode))
                    .bounds(x, y + i * 20, 72, 18).build());
        }
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFF211A24);
        graphics.fill(x + 6, y + 6, x + imageWidth - 6, y + 28, 0xFF352A38);
        graphics.fill(x + 6, y + 29, x + 172, y + 91, 0xFF2B2330);
        graphics.fill(x + 176, y + 29, x + imageWidth - 6, y + 132, 0xFF2B2330);
        for (var slot : menu.slots) {
            graphics.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, 0xFF4A3D4F);
        }
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 8, 0xFFE8DCC8, false);
        FamiliarEntity familiar = menu.familiar();
        if (familiar != null) {
            graphics.drawString(font, Component.translatable(familiar.template().translationKey()), 8, 34, 0xFFE8DCC8, false);
            graphics.drawString(font, Component.translatable("gui.forbidden_hex.familiar_info.level", familiar.levelValue()), 8, 46, 0xFFC9B8A0, false);
            graphics.drawString(font, Component.translatable("gui.forbidden_hex.familiar_info.health", Math.round(familiar.getHealth()), Math.round(familiar.getMaxHealth())), 8, 58, 0xFFC9B8A0, false);
            graphics.drawString(font, Component.translatable("gui.forbidden_hex.familiar_info.hunger", familiar.hunger(), 100), 8, 70, 0xFFC9B8A0, false);
            graphics.drawString(font, Component.translatable("gui.forbidden_hex.familiar_info.mood", familiar.mood(), 100), 8, 82, 0xFFC9B8A0, false);
        }
        graphics.drawString(font, Component.translatable("container.inventory"), inventoryLabelX, inventoryLabelY, 0xFFE8DCC8, false);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
