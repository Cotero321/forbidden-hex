package com.example.akaishi.forbidden.sanity;

import com.example.akaishi.forbidden.api.sanity.ISanityFirstEncounter;
import com.example.akaishi.forbidden.api.sanity.SanityCallbacks;
import com.example.akaishi.forbidden.api.sanity.SanityContext;
import com.example.akaishi.forbidden.api.sanity.SanityFirstRegistry;
import com.example.akaishi.config.ModConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;

/**
 * 首见事件的<b>接入层</b>：声明式轮询 + 触达提示。
 *
 * <p><b>为什么轮询不放在环境结算类里</b>：环境结算类只管"规则扣减 + 暗处状态机"，
 * 首见是另一套语义（一次性记档、上限/认知的一次性增减），混在一起会让两套节流与状态互相污染；
 * 但两者共用<b>同一份上下文与节拍</b>（由 {@code SanityEnvironmentSettlement} 在玩家循环里调用
 * {@link #settle}），因此不会产生第二份群系/结构查询。
 *
 * <p><b>成本口径</b>：轮询先按 {@code first_seen} 做 O(1) 查档，<b>已触发过的条目一次判据都不跑</b>；
 * 未触发条目的判据按代价升序（上下文备忘量 → 方块扫描 → 实体距离扫描 → 物品栏扫描），
 * 且每名玩家最快 1s 一轮（跟随环境结算的 {@code SETTLE_PERIOD_TICKS}）。
 * 换句话说：一名玩家把 20 条全部触发之后，本类每轮只剩 20 次集合查表。
 */
public final class SanityFirstEncounterSettlement {

    private static final Logger LOGGER = LogManager.getLogger("akaishi.sanity");

    /** 提示安装幂等标记（init 被重复调用时只装一份监听） */
    private static boolean notifierInstalled;

    private SanityFirstEncounterSettlement() {
    }

    /**
     * 安装"首见提示"：监听核心派发的 {@link SanityCallbacks#fireFirstEncounter}，按条目的 langKey 发一条聊天提示。
     *
     * <p>提示是纯表现，故挂在回调上而不是塞进 {@code SanityServiceImpl}：数值层不依赖
     * {@code ChatComponent}/玩家连接，附属也不会因为"核心发了提示"而被波及。
     */
    public static void registerNotifier() {
        if (notifierInstalled) {
            return;
        }
        notifierInstalled = true;
        SanityCallbacks.registerFirstEncounterListener(SanityFirstEncounterSettlement::notify);
    }

    /** 派发首见提示（取条目的 langKey；条目已被注销时静默跳过） */
    private static void notify(Player player, ResourceLocation encounterId) {
        if (!(player instanceof ServerPlayer server)) {
            return;
        }
        ISanityFirstEncounter encounter = SanityFirstRegistry.get(encounterId);
        if (encounter == null) {
            return;
        }
        server.sendSystemMessage(Component.translatable(encounter.langKey()));
    }

    /**
     * 声明式轮询：对<b>尚未触发</b>的条目逐个求值，命中即经核心记档并结算。
     *
     * <p>由 {@code SanityEnvironmentSettlement#settlePlayer} 在每个玩家的环境节拍里调用（复用其上下文与缓存）。
     * 单条判据抛异常按"未命中"隔离（本次跳过，下轮再试），不影响其余条目与主流程。
     */
    public static void settle(ServerPlayer player, SanityContext ctx) {
        if (player == null || ctx == null || !ModConfig.sanityEnabled) {
            return;
        }
        Collection<ISanityFirstEncounter> encounters = SanityFirstRegistry.getAll();
        if (encounters.isEmpty()) {
            return; // 无注册条目：连玩家状态都不取（零开销承诺）
        }
        SanityState state = SanityServiceImpl.state(player);
        if (state == null) {
            return;
        }
        for (ISanityFirstEncounter encounter : encounters) {
            if (state.hasFirstSeen(encounter.id().toString())) {
                continue; // 已记档：不跑判据（轮询成本只花在未触发条目上）
            }
            boolean hit;
            try {
                hit = encounter.test(ctx);
            } catch (Throwable t) {
                LOGGER.warn("[akaishi] 理智首见判定异常（本次视为未命中）: {}", encounter.id(), t);
                continue;
            }
            if (hit) {
                SanityServiceImpl.instance().reportFirstEncounter(player, encounter.id());
            }
        }
    }
}
