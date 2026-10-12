package com.example.akaishi.forbidden.forge.client;

import com.example.akaishi.forbidden.wand.WandSocketC2S;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

/**
 * 咒核镶嵌按键（客户端 · TC4 法术焦点式）：手持法杖按 <b>R</b>（可改键）
 * 把背包里的咒核装上 / 轮换（装卸免费）。
 *
 * <p><b>消费口径</b>：{@code while (consumeClick())} 吃掉累计点击再发包 ——
 * 一帧内多次按下也只按次数逐次发（与原版按键一致），不会丢按或重复残留。
 */
public final class WandSocketKeybind {

    private static final String CATEGORY = "key.category.forbidden_hex";
    private static final String NAME = "key.forbidden_hex.wand_socket";

    private static final KeyMapping MAPPING = new KeyMapping(NAME,
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);

    /**
     * 运行期部分（forge 总线）：点击消费 + 把真实键名注入 tooltip 提示。
     *
     * <p><b>与 {@link #registerKeyMapping} 分开的原因</b>：那个必须在<b>模组构造阶段</b>就被调用
     * （mod 总线注册类事件不保证晚于 {@code FMLClientSetupEvent}），
     * 而这个走的是运行期 forge 总线，放哪儿都行。
     */
    public static void registerRuntime(IEventBus forgeBus) {
        forgeBus.register(INSTANCE);
        // tooltip 键名提示：注入真实键位显示（common 不引客户端类，跨层走注入）
        WandSocketC2S.installKeyHint(MAPPING::getTranslatedKeyMessage);
    }

    /** 键位注册（mod 总线 · {@code RegisterKeyMappingsEvent}） */
    public static void registerKeyMapping(RegisterKeyMappingsEvent event) {
        // 1.20.1 Forge 的 register 返回 void：实例先行构造、注册时交给事件持有
        event.register(MAPPING);
    }

    /** 当前键位（tooltip 提示用） */
    public static KeyMapping mapping() {
        return MAPPING;
    }

    private static final WandSocketKeybind INSTANCE = new WandSocketKeybind();

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        // 界面打开时不消费（聊天/容器里按 R 不该触发镶嵌）
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }
        while (MAPPING.consumeClick()) {
            WandSocketC2S.sendSocketRequest();
        }
    }

    /** 换服/退服时清掉累计点击（防跨会话残留一发包） */
    @SubscribeEvent
    public void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        while (MAPPING.consumeClick()) {
            // 清空
        }
    }
}
