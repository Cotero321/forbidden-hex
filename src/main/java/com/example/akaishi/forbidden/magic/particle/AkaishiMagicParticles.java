package com.example.akaishi.forbidden.magic.particle;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.magic.table.MagicInkTable;
import com.example.akaishi.forbidden.magic.table.MagicSource;
import com.example.akaishi.forbidden.magic.visual.SpellCarrierForm;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * 魔法侧粒子：<b>一个类型 {@code forbidden_hex:spell_mote}，形态按「墨 × 载体」，颜色按墨</b>。
 *
 * <p><b>为什么只注册一个类型</b>：粒子的"形状"由 {@code particles/spell_mote.json} 的贴图列表决定，
 * 列表可放多张图、由 {@link SpellMoteOptions#variant()} 选下标。一个类型 + 一份 json
 * 承载全部组合，不必每个组合各注册一个类型。
 *
 * <p><b>形态库的下标约定（改序 = 改所有粒子外观，必须与 json 同步）</b>：
 * <ol>
 *   <li>{@code 0..3} —— 四源形态（{@link MagicSource#values()} 序）：<b>兜底用</b>，
 *       只在"没有墨"或"没有载体"时才落到这里；</li>
 *   <li>{@code 4..69} —— <b>十一墨 × 六载体</b>，下标 = {@code 4 + 墨表序 × 6 + 载体枚举序}。</li>
 * </ol>
 * 之所以是"墨 × 载体"而不是单看一个：<b>墨回答"这是什么魔法"、载体回答"怎么送过来的"</b>，
 * 两件事都该看得见（见 {@link MagicInkStyle} 与 {@link SpellCarrierStyle} 的分工）。
 *
 * <p><b>颜色为什么必须走参数</b>：墨有 11 种且逐词可变，给每种墨各画死色是浪费；
 * 贴图统一去色为"白形"，逐实例 {@code setColor} 染墨。
 *
 * <p><b>资源三件套（RULES §0）</b>：
 * ① 注册 —— 本类；② 可见 —— {@code assets/forbidden_hex/particles/spell_mote.json} ＋
 * {@code assets/forbidden_hex/textures/particle/spell_mote_*.png}；③ 生效 —— 各 {@code burst/trail/ring/converge} 调用点。
 *
 * <p><b>贴图来源</b>：<i>Iron-Elden-Ring-Particle-library</i>（作者 shuimo0413，MIT 许可；
 * 署名与逐张来源映射见 {@code META-INF/third_party_notices.txt}）。
 */
public final class AkaishiMagicParticles {

    /** 源形态段长度（兜底） */
    private static final int SOURCE_COUNT = MagicSource.values().length;
    /** 墨的种数（= 形态表的墨轴长度） */
    private static final int INK_COUNT = MagicInkStyle.size();
    /** 载体的种数（= 形态表的载体轴长度） */
    private static final int CARRIER_COUNT = SpellCarrierForm.values().length;

    /** 粒子初速度幅度：给一点随机扩散 */
    private static final double SPREAD = 0.02;

    private static RegistrySupplier<ParticleType<SpellMoteOptions>> mote;
    private static RegistrySupplier<ParticleType<PhotonOptions>> photon;
    private static long photonTick = Long.MIN_VALUE;
    private static int photonCount;

    private AkaishiMagicParticles() {
    }

    /** 由 {@code AkaishiForbiddenMod.init()} 调用（须早于任何施法/雕刻入口） */
    public static void register() {
        var particles = RegistrarManager.get(AkaishiMod.MOD_ID).get(Registries.PARTICLE_TYPE);
        mote = particles.register(new ResourceLocation(AkaishiMod.MOD_ID, "spell_mote"), SpellMoteType::new);
        photon = particles.register(new ResourceLocation(AkaishiMod.MOD_ID, "photon"), PhotonType::new);
    }

    /** 粒子类型（{@link SpellMoteOptions#getType()} 也用它） */
    public static RegistrySupplier<ParticleType<SpellMoteOptions>> mote() {
        return mote;
    }

    public static RegistrySupplier<ParticleType<PhotonOptions>> photon() {
        return photon;
    }

    /** 源 → 形态下标（兜底用；未知源退回 0） */
    public static int variantOf(MagicSource source) {
        return source == null ? 0 : Math.floorMod(source.ordinal(), SOURCE_COUNT);
    }

    /** (墨, 载体) → 形态下标；任一缺失返回 {@code -1}（调用端回退源形态） */
    public static int variantOf(String inkId, SpellCarrierForm carrier) {
        int ink = MagicInkTable.indexOf(inkId);
        if (ink < 0 || carrier == null) {
            return -1;
        }
        return SOURCE_COUNT + ink * CARRIER_COUNT + carrier.ordinal();
    }

    /** 形态总数（= {@code spell_mote.json} 的贴图张数） */
    public static int variantCount() {
        return SOURCE_COUNT + INK_COUNT * CARRIER_COUNT;
    }

    /** 粒子端反查：该形态对应的<b>墨个性</b>（源形态返回中性） */
    public static MagicInkStyle inkStyleOf(int variant) {
        int combo = variant - SOURCE_COUNT;
        return combo < 0 ? MagicInkStyle.NEUTRAL : MagicInkStyle.byIndex(combo / CARRIER_COUNT);
    }

    /** 粒子端反查：该形态对应的<b>载体修正</b>（源形态返回中性） */
    public static SpellCarrierStyle carrierStyleOf(int variant) {
        int combo = variant - SOURCE_COUNT;
        return combo < 0 ? SpellCarrierStyle.NEUTRAL : SpellCarrierStyle.byIndex(combo % CARRIER_COUNT);
    }

    /** 组装粒子参数：形取（墨 × 载体），色取墨；缺墨或缺载体时回退源形 */
    public static SpellMoteOptions options(MagicSource source, String inkId, SpellCarrierForm carrier, int rgb) {
        int variant = variantOf(inkId, carrier);
        return new SpellMoteOptions(variant >= 0 ? variant : variantOf(source), rgb);
    }

    /** 数量倍率（墨个性 × 载体修正）—— 发射端据此决定"撒几颗" */
    public static float emitMul(String inkId, SpellCarrierForm carrier) {
        return MagicInkStyle.get(inkId).emitMul() * SpellCarrierStyle.get(carrier).emitMul();
    }

    /** 散布倍率（墨个性 × 载体修正）—— 发射端据此决定"散多开" */
    public static float spreadMul(String inkId, SpellCarrierForm carrier) {
        return MagicInkStyle.get(inkId).spreadMul() * SpellCarrierStyle.get(carrier).spreadMul();
    }

    /**
     * <b>服务端广播</b>的一发粒子（命中、雕刻成功这类一次性事件）。
     *
     * <p>用 {@link ServerLevel#sendParticles} 让原版把粒子发给附近玩家；
     * 客户端各自 {@code addParticle} 反而会造成两侧不一致。
     */
    public static void burst(Level level, MagicSource source, String inkId, SpellCarrierForm carrier, int rgb,
                             double x, double y, double z, int count) {
        if (!(level instanceof ServerLevel server)) return;
        server.sendParticles(options(source, inkId, carrier, rgb), x, y, z, count, 0.45, 0.35, 0.45, SPREAD);
    }

    /**
     * <b>客户端本地</b>的一发粒子 —— 给"跟着实体每 tick 出现"的尾迹用（弹体飞行）。
     *
     * <p>尾迹若也逐 tick 广播纯属浪费带宽，故由实体在自己的客户端 tick 里就地生成。
     * {@code ServerLevel#addParticle} 是空实现，所以本方法在服务端调用无副作用。
     */
    public static void trail(Level level, MagicSource source, String inkId, SpellCarrierForm carrier, int rgb,
                             double x, double y, double z, int count, double spread) {
        SpellMoteOptions options = options(source, inkId, carrier, rgb);
        for (int i = 0; i < count; i++) {
            level.addParticle(options, x, y, z,
                    jitter(level, spread), jitter(level, spread), jitter(level, spread));
        }
    }

    /** 在落点外圈撒一圈（半径取法术半径）—— 法阵脉动用，让作用范围看得见。 */
    public static void ring(Level level, MagicSource source, String inkId, SpellCarrierForm carrier, int rgb,
                            double x, double y, double z, double radius, int count) {
        SpellMoteOptions options = options(source, inkId, carrier, rgb);
        for (int i = 0; i < count; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double r = radius * (0.55 + level.random.nextDouble() * 0.45);
            level.addParticle(options,
                    x + Math.cos(angle) * r, y, z + Math.sin(angle) * r, 0d, 0.01d, 0d);
        }
    }

    /**
     * <b>向心微光</b>：在 {@code (x,y,z)} 生成一颗、初速朝着 {@code (tx,ty,tz)} ——
     * 用于"起手蓄力时微光向瞄准点汇聚"（HUD / 蓄力表现层，纯客户端调用）。
     *
     * <p>与 {@link #trail} 的差别在<b>速度方向</b>：trail 给的是随机抖动（散开），
     * 本方法是<b>定向速度</b>（收拢），两者观感完全不同，故各留一个入口而不硬塞参数。
     */
    public static void converge(Level level, MagicSource source, String inkId, SpellCarrierForm carrier, int rgb,
                                double x, double y, double z, double tx, double ty, double tz, double speed) {
        double dx = tx - x;
        double dy = ty - y;
        double dz = tz - z;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0e-4d) {
            return; // 重合：没有方向可言，直接不生成（避免 NaN 速度把粒子甩到天边）
        }
        double scale = speed / length;
        level.addParticle(options(source, inkId, carrier, rgb), x, y, z,
                dx * scale, dy * scale, dz * scale);
    }

    /** 客户端 tick/event 高亮：每刻最多 48 颗，原版非强制粒子路径负责设置与相机距离裁剪。 */
    public static void photon(Level level, int rgb, double x, double y, double z,
            double vx, double vy, double vz, float size, float speed) {
        if (!level.isClientSide || level.getNearestPlayer(x, y, z, 32d, false) == null) return;
        long now = level.getGameTime();
        if (photonTick != now) { photonTick = now; photonCount = 0; }
        if (photonCount >= 48) return;
        photonCount++;
        level.addParticle(PhotonOptions.of(0, photonRgb(rgb), size, speed), x, y, z, vx, vy, vz);
    }

    /** 墨色混入少量白色，暗墨高亮仍保留身份色。 */
    public static int photonRgb(int rgb) {
        int r = (rgb >> 16 & 255) * 3 / 5 + 102;
        int g = (rgb >> 8 & 255) * 3 / 5 + 102;
        int b = (rgb & 255) * 3 / 5 + 102;
        return r << 16 | g << 8 | b;
    }

    private static double jitter(Level level, double scale) {
        return (level.random.nextDouble() - 0.5d) * 2d * scale;
    }
}
