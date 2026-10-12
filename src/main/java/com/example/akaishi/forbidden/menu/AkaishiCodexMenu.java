package com.example.akaishi.forbidden.menu;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 禁忌秘典界面（手持物品右键打开）。
 *
 * <p><b>无任何槽位</b>：秘典是便携的"阅读器"，不搬物品，故不带背包区（照
 * {@code AkaishiMiniMatrixNodeMenu} 的形态：空菜单在 {@code AbstractContainerScreen} 下正常渲染）。
 *
 * <p><b>服务端权威</b>：菜单里只有一份"客户端只读进度镜像"（{@link AkaishiCodexSync.NodeView}），
 * 由服务端在打开界面时与每次研究请求处理后各推一次（{@link #broadcastChanges()}）。
 * 不在每 tick 推送 —— 秘典的进度只在玩家点击时变化，逐 tick 发包纯属浪费。
 *
 * <p><b>为什么不做"打开时拉一次"的 C2S</b>：菜单在服务端构造时就知道玩家是谁，
 * 服务端可以直接在第一次 {@code broadcastChanges} 里推快照，省一次往返。
 */
public class AkaishiCodexMenu extends AbstractContainerMenu implements AkaishiCodexSync.Target {

    /**
     * 书体<b>尺寸上限</b>（720×400）。
     *
     * <p><b>本轮语义变更</b>：上一版这是"面板设计基准"，整幅面板会被等比缩放到屏幕
     * （缩放比常是 0.58 这类非整数 ⇒ 字被重采样、观感发虚）。现在<b>不再有任何缩放</b>：
     * 书体尺寸按内容算（见 {@code AkaishiCodexRender#entryBook}/{@code #chartBook}），
     * 屏幕装不下时改为压低高度、由页内滚动消化。这两个常量只作为上限被引用。
     */
    public static final int PANEL_W = 720;
    public static final int PANEL_H = 400;

    private final Player player;

    /** 客户端：整表进度镜像（服务端推送） */
    private List<AkaishiCodexSync.NodeView> nodes = List.of();
    /** 服务端：是否有一份新进度待推送（打开菜单时为 true ⇒ 首帧必推） */
    private boolean snapshotDirty = true;

    public AkaishiCodexMenu(int id, Inventory inv) {
        super(AkaishiCodexMenuRegs.CHISHI_CODEX.get(), id);
        this.player = inv.player;
    }

    /** 服务端：置位待推送（研究请求处理后调用，下一次节拍即下发新进度） */
    public void markSnapshotDirty() {
        this.snapshotDirty = true;
    }

    /** 客户端：整表进度镜像（只读） */
    public List<AkaishiCodexSync.NodeView> nodes() {
        return nodes;
    }

    @Override
    public void acceptCodex(List<AkaishiCodexSync.NodeView> nodeViews) {
        this.nodes = List.copyOf(nodeViews);
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!snapshotDirty) {
            return;
        }
        // 只有"服务端当前打开着的这个菜单"才推：容器已被换成别的界面时推了也没人收
        if (!(player instanceof ServerPlayer serverPlayer) || serverPlayer.containerMenu != this) {
            return;
        }
        snapshotDirty = false;
        AkaishiCodexSync.sendSnapshot(serverPlayer, this.containerId);
    }

    @Override
    public boolean stillValid(Player player) {
        // 手持物品界面：不做距离/方块校验（与便捷终端同口径），换维度/死亡时原版会自动关闭
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY; // 本界面没有任何槽位
    }
}
