package com.example.akaishi.forbidden.forge.transmutation;

import com.example.akaishi.forbidden.transmutation.TransmutationEnergyTiers;
import com.example.akaishi.forbidden.transmutation.source_furnace.SourceTransmutationFurnaceMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Minimal client screen for the source-energy transmutation furnace. */
public final class SourceTransmutationFurnaceScreen extends AbstractContainerScreen<SourceTransmutationFurnaceMenu> {
    private Button tierButton;

    public SourceTransmutationFurnaceScreen(SourceTransmutationFurnaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = 74;
    }

    @Override protected void init() {
        super.init();
        tierButton = addRenderableWidget(Button.builder(Component.translatable("gui.forbidden_hex.source_transmutation_furnace.tier", menu.tier() + 1),
                        button -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0))
                .bounds(leftPos + 8, topPos + 33, 34, 18).build());
    }

    @Override protected void containerTick() {
        super.containerTick();
        tierButton.setMessage(Component.translatable("gui.forbidden_hex.source_transmutation_furnace.tier", menu.tier() + 1));
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFF211A24);
        graphics.fill(x + 6, y + 6, x + imageWidth - 6, y + 28, 0xFF352A38);
        graphics.fill(x + 42, y + 31, x + 62, y + 51, 0xFF4A3D4F);
        graphics.fill(x + 114, y + 31, x + 134, y + 51, 0xFF4A3D4F);
        int width = menu.duration() <= 0 ? 0 : (int) (24.0D * menu.progress() / menu.duration());
        graphics.fill(x + 78, y + 37, x + 78 + width, y + 45, 0xFFCDAF5B);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 8, 0xFFE8DCC8, false);
        TransmutationEnergyTiers tier = TransmutationEnergyTiers.values()[Math.max(0, Math.min(2, menu.tier()))];
        graphics.drawString(font, Component.translatable("gui.forbidden_hex.source_transmutation_furnace.energy", tier.energy()), 8, 52, 0xFFC9B8A0, false);
        graphics.drawString(font, Component.translatable("gui.forbidden_hex.source_transmutation_furnace.progress", menu.progress(), menu.duration()), 8, 62, 0xFFC9B8A0, false);
        graphics.drawString(font, Component.translatable("container.inventory"), inventoryLabelX, inventoryLabelY, 0xFFE8DCC8, false);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
