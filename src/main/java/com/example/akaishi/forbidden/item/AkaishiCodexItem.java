package com.example.akaishi.forbidden.item;

import com.example.akaishi.forbidden.menu.AkaishiCodexMenu;

import dev.architectury.registry.menu.ExtendedMenuProvider;
import dev.architectury.registry.menu.MenuRegistry;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 禁忌秘典：手持右键打开秘典界面（不走 Patchouli —— 用户明确要求，秘典是自研知识/研究系统）。
 *
 * <p><b>为什么不做成方块</b>：秘典的语义是"随身带的一本书"，玩家应当在任何地方翻开它；
 * 做成方块会把"研读"绑定到固定地点，与"随时被一段描述击中"的叙事相冲。
 * 因此界面形态沿用本项目既有的"便携物品右键开界面"（见 {@code AkaishiWirelessPortableTerminalItem}）。
 *
 * <p><b>打开动作只在服务端执行</b>：客户端只收到原版"打开界面"包，
 * 界面数据由服务端推快照（见 {@code AkaishiCodexMenu}），客户端无从伪造进度。
 */
public class AkaishiCodexItem extends Item {

    public AkaishiCodexItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            MenuRegistry.openExtendedMenu(serverPlayer, new ExtendedMenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("item.forbidden_hex.forbidden_hex_forbidden_codex");
                }

                @Override
                public void saveExtraData(FriendlyByteBuf buf) {
                    // 无额外数据：菜单直接绑玩家（进度由服务端快照下发，不随打开包携带）
                }

                @Override
                public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                    return new AkaishiCodexMenu(id, inv);
                }
            });
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.forbidden_hex.forbidden_hex_forbidden_codex.hint"));
    }
}
