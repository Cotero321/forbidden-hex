package com.example.akaishi.forbidden.forge.brewing;

import com.example.akaishi.forbidden.brewing.BrewingWorldRestoration;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "forbidden_hex")
public final class ForgeBrewingWorldEvents {
    private ForgeBrewingWorldEvents() { }

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) {
            BrewingWorldRestoration.get(level).tick(level);
            com.example.akaishi.forbidden.brewing.BrewingGravityFields.get(level).tick(level);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void broken(BlockEvent.BreakEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof ServerLevel level) BrewingWorldRestoration.get(level).forget(event.getPos());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void placed(BlockEvent.EntityPlaceEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof ServerLevel level) BrewingWorldRestoration.get(level).forget(event.getPos());
    }
}
