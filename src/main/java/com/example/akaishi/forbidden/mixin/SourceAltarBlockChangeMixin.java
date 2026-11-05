package com.example.akaishi.forbidden.mixin;

import com.example.akaishi.forbidden.altar.SourceEnergyNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class SourceAltarBlockChangeMixin {
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void forbidden_hex$blockChanged(BlockPos pos, BlockState state, boolean moving,
                                            CallbackInfoReturnable<BlockState> callback) {
        BlockState previous = callback.getReturnValue();
        if (previous != null && ((LevelChunk) (Object) this).getLevel() instanceof ServerLevel level)
            SourceEnergyNetwork.blockChanged(level, pos, previous, state);
    }
}
