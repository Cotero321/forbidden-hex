package com.example.akaishi.forbidden.sanity;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.api.sanity.SyncPayloadTool;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * 理智数值同步（S2C）：服务端权威值 → 客户端只读镜像 {@link ClientSanityData}。
 *
 * <p><b>包字段顺序（固定，尾段是附属搭车槽位）</b>：
 * <ol>
 *   <li>{@code float san} / {@code sanc} / {@code cog} / {@code protection} / {@code tempCut}（五个浮点）；</li>
 *   <li>{@link SyncPayloadTool} 的附加载荷段——<b>必须置于包尾</b>，
 *       因为该段自带条数；对端版本不一致导致条数错读时，只丢附加段而不会连累后续字段（后面没有字段）。</li>
 * </ol>
 *
 * <p><b>为什么"变化才发 + 最小间隔"</b>：理智会被食补逐 tick 微调（后续段落），
 * 若每 tick 无脑推，一个 100 人服的理智包开销会跟玩家移速同步包同量级，纯属浪费带宽。
 * 设计：
 * <ul>
 *   <li><b>变化检测</b>：任何数值真的落地变化时 {@link SanityState#markDirty()} 打脏（在
 *       {@link SanityServiceImpl} 的写入漏斗里，唯一入口）；未变化的玩家直接跳过，不做任何序列化；</li>
 *   <li><b>最小间隔兜底</b>：脏标记是<b>粘滞</b>的（不会过期），但同一玩家两次下发间隔不得小于
 *       {@link #MIN_SEND_INTERVAL_TICKS}，避免"一跳多改"造成包风暴；</li>
 *   <li><b>时延上界</b>：脏标记在下一个满足间隔的服务端 tick 立即下发，
 *       故最坏时延 = 最小间隔（0.5s），不会出现"变了但永远没发"。</li>
 * </ul>
 *
 * <p><b>最坏发包频率估算</b>（同玩家）：1 / 0.5s = <b>2 包/秒</b>。
 * 单包净荷 = 5×4 字节 + 条数 VarInt(1，无附加条目) + 通道开销 ≈ 22 字节；
 * 100 人在线的最坏总量 ≈ 200 包/秒 ≈ 4.4 KB/s。相对本项目已有的每 20 tick 强度同步
 * （{@code DecayZoneSync}）量级相当，可忽略。
 */
public final class SanitySyncS2C {

    public static final ResourceLocation CHANNEL = new ResourceLocation(AkaishiMod.MOD_ID, "sanity_state");

    /** 同一玩家两次下发的最小间隔（tick）：0.5s——HUD 读数的刷新率足够，又不会一改一包 */
    public static final int MIN_SEND_INTERVAL_TICKS = 10;

    /** 本轮核心不向附加载荷写入任何条目；该段恒为"空段"但必须存在（附属按同一工具读回） */
    private static final Map<ResourceLocation, Integer> CORE_PAYLOAD = Map.of();

    private SanitySyncS2C() {
    }

    /** 服务端每个维度每 tick 调用（由 AkaishiMod.init 的 SERVER_LEVEL_POST 驱动） */
    public static void serverTick(ServerLevel level) {
        long now = level.getGameTime();
        for (ServerPlayer player : level.players()) {
            SanityState state = SanityServiceImpl.state(player);
            if (state == null || !state.syncDirty()) {
                continue; // 数值未变化：不序列化、不发包
            }
            if (now - state.lastSyncTick() < MIN_SEND_INTERVAL_TICKS) {
                continue; // 节流：脏标记保留，下个满足间隔的 tick 再发
            }
            sendToPlayer(player, state, now);
        }
    }

    /** 立即向指定玩家下发当前理智快照（登录首推/调试指令用；同样写回同步簿记） */
    public static void sendToPlayer(ServerPlayer player, SanityState state, long gameTime) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeFloat(state.san());
        buf.writeFloat(state.sanc());
        buf.writeFloat(state.cog());
        buf.writeFloat(state.protection());
        buf.writeFloat(state.tempCut());
        // 附加载荷段置于包尾（约定见 SyncPayloadTool 的 javadoc）
        SyncPayloadTool.write(buf, CORE_PAYLOAD);
        NetworkManager.sendToPlayer(player, CHANNEL, buf);
        state.markSynced(gameTime);
    }

    /** 客户端注册接收器（AkaishiMod.init 的 Env.CLIENT 分支调用） */
    public static void registerClient() {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, CHANNEL, (buf, context) -> {
            // 网络线程只搬运缓冲区，解析与落地调度到客户端主线程（避免与渲染并发）
            buf.retain();
            Minecraft.getInstance().execute(() -> {
                try {
                    ClientSanityData.apply(buf);
                } finally {
                    buf.release();
                }
            });
        });
    }
}
