package com.example.akaishi.forbidden.sanity.shadow;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * 影怪的表现层（粒子 + 音效），全部复用原版资源（零新增贴图/音频）。
 *
 * <p>为什么单独一个类：{@link ShadowEntity} 的职责是"状态机 + 计数 + 落位"，表现只是它的副产品；
 * 混在一起会让"改了哪个音效"和"改了哪条判定"互相干扰（本项目反复出现的"一改就是一大片"）。
 * 这里只用两条原版音效（末影人传送 / 灵魂逃逸）与一个原版粒子（soul），
 * 无需任何新增资源，也就不存在"缺图/死链"。
 */
final class ShadowFx {

    /** 消散粒子数 */
    private static final int VANISH_PARTICLES = 14;
    /** 出现粒子数 */
    private static final int APPEAR_PARTICLES = 8;

    private ShadowFx() {
    }

    /** 出现：从边缘/背后"浮现" */
    static void appear(ShadowEntity shadow) {
        burst(shadow, ParticleTypes.SOUL, APPEAR_PARTICLES, 0.25D);
        play(shadow, SoundEvents.ENDERMAN_TELEPORT, 0.5F, 1.4F);
    }

    /** 受击反馈：被"打散"的一瞬 */
    static void hit(ShadowEntity shadow) {
        burst(shadow, ParticleTypes.SOUL, 6, 0.2D);
    }

    /** 消散 / 瞬移前的消失 */
    static void vanish(ShadowEntity shadow) {
        burst(shadow, ParticleTypes.SOUL, VANISH_PARTICLES, 0.35D);
        play(shadow, SoundEvents.SOUL_ESCAPE, 0.8F, 0.9F);
    }

    /** 瞬移落位：起终两点各来一下，配合 vanish 的消失读成"挪了个位置" */
    static void blink(ServerLevel level, Vec3 from, Vec3 to) {
        level.sendParticles(ParticleTypes.SOUL, from.x, from.y + 1.0D, from.z, 8, 0.3D, 0.5D, 0.3D, 0.02D);
        level.sendParticles(ParticleTypes.SOUL, to.x, to.y + 1.0D, to.z, 8, 0.3D, 0.5D, 0.3D, 0.02D);
        level.playSound(null, to.x, to.y, to.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.6F, 1.2F);
    }

    private static void burst(ShadowEntity shadow, ParticleOptions particle, int count, double spread) {
        if (!(shadow.level() instanceof ServerLevel level)) {
            return;
        }
        level.sendParticles(particle, shadow.getX(), shadow.getY(0.6D), shadow.getZ(), count,
                spread, spread * 1.6D, spread, 0.02D);
    }

    private static void play(ShadowEntity shadow, SoundEvent sound, float volume, float pitch) {
        shadow.level().playSound(null, shadow.getX(), shadow.getY(), shadow.getZ(), sound,
                SoundSource.HOSTILE, volume, pitch);
    }
}
