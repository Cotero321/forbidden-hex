package com.example.akaishi.forbidden.boss.agaitolos.skill;

import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosEntity;
import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosPsychic;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 一阶段技能「恶怨倒转」的<b>结算面</b>（设计文档 §0 一阶段 / §1 第 22 条）。
 * <p>
 * 规格：蓄力期满后，<b>吸取半径内自己召唤的小怪剩余血量，并转化为对玩家的魔法伤害</b>
 * （1/5 量级）。召唤面见 {@link AgaitolosMinionSkill}，蓄力状态机与时长归实体自己持有
 * （见 {@code AgaitolosEntity#tickCharge}），本类只在"自然结束"这一个出口被调用一次。
 * <p>
 * <b>只在自然结束被调用，是刻意的设计约束</b>：蓄力被打断（死亡/复活、累计承伤达标、召唤物全灭）
 * 走的是 {@code endChargeInterrupted()}，那条路径<b>不调本类</b>。于是"打断这一招能免掉那次吸血"
 * 不需要任何额外判断 —— 两条路径在代码里物理隔离，被打断时本方法根本不在调用栈上。
 * <p>
 * <b>为什么必须靠归属标签只吸自己召唤的</b>：若按"半径内所有凋零骷髅"结算，
 * ① 会把自然生成的凋零骷髅一并抽干秒杀（与本次召唤毫无因果的外部生物）；
 * ② 玩家能靠"引一堆凋零骷髅过来"把这一招变成自杀式 AOE。
 * 归属标记由 {@link AgaitolosMinionSkill} 打在召唤物自己的原版实体标签上。
 * <p>
 * <b>★已知取舍（本轮未擅自加闸，待设计确认）</b>：{@link #DRAIN_RADIUS} 同时充当
 * ① 小怪筛选半径与 ② 玩家受伤半径，于是存在两种边界情形：
 * <ul>
 *   <li>小怪被抽干，但 20 格内恰好没有玩家（分队追着远处的玩家跑出去了）⇒ 小怪仍死、伤害无处落地。
 *       对玩家而言这是"拉远距离即可规避"的正向反馈，暂认为可接受。</li>
 *   <li>10 只全在 20 格外 ⇒ 一个都吸不到，本招期满但整招空放（小怪不死、无人受伤）。
 *       蓄力是否提前打断用的是 {@code ALIVE_CHECK_RADIUS=64}，故不会被误判成"全灭"。</li>
 * </ul>
 * 是否改成"没吸到就干脆不结算"或"抽干但没玩家就不杀"，需设计拍板。
 * <p>
 * <b>为什么这段伤害不可格挡</b>：走原版 {@code minecraft:magic}，而 vanilla 的
 * {@code #minecraft:bypasses_shield} 标签<b>直接引用</b> {@code #minecraft:bypasses_armor}，
 * 而 {@code minecraft:magic} 又在 {@code bypasses_armor} 里 ⇒ 传递性地既不吃护甲点数、
 * 也不可被盾牌格挡。这正是"魔法伤害"的原版语义，不需要另造数据。
 */
public final class AgaitolosReversalSkill {

    /** 吸取半径（格）：20（规格「范围 20 格半径」）。待调手感值 / P8 转配置项 */
    public static final double DRAIN_RADIUS = 20.0D;

    /** 转化除数：吸取总量 / 5（规格「1/5 魔法伤害」）。待调手感值 / P8 转配置项 */
    public static final float DRAIN_DAMAGE_DIVISOR = 5.0F;

    private AgaitolosReversalSkill() {
    }

    /**
     * 结算一次「恶怨倒转」：抽干自己召唤的分队，把总量 ÷ {@link #DRAIN_DAMAGE_DIVISOR}
     * 作为魔法伤害打给半径内的玩家（仅服务端；由 {@code AgaitolosEntity#endChargeCompleted} 调用）。
     * <p>
     * 关键口径：
     * <ol>
     *   <li>取的量是召唤物的<b>当前剩余血量</b>（{@code getHealth()}）而不是最大值 ——
     *       玩家先清掉几只、或把某只打残，这一招的收益就随之缩水，构成"清小怪 = 削弱大招"的因果。</li>
     *   <li>伤害是<b>总吸取量除以 5</b>，不是"每个玩家各吸一份全额"：10 只满血召唤物共 400 血 ⇒
     *       每个玩家各吃 80。若按人各吸一遍，5 人队会挨 400 必团灭，与规格量级不符。</li>
     *   <li>目标的"死亡"用 {@link LivingEntity#kill()} —— 它内部就是
     *       {@code hurt(damageSources().genericKill(), Float.MAX_VALUE)}，会走完整死亡流程，
     *       掉落表 / 死亡音效 / 粒子等原版表现全保留，不需要我们手动清理实体。</li>
     * </ol>
     */
    public static void drainMinions(AgaitolosEntity boss) {
        if (boss.level().isClientSide()) {
            // 仅服务端结算：客户端既没有权威血量，也不该由它去改世界
            return;
        }
        List<LivingEntity> minions = AgaitolosMinionSkill.findLivingMinions(boss, DRAIN_RADIUS);
        float drained = 0.0F;
        for (LivingEntity minion : minions) {
            drained += minion.getHealth();
            // "吸取"的实现语义 = 抽干剩余血量并使其死亡
            minion.kill();
        }
        if (drained <= 0.0F) {
            // 一个可吸目标都没有：既不产生伤害，也不做无意义的玩家遍历
            return;
        }
        float damage = drained / DRAIN_DAMAGE_DIVISOR;
        DamageSource source = magicDrain(boss);
        double radiusSqr = DRAIN_RADIUS * DRAIN_RADIUS;
        // 包围盒粗筛 + 球半径精筛（与 AgaitolosEntity#knockbackNearbyPlayers / DiveSweepSkill 同手法）
        for (Player player : boss.level().getEntitiesOfClass(Player.class, boss.getBoundingBox().inflate(DRAIN_RADIUS))) {
            if (!player.isAlive() || player.distanceToSqr(boss) > radiusSqr) {
                continue;
            }
            // 精神污染（阶段三「天魔＊灾」）换壳：被污染的玩家受到的这一发同样按精神伤害结算。
            // 只换伤害源、不动数值与节拍；未污染时 forVictim 原样返回（逐位不变）。
            player.hurt(AgaitolosPsychic.forVictim(source, player, boss.level().getGameTime()), damage);
        }
    }

    /**
     * 构造魔法伤害源：{@code minecraft:magic} + 归属实体 BOSS。
     * <p>
     * 位置补偿理由同 {@link AgaitolosDiveSweepSkill#perform} 用到的写法：三参构造器在
     * {@code directEntity == null} 时 {@code getSourcePosition()} 为 null；故用两参重载
     * {@code DamageSource(Holder, Entity)}（内部 {@code this(type, entity, entity)}）补齐来源位置与击杀归属。
     */
    private static DamageSource magicDrain(AgaitolosEntity boss) {
        if (boss.level() instanceof ServerLevel serverLevel) {
            Registry<DamageType> registry = serverLevel.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
            return new DamageSource(registry.getHolderOrThrow(DamageTypes.MAGIC), boss);
        }
        return boss.level().damageSources().magic();
    }
}
