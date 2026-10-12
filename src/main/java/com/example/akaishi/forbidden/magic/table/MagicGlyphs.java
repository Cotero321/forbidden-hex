package com.example.akaishi.forbidden.magic.table;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/**
 * <b>词符唯一入口</b>（设计记忆 §11 字体纪律 / §42.1）。
 *
 * <p>符文的唯一表现形式就是词表第二列那个符号字符本身
 * （𝔞~𝔷 / 𝔄~𝔜 / ℭ ℑ ℌ ℜ）。任何地方要显示符文都必须经这里取，
 * <b>禁止</b>自造笔画、也<b>禁止</b>另抄一份符号表 —— 自造会与词表脱节，
 * 另抄会造成"改了一处漏了另一处"。
 *
 * <p>这些符号在 1.20.1 的原版字体里没有字形，故随模组提供一套位图字形，
 * 注册为 {@link #FONT}；显示符文必须用 {@link #component} 取带该字体样式的组件，
 * 直接 drawString 字符会得到缺字符方块。
 */
public final class MagicGlyphs {
    private MagicGlyphs() {}

    /** 符文字体 id（位图字形图集；定义见 assets/forbidden_hex/font/rune.json）。 */
    public static final ResourceLocation FONT = new ResourceLocation("forbidden_hex", "rune");

    /** 该词的符文符号；未知 id 返回空串（调用方决定回退，禁止静默造词）。 */
    public static String of(String wordId) {
        MagicWord word = MagicWord.byId(wordId);
        return word == null ? "" : word.glyph();
    }

    /** 该词的符文符号；{@code null} 返回空串。 */
    public static String of(MagicWord word) {
        return word == null ? "" : word.glyph();
    }

    /** 该词符号的码点（不依赖字体的 3D 刻符用）；未知词返回 0。 */
    public static int codePoint(String wordId) {
        String glyph = of(wordId);
        return glyph.isEmpty() ? 0 : glyph.codePointAt(0);
    }

    /** 可渲染的符文组件（已套上模组符文字体）；未知词返回空组件。 */
    public static Component component(String wordId) {
        String glyph = of(wordId);
        return glyph.isEmpty() ? Component.empty()
                : Component.literal(glyph).withStyle(Style.EMPTY.withFont(FONT));
    }

    /** 把符文拼在文字前面（中间一个空格）；未知词只返回文字。 */
    public static Component leading(String wordId, Component text) {
        Component rune = component(wordId);
        return rune.getString().isEmpty() ? text
                : Component.empty().append(rune).append(Component.literal(" ")).append(text);
    }
}
