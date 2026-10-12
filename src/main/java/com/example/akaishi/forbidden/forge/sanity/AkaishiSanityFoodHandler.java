package com.example.akaishi.forbidden.forge.sanity;

import com.example.akaishi.forbidden.sanity.content.SanityFoodService;
import com.example.akaishi.forbidden.sanity.content.SanityRestoreService;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 「用完一口」触发点（Forge 服务端）：把原版进食 / 饮用完成事件转给 common 的两个入口。
 *
 * <p><b>为什么挂在 {@link LivingEntityUseItemEvent.Finish} 而不是 {@code Item#use}</b>：
 * ① Finish 是"物品真的用完/吃完"的时点，与"这一口生效"语义一致（Start 时取消进食等场景不会误触发）；
 * ② 挂在事件上不必改任何原版物品类，金苹果/金萝卜/迷之炖菜等原版物品全部自然覆盖；
 * ③ 与项目既有做法一致（{@code AkaishiSocketEffectHandler#onItemEaten} 同款挂点）。
 *
 * <p><b>一个挂点覆盖三类物品</b>：原版 {@code LivingEntity#completeUsingItem} 对
 * 食物（{@code Item#use} 后完成）、药水（{@code PotionItem#finishUsingItem}）、
 * 牛奶桶（{@code MilkBucketItem#finishUsingItem}）统一走 {@code ForgeEventFactory#onItemUseFinish}，
 * 该处派发本事件 —— 故"食物 / 药水 / 牛奶"不需要各自的钩子，也不会漏掉任何一类。
 *
 * <p>客户端侧同样会收到该事件，故两个 common 入口各自判 {@code isClientSide} 后直接返回；
 * 本类只做搬运，不含任何数值口径（口径全在 common，便于将来跨加载器复用）。
 */
public final class AkaishiSanityFoodHandler {

    public static final AkaishiSanityFoodHandler INSTANCE = new AkaishiSanityFoodHandler();

    private AkaishiSanityFoodHandler() {
    }

    @SubscribeEvent
    public void onItemEaten(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof Player player) {
            // 食补（窗口分摊 + 即时量 + 附加效果）：返回"本次理智收益是否作废"（20% 档食物失效）
            boolean sanityVoided = SanityFoodService.onItemEaten(player, event.getItem());
            // 首用（SANC 一次性抬升，含药水 / 牛奶 / 金西瓜等非"食补档位"物品）。
            // 食物失效时一并作废：否则"收益全部作废"只挡了食补、挡不住上限 +5/+6/+7。
            SanityRestoreService.onItemUsed(player, event.getItem(), sanityVoided);
        }
    }
}
