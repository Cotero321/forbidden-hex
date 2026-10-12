package com.example.akaishi.forbidden.boss.agaitolos;

import com.example.akaishi.forbidden.boss.agaitolos.entity.AgaitolosWitherSkull;
import com.example.akaishi.forbidden.boss.agaitolos.skill.AgaitolosGuardSkill;
import com.example.akaishi.forbidden.boss.agaitolos.skill.AgaitolosMeleeSkill;
import com.example.akaishi.forbidden.boss.agaitolos.skill.AgaitolosSkullSkill;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 阿盖托洛丝的<b>原版战斗钩子协作类</b>：受击管线（{@code hurt} 的报复 / 格挡预判 / resolve 结算 /
 * 蓄力承伤累计）与原版出手闸（{@code doHurtTarget} 普攻、{@code performRangedAttack} 远程）、
 * 以及"不受负面效果"的白名单。
 * <p>
 * 从 {@link AgaitolosEntity} 机械搬出（2026-09-24 拆分轮）：这些钩子是实体与原版
 * {@code LivingEntity} / {@code Mob} 战斗 API 的接缝，闸门顺序（报复 → 格挡 → resolve → super.hurt）
 * 是受击口径的命脉；收进本类后"挨打与出手各过哪几道闸"只有一处可看。
 * 实体覆写只留一行委托；{@code super.hurt} 经实体的 {@code doHurtSuper} 钩子调用
 * （同 {@code doHurtTargetPhysical} 的既有手法：super 调用只能在子类内部）。
 */
public final class AgaitolosCombatHooks {

    private AgaitolosCombatHooks() {
    }

    // ---------------------------------------------------------------- 受击管线

    static boolean hurt(AgaitolosEntity boss, DamageSource source, float amount) {
        // 受击报复排在一切结算之前：<b>"你打我了"这件事必须与"这一下打没打动"完全解耦</b>。
        // 若放在下面几条 return 之后，那么"被格挡 / 复活无敌 / 0.2s 冷却内"的这一次受击就不会触发报复——
        // 而玩家在无敌期狂点鼠标恰恰是最容易观察到"它不还手"的场景。
        retaliate(boss, source);
        // 格挡预判：与 resolve 的 ④ 闸走的是**同一**个 AgaitolosGuardSkill.isBlocking（§3.3「同一判定器」）。
        // 这里只负责"格挡成功 → 发动反击 + 收势"，伤害归零的口径仍由 resolve 决定（本分支直接短路，等价于归零）。
        // 必须在 resolve 之前判：counterAttack 会顺手收势，若先 resolve，判定会因架势已撤而落空。
        if (AgaitolosGuardSkill.isBlocking(boss, source)) {
            AgaitolosGuardSkill.counterAttack(boss, source);
            // 纯表现：格挡成功的火花落在"伤害来源"那一侧（取 source 的位置，理由见 AgaitolosActionFx#guardSpark）。
            // 放在 counterAttack 之后无副作用：它只收架势与推人，不动 source
            AgaitolosActionFx.guardSpark(boss, source);
            return false;
        }
        float resolved = AgaitolosDamageRules.resolve(boss.level().getGameTime(), boss.lastHurtGameTime,
                boss.getMaxHealth(), boss.isRespawning(), isReflectedSkull(source), boss.isGuarding(),
                boss.isProjectileImmune(), boss, source, amount);
        if (resolved <= 0.0F) {
            return false;
        }
        boss.lastHurtGameTime = boss.level().getGameTime();
        // 原版 LivingEntity#hurt 在 invulnerableTime > 10 时走"只结算增量伤害"分支，会吞掉 10 tick 内的后续伤害；
        // 这与本 BOSS「每 0.2s 只吃一次」的规格冲突，故清零，让自定义 4 tick 冷却成为唯一闸门。
        boss.invulnerableTime = 0;
        boolean applied = boss.doSuperHurt(source, resolved);
        if (applied) {
            // 「恶怨倒转」的打断阈值累计：只统计<b>真正落地</b>的伤害，故放在 super.hurt 返回 true 之后。
            // 上面三条 return 已经排除了「被格挡（直接短路）/ 被免伤（非玩家来源、复活无敌）/ 被 0.2s 冷却」
            // 三类分支，再叠加这里的 applied 闸，阈值绝不会被"没打到 BOSS 身上"的伤害推进。
            // 只在蓄力中累加：非蓄力期不写这个字段，避免无意义的字段变动（起手时也会显式归零）。
            if (boss.isCharging()) {
                boss.chargeDamageTaken += resolved;
            }
            // 纯表现：伤害真正落地才播受击动作；不参与结算，也不改返回值
            AgaitolosAnimations.playHurt(boss);
            // 纯表现：受击反馈粒子与受击动作同刻（同一个 applied 闸内，没真吃到伤害就不冒）
            AgaitolosActionFx.hurtFeedback(boss);
        }
        return applied;
    }

    /**
     * 受击报复：把伤害归属者（玩家）锁成当前目标。
     * <p>
     * <b>为什么必须显式做这一步</b>：本类此前<b>从不主动 setTarget</b>，唯一的目标来源是
     * {@code NearestAttackableTargetGoal<Player>} 的主动索敌（mustSee=true ⇒ 索敌要求视线）。
     * 于是"从背后打、隔着掩体打、刚进场时打"都可能落在"没有目标"的状态里：
     * BOSS 既不追也不还手，玩家看到的就是"我打它，它不理我"。
     * 配套的 {@code HurtByTargetGoal}（见 {@link AgaitolosEntity#registerGoals()}）读的是 {@code getLastHurtByMob}，
     * 而那条字段由 {@code LivingEntity#hurt} 写——只有伤害<b>真正落地</b>时才写；
     * 复活无敌 / 0.2s 冷却 / 被格挡这几条早期 return 都会绕过它，故这里独立补一道。
     * <p>
     * <b>不覆盖已有目标</b>：正在打的人不该因为旁边有人蹭了一下就换目标
     * （那属于 {@code HurtByTargetGoal} 的职责，它按"最后打我的人"排序，同样不会乱换）。
     * 这里只补"当前没有有效目标"这一种情况。
     */
    private static void retaliate(AgaitolosEntity boss, DamageSource source) {
        if (!(source.getEntity() instanceof Player player) || !player.isAlive()) {
            return;
        }
        LivingEntity current = boss.getTarget();
        if (current == null || !current.isAlive()) {
            boss.setTarget(player);
        }
    }

    /**
     * 判定这次受击是否来自「被玩家打回来的自家凋零头」（规格：反弹则 BOSS 承受且无视减伤/锁伤）。
     * <p>
     * 本 BOSS 射出的头其 owner 是 BOSS 自己；只有该弹体被玩家反弹后 owner 才变成玩家。
     * 因此「直接伤害实体是本凋零头 + 伤害归属实体是玩家」等价于「这发头被反弹回来」。
     * <p>只做判据、不做结算：伤害的免伤/减伤仍<b>唯一</b>走 {@link AgaitolosDamageRules#resolve}。
     */
    private static boolean isReflectedSkull(DamageSource source) {
        return source.getDirectEntity() instanceof AgaitolosWitherSkull
                && source.getEntity() instanceof Player;
    }

    // ---------------------------------------------------------------- 效果白名单

    static boolean canBeAffected(AgaitolosEntity boss, MobEffectInstance effect) {
        // 规格：不受负面效果影响。
        // 白名单：只放行 GLOWING。蓄力自发光（§1 第 22 条）靠"自施 GLOWING"实现，而 addEffect 的第一道闸
        // 就是本方法 —— 一律 false 会把自己的发光一并挡掉，故必须显式放行这一项。
        // 判据用 == MobEffects.GLOWING（药水是注册单例），只此一项；其余（尤其负面）仍然一律拒绝，
        // 不做"按 beneficial/harmful 分类放行"的模糊判断，将来要放行新东西必须在此显式列出。
        // 附带效果：光灵箭等外部来源的 GLOWING 也会命中，BOSS 会被标记发光（无害，且与自发光同语义）。
        return effect.getEffect() == MobEffects.GLOWING;
    }

    // ---------------------------------------------------------------- 原版出手闸（普攻 / 远程）

    static boolean doHurtTarget(AgaitolosEntity boss, Entity target) {
        // 架势期间不出手：一手格挡一手打人观感很怪，也会让"格挡 = 这一轮放弃进攻"的取舍失效。
        // 蓄力（恶怨倒转）同理：规格要求"手握紫色球往天上举"，这一轮整体让位给召唤与蓄力，
        // 出手既与 charge 姿势打架，也会让"蓄力是一个可被打断的窗口"这一代价语义失效。
        // 出场演出同理：这 4s 是"降临"，一律不起手（与所有技能起手闸同一口径，见各 tickXxx 的闸门）。
        // 复活阶段同理（本轮补齐）：那是"回血 + 无敌"的恢复窗口，招式的起手已被决策层整体禁掉，
        // 只剩原版 MeleeAttackGoal 会直接调本方法这条旁路——补上这一项，"复活期间不出手"才没有漏洞
        //（与 isRespawning 期间所有 tickXxxState 都会中断同一口径）。
        // 冲锋同理（本轮补）：俯冲镰扫的收尾处会结算一次横扫，冲锋途中再补一记普攻等于一招两段伤害，
        // 也与决策层"同一时刻只允许一招在演"的契约冲突。
        // 阶段三五招同理（2026-09-21 补）：它们各自是一段有节拍的持续状态（三连出手 / 投弹 / 踩住 / 施法），
        // 期间补一记普攻既与动作 clip 抢骨骼，也等于"一边施法一边打人"。决策层已不会派普攻，
        // 这里补的是原版 MeleeAttackGoal 那条旁路（它只认自己的可达判据）。
        if (boss.isGuarding() || boss.isCharging() || boss.isIntroPlaying() || boss.isRespawning()
                || boss.isDiving() || boss.phaseThreeState().isBusy()) {
            return false;
        }
        // 普攻间隔（本轮新收口在此）：原版 {@code MeleeAttackGoal} 的 20 tick 间隔只作用于它自己那条路径，
        // 而 {@link AgaitolosSkillDirector} 也会直接调本方法 ⇒ 两边必须共用<b>这一个</b>字段，
        // 否则普攻频率会被叠成两倍（决策层每拍一次 + Goal 自己每 20 tick 一次）。
        if (boss.meleeCooldownTicks > 0) {
            return false;
        }
        // 普攻必须整套走 skill（物理 + 凋零 + 真实伤害），不能只留原版 Mob#doHurtTarget
        boolean hit = AgaitolosMeleeSkill.perform(boss, target);
        if (hit) {
            // 纯表现：命中才播挥砍，不影响上面的结算结果
            AgaitolosAnimations.playAttack(boss);
            // 纯表现：斩击弧与挥砍动作同刻（同样只在 hit 分支内 —— 空挥没有刃痕）
            AgaitolosActionFx.meleeSlash(boss);
        }
        // 无论命中与否都起算间隔：挥空也是"这一刀挥出去了"（与横扫"被格挡也算挥出去"同一取舍），
        // 否则目标走位躲开会让 BOSS 每 tick 补刀。基准 20 tick（原版同值）再按阶段折算。
        boss.meleeCooldownTicks = AgaitolosPace.scaledCooldown(boss, AgaitolosMeleeSkill.MELEE_INTERVAL_TICKS);
        return hit;
    }

    /**
     * 远程攻击（一阶段「召唤凋零头颅」）：<b>由 {@link AgaitolosSkillDirector} 在"这一拍选中远程"时直接调用</b>。
     * <p>原先它由原版 {@code RangedAttackGoal} 按固定间隔调用，但该 Goal 与 {@code MeleeAttackGoal}
     * 抢同一组 Flag（MOVE + LOOK）、优先级又更低，实测<b>一次都放不出来</b>（详见 {@link AgaitolosEntity#registerGoals()}）。
     * 现在节奏由决策层给（见 {@link AgaitolosEntity#rangedCooldownTicks}），本方法只负责"把这一发打出去"。
     * <p>实际生成逻辑收在 {@link AgaitolosSkullSkill}，本类只做接口接线。
     *
     * @param velocity 原版的距离系数（0~1）；本招按固定初速发射，故未使用
     */
    static void performRangedAttack(AgaitolosEntity boss, LivingEntity target, float velocity) {
        // 出场演出期间一律不起手（与 doHurtTarget 同一口径）：即便被外部直接调用，也不发射弹体、不播施法动作，
        // 避免"一边降临一边吐凋零头"
        if (boss.isIntroPlaying() || target == null || !target.isAlive()) {
            return;
        }
        AgaitolosSkullSkill.fire(boss, target);
        // 纯表现：发射动作与弹体生成同刻触发
        AgaitolosAnimations.playCast(boss);
        // 纯表现：掌心聚集 + 离手弹道（一个入口含两段，理由见 AgaitolosActionFx#skullCast）。
        // 放在 fire 之后：弹体已是既成事实，粒子只做注脚，不会出现"有特效没弹体"
        AgaitolosActionFx.skullCast(boss, target);
        // 发射即起算冷却：基准 60 tick（原 RangedAttackGoal 的间隔）再按阶段折算
        boss.rangedCooldownTicks = AgaitolosPace.scaledCooldown(boss, AgaitolosSkullSkill.SKULL_COOLDOWN_TICKS);
    }
}
