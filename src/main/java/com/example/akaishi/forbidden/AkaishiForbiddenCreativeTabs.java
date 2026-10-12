package com.example.akaishi.forbidden;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.block.AkaishiForbiddenBlocks;
import com.example.akaishi.forbidden.block.AkaishiMotherAltarBlocks;
import com.example.akaishi.forbidden.item.AkaishiCodexItems;
import com.example.akaishi.forbidden.item.AkaishiMagicItems;
import com.example.akaishi.forbidden.item.MagicCoreItem;
import com.example.akaishi.forbidden.magic.table.MagicSource;
import com.example.akaishi.forbidden.item.AkaishiSanityItems;
import com.example.akaishi.forbidden.wand.AkaishiWandItems;

import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.registry.registries.RegistrySupplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/**
 * 创造栏 · 禁忌（P3a 随禁忌秘典从本体 {@code ModCreativeTabs} 迁入 forbidden_hex）。
 *
 * <p><b>为什么整栏跟着迁</b>：秘典物品迁走后，本体不得再引用它（依赖只能单向 禁忌 → 本体），
 * 而"没装禁忌包就没有禁忌栏"比"留一个空栏"更符合分割语义。
 *
 * <p>P3b 起栏内条目全部来自本模块自身注册（理智三件 / 生命融合 / 母神祭坛 / 禁断四件饰品），
 * 不再经本体 {@code ModItems} 引用。
 *
 * <p>栏 id / 标题 lang 键 / 图标与位置与迁前逐字一致：{@code forbidden_hex:forbidden_hex}
 * （命名空间不变）、{@code itemGroup.forbidden_hex.forbidden}、{@code TOP} 行第 4 列。
 */
public final class AkaishiForbiddenCreativeTabs {

    /** 禁忌栏 id（命名空间 akaishi 不变；禁断四件饰品 + 秘典 + 生命融合 + 母神祭坛） */
    public static final String FORBIDDEN_TAB_ID = "forbidden_hex";

    private AkaishiForbiddenCreativeTabs() {
    }

    public static void register() {
        var tabs = RegistrarManager.get(AkaishiMod.MOD_ID).get(Registries.CREATIVE_MODE_TAB);
        tabs.register(new ResourceLocation(AkaishiMod.MOD_ID, FORBIDDEN_TAB_ID),
                () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 4)
                        .title(Component.translatable("itemGroup.forbidden_hex.forbidden"))
                        .icon(() -> new ItemStack(AkaishiCodexItems.forbiddenCodex.get()))
                        .displayItems((params, output) -> addForbiddenItems(output))
                        .build());
    }

    private static void addForbiddenItems(CreativeModeTab.Output output) {
        accept(output, AkaishiCodexItems.forbiddenCodex);
        accept(output, AkaishiForbiddenBlocks.INSCRIPTION_TABLE);
        accept(output, com.example.akaishi.forbidden.brewing.BrewingBlocks.OVEN);
        accept(output, com.example.akaishi.forbidden.brewing.BrewingBlocks.CRUCIBLE);
        accept(output, com.example.akaishi.forbidden.brewing.BrewingItems.WOOD_ASH);
        accept(output, com.example.akaishi.forbidden.brewing.BrewingItems.GRASS_ASH);
        accept(output, com.example.akaishi.forbidden.brewing.BrewingItems.FUME);
        accept(output, com.example.akaishi.forbidden.brewing.BrewingItems.POTION);
        // 母神祭坛体系（P3d 自本体「生命科技」栏改列本禁忌栏）：母神祭坛 / 祭坛石 / 猩红哭泣黑曜石
        accept(output, AkaishiMotherAltarBlocks.CHISHI_MOTHER_ALTAR);
        accept(output, AkaishiMotherAltarBlocks.CHISHI_ALTAR_STONE);
        accept(output, AkaishiMotherAltarBlocks.CRYING_OBSIDIAN_RED);
        // 理智系统·物品与酿造（P3b 自生命科技栏改列本禁忌栏）：两瓶药剂 + 花环饰品
        // （均暂无配方，仅创造栏可得；花环为饰品，药剂走酿造台）
        accept(output, AkaishiSanityItems.sanityTonic);
        accept(output, AkaishiSanityItems.greaterSanityTonic);
        accept(output, com.example.akaishi.forbidden.item.ForbiddenMaterialItems.goldenMelonSlice);
        accept(output, com.example.akaishi.forbidden.item.ForbiddenMaterialItems.sculkLifeform);
        accept(output, com.example.akaishi.forbidden.item.ForbiddenMaterialItems.witherCharm);
        accept(output, AkaishiMotherAltarBlocks.CHISHI_ESSENCE_BLOCK);
        // 魔法侧·法器与墨：魔法毛笔（法器）+ 空瓶（基底容器）+ 11 色墨。
        // 墨的顺序直接沿用物品表自身的插入序（= MagicInkTable 的解锁梯队序 Lv1→Lv6），
        // 故此处不排序、不硬编码墨列表。
        accept(output, AkaishiMagicItems.magicBrush);
        accept(output, AkaishiMagicItems.blankMagicCore);
        for (MagicSource source : MagicSource.values()) output.accept(MagicCoreItem.blank(source));
        accept(output, AkaishiMagicItems.inkBottle);
        AkaishiMagicItems.inks().values().forEach(ink -> accept(output, ink));
        // 魔法侧·法杖域：法杖本体（法术槽空，进生存需在工作台三段组装）+ 68 件模块
        // （端 31 / 柄 20 / 核 17；顺序沿用三表原稿序，此处不排序不硬编码）
        accept(output, AkaishiWandItems.wand);
        AkaishiWandItems.parts().values().forEach(part -> accept(output, part));
    }

    /** 判空后把注册内容（物品/方块，均实现 ItemLike）放入创造标签（注册完成前为 null，防御性跳过） */
    private static void accept(CreativeModeTab.Output output, RegistrySupplier<? extends ItemLike> supplier) {
        if (supplier != null) {
            output.accept(new ItemStack(supplier.get()));
        }
    }
}
