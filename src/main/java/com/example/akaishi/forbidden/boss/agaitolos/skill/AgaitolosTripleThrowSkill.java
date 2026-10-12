package com.example.akaishi.forbidden.boss.agaitolos.skill;

import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosEntity;
import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosPhase;
import net.minecraft.world.entity.LivingEntity;

/**
 * 阶段三技能「三重投掷」的<b>伤害与发射面</b>（设计文档 §0 阶段三 / §1 定案表第 26 条）。
 * <p>
 * 规格原文：「三重投掷：BOSS 处于天空中可以释放，投掷三个凋零头进行大范围轰击，
 * 此攻击<b>无视 30% 的防御</b>」。
 * <p>
 * <b>与 clip 的节拍对齐（硬约束）</b>：{@code animation.agaitolos.triple_throw} 是 2.0s = 40 tick 的
 * PLAY_ONCE clip，三次出手落在它的 0.3 / 0.6 / 0.9s ⇒ <b>6 / 12 / 18 tick</b>（{@link #BEAT_TICKS}）。
 * 实体侧的发弹调度就按这三个 tick 打，改 clip 长度/节拍必须同时改这里（否则会出现"手挥完了弹体才出去"）。
 * <p>
 * <b>「无视 30% 的防御」的实现口径</b>（复用既有口径，不另立一套）：
 * 三发凋零头都带 0.7 的破防比例（{@link #ARMOR_KEPT_RATIO}），落点在
 * {@code AgaitolosWitherSkull#onHitEntity} —— 它用 {@code AgaitolosCombat#damageAfterPartialArmorBypass}
 * 把「护甲 × 0.7、韧性 × 0.7」<b>预折算</b>一次，再用已进 {@code bypasses_armor} 的
 * {@code forbidden_hex:triple_throw} 施加（与俯冲镰扫的 0.7、高速踢击的 0.6 共用同一段算法）。
 * <p>
 * <b>伤害数值与既有承伤管线的关系（容易读错的一点）</b>：本招打的是<b>玩家</b>，
 * 走的是原版 {@code LivingEntity#hurt}，<b>不经过</b> {@code AgaitolosDamageRules}
 * —— 那一条是 BOSS<b>挨打</b>的管线（60% 减伤 + 锁伤上限 {@code max(24, 5%maxHealth)} = 72.2）。
 * 所以锁伤上限不会削平本招；<b>单发落地量</b>＝弹体 5 点爆炸段经「护甲×0.7、韧性×0.7」折算后的值
 * ＋ 2 点真实伤害（不吃任何防护）。算到底（20 点最大生命的玩家）：
 * <ul>
 *   <li>全套钻石（护甲 20 / 韧性 8 ⇒ 生效 14 / 5.6）：爆炸段 {@code 5 × (1 − 12.53/25) ≈} <b>2.49</b>，
 *       加真伤 2 ⇒ <b>单发约 4.49</b>（三发全命中约 13.5）；</li>
 *   <li>裸装：爆炸段满额 5，加真伤 2 ⇒ <b>单发 7.0</b>（"无视 30% 防御"在无护甲目标上没有差别）。</li>
 * </ul>
 * 全部为<b>待调手感值 / P8 转配置项</b>。
 */
public final class AgaitolosTripleThrowSkill {

    // ---------------------------------------------------------------- 手感常量（待调手感值 / P8 转配置项）

    /** 三次出手的节拍（tick，相对起手那一 tick）：6 / 12 / 18 —— <b>与 clip 逐字对齐</b> */
    public static final int[] BEAT_TICKS = {6, 12, 18};

    /**
     * 三发的扇面偏角（度），与 {@link #BEAT_TICKS} 下标一一对应：-14 / 0 / +14。
     * <p>偏角绕 Y 轴旋转瞄准向量（见 {@code AgaitolosSkullSkill#rotateY}），不改 BOSS 朝向 ——
     * "扇面"要的是落点散开、形成"大范围轰击"，不是把头转来转去（那会连带影响格挡锥与镰扫扇面）。
     * <p>14° 的依据：目标在 12 格处时相邻两发横移约 3 格，正好形成"能覆盖走位、又不至于全空"的散布。
     */
    public static final float[] FAN_YAW_DEGREES = {-14.0F, 0.0F, 14.0F};

    /** 整招时长（tick）：40 = 2.0s，<b>与 clip 等长</b>（末拍出手后留 22t 收势） */
    public static final int DURATION_TICKS = 40;

    /** 冷却（tick）：120 = 6s。规格未给；按"比远程攻击（60t）更重"取两倍。待调手感值 / P8 转配置项 */
    public static final int COOLDOWN_TICKS = 120;

    /**
     * 射程（格）：直接引用远程攻击那把尺子（{@link AgaitolosSkullSkill#SKULL_ATTACK_RADIUS} = 24）。
     * <p>刻意引用而不是再写一个 24：本招就是"同一发凋零头、三连发"，能开火的距离不该比单发更远或更近。
     */
    public static final double RANGE = AgaitolosSkullSkill.SKULL_ATTACK_RADIUS;

    /** 生效护甲比例：无视 30% 防御 ⇒ 只剩 70% 参与减免（韧性同理）。待调手感值 / P8 转配置项 */
    public static final float ARMOR_KEPT_RATIO = 0.7F;

    private AgaitolosTripleThrowSkill() {
    }

    /**
     * 本招此刻是否可起手（阶段三 + 空中 + 射程内）。
     * <p>规格限定「BOSS 处于天空中可以释放」⇒ <b>地面档与禁飞期都放不出</b>
     * （那两种状态下它根本不在天上，没有"空中三连"的姿态可言）。
     * <p>决策层的权重表与实体侧执行入口复校<b>共用本方法</b>（同一口径，不复制第二份）。
     */
    public static boolean canThrow(AgaitolosEntity boss, LivingEntity target) {
        if (target == null || !target.isAlive()) {
            return false;
        }
        // 阶段门：规格把它列在阶段二条目里，但本轮按"仅阶段三可用"接线（用户本轮明确口径）
        if (boss.getPhase().combatOrdinal() < AgaitolosPhase.PHASE_3.combatOrdinal()) {
            return false;
        }
        if (boss.isPerched() || boss.isGrounded()) {
            return false;
        }
        return boss.distanceTo(target) <= RANGE;
    }

    /**
     * 打出第 {@code beatIndex} 发（0/1/2，对应 clip 的 6/12/18 tick）。
     * <p>发射本体交给 {@link AgaitolosSkullSkill#fireSpread}（唯一的"怎么发射"实现），
     * 本方法只提供"这一发偏多少度、破防多少"。
     */
    public static void fireBeat(AgaitolosEntity boss, LivingEntity target, int beatIndex) {
        if (beatIndex < 0 || beatIndex >= BEAT_TICKS.length
                || target == null || !target.isAlive() || boss.level().isClientSide()) {
            return;
        }
        AgaitolosSkullSkill.fireSpread(boss, target, FAN_YAW_DEGREES[beatIndex], ARMOR_KEPT_RATIO);
    }
}
