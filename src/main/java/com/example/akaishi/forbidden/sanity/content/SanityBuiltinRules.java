package com.example.akaishi.forbidden.sanity.content;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.api.sanity.ISanityRule;
import com.example.akaishi.forbidden.api.sanity.SanityChangeSource;
import com.example.akaishi.forbidden.api.sanity.SanityContext;
import com.example.akaishi.forbidden.api.sanity.SanityRuleRegistry;
import com.example.akaishi.config.ModConfig;
import com.example.akaishi.forbidden.effect.AkaishiForbiddenEffects;
import com.example.akaishi.forbidden.sanity.SanityServiceImpl;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.function.Predicate;

/**
 * 内置环境规则（理智系统的"内容层"）。
 *
 * <p><b>为什么内置内容也走注册表</b>：如果内置规则直接写死在结算循环里，就会出现两套语义
 * （附属的规则走 API、内置的走内部分支），附属既看不到也否决不了内置规则。
 * 全部经 {@link SanityRuleRegistry#register} 注册后，"内置内容"和"附属内容"在结算层完全同权，
 * 否决回调（{@code SanityCallbacks#registerEnvironmentListener}）对两者一致生效。
 *
 * <p><b>扣量口径</b>：本类给出的 {@code amountPerPeriod} 一律是<b>未乘 COG 系数</b>的原始量；
 * 实际扣量 = 原始量 × {@code SanityCogCurve.envDeductionFactor(cog)}，由结算层统一施加。
 * 单次暴露累计也按原始量记账（与 {@code capPerExposure} 同单位）。
 *
 * <p><b>数值冻结来源</b>：本条内容为设计稿冻结值，全部标"待调手感值"；
 * 其中"冷却/刷新时长"与"下界顶部判据"三项外露为配置（见 {@link ModConfig}），其余为内置常量。
 */
public final class SanityBuiltinRules {

    // ===== 规则 id（对外可见：否决回调、调试指令、附属豁免都按 id 匹配）=====

    /** 远古城市：主世界 + 在远古城市内 */
    public static final ResourceLocation ANCIENT_CITY = id("ancient_city");
    /** 暗处·高亮档：Y &lt; 0 且方块亮度 3~7 */
    public static final ResourceLocation DARK_HIGH = id("dark_high");
    /** 暗处·全暗档：Y &lt; 0 且方块亮度 0~2 */
    public static final ResourceLocation DARK_LOW = id("dark_low");
    /** 深海·暗档：主世界 + 海洋 + Y &lt; 0 + 方块光 0~3 */
    public static final ResourceLocation DEEP_SEA = id("deep_sea");
    /**
     * 深海·亮档：主世界 + 海洋 + Y &lt; 0 + 方块光 &gt; 3。
     *
     * <p><b>为什么深海要有两条注册条目</b>：{@link ISanityRule#periodTicks()} 与
     * {@link ISanityRule#amountPerPeriod()} 都是<b>无上下文的静态值</b>，一条规则表达不了
     * "暗处每 30s 扣 1、亮处每 2min 扣 1"这两种节拍。拆成两条同族规则后节拍/用量各自精确，
     * 且结算层对两条按"同时命中即叠加"处理（二者亮度区间互斥，实际只会命中一条）。
     * 另一条路是把分支状态藏在规则实例字段里、靠"结算先调 applies 再读 period"的调用顺序，
     * 那属于对结算实现顺序的隐式依赖，一旦顺序变化就会静默算错，故不采用。
     */
    public static final ResourceLocation DEEP_SEA_BRIGHT = id("deep_sea_bright");
    /** 下界顶部：下界 + 脚部 Y ≥ {@link #netherRoofY()} */
    public static final ResourceLocation NETHER_ROOF = id("nether_roof");
    /**
     * 不可名状气场：玩家身上带着 {@code forbidden_hex:unnameable} 效果期间持续扣减。
     *
     * <p><b>解耦</b>：本规则只<b>读</b>该效果是否存在，不给 {@code UnnameableEffect} 附加任何理智语义
     * （效果的施加、时长、表现全归原体系），因此卸载/替换该效果不会让本规则产生副作用。
     */
    public static final ResourceLocation UNNAMEABLE_AURA = id("unnameable_aura");
    /** 凋零气场：玩家身上带「原版凋零<b>或</b>凋亡（{@code forbidden_hex:doom}）」期间持续扣减（与末影/凋零系对抗的长期代价） */
    public static final ResourceLocation WITHER_AURA = id("wither_aura");

    /** 两条暗处规则（供暗处状态机按 id 读写各自的暴露累计） */
    private static final List<ResourceLocation> DARK_RULES = List.of(DARK_HIGH, DARK_LOW);

    // ===== 数值（待调手感值，冻结自设计稿）=====

    /** 远古城市扣减周期（tick）：1200 = 60s */
    public static final int ANCIENT_CITY_PERIOD_TICKS = 1200;
    /** 暗处·高亮档周期（tick）：600 = 30s */
    public static final int DARK_HIGH_PERIOD_TICKS = 600;
    /** 暗处·高亮档单次暴露累计上限（原始量） */
    public static final int DARK_HIGH_CAP = 15;
    /** 暗处·全暗档周期（tick）：300 = 15s */
    public static final int DARK_LOW_PERIOD_TICKS = 300;
    /** 暗处·全暗档单次暴露累计上限（原始量） */
    public static final int DARK_LOW_CAP = 20;
    /** 暗处·高亮档亮度下界 / 全暗档亮度上界（含）：两个区间天然互斥，无需额外互斥逻辑 */
    public static final int DARK_HIGH_MIN_LIGHT = 3;
    public static final int DARK_LOW_MAX_LIGHT = 2;
    /** 深海·暗档周期（tick）：600 = 30s；深海·亮档周期（tick）：2400 = 2min */
    public static final int DEEP_SEA_DARK_PERIOD_TICKS = 600;
    public static final int DEEP_SEA_BRIGHT_PERIOD_TICKS = 2400;
    /** 深海的明暗分界（方块光，含）：0~3 走暗档，&gt; 3 走亮档 */
    public static final int DEEP_SEA_DARK_MAX_LIGHT = 3;
    /** 下界顶部扣减周期（tick）：1200 = 60s；单次累计上限 20 */
    public static final int NETHER_ROOF_PERIOD_TICKS = 1200;
    public static final int NETHER_ROOF_AMOUNT = 5;
    public static final int NETHER_ROOF_CAP = 20;
    /** 下界顶部基岩层判据的内置默认 Y（外露配置为 0 时回退此值） */
    public static final int NETHER_ROOF_DEFAULT_Y = 128;
    /** 不可名状气场周期（tick）：120 = 6s（待调手感值） */
    public static final int UNNAMEABLE_AURA_PERIOD_TICKS = 120;
    /** 凋零气场周期（tick）：60 = 3s（待调手感值） */
    public static final int WITHER_AURA_PERIOD_TICKS = 60;
    /**
     * 两条气场规则的每周期扣量（原始量，未乘 COG 环境扣除系数；待调手感值）。
     *
     * <p>两条规则都<b>不设</b>单次暴露累计上限（{@code capPerExposure = 0}）：
     * 它们由"玩家自己带着效果"驱动，效果时长即暴露时长，天然有终点，再加额度上限会让
     * "长时不可名状"与"短时不可名状"扣量趋同、读不出差别。
     */
    public static final float AURA_AMOUNT_PER_PERIOD = 1f;
    /**
     * 遭遇不可名状（效果刚被施加到玩家身上）时的一次性立即扣减（原始量；待调手感值）。
     *
     * <p>与 {@link #UNNAMEABLE_AURA} 的周期扣减叠加：一次不可名状 = 起手 −10 + 每 6s −1。
     */
    public static final float UNNAMEABLE_ONSET_SAN = 10f;

    /** 结算优先级：本批内置规则之间无豁免/增幅关系，统一 0（同值顺序不保证，符合 API 约定） */
    private static final int PRIORITY = 0;

    /** 重复注册保护：init 被重复调用时只注册一次（注册表对同 id 重复注册会抛异常） */
    private static boolean registered;

    private SanityBuiltinRules() {
    }

    /** 注册全部内置环境规则（由 {@code AkaishiMod.init} 调用；幂等） */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        // 1) 远古城市：主世界 + 结构 piece 命中
        SanityRuleRegistry.register(new SimpleRule(ANCIENT_CITY, ANCIENT_CITY_PERIOD_TICKS, 1f, 0,
                ctx -> ctx.isOverworld() && ctx.inAncientCity()));
        // 2) 暗处·高亮档：Y<0 + 方块光 3~7（夜视免疫 / 冷却由 SanityDarkCycle 统一裁决）
        SanityRuleRegistry.register(new SimpleRule(DARK_HIGH, DARK_HIGH_PERIOD_TICKS, 1f, DARK_HIGH_CAP,
                ctx -> darkApplies(ctx, DARK_HIGH_MIN_LIGHT, SanityDarkCycle.DARK_MAX_LIGHT)));
        // 3) 暗处·全暗档：Y<0 + 方块光 0~2
        SanityRuleRegistry.register(new SimpleRule(DARK_LOW, DARK_LOW_PERIOD_TICKS, 1f, DARK_LOW_CAP,
                ctx -> darkApplies(ctx, 0, DARK_LOW_MAX_LIGHT)));
        // 4) 深海·暗档：主世界 + 海洋 + Y<0 + 方块光 0~3（无累计上限）
        SanityRuleRegistry.register(new SimpleRule(DEEP_SEA, DEEP_SEA_DARK_PERIOD_TICKS, 1f, 0,
                ctx -> deepSeaApplies(ctx, true)));
        // 4b) 深海·亮档：同上但方块光 > 3（每 2min 扣 1）
        SanityRuleRegistry.register(new SimpleRule(DEEP_SEA_BRIGHT, DEEP_SEA_BRIGHT_PERIOD_TICKS, 1f, 0,
                ctx -> deepSeaApplies(ctx, false)));
        // 5) 下界顶部：下界 + 脚部 Y ≥ 判据（默认 128，见 netherRoofY()）
        SanityRuleRegistry.register(new SimpleRule(NETHER_ROOF, NETHER_ROOF_PERIOD_TICKS,
                NETHER_ROOF_AMOUNT, NETHER_ROOF_CAP,
                ctx -> ctx.isNether() && ctx.pos().getY() >= netherRoofY()));
        // 6) 不可名状气场：身上带 forbidden_hex:unnameable 期间每 6s 扣 1（无单次上限；起手一击见 applyUnnameableOnset）
        SanityRuleRegistry.register(new SimpleRule(UNNAMEABLE_AURA, UNNAMEABLE_AURA_PERIOD_TICKS,
                AURA_AMOUNT_PER_PERIOD, 0, SanityBuiltinRules::hasUnnameable));
        // 7) 凋零气场：身上带「凋零或凋亡」期间每 3s 扣 1（无单次上限；单一规则 ⇒ 两条同时挂也只结算一次）
        SanityRuleRegistry.register(new SimpleRule(WITHER_AURA, WITHER_AURA_PERIOD_TICKS,
                AURA_AMOUNT_PER_PERIOD, 0, SanityBuiltinRules::hasWitherOrDoom));
    }

    /**
     * 玩家身上是否带「凋零<b>或</b>凋亡」—— 本模组所有"读凋零"结算的同权判据（理智侧副本）。
     *
     * <p><b>为什么要同权</b>：BOSS 阶段三会把施加给玩家的凋零换成 {@code forbidden_hex:doom}（见
     * {@code AgaitolosDoom}），若本规则只判 {@link MobEffects#WITHER}，阶段三起「凋零气场」会<b>静默失效</b>。
     * BOSS 侧的既有读法（{@code AgaitolosDoom#isWitheredOrDoomed}）本就是同权口径，这里与之对齐。
     *
     * <p><b>为什么不复用 BOSS 侧那个方法</b>：那会让理智系统反向 import BOSS 包，
     * 违反"理智系统与 BOSS 解耦"（键与判据都不许反向依赖）。两边各持一份"读效果"的判据，
     * 依赖的只是效果注册表（{@link MobEffects} / {@link AkaishiForbiddenEffects}），互不耦合。
     *
     * <p><b>不会重复结算</b>：这是<b>同一条规则</b>的布尔门 —— 同时挂凋零与凋亡时门仍只返回 true，
     * 规则的 {@code periodTicks} 只推进一次、{@code amountPerPeriod} 只扣一次。
     */
    public static boolean hasWitherOrDoom(SanityContext ctx) {
        return ctx != null && ctx.player() != null && hasWitherOrDoom(ctx.player());
    }

    /** 任意生物是否带「凋零或凋亡」（同上，供规则门与调试复用） */
    public static boolean hasWitherOrDoom(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        if (entity.hasEffect(MobEffects.WITHER)) {
            return true;
        }
        MobEffect doom = AkaishiForbiddenEffects.DOOM == null ? null : AkaishiForbiddenEffects.DOOM.get();
        return doom != null && entity.hasEffect(doom);
    }

    /**
     * 玩家身上是否带着 {@code forbidden_hex:unnameable}（只读；效果对象在注册完成后才可取，故双重判空）。
     */
    public static boolean hasUnnameable(SanityContext ctx) {
        return ctx != null && ctx.player() != null && hasUnnameable(ctx.player());
    }

    /** 任意生物是否带着 {@code forbidden_hex:unnameable}（首见"遭遇不可名状"合并条共用同一口径） */
    public static boolean hasUnnameable(LivingEntity entity) {
        MobEffect unnameable = AkaishiForbiddenEffects.UNNAMEABLE == null ? null : AkaishiForbiddenEffects.UNNAMEABLE.get();
        return unnameable != null && entity != null && entity.hasEffect(unnameable);
    }

    /**
     * 遭遇不可名状的"起手一击"：效果刚被施加到玩家身上时立即扣 {@link #UNNAMEABLE_ONSET_SAN}。
     *
     * <p><b>为什么不在规则里做</b>：一次性瞬间扣减表达不了"每 N tick 扣一次"的规则语义
     * （规则的 {@code periodTicks} / {@code amountPerPeriod} 都是无上下文的静态值）。
     * 故由施加钩子（forge 的 {@code MobEffectEvent.Added}）直接调用本方法。
     *
     * <p><b>仍走扣减漏斗</b>：经 {@link SanityServiceImpl#debitInternal} ⇒ 临时保护优先抵扣、
     * 值变化回调、阈值边沿派发、同步脏标记一处不落；<b>不</b>经环境否决门
     * （{@code fireEnvironmentDebit} 需要一份 {@code SanityContext}，而效果施加时刻不在环境结算节拍内）。
     */
    public static void applyUnnameableOnset(Player player) {
        if (player == null || !ModConfig.sanityEnabled) {
            return;
        }
        SanityServiceImpl.instance().debitInternal(player, UNNAMEABLE_ONSET_SAN, SanityChangeSource.ENVIRONMENT);
    }

    /** 两条暗处规则的 id（只读；暗处状态机据此清零各自的暴露累计） */
    public static List<ResourceLocation> darkRuleIds() {
        return DARK_RULES;
    }

    /** 是否为两条暗处规则之一（调试展示暗处冷却、状态机复位时按此筛选） */
    public static boolean isDarkRule(ResourceLocation id) {
        return DARK_RULES.contains(id);
    }

    /**
     * 下界顶部判据 Y：脚部 Y ≥ 该值即视为"在下界顶部"。
     *
     * <p><b>判据选型理由</b>：1.20.1 下界的基岩天花板顶面在 <b>y=127</b>，玩家站上顶部后脚部
     * 最低为 y=128；用"Y ≥ 128"而不是"Y ≥ 基岩层扫描结果"，是因为：
     * ① 原版下界顶部已被玩家广泛用作交通层，位置固定、语义清晰；
     * ② 逐方块扫描基岩既贵又会被玩家自建的y127 平台/打洞干扰；
     * ③ 该值外露为配置（{@code sanity.netherRoofY}），整合包改顶部高度时无需改代码。
     */
    public static int netherRoofY() {
        return ModConfig.sanityNetherRoofY > 0 ? ModConfig.sanityNetherRoofY : NETHER_ROOF_DEFAULT_Y;
    }

    /** 暗处判定：Y<0 + 方块光落在区间内 + 未处于"重见光明"冷却 + 无夜视（后两项见 {@link SanityDarkCycle}） */
    private static boolean darkApplies(SanityContext ctx, int minLight, int maxLight) {
        if (!ctx.isUnderY0()) {
            return false;
        }
        int light = ctx.blockLight();
        if (light < minLight || light > maxLight) {
            return false;
        }
        return SanityDarkCycle.darkDeductionAllowed(ctx);
    }

    /**
     * 深海判定：主世界 + 海洋群系标签 + Y&lt;0 + 方块光落到分支区间。
     *
     * <p>亮度口径与两条暗处规则一致，取<b>方块光</b>（不含天空光）：海面下方的天空光会被水体衰减但
     * 判定口径若混入天空光，则"白天潜到水下 20 格"也会被算成亮处，与"深海黑暗"的设计意图相反。
     */
    private static boolean deepSeaApplies(SanityContext ctx, boolean darkBranch) {
        if (!ctx.isOverworld() || !ctx.isOcean() || !ctx.isUnderY0()) {
            return false;
        }
        return darkBranch == (ctx.blockLight() <= DEEP_SEA_DARK_MAX_LIGHT);
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(AkaishiMod.MOD_ID, path);
    }

    /**
     * 通用规则实现：判定交给调用方给的纯谓词，其余四项（周期/用量/上限/优先级）由构造参数固定。
     *
     * <p>用 record 而不是可变类：规则实例会被注册表长期持有并可能被多线程读取，
     * 不可变对象天然线程安全，也不存在"运行期被改数值"导致结算量突变的问题。
     */
    private record SimpleRule(ResourceLocation id, int periodTicks, float amountPerPeriod, int capPerExposure,
                             Predicate<SanityContext> gate) implements ISanityRule {

        @Override
        public boolean applies(SanityContext ctx) {
            return ctx != null && gate != null && gate.test(ctx);
        }

        @Override
        public int priority() {
            return PRIORITY;
        }
    }
}
