package com.example.akaishi.forbidden.magic;

import net.minecraft.network.FriendlyByteBuf;

/**
 * 客户端魔法状态<b>只读镜像</b>：由 S2C 同步包（{@link MagicSyncS2C}）填充，供 HUD / 界面读取。
 *
 * <p><b>为什么必须镜像而不能各查各的</b>：这些都是服务端权威值，客户端不跑结算；
 * 一旦让表现层自行拼凑（网络线程 + 渲染线程并发），同帧内就会出现自相矛盾的数据。
 * 镜像把"最近一次权威快照"冻结成不可变对象，读端只读它。
 *
 * <p><b>未收到首包时的初值</b>：取内置默认（Lv1、三池满值、热值 0、默认源）而不是全零 ——
 * 首包到达前若按 0 显示，玩家每次登录都会看到一瞬间的"空池"，比默认满值更刺眼。
 * 需要严格判断"是否已有权威数据"的表现层可先问 {@link #hasData()}。
 *
 * <p>写入只发生在客户端主线程（接收器已调度），读取可能在渲染线程，故快照引用为 volatile。
 */
public final class ClientMagicData {

    /** 未收到首包时的兜底快照（= Lv1 满池、无过载的内置默认） */
    private static final Snapshot DEFAULT = new Snapshot(
            MagicState.DEFAULT_LEVEL,
            MagicState.BASE_POOL_CAPACITY, MagicState.BASE_POOL_CAPACITY, MagicState.BASE_POOL_CAPACITY,
            MagicState.HEAT_MIN, false, MagicState.DEFAULT_SOURCE);

    private static volatile Snapshot snapshot = DEFAULT;
    private static volatile boolean received;

    private ClientMagicData() {
    }

    /** 解析同步包并更新镜像（必须在客户端主线程调用） */
    public static void apply(FriendlyByteBuf buf) {
        int level = buf.readVarInt();
        float poolLife = buf.readFloat();
        float poolDark = buf.readFloat();
        float poolWhite = buf.readFloat();
        float heat = buf.readFloat();
        boolean overloaded = buf.readBoolean();
        String source = buf.readUtf();
        snapshot = new Snapshot(level, poolLife, poolDark, poolWhite, heat, overloaded, source);
        received = true;
    }

    /** 最近一次权威快照（永不 null） */
    public static Snapshot snapshot() {
        return snapshot;
    }

    /** 是否已收到过至少一个同步包（未收到时 {@link #snapshot()} 是内置默认值） */
    public static boolean hasData() {
        return received;
    }

    /**
     * 不可变快照。
     *
     * @param heat       当前热值（0~100）
     * @param overloaded <b>是否处于过载罚站</b> —— 权威值，由服务端下发。
     *                   ⚠ <b>不要用 {@code heat >= HEAT_MAX} 代替它</b>：罚站期间热值已被
     *                   −25/秒 降到 100 以下，只看 heat 会显示"不过载"，与实际"仍禁止施法"矛盾。
     */
    public record Snapshot(int level, float poolLife, float poolDark, float poolWhite,
            float heat, boolean overloaded, String sourceId) {

        /** 当前等级的池容量（与服务端同一公式，供能量条换算百分比） */
        public float poolCapacity() {
            return MagicState.BASE_POOL_CAPACITY
                    + MagicState.POOL_CAPACITY_PER_LEVEL * (level - MagicState.LEVEL_MIN);
        }

        /** 按索引读三池（0 = 生命 / 1 = 黑暗 / 2 = 白色） */
        public float pool(int index) {
            return switch (index) {
                case MagicStates.POOL_LIFE -> poolLife;
                case MagicStates.POOL_DARK -> poolDark;
                case MagicStates.POOL_WHITE -> poolWhite;
                default -> 0f;
            };
        }

        /** 热值是否已达过载<b>阈值</b>（客户端据此把热条画到满） */
        public boolean overheated() {
            return heat >= MagicState.HEAT_MAX;
        }
    }
}
