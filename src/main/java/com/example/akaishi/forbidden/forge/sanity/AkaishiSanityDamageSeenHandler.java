package com.example.akaishi.forbidden.forge.sanity;

import com.example.akaishi.forbidden.sanity.SanityServiceImpl;
import com.example.akaishi.forbidden.sanity.SanityState;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.player.Player;

import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 「挨过的伤害类型」的平台记录挂点（forge）：把玩家被什么伤害命中过记进
 * {@link SanityState#markDamageSeen}，供禁忌秘典的 DAMAGE 条件查询。
 *
 * <p><b>为什么用 {@link LivingHurtEvent} 而不是 {@code LivingDamageEvent}</b>：
 * 记的是"有没有被这种伤害<b>打中过</b>"，与最终扣多少血无关——护甲挡下、被吸收吃光的命中
 * 同样算"挨过"。{@code LivingHurtEvent} 在护甲结算之前触发、且伤害被减免到 0 也照样先触发，
 * 正合这个口径；{@code LivingDamageEvent} 只在最终值非 0 时才走，会漏掉"穿了一身好甲"的命中。
 *
 * <p><b>为什么单独一个类、不并进 {@code AkaishiSanityCombatHandler}</b>：那个类整段以
 * {@code ModConfig.sanityEnabled} 为前提（理智系统关掉就整体失效），而"挨过什么伤害"是秘典自己的
 * 事实记档，与理智开关无关；并进去会让"关掉理智"连带让秘典条件永远无法满足。两类各管一件事。
 *
 * <p><b>只记玩家自己挨的</b>：非玩家实体（怪物之间互殴、被自己的爆炸炸到）一律不记。
 * <b>节流</b>：同一 tick 内同一种伤害类型只落一次（判据在 {@link SanityState#markDamageSeen}，
 * 那里同时保证了"真的新增才打脏"），不同伤害类型在同 tick 各记一次，不丢信息。
 */
public final class AkaishiSanityDamageSeenHandler {

    public static final AkaishiSanityDamageSeenHandler INSTANCE = new AkaishiSanityDamageSeenHandler();

    private AkaishiSanityDamageSeenHandler() {
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return; // 只记玩家自己挨的
        }
        ResourceLocation id = damageTypeId(event.getSource());
        if (id == null) {
            return; // 伤害类型没有注册键（理论不会发生）：宁可不记，也不要写进一个空 id
        }
        SanityState state = SanityServiceImpl.state(player);
        if (state != null) {
            state.markDamageSeen(id.toString(), player.level().getGameTime());
        }
    }

    /** 伤害类型的注册键（{@code minecraft:wither} 这种 id），而不是显示用的 msgId */
    private static ResourceLocation damageTypeId(DamageSource source) {
        return source.typeHolder().unwrapKey().map(ResourceKey::location).orElse(null);
    }
}
