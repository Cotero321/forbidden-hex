package com.example.akaishi.forbidden.wand;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * 法杖<b>合成定型</b>配方（定案 12）：工作台内「杖端 + 杖柄 + 杖核」各一 ⇒ 法杖。
 *
 * <p><b>为什么是自定义配方而不是 json 摆位</b>：产物要把三件输入的 id 写进 NBT
 * （哪三段 = 这根杖的身份），原版 json 配方只会产固定物品。摆位上刻意<b>不限形状</b>：
 * 玩家把三件丢在工作台任意三格都能成 —— 组装是"组合"不是"画图"。
 *
 * <p><b>不做装配界面</b>（定案 12）：合成即定型、不可拆卸，故不需要任何 UI。
 */
public class WandAssemblyRecipe implements CraftingRecipe {

    private final ResourceLocation id;

    public WandAssemblyRecipe(ResourceLocation id) {
        this.id = id;
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        int tips = 0;
        int handles = 0;
        int cores = 0;
        int occupied = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            occupied++;
            if (!(stack.getItem() instanceof WandPartItem part)) return false;
            switch (part.def().kind()) {
                case TIP -> tips++;
                case HANDLE -> handles++;
                case CORE -> cores++;
                default -> {
                    return false;
                }
            }
        }
        return occupied == 3 && tips == 1 && handles == 1 && cores == 1;
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        String tip = "";
        String handle = "";
        String core = "";
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.getItem() instanceof WandPartItem part) {
                switch (part.def().kind()) {
                    case TIP -> tip = part.def().id();
                    case HANDLE -> handle = part.def().id();
                    case CORE -> core = part.def().id();
                    default -> {
                        return ItemStack.EMPTY;
                    }
                }
            }
        }
        if (tip.isEmpty() || handle.isEmpty() || core.isEmpty()) return ItemStack.EMPTY;
        return AkaishiWandItems.createWand(tip, handle, core);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 3;
    }

    /** 配方书/JEI 的代表产物（真产物在 assemble 现组；JEI 不进 —— 定案 13） */
    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return new ItemStack(AkaishiWandItems.wand.get());
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return AkaishiWandRecipes.ASSEMBLY.get();
    }

    @Override
    public RecipeType<?> getType() {
        return RecipeType.CRAFTING;
    }

    /** 1.20.1 的 CraftingRecipe 抽象方法：组装类配方归入杂项 */
    @Override
    public CraftingBookCategory category() {
        return CraftingBookCategory.MISC;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    /** 配方无字段：json 只写 {@code {"type":"forbidden_hex:wand_assembly"}}，网络同理零载荷 */
    public static class Serializer implements RecipeSerializer<WandAssemblyRecipe> {
        @Override
        public WandAssemblyRecipe fromJson(ResourceLocation id, com.google.gson.JsonObject json) {
            return new WandAssemblyRecipe(id);
        }

        @Override
        public WandAssemblyRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            return new WandAssemblyRecipe(id);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, WandAssemblyRecipe recipe) {
            // 零载荷
        }
    }
}
