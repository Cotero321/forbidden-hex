package com.example.akaishi.forbidden.alchemy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;

import java.util.List;

public final class AlchemyUpgradeRecipe extends ShapelessRecipe {
    private final AlchemyContent.Kind inputKind;

    private AlchemyUpgradeRecipe(ShapelessRecipe recipe) {
        super(recipe.getId(), recipe.getGroup(), recipe.category(), recipe.getResultItem(RegistryAccess.EMPTY).copy(), recipe.getIngredients());
        ItemStack result = recipe.getResultItem(RegistryAccess.EMPTY);
        if (result.getCount() != 1) throw new JsonParseException("Alchemy upgrade must produce one machine");
        if (result.is(AlchemyContent.BLOCKS.get(AlchemyContent.Kind.SOURCE_CRUSHER).get().asItem())) {
            inputKind = AlchemyContent.Kind.MANUAL_CRUSHER;
        } else if (result.is(AlchemyContent.BLOCKS.get(AlchemyContent.Kind.SOURCE_DISTILLER).get().asItem())) {
            inputKind = AlchemyContent.Kind.DISTILLER;
        } else {
            throw new JsonParseException("Alchemy upgrade result must be a source crusher or source distiller");
        }
    }

    private ItemStack machineInput(CraftingContainer container) {
        ItemStack machine = ItemStack.EMPTY;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)
                    || !(blockItem.getBlock() instanceof AlchemyMachineBlock)) continue;
            if (!machine.isEmpty() || !stack.is(AlchemyContent.BLOCKS.get(inputKind).get().asItem())) return ItemStack.EMPTY;
            machine = stack;
        }
        return machine;
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        return !machineInput(container).isEmpty() && super.matches(container, level);
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        ItemStack input = machineInput(container);
        if (input.isEmpty()) return ItemStack.EMPTY;
        ItemStack result = super.assemble(container, registryAccess);
        CompoundTag stored = input.getTagElement("BlockEntityTag");
        if (stored != null) {
            CompoundTag transferred = new CompoundTag();
            for (String key : List.of("Mode", "Water", "Credit")) {
                if (stored.contains(key, Tag.TAG_ANY_NUMERIC)) transferred.put(key, stored.get(key).copy());
            }
            if (stored.contains("AlchemyJob", Tag.TAG_COMPOUND)) transferred.put("AlchemyJob", stored.getCompound("AlchemyJob").copy());
            if (!transferred.isEmpty()) result.getOrCreateTag().put("BlockEntityTag", transferred);
        }
        return result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return AlchemyContent.UPGRADE_SERIALIZER.get();
    }

    public static final class Serializer implements RecipeSerializer<AlchemyUpgradeRecipe> {
        private final ShapelessRecipe.Serializer delegate = new ShapelessRecipe.Serializer();

        @Override
        public AlchemyUpgradeRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new AlchemyUpgradeRecipe(delegate.fromJson(id, json));
        }

        @Override
        public AlchemyUpgradeRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new AlchemyUpgradeRecipe(delegate.fromNetwork(id, buffer));
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, AlchemyUpgradeRecipe recipe) {
            delegate.toNetwork(buffer, recipe);
        }
    }
}
