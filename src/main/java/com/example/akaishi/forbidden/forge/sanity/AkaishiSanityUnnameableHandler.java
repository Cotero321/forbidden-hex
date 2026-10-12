package com.example.akaishi.forbidden.forge.sanity;

import com.example.akaishi.forbidden.effect.AkaishiForbiddenEffects;
import com.example.akaishi.forbidden.sanity.content.SanityBuiltinRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 「遭遇不可名状」的起手一击（Forge 服务端）：效果刚被施加到玩家身上时立即扣一次理智。
 *
 * <p><b>为什么挂在 {@link MobEffectEvent.Added} 而不是轮询</b>：效果可以来自 BOSS 技能、母神祭坛仪式、
 * 禁忌套装自施等多个入口，逐个改施加点既侵入又容易漏；事件是它们的公共下游，"被施加"这件事只有一处能看见。
 * 本类只<b>读</b>效果并调 common 的扣减入口，不触碰 {@code UnnameableEffect} 的任何语义（时长 / 等级 / 表现全归原体系）。
 *
 * <p><b>去重口径（关键）</b>：Forge 的 {@code Added} 在"同一效果的时长 / 等级被刷新"时<b>也会再发一次</b>
 * （事件在写入效果表之前派发，且把"施加前已有的实例"作为 {@code getOldEffectInstance()} 带过来）。
 * 因此判定条件取 {@code getOldEffectInstance() == null} —— 等价于"施加前该玩家身上没有该效果"，
 * 即一次真正的遭遇；刷新、升级、同一 tick 内第二次施加都带着旧实例，一律跳过，天然去重。
 *
 * <p><b>覆盖范围提醒</b>：原版 {@code /effect give} 走的是 {@code forceAddEffect}（不入本事件的路径），
 * 故用指令强塞效果不会触发起手一击；本模组自身的全部施加入口都走 {@code addEffect}，均在覆盖内。
 * 周期扣减（{@code forbidden_hex:unnameable_aura}）不依赖本事件，指令场景同样生效。
 */
public final class AkaishiSanityUnnameableHandler {

    public static final AkaishiSanityUnnameableHandler INSTANCE = new AkaishiSanityUnnameableHandler();

    private AkaishiSanityUnnameableHandler() {
    }

    @SubscribeEvent
    public void onEffectAdded(MobEffectEvent.Added event) {
        if (event.getOldEffectInstance() != null) {
            return; // 刷新 / 升级 / 重复施加：不是"初次遭遇"
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return; // 只对玩家结算（生物没有理智值）
        }
        MobEffectInstance added = event.getEffectInstance();
        MobEffect unnameable = AkaishiForbiddenEffects.UNNAMEABLE == null ? null : AkaishiForbiddenEffects.UNNAMEABLE.get();
        if (added == null || unnameable == null || added.getEffect() != unnameable) {
            return;
        }
        SanityBuiltinRules.applyUnnameableOnset(player);
    }
}
