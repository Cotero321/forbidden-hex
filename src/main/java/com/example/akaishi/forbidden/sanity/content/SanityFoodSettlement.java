package com.example.akaishi.forbidden.sanity.content;

import com.example.akaishi.forbidden.api.sanity.ISanityFoodProfile;
import com.example.akaishi.forbidden.api.sanity.SanityChangeSource;
import com.example.akaishi.forbidden.api.sanity.SanityFoodRegistry;
import com.example.akaishi.config.ModConfig;
import com.example.akaishi.forbidden.sanity.SanityServiceImpl;
import com.example.akaishi.forbidden.sanity.SanityState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 食补窗口的<b>逐 tick 分摊</b>结算 + 迷之炖菜的生命随机波动。
 *
 * <p><b>为什么单独一条 tick，而不是并入环境结算（1s 节拍）</b>：窗口总量必须"摊到窗口内每个 tick"
 * 才谈得上"持续进食过程"（1s 粒度会让 5s 的迷之炖菜只剩 5 片、且档位/播报体感断层）。
 * 环境结算因成本原因固定 1s 节拍，两者节奏不同，故分开注册、各管一摊。
 *
 * <p><b>开销</b>：无窗口时 {@code foodStates()} 为空、直接早退；有窗口时每 tick 一次映射遍历与两次除法的量级，
 * 与"每 tick 玩家移速同步"同量级，可忽略。
 *
 * <p><b>落盘纪律</b>：窗口走完、且距上次食用已超过重置时间时，把链计数归零 ⇒
 * {@code FoodRuntime} 回到"空闲"状态，从而不再写进存档（避免"吃过一次就永久留条目"）。
 */
public final class SanityFoodSettlement {

    /** 迷之炖菜：生命随机波动的总时长（tick）：80 = 4s（待调手感值） */
    public static final int STEW_RIDER_TICKS = 80;
    /** 掷点步长（tick）：20 = 每秒一次，4s 共 4 次 */
    private static final int STEW_RIDER_STEP_TICKS = 20;
    /** 生命波动幅度上界（|Δ| &lt; 4，取 3.9 保证严格小于 4；待调手感值） */
    public static final float STEW_HEALTH_AMPLITUDE = 3.9f;

    /**
     * 迷之炖菜的生命波动剩余刻：玩家 → 剩余 tick。
     *
     * <p>刻意<b>不落盘</b>：这是 4 秒的瞬时表现，重登/换维度丢掉一次波动无实际损失；
     * 用 {@link WeakHashMap} 让玩家对象被回收后条目自动消失（沿用
     * {@code AkaishiSocketEffectHandler} 对"玩家级瞬时状态"的既有做法），
     * 且写入与读取都在服务端主线程（进食事件与 tick），不需要额外同步。
     */
    private static final Map<ServerPlayer, Integer> STEW_RIDER = new WeakHashMap<>();

    private SanityFoodSettlement() {
    }

    /** 服务端每个维度每 tick 调用（由 AkaishiMod.init 的 SERVER_LEVEL_POST 驱动） */
    public static void serverTick(ServerLevel level) {
        if (!ModConfig.sanityEnabled) {
            return; // 总开关关闭：窗口不推进（已写入的窗口保留在存档里，重新打开后继续分摊）
        }
        long now = level.getGameTime();
        for (ServerPlayer player : level.players()) {
            tickWindows(player, now);
            tickStewRider(player);
        }
    }

    /** 开启（或刷新）迷之炖菜的生命波动：由 {@link SanityFoodService} 在进食时调用 */
    public static void startStewRider(ServerPlayer player) {
        STEW_RIDER.put(player, STEW_RIDER_TICKS);
    }

    /**
     * 窗口分摊：每 tick 施加"剩余总量 / 剩余 tick"，并把剩余量减掉切片。
     *
     * <p>用"除以剩余 tick"而不是"总量 ÷ 总 tick 的定值切片"：前者天然吸收浮点误差
     * （最后一片必然收敛到 0），后者会留一串除不尽的尾巴。被上限夹掉的部分算正常损耗
     * （理智已满时补量本就无处可去），不做余量回滚。
     */
    private static void tickWindows(ServerPlayer player, long now) {
        SanityState state = SanityServiceImpl.state(player);
        if (state == null || state.foodStates().isEmpty()) {
            return;
        }
        for (Map.Entry<String, SanityState.FoodRuntime> entry : state.foodStates().entrySet()) {
            SanityState.FoodRuntime runtime = entry.getValue();
            int remaining = runtime.windowTicks();
            if (remaining <= 0) {
                settleFinishedWindow(runtime, entry.getKey(), now);
                continue;
            }
            float sanSlice = runtime.windowSan() / remaining;
            float protectionSlice = runtime.windowProtection() / remaining;
            runtime.setWindow(runtime.windowSan() - sanSlice, remaining - 1,
                    runtime.windowProtection() - protectionSlice);
            if (sanSlice != 0f) {
                SanityServiceImpl.instance().addSanInternal(player, sanSlice, SanityChangeSource.FOOD);
            }
            if (protectionSlice != 0f) {
                SanityServiceImpl.instance().addProtectionInternal(player, protectionSlice, SanityChangeSource.FOOD);
            }
        }
    }

    /** 窗口收尾：清掉浮点残尾；超过重置时间后把链计数归零（条目回到空闲 ⇒ 不再落盘） */
    private static void settleFinishedWindow(SanityState.FoodRuntime runtime, String itemId, long now) {
        if (runtime.windowSan() != 0f || runtime.windowProtection() != 0f) {
            runtime.setWindow(0f, 0, 0f);
        }
        if (runtime.chain() <= 0 || runtime.lastEatTick() <= 0) {
            return;
        }
        ISanityFoodProfile profile = SanityFoodRegistry.get(itemId);
        int refreshTicks = SanityBuiltinFood.refreshTicksOf(profile);
        if (now - runtime.lastEatTick() > refreshTicks) {
            runtime.setChain(0, 0L);
        }
    }

    /**
     * 迷之炖菜：4s 内每秒掷一次生命波动（待确认的读法）。
     *
     * <p>原文"4s 内随机 ±生命&lt;4"有歧义，此处采用读法：<b>每 20 tick 掷一次，
     * 幅度均匀取 [−3.9, +3.9)</b>——正值回血，负值按魔法伤害扣血；扣血不会致死
     * （最多扣到剩 1 点），避免"吃口炖菜被随机数打死"。
     */
    private static void tickStewRider(ServerPlayer player) {
        Integer ticks = STEW_RIDER.get(player);
        if (ticks == null) {
            return;
        }
        if (ticks <= 0) {
            STEW_RIDER.remove(player);
            return;
        }
        if (ticks % STEW_RIDER_STEP_TICKS == 0) {
            float roll = (player.getRandom().nextFloat() * 2f - 1f) * STEW_HEALTH_AMPLITUDE;
            if (roll >= 0f) {
                player.heal(roll);
            } else {
                float damage = Math.min(-roll, Math.max(0f, player.getHealth() - 1f));
                if (damage > 0f) {
                    player.hurt(player.damageSources().magic(), damage);
                }
            }
        }
        STEW_RIDER.put(player, ticks - 1);
    }
}
