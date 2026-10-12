package com.example.akaishi.forbidden.magic.particle;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;

/** 光子参数：完整 RGB；size 是尺寸倍率，speed 是初速度倍率，不改变寿命。 */
public record PhotonOptions(int variant, int rgb, int packedStyle) implements ParticleOptions {
    public static final Codec<PhotonOptions> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("variant").forGetter(PhotonOptions::variant),
            Codec.INT.fieldOf("rgb").forGetter(PhotonOptions::rgb),
            Codec.INT.fieldOf("style").forGetter(PhotonOptions::packedStyle)
    ).apply(instance, PhotonOptions::new));
    public static final ParticleOptions.Deserializer<PhotonOptions> DESERIALIZER = new ParticleOptions.Deserializer<>() {
        @Override
        public PhotonOptions fromNetwork(ParticleType<PhotonOptions> type, FriendlyByteBuf buf) {
            return new PhotonOptions(buf.readInt(), buf.readInt(), buf.readInt());
        }

        @Override
        public PhotonOptions fromCommand(ParticleType<PhotonOptions> type, StringReader reader)
                throws CommandSyntaxException {
            reader.expect(' ');
            int variant = reader.readInt();
            reader.expect(' ');
            int rgb = reader.readInt();
            reader.expect(' ');
            float size = reader.readFloat();
            reader.expect(' ');
            float speed = reader.readFloat();
            return of(variant, rgb, size, speed);
        }
    };

    /** 两倍率按百分之一精度存储，范围 0.01..2.55。命令 RGB 使用十进制 0xRRGGBB 数值。 */
    public static PhotonOptions of(int variant, int rgb, float size, float speed) {
        int sizeByte = Math.max(1, Math.min(255, Math.round(size * 100f)));
        int speedByte = Math.max(1, Math.min(255, Math.round(speed * 100f)));
        return new PhotonOptions(variant, rgb & 0xFFFFFF, sizeByte << 8 | speedByte);
    }

    public float sizeMul() {
        return 0.01f * (packedStyle >>> 8 & 0xFF);
    }

    public float speedMul() {
        return 0.01f * (packedStyle & 0xFF);
    }

    @Override
    public ParticleType<?> getType() {
        return AkaishiMagicParticles.photon().get();
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buf) {
        buf.writeInt(variant);
        buf.writeInt(rgb);
        buf.writeInt(packedStyle);
    }

    @Override
    public String writeToString() {
        return "forbidden_hex:photon " + variant + " " + rgb + " " + sizeMul() + " " + speedMul();
    }
}
