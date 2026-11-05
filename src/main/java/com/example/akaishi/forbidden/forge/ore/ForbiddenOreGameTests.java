package com.example.akaishi.forbidden.forge.ore;

import com.example.akaishi.forbidden.ore.ForbiddenOres;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder("forbidden_hex")
@PrefixGameTestTemplate(false)
public final class ForbiddenOreGameTests {
    private static final BlockPos ORE_POS = new BlockPos(2, 2, 2);

    private ForbiddenOreGameTests() { }

    @GameTest(template = "brewing_empty")
    public static void ironPickaxeAndOreDrops(GameTestHelper helper) {
        List<Block> ores = List.of(ForbiddenOres.CINNABAR_ORE.get(), ForbiddenOres.DEEPSLATE_CINNABAR_ORE.get(),
                ForbiddenOres.SULFUR_ORE.get(), ForbiddenOres.DEEPSLATE_SULFUR_ORE.get(),
                ForbiddenOres.SILVER_ORE.get(), ForbiddenOres.DEEPSLATE_SILVER_ORE.get());
        ItemStack silk = new ItemStack(Items.IRON_PICKAXE);
        silk.enchant(Enchantments.SILK_TOUCH, 1);
        ItemStack fortune = new ItemStack(Items.IRON_PICKAXE);
        fortune.enchant(Enchantments.BLOCK_FORTUNE, 3);
        for (int index = 0; index < ores.size(); index++) {
            Block ore = ores.get(index);
            var state = ore.defaultBlockState();
            helper.assertTrue(state.requiresCorrectToolForDrops(), "Ore does not require a correct tool: " + ore);
            for (Item tool : List.of(Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.GOLDEN_PICKAXE, Items.IRON_AXE)) {
                helper.assertTrue(!new ItemStack(tool).isCorrectToolForDrops(state), "Weak/wrong tool harvests ore: " + ore);
            }
            for (Item tool : List.of(Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE)) {
                helper.assertTrue(new ItemStack(tool).isCorrectToolForDrops(state), "Correct pickaxe cannot harvest: " + ore);
            }
            helper.setBlock(ORE_POS, ore);
            Item material = index < 2 ? ForbiddenOres.CINNABAR_FRAGMENT.get()
                    : index < 4 ? ForbiddenOres.SULFUR_FRAGMENT.get() : ForbiddenOres.RAW_SILVER.get();
            int minimum = index < 4 ? 2 : 1, maximum = index < 4 ? 4 : 1;
            helper.assertTrue(dropped(helper, ore, silk, ore.asItem()) == 1, "Silk Touch did not preserve ore: " + ore);
            boolean increased = false;
            for (int attempt = 0; attempt < 64; attempt++) {
                int plain = dropped(helper, ore, new ItemStack(Items.IRON_PICKAXE), material);
                helper.assertTrue(plain >= minimum && plain <= maximum, "Incorrect base ore drop: " + ore + " = " + plain);
                int bonus = dropped(helper, ore, fortune, material);
                helper.assertTrue(bonus >= minimum && bonus <= maximum * 4, "Incorrect Fortune III bounds: " + ore);
                increased |= bonus > maximum;
            }
            helper.assertTrue(increased, "Fortune never increased material drops: " + ore);
        }
        for (Block block : List.of(ForbiddenOres.SILVER_BLOCK.get(), ForbiddenOres.RAW_SILVER_BLOCK.get())) {
            helper.setBlock(ORE_POS, block);
            helper.assertTrue(dropped(helper, block, new ItemStack(Items.IRON_PICKAXE), block.asItem()) == 1,
                    "Silver storage block did not drop itself");
        }
        helper.succeed();
    }

    private static int dropped(GameTestHelper helper, Block block, ItemStack tool, Item expected) {
        List<ItemStack> drops = Block.getDrops(block.defaultBlockState(), helper.getLevel(),
                helper.absolutePos(ORE_POS), null, null, tool);
        helper.assertTrue(!drops.isEmpty() && drops.stream().allMatch(stack -> stack.is(expected)),
                "Wrong or missing mineral loot: " + block + " => " + drops);
        return drops.stream().mapToInt(ItemStack::getCount).sum();
    }

    @GameTest(template = "brewing_empty")
    public static void magicStoneHarvestAndLight(GameTestHelper helper) {
        Block ore = ForbiddenOres.DEEPSLATE_MAGIC_STONE_ORE.get();
        for (Block block : List.of(ore, ForbiddenOres.MAGIC_STONE_BLOCK.get())) {
            var state = block.defaultBlockState();
            helper.assertTrue(state.requiresCorrectToolForDrops(), "Magic stone must require the correct tool");
            helper.assertTrue(state.getLightEmission() == 7, "Magic stone must emit vanilla block light");
            for (Item tool : List.of(Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.GOLDEN_PICKAXE,
                    Items.IRON_PICKAXE, Items.DIAMOND_AXE)) {
                helper.assertTrue(!new ItemStack(tool).isCorrectToolForDrops(state), "Incorrect tool harvests magic stone: " + tool);
            }
            for (Item tool : List.of(Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE)) {
                helper.assertTrue(new ItemStack(tool).isCorrectToolForDrops(state), "Diamond-tier pickaxe cannot harvest magic stone");
            }
            helper.setBlock(ORE_POS, block);
            ItemStack silk = new ItemStack(Items.DIAMOND_PICKAXE);
            silk.enchant(Enchantments.SILK_TOUCH, 1);
            helper.assertTrue(dropped(helper, block, silk, block.asItem()) == 1, "Silk Touch did not preserve magic stone block");
        }
        helper.setBlock(ORE_POS, ore);
        ItemStack fortune = new ItemStack(Items.DIAMOND_PICKAXE);
        fortune.enchant(Enchantments.BLOCK_FORTUNE, 3);
        boolean increased = false;
        for (int attempt = 0; attempt < 64; attempt++) {
            helper.assertTrue(dropped(helper, ore, new ItemStack(Items.DIAMOND_PICKAXE), ForbiddenOres.MAGIC_STONE.get()) == 1,
                    "Base magic stone drop must be one");
            int count = dropped(helper, ore, fortune, ForbiddenOres.MAGIC_STONE.get());
            helper.assertTrue(count >= 1 && count <= 4, "Invalid magic stone Fortune III drop");
            increased |= count > 1;
        }
        helper.assertTrue(increased, "Fortune never increased magic stone drops");
        helper.assertTrue(dropped(helper, ForbiddenOres.MAGIC_STONE_BLOCK.get(), fortune,
                ForbiddenOres.MAGIC_STONE_BLOCK.get().asItem()) == 1, "Fortune must not multiply storage blocks");
        helper.succeed();
    }

    @GameTest(template = "brewing_empty")
    public static void silverCooking(GameTestHelper helper) {
        for (Item item : List.of(ForbiddenOres.RAW_SILVER.get(), ForbiddenOres.SILVER_ORE.get().asItem(),
                ForbiddenOres.DEEPSLATE_SILVER_ORE.get().asItem())) {
            var input = new SimpleContainer(new ItemStack(item));
            var smelting = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.SMELTING, input, helper.getLevel());
            var blasting = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.BLASTING, input, helper.getLevel());
            helper.assertTrue(smelting.isPresent() && blasting.isPresent(), "Silver cooking recipe missing: " + item);
            cooking(helper, smelting.orElseThrow(), 200);
            cooking(helper, blasting.orElseThrow(), 100);
        }
        for (Item item : List.of(ForbiddenOres.CINNABAR_FRAGMENT.get(), ForbiddenOres.SULFUR_FRAGMENT.get(),
                ForbiddenOres.MAGIC_STONE.get(), ForbiddenOres.DEEPSLATE_MAGIC_STONE_ORE.get().asItem())) {
            var input = new SimpleContainer(new ItemStack(item));
            helper.assertTrue(helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.SMELTING, input, helper.getLevel()).isEmpty()
                    && helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.BLASTING, input, helper.getLevel()).isEmpty(),
                    "Unrequested fragment processing was added");
        }
        helper.succeed();
    }

    private static void cooking(GameTestHelper helper, AbstractCookingRecipe recipe, int ticks) {
        ItemStack result = recipe.getResultItem(helper.getLevel().registryAccess());
        helper.assertTrue(result.is(ForbiddenOres.SILVER_INGOT.get()) && result.getCount() == 1,
                "Silver cooking output is not one ingot");
        helper.assertTrue(recipe.getCookingTime() == ticks && recipe.getExperience() == 1.0F,
                "Silver cooking time/experience differs from the gold route");
    }

    @GameTest(template = "brewing_empty")
    public static void silverPackingRoundTrips(GameTestHelper helper) {
        for (Item[] pair : List.of(new Item[]{ForbiddenOres.SILVER_NUGGET.get(), ForbiddenOres.SILVER_INGOT.get()},
                new Item[]{ForbiddenOres.SILVER_INGOT.get(), ForbiddenOres.SILVER_BLOCK.get().asItem()},
                new Item[]{ForbiddenOres.RAW_SILVER.get(), ForbiddenOres.RAW_SILVER_BLOCK.get().asItem()},
                new Item[]{ForbiddenOres.MAGIC_STONE.get(), ForbiddenOres.MAGIC_STONE_BLOCK.get().asItem()})) {
            var packed = grid();
            for (int slot = 0; slot < 9; slot++) packed.setItem(slot, new ItemStack(pair[0]));
            craft(helper, packed, pair[1], 1);
            var unpacked = grid();
            unpacked.setItem(4, new ItemStack(pair[1]));
            craft(helper, unpacked, pair[0], 9);
            packed.setItem(8, ItemStack.EMPTY);
            helper.assertTrue(helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, packed, helper.getLevel()).isEmpty(),
                    "Eight materials were accepted as nine");
        }
        helper.succeed();
    }

    private static TransientCraftingContainer grid() {
        AbstractContainerMenu menu = new AbstractContainerMenu(null, -1) {
            @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player player) { return true; }
        };
        return new TransientCraftingContainer(menu, 3, 3);
    }
    private static void craft(GameTestHelper helper, TransientCraftingContainer input, Item output, int count) {
        var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
        helper.assertTrue(recipe.isPresent(), "Silver packing/unpacking recipe missing");
        ItemStack result = recipe.orElseThrow().assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(result.is(output) && result.getCount() == count, "Incorrect silver conversion ratio");
    }

    @GameTest(template = "brewing_empty")
    public static void overworldOnlyOreFeatures(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var features = registries.registryOrThrow(Registries.PLACED_FEATURE);
        var biomes = registries.registryOrThrow(Registries.BIOME);
        for (String name : List.of("cinnabar", "sulfur", "silver", "magic_stone")) {
            var feature = features.get(new ResourceLocation("forbidden_hex", "forbidden_hex_" + name + "_ore"));
            helper.assertTrue(feature != null, "Missing placed ore feature: " + name);
            helper.assertTrue(feature.feature().value().config() instanceof OreConfiguration, "Not a vanilla ore feature");
            OreConfiguration config = (OreConfiguration) feature.feature().value().config();
            boolean magic = name.equals("magic_stone");
            helper.assertTrue(config.targetStates.size() == (magic ? 1 : 2)
                            && config.size == (magic ? 3 : name.equals("silver") ? 9 : 6),
                    "Incorrect stone/deepslate targets or vein size: " + name);
            if (magic) {
                helper.assertTrue(config.targetStates.get(0).state.is(ForbiddenOres.DEEPSLATE_MAGIC_STONE_ORE.get()),
                        "Magic ore target must be the deepslate variant");
            }
            for (String biome : List.of("plains", "badlands")) {
                helper.assertTrue(biomes.get(new ResourceLocation("minecraft", biome)).getGenerationSettings().hasFeature(feature),
                        "Overworld biome lacks ore: " + biome + "/" + name);
            }
            for (String biome : List.of("nether_wastes", "the_end")) {
                helper.assertTrue(!biomes.get(new ResourceLocation("minecraft", biome)).getGenerationSettings().hasFeature(feature),
                        "Ore incorrectly added outside Overworld: " + name);
            }
        }
        helper.succeed();
    }
}
