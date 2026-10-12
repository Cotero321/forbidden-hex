package com.example.akaishi.forbidden.boss.agaitolos;

import com.example.akaishi.forbidden.boss.agaitolos.skill.AgaitolosBombardSkill;
import com.example.akaishi.forbidden.boss.agaitolos.skill.AgaitolosCalamitySkill;
import com.example.akaishi.forbidden.boss.agaitolos.skill.AgaitolosGrabSkill;
import com.example.akaishi.forbidden.boss.agaitolos.skill.AgaitolosTripleThrowSkill;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

/**
 * 阶段三五招的<b>状态机与节拍调度器</b>（三重投掷 / 饱和轰炸 / 投技①② / 天魔＊灾）。
 * <p>
 * <b>为什么单独一个类，而不是把这十几个字段塞进 {@link AgaitolosEntity}</b>：
 * 这五招共享同一套结构 ——「一段有节拍的持续状态 + 一个冷却 + 若干提前退出条件」，
 * 与既有招式（俯冲有 {@code tickDiveState}、蓄力有 {@code tickChargeState}…）逐一对应。
 * 若逐招写进实体，实体要继续长 300 行，且五段"每 tick 递减 + 到点出手 + 中途退出"的骨架会被抄五遍
 * （本项目反复出现的"两套口径"病）。收进本类后：
 * <ul>
 *   <li><b>实体</b>只保留同步位（{@code DATA_BOMBARDING}）与转发入口（{@code startTripleThrow} 等）；</li>
 *   <li><b>本类</b>持有全部计时/冷却/目标 UUID，并每 tick 被实体调用一次（{@link #tick(AgaitolosEntity)}）；</li>
 *   <li><b>伤害与达成条件</b>仍在 {@code skill/} 各技能类（纯函数），本类只负责"什么时候叫它"。</li>
 * </ul>
 * <p>
 * <b>节拍全部与 clip 对齐</b>：
 * <table border="1">
 *   <caption>五招的节拍与 clip 对应</caption>
 *   <tr><th>招</th><th>clip</th><th>起手后出手点（tick）</th><th>状态时长（tick）</th></tr>
 *   <tr><td>三重投掷</td><td>triple_throw（40t）</td><td>6 / 12 / 18</td><td>40</td></tr>
 *   <tr><td>饱和轰炸</td><td>bombard（40t loop）</td><td>10，之后每 20</td><td>120</td></tr>
 *   <tr><td>投技①</td><td>grab_sweep（22t）</td><td>10（那一刀）</td><td>22</td></tr>
 *   <tr><td>投技②</td><td>grab_smash（28t）</td><td>12（发力峰值）</td><td>28</td></tr>
 *   <tr><td>天魔＊灾</td><td>calamity_cast（44t）</td><td>10（改写落点）</td><td>44</td></tr>
 * </table>
 * 表里的时刻常量<b>全部来自各技能类</b>（本类不另写一份数字）：改 clip 只需改技能类那一处。
 * <p>
 * <b>全局动作锁</b>：{@link #isBusy()} 为真期间，决策层不再派任何新招（含保命招硬闸的节拍判定），
 * 与"同一时刻只允许一招在演"的既有契约一致。
 * <p>
 * <b>落盘策略</b>：<b>冷却落盘</b>（口径与既有六招一致：不落盘则读档会白送一次大招），
 * <b>进行中的状态不落盘</b>（与出场演出同一取舍）—— 读档后从"没在演"开始，
 * 理由：这五段状态都挂着外部实体（抓取态钉住的是某个玩家、轰炸挂着弹体节拍），
 * 续播要凭一个可能已失效的 UUID 继续控制别人，风险远大于收益；<b>尤其抓取态</b>，
 * 不落盘就等于"读档/卸载必然松手"，这是它最硬的一条安全出口。
 */
public final class AgaitolosPhaseThreeState {

    // ---------------------------------------------------------------- NBT 键（冷却落盘）

    private static final String NBT_TRIPLE_THROW_COOLDOWN = "AgaitolosTripleThrowCooldown";
    private static final String NBT_BOMBARD_COOLDOWN = "AgaitolosBombardCooldown";
    private static final String NBT_GRAB_SWEEP_COOLDOWN = "AgaitolosGrabSweepCooldown";
    private static final String NBT_GRAB_SMASH_COOLDOWN = "AgaitolosGrabSmashCooldown";
    private static final String NBT_CALAMITY_COOLDOWN = "AgaitolosCalamityCooldown";

    /**
     * 抓取态的"距离异常"解除阈值（格）：16。
     * <p>正常钉住时目标恒在 1 格内；一旦超过这个距离，只可能是"目标被传送/被别的机制搬走了"，
     * 此时继续强行把人拉回来属于跨机制的抢夺，故直接松手。
     */
    private static final double HOLD_BREAK_DISTANCE = 16.0D;

    // ---------------------------------------------------------------- 冷却（落盘）

    private int tripleThrowCooldownTicks;
    private int bombardCooldownTicks;
    private int grabSweepCooldownTicks;
    private int grabSmashCooldownTicks;
    private int calamityCooldownTicks;

    // ---------------------------------------------------------------- 进行中状态（不落盘）

    /** 三重投掷：剩余 tick / 已出手拍数 */
    private int tripleThrowTicks;
    private int tripleThrowFiredBeats;

    /** 饱和轰炸：剩余 tick / 距下一发 */
    private int bombardTicks;
    private int bombardNextShotTicks;

    /** 投技①：剩余锁定 tick / 这一刀是否已落地 / 被钉住的目标 */
    private int grabHoldTicks;
    private boolean grabStruck;
    private UUID grabTargetId;

    /** 投技②：剩余 tick / 劈击是否已落地 / 目标 */
    private int smashTicks;
    private boolean smashStruck;
    private UUID smashTargetId;

    /** 天魔＊灾：剩余 tick / 改写是否已落地 / 目标 */
    private int calamityTicks;
    private boolean calamityApplied;
    private UUID calamityTargetId;

    // ---------------------------------------------------------------- 每 tick 入口

    /**
     * 冷却递减（由 {@code AgaitolosEntity#tickActionCooldowns} 在决策层之前调用一次）。
     * <p>刻意与实体其它招式冷却放在同一个调用点：口径"所有冷却都在决策层读之前统一递减"只有这一处，
     * 不会出现"决策层先读、本类后减"的顺序依赖。
     */
    public void tickCooldowns() {
        if (this.tripleThrowCooldownTicks > 0) {
            --this.tripleThrowCooldownTicks;
        }
        if (this.bombardCooldownTicks > 0) {
            --this.bombardCooldownTicks;
        }
        if (this.grabSweepCooldownTicks > 0) {
            --this.grabSweepCooldownTicks;
        }
        if (this.grabSmashCooldownTicks > 0) {
            --this.grabSmashCooldownTicks;
        }
        if (this.calamityCooldownTicks > 0) {
            --this.calamityCooldownTicks;
        }
    }

    /**
     * 进行中状态推进（由 {@code AgaitolosEntity#aiStep} 在所有招式状态推进之后、决策层之前调用一次）。
     * <p>只在服务端跑（调用点在 {@code aiStep} 的客户端提前 return 之后）。
     * <p><b>死亡 / 复活 / 出场演出一律整段中止</b>：五种状态都是"正在出手"，
     * 演出期间继续出手既与姿势打架，也会让"复活无敌期不出手"出现漏洞；
     * 尤其抓取态必须<b>立刻松手</b>，否则会留下"BOSS 在复活演出里还踩着玩家"的错位。
     */
    public void tick(AgaitolosEntity boss) {
        if (boss.isDeadOrDying() || boss.isRespawning() || boss.isIntroPlaying()) {
            this.abortAll(boss);
            return;
        }
        this.tickTripleThrow(boss);
        this.tickBombard(boss);
        this.tickGrab(boss);
        this.tickSmash(boss);
        this.tickCalamity(boss);
    }

    // ---------------------------------------------------------------- 三重投掷

    /** 起手：三连扇面出手（节拍见技能类）。起手失败返回 false（决策层据此只吃一个短重试节拍） */
    public boolean startTripleThrow(AgaitolosEntity boss, LivingEntity target) {
        if (!this.canStart(boss, AgaitolosSkillDirector.Move.TRIPLE_THROW, this.tripleThrowCooldownTicks)
                || !AgaitolosTripleThrowSkill.canThrow(boss, target)) {
            return false;
        }
        this.tripleThrowTicks = AgaitolosTripleThrowSkill.DURATION_TICKS;
        this.tripleThrowFiredBeats = 0;
        // 冷却在起手时即起算（与"挥出去了就算"的既有口径一致：三发即便全被躲开，这一招也已经放过了）
        this.tripleThrowCooldownTicks = AgaitolosPace.scaledCooldown(boss, AgaitolosTripleThrowSkill.COOLDOWN_TICKS);
        AgaitolosAnimations.playTripleThrow(boss);
        return true;
    }

    /** 推进：到点打出一发（6/12/18 tick），期满收势 */
    private void tickTripleThrow(AgaitolosEntity boss) {
        if (this.tripleThrowTicks <= 0) {
            return;
        }
        // 起手那一 tick 记 elapsed = 0（动画从 0 起算），故先算 elapsed 再递减剩余
        int elapsed = AgaitolosTripleThrowSkill.DURATION_TICKS - this.tripleThrowTicks;
        if (this.tripleThrowFiredBeats < AgaitolosTripleThrowSkill.BEAT_TICKS.length
                && elapsed >= AgaitolosTripleThrowSkill.BEAT_TICKS[this.tripleThrowFiredBeats]) {
            AgaitolosTripleThrowSkill.fireBeat(boss, boss.getTarget(), this.tripleThrowFiredBeats);
            // 纯表现：与弹体同刻（扇面拖尾按这一拍的偏角铺，三拍音高递增）；放在 fireBeat 之后 = 只当注脚，
            // 且两个入口内部各自复校 target（与 fireBeat 同款守卫）⇒ 不会出现"有特效没弹体"
            AgaitolosPhaseThreeFx.tripleThrowBeat(boss, boss.getTarget(), this.tripleThrowFiredBeats);
            AgaitolosPhaseThreeSounds.tripleThrowBeat(boss, this.tripleThrowFiredBeats);
            ++this.tripleThrowFiredBeats;
        }
        if (--this.tripleThrowTicks <= 0) {
            this.tripleThrowFiredBeats = 0;
        }
    }

    // ---------------------------------------------------------------- 饱和轰炸

    /** 起手：进入高空轰炸状态（`DATA_BOMBARDING` 立起来，动画控制器靠它轮询那条 loop clip） */
    public boolean startBombard(AgaitolosEntity boss, LivingEntity target) {
        if (!this.canStart(boss, AgaitolosSkillDirector.Move.BOMBARD, this.bombardCooldownTicks)
                || !AgaitolosBombardSkill.canBombard(boss, target)) {
            return false;
        }
        this.bombardTicks = AgaitolosBombardSkill.DURATION_TICKS;
        this.bombardNextShotTicks = AgaitolosBombardSkill.FIRST_SHOT_TICKS;
        this.bombardCooldownTicks = AgaitolosPace.scaledCooldown(boss, AgaitolosBombardSkill.COOLDOWN_TICKS);
        boss.setBombarding(true);
        return true;
    }

    /** 推进：每 {@code BEAT_TICKS} 投一发，期满撤状态（身高自动由 MoveControl 收回常态档） */
    private void tickBombard(AgaitolosEntity boss) {
        if (this.bombardTicks <= 0) {
            return;
        }
        if (--this.bombardTicks <= 0) {
            // 期满：撤销同步位 ⇒ 客户端 bombard 控制器自行 STOP、MAIN 控制器恢复；高度由 ③ 的悬停修正收回
            boss.setBombarding(false);
            this.bombardNextShotTicks = 0;
            return;
        }
        if (--this.bombardNextShotTicks <= 0) {
            AgaitolosBombardSkill.fire(boss, boss.getTarget());
            // 纯表现：离手线（一条即显的近似拖尾，理由见 AgaitolosPhaseThreeFx#bombardRelease）+ 大半径投弹音；
            // 真正的"落地冲击"在弹体命中那一刻由 AgaitolosWitherSkull 侧补（状态机不知道弹体何时落地）
            AgaitolosPhaseThreeFx.bombardRelease(boss, boss.getTarget());
            AgaitolosPhaseThreeSounds.bombardShot(boss);
            this.bombardNextShotTicks = AgaitolosBombardSkill.BEAT_TICKS;
        }
    }

    // ---------------------------------------------------------------- 投技①（踩住 + 镰扫）

    /** 起手：抓取态（目标被钉在脚下），结束时（或被提前打断时）松手 */
    public boolean startGrabSweep(AgaitolosEntity boss, LivingEntity target) {
        if (!this.canStart(boss, AgaitolosSkillDirector.Move.GRAB_SWEEP, this.grabSweepCooldownTicks)
                || !AgaitolosGrabSkill.canGrab(boss, target)) {
            return false;
        }
        this.grabHoldTicks = AgaitolosGrabSkill.HOLD_TICKS;
        this.grabStruck = false;
        this.grabTargetId = target.getUUID();
        this.grabSweepCooldownTicks = AgaitolosPace.scaledCooldown(boss, AgaitolosGrabSkill.SWEEP_COOLDOWN_TICKS);
        AgaitolosAnimations.playGrabSweep(boss);
        return true;
    }

    /**
     * 抓取态推进：钉住目标 + 到点出刀 + <b>四条解除出口</b>。
     * <p><b>四条出口（缺一条就存在"永久锁住玩家"的风险）</b>：
     * <ol>
     *   <li><b>超时</b>：{@link AgaitolosGrabSkill#HOLD_TICKS} 期满（= clip 长度）；</li>
     *   <li><b>BOSS 受击</b>：{@code hurtTime > 0}。分两段语义 —— 那一刀<b>落地前</b>挨打 = 整招作废（松手、不结算）；
     *       <b>落地后</b>挨打 = 只是提前松手（伤害已经造成）；</li>
     *   <li><b>目标死亡 / 丢失 / 被拉开</b>：超过 {@link #HOLD_BREAK_DISTANCE} 视为被外力搬走，直接松手；</li>
     *   <li><b>阶段变化</b>：离开阶段三（含读档后阶段字段变化）立刻松手。</li>
     * </ol>
     * 另有两条天然出口：<b>死亡/复活/出场演出</b>由 {@link #tick} 的整段中止负责；
     * <b>实体停 tick</b>（区块卸载 / 被移除 / 服务器关停）时本类不再被调用，
     * 钉住动作随之消失 —— 压制<b>不落任何玩家侧状态</b>，故不可能残留。
     */
    private void tickGrab(AgaitolosEntity boss) {
        if (this.grabHoldTicks <= 0) {
            return;
        }
        LivingEntity target = resolveTarget(boss, this.grabTargetId);
        // 出口 ③ 目标没了 / 跑远；出口 ④ 阶段变化
        if (target == null || boss.distanceTo(target) > HOLD_BREAK_DISTANCE || !isPhaseThree(boss)) {
            this.endGrab();
            return;
        }
        int elapsed = AgaitolosGrabSkill.HOLD_TICKS - this.grabHoldTicks + 1;
        // a. 到点出刀（先结算再判受击：同一 tick 既该出刀又挨了打时，不该白丢这一刀）
        if (!this.grabStruck && elapsed >= AgaitolosGrabSkill.HOLD_DAMAGE_TICK) {
            this.grabStruck = true;
            // 返回值只喂给表现层：空挥/被盾挡下时出刀光但不出冲击环（与 diveSweepImpact 同一取舍），
            // 结算与否仍唯一由技能类决定 —— 这里不改它的任何判据
            boolean struck = AgaitolosGrabSkill.sweepStrike(boss, target);
            AgaitolosPhaseThreeFx.grabSweepStrike(boss, target, struck);
            // 音效与命中无关：刀已经挥出去了（与横扫"被格挡也算挥出去"同一取舍）
            AgaitolosPhaseThreeSounds.grabSweepStrike(boss);
        }
        // b. 出口 ② BOSS 受击：出刀前 = 断招（上面没结算）；出刀后 = 提前松手
        if (boss.hurtTime > 0) {
            this.endGrab();
            return;
        }
        // c. 钉住（锁位移 + 跟随），再推计时
        AgaitolosGrabSkill.pin(boss, target);
        // 纯表现：钉住期的压迫感（内部按 tickCount % 4 节流 —— 22t 只出约 5 帧，绝不逐 tick 刷）
        AgaitolosPhaseThreeFx.grabHoldPressure(boss, target);
        if (--this.grabHoldTicks <= 0) {
            this.endGrab();
        }
    }

    /** 松手：只清本类的锁定状态（不往玩家侧写任何东西，故不存在"松手失败"这条分支） */
    private void endGrab() {
        this.grabHoldTicks = 0;
        this.grabStruck = false;
        this.grabTargetId = null;
    }

    // ---------------------------------------------------------------- 投技②（特写 + 劈击）

    /**
     * 起手：劈击（clip 播到 0.6s 时结算真实伤害）。
     * <p>2026-09-24 变更：本招原带的「BOSS 特写」相机表现已按需求<b>彻底删除</b>，
     * 起手只剩「播 clip + 锁定目标」两件事；抓取/摔砸的伤害、冷却、节拍一字未动。
     */
    public boolean startGrabSmash(AgaitolosEntity boss, LivingEntity target) {
        if (!this.canStart(boss, AgaitolosSkillDirector.Move.GRAB_SMASH, this.grabSmashCooldownTicks)
                || !AgaitolosGrabSkill.canSmash(boss, target)) {
            return false;
        }
        this.smashTicks = AgaitolosGrabSkill.SMASH_DURATION_TICKS;
        this.smashStruck = false;
        this.smashTargetId = target.getUUID();
        this.grabSmashCooldownTicks = AgaitolosPace.scaledCooldown(boss, AgaitolosGrabSkill.SMASH_COOLDOWN_TICKS);
        AgaitolosAnimations.playGrabSmash(boss);
        return true;
    }

    /** 推进：0.6s 处结算一次劈击（目标中途死亡/跑掉则不结算），期满收势 */
    private void tickSmash(AgaitolosEntity boss) {
        if (this.smashTicks <= 0) {
            return;
        }
        int elapsed = AgaitolosGrabSkill.SMASH_DURATION_TICKS - this.smashTicks + 1;
        if (!this.smashStruck && elapsed >= AgaitolosGrabSkill.SMASH_DAMAGE_TICK) {
            this.smashStruck = true;
            // 目标中途换人/死亡时不改打别人：这一记劈击是对着"特写里的那个人"落下去的
            LivingEntity victim = resolveTarget(boss, this.smashTargetId);
            // 返回值只用于表现层：没打中就别放重击特效/砸地音（结算口径与判据一字未动）
            if (AgaitolosGrabSkill.smashStrike(boss, victim)) {
                AgaitolosPhaseThreeFx.grabSmashStrike(boss, victim);
                AgaitolosPhaseThreeSounds.grabSmashStrike(boss, victim);
            }
        }
        if (--this.smashTicks <= 0) {
            this.smashStruck = false;
            this.smashTargetId = null;
        }
    }

    // ---------------------------------------------------------------- 天魔＊灾

    /** 起手：施法（0.5s 处落地"不可名状 + 精神伤害改写"） */
    public boolean startCalamity(AgaitolosEntity boss, LivingEntity target) {
        if (!this.canStart(boss, AgaitolosSkillDirector.Move.CALAMITY, this.calamityCooldownTicks)
                || !AgaitolosCalamitySkill.canCast(boss, target)) {
            return false;
        }
        this.calamityTicks = AgaitolosCalamitySkill.CAST_TICKS;
        this.calamityApplied = false;
        this.calamityTargetId = target.getUUID();
        this.calamityCooldownTicks = AgaitolosPace.scaledCooldown(boss, AgaitolosCalamitySkill.COOLDOWN_TICKS);
        AgaitolosAnimations.playCalamityCast(boss);
        return true;
    }

    /** 推进：0.5s 处落地改写（**失败不重试**：目标跑了就算了，冷却照常走 —— 见技能类注释） */
    private void tickCalamity(AgaitolosEntity boss) {
        if (this.calamityTicks <= 0) {
            return;
        }
        int elapsed = AgaitolosCalamitySkill.CAST_TICKS - this.calamityTicks + 1;
        // 纯表现：整段施法的"不可名状"氛围（内部按 tickCount % 4 节流，44t 约 11 帧）
        AgaitolosPhaseThreeFx.calamityAura(boss, elapsed);
        if (!this.calamityApplied && elapsed >= AgaitolosCalamitySkill.APPLY_TICK) {
            this.calamityApplied = true;
            LivingEntity victim = resolveTarget(boss, this.calamityTargetId);
            // 失败不重试的口径不变（calamityApplied 已置位、冷却照常走）；返回值只用于表现层
            // —— 没落地就不放改写特效/呓语，避免"没中也有表现"
            if (AgaitolosCalamitySkill.apply(boss, victim)) {
                AgaitolosPhaseThreeFx.calamityApplied(boss, victim);
                AgaitolosPhaseThreeSounds.calamityApplied(boss, victim);
            }
        }
        if (--this.calamityTicks <= 0) {
            this.calamityApplied = false;
            this.calamityTargetId = null;
        }
    }

    // ---------------------------------------------------------------- 探针（供实体与决策层只读）

    /** 是否有阶段三的招正在演（全局动作锁的一段）：决策层与 {@code doHurtTarget} 都读它 */
    public boolean isBusy() {
        return this.tripleThrowTicks > 0 || this.bombardTicks > 0 || this.grabHoldTicks > 0
                || this.smashTicks > 0 || this.calamityTicks > 0;
    }

    /** 是否正踩着某个目标（消费点：{@code AgaitolosEntity#tickPerchState} —— 抓取期间不许升空） */
    public boolean isHoldingTarget() {
        return this.grabHoldTicks > 0;
    }

    /** 只读探针：某一招的剩余冷却（未接线的招返回 {@link Integer#MAX_VALUE}） */
    public int remainingCooldown(AgaitolosSkillDirector.Move move) {
        switch (move) {
            case TRIPLE_THROW:
                return this.tripleThrowCooldownTicks;
            case BOMBARD:
                return this.bombardCooldownTicks;
            case GRAB_SWEEP:
                return this.grabSweepCooldownTicks;
            case GRAB_SMASH:
                return this.grabSmashCooldownTicks;
            case CALAMITY:
                return this.calamityCooldownTicks;
            default:
                return Integer.MAX_VALUE;
        }
    }

    // ---------------------------------------------------------------- 存档（只落冷却）

    /** 冷却落盘（口径与实体其它六招一致：不落盘则读档会白送一次大招） */
    public void save(CompoundTag tag) {
        tag.putInt(NBT_TRIPLE_THROW_COOLDOWN, this.tripleThrowCooldownTicks);
        tag.putInt(NBT_BOMBARD_COOLDOWN, this.bombardCooldownTicks);
        tag.putInt(NBT_GRAB_SWEEP_COOLDOWN, this.grabSweepCooldownTicks);
        tag.putInt(NBT_GRAB_SMASH_COOLDOWN, this.grabSmashCooldownTicks);
        tag.putInt(NBT_CALAMITY_COOLDOWN, this.calamityCooldownTicks);
    }

    /**
     * 读盘：只恢复冷却，<b>进行中的状态一律留在"没在演"</b>（理由见类注释）。
     * <p>下界保护与实体其它计时同款：缺键 ⇒ 0（安全默认，等同可立刻起手），异常负值也只当 0。
     */
    public void load(CompoundTag tag) {
        this.tripleThrowCooldownTicks = Math.max(0, tag.getInt(NBT_TRIPLE_THROW_COOLDOWN));
        this.bombardCooldownTicks = Math.max(0, tag.getInt(NBT_BOMBARD_COOLDOWN));
        this.grabSweepCooldownTicks = Math.max(0, tag.getInt(NBT_GRAB_SWEEP_COOLDOWN));
        this.grabSmashCooldownTicks = Math.max(0, tag.getInt(NBT_GRAB_SMASH_COOLDOWN));
        this.calamityCooldownTicks = Math.max(0, tag.getInt(NBT_CALAMITY_COOLDOWN));
    }

    // ---------------------------------------------------------------- 内部工具

    /**
     * 五招共用的起手复校：<b>冷却 + 封印 + 状态互斥</b>。
     * <p>空间/阶段条件由各招自己的 {@code canXxx} 判（那些条件各招不同，且与 decision 层权重表同源）；
     * 本方法只管"现在允不允许起手"这一层，避免和 {@code AgaitolosSkillDirector} 的全局动作锁写重。
     * <p>与既有 {@code startGuard}/{@code startKick} 的复校同款（防御性重复，判据只有一处实现）。
     */
    private boolean canStart(AgaitolosEntity boss, AgaitolosSkillDirector.Move move, int cooldownTicks) {
        return cooldownTicks <= 0 && !boss.isScytheSealed() && !boss.isDeadOrDying() && !boss.isRespawning()
                && !boss.isIntroPlaying() && !boss.isGuarding() && !boss.isCharging() && !boss.isDiving()
                && !this.isBusy();
    }

    /** 整段中止：清掉全部进行中状态（并按需撤掉同步位），冷却原样保留 */
    private void abortAll(AgaitolosEntity boss) {
        this.tripleThrowTicks = 0;
        this.tripleThrowFiredBeats = 0;
        this.bombardTicks = 0;
        this.bombardNextShotTicks = 0;
        this.endGrab();
        this.smashTicks = 0;
        this.smashStruck = false;
        this.smashTargetId = null;
        this.calamityTicks = 0;
        this.calamityApplied = false;
        this.calamityTargetId = null;
        // 同步位必须跟着撤：否则客户端会永远停在 bombard 那条循环 clip 上（MAIN 控制器也不再接管）
        if (boss.isBombarding()) {
            boss.setBombarding(false);
        }
    }

    /** 目标 UUID 反查（跨维度/已卸载实体由 {@link ServerLevel#getEntity(UUID)} 返回 null，调用方按"目标丢失"处理） */
    private static LivingEntity resolveTarget(AgaitolosEntity boss, UUID targetId) {
        if (targetId == null || !(boss.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        Entity entity = serverLevel.getEntity(targetId);
        return entity instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    /** 是否仍在阶段三（含更后阶段）：抓取态的解除出口之一 */
    private static boolean isPhaseThree(AgaitolosEntity boss) {
        return boss.getPhase().combatOrdinal() >= AgaitolosPhase.PHASE_3.combatOrdinal();
    }
}
