package com.example.akaishi.forbidden.forge.altar;

import com.example.akaishi.forbidden.AkaishiForbiddenMod;
import com.example.akaishi.forbidden.altar.OfferingPedestalBlock;
import com.example.akaishi.forbidden.altar.SourceAltarBlock;
import com.example.akaishi.forbidden.altar.SourceEnergyNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AkaishiForbiddenMod.MOD_ID)
public final class SourceEnergyEvents {
    private SourceEnergyEvents() { }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) SourceEnergyNetwork.tick(level);
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) SourceEnergyNetwork.unload(level);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { SourceEnergyNetwork.clear(); }
    @SubscribeEvent public static void chunkLoaded(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            var pos = event.getChunk().getPos();
            SourceEnergyNetwork.chunkChanged(level, pos.x, pos.z, true);
        }
    }
    @SubscribeEvent public static void chunkUnloaded(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            var pos = event.getChunk().getPos();
            SourceEnergyNetwork.chunkChanged(level, pos.x, pos.z, false);
        }
    }

    @SubscribeEvent public static void rightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || event.getUseBlock() == Event.Result.DENY || !event.getEntity().isShiftKeyDown()) return;
        var level = event.getLevel();
        var pos = event.getPos();
        if (!level.hasChunkAt(pos)) return;
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SourceAltarBlock) && !(state.getBlock() instanceof OfferingPedestalBlock)) return;
        if (!event.getEntity().mayBuild() || !level.mayInteract(event.getEntity(), pos)) return;
        BlockHitResult hit = event.getHitVec();
        InteractionResult result = state.use(level, event.getEntity(), event.getHand(), hit);
        event.setCancellationResult(result);
        event.setCanceled(true);
    }
}
