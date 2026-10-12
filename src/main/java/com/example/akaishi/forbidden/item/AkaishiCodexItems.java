package com.example.akaishi.forbidden.item;

import com.example.akaishi.AkaishiMod;

import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.registry.registries.RegistrySupplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

import java.util.function.Supplier;

/**
 * 禁忌秘典域物品注册（本轮只有一件：秘典本体）。
 *
 * <p><b>P3a 归属</b>：本类随禁忌秘典整体迁入 {@code forbidden_hex}（包名与注册 id 均不变）；
 * 注册命名空间仍为本体共用的 {@code forbidden_hex:}，故 id 常量的唯一来源也一并从本体门面迁至此处。
 *
 * <p><b>配方</b>：本轮用户未指定配方，故秘典<b>仅创造栏可得</b>（展示位见 {@code AkaishiForbiddenCreativeTabs} 的禁忌栏）。
 *
 * <p>贴图 {@code forbidden_hex:item/forbidden_hex_forbidden_codex} 由建模侧产出，本类只保证 id 与资源路径严格同名。
 */
public final class AkaishiCodexItems {

    /** 注册 id（命名空间 akaishi 不变；唯一来源） */
    public static final String FORBIDDEN_CODEX_ID = "forbidden_hex_forbidden_codex";

    /** 禁忌秘典（禁忌栏）：右键翻开，入知识/研究系统 */
    public static RegistrySupplier<Item> forbiddenCodex;

    private AkaishiCodexItems() {
    }

    public static void register() {
        // 唯一性物品：秘典记载的是"这个玩家读到了哪一页"，同一本多持无意义，故不堆叠
        forbiddenCodex = item(FORBIDDEN_CODEX_ID, () -> new AkaishiCodexItem(
                new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    }

    /** 注册物品（generated 模型引用 textures/item/&lt;id&gt;.png，与 id 严格同名） */
    private static RegistrySupplier<Item> item(String id, Supplier<Item> factory) {
        return RegistrarManager.get(AkaishiMod.MOD_ID).get(Registries.ITEM)
                .register(new ResourceLocation(AkaishiMod.MOD_ID, id), factory);
    }
}
