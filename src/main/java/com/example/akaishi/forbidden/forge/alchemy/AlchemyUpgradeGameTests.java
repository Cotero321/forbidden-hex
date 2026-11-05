package com.example.akaishi.forbidden.forge.alchemy;

import com.example.akaishi.forbidden.alchemy.AlchemyContent;
import com.example.akaishi.forbidden.alchemy.AlchemyMachineBlockEntity;
import com.example.akaishi.forbidden.alchemy.AlchemyMaterials;
import com.example.akaishi.forbidden.alchemy.AlchemyUpgradeRecipe;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@GameTestHolder("forbidden_hex")
@PrefixGameTestTemplate(false)
public final class AlchemyUpgradeGameTests {
    private static final BlockPos MACHINE = new BlockPos(7, 3, 7);
    private AlchemyUpgradeGameTests() { }
    private static void check(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private static AlchemyUpgradeRecipe recipe(GameTestHelper helper, AlchemyContent.Kind kind) {
        return (AlchemyUpgradeRecipe) helper.getLevel().getRecipeManager().byKey(new ResourceLocation("forbidden_hex",
                "alchemy/source_" + (kind.crusher() ? "crusher" : "distiller"))).orElseThrow();
    }
    private static TransientCraftingContainer grid(ItemStack machine) {
        var grid = new TransientCraftingContainer(new AbstractContainerMenu(null, -1) {
            @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player player) { return true; }
        }, 3, 3);
        grid.setItem(4, machine);
        grid.setItem(0, new ItemStack(BuiltInRegistries.ITEM.get(AlchemyMaterials.id("magic_stone"))));
        for (int slot : new int[]{2, 3, 6, 8}) grid.setItem(slot, new ItemStack(BuiltInRegistries.ITEM.get(AlchemyMaterials.id("silver_ingot"))));
        return grid;
    }
    private static AlchemyMachineBlockEntity machine(GameTestHelper helper, AlchemyContent.Kind kind) {
        helper.setBlock(MACHINE, AlchemyContent.BLOCKS.get(kind).get());
        return (AlchemyMachineBlockEntity) helper.getBlockEntity(MACHINE);
    }
    private static void tick(GameTestHelper helper, AlchemyMachineBlockEntity machine, int ticks) {
        for (int i = 0; i < ticks; i++) AlchemyMachineBlockEntity.tick(helper.getLevel(), machine.getBlockPos(), machine.getBlockState(), machine);
    }
    @GameTest(template = "source_altar_empty")
    public static void runningCrusherSurvivesCraftedUpgrade(GameTestHelper helper) {
        runningUpgrade(helper, AlchemyContent.Kind.MANUAL_CRUSHER);
    }
    @GameTest(template = "source_altar_empty")
    public static void runningDistillerSurvivesCraftedUpgrade(GameTestHelper helper) {
        runningUpgrade(helper, AlchemyContent.Kind.DISTILLER);
    }
    private static void runningUpgrade(GameTestHelper helper, AlchemyContent.Kind kind) {
        AlchemyGameTests.altar(helper);
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "alchemy-upgrade"));
        helper.getLevel().players().add(player);
        var original = machine(helper, kind);
        player.setPos(Vec3.atCenterOf(original.getBlockPos()));
        var menu = original.createMenu(1, player.getInventory(), player);
        player.containerMenu = menu;
        try {
            if (kind.crusher()) {
                original.setMode(1); original.setItem(0, new ItemStack(Items.RAW_IRON, 2));
                check(menu.clickMenuButton(player, 1), "Manual work input rejected");
            } else {
                helper.setBlock(MACHINE.below(), Blocks.MAGMA_BLOCK);
                original.fillWater(1000, false);
                original.setItem(0, new ItemStack(AlchemyMaterials.item("tiny_green_vitriol_dust")));
                original.setItem(2, new ItemStack(Items.GLASS_BOTTLE));
            }
            tick(helper, original, 3);
            check(original.hasJob() && original.progress() == 3 && original.getItem(0).isEmpty(), "Upgrade fixture did not reserve an active batch");
            CompoundTag before = original.saveWithoutMetadata();
            var drops = Block.getDrops(original.getBlockState(), helper.getLevel(), original.getBlockPos(), original);
            check(drops.size() == 1, "Machine did not produce exactly one persistent drop");
            var grid = grid(drops.get(0)); var recipe = recipe(helper, kind);
            check(helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, helper.getLevel())
                    .orElseThrow().getId().equals(recipe.getId()), "Upgrade not selected through crafting recipe manager");
            ItemStack upgraded = recipe.assemble(grid, helper.getLevel().registryAccess());
            CompoundTag transferred = upgraded.getTagElement("BlockEntityTag");
            check(transferred != null && transferred.getCompound("AlchemyJob").equals(before.getCompound("AlchemyJob"))
                    && transferred.getInt("Water") == original.water() && transferred.getInt("Mode") == original.mode(), "Upgrade lost reserved inputs, random products, progress, mode or water");
            var resultSlots = new ResultContainer(); resultSlots.setItem(0, upgraded);
            new ResultSlot(player, grid, resultSlots, 0, 0, 0).onTake(player, upgraded);
            check(grid.isEmpty(), "Taking upgrade result did not consume each ingredient exactly once");
            menu.removed(player); player.containerMenu = player.inventoryMenu;
            original.clearContent(); helper.setBlock(MACHINE, Blocks.AIR);
            var replacement = machine(helper, kind.crusher() ? AlchemyContent.Kind.SOURCE_CRUSHER : AlchemyContent.Kind.SOURCE_DISTILLER);
            check(BlockItem.updateCustomBlockEntityTag(helper.getLevel(), player, replacement.getBlockPos(), upgraded), "Placed upgrade did not apply machine NBT");
            check(replacement.saveWithoutMetadata().getCompound("AlchemyJob").equals(before.getCompound("AlchemyJob")), "Placement changed persisted batch");
            NonNullList<ItemStack> expected = NonNullList.withSize(4, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(before.getCompound("AlchemyJob").getCompound("Products"), expected);
            int water = replacement.water();
            tick(helper, replacement, replacement.duration() - replacement.progress());
            check(!replacement.hasJob() && replacement.water() == water && replacement.getItem(0).isEmpty(), "Upgraded batch restarted, lost water or failed to finish");
            for (int slot = 0; slot < 4; slot++) {
                ItemStack actual = replacement.getItem(slot + 3), wanted = expected.get(slot);
                check(actual.getCount() == wanted.getCount() && (wanted.isEmpty() ? actual.isEmpty() : ItemStack.isSameItemSameTags(actual, wanted)), "Upgrade lost or rerolled output slot " + slot);
            }
            helper.succeed();
        } finally {
            menu.removed(player); player.containerMenu = player.inventoryMenu; helper.getLevel().players().remove(player);
        }
    }
    @GameTest(template = "source_altar_empty")
    public static void upgradeGuardsAndNetworkPreserveOnlyAllowedState(GameTestHelper helper) {
        for (var kind : List.of(AlchemyContent.Kind.MANUAL_CRUSHER, AlchemyContent.Kind.DISTILLER)) {
            var recipe = recipe(helper, kind);
            ItemStack input = new ItemStack(AlchemyContent.BLOCKS.get(kind).get());
            check(recipe.getIngredients().size() == 6 && recipe.matches(grid(input), helper.getLevel()), "Fresh machine upgrade rejected or ingredient count changed");
            check(!recipe.assemble(grid(input), helper.getLevel().registryAccess()).hasTag(), "Fresh upgrade gained phantom state");
            CompoundTag stored = input.getOrCreateTagElement("BlockEntityTag");
            stored.putInt("Mode", 1); stored.putInt("Water", 1500); stored.putInt("Credit", kind.crusher() ? 9 : 4);
            stored.putString("id", "minecraft:chest"); stored.putInt("x", 100); stored.putString("Operator", "unrelated");
            CompoundTag slots = new CompoundTag();
            ContainerHelper.saveAllItems(slots, NonNullList.of(ItemStack.EMPTY, new ItemStack(Items.DIAMOND)));
            stored.put("Items", slots.get("Items")); input.getOrCreateTag().putString("Unrelated", "ignore");
            ItemStack snapshot = input.copy(); var grid = grid(input);
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            AlchemyUpgradeRecipe decoded;
            try {
                AlchemyContent.UPGRADE_SERIALIZER.get().toNetwork(buffer, recipe);
                decoded = AlchemyContent.UPGRADE_SERIALIZER.get().fromNetwork(recipe.getId(), buffer);
                check(buffer.readableBytes() == 0, "Upgrade recipe network payload not fully consumed");
            } finally { buffer.release(); }
            check(decoded.matches(grid, helper.getLevel()), "Synced upgrade recipe rejected saved machine");
            ItemStack output = decoded.assemble(grid, helper.getLevel().registryAccess());
            check(ItemStack.isSameItemSameTags(output, recipe.assemble(grid, helper.getLevel().registryAccess())), "Network sync changed upgrade output");
            CompoundTag state = output.getTagElement("BlockEntityTag");
            check(state != null && state.getAllKeys().equals(Set.of("Mode", "Water", "Credit"))
                    && state.getInt("Mode") == 1 && state.getInt("Water") == 1500 && state.getInt("Credit") == (kind.crusher() ? 9 : 4)
                    && !output.getTag().contains("Unrelated"), "Upgrade lost idle state or copied forbidden metadata/inventory");
            state.putInt("Water", 0);
            check(ItemStack.isSameItemSameTags(input, snapshot) && !recipe.getResultItem(helper.getLevel().registryAccess()).hasTag(), "Crafting mutated input or cached recipe result");
            grid.setItem(4, new ItemStack(AlchemyContent.BLOCKS.get(kind.crusher() ? AlchemyContent.Kind.DISTILLER : AlchemyContent.Kind.MANUAL_CRUSHER).get()));
            check(!decoded.matches(grid, helper.getLevel()) && decoded.assemble(grid, helper.getLevel().registryAccess()).isEmpty(), "Wrong machine kind accepted");
            grid.setItem(4, new ItemStack(AlchemyContent.BLOCKS.get(kind.crusher() ? AlchemyContent.Kind.SOURCE_CRUSHER : AlchemyContent.Kind.SOURCE_DISTILLER).get()));
            check(!decoded.matches(grid, helper.getLevel()), "Already upgraded machine accepted");
            grid = grid(input); grid.setItem(8, ItemStack.EMPTY);
            check(!decoded.matches(grid, helper.getLevel()), "Missing silver accepted");
            grid.setItem(8, new ItemStack(Items.COPPER_INGOT));
            check(!decoded.matches(grid, helper.getLevel()), "Wrong upgrade material accepted");
            grid = grid(input); grid.setItem(1, input.copy());
            check(!decoded.matches(grid, helper.getLevel()), "Two machine inputs accepted");
        }
        helper.succeed();
    }
}
