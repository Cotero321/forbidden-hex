package com.example.akaishi.forbidden.boss.agaitolos;

import com.example.akaishi.forbidden.boss.agaitolos.arena.NetherPrisonArena;
import com.example.akaishi.forbidden.boss.agaitolos.skill.AgaitolosMinionSkill;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

/**
 * 阿盖托洛丝的<b>生命周期钩子协作类</b>：死亡演出（{@code tickDeath} 的 2.5s 收尾门槛）、
 * 死亡/移除时的整队回收与牢狱还原挂点。
 * <p>
 * 从 {@link AgaitolosEntity} 机械搬出（2026-09-24 拆分轮）：die / tickDeath / remove 三条钩子
 * 共同保证「召唤物与场地在 BOSS 离场的每条路径上都收敛」（§4.4 自愈口径的入口），
 * 收进本类后"哪条路径清什么、哪条刻意不清"只有一处可看。
 * 实体覆写只留一行委托；{@code super.die} / {@code super.remove} 经实体的
 * {@code doSuperDie} / {@code doSuperRemove} 钩子调用（同 {@code doHurtTargetPhysical} 的既有手法）。
 */
public final class AgaitolosLifecycle {

    private AgaitolosLifecycle() {
    }

    // 死亡演出时长常量在 AgaitolosEntity#DEATH_TICKS（50 = 2.5s，与 death clip 对齐；
    // AgaitolosActionFx#deathSoulRise 直接引用它，故留在实体上），本类只消费。

    // ---------------------------------------------------------------- 死亡 / 移除钩子

    static void die(AgaitolosEntity boss, DamageSource source) {
        boss.doSuperDie(source);
        // 整队回收召唤物：BOSS 一死，分队就没有存在理由（它们是这一招的产物，不是野外怪）。
        // 放 die() 而不是等 tickDeath 收尾：死亡演出还有 2.5s（DEATH_TICKS），
        // 若等到那时才清，玩家会看到"BOSS 已经倒了，小怪还在打人"这半段错位。
        AgaitolosMinionSkill.dismissAll(boss);
        // 下界牢狱开始还原（设计 §4.4 的"BOSS 死亡 ⇒ 场地还原"）：只置状态、不动方块，
        // 逐格还原由 NetherPrisonArena 的分批节拍做，死亡演出（2.5s）与还原可以并行
        NetherPrisonArena.end(boss);
        // 纯表现：死亡瞬间的一次性青蓝爆发。只做表现、不干扰结算与回收。
        // die() 每次死亡只会进来一次：原版 LivingEntity#hurt 在 isDeadOrDying() 时直接 return false，
        // 且 LivingEntity#die 自身有 !this.dead 闸，故不会重复爆发。
        AgaitolosFx.deathBurst(boss);
    }

    /**
     * 死亡演出：把 death clip（2.5s）放完再走原版收尾。
     * <p>
     * 原版在 {@code deathTime >= 20}（1s）就 {@code remove(KILLED)}，2.5s 的死亡动画只能看到前 40%；
     * 这里把收尾门槛抬到 {@link AgaitolosEntity#DEATH_TICKS}，其余逐行照抄 {@code LivingEntity#tickDeath}
     * （{@code broadcastEntityEvent((byte) 60)} + {@code remove(KILLED)}），<b>不另开收尾路径</b>。
     * <p>血条无需在此回收：它已是客户端 overlay，实体从客户端世界消失后血条自然不再绘制。
     * <p>
     * <b>尸体姿态安全性（已实测源码）</b>：本 BOSS 由 GeckoLib 的 {@code GeoEntityRenderer} 渲染，
     * 它继承的是 {@code EntityRenderer} 而<b>不是</b> {@code LivingEntityRenderer}，
     * 死亡倾倒角这段代码根本不参与；即便参与，{@code LivingEntityRenderer#setupRotations} 里
     * {@code f = Mth.sqrt((deathTime + partialTicks - 1) / 20 * 1.6); if (f > 1.0f) f = 1.0f;}
     * 也已把倾倒系数钳在 1.0（deathTime ≈ 14 即到顶），更长的 deathTime 不会让尸体继续翻转。
     */
    static void tickDeath(AgaitolosEntity boss) {
        ++boss.deathTime;
        // 纯表现：死亡演出期间逐帧外逸的灵魂（服务端限定在 AgaitolosActionFx 内兜底）。
        // 挂在演出计时上而不是 die()：die() 只有一帧，而"灵魂外逸上升"是一段需要逐帧推进的过程
        AgaitolosActionFx.deathSoulRise(boss, boss.deathTime);
        if (boss.deathTime >= AgaitolosEntity.DEATH_TICKS && !boss.level().isClientSide() && !boss.isRemoved()) {
            boss.level().broadcastEntityEvent(boss, (byte) 60);
            boss.remove(Entity.RemovalReason.KILLED);
        }
    }

    /**
     * 实体被"从世界移除"（死亡收尾 {@code KILLED} / {@code discard} / 换维度）时整队回收召唤物。
     * <p>
     * <b>为什么挂在 {@code remove} 而不是 {@code setRemoved}</b>（实测 1.20.1 字节码）：
     * {@code Entity#setRemoved} 是 <b>final</b>（编译期就会报"无法覆盖 final 方法"），
     * 而 {@code Entity#remove(reason)} 内部正是 {@code setRemoved(reason)} + {@code invalidateCaps()}、
     * {@code LivingEntity#remove} 再补 {@code brain.clearMemories()} ⇒ 覆写 {@code remove} 就覆盖了
     * 所有走 {@code remove}/{@code discard} 的路径。
     * <p>
     * <b>区块卸载这一支不在这里</b>：{@code PersistentEntitySectionManager#unloadEntity(EntityAccess)}
     * 直接调 {@code setRemoved(UNLOADED_TO_CHUNK)}，<b>不经过 {@code remove}</b>，而 {@code setRemoved}
     * 又是 final（无钩子可挂）。故那一种情形改由<b>召唤物自带的看门狗</b>兜底：
     * {@code AgaitolosMinionWatchdogGoal} 每 tick 查一次"主人还在不在、还在不在蓄力"，主人被卸载即自我消散；
     * 即便看门狗也随着召唤物的区块一起卸载（重载后变回原版凋零骷髅、丢掉这个 Goal），
     * BOSS 自身的蓄力状态是落盘的，重载后蓄力照常推进并在 ≤12s 内走到收尾清场，不会留下永久孤儿。
     * <p>{@code UNLOADED_WITH_PLAYER} 被排除：那一条发生在整维度回收（关服/删维度）的时刻，
     * 世界正在拆，此刻遍历并 discard 其它实体没有收益、只有风险。
     * <p><b>幂等</b>：没有存活召唤物时只是一次 64 格查询 + 一次裁判空队，可被多个出口重复调用。
     */
    static void remove(AgaitolosEntity boss, Entity.RemovalReason reason) {
        boss.doSuperRemove(reason);
        // 血条已整体移交客户端 overlay：实体离开客户端世界后 overlay 自然扫不到它，
        // 这里不再需要（也没有）任何服务端血条资源要回收。
        if (!boss.level().isClientSide() && reason != Entity.RemovalReason.UNLOADED_WITH_PLAYER) {
            AgaitolosMinionSkill.dismissAll(boss);
            // /kill 直接走 remove(KILLED)（不经 die），换维度/普通 discard 也走这里 ⇒ 场地还原挂在此处才全覆盖。
            // 幂等：die() 已经置过还原态时这次是空操作；区块卸载（UNLOADED_TO_CHUNK）不经 remove，
            // 那一条由 NetherPrisonArena 的 ACTIVE 看门狗兜底（设计 §4.4 同样要求"区块卸载也要能还"）
            NetherPrisonArena.end(boss);
        }
    }
}
