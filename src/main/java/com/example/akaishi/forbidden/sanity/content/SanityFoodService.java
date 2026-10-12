package com.example.akaishi.forbidden.sanity.content;

import com.example.akaishi.forbidden.api.sanity.ISanityFoodProfile;
import com.example.akaishi.forbidden.api.sanity.SanityChangeSource;
import com.example.akaishi.forbidden.api.sanity.SanityFoodRegistry;
import com.example.akaishi.config.ModConfig;
import com.example.akaishi.forbidden.sanity.SanityCogCurve;
import com.example.akaishi.forbidden.sanity.SanityPenalties;
import com.example.akaishi.forbidden.sanity.SanityServiceImpl;
import com.example.akaishi.forbidden.sanity.SanityState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 食补入口：进食后按档位开启/刷新"窗口分摊"，并施加即时量与附加效果。
 *
 * <p><b>为什么食用时就把系数乘完</b>：窗口总量一旦写进 {@link SanityState.FoodRuntime}，
 * 后续每 tick 的分摊只做除法（剩余量 / 剩余 tick）。若把 COG 系数留到分摊阶段再乘，
 * 玩家在窗口期内吃认知药会瞬间放大整段窗口的补量（"先吃金苹果再补 COG"成为最优解），
 * 与"补量由进食那一刻的状态决定"的设计意图不符。
 *
 * <p><b>连续食用衰减</b>：同物品 15 分钟（{@link SanityBuiltinFood#refreshTicksOf}）内重复食用按档位递减，
 * 超时回满效；档位不足（数组长度用尽）视为该次数起不再生效（API 约定）。计数与上次食用时刻落盘
 * （{@code food_states} 的 {@code chain}/{@code last_eat}），故重登不清零、窗口跨重登继续分摊。
 */
public final class SanityFoodService {

    /** 金萝卜附加夜视时长（tick）：600 = 30s（用原版夜视，不新增任何自定义效果；待调手感值） */
    public static final int GOLDEN_CARROT_NIGHT_VISION_TICKS = 600;
    /** 金西瓜附加生命回复时长（tick）：100 = 5s，I 级（待调手感值） */
    public static final int MELON_REGEN_TICKS = 100;
    public static final int MELON_REGEN_AMPLIFIER = 0;
    /** 迷之炖菜的理智幅度上界（|Δ| &lt; 10，取 9.9 保证严格小于 10；待调手感值） */
    public static final float STEW_SAN_AMPLITUDE = 9.9f;

    private SanityFoodService() {
    }

    /**
     * 进食结算入口（由平台侧 {@code LivingEntityUseItemEvent.Finish} 调用）。
     *
     * <p>只处理"注册过档位"的物品；未注册物品直接返回（原版食物绝大多数不参与理智结算）。
     * 非服务端、系统总开关关闭、无 capability 时一律静默返回（不抛异常、不影响原版进食结果）。
     *
     * @return <b>本次进食的理智收益是否已作废</b>（仅 20% 档及以下的"食物失效"掷中时为 true）。
     *         平台侧据此决定要不要继续走首用 SANC 链路——"收益全部作废"必须同时覆盖首用，
     *         否则会出现"理智收益没了、上限却照样 +5/+6/+7"的口径矛盾。
     */
    public static boolean onItemEaten(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty() || player.level().isClientSide
                || !ModConfig.sanityEnabled) {
            return false;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        ISanityFoodProfile profile = SanityFoodRegistry.get(itemId);
        if (profile == null) {
            // 未注册档位：不是"失效"，而是本入口不管它（药水/牛奶等首用物品仍走首用链路）
            return false;
        }
        SanityState state = SanityServiceImpl.state(player);
        if (state == null) {
            return false;
        }
        // 阈值惩罚·20% 档及以下：食用食物有概率"失效"。
        // 语义（本期定义）：本次进食的理智收益【全部作废】——不开窗口、不给即时量、不给附加效果、
        // 不计链计数、也不触发"初次食用迷之炖菜"这类行为首见，且<b>首用 SANC 一并作废</b>
        // （由本方法返回 true 透传给调用方）；食物本身照常被消耗、饱食度照常恢复。
        // 之所以选"作废收益"而不是"取消这次进食"：原版进食完成事件不可取消（Finish 只是通知），
        // 强行回退物品消耗会与既有食补/首用两条链路打结，代价远大于收益。
        if (SanityPenalties.tierOf(state) >= SanityPenalties.TIER_20
                && player.getRandom().nextFloat() < SanityPenalties.FOOD_FAIL_CHANCE) {
            player.displayClientMessage(Component.translatable("message.forbidden_hex.sanity.food_failed"), true);
            return true;
        }
        // 初次食用迷之炖菜：行为类首见，环境轮询表达不了"吃"这个动作，故在"真的吃完一口"的同一时点程序化上报
        // （是否首次由核心按存档判定，本处只负责"发生了什么"）
        if (SanityBuiltinFood.SUSPICIOUS_STEW.equals(itemId)) {
            SanityServiceImpl.instance()
                    .reportFirstEncounter(player, SanityBuiltinFirstEncounters.SUSPICIOUS_STEW);
        }
        long now = player.level().getGameTime();
        SanityState.FoodRuntime runtime = state.foodRuntime(itemId.toString());
        int chain = nextChain(runtime, now, SanityBuiltinFood.refreshTicksOf(profile));
        float tier = tierFactor(profile, chain);
        if (tier <= 0f) {
            // 档位用尽：本次食用对理智完全无效（但仍登记链计数，避免"一直吃就一直是第 N 档"）。
            // 这不是"食物失效"，故不算作废（首用链路照旧）。
            runtime.setChain(chain, now);
            state.markDirty();
            return false;
        }
        // 食用时乘完两个系数：档位衰减 × COG 食补效力
        float scale = tier * SanityCogCurve.foodEfficiency(state.cog());
        float windowSan = windowAmount(player, profile, itemId) * scale;
        // 保护随档位递减（待确认：设计稿未说明"附加保护"是否也按档位衰减，此处按"同档递减、不乘 COG"处理）
        float windowProtection = profile.protection() * tier;
        runtime.setWindow(windowSan, profile.windowTicks(), windowProtection);
        runtime.setChain(chain, now);
        state.markDirty();
        float instant = profile.instantSan() * scale;
        if (instant != 0f) {
            SanityServiceImpl.instance().addSanInternal(player, instant, SanityChangeSource.FOOD);
        }
        applyEatSideEffects(player, itemId);
        return false;
    }

    /**
     * 调试用：清空某物品的食补窗口与链计数（下次食用按第 1 档全额生效）。
     *
     * @return 该物品是否注册过档位（未注册返回 false）
     */
    public static boolean resetChain(Player player, ResourceLocation itemId) {
        if (player == null || itemId == null || SanityFoodRegistry.get(itemId) == null) {
            return false;
        }
        SanityState state = SanityServiceImpl.state(player);
        if (state == null) {
            return false;
        }
        SanityState.FoodRuntime runtime = state.foodRuntime(itemId.toString());
        runtime.setWindow(0f, 0, 0f);
        runtime.setChain(0, 0L);
        state.markDirty();
        return true;
    }

    /** 连续食用链计数：距上次食用超过重置时间则回到第 1 档，否则递增 */
    private static int nextChain(SanityState.FoodRuntime runtime, long now, int refreshTicks) {
        boolean continuous = runtime.chain() > 0 && runtime.lastEatTick() > 0
                && now - runtime.lastEatTick() <= refreshTicks;
        return continuous ? runtime.chain() + 1 : 1;
    }

    /** 档位系数：第 1 档 = tiers[0]；越界（档位用尽）返回 0 */
    private static float tierFactor(ISanityFoodProfile profile, int chain) {
        float[] tiers = profile.decayTiers();
        int index = chain - 1;
        return index >= 0 && index < tiers.length ? tiers[index] : 0f;
    }

    /**
     * 窗口内总量（原始，未乘系数）。
     *
     * <p>迷之炖菜例外：设计稿要求"随机 ±&lt;10 SAN"，档位表只能存静态数值，
     * 故把档位的 {@code totalSan} 视为<b>幅度</b>，每次食用随机掷定符号与大小
     * （待确认：原文歧义，此处按"均匀取 [−9.9, +9.9)"读）。
     */
    private static float windowAmount(Player player, ISanityFoodProfile profile, ResourceLocation itemId) {
        if (SanityBuiltinFood.SUSPICIOUS_STEW.equals(itemId)) {
            return (player.getRandom().nextFloat() * 2f - 1f) * STEW_SAN_AMPLITUDE;
        }
        return profile.totalSan();
    }

    /** 附加效果：金萝卜夜视 / 金西瓜生命回复 / 迷之炖菜生命随机波动（服务端施加，随原版效果同步） */
    private static void applyEatSideEffects(Player player, ResourceLocation itemId) {
        if (SanityBuiltinFood.GOLDEN_CARROT.equals(itemId)) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,
                    GOLDEN_CARROT_NIGHT_VISION_TICKS, 0, true, false));
            // 「亮度 < 7 时食用不刷新暗处"重见天日"冷却」：本实现里该冷却<b>唯一</b>的起算点是
            // SanityDarkCycle 观测到"重见光明"（canSeeSky 且白天），食补路径从不写
            // dark_cooldown_until / dark_cycle_ended，故"在暗处吃金萝卜"天然不会刷新冷却；
            // 金萝卜的夜视只通过 SanityDarkCycle.nightVisionImmune 暂停暗处扣减，不动暴露周期与冷却起点。
            return;
        }
        if (SanityBuiltinFood.GOLDEN_MELON_SLICE.equals(itemId)) {
            // 金西瓜：5s 生命回复 I（本模组自有食物，档位与首用上限见 SanityBuiltinFood / SanityBuiltinRestores）
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION,
                    MELON_REGEN_TICKS, MELON_REGEN_AMPLIFIER, true, false));
            return;
        }
        if (SanityBuiltinFood.SUSPICIOUS_STEW.equals(itemId) && player instanceof ServerPlayer server) {
            SanityFoodSettlement.startStewRider(server);
        }
    }
}
