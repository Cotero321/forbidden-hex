package com.example.akaishi.forbidden.wand;

import com.example.akaishi.forbidden.magic.MagicState;
import com.example.akaishi.forbidden.magic.MagicStates;
import com.example.akaishi.forbidden.magic.table.MagicSource;

import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 法杖的<b>常驻结算节拍</b>（1 秒）：
 * <ol>
 *   <li><b>过热阈值刷新</b>：手持杖（主/副手）的 HEAT_THRESHOLD 乘子刷进玩家状态
 *       （寂杖端 +20% ⇒ 阈值 120）；收起即回落 —— 阈值是"拿着它"的条件属性；</li>
 *   <li><b>额外池恢复</b>：背包内<b>所有</b>法杖按各自间隔（默认 5s / 潮汐 3s）
 *       补 10 × 恢复效率（每源独立夹到各自容量，定案 §0-7 / §10.4）；</li>
 *   <li><b>离线清理</b>：施法链接与连击账本的兜底回收（防泄漏）。</li>
 * </ol>
 */
public final class WandSettlement {

    /** 结算节拍：20 tick = 1 秒 */
    private static final int INTERVAL_TICKS = 20;

    private WandSettlement() {
    }

    /** 由 {@code AkaishiForbiddenMod.init()} 调用 */
    public static void register() {
        TickEvent.SERVER_LEVEL_POST.register(WandSettlement::serverTick);
    }

    private static void serverTick(ServerLevel level) {
        if (level.getGameTime() % INTERVAL_TICKS != 0L) {
            return;
        }
        long now = level.getGameTime();
        for (ServerPlayer player : level.players()) {
            try {
                settleOne(player, now);
            } catch (RuntimeException e) {
                org.slf4j.LoggerFactory.getLogger(WandSettlement.class)
                        .error("[AkaishiMagic] 法杖恢复结算失败，玩家={}", player.getUUID(), e);
            }
        }
        // 离线清理（杖的账本跟着人走，人走了就回收）
        MinecraftServer server = level.getServer();
        WandSessionLinks.cleanup(server);
        WandComboTracker.cleanupOffline(server);
    }

    private static void settleOne(ServerPlayer player, long now) {
        MagicState state = MagicStates.state(player);
        List<ItemStack> staffs = collectStaffs(player);

        // ① 过热阈值：主/副手法杖的乘子（多持相乘；无杖回 1）
        float thresholdMul = 1f;
        for (ItemStack held : List.of(player.getMainHandItem(), player.getOffhandItem())) {
            if (held.getItem() instanceof WandItem) {
                thresholdMul *= WandModifiers.resolve(held, MagicSource.DEFAULT, List.of(), 0f, 1)
                        .heatThresholdMul();
            }
        }
        if (state != null) {
            state.setHeatThresholdMul(thresholdMul);
        }

        // ② 额外池恢复：背包内所有杖（含主副手）各自按自己的节拍走
        float mainCap = state == null ? MagicState.BASE_POOL_CAPACITY : state.poolCapacity();
        for (ItemStack wand : staffs) {
            regenExtras(wand, now, mainCap);
        }
    }

    /** 收集背包里全部法杖（覆盖 Inventory 全部槽位：主物品栏 + 盔甲 + 副手） */
    private static List<ItemStack> collectStaffs(ServerPlayer player) {
        List<ItemStack> staffs = new ArrayList<>(2);
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof WandItem) {
                staffs.add(stack);
            }
        }
        return staffs;
    }

    /** 一根杖的额外池恢复（首次见到排程；到点给四源各补一次并排下一轮） */
    private static void regenExtras(ItemStack wand, long now, float mainPoolCapacity) {
        WandModifiers mods = WandModifiers.resolve(wand, MagicSource.DEFAULT, List.of(), 0f, 1);
        long intervalTicks = Math.max(1L, (long) (mods.extraRegenInterval() * 20f));
        for (MagicSource source : MagicSource.values()) {
            float cap = WandModifiers.extraPoolCapacity(mods.parts(), mainPoolCapacity, source);
            float balance = WandItem.extra(wand, source);
            WandItem.setExtra(wand, source,
                    Float.isFinite(balance) ? Math.max(0f, Math.min(cap, balance)) : 0f);
        }
        long at = WandItem.extraRegenAt(wand);
        if (at == 0L) {
            WandItem.setExtraRegenAt(wand, now + intervalTicks);
            return;
        }
        if (now < at) {
            return;
        }
        for (MagicSource source : MagicSource.values()) {
            float cap = WandModifiers.extraPoolCapacity(mods.parts(), mainPoolCapacity, source);
            float current = WandItem.extra(wand, source);
            current = Float.isFinite(current) ? Math.max(0f, Math.min(cap, current)) : 0f;
            float amount = Math.max(0f, WandModifiers.extraRegenAmount(mods.parts(), source));
            WandItem.setExtra(wand, source, Math.min(cap, current + amount));
        }
        WandItem.setExtraRegenAt(wand, now + intervalTicks);
    }
}
