package com.example.akaishi.forbidden.brewing.client;

import com.example.akaishi.forbidden.brewing.BrewingBlocks;
import com.example.akaishi.forbidden.brewing.BrewingEntities;
import com.example.akaishi.forbidden.brewing.BrewingMenus;
import com.example.akaishi.forbidden.brewing.CrucibleBlockEntity;
import dev.architectury.registry.menu.MenuRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderers;

public final class BrewingClientSetup {
    private BrewingClientSetup() { }

    public static void register() {
        MenuRegistry.registerScreenFactory(BrewingMenus.OVEN.get(), WitchOvenScreen::new);
        EntityRenderers.register(BrewingEntities.POTION.get(), BrewingPotionRenderer::new);
        EntityRenderers.register(BrewingEntities.CLOUD.get(), BrewingCloudRenderer::new);
        Minecraft.getInstance().getItemColors().register((stack, tint) -> tint == 0
                ? com.example.akaishi.forbidden.brewing.BrewingPotionData.read(stack)
                    .map(data -> data.effects().get(0).getEffect().getColor()).orElse(0x3F76E4)
                : 0xFFFFFF, com.example.akaishi.forbidden.brewing.BrewingItems.POTION.get());
        Minecraft.getInstance().getItemColors().register((stack, tint) -> tint == 0
                ? com.example.akaishi.forbidden.brewing.BrewingFumes.color(com.example.akaishi.forbidden.brewing.BrewingFumeItem.kind(stack))
                : 0xFFFFFF, com.example.akaishi.forbidden.brewing.BrewingItems.FUME.get());
        for (String herb : com.example.akaishi.forbidden.brewing.BrewingPlants.HERBS) {
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                    com.example.akaishi.forbidden.brewing.BrewingPlants.crop(herb), net.minecraft.client.renderer.RenderType.cutout());
        }
        for (String tree : com.example.akaishi.forbidden.brewing.BrewingPlants.TREES) {
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                    com.example.akaishi.forbidden.brewing.BrewingPlants.sapling(tree), net.minecraft.client.renderer.RenderType.cutout());
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                    com.example.akaishi.forbidden.brewing.BrewingPlants.leaves().get(tree).get(), net.minecraft.client.renderer.RenderType.cutoutMipped());
        }
        Minecraft.getInstance().getBlockColors().register((state, level, pos, tint) ->
                level != null && pos != null && level.getBlockEntity(pos) instanceof CrucibleBlockEntity pot
                        ? pot.color() : 0x3F76E4, BrewingBlocks.CRUCIBLE.get());
    }
}
