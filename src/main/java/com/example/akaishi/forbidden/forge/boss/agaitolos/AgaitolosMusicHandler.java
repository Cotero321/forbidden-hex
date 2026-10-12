package com.example.akaishi.forbidden.forge.boss.agaitolos;

import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

/**
 * 阿盖托洛丝战斗音乐管理器（仅客户端 HUD 之外的第二类"客户端表现"）：每 tick 决定"要不要有音乐 / 换成哪一只"。
 * <p>
 * <b>与血条同口径</b>：客户端世界里的实体是服务端按 {@code clientTrackingRange} 同步过来的，
 * 扫到实体 ≈ 玩家看得见它，因此不需要任何自定义网络包（与
 * {@link AgaitolosBossBarOverlay} 的目标选取逻辑同源，半径口径一致）。
 * <p>
 * <b>为什么在客户端管</b>：音乐是<b>位置循环音</b>且必须"立刻停"，只有客户端的
 * {@link AgaitolosThemeSound}（{@code TickableSoundInstance}）能满足；服务端播出去就收不回来。
 * <p>
 * <b>单条保证</b>：本类只持有一个 {@code current} 实例字段 —— 同场多只 BOSS 也只会有一条音轨在响。
 * <p>
 * <b>状态自愈</b>：{@code current} 一旦已停（BOSS 死了 / 自己超距停了），下次 tick 就会被清掉重建，
 * 不会出现"字段里挂着一条已经不在响的实例"导致不再播放。
 */
public final class AgaitolosMusicHandler {

    public static final AgaitolosMusicHandler INSTANCE = new AgaitolosMusicHandler();

    /**
     * 音乐播放 / 维持半径（格）。
     * <p>取值与 sounds.json 里 {@code attenuation_distance: 64} <b>同值</b>：衰减范围和
     * "该不该继续放"用同一口径，避免出现"还听着有声音但已经判定为超距"的撕裂感。
     * <p>待调手感值 / P8 转配置项。
     */
    private static final double MUSIC_RADIUS = 64.0D;

    /** 音乐音量（MUSIC 通道之后还会再乘玩家"音乐"滑块音量）。待调手感值 / P8 转配置项 */
    private static final float MUSIC_VOLUME = 0.8F;

    /** 音乐音高（1.0 = 原速）。待调手感值 / P8 转配置项 */
    private static final float MUSIC_PITCH = 1.0F;

    /** 当前音轨：null = 没有在放。同一时刻只可能是一条 */
    private AgaitolosThemeSound current;
    /** current 绑定的 BOSS：用于判断"沿用还是换一条" */
    private AgaitolosEntity bound;

    private AgaitolosMusicHandler() {
    }

    /** 客户端 tick（末尾阶段）：实体位置与存活状态此时已定，不会拿半帧旧状态做判断 */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        // 回主菜单 / 世界已卸载：收掉音乐并清空字段（否则会残留一条已失效实例，拦住后续播放）
        if (player == null || minecraft.level == null) {
            stop();
            return;
        }
        // 已有音轨且绑定 BOSS 仍可听：沿用（不重启、不换源 —— 多只 BOSS 之间也不会来回抢一条音轨）
        if (current != null && !current.isStopped() && isAudible(player, bound)) {
            return;
        }
        stop();
        AgaitolosEntity target = findNearest(player);
        if (target == null) {
            return;
        }
        AgaitolosThemeSound sound = new AgaitolosThemeSound(
                target, MUSIC_VOLUME, MUSIC_PITCH, MUSIC_RADIUS, player.getRandom().nextLong());
        current = sound;
        bound = target;
        minecraft.getSoundManager().play(sound);
    }

    private void stop() {
        if (current != null) {
            current.requestStop();
            current = null;
        }
        bound = null;
    }

    /** 半径内最近的一只"存活且未被移除"的 BOSS；一只都没有则返回 null */
    private static AgaitolosEntity findNearest(LocalPlayer player) {
        Level level = player.level();
        List<AgaitolosEntity> candidates = level.getEntitiesOfClass(AgaitolosEntity.class,
                player.getBoundingBox().inflate(MUSIC_RADIUS));
        AgaitolosEntity nearest = null;
        double nearestSqr = Double.MAX_VALUE;
        for (AgaitolosEntity candidate : candidates) {
            if (!isAliveBoss(candidate)) {
                continue;
            }
            double distanceSqr = candidate.distanceToSqr(player);
            if (distanceSqr < nearestSqr) {
                nearestSqr = distanceSqr;
                nearest = candidate;
            }
        }
        return nearest;
    }

    /** 存活且未被移除；{@code isDeadOrDying()} 在血量归零那一刻即为 true（覆盖 {@code /kill}） */
    private static boolean isAliveBoss(AgaitolosEntity boss) {
        return boss != null && !boss.isRemoved() && !boss.isDeadOrDying() && boss.isAlive();
    }

    /** 绑定 BOSS 是否仍可听：同维度 + 半径内 + 存活（任一不满足即换 / 停） */
    private static boolean isAudible(LocalPlayer player, AgaitolosEntity boss) {
        return isAliveBoss(boss)
                && boss.level() == player.level()
                && player.distanceToSqr(boss) <= MUSIC_RADIUS * MUSIC_RADIUS;
    }
}
