package com.example.akaishi.forbidden.api.sanity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * 理智数值门面：<b>附属唯一被允许读写理智数值的入口</b>。
 *
 * <p><b>为什么收口成一个服务</b>：理智的五个值之间彼此耦合（上限被削减时当前值必须连带下压、
 * 保护要先抵扣才动 SAN），如果允许附属直接改玩家字段，任何一处漏夹取都会造出自相矛盾的状态
 * （SAN 高于硬上限、保护为负）。收口到本接口后，夹取规则只在实现层存在一份，
 * 附属无论怎么调都改不出非法状态。
 *
 * <p><b>权威性（务必读懂）</b>：
 * <ul>
 *   <li>理智是<b>服务端权威</b>数值。服务端调用读写均真实生效；</li>
 *   <li><b>客户端调用只读</b>：读方法返回最近一次同步下来的快照值，
 *       写方法（{@code setXxx} / {@code addXxx} / {@link #reportFirstEncounter}）<b>静默无效</b>，不抛异常也不上报；</li>
 *   <li>写方法只在服务端线程语义下有意义，实现层负责拒绝非服务端调用；附属不需要自己判断侧，调了没效就是没效。</li>
 * </ul>
 *
 * <p><b>获取方式</b>：{@code SanityServices.get()}；未注册时拿到只读空实现，永不返回 null。
 *
 * <p><b>实现者注意</b>：本接口的实现由本模组内部提供，附属<b>不要实现它</b>。
 * 实现层必须在每次数值真实变化后触发 {@link SanityCallbacks#fireValueChanged}
 * （来源打 {@link SanityChangeSource#EXTERNAL} 及以上对应标签）。
 */
public interface ISanityService {

    // ------------------------------------------------------------------
    // 读：客户端可安全调用（返回最近一次同步值）
    // ------------------------------------------------------------------

    /**
     * 取同一时刻的五个值（推荐：一次拿全，避免连读之间发生变更）。
     *
     * @return 只读快照，永不返回 null（未注册/无数据时返回 {@link SanityValues#ZERO}）
     */
    SanityValues snapshot(Player player);

    /** 当前理智值；区间 {@code [0, }{@link #getEffectiveMax(Player)}{@code ]} */
    float getSan(Player player);

    /** 理智上限（基础容量，未扣临时削减） */
    float getSanc(Player player);

    /** 认知值（环境扣除系数与食补效力系数的来源） */
    float getCog(Player player);

    /** 临时理智保护（结算时优先抵扣） */
    float getProtection(Player player);

    /** 临时上限削减（直接从上限里扣） */
    float getTempCut(Player player);

    /** SAN 的硬上限 = {@link #getSanc} − {@link #getTempCut}（下限 0），即所有写 SAN 路径的夹取上界 */
    float getEffectiveMax(Player player);

    // ------------------------------------------------------------------
    // 写：仅服务端生效；客户端调用静默忽略
    // ------------------------------------------------------------------

    /** 设当前理智值；按 {@code [0, 硬上限]} 夹取 */
    void setSan(Player player, float value);

    /** 设理智上限；按 {@code >= 0} 夹取，并连带把超出新硬上限的当前值下压（保持快照自洽） */
    void setSanc(Player player, float value);

    /** 设认知值；按实现定义的合法区间夹取（建议 {@code [0, 100]}，实际上下限由配置决定） */
    void setCog(Player player, float value);

    /** 设临时理智保护；负数按 0 处理 */
    void setProtection(Player player, float value);

    /** 设临时上限削减；负数按 0 处理，并连带下压超出新硬上限的当前值 */
    void setTempCut(Player player, float value);

    /** 增减当前理智值（负数为扣减）；结果按 {@code [0, 硬上限]} 夹取 */
    void addSan(Player player, float delta);

    /** 增减理智上限；结果按 {@code >= 0} 夹取，并连带下压超限的当前值 */
    void addSanc(Player player, float delta);

    /** 增减认知值；结果按实现定义的合法区间夹取 */
    void addCog(Player player, float delta);

    /** 增减临时理智保护；结果按 {@code >= 0} 夹取 */
    void addProtection(Player player, float delta);

    /** 增减临时上限削减；结果按 {@code >= 0} 夹取，并连带下压超限的当前值 */
    void addTempCut(Player player, float delta);

    /**
     * 程序化上报"首见"（声明式 {@link ISanityFirstEncounter#test} 的补充入口）。
     *
     * <p><b>为什么需要它</b>：{@link ISanityFirstEncounter#test} 只能表达"可被轮询的环境条件"，
     * 而有些首见是附属自己事件驱动的（例如附属的某个自定义交互发生了）。附属事件里直接上报 id，
     * 是否"首次"由核心按玩家存档判定，附属不必自己存状态——存档容错的责任因此只在核心一处。
     *
     * <p><b>语义</b>：只认 {@link SanityFirstRegistry} 里已注册的 id；
     * 未注册 / 已触发过 / 客户端调用 / 玩家为 null 一律返回 false 并记日志，<b>不抛异常</b>。
     *
     * @param encounterId 首见条目的 id（须已注册）
     * @return true = 本次确实是首次触发且已结算该条目的增减
     */
    boolean reportFirstEncounter(Player player, ResourceLocation encounterId);

    /**
     * 服务实现版本号：数值口径 / 存档格式发生不兼容变动时由实现层自增。
     *
     * <p>0 = 未注册（当前拿到的是空实现兜底）。附属若要断言"理智系统确实在跑"，
     * 请比较 {@link SanityServices#API_VERSION} 与 {@link SanityServices#get()}{@code .version() > 0}。
     */
    int version();
}
