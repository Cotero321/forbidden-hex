package com.example.akaishi.forbidden.forge.data;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.alchemy.AlchemyContent;
import com.example.akaishi.forbidden.altar.SourceAltarContent;
import com.example.akaishi.forbidden.block.AkaishiForbiddenBlocks;
import com.example.akaishi.forbidden.block.AkaishiMotherAltarBlocks;
import com.example.akaishi.forbidden.brewing.BrewingBlocks;
import com.example.akaishi.forbidden.brewing.BrewingFumeItem;
import com.example.akaishi.forbidden.brewing.BrewingFumes;
import com.example.akaishi.forbidden.brewing.BrewingItems;
import com.example.akaishi.forbidden.familiar.FamiliarContent;
import com.example.akaishi.forbidden.familiar.core.FamiliarTemplateId;
import com.example.akaishi.forbidden.familiar.item.FamiliarMaterialItems;
import com.example.akaishi.forbidden.familiar.item.FamiliarTemplateItems;
import com.example.akaishi.forbidden.familiar.workbench.FamiliarWorkbenchContent;
import com.example.akaishi.forbidden.item.AkaishiCodexItems;
import com.example.akaishi.forbidden.item.AkaishiMagicItems;
import com.example.akaishi.forbidden.item.AkaishiSanityItems;
import com.example.akaishi.forbidden.item.ForbiddenMaterialItems;
import com.example.akaishi.forbidden.item.MagicCoreItem;
import com.example.akaishi.forbidden.magic.table.MagicSource;
import com.example.akaishi.forbidden.ore.ForbiddenOres;
import com.example.akaishi.forbidden.soul.SoulContent;
import com.example.akaishi.forbidden.transmutation.life_furnace.LifeFurnaceContent;
import com.example.akaishi.forbidden.transmutation.source_furnace.SourceTransmutationFurnaceContent;
import com.example.akaishi.forbidden.wand.AkaishiWandItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
import net.minecraftforge.common.CreativeModeTabRegistry;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@GameTestHolder("forbidden_hex")
@PrefixGameTestTemplate(false)
public final class ForbiddenCreativeTabGameTests {
    private static final List<String> TAB_IDS = List.of("forbidden_hex", "herbs_brewing", "alchemy_crafting",
            "equipment_tools", "rituals", "misc");
    private static final List<String> TITLES = List.of("forbidden", "herbs_brewing", "alchemy_crafting",
            "equipment_tools", "rituals", "misc");
    private static final List<FamiliarTemplateId> FAMILIAR_TEMPLATES = List.of(FamiliarTemplateId.WOLF,
            FamiliarTemplateId.CAT, FamiliarTemplateId.BAT, FamiliarTemplateId.FROG,
            FamiliarTemplateId.PARROT, FamiliarTemplateId.DRAGON);
    private static final List<String> FAMILIAR_MATERIALS = List.of("starfallen_iron", "flawless_silver",
            "void_obsidian", "aurum", "alchemical_white_salt", "moonlight_brocade", "windwhisper_thread",
            "hardened_leather", "abyssal_soft_hide", "soul_bone_block", "carbonized_flesh",
            "active_protoplasm", "resilient_vine_core", "dream_crystal", "antimagic_crystal");

    private ForbiddenCreativeTabGameTests() { }

    @GameTest(template = "brewing_empty")
    public static void creativeTabRegistration(GameTestHelper helper) {
        var registered = BuiltInRegistries.CREATIVE_MODE_TAB.keySet().stream()
                .filter(id -> id.getNamespace().equals(AkaishiMod.MOD_ID)).toList();
        helper.assertTrue(registered.size() == 6, "Expected exactly six Forbidden Hex creative tabs");
        var sorted = CreativeModeTabRegistry.getSortedCreativeModeTabs().stream()
                .map(BuiltInRegistries.CREATIVE_MODE_TAB::getKey)
                .filter(id -> id.getNamespace().equals(AkaishiMod.MOD_ID))
                .map(ResourceLocation::getPath).toList();
        helper.assertTrue(sorted.equals(TAB_IDS), "Incorrect creative tab order: " + sorted);
        for (int i = 0; i < TAB_IDS.size(); i++) {
            CreativeModeTab tab = tab(TAB_IDS.get(i));
            helper.assertTrue(tab.getDisplayName().getContents() instanceof TranslatableContents title
                    && title.getKey().equals("itemGroup.forbidden_hex." + TITLES.get(i)), "Incorrect tab title");
            helper.assertTrue(!tab.getIconItem().isEmpty(), "Missing tab icon: " + TAB_IDS.get(i));
        }
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void creativeTabCoverage(GameTestHelper helper) {
        Set<ItemStack> expected = expectedItems();
        for (boolean permissions : new boolean[]{false, true}) {
            rebuild(helper, permissions);
            Set<ItemStack> actual = ItemStackLinkedSet.createTypeAndTagSet();
            for (String name : TAB_IDS) {
                CreativeModeTab tab = tab(name);
                helper.assertTrue(!tab.getDisplayItems().isEmpty(), "Empty tab: " + name);
                for (ItemStack stack : tab.getDisplayItems()) {
                    helper.assertTrue(!stack.isEmpty() && stack.getCount() == 1, "Invalid creative stack: " + name);
                    helper.assertTrue(actual.add(stack), "Item appears in multiple tabs: " + stack);
                    helper.assertTrue(expected.contains(stack), "Unexpected creative item: " + stack);
                    helper.assertTrue(tab.getSearchTabDisplayItems().contains(stack), "Item missing from search: " + stack);
                }
                helper.assertTrue(tab.getDisplayItems().size() == tab.getSearchTabDisplayItems().size(),
                        "Parent/search contents differ: " + name);
                helper.assertTrue(tab.getDisplayItems().contains(tab.getIconItem()), "Tab icon absent from contents: " + name);
            }
            helper.assertTrue(actual.size() == expected.size(), "Creative item count changed");
            for (ItemStack stack : expected) helper.assertTrue(actual.contains(stack), "Lost creative item or NBT: " + stack);
        }
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void creativeTabAssignmentsAndVariants(GameTestHelper helper) {
        rebuild(helper, false);
        Collection<ItemStack> magic = tab("forbidden_hex").getDisplayItems();
        helper.assertTrue(magic.contains(new ItemStack(AkaishiWandItems.wand.get())), "Wand must be in source/spells");
        AkaishiWandItems.parts().values().forEach(part -> member(helper, magic, new ItemStack(part.get())));
        AkaishiMagicItems.inks().values().forEach(ink -> member(helper, magic, new ItemStack(ink.get())));
        for (MagicSource source : MagicSource.values()) member(helper, magic, MagicCoreItem.blank(source));
        SourceAltarContent.addCreativeItems((stack, visibility) -> member(helper, magic, stack));
        Collection<ItemStack> alchemy = tab("alchemy_crafting").getDisplayItems();
        ForbiddenOres.addCreativeItems((stack, visibility) -> member(helper, alchemy, stack));
        AlchemyContent.addCreativeItems((stack, visibility) -> member(helper, alchemy, stack));
        Collection<ItemStack> equipment = tab("equipment_tools").getDisplayItems();
        helper.assertTrue(equipment.size() == 1, "Equipment must not duplicate the wand family");
        member(helper, equipment, new ItemStack(AkaishiMagicItems.magicBrush.get()));
        Collection<ItemStack> rituals = tab("rituals").getDisplayItems();
        helper.assertTrue(rituals.size() == 4, "Incorrect ritual item count");
        member(helper, rituals, new ItemStack(AkaishiMotherAltarBlocks.CHISHI_MOTHER_ALTAR.get()));
        member(helper, rituals, new ItemStack(AkaishiMotherAltarBlocks.CHISHI_ALTAR_STONE.get()));
        member(helper, rituals, new ItemStack(AkaishiMotherAltarBlocks.CRYING_OBSIDIAN_RED.get()));
        member(helper, rituals, new ItemStack(ForbiddenMaterialItems.witherCharm.get()));
        Collection<ItemStack> misc = tab("misc").getDisplayItems();
        helper.assertTrue(misc.size() == 32, "Incorrect miscellaneous item count: " + misc.size());
        familiarAndTransmutationItems().forEach(stack -> member(helper, misc, stack));
        member(helper, misc, new ItemStack(AkaishiCodexItems.knowledgeTest.get()));
        member(helper, misc, new ItemStack(ForbiddenMaterialItems.goldenMelonSlice.get()));
        member(helper, misc, new ItemStack(ForbiddenMaterialItems.sculkLifeform.get()));
        member(helper, misc, new ItemStack(AkaishiMotherAltarBlocks.CHISHI_ESSENCE_BLOCK.get()));
        Collection<ItemStack> brewing = tab("herbs_brewing").getDisplayItems();
        BrewingItems.addCreativeItems((stack, visibility) -> member(helper, brewing, stack));
        member(helper, brewing, new ItemStack(BrewingBlocks.OVEN.get()));
        member(helper, brewing, new ItemStack(BrewingBlocks.CRUCIBLE.get()));
        member(helper, brewing, new ItemStack(BrewingItems.WOOD_ASH.get()));
        member(helper, brewing, new ItemStack(BrewingItems.GRASS_ASH.get()));
        member(helper, brewing, new ItemStack(AkaishiSanityItems.sanityTonic.get()));
        member(helper, brewing, new ItemStack(AkaishiSanityItems.greaterSanityTonic.get()));
        List<ResourceLocation> fumes = new ArrayList<>(BrewingFumes.NON_EVIL);
        fumes.add(BrewingFumes.EVIL_BREATH);
        fumes.addAll(BrewingFumes.REFINED);
        var actualFumes = brewing.stream().filter(stack -> stack.is(BrewingItems.FUME.get()))
                .map(BrewingFumeItem::kind).toList();
        helper.assertTrue(fumes.size() == 36 && actualFumes.equals(fumes), "Fume NBT variants or order changed");
        List<List<ItemStack>> before = TAB_IDS.stream().map(name -> List.copyOf(tab(name).getDisplayItems())).toList();
        rebuild(helper, false);
        for (int i = 0; i < TAB_IDS.size(); i++) {
            var after = List.copyOf(tab(TAB_IDS.get(i)).getDisplayItems());
            helper.assertTrue(after.size() == before.get(i).size(), "Rebuilding changed creative contents");
            for (int j = 0; j < after.size(); j++) {
                helper.assertTrue(ItemStack.isSameItemSameTags(before.get(i).get(j), after.get(j)),
                        "Rebuilding changed creative order or NBT");
            }
        }
        helper.succeed();
    }

    private static void rebuild(GameTestHelper helper, boolean permissions) {
        var parameters = new CreativeModeTab.ItemDisplayParameters(helper.getLevel().enabledFeatures(), permissions,
                helper.getLevel().registryAccess());
        for (String name : TAB_IDS) tab(name).buildContents(parameters);
    }

    private static CreativeModeTab tab(String name) {
        return BuiltInRegistries.CREATIVE_MODE_TAB.getOptional(new ResourceLocation(AkaishiMod.MOD_ID, name))
                .orElseThrow(() -> new IllegalStateException("Missing creative tab: " + name));
    }

    private static void member(GameTestHelper helper, Collection<ItemStack> items, ItemStack stack) {
        helper.assertTrue(items.contains(stack), "Item in wrong category or missing NBT: " + stack);
    }

    /** Explicit new-item whitelist, independent of the production tab-population helper. */
    private static List<ItemStack> familiarAndTransmutationItems() {
        List<ItemStack> items = new ArrayList<>();
        items.add(new ItemStack(FamiliarContent.FAMILIAR_CONTRACT.get()));
        for (FamiliarTemplateId template : FAMILIAR_TEMPLATES) {
            items.add(new ItemStack(FamiliarTemplateItems.item(template).get()));
        }
        for (String material : FAMILIAR_MATERIALS) {
            items.add(new ItemStack(FamiliarMaterialItems.item(material).get()));
        }
        items.add(new ItemStack(FamiliarWorkbenchContent.ITEM.get()));
        items.add(new ItemStack(LifeFurnaceContent.BLOCK_ITEM.get()));
        items.add(new ItemStack(LifeFurnaceContent.BIOMASS.get()));
        items.add(new ItemStack(SourceTransmutationFurnaceContent.ITEM.get()));
        items.add(new ItemStack(SoulContent.SOUL_FRAGMENT.get()));
        items.add(new ItemStack(SoulContent.SOUL_CRYSTAL.get()));
        return items;
    }

    private static Set<ItemStack> expectedItems() {
        Set<ItemStack> expected = ItemStackLinkedSet.createTypeAndTagSet();
        CreativeModeTab.Output output = (stack, visibility) -> expected.add(stack);
        output.accept(AkaishiCodexItems.forbiddenCodex.get());
        output.accept(AkaishiCodexItems.knowledgeTest.get());
        output.accept(AkaishiForbiddenBlocks.INSCRIPTION_TABLE.get());
        output.accept(BrewingBlocks.OVEN.get());
        output.accept(BrewingBlocks.CRUCIBLE.get());
        output.accept(BrewingItems.WOOD_ASH.get());
        output.accept(BrewingItems.GRASS_ASH.get());
        BrewingItems.addCreativeItems(output);
        ForbiddenOres.addCreativeItems(output);
        AlchemyContent.addCreativeItems(output);
        SourceAltarContent.addCreativeItems(output);
        output.accept(AkaishiMotherAltarBlocks.CHISHI_MOTHER_ALTAR.get());
        output.accept(AkaishiMotherAltarBlocks.CHISHI_ALTAR_STONE.get());
        output.accept(AkaishiMotherAltarBlocks.CRYING_OBSIDIAN_RED.get());
        output.accept(AkaishiSanityItems.sanityTonic.get());
        output.accept(AkaishiSanityItems.greaterSanityTonic.get());
        output.accept(ForbiddenMaterialItems.goldenMelonSlice.get());
        output.accept(ForbiddenMaterialItems.sculkLifeform.get());
        output.accept(ForbiddenMaterialItems.witherCharm.get());
        output.accept(AkaishiMotherAltarBlocks.CHISHI_ESSENCE_BLOCK.get());
        output.accept(AkaishiMagicItems.magicBrush.get());
        output.accept(AkaishiMagicItems.blankMagicCore.get());
        for (MagicSource source : MagicSource.values()) output.accept(MagicCoreItem.blank(source));
        output.accept(AkaishiMagicItems.inkBottle.get());
        AkaishiMagicItems.inks().values().forEach(ink -> output.accept(ink.get()));
        output.accept(AkaishiWandItems.wand.get());
        AkaishiWandItems.parts().values().forEach(part -> output.accept(part.get()));
        familiarAndTransmutationItems().forEach(output::accept);
        return expected;
    }
}
