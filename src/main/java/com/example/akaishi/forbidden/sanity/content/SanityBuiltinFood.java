package com.example.akaishi.forbidden.sanity.content;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.api.sanity.ISanityFoodProfile;
import com.example.akaishi.forbidden.api.sanity.SanityFoodRegistry;
import com.example.akaishi.config.ModConfig;
import com.example.akaishi.forbidden.item.ForbiddenMaterialItems;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;

/**
 * 内置食补档位（理智系统的"内容层"）：进食后理智在窗口内被分批补回，并携带保护/即时回补。
 *
 * <p><b>口径</b>（对齐 {@link ISanityFoodProfile} 的对外承诺）：
 * <ul>
 *   <li>{@code totalSan} 是<b>窗口内总量</b>，由 {@link SanityFoodSettlement} 按"剩余总量 / 剩余 tick"
 *       逐 tick 分摊（除不尽的部分自动落到后续 tick，不会因浮点截断而少补）；</li>
 *   <li>{@code instantSan} <b>立刻</b>施加，不参与分摊（附魔金苹果的"立刻 5"）；</li>
 *   <li>补量在<b>食用时</b>一次性乘好两个系数：连续食用衰减档位 × COG 食补效力系数
 *       （食用后写进窗口的是"最终生效量"，窗口分摊阶段不再二次乘系数 ⇒ 途中 COG 变化不会让窗口量突变）；</li>
 *   <li>{@code refreshTicks} 一律填 0（"跟随配置/内置默认"），使
 *       {@code sanity.foodRefreshTicks} 这一项配置真正可调而不是摆设（第三方档案填正数则以其自述为准）。</li>
 * </ul>
 *
 * <p>所有数值均为<b>待调手感值</b>（冻结自设计稿，未做平衡验证）。
 */
public final class SanityBuiltinFood {

    /** 内置恒定速率档位数量上限：用于"无衰减"物品（详见 {@link #noDecay()}） */
    private static final int NO_DECAY_TIER_COUNT = 8;

    /** 食补衰减重置时间的内置默认（tick）：18000 = 15 分钟（待调手感值） */
    public static final int DEFAULT_REFRESH_TICKS = 18_000;

    // ===== 物品 id（原版物品为主；金西瓜为本模组自有物品，见 SanityBuiltinFood.GOLDEN_MELON_SLICE）=====

    public static final ResourceLocation HONEY_BOTTLE = item("honey_bottle");
    public static final ResourceLocation GOLDEN_APPLE = item("golden_apple");
    public static final ResourceLocation ENCHANTED_GOLDEN_APPLE = item("enchanted_golden_apple");
    public static final ResourceLocation GOLDEN_CARROT = item("golden_carrot");
    /**
     * 金西瓜：<b>本模组自有</b>食物（原版 {@code minecraft:glistering_melon_slice} 不可食用，
     * 故 P2 改为新建物品承载该档位，原版物品不再注册）。
     */
    public static final ResourceLocation GOLDEN_MELON_SLICE =
            new ResourceLocation(AkaishiMod.MOD_ID, ForbiddenMaterialItems.GOLDEN_MELON_SLICE_ID);
    public static final ResourceLocation SUSPICIOUS_STEW = item("suspicious_stew");

    /** 重复注册保护（init 被重复调用时只注册一次） */
    private static boolean registered;

    private SanityBuiltinFood() {
    }

    /** 注册全部内置食补档位（由 {@code AkaishiMod.init} 调用；幂等） */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        // 蜂蜜瓶：30s / 总量 10 / 无附加；三档衰减 100% · 60% · 10%
        SanityFoodRegistry.register(new Profile(HONEY_BOTTLE, 600, 10f, 0f, 0f,
                new float[]{1.0f, 0.6f, 0.1f}));
        // 金苹果：30s / 总量 30 / 保护 10；四档衰减 100% · 70% · 40% · 10%
        SanityFoodRegistry.register(new Profile(GOLDEN_APPLE, 600, 30f, 10f, 0f,
                new float[]{1.0f, 0.7f, 0.4f, 0.1f}));
        // 附魔金苹果：20s / 总量 40 / 保护 15 / 立刻 5
        // 待确认：设计稿只写"效能同上"，此处沿用金苹果的 100/70/40/10 四档（未另给档位表）
        SanityFoodRegistry.register(new Profile(ENCHANTED_GOLDEN_APPLE, 400, 40f, 15f, 5f,
                new float[]{1.0f, 0.7f, 0.4f, 0.1f}));
        // 金萝卜：30s / 总量 15 / 附加 30s 夜视（附加效果见 SanityFoodService）；无衰减
        SanityFoodRegistry.register(new Profile(GOLDEN_CARROT, 600, 15f, 0f, 0f, noDecay()));
        // 金西瓜：30s / 总量 10 / 附加 5s 生命回复 I；无衰减
        // 注：原版 minecraft:glistering_melon_slice 不可食用（纯酿造材料），故 P2 新建 forbidden_hex:forbidden_hex_golden_melon_slice 承载本档位，
        // 原版物品的档位已移除（避免"两条并存"导致原版物品也被扣/补理智而无获取路径）
        SanityFoodRegistry.register(new Profile(GOLDEN_MELON_SLICE, 600, 10f, 0f, 0f, noDecay()));
        // 迷之炖菜：5s / 总量 ±10（食用时随机掷定，见 SanityFoodService）/ 附加 4s 生命随机波动
        SanityFoodRegistry.register(new Profile(SUSPICIOUS_STEW, 100, 10f, 0f, 0f,
                new float[]{1.0f}));
    }

    /**
     * 食补衰减的重置时间（tick）：档案自述优先；自述 0 = 跟随配置；配置写 0 = 用内置默认。
     *
     * <p>三级回退保证"任何一个环节没配"都不会退化成"永不重置"（那会让衰减档位永久停在低效档）。
     */
    public static int refreshTicksOf(ISanityFoodProfile profile) {
        if (profile != null && profile.refreshTicks() > 0) {
            return profile.refreshTicks();
        }
        return ModConfig.sanityFoodRefreshTicks > 0 ? ModConfig.sanityFoodRefreshTicks : DEFAULT_REFRESH_TICKS;
    }

    /**
     * "无衰减"档位表。
     *
     * <p>API 约定<b>数组长度不足即视为该次数起不再生效</b>，故"永不衰减"只能用足够长的全 1 数组近似：
     * 8 档意味着 15 分钟窗口内连续吃到第 9 口才开始失效——按原版进食节奏（每口都有使用动画 + 饱食上限）
     * 到不了第 9 口；真要无限档位需要改 API 语义，本轮不动 API。
     */
    private static float[] noDecay() {
        float[] tiers = new float[NO_DECAY_TIER_COUNT];
        Arrays.fill(tiers, 1.0f);
        return tiers;
    }

    private static ResourceLocation item(String path) {
        return new ResourceLocation("minecraft", path);
    }

    /** 通用食补档位：窗口/总量/保护/即时量/档位表五位一体 */
    private record Profile(ResourceLocation itemId, int windowTicks, float totalSan, float protection,
                           float instantSan, float[] decayTiers) implements ISanityFoodProfile {

        @Override
        public float[] decayTiers() {
            // 返回副本：调用方（结算层/第三方）拿到的是快照，改不动本档位内部的数组
            return decayTiers.clone();
        }

        @Override
        public int refreshTicks() {
            return 0; // 0 = 跟随配置/内置默认，见 refreshTicksOf
        }
    }
}
