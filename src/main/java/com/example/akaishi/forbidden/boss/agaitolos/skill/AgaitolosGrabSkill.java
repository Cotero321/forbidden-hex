package com.example.akaishi.forbidden.boss.agaitolos.skill;

import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosCombat;
import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosEntity;
import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosPhase;
import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosPsychic;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * 阶段三「投技」两招的<b>达成条件、抓取态与伤害结算面</b>
 * （设计文档 §0 阶段三 / §1 定案表第 27、33 条）。
 * <p>
 * 规格原文：
 * <ul>
 *   <li><b>投技①</b>：「BOSS 位于地面将玩家踩在脚下并且大力挥出镰刀，造成最大生命值 25% 的伤害」；</li>
 *   <li><b>投技②</b>：「出现 BOSS 特写随后进行劈击，造成 20% 最大生命值的真实伤害」。</li>
 * </ul>
 * <p>
 * <b>本类的分工</b>：只提供"能不能抓 / 怎么钉住 / 伤害怎么算"三件事（纯函数 + 静态常量），
 * 抓取态的<b>计时与解除</b>在 {@code AgaitolosPhaseThreeState}（状态归实体侧，与既有"状态归实体、
 * 结算归 skill"分工一致）。
 * <p>
 * <b>与 clip 的节拍对齐</b>：{@code grab_sweep} 1.1s = 22 tick（{@link #HOLD_TICKS}），
 * 25% 那一刀落在 clip 的 0.5s = 10 tick（{@link #HOLD_DAMAGE_TICK}）；
 * {@code grab_smash} 1.4s = 28 tick（{@link #SMASH_DURATION_TICKS}），
 * 发力峰值 0.6s = 12 tick（{@link #SMASH_DAMAGE_TICK}）。
 * <p>
 * <b>伤害数值与既有承伤管线的关系（容易读错的一点）</b>：两招打的都是<b>玩家</b>，
 * 走原版 {@code LivingEntity#hurt}，<b>不经过</b> {@code AgaitolosDamageRules}
 * —— 那是 BOSS 挨打的管线（60% 减伤 + 锁伤上限 {@code max(24, 5%maxHealth)}）。
 * 故锁伤上限不参与本招；真正会削平"25% 最大生命"的是<b>玩家自己的护甲/抗性/附魔</b>
 * （见 {@link #sweepStrike} 的算式）。
 * <p>
 * <b>把数值算到底（20 点最大生命的玩家为例）</b>：
 * <ul>
 *   <li>投技① 25% = <b>5.0</b> 基础；经全额护甲折算后 —— 全套钻石（护甲 20 / 韧性 8）
 *       {@code 5 × (1 − 18.75/25) =} <b>1.25</b>；布甲（护甲 7 / 韧性 0）≈ <b>4.1</b>；裸装 <b>5.0</b>；</li>
 *   <li>投技② 20% = <b>4.0</b> 真实伤害，<b>任何防护都不减</b>（护甲/抗性/附魔/盾牌全不吃）。</li>
 * </ul>
 * 参考对照（说明"锁伤上限"为什么与本批招式无关）：那条上限本身 = {@code max(24, 5% × 1444)} = <b>72.2</b>，
 * 若把 25% 这种量级（1444 × 25% = 361）塞进 BOSS 那条承伤管线，361 × 0.4 = 144.4 会被夹到 <b>72.2</b>
 * —— 这正是"大额伤害会被锁伤削平"的形态；而本招的受击方是玩家，故该现象在本招上<b>不存在</b>。
 * 全部为<b>待调手感值 / P8 转配置项</b>。
 * <p>
 * <b>本类只负责服务端结算</b>，不含任何客户端表现。（2026-09-24：投技②原带的
 * 「BOSS 特写」相机表现已按需求彻底删除 —— 相关类、S2C 通道、配置项与 lang 键一并移除。）
 */
public final class AgaitolosGrabSkill {

    // ---------------------------------------------------------------- 投技①（踩住 + 镰扫）

    /**
     * 抓取距离（格，3D）：2.5。
     * <p>比近战可达尺子（含悬停折算约 3.18 格）更紧：抓取是"踩住"，要的是人正好在脚边，
     * 不是"勉强够得着"；留出的差值也保证"抓起手那一刻玩家已经贴住"，不至于抓了个空气。
     */
    public static final double GRAB_RANGE = 2.5D;

    /** 抓取锁定（tick）：22 = 1.1s，<b>与 {@code grab_sweep} clip 等长</b> */
    public static final int HOLD_TICKS = 22;

    /** 25% 那一刀的落点（tick，相对起手）：10 = clip 的 0.5s */
    public static final int HOLD_DAMAGE_TICK = 10;

    /** 伤害 = 目标最大生命 × 25%（规格明确）。待调手感值 / P8 转配置项 */
    public static final float SWEEP_DAMAGE_MAX_HEALTH_RATIO = 0.25F;

    /**
     * 生效护甲比例：<b>1.0 = 全额护甲照常生效</b>。
     * <p>规格只为投技①写了"造成最大生命值 25% 的伤害"，<b>没写</b>"无视护甲"
     * （俯冲镰扫 30% / 高速踢击 40% 都写了）⇒ 按字面不做任何破防。
     * 走 {@link AgaitolosCombat#damageAfterPartialArmorBypass} 传 1.0 而不是"直接 hurt 基础值"，
     * 是为了复用同一段折算代码并配 {@code forbidden_hex:heavy_strike}（该类型在 {@code bypasses_armor} 里）——
     * 传 1.0 时算出来的就是"完整护甲 + 完整韧性"那一步，与原版护甲步骤逐位等价。
     */
    public static final float SWEEP_ARMOR_KEPT_RATIO = 1.0F;

    /** 投技①冷却（tick）：200 = 10s（与俯冲镰扫同级：都是"抓一段、打一刀"的重招）。待调手感值 / P8 转配置项 */
    public static final int SWEEP_COOLDOWN_TICKS = 200;

    // ---------------------------------------------------------------- 投技②（特写 + 劈击）

    /** 劈击距离（格，3D）：3.5（比抓取宽一点：劈击是"挥下去"，不必贴死）。待调手感值 / P8 转配置项 */
    public static final double SMASH_RANGE = 3.5D;

    /** 整招时长（tick）：28 = 1.4s，<b>与 {@code grab_smash} clip 等长</b> */
    public static final int SMASH_DURATION_TICKS = 28;

    /** 发力峰值 / 结算落点（tick，相对起手）：12 = clip 的 0.6s。待调手感值 / P8 转配置项 */
    public static final int SMASH_DAMAGE_TICK = 12;

    /** 伤害 = 目标最大生命 × 20%（规格明确"20% 最大生命值的真实伤害"）。待调手感值 / P8 转配置项 */
    public static final float SMASH_DAMAGE_MAX_HEALTH_RATIO = 0.20F;

    /** 投技②冷却（tick）：300 = 15s（比投技①更稀有：真实伤害不吃任何防护）。待调手感值 / P8 转配置项 */
    public static final int SMASH_COOLDOWN_TICKS = 300;

    // ---------------------------------------------------------------- 抓取态的钉住参数

    /**
     * 锚点相对 BOSS 正面的前移量（格）：0.5 —— "被踩在脚下"的落点。
     * <p>取一个很小的前移而不是正中心：BOSS 碰撞箱宽约 1.6，正中心会把玩家塞进它身体正中，
     * 第一人称看出去全是模型内壁；前 0.5 格既读得出"踩在脚下"，又不会让镜头穿模。
     */
    private static final double PIN_FORWARD_OFFSET = 0.5D;

    private AgaitolosGrabSkill() {
    }

    // ---------------------------------------------------------------- 达成条件

    /**
     * 投技①是否可起手：阶段三 + <b>BOSS 在地面档</b> + 目标贴地且在 {@link #GRAB_RANGE} 内。
     * <p>「BOSS 位于地面」是规格原文 ⇒ 判 {@code isPerched()}（地面档，贴地稳态）；
     * 顺带排除禁飞期（{@code isGrounded()} 是"被格挡后的惩罚"，那段时间它自己站不稳，不该有投技）。
     */
    public static boolean canGrab(AgaitolosEntity boss, LivingEntity target) {
        return isPhaseThree(boss) && boss.isPerched() && !boss.isGrounded()
                && target != null && target.isAlive()
                && boss.distanceTo(target) <= GRAB_RANGE;
    }

    /** 投技②是否可起手：阶段门与身位要求与投技①完全同源，只有距离尺子不同（{@link #SMASH_RANGE}） */
    public static boolean canSmash(AgaitolosEntity boss, LivingEntity target) {
        return isPhaseThree(boss) && boss.isPerched() && !boss.isGrounded()
                && target != null && target.isAlive()
                && boss.distanceTo(target) <= SMASH_RANGE;
    }

    /** 阶段门（阶段三及以后）：两招共用，避免"改一招忘一招" */
    private static boolean isPhaseThree(AgaitolosEntity boss) {
        return boss.getPhase().combatOrdinal() >= AgaitolosPhase.PHASE_3.combatOrdinal();
    }

    // ---------------------------------------------------------------- 抓取态（锁位移 + 跟随）

    /**
     * 把目标钉在 BOSS 脚下（每 tick 调用一次，仅服务端）。
     * <p>
     * <b>为什么"锁位移 + 跟随"要同时做两件事</b>：只清速度挡不住玩家自己走（玩家移动是客户端预测、
     * 服务端只做校正），只传送不锁速度则玩家每 tick 会被自己那一步移开一小段、抖动读作"卡顿"。
     * 故两条一起上：
     * <ol>
     *   <li><b>位置跟随</b>：把目标传送到"BOSS 脚下、正面偏 {@link #PIN_FORWARD_OFFSET} 格"的锚点
     *       —— BOSS 走到哪，人就被拖到哪（规格的"踩住"）；</li>
     *   <li><b>位移封锁</b>：水平速度归零（竖直只保留向下的分量，避免把玩家抛起来），
     *       并置 {@code hurtMarked} 让原版把速度包下发（服务端改速度不会自动同步）。</li>
     * </ol>
     * 另外清掉落距：整段期间玩家是被"搬"着走的，不该在松手那一 tick 结算一段下落伤害。
     * <p>
     * ⚠ 这是<b>逐 tick 覆写玩家位置</b>的强控，所以解除出口必须齐全（见 {@code AgaitolosPhaseThreeState}
     * 的四条出口）。本方法本身不持有任何计时，也不往玩家侧写状态 ⇒ BOSS 一旦停 tick（死亡/卸载/移除），
     * 压制立刻消失，<b>不可能永久锁住玩家</b>。
     */
    public static void pin(AgaitolosEntity boss, LivingEntity target) {
        if (boss.level().isClientSide() || target == null || !target.isAlive()) {
            return;
        }
        // MC 的 yaw：0 = 朝 +Z；水平前向 = (-sin, cos)
        double yaw = Math.toRadians(boss.getYRot());
        double anchorX = boss.getX() - Math.sin(yaw) * PIN_FORWARD_OFFSET;
        double anchorZ = boss.getZ() + Math.cos(yaw) * PIN_FORWARD_OFFSET;
        target.teleportTo(anchorX, boss.getY(), anchorZ);
        target.setDeltaMovement(0.0D, Math.min(target.getDeltaMovement().y, 0.0D), 0.0D);
        target.hurtMarked = true;
        target.resetFallDistance();
    }

    // ---------------------------------------------------------------- 伤害结算

    /**
     * 投技①的一刀（25% 最大生命；由状态机在起手后 {@link #HOLD_DAMAGE_TICK} tick 调用一次）。
     * <p>
     * 结算三件（与高速踢击同款三件套，只有比例与"不破防"两处不同）：
     * <ol>
     *   <li>基础 = <b>目标</b>最大生命 × {@link #SWEEP_DAMAGE_MAX_HEALTH_RATIO}；</li>
     *   <li>护甲折算走 {@link AgaitolosCombat#damageAfterPartialArmorBypass}（比例 1.0 = 不破防，
     *       见 {@link #SWEEP_ARMOR_KEPT_RATIO} 的说明）；</li>
     *   <li>真实施加用 {@code forbidden_hex:heavy_strike}（已进 {@code bypasses_armor}，避免二次减免）。</li>
     * </ol>
     * <b>可格挡性</b>：沿用高速踢击的既有口径 —— 判定用等价探针源 {@code mobAttack}
     * （{@code heavy_strike} 因 {@code bypasses_shield} 引用了 {@code bypasses_armor} 而传递性地"无视盾牌"，
     * 拿真实源判恒为 false），判定器仍是唯一的 {@link AgaitolosGuardSkill#isBlocking}。
     * 挡下即这一刀不落地（被踩住的人仍被踩着，直到松手 —— 压制与减伤是两件事）。
     * <p>受击方若已被「天魔＊灾」改写，伤害类型整体换成精神伤害（口径唯一收在 {@link AgaitolosPsychic}）。
     *
     * @return 是否真的结算了伤害（被盾牌挡下 / 目标已死时为 false）
     */
    public static boolean sweepStrike(AgaitolosEntity boss, LivingEntity target) {
        if (boss.level().isClientSide() || target == null || !target.isAlive()) {
            return false;
        }
        float damage = target.getMaxHealth() * SWEEP_DAMAGE_MAX_HEALTH_RATIO;
        damage = AgaitolosCombat.damageAfterPartialArmorBypass(target, damage, SWEEP_ARMOR_KEPT_RATIO);
        DamageSource source = AgaitolosCombat.heavyStrike(boss.level(), null, boss);
        if (AgaitolosGuardSkill.isBlocking(target, boss.damageSources().mobAttack(boss))) {
            return false;
        }
        target.hurt(AgaitolosPsychic.forVictim(source, target, boss.level().getGameTime()), damage);
        return true;
    }

    /**
     * 投技②的劈击（20% 最大生命的<b>真实伤害</b>；由状态机在起手后 {@link #SMASH_DAMAGE_TICK} tick 调用一次）。
     * <p>
     * 真实伤害走 {@code forbidden_hex:true_damage}：无视护甲 / 抗性提升 / 保护类附魔
     * （标签口径见 {@code AgaitolosCombat#TRUE_DAMAGE}）。
     * <b>不做格挡判定</b>：真实伤害类型本身在 {@code bypasses_armor} 里，
     * 而原版 {@code bypasses_shield} 直接引用了该标签 ⇒ 它传递性地"无视盾牌"，判了也恒为"挡不住"，
     * 与其留一段永远走不到的分支，不如按规格"真实伤害"直接施加。
     *
     * @return 是否真的结算了伤害（目标已死 / 拉开到场外时为 false）
     */
    public static boolean smashStrike(AgaitolosEntity boss, LivingEntity target) {
        if (boss.level().isClientSide() || target == null || !target.isAlive()
                || boss.distanceTo(target) > SMASH_RANGE) {
            return false;
        }
        float damage = target.getMaxHealth() * SMASH_DAMAGE_MAX_HEALTH_RATIO;
        DamageSource source = AgaitolosCombat.trueDamage(boss.level(), null, boss);
        target.hurt(AgaitolosPsychic.forVictim(source, target, boss.level().getGameTime()), damage);
        return true;
    }
}
