package com.example.akaishi.forbidden.magic.cast;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.magic.ClientCastState;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 施法状态同步（S2C · <b>即时事件包</b>）：服务端状态机每次转向，就把新状态推给施法者本人。
 *
 * <p><b>载荷只有一个 varint</b>（{@link MagicCastState} 序号）—— 状态是闭集、且只有四个取值，
 * 没必要带任何上下文；客户端只镜像它，不自行推断。
 *
 * <p><b>发送时机</b>（见 {@code MagicCastManager}）：进入前摇、前摇转持续段、正常结束、被动打断。
 * <b>只在转向时发</b>，不做周期心跳：状态是"事件"不是"数值"，没有心跳的必要。
 */
public final class MagicCastStateS2C {

    public static final ResourceLocation CHANNEL = new ResourceLocation(AkaishiMod.MOD_ID, "cast_state");

    private MagicCastStateS2C() {
    }

    /** 服务端：向该玩家的客户端推一次状态（施法者本人；其他人不需要知道） */
    public static void send(ServerPlayer player, MagicCastState state) {
        if (player == null || state == null) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(state.ordinal());
        NetworkManager.sendToPlayer(player, CHANNEL, buf);
    }

    /** 客户端注册接收器（{@code AkaishiForbiddenMod.init} 的 Env.CLIENT 分支调用） */
    public static void registerClient() {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, CHANNEL, (buf, context) -> {
            int ordinal = buf.readVarInt();
            // 网络线程只搬运，落地调度到客户端主线程（与渲染线程互斥，避免半帧状态）
            Minecraft.getInstance().execute(() -> ClientCastState.set(ordinal));
        });
    }
}
