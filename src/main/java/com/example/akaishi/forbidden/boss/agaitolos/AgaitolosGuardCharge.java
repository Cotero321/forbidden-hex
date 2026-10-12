package com.example.akaishi.forbidden.boss.agaitolos;

import com.example.akaishi.forbidden.boss.agaitolos.skill.AgaitolosGuardSkill;
import com.example.akaishi.forbidden.boss.agaitolos.skill.AgaitolosMinionSkill;
import com.example.akaishi.forbidden.boss.agaitolos.skill.AgaitolosReversalSkill;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * 阿盖托洛丝的<b>架势（格挡）与蓄力（恶怨倒转）状态机</b>：
 * 两段"进行中状态"的推进（倒计时 / 打断出口 / 朝向锁定 / 收尾分流）、两个执行入口与共用探针。
 * <p>
 * 从 {@link AgaitolosEntity} 机械搬出（2026-09-24 拆分轮）：架势与蓄力共享同一套骨架
 * ——「决策层起手 → 实体持计时 → 每 tick 推进 → 期满/打断双出口收尾」，与俯冲（{@link AgaitolosDiveMachine}）、
 * 阶段三五招（{@code AgaitolosPhaseThreeState}）同构；收进本类后"这两招何时结算、何时清场"只有一处可看。
 * 状态字段仍归实体持有（包私有，由本类读写）；同步位 DATA_GUARDING / DATA_CHARGING 的读写仍留在实体上。
 */
public final class AgaitolosGuardCharge {

    private AgaitolosGuardCharge() {
    }

    // ---------------------------------------------------------------- 蓄力常量

    /** 恶怨倒转的蓄力时长（tick）：240 = 12s（规格明确：蓄力状态存在 12s） */
    public static final int CHARGE_DURATION_TICKS = 240;

    /**
     * 蓄力自发光（GLOWING）的药水时长：蓄力时长 + 2 的余量。
     * <p>刻意比蓄力长一点：药水与蓄力同刻起算、每 tick 递减，留余量才不会在蓄力最后几帧提前熄灭；
     * 蓄力被打断或自然结束都由 {@link #finishCharge} 主动摘除，不依赖自然过期。
     */
    private static final int CHARGE_GLOW_DURATION_TICKS = CHARGE_DURATION_TICKS + 2;

    /**
     * 「恶怨倒转」被打断所需的<b>累计承伤比例</b>：6%（阈值 = {@code getMaxHealth() × 该比例}，
     * 1444 血时 ≈ 87 点）。<b>待调手感值 / P8 转配置项</b>
     * <p>
     * <b>为什么是 6%（用户拍板：单人必须能打断）</b>：本 BOSS 受击冷却只有 4 tick
     * （{@code AgaitolosDamageRules.HURT_COOLDOWN_TICKS}），12s 内理论上有 240 / 4 = 60 个命中窗口，
     * 单次落地还要过 60% 减伤（{@code DAMAGE_MULTIPLIER}），下界合金剑 + 锋利 V（原始 11）落地约 4.4、
     * 跳跃暴击约 6.6；但真正卡住单人的是<b>玩家攻击速度</b>（1.6 ⇒ 12.5 tick/刀）：
     * 240 / 12.5 ≈ 19 刀 × 6.6 ≈ <b>125 点</b> 才是单人 12s 的实际上限（装备差些或要走位则更低）。
     * <p>
     * ⇒ 阈值取 6%（≈87，约 7 次落地）让单人<b>稳定可达</b>，同时给走位与躲小怪留约 30% 余量；
     * 若取 2%（≈29）则一碰就断、蓄力形同虚设。历史取值 25%（≈361）是"团队阈值"（单人打不断），
     * 已按用户口径废弃。若要回调：0.04F（≈58）更易、0.09F（≈130）更难。
     * <p>
     * <b>已知代价</b>：多人同场时几乎必然被打断 —— 这是"单人可打断"口径的必然结果，不是缺陷。
     */
    public static final float CHARGE_BREAK_DAMAGE_RATIO = 0.06F;

    /**
     * 恶怨倒转的冷却（tick）：900 = 45s，**在蓄力结束后**才开始计时。<b>待调手感值 / P8 转配置项</b>
     * <p>
     * <b>2026-09-21 由 300（15s）上调到 900（45s）</b>，理由（用户实测反馈"召唤怪物太频繁了"，两条根因）：
     * <ol>
     *   <li><b>基准太短</b>：本招在"蓄力 12s（{@link #CHARGE_DURATION_TICKS}）之后"才起算冷却，
     *       故改前一轮完整的间隔只有 12+15 = 27s（阶段二三经 {@link AgaitolosPace#cooldownScale} 再缩到
     *       23.25s / 21s）——<b>每 20 秒就往场上多塞 10 只凋零骷髅</b>，观感必然是"一直在招"。</li>
     *   <li><b>旧分队不消失（已同时修）</b>：技能结束不清场时，冷却到点就能在旧分队头上再招一支，
     *       场上会叠成 20、30 只（问题叠加放大）。清场落点见 {@code AgaitolosMinionSkill#dismissAll}。</li>
     * </ol>
     * <p>
     * <b>阶段差异化沿用既有唯一倍率表</b>：本常量仍走 {@link AgaitolosPace#scaledCooldown},
     * 不另立"召唤专用节奏"，故与决策层的出手节拍（{@code AgaitolosSkillDirector#beatTicksFor}）同源、不会互相打架：
     * <table border="1">
     *   <caption>改前 / 改后</caption>
     *   <tr><th>阶段</th><th>冷却系数</th><th>改前冷却</th><th>改前整轮间隔</th><th>改后冷却</th><th>改后整轮间隔</th></tr>
     *   <tr><td>一</td><td>1.00</td><td>15.0s</td><td>27.0s</td><td>45.0s</td><td><b>57.0s</b></td></tr>
     *   <tr><td>二</td><td>0.75</td><td>11.25s</td><td>23.25s</td><td>33.75s</td><td><b>45.75s</b></td></tr>
     *   <tr><td>三</td><td>0.60</td><td>9.0s</td><td>21.0s</td><td>27.0s</td><td><b>39.0s</b></td></tr>
     * </table>
     * 即"一阶段最少、二/三阶段略多"（用户口径），且每一轮都给玩家留足清场与喘息窗口。
     * 若要回调：600 ⇒ 一阶段整轮 42s（更凶），1200 ⇒ 66s（更松）。
     */
    public static final int MINION_COOLDOWN_TICKS = 900;

    // ---------------------------------------------------------------- 格挡架势（一阶段）

    /**
     * 立即收势：结束架势并进入冷却。
     * <p>只给格挡反击用（{@link AgaitolosGuardSkill#counterAttack}）：状态（剩余 tick / 冷却）归实体自己持有，
     * 技能只表达"这次反击把架势打断了"这一语义，不去碰实体的计数字段。
     */
    public static void endGuard(AgaitolosEntity boss) {
        boss.guardTicks = 0;
        // 冷却按阶段折算（二阶段起更短，即"更频繁地摆架势"）：倍率表见 AgaitolosPace
        boss.guardCooldownTicks = AgaitolosPace.scaledCooldown(boss, AgaitolosGuardSkill.GUARD_COOLDOWN_TICKS);
        boss.setGuarding(false);
    }

    /**
     * 格挡架势的<b>进行中状态</b>推进（服务端权威，每 tick 一次）：架势倒计时 + 强制收势出口 + 姿态朝向对齐。
     * <p>起手判定已移交 {@link AgaitolosSkillDirector}：<b>"该不该架"由决策层的保命招硬闸确定性判定</b>
     * （{@code shouldGuardForced}，不参与权重掷骰 —— 2026-09-21 修正：交回掷骰会让"该架的时候多半不架"），
     * 本方法只剩两件事：①"已经在举的架势怎么结束"（倒计时归零、或撞上复活/出场/死亡演出必须立刻收势）；
     * ② 守住架势方向 —— 每 tick 把头按回起手锁定的身体朝向（2026-09-21 补，理由见方法内注释）。
     * <p>架势只是"状态"，判定与消费在别处：伤害归零见 {@link AgaitolosDamageRules#resolve} 的 ④ 闸，
     * 朝向/近战判定见 {@link AgaitolosGuardSkill#isBlocking}，动画见 {@code AgaitolosAnimations} 的 guard 控制器。
     */
    static void tickGuardState(AgaitolosEntity boss) {
        if (!boss.isGuarding()) {
            return;
        }
        // 复活阶段/死亡/出场演出必须立刻撤架势：无敌演出期间还举着格挡会与死亡/复活/降临姿势打架，也白吃一次免伤
        if (boss.isRespawning() || boss.isIntroPlaying() || boss.isDeadOrDying() || --boss.guardTicks <= 0) {
            endGuard(boss);
        } else {
            // 姿态朝向对齐：把头部朝向按回身体朝向（身体 yaw 见 startGuard 的起手锁定）。
            // 原版 LookControl 每 tick 都把 yHeadRot 转向当前目标，而客户端渲染的身体朝向
            // （BodyRotationControl 跟随 yHeadRot）因此会跟着玩家转过去 —— 不对齐就会出现
            // "身子已经转向玩家、但格挡锥仍锁在起手方向"的所见非所得错位（用户反馈的"明明在架盾却挡不住"）。
            // 对齐后：渲染朝向 == 格挡锥方向（AgaitolosGuardSkill#isWithinFrontArc 读 yRot）
            // == 护盾纹方向（AgaitolosActionFx#guardAura 也读 look 的水平分量）。
            // 另一侧保证在 AgaitolosMoveControl：它在架势期间跳过身体 yaw 的转向（见其 tick() ⓪''），
            // 故这里的锁定不会每 tick 被"转向目标"覆盖掉 —— 两处合起来才是"整个架势朝向不变"。
            // 代价是架势期间头部不再追人，这恰是我们要的语义：举镰是一段"有方向的承诺"，
            // 玩家能看到它锁定了哪一侧，从那一侧之外绕过去就能打穿。
            boss.setYHeadRot(boss.getYRot());
        }
        // 纯表现：架势仍在的每 tick 刷一帧正面护盾纹；放在撤架势判定<b>之后</b>，
        // 本 tick 刚放下的架势不会再亮一帧，避免"盾已收还亮着"
        AgaitolosActionFx.guardAura(boss);
    }

    /**
     * 执行入口：起手格挡架势（由 {@link AgaitolosSkillDirector} 在"保命招硬闸命中格挡"时调用）。
     * <p>前置复校（冷却/距离）与决策层的硬闸判据同源（{@code AgaitolosSkillDirector#shouldGuardForced}）：
     * 决策层据此"必定"起手，这里再挡一次，保证"即使将来有人在别处直接调用本方法"也不会绕过冷却。
     * <p>
     * <b>起手瞬间锁定朝向（2026-09-21 补，2026-09-21 二轮复核后保留）</b>：把身体朝向对准目标，格挡锥才有意义。
     * 姿势角度的判定读的是 {@link AgaitolosEntity#getYRot()} 的水平分量（见
     * {@link AgaitolosGuardSkill#isWithinFrontArc}）。身体 yaw 自本轮起由
     * {@code AgaitolosMoveControl} 每 tick 朝目标限速转动（根因修复，字节码证据见该类 javadoc），
     * 故"起手那一刻的朝向"通常已经≈目标方向；但这里的显式对准<b>必须保留</b>，两条理由：
     * <ol>
     *   <li><b>硬保证</b>：转速是限速的（{@code MAX_YAW_SPEED_DEGREES} 度/tick），起手前一刻可能还差几度；
     *       架势锥只有 120°（半角 60°），"差几度"不该由运气决定 ⇒ 起手这一帧直接对准，锥心零误差；</li>
     *   <li><b>可惩罚面</b>：{@code AgaitolosMoveControl#tick()} ⓪'' 在<b>架势期间跳过转向</b>，
     *       故这里的锁定在整个 24t 架势里保持不变 —— 玩家仍能从锁定方向之外绕过去打穿（不是 360° 无死角）。</li>
     * </ol>
     * 写 {@code yHeadRot} 的理由同 {@code AgaitolosBlinkSkill#faceTarget}：渲染的头部朝向读它，
     * 不写会看到"身子转了、头还在看原来的方向"。
     *
     * @return 是否真的起手（冷却中/距离不满足/已被别的状态占用时为 false，此时不消耗全局节拍）
     */
    static boolean startGuard(AgaitolosEntity boss, LivingEntity target) {
        if (boss.guardCooldownTicks > 0 || boss.isRespawning() || boss.isIntroPlaying() || boss.isDiving()
                || boss.isCharging() || !boss.isTargetWithinGuardRange(target)) {
            return false;
        }
        boss.setGuarding(true);
        boss.guardTicks = AgaitolosGuardSkill.GUARD_DURATION_TICKS;
        // 朝向锁定：身体 + 头部同写（俯仰不锁 —— 它由 LookControl 按目标眼睛高度逐 tick 给，
        // 不影响水平面的格挡锥，也让"低头看着脚下的人"这一幕保留）
        float yaw = yawTowards(boss, target);
        boss.setYRot(yaw);
        boss.setYHeadRot(yaw);
        // 架势要站定：先停掉寻路，否则 Goal 会继续下发目标点。
        // （水平加速另在 AgaitolosMoveControl 里掐断——travel 在 super.aiStep() 内已执行，此处清速度对当帧无效）
        boss.getNavigation().stop();
        return true;
    }

    /**
     * 水平朝向目标所需的 yaw（度）。公式与 {@code AgaitolosBlinkSkill#faceTarget} 同源
     * （原版惯例：{@code atan2(dz, dx)} 转角度后 − 90°；MC 里 yaw 0 = 朝 +Z、−90 = 朝 +X）。
     * <p>水平位移为 0（目标恰在正上/正下方）时得 {@code atan2(0, 0) = 0} ⇒ 一个<b>确定</b>但任意的角；
     * 该情形下"是否正面"由 {@link AgaitolosGuardSkill#isWithinFrontArc} 的退化分支直接判为正面，
     * 不依赖这里的取值。
     */
    private static float yawTowards(AgaitolosEntity boss, LivingEntity target) {
        double deltaX = target.getX() - boss.getX();
        double deltaZ = target.getZ() - boss.getZ();
        return (float) (Mth.atan2(deltaZ, deltaX) * (180.0D / Math.PI)) - 90.0F;
    }

    /**
     * 只读探针：是否该起手格挡 —— 目标存活 + 水平距离已进近战可达范围（= 马上要挨打）。
     * <p>距离口径复用 {@link AgaitolosEntity#getMeleeAttackRangeSqr}（已按悬停高度放大），让"起手格挡"与"打得着它"用同一把尺子；
     * 判据本身仍是<b>水平</b>距离² 与那个 3D 可达² 比较（沿用既有口径，不擅自改成 3D —— 那会改变起手时机）。
     * <p>供 {@link AgaitolosSkillDirector} 打分使用（本类不自持"要不要格挡"的判断，只提供探针）。
     */
    static boolean isTargetWithinGuardRange(AgaitolosEntity boss, LivingEntity target) {
        if (target == null || !target.isAlive()) {
            return false;
        }
        double deltaX = target.getX() - boss.getX();
        double deltaZ = target.getZ() - boss.getZ();
        return deltaX * deltaX + deltaZ * deltaZ <= boss.getMeleeAttackRangeSqr(target);
    }

    /**
     * 只读探针：目标是否已进<b>近战可达距离</b>（3D，含悬停高度折算）。
     * <p>
     * 与 {@code AgaitolosMeleeAttackGoal} 用的是<b>同一个</b> {@link AgaitolosEntity#getMeleeAttackRangeSqr}：
     * 决策层用它判断"这一拍要不要选普攻"，Goal 用它判断"这一刀能不能挥出去"，
     * 两处若各算一套，就会出现"打分说打得着、挥出去落空"或反过来的空拍。
     */
    static boolean isTargetWithinMeleeReach(AgaitolosEntity boss, LivingEntity target) {
        return target != null && target.isAlive()
                && boss.distanceToSqr(target) <= boss.getMeleeAttackRangeSqr(target);
    }

    // ---------------------------------------------------------------- 恶怨倒转（一阶段：召唤分队 + 蓄力）

    /**
     * 「恶怨倒转」的<b>进行中状态</b>推进（服务端权威，每 tick 一次）：蓄力倒计时 → 四条取消条件 → 收尾。
     * <p>
     * 规格：召唤凋零骷髅分队（见 {@link AgaitolosMinionSkill#summon}）后进入蓄力状态 12s；
     * 期间造成<b>足量的伤害</b>则取消此状态；<b>未取消</b>则吸取周围小怪剩余血量造成 1/5 的魔法伤害（范围 20 格）。
     * <p>
     * <b>四条取消条件与"是否结算"的分流</b>（这是本招唯二的口径，改一处必须同时看另一处）：
     * <ul>
     *   <li>死亡 / 复活演出 → {@link #endChargeInterrupted}（不结算）；</li>
     *   <li>累计承伤 ≥ {@code getMaxHealth() × }{@link #CHARGE_BREAK_DAMAGE_RATIO} → {@link #endChargeInterrupted}（不结算）；</li>
     *   <li>召唤物全部阵亡 → {@link #endChargeInterrupted}（不结算）；</li>
     *   <li>蓄力跑满 {@link #CHARGE_DURATION_TICKS} → {@link #endChargeCompleted}（<b>唯一</b>会结算吸血的出口）。</li>
     * </ul>
     * 拆成两个语义入口而不是给公共清理加布尔参数：这样"哪条路径会吸血"由<b>方法名</b>直接表达，
     * 只有 {@code endChargeCompleted} 里写着 {@code drainMinions}，被打断路径物理上无从误触发。
     * <p>
     * <b>起手已不在本方法</b>：见 {@link #startCharge}（由 {@link AgaitolosSkillDirector} 调用）。
     */
    static void tickChargeState(AgaitolosEntity boss) {
        if (!boss.isCharging()) {
            return;
        }
        // 纯表现：蓄力每 tick 上报一次，节流在 AgaitolosFx 内部（每 2 tick 一帧）。
        // 放在收尾判断之前：本 tick 只要 isCharging() 仍成立就发一帧，末帧多发一次无副作用。
        AgaitolosFx.chargedOrb(boss);
        // ① 死亡/复活/出场演出必须立刻收势（与 tickGuardState 同一口径）：打断，不结算吸血
        if (boss.isDeadOrDying() || boss.isRespawning() || boss.isIntroPlaying()) {
            endChargeInterrupted(boss);
            return;
        }
        // ② 打断阈值（规格："如期间造成足量的伤害便取消此状态"）。
        //    阈值 = 最大生命 × CHARGE_BREAK_DAMAGE_RATIO（1444 × 6% ≈ 87），累计值由 hurt() 在
        //    伤害真正落地时累加、起手时归零。打断 ⇒ 不结算吸血（"取消此状态"即整招作废）。
        //    取值口径与算式见 CHARGE_BREAK_DAMAGE_RATIO 的 javadoc（6% 是为了让单人也能打断）。
        if (boss.chargeDamageTaken >= boss.getMaxHealth() * CHARGE_BREAK_DAMAGE_RATIO) {
            endChargeInterrupted(boss);
            return;
        }
        // ③ 分队全灭：提前收势，不结算（没有小怪可吸，本就不该给伤害）。
        //    判"全灭"用 ALIVE_CHECK_RADIUS（64）而不是吸血结算的 20：召唤物追出去很远也算活着，
        //    否则会把"追敌中"误判成"已阵亡"、蓄力被提前打断。
        if (AgaitolosMinionSkill.findLivingMinions(boss, AgaitolosMinionSkill.ALIVE_CHECK_RADIUS).isEmpty()) {
            endChargeInterrupted(boss);
            return;
        }
        // ④ 期满（自然结束）：规格"如未取消便吸取周围小怪剩余血量造成 1/5 魔法伤害"的唯一触发点。
        //    倒计时放在最后递减：上面三条一旦判定成立就直接返回，此处不必再管剩余 tick（endCharge* 会清零）
        if (--boss.chargeTicks <= 0) {
            endChargeCompleted(boss);
        }
    }

    /**
     * 执行入口：起手「恶怨倒转」（由 {@link AgaitolosSkillDirector} 在"这一拍选中召唤"时调用）。
     * <p>
     * 与其它招的互斥不再写在这里：决策层的全局动作锁已经保证"同一时刻只允许一招在演"
     * （起手前会检查 {@code isDiving()/isGuarding()/isCharging()/复活/演出}）。
     * 本方法只保留两条<b>本招专属</b>的前置：召唤冷却未过、目标有效 —— 前者是规格给它的 15s 冷却，
     * 后者是"召唤物要共享谁的目标"（见 {@link AgaitolosMinionSkill#summon}）。
     * <p>先召唤再立状态：召唤失败（异常情况下 0 只落位）时下一 tick 就会因"全灭"立刻收势并进冷却，
     * 不会每 tick 空转起手。
     *
     * @return 是否真的起手（冷却中/目标无效时为 false，此时不消耗全局节拍）
     */
    static boolean startCharge(AgaitolosEntity boss, LivingEntity target) {
        if (boss.minionCooldownTicks > 0 || target == null || !target.isAlive()
                || boss.isRespawning() || boss.isIntroPlaying() || boss.isDeadOrDying()) {
            return false;
        }
        AgaitolosMinionSkill.summon(boss);
        boss.chargeTicks = CHARGE_DURATION_TICKS;
        // 上一轮的承伤累计必须归零后再起手：否则"被打断过的那一轮"的累计会残留，
        // 下一轮起手第一 tick 就立刻再判成打断、连蓄力都立不起来
        boss.chargeDamageTaken = 0.0F;
        boss.setCharging(true);
        // 蓄力自发光（§1 第 22 条）：给 BOSS 自己挂 GLOWING，起手加、finishCharge() 摘。
        // 靠"自施药水"而非 setGlowingTag：发光是要随状态自动消失的临时状态，药水自带计时，
        // 且 GLOWING 走原版实体描边通道（LevelRenderer 对任何实体都生效，不需要改 renderer）。
        // 注意副作用：描边色由队伍决定，无队伍时为白色（不是紫色）；发光轮廓会穿墙可见。
        // 参数 (duration, amplifier=0, ambient=false, visible=false, showIcon=false)：不要药水气泡粒子，
        // 只要轮廓（气泡会被误读成"中毒"一类状态）。
        boss.addEffect(new MobEffectInstance(MobEffects.GLOWING, CHARGE_GLOW_DURATION_TICKS, 0, false, false, false));
        return true;
    }

    /**
     * 蓄力收尾（<b>期满结算</b>）：蓄力跑满 {@link #CHARGE_DURATION_TICKS} 自然结束时走这条 ——
     * 先做公共清理，再结算「恶怨倒转」的吸血（规格："如未取消便吸取周围小怪剩余血量造成 1/5 的魔法伤害"）。
     * <p>结算收在 {@link AgaitolosReversalSkill#drainMinions}：本类只表达"这一轮蓄力没被打断"这一语义，
     * 半径、伤害折算与归属筛选归技能持有（与 {@link AgaitolosGuardSkill#counterAttack} 同一分工）。
     * <p><b>三步顺序不可调换</b>：{@code finishCharge}（撤蓄力状态，动画/出手闸先回常态）→
     * {@code drainMinions}（按规格吸血，内部 kill 掉 20 格内的）→ {@code dismissAll}（收尾清场）。
     * 若把清场提到吸血之前，吸血会把"已经消失的小怪"当成 0 血来算 ⇒ 这一招直接空放。
     */
    private static void endChargeCompleted(AgaitolosEntity boss) {
        finishCharge(boss);
        // 先收干净状态再结算：结算会杀死召唤物，期间 BOSS 不该还处于"蓄力中"（否则动画/出手闸会打架）
        AgaitolosReversalSkill.drainMinions(boss);
        // 收尾清场：把 20 格外的漏网者、以及结算瞬间新追近的残余一并回收（口径见 dismissAll 的 javadoc）
        AgaitolosMinionSkill.dismissAll(boss);
    }

    /**
     * 蓄力收尾（<b>被打断</b>）：死亡 / 复活 / 承伤达阈值 / 召唤物全灭走这条，<b>不结算吸血</b>
     * （规格：蓄力被打断即"此状态被取消"，吸不到任何东西 —— 这正是玩家抢输出的收益）。
     * <p>同时<b>整队回收</b>：打断的收益是"这一招白放"，不该变成"留下一整队小怪继续追着玩家打"
     * —— 旧口径只收状态不清场，正是用户实测到的"释放完技能之后怪物不会消失"。
     */
    private static void endChargeInterrupted(AgaitolosEntity boss) {
        finishCharge(boss);
        AgaitolosMinionSkill.dismissAll(boss);
    }

    /**
     * 蓄力收尾的公共部分：清计时、清承伤累计、置冷却、撤状态。
     * <p>两个语义入口（{@link #endChargeCompleted} / {@link #endChargeInterrupted}）共用本方法，
     * 保证"只要不在蓄力就必须在冷却"这条不变量不会因分流而漏掉一边。
     */
    private static void finishCharge(AgaitolosEntity boss) {
        boss.chargeTicks = 0;
        // 承伤累计随收尾一起清：两个出口都清，下一轮起手（tickCharge 起手处也清一次）不会残留
        boss.chargeDamageTaken = 0.0F;
        // 冷却按阶段折算（二阶段起更短）：倍率表见 AgaitolosPace
        boss.minionCooldownTicks = AgaitolosPace.scaledCooldown(boss, MINION_COOLDOWN_TICKS);
        boss.setCharging(false);
        // 自发光随状态一起摘：否则会出现"球已经收了，人还亮着"的错位
        boss.removeEffect(MobEffects.GLOWING);
    }
}
