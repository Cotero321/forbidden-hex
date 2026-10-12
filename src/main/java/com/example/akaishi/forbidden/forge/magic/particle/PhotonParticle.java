package com.example.akaishi.forbidden.forge.magic.particle;

import com.example.akaishi.forbidden.magic.particle.PhotonOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** 短寿命高亮层：只负责给主体法术补边缘闪点，不承担主体形态。 */
final class PhotonParticle extends TextureSheetParticle {

    private PhotonParticle(ClientLevel level, double x, double y, double z,
            double vx, double vy, double vz, TextureAtlasSprite sprite, PhotonOptions options) {
        super(level, x, y, z);
        this.xd = vx * options.speedMul();
        this.yd = vy * options.speedMul();
        this.zd = vz * options.speedMul();
        this.lifetime = 6 + this.random.nextInt(3);
        this.quadSize = Math.max(0.025f, 0.045f * options.sizeMul());
        this.roll = (this.random.nextFloat() - 0.5f) * 3.14f;
        int rgb = options.rgb();
        this.setColor((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f);
        this.setSprite(sprite);
        this.hasPhysics = false;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    @Override
    public void tick() {
        super.tick();
        float progress = (float) this.age / Math.max(1, this.lifetime);
        this.alpha = progress < 0.25f ? progress * 4f : 1f - (progress - 0.25f) / 0.75f;
        this.quadSize *= 0.985f;
    }

    static final class Provider implements ParticleProvider<PhotonOptions> {
        private final SpriteSet sprites;

        Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(PhotonOptions options, ClientLevel level,
                double x, double y, double z, double vx, double vy, double vz) {
            return new PhotonParticle(level, x, y, z, vx, vy, vz,
                    this.sprites.get(level.random), options);
        }
    }
}
