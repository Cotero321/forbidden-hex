package com.example.akaishi.sound;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

/**
 * 机器运转音播放器：每台机器持有一个独立实例，内部维护重播冷却。
 * <p>
 * 职责单一（只负责"在工作时按间隔播放本机音色"），避免各 BlockEntity 重复实现冷却逻辑。
 * 仅应在服务端 tick 中调用；{@code level.playSound(null, ...)} 会广播给附近玩家。
 */
public final class MachineHum {

    /** 默认重播间隔（tick），约 0.75s，与既有 Purifier 行为一致 */
    public static final int DEFAULT_INTERVAL = 15;

    private final RegistrySupplier<SoundEvent> sound;
    private final float volume;
    private final float pitch;
    private final int interval;
    /** 初始为 1，使机器一进入工作状态立即发声 */
    private int cooldown = 1;

    public MachineHum(RegistrySupplier<SoundEvent> sound, float volume, float pitch) {
        this(sound, volume, pitch, DEFAULT_INTERVAL);
    }

    public MachineHum(RegistrySupplier<SoundEvent> sound, float volume, float pitch, int interval) {
        this.sound = sound;
        this.volume = volume;
        this.pitch = pitch;
        this.interval = Math.max(1, interval);
    }

    /** 机器正在工作时调用；未工作时不要调用，冷却会自然保持 */
    public void tick(Level level, BlockPos pos) {
        if (--cooldown <= 0) {
            level.playSound(null, pos, sound.get(), SoundSource.BLOCKS, volume, pitch);
            cooldown = interval;
        }
    }

    /**
     * 重置冷却使下一个 tick 立即发声。
     * 用于"停机后重新启用"：停用期间 tick 不被调用，冷却会冻结在 interval，
     * 若直接复用需空等一整段间隔（长循环氛围音可达数秒）才重新出声。
     */
    public void restart() {
        cooldown = 1;
    }

    /**
     * 一次性播放机器音：用于按钮锻造 / 融合 / 仪式等单次动作（非循环运转），不占冷却。
     * 仅服务端调用，{@code level.playSound(null, ...)} 会广播给附近玩家。
     */
    public static void playOnce(Level level, BlockPos pos, RegistrySupplier<SoundEvent> sound, float volume, float pitch) {
        if (level == null || level.isClientSide) {
            return;
        }
        level.playSound(null, pos, sound.get(), SoundSource.BLOCKS, volume, pitch);
    }
}
