/**
 * 理智系统对外 API —— <b>附属模组唯一允许 import 的理智包</b>。
 *
 * <p>本包只含接口、注册表、监听表与同步附加载荷工具，不含任何结算逻辑。
 * 内部实现（玩家数值字段、环境结算、食补、HUD、指令、配置、网络包）位于其他包，
 * 附属不得反向依赖它们——那属于内部实现，随时可能重构。
 *
 * <h2>1. 数值语义速查</h2>
 * <ul>
 *   <li><b>SAN</b>：当前理智值，区间 {@code [0, 硬上限]}；</li>
 *   <li><b>SANC</b>：理智上限（基础容量）；</li>
 *   <li><b>COG</b>：认知值，环境扣除系数与食补效力系数的唯一来源；</li>
 *   <li><b>protection</b>：临时理智保护，结算时优先抵扣，抵扣完才动 SAN；</li>
 *   <li><b>tempCut</b>：临时上限削减；</li>
 *   <li><b>硬上限</b> = {@code SANC - tempCut}（{@link com.example.akaishi.forbidden.api.sanity.SanityValues#effectiveMax()}）。
 *       所有写 SAN 的路径都按硬上限夹取，不是按 SANC。</li>
 * </ul>
 *
 * <h2>2. 客户端只读语义</h2>
 * 理智数值<b>服务端权威</b>。客户端可放心调用全部读方法（返回最近一次同步值），
 * 但写方法（{@code setXxx} / {@code addXxx} / {@link com.example.akaishi.forbidden.api.sanity.ISanityService#reportFirstEncounter}）
 * 在客户端<b>静默无效</b>：不抛异常、不上报、不改本地值。
 * 附属不需要自己判断当前侧，也不需要为了"客户端算个显示值"另开一套逻辑。
 *
 * <h2>3. 注册时机</h2>
 * 注册表是静态、线程安全（{@code ConcurrentHashMap}）的，结算层<b>每次结算惰性读表</b>、不缓存快照，
 * 因此注册时机不敏感。推荐：
 * <ul>
 *   <li>Forge：模组构造阶段或 {@code FMLCommonSetupEvent}（该事件并行执行，注册表线程安全，无需串行包装）；</li>
 *   <li>Fabric：{@code ModInitializer#onInitialize}；</li>
 *   <li>底线要求：<b>早于玩家上线</b>。运行中也允许注册（例如数据包重载时），下一次结算即生效。</li>
 * </ul>
 *
 * <h2>4. id 命名规范</h2>
 * 所有注册项 id 必须是合法的命名空间资源位置（{@code 你的命名空间:路径}），
 * 且<b>必须使用你自己模组的命名空间</b>——不得用 {@code minecraft} 冒充原版内容，
 * 也不得用 {@code akaishi} 冒充本模组内容（注册表不做重名保护时，会用你的 id 覆盖别人的条目）。
 * 非法 id 会在注册时直接抛 {@code IllegalArgumentException} 并带上 id（宁可开服就报错，也不要上线后静默失效）。
 *
 * <h2>5. 重复注册与注销</h2>
 * <ul>
 *   <li>{@code register(...)}：同 id 已存在 → 抛 {@code IllegalArgumentException}；</li>
 *   <li>{@code override(...)}：同 id 直接替换（预留 id 抢占、开发期热替换）；</li>
 *   <li>{@code unregister(id)}：返回被移除的实例，不存在返回 null，可随时调用；
 *       但<b>注销不影响玩家存档里已记录的首见/首用 id</b>（见第 6 条）；</li>
 *   <li>回调（{@link com.example.akaishi.forbidden.api.sanity.SanityCallbacks}）：注册返回可注销句柄，
 *       同一实例重复注册是幂等的。</li>
 * </ul>
 *
 * <h2>6. 存档容错承诺</h2>
 * 首见（{@link com.example.akaishi.forbidden.api.sanity.ISanityFirstEncounter#id()}）与一次性首用
 * （{@link com.example.akaishi.forbidden.api.sanity.ISanityRestoreSource#id()}）的 id 会写进玩家存档。
 * 载入时遇到<b>当前未注册</b>的 id（附属被卸载 / 改 id / 版本回退），内部实现必须<b>原样保留、不清档、不抛异常</b>；
 * 反过来，附属也不必担心"卸载过再装回来"会重复触发——只要 id 与命名空间不变即可接续。
 * 唯一代价：<b>id 一经发布不要再改</b>，改了等于对全体玩家重新触发一次。
 *
 * <h2>7. API 版本断言</h2>
 * <pre>{@code
 * // 形态版本：追加方法不自增，删除/改语义才自增；用它在旧核心上安全降级
 * if (SanityServices.API_VERSION < 1) {
 *     return; // 核心太旧，本附属的理智联动整体关闭
 * }
 * // 服务是否真的在跑：未注册时是只读空实现兜底（version() == 0，读写全是 0）
 * if (!SanityServices.isAvailable() || SanityServices.version() <= 0) {
 *     return;
 * }
 * }</pre>
 *
 * <h2>8. 最小接入示例</h2>
 * <pre>{@code
 * public final class MySanityIntegration {
 *
 *     // 在附属初始化里调用一次（Forge: FMLCommonSetupEvent；Fabric: onInitialize）
 *     public void init() {
 *         // ① 环境规则：幽匿环境每 40 tick 扣 1 点，单次暴露最多累计 30 点
 *         SanityRuleRegistry.register(new MySculkRule());
 *
 *         // ② 食补档位：某食物 200 tick 内共补 6 点，第 2 次只剩 50% 效力
 *         SanityFoodRegistry.register(new MyFoodProfile());
 *
 *         // ③ 首见条目：第一次进入某结构时 +5 上限、+2 认知（langKey 必填，双语 lang 里要有）
 *         SanityFirstRegistry.register(new MyFirstEncounter());
 *
 *         // ④ 监听数值变化（返回句柄，可随时注销）
 *         SanityCallbacks.Registration handle = SanityCallbacks.registerValueListener(
 *                 (player, stat, oldValue, newValue, source) -> {
 *                     if (stat == SanityStat.SAN && source == SanityChangeSource.ENVIRONMENT) {
 *                         // 只在环境扣减时播个表现，食补回补不播
 *                     }
 *                 });
 *
 *         // ⑤ 否决某次环境扣减：某饰品免疫幽匿环境
 *         SanityCallbacks.registerEnvironmentListener(
 *                 (player, rule, ctx, amount) -> !isImmune(player, rule.id()));
 *
 *         // ⑥ 读数值（客户端同样安全；写方法在客户端静默无效）
 *         float max = SanityServices.get().getEffectiveMax(player);
 *         float percent = max <= 0f ? 0f : SanityServices.get().getSan(player) / max;
 *     }
 *
 *     // 规则实现：只需自述"命中条件 + 周期 + 每周期量 + 累计上限 + 优先级"
 *     private static final class MySculkRule implements ISanityRule {
 *         private static final ResourceLocation ID = new ResourceLocation("mymod", "sculk_dark");
 *
 *         public ResourceLocation id() { return ID; }
 *         public boolean applies(SanityContext ctx) { return ctx.inDeepDark(); }
 *         public int periodTicks() { return 40; }
 *         public float amountPerPeriod() { return 1f; }   // 未乘 COG 环境系数，系统统一施加
 *         public int capPerExposure() { return 30; }
 *         public int priority() { return 0; }
 *     }
 * }
 * }</pre>
 * <p>（示例为排版省略了 {@code @Override}，实际实现请自行补上。类与字段沿用项目既有惯例：
 * 一个附属只建一个接入类，注册集中在初始化方法里，便于排查孤儿注册。）
 *
 * <h2>9. 不要做的事</h2>
 * <ul>
 *   <li>不要实现 {@link com.example.akaishi.forbidden.api.sanity.ISanityService}——它是内部实现层的位置；</li>
 *   <li>不要把首见状态塞进玩家自己的 NBT——交给核心按 id 记档，否则卸载即丢档、可刷；</li>
 *   <li>不要自己发网络包同步理智——用
 *       {@link com.example.akaishi.forbidden.api.sanity.SyncPayloadTool} 搭核心的理智同步包；</li>
 *   <li>不要在 {@code applies} / {@code test} / {@code matches} 里写世界状态或改理智数值——
 *       判定应只读且可重复调用，写入请走 {@link com.example.akaishi.forbidden.api.sanity.ISanityService}；</li>
 *   <li>不要在阈值回调里再写理智数值——会引发递归跨越，需要调整请延后一 tick。</li>
 * </ul>
 *
 * @since 1.0（API_VERSION = 1）
 */
package com.example.akaishi.forbidden.api.sanity;
