package com.example.akaishi.forbidden.boss.agaitolos;

import com.example.akaishi.forbidden.effect.AkaishiForbiddenEffects;
import com.example.akaishi.forbidden.state.ForbiddenPlayerState;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 阿盖托洛丝【下界本源】阶段三「天魔＊灾」的<b>精神污染</b>唯一口径
 * （设计文档 §0 阶段三 / §1 定案表第 31 条）。
 * <p>
 * 规格原文：「BOSS 对玩家施加不可名状效果，期间 BOSS 的伤害类型替换为精神伤害，持续 30s；
 * <b>此技能结束后伤害类型不变</b>」。
 * <p>
 * <b>三件事收在一个类里</b>（与 {@link AgaitolosDoom} 同款思路：口径只此一处，技能类只负责调用）：
 * <ol>
 *   <li><b>施放</b>：{@link #applyCalamity} —— 复用现成的 {@code AkaishiForbiddenEffects.UNNAMEABLE}（30s），
 *       并把"改写窗口"写进玩家侧持久状态；</li>
 *   <li><b>判定</b>：{@link #isConverted} —— "这个玩家现在算不算精神伤害受体"的唯一读法
 *       （含"窗口走完即转永久"的一次性提升）；</li>
 *   <li><b>换壳</b>：{@link #forVictim} —— 把 BOSS 已经造好的伤害源换成精神伤害源（口径见下）。</li>
 * </ol>
 * <p>
 * <b>"此后永久"是怎么落地的（不是内存态）</b>：窗口截止刻写在玩家侧 capability
 * （{@link IPlayerBodyState#getPsychicUntil()}，NBT 键 {@code psychic_until}）。该 capability 是本项目
 * <b>唯一</b>一条已验证过"落盘 + 死亡即时快照 + 重生/换维度克隆"的玩家持久化链路
 * （forge 侧 {@code PlayerBodyCapability} 的三条事件），故：
 * <ul>
 *   <li>退出游戏 / 重启服务器 / 区块卸载后仍在（随玩家存档落盘）；</li>
 *   <li><b>死亡重生后仍在</b>（死亡那一 tick 写快照，重生时优先从快照恢复）；</li>
 *   <li>换维度 / 跨越传送门后仍在（克隆时整体复制）。</li>
 * </ul>
 * 状态机只有三个值：<b>0（从未污染）/ 正数（窗口截止刻）/ {@link Long#MAX_VALUE}（永久）</b>；
 * "窗口走完 ⇒ 转永久"的翻转在 {@link #isConverted} 里做一次懒提升（读到过期即写永久），
 * 因此不需要任何额外的每 tick 轮询，也不存在"玩家在窗口期间下线、之后再也转不了永久"的漏网。
 * <p>
 * <b>换壳的覆盖范围（本轮如实口径）</b>：{@link #forVictim} 被接在 BOSS 侧<b>自造伤害源</b>的每一个施加点上
 * ——普攻的真实伤害段、高速踢击（{@code heavy_strike}）、俯冲镰扫（{@code scythe_sweep} / 魔法）、
 * 凋零头的两段（爆炸 + 真实），以及本轮新增的四招。
 * <p>
 * ⚠ <b>未覆盖的一项</b>：普攻的<b>物理段</b>走原版 {@code Mob#doHurtTarget} → {@code damageSources().mobAttack()}，
 * 伤害类型由原版内部硬写，无法在不重写整段原版结算（攻击力/附魔/击退/暴击）的前提下换壳。
 * 故该段<b>仍是原版近战伤害类型</b>（不吃"精神伤害"的三条无视标签）。这是已知取舍，
 * 不是静默失效 —— 若实机要求"连平A都是精神伤害"，可行的补法是在 {@code AgaitolosMeleeSkill}
 * 里放弃原版物理段、改用 {@code AgaitolosCombat.psychic(...)} 自算（代价是丢掉原版附魔/暴击结算）。
 */
public final class AgaitolosPsychic {

    /**
     * 精神改写窗口（tick）：600 = <b>30s</b>（规格明确"持续 30s"）。
     * <p>窗口走完<b>不是失效而是转永久</b>（规格"此技能结束后伤害类型不变"），故本常量只表达
     * "不可名状效果挂多久"，不表达"精神伤害挂多久"。
     */
    public static final int WINDOW_TICKS = 600;

    /** 不可名状效果等级放大器：0 = I 级（规格只写"施加不可名状效果"，未给等级，按字面取 I） */
    private static final int UNNAMEABLE_AMPLIFIER = 0;

    private AgaitolosPsychic() {
    }

    /**
     * 施放「天魔＊灾」对一名玩家的精神污染（仅服务端；由 {@link AgaitolosEntity#startCalamity} 调用）。
     * <p>
     * 幂等：同一玩家被重复施放（多只 BOSS / 技能连放）时，效果按原版规则刷新时长，
     * 窗口取 {@code max(旧, 新)} —— <b>只延不缩</b>，已经转过永久的不会被降回窗口态。
     * <p>非玩家目标直接忽略：规格写的是"BOSS 对玩家施加不可名状效果"，
     * 而"永久改写"这条需要一个能落盘的玩家侧载体，召唤物/其它生物没有（也不该有）。
     *
     * @return 是否真的施放成功（非玩家 / 无 capability 载体时为 false，调用方据此决定要不要进冷却）
     */
    public static boolean applyCalamity(AgaitolosEntity boss, LivingEntity target) {
        if (boss.level().isClientSide() || !(target instanceof Player player) || !player.isAlive()) {
            return false;
        }
        MobEffect unnameable = AkaishiForbiddenEffects.UNNAMEABLE == null ? null : AkaishiForbiddenEffects.UNNAMEABLE.get();
        // 注册表尚未就绪（理论上不可达：本方法只在游戏内被调用）时不静默：仍写窗口，只少一层视觉表现
        if (unnameable != null) {
            player.addEffect(new MobEffectInstance(unnameable, WINDOW_TICKS, UNNAMEABLE_AMPLIFIER), boss);
        }
        ForbiddenPlayerState state = ForbiddenPlayerState.of(player);
        if (state == null) {
            // 没有玩家侧持久载体 ⇒ 如实返回 false（不做"只存内存、退出即失"的假实现）
            return false;
        }
        long current = state.getPsychicUntil();
        if (current == Long.MAX_VALUE) {
            return true; // 已经是永久，无需再动窗口
        }
        long until = boss.level().getGameTime() + WINDOW_TICKS;
        state.setPsychicUntil(Math.max(current, until));
        return true;
    }

    /**
     * 该目标此刻是否算「精神伤害受体」（该口径的唯一读法）。
     * <p>
     * 三分支：0 ⇒ 否；{@link Long#MAX_VALUE} ⇒ 是（永久）；正数 ⇒ 比较当前游戏刻 ——
     * <b>已过期即就地转永久</b>（写一次 {@code MAX_VALUE}），此后恒为是。
     * <p>非玩家 / 无 capability 载体一律返回 false：精神改写只针对玩家（同 {@link #applyCalamity}）。
     */
    public static boolean isConverted(LivingEntity target, long gameTime) {
        if (!(target instanceof Player player)) {
            return false;
        }
        ForbiddenPlayerState state = ForbiddenPlayerState.of(player);
        if (state == null) {
            return false;
        }
        long until = state.getPsychicUntil();
        if (until == 0L) {
            return false;
        }
        if (until == Long.MAX_VALUE) {
            return true;
        }
        if (gameTime >= until) {
            // 30s 窗口走完 ⇒ 规格的"此后永久"：懒提升一次，之后无需再判时间
            state.setPsychicUntil(Long.MAX_VALUE);
        }
        return true;
    }

    /**
     * 把 BOSS 造好的伤害源换成精神伤害源（不需要改写时原样返回）。
     * <p>
     * <b>为什么是"换壳"而不是"在施加点分支"</b>：BOSS 侧有六处自造伤害源（见类注释），
     * 每处都写一遍 {@code if (isConverted) …} 会立刻长出第二套口径；集中成一次调用后，
     * 各技能只需要在 {@code hurt} 之前套一层，且"哪些伤害吃改写"一眼可见（grep 本方法即可）。
     * <p>
     * 保留原源的 {@code directEntity / causingEntity}（理由见 {@code AgaitolosCombat#psychic}）：
     * 击杀归属与"被玩家打回的凋零头"这条自伤判据都不会因为换壳而丢。
     * <p>只在受击方是玩家且已被污染时动作；其余（含 BOSS 自己挨的反弹伤害）原样返回，
     * 故反弹自伤通道 {@code AgaitolosDamageRules} ⑤ 不受影响。
     */
    public static DamageSource forVictim(DamageSource original, LivingEntity victim, long gameTime) {
        if (original == null || !isConverted(victim, gameTime)) {
            return original;
        }
        return AgaitolosCombat.psychic(victim.level(), original.getDirectEntity(), original.getEntity());
    }
}
