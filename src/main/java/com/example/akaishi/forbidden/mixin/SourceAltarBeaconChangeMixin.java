package com.example.akaishi.forbidden.mixin;

import com.example.akaishi.forbidden.altar.SourceEnergyNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BeaconBlockEntity.class)
public abstract class SourceAltarBeaconChangeMixin {
    @Unique private boolean forbidden_hex$wasActive;

    @Inject(method = "tick", at = @At("TAIL"))
    private static void forbidden_hex$activationChanged(Level level, BlockPos pos, BlockState state,
                                                        BeaconBlockEntity beacon, CallbackInfo callback) {
        if (!(level instanceof ServerLevel server)) return;
        SourceAltarBeaconChangeMixin tracker = (SourceAltarBeaconChangeMixin) (Object) beacon;
        boolean active = !beacon.getBeamSections().isEmpty();
        if (tracker.forbidden_hex$wasActive != active) {
            tracker.forbidden_hex$wasActive = active;
            SourceEnergyNetwork.blockChanged(server, pos);
        }
    }
}
