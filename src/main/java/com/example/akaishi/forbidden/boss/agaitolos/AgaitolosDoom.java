package com.example.akaishi.forbidden.boss.agaitolos;

import com.example.akaishi.forbidden.effect.AkaishiForbiddenEffects;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * 阿盖托洛丝的「凋零 → 凋亡」阶段分流（设计文档 §0 阶段三：
 * 「BOSS 施加的凋零 buff 更改为凋亡」）。
 * <p>
 * <b>为什么要单独一个类</b>：这句规格要落在<b>三处既有的施加点</b>上
 * （{@code AgaitolosMeleeSkill} 普攻、{@code AgaitolosKickSkill} 高速踢击、
 * {@code AgaitolosWitherSkull} 凋零头命中），并且还有两处"读凋零"的结算
 * （普攻的"带凋零则真伤双倍"、俯冲镰扫的"带凋零则转魔法伤害"）。若三处各写一遍
 * {@code if (phase == 3)}，阶段口径就会散成五份、改一处必然漏四处 —— 这正是本项目反复出现的"两套口径"病。
 * 故收成两类入口：
 * <ul>
 *   <li><b>施加</b>：{@link #applyWitherOrDoom} —— 阶段一/二施加原版凋零（时长与等级由调用方传入，
 *       与改动前<b>逐字相同</b>），阶段三改施加 {@link ModEffects#DOOM 凋亡}；</li>
 *   <li><b>读取</b>：{@link #isWitheredOrDoomed} —— "带凋零/带凋亡"的唯一判据，
 *       让"凋亡"在既有结算里与原凋零<b>同权</b>（不然阶段三会凭空掉掉"真伤双倍"与"镰扫转魔法"两条规则）。</li>
 * </ul>
 * <p>
 * <b>阶段一/二的行为不变性</b>：{@link #applyWitherOrDoom} 在非阶段三时调用的就是原来的
 * {@code addEffect(new MobEffectInstance(MobEffects.WITHER, 时长, 等级), 施加者)}，
 * 参数与施加者与改动前完全一致 ⇒ 一、二阶段的手感与结算逐位不变。
 * <p>
 * <b>当前生效状态</b>：已接线（三处施加点全部走本类）⇒ 阶段三<b>真的会</b>给玩家挂上凋亡；
 * 凋亡本身的效果定义见 {@code DoomEffect}（掉血 + 降低治疗）。
 */
public final class AgaitolosDoom {

    private AgaitolosDoom() {
    }

    /**
     * 是否已到"施加凋亡"的阶段（阶段三及以后）。
     * <p>用 {@code combatOrdinal() >= PHASE_3} 而不是 {@code == PHASE_3}：与既有
     * {@code AgaitolosEntity#isPhaseTwoOrLater} 同一写法；复活阶段（序数 0）因此天然不会命中。
     */
    public static boolean isDoomPhase(AgaitolosEntity boss) {
        return boss.getPhase().combatOrdinal() >= AgaitolosPhase.PHASE_3.combatOrdinal();
    }

    /**
     * 目标身上是否带「凋零<b>或</b>凋亡」——所有"读凋零"结算的唯一判据。
     * <p>凋亡是凋零在阶段三的替代品，凡规格里写"如玩家拥有凋零效果便 ……"的规则都应把凋亡一并认下，
     * 否则阶段三会因为"手上这个减益换了名字"而静默失去那些加成/转换。
     */
    public static boolean isWitheredOrDoomed(LivingEntity entity) {
        if (entity.hasEffect(MobEffects.WITHER)) {
            return true;
        }
        MobEffect doom = AkaishiForbiddenEffects.DOOM == null ? null : AkaishiForbiddenEffects.DOOM.get();
        return doom != null && entity.hasEffect(doom);
    }

    /**
     * 施加「凋零或凋亡」（技能侧唯一入口）。
     * <p>
     * 时长与等级一律由调用方传入各自的既有常量：本类<b>不</b>持有任何时长/等级默认值，
     * 避免出现"两处各有一个「上凋零的时长」"这种迟早漂移的定义。
     *
     * @param boss         施法者（阶段判定的唯一来源，也作为效果施加者保证击杀归属）
     * @param target       被施加者
     * @param durationTicks 时长（tick）：阶段一/二给凋零、阶段三给凋亡，两者同值
     * @param amplifier    等级放大器：同上，两者同值（BOSS 的三处施加点都是 2 = III 级）
     */
    public static void applyWitherOrDoom(AgaitolosEntity boss, LivingEntity target, int durationTicks, int amplifier) {
        if (boss.level().isClientSide()) {
            return;
        }
        if (isDoomPhase(boss)) {
            MobEffect doom = AkaishiForbiddenEffects.DOOM == null ? null : AkaishiForbiddenEffects.DOOM.get();
            // 注册表尚未就绪（理论上不可达：本方法只在游戏内被调用）时退化为凋零，不静默什么也不做
            if (doom != null) {
                target.addEffect(new MobEffectInstance(doom, durationTicks, amplifier), boss);
                return;
            }
        }
        target.addEffect(new MobEffectInstance(MobEffects.WITHER, durationTicks, amplifier), boss);
    }
}
