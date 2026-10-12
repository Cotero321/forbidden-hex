package com.example.akaishi.forbidden.boss.agaitolos.skill;

import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosCombat;
import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosDoom;
import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosEntity;
import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosPsychic;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * 二阶段技能「高速踢击」的<b>伤害面</b>（设计文档 §0 阶段二 / §1 定案表第 28 条，P5）。
 * <p>
 * 规格原文：「高速踢击：任何状态下可用，快速踢向玩家造成凋零 III 和缓慢效果，
 * 造成 20% 最大生命值伤害，无视 40% 护甲」。
 * <p>
 * <b>"任何状态"的实现口径</b>：规格的"任何状态"指的是<b>不限定飞行 / 地面 / 高度</b>
 * （与瞬击恰相反：瞬击只能在禁飞窗口用，踢击在悬停、禁飞、任何高度都能起手）；
 * <b>并且含技能封印期</b> —— "任何状态"字面上就包含了"被玩家格挡后的惩罚窗口"，故踢击不吃封印。
 * 但<b>死亡 / 复活 / 架势 / 蓄力 / 冲锋</b>这五种"正在做别的事"的状态仍然禁止起手 ——
 * 它们与其它招式共用同一批骨骼动画，同时成立会互相拉扯
 * （详见 {@code AgaitolosEntity#canStartPhaseTwoSkill} 与 {@code #isScytheSealed()}）。
 * <p>
 * <b>伤害结算三件套</b>：
 * <ol>
 *   <li>基础伤害 = <b>目标</b>最大生命 × {@link #DAMAGE_MAX_HEALTH_RATIO}
 *       —— 与俯冲镰扫的"30% 最大生命"同句式、同口径（同一句规格里的"玩家最大生命"）；</li>
 *   <li>「无视 40% 护甲」= 复用 {@link AgaitolosCombat#damageAfterPartialArmorBypass}（传入
 *       {@link #ARMOR_KEPT_RATIO} = 0.6），与俯冲镰扫的 30% 共用同一段代码，仅比例参数不同；</li>
 *   <li>伤害源用 {@code forbidden_hex:heavy_strike}（已登记进 {@code #minecraft:bypasses_armor}），
 *       避免原版护甲步骤对已折算过的伤害二次减免。</li>
 * </ol>
 * <p>
 * <b>可格挡性（规格未写，按俯冲镰扫的既有口径处理）</b>：因为 {@code #minecraft:bypasses_shield}
 * 直接引用了 {@code #minecraft:bypasses_armor}，进了破甲标签的 {@code heavy_strike} 会传递性地
 * 被视为"无视盾牌"，拿它去调 {@code isDamageSourceBlocked} 恒为 false。
 * 故与俯冲镰扫完全一致地分流：<b>判定用等价探针源</b> {@code damageSources().mobAttack(boss)}
 * （同样携带攻击者位置、能走完原版朝向锥，但不在破甲标签里），<b>真实施加仍用 heavy_strike</b>。
 * 结论 = 踢击<b>挡得住</b>（盾牌朝向正确即可挡下）。
 * 与俯冲镰扫的唯一差别：被挡下<b>不</b>授予"禁飞 + 封印"——那是规格只写给俯冲镰扫的惩罚，
 * 踢击沿用它的口径只会凭空多出一条惩罚规则。
 */
public final class AgaitolosKickSkill {

    // ---------------------------------------------------------------- 手感常量（待调手感值 / P8 转配置项）

    /** 伤害 = <b>目标</b>最大生命 × 20%（规格明确"造成 20% 最大生命值伤害"） */
    public static final float DAMAGE_MAX_HEALTH_RATIO = 0.2F;

    /** 生效护甲比例：无视 40% ⇒ 只剩 60% 参与减免（韧性同理，口径同俯冲镰扫，仅比例不同） */
    public static final float ARMOR_KEPT_RATIO = 0.6F;

    /** 踢击冷却（tick）：80 = 4s（定案表"短冷却"；明显短于俯冲镰扫的 10s 与恶怨倒转的 15s）。待调手感值 / P8 转配置项 */
    public static final int KICK_COOLDOWN_TICKS = 80;

    /** 凋零持续（tick）：60 = 3s。规格定了等级（凋零 III）未定时长；比普攻的 1s 长，因为这是"重招"。待调手感值 / P8 转配置项 */
    public static final int WITHER_DURATION_TICKS = 60;

    /** 缓慢持续（tick）：60 = 3s。规格只写"缓慢效果"，未给时长与等级。待调手感值 / P8 转配置项 */
    public static final int SLOWNESS_DURATION_TICKS = 60;

    /**
     * 缓慢等级放大器：0 = 缓慢 I。
     * <p>规格只写"缓慢"，未给等级 ⇒ 按字面取 I（不动脑补成 II/III）。
     * 凋零等级则复用普攻的 {@link AgaitolosMeleeSkill#WITHER_AMPLIFIER}（= 2 ⇒ 凋零 III），
     * 让"上凋零"在全 BOSS 内只有一套等级口径。
     */
    public static final int SLOWNESS_AMPLIFIER = 0;

    private AgaitolosKickSkill() {
    }

    /**
     * 目标是否在踢击可达范围内。
     * <p>刻意复用实体自己那把尺子 {@link AgaitolosEntity#getMeleeAttackRangeSqr}：
     * 它已经把常态悬停高度折算进可达距离，于是"踢得着"与"普攻打得着"用的是同一把尺子
     * （与 {@code AgaitolosEntity#isTargetWithinGuardRange} 复用同一判据同理），不另写一套距离口径。
     */
    public static boolean isWithinKickRange(AgaitolosEntity boss, LivingEntity target) {
        double deltaX = target.getX() - boss.getX();
        double deltaY = target.getY() - boss.getY();
        double deltaZ = target.getZ() - boss.getZ();
        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ <= boss.getMeleeAttackRangeSqr(target);
    }

    /**
     * 执行一次高速踢击（仅服务端；由 {@code AgaitolosEntity#startKick} 在决策层选中且复校通过后调用）。
     *
     * @return 是否<b>已经出招</b>（含被盾牌挡下）—— 调用方据此进冷却。
     *         被挡下也算"这一脚踢出去了"，否则格挡成功会让 BOSS 每 tick 空踢（与俯冲镰扫同一取舍）。
     */
    public static boolean perform(AgaitolosEntity boss, LivingEntity target) {
        if (boss.level().isClientSide()) {
            return false;
        }
        // a. 基础伤害 = 目标最大生命 × 20%
        float damage = target.getMaxHealth() * DAMAGE_MAX_HEALTH_RATIO;
        // b. 无视 40% 护甲：与俯冲镰扫共用同一段折算代码（AgaitolosCombat#damageAfterPartialArmorBypass），
        //    只是把生效比例从 0.7 换成 0.6 —— 不复制第二份实现（设计文档 §3.1 的"两套口径"病）
        damage = AgaitolosCombat.damageAfterPartialArmorBypass(target, damage, ARMOR_KEPT_RATIO);
        // c. 真实施加的伤害源：带 bypasses_armor 的 forbidden_hex:heavy_strike（无独立弹体 ⇒ directEntity 传 null）
        DamageSource source = AgaitolosCombat.heavyStrike(boss.level(), null, boss);
        // d. 格挡：判定用等价探针源（理由见类 javadoc），判定器仍是攻守共用的 AgaitolosGuardSkill#isBlocking
        //    （对玩家而言即原版 LivingEntity#isDamageSourceBlocked）
        if (AgaitolosGuardSkill.isBlocking(target, boss.damageSources().mobAttack(boss))) {
            // 挡下 ⇒ 本次踢击整体无效：伤害与两个负面效果都不落地。
            // 不调用 onSweepBlocked（禁飞/封印是俯冲镰扫专属惩罚，规格没有把它挂到踢击上）。
            return true;
        }
        // 阶段三「天魔＊灾」之后：受击方已被改写时本脚整体换成精神伤害（口径唯一收在 AgaitolosPsychic）。
        // 判定用的探针源不换：格挡口径与伤害类型是两件事，换壳只影响落地时的伤害类型
        target.hurt(AgaitolosPsychic.forVictim(source, target, boss.level().getGameTime()), damage);
        // e. 凋零 III + 缓慢：等级复用普攻的 WITHER_AMPLIFIER（统一口径），施加者记为本 BOSS 保证击杀归属。
        //    阶段三由 AgaitolosDoom 分流成"凋亡 III"（等级/时长口径不变，只换效果定义）
        AgaitolosDoom.applyWitherOrDoom(boss, target, WITHER_DURATION_TICKS, AgaitolosMeleeSkill.WITHER_AMPLIFIER);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOWNESS_DURATION_TICKS,
                SLOWNESS_AMPLIFIER), boss);
        return true;
    }
}
