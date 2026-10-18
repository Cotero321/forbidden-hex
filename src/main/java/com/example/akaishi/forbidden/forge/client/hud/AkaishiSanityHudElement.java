package com.example.akaishi.forbidden.forge.client.hud;

import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.List;
import com.example.akaishi.forbidden.api.sanity.SanityValues;
import com.example.akaishi.config.ModConfig;
import com.example.akaishi.forbidden.effect.AkaishiForbiddenEffects;
import com.example.akaishi.forbidden.sanity.ClientSanityData;
import com.example.akaishi.forbidden.sanity.SanityPenalties;
import com.example.akaishi.forbidden.sanity.SanityState;
import com.example.akaishi.forbidden.sanity.SanityThresholds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * 理智 HUD 元素（仅客户端，<b>五段刻度条</b>方案）：注册为 Forge overlay，锚在快捷栏右侧、
 * 与经验条同一水平带。
 *
 * <p><b>紧凑三段式布局</b>（总宽按锚点可用宽度自适应，高固定 32）：
 * <pre>
 *   数字行  | 88 / 150                 （当前量 / 有效上限）
 *   条行    | (COG 环) [五段刻度条 ……]
 *   状态行  | 上限 200 · 削 −50        （仅当有削减时；不可名状持续时追加剩余秒数）
 * </pre>
 * <p>环心不绘制数字，使用弧长表达 COG，精确数值保留在悬停提示中，避免底部 HUD 产生文字噪声。
 *
 * <p><b>条体读法</b>（全部来自服务端权威快照 {@link ClientSanityData}，客户端不自行推算）：
 * <ul>
 *   <li>轨道整长 = <b>基础上限 SANC</b>，等分为 {@link #SEGMENTS} 段 —— 段界正好落在 20/40/60/80%，
 *       即 {@link SanityPenalties} 的<b>五个惩罚档位</b>边界；填充长度 = {@code SAN / SANC}，
 *       故"填充停在第几段"直接等于惩罚档位（与服端档位基准同源，均为 SAN/SANC）；</li>
 *   <li><b>右端斜纹区</b> = {@code [有效上限, SANC]} = 被临时削减掉、当前用不了的那一截；</li>
 *   <li><b>黄色竖线</b> = 有效上限分界（{@code SANC − tempCut}）；<b>白色右端线</b> = 基础上限 SANC；</li>
 *   <li><b>保护高亮</b> = 临时保护（{@code protection}）在填充上叠的一层亮色；</li>
 *   <li><b>左端 COG 环</b> = 环形仪表，弧长表示 {@code cog / COG_UPPER_BOUND}，精确值在悬停提示中查看。</li>
 * </ul>
 *
 * <p><b>换算式只有一份</b>：{@code 有效上限 = SANC − tempCut} 直接取
 * {@link SanityValues#effectiveMax()}（= 服端 {@code SanityState#effectiveMax()} 用的同一个
 * {@link SanityValues#hardLimit(float, float)}），客户端<b>不另写一份</b>；档位同样复用服端
 * {@link SanityPenalties#tierOfPercent(float)} + {@link SanityThresholds#percentOf(float, float)}。
 *
 * <p><b>配色</b>：正常状态使用低饱和青绿色，随档位经过灰紫逐步过渡到暖玫红；
 * 六档色为常量表 {@link #TIER_FILL}，避免低理智时只靠亮度变化判断状态。
 *
 * <p><b>性能</b>：字符串与斜纹几何均按"值未变则不重建"缓存；COG 环直接使用共享的逐像素环带
 * 绘制器，稳态下本元素每帧只做比较 + 若干次 {@code fill}，零集合/数组分配。
 */
public final class AkaishiSanityHudElement implements IGuiOverlay {

    // ===== 布局常量（逻辑像素，全部"待调手感值"）=====

    /** 期望宽度：GUI 缩放 4 / 1920×1080 下锚点可用宽 135 ⇒ 132 恰好放得下并留 3px 余量。 */
    private static final int PREFERRED_WIDTH = 132;
    /** 最小可读宽度：窄屏仍保留状态条与分段刻度。 */
    private static final int MIN_WIDTH = 76;
    /** 数字行 / 状态行高度 = 原版字体行高。 */
    private static final int ROW_TEXT_HEIGHT = 9;
    /** 条行高度：容纳紧凑 COG 环（12）与条体（5）。 */
    private static final int ROW_BAR_HEIGHT = 12;
    /** 行间距。 */
    private static final int ROW_GAP = 1;
    /** COG 环外径。 */
    private static final int RING_BOX = 12;
    /** COG 环与条体之间的横向间隙。 */
    private static final int RING_GAP = 2;
    /** 条体高度。 */
    private static final int BAR_HEIGHT = 5;
    /** 刻度段数 = 惩罚档位数（20/40/60/80 四条段界）。 */
    private static final int SEGMENTS = 5;

    private static final int ROW_NUM_Y = 0;
    private static final int ROW_BAR_Y = ROW_TEXT_HEIGHT + ROW_GAP;
    private static final int ROW_STATUS_Y = ROW_BAR_Y + ROW_BAR_HEIGHT + ROW_GAP;
    /** 总高固定 32：状态行始终占位，避免"有削减才出现"导致整块上下跳动。 */
    private static final int TOTAL_HEIGHT = ROW_STATUS_Y + ROW_TEXT_HEIGHT;

    // ===== 配色 =====

    /** 条体外的 1px 半透明衬底：带冷青色调，减少纯黑块对画面的切割感。 */
    private static final int COLOR_BACKDROP = 0xA81B282D;
    /** 轨道底槽。 */
    private static final int COLOR_TRACK = 0xD51A252C;
    /**
     * 填充色 6 档表：下标 = {@link SanityPenalties} 档位下标（0 正常 → 5 零档）。
     * 从清透青绿过渡到蓝紫与珊瑚红，让理智下降时的危险状态更容易分辨。
     */
    private static final int[] TIER_FILL = {
            0xFF62E6CF, 0xFF55D5C8, 0xFF62C3D0, 0xFF718FC5, 0xFF9274B6, 0xFFE06A73
    };
    /** 被削区底色（斜纹的暗底）。 */
    private static final int COLOR_CUT_BASE = 0xD13F2D4A;
    /** 被削区斜纹色。 */
    private static final int COLOR_CUT_HATCH = 0xE06F5579;
    /** 五段分隔刻度（1px 暗线，压在填充之上）。 */
    private static final int COLOR_SEPARATOR = 0xB512202A;
    /** 有效上限分界竖线（柔和紫金）。 */
    private static final int COLOR_TICK_HARD = 0xFFD8B77A;
    /** 基础上限右端线（冷白）。 */
    private static final int COLOR_TICK_BASE = 0xCDE5F6F3;
    /** 临时保护高亮（带 alpha 的冷白绿，叠在填充之上）。 */
    private static final int COLOR_PROTECTION = 0x91D8FFF3;
    /** 主行字色。 */
    private static final int COLOR_TEXT = 0xFFE7FBF5;
    /** 状态行字色（弱化一档）。 */
    private static final int COLOR_TEXT_DIM = 0xA8B7D0D0;
    /** COG 环未填充部分。 */
    private static final int COLOR_RING_DIM = 0x80344454;
    // ===== 斜纹口径 =====

    /** 斜纹最多条数（待调手感值）：受它反推条距，避免条窄时斜纹密成一片。 */
    private static final int HATCH_MAX_LINES = 12;

    // ===== 斜纹缓存 =====

    private static final int[] NO_RECTS = new int[0];

    // ===== 文案（双语键，见 lang 的 hud.forbidden_hex.sanity.*）=====

    private static final String KEY_CAP_CUT = "hud.forbidden_hex.sanity.cap_cut";
    private static final String KEY_UNNAMEABLE_SECONDS = "hud.forbidden_hex.sanity.unnameable_seconds";
    private static final String STATUS_SEPARATOR = " · ";

    // ===== 缓存（值未变则不重建；字符串与像素几何都在此）=====

    private String mainText = "";
    private int cachedSan = Integer.MIN_VALUE;
    private int cachedMax = Integer.MIN_VALUE;

    private String statusText = "";
    private int cachedStatusSanc = Integer.MIN_VALUE;
    private int cachedStatusCut = Integer.MIN_VALUE;
    private int cachedStatusSeconds = Integer.MIN_VALUE;

    private int barWidth = -1;
    private int fillPx;
    private int cutStartPx;
    private int protectionPx;
    private int geomSan = Integer.MIN_VALUE;
    private int geomSanc = Integer.MIN_VALUE;
    private int geomCut = Integer.MIN_VALUE;
    private int geomProtection = Integer.MIN_VALUE;

    private int[] hatchRects = NO_RECTS;
    private int hatchZonePx = -1;

    public boolean isVisible() {
        if (!ModConfig.sanityEnabled) {
            return false;
        }
        SanityValues values = ClientSanityData.snapshot();
        // 未收到首包 / 上限为 0：不画退化的空条（首包前显示内置默认值会闪一条假满条）
        return ClientSanityData.hasData() && values.sanc() > 0f;
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null || !isVisible()) {
            return;
        }
        int availableWidth = Math.max(0, screenWidth / 2 - 91 - 6 - 8);
        int width = Math.max(MIN_WIDTH, Math.min(PREFERRED_WIDTH, availableWidth));
        int x = Math.max(0, Math.min(screenWidth - width, screenWidth / 2 + 91 + 6));
        int y = screenHeight - 22 - 4 - TOTAL_HEIGHT;
        if (x < screenWidth / 2 + 91 && x + width > screenWidth / 2 - 91) {
            y = Math.min(y, screenHeight - 49 - TOTAL_HEIGHT - 2);
        }
        y = Math.max(0, Math.min(screenHeight - TOTAL_HEIGHT, y));
        Font font = minecraft.font;
        SanityValues values = ClientSanityData.snapshot();
        float sanc = values.sanc();
        float san = Mth.clamp(values.san(), 0f, sanc);
        float tempCut = Math.max(0f, values.tempCut());
        float protection = Math.max(0f, values.protection());
        float effectiveMax = values.effectiveMax();     // 与服端同一换算（见类注释）
        int tier = SanityPenalties.tierOfPercent(SanityThresholds.percentOf(san, sanc));

        int barX = x + RING_BOX + RING_GAP;
        int barWidth = width - RING_BOX - RING_GAP;
        if (barWidth <= 0) {
            return; // 可用区过窄：宁可不画，也不画一条错位的条
        }
        int barTop = y + ROW_BAR_Y + (ROW_BAR_HEIGHT - BAR_HEIGHT) / 2;
        int barBottom = barTop + BAR_HEIGHT;

        // ---- 像素几何：按（条宽 + 四个取整后的值）缓存 ----
        int qSan = Math.round(san);
        int qSanc = Math.round(sanc);
        int qCut = Math.round(tempCut);
        int qProt = Math.round(protection);
        if (barWidth != this.barWidth || qSan != geomSan || qSanc != geomSanc
                || qCut != geomCut || qProt != geomProtection) {
            rebuildGeometry(barWidth, san, sanc, effectiveMax, protection, qSan, qSanc, qCut, qProt);
        }

        // ---- 文本：按取整后的值缓存，值未变不重建字符串 ----
        int qMax = Math.round(effectiveMax);
        if (qSan != cachedSan || qMax != cachedMax) {
            cachedSan = qSan;
            cachedMax = qMax;
            mainText = qSan + " / " + qMax;
        }
        int qCog = Math.round(values.cog());
        int seconds = unnameableSeconds();
        if (qSanc != cachedStatusSanc || qCut != cachedStatusCut || seconds != cachedStatusSeconds) {
            cachedStatusSanc = qSanc;
            cachedStatusCut = qCut;
            cachedStatusSeconds = seconds;
            rebuildStatus(font, width, qSanc, qCut, seconds);
        }

        // 条体：衬底 → 底槽 → 被削斜纹区 → 填充 → 保护高亮 → 段界 → 两条竖线
        graphics.fill(barX - 1, barTop - 1, barX + barWidth + 1, barBottom + 1, COLOR_BACKDROP);
        graphics.fill(barX, barTop, barX + barWidth, barBottom, COLOR_TRACK);
        if (cutStartPx < barWidth) {
            graphics.fill(barX + cutStartPx, barTop, barX + barWidth, barBottom, COLOR_CUT_BASE);
            for (int i = 0; i + 3 < hatchRects.length; i += 4) {
                // 斜纹矩形以"被削区左上角"为原点，故绘制时平移 cutStartPx
                graphics.fill(barX + cutStartPx + hatchRects[i], barTop + hatchRects[i + 1],
                        barX + cutStartPx + hatchRects[i + 2], barTop + hatchRects[i + 3], COLOR_CUT_HATCH);
            }
        }
        if (fillPx > 0) {
            graphics.fill(barX, barTop, barX + fillPx, barBottom, TIER_FILL[tier]);
        }
        if (protectionPx > 0) {
            graphics.fill(barX, barTop, barX + protectionPx, barBottom, COLOR_PROTECTION);
        }
        for (int i = 1; i < SEGMENTS; i++) {
            int sx = barX + Math.round((float) barWidth * i / SEGMENTS);
            graphics.fill(sx, barTop, sx + 1, barBottom, COLOR_SEPARATOR);
        }
        if (cutStartPx < barWidth) {
            graphics.fill(barX + cutStartPx, barTop - 1, barX + cutStartPx + 1, barBottom + 1, COLOR_TICK_HARD);
        }
        graphics.fill(barX + barWidth - 1, barTop - 1, barX + barWidth, barBottom + 1, COLOR_TICK_BASE);

        // COG 环：纯弧形状态指示，颜色随 SAN 档位变化，不在环心叠加数字。
        int ringCx = x + RING_BOX / 2;
        int ringCy = y + ROW_BAR_Y + ROW_BAR_HEIGHT / 2;
        int ringRadius = RING_BOX / 2 - 1;
        WandHudPainter.ringArc(graphics, ringCx, ringCy, ringRadius, 0f, 1f, COLOR_RING_DIM, 1);
        if (qCog > 0) {
            float cogRatio = Mth.clamp(values.cog() / SanityState.COG_UPPER_BOUND, 0f, 1f);
            float pulse = WandHudPainter.breath((minecraft.player.tickCount + partialTick) / 20f);
            WandHudPainter.ringArc(graphics, ringCx, ringCy, ringRadius, 0f, cogRatio,
                    WandHudPainter.withAlpha(TIER_FILL[tier], pulse), 1);
        }

        // 文本行
        graphics.drawString(font, mainText, x, y + ROW_NUM_Y, COLOR_TEXT, true);
        if (!statusText.isEmpty()) {
            graphics.drawString(font, statusText, x, y + ROW_STATUS_Y, COLOR_TEXT_DIM, true);
        }
        if (minecraft.screen != null) {
            int mouseX = (int) (minecraft.mouseHandler.xpos() * screenWidth / minecraft.getWindow().getScreenWidth());
            int mouseY = (int) (minecraft.mouseHandler.ypos() * screenHeight / minecraft.getWindow().getScreenHeight());
            if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + TOTAL_HEIGHT) {
                graphics.renderComponentTooltip(font, List.of(
                        Component.literal("SAN: " + qSan + " / " + qMax),
                        Component.literal("SANC: " + qSanc),
                        Component.literal("COG: " + qCog),
                        Component.literal("tempCut: " + qCut),
                        Component.literal("protection: " + qProt)), mouseX, mouseY);
            }
        }
    }

    /** 重算像素几何（仅当条宽或取整后的四值变化时调用）。 */
    private void rebuildGeometry(int barWidth, float san, float sanc, float effectiveMax, float protection,
                                 int qSan, int qSanc, int qCut, int qProt) {
        this.barWidth = barWidth;
        this.geomSan = qSan;
        this.geomSanc = qSanc;
        this.geomCut = qCut;
        this.geomProtection = qProt;
        this.fillPx = scaled(san, sanc, barWidth);
        // 分界位置直接用服端同一换算出来的有效上限，不在客户端再写一次 max(0, sanc − tempCut)
        this.cutStartPx = scaled(effectiveMax, sanc, barWidth);
        // 保护高亮只叠在填充段内：超出填充的部分视觉上会被当成"凭空多出来的量"
        this.protectionPx = Math.min(scaled(protection, sanc, barWidth), fillPx);
        // 斜纹图案随被削区宽度缓存（静态图案，不随最小数变动而逐帧重算）
        int zonePx = barWidth - cutStartPx;
        if (zonePx != hatchZonePx) {
            hatchZonePx = zonePx;
            hatchRects = hatchRectsFor(zonePx);
        }
    }

    /** 重建状态行文案（含"放不下就先丢秒数"的降级）。 */
    private void rebuildStatus(Font font, int contentWidth, int qSanc, int qCut, int seconds) {
        String cut = qCut >= 1
                ? Component.translatable(KEY_CAP_CUT, qSanc, qCut).getString()
                : "";
        String secs = seconds > 0
                ? Component.translatable(KEY_UNNAMEABLE_SECONDS, seconds).getString()
                : "";
        String joined = cut.isEmpty() ? secs
                : (secs.isEmpty() ? cut : cut + STATUS_SEPARATOR + secs);
        if (!joined.isEmpty() && font.width(joined) > contentWidth) {
            joined = cut; // 挤不下时保留更重要的"上限/削减"，丢掉秒数
        }
        statusText = joined;
    }

    /** 斜纹矩形表（相对条体左上角的四元组 x0,y0,x1,y1）；按被削像素宽缓存。 */
    private int[] hatchRectsFor(int cutPx) {
        if (cutPx <= 0) {
            return NO_RECTS;
        }
        int period = Math.max(3, cutPx / HATCH_MAX_LINES);
        int[] rects = new int[((cutPx + period) / period + 1) * BAR_HEIGHT * 4];
        int count = 0;
        // 45° 斜纹：每条从 (x0, 0) 起、每行右移 1px
        for (int start = -BAR_HEIGHT + 1; start < cutPx; start += period) {
            for (int row = 0; row < BAR_HEIGHT; row++) {
                int px = start + row;
                if (px < 0 || px >= cutPx) {
                    continue;
                }
                rects[count++] = px;
                rects[count++] = row;
                rects[count++] = px + 1;
                rects[count++] = row + 1;
            }
        }
        if (count == rects.length) {
            return rects;
        }
        int[] trimmed = new int[count];
        System.arraycopy(rects, 0, trimmed, 0, count);
        return trimmed;
    }

    /** 数值 → 轨道内像素（按基础上限 SANC 归一，越界夹回轨道内）。 */
    private static int scaled(float value, float total, int pixels) {
        if (!(total > 0f)) {
            return 0;
        }
        return Math.max(0, Math.min(pixels, Math.round(value / total * pixels)));
    }

    /** 本地玩家身上「不可名状」的剩余秒数（无效果 = 0）；取上整，避免最后一秒显示 0。 */
    private static int unnameableSeconds() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || AkaishiForbiddenEffects.UNNAMEABLE == null) {
            return 0;
        }
        MobEffectInstance instance = player.getEffect(AkaishiForbiddenEffects.UNNAMEABLE.get());
        return instance == null ? 0 : (instance.getDuration() + 19) / 20;
    }
}
