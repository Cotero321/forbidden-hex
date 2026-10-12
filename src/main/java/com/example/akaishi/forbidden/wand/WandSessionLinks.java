package com.example.akaishi.forbidden.wand;

import com.example.akaishi.forbidden.magic.table.MagicSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 施法期间「玩家 ↔ 那根杖」的<b>活实例链接</b>。
 *
 * <p><b>为什么存活实例而不是副本</b>：持续段（X3/X7）逐段扣额外池，扣的是
 * <b>玩家背包里那根真杖</b>的 NBT —— 存副本会把账记在影子物上，真杖纹丝不动。
 * 链接的是 {@code use()} 时刻手里的 ItemStack 实例（原版容器移动的是实例而非拷贝），
 * 逐段扣费时在背包里按<b>引用相等</b>找回它；中途换下杖则找不到，扣费优雅跳过额外池
 * （回退主池通道），不吞钱也不刷钱。
 */
public final class WandSessionLinks {

    private record Link(ItemStack wand, MagicSource source, float refundRatio) {
    }

    private static final Map<UUID, Link> LINKS = new ConcurrentHashMap<>();

    private WandSessionLinks() {
    }

    /** 施法受理成功时建立（覆盖旧链接 —— 单施法槽下同一玩家至多一次在施） */
    public static void link(UUID playerId, ItemStack wand, MagicSource source) {
        link(playerId, wand, source,
                WandModifiers.resolve(wand, source, java.util.List.of(), 0f, 1).postCastRefund());
    }

    public static void link(UUID playerId, ItemStack wand, MagicSource source, float refundRatio) {
        LINKS.put(playerId, new Link(wand, source, refundRatio));
    }

    public static ItemStack original(UUID playerId) {
        Link link = LINKS.get(playerId);
        return link == null ? ItemStack.EMPTY : link.wand();
    }

    public static float refundRatio(UUID playerId) {
        Link link = LINKS.get(playerId);
        return link == null ? 0f : link.refundRatio();
    }

    public static void unlink(UUID playerId) {
        LINKS.remove(playerId);
    }

    /** 施法源（循柄返还进对应源的额外池用） */
    public static MagicSource source(UUID playerId) {
        Link link = LINKS.get(playerId);
        return link == null ? MagicSource.DEFAULT : link.source;
    }

    /** 在玩家背包里找回链接中的那根杖（引用相等；找不到返回 EMPTY） */
    public static ItemStack find(Player player) {
        Link link = LINKS.get(player.getUUID());
        if (link == null) {
            return ItemStack.EMPTY;
        }
        ItemStack target = link.wand();
        if (same(target, player.getMainHandItem()) || same(target, player.getOffhandItem())) {
            return target;
        }
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (same(target, inventory.getItem(i))) {
                return target;
            }
        }
        return ItemStack.EMPTY;
    }

    /** 离线清理（结算节拍兜底调用，防泄漏） */
    public static void cleanup(net.minecraft.server.MinecraftServer server) {
        if (LINKS.isEmpty() || server == null) {
            return;
        }
        LINKS.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
    }

    private static boolean same(ItemStack a, ItemStack b) {
        return a == b;
    }
}
