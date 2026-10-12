package com.example.akaishi.forbidden.boss.agaitolos;

import net.minecraft.nbt.CompoundTag;

/**
 * 阿盖托洛丝的<b>NBT 存取协作类</b>：{@code addAdditionalSaveData} / {@code readAdditionalSaveData}
 * 的具体读写（键名与读写顺序逐字承自实体，未做任何增删或重排）。
 * <p>
 * 从 {@link AgaitolosEntity} 机械搬出（2026-09-24 拆分轮）：存档字段横跨冷却 / 阶段 / 缩放 / 演出复位
 * 四块职责，是实体里最长的两段样板；实体覆写只留一行委托，键常量随本类迁移。
 * <p>
 * 落盘口径（与拆分前逐位一致）：
 * <ul>
 *   <li><b>冷却与进行中标志落盘</b>（不落盘则读档白送一次招式/免伤/惩罚窗口）；</li>
 *   <li><b>「已定案场内人数」落盘</b>（{@link #NBT_PLAYER_COUNT}）—— 血量上限以属性修饰符独立落盘，
 *       人数不落盘会出现"血量按 3 人、伤害按 1 人"的分叉（见 {@code AgaitolosScaling}）；</li>
 *   <li><b>出场演出刻意不落盘</b>（读回一律复位，理由见 {@link #load} 内注释）；</li>
 *   <li><b>阶段三五招只落冷却</b>，进行中状态刻意不落盘（理由见 {@code AgaitolosPhaseThreeState} 的类注释）。</li>
 * </ul>
 */
public final class AgaitolosNbtCodec {

    private AgaitolosNbtCodec() {
    }

    // ---------------------------------------------------------------- NBT 键（键名逐字承自实体，未改）

    private static final String NBT_PHASE = "AgaitolosPhase";
    private static final String NBT_RESPAWN_TICKS = "AgaitolosRespawnTicks";
    private static final String NBT_INITIAL_RESPAWN_DONE = "AgaitolosInitialRespawnDone";
    private static final String NBT_GUARDING = "AgaitolosGuarding";
    private static final String NBT_GUARD_TICKS = "AgaitolosGuardTicks";
    private static final String NBT_GUARD_COOLDOWN = "AgaitolosGuardCooldown";
    private static final String NBT_DIVE_TICKS = "AgaitolosDiveTicks";
    private static final String NBT_DIVE_SWEEP_COOLDOWN = "AgaitolosDiveSweepCooldown";
    private static final String NBT_GROUNDED_TICKS = "AgaitolosGroundedTicks";
    private static final String NBT_SCYTHE_SEAL_TICKS = "AgaitolosScytheSealTicks";
    private static final String NBT_CHARGING = "AgaitolosCharging";
    private static final String NBT_CHARGE_TICKS = "AgaitolosChargeTicks";
    private static final String NBT_MINION_COOLDOWN = "AgaitolosMinionCooldown";
    private static final String NBT_CHARGE_DAMAGE = "AgaitolosChargeDamage";
    private static final String NBT_BLINK_COOLDOWN = "AgaitolosBlinkCooldown";
    private static final String NBT_KICK_COOLDOWN = "AgaitolosKickCooldown";
    private static final String NBT_MELEE_COOLDOWN = "AgaitolosMeleeCooldown";
    private static final String NBT_RANGED_COOLDOWN = "AgaitolosRangedCooldown";
    /** 已定案的「场内人数」（缩放用），见 {@link AgaitolosScaling} */
    private static final String NBT_PLAYER_COUNT = "AgaitolosPlayerCount";

    // ---------------------------------------------------------------- 写（由实体覆写一行委托，super 由实体先调）

    static void save(AgaitolosEntity boss, CompoundTag tag) {
        tag.putInt(NBT_PHASE, boss.getPhase().combatOrdinal());
        tag.putInt(NBT_RESPAWN_TICKS, boss.respawnTicks);
        tag.putBoolean(NBT_INITIAL_RESPAWN_DONE, boss.initialRespawnDone);
        // 已定案的场内人数一并落盘（理由见字段注释）：它决定伤害缩放系数，而血量上限是以属性修饰符
        // 的形式独立落盘的 ⇒ 两者必须同时落盘，否则读档后会出现"血量按 3 人、伤害按 1 人"的分叉
        tag.putInt(NBT_PLAYER_COUNT, boss.scaledPlayerCount);
        // 架势与冷却一并落盘（理由同 DATA_RESPAWNING）：读档/区块卸载会重建实体，
        // 不落盘则"正举着格挡"的 BOSS 读档后白送一次免伤，冷却也会被重置成可立刻连挡。
        tag.putBoolean(NBT_GUARDING, boss.isGuarding());
        tag.putInt(NBT_GUARD_TICKS, boss.guardTicks);
        tag.putInt(NBT_GUARD_COOLDOWN, boss.guardCooldownTicks);
        // 冲锋 / 禁飞 / 封印 / 横扫冷却同理落盘：不落盘则"正在俯冲的 BOSS"读档后会停在半空，
        // 玩家辛苦格挡换来的 30s 禁飞与封印也会被读档洗掉。
        tag.putInt(NBT_DIVE_TICKS, boss.diveTicks);
        tag.putInt(NBT_DIVE_SWEEP_COOLDOWN, boss.diveSweepCooldownTicks);
        tag.putInt(NBT_GROUNDED_TICKS, boss.groundedTicks);
        tag.putInt(NBT_SCYTHE_SEAL_TICKS, boss.scytheSealTicks);
        // 蓄力与召唤冷却同理落盘：不落盘则"正举着球蓄力的 BOSS"读档后会白送一次 12s 蓄力，
        // 冷却也会被重置成可立刻再招一支分队。
        tag.putBoolean(NBT_CHARGING, boss.isCharging());
        tag.putInt(NBT_CHARGE_TICKS, boss.chargeTicks);
        tag.putInt(NBT_MINION_COOLDOWN, boss.minionCooldownTicks);
        // 打断阈值的累计承伤也要落盘：不落盘则"玩家已经打进一半阈值的蓄力"被读档洗成从零开始，
        // 等于用读档白嫖一次续命（区块卸载/重启都会重走 readAdditionalSaveData）
        tag.putFloat(NBT_CHARGE_DAMAGE, boss.chargeDamageTaken);
        // 二阶段两招的冷却同理落盘：不落盘则读档会白送一次瞬击/踢击（且与"冷却已过"无法区分）
        tag.putInt(NBT_BLINK_COOLDOWN, boss.blinkCooldownTicks);
        tag.putInt(NBT_KICK_COOLDOWN, boss.kickCooldownTicks);
        // 普攻/远程的出手间隔同理落盘：不落盘则读档会白送一次贴脸连击（普攻间隔原本藏在原版 Goal 里，
        // 读档本来也会被重置；现在由实体持有，就把它一并按同一口径处理，不留特例）
        tag.putInt(NBT_MELEE_COOLDOWN, boss.meleeCooldownTicks);
        tag.putInt(NBT_RANGED_COOLDOWN, boss.rangedCooldownTicks);
        // 阶段三五招：只落冷却（口径同上 —— 不落盘则读档会白送一次大招）；
        // 进行中的状态（三连节拍 / 轰炸 / 抓取 / 劈击 / 施法）**刻意不落盘**，
        // 理由见 AgaitolosPhaseThreeState 的类注释（抓取态续播要凭一个可能失效的 UUID 去控人，风险远大于收益）
        boss.phaseThreeState().save(tag);
    }

    // ---------------------------------------------------------------- 读（由实体覆写一行委托，super 由实体先调）

    static void load(AgaitolosEntity boss, CompoundTag tag) {
        // 缺键时 getInt 返回 0，而 0 在 AgaitolosPhase 里是 RESPAWN（不是 PHASE_1）——
        // 复活阶段<b>永远不会由阶段机产生</b>（enterRespawn 只动 respawning 布尔，不动阶段字段），
        // 所以存档里出现 0 只可能是"键不存在"（典型场景：/summon 生成时原版会把命令 NBT 整个 load 一遍，
        // 那里没有本键）。若照搬 getInt 的 0，BOSS 会永久停在 RESPAWN：
        // checkPhaseAdvance 对它取不到阈值（-1）直接 return ⇒ <b>永远进不了二/三阶段</b>
        // （表现：招式永远只有一阶段那五招、模型不换、节奏不加快）。
        // 故这里显式区分"键不存在"与"值为 0"：缺键一律当 PHASE_1（新实体的正确初值）。
        boss.setPhase(tag.contains(NBT_PHASE)
                ? AgaitolosPhase.byCombatOrdinal(tag.getInt(NBT_PHASE))
                : AgaitolosPhase.PHASE_1);
        boss.respawnTicks = Math.max(0, tag.getInt(NBT_RESPAWN_TICKS));
        // 存档里若还在复活阶段，恢复无敌与倒计时：BOSS 不能靠读档跳过无敌期
        boss.setRespawning(boss.respawnTicks > 0);
        boss.initialRespawnDone = tag.getBoolean(NBT_INITIAL_RESPAWN_DONE);
        // 场内人数（缩放用）：缺键一律当 1（= 单人，与改动前逐位一致）。
        // 读回后<b>重挂一次</b>缩放修饰符而不是只赋值：applyFor 是幂等的（摘旧挂新），
        // 且 super.readAdditionalSaveData 已把属性块（含永久修饰符）与当前血量读完 ⇒ 这里既保留
        // "血量按比例不变"，又顺手把"旧存档缺修饰符 / 属性被外部工具改过"的坏数据纠正回与人数一致。
        AgaitolosScaling.applyFor(boss, tag.contains(NBT_PLAYER_COUNT) ? tag.getInt(NBT_PLAYER_COUNT) : 1);
        // 缺键时 getInt 返回 0 ⇒ 冷却为 0（可立刻起手），属安全默认
        boss.guardTicks = Math.max(0, tag.getInt(NBT_GUARD_TICKS));
        boss.guardCooldownTicks = Math.max(0, tag.getInt(NBT_GUARD_COOLDOWN));
        // 架势随存档恢复；但剩余 tick 已为 0（异常存档）时不该继续举着，交给 tickGuardState 下一 tick 收势
        boss.setGuarding(tag.getBoolean(NBT_GUARDING) && boss.guardTicks > 0);
        // 四个计时一律做下界保护：缺键 ⇒ 0（安全默认，等同"没有在冲锋/禁飞/封印/冷却"），
        // 异常存档里的负值也只当 0，绝不把状态搞成负数（负数会让 isDiving() 等判定失真）
        boss.diveTicks = Math.max(0, tag.getInt(NBT_DIVE_TICKS));
        boss.diveSweepCooldownTicks = Math.max(0, tag.getInt(NBT_DIVE_SWEEP_COOLDOWN));
        boss.groundedTicks = Math.max(0, tag.getInt(NBT_GROUNDED_TICKS));
        boss.scytheSealTicks = Math.max(0, tag.getInt(NBT_SCYTHE_SEAL_TICKS));
        // 蓄力 / 召唤冷却同做下界保护；蓄力随存档恢复，但剩余 tick 已为 0（异常存档）时不该继续举着，
        // 交给 tickChargeState 下一 tick 收势
        boss.chargeTicks = Math.max(0, tag.getInt(NBT_CHARGE_TICKS));
        boss.minionCooldownTicks = Math.max(0, tag.getInt(NBT_MINION_COOLDOWN));
        // 下界保护：缺键 ⇒ 0（安全默认，等同"这一轮蓄力还没被打进任何伤害"）；
        // 异常存档里的负值也只当 0，否则负累计会让阈值判定永远差一截才算数
        boss.chargeDamageTaken = Math.max(0.0F, tag.getFloat(NBT_CHARGE_DAMAGE));
        // 二阶段两招的冷却同做下界保护（缺键 ⇒ 0 = 可立刻起手，安全默认；异常负值也只当 0）
        boss.blinkCooldownTicks = Math.max(0, tag.getInt(NBT_BLINK_COOLDOWN));
        boss.kickCooldownTicks = Math.max(0, tag.getInt(NBT_KICK_COOLDOWN));
        // 普攻/远程间隔同做下界保护（口径与上面四招完全一致：缺键 ⇒ 0 = 可立刻出手）
        boss.meleeCooldownTicks = Math.max(0, tag.getInt(NBT_MELEE_COOLDOWN));
        boss.rangedCooldownTicks = Math.max(0, tag.getInt(NBT_RANGED_COOLDOWN));
        boss.setCharging(tag.getBoolean(NBT_CHARGING) && boss.chargeTicks > 0);
        // 阶段三五招：只读冷却（缺键 ⇒ 0 = 可立刻起手，安全默认；异常负值也只当 0，由状态机内部钳制）。
        // 进行中的状态一律复位成"没在演"：bombard 的同步位由 defineSynchedData 的默认 false 保证，
        // 抓取态则因为"本类不再逐 tick 钉人"而天然消失 —— 这就是它最硬的一条松手出口。
        boss.phaseThreeState().load(tag);
        // 出场演出<b>刻意不落盘</b>（没有对应的 NBT 键），读回时一律复位成"没在出场"：
        // ① 它是首次召唤的一次性演出（initialRespawnDone 已落盘保证不重演），续播没有意义；
        // ② 续播还会错位 —— 降临的基准是"开演那一 tick 的悬停高度"，服务器存盘/区块卸载后这个基准已经丢了，
        //    若接着从半空插值，BOSS 会擦着地面或悬在半空落地（比直接复位更糟）。
        // 复位后由 AgaitolosMoveControl ③ 的悬停修正把 BOSS 收回常态高度，无需额外处理。
        boss.introTicks = 0;
        boss.setIntroPlaying(false);
    }
}
