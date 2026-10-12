package com.example.akaishi.forbidden.sanity.shadow;

import com.example.akaishi.effect.ModDamageTypes;
import com.example.akaishi.forbidden.entity.AkaishiForbiddenEntities;
import com.example.akaishi.forbidden.sanity.SanityPenalties;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 影怪的出手面：<b>近战扑击</b>（40% 档即有）与<b>远程精神弹</b>（只有 20% 档）。
 *
 * <p><b>档位口径</b>：档位取自<b>目标玩家此刻</b>的 SAN 状态
 * （{@link SanityPenalties#tierOf(net.minecraft.world.entity.player.Player)}，
 * 与阈值惩罚共用同一个纯函数入口）。这样"20% 档更强更频繁"是<b>动态</b>的：
 * 玩家理智继续下坠时，已经在场的影怪会自然变得更有威胁，不需要影怪自己记录"我是哪一档生成的"。
 * 倍率常量收在 {@link SanityPenalties}（{@code SHADOW_DAMAGE_MULT_20} / {@code SHADOW_INTERVAL_MULT_20}），
 * 与其它档位数值同表，便于一处调平衡。
 *
 * <p><b>伤害源口径</b>：一律用 {@code forbidden_hex:psychic}（精神伤害），于是：
 * <ul>
 *   <li>不吃护甲/抗性/保护附魔（该类型自带三条绕过标签）；</li>
 *   <li>自动继承理智系统既有的 COG 精神减免（forge 侧 {@code AkaishiSanityDamageHandler}）与
 *       40% 档易伤 ×1.2（{@code AkaishiSanityCombatHandler}）—— 两处都是既有代码，本类不重复实现；</li>
 *   <li>0% 档"全伤害精神化"对精神伤害本身<b>跳过</b>，因此不会二次减免。</li>
 * </ul>
 *
 * <p>数值均为<b>待调手感值</b>。
 */
public final class ShadowCombat {

    // ===== 手感常量（待调手感值）=====

    /** 近战扑击基础伤害（40% 档基准；20% 档再乘 {@link SanityPenalties#SHADOW_DAMAGE_MULT_20}） */
    public static final float MELEE_DAMAGE = 4.0F;
    /** 精神弹基础伤害（同样按档位乘倍率） */
    public static final float BOLT_DAMAGE = 3.0F;

    /** 远程射程（格）：超出就不会开火（近战优先，见 {@code ShadowEntity#serverStep}） */
    public static final double RANGED_RANGE = 16.0D;

    /** 近战出手间隔（tick）：40 = 2s */
    public static final int MELEE_COOLDOWN_TICKS = 40;
    /** 远程出手间隔（tick）：60 = 3s */
    public static final int BOLT_COOLDOWN_TICKS = 60;

    /** 扑击推力（格/tick）：命中瞬间的冲量，读成"撞过去"而不是"站着挥一下" */
    public static final double LUNGE_SPEED = 0.6D;

    /** 弹体初速（格/tick）与散布（弧度） */
    public static final float BOLT_VELOCITY = 0.7F;
    public static final float BOLT_INACCURACY = 3.0F;

    private ShadowCombat() {
    }

    /** 近战扑击：冲量 → 播 attack → 结算精神伤害 */
    public static void melee(ShadowEntity shadow, ServerPlayer target, int tier) {
        shadow.setMeleeCooldown(scaledTicks(MELEE_COOLDOWN_TICKS, tier));
        shadow.playAction(ShadowAnimations.TRIGGER_ATTACK, ShadowAnimations.ATTACK_TICKS);
        Vec3 toward = target.position().subtract(shadow.position());
        if (toward.lengthSqr() > 1.0E-6D) {
            shadow.setDeltaMovement(toward.normalize().scale(LUNGE_SPEED));
        }
        target.hurt(psychic(shadow.level(), shadow, shadow),
                MELEE_DAMAGE * SanityPenalties.shadowDamageMultiplier(tier));
    }

    /** 远程精神弹：只有 20% 档及以下才开火；不在射程内或客户端一律不生成 */
    public static void fireBolt(ShadowEntity shadow, ServerPlayer target, int tier) {
        if (shadow.level().isClientSide() || !SanityPenalties.shadowRangedUnlocked(tier)) {
            return;
        }
        shadow.setRangedCooldown(scaledTicks(BOLT_COOLDOWN_TICKS, tier));
        shadow.playAction(ShadowAnimations.TRIGGER_ATTACK, ShadowAnimations.ATTACK_TICKS);
        ShadowBolt bolt = new ShadowBolt(AkaishiForbiddenEntities.SHADOW_BOLT.get(), shadow.level());
        // owner = 影怪：既是击杀归属，也是"别打自己"的判据（见 ShadowBolt#onHit）
        bolt.setOwner(shadow);
        bolt.setDamage(BOLT_DAMAGE * SanityPenalties.shadowDamageMultiplier(tier));
        Vec3 origin = shadow.getEyePosition();
        // 瞄准目标身体中部而非脚底，避免弹体一路撞地板（同 AgaitolosSkullSkill 的口径）
        Vec3 aim = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D).subtract(origin);
        bolt.setPos(origin.x, origin.y, origin.z);
        bolt.shoot(aim.x, aim.y, aim.z, BOLT_VELOCITY, BOLT_INACCURACY);
        shadow.level().addFreshEntity(bolt);
    }

    /**
     * 精神伤害源（{@code forbidden_hex:psychic}）。
     *
     * <p>伤害类型键取自 {@link ModDamageTypes#PSYCHIC}（<b>中立真源</b>，与 forge 侧
     * {@code AkaishiSanityDamageHandler} 读的是同一个键）—— 键定义在中立类后，<b>理智侧不再 import
     * BOSS 包</b>（这是"理智系统与 BOSS 必须解耦"的落点），也不重复写 id 字符串
     * （那会让"键换名时理智侧静默失效"）。客户端 / 非 ServerLevel 走
     * {@code generic()} 兜底，避免 NPE（与 BOSS 侧同类工厂同款）。
     */
    public static DamageSource psychic(Level level, Entity directEntity, Entity causingEntity) {
        if (level instanceof ServerLevel serverLevel) {
            Registry<DamageType> registry = serverLevel.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
            Holder<DamageType> holder = registry.getHolderOrThrow(ModDamageTypes.PSYCHIC);
            if (directEntity == null && causingEntity != null) {
                // 两参重载：内部即 this(type, entity, entity)，同时给出"来源位置"（盾牌朝向判定要用）
                return new DamageSource(holder, causingEntity);
            }
            return new DamageSource(holder, directEntity, causingEntity);
        }
        return level.damageSources().generic();
    }

    /** 出手间隔按档位折算（20% 档更频繁） */
    private static int scaledTicks(int baseTicks, int tier) {
        return Math.max(1, Math.round(baseTicks * SanityPenalties.shadowIntervalMultiplier(tier)));
    }
}
