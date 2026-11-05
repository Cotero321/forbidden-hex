package com.example.akaishi.forbidden.brewing.client;

import com.example.akaishi.forbidden.brewing.BrewingBlocks;
import com.example.akaishi.forbidden.brewing.BrewingEntities;
import com.example.akaishi.forbidden.brewing.BrewingFumeItem;
import com.example.akaishi.forbidden.brewing.BrewingFumes;
import com.example.akaishi.forbidden.brewing.BrewingItems;
import com.example.akaishi.forbidden.brewing.BrewingMenus;
import com.example.akaishi.forbidden.brewing.CrucibleBlockEntity;
import dev.architectury.registry.menu.MenuRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;

public final class BrewingClientSetup {
    private BrewingClientSetup() { }

    public static void register() {
        MenuRegistry.registerScreenFactory(BrewingMenus.OVEN.get(), WitchOvenScreen::new);
        MenuRegistry.registerScreenFactory(BrewingMenus.CRUCIBLE.get(), CrucibleScreen::new);
        MenuRegistry.registerScreenFactory(com.example.akaishi.forbidden.alchemy.AlchemyContent.MENU.get(),
                com.example.akaishi.forbidden.alchemy.client.AlchemyScreen::new);
        net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(
                com.example.akaishi.forbidden.alchemy.AlchemyContent.MACHINE_ENTITY.get(),
                com.example.akaishi.forbidden.alchemy.client.AlchemyMachineRenderer::new);
        EntityRenderers.register(BrewingEntities.POTION.get(), BrewingPotionRenderer::new);
        EntityRenderers.register(BrewingEntities.CLOUD.get(), BrewingCloudRenderer::new);
        Minecraft.getInstance().getItemColors().register((stack, tint) -> tint == 0
                ? com.example.akaishi.forbidden.brewing.BrewingPotionData.read(stack)
                    .map(com.example.akaishi.forbidden.brewing.BrewingPotionData::color).orElse(0x3F76E4)
                : 0xFFFFFF, com.example.akaishi.forbidden.brewing.BrewingItems.POTION.get());
        Minecraft.getInstance().getItemColors().register((stack, tint) -> tint == 1
                ? BrewingFumes.color(BrewingFumeItem.kind(stack))
                : 0xFFFFFF, BrewingItems.FUME.get());
        ItemProperties.register(BrewingItems.FUME.get(), new ResourceLocation("forbidden_hex", "refined_fume"),
                (stack, level, entity, seed) -> {
                    ResourceLocation kind = BrewingFumeItem.kind(stack);
                    return kind != null && BrewingFumes.REFINED.contains(kind) ? 1F : 0F;
                });
        ItemProperties.register(BrewingItems.FUME.get(), new ResourceLocation("forbidden_hex", "fume_kind"),
                (stack, level, entity, seed) -> {
                    ResourceLocation kind = BrewingFumeItem.kind(stack);
                    if (!BrewingFumes.isAllowed(kind)) return 0.0F;
                    int index = BrewingFumes.EVIL_BREATH.equals(kind)
                            ? BrewingFumes.NON_EVIL.size() : BrewingFumes.NON_EVIL.indexOf(kind);
                    return (index + 1) / 32.0F;
                });
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
