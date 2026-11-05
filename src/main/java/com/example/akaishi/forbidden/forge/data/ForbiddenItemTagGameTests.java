package com.example.akaishi.forbidden.forge.data;

import com.example.akaishi.forbidden.ForbiddenItemTags;
import com.example.akaishi.forbidden.alchemy.AlchemyMaterials;
import com.example.akaishi.forbidden.brewing.BrewingItems;
import com.example.akaishi.forbidden.item.AkaishiMagicItems;
import com.example.akaishi.forbidden.ore.ForbiddenOres;
import com.example.akaishi.forbidden.wand.AkaishiWandItems;
import com.example.akaishi.forbidden.wand.WandPartDef;
import com.example.akaishi.forbidden.wand.WandPartItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder("forbidden_hex")
@PrefixGameTestTemplate(false)
public final class ForbiddenItemTagGameTests {
    private ForbiddenItemTagGameTests() { }

    @GameTest(template = "brewing_empty")
    public static void internalClassification(GameTestHelper helper) {
        members(helper, ForbiddenItemTags.HERBS, BrewingItems.MANDRAKE_ROOT.get(), BrewingItems.HENBANE.get(),
                BrewingItems.BELLADONNA.get(), BrewingItems.DATURA.get(), BrewingItems.ACONITE.get(),
                BrewingItems.FOXGLOVE.get(), BrewingItems.HELLROOT.get(), BrewingItems.COLCHICUM.get(),
                BrewingItems.YEW.get(), BrewingItems.ERGOT.get(), BrewingItems.PARASITIC_VINE.get(),
                BrewingItems.EMBER_MOSS.get(), BrewingItems.GLINT_WEED.get());
        excluded(helper, ForbiddenItemTags.HERBS, BrewingItems.MANDRAKE_SEEDS.get(), BrewingItems.REFINED_HERB_PASTE.get(),
                BrewingItems.WOOD_ASH.get(), BrewingItems.GRASS_ASH.get());
        members(helper, ForbiddenItemTags.CINNABAR_FRAGMENTS, ForbiddenOres.CINNABAR_FRAGMENT.get());
        members(helper, ForbiddenItemTags.SULFUR_FRAGMENTS, ForbiddenOres.SULFUR_FRAGMENT.get());
        members(helper, ForbiddenItemTags.ORE_FRAGMENTS, ForbiddenOres.CINNABAR_FRAGMENT.get(), ForbiddenOres.SULFUR_FRAGMENT.get());
        excluded(helper, ForbiddenItemTags.CINNABAR_FRAGMENTS, ForbiddenOres.SULFUR_FRAGMENT.get());
        excluded(helper, ForbiddenItemTags.SULFUR_FRAGMENTS, ForbiddenOres.CINNABAR_FRAGMENT.get());
        excluded(helper, ForbiddenItemTags.ORE_FRAGMENTS, ForbiddenOres.RAW_SILVER.get(), ForbiddenOres.MAGIC_STONE.get());
        for (String material : List.of("cinnabar", "sulfur")) {
            excluded(helper, TagKey.create(Registries.ITEM, new ResourceLocation("forge", "dusts/" + material)),
                    ForbiddenOres.CINNABAR_FRAGMENT.get(), ForbiddenOres.SULFUR_FRAGMENT.get());
        }
        for (var supplier : AkaishiWandItems.parts().values()) {
            Item item = supplier.get();
            WandPartDef.Kind kind = ((WandPartItem) item).def().kind();
            members(helper, ForbiddenItemTags.WAND_PARTS, item);
            helper.assertTrue(new ItemStack(item).is(ForbiddenItemTags.WAND_TIPS) == (kind == WandPartDef.Kind.TIP), "Incorrect tip classification");
            helper.assertTrue(new ItemStack(item).is(ForbiddenItemTags.WAND_HANDLES) == (kind == WandPartDef.Kind.HANDLE), "Incorrect handle classification");
            helper.assertTrue(new ItemStack(item).is(ForbiddenItemTags.WAND_CORES) == (kind == WandPartDef.Kind.CORE), "Incorrect wand core classification");
        }
        members(helper, ForbiddenItemTags.WANDS, AkaishiWandItems.wand.get());
        excluded(helper, ForbiddenItemTags.WAND_PARTS, AkaishiWandItems.wand.get(), AkaishiMagicItems.blankMagicCore.get());
        for (var ink : AkaishiMagicItems.inks().values()) {
            members(helper, ForbiddenItemTags.INKS, ink.get());
            excluded(helper, ForbiddenItemTags.MAGIC_CORES, ink.get());
        }
        for (var core : AkaishiMagicItems.cores().values()) {
            members(helper, ForbiddenItemTags.MAGIC_CORES, core.get());
            excluded(helper, ForbiddenItemTags.WAND_CORES, core.get());
        }
        excluded(helper, ForbiddenItemTags.INKS, AkaishiMagicItems.inkBottle.get(), AkaishiMagicItems.magicBrush.get());
        excluded(helper, ForbiddenItemTags.MAGIC_CORES, AkaishiMagicItems.blankMagicCore.get(), AkaishiMagicItems.magicBrush.get());
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void bottledClassification(GameTestHelper helper) {
        members(helper, ForbiddenItemTags.BOTTLED, BrewingItems.SLAKED_LIME_BOTTLE.get(),
                BrewingItems.POTION.get(), BrewingItems.FUME.get());
        for (var material : AlchemyMaterials.MATERIALS) {
            Item item = AlchemyMaterials.item(material.name());
            if (material.shape().equals("bottle")) members(helper, ForbiddenItemTags.BOTTLED, item);
            else excluded(helper, ForbiddenItemTags.BOTTLED, item);
        }
        for (var ink : AkaishiMagicItems.inks().values()) members(helper, ForbiddenItemTags.BOTTLED, ink.get());
        excluded(helper, ForbiddenItemTags.BOTTLED, Items.GLASS_BOTTLE, AkaishiMagicItems.inkBottle.get(),
                AkaishiMagicItems.magicBrush.get(), AkaishiMagicItems.blankMagicCore.get(),
                BrewingItems.TRANSMUTATION_DUST.get(), BrewingItems.REFINED_HERB_PASTE.get(),
                BrewingItems.WOOD_ASH.get(), BrewingItems.GRASS_ASH.get());
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void publicMaterialClassification(GameTestHelper helper) {
        members(helper, ForbiddenItemTags.SILVER_ORES, ForbiddenOres.SILVER_ORE.get().asItem(), ForbiddenOres.DEEPSLATE_SILVER_ORE.get().asItem());
        members(helper, ForbiddenItemTags.RAW_SILVER, ForbiddenOres.RAW_SILVER.get());
        members(helper, ForbiddenItemTags.SILVER_INGOTS, ForbiddenOres.SILVER_INGOT.get());
        members(helper, ForbiddenItemTags.SILVER_NUGGETS, ForbiddenOres.SILVER_NUGGET.get());
        members(helper, ForbiddenItemTags.SILVER_BLOCKS, ForbiddenOres.SILVER_BLOCK.get().asItem());
        members(helper, ForbiddenItemTags.RAW_SILVER_BLOCKS, ForbiddenOres.RAW_SILVER_BLOCK.get().asItem());
        for (String material : List.of("cinnabar", "sulfur", "silver", "magic_stone")) {
            TagKey<Item> items = TagKey.create(Registries.ITEM, new ResourceLocation("forge", "ores/" + material));
            var blocks = TagKey.create(Registries.BLOCK, items.location());
            for (var block : BuiltInRegistries.BLOCK.getTagOrEmpty(blocks)) {
                members(helper, items, block.value().asItem());
            }
        }
        members(helper, TagKey.create(Registries.ITEM, new ResourceLocation("forge", "seeds")), BrewingItems.MANDRAKE_SEEDS.get());
        members(helper, TagKey.create(Registries.ITEM, new ResourceLocation("minecraft", "saplings")), BrewingItems.HAWTHORN_SAPLING.get());
        excluded(helper, ForbiddenItemTags.SILVER_INGOTS, ForbiddenOres.MAGIC_STONE.get(), ForbiddenOres.SULFUR_FRAGMENT.get());
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void silverTagCooking(GameTestHelper helper) {
        for (String source : List.of("raw_silver", "silver_ore")) {
            TagKey<Item> inputTag = source.equals("raw_silver") ? ForbiddenItemTags.RAW_SILVER : ForbiddenItemTags.SILVER_ORES;
            for (String type : List.of("smelting", "blasting")) {
                Recipe<?> value = recipe(helper, "silver_ingot_from_" + type + "_" + source);
                helper.assertTrue(value instanceof AbstractCookingRecipe, "Silver recipe is not a cooking recipe");
                AbstractCookingRecipe cooking = (AbstractCookingRecipe) value;
                helper.assertTrue(cooking.getType() == (type.equals("smelting") ? RecipeType.SMELTING : RecipeType.BLASTING), "Incorrect cooking type");
                helper.assertTrue(cooking.getCookingTime() == (type.equals("smelting") ? 200 : 100)
                        && cooking.getExperience() == 1.0F, "Silver cooking costs changed");
                tagIngredients(helper, cooking, inputTag);
                int checked = 0;
                for (var member : BuiltInRegistries.ITEM.getTagOrEmpty(inputTag)) {
                    var input = new SimpleContainer(new ItemStack(member.value()));
                    helper.assertTrue(cooking.matches(input, helper.getLevel()), "Compatible silver material was rejected");
                    output(helper, cooking.assemble(input, helper.getLevel().registryAccess()), ForbiddenOres.SILVER_INGOT.get(), 1);
                    checked++;
                }
                helper.assertTrue(checked > 0, "Empty public silver ingredient tag");
                for (Item wrong : List.of(ForbiddenOres.CINNABAR_FRAGMENT.get(), ForbiddenOres.SULFUR_FRAGMENT.get(),
                        ForbiddenOres.MAGIC_STONE.get(), Items.COPPER_INGOT)) {
                    helper.assertTrue(!cooking.matches(new SimpleContainer(new ItemStack(wrong)), helper.getLevel()), "Unrelated material produces silver");
                }
            }
            for (String type : List.of("smelting", "blasting")) {
                helper.assertTrue(helper.getLevel().getRecipeManager().byKey(id("silver_ingot_from_" + type + "_deepslate_silver_ore")).isEmpty(),
                        "Duplicate deepslate silver cooking recipe remains");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void silverTagPacking(GameTestHelper helper) {
        packing(helper, "silver_nugget", "silver_ingot", ForbiddenItemTags.SILVER_NUGGETS, ForbiddenItemTags.SILVER_INGOTS,
                ForbiddenOres.SILVER_NUGGET.get(), ForbiddenOres.SILVER_INGOT.get());
        packing(helper, "silver_ingot", "silver_block", ForbiddenItemTags.SILVER_INGOTS, ForbiddenItemTags.SILVER_BLOCKS,
                ForbiddenOres.SILVER_INGOT.get(), ForbiddenOres.SILVER_BLOCK.get().asItem());
        packing(helper, "raw_silver", "raw_silver_block", ForbiddenItemTags.RAW_SILVER, ForbiddenItemTags.RAW_SILVER_BLOCKS,
                ForbiddenOres.RAW_SILVER.get(), ForbiddenOres.RAW_SILVER_BLOCK.get().asItem());
        helper.succeed();
    }

    private static void packing(GameTestHelper helper, String small, String large, TagKey<Item> smallTag,
                                TagKey<Item> largeTag, Item smallItem, Item largeItem) {
        CraftingRecipe pack = (CraftingRecipe) recipe(helper, large + "_from_" + small);
        CraftingRecipe unpack = (CraftingRecipe) recipe(helper, small + "_from_" + large);
        tagIngredients(helper, pack, smallTag);
        tagIngredients(helper, unpack, largeTag);
        for (var member : BuiltInRegistries.ITEM.getTagOrEmpty(smallTag)) {
            var input = grid();
            for (int slot = 0; slot < 9; slot++) input.setItem(slot, new ItemStack(slot % 2 == 0 ? member.value() : smallItem));
            helper.assertTrue(pack.matches(input, helper.getLevel()), "Mixed compatible silver materials were rejected");
            output(helper, pack.assemble(input, helper.getLevel().registryAccess()), largeItem, 1);
            helper.assertTrue(pack.getRemainingItems(input).stream().allMatch(ItemStack::isEmpty), "Silver packing returns extra items");
            input.setItem(8, ItemStack.EMPTY);
            helper.assertTrue(!pack.matches(input, helper.getLevel()), "Eight silver materials were accepted as nine");
            input.setItem(8, new ItemStack(Items.COPPER_INGOT));
            helper.assertTrue(!pack.matches(input, helper.getLevel()), "Unrelated material was accepted as silver");
        }
        for (var member : BuiltInRegistries.ITEM.getTagOrEmpty(largeTag)) {
            var input = grid();
            input.setItem(4, new ItemStack(member.value()));
            helper.assertTrue(unpack.matches(input, helper.getLevel()), "Compatible silver block/ingot was rejected");
            output(helper, unpack.assemble(input, helper.getLevel().registryAccess()), smallItem, 9);
            helper.assertTrue(unpack.getRemainingItems(input).stream().allMatch(ItemStack::isEmpty), "Silver unpacking returns extra items");
            input.setItem(0, new ItemStack(Items.STICK));
            helper.assertTrue(!unpack.matches(input, helper.getLevel()), "Unpacking ignored an extra ingredient");
        }
    }

    private static Recipe<?> recipe(GameTestHelper helper, String name) {
        return helper.getLevel().getRecipeManager().byKey(id(name)).orElseThrow(() -> new IllegalStateException("Missing recipe: " + name));
    }

    private static void tagIngredients(GameTestHelper helper, Recipe<?> recipe, TagKey<Item> tag) {
        helper.assertTrue(!recipe.getIngredients().isEmpty(), "Recipe has no ingredients");
        for (var ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) continue;
            var json = ingredient.toJson().getAsJsonObject();
            helper.assertTrue(json.has("tag") && json.get("tag").getAsString().equals(tag.location().toString()), "Recipe does not use the specific public tag");
        }
    }

    private static void members(GameTestHelper helper, TagKey<Item> tag, Item... items) {
        for (Item item : items) helper.assertTrue(new ItemStack(item).is(tag), "Missing tag member: " + tag.location() + " / " + item);
    }

    private static void excluded(GameTestHelper helper, TagKey<Item> tag, Item... items) {
        for (Item item : items) helper.assertTrue(!new ItemStack(item).is(tag), "Incorrect tag member: " + tag.location() + " / " + item);
    }

    private static void output(GameTestHelper helper, ItemStack result, Item item, int count) {
        helper.assertTrue(result.is(item) && result.getCount() == count, "Incorrect silver conversion output");
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("forbidden_hex", "forbidden_hex_" + path);
    }

    private static TransientCraftingContainer grid() {
        return new TransientCraftingContainer(new AbstractContainerMenu(null, -1) {
            @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player player) { return true; }
        }, 3, 3);
    }
}
