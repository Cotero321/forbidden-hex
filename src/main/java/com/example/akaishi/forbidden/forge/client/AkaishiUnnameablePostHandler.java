package com.example.akaishi.forbidden.forge.client;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.effect.UnnameableClientAmbience;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 「不可名状」后处理（仅客户端）：效果生效期间把自定义后处理链挂到
 * {@code GameRenderer}，对整帧画面（含手持物）提升对比度并叠加电视机花白。
 * <p>
 * 采用原版后处理管线而非自绘 HUD 的原因：原版后处理在世界与手持物渲染完成之后、
 * GUI 之前统一执行，能覆盖全画面；窗口缩放由 {@code GameRenderer.resize} 自动
 * 同步渲染目标尺寸。挂载点选在客户端 tick 而非渲染阶段，避免在渲染中途创建
 * 渲染目标破坏 GL 状态。
 */
public final class AkaishiUnnameablePostHandler {

    public static final AkaishiUnnameablePostHandler INSTANCE = new AkaishiUnnameablePostHandler();

    /** 自定义后处理链资源：PostChain 直接以该完整路径取资源 */
    private static final ResourceLocation EFFECT =
            new ResourceLocation(AkaishiMod.MOD_ID, "shaders/post/unnameable.json");

    /** 与画面表现层保持同一阈值，避免残留淡影 */
    private static final float MIN_LEVEL = 0.02f;

    private AkaishiUnnameablePostHandler() {
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        boolean ours = isOursLoaded(minecraft);
        boolean want = minecraft.level != null && UnnameableClientAmbience.peek() > MIN_LEVEL;
        if (want && !ours) {
            minecraft.gameRenderer.loadEffect(EFFECT);
        } else if (!want && ours) {
            minecraft.gameRenderer.shutdownEffect();
        }
    }

    /** 当前挂载的是否为本模组的效果：避免误关旁观生物等原版后处理 */
    private static boolean isOursLoaded(Minecraft minecraft) {
        PostChain current = minecraft.gameRenderer.currentEffect();
        return current != null && EFFECT.toString().equals(current.getName());
    }
}
