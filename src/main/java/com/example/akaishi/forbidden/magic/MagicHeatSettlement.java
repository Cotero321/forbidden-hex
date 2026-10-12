package com.example.akaishi.forbidden.magic;

import com.example.akaishi.forbidden.magic.cast.MagicCastManager;

import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 过载热值结算（设计记忆 §24.1）。
 *
 * <p><b>口径</b>：
 * <ul>
 *   <li><b>自然散热 −10 / 秒</b>（未施法时）；</li>
 *   <li><b>热值 ≥ 100 ⇒ 进入过载罚站</b>，罚站期间散热加速到 <b>−25 / 秒</b>、且<b>禁止施法</b>；</li>
 *   <li>解除条件 = <b>时长走完（5s）且热值归零</b>（两个条件都要满足）。</li>
 * </ul>
 *
 * <p><b>入热由谁产生</b>：施法时 `20 + 词数 × 4`（§24.1）—— 那是 P5 执行层的职责，
 * 本类只管"热值自己怎么降"。分开的原因：散热是<b>常驻</b>的（不施法也在跑），
 * 与施法流程无关，混在一起会让"没在施法时热值永不下降"。
 *
 * <p><b>为什么触发判定必须在散热之前</b>：若先散热，热值 100 会先被降到 90，
 * 此时再判 {@code overheated()} 已为假 ⇒ <b>永远触发不了过载</b>。
 *
 * <p><b>为什么取"先判触发、再散热、后判解除"这个顺序</b>：三段各自依赖不同的状态快照，
 * 顺序写反任一处都会静默失效（少触发、或该解除时不解除）。
 *
 * <p><b>持续段的例外（v25 接入，原 §33.5）</b>：处于 X3 引导 / X7 连发的<b>持续段</b>时，
 * <b>暂停自然散热</b>并改为 <b>+2/秒</b>（§24.1 v13）。若只加不减地写 +2/s，会被 −10/s 完全吃掉、
 * 等于虚设 —— 故这两个动作必须是<b>互斥的同一分支</b>，而不是"加热之后再散热"。
 * 判据来自 {@link MagicCastManager#inContinuousSegment}（施法状态机）。
 *
 * <p><b>尚未接入</b>：§24.1 的「<b>质变 B 法阵复现</b>期间同样暂停散热并 +2/秒」。
 * 质变 B 的法阵由世界实体承载（§36.2），其"正在复现"的标志要到 P5-B 建该实体后才存在。
 * <b>现在不预留空开关</b> —— 留一个没人调用的开关，就是又一个"声明了不实现"。
 */
public final class MagicHeatSettlement {

    /** 结算节拍：20 tick = 1 秒（与三池恢复同频） */
    public static final int INTERVAL_TICKS = 20;

    /** 自然散热速率（热值/秒 · §24.1） */
    public static final float COOLING_PER_SECOND = 10f;

    /** 过载罚站期间的加速散热速率（热值/秒 · §24.1） */
    public static final float OVERLOAD_COOLING_PER_SECOND = 25f;

    private MagicHeatSettlement() {
    }

    /** 注册到服务端 1 秒节拍（由 {@code AkaishiForbiddenMod.init} 调用） */
    public static void register() {
        TickEvent.SERVER_LEVEL_POST.register(MagicHeatSettlement::serverTick);
    }

    /** 每维度每 tick 调用；仅在秒边界推进 */
    public static void serverTick(ServerLevel level) {
        if (level.getGameTime() % INTERVAL_TICKS != 0L) {
            return;
        }
        long now = level.getGameTime();
        for (ServerPlayer player : level.players()) {
            MagicState state = MagicStates.state(player);
            if (state == null) {
                continue;
            }
            // 单个玩家的异常不得中断其余玩家
            try {
                settleOne(state, player, now);
            } catch (RuntimeException ignored) {
                // 这一秒没结算，下一拍会重试
            }
        }
    }

    /**
     * 推进一名玩家的热值。
     *
     * @param player   该玩家（用于查"是否处于持续段"）
     * @param gameTime 当前游戏刻
     * @return 本秒是否真的发生了变化（便于自检 / 调试断言）
     */
    public static boolean settleOne(MagicState state, Player player, long gameTime) {
        // ① 触发（必须在散热之前，否则 100 − 10 = 90 会漏触发）
        if (state.overheated()) {
            state.markOverloaded(gameTime, MagicState.OVERLOAD_DURATION_SECONDS);
        }

        // ② 散热：持续段改为"暂停散热 + 持续加热"（两条互斥，写在同一个分支里）
        // 散热速率乘载体词条（寂杖端 消散 +50%，经载体钩子；未装载体时为 1）
        float dissipation = COOLING_PER_SECOND;
        com.example.akaishi.forbidden.magic.cast.MagicCarrierHook hook =
                com.example.akaishi.forbidden.magic.cast.MagicPayHooks.get();
        if (hook != null) {
            dissipation *= Math.max(0.1f, hook.heatDissipationMultiplier(player));
        }
        float before = state.heat();
        if (MagicCastManager.inContinuousSegment(player)) {
            state.addHeat(MagicCastManager.CHANNEL_HEAT_PER_SECOND);
        } else if (before > MagicState.HEAT_MIN) {
            state.addHeat(state.overloaded(gameTime)
                    ? -OVERLOAD_COOLING_PER_SECOND : -dissipation);
        }

        // ③ 解除：热值归零 **且** 罚站时长走完（两个条件都满足）
        if (state.heat() <= MagicState.HEAT_MIN && !state.overloaded(gameTime)) {
            state.clearOverload();
        }
        return Math.abs(state.heat() - before) > 0f;
    }
}
