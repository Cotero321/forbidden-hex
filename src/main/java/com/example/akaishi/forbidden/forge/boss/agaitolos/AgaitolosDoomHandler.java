package com.example.akaishi.forbidden.forge.boss.agaitolos;

import com.example.akaishi.forbidden.effect.AkaishiForbiddenEffects;
import com.example.akaishi.forbidden.effect.DoomEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 凋亡（{@code forbidden_hex:doom}）的「<b>降低治疗</b>」平台生效层（Forge 侧）。
 * <p>
 * <b>为什么这条语义不能写在 common</b>：common 层没有任何"治疗"钩子 —— 原版
 * {@code LivingEntity#heal} 不派发平台无关事件，Architectury 的 {@code EntityEvent} 里也没有治疗事件
 * （已核对 architectury-forge 9.x 的 {@code EntityEvent} 源码：只有 LIVING_DEATH / LIVING_HURT /
 * LIVING_CHECK_SPAWN / ADD / ENTER_SECTION / ANIMAL_TAME）。故必须落在平台侧：
 * Forge 的 {@code LivingHealEvent} 由 {@code LivingEntity#heal} 经
 * {@code ForgeEventFactory.onLivingHeal} 派发，正是"治疗量落地前改量"的唯一入口
 * （原版 {@code MobEffects.REGENERATION} 与瞬间治疗也都会经过它）。
 * <p>
 * <b>语义与取值只有一份定义</b>：系数在 common 侧的 {@link DoomEffect#HEAL_MULTIPLIER}，
 * 本类只做接线（"谁带凋亡 ⇒ 治疗量乘系数"）。刻意<b>不</b>用 {@code setCanceled} 直接取消治疗：
 * 规格是"<b>降低</b>治疗"而不是"免疫治疗"。
 * <p>
 * 两端都生效：判据是纯函数、不读权威状态；服务端算出的血量照常同步覆盖客户端的本地预测。
 * <p>
 * 注册见 {@code AkaishiModForge} 的 forge 事件总线（与 {@code WardenBossHandler} 同一处）。
 */
public final class AgaitolosDoomHandler {

    public static final AgaitolosDoomHandler INSTANCE = new AgaitolosDoomHandler();

    private AgaitolosDoomHandler() {
    }

    /** 治疗前改量：带凋亡的目标，治疗量 × {@link DoomEffect#HEAL_MULTIPLIER} */
    @SubscribeEvent
    public void onLivingHeal(LivingHealEvent event) {
        LivingEntity entity = event.getEntity();
        // 注册表尚未就绪（理论上不可达）时不干预：本事件的宿主是"任何实体回血"，
        // 绝不能因为本模组自己的空指针把别人的治疗打断（异常隔离优先）
        MobEffect doom = AkaishiForbiddenEffects.DOOM == null ? null : AkaishiForbiddenEffects.DOOM.get();
        if (doom == null || !entity.hasEffect(doom)) {
            return;
        }
        event.setAmount(event.getAmount() * DoomEffect.HEAL_MULTIPLIER);
    }
}
