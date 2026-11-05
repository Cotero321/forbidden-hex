package com.example.akaishi.forbidden.altar;

import com.example.akaishi.forbidden.block.entity.AkaishiForbiddenBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class OfferingPedestalBlockEntity extends BlockEntity {
    private ItemStack display = ItemStack.EMPTY;
    public OfferingPedestalBlockEntity(BlockPos pos, BlockState state) { super(AkaishiForbiddenBlockEntities.OFFERING_PEDESTAL.get(), pos, state); }
    public ItemStack display() { return display.copy(); }
    public boolean putDisplay(ItemStack stack) {
        if (level == null || level.isClientSide || !display.isEmpty() || SourceAltarRules.offeringHalves(stack) == 0) return false;
        display = stack.copyWithCount(1);
        changed();
        return true;
    }
    public ItemStack takeDisplay() {
        if (display.isEmpty()) return ItemStack.EMPTY;
        ItemStack taken = display;
        display = ItemStack.EMPTY;
        changed();
        return taken;
    }
    private void changed() {
        setChanged();
        if (level instanceof ServerLevel server) {
            server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            SourceEnergyNetwork.invalidateNear(server, worldPosition);
        }
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!display.isEmpty()) tag.put("Offering", display.save(new CompoundTag()));
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        display = ItemStack.of(tag.getCompound("Offering"));
        if (SourceAltarRules.offeringHalves(display) == 0) display = ItemStack.EMPTY;
        else display.setCount(1);
        if (level instanceof ServerLevel server) SourceEnergyNetwork.invalidateNear(server, worldPosition);
    }
    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
