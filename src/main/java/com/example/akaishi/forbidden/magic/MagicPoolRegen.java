package com.example.akaishi.forbidden.magic;

import com.example.akaishi.forbidden.magic.cast.MagicCarrierHook;
import com.example.akaishi.forbidden.magic.cast.MagicPayHooks;
import com.example.akaishi.forbidden.magic.table.MagicSource;

import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 三池自然恢复（设计记忆 §16.1 + **v33 修订**）。
 *
 * <p><b>口径（v33 定案 §0-7）</b>：三池<b>独立</b>恢复，<b>每 5 秒补 10 点</b>；<b>睡眠时 ×3</b>；
 * 战斗 / 施法<b>不减速</b>。SAN 不走本链路（它有理智系统自己的恢复管道）。
 *
 * <p><b>载体修正（C4 接入）</b>：经 {@link MagicCarrierHook}——
 * <ul>
 *   <li>{@code mainRegenMultiplier}：永动核（额外池耗尽时 ×2）与 MAIN_REGEN 词条；</li>
 *   <li>{@code overflowToExtra}：主池满时的溢流改道（蓄杖柄把恢复量存进额外池）。</li>
 * </ul>
 *
 * <p><b>为什么 5 秒一拍而不是逐 tick</b>：恢复是整数池量的小步进，逐 tick 只会摊成小数；
 * 5s 节拍与"额外池每 5 秒 10 点"同一基准（定案 §0-7），两侧节拍对齐便于排查。
 */
public final class MagicPoolRegen {

    /** 恢复节拍：100 tick = 5 秒（v33 修订：1 池量/秒 → 每 5 秒 10 点） */
    public static final int INTERVAL_TICKS = 100;

    /** 单次恢复量（池量 / 5 秒） */
    public static final float REGEN_PER_INTERVAL = 10f;

    /** 睡眠时的恢复倍率 */
    public static final float SLEEP_MULTIPLIER = 3f;

    private MagicPoolRegen() {
    }

    /** 注册到服务端节拍（由 {@code AkaishiForbiddenMod.init} 调用） */
    public static void register() {
        TickEvent.SERVER_LEVEL_POST.register(MagicPoolRegen::serverTick);
    }

    /** 每维度每 tick 调用；仅在 5 秒边界推进 */
    public static void serverTick(ServerLevel level) {
        if (level.getGameTime() % INTERVAL_TICKS != 0L) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            MagicState state = MagicStates.state(player);
            if (state == null) {
                continue;
            }
            // 单个玩家的异常不得中断其余玩家的恢复
            try {
                regenOne(state, player, player.isSleeping());
            } catch (RuntimeException ignored) {
                // 恢复失败只是"这一拍没回"，下一拍会重试
            }
        }
    }

    /** 推进一名玩家的三池；返回是否真的有变化（便于自检/调试断言） */
    public static boolean regenOne(MagicState state, Player player, boolean sleeping) {
        float amount = REGEN_PER_INTERVAL * (sleeping ? SLEEP_MULTIPLIER : 1f);
        // 载体修正：永动（额外池空则翻倍）与 MAIN_REGEN 词条（经钩子，未装载体时为 1）
        MagicCarrierHook hook = MagicPayHooks.get();
        if (hook != null) {
            amount *= Math.max(0.1f, hook.mainRegenMultiplier(player));
        }
        float capacity = state.poolCapacity();
        boolean changed = false;
        for (int i = 0; i < com.example.akaishi.forbidden.api.magic.MagicValues.POOL_COUNT; i++) {
            float current = state.pool(i);
            if (current >= capacity) {
                // 主池已满：蓄杖柄把"本该恢复的量"改道存进额外池（定案 §10.4）
                if (hook != null) {
                    hook.overflowToExtra(player, MagicStates.sourceOfPool(i), amount);
                }
                continue;
            }
            state.setPool(i, current + amount);
            changed = true;
        }
        return changed;
    }
}
