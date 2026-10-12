package com.example.akaishi.forbidden.sanity.content;

import com.example.akaishi.forbidden.api.sanity.ISanityRestoreSource;
import com.example.akaishi.forbidden.api.sanity.SanityChangeSource;
import com.example.akaishi.forbidden.api.sanity.SanityRestoreRegistry;
import com.example.akaishi.config.ModConfig;
import com.example.akaishi.forbidden.sanity.SanityCogCurve;
import com.example.akaishi.forbidden.sanity.SanityServiceImpl;
import com.example.akaishi.forbidden.sanity.SanityState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 首用结算入口（{@code SanityRestoreRegistry} 的<b>唯一消费点</b>）：玩家用完一件物品时判定并抬升理智上限。
 *
 * <p><b>为什么挂在"用完一口"而不是"右键开始使用"</b>：与食补同款口径——用药水途中被打断、
 * 把金苹果丢出去都不该算"用过了"；{@code LivingEntityUseItemEvent.Finish}（原版
 * {@code Item#finishUsingItem} 完成后派发）是三类物品共用的同一时点：食物（{@code Item#use} + 默认完成）、
 * 可饮用物（{@code PotionItem#finishUsingItem}）、牛奶桶（{@code MilkBucketItem#finishUsingItem}）
 * 都会走原版 {@code LivingEntity#completeUsingItem} ⇒ 一个挂点覆盖全部。
 *
 * <p><b>系数</b>：恢复量 = {@code sancAmount × SanityCogCurve.sancEfficiency(cog)}（本类是它的消费方）——
 * 认知越高，越能"理解并承受"这些经历，上限收益略低（与食补效力同族设计）。
 *
 * <p><b>记档</b>：一次性 id 记进 {@link SanityState#markFirstSeen}（与首见共用集合与存档容错口径），
 * 因此重登、死亡、换维度都不会重复领取；写值走 {@link SanityServiceImpl#addSancInternal}，
 * 夹取 / 值回调 / 阈值派发 / 同步脏标记一处不落。
 */
public final class SanityRestoreService {

    private static final Logger LOGGER = LogManager.getLogger("akaishi.sanity");

    private SanityRestoreService() {
    }

    /**
     * 首用结算入口（由平台侧 {@code LivingEntityUseItemEvent.Finish} 调用）。
     *
     * @param sanityVoided 本次"进食"的理智收益是否已被食补入口（{@code SanityFoodService.onItemEaten}）
     *                     判定为作废（20% 档及以下的"食物失效"）。为 true 时<b>整段跳过</b>首用 SANC ——
     *                     否则会出现"这一口的理智收益全部作废、上限却照样 +5/+6/+7"的口径矛盾。
     *                     非食补类物品（药水 / 牛奶桶等）该参数恒为 false，行为与从前一致。
     */
    public static void onItemUsed(Player player, ItemStack stack, boolean sanityVoided) {
        if (sanityVoided) {
            return; // 食物失效：理智收益全部作废（含首用上限）
        }
        if (player == null || stack == null || stack.isEmpty()
                || player.level().isClientSide || !ModConfig.sanityEnabled) {
            return;
        }
        SanityState state = SanityServiceImpl.state(player);
        if (state == null) {
            return;
        }
        // 一次结算内所有来源共用同一份系数：中途改认知值不会让同一次使用出现两套系数
        float efficiency = SanityCogCurve.sancEfficiency(state.cog());
        for (ISanityRestoreSource source : SanityRestoreRegistry.getAll()) {
            String sourceId = source.id().toString();
            if (source.onceOnly() && state.hasFirstSeen(sourceId)) {
                continue; // 已领取过：不跑匹配（匹配要读物品栈/药水类型）
            }
            boolean hit;
            try {
                hit = source.matches(player, stack);
            } catch (Throwable t) {
                LOGGER.warn("[akaishi] 理智首用匹配异常（本次视为未命中）: {}", sourceId, t);
                continue;
            }
            if (!hit) {
                continue;
            }
            if (source.onceOnly() && !state.markFirstSeen(sourceId)) {
                continue; // 记档竞争兜底：同一次使用内被别的来源抢先记档
            }
            SanityServiceImpl.instance().addSancInternal(player, source.sancAmount() * efficiency,
                    SanityChangeSource.RESTORE);
        }
    }
}
