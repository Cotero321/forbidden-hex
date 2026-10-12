package com.example.akaishi.forbidden.boss.agaitolos;

import com.example.akaishi.forbidden.boss.agaitolos.arena.ArenaGeometry;
import com.example.akaishi.forbidden.boss.agaitolos.arena.ArenaRecord;
import com.example.akaishi.forbidden.boss.agaitolos.arena.ArenaSnapshotStore;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.UUID;

/**
 * 阿盖托洛丝「随在场玩家数增强」（设计文档 §0 第 71 行 / §1 定案表第 11 条）的<b>唯一口径</b>。
 * <p>
 * 规格原文只有一句「BOSS 会根据在场玩家数获得生命值提升、伤害幅度提升」，本类把它落成三条可执行的式子：
 * <ul>
 *   <li><b>生命</b>：{@code BASE_HEALTH × (1 + 0.30 × (n − 1))}；</li>
 *   <li><b>固定数值伤害</b>：{@code × (1 + 0.15 × (n − 1))}；</li>
 *   <li><b>召唤物</b>：血量与伤害按 {@code (1 + 0.30 × (n − 1))} 放大，并按同一 {@code n} 提高召唤数量。</li>
 * </ul>
 * <p>
 * <b>为什么百分比最大生命的招式不乘人数系数（用户拍板）</b>：俯冲镰扫 30%、高速踢击 20%、
 * 投技① 25%、投技② 20% 打的都是<b>玩家的</b>最大生命，与本 BOSS 的血量无关；
 * 而它们要"随人数变强"的正确途径是<b>人数 → BOSS 血量 → 阶段更耐打 / 复活窗口更多</b>，
 * 再乘一层人数系数等于同一件事收两次税（一份人数加成被算计两遍）。
 * 故本类只提供"固定值"这一把尺子，百分比伤害的调用方<b>不</b>调它。
 * <p>
 * <b>为什么把"数人"也放在这里</b>：判据（牢狱几何 vs 兜底半径）与缩放系数必须在同一个类里对齐 ——
 * 分家的后果是"按牢狱数的人、却按另一个半径缩放"，而这正是本项目历史上反复出现的"两套口径"病。
 * <p>
 * <b>不实时跟随（用户拍板）</b>：本类的 {@link #countPresentPlayers} 只在
 * 「BOSS 入场」与「每次进阶段」两处被调用（见 {@code AgaitolosEntity}），
 * 结果缓存在实体的 {@code scaledPlayerCount} 字段并落盘。玩家中途进出<b>不</b>改变本次已定的人数，
 * 避免"打着打着血量忽大忽小"的观感灾难与血条抖动的技术问题。
 */
public final class AgaitolosScaling {

    // ---------------------------------------------------------------- 手感常量（待调手感值 / P8 转配置项）

    /**
     * 基础生命：与 {@code AgaitolosEntity.createAttributes} 的 {@code MAX_HEALTH} 基础值<b>同源</b>
     * （规格「生命 1444」）。只在这里留一份，避免"改了一处忘了另一处"。
     */
    public static final double BASE_HEALTH = 1444.0D;

    /** 基础攻击力：与 {@code createAttributes} 的 {@code ATTACK_DAMAGE} 基础值同源（规格「基础攻击 30」） */
    public static final double BASE_ATTACK_DAMAGE = 30.0D;

    /**
     * 每多 1 名在场玩家的<b>生命</b>加成比例：+30%（用户拍板）。
     * <p>即 1 人 = 100%（1444）、2 人 = 130%（1877.2）、3 人 = 160%（2310.4）……
     * <p>与 {@code AgaitolosDamageRules} 的锁伤上限（5% 当前最大生命）联动：血量抬高后锁伤上限同步抬高，
     * 不会再让"多人血量"被单次 72.2 的天花板削平。待调手感值 / P8 转配置项
     */
    public static final double HEALTH_PER_EXTRA_PLAYER = 0.30D;

    /**
     * 每多 1 名在场玩家的<b>固定数值伤害</b>加成比例：+15%（用户拍板，保留定案表原提案）。
     * <p>只作用于"写死的数值"：BOSS 攻击力（普攻物理段）、普攻真实段、凋零头爆炸 5 / 真实 2。
     * 百分比最大生命的招式不乘（理由见类注释）。待调手感值 / P8 转配置项
     */
    public static final double DAMAGE_PER_EXTRA_PLAYER = 0.15D;

    /**
     * 计入缩放的玩家数<b>硬上限</b>：8。
     * <p>取值依据：极多玩家（服务器大厅、刷怪场）下 {@code 1 + 0.30 × 7 = 3.1} 已是 4486 血、
     * 单次锁伤上限 224；继续线性放大既没有手感收益（战斗时长与人数早已非线性），
     * 又会让 BossBar 的百分比显示与"打一管血"的节奏彻底失控，故按 8 人封顶。
     * 超出部分的玩家仍能正常输出与承伤，只是不再继续抬高数值。待调手感值 / P8 转配置项
     */
    public static final int MAX_SCALED_PLAYERS = 8;

    /** 召唤物血量/伤害的放大比例：与 BOSS 血量同比例（+30%/人，用户拍板）。待调手感值 / P8 转配置项 */
    public static final double MINION_ATTR_PER_EXTRA_PLAYER = 0.30D;

    /**
     * 每多 1 人额外召唤的<b>近战</b>凋零骷髅数：+1。
     * <p>取值依据：基准 5 只（规格）已够围住 BOSS，增量取 1 是"多一个人多一个对手"的最小可见台阶；
     * 取 2 会让 4 人局一次刷 11 只近战，在牢狱半径 50 的封闭场地里形成纯粹的物理挤压。待调手感值 / P8 转配置项
     */
    public static final int MINION_EXTRA_MELEE_PER_PLAYER = 1;

    /** 每多 1 人额外召唤的<b>远程</b>凋零骷髅数：+1（理由同近战）。待调手感值 / P8 转配置项 */
    public static final int MINION_EXTRA_RANGED_PER_PLAYER = 1;

    /**
     * 近战召唤数的<b>硬上限</b>：9。
     * <p>取值依据：① 性能 —— 加上远程 9 只，整场召唤物封顶 18 只，仍低于原版"一次袭击"的量级；
     * ② 阵型 —— 半径 {@code AgaitolosMinionSkill.RING_RADIUS}（4 格）均分 9 只时相邻弧长
     * {@code 2π × 4 / 9 ≈ 2.8} 格，远大于凋零骷髅 0.7 的碰撞宽度，落位仍不互相挤压。待调手感值 / P8 转配置项
     */
    public static final int MINION_MAX_MELEE = 9;

    /** 远程召唤数的硬上限：9（半径 6 格时相邻弧长 ≈ 4.2 格，理由同近战）。待调手感值 / P8 转配置项 */
    public static final int MINION_MAX_RANGED = 9;

    /**
     * 无牢狱记录时的人数判据半径（格）：64。
     * <p>刻意复用既有的 64（{@code AgaitolosMinionSkill.ALIVE_CHECK_RADIUS} 与
     * {@code Attributes.FOLLOW_RANGE} 同值，也是本 BOSS 的索敌/搜寻尺度）：
     * 召唤物追人追不出这个范围，超出 64 格的玩家本来也打不到 BOSS，
     * 不另造一个"防重复半径"式的数字。待调手感值 / P8 转配置项
     */
    public static final double FALLBACK_PLAYER_RADIUS = 64.0D;

    private AgaitolosScaling() {
    }

    // ---------------------------------------------------------------- 在场玩家数判据

    /**
     * 数出本 BOSS 的「场内人数」n（仅服务端权威；客户端返回 1，因为该值只在服务端被消费）。
     * <p>
     * <b>两条路径（用户拍板）</b>：
     * <ol>
     *   <li><b>有牢狱记录</b>（本 BOSS 名下存在 {@link ArenaRecord}）⇒ 取牢狱几何
     *       {@link ArenaGeometry#box}（中心 ±50 格圆盘、垂直 ±20 层）内的玩家数 ——
     *       与上一轮场地系统<b>同一个</b> {@code box}，不另写一套"牢狱范围"判定；</li>
     *   <li><b>无牢狱记录</b>（{@code /summon} 旁路、或本维度已有牢狱导致 {@code begin} 跳过铺场）
     *       ⇒ 退化为"以 BOSS 为中心、半径 {@link #FALLBACK_PLAYER_RADIUS} 内的玩家数"。</li>
     * </ol>
     * <p>
     * <b>为什么只数"生存/冒险"</b>：创造模式玩家不受伤害（放大 BOSS 只会让真正在打的人更难受），
     * 旁观者更是纯粹的观测者 —— 两者都不构成"必须增强"的理由，故与 {@code isAlive()} 一并筛掉。
     * 结果是"至少 1"（{@link #clampPlayerCount} 兜底），单人场景与改动前逐位一致。
     */
    public static int countPresentPlayers(AgaitolosEntity boss) {
        if (!(boss.level() instanceof ServerLevel level)) {
            return 1;
        }
        ArenaRecord record = ArenaSnapshotStore.get(level).findForBoss(boss.getUUID());
        if (record != null) {
            return clampPlayerCount(countParticipants(level, ArenaGeometry.box(record.center())));
        }
        double radiusSqr = FALLBACK_PLAYER_RADIUS * FALLBACK_PLAYER_RADIUS;
        int count = 0;
        // 包围盒粗筛（方盒）+ 球半径精筛：与 AgaitolosEntity#knockbackNearbyPlayers 等既有查询同一手法
        for (Player player : level.getEntitiesOfClass(Player.class, boss.getBoundingBox().inflate(FALLBACK_PLAYER_RADIUS))) {
            if (isParticipant(player) && player.distanceToSqr(boss) <= radiusSqr) {
                count++;
            }
        }
        return clampPlayerCount(count);
    }

    /** 盒内"参与战斗的玩家"计数（牢狱路径：box 已是圆盘外接方盒，与场地系统同义，不再做二次球筛） */
    private static int countParticipants(ServerLevel level, AABB box) {
        int count = 0;
        for (Player player : level.getEntitiesOfClass(Player.class, box)) {
            if (isParticipant(player)) {
                count++;
            }
        }
        return count;
    }

    /** 是否算"在场参战"：存活 + 生存/冒险（排除创造与旁观，理由见 {@link #countPresentPlayers}） */
    private static boolean isParticipant(Player player) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator();
    }

    // ---------------------------------------------------------------- 缩放算式

    /** 玩家数下界保护：至少 1、至多 {@link #MAX_SCALED_PLAYERS}（负值/0 一律回退 1，防坏数据把血量算成 0） */
    public static int clampPlayerCount(int rawPlayers) {
        return Math.max(1, Math.min(MAX_SCALED_PLAYERS, rawPlayers));
    }

    /** 生命缩放系数（1.0 = 1 人时的原样）：{@code 1 + 0.30 × (n − 1)} */
    public static double healthScale(int players) {
        return 1.0D + HEALTH_PER_EXTRA_PLAYER * (clampPlayerCount(players) - 1);
    }

    /** 固定数值伤害缩放系数：{@code 1 + 0.15 × (n − 1)} */
    public static double damageScale(int players) {
        return 1.0D + DAMAGE_PER_EXTRA_PLAYER * (clampPlayerCount(players) - 1);
    }

    /** 召唤物属性缩放系数：{@code 1 + 0.30 × (n − 1)}（与 BOSS 血量同比例，用户拍板） */
    public static double minionAttributeScale(int players) {
        return 1.0D + MINION_ATTR_PER_EXTRA_PLAYER * (clampPlayerCount(players) - 1);
    }

    /**
     * 召唤数量：{@code min(硬上限, 基准 + 每人增量 × (n − 1))}。
     * <p>基准数与上限都取常量（{@code AgaitolosMinionSkill.MELEE_COUNT} 等），
     * n = 1 时结果恒等于基准 ⇒ 单人场景与改动前逐位一致。
     */
    public static int scaledMinionCount(int baseCount, int perPlayer, int hardCap, int players) {
        return Math.min(hardCap, baseCount + perPlayer * (clampPlayerCount(players) - 1));
    }

    // ---------------------------------------------------------------- 缩放落点（修饰符挂载，2026-09-24 拆分轮自实体搬入）

    /**
     * 生命缩放修饰符的 UUID。
     * <p>做成 {@code ADDITION} 的<b>永久</b>修饰符而不是直接改基础值：{@code AttributeInstance#save()} 会写
     * 基础值 + 永久修饰符两者，但走修饰符才能让"重算"变成幂等的"摘旧的、挂新的"（见
     * {@link #setScalingBonus}），基础值则始终是规格里的 1444 / 30 这两个数，读起来不会自相矛盾。
     */
    private static final UUID UUID_SCALE_HEALTH = UUID.fromString("a1c2e3f4-0b17-4c58-9d6e-2f3a4b5c6d71");

    /** 攻击力缩放修饰符的 UUID（理由同 {@link #UUID_SCALE_HEALTH}） */
    private static final UUID UUID_SCALE_ATTACK = UUID.fromString("a1c2e3f4-0b17-4c58-9d6e-2f3a4b5c6d72");

    /**
     * <b>重算</b>场内人数并按它缩放生命与攻击力（服务端；只在入场与每次进阶段两个点调用）。
     * <p>调用点见 {@code aiStep} 的首次召唤分支与 {@link AgaitolosPhaseMachine#advancePhase}；
     * <b>不做</b>每 tick 跟随，理由见 {@link AgaitolosEntity#scaledPlayerCount}。
     */
    public static void applyPlayerCountScaling(AgaitolosEntity boss) {
        applyFor(boss, countPresentPlayers(boss));
    }

    /**
     * 按给定人数缩放（幂等）。
     * <p>
     * <b>为什么按"比例"保留当前血量而不是按绝对值</b>：入场那一路刚把血量压到
     * {@code INTRO_HEALTH_RATIO}（见 {@code aiStep}），若直接抬高上限，压在 25% 的血会变成
     * "1877 上限里的 361（19%）"，出场演出的回血曲线立刻走形；按比例搬移则"压到 25%"的语义不变。
     * 阶段推进那一路本来就紧接 {@link AgaitolosPhaseMachine#enterRespawn} 的回满，故这条口径对它同样无害。
     *
     * @param boss   目标 BOSS（人数定案值落在其 {@code scaledPlayerCount} 字段上并随实体落盘）
     * @param players 已定案的场内人数（未钳制，由 {@link AgaitolosScaling} 内部钳制）
     */
    public static void applyFor(AgaitolosEntity boss, int players) {
        boss.scaledPlayerCount = clampPlayerCount(players);
        float healthRatio = boss.getMaxHealth() > 0.0F ? boss.getHealth() / boss.getMaxHealth() : 1.0F;
        setScalingBonus(boss, Attributes.MAX_HEALTH, UUID_SCALE_HEALTH, "Agaitolos player scaling health",
                BASE_HEALTH * (healthScale(boss.scaledPlayerCount) - 1.0D));
        setScalingBonus(boss, Attributes.ATTACK_DAMAGE, UUID_SCALE_ATTACK, "Agaitolos player scaling attack",
                BASE_ATTACK_DAMAGE * (damageScale(boss.scaledPlayerCount) - 1.0D));
        // 上限变了：按旧比例搬回当前血量（setHealth 内部按新上限夹取，故缩小时也不会超上限）
        boss.setHealth(boss.getMaxHealth() * healthRatio);
    }

    /**
     * 幂等地设置一条属性加成：先按 UUID 摘掉旧值，再按需挂新值。
     * <p>用 {@code addPermanentModifier} 而非 {@code addTransientModifier}：前者会被
     * {@code AttributeInstance#save()} 序列化 ⇒ 区块卸载/读档后加成<b>不丢</b>
     * （原版 {@code Mob#finalizeSpawn} 的刷怪加成正是这么做的）。
     * <p>加成为 0（单人局）时<b>不挂</b>：少一条无意义的修饰符，也让存档里的属性块保持干净。
     */
    private static void setScalingBonus(AgaitolosEntity boss, Attribute attribute, UUID id, String name, double bonus) {
        AttributeInstance instance = boss.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(id);
        if (bonus > 1.0E-6D) {
            instance.addPermanentModifier(new AttributeModifier(id, name, bonus, AttributeModifier.Operation.ADDITION));
        }
    }
}
