package com.example.akaishi.forbidden.forge.magic.particle;

import com.example.akaishi.forbidden.magic.particle.AkaishiMagicParticles;
import com.example.akaishi.forbidden.magic.particle.MagicInkStyle;
import com.example.akaishi.forbidden.magic.particle.SpellCarrierStyle;
import com.example.akaishi.forbidden.magic.particle.SpellMoteOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * 法术微光粒子（客户端）：<b>形取「墨 × 载体」、色取墨、动取「墨个性 × 载体修正」</b>。
 *
 * <p><b>为什么自绘而不是直接用原版 {@code minecraft:generic}</b>：原版 generic 是
 * 8×8 图集的 1/8 切片，会把我们 32×32 的贴图裁掉；自定义 provider 才能整张使用，
 * 也才能按参数选图与逐实例染色（{@code setColor}）。
 *
 * <p><b>为什么运动要按墨改</b>：只换贴图的"多样化"是假的 —— 11 张图配同一套"缓慢上飘"，
 * 玩家看到的仍然是一团东西在飘。故按 {@link MagicInkStyle} 应用
 * <b>重力 / 阻尼 / 水平旋绕</b>，按 {@link SpellCarrierStyle} 应用<b>寿命 / 尺寸</b>修正：
 * 炽是窜上去的火星、渊是坠下来的黑点、蔓是绕着爬、序是几乎不动的规整阵列。
 *
 * <p><b>为什么覆盖 {@link #getLightColor}</b>：魔法粒子应当自发光，
 * 在洞穴 / 夜晚也要看得见（否则玩家会以为法术没生效）。
 */
final class SpellMoteParticle extends TextureSheetParticle {

    private static final int LIFETIME_MIN = 12;
    private static final int LIFETIME_RANDOM = 10;
    private static final float SIZE_BASE = 0.10f;
    private static final float SIZE_RANDOM = 0.06f;
    private static final float CHANNEL_MAX = 255f;
    /** 寿命下限：再短就只是闪一下，读不出"是粒子" */
    private static final int LIFETIME_FLOOR = 5;

    /** 墨的个性（重力 / 阻尼 / 旋绕 / 尺寸寿命倍率） */
    private final MagicInkStyle ink;

    private SpellMoteParticle(ClientLevel level, double x, double y, double z,
                              double vx, double vy, double vz,
                              TextureAtlasSprite sprite, int rgb,
                              MagicInkStyle ink, SpellCarrierStyle carrier) {
        super(level, x, y, z);
        this.xd = vx * carrier.speedMul();
        this.yd = vy * carrier.speedMul();
        this.zd = vz * carrier.speedMul();
        this.ink = ink;
        float life = ink.lifeMul() * carrier.lifeMul();
        this.lifetime = Math.max(LIFETIME_FLOOR,
                Math.round((LIFETIME_MIN + this.random.nextInt(LIFETIME_RANDOM)) * life));
        float size = ink.sizeMul() * carrier.sizeMul();
        this.quadSize = Math.max(0.02f, (SIZE_BASE + this.random.nextFloat() * SIZE_RANDOM) * size);
        // 正=下沉 / 负=上飘（与 Particle#gravity 同义）
        this.gravity = ink.gravity();
        this.hasPhysics = false;
        applyLook(sprite, rgb);
    }

    /** 外观：形态 = 按 (墨×载体) 选好的贴图；颜色 = 墨色。 */
    private void applyLook(TextureAtlasSprite sprite, int rgb) {
        this.setSprite(sprite);
        this.setColor((rgb >> 16 & 0xFF) / CHANNEL_MAX, (rgb >> 8 & 0xFF) / CHANNEL_MAX, (rgb & 0xFF) / CHANNEL_MAX);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** 自发光：不受世界光照影响。 */
    @Override
    public int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    @Override
    public void tick() {
        super.tick();
        // ① 额外阻尼：super.tick 只给固定 0.98，按墨的 drag 再收一次（越小停得越快）
        float drag = this.ink.drag();
        if (drag != 1f) {
            this.xd *= drag;
            this.yd *= drag;
            this.zd *= drag;
        }
        // ② 水平旋绕：把水平速度绕 Y 轴转一个角度 —— 这就是"缠绕 / 乱窜"的观感来源
        float swirl = this.ink.swirl();
        if (swirl != 0f) {
            double rad = Math.toRadians(swirl);
            double cos = Math.cos(rad);
            double sin = Math.sin(rad);
            double nx = this.xd * cos - this.zd * sin;
            double nz = this.xd * sin + this.zd * cos;
            this.xd = nx;
            this.zd = nz;
        }
        // 末段淡出，避免粒子"啪"地消失
        this.alpha = 1f - (float) this.age / this.lifetime;
    }

    /** 供 Forge 的 {@code RegisterParticleProvidersEvent} 注册。 */
    static final class Provider implements ParticleProvider<SpellMoteOptions> {

        /**
         * 按"形态序"预先取好的贴图表。
         *
         * <p>{@link SpriteSet} 在 1.20.1 只有 {@code get(age, lifetime)} / {@code get(random)}，
         * 没有"按下标直取"。它的下标算法是 {@code age * (size-1) / lifetime}，
         * 因此取 {@code get(i, size-1)} 正好落到第 {@code i} 张 —— 我们把这一步一次性算好存起来，
         * 免得每个粒子每帧再算一遍，也避免"多张图被当成动画帧"的误用。
         */
        private final SpriteSet sprites;
        private final int variantCount;

        Provider(SpriteSet sprites) {
            this.sprites = sprites;
            this.variantCount = AkaishiMagicParticles.variantCount();
        }

        @Override
        public Particle createParticle(SpellMoteOptions options, ClientLevel level,
                                       double x, double y, double z,
                                       double vx, double vy, double vz) {
            int variant = Math.floorMod(options.variant(), this.variantCount);
            TextureAtlasSprite sprite = this.sprites.get(variant, Math.max(1, this.variantCount - 1));
            return new SpellMoteParticle(level, x, y, z, vx, vy, vz, sprite,
                    options.rgb(),
                    AkaishiMagicParticles.inkStyleOf(variant),
                    AkaishiMagicParticles.carrierStyleOf(variant));
        }
    }
}
