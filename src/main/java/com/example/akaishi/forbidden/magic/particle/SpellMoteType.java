package com.example.akaishi.forbidden.magic.particle;

import com.mojang.serialization.Codec;
import net.minecraft.core.particles.ParticleType;

/**
 * 法术微光的粒子类型：<b>带参数</b>（形态 + 颜色），故不能用原版的 {@code SimpleParticleType}。
 *
 * <p>只注册这一个类型；"形态"由 {@link SpellMoteOptions#variant()} 在同一张
 * {@code particles/spell_mote.json} 的贴图列表里选下标 —— 一个类型承载全部源的贴图，
 * 避免为每个源各注册一个类型、各写一份 json。
 */
public final class SpellMoteType extends ParticleType<SpellMoteOptions> {

    public SpellMoteType() {
        super(false, SpellMoteOptions.DESERIALIZER);
    }

    @Override
    public Codec<SpellMoteOptions> codec() {
        return SpellMoteOptions.CODEC;
    }
}
