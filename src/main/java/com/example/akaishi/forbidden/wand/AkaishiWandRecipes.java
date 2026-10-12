package com.example.akaishi.forbidden.wand;

import com.example.akaishi.AkaishiMod;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;

/**
 * 法杖配方的序列化器注册（{@code forbidden_hex:wand_assembly}）。
 *
 * <p>配方本体 {@link WandAssemblyRecipe} 无字段：数据包侧只需要一份
 * {@code data/forbidden_hex/recipes/forbidden_hex_wand.json}（内容 {@code {"type":"forbidden_hex:wand_assembly"}}）
 * 把它挂进配方管理器，匹配与产物全在代码里（三段组合 → NBT 定型，json 表达不了）。
 */
public final class AkaishiWandRecipes {

    public static RegistrySupplier<RecipeSerializer<WandAssemblyRecipe>> ASSEMBLY;

    private AkaishiWandRecipes() {
    }

    /** 由 {@code AkaishiForbiddenMod.init()} 调用（注册表冻结前） */
    public static void register() {
        ASSEMBLY = RegistrarManager.get(AkaishiMod.MOD_ID).get(Registries.RECIPE_SERIALIZER)
                .register(new ResourceLocation(AkaishiMod.MOD_ID, "wand_assembly"),
                        WandAssemblyRecipe.Serializer::new);
    }
}
