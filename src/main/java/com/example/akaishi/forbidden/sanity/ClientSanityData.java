package com.example.akaishi.forbidden.sanity;

import com.example.akaishi.forbidden.api.sanity.SanityValues;
import com.example.akaishi.forbidden.api.sanity.SyncPayloadTool;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * 客户端理智只读镜像：由 S2C 同步包（{@link SanitySyncS2C}）填充，供 HUD 等客户端表现读取。
 *
 * <p><b>为什么必须镜像而不能各查各的</b>：理智是服务端权威值，客户端不跑结算，
 * 一旦让表现层自行拼凑（并发的网络线程 + 渲染线程），就会出现同帧内的自相矛盾数据。
 * 镜像把"最近一次权威快照"冻结成不可变对象，读端只读它。
 *
 * <p><b>未收到首包时的初值</b>：取内置默认（SAN/SANC = 100，其余 0）而不是全零——
 * 首包到达前若按 0 显示，玩家每次登录都会看到一瞬间的"空理智条"，比默认满值更刺眼；
 * 需要严格判断"是否已有权威数据"的表现层可先问 {@link #hasData()}。
 *
 * <p>写入只发生在客户端主线程（接收器已调度），读取可能在渲染线程，故快照字段为 volatile。
 */
public final class ClientSanityData {

    /** 未收到首包时的兜底快照（= 内置默认状态） */
    private static final SanityValues DEFAULT = new SanityValues(
            SanityState.DEFAULT_SAN, SanityState.DEFAULT_SANC, SanityState.DEFAULT_COG, 0f, 0f);

    private static volatile SanityValues values = DEFAULT;
    private static volatile Map<ResourceLocation, Integer> attached = Map.of();
    private static volatile boolean received;

    private ClientSanityData() {
    }

    /** 解析同步包并更新镜像（必须在客户端主线程调用） */
    public static void apply(FriendlyByteBuf buf) {
        float san = buf.readFloat();
        float sanc = buf.readFloat();
        float cog = buf.readFloat();
        float protection = buf.readFloat();
        float tempCut = buf.readFloat();
        // 附加载荷段：按 API 约定置于包尾（读到多少条由段内自述长度决定）
        Map<ResourceLocation, Integer> extra = SyncPayloadTool.read(buf);
        values = new SanityValues(san, sanc, cog, protection, tempCut);
        attached = extra;
        received = true;
    }

    /** 最近一次权威快照（永不 null） */
    public static SanityValues snapshot() {
        return values;
    }

    /** 附属搭车下发的附加载荷（不可变映射，永不为 null） */
    public static Map<ResourceLocation, Integer> attached() {
        return attached;
    }

    /** 是否已收到过至少一个同步包（未收到时 {@link #snapshot()} 是内置默认值） */
    public static boolean hasData() {
        return received;
    }
}
