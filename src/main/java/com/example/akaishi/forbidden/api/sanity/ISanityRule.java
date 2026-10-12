package com.example.akaishi.forbidden.api.sanity;

import net.minecraft.resources.ResourceLocation;

/**
 * 环境周期扣减规则：描述"处在某环境下，每过一段时间扣一点理智"。
 *
 * <p><b>结算语义（内部实现层必须按此实现，附属按此理解）</b>：
 * <ol>
 *   <li><b>节流</b>：{@link #applies(SanityContext)} 为真时开始计时，每满 {@link #periodTicks()} 才扣一次
 *       {@link #amountPerPeriod()}；不是每 tick 都扣。规则之间各自独立计时。</li>
 *   <li><b>单次暴露累计上限</b>：一次连续"暴露周期"（{@code applies} 持续为真期间）内，
 *       该规则累计扣减不超过 {@link #capPerExposure()}；{@code 0} = 该机制无累计上限（可无限扣）。
 *       暴露中断（{@code applies} 变假）后累计清零，下次重新进入可再扣满一轮。</li>
 *   <li><b>统一乘系数</b>：{@code amountPerPeriod()} 是<b>未乘认知系数</b>的原始值，
 *       实际扣量 = 原始值 × COG 的环境扣除系数。<b>该系数不在规则里给</b>，
 *       由系统统一施加——规则只管"这环境扣多少"，认知值的影响全服一致、只在一处调平衡。</li>
 *   <li><b>优先级</b>：一次结算内多条规则同时命中时全部生效（可叠加），
 *       {@link #priority()} 只决定<b>结算顺序</b>（数值大的先结），用于让"豁免/增幅类"规则先行。</li>
 * </ol>
 *
 * <p>规则的 id 会出现在 {@link SanityCallbacks#registerEnvironmentListener} 的否决回调里，
 * 附属可据此针对特定规则放行（例如某饰品免疫幽匿环境扣减）。
 */
public interface ISanityRule {

    /** 规则 id，必须带自己的命名空间（不得用 {@code minecraft} 或 {@code akaishi} 冒充官方内容） */
    ResourceLocation id();

    /**
     * 本次结算该规则是否命中。
     *
     * <p>只读上下文，禁止在此写世界状态或改理智数值（那是结算层的职责）；
     * 实现应尽量无副作用，本方法会被重复调用。抛异常由结算层隔离（该规则本次视为不命中）。
     */
    boolean applies(SanityContext ctx);

    /** 扣减周期：每多少 tick 结算一次。必须 &gt; 0（注册时校验，避免除零 / 每 tick 狂扣） */
    int periodTicks();

    /** 每个周期扣减的原始量（&gt; 0 为扣，&lt; 0 为回补）；尚未乘 COG 环境扣除系数 */
    float amountPerPeriod();

    /** 单次暴露累计扣减上限；{@code 0} = 无累计上限 */
    int capPerExposure();

    /** 结算优先级：数值大者先结算；同值顺序不保证 */
    int priority();
}
