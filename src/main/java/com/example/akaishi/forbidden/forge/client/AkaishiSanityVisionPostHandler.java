package com.example.akaishi.forbidden.forge.client;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.effect.UnnameableClientAmbience;
import com.example.akaishi.forbidden.sanity.ClientSanityVision;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 低理智视野表现的后处理驱动（仅客户端）：把 {@code sanity_vision} 链按需挂到 {@code GameRenderer}。
 *
 * <p><b>与「不可名状」后处理的关系：互斥 + 不可名状优先</b>。依据：Minecraft 的
 * {@code GameRenderer} 同一时刻<b>只持有一条 {@link PostChain}</b>（{@code loadEffect} 会先把旧的
 * {@code shutdownEffect}），因此两条自定义链无法并存。三个可选方案里：
 * <ul>
 *   <li>并入同一条链 ⇒ 必须改 {@code unnameable.fsh}（用户明确要求不动既有语义，且会把
 *       "不可名状的电视机花白"与"低理智的灰化血丝"两套视觉语言搅在一起）；</li>
 *   <li>每帧互相顶掉 ⇒ 两条链在同一 tick 互相 {@code loadEffect}，渲染目标被反复重建（性能与画面都是灾难）；</li>
 *   <li><b>互斥 + 优先级</b> ⇒ 不可名状生效时低理智表现让位（"更强的精神错乱盖过较弱的表现"，
 *       且不可名状的施放本身就是理智低谷的常见伴随状态）。取此案。</li>
 * </ul>
 * 实现上不会误关原版/他模组的链：只在"当前挂的确实是自己这条"时才 {@code shutdownEffect}
 * （与 {@code AkaishiUnnameablePostHandler} 同一范式，按 {@link PostChain#getName()} 比对）。
 */
public final class AkaishiSanityVisionPostHandler {

    public static final AkaishiSanityVisionPostHandler INSTANCE = new AkaishiSanityVisionPostHandler();

    /** 自定义后处理链资源：PostChain 直接以该完整路径取资源 */
    private static final ResourceLocation EFFECT =
            new ResourceLocation(AkaishiMod.MOD_ID, "shaders/post/sanity_vision.json");

    /** 与「不可名状」对方约定的同一阈值：高于它就算对方在生效 */
    private static final float UNNAMEABLE_PRIORITY_LEVEL = 0.02f;

    private AkaishiSanityVisionPostHandler() {
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        // 推进三个强度（渲染线程只读，避免在渲染中途改渲染目标破坏 GL 状态）
        ClientSanityVision.update();
        Minecraft minecraft = Minecraft.getInstance();
        boolean ours = isOursLoaded(minecraft);
        // 不可名状优先：它生效时本表现整体让位（不加载、也不去抢）
        boolean suppressed = UnnameableClientAmbience.peek() > UNNAMEABLE_PRIORITY_LEVEL;
        boolean want = minecraft.level != null && !suppressed && ClientSanityVision.active();
        if (want && !ours) {
            minecraft.gameRenderer.loadEffect(EFFECT);
        } else if (!want && ours) {
            minecraft.gameRenderer.shutdownEffect();
        }
    }

    /** 当前挂载的是否为本模组这条低理智链：避免误关原版旁观后处理或「不可名状」链 */
    private static boolean isOursLoaded(Minecraft minecraft) {
        PostChain current = minecraft.gameRenderer.currentEffect();
        return current != null && EFFECT.toString().equals(current.getName());
    }
}
