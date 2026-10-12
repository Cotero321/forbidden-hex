package com.example.akaishi.forbidden.forge.client.hud;

import com.example.akaishi.forbidden.magic.ClientCastState;
import com.example.akaishi.forbidden.magic.ClientMagicData;
import com.example.akaishi.forbidden.magic.table.MagicSource;
import com.example.akaishi.forbidden.wand.WandItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** 挂在原版 CROSSHAIR 之后的法杖 HUD，保留原版十字与物品冷却机制。 */
public final class AkaishiWandFocusOverlay implements IGuiOverlay {

    private static final int AURA_LAYERS = 2;
    private static final float HALO_ALPHA = 1f;

    private LocalPlayer motePlayer;
    private int lastMoteTick = Integer.MIN_VALUE;
    private int castStartTick;
    private boolean wasCasting;
    private int cachedSecondsValue = Integer.MIN_VALUE;
    private String cachedSeconds = "";

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player != motePlayer) {
            motePlayer = player;
            lastMoteTick = Integer.MIN_VALUE;
            wasCasting = false;
        }
        boolean overloaded = ClientMagicData.snapshot().overloaded();
        boolean casting = player != null && ClientCastState.casting() && !overloaded;
        if (casting && !wasCasting) {
            castStartTick = player.tickCount;
        }
        wasCasting = casting;
        if (minecraft.screen != null || minecraft.options.hideGui || player == null) {
            return;
        }
        ItemStack wand = WandHudPainter.heldWand(player);
        if (wand.isEmpty()) {
            return;
        }
        MagicSource source = WandHudPainter.socketedSource(wand);
        if (source == null) {
            return;
        }
        String inkId = WandItem.mainInkId(WandItem.spell(wand));
        float timeSeconds = (player.tickCount + partialTick) / 20f;
        int rgb = WandHudPainter.hudColor(source);
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;
        Font font = minecraft.font;

        if (casting) {
            float phase = WandHudPainter.cyclePhase(timeSeconds, WandHudPainter.HALO_PERIOD_TICKS);
            for (int layer = 0; layer < AURA_LAYERS; layer++) {
                WandHudPainter.drawHalo(graphics, centerX, centerY, rgb,
                        (phase + (float) layer / AURA_LAYERS) % 1f, HALO_ALPHA);
            }
            int tick = player.tickCount;
            if (lastMoteTick == Integer.MIN_VALUE || tick - lastMoteTick >= 3) {
                lastMoteTick = tick;
                WandHudPainter.spawnConvergingMotes(player, source, inkId, rgb, tick - castStartTick);
            }
        } else {
            drawCooldown(graphics, font, player, wand, centerX, centerY, partialTick, timeSeconds);
        }

        WandHudPainter.PoolRead pool = WandHudPainter.read(wand, source);
        WandHudPainter.drawPoolBar(graphics,
                centerX - WandHudPainter.BAR_WIDTH / 2, centerY + WandHudPainter.BAR_DY,
                WandHudPainter.BAR_WIDTH, WandHudPainter.BAR_HEIGHT, pool);
    }

    private void drawCooldown(GuiGraphics graphics, Font font, LocalPlayer player, ItemStack wand,
            int centerX, int centerY, float partialTick, float timeSeconds) {
        ItemCooldowns cooldowns = player.getCooldowns();
        float remain = cooldowns.getCooldownPercent(wand.getItem(), partialTick);
        boolean overloaded = ClientMagicData.snapshot().overloaded();
        String seconds = null;
        if (!overloaded && remain > 0f) {
            float total = WandItem.cooldownSeconds(wand);
            if (total > 0f) {
                // 与 WandMagicHook.addCooldown 的取整完全一致，再换算真实剩余秒。
                int totalTicks = Math.max(1, Math.round(total * 20f));
                seconds = secondsText(remain * totalTicks / 20f);
            }
        }
        WandHudPainter.drawCooldownRing(graphics, font,
                centerX + WandHudPainter.RING_DX, centerY + WandHudPainter.RING_DY,
                WandHudPainter.RING_RADIUS, remain, seconds, overloaded,
                WandHudPainter.breath(timeSeconds));
    }

    private String secondsText(float seconds) {
        int whole = Math.max(0, (int) Math.ceil(seconds));
        if (whole != cachedSecondsValue) {
            cachedSecondsValue = whole;
            cachedSeconds = WandHudPainter.secondsText(seconds);
        }
        return cachedSeconds;
    }
}
