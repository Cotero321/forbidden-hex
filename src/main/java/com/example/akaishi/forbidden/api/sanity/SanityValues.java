package com.example.akaishi.forbidden.api.sanity;

/**
 * 理智数值快照（只读）。
 *
 * <p><b>为什么是快照而不是实时视图</b>：附属一次结算里往往要连读多个值（例如先取 SAN 再取上限算百分比），
 * 若每次都回查玩家字段，中途发生写入就会读到自相矛盾的一组数（SAN=95 / 上限=10 / 百分比=950%）。
 * 快照把同一时刻的五个值冻结在一处，读端拿到的永远是自洽的一组。
 *
 * <p>字段语义：
 * <ul>
 *   <li>{@link #san()} —— 当前理智值，取值区间 {@code [0, }{@link #effectiveMax()}{@code ]}；</li>
 *   <li>{@link #sanc()} —— 理智上限（SAN 容量），基础容量，尚未扣除临时削减；</li>
 *   <li>{@link #cog()} —— 认知值，<b>环境扣除系数</b>与<b>食补效力系数</b>的唯一来源（具体曲线由内部实现决定）；</li>
 *   <li>{@link #protection()} —— 临时理智保护，结算时优先抵扣，抵扣完才动 SAN；</li>
 *   <li>{@link #tempCut()} —— 临时上限削减，直接从上限里扣。</li>
 * </ul>
 *
 * <p><b>硬上限</b>：{@code san} 的实际上限是 {@link #effectiveMax()}（{@code sanc - tempCut}）而不是 {@code sanc}。
 * 任何写入路径（含附属经 {@link ISanityService} 的写入）都按该硬上限夹取。
 *
 * <p>本类型不可变，可安全跨线程传递与缓存（但快照会过期，跨 tick 请重新取）。
 *
 * @param san        理智当前值
 * @param sanc       理智上限
 * @param cog        认知值
 * @param protection 临时理智保护
 * @param tempCut    临时上限削减
 */
public record SanityValues(float san, float sanc, float cog, float protection, float tempCut) {

    /** 全零快照：服务未注册 / 理智系统未启用时的兜底值（保证调用方永远拿不到 null） */
    public static final SanityValues ZERO = new SanityValues(0f, 0f, 0f, 0f, 0f);

    /**
     * SAN 的硬上限 = 上限 − 临时削减。
     *
     * <p>下限夹 0：临时削减被叠到超过上限时（例如多个来源同时扣上限），
     * 不能让上限变成负数，否则夹取逻辑会把 SAN 推向负值。
     */
    public float effectiveMax() {
        return hardLimit(sanc, tempCut);
    }

    /**
     * {@code 有效上限 = 上限 − 临时削减} 的<b>唯一换算函数</b>（纯函数、无状态）。
     *
     * <p>服务端写路径（{@code SanityState#effectiveMax()} → {@code SanityServiceImpl#pressSanIntoLimit}）
     * 与客户端 HUD 都只走这一份：客户端<b>不得</b>另写一份 {@code max(0, sanc - tempCut)}，
     * 否则两边一旦各自演化就会重新变成"两套口径"（本项目已踩过该坑）。
     */
    public static float hardLimit(float sanc, float tempCut) {
        return Math.max(0f, sanc - tempCut);
    }
}
