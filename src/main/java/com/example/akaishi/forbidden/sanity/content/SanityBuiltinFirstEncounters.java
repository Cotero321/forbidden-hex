package com.example.akaishi.forbidden.sanity.content;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.api.sanity.ISanityFirstEncounter;
import com.example.akaishi.forbidden.api.sanity.SanityContext;
import com.example.akaishi.forbidden.api.sanity.SanityFirstRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.function.Predicate;

/**
 * 内置首见事件全表（理智系统的"内容层"）。
 *
 * <p><b>两条触发路径</b>（对齐 {@link ISanityFirstEncounter} 的对外承诺）：
 * <ol>
 *   <li><b>声明式</b>：{@link #test(SanityContext)} 由 {@code SanityFirstEncounterSettlement}
 *       在每个玩家的环境节拍里轮询（未触发过的条目才跑判据，见该类的成本口径）；</li>
 *   <li><b>程序化</b>：{@code test} 恒 false 的条目（行为类：食用 / 使用 / 击杀）由对应钩子调
 *       {@code ISanityService#reportFirstEncounter} 上报——"食用了一口""完成了一次仪式""杀死了它"
 *       这类事实无法被环境轮询表达，只能由事件方上报。</li>
 * </ol>
 * 两条路径共用同一份存档记档（{@code first_seen}），因此"轮询先命中"与"事件先上报"不会重复结算。
 *
 * <p><b>数值</b>均为待调手感值；{@code +N} 表示增益。首见只结算<b>一次</b>（按 id 记档），
 * 之后无论再遇到多少次都不再触发。
 */
public final class SanityBuiltinFirstEncounters {

    // ===== 条目 id（进玩家存档：一旦发布不得再改，改了等于全员重触发）=====

    /**
     * 初次遭遇不可名状：<b>自身被施加</b>该效果，<b>或</b> 24 格内有其它生物带着该效果（目击）——两者合并为一条。
     *
     * <p><b>合并原因（勿再加回第二条）</b>：原实现拆成 {@code witness_unnameable}（目击他人）与
     * {@code suffer_unnameable}（自身受害）两条，但数值同档（−10 / +5）、语义高度重叠；
     * BOSS"天魔＊灾"与母神祭坛对玩家施放时玩家自身带效果、附近同源生物也带效果，
     * 两条判据会同时命中 ⇒ 一次遭遇被扣两次上限。故取"或"合并为一条，一次遭遇只上报一次首见
     * （首见按 id 只记一次档，合并后不存在两条独立上报路径）。
     */
    public static final ResourceLocation SUFFER_UNNAMEABLE = id("suffer_unnameable");
    /** 首次遇到远古城市 */
    public static final ResourceLocation ANCIENT_CITY = id("ancient_city");
    /** 首次到达幽匿环境 */
    public static final ResourceLocation DEEP_DARK = id("deep_dark");
    /** 首次遇到监守者 */
    public static final ResourceLocation WARDEN = id("warden");
    /** 首次遇到凋零 */
    public static final ResourceLocation WITHER = id("wither");
    /** 首次遇到末影龙 */
    public static final ResourceLocation ENDER_DRAGON = id("ender_dragon");
    /** 首次遇到蘑菇牛 */
    public static final ResourceLocation MOOSHROOM = id("mooshroom");
    /** 首次遇到菌丝 */
    public static final ResourceLocation MYCELIUM = id("mycelium");
    /** 初次完成母神祭坛仪式 */
    public static final ResourceLocation MOTHER_ALTAR = id("mother_altar");
    /** 首次触碰 / 看见凋零玫瑰（掉落物或背包内均算） */
    public static final ResourceLocation WITHER_ROSE = id("wither_rose");
    /** 首次触碰 / 看见灵魂火族方块 */
    public static final ResourceLocation SOUL_FIRE = id("soul_fire");
    /** 初次食用迷之炖菜 */
    public static final ResourceLocation SUSPICIOUS_STEW = id("suspicious_stew");
    /** 首次进入蘑菇岛（待确认：数值按同类地形档位取） */
    public static final ResourceLocation MUSHROOM_FIELDS = id("mushroom_fields");
    /** 首次进入深层（Y&lt;0；待确认：数值按同类地形档位取） */
    public static final ResourceLocation DEEP_DARK_VISIT = id("deep_dark_visit");
    /** 首次进入末地（待确认：数值按同类地形档位取） */
    public static final ResourceLocation THE_END = id("the_end");
    /** 首次进入地狱（待确认：数值按同类地形档位取） */
    public static final ResourceLocation THE_NETHER = id("the_nether");
    /** 初次击杀监守者 */
    public static final ResourceLocation KILL_WARDEN = id("kill_warden");
    /** 初次击杀凋零 */
    public static final ResourceLocation KILL_WITHER = id("kill_wither");
    /** 初次击杀末影龙 */
    public static final ResourceLocation KILL_ENDER_DRAGON = id("kill_ender_dragon");

    // ===== 数值分档（待调手感值）=====

    /** 重冲击档：遭遇不可名状 / 监守者 / 凋零 / 末影龙 ⇒ 上限 −10、认知 +5 */
    private static final float BOSS_SANC = -10f;
    private static final float BOSS_COG = 5f;
    /** 常规档：地形 / 环境 / 生物 / 物品首见 ⇒ 上限 −6、认知 +3 */
    private static final float ENV_SANC = -6f;
    private static final float ENV_COG = 3f;
    /** 祭坛档：最重的一次性冲击 ⇒ 上限 −20、认知 +10 */
    private static final float ALTAR_SANC = -20f;
    private static final float ALTAR_COG = 10f;
    /** 击杀档：不削上限，只给认知 +3（"见过"扣的是上限，"亲手杀死"给的是认知） */
    private static final float KILL_SANC = 0f;
    private static final float KILL_COG = 3f;

    // ===== 判据参数（半径单位：格；步长 2 的方块扫描为 5³=125 次读取，与幽匿扫描同量级）=====

    /** 目击半径：不可名状持有者 24 格（"遭遇不可名状"合并条的目击半边沿用此半径） */
    private static final double WITNESS_RADIUS = 24.0;
    /** 普通生物遭遇半径：蘑菇牛 16 格 */
    private static final double MOB_RADIUS = 16.0;
    /** BOSS 遭遇半径：监守者 / 凋零 32 格 */
    private static final double BOSS_RADIUS = 32.0;
    /** 末影龙遭遇半径：64 格（末地战场大、龙在空盘旋） */
    private static final double DRAGON_RADIUS = 64.0;
    /** 掉落物遭遇半径：凋零玫瑰掉在地上 8 格内即算"看见" */
    private static final double DROP_RADIUS = 8.0;
    /** 方块遭遇半径 / 步长：菌丝 4、灵魂火族 4（5³ 采样 125 次） */
    private static final int BLOCK_SCAN_RADIUS = 4;
    private static final int BLOCK_SCAN_STEP = 2;

    /** 重复注册保护（init 被重复调用时只注册一次） */
    private static boolean registered;

    private SanityBuiltinFirstEncounters() {
    }

    /** 注册全部内置首见条目（由 {@code AkaishiMod.init} 调用；幂等） */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        // —— 不可名状（合并条：自身带效果 ‖ 目击 24 格内他人带效果；判据取"或"，命中即只上报一次）——
        SanityFirstRegistry.register(new Entry(SUFFER_UNNAMEABLE, BOSS_SANC, BOSS_COG,
                ctx -> SanityBuiltinRules.hasUnnameable(ctx.player()) || nearbyHolder(ctx, WITNESS_RADIUS)));
        // 注：本条的 −10 上限只扣一次（首见按 id 记档）；此后"效果消退 → 再次被施加"仍算一次新的遭遇，
        // 由施加钩子 SanityBuiltinRules#applyUnnameableOnset 每施加一次扣 10 —— 本轮不加冷却，只记录现状。
        // —— 地形 / 结构（判据全部来自上下文的备忘查询，代价常数级）——
        SanityFirstRegistry.register(new Entry(ANCIENT_CITY, ENV_SANC, ENV_COG, SanityContext::inAncientCity));
        SanityFirstRegistry.register(new Entry(DEEP_DARK, ENV_SANC, ENV_COG, SanityContext::inDeepDark));
        SanityFirstRegistry.register(new Entry(DEEP_DARK_VISIT, ENV_SANC, ENV_COG, SanityContext::isUnderY0));
        SanityFirstRegistry.register(new Entry(MUSHROOM_FIELDS, ENV_SANC, ENV_COG,
                ctx -> ctx.level().getBiome(ctx.pos()).is(Biomes.MUSHROOM_FIELDS)));
        SanityFirstRegistry.register(new Entry(THE_END, ENV_SANC, ENV_COG,
                ctx -> ctx.level().dimension() == Level.END));
        SanityFirstRegistry.register(new Entry(THE_NETHER, ENV_SANC, ENV_COG, SanityContext::isNether));
        // —— 生物（实体距离扫描：按半径取包围盒，交给原版区块实体段查表，不走全实体遍历）——
        SanityFirstRegistry.register(new Entry(WARDEN, BOSS_SANC, BOSS_COG,
                ctx -> nearby(ctx, Warden.class, BOSS_RADIUS)));
        SanityFirstRegistry.register(new Entry(WITHER, BOSS_SANC, BOSS_COG,
                ctx -> nearby(ctx, WitherBoss.class, BOSS_RADIUS)));
        SanityFirstRegistry.register(new Entry(ENDER_DRAGON, BOSS_SANC, BOSS_COG,
                // 先按维度短路：末影龙的 64 格扫描是最贵的一条判据，非末地玩家不该为它付这笔钱
                ctx -> ctx.level().dimension() == Level.END && nearby(ctx, EnderDragon.class, DRAGON_RADIUS)));
        SanityFirstRegistry.register(new Entry(MOOSHROOM, ENV_SANC, ENV_COG,
                ctx -> nearby(ctx, MushroomCow.class, MOB_RADIUS)));
        // —— 方块 ——
        // 菌丝：脚部采样点周围 4 格内出现 minecraft:mycelium
        SanityFirstRegistry.register(new Entry(MYCELIUM, ENV_SANC, ENV_COG,
                ctx -> blockNear(ctx, block -> block == Blocks.MYCELIUM)));
        // 灵魂火：周围 4 格内有灵魂火 / 灵魂火把（"看见"口径见类注释）
        SanityFirstRegistry.register(new Entry(SOUL_FIRE, ENV_SANC, ENV_COG, SanityBuiltinFirstEncounters::soulFireNear));
        // —— 物品 ——
        // 凋零玫瑰：附近 8 格有掉落物，或背包/副手/护甲槽里有（用户口径：掉落物与背包内都算）
        SanityFirstRegistry.register(new Entry(WITHER_ROSE, ENV_SANC, ENV_COG,
                ctx -> dropNear(ctx, Items.WITHER_ROSE) || inventoryHas(ctx.player(), Items.WITHER_ROSE)));
        // —— 行为类（test 恒 false，由对应钩子程序化上报）——
        SanityFirstRegistry.register(new Entry(MOTHER_ALTAR, ALTAR_SANC, ALTAR_COG, never()));
        SanityFirstRegistry.register(new Entry(SUSPICIOUS_STEW, ENV_SANC, ENV_COG, never()));
        SanityFirstRegistry.register(new Entry(KILL_WARDEN, KILL_SANC, KILL_COG, never()));
        SanityFirstRegistry.register(new Entry(KILL_WITHER, KILL_SANC, KILL_COG, never()));
        SanityFirstRegistry.register(new Entry(KILL_ENDER_DRAGON, KILL_SANC, KILL_COG, never()));
    }

    // ------------------------------------------------------------------
    // 判据工具（全部只读、无副作用）
    // ------------------------------------------------------------------

    /** 行为类条目：不参与环境轮询（由事件方经 {@code reportFirstEncounter} 上报） */
    private static Predicate<SanityContext> never() {
        return ctx -> false;
    }

    /** 附近（半径内）是否存在指定类型的生物 */
    private static boolean nearby(SanityContext ctx, Class<? extends LivingEntity> type, double radius) {
        Player player = ctx.player();
        return !ctx.level().getEntitiesOfClass(type, player.getBoundingBox().inflate(radius)).isEmpty();
    }

    /** 附近（半径内）除自己以外是否有生物带着 {@code forbidden_hex:unnameable}（"遭遇不可名状"合并条的目击半边） */
    private static boolean nearbyHolder(SanityContext ctx, double radius) {
        Player player = ctx.player();
        return !ctx.level()
                .getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                        entity -> entity != player && SanityBuiltinRules.hasUnnameable(entity))
                .isEmpty();
    }

    /** 附近（半径内）是否有掉落物形式的目标物品 */
    private static boolean dropNear(SanityContext ctx, Item item) {
        Player player = ctx.player();
        return !ctx.level()
                .getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(DROP_RADIUS),
                        entity -> entity.getItem().is(item))
                .isEmpty();
    }

    /**
     * 背包内是否有目标物品：遍历 {@code Inventory#getContainerSize()}（41 格 = 主背包 36 + 护甲 4 + 副手 1，
     * 其中已含副手，满足"含副手"的口径）。41 次物品比较为常数级，不触发任何世界查询。
     *
     * <p>未覆盖外部容器（箱子 / 潜影盒内部）：那需要扫方块实体或递归 NBT，代价与收益不成正比；
     * 如后续需要，在此扩展而不改调用点。
     */
    private static boolean inventoryHas(Player player, Item item) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(item)) {
                return true;
            }
        }
        return false;
    }

    /** 采样点周围是否有满足条件的方块（半径 / 步长见常量） */
    private static boolean blockNear(SanityContext ctx, Predicate<Block> match) {
        BlockPos pos = ctx.pos();
        for (int dx = -BLOCK_SCAN_RADIUS; dx <= BLOCK_SCAN_RADIUS; dx += BLOCK_SCAN_STEP) {
            for (int dy = -BLOCK_SCAN_RADIUS; dy <= BLOCK_SCAN_RADIUS; dy += BLOCK_SCAN_STEP) {
                for (int dz = -BLOCK_SCAN_RADIUS; dz <= BLOCK_SCAN_RADIUS; dz += BLOCK_SCAN_STEP) {
                    if (match.test(ctx.level().getBlockState(pos.offset(dx, dy, dz)).getBlock())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 灵魂火族判据：周围 {@link #BLOCK_SCAN_RADIUS} 格内有灵魂火 / 灵魂火把。
     *
     * <p><b>为什么选"附近方块"而不是"踩在上面"</b>：用户原文是"触碰到看见"，两者取其一即可；
     * 纯"踩在火上"几乎只能靠伤害触发（灵魂火会扣血），体验上偏惩罚性，而"看见"才是首见的本意
     * （与凋零玫瑰的"掉落物 / 背包内均算"同属宽松口径）。故取近距方块判据。
     */
    private static boolean soulFireNear(SanityContext ctx) {
        return blockNear(ctx, block -> block == Blocks.SOUL_FIRE
                || block == Blocks.SOUL_TORCH || block == Blocks.SOUL_WALL_TORCH);
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(AkaishiMod.MOD_ID, path);
    }

    /** 首见提示的 lang 键：由条目 id 路径统一派生，保证"一个条目一个键"且不会漏写 */
    private static String langKey(ResourceLocation id) {
        return "message.forbidden_hex.first_encounter." + id.getPath();
    }

    /**
     * 通用首见条目：判定交给纯谓词，其余（数值 / lang 键）由构造参数固定。
     *
     * <p>用 record 而不是可变类：条目实例被注册表长期持有，不可变对象天然线程安全。
     */
    private record Entry(ResourceLocation id, float sancDelta, float cogDelta,
                         Predicate<SanityContext> gate) implements ISanityFirstEncounter {

        @Override
        public boolean test(SanityContext ctx) {
            return ctx != null && ctx.player() != null && gate != null && gate.test(ctx);
        }

        @Override
        public String langKey() {
            return SanityBuiltinFirstEncounters.langKey(id);
        }
    }
}
