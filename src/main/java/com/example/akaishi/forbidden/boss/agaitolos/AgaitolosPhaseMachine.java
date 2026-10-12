package com.example.akaishi.forbidden.boss.agaitolos;

import com.example.akaishi.forbidden.boss.agaitolos.arena.NetherPrisonArena;
import com.example.akaishi.forbidden.boss.agaitolos.skill.AgaitolosMinionSkill;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 阿盖托洛丝的<b>阶段机与复活阶段</b>：阶段阈值判定 → 推进、复活阶段（无敌 + 回血 + 结束击飞 + 凋零 III）、
 * 以及"阶段三免疫远程"的阶段探针。
 * <p>
 * 从 {@link AgaitolosEntity} 机械搬出（2026-09-24 拆分轮）：阶段/复活是一条独立的状态线
 * （阈值与缩放、复活回血、场地还原钩子互相咬合），与招式状态机无耦合；收进本类后
 * "进阶段必经哪几步"（setPhase → 定案人数 → enterRespawn）只有一处可看。
 * 倒计时与标志字段仍归实体持有（包私有，由本类读写）；同步位 DATA_PHASE / DATA_RESPAWNING 的读写仍留在实体上。
 */
public final class AgaitolosPhaseMachine {

    private AgaitolosPhaseMachine() {
    }

    // ---------------------------------------------------------------- 复活阶段常量

    /** 复活阶段时长：4s = 80 tick（用户拍板；期间回满生命，结束时击飞周围玩家） */
    public static final int RESPAWN_DURATION_TICKS = 80;

    /** 复活结束的击飞半径（格）——待调手感值 */
    public static final double RESPAWN_KNOCKBACK_RADIUS = 12.0D;

    /** 复活结束的水平击飞力度——待调手感值 */
    public static final double RESPAWN_KNOCKBACK_HORIZONTAL = 1.2D;

    /** 复活结束的垂直上抛力度——待调手感值 */
    public static final double RESPAWN_KNOCKBACK_VERTICAL = 0.9D;

    // ---------------------------------------------------------------- 阶段阈值判定（aiStep 每 tick）

    /** 阶段阈值判定：生命比例跌破本阶段的下一阶段门槛即推进 */
    static void checkPhaseAdvance(AgaitolosEntity boss) {
        // 已死亡 ⇒ 阶段机必须停摆。
        // 漏这道的后果不是"少进一个阶段"而是不可逆的僵尸态：血量归零时 0 <= 阈值 恒成立 ⇒ advancePhase ⇒
        // enterRespawn 开始回血 ⇒ isDeadOrDying() 变回 false ⇒ LivingEntity#tick 的
        // 「isDeadOrDying() && shouldTickDeath()」不再成立 ⇒ tickDeath 停摆 ⇒ 实体**永远不被移除**，
        // 表现为"死亡动画冻在半途 + 血条满格 + 打不死"。
        // 当前该路径**尚不可达**（单次伤害被锁伤 max(24, 5%maxHealth) 卡在 72.2，无法从 >50% 一刀到 0；
        // /kill 又被受击管线第 ① 步『非玩家来源免伤』挡下），但它是一颗引信：任何放开伤害上限的改动都会踩爆。
        if (boss.isDeadOrDying()) {
            return;
        }
        AgaitolosPhase phase = boss.getPhase();
        double threshold = phase.healthThresholdRatio();
        // healthThresholdRatio() 用 -1 表示"无阈值"：PHASE_3 与 RESPAWN 都走这条，不可能再推进
        if (threshold < 0.0D) {
            return;
        }
        // 用 <=：规格"每失去一半的生命值进入下一阶段"，恰好在门槛上也算进入
        if (boss.getHealth() / boss.getMaxHealth() <= threshold) {
            advancePhase(boss, phase.nextCombatPhase());
        }
    }

    /** 推进阶段：落阶段 + 进复活阶段。换模型（P7）与解锁招式（P4~P6）后续在此接入 */
    private static void advancePhase(AgaitolosEntity boss, AgaitolosPhase next) {
        boss.setPhase(next);
        // 随在场玩家数增强：<b>每次进阶段重算一次</b>（§1 第 11 条，用户拍板的口径 —— 玩家中途进出
        // 只在阶段边界生效）。放在 enterRespawn 之前：复活阶段会把血量回满，上限必须先定案，
        // 回血的分母（getMaxHealth() / RESPAWN_DURATION_TICKS）才是新上限的 1/80。
        // 同时这里也是"阶段阈值跟着上限走"的另一半：阈值本就读 当前 上限的比例（AgaitolosPhase#healthThresholdRatio），
        // 上限一涨，二/三阶段的门槛立刻同步上涨（见该枚举的 javadoc）。
        AgaitolosScaling.applyPlayerCountScaling(boss);
        enterRespawn(boss);
    }

    // ---------------------------------------------------------------- 复活阶段

    /**
     * 进入复活阶段：无敌 + 4s 内回满，倒计时归零时击飞周围玩家。
     * <p>
     * <b>同时整队回收召唤物</b>（首次召唤与阶段推进两条入口都经这里）：复活是"无敌 + 回血 + 4s 演出"的
     * 场景边界 —— 这段窗口里留在场上的小怪只会单方面追打玩家（BOSS 无敌、又不该让"召唤物还能打"
     * 成为复活的附带伤害）。阶段推进必然先把蓄力打断（{@code tickChargeState} 的 ① 闸会走到
     * {@code endChargeInterrupted}，那里已经清过一次），这里是同一口径的显式化：
     * <b>"进入复活 ⇒ 场上无召唤物"是一条不变量，不依赖调用顺序</b>。
     * <p>首次召唤那一 tick 也走本方法，此时场上本来就没有召唤物，{@code dismissAll} 是空操作
     * （代价只是一次 64 格实体查询，一次性开销）。
     */
    static void enterRespawn(AgaitolosEntity boss) {
        boss.initialRespawnDone = true;
        boss.respawnTicks = RESPAWN_DURATION_TICKS;
        boss.setRespawning(true);
        AgaitolosMinionSkill.dismissAll(boss);
    }

    /** 复活阶段每 tick：回血并递减倒计时，归零则收尾 */
    static void tickRespawn(AgaitolosEntity boss) {
        if (--boss.respawnTicks > 0) {
            // 每 tick 回 maxHealth/80：4s 恰好回满；heal 内部按最大生命夹取，不会溢出
            boss.heal(boss.getMaxHealth() / RESPAWN_DURATION_TICKS);
            return;
        }
        boss.heal(boss.getMaxHealth());
        boss.setRespawning(false);
        knockbackNearbyPlayers(boss);
        // 规格 §0：复活后对下界牢狱内的所有生物（含玩家）施加凋零 III 持续 5s。
        // 挂在这里而不是"首次召唤"分支：复活阶段有两个入口（首次召唤 / 阶段推进），两处都要表现
        NetherPrisonArena.applyRespawnWither(boss);
    }

    /** 复活结束的击飞：半径内玩家被推离 BOSS 并上抛 */
    private static void knockbackNearbyPlayers(AgaitolosEntity boss) {
        double radiusSqr = RESPAWN_KNOCKBACK_RADIUS * RESPAWN_KNOCKBACK_RADIUS;
        for (Player player : boss.level().getEntitiesOfClass(Player.class,
                boss.getBoundingBox().inflate(RESPAWN_KNOCKBACK_RADIUS))) {
            if (player.distanceToSqr(boss) > radiusSqr) {
                continue; // 取到的是方盒，按球半径再筛一遍
            }
            Vec3 offset = player.position().subtract(boss.position());
            Vec3 horizontal = new Vec3(offset.x, 0.0D, offset.z);
            // 玩家恰好位于 BOSS 正下方时水平向量无法归一化，退化为纯上抛
            Vec3 direction = horizontal.lengthSqr() > 1.0E-6D ? horizontal.normalize() : Vec3.ZERO;
            player.setDeltaMovement(direction.x * RESPAWN_KNOCKBACK_HORIZONTAL,
                    RESPAWN_KNOCKBACK_VERTICAL,
                    direction.z * RESPAWN_KNOCKBACK_HORIZONTAL);
            // 服务端改速度不会自动下发，置 hurtMarked 让原版把速度包发给该玩家
            player.hurtMarked = true;
        }
    }

    // ---------------------------------------------------------------- 阶段探针

    /**
     * 是否处于「免疫远程」的阶段（<b>阶段三专属</b>，规格："BOSS 免疫远程攻击"）。
     * <p>
     * 判据用 {@code combatOrdinal() >= PHASE_3}（与 {@code AgaitolosPhaseTwoSkills#isPhaseTwoOrLater} 同一写法）：
     * 复活阶段序数为 0，天然不在此列 —— 那一段的免伤由受击管线第 ② 步（复活无敌）负责，
     * 两条口径不重叠。
     * <p>
     * <b>唯一消费点</b>是 {@link AgaitolosDamageRules#resolve} 的第 ⑥ 闸（弹射物伤害归零）。
     * 本方法只回答"这个阶段要不要免疫"，"什么算弹射物"的口径在
     * {@link AgaitolosDamageRules#isProjectileDamage} 一处，不在这里复制第二份。
     */
    public static boolean isProjectileImmune(AgaitolosEntity boss) {
        return boss.getPhase().combatOrdinal() >= AgaitolosPhase.PHASE_3.combatOrdinal();
    }
}
