package com.example.akaishi.forbidden.sanity;

import com.example.akaishi.forbidden.sanity.shadow.ShadowCombat;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 0% 档的「幻翼自杀式袭击」：让玩家附近的原版幻翼提前锁定他并<b>加速俯冲</b>，撞上后自毁。
 *
 * <p><b>为什么敢做（而不是像"中立生物敌对"那样跳过）</b>：原版幻翼本来就是这么打的 ——
 * {@code PhantomAttackStrategyGoal} 定锚在目标上方、{@code PhantomSweepAttackGoal} 俯冲到贴身时
 * {@code doHurtTarget} 咬一口（1.20.1 源码逐行核对）。它们只是<b>只打"长期不睡"的玩家</b>。
 * 本类做的事因此是"借力"而非"改写"：<b>强制目标 + 俯冲助推 + 命中自毁</b>，三步都只作用于
 * 我们主动标记的那几只幻翼，不碰 {@code Phantom} 类、不加 mixin、不动全局刷怪。
 *
 * <p><b>边界（明确写下，避免误伤正常游玩）</b>：
 * <ol>
 *   <li>只有<b>玩家处于 0% 档</b>（{@link SanityPenalties#isLowSanPsychic(int)}）时才标记，
 *       标记来自 {@code SanityPenaltySettlement} 的 0% 档分支（1s 节拍）；</li>
 *   <li>只标记<b>该玩家 {@link #MARK_RANGE} 格内</b>、且"当前没有在追别的玩家"的幻翼；</li>
 *   <li>标记有<b>寿命</b>（{@link #MARK_TTL_TICKS}）：玩家理智回升、幻翼跑远、目标丢失、
 *       实体消失，条目一律在下一次扫描时清掉 ⇒ 幻翼随后回到原版行为（本类不再推它）；</li>
 *   <li>理智系统总开关关闭时整套不运行（由结算层的总开关前置保证）；</li>
 *   <li>不改变幻翼的伤害数值（那一下仍是原版咬击），只额外补一发精神伤害并在命中处让它自毁
 *       （{@link #IMPACT_DAMAGE}，调 0 即退化为"只自毁"）—— 与影怪同为精神伤害口径，
 *       因此同样吃既有的 COG 精神减免。</li>
 * </ol>
 *
 * <p><b>为什么状态是静态表、且键为 UUID</b>：标记只在"俯冲窗口"内存在（几秒），
 * 用 UUID + 维度 + 到期刻表达，配合逐 tick 清理，表的大小与"当前正在自杀俯冲的幻翼数"同阶，
 * 不会随怪物数量或运行时长增长（无内存泄漏面）。
 *
 * <p>数值均为<b>待调手感值</b>。
 */
public final class SanityPhantomDive {

    // ===== 手感常量（待调手感值）=====

    /** 标记范围（格）：玩家附近这个半径内的幻翼才会被征用 */
    public static final double MARK_RANGE = 24.0D;
    /** 标记寿命（tick）：100 = 5s，到期自动退出俯冲状态 */
    public static final int MARK_TTL_TICKS = 100;
    /** 俯冲助推的起效距离（格）：离得远时只锁定目标、不推速度（让它自己飞近） */
    public static final double DIVE_START_RANGE = 14.0D;
    /** 俯冲速度（格/tick）：1.5 ≈ 30 格/s，比原版俯冲明显更快 */
    public static final double DIVE_SPEED = 1.5D;
    /** 命中判定半径（格）：贴到这个距离即视为撞上，结算并自毁 */
    public static final double IMPACT_RANGE = 1.6D;
    /** 命中时追加的精神伤害（0 = 只自毁；原版咬击的伤害照常由幻翼自己结算） */
    public static final float IMPACT_DAMAGE = 3.0F;
    /** 跑出这个距离（标记范围的 1.5 倍）就放弃标记，把幻翼还给原版行为 */
    private static final double ABANDON_RANGE = MARK_RANGE * 1.5D;

    /** 幻翼 UUID → 标记（维度 + 目标玩家 + 到期刻） */
    private static final Map<UUID, Mark> MARKS = new ConcurrentHashMap<>();

    /** 一条俯冲标记 */
    private record Mark(UUID playerId, ResourceKey<Level> dimension, long expireTick) {
    }

    private SanityPhantomDive() {
    }

    /**
     * 征用附近的幻翼（1s 节拍，由 {@code SanityPenaltySettlement} 的 0% 档分支调用）。
     * <p>只标记"没有别的目标"的幻翼：已经在追别人的那些不动（少一层不可预期性）。
     */
    public static void mark(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        long expireTick = level.getGameTime() + MARK_TTL_TICKS;
        for (Phantom phantom : level.getEntitiesOfClass(Phantom.class,
                player.getBoundingBox().inflate(MARK_RANGE))) {
            if (phantom.isRemoved() || phantom.isDeadOrDying()) {
                continue;
            }
            if (phantom.getTarget() != null && !phantom.getTarget().getUUID().equals(player.getUUID())) {
                continue;
            }
            MARKS.put(phantom.getUUID(), new Mark(player.getUUID(), level.dimension(), expireTick));
        }
    }

    /**
     * 每 tick 驱动（由 {@code AkaishiMod.init} 的 SERVER_LEVEL_POST 注册）。
     * <p>只处理与当前维度匹配的条目，其余维度由该维度自己的 tick 处理；
     * 逐条校验并清理（实体没了 / 玩家没了 / 跑远 / 过期），最后才是"锁定 + 助推 + 命中自毁"。
     */
    public static void serverTick(ServerLevel level) {
        if (MARKS.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        for (Map.Entry<UUID, Mark> entry : MARKS.entrySet()) {
            Mark mark = entry.getValue();
            if (!mark.dimension().equals(level.dimension())) {
                continue;
            }
            if (mark.expireTick() < now
                    || !(level.getEntity(entry.getKey()) instanceof Phantom phantom) || phantom.isRemoved()
                    || !(level.getPlayerByUUID(mark.playerId()) instanceof ServerPlayer player)
                    || player.isDeadOrDying() || player.isCreative() || player.isSpectator()) {
                MARKS.remove(entry.getKey());
                continue;
            }
            dive(level, phantom, player);
        }
    }

    /** 单只幻翼的俯冲推进：锁目标 → 近距助推 → 贴身结算并自毁 */
    private static void dive(ServerLevel level, Phantom phantom, ServerPlayer player) {
        double distance = Math.sqrt(phantom.distanceToSqr(player));
        if (distance > ABANDON_RANGE) {
            MARKS.remove(phantom.getUUID()); // 跟丢了：还给原版行为
            return;
        }
        phantom.setTarget(player); // 借原版 AI 的俯冲链（PhantomAttackStrategyGoal → PhantomSweepAttackGoal）
        if (distance > DIVE_START_RANGE) {
            return;
        }
        Vec3 aim = player.getEyePosition().subtract(phantom.position());
        if (aim.lengthSqr() > 1.0E-6D) {
            // 直接给速度：方向锁死在玩家身上，比原版"绕圈再俯冲"快得多
            phantom.setDeltaMovement(aim.normalize().scale(DIVE_SPEED));
        }
        if (distance <= IMPACT_RANGE) {
            if (IMPACT_DAMAGE > 0.0F) {
                player.hurt(ShadowCombat.psychic(level, phantom, phantom), IMPACT_DAMAGE);
            }
            phantom.discard(); // 自杀式：撞击即解体（不走 die()，不给掉落/经验）
            MARKS.remove(phantom.getUUID());
        }
    }
}
