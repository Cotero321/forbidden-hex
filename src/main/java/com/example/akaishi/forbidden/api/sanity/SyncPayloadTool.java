package com.example.akaishi.forbidden.api.sanity;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 理智同步包的<b>附加载荷</b>读写工具：附属把自己的 {@code Map<ResourceLocation, Integer>} 搭车下发，
 * 客户端侧原样读回。
 *
 * <p><b>为什么需要它</b>：理智同步包由核心发，附属没法自己插网络包（附属若各发一个通道，
 * 玩家移动时会成倍放大网络开销；而且附属的附加数据往往与理智状态强相关，分开同步会出现"理智已变、附属标记未到"的中间态）。
 * 核心预留一段"附加载荷"槽位，附属把状态塞进这唯一的入口，客户端按同一工具读回，
 * 两端字节序由本工具独占，不会互相踩到。
 *
 * <p><b>上限与理由</b>（写端超限不抛异常，按规则丢弃并记日志）：
 * <ul>
 *   <li>{@link #MAX_ENTRIES} = 64：理智同步是高频包（玩家移动/环境变化即推送），
 *       单包附加条目必须有界；64 条足以表达"玩家身上的理智附加状态 + 附属标记集合"，
 *       再多说明该改用持久化数据而不是每包同步；</li>
 *   <li>{@link #MAX_TOTAL_BYTES} = 4096：Minecraft 1.20.1 自定义载荷单包上限约 1 MiB，
 *       4 KiB 远低于该线，保证附加载荷永远不会把理智同步包推成"大包"
 *       （大包在同一个 tick 内抢占带宽会让玩家端出现卡顿/掉包）；</li>
 *   <li>{@link #MAX_ID_BYTES} = 128：单个 id 的 UTF-8 字节上限。正常 {@code 命名空间:路径} 远小于此，
 *       留出余量但不给"超长 id 撑爆包"的口子。</li>
 * </ul>
 *
 * <p><b>超限取舍策略</b>：<b>按 id 字典序排序后取前 N 条</b>。
 * 先排序再截断是为了确定性与稳定性——同一份状态无论 Map 迭代顺序如何，推送内容都完全一致，
 * 客户端缓存不会因为"这次少了 A 多了 B"而抖动。被丢弃的条目只记 WARN，<b>绝不抛异常</b>
 * （网络线程抛异常等于玩家掉线）。
 *
 * <p><b>调用约定</b>：本工具写入/读取的是<b>包内的一个自述长度段</b>（条数 VarInt + 各条），
 * 读到多少条由段内条数自述决定。因此<b>必须把该段放在同步包的最后</b>，
 * 这样即使对端版本不一致导致条数错读，也只会丢掉附加段而不会连累后续字段（没有后续字段）。
 *
 * <p>本类不参与任何平台专有 API，客户端与服务端都可用。
 */
public final class SyncPayloadTool {

    /** 条目数上限 */
    public static final int MAX_ENTRIES = 64;

    /** 附加段总字节上限（含条数字段） */
    public static final int MAX_TOTAL_BYTES = 4096;

    /** 单条 id 的 UTF-8 字节上限 */
    public static final int MAX_ID_BYTES = 128;

    private static final Logger LOGGER = LogManager.getLogger("akaishi.api.sanity");

    /** 条数 VarInt + 安全余量：先从预算里扣掉，保证写完不越界 */
    private static final int COUNT_FIELD_RESERVE = 2;

    private SyncPayloadTool() {
    }

    /**
     * 写入附加载荷（服务端调用）。
     *
     * <p>可容忍的坏输入一律"跳过 + 记日志"而不是抛异常：{@code buf}/{@code payload} 为 null 时写空段，
     * 条目键值任一为 null、id 非法、id 超长都会被跳过。
     *
     * <p>线程安全：无共享状态，纯函数式写入调用方传入的缓冲区（由调用方保证该缓冲区独占）。
     *
     * @return 实际写入的条目数（便于调用方判断是否发生了截断）
     */
    public static int write(FriendlyByteBuf buf, Map<ResourceLocation, Integer> payload) {
        if (buf == null) {
            return 0;
        }
        if (payload == null || payload.isEmpty()) {
            buf.writeVarInt(0);
            return 0;
        }
        // 排序容器：id 字符串 → 值。同时天然完成去重（同一 id 的重复项保留后者）
        TreeMap<String, Integer> sorted = new TreeMap<>();
        for (Map.Entry<ResourceLocation, Integer> entry : payload.entrySet()) {
            if (entry == null || entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String raw = entry.getKey().toString();
            if (utf8Length(raw) > MAX_ID_BYTES) {
                LOGGER.warn("[akaishi] 理智同步附加载荷的 id 过长（> {} 字节），已跳过：{}", MAX_ID_BYTES, raw);
                continue;
            }
            sorted.put(raw, entry.getValue());
        }

        List<String> ids = new ArrayList<>();
        int budget = MAX_TOTAL_BYTES - COUNT_FIELD_RESERVE;
        int used = 0;
        int dropped = 0;
        for (Map.Entry<String, Integer> entry : sorted.entrySet()) {
            String raw = entry.getKey();
            int idsBytes = utf8Length(raw);
            int cost = varintSize(idsBytes) + idsBytes + varintSize(entry.getValue());
            if (ids.size() >= MAX_ENTRIES || used + cost > budget) {
                dropped++;
                continue;
            }
            ids.add(raw);
            used += cost;
        }

        buf.writeVarInt(ids.size());
        for (String raw : ids) {
            buf.writeUtf(raw);
            buf.writeVarInt(sorted.get(raw));
        }
        if (dropped > 0) {
            LOGGER.warn("[akaishi] 理智同步附加载荷超限（上限 {} 条 / {} 字节），已按 id 字典序保留前 {} 条、丢弃 {} 条",
                    MAX_ENTRIES, MAX_TOTAL_BYTES, ids.size(), dropped);
        }
        return ids.size();
    }

    /**
     * 读取附加载荷（客户端调用）。
     *
     * <p>任何解析异常（缓冲区不足、条数异常、非法 id）都不会抛出：
     * 记 WARN 后返回"已成功读出的部分"或空表。<b>网络线程抛异常等于玩家掉线</b>，
     * 一个附属的坏数据不该造成这种后果。
     *
     * @return 不可变映射（可能为空，永不为 null）
     */
    public static Map<ResourceLocation, Integer> read(FriendlyByteBuf buf) {
        if (buf == null) {
            return Map.of();
        }
        Map<ResourceLocation, Integer> out = new LinkedHashMap<>();
        try {
            int declared = buf.readVarInt();
            if (declared <= 0) {
                return Map.of();
            }
            int count = declared;
            if (count > MAX_ENTRIES) {
                // 写端不可能超限 → 对端版本不一致或包被破坏；按上限截断，本段之后的字段可能已失步（故约定本段置于包尾）
                LOGGER.warn("[akaishi] 理智同步附加载荷条数异常（声明 {} 条 > 上限 {}），已按上限截断",
                        count, MAX_ENTRIES);
                count = MAX_ENTRIES;
            }
            for (int i = 0; i < count; i++) {
                String raw = buf.readUtf(MAX_ID_BYTES);
                int value = buf.readVarInt();
                ResourceLocation id = ResourceLocation.tryParse(raw);
                if (id == null) {
                    LOGGER.warn("[akaishi] 理智同步附加载荷含非法 id，已跳过该条：{}", raw);
                    continue;
                }
                out.put(id, value);
            }
        } catch (Throwable t) {
            // 捕获 Error：附属相关类缺失时抛的是 NoClassDefFoundError，不能让网络线程崩
            LOGGER.warn("[akaishi] 理智同步附加载荷读取失败，已丢弃本段附加数据：{}", t.toString());
        }
        return Map.copyOf(out);
    }

    /** UTF-8 字节数（与 {@code FriendlyByteBuf.writeUtf} 的计长方式一致） */
    private static int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    /** VarInt 编码长度（负值固定 5 字节，与 {@code FriendlyByteBuf.writeVarInt} 一致） */
    private static int varintSize(int value) {
        int size = 1;
        while ((value & 0xFFFFFF80) != 0) {
            value >>>= 7;
            size++;
        }
        return size;
    }
}
