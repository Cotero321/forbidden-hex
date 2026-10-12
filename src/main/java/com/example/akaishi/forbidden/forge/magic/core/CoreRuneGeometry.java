package com.example.akaishi.forbidden.forge.magic.core;

import com.example.akaishi.forbidden.magic.table.MagicGlyphs;
import com.example.akaishi.forbidden.magic.table.MagicInkTable;
import com.example.akaishi.forbidden.magic.table.MagicWord;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * 核心八刻槽里的词符。
 *
 * <p>符文唯一来源是 {@link MagicGlyphs}（词表符号本身 + 模组符文字体），这里直接把该字符
 * 刻进凹槽，与 GUI 词表网格、核心圆环用的是同一个字形，不再自造笔画。
 */
final class CoreRuneGeometry {

    private CoreRuneGeometry() {}

    /** 符号缩放到刚好填满半径约 0.055 格的凹槽（字形墨迹约 8px，故 0.0055 得到约 0.044 格）。 */
    private static final float SCALE = 0.0055f;

    static void draw(PoseStack pose, MultiBufferSource buffers, float x, float y,
                     MagicWord word, String inkId, int light) {
        Component rune = MagicGlyphs.component(word.id());
        if (rune.getString().isEmpty()) return;
        int ink = MagicInkTable.get(inkId) == null ? 0x8F929D : MagicInkTable.get(inkId).rgb();
        Font font = Minecraft.getInstance().font;
        FormattedCharSequence sequence = rune.getVisualOrderText();
        pose.pushPose();
        pose.translate(x, y, 0.048f);
        pose.scale(SCALE, -SCALE, SCALE);      // Y 取负：字体坐标向下为正
        font.drawInBatch(sequence, -font.width(sequence) / 2f, -4f, 0xFF000000 | ink, false,
                pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, light);
        pose.popPose();
    }
}
