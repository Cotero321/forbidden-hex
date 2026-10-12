package com.example.akaishi.forbidden.brewing.client;

import com.example.akaishi.forbidden.brewing.BrewingBlocks;
import com.example.akaishi.forbidden.brewing.BrewingEntities;
import com.example.akaishi.forbidden.brewing.BrewingMenus;
import com.example.akaishi.forbidden.brewing.CrucibleBlockEntity;
import dev.architectury.registry.menu.MenuRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

public final class BrewingClientSetup {
    private BrewingClientSetup() { }

    public static void register() {
        MenuRegistry.registerScreenFactory(BrewingMenus.OVEN.get(), WitchOvenScreen::new);
        EntityRenderers.register(BrewingEntities.POTION.get(), ThrownItemRenderer::new);
        EntityRenderers.register(BrewingEntities.CLOUD.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        Minecraft.getInstance().getItemColors().register((stack, tint) -> tint == 0
                ? com.example.akaishi.forbidden.brewing.BrewingPotionData.read(stack)
                    .map(data -> data.effects().get(0).getEffect().getColor()).orElse(0x3F76E4)
                : 0xFFFFFF, com.example.akaishi.forbidden.brewing.BrewingItems.POTION.get());
        Minecraft.getInstance().getBlockColors().register((state, level, pos, tint) ->
                level != null && pos != null && level.getBlockEntity(pos) instanceof CrucibleBlockEntity pot
                        ? pot.color() : 0x3F76E4, BrewingBlocks.CRUCIBLE.get());
    }
}
