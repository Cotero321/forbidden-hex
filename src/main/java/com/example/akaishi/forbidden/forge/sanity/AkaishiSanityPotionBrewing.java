package com.example.akaishi.forbidden.forge.sanity;

import com.example.akaishi.forbidden.sanity.content.SanityRestorePotions;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import net.minecraftforge.common.brewing.IBrewingRecipe;

/**
 * 理智回复药水的酿造配方（Forge 侧）。
 *
 * <p><b>为什么走 {@link IBrewingRecipe} 而不是 {@code PotionBrewing.addMix}</b>：
 * 1.20.1 Forge 把原版 {@code PotionBrewing.addMix} 保留为 <b>private</b>，改用
 * {@link BrewingRecipeRegistry} 接管酿造台（见 Forge 的 {@code BrewingStandBlockEntity} 补丁：
 * {@code canBrew} / {@code brewPotions} 一律转交该注册表，原版行为由其中的
 * {@code VanillaBrewingRecipe} 承担）。因此第三方加配方的官方姿势就是实现 {@code IBrewingRecipe}
 * 并 {@code addRecipe}，而不是去反射私有方法。
 *
 * <p><b>完整配方链</b>：
 * <ol>
 *   <li>粗制药水（{@code minecraft:awkward}）+ <b>发光鱿鱼墨囊</b> → {@code forbidden_hex:sanity_restore}
 *       （I 级：5s 内共回 7 SAN）</li>
 *   <li>上一步产物 + <b>萤石粉</b> → {@code forbidden_hex:strong_sanity_restore}（II 级：5s 内共回 10 SAN）</li>
 * </ol>
 * 输出<b>保持输入容器类型</b>（普通 → 普通、喷溅 → 喷溅、滞留 → 滞留），与
 * {@code VanillaBrewingRecipe} 的容器语义一致：故"先把理智药水做成喷溅型，再点萤石粉"
 * 也能自然得到强效喷溅型。
 *
 * <p><b>为什么选"发光鱿鱼墨囊"作基础款材料</b>：
 * <ol>
 *   <li><b>主题贴合</b>：本模组理智扣减的一条主要机制就是暗处暴露（{@code SanityDarkCycle}），
 *       而发光墨囊正是原版"黑暗中的一点光"，语义上就是"在黑暗里保持清醒"；
 *       与强化材料萤石粉（"让光更亮"）连起来，配方链自洽；</li>
 *   <li><b>原版可得且无冲突</b>：发光墨囊在原版没有任何酿造用途，不占原版配方位、不与既有链条打架；</li>
 *   <li><b>获取难度适中</b>：发光鱿鱼可在水下洞窟刷取，产量"中期可量产、前期稀缺"，
 *       契合消耗品定位。</li>
 * </ol>
 * 要替换材料（例如紫水晶碎片 = 更"精神共鸣"、回响碎片 = 更贴幽匿主题），只改
 * {@link #BASE_INGREDIENT} 一行，效果口径不动。
 *
 * <p><b>"红石无效"如何保证</b>：本类<b>只注册上面两条</b>，没有任何红石条目；
 * 原版的长效版本也不是自动生成的（需显式注册 mix），故
 * {@code BrewingRecipeRegistry.getOutput(理智回复药水, 红石粉)} 恒为空 ——
 * 红石粉放进酿造台对本药水<b>根本不命中配方</b>（而不是"命中了但没效果"）。
 * 同理只由萤石粉给出唯一一档强化，没有 III 级及以上。
 */
public final class AkaishiSanityPotionBrewing {

    /** 基础款酿造材料：发光鱿鱼墨囊（理由见类注释；替换点只此一行） */
    private static final Item BASE_INGREDIENT = Items.GLOW_INK_SAC;

    /** 重复注册保护（init 被重复调用时只注册一次） */
    private static boolean registered;

    private AkaishiSanityPotionBrewing() {
    }

    /**
     * 注册两条酿造配方（由 {@code AkaishiModForge} 在 {@code FMLCommonSetupEvent} 的 enqueueWork 中调用）。
     *
     * <p><b>为什么必须等 common setup</b>：药水实例由 Architectury 注册器在注册表事件里创建，
     * 必须等 {@code Registries.POTION} 注册完成后再取 {@code .get()}（否则会拿到 null 混进注册表）。
     * 另：{@link BrewingRecipeRegistry} 自带 {@code VanillaBrewingRecipe}（静态块），原版配方不受影响。
     */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        BrewingRecipeRegistry.addRecipe(new Step(Potions.AWKWARD, BASE_INGREDIENT,
                SanityRestorePotions.SANITY_RESTORE.get()));
        BrewingRecipeRegistry.addRecipe(new Step(SanityRestorePotions.SANITY_RESTORE.get(), Items.GLOWSTONE_DUST,
                SanityRestorePotions.STRONG_SANITY_RESTORE.get()));
    }

    /** 一条"药水 + 材料 → 药水"的配方（数据驱动：想加档位只需再来一条，不必改逻辑） */
    private record Step(Potion inputPotion, Item ingredientItem, Potion outputPotion) implements IBrewingRecipe {

        @Override
        public boolean isInput(ItemStack stack) {
            // 药水瓶三态（普通/喷溅/滞留）都算输入：与 VanillaBrewingRecipe.isInput 同口径
            return stack.getItem() instanceof PotionItem && PotionUtils.getPotion(stack) == inputPotion;
        }

        @Override
        public boolean isIngredient(ItemStack stack) {
            return stack.is(ingredientItem);
        }

        @Override
        public ItemStack getOutput(ItemStack input, ItemStack ingredient) {
            if (input.isEmpty() || ingredient.isEmpty() || !isInput(input) || !isIngredient(ingredient)) {
                return ItemStack.EMPTY;
            }
            // 只换药水类型、保留容器物品（普通/喷溅/滞留），与 VanillaBrewingRecipe 的容器语义一致
            return PotionUtils.setPotion(new ItemStack(input.getItem()), outputPotion);
        }
    }
}
