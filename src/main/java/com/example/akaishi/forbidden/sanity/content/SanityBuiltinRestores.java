package com.example.akaishi.forbidden.sanity.content;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.api.sanity.ISanityRestoreSource;
import com.example.akaishi.forbidden.api.sanity.SanityRestoreRegistry;
import com.example.akaishi.forbidden.item.AkaishiSanityItems;
import com.example.akaishi.forbidden.item.ForbiddenMaterialItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;

import java.util.List;
import java.util.function.Predicate;

/**
 * 内置 SANC（理智上限）恢复来源（"首用类"）：玩家<b>第一次使用</b>某物品时永久抬升上限。
 *
 * <p><b>与首见的区别</b>（见 {@link ISanityRestoreSource} 的对外口径）：首见认"环境/接触"，
 * 首用认"行为"。二者共用同一份 {@code first_seen} 记档，故都只对同一玩家结算一次；
 * 消费点在 {@link SanityRestoreService}（一处），本类只声明"什么算、给多少"。
 *
 * <p><b>表已补全</b>：设计表里 +6 档的"理智回复剂"（{@code forbidden_hex:forbidden_hex_sanity_tonic}）与 +7 档的
 * "高级理智恢复剂"（{@code forbidden_hex:forbidden_hex_greater_sanity_tonic}）两件物品已于 P5 落地，档位见
 * {@link #TIER_SANITY_TONIC} / {@link #TIER_GREATER_SANITY_TONIC}；二者同时是"窗口补 SAN"物品
 * （见 {@code SanityTonicService}），两条链路各记各的账（上限一次性抬升 · SAN 窗口分摊），互不冲突。
 *
 * <p>所有数值均为<b>待调手感值</b>。
 */
public final class SanityBuiltinRestores {

    /** 常规首用档：+5（理论回复剂档位之一；待调手感值） */
    public static final float TIER_COMMON = 5f;
    /** 金苹果档：+6（待调手感值） */
    public static final float TIER_GOLDEN_APPLE = 6f;
    /** 附魔金苹果档：+7（待调手感值） */
    public static final float TIER_ENCHANTED_GOLDEN_APPLE = 7f;
    /**
     * 理智回复剂（{@code forbidden_hex:forbidden_hex_sanity_tonic}）：+6（P5 接线，原设计表的缺口档位；待调手感值）
     *
     * <p>注意：该物品同时是"5s 内共回 30 SAN"的窗口补量物（{@code SanityTonicService}）。
     * 本条只是<b>首用一次性抬升上限</b>，与之无关——不要因为看到同一件物品就以为补量被记两次。
     */
    public static final float TIER_SANITY_TONIC = 6f;
    /** 高级理智恢复剂（{@code forbidden_hex:forbidden_hex_greater_sanity_tonic}）：+7（P5 接线；待调手感值） */
    public static final float TIER_GREATER_SANITY_TONIC = 7f;

    /** 瞬间治疗药水（含 II 级"强效"；按药水注册 id 匹配，避免依赖 Potions 常量名） */
    private static final List<ResourceLocation> HEALING_POTIONS =
            List.of(potion("healing"), potion("strong_healing"));
    /** 生命回复药水（含长效 / 强效） */
    private static final List<ResourceLocation> REGENERATION_POTIONS =
            List.of(potion("regeneration"), potion("long_regeneration"), potion("strong_regeneration"));

    /** 重复注册保护（init 被重复调用时只注册一次） */
    private static boolean registered;

    private SanityBuiltinRestores() {
    }

    /** 注册全部内置首用来源（由 {@code AkaishiMod.init} 调用；幂等） */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        // 药水两类（饮用瞬间治疗 / 生命回复）：+5
        SanityRestoreRegistry.register(new PotionSource(id("restore_healing_potion"),
                TIER_COMMON, HEALING_POTIONS));
        SanityRestoreRegistry.register(new PotionSource(id("restore_regeneration_potion"),
                TIER_COMMON, REGENERATION_POTIONS));
        // 牛奶桶：+5（原版唯一"清除全部效果"的消耗品，与"理智回稳"的语义一致）
        SanityRestoreRegistry.register(new ItemSource(id("restore_milk_bucket"),
                TIER_COMMON, stack -> stack.is(Items.MILK_BUCKET)));
        // 蜂蜜瓶：+5
        SanityRestoreRegistry.register(new ItemSource(id("restore_honey_bottle"),
                TIER_COMMON, stack -> stack.is(Items.HONEY_BOTTLE)));
        // 金萝卜：+5
        SanityRestoreRegistry.register(new ItemSource(id("restore_golden_carrot"),
                TIER_COMMON, stack -> stack.is(Items.GOLDEN_CARROT)));
        // 金西瓜（本项目自有食物，食补档位见 SanityBuiltinFood）：+5
        SanityRestoreRegistry.register(new ItemSource(id("restore_golden_melon_slice"),
                TIER_COMMON, stack -> stack.is(ForbiddenMaterialItems.goldenMelonSlice.get())));
        // 金苹果：+6
        SanityRestoreRegistry.register(new ItemSource(id("restore_golden_apple"),
                TIER_GOLDEN_APPLE, stack -> stack.is(Items.GOLDEN_APPLE)));
        // 附魔金苹果：+7
        SanityRestoreRegistry.register(new ItemSource(id("restore_enchanted_golden_apple"),
                TIER_ENCHANTED_GOLDEN_APPLE, stack -> stack.is(Items.ENCHANTED_GOLDEN_APPLE)));
        // 理智回复剂：+6（P5 落地物品；窗口补量走 SanityTonicService，与本首用档位互不干涉）
        SanityRestoreRegistry.register(new ItemSource(id("restore_sanity_tonic"),
                TIER_SANITY_TONIC, stack -> stack.is(AkaishiSanityItems.sanityTonic.get())));
        // 高级理智恢复剂：+7
        SanityRestoreRegistry.register(new ItemSource(id("restore_greater_sanity_tonic"),
                TIER_GREATER_SANITY_TONIC, stack -> stack.is(AkaishiSanityItems.greaterSanityTonic.get())));
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(AkaishiMod.MOD_ID, path);
    }

    private static ResourceLocation potion(String path) {
        return new ResourceLocation("minecraft", path);
    }

    /** 按物品匹配的来源（一次性；{@code sancAmount} 为未乘 COG SANC 效力的原始量） */
    private record ItemSource(ResourceLocation id, float sancAmount,
                              Predicate<ItemStack> matcher) implements ISanityRestoreSource {

        @Override
        public boolean matches(Player player, ItemStack stack) {
            return stack != null && !stack.isEmpty() && matcher != null && matcher.test(stack);
        }

        @Override
        public boolean onceOnly() {
            return true;
        }
    }

    /**
     * 按药水类型匹配的来源（一次性）。
     *
     * <p>只认可饮用的 {@code minecraft:potion}：喷溅 / 滞留药水不算"饮用"，不参与首用结算。
     */
    private record PotionSource(ResourceLocation id, float sancAmount,
                                List<ResourceLocation> potions) implements ISanityRestoreSource {

        @Override
        public boolean matches(Player player, ItemStack stack) {
            if (stack == null || stack.isEmpty() || stack.getItem() != Items.POTION) {
                return false;
            }
            return potions.contains(BuiltInRegistries.POTION.getKey(PotionUtils.getPotion(stack)));
        }

        @Override
        public boolean onceOnly() {
            return true;
        }
    }
}
