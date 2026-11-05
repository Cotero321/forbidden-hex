package com.example.akaishi.forbidden.alchemy.client;

import com.example.akaishi.forbidden.alchemy.AlchemyContent;
import com.example.akaishi.forbidden.alchemy.AlchemyMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

public final class AlchemyScreen extends AbstractContainerScreen<AlchemyMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("forbidden_hex", "textures/gui/alchemy_machine.png");
    private Button modeButton;
    private boolean grinding;
    private int heartbeat;
    public AlchemyScreen(AlchemyMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 210; imageHeight = 200;
        inventoryLabelX = 24; inventoryLabelY = 102;
    }
    @Override protected void init() {
        super.init();
        modeButton = addRenderableWidget(Button.builder(Component.empty(), button -> send(0)).bounds(leftPos + 12, topPos + 76, 76, 20).build());
        updateButton();
    }
    private void send(int action) { if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action); }
    private void updateButton() {
        modeButton.visible = menu.kind().crusher();
        modeButton.active = !menu.hasJob() && !grinding;
        modeButton.setMessage(Component.translatable("gui.forbidden_hex.alchemy.mode_" + menu.mode()));
    }
    @Override protected void containerTick() {
        super.containerTick(); updateButton();
        if (grinding) {
            if (minecraft == null || GLFW.glfwGetMouseButton(minecraft.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) stop();
            else if (++heartbeat >= 2) { heartbeat = 0; send(1); }
        }
    }
    private void stop() { if (grinding) { grinding = false; send(2); } }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && menu.kind() == AlchemyContent.Kind.MANUAL_CRUSHER && isHovering(94, 76, 104, 20, mouseX, mouseY)) {
            grinding = true; heartbeat = 0; send(1); return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    @Override public boolean mouseReleased(double x, double y, int button) { if (button == 0) stop(); return super.mouseReleased(x, y, button); }
    @Override public void removed() { stop(); super.removed(); }
    @Override protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        gui.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight, 256, 256);
        for (var slot : menu.slots) if (slot.isActive()) {
            gui.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, 0xFF463F48);
            gui.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, 0xFFABA494);
        }
        gui.drawString(font, Component.translatable("gui.forbidden_hex.alchemy.input"), x + 15, y + 25, 0xFF4E473F, false);
        gui.drawString(font, Component.translatable("gui.forbidden_hex.alchemy.outputs"), x + 106, y + 25, 0xFF4E473F, false);
        gui.fill(x + 18, y + 63, x + 192, y + 69, 0xFF50474B);
        if (menu.total() > 0) gui.fill(x + 19, y + 64, x + 19 + (int) Math.min(172, 172L * menu.progress() / menu.total()), y + 68, 0xFFC7A85E);
        if (isHovering(18, 63, 174, 6, mouseX, mouseY)) gui.renderTooltip(font, Component.translatable("gui.forbidden_hex.alchemy.progress", menu.progress(), menu.total()), mouseX, mouseY);
        if (menu.kind() == AlchemyContent.Kind.MANUAL_CRUSHER) {
            gui.fill(x + 94, y + 76, x + 198, y + 96, grinding ? 0xFF8A6A44 : 0xFF64564B);
            gui.drawCenteredString(font, Component.translatable("gui.forbidden_hex.alchemy.hold"), x + 146, y + 82, 0xFFF0E8CF);
        } else if (menu.kind().powered()) {
            gui.drawString(font, Component.translatable("gui.forbidden_hex.alchemy.energy", menu.kind().crusher() ? 1 : 2), x + (menu.kind().crusher() ? 94 : 14), y + 81, 0xFF65517E, false);
        } else {
            gui.drawString(font, Component.translatable("gui.forbidden_hex.alchemy.water", menu.water()), x + 14, y + 81, 0xFF426980, false);
            if (isHovering(12, 75, 186, 21, mouseX, mouseY)) gui.renderTooltip(font, Component.translatable("gui.forbidden_hex.alchemy.fill"), mouseX, mouseY);
        }
    }
    @Override protected void renderLabels(GuiGraphics gui, int mouseX, int mouseY) {
        super.renderLabels(gui, mouseX, mouseY);
        gui.drawString(font, Component.translatable("gui.forbidden_hex.alchemy.status_" + menu.status()), 12, 14, 0xFF796D58, false);
    }
    @Override public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        renderBackground(gui); super.render(gui, mouseX, mouseY, partialTick); renderTooltip(gui, mouseX, mouseY);
    }
}
