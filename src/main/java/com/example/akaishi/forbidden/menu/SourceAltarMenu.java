package com.example.akaishi.forbidden.menu;

import com.example.akaishi.forbidden.altar.SourceAltarBlockEntity;
import com.example.akaishi.util.LongDataSlots;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class SourceAltarMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final SourceAltarBlockEntity altar;
    private final ContainerData data;

    public SourceAltarMenu(int id, Inventory inventory, BlockPos pos, SourceAltarBlockEntity altar) {
        super(AkaishiForbiddenMenuRegs.SOURCE_ALTAR.get(), id);
        this.pos = pos;
        this.altar = altar;
        data = !inventory.player.level().isClientSide && altar != null ? altar.data() : new SimpleContainerData(SourceAltarBlockEntity.DATA_COUNT);
        Container offerings = !inventory.player.level().isClientSide && altar != null ? altar.pedestalDisplay() : new SimpleContainer(4);
        for (int i = 0; i < 4; i++) {
            addSlot(new Slot(offerings, i, 74 + i * 22, 136) {
                @Override public boolean mayPlace(ItemStack stack) { return false; }
                @Override public boolean mayPickup(Player player) { return false; }
            });
        }
        addDataSlots(data);
    }
    public int value(int index) { return data.get(index); }
    public long units(int start) { return LongDataSlots.read(data, start, start + 1, start + 2, start + 3); }
    @Override public void clicked(int slot, int button, ClickType type, Player player) { }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64
                && player.level().hasChunkAt(pos)
                && player.level().getBlockEntity(pos) instanceof SourceAltarBlockEntity current && current.isMaster()
                && (player.level().isClientSide || current == altar);
    }
}
