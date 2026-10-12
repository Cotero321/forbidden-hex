package com.example.akaishi.forbidden.forge.magic.core;

import com.example.akaishi.forbidden.forge.magic.visual.SpellGeometry;
import com.example.akaishi.forbidden.item.MagicCoreItem;
import com.example.akaishi.forbidden.magic.table.MagicInkDef;
import com.example.akaishi.forbidden.magic.table.MagicInkTable;
import com.example.akaishi.forbidden.magic.table.MagicWord;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/**
 * 咒核<b>可视层</b>：把咒核的词符画在顶部杖核宝石的<b>前后面</b>上。
 *
 * <p><b>不改变杖核轮廓</b>：宝石外形始终由 {@code Core} 部件决定 ——
 * 于是"换咒核"表现为<b>宝石上显示的法术变了</b>，而不是外面又多挂一颗球。
 *
 * <h3>为什么用"已居中"的坐标空间</h3>
 * 本层由 {@link WandRenderer} 在同一 pose 下调用，而那份 pose 已由原版施加过
 * {@code display} 变换与 {@code translate(-0.5,-0.5,-0.5)}（详见 {@link WandRenderer} 类注释）。
 * 因此这里的数字是"模型 0..16 坐标 ÷ 16 − 0.5"后的值，而<b>不是</b> 0..1 原始坐标；
 * {@link #GEM_MIN} 等常量直接抄自 {@code core_base.json} 的宝石包围盒。
 *
 * <h3>⚠ 为什么贴在"表面"而不是画在"内部"</h3>
 * 早先的实现把符文画在晶腔内（{@code z = −0.0155}），但宝石是<b>实心块</b>
 * （占据 {@code z −0.0438 ~ +0.0438}）—— 符文被整个包在里面，<b>永远看不见</b>。
 * 现在改为贴在宝石<b>前后面之外 0.003</b>：既可见、又不与宝石面同深度（同深度会 z-fighting 闪烁）。
 * 两面都画是因为法杖在手里会转，只画一面就成了"半张脸"。
 *
 * <h3>分段式符文</h3>
 * 主词在<b>正中</b>（大，占主要视觉），其余词按槽位序排在<b>内圈</b>（小）。
 * 每个词用自己的墨色 —— 逐词墨在咒核 NBT 里已按槽位记录，这里只做映射，不猜颜色。
 * 分段排布是刻意的：全部叠在中心会互相盖住，读不出"这句里有几个词"。
 */
final class SpellInsetRenderer {

    /**
     * 宝石几何（抄自 {@code core_base.json}）—— 符文必须贴在宝石<b>实际最外沿</b>之外，
     * 否则会被宝石自身挡住。所以这些数字必须与几何一致。
     */
    private static final float GEM_Y_MIN = 13.4f;
    private static final float GEM_Y_MAX = 15.1f;
    /** 宝石中心（已居中坐标）：y = (13.4+15.1)/2 ÷ 16 − 0.5 */
    private static final float GEM_CENTER_Y = (GEM_Y_MIN + GEM_Y_MAX) / 2f / 16f - 0.5f;
    /**
     * 宝石主体的真实前后表面：主体元素是 x/z 6.8..9.2 的对称 cuboid。
     *
     * <p>之前为了一个已删除的 45° 元素计算“角点最外沿”，反而把符文推到宝石外面，
     * 在截图里看成悬浮突起。现在回到实际存在的几何：离表面只留 0.001。
     */
    private static final float FACE_OFFSET = 0.001f;
    private static final float FRONT_Z = 6.8f / 16f - 0.5f - FACE_OFFSET;
    private static final float BACK_Z = 9.2f / 16f - 0.5f + FACE_OFFSET;
    /** 主符文半径：收小到 0.022，像刻在晶面上的核心符号，不形成悬浮大块 */
    private static final float MAIN_SIZE = 0.022f;
    /** 辅助符文环半径与单枚大小 */
    private static final float AUX_RING = 0.030f;
    private static final float AUX_SIZE = 0.007f;
    /** 无墨记录时的兜底色（冷白）：宁可画白，也不猜成别的墨 */
    private static final int FALLBACK_INK = 0xD8E8FF;

    private SpellInsetRenderer() {
    }

    /** 两个绘制平面（正面 / 背面）；用静态数组避免每帧新建 */
    private static final float[] FACE_PLANES = {FRONT_Z, BACK_Z};

    static void render(PoseStack pose, MultiBufferSource buffers, ItemStack spell, int light) {
        if (!(spell.getItem() instanceof MagicCoreItem)) {
            return;
        }
        var summary = MagicCoreItem.summarize(spell);
        if (summary.result() == null) return;
        var sourceSlots = summary.result().operations().stream().flatMap(op -> op.slots().stream())
                .distinct().sorted().toList();
        int auxTotal = sourceSlots.size();
        var out = buffers.getBuffer(SpellGeometry.energyType());

        // 前后面各画一遍：法杖在手里会转，只画一面会变成"半张脸"
        for (float planeZ : FACE_PLANES) {
            pose.pushPose();
            pose.translate(0f, GEM_CENTER_Y, planeZ);
            Matrix4f matrix = pose.last().pose();

            // 计划来源词按原槽位分段，各自墨色，不隐式选择中心主词。
            int aux = 0;
            for (int slot : sourceSlots) {
                MagicWord word = MagicWord.byId(MagicCoreItem.word(spell, slot));
                if (word == null) {
                    continue;
                }
                double angle = Math.PI * 2d / Math.max(1, auxTotal) * aux - Math.PI / 2d;
                float dx = (float) Math.cos(angle) * AUX_RING;
                float dy = (float) Math.sin(angle) * AUX_RING;
                int ink = inkColor(spell, slot);
                Matrix4f local = new Matrix4f(matrix).translate(dx, dy, 0f);
                SpellGeometry.runeMark(out, local, glyphCode(word), AUX_SIZE, 0.72f,
                        red(ink), green(ink), blue(ink));
                aux++;
            }
            pose.popPose();
        }
    }

    /** 该槽位词的墨色；未记墨回退兜底色（不猜成别的墨）。 */
    private static int inkColor(ItemStack spell, int slot) {
        MagicInkDef ink = MagicInkTable.get(MagicCoreItem.ink(spell, slot));
        return ink == null ? FALLBACK_INK : ink.rgb();
    }

    /**
     * 词的字形码点。
     *
     * <p>用 {@link MagicWord#glyph()} 而非 {@code id().hashCode()}：字形是这张表<b>已声明的</b>
     * 符号，与法阵词符环、束流节点同源；用哈希会得到与其它地方都不一致的另一套形状。
     */
    private static int glyphCode(MagicWord word) {
        String glyph = word.glyph();
        return glyph == null || glyph.isEmpty() ? word.id().hashCode() : glyph.codePointAt(0);
    }

    private static float red(int rgb) {
        return (rgb >> 16 & 0xFF) / 255f;
    }

    private static float green(int rgb) {
        return (rgb >> 8 & 0xFF) / 255f;
    }

    private static float blue(int rgb) {
        return (rgb & 0xFF) / 255f;
    }
}
