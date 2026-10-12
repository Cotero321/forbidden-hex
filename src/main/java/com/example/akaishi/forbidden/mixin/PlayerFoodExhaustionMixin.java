package com.example.akaishi.forbidden.mixin;

import com.example.akaishi.forbidden.sanity.SanityPenalties;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 阈值惩罚·饱食度消耗 +100%（80% 档及以下）：把 {@code Player#causeFoodExhaustion(float)} 的入参翻倍。
 *
 * <p><b>为什么必须 mixin</b>：{@code FoodData#addExhaustion(float)} 是饥饿系统的唯一累加点，
 * 但它<b>拿不到玩家</b>（{@code FoodData} 不持有宿主引用），{@code Player#causeFoodExhaustion} 才是
 * "带玩家的那个入口"。字节码取证（1.20.1 / Forge 47.3.0）：
 * <pre>
 *   Player#causeFoodExhaustion(float):            // public void causeFoodExhaustion(float)
 *     if (abilities.invulnerable) return;
 *     if (level.isClientSide) return;              // 客户端直接返回 ⇒ 本 mixin 在客户端恒定无副作用
 *     foodData.addExhaustion(amount);              // FoodData#addExhaustion(float)
 * </pre>
 * 改在这里同时覆盖三条消耗来源：原版疾跑/跳跃/挖掘，以及 {@code Player#actuallyHurt} 里
 * 每次受伤调的 {@code causeFoodExhaustion(source.getFoodExhaustion())}（即"挨打也饿得快"）。
 *
 * <p>口径全部在 common 的 {@link SanityPenalties#exhaustionMultiplier(int)}（总开关关/客户端/无数据 ⇒ 返回 1）。
 */
@Mixin(Player.class)
public class PlayerFoodExhaustionMixin {

    @ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float akaishi$sanityExhaustionMultiplier(float amount) {
        float multiplier = SanityPenalties.exhaustionMultiplier(SanityPenalties.tierOf((Player) (Object) this));
        return multiplier == 1f ? amount : amount * multiplier;
    }
}
