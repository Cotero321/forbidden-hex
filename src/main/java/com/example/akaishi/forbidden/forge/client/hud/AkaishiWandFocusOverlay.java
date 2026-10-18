package com.example.akaishi.forbidden.forge.client.hud;

import com.example.akaishi.forbidden.forge.client.visual.ClientWandVisuals;
import com.example.akaishi.forbidden.magic.ClientMagicData;
import com.example.akaishi.forbidden.magic.cast.MagicCastState;
import com.example.akaishi.forbidden.magic.table.MagicSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** Compact status indicator beside the vanilla crosshair; rendering never spawns particles. */
public final class AkaishiWandFocusOverlay implements IGuiOverlay {
    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (minecraft.screen != null || minecraft.options.hideGui || player == null || player.isSpectator()) return;
        ItemStack wand = ClientWandVisuals.wand();
        if (wand.isEmpty()) return;
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;
        int indicatorX = centerX + WandHudPainter.RING_DX;
        int indicatorY = centerY + WandHudPainter.RING_DY;
        MagicSource source = ClientWandVisuals.source();
        float time = (player.tickCount + partialTick) / 20f;
        boolean overloaded = ClientMagicData.snapshot().overloaded();
        MagicCastState state = ClientWandVisuals.state();
        if (state.isActive() && source != null) {
            WandHudPainter.drawCastIndicator(graphics, indicatorX, indicatorY, WandHudPainter.hudColor(source),
                    state, ClientWandVisuals.age(partialTick), ClientWandVisuals.chargeProgress(partialTick),
                    ClientWandVisuals.transition(partialTick), ClientWandVisuals.segmentPhase(partialTick));
        } else if (ClientWandVisuals.denied()) {
            WandHudPainter.drawUnavailable(graphics, indicatorX, indicatorY, false, true);
        } else if (source == null || !ClientWandVisuals.valid()) {
            WandHudPainter.drawUnavailable(graphics, indicatorX, indicatorY, source == null, false);
        } else if (overloaded) {
            WandHudPainter.drawCooldownRing(graphics, indicatorX, indicatorY, WandHudPainter.RING_RADIUS,
                    1f, true, WandHudPainter.breath(time));
        } else {
            float remain = player.getCooldowns().getCooldownPercent(wand.getItem(), partialTick);
            WandHudPainter.drawCooldownRing(graphics, indicatorX, indicatorY, WandHudPainter.RING_RADIUS,
                    remain, false, WandHudPainter.breath(time));
            if (remain <= 0f) {
                WandHudPainter.ringArc(graphics, indicatorX, indicatorY, WandHudPainter.RING_RADIUS,
                        0f, 1f, WandHudPainter.withAlpha(WandHudPainter.hudColor(source), ClientWandVisuals.selection(partialTick)), 1);
            }
        }
        if (source == null) return;
        WandHudPainter.drawPoolBar(graphics,
                centerX - WandHudPainter.BAR_WIDTH / 2, centerY + WandHudPainter.BAR_DY,
                WandHudPainter.BAR_WIDTH, WandHudPainter.BAR_HEIGHT, WandHudPainter.read(wand, source));
    }
}
