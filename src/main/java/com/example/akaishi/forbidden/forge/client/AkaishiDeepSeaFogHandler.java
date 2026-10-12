package com.example.akaishi.forbidden.forge.client;

import com.example.akaishi.config.ModConfig;
import com.example.akaishi.forbidden.sanity.ClientDeepSeaLock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 深海视野锁定（仅客户端）：玩家身处深海（主世界 + 海洋群系 + {@code Y < 0}）时，
 * 用雾效把可视距离大幅压近并染成深水暗青，表现"被深水压住视力"。
 * 照 {@link AkaishiDecayFogHandler} 的写法挂在 {@code ViewportEvent.RenderFog} / {@code ComputeFogColor}。
 *
 * <p><b>与既有衰竭区雾效的叠加口径（本类刻意不读对方状态）</b>：
 * <ul>
 *   <li><b>雾距 = 叠乘</b>。{@code RenderFog} 事件每帧都由原版<b>重新算出</b>基准值后再发射，
 *       两个 handler 各自只对事件当前值做一次乘法，结果与处理顺序无关（乘法交换），
 *       不存在"先后覆盖"或跨帧累积 ⇒ 不会有抖动。</li>
 *   <li><b>雾色 = 各自朝自己的目标色插值</b>。两边都不读对方的强度、不改对方的状态，
 *       因此谁也不会把对方推向反复横跳；两个目标色（污染暗紫灰 / 深水暗青）都偏暗，
 *       同时生效时读作"深水里的污染"，且同一帧内处理顺序固定（同一事件总线、注册顺序固定）。</li>
 *   <li><b>为什么不"取强者"</b>：取强者要读对方的强度，而 {@code DecayClientAmbience.current()}
 *       自带平滑副作用（每调一次就推进一帧过渡），被第二个读端再调一次会让对方的过渡提速一倍
 *       —— 那才是真正的互相打架。故本类<b>完全不碰</b>衰减区的状态，只做自己的那一份。</li>
 *   <li>只在 {@code FOG_TERRAIN} 生效（天空雾不动），与衰减区雾效同口径。</li>
 * </ul>
 *
 * <p>数值均为<b>待调手感值</b>。
 */
public final class AkaishiDeepSeaFogHandler {

    public static final AkaishiDeepSeaFogHandler INSTANCE = new AkaishiDeepSeaFogHandler();

    /** 满强度时的雾距收拢比例：远距收到 15%（水下原版远距约 96 格 ⇒ 约 14 格可视） */
    private static final float FAR_SHRINK = 0.85f;
    /** 满强度时的近距收拢比例（近距保持很小，避免"整屏贴脸"） */
    private static final float NEAR_SHRINK = 0.35f;
    /** 深水雾目标色（RGB 0~1）：比原版水下雾更暗更黑，读作"深处" */
    private static final float FOG_R = 0.03f;
    private static final float FOG_G = 0.09f;
    private static final float FOG_B = 0.12f;

    /** 深海判定：Y 上界（不含），与规则侧"Y<0"同口径 */
    private static final int DEEP_Y_MAX = 0;

    private AkaishiDeepSeaFogHandler() {
    }

    /** 客户端 tick：重算目标强度（贵操作——群系采样——每 20t 才做一次） */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            ClientDeepSeaLock.setTarget(0f);
            return;
        }
        boolean deep = ModConfig.sanityEnabled
                && minecraft.level.dimension() == Level.OVERWORLD
                && player.getY() < DEEP_Y_MAX
                && minecraft.level.getBiome(player.blockPosition()).is(BiomeTags.IS_OCEAN);
        ClientDeepSeaLock.setTarget(deep ? 1f : 0f);
    }

    @SubscribeEvent
    public void onRenderFog(ViewportEvent.RenderFog event) {
        float level = ClientDeepSeaLock.current();
        // 仅地形雾生效；雾距修改必须取消事件才会被采用
        if (level <= ClientDeepSeaLock.MIN_VISIBLE || event.getMode() != FogRenderer.FogMode.FOG_TERRAIN) {
            return;
        }
        event.setNearPlaneDistance(event.getNearPlaneDistance() * (1f - NEAR_SHRINK * level));
        event.setFarPlaneDistance(event.getFarPlaneDistance() * (1f - FAR_SHRINK * level));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        float level = ClientDeepSeaLock.current();
        if (level <= ClientDeepSeaLock.MIN_VISIBLE) {
            return;
        }
        event.setRed(event.getRed() + (FOG_R - event.getRed()) * level);
        event.setGreen(event.getGreen() + (FOG_G - event.getGreen()) * level);
        event.setBlue(event.getBlue() + (FOG_B - event.getBlue()) * level);
    }
}
