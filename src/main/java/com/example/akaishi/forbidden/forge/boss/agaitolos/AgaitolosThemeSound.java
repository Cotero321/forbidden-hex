package com.example.akaishi.forbidden.forge.boss.agaitolos;

import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosEntity;
import com.example.akaishi.forbidden.sound.AkaishiForbiddenSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * 阿盖托洛丝战斗音乐的<b>循环位置音效实例</b>（仅客户端），逐 tick 跟随 BOSS。
 * <p>
 * <b>为什么必须走客户端的 {@code AbstractTickableSoundInstance}</b>：
 * <ul>
 *   <li>服务端 {@code level.playSound(...)}（项目既有 {@link com.example.akaishi.sound.MachineHum} 的做法）
 *       一旦发出就<b>收不回来</b> —— 53 秒的曲子会一直放完，做不到"BOSS 一死立刻静音"；</li>
 *   <li>只有客户端 {@code TickableSoundInstance} 能逐 tick 自行判定并调用 {@code stop()}，
 *       {@code SoundEngine} 会在同一 tick 里停掉对应声道（见 {@code SoundEngine#tickNonPaused}）。</li>
 * </ul>
 * <b>为什么不放进 common</b>：{@code AbstractTickableSoundInstance} 是纯客户端类
 * （{@code net.minecraft.client.resources.sounds}），common 是零客户端引用的 Architectury 层。
 * <p>
 * <b>位置策略</b>：本类即"声音挂在 BOSS 身上" —— 不调 {@code atEntity}（那是服务端 API），
 * 而是每 tick 把 {@code x/y/z} 直接写成 BOSS 的坐标，配合 {@code Attenuation.LINEAR} 得到
 * 距离衰减。逐 tick 跟随后，BOSS 移动时声源同步移动，不会拖尾或漂移。
 * <p>
 * <b>循环</b>：53 秒素材已做成无缝（首尾相位连续），{@code looping = true} 交给
 * {@code LoopingAudioStream} 循环，接缝听不出来。
 */
public final class AgaitolosThemeSound extends AbstractTickableSoundInstance {

    /** 音乐跟随的 BOSS：本类只读它的位置与存活状态，不写任何字段 */
    private final AgaitolosEntity boss;
    /** 超出该距离（平方）即自我叫停 */
    private final double radiusSqr;

    /**
     * @param boss   BOSS 实体（客户端世界里的那一只）
     * @param volume 音量（MUSIC 通道还会再乘玩家"音乐"滑块音量）
     * @param pitch  音高
     * @param radius 播放半径（格），与 {@code AgaitolosMusicHandler.MUSIC_RADIUS} 同源
     * @param seed   随机种子：与原版一致，用于音效变体/音量抖动的可复现
     */
    AgaitolosThemeSound(AgaitolosEntity boss, float volume, float pitch, double radius, long seed) {
        // 音源用 MUSIC：自动尊重玩家"音乐"滑块，且与"唱片 / 环境音"通道分开
        super(AkaishiForbiddenSounds.AGAITOLOS_THEME.get(), SoundSource.MUSIC, RandomSource.create(seed));
        this.boss = boss;
        this.radiusSqr = radius * radius;
        this.looping = true;
        this.volume = volume;
        this.pitch = pitch;
        // 显式写出"位置音效"意图：线性距离衰减（也是 AbstractSoundInstance 的默认值）
        this.attenuation = Attenuation.LINEAR;
        updatePosition();
    }

    @Override
    public void tick() {
        if (shouldStop()) {
            // stop() 是 AbstractTickableSoundInstance 的 protected final：本 tick 内即生效，
            // SoundEngine 随后读到 isStopped() 立刻关声道，不会拖到自然结束
            stop();
            return;
        }
        updatePosition();
    }

    /** 供管理器主动叫停（绑定 BOSS 换了 / 玩家离开世界） */
    void requestStop() {
        stop();
    }

    /**
     * 自己判定"该不该继续放"：BOSS 不存活（{@code isDeadOrDying()} 在血量归零那一刻即为 true，
     * 含 {@code /kill}）/ 被移除 / 玩家换维度 / 超出半径 ⇒ 立刻停。
     */
    private boolean shouldStop() {
        if (boss.isRemoved() || boss.isDeadOrDying() || !boss.isAlive()) {
            return true;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null
                || player.level() != boss.level()
                || player.distanceToSqr(boss) > radiusSqr;
    }

    private void updatePosition() {
        this.x = boss.getX();
        this.y = boss.getY();
        this.z = boss.getZ();
    }
}
