package com.example.akaishi.forbidden.boss.agaitolos.skill;

import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosEntity;
import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosPhase;
import net.minecraft.world.entity.LivingEntity;

/**
 * 阶段三技能「饱和轰炸」的<b>发射面</b>（设计文档 §0 阶段三 / §1 定案表第 32 条）。
 * <p>
 * 规格原文：「饱和轰炸：BOSS 飞于高空源源不断投掷凋零头颅进行轰击，<b>如反弹至 BOSS 则 BOSS 受到伤害</b>」。
 * <p>
 * <b>与 clip 的节拍对齐（硬约束）</b>：{@code animation.agaitolos.bombard} 是 <b>loop</b> 的 2.0s = 40 tick
 * clip，两拍投弹落在 0.5 / 1.5s ⇒ 首发在起手后 <b>10 tick</b>{@link #FIRST_SHOT_TICKS}、
 * 之后循环起来<b>每 20 tick 一发</b>{@link #BEAT_TICKS}。实体侧的调度器按这两个常量投弹，
 * 改 clip 长度/节拍必须同时改这里。
 * <p>
 * <b>「被反弹则自伤」是怎么落地的（本轮<b>不新增</b>任何代码）</b>：
 * 本招投出的仍是 {@code AgaitolosWitherSkull}，玩家近战打回后该弹体的 {@code owner} 被换成玩家
 * （见 {@code AgaitolosWitherSkull#hurt}），于是命中 BOSS 时：
 * <ol>
 *   <li>{@code AgaitolosEntity#isReflectedSkull} 成立（直接伤害实体是本弹体 + 归属实体是玩家）；</li>
 *   <li>该布尔喂给 {@code AgaitolosDamageRules#resolve} 的 <b>⑤ 反弹自伤通道</b>：<b>跳过 ⑦减伤与 ⑧锁伤</b>，
 *       直接返回原始伤害（规格"BOSS 承受此伤害并且无视自身减伤和锁伤"）；</li>
 *   <li>⑤ 排在 <b>⑥ 阶段三免疫远程</b>之前 —— 否则"反弹就能伤到它"这条唯一手段会被它自己的免疫吃掉。</li>
 * </ol>
 * 即：本招只要"投的是自家的头"，反弹自伤就是既有链路的自然结果；本轮要做的只是保证
 * 投出去的弹体 owner 是 BOSS（{@code AgaitolosSkullSkill#fireSpread} 已保证）。
 * <p>
 * <b>伤害数值</b>：每发与远程攻击同款（5 点爆炸段 + 凋零/凋亡 III 2s + 2 点真实段）。
 * 打的是玩家 ⇒ <b>不经过</b> {@code AgaitolosDamageRules}（那是 BOSS 挨打的管线），
 * 故"60% 减伤 + 锁伤上限"不会削平它；而反弹回 BOSS 的那一发恰好<b>走的是自伤通道、明文豁免减伤与锁伤</b>。
 * 全部为<b>待调手感值 / P8 转配置项</b>。
 */
public final class AgaitolosBombardSkill {

    // ---------------------------------------------------------------- 手感常量（待调手感值 / P8 转配置项）

    /** 首拍延迟（tick）：10 = clip 的 0.5s（与 clip 两拍中的第一拍逐字对齐） */
    public static final int FIRST_SHOT_TICKS = 10;

    /** 投弹间隔（tick）：20 —— clip 两拍 0.5 / 1.5s 的间距（循环周期 40t ÷ 2 发） */
    public static final int BEAT_TICKS = 20;

    /**
     * 轰炸持续（tick）：120 = 6s ⇒ 起手后 10 / 30 / 50 / 70 / 90 / 110 共 <b>6 发</b>。
     * <p>取 6s 而不是规格别的"30 发"：阶段三 BOSS 免疫远程（{@code isProjectileImmune}），
     * 这段窗口里玩家<b>打不到远在天上的它</b>，只能走位躲避 —— 30 发（约 30s）会把"压制"做成"罚站"。
     * 6s 既读得出"饱和轰炸"的密度，又不至于让玩家无事可做。待调手感值 / P8 转配置项
     */
    public static final int DURATION_TICKS = 120;

    /** 冷却（tick）：400 = 20s（阶段三经 {@code AgaitolosPace} 折算后 ≈ 12s）。待调手感值 / P8 转配置项 */
    public static final int COOLDOWN_TICKS = 400;

    /**
     * 轰炸期的悬停高度（格）：6.0 —— 规格「飞于高空」的落点
     * （消费点见 {@code AgaitolosMoveControl#airborneHeight()}）。
     * <p>与阶段三常态高度（{@link AgaitolosMoveControl#PHASE_3_HOVER_HEIGHT} = 1.0 的超低空）<b>并存不冲突</b>：
     * 常态仍是超低空，只有本招持续的这 6s 抬到高空，结束后由 ③ 的悬停修正自动收回 1.0。
     * 取 6.0 的依据：玩家跳起（约 1.25 格）+ 站在两格塔上也够不着它，读作"上天了"。待调手感值 / P8 转配置项
     */
    public static final double HOVER_HEIGHT = 6.0D;

    /** 射程（格）：引用远程攻击那把尺子（超出就只剩追击，不该再投弹） */
    public static final double RANGE = AgaitolosSkullSkill.SKULL_ATTACK_RADIUS;

    private AgaitolosBombardSkill() {
    }

    /**
     * 本招此刻是否可起手（阶段三 + 空中 + 射程内）。
     * <p>规格「飞于高空」⇒ 地面档与禁飞期都放不出（那两种状态它在地面，谈不上高空轰炸）。
     * <p>决策层权重表与实体侧执行入口复校<b>共用本方法</b>。
     */
    public static boolean canBombard(AgaitolosEntity boss, LivingEntity target) {
        if (target == null || !target.isAlive()) {
            return false;
        }
        if (boss.getPhase().combatOrdinal() < AgaitolosPhase.PHASE_3.combatOrdinal()) {
            return false;
        }
        if (boss.isPerched() || boss.isGrounded()) {
            return false;
        }
        return boss.distanceTo(target) <= RANGE;
    }

    /** 投出一发（每 {@link #BEAT_TICKS} tick 由状态机调用一次）：与远程攻击同一发弹体，只是散布更大 */
    public static void fire(AgaitolosEntity boss, LivingEntity target) {
        if (boss.level().isClientSide() || target == null || !target.isAlive()) {
            return;
        }
        // 标记为「高空投弹」：落地那一刻由弹体补一圈冲击环（见 AgaitolosWitherSkull#onHitEntity）。
        // 纯表现标记，不改任何结算口径；上面的守卫已保证这里是服务端 ⇒ fireSpread 不会返回 null，
        // 发射本体与远程攻击逐位相同（区别只在弹体身上多一个布尔）
        AgaitolosSkullSkill.fireSpread(boss, target, 0.0F, 1.0F).markBombard();
    }
}
