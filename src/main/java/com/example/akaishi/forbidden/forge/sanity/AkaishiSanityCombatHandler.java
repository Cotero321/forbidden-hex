package com.example.akaishi.forbidden.forge.sanity;

import com.example.akaishi.config.ModConfig;
import com.example.akaishi.effect.ModDamageTypes;
import com.example.akaishi.forbidden.sanity.SanityDamageGuard;
import com.example.akaishi.forbidden.sanity.SanityPenalties;
import com.example.akaishi.forbidden.sanity.SanityServiceImpl;
import com.example.akaishi.forbidden.sanity.SanityState;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 阈值惩罚的<b>伤害侧挂点</b>（Forge 服务端）：攻击效能、易伤、护甲效能、0% 档精神化、骷髅凋零。
 *
 * <p><b>为什么同时挂两个事件（源码/字节码依据）</b>：反编译 1.20.1（Forge 47.3.0）的
 * {@code Player#actuallyHurt} 字节码，调用顺序是：
 * <pre>
 *   ForgeHooks.onLivingHurt(this, src, amount)      ← LivingHurtEvent（护甲之前）
 *   amount = getDamageAfterArmorAbsorb(...)         ← 护甲 + 韧性
 *   amount = getDamageAfterMagicAbsorb(...)         ← 抗性提升 + 保护附魔
 *   f3 = max(amount - getAbsorptionAmount(), 0)     ← 伤害吸收
 *   f3 = ForgeHooks.onLivingDamage(this, src, f3)   ← LivingDamageEvent（最终值，护甲/附魔/吸收之后）
 *   if (f3 != 0) { recordDamage; setHealth(health - f3); }
 * </pre>
 * 因此：<b>需要"护甲之前"的量</b>（护甲效能还原、精神化的原始伤害）必须读 {@link LivingHurtEvent}；
 * <b>要把结果精确地加到最终伤害上</b>必须写 {@link LivingDamageEvent}
 * （Forge 对该事件的官方说明逐字写着"armor, potion and absorption modifiers have already been applied
 * — this is FINAL value"）。
 *
 * <p><b>"护甲效能 e" 的公式与依据</b>（e = 0.9 / 0.8，见 {@link SanityPenalties}）：
 * 设计口径是"护甲提供的那部分减伤只有 e 有效" ⇒ 应在最终伤害上<b>还原失效的那一截</b>：
 * <pre>
 *   LivingHurtEvent：armored = CombatRules.getDamageAfterAbsorb(amount, 护甲, 韧性)（与原版同函数同参数）
 *                    absorbed = amount - armored
 *                    暂存 restore = absorbed × (1 - e)
 *   LivingDamageEvent：final += restore
 * </pre>
 * 之所以不在 LivingHurtEvent 里直接改 amount：那里的量还要被原版<b>再乘一次护甲</b>，
 * 改小了会被二次吸收（超额减伤）、改大了还会被吸收（抵不过），只有"事后还原"才是精确的
 * "护甲的那一份打了折"。{@code bypasses_armor} 的伤害（含精神伤害）没有护甲那一份，直接跳过。
 *
 * <p><b>0% 档"受到伤害全部转为精神伤害"</b>：口径是"本次伤害按精神伤害结算" ——
 * 精神伤害进 {@code bypasses_armor / bypasses_resistance / bypasses_enchantments} 三条标签，
 * 即只被伤害吸收（absorption）减免。故在 LivingHurtEvent 记下 {@code (原始量, 吸收量)}，
 * 在 LivingDamageEvent <b>绝对设定</b>最终值 = {@code max(原始量 − 吸收量, 0)}，
 * 再套用本模组对精神伤害的 COG 减免（{@link SanityDamageGuard}，与 BOSS 精神伤害同一条减免口径）。
 * 换壳<b>不构造新伤害源、不动 {@code psychic_until}</b>：只按标记改写最终数值，
 * 与本项目 BOSS 的"换 DamageSource"做法解耦（避免无敌帧/击杀归属/死亡消息被牵动）。
 * 已在精神伤害通道里的伤害（{@code forbidden_hex:psychic}）原样跳过，避免二次减免。
 */
public final class AkaishiSanityCombatHandler {

    public static final AkaishiSanityCombatHandler INSTANCE = new AkaishiSanityCombatHandler();

    /** 暂存条目的有效期（tick）：Hurt 与 Damage 必然同一次结算，留 2 tick 余量防跨帧异常 */
    private static final long PENDING_TTL_TICKS = 2L;

    /** 玩家 UUID → 本次结算的暂存（LivingHurtEvent 记、LivingDamageEvent 用、用后即删） */
    private final ConcurrentMap<UUID, Pending> pending = new ConcurrentHashMap<>();

    private AkaishiSanityCombatHandler() {
    }

    /** 一次结算的暂存：护甲还原量，或（精神化时）原始量 + 吸收量 */
    private record Pending(long tick, float restore, float incoming, float absorption, boolean psychic) {
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide || !ModConfig.sanityEnabled) {
            return;
        }
        long now = event.getEntity().level().getGameTime();
        prunePending(now);
        float amount = event.getAmount();
        // 1) 攻击效能：玩家造成伤害 ×0.9（80% 档）/ ×0.8（60% 档及以下）
        if (event.getSource().getEntity() instanceof Player attacker
                && !attacker.level().isClientSide) {
            float attackMult = SanityPenalties.attackMultiplier(SanityPenalties.tierOf(attacker));
            if (attackMult != 1f) {
                amount *= attackMult;
            }
        }
        // 2) 受害方
        if (!(event.getEntity() instanceof Player victim) || victim.level().isClientSide) {
            event.setAmount(amount);
            return;
        }
        SanityState state = SanityServiceImpl.state(victim);
        if (state == null) {
            event.setAmount(amount);
            return;
        }
        int tier = SanityPenalties.tierOf(state);
        // 2a) 易伤：40% 档及以下受到伤害 ×1.2
        amount *= SanityPenalties.incomingMultiplier(tier);
        boolean psychicSource = event.getSource().is(ModDamageTypes.PSYCHIC);
        boolean psychic = !psychicSource
                && (state.lowSanPsychic() || SanityPenalties.isLowSanPsychic(tier));
        if (psychic) {
            // 2b) 0% 档精神化：暂存原始量与吸收量，最终值在 LivingDamageEvent 绝对设定
            pending.put(victim.getUUID(), new Pending(now, 0f,
                    amount, Math.max(0f, victim.getAbsorptionAmount()), true));
        } else if (!psychicSource) {
            // 2c) 护甲效能：暂存"护甲那一份里失效的部分"，最终值在 LivingDamageEvent 加回
            float effectiveness = SanityPenalties.armorEffectiveness(tier);
            if (effectiveness < 1f && !event.getSource().is(DamageTypeTags.BYPASSES_ARMOR)) {
                float armored = CombatRules.getDamageAfterAbsorb(amount,
                        victim.getArmorValue(),
                        (float) victim.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
                float absorbed = Math.max(0f, amount - armored);
                float restore = absorbed * (1f - effectiveness);
                if (restore > 0f) {
                    pending.put(victim.getUUID(), new Pending(now, restore, 0f, 0f, false));
                }
            }
        }
        // 3) 0% 档：骷髅的攻击附带凋零
        if (SanityPenalties.isLowSanPsychic(tier)
                && event.getSource().getEntity() instanceof AbstractSkeleton) {
            victim.addEffect(new MobEffectInstance(MobEffects.WITHER,
                    SanityPenalties.SKELETON_WITHER_TICKS, SanityPenalties.SKELETON_WITHER_AMPLIFIER,
                    false, true));
        }
        event.setAmount(amount);
    }

    @SubscribeEvent
    public void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        long now = player.level().getGameTime();
        Pending stored = pending.remove(player.getUUID());
        if (stored == null || stored.tick() != now) {
            // 无暂存 / 暂存不属于"本次结算"（Hurt 与 Damage 必然在同一 tick 的同一调用栈内）：
            // 直接丢弃，避免"上一次被取消的 Hurt"误配到"这一次的 Damage"上
            prunePending(now);
            return;
        }
        if (stored.psychic()) {
            float desired = Math.max(stored.incoming() - stored.absorption(), 0f);
            event.setAmount(SanityDamageGuard.applyPsychicReduction(player, desired));
        } else if (stored.restore() > 0f) {
            event.setAmount(event.getAmount() + stored.restore());
        }
        prunePending(now);
    }

    /** 清掉久未消费的暂存（Hurt 被取消 / 玩家中途退出时不会长期钉住 UUID） */
    private void prunePending(long now) {
        if (pending.isEmpty()) {
            return;
        }
        pending.entrySet().removeIf(entry -> now - entry.getValue().tick() > PENDING_TTL_TICKS);
    }
}
