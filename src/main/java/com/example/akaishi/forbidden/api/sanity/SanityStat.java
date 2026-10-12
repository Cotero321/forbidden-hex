package com.example.akaishi.forbidden.api.sanity;

/**
 * 理智数值的种类。
 *
 * <p><b>为什么需要它</b>：数值变化回调 {@link SanityCallbacks.ValueListener} 是一条共用通道，
 * 若不带种类区分，监听方就无法判断"变的是 SAN 还是上限"，只能退化成重新读一遍全部数值——
 * 既丢掉了"哪个值变了"的信息，也丢掉了旧值。五个值各自对应一个枚举常量，
 * 与 {@link SanityValues} 的五个字段一一对应。
 */
public enum SanityStat {

    /** 当前理智值：区间 {@code [0, 硬上限]}，归零触发阈值链最底端 */
    SAN,

    /** 理智上限：基础容量，会与临时削减共同决定硬上限 */
    SANC,

    /** 认知值：环境扣除系数与食补效力系数的来源 */
    COG,

    /** 临时理智保护：结算时优先抵扣，抵扣完才动 SAN */
    PROTECTION,

    /** 临时上限削减：直接从上限里扣，压低下限（硬上限） */
    TEMP_CUT
}
