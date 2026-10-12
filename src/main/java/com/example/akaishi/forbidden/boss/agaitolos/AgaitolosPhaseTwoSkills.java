package com.example.akaishi.forbidden.boss.agaitolos;

import com.example.akaishi.forbidden.boss.agaitolos.skill.AgaitolosBlinkSkill;
import com.example.akaishi.forbidden.boss.agaitolos.skill.AgaitolosKickSkill;
import net.minecraft.world.entity.LivingEntity;

/**
 * 阿盖托洛丝的<b>二阶段两招执行入口</b>（瞬击 / 高速踢击）与共用起手闸。
 * <p>
 * 从 {@link AgaitolosEntity} 机械搬出（2026-09-24 拆分轮）：两招的互斥条件完全相同
 * （共用 {@link #canStartPhaseTwoSkill} 一条判断，分开写就会出现"改一招忘一招"），
 * 与架势/蓄力（{@link AgaitolosGuardCharge}）、俯冲（{@link AgaitolosDiveMachine}）同属
 * "决策层起手 → 实体持计时 → 技能类结算"的骨架；单独成类让二阶段的阶段门只有一处可看。
 * 冷却字段仍归实体持有（包私有，由本类写入、{@link AgaitolosTimers} 递减）。
 */
public final class AgaitolosPhaseTwoSkills {

    private AgaitolosPhaseTwoSkills() {
    }

    /**
     * 二阶段两招共用的起手闸（服务端权威）。
     * <p>
     * <b>为什么两招共用一条判断</b>：它们的互斥条件完全相同，分开写就会出现"改一招忘一招"；
     * 与 {@link AgaitolosDamageRules} / {@link AgaitolosGuardSkill} 的收口思路一致。
     * <p>
     * 三段判据：
     * <ul>
     *   <li><b>阶段门</b>：两招都是规格里的二阶段招式（设计文档 §0 阶段二 / §5 的 P5），
     *       阶段一起手会破坏"二阶段才解锁"的契约；</li>
     *   <li><b>状态互斥</b>：死亡 / 复活演出 / 出场演出 / 架势 / 蓄力 / 冲锋任一成立都不起手 ——
     *       与 {@link AgaitolosSkillDirector} 的全局动作锁同款判据（该锁已把这几项统一收口，
     *       不再需要各招各写一份互斥列表，历史上"改一招忘一招"正是这么来的）；</li>
     *   <li><b>技能封印</b>（{@link AgaitolosEntity#isScytheSealed()}）：封印<b>只封"大招"</b>（俯冲镰扫，
     *       以及后续接入的三重投掷 / 天魔灾 / 投技），<b>不封瞬击与高速踢击</b>，
     *       故本方法<b>不含</b>封印判定；作用范围与理由见 {@link AgaitolosEntity#isScytheSealed()} 的 javadoc。</li>
     * </ul>
     * <p>
     * <b>与决策层的关系（本轮新增）</b>：起手的主闸已上移到 {@link AgaitolosSkillDirector}
     * （它用全局动作锁保证"同一时刻只允许一招在演"，并把阶段门与封印口径一并纳入打分）。
     * 本方法保留为<b>执行入口自己的复校</b>：{@code startBlink}/{@code startKick} 在被决策层调用时仍会走一遍，
     * 这样"将来有人在别处直接调用这两个入口"也不会绕过互斥条件（防御性重复，不是两份口径——
     * 判据本身只有这一处实现）。
     */
    private static boolean canStartPhaseTwoSkill(AgaitolosEntity boss) {
        return isPhaseTwoOrLater(boss) && !boss.isDeadOrDying() && !boss.isRespawning()
                && !boss.isIntroPlaying() && !boss.isGuarding() && !boss.isCharging() && !boss.isDiving();
    }

    /** 是否已进入二阶段（含三阶段）：二阶段招式的阶段门 */
    private static boolean isPhaseTwoOrLater(AgaitolosEntity boss) {
        return boss.getPhase().combatOrdinal() >= AgaitolosPhase.PHASE_2.combatOrdinal();
    }

    /**
     * 执行入口：起手「瞬击」（由 {@link AgaitolosSkillDirector} 在"这一拍选中瞬击"时调用）；
     * 两段式里的"冷却"由 {@link AgaitolosTimers#tickActionCooldowns} 统一递减，本方法只剩落点判定与瞬移。
     * <p>
     * <b>"不飞行"为什么只能是禁飞窗口</b>：本 BOSS 常态低空悬停（{@code AgaitolosMoveControl} 恒定维持高度），
     * 唯一的落地状态就是被玩家格挡俯冲镰扫后授予的 30s 禁飞（{@link AgaitolosTimers#onSweepBlocked}）。
     * 于是瞬击天然是一招"<b>惩罚期的补偿手段</b>"：禁飞期间够不到目标，靠绕后瞬移把距离拉回近战范围
     * （详见 {@link AgaitolosBlinkSkill} 的类注释）。
     * <p>
     * <b>与技能封印的关系（2026-09-20 用户拍板：封印不覆盖瞬击）</b>：禁飞与封印由
     * {@code onSweepBlocked} <b>同刻授予、且同为 600 tick</b>，若瞬击也受封印约束，它的唯一起手窗口
     * 就会被完全覆盖、一次也放不出来。故瞬击走 {@link #canStartPhaseTwoSkill}（<b>不含</b>封印），
     * 与踢击同属"不吃封印"的基础手段；作用范围见 {@link AgaitolosEntity#isScytheSealed()} 的 javadoc。
     * <p>
     * 冷却落点两分支：<b>成功进完整冷却</b>（{@link AgaitolosBlinkSkill#BLINK_COOLDOWN_TICKS}，
     * 按阶段折算）；<b>落点校验失败只给短重试窗口</b>（{@link AgaitolosBlinkSkill#BLINK_FAILED_RETRY_TICKS}）
     * —— 失败不传送、不改朝向、不扣完整冷却，只是别每 tick 重扫方块。
     *
     * @return 是否真的起手（阶段未开放/状态冲突/不在地面/落点校验失败时为 false）
     */
    static boolean startBlink(AgaitolosEntity boss, LivingEntity target) {
        if (!canStartPhaseTwoSkill(boss) || !boss.isGrounded()
                || target == null || !target.isAlive() || !AgaitolosBlinkSkill.canBlink(boss, target)) {
            return false;
        }
        if (AgaitolosBlinkSkill.perform(boss, target)) {
            boss.blinkCooldownTicks = AgaitolosPace.scaledCooldown(boss, AgaitolosBlinkSkill.BLINK_COOLDOWN_TICKS);
            return true;
        }
        // 落点校验没过：只吃短重试窗口，且<b>不算起手</b>（决策层据此只给一个短重试节拍，不空等整拍）
        boss.blinkCooldownTicks = AgaitolosBlinkSkill.BLINK_FAILED_RETRY_TICKS;
        return false;
    }

    /**
     * 执行入口：起手「高速踢击」（由 {@link AgaitolosSkillDirector} 在"这一拍选中踢击"时调用）。
     * <p>
     * <b>与瞬击恰相反，本招不限定飞行/地面</b>（规格："任何状态下可用"）：悬停、禁飞、任何高度都能起手；
     * 但仍受 {@link #canStartPhaseTwoSkill} 的六项互斥约束（死亡/复活/架势/蓄力/冲锋/封印）
     * —— "任何状态"指的是空间状态，不是"可以一边蓄力一边踢"。
     * <p>
     * 目标与距离一律复用既有口径：距离取 {@link AgaitolosEntity#getMeleeAttackRangeSqr}（含悬停高度折算，
     * 与普攻/格挡同一把尺子）。出手即进冷却（被盾牌挡下也算"这一脚踢出去了"，与俯冲镰扫同一取舍），
     * 避免格挡成功时每 tick 空踢。
     *
     * @return 是否真的起手（阶段未开放/状态冲突/距离不够时为 false）
     */
    static boolean startKick(AgaitolosEntity boss, LivingEntity target) {
        if (!canStartPhaseTwoSkill(boss) || target == null || !target.isAlive()
                || !AgaitolosKickSkill.isWithinKickRange(boss, target)) {
            return false;
        }
        if (AgaitolosKickSkill.perform(boss, target)) {
            boss.kickCooldownTicks = AgaitolosPace.scaledCooldown(boss, AgaitolosKickSkill.KICK_COOLDOWN_TICKS);
            return true;
        }
        return false;
    }
}
