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
import com.mojang.blaze3d.vertex.PoseStack;
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
 * <p><b>三段式布局</b>（总宽按锚点可用宽度自适应，高固定 38）：
 * <pre>
 *   数字行  | 88 / 150                 （当前量 / 有效上限）
 *   条行    | (COG 环) [五段刻度条 ……]
 *   状态行  | 上限 200 · 削 −50        （仅当有削减时；不可名状持续时追加剩余秒数）
 * </pre>
 *
 * <p><b>条体读法</b>（全部来自服务端权威快照 {@link ClientSanityData}，客户端不自行推算）：
 * <ul>
 *   <li>轨道整长 = <b>基础上限 SANC</b>，等分为 {@link #SEGMENTS} 段 —— 段界正好落在 20/40/60/80%，
 *       即 {@link SanityPenalties} 的<b>五个惩罚档位</b>边界；填充长度 = {@code SAN / SANC}，
 *       故"填充停在第几段"直接等于惩罚档位（与服端档位基准同源，均为 SAN/SANC）；</li>
 *   <li><b>右端斜纹区</b> = {@code [有效上限, SANC]} = 被临时削减掉、当前用不了的那一截；</li>
 *   <li><b>黄色竖线</b> = 有效上限分界（{@code SANC − tempCut}）；<b>白色右端线</b> = 基础上限 SANC；</li>
 *   <li><b>保护高亮</b> = 临时保护（{@code protection}）在填充上叠的一层亮色；</li>
 *   <li><b>左端 COG 环</b> = 环形仪表，弧长表示 {@code cog / COG_UPPER_BOUND}，环心写 COG 整数。</li>
 * </ul>
 *
 * <p><b>换算式只有一份</b>：{@code 有效上限 = SANC − tempCut} 直接取
 * {@link SanityValues#effectiveMax()}（= 服端 {@code SanityState#effectiveMax()} 用的同一个
 * {@link SanityValues#hardLimit(float, float)}），客户端<b>不另写一份</b>；档位同样复用服端
 * {@link SanityPenalties#tierOfPercent(float)} + {@link SanityThresholds#percentOf(float, float)}。
 *
 * <p><b>配色</b>：填充色统一到系统青蓝识别色 {@code #4FE3D0}，随档位向暗紫 {@code #5A2E8C} 过渡
 * （零档 = 最暗紫）；六档色为常量表 {@link #TIER_FILL}，不做逐帧插值。
 *
 * <p><b>性能</b>：字符串与像素几何均按"值未变则不重建"缓存；斜纹矩形与 COG 环刻度点位在静态初始化
 * 里一次算好 —— 稳态下本元素每帧只做比较 + 若干次 {@code fill}，零集合/数组分配。
 */
public final class AkaishiSanityHudElement implements IGuiOverlay {

    // ===== 布局常量（逻辑像素，全部"待调手感值"）=====

    /** 期望宽度：GUI 缩放 4 / 1920×1080 下锚点可用宽 135 ⇒ 132 恰好放得下并留 3px 余量。 */
    private static final int PREFERRED_WIDTH = 132;
    /** 最小可读宽度：再窄就不画五段刻度（数字会被截断）。 */
    private static final int MIN_WIDTH = 84;
    /** 数字行 / 状态行高度 = 原版字体行高。 */
    private static final int ROW_TEXT_HEIGHT = 9;
    /** 条行高度：容纳 COG 环（16）与条体（6）。 */
    private static final int ROW_BAR_HEIGHT = 16;
    /** 行间距。 */
    private static final int ROW_GAP = 2;
    /** COG 环外径。 */
    private static final int RING_BOX = 16;
    /** COG 环与条体之间的横向间隙。 */
    private static final int RING_GAP = 3;
    /** 条体高度。 */
    private static final int BAR_HEIGHT = 6;
    /** 刻度段数 = 惩罚档位数（20/40/60/80 四条段界）。 */
    private static final int SEGMENTS = 5;

    private static final int ROW_NUM_Y = 0;
    private static final int ROW_BAR_Y = ROW_TEXT_HEIGHT + ROW_GAP;
    private static final int ROW_STATUS_Y = ROW_BAR_Y + ROW_BAR_HEIGHT + ROW_GAP;
    /** 总高固定 38：状态行始终占位，避免"有削减才出现"导致整块上下跳动。 */
    private static final int TOTAL_HEIGHT = ROW_STATUS_Y + ROW_TEXT_HEIGHT;

    // ===== 配色 =====

    /** 条体外的 1px 半透明衬底：亮背景（雪地/天空）下也能看清轮廓。 */
    private static final int COLOR_BACKDROP = 0xC0000000;
    /** 轨道底槽。 */
    private static final int COLOR_TRACK = 0xFF20202A;
    /**
     * 填充色 6 档表：下标 = {@link SanityPenalties} 档位下标（0 正常 → 5 零档）。
     * 从识别色 {@code #4FE3D0} 线性过渡到暗紫 {@code #5A2E8C}（t = 下标 / 5）。
     */
    private static final int[] TIER_FILL = {
            0xFF4FE3D0, 0xFF51BFC2, 0xFF539BB5, 0xFF5676A7, 0xFF58529A, 0xFF5A2E8C
    };
    /** 被削区底色（斜纹的暗底）。 */
    private static final int COLOR_CUT_BASE = 0xFF7A2626;
    /** 被削区斜纹色。 */
    private static final int COLOR_CUT_HATCH = 0xFFD04545;
    /** 五段分隔刻度（1px 暗线，压在填充之上）。 */
    private static final int COLOR_SEPARATOR = 0xFF0A0A12;
    /** 有效上限分界竖线（黄）。 */
    private static final int COLOR_TICK_HARD = 0xFFFFE08A;
    /** 基础上限右端线（白）。 */
    private static final int COLOR_TICK_BASE = 0xFFFFFFFF;
    /** 临时保护高亮（带 alpha 的冷白，叠在填充之上）。 */
    private static final int COLOR_PROTECTION = 0x99D9FBFF;
    /** 主行字色。 */
    private static final int COLOR_TEXT = 0xFFE6E6F0;
    /** 状态行字色（弱化一档）。 */
    private static final int COLOR_TEXT_DIM = 0xFFB0B4C8;
    /** COG 环未填充部分。 */
    private static final int COLOR_RING_DIM = 0xFF2A2A36;
    /** COG 环填充弧（识别色）。 */
    private static final int COLOR_RING = 0xFF4FE3D0;

    // ===== 斜纹口径 =====

    /** 斜纹最多条数（待调手感值）：受它反推条距，避免条窄时斜纹密成一片。 */
    private static final int HATCH_MAX_LINES = 12;

    // ===== COG 环刻度点位（静态预计算，逐帧零计算）=====

    /** 环上点位步数（40 步 ⇒ 每步 9°；满环 500 点时每步 12.5 点）。 */
    private static final int RING_STEPS = 40;
    private static final int[] RING_DOT_DX = new int[RING_STEPS];
    private static final int[] RING_DOT_DY = new int[RING_STEPS];
    private static final int[] NO_RECTS = new int[0];

    static {
        for (int i = 0; i < RING_STEPS; i++) {
            // 从正上方（-90°）起、顺时针铺点
            double angle = -Math.PI / 2.0 + Math.PI * 2.0 * i / RING_STEPS;
            RING_DOT_DX[i] = (int) Math.round(Math.cos(angle) * (RING_BOX / 2 - 1));
            RING_DOT_DY[i] = (int) Math.round(Math.sin(angle) * (RING_BOX / 2 - 1));
        }
    }

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

    private String cogText = "0";
    private int cachedCog = Integer.MIN_VALUE;

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
        if (qCog != cachedCog) {
            cachedCog = qCog;
            cogText = Integer.toString(qCog);
        }
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

        // COG 环：整圈暗底 + 按 cog 比例点亮前 n 个点位
        int ringCx = x + RING_BOX / 2;
        int ringCy = y + ROW_BAR_Y + ROW_BAR_HEIGHT / 2;
        for (int i = 0; i < RING_STEPS; i++) {
            drawRingDot(graphics, ringCx, ringCy, i, COLOR_RING_DIM);
        }
        int arcSteps = qCog <= 0 ? 0
                : Mth.clamp(Math.round(values.cog() / SanityState.COG_UPPER_BOUND * RING_STEPS), 1, RING_STEPS);
        for (int i = 0; i < arcSteps; i++) {
            drawRingDot(graphics, ringCx, ringCy, i, COLOR_RING);
        }
        // 环心数字：0.5 倍字号才能塞进 16px 的环内（-4 是 9px 行高在该字号下的竖直居中）
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate((float) ringCx, (float) ringCy, 0f);
        pose.scale(0.5f, 0.5f, 1f);
        graphics.drawString(font, cogText, -font.width(cogText) / 2, -4, COLOR_RING, false);
        pose.popPose();

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

    /** 画一枚 2×2 的环上点位。 */
    private static void drawRingDot(GuiGraphics graphics, int centerX, int centerY, int index, int color) {
        int x = centerX + RING_DOT_DX[index];
        int y = centerY + RING_DOT_DY[index];
        graphics.fill(x - 1, y - 1, x + 1, y + 1, color);
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
