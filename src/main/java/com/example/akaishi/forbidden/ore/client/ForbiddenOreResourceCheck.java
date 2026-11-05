package com.example.akaishi.forbidden.ore.client;

import com.example.akaishi.forbidden.ore.ForbiddenOres;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.io.IOException;
import java.util.List;

public final class ForbiddenOreResourceCheck {
    private ForbiddenOreResourceCheck() { }

    public static void verify(Minecraft minecraft) throws IOException {
        List<ItemLike> blocks = List.of(ForbiddenOres.CINNABAR_ORE.get(), ForbiddenOres.DEEPSLATE_CINNABAR_ORE.get(),
                ForbiddenOres.SULFUR_ORE.get(), ForbiddenOres.DEEPSLATE_SULFUR_ORE.get(),
                ForbiddenOres.SILVER_ORE.get(), ForbiddenOres.DEEPSLATE_SILVER_ORE.get(),
                ForbiddenOres.SILVER_BLOCK.get(), ForbiddenOres.RAW_SILVER_BLOCK.get());
        List<ItemLike> materials = List.of(ForbiddenOres.CINNABAR_FRAGMENT.get(), ForbiddenOres.SULFUR_FRAGMENT.get(),
                ForbiddenOres.RAW_SILVER.get(), ForbiddenOres.SILVER_INGOT.get(), ForbiddenOres.SILVER_NUGGET.get());
        for (ItemLike block : blocks) verifyItem(minecraft, block, "block");
        for (ItemLike material : materials) verifyItem(minecraft, material, "item");
    }

    private static void verifyItem(Minecraft minecraft, ItemLike item, String folder) throws IOException {
        ItemStack stack = new ItemStack(item);
        String key = stack.getDescriptionId();
        if (!I18n.exists(key)) throw new IllegalStateException("Missing mineral translation: " + key);
        String path = key.substring(key.lastIndexOf('.') + 1);
        ResourceLocation sprite = new ResourceLocation("forbidden_hex", folder + "/" + path);
        var model = minecraft.getItemRenderer().getModel(stack, null, null, 0);
        if (model == minecraft.getModelManager().getMissingModel()
                || !sprite.equals(model.getParticleIcon().contents().name())) {
            throw new IllegalStateException("Missing mineral model or texture: " + path);
        }
        ResourceLocation texture = new ResourceLocation("forbidden_hex", "textures/" + folder + "/" + path + ".png");
        try (var input = minecraft.getResourceManager().getResourceOrThrow(texture).open();
             NativeImage image = NativeImage.read(input)) {
            if (image.getWidth() != 16 || image.getHeight() != 16) {
                throw new IllegalStateException("Mineral textures must be 16x16: " + path);
            }
        }
    }
}
