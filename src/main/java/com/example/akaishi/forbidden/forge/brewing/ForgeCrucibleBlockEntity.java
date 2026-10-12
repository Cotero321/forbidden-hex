package com.example.akaishi.forbidden.forge.brewing;

import com.example.akaishi.forbidden.brewing.CrucibleBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

public final class ForgeCrucibleBlockEntity extends CrucibleBlockEntity {
    private LazyOptional<IFluidHandler> fluid = createFluid();

    public ForgeCrucibleBlockEntity(BlockPos pos, BlockState state) { super(pos, state); }

    private LazyOptional<IFluidHandler> createFluid() {
        return LazyOptional.of(() -> new IFluidHandler() {
            @Override public int getTanks() { return 1; }
            @Override public FluidStack getFluidInTank(int tank) {
                return tank == 0 && water() > 0 ? new FluidStack(Fluids.WATER, water()) : FluidStack.EMPTY;
            }
            @Override public int getTankCapacity(int tank) { return tank == 0 ? WATER_CAPACITY : 0; }
            @Override public boolean isFluidValid(int tank, FluidStack stack) { return tank == 0 && stack.getFluid() == Fluids.WATER; }
            @Override public int fill(FluidStack stack, FluidAction action) {
                return stack.getFluid() == Fluids.WATER ? fillWater(stack.getAmount(), action.execute()) : 0;
            }
            @Override public FluidStack drain(FluidStack stack, FluidAction action) {
                return stack.getFluid() == Fluids.WATER ? drain(stack.getAmount(), action) : FluidStack.EMPTY;
            }
            @Override public FluidStack drain(int amount, FluidAction action) {
                int drained = drainWater(amount, action.execute());
                return drained == 0 ? FluidStack.EMPTY : new FluidStack(Fluids.WATER, drained);
            }
        });
    }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
        return !isRemoved() && capability == ForgeCapabilities.FLUID_HANDLER ? fluid.cast() : super.getCapability(capability, side);
    }
    @Override public void invalidateCaps() { super.invalidateCaps(); fluid.invalidate(); }
    @Override public void reviveCaps() { super.reviveCaps(); fluid = createFluid(); }
}
