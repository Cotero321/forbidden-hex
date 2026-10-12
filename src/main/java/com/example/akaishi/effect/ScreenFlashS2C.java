package com.example.akaishi.effect;

import com.example.akaishi.AkaishiMod;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 屏幕泛红同步（S2C）：服务端在关键瞬间推一次表现信号，客户端播完整段包络。
 * <p>
 * 复用点：侵蚀跑满（D100/D103/D208）、巨坛吸取被吸反馈（D180，服务端每 5s 节流）。
 * 之所以走网络而非客户端自行判定：表现由服务端事件驱动，客户端不具备该事件的时间点。
 */
public final class ScreenFlashS2C {

    public static final ResourceLocation CHANNEL = new ResourceLocation(AkaishiMod.MOD_ID, "screen_flash");

    private ScreenFlashS2C() {
    }

    /** 客户端注册接收器（AkaishiMod.init 的 Env.CLIENT 分支调用） */
    public static void registerClient() {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, CHANNEL, (buf, context) -> {
            int ticks = buf.readVarInt();
            float intensity = buf.readFloat();
            // 网络线程读取后调度回客户端主线程，与渲染线程的读取解耦
            Minecraft.getInstance().execute(() -> ScreenFlashClient.trigger(ticks, intensity));
        });
    }

    /** 服务端：向玩家推一次泛红（ticks = 持续时长，intensity = 峰值强度 0~1） */
    public static void sendToPlayer(ServerPlayer player, int ticks, float intensity) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(ticks);
        buf.writeFloat(intensity);
        NetworkManager.sendToPlayer(player, CHANNEL, buf);
    }
}
