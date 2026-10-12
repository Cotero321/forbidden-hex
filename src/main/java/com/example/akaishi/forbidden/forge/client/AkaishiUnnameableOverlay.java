package com.example.akaishi.forbidden.forge.client;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.effect.UnnameableClientAmbience;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.ArrayList;
import java.util.List;

/**
 * 「不可名状」画面表现层（仅客户端 HUD 叠加）：
 * 视野四条边缘绘制粗线并平铺噪点纹理，随后在屏幕随机位置缓慢浮现低语文字。
 * <p>低语不再逐帧重掷随机位置（那会造成高频闪烁），改为"带生命周期的条目"：
 * 每条低语自诞生起走淡入 → 平台 → 淡出的透明度曲线，同时缓慢上浮并左右摆动，形成悬浮飘动的耳语感。
 * 表现强度由 {@link UnnameableClientAmbience} 平滑提供，效果消退后自动消失。本类只负责绘制，不含服务端逻辑。
 */
public final class AkaishiUnnameableOverlay implements IGuiOverlay {

    /** 噪点平铺源贴图：16×16 半透明噪点 */
    private static final ResourceLocation NOISE =
            new ResourceLocation(AkaishiMod.MOD_ID, "textures/gui/unnameable_noise.png");

    /** 低语文本池：每次随机抽取一条（统一加粗，弱化"UI 提示"感、更像压迫性的耳语） */
    private static final Component[] WHISPERS = {
            Component.translatable("message.forbidden_hex.unnameable.whisper.1").withStyle(ChatFormatting.BOLD),
            Component.translatable("message.forbidden_hex.unnameable.whisper.2").withStyle(ChatFormatting.BOLD),
            Component.translatable("message.forbidden_hex.unnameable.whisper.3").withStyle(ChatFormatting.BOLD),
            Component.translatable("message.forbidden_hex.unnameable.whisper.4").withStyle(ChatFormatting.BOLD),
            Component.translatable("message.forbidden_hex.unnameable.whisper.5").withStyle(ChatFormatting.BOLD),
            Component.translatable("message.forbidden_hex.unnameable.whisper.6").withStyle(ChatFormatting.BOLD),
    };

    /** 低于此强度视为无效果，避免残留的淡影 */
    private static final float MIN_LEVEL = 0.02f;
    /** 满强度时的边缘粗线厚度（像素） */
    private static final int MAX_BAND = 14;
    /** 低语文字基础色（RGB）：惨白偏血色的暗红 */
    private static final int WHISPER_RGB = 0xD8C2C6;

    /** 单条低语的存活时长区间（毫秒）：足够长才不显得高频闪烁 */
    private static final int LIFE_MIN_MS = 2600;
    private static final int LIFE_MAX_MS = 4400;
    /** 生命周期中淡入 / 淡出所占比例，其余为全亮的平台期 */
    private static final float FADE_IN_RATIO = 0.22f;
    private static final float FADE_OUT_RATIO = 0.38f;
    /** 相邻两条低语的间隔区间（毫秒）：强度越高越密，但仍远慢于逐帧刷新 */
    private static final long SPAWN_GAP_MAX_MS = 2400L;
    private static final long SPAWN_GAP_MIN_MS = 1000L;
    /** 生成间隔的随机抖动上限（毫秒）：打散节奏，避免机械的等间隔出现 */
    private static final int SPAWN_JITTER_MS = 700;
    /** 同屏同时存在的低语条数上限（基础值，另随强度递增） */
    private static final int MAX_CONCURRENT_BASE = 2;
    /** 悬浮效果：向上漂移速度（像素/秒） */
    private static final float RISE_PER_SECOND = 9.0f;
    /** 悬浮效果：左右摆动幅度（像素）与角频率（弧度/秒） */
    private static final float DRIFT_AMPLITUDE = 10.0f;
    private static final float DRIFT_FREQUENCY = 0.9f;
    /** 文字距屏幕边缘的最小留白（像素），避免被裁切 */
    private static final int MARGIN = 12;

    private final RandomSource random = RandomSource.create();

    /** 当前存活中的低语条目（仅渲染主线程访问，无需并发容器） */
    private final List<Whisper> whispers = new ArrayList<>();
    /** 下一条低语的生成时刻（毫秒时间戳） */
    private long nextSpawnAt;

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        // 打开界面或隐藏 HUD 时不叠加，避免遮挡交互控件
        if (minecraft.screen != null || minecraft.options.hideGui) {
            return;
        }
        float level = UnnameableClientAmbience.level();
        if (level <= MIN_LEVEL) {
            // 效果已消退：清空残留条目，下次触发重新起算，避免旧条目"带偏移地"突然出现
            whispers.clear();
            return;
        }
        int band = Math.max(3, Math.round(MAX_BAND * level));
        drawEdgeBands(graphics, screenWidth, screenHeight, band);
        drawEdgeNoise(graphics, screenWidth, screenHeight, band, level);
        drawWhispers(graphics, screenWidth, screenHeight, level);
    }

    /** 四条边缘粗线：外层近黑压实边界，内层半透明羽化，避免生硬直角 */
    private void drawEdgeBands(GuiGraphics graphics, int width, int height, int band) {
        int outer = 0xFF14060A;
        int inner = 0x6614060A;
        graphics.fill(0, 0, width, band, outer);
        graphics.fill(0, height - band, width, height, outer);
        graphics.fill(0, band, band, height - band, outer);
        graphics.fill(width - band, band, width, height - band, outer);

        int feather = Math.max(1, band / 2);
        graphics.fill(band, band, width - band, band + feather, inner);
        graphics.fill(band, height - band - feather, width - band, height - band, inner);
        graphics.fill(band, band + feather, band + feather, height - band - feather, inner);
        graphics.fill(width - band - feather, band + feather, width - band, height - band - feather, inner);
    }

    /** 边缘噪点：仅在四条粗线范围内平铺噪点纹理，透明度随效果强度加深 */
    private void drawEdgeNoise(GuiGraphics graphics, int width, int height, int band, float level) {
        graphics.setColor(1.0F, 1.0F, 1.0F, 0.45F + 0.55F * level);
        graphics.blitRepeating(NOISE, 0, 0, width, band, 0, 0, 16, 16);
        graphics.blitRepeating(NOISE, 0, height - band, width, band, 0, 0, 16, 16);
        graphics.blitRepeating(NOISE, 0, band, band, height - 2 * band, 0, 0, 16, 16);
        graphics.blitRepeating(NOISE, width - band, band, band, height - 2 * band, 0, 0, 16, 16);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** 按节奏生成低语并逐条绘制：透明度走淡入/淡出包络，位置随存活时间上浮并左右摆动 */
    private void drawWhispers(GuiGraphics graphics, int width, int height, float level) {
        Font font = Minecraft.getInstance().font;
        long now = Util.getMillis();
        // 先清理已过期的条目，再做生成与绘制，保证列表长度恒等于同屏可见条数
        whispers.removeIf(whisper -> now - whisper.bornAt >= whisper.lifeMs);
        if (now >= nextSpawnAt) {
            int maxConcurrent = MAX_CONCURRENT_BASE + Math.round(level * 2.0f);
            if (whispers.size() < maxConcurrent) {
                whispers.add(createWhisper(font, width, height, now, level));
            }
            nextSpawnAt = now + nextGap(level);
        }
        for (Whisper whisper : whispers) {
            long ageMs = now - whisper.bornAt;
            float age = ageMs / 1000.0f;
            float alpha = fadeEnvelope((float) ageMs / whisper.lifeMs) * whisper.peakAlpha * level;
            int argbAlpha = Math.round(Math.max(0.0f, Math.min(1.0f, alpha)) * 255.0f);
            if (argbAlpha <= 2) {
                continue;
            }
            int x = Math.round(whisper.x
                    + (float) Math.sin(age * DRIFT_FREQUENCY + whisper.driftPhase) * DRIFT_AMPLITUDE);
            int y = Math.round(whisper.y - RISE_PER_SECOND * age);
            // 颜色必须带非零 alpha，否则文本完全不可见
            graphics.drawString(font, whisper.text, x, y, (argbAlpha << 24) | WHISPER_RGB, true);
        }
    }

    /** 在屏幕内随机取位生成一条低语（四周留白，防止文字被裁切） */
    private Whisper createWhisper(Font font, int width, int height, long now, float level) {
        Component text = WHISPERS[random.nextInt(WHISPERS.length)];
        int maxX = Math.max(MARGIN, width - font.width(text) - MARGIN);
        int maxY = Math.max(MARGIN, height - MARGIN - 20);
        float x = MARGIN + random.nextInt(Math.max(1, maxX - MARGIN));
        float y = MARGIN + random.nextInt(Math.max(1, maxY - MARGIN));
        int lifeMs = LIFE_MIN_MS + random.nextInt(LIFE_MAX_MS - LIFE_MIN_MS + 1);
        // 峰值透明度随机，并随强度抬升：弱效果下只是隐约暗影，强效果下清晰可读
        float peakAlpha = Math.min(1.0f, 0.55f + random.nextFloat() * 0.35f + 0.1f * level);
        return new Whisper(text, x, y, now, lifeMs, random.nextFloat() * 6.283f, peakAlpha);
    }

    /** 生成间隔：强度越高越短，另叠加随机抖动打散节奏 */
    private long nextGap(float level) {
        long base = (long) (SPAWN_GAP_MAX_MS - (SPAWN_GAP_MAX_MS - SPAWN_GAP_MIN_MS) * level);
        return base + random.nextInt(SPAWN_JITTER_MS);
    }

    /** 透明度包络：前段淡入、后段淡出、中间全亮，消除"啪地出现/消失"的突兀感 */
    private static float fadeEnvelope(float t) {
        if (t <= FADE_IN_RATIO) {
            return t / FADE_IN_RATIO;
        }
        if (t >= 1.0f - FADE_OUT_RATIO) {
            return (1.0f - t) / FADE_OUT_RATIO;
        }
        return 1.0f;
    }

    /** 单条低语的存活状态：基准位置固定，随存活时间淡入淡出、缓慢上浮并左右摆动 */
    private static final class Whisper {
        final Component text;
        final float x;
        final float y;
        final long bornAt;
        final int lifeMs;
        final float driftPhase;
        final float peakAlpha;

        Whisper(Component text, float x, float y, long bornAt, int lifeMs, float driftPhase, float peakAlpha) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.bornAt = bornAt;
            this.lifeMs = lifeMs;
            this.driftPhase = driftPhase;
            this.peakAlpha = peakAlpha;
        }
    }
}
