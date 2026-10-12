package com.example.akaishi.forbidden.magic.particle;

import com.mojang.brigadier.StringReader;
import com.mojang.serialization.Codec;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;

/**
 * 法术微光的粒子参数：<b>形态（墨优先，回退源）+ 颜色（墨）</b>。
 *
 * <p>打包成<b>一个 int</b> 上网络：高 8 位形态、低 24 位 RGB。
 * 参数少且结构固定，没必要为它开一套多字段序列化 —— 一个 int 同时够 {@code Codec} 与网络用。
 * 形态下标的约定见 {@link AkaishiMagicParticles}（0..3 源、4..14 墨）。
 */
public record SpellMoteOptions(int variant, int rgb) implements ParticleOptions {

    public static final Codec<SpellMoteOptions> CODEC =
            Codec.INT.xmap(SpellMoteOptions::unpack, SpellMoteOptions::packed);

    /** 反序列化器（1.20.1 的 {@link ParticleType} 构造器要求提供它；网络与命令两条入口）。 */
    public static final ParticleOptions.Deserializer<SpellMoteOptions> DESERIALIZER =
            new ParticleOptions.Deserializer<>() {
                @Override
                public SpellMoteOptions fromNetwork(ParticleType<SpellMoteOptions> type, FriendlyByteBuf buf) {
                    return unpack(buf.readInt());
                }

                @Override
                public SpellMoteOptions fromCommand(ParticleType<SpellMoteOptions> type, StringReader reader) {
                    return new SpellMoteOptions(0, 0xFFFFFF);
                }
            };

    public static SpellMoteOptions unpack(int packed) {
        return new SpellMoteOptions(packed >>> 24 & 0xFF, packed & 0xFFFFFF);
    }

    public int packed() {
        return (variant & 0xFF) << 24 | rgb & 0xFFFFFF;
    }

    @Override
    public ParticleType<?> getType() {
        return AkaishiMagicParticles.mote().get();
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buf) {
        buf.writeInt(packed());
    }

    @Override
    public String writeToString() {
        return "forbidden_hex:spell_mote[" + variant + "," + Integer.toHexString(rgb) + "]";
    }
}
