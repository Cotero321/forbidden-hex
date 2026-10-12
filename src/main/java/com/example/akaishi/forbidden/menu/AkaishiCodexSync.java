package com.example.akaishi.forbidden.menu;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.codex.CodexCondition;
import com.example.akaishi.forbidden.codex.CodexGates;
import com.example.akaishi.forbidden.codex.CodexNode;
import com.example.akaishi.forbidden.codex.CodexNodeState;
import com.example.akaishi.forbidden.codex.CodexReward;
import com.example.akaishi.forbidden.codex.CodexService;
import com.example.akaishi.forbidden.codex.CodexTable;
import com.example.akaishi.forbidden.sanity.SanityServiceImpl;
import com.example.akaishi.forbidden.sanity.SanityState;

import dev.architectury.networking.NetworkManager;

import io.netty.buffer.Unpooled;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * 禁忌秘典网络包（S2C 进度快照 + C2S 研究请求）。
 *
 * <p><b>节点表本身不下发</b>：节点表是 common 的静态数据（{@link CodexTable}），两端同一份二进制，
 * 客户端直接读本地表取名称/描述/分类/阶段数；下发的只有<b>服务端权威的进度与条件求值结果</b>
 * （认知值、首见记档、前置节点状态都只存在服务端存档里，客户端算不出来）。
 *
 * <p><b>触发时机</b>：不做每 tick 发包。只在①菜单打开（首次 broadcastChanges）、
 * ②每次研究请求被处理完之后，各推一次（见 {@link AkaishiCodexMenu#broadcastChanges()}）。
 *
 * <p><b>条数钳制</b>：解码端不信任对端声明的条数，按 {@link #MAX_NODES} / {@link #MAX_CONDITIONS}
 * 钳制（照 {@code AkaishiMiniMatrixSync} 的范式，畸形包不会让客户端按大数预分配内存）。
 */
public final class AkaishiCodexSync {

    /** S2C：秘典进度快照 */
    public static final ResourceLocation SNAPSHOT_CHANNEL =
            new ResourceLocation(AkaishiMod.MOD_ID, "codex_snapshot");
    /** C2S：研究/仪式请求 */
    public static final ResourceLocation ACTION_CHANNEL =
            new ResourceLocation(AkaishiMod.MOD_ID, "codex_action");

    /** 单次快照最多携带的节点数（本轮仅 5 个；留足扩展余量，防畸形包） */
    public static final int MAX_NODES = 64;
    /** 单节点最多携带的条件条数（节点级最多 3 条 + 阶段级按表声明的最多条数；留足余量，防畸形包） */
    public static final int MAX_CONDITIONS = 12;
    /** 单个 id 字符串长度上限（读写同口径） */
    private static final int MAX_ID = 128;

    /**
     * 单个节点的进度 + 条件快照（客户端只读）。
     *
     * @param stage      已完成的阶段数
     * @param learned    是否已学完
     * @param uses       可重复节点的已用次数
     * @param state      节点三态（{@link CodexNodeState} 的编号；画布据此决定画法）
     * @param canAct     当前是否可按下按钮（未学 = 条件全满足；已学可重复 = 次数未用尽）
     * @param conditions 当前该满足的条件清单（已学完的节点为空）
     */
    public record NodeView(String id, int stage, boolean learned, int uses, byte state, boolean canAct,
                           List<CodexCondition> conditions) {
    }

    /** 接收方：只有秘典菜单实现 */
    public interface Target {
        /** S2C：整表进度快照落地（客户端只读镜像） */
        void acceptCodex(List<NodeView> nodes);
    }

    private AkaishiCodexSync() {
    }

    // ===== C2S =====

    /** 服务端注册接收器（AkaishiMod.init 调用） */
    public static void register() {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, ACTION_CHANNEL, (buf, context) -> {
            int containerId = buf.readInt();
            String nodeId = buf.readUtf(MAX_ID);
            context.queue(() -> {
                Player player = context.getPlayer();
                // 校验：必须是打开中的秘典菜单且容器 ID 一致，拦截伪造包
                if (player instanceof ServerPlayer serverPlayer
                        && serverPlayer.containerMenu instanceof AkaishiCodexMenu menu
                        && menu.containerId == containerId) {
                    ResourceLocation id = ResourceLocation.tryParse(nodeId);
                    serverPlayer.displayClientMessage(CodexService.study(serverPlayer, id), true);
                    // 处理完立刻让下一次 broadcastChanges 推一份新进度（界面无需自行推断结果）
                    menu.markSnapshotDirty();
                }
            });
        });
    }

    /** 客户端：请求研究/推进/举行仪式（只有节点 id，门槛一律由服务端重算） */
    public static void sendStudy(int containerId, String nodeId) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(containerId);
        buf.writeUtf(nodeId == null ? "" : nodeId, MAX_ID);
        NetworkManager.sendToServer(ACTION_CHANNEL, buf);
    }

    // ===== S2C =====

    /** 客户端注册接收器（AkaishiMod.init 的客户端分支调用） */
    public static void registerClient() {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, SNAPSHOT_CHANNEL, (buf, context) -> {
            int containerId = buf.readInt();
            int declared = buf.readVarInt();
            int count = Math.min(Math.max(declared, 0), MAX_NODES);
            List<NodeView> nodes = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String id = buf.readUtf(MAX_ID);
                int stage = buf.readVarInt();
                boolean learned = buf.readBoolean();
                int uses = buf.readVarInt();
                // 三态编号按白名单解析：未知编号回退 LOCKED（不把没资格的节点画亮）
                byte state = CodexNodeState.byId(buf.readByte()).id();
                boolean canAct = buf.readBoolean();
                int declaredConditions = buf.readVarInt();
                int conditions = Math.min(Math.max(declaredConditions, 0), MAX_CONDITIONS);
                List<CodexCondition> list = new ArrayList<>(conditions);
                for (int k = 0; k < conditions; k++) {
                    list.add(new CodexCondition(buf.readByte(), buf.readByte(), buf.readBoolean(),
                            buf.readUtf(MAX_ID), buf.readFloat(), buf.readFloat()));
                }
                nodes.add(new NodeView(id, Math.max(0, stage), learned, Math.max(0, uses), state, canAct,
                        List.copyOf(list)));
            }
            // 网络线程只解码，落地回客户端主线程，避免与渲染线程并发读写
            Minecraft.getInstance().execute(() -> {
                var player = Minecraft.getInstance().player;
                if (player != null && player.containerMenu instanceof Target target
                        && player.containerMenu.containerId == containerId) {
                    target.acceptCodex(nodes);
                }
            });
        });
    }

    /** 服务端：推送一次进度快照（菜单打开 / 研究请求处理后各一次） */
    public static void sendSnapshot(ServerPlayer player, int containerId) {
        SanityState state = SanityServiceImpl.state(player);
        List<CodexNode> table = CodexTable.all();
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(containerId);
        int count = Math.min(table.size(), MAX_NODES);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            CodexNode node = table.get(i);
            String id = node.id().toString();
            int stage = state == null ? 0 : Math.min(state.codexStage(id), node.stageCount());
            boolean learned = state != null && state.hasCodexNode(id);
            int uses = state == null ? 0 : state.codexUses(id);
            // 条件只在"还没学完"时有意义；学完后按钮语义换成"举行仪式/已学完"
            List<CodexCondition> conditions = learned ? List.of()
                    : CodexGates.evaluate(player, state, node, stage);
            buf.writeUtf(id, MAX_ID);
            buf.writeVarInt(stage);
            buf.writeBoolean(learned);
            buf.writeVarInt(uses);
            buf.writeByte(CodexGates.nodeState(state, node).id());
            buf.writeBoolean(canAct(node, learned, uses, conditions));
            int written = Math.min(conditions.size(), MAX_CONDITIONS);
            buf.writeVarInt(written);
            for (int k = 0; k < written; k++) {
                CodexCondition condition = conditions.get(k);
                buf.writeByte(condition.kind());
                buf.writeByte(condition.variant());
                buf.writeBoolean(condition.satisfied());
                buf.writeUtf(condition.arg() == null ? "" : condition.arg(), MAX_ID);
                buf.writeFloat(condition.required());
                buf.writeFloat(condition.current());
            }
        }
        NetworkManager.sendToPlayer(player, SNAPSHOT_CHANNEL, buf);
    }

    /**
     * 按钮可用性（与 {@link CodexService} 的服务端校验同源，避免"按钮亮着、点下去被拒"）：
     * 未学完 = 条件全满足；已学完的可重复节点 = 次数未用尽。
     */
    private static boolean canAct(CodexNode node, boolean learned, int uses,
            List<CodexCondition> conditions) {
        if (!learned) {
            return CodexGates.allSatisfied(conditions);
        }
        CodexReward reward = node.reward();
        return reward != null && reward.type() == CodexReward.Type.RITUAL
                && uses < reward.ritualMaxUses();
    }
}
