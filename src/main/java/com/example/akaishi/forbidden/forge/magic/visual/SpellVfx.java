package com.example.akaishi.forbidden.forge.magic.visual;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.magic.table.MagicSource;
import net.minecraft.resources.ResourceLocation;

/**
 * 法术 VFX 的<b>贴图位库</b>（取自 <i>Iron-Elden-Ring-Particle-library</i> · MIT；
 * 署名与来源映射见 {@code META-INF/third_party_notices.txt}）。
 *
 * <p><b>为什么统一去色为白</b>：库贴图自带色相（蓝环 / 橙点），直接乘墨色会脏。
 * 入库时全部"保留 alpha 轮廓、RGB 取最大通道"处理成白形，
 * 颜色一律靠顶点色染 —— 与粒子层「白形 × 墨色」同一口径。
 *
 * <p><b>角色分工（这是本类的全部意义：一张贴图只干一件事）</b>
 * <table border="1">
 *   <tr><th>贴图</th><th>形状</th><th>用在哪</th></tr>
 *   <tr><td>{@link #RING}</td><td>实线圆环</td><td>法阵底纹</td></tr>
 *   <tr><td>{@link #RING_PULSE}</td><td>虚线断环</td><td>命中冲击环、法阵脉动外环</td></tr>
 *   <tr><td>{@link #NOVA}</td><td>放射星芒</td><td>命中放射闪光</td></tr>
 *   <tr><td>{@link #HALO}</td><td>柔光圆盘</td><td>通用柔光（束端 / 印记底光 / 残影）</td></tr>
 *   <tr><td>{@link #GLOW}</td><td>次级柔光</td><td>小体量补光</td></tr>
 *   <tr><td>{@link #COMET}</td><td>彗头</td><td>投射物头部</td></tr>
 *   <tr><td>{@link #STREAK}</td><td>斜向光带</td><td>投射物尾迹、束流光带</td></tr>
 *   <tr><td>{@link #FILAMENT}</td><td>光丝</td><td>束流丝缕</td></tr>
 *   <tr><td>{@link #RUNE}</td><td>符文纹样</td><td>命中符文圈、法阵符环</td></tr>
 *   <tr><td>{@link #SIGIL}</td><td>符印纹样</td><td>附着印记</td></tr>
 *   <tr><td>{@link #WISP}</td><td>雾缕</td><td>回响 / 残影</td></tr>
 *   <tr><td>{@link #SPRAY}</td><td>喷洒</td><td>连发散射</td></tr>
 *   <tr><td>{@link #SHARD}</td><td>碎片</td><td>破防飞散</td></tr>
 *   <tr><td>{@link #SPARK}</td><td>火花</td><td>束端光晕</td></tr>
 * </table>
 */
public final class SpellVfx {

    /** 实线圆环：法阵底纹 */
    public static final ResourceLocation RING = tex("vfx_ring");
    /** 虚线断环：命中冲击环 / 法阵脉动外环 */
    public static final ResourceLocation RING_PULSE = tex("vfx_ring_pulse");
    /** 放射星芒：命中放射闪光 */
    public static final ResourceLocation NOVA = tex("vfx_nova");
    /** 柔光圆盘：通用柔光层 */
    public static final ResourceLocation HALO = tex("vfx_halo");
    /** 次级柔光 */
    public static final ResourceLocation GLOW = tex("vfx_glow");
    /** 彗头：投射物头部 */
    public static final ResourceLocation COMET = tex("vfx_comet");
    /** 光带：尾迹 / 束流 */
    public static final ResourceLocation STREAK = tex("vfx_streak");
    /** 光丝：束流丝缕 */
    public static final ResourceLocation FILAMENT = tex("vfx_filament");
    /** 符文：命中符圈 / 法阵符环 */
    public static final ResourceLocation RUNE = tex("vfx_rune");
    /** 符印：附着印记 */
    public static final ResourceLocation SIGIL = tex("vfx_sigil");
    /** 雾缕：回响残影 */
    public static final ResourceLocation WISP = tex("vfx_wisp");
    /** 喷洒：连发散射 */
    public static final ResourceLocation SPRAY = tex("vfx_spray");
    /** 碎片：破防飞散 */
    public static final ResourceLocation SHARD = tex("vfx_shard");
    /** 火花：束端光晕 */
    public static final ResourceLocation SPARK = tex("vfx_spark");

    // ===== 源专属闪光：同一个"命中核心一亮"，四种源长得不一样 =====

    private static final ResourceLocation FLARE_SAN = tex("vfx_flare_san");
    private static final ResourceLocation FLARE_LIFE = tex("vfx_flare_life");
    private static final ResourceLocation FLARE_DARK = tex("vfx_flare_dark");
    private static final ResourceLocation FLARE_WHITE = tex("vfx_flare_white");

    private SpellVfx() {
    }

    /** 该源的命中闪光贴图（源序：灰 / 绿 / 暗 / 白） */
    public static ResourceLocation flare(MagicSource source) {
        return switch (source) {
            case LIFE -> FLARE_LIFE;
            case DARK -> FLARE_DARK;
            case WHITE -> FLARE_WHITE;
            case SAN -> FLARE_SAN;
        };
    }

    private static ResourceLocation tex(String name) {
        return new ResourceLocation(AkaishiMod.MOD_ID, "textures/magic/" + name + ".png");
    }
}
