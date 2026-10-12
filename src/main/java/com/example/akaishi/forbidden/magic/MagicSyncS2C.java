package com.example.akaishi.forbidden.magic;

import com.example.akaishi.AkaishiMod;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * 魔法状态同步（S2C）：服务端权威值 → 客户端只读镜像 {@link ClientMagicData}。
 * 设计记忆 §17.2「同步策略」：<b>事件驱动 + 低频兜底</b>，客户端按已知速率本地外推渲染。
 *
 * <p><b>包字段顺序（固定）</b>：{@code int level} → {@code float poolLife / poolDark / poolWhite}
 * → {@code float heat} → {@code boolean overloaded} → {@code String source}。
 *
 * <p><b>为什么只发这五项</b>：§11 定「静态表不下发，只同步进度与状态」。已解锁词表（{@code words}）
 * 是"进度"，但它的变化频率极低（解锁是稀有事）且体积随词数增长，逐次随包下发不划算 ——
 * 它由 <b>P3 的专用请求/全量包</b>承担（与秘典的进度快照同口径），不在本高频包内。
 *
 * <p><b>为什么"变化才发 + 最小间隔"</b>：三池会自然恢复（每 1 池量/秒），若每 tick 无脑推，
 * 一个百人服的池量包开销会与玩家移速同步同量级。
 * <ul>
 *   <li><b>变化检测</b>：任何数值真的落地变化时 {@link MagicState#markDirty()} 打脏；未变化直接跳过；</li>
 *   <li><b>最小间隔</b>：脏标记粘滞（不过期），但同一玩家两次下发间隔不小于
 *       {@link #MIN_SEND_INTERVAL_TICKS}，避免"一跳多改"造成包风暴；</li>
 *   <li><b>时延上界</b>：脏标记在下一个满足间隔的 tick 立即下发 ⇒ 最坏时延 = 最小间隔（0.5s）。</li>
 * </ul>
 */
public final class MagicSyncS2C {

    public static final ResourceLocation CHANNEL = new ResourceLocation(AkaishiMod.MOD_ID, "magic_state");

    /** 同一玩家两次下发的最小间隔（tick）：0.5s —— 条与能量显示的刷新率足够，又不会一改一包 */
    public static final int MIN_SEND_INTERVAL_TICKS = 10;

    private MagicSyncS2C() {
    }

    /** 服务端每个维度每 tick 调用（由 {@code AkaishiForbiddenMod.init} 注册） */
    public static void serverTick(ServerLevel level) {
        long now = level.getGameTime();
        for (ServerPlayer player : level.players()) {
            MagicState state = MagicStates.state(player);
            if (state == null || !state.syncDirty()) {
                continue; // 数值未变化：不序列化、不发包
            }
            if (now - state.lastSyncTick() < MIN_SEND_INTERVAL_TICKS) {
                continue; // 节流：脏标记保留，下个满足间隔的 tick 再发
            }
            // 单个玩家序列化失败不得中断其余玩家
            try {
                sendToPlayer(player, state, now);
            } catch (RuntimeException ignored) {
                // 下一拍重试；脏标记仍在
            }
        }
    }

    /** 立即向指定玩家下发当前快照（登录首推 / 调试指令用；同样写回同步簿记） */
    public static void sendToPlayer(ServerPlayer player, MagicState state, long gameTime) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(state.level());
        buf.writeFloat(state.pool(MagicStates.POOL_LIFE));
        buf.writeFloat(state.pool(MagicStates.POOL_DARK));
        buf.writeFloat(state.pool(MagicStates.POOL_WHITE));
        buf.writeFloat(state.heat());
        // 过载罚站是"持续状态"，不能由客户端从 heat 反推 ——
        // 罚站期间热值已被 −25/秒 降到 100 以下，客户端若只看 heat 会显示"不过载"，
        // 与实际"仍禁止施法"不一致。故把权威判定结果一并下发。
        buf.writeBoolean(state.overloaded(gameTime));
        buf.writeUtf(state.sourceId());
        NetworkManager.sendToPlayer(player, CHANNEL, buf);
        state.markSynced(gameTime);
    }

    /** 客户端注册接收器（{@code AkaishiForbiddenMod.init} 的 Env.CLIENT 分支调用） */
    public static void registerClient() {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, CHANNEL, (buf, context) -> {
            // 网络线程只搬运缓冲区，解析与落地调度到客户端主线程（避免与渲染并发）
            buf.retain();
            Minecraft.getInstance().execute(() -> {
                try {
                    ClientMagicData.apply(buf);
                } finally {
                    buf.release();
                }
            });
        });
    }
}
