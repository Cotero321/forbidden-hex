package com.example.akaishi.forbidden.wand;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.item.MagicCoreItem;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>咒核镶嵌按键</b>（TC4 法术焦点式，Owner 2026-10-06 定）：手持法杖按绑定键（默认 R），
 * 直接把背包里的咒核装上 / 轮换 —— **装卸免费**（定案 3），不走铭文台。
 *
 * <p><b>轮换口径（TC4 foci 式）</b>：按背包槽位序收集全部咒核；
 * 已装核 ⇒ 装"当前核的下一个"，两核**互换位置**（新核进杖、旧核回它槽位）；无核 ⇒ 装"第一个"。
 * 背包无咒核只提示不动作。
 */
public final class WandSocketC2S {

    /** C2S：镶嵌/轮换请求（零载荷 —— 手持杖由服务端自选主手优先） */
    public static final ResourceLocation CHANNEL =
            new ResourceLocation(AkaishiMod.MOD_ID, "wand_socket");

    private WandSocketC2S() {
    }

    /** 键名提示提供者：forge 客户端 setup 注入真实键位显示；默认退通用键名（服务端 / 未注入） */
    private static volatile java.util.function.Supplier<Component> keyHint =
            () -> Component.translatable("key.forbidden_hex.wand_socket");

    /** forge 客户端 setup 注入真实键位显示（跨层解耦：common 不引客户端类） */
    public static void installKeyHint(java.util.function.Supplier<Component> provider) {
        if (provider != null) keyHint = provider;
    }

    /** 按键名（tooltip 提示用） */
    public static Component keybindHint() {
        return keyHint.get();
    }

    /** 服务端注册（AkaishiForbiddenMod.init 调用） */
    public static void register() {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, CHANNEL, (buf, context) ->
                context.queue(() -> {
                    if (context.getPlayer() instanceof ServerPlayer player) {
                        socketHeld(player);
                    }
                }));
    }

    /** 客户端：按键回调里发一次请求 */
    public static void sendSocketRequest() {
        NetworkManager.sendToServer(CHANNEL, new FriendlyByteBuf(Unpooled.buffer()));
    }

    // ==================== 服务端轮换 ====================

    private static void socketHeld(ServerPlayer player) {
        ItemStack wand = heldWand(player);
        if (wand.isEmpty()) {
            feedback(player, "no_wand");
            return;
        }
        // 背包槽位序收集全部咒核
        List<Integer> coreSlots = new ArrayList<>();
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).getItem() instanceof MagicCoreItem) {
                coreSlots.add(i);
            }
        }
        if (coreSlots.isEmpty()) {
            feedback(player, "no_core");
            return;
        }
        ItemStack current = WandItem.spell(wand);
        // 已装核 ⇒ 找它的下一个；否则装第一个
        int pickIndex = 0;
        if (!current.isEmpty()) {
            for (int i = 0; i < coreSlots.size(); i++) {
                if (ItemStack.isSameItemSameTags(current, inventory.getItem(coreSlots.get(i)))) {
                    pickIndex = (i + 1) % coreSlots.size();
                    break;
                }
            }
            // 找不到相同核（理论上不会）：也装第一个
        }
        int pickedSlot = coreSlots.get(pickIndex);
        ItemStack picked = inventory.getItem(pickedSlot);
        // 互换：新核进杖，旧核回新核的槽位（装卸免费、双向零成本）
        WandItem.setSpell(wand, picked);
        inventory.setItem(pickedSlot, current);
        player.getInventory().setChanged();
        feedback(player, current.isEmpty() ? "socketed" : "swapped");
    }

    /** 手持法杖：主手优先，其次副手 */
    private static ItemStack heldWand(ServerPlayer player) {
        if (player.getMainHandItem().getItem() instanceof WandItem) {
            return player.getMainHandItem();
        }
        if (player.getOffhandItem().getItem() instanceof WandItem) {
            return player.getOffhandItem();
        }
        return ItemStack.EMPTY;
    }

    private static void feedback(Player player, String key) {
        player.displayClientMessage(
                Component.translatable("gui.forbidden_hex.wand.socket." + key).withStyle(ChatFormatting.GRAY), true);
    }
}
