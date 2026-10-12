package com.example.akaishi.forbidden.menu;

import com.example.akaishi.forbidden.block.entity.AkaishiMotherAltarBlockEntity;
import com.example.akaishi.util.LongDataSlots;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 合并母神祭坛界面：仅展示当前结构等级 + 一个供奉槽（上限 1 个物品）。
 * 供奉槽由方块实体提供（{@code altarSlot()}），是 {@code offering} 字段的只读视图，
 * 避免界面与悬浮渲染各自持有一份状态导致不同步。
 */
public class AkaishiMotherAltarMenu extends AbstractContainerMenu {

    /** 合并祭坛唯一供奉槽（GUI 内坐标，居中） */
    public static final int OFFERING_SLOT_X = 79;
    public static final int OFFERING_SLOT_Y = 28;

    private final ContainerData data;

    public AkaishiMotherAltarMenu(int id, Inventory playerInv, Container altarSlot, ContainerData data) {
        super(AkaishiForbiddenMenuRegs.CHISHI_MOTHER_ALTAR.get(), id);
        this.data = data;

        // 供奉槽：任意物品均可放入，但槽位上限为 1（由容器 getMaxStackSize 约束）
        this.addSlot(new Slot(altarSlot, 0, OFFERING_SLOT_X, OFFERING_SLOT_Y));

        // 玩家背包 3 行 × 9
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        // 快捷栏
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
        }
        this.addDataSlots(data);
    }

    /** 当前结构等级：0=未成型，1/2/3=对应等级 */
    public int getTier() {
        return data.get(AkaishiMotherAltarBlockEntity.DATA_TIER);
    }

    /** 当前仪式进度：跨 4 槽重组 64 位（旧配方上限 80K、新配方 800K，仍按 long 读取以留扩展余地） */
    public long getProgress() {
        return LongDataSlots.read(data,
                AkaishiMotherAltarBlockEntity.DATA_PROGRESS,
                AkaishiMotherAltarBlockEntity.DATA_PROGRESS + 1,
                AkaishiMotherAltarBlockEntity.DATA_PROGRESS + 2,
                AkaishiMotherAltarBlockEntity.DATA_PROGRESS + 3);
    }

    /** 仪式进度上限：由方块实体按当前匹配配方同步（旧 80K / 新 800K） */
    public long getProgressMax() {
        return Integer.toUnsignedLong(data.get(AkaishiMotherAltarBlockEntity.DATA_PROGRESS_MAX));
    }

    /**
     * 祭品配方是否齐备（= 真正在合成）。
     * 未齐备（含供奉槽为空）时界面不显示所需能量与进度条。
     */
    public boolean isRecipeReady() {
        return data.get(AkaishiMotherAltarBlockEntity.DATA_READY) != 0;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index == 0) {
                // 供奉槽 → 玩家背包
                if (!this.moveItemStackTo(stack, 1, 37, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stack, 0, 1, false)) {
                // 背包物品 → 供奉槽（槽满则不移动）
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (stack.getCount() == result.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, stack);
        }
        return result;
    }

    /** 供无方块实体兜底时使用的空菜单（等级 0、空供奉槽） */
    public static AkaishiMotherAltarMenu emptyMenu(int id, Inventory inv) {
        return new AkaishiMotherAltarMenu(id, inv,
                new SimpleContainer(1),
                new SimpleContainerData(AkaishiMotherAltarBlockEntity.DATA_SLOTS));
    }
}
