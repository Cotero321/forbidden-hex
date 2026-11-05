package com.example.akaishi.forbidden;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.alchemy.AlchemyContent;
import com.example.akaishi.forbidden.altar.SourceAltarContent;
import com.example.akaishi.forbidden.brewing.BrewingBlocks;
import com.example.akaishi.forbidden.brewing.BrewingItems;
import com.example.akaishi.forbidden.item.ForbiddenMaterialItems;
import com.example.akaishi.forbidden.familiar.FamiliarContent;
import com.example.akaishi.forbidden.familiar.core.FamiliarTemplateId;
import com.example.akaishi.forbidden.familiar.item.FamiliarMaterialItems;
import com.example.akaishi.forbidden.familiar.item.FamiliarTemplateItems;
import com.example.akaishi.forbidden.familiar.workbench.FamiliarWorkbenchContent;
import com.example.akaishi.forbidden.soul.SoulContent;
import com.example.akaishi.forbidden.block.AkaishiForbiddenBlocks;
import com.example.akaishi.forbidden.block.AkaishiMotherAltarBlocks;
import com.example.akaishi.forbidden.item.AkaishiCodexItems;
import com.example.akaishi.forbidden.item.AkaishiMagicItems;
import com.example.akaishi.forbidden.item.MagicCoreItem;
import com.example.akaishi.forbidden.magic.table.MagicSource;
import com.example.akaishi.forbidden.item.AkaishiSanityItems;
import com.example.akaishi.forbidden.ore.ForbiddenOres;
import com.example.akaishi.forbidden.wand.AkaishiWandItems;
import com.example.akaishi.forbidden.transmutation.life_furnace.LifeFurnaceContent;
import com.example.akaishi.forbidden.transmutation.source_furnace.SourceTransmutationFurnaceContent;

import dev.architectury.registry.registries.Registrar;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.registry.registries.RegistrySupplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.function.Consumer;
import java.util.function.Supplier;

public final class AkaishiForbiddenCreativeTabs {

    /** 源能与魔咒栏沿用旧总栏 ID。 */
    public static final String FORBIDDEN_TAB_ID = "forbidden_hex";
    public static final String HERBS_BREWING_TAB_ID = "herbs_brewing";
    public static final String ALCHEMY_CRAFTING_TAB_ID = "alchemy_crafting";
    public static final String EQUIPMENT_TOOLS_TAB_ID = "equipment_tools";
    public static final String RITUALS_TAB_ID = "rituals";
    public static final String MISC_TAB_ID = "misc";

    private AkaishiForbiddenCreativeTabs() {
    }

    public static void register() {
        Registrar<CreativeModeTab> tabs = RegistrarManager.get(AkaishiMod.MOD_ID).get(Registries.CREATIVE_MODE_TAB);
        registerTab(tabs, FORBIDDEN_TAB_ID, "forbidden", CreativeModeTabs.SPAWN_EGGS.location(),
                () -> new ItemStack(AkaishiCodexItems.forbiddenCodex.get()), AkaishiForbiddenCreativeTabs::addSourceMagicItems);
        registerTab(tabs, HERBS_BREWING_TAB_ID, "herbs_brewing", id(FORBIDDEN_TAB_ID),
                () -> new ItemStack(BrewingBlocks.CRUCIBLE.get()), AkaishiForbiddenCreativeTabs::addHerbsBrewingItems);
        registerTab(tabs, ALCHEMY_CRAFTING_TAB_ID, "alchemy_crafting", id(HERBS_BREWING_TAB_ID),
                () -> new ItemStack(AlchemyContent.BLOCKS.get(AlchemyContent.Kind.MANUAL_CRUSHER).get()),
                AkaishiForbiddenCreativeTabs::addAlchemyCraftingItems);
        registerTab(tabs, EQUIPMENT_TOOLS_TAB_ID, "equipment_tools", id(ALCHEMY_CRAFTING_TAB_ID),
                () -> new ItemStack(AkaishiMagicItems.magicBrush.get()), AkaishiForbiddenCreativeTabs::addEquipmentToolsItems);
        registerTab(tabs, RITUALS_TAB_ID, "rituals", id(EQUIPMENT_TOOLS_TAB_ID),
                () -> new ItemStack(AkaishiMotherAltarBlocks.CHISHI_MOTHER_ALTAR.get()), AkaishiForbiddenCreativeTabs::addRitualItems);
        registerTab(tabs, MISC_TAB_ID, "misc", id(RITUALS_TAB_ID),
                () -> new ItemStack(ForbiddenMaterialItems.sculkLifeform.get()), AkaishiForbiddenCreativeTabs::addMiscItems);
    }

    private static void registerTab(Registrar<CreativeModeTab> tabs, String name, String title,
                                    ResourceLocation previous, Supplier<ItemStack> icon,
                                    Consumer<CreativeModeTab.Output> items) {
        tabs.register(id(name), () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.forbidden_hex." + title))
                .icon(icon)
                .withTabsBefore(previous)
                .displayItems((params, output) -> items.accept(output))
                .build());
    }

    private static ResourceLocation id(String name) {
        return new ResourceLocation(AkaishiMod.MOD_ID, name);
    }

    private static void addSourceMagicItems(CreativeModeTab.Output output) {
        accept(output, AkaishiCodexItems.forbiddenCodex);
        accept(output, AkaishiForbiddenBlocks.INSCRIPTION_TABLE);
        accept(output, AkaishiMagicItems.blankMagicCore);
        for (MagicSource source : MagicSource.values()) output.accept(MagicCoreItem.blank(source));
        accept(output, AkaishiMagicItems.inkBottle);
        AkaishiMagicItems.inks().values().forEach(ink -> accept(output, ink));
        accept(output, AkaishiWandItems.wand);
        AkaishiWandItems.parts().values().forEach(part -> accept(output, part));
        SourceAltarContent.addCreativeItems(output);
    }

    private static void addHerbsBrewingItems(CreativeModeTab.Output output) {
        accept(output, BrewingBlocks.OVEN);
        accept(output, BrewingBlocks.CRUCIBLE);
        accept(output, BrewingItems.WOOD_ASH);
        accept(output, BrewingItems.GRASS_ASH);
        BrewingItems.addCreativeItems(output);
        accept(output, AkaishiSanityItems.sanityTonic);
        accept(output, AkaishiSanityItems.greaterSanityTonic);
    }

    private static void addAlchemyCraftingItems(CreativeModeTab.Output output) {
        ForbiddenOres.addCreativeItems(output);
        AlchemyContent.addCreativeItems(output);
    }

    private static void addEquipmentToolsItems(CreativeModeTab.Output output) {
        accept(output, AkaishiMagicItems.magicBrush);
    }

    private static void addRitualItems(CreativeModeTab.Output output) {
        accept(output, AkaishiMotherAltarBlocks.CHISHI_MOTHER_ALTAR);
        accept(output, AkaishiMotherAltarBlocks.CHISHI_ALTAR_STONE);
        accept(output, AkaishiMotherAltarBlocks.CRYING_OBSIDIAN_RED);
        accept(output, ForbiddenMaterialItems.witherCharm);
    }

    private static void addMiscItems(CreativeModeTab.Output output) {
        accept(output, FamiliarContent.FAMILIAR_CONTRACT);
        for (FamiliarTemplateId template : FamiliarTemplateId.values()) {
            if (template.isConfigured()) accept(output, FamiliarTemplateItems.item(template));
        }
        FamiliarMaterialItems.definitions().values().forEach(definition -> accept(output, definition.item()));
        accept(output, FamiliarWorkbenchContent.ITEM);
        accept(output, LifeFurnaceContent.BLOCK_ITEM);
        accept(output, LifeFurnaceContent.BIOMASS);
        accept(output, SourceTransmutationFurnaceContent.ITEM);
        accept(output, SoulContent.SOUL_FRAGMENT);
        accept(output, SoulContent.SOUL_CRYSTAL);
        accept(output, AkaishiCodexItems.knowledgeTest);
        accept(output, ForbiddenMaterialItems.goldenMelonSlice);
        accept(output, ForbiddenMaterialItems.sculkLifeform);
        accept(output, AkaishiMotherAltarBlocks.CHISHI_ESSENCE_BLOCK);
    }

    /** 判空后把注册内容（物品/方块，均实现 ItemLike）放入创造标签（注册完成前为 null，防御性跳过） */
    private static void accept(CreativeModeTab.Output output, RegistrySupplier<? extends ItemLike> supplier) {
        if (supplier != null) {
            output.accept(new ItemStack(supplier.get()));
        }
    }
}
