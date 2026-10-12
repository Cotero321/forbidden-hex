package com.example.akaishi.forbidden.magic.particle;

import com.mojang.serialization.Codec;
import net.minecraft.core.particles.ParticleType;

/** 独立的短寿命高亮粒子类型。 */
public final class PhotonType extends ParticleType<PhotonOptions> {

    public PhotonType() {
        super(false, PhotonOptions.DESERIALIZER);
    }

    @Override
    public Codec<PhotonOptions> codec() {
        return PhotonOptions.CODEC;
    }
}
