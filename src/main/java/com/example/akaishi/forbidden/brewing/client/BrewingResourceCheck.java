package com.example.akaishi.forbidden.brewing.client;

import com.example.akaishi.forbidden.brewing.BrewingEffectType;
import com.example.akaishi.forbidden.brewing.BrewingFumeItem;
import com.example.akaishi.forbidden.brewing.BrewingFumes;
import com.example.akaishi.forbidden.brewing.BrewingItems;
import com.example.akaishi.forbidden.brewing.BrewingStatusEffects;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.util.ArrayList;

public final class BrewingResourceCheck {
    private BrewingResourceCheck() { }

    public static void verify(Minecraft minecraft) throws IOException {
        verifyFumeBottle(minecraft);
        for (BrewingEffectType type : BrewingEffectType.values()) {
            if (!I18n.exists(type.translationKey()) || !I18n.exists(type.translationKey() + ".description")) {
                throw new IllegalStateException("Missing brewing effect translation: " + type.id());
            }
            ResourceLocation icon = new ResourceLocation("forbidden_hex", "textures/mob_effect/" + type.id().getPath() + ".png");
            var resource = minecraft.getResourceManager().getResourceOrThrow(icon);
            try (var input = resource.open(); NativeImage image = NativeImage.read(input)) {
                if (image.getWidth() != 18 || image.getHeight() != 18) {
                    throw new IllegalStateException("Invalid brewing HUD icon dimensions: " + icon);
                }
            }
            if (!type.instantaneous() && BrewingStatusEffects.get(type) == null) {
                throw new IllegalStateException("Missing brewing effect registration: " + type.id());
            }
        }
        for (var type : java.util.List.of(
                com.example.akaishi.forbidden.entity.AkaishiForbiddenEntities.SHADOW.get(),
                com.example.akaishi.forbidden.entity.AkaishiForbiddenEntities.AGAITOLOS.get())) {
            var renderer = minecraft.getEntityRenderDispatcher().renderers.get(type);
            if (!(renderer instanceof software.bernie.geckolib.renderer.GeoEntityRenderer<?> geo)
                    || geo.getRenderLayers().stream().noneMatch(layer -> layer instanceof BrewingGeoTintLayer<?>)) {
                throw new IllegalStateException("Missing brewing Geo tint layer: " + type);
            }
        }
    }

    private static void verifyFumeBottle(Minecraft minecraft) {
        var kinds = new ArrayList<>(BrewingFumes.NON_EVIL);
        kinds.add(BrewingFumes.EVIL_BREATH);
        kinds.addAll(BrewingFumes.REFINED);
        for (ResourceLocation kind : kinds) {
            verifyFumeModel(minecraft, BrewingFumeItem.create(kind), kind.getPath());
        }
        verifyFumeModel(minecraft, new ItemStack(BrewingItems.FUME.get()), "default");
        for (String value : java.util.List.of("invalid id!", "forbidden_hex:unknown_breath")) {
            var invalid = new ItemStack(BrewingItems.FUME.get());
            invalid.getOrCreateTag().putString(BrewingFumeItem.FUME_ID, value);
            verifyFumeModel(minecraft, invalid, "default");
        }
    }

    private static void verifyFumeModel(Minecraft minecraft, ItemStack stack, String markName) {
        ResourceLocation glass = new ResourceLocation("minecraft", "item/glass_bottle");
        ResourceLocation gas = new ResourceLocation("forbidden_hex", "item/forbidden_hex_fume");
        ResourceLocation kind = BrewingFumeItem.kind(stack);
        ResourceLocation mark = new ResourceLocation("forbidden_hex", kind != null && BrewingFumes.REFINED.contains(kind)
                ? "item/forbidden_hex_refined_fume_mark" : "item/fume_marks/" + markName);
        var quads = minecraft.getItemRenderer().getModel(stack, null, null, 0)
                .getQuads(null, null, RandomSource.create(0L));
        if (quads.stream().noneMatch(quad -> quad.getTintIndex() == 0 && glass.equals(quad.getSprite().contents().name()))
                || quads.stream().noneMatch(quad -> quad.getTintIndex() == 1 && gas.equals(quad.getSprite().contents().name()))
                || quads.stream().noneMatch(quad -> quad.getTintIndex() == 2 && mark.equals(quad.getSprite().contents().name()))) {
            throw new IllegalStateException("Missing vanilla fume bottle, gas or specific mark: " + markName);
        }
        if (minecraft.getItemColors().getColor(stack, 0) != 0xFFFFFF
                || minecraft.getItemColors().getColor(stack, 1) != BrewingFumes.color(BrewingFumeItem.kind(stack))
                || minecraft.getItemColors().getColor(stack, 2) != 0xFFFFFF) {
            throw new IllegalStateException("Fume tint must only affect gas, not glass or marks: " + markName);
        }
    }
}
