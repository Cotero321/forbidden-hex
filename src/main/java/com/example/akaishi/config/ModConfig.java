package com.example.akaishi.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/** 禁忌模块独立服务端配置；默认值与范围沿用原配置。 */
public final class ModConfig {
    public static volatile boolean sanityEnabled = true;
    public static volatile int sanityDarkLightCooldownTicks = 18_000;
    public static volatile int sanityFoodRefreshTicks = 18_000;
    public static volatile int sanityNetherRoofY = 128;
    public static volatile int sanityNaturalRegenPeriodTicks = 6_000;
    public static volatile int sanityFlowerCountThreshold = 10;
    public static volatile int sanitySleepDeprivationDays = 5;
    public static volatile double sanitySleepDeprivationDailyDebit = 10.0;

    public static volatile boolean altarDrainEnabled = true;
    public static volatile int altarDrainRadius = 64;
    public static volatile int altarDrainIntervalTicks = 20;
    public static volatile double altarDrainHealthPercent = 0.10;
    public static volatile long altarDrainEnergyPerHp = 1000L;
    public static volatile int altarDrainMaxTargets = 16;
    public static volatile long altarDrainMaxEnergyPerTarget = 5000L;
    public static volatile boolean altarDrainExemptCreative = true;
    public static volatile int altarNewRecipeTierRequired = 3;
    public static volatile long altarNewRecipeProgressMax = 800_000L;
    public static volatile long altarLegacyProgressMax = 80_000L;

    private static final ForgeConfigSpec.BooleanValue SANITY_ENABLED;
    private static final ForgeConfigSpec.IntValue DARK_LIGHT_COOLDOWN;
    private static final ForgeConfigSpec.IntValue FOOD_REFRESH;
    private static final ForgeConfigSpec.IntValue NETHER_ROOF_Y;
    private static final ForgeConfigSpec.IntValue NATURAL_REGEN_PERIOD;
    private static final ForgeConfigSpec.IntValue FLOWER_COUNT;
    private static final ForgeConfigSpec.IntValue SLEEP_DAYS;
    private static final ForgeConfigSpec.DoubleValue SLEEP_DEBIT;
    private static final ForgeConfigSpec.BooleanValue DRAIN_ENABLED;
    private static final ForgeConfigSpec.IntValue DRAIN_RADIUS;
    private static final ForgeConfigSpec.IntValue DRAIN_INTERVAL;
    private static final ForgeConfigSpec.DoubleValue DRAIN_HEALTH_PERCENT;
    private static final ForgeConfigSpec.LongValue DRAIN_ENERGY_PER_HP;
    private static final ForgeConfigSpec.IntValue DRAIN_MAX_TARGETS;
    private static final ForgeConfigSpec.LongValue DRAIN_MAX_ENERGY;
    private static final ForgeConfigSpec.BooleanValue DRAIN_EXEMPT_CREATIVE;
    private static final ForgeConfigSpec.IntValue NEW_RECIPE_TIER;
    private static final ForgeConfigSpec.LongValue NEW_RECIPE_PROGRESS;
    private static final ForgeConfigSpec.LongValue LEGACY_PROGRESS;
    public static final ForgeConfigSpec SPEC;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("sanity");
        SANITY_ENABLED = builder.comment("理智系统总开关：关闭后不结算理智，也不绘制 HUD。")
                .define("enabled", true);
        DARK_LIGHT_COOLDOWN = builder.comment("重见光明后的暗处冷却 (tick)；0 = 内置默认 18000。")
                .defineInRange("darkLightCooldownTicks", 18_000, 0, Integer.MAX_VALUE);
        FOOD_REFRESH = builder.comment("连续食用衰减的重置时间 (tick)；0 = 内置默认 18000。")
                .defineInRange("foodRefreshTicks", 18_000, 0, Integer.MAX_VALUE);
        NETHER_ROOF_Y = builder.comment("下界顶部的脚部 Y 判据；0 = 内置默认 128。")
                .defineInRange("netherRoofY", 128, 0, 320);
        NATURAL_REGEN_PERIOD = builder.comment("自然恢复基础周期 (tick)；0 = 内置默认 6000。")
                .defineInRange("naturalRegenPeriodTicks", 6_000, 0, Integer.MAX_VALUE);
        FLOWER_COUNT = builder.comment("自然恢复花丛加成的花朵数量阈值；0 = 内置默认 10。")
                .defineInRange("flowerCountThreshold", 10, 0, 1000);
        SLEEP_DAYS = builder.comment("睡眠剥夺开始惩罚的游戏日数；0 = 内置默认 5。")
                .defineInRange("sleepDeprivationDays", 5, 0, 1000);
        SLEEP_DEBIT = builder.comment("睡眠剥夺每天扣减的理智；0 = 内置默认 10。")
                .defineInRange("sleepDeprivationDailyDebit", 10.0, 0.0, 1000.0);
        builder.pop();
        builder.push("altar");
        DRAIN_ENABLED = builder.comment("配方齐备时自动吸取周边生物。")
                .define("drainEnabled", true);
        DRAIN_RADIUS = builder.defineInRange("drainRadius", 64, 4, 128);
        DRAIN_INTERVAL = builder.defineInRange("drainIntervalTicks", 20, 1, 200);
        DRAIN_HEALTH_PERCENT = builder.defineInRange("drainHealthPercent", 0.10, 0.0, 1.0);
        DRAIN_ENERGY_PER_HP = builder.defineInRange("drainEnergyPerHp", 1000L, 1L, 100_000L);
        DRAIN_MAX_TARGETS = builder.defineInRange("drainMaxTargets", 16, 1, 128);
        DRAIN_MAX_ENERGY = builder.comment("单只单次能量贡献上限；0 = 不限制。")
                .defineInRange("drainMaxEnergyPerTarget", 5000L, 0L, 1_000_000L);
        DRAIN_EXEMPT_CREATIVE = builder.define("drainExemptCreative", true);
        NEW_RECIPE_TIER = builder.defineInRange("newRecipeTierRequired", 3, 1, 3);
        NEW_RECIPE_PROGRESS = builder.defineInRange("newRecipeProgressMax", 800_000L, 1L, Long.MAX_VALUE);
        LEGACY_PROGRESS = builder.defineInRange("legacyProgressMax", 80_000L, 1L, Long.MAX_VALUE);
        builder.pop();
        SPEC = builder.build();
    }

    private ModConfig() {
    }

    /** 在模组构造阶段挂载配置加载与重载监听；入口另行注册 SERVER SPEC。 */
    public static void bake(IEventBus modBus) {
        modBus.addListener((ModConfigEvent.Loading event) -> apply(event));
        modBus.addListener((ModConfigEvent.Reloading event) -> apply(event));
    }

    private static void apply(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        sanityEnabled = SANITY_ENABLED.get();
        sanityDarkLightCooldownTicks = DARK_LIGHT_COOLDOWN.get();
        sanityFoodRefreshTicks = FOOD_REFRESH.get();
        sanityNetherRoofY = NETHER_ROOF_Y.get();
        sanityNaturalRegenPeriodTicks = NATURAL_REGEN_PERIOD.get();
        sanityFlowerCountThreshold = FLOWER_COUNT.get();
        sanitySleepDeprivationDays = SLEEP_DAYS.get();
        sanitySleepDeprivationDailyDebit = SLEEP_DEBIT.get();
        altarDrainEnabled = DRAIN_ENABLED.get();
        altarDrainRadius = DRAIN_RADIUS.get();
        altarDrainIntervalTicks = DRAIN_INTERVAL.get();
        altarDrainHealthPercent = DRAIN_HEALTH_PERCENT.get();
        altarDrainEnergyPerHp = DRAIN_ENERGY_PER_HP.get();
        altarDrainMaxTargets = DRAIN_MAX_TARGETS.get();
        altarDrainMaxEnergyPerTarget = DRAIN_MAX_ENERGY.get();
        altarDrainExemptCreative = DRAIN_EXEMPT_CREATIVE.get();
        altarNewRecipeTierRequired = NEW_RECIPE_TIER.get();
        altarNewRecipeProgressMax = NEW_RECIPE_PROGRESS.get();
        altarLegacyProgressMax = LEGACY_PROGRESS.get();
    }
}
